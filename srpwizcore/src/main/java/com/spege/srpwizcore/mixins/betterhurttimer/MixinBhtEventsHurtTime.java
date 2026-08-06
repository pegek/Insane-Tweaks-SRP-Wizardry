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
 * <p>Measured 2026-08-06 with the {@code canswing|} probe: for an attacking mob the held item is
 * irrelevant — an iron sword reports {@code speedAttr=yes} and bare hands {@code speedAttr=no},
 * yet both give {@code swingTicks=neg} and both produce the same cooldown (19). The failing
 * conjunct is {@code ticksSinceLastSwing >= 0}: only {@code EntityPlayer} maintains that counter
 * in 1.12.2, so {@code canSwing} is in effect a disguised "is this a player" test and the
 * attack-speed branch is meant for a <em>player</em> attacking. Incoming melee therefore always
 * takes {@code getHurtResistantTime(target)} — there is no path by which an armed attacker could
 * slip past this mixin. (An earlier note here called the branch outright dead in this pack; that
 * over-generalised from a sample containing only mob-on-player hits.) RETURN remains the right
 * injection point regardless: it does not care which branch ran.
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
        final int original = cir.getReturnValueI();
        final int scaled = multiplier == 1.0F ? original : (int) (original * multiplier);
        if (com.spege.srpwizcore.whtcompat.WhtDiag.ENABLED) {
            com.spege.srpwizcore.whtcompat.WhtDiag.recordMelee(target, attacker, original, scaled);
            com.spege.srpwizcore.whtcompat.WhtDiag.recordCanSwing(target, attacker);
        }
        if (scaled != original) {
            cir.setReturnValue(Integer.valueOf(scaled));
        }
    }
}
