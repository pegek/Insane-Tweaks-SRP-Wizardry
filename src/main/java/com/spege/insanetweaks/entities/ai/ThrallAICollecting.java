package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallChestHelper;
import com.spege.insanetweaks.entities.ThrallMode;
import com.spege.insanetweaks.entities.inventory.ThrallInventory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.block.BlockStone;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI task: Collecting mode.
 *
 * Lifecycle:
 *  1. WAITING_FOR_ITEMS — player tosses 1-N (default 4) block-items at the thrall.
 *     Lock-in trigger: max-targets reached OR pickup-window timeout since the FIRST accepted item.
 *     Lock-in also re-arms the thrall's work timer (see {@code rearmWorkTimer}).
 *  2. SEARCHING — local scan around the current position first (C-2a, no teleport), then every
 *     collectingTickInterval ticks: random teleport in a ring around home, adaptive Y per current
 *     target hint, sphere-scan for matches.
 *  3. HARVESTING — drain a queue of scan hits; each block: TP adjacent, mine for hardness*MULT ticks,
 *     drop default loot, then vein-BFS into same-type neighbors.
 *  4. RETURNING — TP home, smartDeposit into nearby chests, then rest.
 *  5. RESTING — short pause at home. On rest end: loop back to SEARCHING with the SAME target list
 *     (session loop, C-3), UNLESS the work timer expired, the target list is empty, or the
 *     just-finished session harvested nothing (exhausted area) — those drop the AI back to
 *     WAITING_FOR_ITEMS so the player can stage a fresh target set without leaving COLLECTING.
 *
 * Per-session triggers into RETURNING: session-minutes budget OR inventory full OR N consecutive
 * empty scan cycles. DONE is a near-dead terminal: reached only when NO home is set (nowhere to
 * deposit or loop), or via NBT load — where the readFromNBT guard immediately rewrites a
 * COLLECTING+DONE restore to WAITING_FOR_ITEMS.
 *
 * Entire state persists in NBT under "ThrallCollecting" so a saved game restores cleanly.
 * Reading state with the player having interrupted the mode (FOLLOW/STAY) is supported via
 * a resume window: re-clicking COLLECTING within {@code collectingResumeWindowMinutes} restores
 * the locked target list and remaining budget.
 */
@SuppressWarnings("null")
public class ThrallAICollecting extends EntityAIBase {

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallCollect");

    private static final float MINING_SPEED_MULTIPLIER = 15.0F;
    private static final int MIN_MINING_TICKS = 3;

    private static final int MAX_SCAN_HITS = 64;
    private static final int VEIN_BFS_FRONTIER_RADIUS = 30;
    private static final int SAFE_SPOT_VERTICAL_PROBE = 6;
    private static final int REST_AT_HOME_TICKS = 100;

    private final EntityThrallMinion thrall;

    public enum Phase { WAITING_FOR_ITEMS, SEARCHING, HARVESTING, RETURNING, RESTING, DONE }
    private Phase phase = Phase.DONE;

    private final List<Sig> targets = new ArrayList<>();

    private long sessionStartTick;
    private long firstItemTick;
    private long lastCycleTick;
    private long pausedAtTick;
    private int consecutiveEmptyCycles;
    private int totalItemsHarvestedThisSession;
    private int targetCycleIndex;
    /** When > 0, the AI is resting at home between looped sessions; counts down each tick. */
    private int restTicksRemaining;
    /** Set by EntityThrallMinion when thrallWorkDurationHours elapses — forces WAITING on next rest end. */
    private boolean workTimerExpired;

    private final Deque<BlockPos> harvestQueue = new ArrayDeque<>();
    private final Set<BlockPos> harvestVisited = new HashSet<>();
    @Nullable private BlockPos miningTarget;
    private int miningTicks;
    private int miningTicksRequired;
    @Nullable private Sig miningSig;
    @Nullable private BlockPos veinRoot;

    public ThrallAICollecting(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(3);
    }

    private static boolean debugLogs() { return ModConfig.client.enableThrallDebugLogs; }

