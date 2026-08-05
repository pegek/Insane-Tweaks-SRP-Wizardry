package com.spege.srpwizcore.mixins.betterhurttimer;

import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.srpwizcore.config.SrpWizCoreConfig;

import arekkuusu.betterhurttimer.api.capability.data.HurtSourceInfo;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/**
 * Replaces WorseHurtTimer's seeding of unconfigured damage sources with a fixed value.
 *
 * <p>{@code BHTAPI.get} does
 * {@code DAMAGE_SOURCE_INFO_MAP.computeIfAbsent(source.getDamageType(), HURT_SOURCE_INFO_FUNCTION.apply(entity))},
 * and that function builds {@code new HurtSourceInfo(name, false, entity.maxHurtResistantTime)}.
 * The map is a global static keyed by source name alone, so for any source missing from
 * {@code betterhurttimer.cfg}'s {@code damageSource} table — {@code explosion}, {@code drown},
 * {@code onFire}, {@code sting}, and every modded source from SRParasites, Electroblob's
 * Wizardry and CQR — the wait time is fixed for the whole session by whichever entity that
 * source happened to hit first. That is an upstream bug, and it would also make the per-source
 * multiplier land on a random base.
 *
 * <p>Sources that <em>are</em> configured were inserted by {@code BHTAPI.addSource} at config
 * load, so {@code computeIfAbsent} never fires for them and their tuning is untouched.
 *
 * <p>The redirect targets the {@code computeIfAbsent} call rather than the seeding lambda: the
 * lambda would have to be named {@code lambda$null$0}, which is a compiler-generated name.
 */
@Mixin(targets = "arekkuusu.betterhurttimer.api.BHTAPI", remap = false)
public class MixinBhtApiSourceSeed {

    @Redirect(
            method = "get(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/util/DamageSource;)Larekkuusu/betterhurttimer/api/capability/data/HurtSourceInfo$HurtSourceData;",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            remap = false)
    private static Object srpwizcore$seedDeterministic(Object2ObjectMap<Object, Object> map,
            Object key, Function<Object, Object> original) {
        final Object existing = map.get(key);
        if (existing != null) {
            return existing;
        }
        if (!SrpWizCoreConfig.whtCompat.enabled) {
            return map.computeIfAbsent(key, original);
        }
        final HurtSourceInfo info = new HurtSourceInfo((CharSequence) key, false,
                SrpWizCoreConfig.whtCompat.baseIFrameTicks);
        map.put(key, info);
        return info;
    }
}
