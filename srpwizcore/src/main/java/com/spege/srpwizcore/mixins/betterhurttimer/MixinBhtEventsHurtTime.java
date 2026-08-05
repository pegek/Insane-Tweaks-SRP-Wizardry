package com.spege.srpwizcore.mixins.betterhurttimer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.srpwizcore.api.WhtIFrames;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * Scales WorseHurtTimer's melee cooldown by the victim's i-frame multiplier.
 *
 * <p>{@code Events.getHurtTime(target, attacker)} returns how many ticks the attacker must wait
 * before it may hit this target again; {@code Events.onEntityAttack} cancels the
 * {@code LivingAttackEvent} until then. Injecting at RETURN covers both of its branches: the
 * attack-speed one ({@code getCoolPeriod}) taken when {@code canSwing(attacker)} holds, and the
 * {@code getHurtResistantTime(target)} one taken otherwise.
 *
 * <p>Measured in this pack on 2026-08-05: {@code canSwing} returned {@code false} 314 times and
 * {@code true} never, a zombie holding an iron sword included, and WorseHurtTimer's
 * {@code "Checking the Cooldown Period"} line — logged only inside the {@code canSwing} branch —
 * never appeared. Its {@code "No try catch error"} line appeared on every call, so the
 * {@code ticksSinceLastSwing} reflection lookup succeeds and this is not a swallowed exception.
 * The attack-speed branch is therefore dead here and all melee flows through
 * {@code getHurtResistantTime}. Why a sword fails the {@code generic.attackSpeed} check is
 * unresolved — some other mod in the pack most likely rewrites weapon attributes. RETURN stays
 * the right injection point precisely because it does not care which branch ran: if that ever
 * flips, this mixin keeps working unchanged.
 *
 * <p>The multiplier is computed from the <em>target</em>, so the call from
 * {@code Events.lambda$onPlayerAttack$3}, which passes (mob, player), correctly gives a player
 * attacking a mob no bonus. Do not add an attacker-side check — it would be wrong.
 */
@Mixin(targets = "arekkuusu.betterhurttimer.common.Events", remap = false)
public class MixinBhtEventsHurtTime {

    @Inject(
            method = "getHurtTime(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;)I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private static void srpwizcore$scaleHurtTime(Entity target, Entity attacker,
            CallbackInfoReturnable<Integer> cir) {
        if (!SrpWizCoreConfig.whtCompat.enabled) {
            return;
        }
        if (!(target instanceof EntityLivingBase)) {
            return;
        }
        final float multiplier = WhtIFrames.getMultiplier((EntityLivingBase) target);
        if (multiplier == 1.0F) {
            return;
        }
        cir.setReturnValue(Integer.valueOf((int) (cir.getReturnValueI() * multiplier)));
    }
}
