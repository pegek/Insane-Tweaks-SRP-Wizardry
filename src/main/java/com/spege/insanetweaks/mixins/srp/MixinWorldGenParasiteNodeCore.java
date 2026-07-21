package com.spege.insanetweaks.mixins.srp;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Sanctuary Dome — stop SRP parasite nodes from generating inside a protected region, at the source.
 *
 * <p>Without this, a node whose heart lands in (or grows into) a sanctuary fights the cleanse: SRP's
 * {@code BlockBiomeCore} random-tick and {@code ParasiteEventWorld} repeatedly call the node-core
 * generator to lay down the structure, our cleanse reverts every block, SRP re-lays it next tick —
 * a block-update storm that lags the server. Vetoing the generator breaks the loop before any block
 * is written, so there is nothing for the cleanse to undo.
 *
 * <p>Target: {@code WorldGenParasiteNodeCore.func_180709_b(World, Random, BlockPos)} — the
 * {@code WorldGenerator.generate} override (SRG name, {@code remap = false}). Returning {@code false}
 * is the standard "did not generate here" contract every caller already handles, so no SRP retry
 * error path is triggered. Normal worldgen elsewhere is untouched — the veto only fires when the
 * generation anchor is inside an active sanctuary. Gated on {@code sanctuary.vetoNodeGeneration}.
 */
@Mixin(targets = "com.dhanantry.scapeandrunparasites.world.gen.feature.WorldGenParasiteNodeCore", remap = false)
public class MixinWorldGenParasiteNodeCore {

    @Inject(method = "func_180709_b", at = @At("HEAD"), cancellable = true, remap = false)
    private void insanetweaks$vetoNodeInSanctuary(World world, Random rand, BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ModConfig.sanctuary.vetoNodeGeneration) {
            return;
        }
        if (SanctuaryRegionHelper.isProtected(world, pos)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
