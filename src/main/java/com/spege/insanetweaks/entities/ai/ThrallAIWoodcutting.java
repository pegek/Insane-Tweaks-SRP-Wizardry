package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockSapling;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * AI task: Woodcutting mode.
 *
 * DEBUG VERSION — logs every significant state transition.
 * Remove or gate behind ModConfig.debug before shipping.
 */
@SuppressWarnings("null")
public class ThrallAIWoodcutting extends EntityAIBase {

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallWoodcut");

    private static final int SCAN_RANGE         = 40;
    private static final int SCAN_INTERVAL_TICKS = 100;
    private static final float MINING_SPEED_MULTIPLIER = 15.0F;
    private static final int MIN_MINING_TICKS   = 5;
    private static final double CLOSE_ENOUGH_SQ = 8.0 * 8.0; // 8 blocks reach

    private final EntityThrallMinion thrall;

    private enum State { SEARCHING, NAVIGATING, MINING }
    private State state = State.SEARCHING;

    @Nullable private BlockPos targetBlock;
    private int miningTicks;
    private int miningTicksRequired;
    private long lastScanTime;
    private int debugTickCounter = 0;

    // Stuck detection
    private int navTimer; // ticks spent in NAVIGATING toward current target
    private static final int NAV_TIMEOUT_TICKS = 200; // ~10 seconds — abort if can't reach
    private int consecutiveStuckBlocks;
    private static final int MAX_STUCK_BLOCKS = 3;

    /** Connected logs discovered after mining one log (tree trunk/branches). */
    private final Deque<BlockPos> connectedLogs = new ArrayDeque<>();

    public ThrallAIWoodcutting(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(3);
        if (debugLogs()) LOG.info("[Thrall#{}] ThrallAIWoodcutting constructed", thrall.getEntityId());
    }

