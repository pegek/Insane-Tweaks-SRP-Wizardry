package com.spege.insanetweaks.mixins.enchantsources;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.EnchantSourceGuard;

import net.minecraft.enchantment.Enchantment;

/**
 * Quest-gate, third-party source: Chance Cubes rewards.
 *
 * <p>{@code RewardsUtil.getRandomEnchantment()} and {@code getRandomEnchantmentAndLevel()} pick with
 * {@code Enchantment.getEnchantmentByID(rand.nextInt(Enchantment.REGISTRY.getKeys().size()))} — a
 * by-numeric-id lottery over the whole registry with no filtering whatsoever. Unlike the other
 * third-party rollers this one never calls {@code iterator()}, so it needs its own anchor: the
 * {@code getEnchantmentByID} lookup itself.
 *
 * <p>Rather than reroll (the RNG is not reachable from a redirect on a static one-arg method) we walk
 * forward through the id space until we land on something that is not quest-gated. The bias this adds
 * is one id slot on the rare occasions our enchantments are hit, which is meaningless for a
 * chance-cube prize and buys a bounded, allocation-free, RNG-free implementation.
 *
 * <p>Returning null is deliberately possible only if the walk exhausts the id space (every id blocked
 * or unmapped) — Chance Cubes already handles a null enchantment from this call, since
 * {@code getEnchantmentByID} returns null for any unmapped id and always could.
 *
 * <p>Gated on {@code chancecubes} by {@code LateMixinBooter}.
 */
@Mixin(targets = "chanceCubes.util.RewardsUtil", remap = false)
public class MixinChanceCubesEnchantReward {

    /** Registry ids are dense and small; this is a hard stop, not an expected number of steps. */
    private static final int ID_SCAN_LIMIT = 512;

    @Redirect(method = { "getRandomEnchantment", "getRandomEnchantmentAndLevel" },
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/enchantment/Enchantment;func_185262_c(I)"
                            + "Lnet/minecraft/enchantment/Enchantment;"),
            remap = false)
    private static Enchantment insanetweaks$skipQuestGatedId(int id) {
        Enchantment picked = Enchantment.getEnchantmentByID(id);
        if (!EnchantSourceGuard.isNaturallyBlocked(picked)) {
            return picked;
        }
        EnchantSourceGuard.logOnce(picked, "Chance Cubes reward");
        for (int step = 1; step < ID_SCAN_LIMIT; step++) {
            Enchantment next = Enchantment.getEnchantmentByID(id + step);
            if (next != null && !EnchantSourceGuard.isNaturallyBlocked(next)) {
                return next;
            }
        }
        return null;
    }
}
