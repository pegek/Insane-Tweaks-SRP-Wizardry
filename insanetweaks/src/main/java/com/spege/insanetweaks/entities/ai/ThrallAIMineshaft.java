package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallMode;
import net.minecraft.block.Block;
import net.minecraft.block.BlockOre;
import net.minecraft.block.BlockRedstoneOre;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * AI task: Mineshaft mode.
 *
 * Phase 1 — Spiral shaft: clears a 5x5 area layer-by-layer going down,
 * placing cobblestone stairs along the perimeter in a spiral pattern.
 * Stops at Y=5.
 *
 * Phase 2 — Strip mine: from the shaft bottom, digs a main corridor (NORTH)
 * with branch tunnels every 3 blocks (EAST + WEST), each 16 blocks long.
 * Includes ore detection (1 block each direction) and torch placement.
 *
 * Mining speed is tick-based, proportional to block hardness.
 * Uses world.getTotalWorldTime() for deterministic timing.
 *
 * Full state is persisted in the entity's NBT via writeToNBT/readFromNBT
 * so mining progress survives chunk unloads and relogs.
 */
@SuppressWarnings("null")
public class ThrallAIMineshaft extends EntityAIBase {

    private static final float MINING_SPEED_MULTIPLIER = 15.0F;
    private static final int MIN_MINING_TICKS = 3;
    private static final int GRID_SIZE = 5;
    private static final int BLOCKS_PER_LAYER = GRID_SIZE * GRID_SIZE;

    // Strip mine constants
    private static final int BRANCH_LENGTH = 16;
    private static final int TUNNEL_HEIGHT = 3; // mine 3 blocks high

    // Torch placement interval (every N blocks along a tunnel)
    private static final int TORCH_INTERVAL = 8;

    // Live config accessors — re-read every call so config changes apply without restart
    private static int minY()              { return ModConfig.thrall.labour.mineshaftDepthMin; }
    private static int mainTunnelLength()  { return ModConfig.thrall.labour.mineshaftStripLength; }
    private static int branchInterval()    { return ModConfig.thrall.labour.mineshaftBranchSpacing; }

    // Spiral staircase perimeter offsets (clockwise, relative to center)
    private static final int[][] SPIRAL_OFFSETS = {
            {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2},
            {2, -1}, {2, 0}, {2, 1}, {2, 2},
            {1, 2}, {0, 2}, {-1, 2}, {-2, 2},
            {-2, 1}, {-2, 0}, {-2, -1}
    };
    // Corner blocks (indices 4, 8, 12, 15) take the NEXT edge's facing instead of their own
    // edge's facing. This rotates the corner stair so its low step aligns with the side a climber
    // approaches from, smoothing the L-turn for navigator step-up checks.
    private static final EnumFacing[] SPIRAL_STAIR_FACINGS = {
            EnumFacing.WEST, EnumFacing.WEST, EnumFacing.WEST, EnumFacing.WEST, EnumFacing.NORTH,
            EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.EAST,
            EnumFacing.EAST, EnumFacing.EAST, EnumFacing.EAST, EnumFacing.SOUTH,
            EnumFacing.SOUTH, EnumFacing.SOUTH, EnumFacing.WEST
    };
    // Wall facing for a torch placed at the stair position (attached to the outer wall)
    private static final EnumFacing[] SPIRAL_WALL_FACINGS = {
            EnumFacing.SOUTH, EnumFacing.SOUTH, EnumFacing.SOUTH, EnumFacing.SOUTH, EnumFacing.WEST,
            EnumFacing.WEST, EnumFacing.WEST, EnumFacing.WEST, EnumFacing.NORTH,
            EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.EAST,
            EnumFacing.EAST, EnumFacing.EAST, EnumFacing.EAST
    };
    private static final int SPIRAL_LENGTH = SPIRAL_OFFSETS.length;

    private final EntityThrallMinion thrall;

    private enum State {
        INIT, NAVIGATING, MINING_LAYER, PLACING_STAIR,
        STRIP_MINING, MINING_ORE_VEIN, DONE
    }
    private State state = State.INIT;

    // Shaft state
    @Nullable private BlockPos shaftCenter;
    private int currentY;
    private int blockIndex;
    private int spiralIndex;

    // Strip mine state
    private int mainTunnelProgress;
    private int branchProgress;
    private boolean branchGoingEast;
    private boolean inBranch;
    private int stripBlockSubIndex;

    // Ore vein mining queue (detected ores adjacent to tunnel)
    private final Deque<BlockPos> oreQueue = new ArrayDeque<>();

    // Current block mining state
    @Nullable private BlockPos miningTarget;
    private int miningTicks;
    private int miningTicksRequired;

    // Track blocks mined for torch placement
    private int blocksSinceLastTorch;