    private static boolean debugLogs() {
        return ModConfig.client.enableThrallDebugLogs;
    }

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() != ThrallMode.WOODCUTTING) return false;
        boolean invFull = thrall.getThrallInventory().isFull();
        if (invFull && debugLogs()) {
            LOG.warn("[Thrall#{}] shouldExecute=false — inventory isFull=true! Slots: {}",
                    thrall.getEntityId(), inventorySnapshot());
        }
        return !invFull;
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (thrall.getMode() != ThrallMode.WOODCUTTING) {
            if (debugLogs()) LOG.info("[Thrall#{}] shouldContinueExecuting=false — mode changed to {}",
                    thrall.getEntityId(), thrall.getMode());
            return false;
        }
        boolean invFull = thrall.getThrallInventory().isFull();
        if (invFull && debugLogs()) {
            LOG.info("[Thrall#{}] shouldContinueExecuting=false — inventory full, slots: {}",
                    thrall.getEntityId(), inventorySnapshot());
        }
        return !invFull;
    }

    @Override
    public void startExecuting() {
        if (debugLogs()) LOG.info("[Thrall#{}] startExecuting() — resetting woodcut state at pos ({},{},{})",
                thrall.getEntityId(),
                (int)thrall.posX, (int)thrall.posY, (int)thrall.posZ);
        state = State.SEARCHING;
        targetBlock = null;
        connectedLogs.clear();
        lastScanTime = 0;
        navTimer = 0;
    }

    @Override
    public void resetTask() {
        if (debugLogs()) LOG.info("[Thrall#{}] resetTask() — state was {}, target was {}",
                thrall.getEntityId(), state, targetBlock);
        if (targetBlock != null) {
            thrall.world.sendBlockBreakProgress(thrall.getEntityId(), targetBlock, -1);
        }
        targetBlock = null;
        connectedLogs.clear();
        state = State.SEARCHING;
        thrall.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        debugTickCounter++;
        // Periodic inventory/state log every 100 ticks
        if (debugLogs() && debugTickCounter % 100 == 0) {
            LOG.info("[Thrall#{}] updateTask() tick={} state={} target={} queueSize={} inv=[{}]",
                    thrall.getEntityId(), debugTickCounter, state, targetBlock,
                    connectedLogs.size(), inventorySnapshot());
        }

        switch (state) {
            case SEARCHING: tickSearching(); break;
            case NAVIGATING: tickNavigating(); break;
            case MINING: tickMining(); break;
        }
    }

    // ---- SEARCHING ----

    private void tickSearching() {
        long now = thrall.world.getTotalWorldTime();
        if (now - lastScanTime < SCAN_INTERVAL_TICKS && lastScanTime != 0) return;
        lastScanTime = now;

        // Try connected logs first (continuing to chop the same tree)
        while (!connectedLogs.isEmpty()) {
            BlockPos next = connectedLogs.poll();
            if (next != null && isLog(thrall.world.getBlockState(next))) {
                if (debugLogs()) LOG.info("[Thrall#{}] Continuing tree — next connected log at {}", thrall.getEntityId(), next);
                targetBlock = next;
                state = State.NAVIGATING;
                navTimer = 0;
                return;
            }
        }

        // Scan for nearest log in range
        if (debugLogs()) LOG.info("[Thrall#{}] Scanning {} block radius for logs...", thrall.getEntityId(), SCAN_RANGE);
        BlockPos nearest = findNearestLog();
        if (nearest != null) {
            if (debugLogs()) LOG.info("[Thrall#{}] Found log at {} (dist={})", thrall.getEntityId(), nearest,
                    String.format("%.1f", Math.sqrt(thrall.getDistanceSq(nearest))));
            targetBlock = nearest;
            state = State.NAVIGATING;
            navTimer = 0;
            thrall.setStatusText("Woodcutting...");
        } else {
            if (debugLogs()) LOG.info("[Thrall#{}] No logs found in {} block radius", thrall.getEntityId(), SCAN_RANGE);
            thrall.setStatusText("No trees found");
        }
    }

    @Nullable
    private BlockPos findNearestLog() {
        BlockPos thrallPos = new BlockPos(thrall);
        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;
        World world = thrall.world;
        int logCount = 0;

        int minY = Math.max(1, thrallPos.getY() - 5);
        int maxY = Math.min(255, thrallPos.getY() + 20);

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int x = thrallPos.getX() - SCAN_RANGE; x <= thrallPos.getX() + SCAN_RANGE; x++) {
            for (int z = thrallPos.getZ() - SCAN_RANGE; z <= thrallPos.getZ() + SCAN_RANGE; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mpos.setPos(x, y, z);
                    if (!world.isBlockLoaded(mpos)) continue;
                    if (isLog(world.getBlockState(mpos))) {
                        logCount++;
                        double distSq = thrallPos.distanceSq(mpos);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            bestPos = mpos.toImmutable();
                        }
                    }
                }
            }
        }
        if (debugLogs()) LOG.info("[Thrall#{}] Scan complete — found {} log blocks total, nearest: {}",
                thrall.getEntityId(), logCount, bestPos);
        return bestPos;
    }

    // ---- NAVIGATING ----

    private void tickNavigating() {
        if (targetBlock == null || !isLog(thrall.world.getBlockState(targetBlock))) {
            if (debugLogs()) LOG.info("[Thrall#{}] NAVIGATING — target {} is no longer a log, resetting to SEARCHING",
                    thrall.getEntityId(), targetBlock);
            targetBlock = null;
            state = State.SEARCHING;
            return;
        }

        navTimer++;
        if (navTimer > NAV_TIMEOUT_TICKS) {
            LOG.warn("[Thrall#{}] NAVIGATING — timeout reaching {} after {} ticks, abandoning",
                    thrall.getEntityId(), targetBlock, navTimer);
            targetBlock = null;
            state = State.SEARCHING;
            lastScanTime = 0;
            navTimer = 0;
            thrall.getNavigator().clearPath();
            return;
        }

        double distSq = thrall.getDistanceSq(targetBlock);
        if (distSq <= CLOSE_ENOUGH_SQ) {
            if (debugLogs()) LOG.info("[Thrall#{}] NAVIGATING — reached {} (distSq={}), starting MINING",
                    thrall.getEntityId(), targetBlock, String.format("%.2f", distSq));
            startMiningBlock();
            return;
        }

        if (thrall.getNavigator().noPath()) {
            if (debugLogs()) LOG.info("[Thrall#{}] NAVIGATING — requesting path to {} (distSq={})",
                    thrall.getEntityId(), targetBlock, String.format("%.2f", distSq));
            boolean success = thrall.getNavigator().tryMoveToXYZ(
                    targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5, 0.8);
            if (!success) {
                LOG.warn("[Thrall#{}] NAVIGATING — pathfinding FAILED to {}, skipping log",
                        thrall.getEntityId(), targetBlock);
                targetBlock = null;
                state = State.SEARCHING;
                lastScanTime = 0;
                return; // Prevent NullPointerException below
            }
        }

        thrall.getLookHelper().setLookPosition(
                targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5,
                10.0F, (float) thrall.getVerticalFaceSpeed());
    }

    // ---- MINING ----

    private void startMiningBlock() {
        if (targetBlock == null) return;
        IBlockState blockState = thrall.world.getBlockState(targetBlock);
        float hardness = blockState.getBlockHardness(thrall.world, targetBlock);
        if (hardness < 0) {
            LOG.warn("[Thrall#{}] startMining — block {} is unbreakable (hardness={}), skipping",
                    thrall.getEntityId(), targetBlock, hardness);
            targetBlock = null;
            state = State.SEARCHING;
            return;
        }
        miningTicksRequired = Math.max(MIN_MINING_TICKS, (int) (hardness * MINING_SPEED_MULTIPLIER));
        miningTicks = 0;
        state = State.MINING;
        if (debugLogs()) LOG.info("[Thrall#{}] startMining — block={} hardness={} ticksRequired={}",
                thrall.getEntityId(), targetBlock, hardness, miningTicksRequired);
    }

    private void tickMining() {
        if (targetBlock == null || !isLog(thrall.world.getBlockState(targetBlock))) {
            if (targetBlock != null) {
                thrall.world.sendBlockBreakProgress(thrall.getEntityId(), targetBlock, -1);
                if (debugLogs()) LOG.info("[Thrall#{}] MINING — target {} disappeared mid-mine", thrall.getEntityId(), targetBlock);
            }
            targetBlock = null;
            state = State.SEARCHING;
            return;
        }

        thrall.getLookHelper().setLookPosition(
                targetBlock.getX() + 0.5, targetBlock.getY() + 0.5, targetBlock.getZ() + 0.5,
                10.0F, (float) thrall.getVerticalFaceSpeed());

        // Walk closer if far
        double distSq = thrall.getDistanceSq(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5);
        if (distSq > 4.0D) {
            thrall.getNavigator().tryMoveToXYZ(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5, 0.8D);
        } else if (distSq < 1.5D) {
            thrall.getNavigator().clearPath();
        }

        if (miningTicks % 5 == 0) thrall.swingArm(EnumHand.MAIN_HAND);
        miningTicks++;

        int progress = Math.min(9, (int) ((float) miningTicks / miningTicksRequired * 10.0F));
        thrall.world.sendBlockBreakProgress(thrall.getEntityId(), targetBlock, progress);

        if (miningTicks >= miningTicksRequired) {
            if (debugLogs()) LOG.info("[Thrall#{}] MINING — breaking block {} after {} ticks",
                    thrall.getEntityId(), targetBlock, miningTicks);
            BlockPos broken = targetBlock;
            IBlockState minedState = breakBlock(broken);
            discoverConnectedLogs(broken);
            thrall.world.sendBlockBreakProgress(thrall.getEntityId(), broken, -1);
            targetBlock = null;
            state = State.SEARCHING;
            lastScanTime = 0; // allow immediate rescan for connected logs

            // Stuck detection — block survived setBlockToAir (protected zone, etc.)
            if (!thrall.world.isAirBlock(broken)) {
                consecutiveStuckBlocks++;
                LOG.warn("[Thrall#{}] Log at {} survived break attempt (stuck #{} of {})",
                        thrall.getEntityId(), broken, consecutiveStuckBlocks, MAX_STUCK_BLOCKS);
                if (consecutiveStuckBlocks >= MAX_STUCK_BLOCKS) {
                    LOG.warn("[Thrall#{}] Aborting woodcutting — too many stuck logs, switching to STAY",
                            thrall.getEntityId());
                    thrall.setMode(ThrallMode.STAY);
                    thrall.setStatusText("Stuck — please help");
                    consecutiveStuckBlocks = 0;
                }
            } else {
                consecutiveStuckBlocks = 0;
                // Replant the matching sapling once we've confirmed the log was actually removed —
                // doing it earlier would make the stuck-detection's air check fail.
                if (minedState != null) tryReplantSapling(broken, minedState);
            }
        }
    }

    /** Returns the block state that was at {@code pos} before it was set to air, or null on failure. */
    @Nullable
    private IBlockState breakBlock(BlockPos pos) {
        World world = thrall.world;
        IBlockState blockState = world.getBlockState(pos);
        Block block = blockState.getBlock();

        world.playEvent(2001, pos, Block.getStateId(blockState));

        NonNullList<ItemStack> drops = NonNullList.create();
        block.getDrops(drops, world, pos, blockState, 0);
        if (debugLogs()) LOG.info("[Thrall#{}] Breaking {} — {} drops, inv before: [{}]",
                thrall.getEntityId(), pos, drops.size(), inventorySnapshot());

        for (ItemStack drop : drops) {
            boolean added = thrall.getThrallInventory().addItemStackToInventory(drop);
            if (!added) {
                LOG.warn("[Thrall#{}] Inventory full! Dropping {} on ground", thrall.getEntityId(), drop.getDisplayName());
                net.minecraft.entity.item.EntityItem entity =
                        new net.minecraft.entity.item.EntityItem(
                                world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                entity.setPickupDelay(10);
                world.spawnEntity(entity);
            }
        }
        if (debugLogs()) LOG.info("[Thrall#{}] After break — inv: [{}]", thrall.getEntityId(), inventorySnapshot());
        world.setBlockToAir(pos);
        return blockState;
    }

    /**
     * Attempts to plant a sapling at {@code pos} matching the species of the log we just broke.
     * Only fires for the bottom log of a tree — detected by the block below being dirt/grass.
     * Falls back to "any sapling in inventory" if the matching variant isn't available, so a
     * thrall stocked with oak saplings can still replant a chopped birch (cosmetically off, but
     * the player retains a tree where they had one). Modded logs get the fallback only.
     */
    private void tryReplantSapling(BlockPos pos, IBlockState minedLogState) {
        World world = thrall.world;
        Block ground = world.getBlockState(pos.down()).getBlock();
        if (ground != Blocks.DIRT && ground != Blocks.GRASS) return;
        if (!world.isAirBlock(pos)) return;

        int preferredMeta = saplingMetaForLog(minedLogState);
        int slot = (preferredMeta >= 0) ? findSaplingSlot(preferredMeta) : -1;
        if (slot < 0) slot = findSaplingSlot(-1);
        if (slot < 0) return;

        ItemStack saplingStack = thrall.getThrallInventory().getStackInSlot(slot);
        BlockPlanks.EnumType saplingType = BlockPlanks.EnumType.byMetadata(saplingStack.getMetadata() & 7);
        IBlockState saplingState = Blocks.SAPLING.getDefaultState().withProperty(BlockSapling.TYPE, saplingType);
        world.setBlockState(pos, saplingState, 3);

        saplingStack.shrink(1);
        if (saplingStack.isEmpty()) {
            thrall.getThrallInventory().setInventorySlotContents(slot, ItemStack.EMPTY);
        }
        if (debugLogs()) LOG.info("[Thrall#{}] Replanted sapling (meta={}) at {}",
                thrall.getEntityId(), saplingStack.getMetadata(), pos);
    }

    /**
     * Maps a vanilla log state to its sapling metadata. BlockOldLog covers oak/spruce/birch/jungle
     * (meta 0–3); BlockNewLog covers acacia/dark-oak (meta 4–5). Both share BlockPlanks.EnumType,
     * so the metadata aligns directly with the sapling's {@code TYPE} property.
     */
    private static int saplingMetaForLog(IBlockState logState) {
        Block block = logState.getBlock();
        if (block instanceof BlockOldLog) return logState.getValue(BlockOldLog.VARIANT).getMetadata();
        if (block instanceof BlockNewLog) return logState.getValue(BlockNewLog.VARIANT).getMetadata();
        return -1;
    }

    /** Finds a sapling stack in inventory. Pass -1 for {@code desiredMeta} to accept any variant. */
    private int findSaplingSlot(int desiredMeta) {
        Item saplingItem = Item.getItemFromBlock(Blocks.SAPLING);
        for (int i = 0; i < thrall.getThrallInventory().getSizeInventory(); i++) {
            ItemStack s = thrall.getThrallInventory().getStackInSlot(i);
            if (s.isEmpty() || s.getItem() != saplingItem) continue;
            if (desiredMeta < 0 || s.getMetadata() == desiredMeta) return i;
        }
        return -1;
    }

    /**
     * After mining a log, discover connected logs (trunk going up, branches sideways).
     * Trunk (up) gets priority so we fell top-down.
     */
    private void discoverConnectedLogs(BlockPos minedPos) {
        World world = thrall.world;
        int found = 0;

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    
                    BlockPos pos = minedPos.add(dx, dy, dz);
                    if (isLog(world.getBlockState(pos)) && !connectedLogs.contains(pos)) {
                        // Prioritize blocks above so we climb the trunk
                        if (dy > 0) {
                            connectedLogs.addFirst(pos);
                        } else {
                            connectedLogs.addLast(pos);
                        }
                        found++;
                    }
                }
            }
        }

        if (debugLogs()) LOG.info("[Thrall#{}] discoverConnectedLogs({}) — found {} connected logs, queue size now: {}",
                thrall.getEntityId(), minedPos, found, connectedLogs.size());
    }

    // ---- Utility ----

    /**
     * Soft log detection: matches vanilla BlockLog AND any modded block
     * whose registry name contains "log".
     */
    private static boolean isLog(IBlockState blockState) {
        if (blockState == null) return false;
        Block block = blockState.getBlock();
        if (block instanceof BlockLog) return true;
        net.minecraft.util.ResourceLocation regName = block.getRegistryName();
        if (regName == null) return false;
        return regName.getResourcePath().contains("log");
    }

    /** Compact inventory snapshot for logging. */
    private String inventorySnapshot() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < thrall.getThrallInventory().getSizeInventory(); i++) {
            ItemStack s = thrall.getThrallInventory().getStackInSlot(i);
            if (!s.isEmpty()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.getDisplayName()).append("x").append(s.getCount());
            }
        }
        return sb.length() == 0 ? "EMPTY" : sb.toString();
    }
}
