package com.spege.insanetweaks.mixins.worldgen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.worldgen.sanctum.SanctumDungeonGuard;

import net.minecraft.world.World;

/**
 * Doomlike Dungeons plans each dungeon around a centre chunk chosen by isDungeonCenter(world, seed,
 * chunkX, chunkZ, dimension); near a Sunken Arcane Sanctum no chunk qualifies, so no dungeon is
 * planned into it. Vetoing the centre (not the per-chunk build) keeps other dungeons whole.
 * String target and only vanilla types in the handler, so no Doomlike jar is needed to compile.
 */
@Mixin(targets = "jaredbgreat.dldungeons.GenerationHandler", remap = false)
public abstract class MixinDoomlikeDungeonCenter {

    @Inject(method = "isDungeonCenter", at = @At("HEAD"), cancellable = true, remap = false)
    private void insanetweaks$keepClearOfSanctums(World world, long seed, int chunkX, int chunkZ, int dimension,
            CallbackInfoReturnable<Boolean> cir) {
        if (SanctumDungeonGuard.blocks(world, chunkX, chunkZ)) {
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
