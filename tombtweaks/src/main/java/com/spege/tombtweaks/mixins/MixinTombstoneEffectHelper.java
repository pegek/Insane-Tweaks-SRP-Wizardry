package com.spege.tombtweaks.mixins;

import java.util.Random;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.tombtweaks.effects.EffectPoolEntry;
import com.spege.tombtweaks.effects.EffectPoolId;
import com.spege.tombtweaks.effects.EffectPoolRegistry;

import net.minecraft.potion.PotionEffect;
import ovh.corail.tombstone.helper.Helper;

/**
 * Restricts Tombstone's shared random-effect funnel to our whitelist.
 *
 * <p>This overload is the one worth injecting into: it is where every path except the Magic Scroll
 * converges (Ankh of Prayer and Lollipop and Tablet of Cupidity through {@code addRandomPotion},
 * Blessing and Plague Bringer directly), and it is the last point at which the beneficial/harmful
 * polarity is still a plain {@code boolean}. One frame deeper, in the {@code Predicate} overload,
 * that information has already been folded into a lambda.
 *
 * <p>🚨 Deliberately <b>not</b> patching {@code isAllowedEffect} / {@code isBadEffect}. Those same
 * two filters are read by {@code MercyEffect}, {@code EnchantmentMagicSiphon} and the
 * {@code DeathHandler} behind the Scroll of Preservation — narrowing them would stop off-list
 * buffs being <i>preserved</i> on death, which is not what a rolling whitelist is meant to do.
 *
 * <p>The returned effect is built exactly the way Tombstone builds its own (verified against
 * {@code javap -c}): instant effects get a duration of 1, the amplifier comes from the caller's
 * own level function, and both booleans are true.
 */
@Mixin(targets = { "ovh.corail.tombstone.helper.EffectHelper" }, remap = false)
public abstract class MixinTombstoneEffectHelper {

    @Inject(method = "getRandomEffect(IZZLjava/util/function/Function;)Lnet/minecraft/potion/PotionEffect;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void tombtweaks$whitelistRandomEffect(int duration, boolean bad, boolean allowInstant,
            Function<Random, Integer> levelFunction, CallbackInfoReturnable<PotionEffect> cir) {
        if (!EffectPoolRegistry.isActive()) {
            return;
        }

        EffectPoolEntry entry = EffectPoolRegistry.pick(
                bad ? EffectPoolId.HARMFUL : EffectPoolId.BENEFICIAL, allowInstant);
        if (entry == null) {
            // Empty or fully filtered pool: leave the stock roll alone rather than hand back null,
            // which callers treat as "nothing happened".
            return;
        }

        int amplifier = entry.clampAmplifier(levelFunction.apply(Helper.RANDOM).intValue());
        cir.setReturnValue(new PotionEffect(entry.potion,
                entry.potion.isInstant() ? 1 : duration, amplifier, true, true));
    }
}
