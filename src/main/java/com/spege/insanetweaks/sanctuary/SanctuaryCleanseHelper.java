package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.util.SrpPurificationHelper;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Reverts SRP-infested terrain back to natural blocks within a cylinder slice.
 *  Delegates the infestation check and block mapping to
 *  {@link SrpPurificationHelper}, which already carries the real SRParasites
 *  registry-name/material heuristics used elsewhere (e.g. Task 6/7 shield). */
public final class SanctuaryCleanseHelper {

    private SanctuaryCleanseHelper() {}

    /** Returns true if this position held infested SRP terrain and was reverted. */
    public static boolean tryCleanse(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!SrpPurificationHelper.isSrpInfested(state)) {
            return false;
        }
        IBlockState purified = SrpPurificationHelper.getPurifiedState(state);
        if (purified == null) {
            return false;
        }
        world.setBlockState(pos, purified, 3);
        return true;
    }
}
