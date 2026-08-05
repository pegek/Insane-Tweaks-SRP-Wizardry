package com.spege.srpwizcore.mixins.betterhurttimer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.srpwizcore.api.WhtIFrames;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import arekkuusu.betterhurttimer.api.capability.data.HurtSourceInfo;
import arekkuusu.betterhurttimer.api.event.PreLivingAttackEvent;

import net.minecraft.entity.EntityLivingBase;

/**
 * Scales the per-damage-source invincibility frames — the ones configured in
 * {@code betterhurttimer.cfg}'s {@code damageSource} table — by the victim's multiplier.
 *
 * <p>{@code HurtSourceData.trigger()} sets {@code tick = info.waitTime} and clears
 * {@code canApply}. {@code info} is shared globally between every entity, so it must not be
 * touched; {@code data} is per-entity, so scaling {@code data.tick} right after the original
 * call is the correct place. This is what makes the Cross Necklace work against arrows, magic,
 * fire and everything else in that table, which plain WorseHurtTimer never let it do.
 *
 * <p>The redirect handler takes the enclosing method's argument as a trailing parameter, which
 * is how the victim gets into scope. {@code onAttackEntityFromPre} contains exactly one
 * {@code trigger()} call.
 */
@Mixin(targets = "arekkuusu.betterhurttimer.common.Events", remap = false)
public class MixinBhtEventsSourceFrames {

    @Redirect(
            method = "onAttackEntityFromPre(Larekkuusu/betterhurttimer/api/event/PreLivingAttackEvent;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Larekkuusu/betterhurttimer/api/capability/data/HurtSourceInfo$HurtSourceData;trigger()V"),
            remap = false)
    private static void srpwizcore$scaleSourceFrames(HurtSourceInfo.HurtSourceData data,
            PreLivingAttackEvent event) {
        data.trigger();
        if (!SrpWizCoreConfig.whtCompat.enabled) {
            return;
        }
        final EntityLivingBase victim = event.getEntityLiving();
        if (victim == null) {
            return;
        }
        final float multiplier = WhtIFrames.getMultiplier(victim);
        if (multiplier == 1.0F) {
            return;
        }
        data.tick = (int) (data.tick * multiplier);
    }
}
