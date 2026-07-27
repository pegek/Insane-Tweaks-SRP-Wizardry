package com.spege.srpwizcore.mixins;

import com.spege.srpwizcore.spawnengine.SpawnEngine;

import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SpawnEngine E1 + E4 (spec: {@code notes/spawnengine_v1_spec_2026-07-27.md} in the DEv 1.2
 * instance).
 *
 * <p><b>HEAD</b>: in engine dimensions the cap decision is ours and runs before vanilla's own
 * comparison — which in dim 150 is bypassed by an unidentified mechanism (measured 2026-07-21:
 * 1600-2000 hostiles at a cap of 55). {@code setReturnValue(0)} skips the entire pass, both the
 * chunk/position scan and every entity construction, which is the 39.1%-of-tick storm source.
 * The gate never depends on the method's internals, so the bypass cannot defeat it.
 *
 * <p><b>RETURN</b>: the return value is the number of entities the pass actually spawned —
 * verified with {@code javap -p -c} against
 * {@code forge-1.12.2-14.23.5.2860_mapped_snapshot_20171003-1.12.jar}: the per-pack counter is
 * added into the returned local at every successful {@code WorldServer.spawnEntity}, and that
 * local is what both the normal and the exception-path {@code ireturn} return. It is charged
 * against the refill token bucket (E4, the anti-infinite-horde rate limit). After a HEAD cancel
 * the pass reports 0, which is a no-op on the bucket — that is intentional, not a bug to patch.
 *
 * <p>Nobody else mixes into {@code WorldEntitySpawner} in this pack (verified: zero
 * {@code APPLY.*WorldEntitySpawner} lines in a full {@code cleanmix.log} session), so both
 * injection points are collision-free.
 *
 * <p>No fields on purpose: nothing to merge through {@code <clinit>} into the target, which is
 * the VerifyError class of bugs this repo has already paid for once. All logic lives in
 * {@code spawnengine.SpawnEngine}, outside the mixin package.
 */
@Mixin(WorldEntitySpawner.class)
public class MixinWorldEntitySpawner {

    @Inject(method = { "findChunksForSpawning", "func_77192_a" }, at = @At("HEAD"),
            cancellable = true, remap = false)
    private void srpwizcore$engineGate(WorldServer world, boolean spawnHostileMobs,
            boolean spawnPeacefulMobs, boolean spawnOnSetTickRate,
            CallbackInfoReturnable<Integer> cir) {
        if (SpawnEngine.gateFindChunks(world, spawnHostileMobs, spawnPeacefulMobs,
                spawnOnSetTickRate)) {
            cir.setReturnValue(Integer.valueOf(0));
        }
    }

    @Inject(method = { "findChunksForSpawning", "func_77192_a" }, at = @At("RETURN"),
            remap = false)
    private void srpwizcore$consumeRefillTokens(WorldServer world, boolean spawnHostileMobs,
            boolean spawnPeacefulMobs, boolean spawnOnSetTickRate,
            CallbackInfoReturnable<Integer> cir) {
        SpawnEngine.onPassCompleted(world, cir.getReturnValue().intValue());
    }
}
