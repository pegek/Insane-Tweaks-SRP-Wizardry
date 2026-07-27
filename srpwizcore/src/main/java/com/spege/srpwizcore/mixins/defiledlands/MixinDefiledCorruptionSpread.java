package com.spege.srpwizcore.mixins.defiledlands;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.util.PerfGlueState;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * DefiledLands: every random block tick of a corrupted block calls
 * {@code CorruptionHelper.spread}, which does a biome lookup plus up to four
 * {@code CorruptionRecipes} map probes — 13.5% of dim-150 spike time once the spawn pipeline
 * was fixed (2026-07-27 re-profile, {@code notes/flare_dim150_POST_spawnengine_2026-07-27.md}),
 * and the mod has no config for the spread rate ({@code conversionRate} is unrelated;
 * {@code canSpread} reads only {@code confinedSpread} — verified with javap on 1.4.3).
 *
 * <p>The HEAD gate cancels a configurable percentage of spread calls BEFORE the biome/recipe
 * work, so it cuts the corruption pace and the tick cost by the same factor. Uses the random
 * block tick's own {@code Random} (server thread), no state of our own.
 *
 * <p>String target — no DefiledLands jar in libs, none needed. Gated on
 * {@code perfGlue.defiledCorruptionSpreadPct}, read live; 100 = untouched vanilla behaviour.
 */
@Mixin(targets = "lykrast.defiledlands.common.util.CorruptionHelper", remap = false)
public class MixinDefiledCorruptionSpread {

    @Inject(method = "spread", at = @At("HEAD"), cancellable = true, remap = false)
    private static void srpwizcore$throttleSpread(
            World world, BlockPos pos, IBlockState state, Random rand, CallbackInfo ci) {
        int pct = SrpWizCoreConfig.perfGlue.defiledCorruptionSpreadPct;
        if (pct >= 100) {
            return;
        }
        if (pct <= 0 || rand.nextInt(100) >= pct) {
            PerfGlueState.corruptionSpreadSkips++;
            ci.cancel();
        }
    }
}
