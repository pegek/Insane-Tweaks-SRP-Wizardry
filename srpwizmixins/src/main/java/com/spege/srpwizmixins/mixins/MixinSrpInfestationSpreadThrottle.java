package com.spege.srpwizmixins.mixins;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.dhanantry.scapeandrunparasites.block.BlockInfestedRemain;
import com.dhanantry.scapeandrunparasites.block.BlockParasiteSpreading;
import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Perf throttle for SRP infestation blocks. Their updateTick does full spread +
 * evolution-point work on every tick (~1100/s at node area ~550); with divisor N only
 * ~1/N run - the rest cancel at HEAD, skipping the whole call graph (block mutations and
 * the code=20 setTotalKills). randomTick delegates to updateTick in BlockParasiteSpreading
 * (verified in bytecode), so this single hook covers both the random-tick and
 * scheduled-tick paths without double-throttling. Gated on
 * {@code srpCompat.spreadThrottleDivisor} (1 = inert). No shadows - safe multi-target mixin.
 */
@Mixin(value = { BlockParasiteSpreading.class, BlockInfestedRemain.class }, remap = false)
public abstract class MixinSrpInfestationSpreadThrottle {

    @Inject(
            method = { "updateTick", "func_180650_b" },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void insanetweaks$throttleSpread(World world, BlockPos pos, IBlockState state,
            Random rand, CallbackInfo ci) {
        int n = SrpWizMixinsConfig.srpCompat.spreadThrottleDivisor;
        if (n > 1 && !world.isRemote && rand.nextInt(n) != 0) {
            ci.cancel();
        }
    }
}
