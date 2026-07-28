package com.spege.srpwizcore.mixins.iceandfire;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
 * Known quirk left alone: a failed generate() still returns {@code true} to
 * {@code StructureGenerator}, which then arms the min-distance gate around a mausoleum that
 * does not exist — with the tolerant check failures become rare enough that this stops
 * mattering, and fixing it would mean patching a second method for no measured gain.
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
}
