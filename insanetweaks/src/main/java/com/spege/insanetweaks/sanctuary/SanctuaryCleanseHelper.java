package com.spege.insanetweaks.sanctuary;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.SrpNativePurifyHelper;
import com.spege.insanetweaks.util.SrpPurificationHelper;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Reverts SRP-infested terrain back to natural blocks within a cylinder slice.
 *
 *  <p>Prefers SRP's own authoritative infested-&gt;vanilla map ({@link SrpNativePurifyHelper},
 *  R2) for terrain, and falls back to our heuristic {@link SrpPurificationHelper} when the native
 *  bridge is off/unavailable or a block is unmapped. Node cores (biomeheart/colonyheart/noderelay)
 *  are always removed to AIR via our heuristic, since SRP's terrain map does not cover them. */
public final class SanctuaryCleanseHelper {

    private SanctuaryCleanseHelper() {}

    /** Returns true if this position held infested SRP terrain and was reverted. */
    public static boolean tryCleanse(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);

        // Our heuristic result up front: it uniquely handles node cores/gore -> AIR.
        IBlockState ours = SrpPurificationHelper.isSrpInfested(state)
                ? SrpPurificationHelper.getPurifiedState(state) : null;
        boolean oursIsAir = ours != null && ours.getBlock() == Blocks.AIR;

        // R2: prefer SRP's authoritative terrain mapping, but never for the AIR (node-core) case.
        if (!oursIsAir && ModConfig.sanctuary.nativeBlockPurify && SrpNativePurifyHelper.isAvailable()) {
            SrpNativePurifyHelper.ensureMappingsLoaded(world);
            if (SrpNativePurifyHelper.isSrpPurifiable(state)) {
                IBlockState vanilla = SrpNativePurifyHelper.mapToVanilla(state);
                if (vanilla != null) {
                    world.setBlockState(pos, vanilla, 3);
                    return true;
                }
            }
        }

        if (ours != null) {
            world.setBlockState(pos, ours, 3);
            return true;
        }
        return false;
    }

    /**
     * Drains the connected body of {@code srparasites:deadblood} reachable from {@code origin},
     * up to {@code budget} cells, replacing every one with air.
     *
     * <p>This exists instead of a one-block swap in {@link #tryCleanse} because dead blood is a
     * non-finite {@link net.minecraftforge.fluids.BlockFluidClassic}: a cleared non-source cell
     * simply refills from its neighbours' quanta on the next fluid tick, so single-cell removal
     * makes no progress at all. Removing a whole connected cluster in one pass leaves every
     * surviving neighbour at quanta 0, with nothing to flow back from.
     *
     * <p>Blocks are cleared with flag {@code 2} - send to client, <b>no</b> neighbour notification.
     * Notifying would re-arm the fluid ticker on every cell we are about to delete anyway; any tick
     * already scheduled is dropped by vanilla once it finds the block changed.
     *
     * <p>The cluster is clipped to the dome. Source blocks outside it will flow back to the
     * boundary, which is intended - the dome edge is the boundary, and the cleanse cursor sweeps it
     * again on its next pass. Hitting {@code budget} mid-cluster is likewise fine: the remainder is
     * picked up on a later pass.
     *
     * @return how many cells were cleared
     */
    public static int drainDeadBlood(World world, BlockPos origin, int budget) {
        if (world == null || origin == null || budget <= 0) {
            return 0;
        }
        if (!SrpPurificationHelper.isDeadBlood(world.getBlockState(origin))) {
            return 0;
        }

        Deque<BlockPos> queue = new ArrayDeque<BlockPos>();
        Set<BlockPos> seen = new HashSet<BlockPos>();
        queue.add(origin);
        seen.add(origin);

        int drained = 0;
        while (!queue.isEmpty() && drained < budget) {
            BlockPos current = queue.poll();
            world.setBlockState(current, Blocks.AIR.getDefaultState(), 2);
            drained++;

            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos next = current.offset(facing);
                if (seen.contains(next) || !world.isBlockLoaded(next)) {
                    continue;
                }
                if (!SanctuaryRegionHelper.isProtected(world, next)) {
                    continue; // never drain outside the dome that paid for it
                }
                if (!SrpPurificationHelper.isDeadBlood(world.getBlockState(next))) {
                    continue;
                }
                seen.add(next);
                queue.add(next);
            }
        }
        return drained;
    }
}
