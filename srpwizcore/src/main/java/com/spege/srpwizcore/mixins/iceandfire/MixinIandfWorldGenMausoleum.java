package com.spege.srpwizcore.mixins.iceandfire;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.alexthe666.iceandfire.structures.WorldGenMausoleum;
import com.spege.srpwizcore.util.IandfMausoleumGuard;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Loosens the Dread Mausoleum foundation check — see {@link IandfMausoleumGuard} for the
 * measured evidence (all-4-edges-opaque-at-exact-Y kills every attempt on sloped ground) and
 * the tolerant algorithm. Gate {@code iandfWorldgen.mausoleumFoundationTolerance}, read live
 * inside the guard; {@code 0} routes back to Ice&amp;Fire's own check, bit for bit.
 *
 * <p>{@code generate} is an override of the vanilla {@code WorldGenerator} method, hence the
 * dual dev/SRG selector; {@code checkIfCanGenAt} is Ice&amp;Fire's own name and needs none.
 *
 * <p>Second patch (1.7.4): Ice&amp;Fire's generate() returns {@code true} even when the
 * foundation check vetoed, so {@code StructureGenerator} recorded {@code lastMausoleum} and
 * its min-distance gate then suppressed every attempt around a structure that was never
 * built (observed live 2026-07-28: one veto silenced a whole region). The RETURN inject
 * consumes the veto flag the guard sets and forces the honest {@code false}, so vetoed
 * chunks keep rolling.
 */
@Mixin(value = WorldGenMausoleum.class, remap = false)
public class MixinIandfWorldGenMausoleum {

    @Redirect(
            method = { "generate", "func_180709_b" },
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/alexthe666/iceandfire/structures/WorldGenMausoleum;"
                            + "checkIfCanGenAt(Lnet/minecraft/world/World;"
                            + "Lnet/minecraft/util/math/BlockPos;II"
                            + "Lnet/minecraft/util/EnumFacing;)Z"),
            remap = false)
    private boolean srpwizcore$tolerantFoundation(WorldGenMausoleum self, World world,
            BlockPos pos, int xSize, int zSize, EnumFacing facing) {
        return IandfMausoleumGuard.check(self, world, pos, xSize, zSize, facing);
    }

    @Inject(method = { "generate", "func_180709_b" }, at = @At("RETURN"), cancellable = true,
            remap = false)
    private void srpwizcore$vetoedGenerateReturnsFalse(World world, Random rand, BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (IandfMausoleumGuard.lastCheckVetoed) {
            IandfMausoleumGuard.lastCheckVetoed = false;
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
