package com.spege.srpwizcore.mixins;

import com.spege.srpwizcore.util.OffThreadChunkGuard;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops chunk generation from happening on a thread other than the server thread
 * ({@code notes/crash_biomecache_race_2026-07-28.md}; all logic in
 * {@link OffThreadChunkGuard}).
 *
 * <p><b>Why this anchor.</b> {@code provideChunk} is the complete choke point: it is the only
 * method that generates, and {@code World.getChunk} ({@code func_72964_e}) is a four-instruction
 * delegate straight to it. Anchoring on {@code World} instead would both miss callers that go via
 * {@code world.getChunkProvider().provideChunk(x, z)} directly — OTG's pregenerator does — and put
 * this mixin on the single most heavily transformed class in the game, which is exactly what
 * broke EntityThreading's own {@code MixinWorld}.
 *
 * <p><b>HEAD</b> is what makes the cancel safe: {@code provideChunk} writes its result into
 * {@code id2ChunkMap} at offsets 118-130, so cancelling before any of it runs means the
 * substitute chunk is never published to the map and can never be handed to the server thread.
 *
 * <p><b>Collision surface</b> (verified against a full {@code cleanmix.log} session and the
 * transformer bytecode of every jar in the pack): Alfheim's {@code ChunkProviderServerMixin}
 * touches only {@code saveChunks} and {@code tick}; vintagefix's is a bare field initialiser
 * (it swaps {@code id2ChunkMap} for a {@code Long2ObjectOpenHashMap} subclass and injects
 * nothing); RoughlyEnoughIDs inserts a biome-array hook <em>after</em> the generator call inside
 * this method, leaving offset 0 untouched; RLTweaker's {@code PreventStructureRecreationPatch}
 * targets {@code loadChunkFromFile}. Nothing else injects here and nothing caches a chunk that
 * this method returns.
 *
 * <p>Names verified against {@code joined.tsrg} + {@code srg_to_snapshot_20171003-1.12.tsrg}:
 * {@code provideChunk(II)Lnet/minecraft/world/chunk/Chunk;} is {@code func_186025_d}. No
 * descriptor on the selector on purpose — the name is unique in the class, and a descriptor would
 * have to be duplicated onto the MCP form or that half of the pair would silently stop resolving.
 *
 * <p>No fields, no {@code @Shadow}: nothing to merge through {@code <clinit>} into the target
 * (the VerifyError class of bug this repo has already paid for once), and the two fields needed
 * are {@code public}, where {@code @Shadow} is the failure mode that keeps {@code MixinLocale}
 * from applying. The cast idiom below is remapped by {@code reobfJar}.
 */
@Mixin(ChunkProviderServer.class)
public class MixinChunkProviderServerThreadGuard {

    @Inject(method = { "provideChunk", "func_186025_d" }, at = @At("HEAD"), cancellable = true,
            remap = false)
    private void srpwizcore$guardOffThreadChunkGen(int x, int z,
            CallbackInfoReturnable<Chunk> cir) {
        Chunk safe = OffThreadChunkGuard.provide((ChunkProviderServer) (Object) this, x, z);
        if (safe != null) {
            cir.setReturnValue(safe);
        }
    }
}
