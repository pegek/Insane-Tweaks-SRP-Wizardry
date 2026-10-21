package com.spege.insanetweaks.mixins.worldgen;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.insanetweaks.worldgen.sanctum.SanctumDungeonGuard;

import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;

/**
 * Roguelike Dungeons (Fnar Edition) builds a whole dungeon from one chunk's IWorldGenerator call;
 * near a Sunken Arcane Sanctum that call is skipped, so the dungeon never cuts into the sanctum.
 * String target and only vanilla types in the handler, so no Roguelike jar is needed to compile.
 */
@Mixin(targets = "com.github.fnar.roguelike.worldgen.DungeonGenerator1_12", remap = false)
public abstract class MixinRoguelikeDungeonGenerator {

    @Inject(method = "generate", at = @At("HEAD"), cancellable = true, remap = false)
    private void insanetweaks$keepClearOfSanctums(Random random, int chunkX, int chunkZ, World world,
            IChunkGenerator generator, IChunkProvider provider, CallbackInfo ci) {
        if (SanctumDungeonGuard.blocks(world, chunkX, chunkZ)) {
            ci.cancel();
        }
    }
}
