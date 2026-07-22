package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.SrpNativePurifyHelper;
import com.spege.insanetweaks.util.SrpPurificationHelper;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
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
}
