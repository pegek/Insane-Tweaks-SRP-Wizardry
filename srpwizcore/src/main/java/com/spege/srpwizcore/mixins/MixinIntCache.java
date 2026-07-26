package com.spege.srpwizcore.mixins;

import com.spege.srpwizcore.util.IntCacheState;

import net.minecraft.world.gen.layer.IntCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Make vanilla's {@code IntCache} thread-safe by giving every thread its own array pools.
 *
 * <p>{@code IntCache} is a static utility shared by the whole {@code GenLayer} chain. Its methods
 * are {@code synchronized}, so the pools never corrupt structurally — but
 * {@code resetIntCache()} recycles <em>every</em> in-use array globally. With cascading chunk
 * generation running on a worker thread and the server thread at the same time, one thread's
 * reset pulls the arrays out from under the other's GenLayer chain. Result: biome ints that are
 * not biome ints. Crash 2026-07-26 03:28, {@code Climate lookup failed climateOrdinal: 92} in
 * {@code GenLayerBiomeBOP} (BoP climate ordinals are 0–14). Full reasoning and the exact vanilla
 * semantics being preserved: {@link IntCacheState}.
 *
 * <p>All three methods are replaced wholesale via HEAD-cancel and delegate to
 * {@link IntCacheState}. That is safe precisely because every field of {@code IntCache} is
 * {@code private} — nothing outside the class can observe that the state moved. The vanilla
 * fields simply stay empty.
 *
 * <p>Two deliberate non-choices:
 * <ul>
 * <li><b>No config check in these handlers.</b> The toggle is a real application gate
 *     ({@code IntCacheMixinPlugin#shouldApplyMixin}), so it genuinely needs a restart. Reading
 *     the live config here as well would let someone flip it mid-worldgen, stranding the arrays
 *     a thread already holds in its thread-local "in use" list.</li>
 * <li><b>{@code ACC_SYNCHRONIZED} is left on the vanilla methods.</b> With per-thread pools the
 *     monitor is redundant, and the plugin could strip it in {@code postApply} — but that is a
 *     perf change, not a correctness one (the lock only covers pool bookkeeping, not the
 *     GenLayer work), and stripping a flag off a vanilla method other coremods may rely on is
 *     risk this fix does not need.</li>
 * </ul>
 *
 * <p>Early mixin (vanilla target) via the jar-manifest {@code MixinConfigs} route, in its own
 * config {@code mixins.srpwizcore.intcache.json} so the {@code plugin} gate cannot affect the
 * mixins in {@code mixins.srpwizcore.early.json}. {@code remap = false} + explicit SRG per
 * project convention. Bytecode verified with {@code javap -p -c} against
 * {@code forge-1.12.2-14.23.5.2860-srg.jar}; Cleanroom does not binpatch {@code IntCache}
 * (no entry in {@code binpatches.pack.lzma} 0.6.2–0.6.6) and no other jar in the pack touches it.
 *
 * <p>NOTE: 1.12.2 has no {@code clearCache()} — {@code IntCache} declares exactly
 * {@code getIntCache}, {@code resetIntCache} and {@code getCacheSizes}.
 *
 * <p>This class deliberately declares NO static fields, so Mixin has no {@code <clinit>} to merge
 * into {@code IntCache}. State and constants live in {@link IntCacheState}.
 */
@Mixin(IntCache.class)
public class MixinIntCache {

    @Inject(
            method = { "getIntCache", "func_76445_a" },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void srpwizcore$getIntCachePerThread(int size,
            CallbackInfoReturnable<int[]> cir) {
        cir.setReturnValue(IntCacheState.getIntCache(size));
    }

    @Inject(
            method = { "resetIntCache", "func_76446_a" },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void srpwizcore$resetIntCachePerThread(CallbackInfo ci) {
        IntCacheState.resetIntCache();
        ci.cancel();
    }

    @Inject(
            method = { "getCacheSizes", "func_85144_b" },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void srpwizcore$getCacheSizesPerThread(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(IntCacheState.getCacheSizes());
    }
}