    // Stuck detection — counts consecutive blocks that survived a break attempt.
    // Some mods/protections can prevent setBlockToAir from removing blocks.
    private int consecutiveStuckBlocks;
    private static final int MAX_STUCK_BLOCKS = 3;

    // Strip-mine / ore-vein pathing
    /** Squared distance at which the thrall is close enough to the work column to start mining. */
    private static final double STRIP_REACH_DIST_SQ = 4.0; // ~2 blocks
    /** How often to re-issue tryMoveToXYZ while pathing toward a column. */
    private static final int STRIP_REPATH_INTERVAL_TICKS = 20;
    /** After this many ticks of failed pathing, fallback to a direct teleport. */
    private static final int STRIP_NAV_TIMEOUT_TICKS = 100;
    /** Ticks since we started trying to reach the current strip-mine work position. Resets on arrival. */
    private int stripNavTimer;

    public ThrallAIMineshaft(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(3);
        if (debugLogs()) LOG.info("[Thrall#{}] ThrallAIMineshaft constructed", thrall.getEntityId());
    }

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallMine");
    private int debugTickCounter = 0;

    private static boolean debugLogs() {
        return ModConfig.client.enableThrallDebugLogs;
    }

    @Override
    public boolean shouldExecute() {
        boolean modeOk = thrall.getMode() == ThrallMode.MINESHAFT;
        boolean invFull = thrall.getThrallInventory().isFull();
        if (modeOk && invFull && debugLogs()) {
            LOG.warn("[Thrall#{}] shouldExecute=false — isFull=true! This should not happen with empty inv.",
                    thrall.getEntityId());
        }
        return modeOk && !invFull;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return thrall.getMode() == ThrallMode.MINESHAFT
                && !thrall.getThrallInventory().isFull()
                && state != State.DONE;
    }

    @Override
    public void startExecuting() {
        if (shaftCenter == null) {
            shaftCenter = new BlockPos(thrall);
            currentY = shaftCenter.getY() - 1;
            blockIndex = 0;
            spiralIndex = 0;
            mainTunnelProgress = 0;
            branchProgress = 0;
            branchGoingEast = true;
            inBranch = false;
            stripBlockSubIndex = 0;
            blocksSinceLastTorch = 0;
            // Skip NAVIGATING — mine the first layer in-place
            state = State.MINING_LAYER;
            if (debugLogs()) LOG.info("[Thrall#{}] startExecuting() — shaftCenter={} currentY={} starting MINING_LAYER",
                    thrall.getEntityId(), shaftCenter, currentY);
            thrall.setStatusText("Mining... (Y=" + currentY + ")");
        } else {
            boolean doStrip = currentY <= minY() && blockIndex >= BLOCKS_PER_LAYER;
            state = doStrip ? State.STRIP_MINING : State.MINING_LAYER;
            if (debugLogs()) LOG.info("[Thrall#{}] startExecuting() RESUME — shaftCenter={} currentY={} state={}",
                    thrall.getEntityId(), shaftCenter, currentY, state);
        }
    }

    @Override
    public void resetTask() {
        if (miningTarget != null) {
            thrall.world.sendBlockBreakProgress(thrall.getEntityId(), miningTarget, -1);
        }
        miningTarget = null;
        thrall.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        debugTickCounter++;
        if (debugLogs() && debugTickCounter % 60 == 0) {
            LOG.info("[Thrall#{}] tick={} state={} currentY={} blockIdx={} mineTarget={}",
                    thrall.getEntityId(), debugTickCounter, state, currentY, blockIndex, miningTarget);
        }

        // Safety: only the spiral-shaft phase needs to stay near shaftCenter (5x5 footprint).
        // Strip mining and ore-vein following naturally walk far away — they manage their own pathing.
        if (shaftCenter != null
                && (state == State.MINING_LAYER || state == State.PLACING_STAIR)) {
            double distSq = thrall.getDistanceSq(shaftCenter.getX() + 0.5,
                    thrall.posY, shaftCenter.getZ() + 0.5);
            if (distSq > 8.0 * 8.0) {
                if (debugTickCounter % 20 == 0) {
                    thrall.getNavigator().tryMoveToXYZ(
                            shaftCenter.getX() + 0.5, thrall.posY, shaftCenter.getZ() + 0.5, 0.9D);
                    if (debugLogs()) LOG.info("[Thrall#{}] Too far from shaft ({} blocks) — navigating back",
                            thrall.getEntityId(), String.format("%.1f", Math.sqrt(distSq)));
                }
                return; // don't mine until back at shaft
            }
        }

        switch (state) {
            case INIT:          startExecuting(); break;
            case NAVIGATING:    tickNavigating(); break;
            case MINING_LAYER:  tickMiningLayer(); break;
            case PLACING_STAIR: tickPlacingStair(); break;
            case STRIP_MINING:  tickStripMining(); break;
            case MINING_ORE_VEIN: tickMiningOreVein(); break;
            case DONE:
                thrall.setStatusText("Mining complete");
                break;
        }
    }

