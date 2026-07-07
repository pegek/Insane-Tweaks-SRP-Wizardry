package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallMaterialHelper;
import com.spege.insanetweaks.entities.ThrallMode;
import com.spege.insanetweaks.entities.inventory.ThrallInventory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFarmland;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.item.Item;
import net.minecraftforge.common.IPlantable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

/**
 * AI task: Farming mode.
 *
 * <p>Operates on player-prepared farmland only — never tills random ground to create new plots.
 * Each scan cycle the thrall picks the highest-priority task within its radius:
 * <ol>
 *   <li>Harvest mature crops (with replant from inventory)</li>
 *   <li>Harvest sugar cane / cactus / melon / pumpkin (no replant — bases regrow)</li>
 *   <li>Plant on empty farmland — chooses crop by neighbour-field vote, falls back to any seed in inventory</li>
 *   <li>Bone-meal immature crops (when bone meal available and config permits)</li>
 *   <li>Restore trampled farmland — re-tills DIRT blocks that border existing farmland</li>
 * </ol>
 *
 * Only activates when the Thrall is in {@link ThrallMode#FARMING}; idles silently if no
 * home point is set. The home point gates activation and is the deposit/recall target,
 * but the scan itself is anchored to the thrall's current position (same as woodcutting),
 * so the user can deploy the thrall onto the farm and keep the home point at a chest.
 */
@SuppressWarnings("null")
public class ThrallAIFarming extends EntityAIBase {

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallFarm");

    private static final int    SCAN_INTERVAL_TICKS  = 60;
    private static final int    VERTICAL_RANGE       = 2;
    private static final int    NAV_TIMEOUT_TICKS    = 100; // ~5s — then teleport fallback
    /** Distance at which the thrall considers itself within reach to start working a target. */
    private static final double CLOSE_ENOUGH_SQ      = 4.0;  // (2.0 blocks)^2 — arrival radius
    /**
     * WORKING drift radius. Strictly LARGER than {@link #CLOSE_ENOUGH_SQ} so that a thrall which has
     * just arrived (distance ≈ CLOSE_ENOUGH_SQ) cannot immediately trip the drift check and bounce
     * back to NAVIGATING. Equal radii + a corner-vs-center mismatch previously produced a permanent
     * NAVIGATING↔WORKING oscillation on diagonal approaches.
     */
    private static final double WORK_RANGE_SQ        = 6.25; // (2.5 blocks)^2 — drift radius
    /** How often to re-issue tryMoveToXYZ during NAVIGATING. Avoids spamming the pathfinder. */
    private static final int    REPATH_INTERVAL_TICKS = 20;
    private static final int    MIN_WORK_TICKS       = 10;
    private static final float  WORK_SPEED_MULT      = 15.0F;
    private static final int    MAX_TILLS_PER_SHIFT  = 16;

    private final EntityThrallMinion thrall;

    private enum State { SEARCHING, NAVIGATING, WORKING }
    private State state = State.SEARCHING;

    private enum TargetKind { HARVEST_CROP, HARVEST_BLOCK, BONEMEAL, TILL, PLANT }

    @Nullable private BlockPos targetPos;
    private TargetKind targetKind = TargetKind.HARVEST_CROP;
    /** Block class snapshotted when targeting a HARVEST_BLOCK (sugar cane / cactus / melon / pumpkin) — re-validated on approach. */
    @Nullable private Block expectedBlock;

    /** Cap how many till operations a thrall does in one shift to prevent runaway tilling. */
    private int tillsThisShift;

    private int workTicks;
    private int workTicksRequired;
    private long lastScanTime;
    private int navTimer;

    public ThrallAIFarming(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(3);
    }