    // ------------------------------------------------------------------
    // EntityAIBase plumbing
    // ------------------------------------------------------------------

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() != ThrallMode.COLLECTING) return false;
        if (!ModConfig.thrall.collecting.enableCollectingMode) return false;
        return thrall.getHomePoint() != null;
    }

    @Override
    public boolean shouldContinueExecuting() { return shouldExecute(); }

    @Override
    public void resetTask() {
        thrall.getNavigator().clearPath();
        if (phase != Phase.DONE) {
            // Mode is being interrupted (player picked another mode). Mark paused so a re-click
            // within the resume window restores the locked target list.
            pausedAtTick = thrall.world.getTotalWorldTime();
        }
    }

    @Override
    public void updateTask() {
        long now = thrall.world.getTotalWorldTime();

        // Termination triggers (checked once per tick — cheap)
        if (phase != Phase.RETURNING && phase != Phase.DONE && phase != Phase.WAITING_FOR_ITEMS
                && phase != Phase.RESTING) {
            long durationTicks = (long) ModConfig.thrall.collecting.collectingDurationMinutes * 60L * 20L;
            if (now - sessionStartTick >= durationTicks) {
                if (debugLogs()) LOG.info("[Thrall#{}] Collecting: session timeout", thrall.getEntityId());
                beginReturn();
                return;
            }
            if (thrall.getThrallInventory().isFull()) {
                if (debugLogs()) LOG.info("[Thrall#{}] Collecting: inventory full", thrall.getEntityId());
                beginReturn();
                return;
            }
            if (consecutiveEmptyCycles >= ModConfig.thrall.collecting.collectingMaxEmptyCycles) {
                if (debugLogs()) LOG.info("[Thrall#{}] Collecting: empty-cycle abort", thrall.getEntityId());
                beginReturn();
                return;
            }
        }

        switch (phase) {
            case WAITING_FOR_ITEMS:
                tickWaiting(now);
                break;
            case SEARCHING:
                tickSearching(now);
                break;
            case HARVESTING:
                tickHarvesting(now);
                break;
            case RETURNING:
                tickReturning();
                break;
            case RESTING:
                tickResting();
                break;
            case DONE:
                break;
        }
    }

    // ------------------------------------------------------------------
    // External entry points (called from EntityThrallMinion / packet handler)
    // ------------------------------------------------------------------

    /**
     * Called by the network handler when the player picks COLLECTING. Either starts a fresh
     * waiting session or resumes a paused one if within the resume window.
     */
    public void startOrResume() {
        long now = thrall.world.getTotalWorldTime();
        long resumeWindowTicks = (long) ModConfig.thrall.collecting.collectingResumeWindowMinutes * 60L * 20L;
        boolean resumeEligible = pausedAtTick > 0
                && resumeWindowTicks > 0
                && (now - pausedAtTick) <= resumeWindowTicks
                && !targets.isEmpty()
                && (phase == Phase.SEARCHING || phase == Phase.HARVESTING);

        if (resumeEligible) {
            // Push sessionStartTick forward by the paused duration so the 2h budget is preserved.
            sessionStartTick += (now - pausedAtTick);
            pausedAtTick = 0;
            harvestQueue.clear();
            harvestVisited.clear();
            miningTarget = null;
            phase = Phase.SEARCHING;
            thrall.setStatusText("Resuming...");
        } else {
            resetSession();
            phase = Phase.WAITING_FOR_ITEMS;
            firstItemTick = 0;
            sessionStartTick = now;
            thrall.setStatusText("Waiting for items...");
        }
    }

    /** True if currently waiting for the player to toss target items. */
    public boolean isWaitingForItems() {
        return phase == Phase.WAITING_FOR_ITEMS && thrall.getMode() == ThrallMode.COLLECTING;
    }

    /**
     * Tries to register the (block, metadata) signature of {@code stack} as a target.
     * Returns true iff accepted (caller should consume one item from the stack).
     * Duplicates of an already-registered signature also return true (refresh timer) but
     * don't add a slot — caller still consumes one item to provide visual feedback.
     */
    public boolean tryAcceptItem(ItemStack stack) {
        if (phase != Phase.WAITING_FOR_ITEMS) return false;
        if (stack.isEmpty()) return false;
        Block block = Block.getBlockFromItem(stack.getItem());
        if (block == Blocks.AIR) return false;

        long now = thrall.world.getTotalWorldTime();
        Sig sig = new Sig(block, stack.getMetadata());

        if (firstItemTick == 0) firstItemTick = now;

        if (containsSig(targets, sig)) {
            firstItemTick = now; // refresh timer
            return true;
        }

        int cap = ModConfig.thrall.collecting.collectingMaxTargets;
        if (targets.size() >= cap) {
            thrall.setStatusText("Targets full (" + cap + ")");
            return false;
        }

        targets.add(sig);
        thrall.setStatusText("Targets: " + targets.size() + "/" + cap);

        if (targets.size() >= cap) {
            // Hit the cap → instant lock-in
            beginSearch(now);
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Phase: WAITING_FOR_ITEMS
    // ------------------------------------------------------------------

    private void tickWaiting(long now) {
        if (firstItemTick == 0) {
            // No items yet — keep waiting indefinitely.
            return;
        }
        long timeoutTicks = (long) ModConfig.thrall.collecting.collectingItemPickupTimeoutSeconds * 20L;
        if (now - firstItemTick >= timeoutTicks) {
            beginSearch(now);
        }
    }

    private void beginSearch(long now) {
        if (targets.isEmpty()) {
            // Edge case: lock-in fired without any targets (shouldn't happen via normal flow).
            beginReturn();
            return;
        }
        sessionStartTick = now;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        phase = Phase.SEARCHING;
        thrall.setStatusText("Searching...");
        if (debugLogs()) LOG.info("[Thrall#{}] Collecting: locked in {} targets, beginning search",
                thrall.getEntityId(), targets.size());

        // Re-arm the work timer for this new session. After a timer expiry the mode stays
        // COLLECTING (setMode is never called on the WAITING re-entry path), so workStartTick
        // would otherwise remain 0 and the newly staged session would run unbounded.
        if (thrall.getMode() == ThrallMode.COLLECTING) {
            thrall.rearmWorkTimer();
        }

        // C-2a: sweep the thrall's CURRENT position first (no teleport), so targets right next to
        // where the player deployed it are collected before any ring-teleport search begins.
        tryLocalScan();
    }

    /**
     * Scans around the thrall's current position (no teleport). If matches are found, loads the
     * harvest queue and switches to HARVESTING immediately. No-op if nothing matches — the normal
     * ring-teleport SEARCHING cycle then takes over.
     */
    private void tryLocalScan() {
        BlockPos here = new BlockPos(thrall);
        List<BlockPos> hits = scanForTargets(here);
        if (hits.isEmpty()) return;

        consecutiveEmptyCycles = 0;
        harvestQueue.clear();
        harvestVisited.clear();
        for (BlockPos p : hits) {
            harvestQueue.add(p);
            harvestVisited.add(p);
        }
        veinRoot = hits.get(0);
        phase = Phase.HARVESTING;
        thrall.setStatusText("Harvesting (" + hits.size() + ")");
    }

    // ------------------------------------------------------------------
    // Phase: SEARCHING
    // ------------------------------------------------------------------

    private void tickSearching(long now) {
        long interval = ModConfig.thrall.collecting.collectingTickInterval;
        if (lastCycleTick != 0 && now - lastCycleTick < interval) return;
        lastCycleTick = now;

        BlockPos home = thrall.getHomePoint();
        if (home == null) { beginReturn(); return; }

        // Pick a target whose YHint biases this cycle's TP selection.
        Sig hintTarget = targets.get(targetCycleIndex % targets.size());
        targetCycleIndex++;
        YHint hint = classify(hintTarget.block);

        BlockPos tpPoint = pickRandomSearchPoint(home, hint);
        if (tpPoint == null) {
            consecutiveEmptyCycles++;
            return;
        }

        thrall.setPositionAndUpdate(tpPoint.getX() + 0.5, tpPoint.getY(), tpPoint.getZ() + 0.5);
        thrall.playTeleportSound();
        thrall.getNavigator().clearPath();

        List<BlockPos> hits = scanForTargets(tpPoint);
        if (hits.isEmpty()) {
            consecutiveEmptyCycles++;
            return;
        }

        consecutiveEmptyCycles = 0;
        harvestQueue.clear();
        harvestVisited.clear();
        for (BlockPos p : hits) {
            harvestQueue.add(p);
            harvestVisited.add(p);
        }
        veinRoot = hits.get(0);
        phase = Phase.HARVESTING;
        thrall.setStatusText("Harvesting (" + hits.size() + ")");
    }

    @Nullable
    private BlockPos pickRandomSearchPoint(BlockPos home, YHint hint) {
        World world = thrall.world;
        int minDist = ModConfig.thrall.collecting.collectingMinTpDistance;
        int maxDist = ModConfig.thrall.collecting.collectingMaxTpDistance;
        if (maxDist < minDist) maxDist = minDist + 1;

        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = world.rand.nextDouble() * Math.PI * 2.0;
            int dist = minDist + world.rand.nextInt(maxDist - minDist + 1);
            int x = home.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = home.getZ() + (int) Math.round(Math.sin(angle) * dist);
            int targetY = pickYForHint(world, x, z, hint);
            BlockPos safe = resolveSafeSpot(world, x, targetY, z);
            if (safe != null) return safe;
        }
        return null;
    }

    private int pickYForHint(World world, int x, int z, YHint hint) {
        switch (hint) {
            case SURFACE: {
                BlockPos top = world.getHeight(new BlockPos(x, 0, z));
                return top.getY();
            }
            case UNDERGROUND:
                return 5 + world.rand.nextInt(36); // 5..40
            case ANYWHERE:
            default: {
                if (world.rand.nextBoolean()) {
                    BlockPos top = world.getHeight(new BlockPos(x, 0, z));
                    return top.getY();
                }
                return 5 + world.rand.nextInt(56); // 5..60
            }
        }
    }

    /** Finds the nearest empty 2-block-air column with solid floor within ±SAFE_SPOT_VERTICAL_PROBE of (x, y, z). */
    @Nullable
    private BlockPos resolveSafeSpot(World world, int x, int y, int z) {
        for (int dy = 0; dy <= SAFE_SPOT_VERTICAL_PROBE; dy++) {
            for (int sign : new int[]{1, -1}) {
                int probe = y + dy * sign;
                if (probe < 1 || probe > 250) continue;
                BlockPos floor = new BlockPos(x, probe - 1, z);
                BlockPos feet = new BlockPos(x, probe, z);
                BlockPos head = new BlockPos(x, probe + 1, z);
                IBlockState floorState = world.getBlockState(floor);
                if (!floorState.getMaterial().blocksMovement()) continue;
                if (floorState.getMaterial().isLiquid()) continue;
                if (!world.isAirBlock(feet) && world.getBlockState(feet).getMaterial().blocksMovement()) continue;
                if (!world.isAirBlock(head) && world.getBlockState(head).getMaterial().blocksMovement()) continue;
                return feet;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Phase: HARVESTING
    // ------------------------------------------------------------------

    private List<BlockPos> scanForTargets(BlockPos center) {
        List<BlockPos> hits = new ArrayList<>();
        World world = thrall.world;
        int r = ModConfig.thrall.collecting.collectingScanRadius;
        int rSq = r * r;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > rSq) continue;
                    BlockPos p = center.add(dx, dy, dz);
                    IBlockState s = world.getBlockState(p);
                    if (matchesAnyTarget(s)) {
                        hits.add(p);
                        if (hits.size() >= MAX_SCAN_HITS) return hits;
                    }
                }
            }
        }
        return hits;
    }

    private boolean matchesAnyTarget(IBlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.AIR) return false;
        int meta = block.getMetaFromState(state);
        for (Sig sig : targets) {
            if (sig.block == block && sig.metadata == meta) return true;
        }
        return false;
    }

    @Nullable
    private Sig sigOf(IBlockState state) {
        Block block = state.getBlock();
        int meta = block.getMetaFromState(state);
        for (Sig sig : targets) {
            if (sig.block == block && sig.metadata == meta) return sig;
        }
        return null;
    }

    private void tickHarvesting(long now) {
        if (miningTarget == null) {
            // Pop next from queue.
            BlockPos next = null;
            while (!harvestQueue.isEmpty()) {
                BlockPos cand = harvestQueue.poll();
                IBlockState s = thrall.world.getBlockState(cand);
                if (matchesAnyTarget(s)) {
                    next = cand;
                    break;
                }
            }
            if (next == null) {
                phase = Phase.SEARCHING;
                harvestVisited.clear();
                veinRoot = null;
                thrall.setStatusText("Searching...");
                return;
            }
            startMining(next);
            return;
        }

        // Already mining.
        miningTicks++;
        if (miningTicks < miningTicksRequired) return;

        finishMining();
    }

    private void startMining(BlockPos pos) {
        miningTarget = pos;
        miningTicks = 0;
        IBlockState state = thrall.world.getBlockState(pos);
        miningSig = sigOf(state);
        float hardness = state.getBlockHardness(thrall.world, pos);
        if (hardness < 0) hardness = 0;
        miningTicksRequired = Math.max(MIN_MINING_TICKS, (int) (hardness * MINING_SPEED_MULTIPLIER));

        // TP adjacent (closest non-solid face). Falls back to the column above.
        BlockPos adj = findAdjacentTpSpot(pos);
        if (adj != null) {
            thrall.setPositionAndUpdate(adj.getX() + 0.5, adj.getY(), adj.getZ() + 0.5);
            thrall.getNavigator().clearPath();
        }
        thrall.getLookHelper().setLookPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                30.0F, 30.0F);
    }

    @Nullable
    private BlockPos findAdjacentTpSpot(BlockPos block) {
        World world = thrall.world;
        // Try 4 horizontal neighbours.
        int[][] offsets = { {1,0,0}, {-1,0,0}, {0,0,1}, {0,0,-1} };
        for (int[] o : offsets) {
            BlockPos feet = block.add(o[0], 0, o[2]);
            BlockPos head = feet.up();
            BlockPos floor = feet.down();
            if (!world.getBlockState(floor).getMaterial().blocksMovement()) continue;
            if (world.getBlockState(feet).getMaterial().blocksMovement()) continue;
            if (world.getBlockState(head).getMaterial().blocksMovement()) continue;
            return feet;
        }
        // Fallback: column probe upward to first 2-block air with solid floor.
        for (int dy = 1; dy <= 4; dy++) {
            BlockPos feet = block.up(dy);
            BlockPos head = feet.up();
            BlockPos floor = feet.down();
            if (!world.getBlockState(floor).getMaterial().blocksMovement()) continue;
            if (world.getBlockState(feet).getMaterial().blocksMovement()) continue;
            if (world.getBlockState(head).getMaterial().blocksMovement()) continue;
            return feet;
        }
        return null;
    }

    private void finishMining() {
        BlockPos pos = miningTarget;
        miningTarget = null;
        if (pos == null) return;

        World world = thrall.world;
        IBlockState state = world.getBlockState(pos);
        if (matchesAnyTarget(state)) {
            // Drop default loot, then air the block.
            Block block = state.getBlock();
            block.dropBlockAsItem(world, pos, state, 0);
            world.setBlockToAir(pos);
            totalItemsHarvestedThisSession++;

            // Vein-BFS: enqueue 26-neighbours of same sig within frontier radius and not yet visited.
            if (miningSig != null && veinRoot != null
                    && harvestVisited.size() < ModConfig.thrall.collecting.collectingVeinMaxBlocks) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos n = pos.add(dx, dy, dz);
                            if (harvestVisited.contains(n)) continue;
                            if (n.distanceSq(veinRoot) > VEIN_BFS_FRONTIER_RADIUS * (long) VEIN_BFS_FRONTIER_RADIUS) continue;
                            IBlockState ns = world.getBlockState(n);
                            Sig nsig = sigOf(ns);
                            if (nsig != null && nsig.equals(miningSig)) {
                                harvestVisited.add(n);
                                harvestQueue.add(n);
                                if (harvestVisited.size() >= ModConfig.thrall.collecting.collectingVeinMaxBlocks) break;
                            }
                        }
                    }
                }
            }
        }
        miningSig = null;
    }

    // ------------------------------------------------------------------
    // Phase: RETURNING / DONE
    // ------------------------------------------------------------------

    private void beginReturn() {
        phase = Phase.RETURNING;
        thrall.setStatusText("Returning...");
    }

    /**
     * Teleport home, deposit, then rest briefly before deciding whether to loop (C-3).
     * With no home set there is nowhere to deposit or loop, so we go terminal via finishDone().
     */
    private void tickReturning() {
        BlockPos home = thrall.getHomePoint();
        if (home == null) {
            finishDone();
            return;
        }
        thrall.setPositionAndUpdate(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        thrall.playTeleportSound();
        thrall.getNavigator().clearPath();
        ThrallChestHelper.smartDeposit(thrall, home,
                ModConfig.thrall.collecting.collectingChestScanRange, 4, false);

        phase = Phase.RESTING;
        restTicksRemaining = REST_AT_HOME_TICKS;
        thrall.setStatusText("Resting...");
    }

    /**
     * Short pause at home between looped sessions. On completion: if the work-timer expired, the
     * target list is empty, or the just-finished session harvested NOTHING (exhausted area), drop to
     * WAITING_FOR_ITEMS ("Waiting for items"); otherwise start a fresh SEARCHING pass with the same
     * target list (C-3).
     */
    private void tickResting() {
        if (restTicksRemaining > 0) {
            restTicksRemaining--;
            return;
        }

        // Intrinsic loop terminator: capture the just-finished session's yield BEFORE the loop
        // branch resets it. A zero-yield session means the area is exhausted — idle in WAITING
        // instead of ring-teleporting forever. This must not rely on the work timer alone, because
        // thrallWorkDurationHours = 0 disables the timer entirely (onUpdate gates on workHours > 0).
        int harvestedLastSession = totalItemsHarvestedThisSession;

        if (workTimerExpired || targets.isEmpty() || harvestedLastSession <= 0) {
            workTimerExpired = false;
            enterWaitingForItems();
            return;
        }

        // Loop: restart a session with the same targets, resetting per-session counters
        // but KEEPING the locked target list.
        harvestQueue.clear();
        harvestVisited.clear();
        miningTarget = null;
        miningSig = null;
        veinRoot = null;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        totalItemsHarvestedThisSession = 0;
        sessionStartTick = thrall.world.getTotalWorldTime();
        phase = Phase.SEARCHING;
        thrall.setStatusText("Searching...");
        tryLocalScan();
    }

    /**
     * Drops the AI into WAITING_FOR_ITEMS at home without leaving COLLECTING mode. Clears the target
     * list and session state so the player can stage a fresh set of targets.
     */
    private void enterWaitingForItems() {
        phase = Phase.WAITING_FOR_ITEMS;
        targets.clear();
        harvestQueue.clear();
        harvestVisited.clear();
        miningTarget = null;
        miningSig = null;
        veinRoot = null;
        pausedAtTick = 0;
        firstItemTick = 0;
        sessionStartTick = 0;
        restTicksRemaining = 0;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        totalItemsHarvestedThisSession = 0;
        thrall.setStatusText("Waiting for items");
    }

    /** Called by EntityThrallMinion when thrallWorkDurationHours elapses while in COLLECTING mode.
     *  Routes the AI home to deposit, then (via the RESTING branch) into WAITING_FOR_ITEMS — never STAY. */
    public void onWorkTimerExpired() {
        workTimerExpired = true;
        // If already resting/returning, let the existing flow pick up workTimerExpired on rest-end.
        if (phase == Phase.SEARCHING || phase == Phase.HARVESTING) {
            beginReturn();
        } else if (phase == Phase.WAITING_FOR_ITEMS || phase == Phase.DONE) {
            // Nothing in flight — just clear the flag; we're already idle/waiting.
            workTimerExpired = false;
        }
    }

    /** Terminal path used only when NO home is set (nowhere to deposit or loop). */
    private void finishDone() {
        phase = Phase.DONE;
        int harvested = totalItemsHarvestedThisSession;
        thrall.setStatusText("Done collecting (" + harvested + ")");
        targets.clear();
        harvestQueue.clear();
        harvestVisited.clear();
        miningTarget = null;
        veinRoot = null;
        pausedAtTick = 0;
        firstItemTick = 0;
        sessionStartTick = 0;
        restTicksRemaining = 0;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        totalItemsHarvestedThisSession = 0;
        thrall.setMode(ThrallMode.STAY);
    }

    private void resetSession() {
        targets.clear();
        harvestQueue.clear();
        harvestVisited.clear();
        miningTarget = null;
        veinRoot = null;
        pausedAtTick = 0;
        firstItemTick = 0;
        sessionStartTick = 0;
        restTicksRemaining = 0;
        workTimerExpired = false;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        totalItemsHarvestedThisSession = 0;
    }

    // ------------------------------------------------------------------
    // Y-hint classification (no per-mod hardcoding)
    // ------------------------------------------------------------------

    private enum YHint { SURFACE, UNDERGROUND, ANYWHERE }

    private static YHint classify(Block block) {
        if (block instanceof BlockOre || block instanceof BlockRedstoneOre) return YHint.UNDERGROUND;
        if (block instanceof BlockStone) return YHint.UNDERGROUND;
        if (block instanceof BlockLog || block instanceof BlockLeaves) return YHint.SURFACE;
        Material mat = block.getDefaultState().getMaterial();
        if (mat == Material.ROCK) return YHint.UNDERGROUND;
        if (mat == Material.GROUND || mat == Material.GRASS || mat == Material.WOOD || mat == Material.LEAVES) {
            return YHint.SURFACE;
        }
        return YHint.ANYWHERE;
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("Phase", phase.ordinal());
        tag.setLong("SessionStart", sessionStartTick);
        tag.setLong("FirstItem", firstItemTick);
        tag.setLong("LastCycle", lastCycleTick);
        tag.setLong("PausedAt", pausedAtTick);
        tag.setInteger("EmptyCycles", consecutiveEmptyCycles);
        tag.setInteger("Harvested", totalItemsHarvestedThisSession);
        tag.setInteger("TargetCycle", targetCycleIndex);
        tag.setInteger("RestTicks", restTicksRemaining);
        tag.setBoolean("WorkTimerExpired", workTimerExpired);

        NBTTagList list = new NBTTagList();
        for (Sig sig : targets) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("Block", Block.REGISTRY.getNameForObject(sig.block).toString());
            t.setInteger("Meta", sig.metadata);
            list.appendTag(t);
        }
        tag.setTag("Targets", list);
    }

    public void readFromNBT(NBTTagCompound tag) {
        try {
            int ord = tag.getInteger("Phase");
            Phase[] vals = Phase.values();
            phase = ord >= 0 && ord < vals.length ? vals[ord] : Phase.DONE;
        } catch (RuntimeException ignored) { phase = Phase.DONE; }
        sessionStartTick = tag.getLong("SessionStart");
        firstItemTick = tag.getLong("FirstItem");
        lastCycleTick = tag.getLong("LastCycle");
        pausedAtTick = tag.getLong("PausedAt");
        consecutiveEmptyCycles = tag.getInteger("EmptyCycles");
        totalItemsHarvestedThisSession = tag.getInteger("Harvested");
        targetCycleIndex = tag.getInteger("TargetCycle");
        restTicksRemaining = tag.getInteger("RestTicks");
        workTimerExpired = tag.getBoolean("WorkTimerExpired");

        targets.clear();
        if (tag.hasKey("Targets")) {
            NBTTagList list = tag.getTagList("Targets", net.minecraftforge.common.util.Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                String name = t.getString("Block");
                int meta = t.getInteger("Meta");
                Block b = Block.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(name));
                if (b != null && b != Blocks.AIR) targets.add(new Sig(b, meta));
            }
        }
        // Don't restore mining target — let HARVESTING re-pop from queue on next tick.
        miningTarget = null;
        miningSig = null;
        veinRoot = null;
        harvestQueue.clear();
        harvestVisited.clear();

        // C-4: a save that landed mid-COLLECTING with phase DONE would otherwise sit idle forever
        // (DONE does nothing in updateTask and STAY was already applied). Force WAITING so the player
        // can immediately re-stage targets after load.
        if (thrall.getMode() == ThrallMode.COLLECTING && phase == Phase.DONE) {
            phase = Phase.WAITING_FOR_ITEMS;
            thrall.setStatusText("Waiting for items");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static boolean containsSig(List<Sig> list, Sig sig) {
        for (Sig s : list) if (s.equals(sig)) return true;
        return false;
    }

    private static final class Sig {
        final Block block;
        final int metadata;
        Sig(Block block, int metadata) {
            this.block = block;
            this.metadata = metadata;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Sig)) return false;
            Sig s = (Sig) o;
            return s.block == block && s.metadata == metadata;
        }
        @Override
        public int hashCode() {
            return System.identityHashCode(block) * 31 + metadata;
        }
    }
}