    // =========================================================================
    // PHASE 1: Spiral Shaft
    // =========================================================================

    // NAVIGATING is kept for potential future use (e.g. resuming after being pushed far away)
    private void tickNavigating() {
        // Immediately start mining — thrall digs in-place, no pathfinding needed for shaft
        if (debugLogs()) LOG.info("[Thrall#{}] tickNavigating() transitioning to MINING_LAYER at Y={}",
                thrall.getEntityId(), currentY);
        state = State.MINING_LAYER;
    }

    private void tickMiningLayer() {
        if (shaftCenter == null) { LOG.error("[Thrall#{}] tickMiningLayer — shaftCenter is NULL!", thrall.getEntityId()); return; }

        if (currentY <= minY()) {
            if (debugLogs()) LOG.info("[Thrall#{}] tickMiningLayer — reached Y={}, transitioning to STRIP_MINING",
                    thrall.getEntityId(), currentY);
            transitionToStripMining();
            return;
        }

        // Find next non-air block in current 5x5 layer
        while (blockIndex < BLOCKS_PER_LAYER) {
            int dx = (blockIndex % GRID_SIZE) - 2;
            int dz = (blockIndex / GRID_SIZE) - 2;
            BlockPos pos = new BlockPos(shaftCenter.getX() + dx, currentY, shaftCenter.getZ() + dz);

            IBlockState blockState = thrall.world.getBlockState(pos);

            // Seal liquids with cobblestone instead of skipping
            if (blockState.getMaterial().isLiquid()) {
                if (thrall.consumeCobblestone()) {
                    thrall.world.setBlockState(pos, net.minecraft.init.Blocks.COBBLESTONE.getDefaultState());
                    if (debugLogs()) LOG.info("[Thrall#{}] Sealed liquid at {} with cobblestone", thrall.getEntityId(), pos);
                }
                blockIndex++;
                continue;
            }

            if (!thrall.world.isAirBlock(pos)
                    && blockState.getBlockHardness(thrall.world, pos) >= 0) {
                if (miningTarget == null || !miningTarget.equals(pos)) {
                    miningTarget = pos;
                    miningTicks = 0;
                    float hardness = blockState.getBlockHardness(thrall.world, pos);
                    miningTicksRequired = Math.max(MIN_MINING_TICKS, (int) (hardness * MINING_SPEED_MULTIPLIER));
                    if (debugLogs()) LOG.info("[Thrall#{}] MINING_LAYER Y={} blockIdx={} target={} block={} ticks={}",
                            thrall.getEntityId(), currentY, blockIndex,
                            pos, blockState.getBlock().getRegistryName(), miningTicksRequired);
                }
                tickMiningBlock();
                return;
            }
            blockIndex++;
        }

        // Layer cleared
        if (debugLogs()) LOG.info("[Thrall#{}] MINING_LAYER Y={} cleared all {} blocks — placing stair",
                thrall.getEntityId(), currentY, BLOCKS_PER_LAYER);
        state = State.PLACING_STAIR;
    }

    private void tickMiningBlock() {
        if (miningTarget == null) { blockIndex++; return; }

        if (thrall.world.isAirBlock(miningTarget)) {
            thrall.world.sendBlockBreakProgress(thrall.getEntityId(), miningTarget, -1);
            miningTarget = null;
            blockIndex++;
            return;
        }

        thrall.getLookHelper().setLookPosition(
                miningTarget.getX() + 0.5, miningTarget.getY() + 0.5, miningTarget.getZ() + 0.5,
                10.0F, (float) thrall.getVerticalFaceSpeed());

        if (miningTicks % 4 == 0) thrall.swingArm(EnumHand.MAIN_HAND);
        miningTicks++;

        int progress = Math.min(9, (int) ((float) miningTicks / miningTicksRequired * 10.0F));
        thrall.world.sendBlockBreakProgress(thrall.getEntityId(), miningTarget, progress);

        if (miningTicks >= miningTicksRequired) {
            if (debugLogs()) LOG.info("[Thrall#{}] Breaking shaft block {} (Y={})",
                    thrall.getEntityId(), miningTarget, miningTarget.getY());
            BlockPos broken = miningTarget;
            breakBlock(broken);
            thrall.world.sendBlockBreakProgress(thrall.getEntityId(), broken, -1);
            miningTarget = null;
            blockIndex++;
            if (!handlePostBreak(broken)) return;
        }
    }

    private void tickPlacingStair() {
        if (shaftCenter == null) return;

        int si = spiralIndex % SPIRAL_LENGTH;
        BlockPos stairPos = new BlockPos(
                shaftCenter.getX() + SPIRAL_OFFSETS[si][0],
                currentY,
                shaftCenter.getZ() + SPIRAL_OFFSETS[si][1]);

        IBlockState stairState = Blocks.STONE_STAIRS.getDefaultState()
                .withProperty(BlockStairs.FACING, SPIRAL_STAIR_FACINGS[si])
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.BOTTOM);
        thrall.world.setBlockState(stairPos, stairState);