    private static boolean debugLogs() {
        return ModConfig.client.enableThrallDebugLogs;
    }

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() != ThrallMode.FARMING) return false;
        if (!ModConfig.thrall.enableFarmingMode) return false;
        if (thrall.getHomePoint() == null) return false;
        return !thrall.getThrallInventory().isFull();
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (thrall.getMode() != ThrallMode.FARMING) return false;
        if (thrall.getHomePoint() == null) return false;
        return !thrall.getThrallInventory().isFull();
    }

    @Override
    public void startExecuting() {
        state = State.SEARCHING;
        targetPos = null;
        expectedBlock = null;
        lastScanTime = 0;
        navTimer = 0;
        tillsThisShift = 0;
        if (debugLogs()) LOG.info("[Thrall#{}] Farming startExecuting()", thrall.getEntityId());
    }

    @Override
    public void resetTask() {
        if (targetPos != null && (targetKind == TargetKind.HARVEST_CROP || targetKind == TargetKind.HARVEST_BLOCK)) {
            thrall.world.sendBlockBreakProgress(thrall.getEntityId(), targetPos, -1);
        }
        targetPos = null;
        expectedBlock = null;
        state = State.SEARCHING;
        thrall.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        switch (state) {
            case SEARCHING:  tickSearching();  break;
            case NAVIGATING: tickNavigating(); break;
            case WORKING:    tickWorking();    break;
        }
    }

    // ---- SEARCHING ----

    private void tickSearching() {
        long now = thrall.world.getTotalWorldTime();
        if (now - lastScanTime < SCAN_INTERVAL_TICKS && lastScanTime != 0) return;
        lastScanTime = now;

        // The home point is the deposit/recall target; the actual farm may be many blocks away.
        // Scan the thrall's current position first (so a thrall deployed onto the farm works locally),
        // then fall back to scanning around home (F-1) so a thrall standing OFF the field — e.g. parked
        // at its home chest — still finds and walks/teleports to farm work.
        BlockPos home = thrall.getHomePoint();
        if (home == null) return;

        if (searchAround(new BlockPos(thrall))) return;
        if (!home.equals(new BlockPos(thrall)) && searchAround(home)) return;

        thrall.setStatusText("No crops");
    }

    /**
     * Runs the five prioritized farm-task searches around {@code center}. Sets targetPos/targetKind/
     * state and returns true if a target was found; returns false (no state change) otherwise.
     */
    private boolean searchAround(BlockPos center) {
        // Phase 1 — find a mature crop to harvest
        BlockPos mature = findCropInRange(center, true);
        if (mature != null) {
            targetPos = mature;
            targetKind = TargetKind.HARVEST_CROP;
            expectedBlock = null;
            state = State.NAVIGATING;
            navTimer = 0;
            thrall.setStatusText("Harvesting...");
            return true;
        }

        // Phase 2 — sugar cane / cactus / melon / pumpkin
        HarvestTarget hb = findHarvestableBlock(center);
        if (hb != null) {
            targetPos = hb.pos;
            targetKind = TargetKind.HARVEST_BLOCK;
            expectedBlock = hb.block;
            state = State.NAVIGATING;
            navTimer = 0;
            thrall.setStatusText("Harvesting...");
            return true;
        }

        // Phase 3 — plant on empty farmland (prefer neighbour crop, fall back to any CROP seed)
        HarvestTarget plant = findEmptyFarmland(center);
        if (plant != null) {
            targetPos = plant.pos;
            targetKind = TargetKind.PLANT;
            expectedBlock = plant.block;
            state = State.NAVIGATING;
            navTimer = 0;
            thrall.setStatusText("Planting...");
            return true;
        }

        // Phase 4 — only if bone meal use is enabled, look for an immature crop to fertilize
        if (ModConfig.thrall.farmUseBoneMeal && hasBoneMeal()) {
            BlockPos immature = findCropInRange(center, false);
            if (immature != null) {
                targetPos = immature;
                targetKind = TargetKind.BONEMEAL;
                expectedBlock = null;
                state = State.NAVIGATING;
                navTimer = 0;
                thrall.setStatusText("Fertilizing...");
                return true;
            }
        }

        // Phase 5 — restore trampled farmland: re-till dirt blocks adjacent to existing farmland.
        // Bounded by tillsThisShift to avoid runaway restoration if a hostile mob keeps trampling.
        if (tillsThisShift < MAX_TILLS_PER_SHIFT && hasHoe()) {
            BlockPos tillable = findTrampledFarmland(center);
            if (tillable != null) {
                targetPos = tillable;
                targetKind = TargetKind.TILL;
                expectedBlock = null;
                state = State.NAVIGATING;
                navTimer = 0;
                thrall.setStatusText("Restoring...");
                return true;
            }
        }

        return false;
    }

    @Nullable
    private BlockPos findCropInRange(BlockPos center, boolean wantMature) {
        World world = thrall.world;
        int radius = ModConfig.thrall.farmRadius;
        int radSq = radius * radius;

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        BlockPos thrallPos = new BlockPos(thrall);

        for (int dy = -VERTICAL_RANGE; dy <= VERTICAL_RANGE; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radSq) continue;
                    int x = center.getX() + dx;
                    int y = center.getY() + dy;
                    int z = center.getZ() + dz;
                    mpos.setPos(x, y, z);
                    if (!world.isBlockLoaded(mpos)) continue;

                    IBlockState below = world.getBlockState(mpos.down());
                    if (!(below.getBlock() instanceof BlockFarmland)) continue;

                    IBlockState state = world.getBlockState(mpos);
                    boolean isMature = ThrallMaterialHelper.isMatureCrop(world, mpos, state);
                    boolean isCrop = state.getBlock() instanceof IGrowable;
                    if (wantMature && !isMature) continue;
                    if (!wantMature && (!isCrop || isMature)) continue;
                    // Don't filter by replant capability — any mature crop is worth harvesting
                    // even if we can't replant it (modded crops we don't have seeds for).

                    double distSq = thrallPos.distanceSq(mpos);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = mpos.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    /** Result of {@link #findHarvestableBlock} — pos + the block class snapshotted for re-validation on approach. */
    private static final class HarvestTarget {
        final BlockPos pos;
        final Block block;
        HarvestTarget(BlockPos pos, Block block) { this.pos = pos; this.block = block; }
    }

    /**
     * Scans for tall-plant and fruit blocks: sugar cane, cactus, melon, pumpkin. Tall plants
     * are only targeted when at least one segment exists below the candidate (so harvesting
     * never removes the regrowth base). Vertical scan extends past {@link #VERTICAL_RANGE} to
     * catch the upper segments of plants whose base sits at {@code center.y + VERTICAL_RANGE}.
     */
    @Nullable
    private HarvestTarget findHarvestableBlock(BlockPos center) {
        World world = thrall.world;
        int radius = ModConfig.thrall.farmRadius;
        int radSq = radius * radius;
        BlockPos thrallPos = new BlockPos(thrall);
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        HarvestTarget best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radSq) continue;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;

                // Walk the column low→high; for tall plants pick the lowest position whose
                // block-below is the same plant (i.e. base+1) so a single break drops the whole stack.
                for (int dy = -VERTICAL_RANGE; dy <= VERTICAL_RANGE + 2; dy++) {
                    int y = center.getY() + dy;
                    mpos.setPos(x, y, z);
                    if (!world.isBlockLoaded(mpos)) continue;

                    Block block = world.getBlockState(mpos).getBlock();
                    boolean column = (block == Blocks.REEDS || block == Blocks.CACTUS);
                    boolean fruit  = (block == Blocks.MELON_BLOCK || block == Blocks.PUMPKIN);

                    BlockPos hit = null;
                    if (column) {
                        Block below = world.getBlockState(mpos.down()).getBlock();
                        if (below == block) hit = mpos.toImmutable();
                    } else if (fruit) {
                        hit = mpos.toImmutable();
                    }

                    if (hit != null) {
                        double distSq = thrallPos.distanceSq(hit);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = new HarvestTarget(hit, block);
                        }
                        break; // one target per (x,z) column is enough
                    }
                }
            }
        }
        return best;
    }

    private boolean hasBoneMeal() {
        ThrallInventory inv = thrall.getThrallInventory();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (ThrallMaterialHelper.isBoneMeal(inv.getStackInSlot(i))) return true;
        }
        return false;
    }

    private int findBoneMealSlot() {
        ThrallInventory inv = thrall.getThrallInventory();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (ThrallMaterialHelper.isBoneMeal(inv.getStackInSlot(i))) return i;
        }
        return -1;
    }

    private boolean hasHoe() {
        return findHoeSlot() >= 0;
    }

    private int findHoeSlot() {
        ThrallInventory inv = thrall.getThrallInventory();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (ThrallMaterialHelper.isHoeItem(inv.getStackInSlot(i))) return i;
        }
        return -1;
    }

    /**
     * Looks for DIRT blocks adjacent to existing farmland — these are typically trampled/decayed
     * farmland tiles that should be re-tilled. Restricting the scan to dirt-with-farmland-neighbour
     * keeps the thrall from inventing brand-new fields out of random dirt or grass paths.
     */
    @Nullable
    private BlockPos findTrampledFarmland(BlockPos center) {
        World world = thrall.world;
        int radius = ModConfig.thrall.farmRadius;
        int radSq = radius * radius;
        BlockPos thrallPos = new BlockPos(thrall);

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dy = -VERTICAL_RANGE; dy <= VERTICAL_RANGE; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radSq) continue;
                    int x = center.getX() + dx;
                    int y = center.getY() + dy;
                    int z = center.getZ() + dz;
                    mpos.setPos(x, y, z);
                    if (!world.isBlockLoaded(mpos)) continue;

                    if (world.getBlockState(mpos).getBlock() != Blocks.DIRT) continue;
                    if (!world.isAirBlock(mpos.up())) continue;
                    if (!hasAdjacentFarmland(world, mpos)) continue;

                    double distSq = thrallPos.distanceSq(mpos);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = mpos.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    /** Cardinal-only farmland adjacency check. Diagonal neighbours don't count — they may belong to a separate plot. */
    private boolean hasAdjacentFarmland(World world, BlockPos pos) {
        for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.HORIZONTALS) {
            BlockPos n = pos.offset(face);
            if (!world.isBlockLoaded(n)) continue;
            if (world.getBlockState(n).getBlock() instanceof BlockFarmland) return true;
        }
        return false;
    }

    /**
     * Scans for empty farmland (block above is air) inside the farm radius and returns the nearest
     * one for which we can pick a viable seed. The crop block snapshotted here is later re-resolved
     * to a seed slot at finish time, so even if inventory shifts mid-walk we'll still try to plant.
     */
    @Nullable
    private HarvestTarget findEmptyFarmland(BlockPos center) {
        World world = thrall.world;
        int radius = ModConfig.thrall.farmRadius;
        int radSq = radius * radius;
        BlockPos thrallPos = new BlockPos(thrall);
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        HarvestTarget best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dy = -VERTICAL_RANGE; dy <= VERTICAL_RANGE; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz > radSq) continue;
                    int x = center.getX() + dx;
                    int y = center.getY() + dy;
                    int z = center.getZ() + dz;
                    mpos.setPos(x, y, z);
                    if (!world.isBlockLoaded(mpos)) continue;

                    if (!(world.getBlockState(mpos).getBlock() instanceof BlockFarmland)) continue;
                    if (!world.isAirBlock(mpos.up())) continue;

                    Block crop = pickCropForFarmland(mpos.toImmutable());
                    if (crop == null) continue;

                    double distSq = thrallPos.distanceSq(mpos);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = new HarvestTarget(mpos.toImmutable(), crop);
                    }
                }
            }
        }
        return best;
    }

    /**
     * Picks which crop block to plant on an empty farmland tile. Voting order:
     * <ol>
     *   <li>Plurality of crop blocks growing on the 4 cardinal-neighbour farmland tiles, IF we have a seed for it.</li>
     *   <li>Any item in inventory that is {@link IPlantable} with type Crop, whose getPlant resolves to a real block.</li>
     * </ol>
     * Returns {@code null} if neither path yields a plantable seed in inventory.
     */
    @Nullable
    private Block pickCropForFarmland(BlockPos farmlandPos) {
        World world = thrall.world;
        BlockPos plantPos = farmlandPos.up();

        // Tally neighbour crop types
        java.util.Map<Block, Integer> votes = new java.util.HashMap<Block, Integer>();
        for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.HORIZONTALS) {
            BlockPos n = farmlandPos.offset(face);
            if (!world.isBlockLoaded(n)) continue;
            if (!(world.getBlockState(n).getBlock() instanceof BlockFarmland)) continue;
            Block above = world.getBlockState(n.up()).getBlock();
            if (above instanceof IGrowable) {
                Integer prev = votes.get(above);
                votes.put(above, prev == null ? 1 : prev + 1);
            }
        }
        Block winner = null;
        int winnerVotes = 0;
        for (java.util.Map.Entry<Block, Integer> e : votes.entrySet()) {
            if (e.getValue() > winnerVotes) {
                winnerVotes = e.getValue();
                winner = e.getKey();
            }
        }

        if (winner != null) {
            int slot = ThrallMaterialHelper.findPlantableSeedSlot(
                    thrall.getThrallInventory(), world, plantPos, winner);
            if (slot >= 0) return winner;
        }
        return findAnyCropSeedBlock(plantPos);
    }

    /**
     * Walks inventory for any {@link IPlantable} whose getPlantType is {@code Crop}, and returns
     * the resolved plant block (so we can call {@link ThrallMaterialHelper#findPlantableSeedSlot}
     * with it later). Used as the "no neighbour" fallback when planting on isolated farmland.
     */
    @Nullable
    private Block findAnyCropSeedBlock(BlockPos plantPos) {
        World world = thrall.world;
        ThrallInventory inv = thrall.getThrallInventory();
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            Item item = s.getItem();
            if (!(item instanceof IPlantable)) continue;
            IPlantable plantable = (IPlantable) item;
            try {
                if (plantable.getPlantType(world, plantPos) != net.minecraftforge.common.EnumPlantType.Crop) continue;
                IBlockState planted = plantable.getPlant(world, plantPos);
                if (planted != null) return planted.getBlock();
            } catch (RuntimeException ignored) {
                // Modded IPlantables sometimes NPE without specific context — skip them.
            }
        }
        return null;
    }


    // ---- NAVIGATING ----

    private void tickNavigating() {
        if (targetPos == null) {
            state = State.SEARCHING;
            return;
        }

        // Validate target still satisfies the criteria we picked it for
        IBlockState state = thrall.world.getBlockState(targetPos);
        boolean stillValid;
        switch (targetKind) {
            case HARVEST_CROP:
                stillValid = ThrallMaterialHelper.isMatureCrop(thrall.world, targetPos, state);
                break;
            case HARVEST_BLOCK:
                stillValid = expectedBlock != null && state.getBlock() == expectedBlock;
                break;
            case BONEMEAL:
                stillValid = state.getBlock() instanceof IGrowable
                        && !ThrallMaterialHelper.isMatureCrop(thrall.world, targetPos, state);
                break;
            case TILL: {
                // Restore-only: must still be dirt with farmland next door. Grass is no longer accepted.
                Block b = state.getBlock();
                stillValid = b == Blocks.DIRT
                        && thrall.world.isAirBlock(targetPos.up())
                        && hasAdjacentFarmland(thrall.world, targetPos);
                break;
            }
            case PLANT:
                stillValid = expectedBlock != null
                        && state.getBlock() instanceof BlockFarmland
                        && thrall.world.isAirBlock(targetPos.up());
                break;
            default:
                stillValid = false;
        }
        if (!stillValid) {
            targetPos = null;
            this.state = State.SEARCHING;
            return;
        }

        // Advance the nav timer BEFORE the arrival short-circuit so the teleport fallback keeps
        // counting during borderline approaches. Previously it sat below the `return`, so a thrall
        // oscillating on the reach boundary never accumulated navTimer and never teleported in.
        navTimer++;

        // Distance to the block's horizontal centre (X/Z +0.5; Y unadjusted), matching the WORKING
        // drift check below. The old corner-based getDistanceSq(BlockPos) measured to the block's
        // integer corner, letting a −X/−Z diagonal approach satisfy arrival while the centre-based
        // WORKING check failed the same tick — a permanent NAVIGATING↔WORKING freeze. Both checks
        // now use the block's horizontal centre.
        double distSq = thrall.getDistanceSq(
                targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        if (distSq <= CLOSE_ENOUGH_SQ) {
            startWorking();
            return;
        }

        // Repath periodically — pathfinder needs time to settle, don't spam every tick
        if (thrall.getNavigator().noPath() || navTimer % REPATH_INTERVAL_TICKS == 0) {
            thrall.getNavigator().tryMoveToXYZ(
                    targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 0.8);
        }

        thrall.getLookHelper().setLookPosition(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                10.0F, (float) thrall.getVerticalFaceSpeed());

        // Stuck for too long — teleport in. Keeps fenced-off plots and weird geometry working.
        if (navTimer >= NAV_TIMEOUT_TICKS) {
            if (debugLogs()) LOG.info("[Thrall#{}] Farming nav timeout to {} — teleporting in",
                    thrall.getEntityId(), targetPos);
            teleportToTarget(targetPos);
            navTimer = 0;
            // Re-check distance; if we landed inside reach, transition to WORKING immediately.
            // Measured to the block's horizontal centre (X/Z +0.5; Y unadjusted) to match the
            // arrival + drift checks (avoids re-triggering the freeze right after a teleport
            // that lands on a diagonal).
            if (thrall.getDistanceSq(
                    targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5) <= CLOSE_ENOUGH_SQ) {
                startWorking();
            }
        }
    }

    /**
     * Last-resort teleport when pathfinding can't reach the target — picks an air column adjacent
     * to the target so the thrall doesn't spawn inside a crop. Same Enderman whoosh + smoke pair
     * as the rest of the thrall's teleports.
     */
    private void teleportToTarget(BlockPos target) {
        BlockPos standing = pickStandingSpot(target);
        World world = thrall.world;
        thrall.playTeleportSound();
        world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE,
                thrall.posX, thrall.posY + 1.0, thrall.posZ, 0.0, 0.0, 0.0);
        thrall.setPositionAndUpdate(standing.getX() + 0.5, standing.getY(), standing.getZ() + 0.5);
        world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE,
                thrall.posX, thrall.posY + 1.0, thrall.posZ, 0.0, 0.0, 0.0);
        thrall.playTeleportSound();
        thrall.getNavigator().clearPath();
    }

    /**
     * Picks a 1x2 air column adjacent to {@code target} (same Y) so the thrall has somewhere
     * to stand without trampling a neighboring crop. Falls back to the target itself if every
     * neighbor is occupied — the worst case is the thrall briefly destroys one extra crop on landing.
     */
    private BlockPos pickStandingSpot(BlockPos target) {
        World world = thrall.world;
        for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.HORIZONTALS) {
            BlockPos candidate = target.offset(face);
            if (world.isAirBlock(candidate) && world.isAirBlock(candidate.up())) {
                return candidate;
            }
        }
        return target;
    }

    // ---- WORKING ----

    private void startWorking() {
        if (targetPos == null) return;
        if (targetKind == TargetKind.HARVEST_CROP || targetKind == TargetKind.HARVEST_BLOCK) {
            IBlockState s = thrall.world.getBlockState(targetPos);
            float hardness = s.getBlockHardness(thrall.world, targetPos);
            workTicksRequired = Math.max(MIN_WORK_TICKS, (int) (Math.max(0.0F, hardness) * WORK_SPEED_MULT));
        } else {
            // Bone meal and tilling are instant actions — just enough delay for one arm swing
            workTicksRequired = MIN_WORK_TICKS;
        }
        workTicks = 0;
        state = State.WORKING;
    }

    private void tickWorking() {
        if (targetPos == null) {
            state = State.SEARCHING;
            return;
        }

        // Drifted out of range (pushed by mob, blown up, owner moved a piston) — re-navigate
        // instead of spamming tryMoveToXYZ every tick. NAVIGATING already handles approach + teleport fallback.
        double distSq = thrall.getDistanceSq(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        if (distSq > WORK_RANGE_SQ) {
            if (targetKind == TargetKind.HARVEST_CROP || targetKind == TargetKind.HARVEST_BLOCK) {
                thrall.world.sendBlockBreakProgress(thrall.getEntityId(), targetPos, -1);
            }
            state = State.NAVIGATING;
            navTimer = 0;
            return;
        }

        // In range — stand still while we work.
        if (!thrall.getNavigator().noPath()) {
            thrall.getNavigator().clearPath();
        }

        thrall.getLookHelper().setLookPosition(
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                10.0F, (float) thrall.getVerticalFaceSpeed());

        if (workTicks % 5 == 0) thrall.swingArm(EnumHand.MAIN_HAND);
        workTicks++;

        if (targetKind == TargetKind.HARVEST_CROP || targetKind == TargetKind.HARVEST_BLOCK) {
            int progress = Math.min(9, (int) ((float) workTicks / workTicksRequired * 10.0F));
            thrall.world.sendBlockBreakProgress(thrall.getEntityId(), targetPos, progress);
        }

        if (workTicks >= workTicksRequired) {
            BlockPos finished = targetPos;
            TargetKind finishedKind = targetKind;
            switch (targetKind) {
                case HARVEST_CROP:  finishHarvestCrop();  break;
                case HARVEST_BLOCK: finishHarvestBlock(); break;
                case BONEMEAL:      finishBoneMeal();     break;
                case TILL:          finishTill();         break;
                case PLANT:         finishPlant();        break;
            }
            if (finished != null && (finishedKind == TargetKind.HARVEST_CROP || finishedKind == TargetKind.HARVEST_BLOCK)) {
                thrall.world.sendBlockBreakProgress(thrall.getEntityId(), finished, -1);
            }
            targetPos = null;
            expectedBlock = null;
            state = State.SEARCHING;
            lastScanTime = 0; // immediate rescan to chain harvests
        }
    }

    private void finishHarvestCrop() {
        World world = thrall.world;
        BlockPos pos = targetPos;
        if (pos == null) return;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (!ThrallMaterialHelper.isMatureCrop(world, pos, state)) return;

        world.playEvent(2001, pos, Block.getStateId(state));

        NonNullList<ItemStack> drops = NonNullList.create();
        block.getDrops(drops, world, pos, state, 0);
        for (ItemStack drop : drops) {
            boolean added = thrall.getThrallInventory().addItemStackToInventory(drop);
            if (!added) {
                net.minecraft.entity.item.EntityItem ei = new net.minecraft.entity.item.EntityItem(
                        world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                ei.setPickupDelay(10);
                world.spawnEntity(ei);
            }
        }
        world.setBlockToAir(pos);

        // Replant if we have any IPlantable seed for this crop (vanilla or modded).
        // Use IPlantable.getPlant(world, pos) for the planted state — block.getDefaultState()
        // is wrong for modded crops whose plant state isn't the block's default (e.g. PamHC,
        // some Roots/Botania plants), and for vanilla seeds it returns the same age-0 state anyway.
        int seedSlot = ThrallMaterialHelper.findPlantableSeedSlot(
                thrall.getThrallInventory(), world, pos, block);
        if (seedSlot >= 0) {
            ItemStack seedStack = thrall.getThrallInventory().getStackInSlot(seedSlot);
            IBlockState plantState = resolvePlantState(seedStack, pos, block);
            if (plantState != null) {
                seedStack.shrink(1);
                if (seedStack.isEmpty()) {
                    thrall.getThrallInventory().setInventorySlotContents(seedSlot, ItemStack.EMPTY);
                }
                world.setBlockState(pos, plantState, 3);
            }
        }
    }

    /**
     * Resolves the block state to set for a replant. Prefers {@link IPlantable#getPlant(net.minecraft.world.IBlockAccess, BlockPos)}
     * because vanilla {@code Items.WHEAT_SEEDS}, {@code Items.CARROT}, etc. all implement it,
     * and modded plants frequently override it to return a non-default state. Falls back to
     * {@code block.getDefaultState()} for the rare seed item that isn't IPlantable but was still
     * matched by the vanilla fast-path mapping.
     */
    @Nullable
    private IBlockState resolvePlantState(ItemStack seedStack, BlockPos pos, Block cropBlock) {
        Item seedItem = seedStack.getItem();
        if (seedItem instanceof IPlantable) {
            try {
                IBlockState planted = ((IPlantable) seedItem).getPlant(thrall.world, pos);
                if (planted != null) return planted;
            } catch (RuntimeException ignored) {
                // Some modded IPlantables NPE without specific world/pos context — fall through.
            }
        }
        return cropBlock.getDefaultState();
    }

    /**
     * Harvest a sugar cane / cactus / melon / pumpkin block. No replant — sugar cane and cactus
     * regrow from the unharvested base block, and melon/pumpkin stems regrow fruits automatically.
     * Breaking the lowest sugar cane / cactus block above the base cascades drops upward via
     * vanilla support-check logic, so a single break harvests the entire stack.
     */
    private void finishHarvestBlock() {
        World world = thrall.world;
        BlockPos pos = targetPos;
        if (pos == null) return;
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (expectedBlock != null && block != expectedBlock) return;

        world.playEvent(2001, pos, Block.getStateId(state));

        NonNullList<ItemStack> drops = NonNullList.create();
        block.getDrops(drops, world, pos, state, 0);
        for (ItemStack drop : drops) {
            boolean added = thrall.getThrallInventory().addItemStackToInventory(drop);
            if (!added) {
                net.minecraft.entity.item.EntityItem ei = new net.minecraft.entity.item.EntityItem(
                        world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                ei.setPickupDelay(10);
                world.spawnEntity(ei);
            }
        }
        world.setBlockToAir(pos);
    }

    private void finishTill() {
        BlockPos pos = targetPos;
        if (pos == null) return;
        World world = thrall.world;
        if (world.getBlockState(pos).getBlock() != Blocks.DIRT) return;
        if (!world.isAirBlock(pos.up())) return;
        if (!hasAdjacentFarmland(world, pos)) return;

        int hoeSlot = findHoeSlot();
        if (hoeSlot < 0) return; // hoe consumed mid-walk

        ItemStack hoeStack = thrall.getThrallInventory().getStackInSlot(hoeSlot);
        // Damage the hoe by 1; if it breaks, clear the slot.
        if (hoeStack.attemptDamageItem(1, thrall.getRNG(), null)) {
            thrall.getThrallInventory().setInventorySlotContents(hoeSlot, ItemStack.EMPTY);
            world.playSound(null, thrall.posX, thrall.posY, thrall.posZ,
                    SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.NEUTRAL, 0.8F, 0.8F + world.rand.nextFloat() * 0.4F);
        }

        world.setBlockState(pos, Blocks.FARMLAND.getDefaultState(), 3);
        world.playSound(null, pos, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0F, 1.0F);
        tillsThisShift++;
    }

    private void finishBoneMeal() {
        BlockPos pos = targetPos;
        if (pos == null) return;
        int slot = findBoneMealSlot();
        if (slot < 0) return;
        ItemStack stack = thrall.getThrallInventory().getStackInSlot(slot);

        // Use vanilla forge ItemDye.applyBonemeal so any IGrowable works.
        boolean used = net.minecraft.item.ItemDye.applyBonemeal(stack, thrall.world, pos);
        if (used) {
            if (stack.isEmpty()) {
                thrall.getThrallInventory().setInventorySlotContents(slot, ItemStack.EMPTY);
            }
            thrall.world.playEvent(2005, pos, 0);
        }
    }

    /**
     * Plants a seed on empty farmland. Re-resolves the seed slot at finish time because
     * inventory state may have shifted while the thrall was walking. Falls through silently
     * if the seed is gone — the next scan cycle will pick up the still-empty farmland.
     */
    private void finishPlant() {
        BlockPos pos = targetPos;
        if (pos == null || expectedBlock == null) return;
        World world = thrall.world;
        if (!(world.getBlockState(pos).getBlock() instanceof BlockFarmland)) return;
        BlockPos plantPos = pos.up();
        if (!world.isAirBlock(plantPos)) return;

        int seedSlot = ThrallMaterialHelper.findPlantableSeedSlot(
                thrall.getThrallInventory(), world, plantPos, expectedBlock);
        if (seedSlot < 0) return;

        ItemStack seedStack = thrall.getThrallInventory().getStackInSlot(seedSlot);
        IBlockState plantState = resolvePlantState(seedStack, plantPos, expectedBlock);
        if (plantState == null) return;

        seedStack.shrink(1);
        if (seedStack.isEmpty()) {
            thrall.getThrallInventory().setInventorySlotContents(seedSlot, ItemStack.EMPTY);
        }
        world.setBlockState(plantPos, plantState, 3);
    }
}