        if (debugLogs()) LOG.info("[Thrall#{}] Placed stair at {} (spiralIdx={}) — descending to Y={}",
                thrall.getEntityId(), stairPos, si, currentY - 1);

        spiralIndex++;
        currentY--;
        blockIndex = 0;

        // Place torch on the wall above the stair every 6 stairs
        blocksSinceLastTorch++;
        if (blocksSinceLastTorch >= 6) {
            BlockPos torchPos = stairPos.up();
            EnumFacing torchFacing = SPIRAL_WALL_FACINGS[si];
            IBlockState torchState = Blocks.TORCH.getDefaultState()
                    .withProperty(net.minecraft.block.BlockTorch.FACING, torchFacing);
            tryPlaceTorch(torchPos, torchState);
            blocksSinceLastTorch = 0;
        }

        if (currentY <= minY()) {
            transitionToStripMining();
        } else {
            state = State.MINING_LAYER;
            thrall.setStatusText("Mining... (Y=" + currentY + ")");
        }
    }

    private void transitionToStripMining() {
        state = State.STRIP_MINING;
        mainTunnelProgress = 0;
        branchProgress = 0;
        inBranch = false;
        branchGoingEast = true;
        stripBlockSubIndex = 0;
        blocksSinceLastTorch = 0;
        thrall.setStatusText("Strip mining... (Y=" + (minY() + 1) + ")");
    }

    // =========================================================================
    // PHASE 2: Strip Mining
    // =========================================================================

    /**
     * Strip mine pattern from shaft bottom:
     *
     *   Main corridor goes NORTH from shaft center.
     *   Every BRANCH_INTERVAL blocks, branch tunnels go EAST and WEST.
     *   Each tunnel is 1 wide x TUNNEL_HEIGHT high.
     *
     *        W ←←←←← + →→→→→ E   (branch at mainTunnelProgress=3)
     *                 |
     *        W ←←←←← + →→→→→ E   (branch at mainTunnelProgress=6)
     *                 |
     *                 |  ← main corridor (NORTH)
     *                 |
     *               [SHAFT]
     *
     * Ore detection: after mining each tunnel column, check 1 block on
     * each side (left, right, above, below) for ore blocks. Queue them.
     *
     * Torch placement: every TORCH_INTERVAL blocks along a tunnel,
     * place a torch on the ground if thrall has torches in inventory.
     */
    private void tickStripMining() {
        if (shaftCenter == null) return;

        // If we have queued ores from adjacent walls, mine them first
        if (!oreQueue.isEmpty()) {
            state = State.MINING_ORE_VEIN;
            tickMiningOreVein();
            return;
        }

        // Determine current target block
        BlockPos target = getStripMineTarget();
        if (target == null) {
            // Mining complete
            state = State.DONE;
            thrall.setStatusText("Mining complete");
            return;
        }

        IBlockState blockState = thrall.world.getBlockState(target);

        // Seal liquids with cobblestone instead of skipping
        if (blockState.getMaterial().isLiquid()) {
            if (thrall.consumeCobblestone()) {
                thrall.world.setBlockState(target, net.minecraft.init.Blocks.COBBLESTONE.getDefaultState());
                if (debugLogs()) LOG.info("[Thrall#{}] Sealed liquid at {} with cobblestone (strip)", thrall.getEntityId(), target);
            }
            advanceStripMine();
            return;
        }

        // Skip air and unbreakable blocks (e.g., bedrock)
        if (thrall.world.isAirBlock(target) || blockState.getBlockHardness(thrall.world, target) < 0) {
            advanceStripMine();
            return;
        }

        // Pathing: walk to the column floor before mining. Repath periodically; teleport if stuck.
        BlockPos basePos = getColumnBase();
        if (basePos != null && !isWithinReach(basePos)) {
            navigateToWorkPos(target, basePos);
            return; // wait until we're close enough
        }
        stripNavTimer = 0;

        // Mine the block
        if (miningTarget == null || !miningTarget.equals(target)) {
            // New target
            if (miningTarget != null) {
                thrall.world.sendBlockBreakProgress(thrall.getEntityId(), miningTarget, -1);
            }
            miningTarget = target;
            miningTicks = 0;
            float hardness = blockState.getBlockHardness(thrall.world, target);
            miningTicksRequired = Math.max(MIN_MINING_TICKS, (int) (hardness * MINING_SPEED_MULTIPLIER));
        }

        thrall.getLookHelper().setLookPosition(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
                10.0F, (float) thrall.getVerticalFaceSpeed());

        // Once in reach, stop the navigator so the thrall stands still while mining.
        if (!thrall.getNavigator().noPath()) {
            thrall.getNavigator().clearPath();
        }

        if (miningTicks % 4 == 0) thrall.swingArm(EnumHand.MAIN_HAND);
        miningTicks++;

        int progress = Math.min(9, (int) ((float) miningTicks / miningTicksRequired * 10.0F));
        thrall.world.sendBlockBreakProgress(thrall.getEntityId(), target, progress);

        if (miningTicks >= miningTicksRequired) {
            breakBlock(target);
            thrall.world.sendBlockBreakProgress(thrall.getEntityId(), target, -1);
            miningTarget = null;
            advanceStripMine();
            if (!handlePostBreak(target)) return;
        }
    }

    /**
     * After mining each complete column in the strip mine, detect adjacent ores
     * and try to place a torch.
     */
    private void onColumnComplete(BlockPos columnBase) {
        World world = thrall.world;
        int baseY = minY() + 1;

        // Ore detection: scan 1 block outward from the tunnel in each direction
        for (int dy = 0; dy < TUNNEL_HEIGHT; dy++) {
            BlockPos center = new BlockPos(columnBase.getX(), baseY + dy, columnBase.getZ());
            for (EnumFacing face : EnumFacing.values()) {
                BlockPos neighbor = center.offset(face);
                if (isOre(world.getBlockState(neighbor))) {
                    oreQueue.addLast(neighbor);
                }
            }
        }

        // Torch placement
        blocksSinceLastTorch++;
        if (blocksSinceLastTorch >= TORCH_INTERVAL) {
            tryPlaceTorch(new BlockPos(columnBase.getX(), baseY, columnBase.getZ()));
            blocksSinceLastTorch = 0;
        }
    }

    /** Mine queued ore blocks discovered by ore detection. */
    private void tickMiningOreVein() {
        if (oreQueue.isEmpty()) {
            state = State.STRIP_MINING;
            return;
        }

        BlockPos orePos = oreQueue.peekFirst();
        if (orePos == null || thrall.world.isAirBlock(orePos) || !isOre(thrall.world.getBlockState(orePos))) {
            oreQueue.pollFirst();
            return;
        }

        // Pathing toward the ore (vein following can wander away from the tunnel floor).
        BlockPos approach = findOreApproachPos(orePos);
        if (!isWithinReach(orePos) && approach != null) {
            navigateToWorkPos(orePos, approach);
            return;
        }
        stripNavTimer = 0;

        // Set up mining if needed
        if (miningTarget == null || !miningTarget.equals(orePos)) {
            if (miningTarget != null) {
                thrall.world.sendBlockBreakProgress(thrall.getEntityId(), miningTarget, -1);
            }
            miningTarget = orePos;
            miningTicks = 0;
            IBlockState blockState = thrall.world.getBlockState(orePos);
            float hardness = blockState.getBlockHardness(thrall.world, orePos);
            miningTicksRequired = Math.max(MIN_MINING_TICKS, (int) (hardness * MINING_SPEED_MULTIPLIER));
        }

        thrall.getLookHelper().setLookPosition(
                orePos.getX() + 0.5, orePos.getY() + 0.5, orePos.getZ() + 0.5,
                10.0F, (float) thrall.getVerticalFaceSpeed());

        if (!thrall.getNavigator().noPath()) {
            thrall.getNavigator().clearPath();
        }

        if (miningTicks % 4 == 0) thrall.swingArm(EnumHand.MAIN_HAND);
        miningTicks++;

        int progress = Math.min(9, (int) ((float) miningTicks / miningTicksRequired * 10.0F));
        thrall.world.sendBlockBreakProgress(thrall.getEntityId(), orePos, progress);

        if (miningTicks >= miningTicksRequired) {
            breakBlock(orePos);
            thrall.world.sendBlockBreakProgress(thrall.getEntityId(), orePos, -1);
            miningTarget = null;
            oreQueue.pollFirst();

            // Check for more ore adjacent to this ore (vein following)
            for (EnumFacing face : EnumFacing.values()) {
                BlockPos adj = orePos.offset(face);
                if (isOre(thrall.world.getBlockState(adj)) && !oreQueue.contains(adj)) {
                    oreQueue.addLast(adj);
                }
            }
            if (!handlePostBreak(orePos)) return;
        }
    }

    /**
     * Returns the current block position to mine in the strip mine pattern.
     * Returns null when the entire pattern is complete.
     */
    @Nullable
    private BlockPos getStripMineTarget() {
        if (shaftCenter == null) return null;

        int baseY = minY() + 1; // mine one above bedrock floor

        if (inBranch) {
            EnumFacing branchDir = branchGoingEast ? EnumFacing.EAST : EnumFacing.WEST;
            BlockPos mainPos = shaftCenter.offset(EnumFacing.NORTH, mainTunnelProgress);
            BlockPos branchPos = mainPos.offset(branchDir, branchProgress + 1);
            return new BlockPos(branchPos.getX(), baseY + stripBlockSubIndex, branchPos.getZ());
        } else {
            if (mainTunnelProgress >= mainTunnelLength()) return null;
            BlockPos mainPos = shaftCenter.offset(EnumFacing.NORTH, mainTunnelProgress + 1);
            return new BlockPos(mainPos.getX(), baseY + stripBlockSubIndex, mainPos.getZ());
        }
    }

    /** Advance to next block/position in the strip mine pattern. */
    private void advanceStripMine() {
        stripBlockSubIndex++;

        // Still mining height of current column?
        if (stripBlockSubIndex < TUNNEL_HEIGHT) return;
        stripBlockSubIndex = 0;

        // Column complete — run ore detection + torch placement
        BlockPos columnBase = getColumnBase();
        if (columnBase != null) {
            onColumnComplete(columnBase);
        }

        if (inBranch) {
            branchProgress++;
            if (branchProgress >= BRANCH_LENGTH) {
                branchProgress = 0;
                if (branchGoingEast) {
                    branchGoingEast = false;
                } else {
                    inBranch = false;
                    branchGoingEast = true;
                    mainTunnelProgress++;
                }
            }
        } else {
            mainTunnelProgress++;
            if (mainTunnelProgress >= mainTunnelLength()) {
                return;
            }
            if (mainTunnelProgress > 0 && mainTunnelProgress % branchInterval() == 0) {
                inBranch = true;
                branchProgress = 0;
                branchGoingEast = true;
            }
        }
    }

    /** Get the base position (floor level) of the current tunnel column. */
    @Nullable
    private BlockPos getColumnBase() {
        if (shaftCenter == null) return null;
        int baseY = minY() + 1;
        if (inBranch) {
            EnumFacing branchDir = branchGoingEast ? EnumFacing.EAST : EnumFacing.WEST;
            BlockPos mainPos = shaftCenter.offset(EnumFacing.NORTH, mainTunnelProgress);
            BlockPos branchPos = mainPos.offset(branchDir, branchProgress);
            return new BlockPos(branchPos.getX(), baseY, branchPos.getZ());
        } else {
            BlockPos mainPos = shaftCenter.offset(EnumFacing.NORTH, mainTunnelProgress);
            return new BlockPos(mainPos.getX(), baseY, mainPos.getZ());
        }
    }

    // =========================================================================
    // Torch Placement
    // =========================================================================

    /** Try to place a floor torch at the given position. */
    private void tryPlaceTorch(BlockPos pos) {
        tryPlaceTorch(pos, Blocks.TORCH.getDefaultState());
    }

    /** Try to place a specific torch block state at the given position. */
    private void tryPlaceTorch(BlockPos pos, IBlockState torchState) {
        // Try to resupply torches if running low (< 8)
        int torchSlot = findTorchSlot();
        if (torchSlot < 0) {
            thrall.tryResupplyTorches(16);
            torchSlot = findTorchSlot();
        }
        if (torchSlot < 0) return; // no torches available even after resupply

        // Place torch in world
        if (thrall.world.isAirBlock(pos) && Blocks.TORCH.canPlaceBlockAt(thrall.world, pos)) {
            thrall.world.setBlockState(pos, torchState);
            // Consume 1 torch from inventory
            ItemStack torchStack = thrall.getThrallInventory().getStackInSlot(torchSlot);
            torchStack.shrink(1);
            if (torchStack.isEmpty()) {
                thrall.getThrallInventory().setInventorySlotContents(torchSlot, ItemStack.EMPTY);
            }
            if (debugLogs()) LOG.info("[Thrall#{}] Placed torch at {}", thrall.getEntityId(), pos);
        }
    }

    /** Find the first slot in thrall inventory containing torches. Returns -1 if none. */
    private int findTorchSlot() {
        for (int i = 0; i < thrall.getThrallInventory().getSizeInventory(); i++) {
            ItemStack stack = thrall.getThrallInventory().getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == Item.getItemFromBlock(Blocks.TORCH)) {
                return i;
            }
        }
        return -1;
    }

    // =========================================================================
    // Ore Detection
    // =========================================================================

    /**
     * Ore detection: prefers Forge OreDict entries (any name starting with "ore"),
     * falls back to vanilla BlockOre/BlockRedstoneOre instanceof for unregistered ores.
     * Last-resort registry-name substring kept for mods that skip both.
     */
    private static boolean isOre(IBlockState blockState) {
        if (blockState == null) return false;
        Block block = blockState.getBlock();
        if (block instanceof BlockOre || block instanceof BlockRedstoneOre) return true;

        // OreDict check: scan ore IDs of the block's pickblock stack for any "ore*" entry
        ItemStack pick = new ItemStack(block);
        if (!pick.isEmpty()) {
            for (int id : net.minecraftforge.oredict.OreDictionary.getOreIDs(pick)) {
                String name = net.minecraftforge.oredict.OreDictionary.getOreName(id);
                if (name != null && name.startsWith("ore") && name.length() > 3) return true;
            }
        }

        // Final fallback: registry-name heuristic
        ResourceLocation regName = block.getRegistryName();
        if (regName == null) return false;
        String path = regName.getResourcePath();
        return path.contains("ore") && !path.contains("more") && !path.contains("store");
    }

    // =========================================================================
    // Shared utilities
    // =========================================================================

    /**
     * Verifies the block was actually removed after breakBlock(). Some mods or world
     * protections can prevent setBlockToAir from succeeding (e.g., FTBUtilities chunk
     * claims, ProjectE warded blocks). When that happens, the AI silently skips past,
     * but if it happens N times in a row we abort to STAY mode so the player notices.
     *
     * @return true if mining can continue; false if the AI should abort (caller should return)
     */
    private boolean handlePostBreak(BlockPos pos) {
        if (!thrall.world.isAirBlock(pos)) {
            consecutiveStuckBlocks++;
            LOG.warn("[Thrall#{}] Block at {} survived break attempt (stuck #{} of {})",
                    thrall.getEntityId(), pos, consecutiveStuckBlocks, MAX_STUCK_BLOCKS);
            if (consecutiveStuckBlocks >= MAX_STUCK_BLOCKS) {
                LOG.warn("[Thrall#{}] Aborting mining — too many stuck blocks, switching to STAY", thrall.getEntityId());
                thrall.setMode(ThrallMode.STAY);
                thrall.setStatusText("Stuck — please help");
                consecutiveStuckBlocks = 0;
                return false;
            }
        } else {
            consecutiveStuckBlocks = 0; // success — reset
        }
        return true;
    }

    private void breakBlock(BlockPos pos) {
        World world = thrall.world;
        IBlockState blockState = world.getBlockState(pos);
        Block block = blockState.getBlock();

        // PRE-BREAK: seal any adjacent liquid sources with cobblestone
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos neighbor = pos.offset(face);
            IBlockState neighborState = world.getBlockState(neighbor);
            if (neighborState.getMaterial().isLiquid()) {
                if (thrall.consumeCobblestone()) {
                    world.setBlockState(neighbor, Blocks.COBBLESTONE.getDefaultState());
                    if (debugLogs()) LOG.info("[Thrall#{}] Sealed liquid at {} before breaking {}",
                            thrall.getEntityId(), neighbor, pos);
                }
            }
        }

        world.playEvent(2001, pos, Block.getStateId(blockState));

        NonNullList<ItemStack> drops = NonNullList.create();
        block.getDrops(drops, world, pos, blockState, 0);
        for (ItemStack drop : drops) {
            if (!thrall.getThrallInventory().addItemStackToInventory(drop)) {
                net.minecraft.entity.item.EntityItem entity =
                        new net.minecraft.entity.item.EntityItem(
                                world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
                entity.setPickupDelay(10);
                world.spawnEntity(entity);
            }
        }

        world.setBlockToAir(pos);
    }

    // =========================================================================
    // Strip-mine / ore-vein pathing helpers
    // =========================================================================

    /** True if the thrall is close enough to {@code workPos} to begin mining. */
    private boolean isWithinReach(BlockPos workPos) {
        return thrall.getDistanceSq(workPos.getX() + 0.5, workPos.getY(), workPos.getZ() + 0.5)
                <= STRIP_REACH_DIST_SQ;
    }

    /**
     * Drive the navigator toward {@code walkTo} (typically the column floor or an air space
     * adjacent to an ore). Re-issues the path every {@link #STRIP_REPATH_INTERVAL_TICKS} ticks
     * rather than spamming every tick — pathfinding needs time to compute. After
     * {@link #STRIP_NAV_TIMEOUT_TICKS} ticks of failed approach, falls back to a teleport
     * so the thrall doesn't hang on awkward geometry (corner of a branch, ore behind cobble, etc.).
     *
     * @param lookAt the block we ultimately care about (usually the mine target — for face-tracking)
     * @param walkTo where the thrall's body should stand to mine it
     */
    private void navigateToWorkPos(BlockPos lookAt, BlockPos walkTo) {
        thrall.getLookHelper().setLookPosition(
                lookAt.getX() + 0.5, lookAt.getY() + 0.5, lookAt.getZ() + 0.5,
                10.0F, (float) thrall.getVerticalFaceSpeed());

        stripNavTimer++;

        if (thrall.getNavigator().noPath() || stripNavTimer % STRIP_REPATH_INTERVAL_TICKS == 0) {
            thrall.getNavigator().tryMoveToXYZ(
                    walkTo.getX() + 0.5, walkTo.getY(), walkTo.getZ() + 0.5, 0.8D);
        }

        if (stripNavTimer >= STRIP_NAV_TIMEOUT_TICKS) {
            teleportToWorkPos(walkTo);
            stripNavTimer = 0;
        }
    }

    /**
     * Last-resort teleport when pathing fails. Uses the same Enderman whoosh + smoke pair
     * as the rest of the thrall's teleports to keep behavior visually consistent.
     */
    private void teleportToWorkPos(BlockPos walkTo) {
        World world = thrall.world;
        thrall.playTeleportSound();
        world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE,
                thrall.posX, thrall.posY + 1.0, thrall.posZ, 0.0, 0.0, 0.0);
        thrall.setPositionAndUpdate(walkTo.getX() + 0.5, walkTo.getY(), walkTo.getZ() + 0.5);
        world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE,
                thrall.posX, thrall.posY + 1.0, thrall.posZ, 0.0, 0.0, 0.0);
        thrall.playTeleportSound();
        thrall.getNavigator().clearPath();
        if (debugLogs()) LOG.info("[Thrall#{}] Strip-mine teleport (stuck) to {}",
                thrall.getEntityId(), walkTo);
    }

    /**
     * Picks a reasonable standing spot for mining {@code orePos}: the first air block
     * neighbour (preferring the one below, then orthogonal to the tunnel). Returns null
     * if no viable approach was found, in which case we just stand still and let the
     * mining timer finish from out-of-range (matches old behavior).
     */
    @Nullable
    private BlockPos findOreApproachPos(BlockPos orePos) {
        // Prefer below (we're usually walking on a tunnel floor at orePos.y - 1)
        BlockPos below = orePos.down();
        if (thrall.world.isAirBlock(below) && thrall.world.isAirBlock(below.up())) {
            return below;
        }
        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            BlockPos candidate = orePos.offset(face);
            if (thrall.world.isAirBlock(candidate) && thrall.world.isAirBlock(candidate.up())) {
                return candidate;
            }
        }
        return null;
    }

    /** Full reset — used when mode is explicitly switched away and back. */
    public void resetShaftState() {
        shaftCenter = null;
        currentY = 0;
        blockIndex = 0;
        spiralIndex = 0;
        mainTunnelProgress = 0;
        branchProgress = 0;
        inBranch = false;
        branchGoingEast = true;
        stripBlockSubIndex = 0;
        blocksSinceLastTorch = 0;
        state = State.INIT;
        miningTarget = null;
        oreQueue.clear();
        stripNavTimer = 0;
    }

    // =========================================================================
    // NBT Persistence — mining progress survives chunk unloads / relogs
    // =========================================================================

    /** Write all mineshaft state to the given NBT tag. Called from EntityThrallMinion. */
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("MineState", state.ordinal());
        if (shaftCenter != null) {
            tag.setInteger("MineCenterX", shaftCenter.getX());
            tag.setInteger("MineCenterY", shaftCenter.getY());
            tag.setInteger("MineCenterZ", shaftCenter.getZ());
        }
        tag.setInteger("MineCurrentY", currentY);
        tag.setInteger("MineBlockIdx", blockIndex);
        tag.setInteger("MineSpiralIdx", spiralIndex);
        tag.setInteger("MineMainProg", mainTunnelProgress);
        tag.setInteger("MineBranchProg", branchProgress);
        tag.setBoolean("MineBranchEast", branchGoingEast);
        tag.setBoolean("MineInBranch", inBranch);
        tag.setInteger("MineStripSub", stripBlockSubIndex);
        tag.setInteger("MineTorchCount", blocksSinceLastTorch);
    }

    /** Read all mineshaft state from the given NBT tag. Called from EntityThrallMinion. */
    public void readFromNBT(NBTTagCompound tag) {
        if (!tag.hasKey("MineState")) return;

        int stateOrd = tag.getInteger("MineState");
        State[] states = State.values();
        state = (stateOrd >= 0 && stateOrd < states.length) ? states[stateOrd] : State.INIT;

        if (tag.hasKey("MineCenterX")) {
            shaftCenter = new BlockPos(
                    tag.getInteger("MineCenterX"),
                    tag.getInteger("MineCenterY"),
                    tag.getInteger("MineCenterZ"));
        }
        currentY = tag.getInteger("MineCurrentY");
        blockIndex = tag.getInteger("MineBlockIdx");
        spiralIndex = tag.getInteger("MineSpiralIdx");
        mainTunnelProgress = tag.getInteger("MineMainProg");
        branchProgress = tag.getInteger("MineBranchProg");
        branchGoingEast = tag.getBoolean("MineBranchEast");
        inBranch = tag.getBoolean("MineInBranch");
        stripBlockSubIndex = tag.getInteger("MineStripSub");
        blocksSinceLastTorch = tag.getInteger("MineTorchCount");
    }
}
