package com.spege.insanetweaks.enchant;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.MmmmCategory;
import com.spege.insanetweaks.init.ModPotions;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Runtime for the {@link EnchantmentMmmm} enchantment: the whole effect fires once, when a player
 * finishes eating a food stack carrying it.
 *
 * <h3>What it does (port of UniqueEnchantments' Ambrosia)</h3>
 * <ol>
 * <li>Fills the hunger bar outright - {@code addStats(2000, 0)}, i.e. food to 20 with no saturation
 *     of its own. This lands <i>after</i> the food's normal healing, because
 *     {@code LivingEntityUseItemEvent.Finish} fires at the end of {@code onItemUseFinish}.</li>
 * <li>Applies {@code insanetweaks:nourished} for a duration driven by the eater's XP level, which
 *     keeps saturation pinned so hunger never starts draining again until it runs out.</li>
 * </ol>
 *
 * <h3>The duration formula</h3>
 * Upstream computes {@code BASE + ln(5^(1 + xpLevel * level)) * MULTIPLIER} through a cached
 * power-of-five table. This port evaluates the algebraically identical
 * {@code BASE + (1 + xpLevel * power) * ln(5) * MULTIPLIER} instead: same numbers, and it cannot
 * overflow. Raising 5 to the power of a large XP level produces {@code Double.POSITIVE_INFINITY}
 * long before anything is logged, which would come out of the {@code int} cast as
 * {@link Integer#MAX_VALUE}; the linear form just keeps scaling.
 *
 * <p>{@code power} is {@code level * powerPerLevel}, which is how a single tier here reproduces
 * upstream tier II - see {@link EnchantmentMmmm}. At the defaults (base 600, multiplier 40,
 * power 2) a level-30 player gets roughly 4500 ticks, about three and three-quarter minutes.
 *
 * <p>Registered unconditionally in {@code InsaneTweaksMod#init} and gated live on
 * {@code modules.enableMmmm} below, so the flag can be flipped without a restart. The enchantment
 * and the Nourished effect also register unconditionally - gating a registry object on a config
 * flag means turning the flag off deletes the entry from an existing world.
 *
 * <p>PLANNED: this enchantment will also be the hook for protecting a player's food against the
 * pack's still-unidentified "food rots or vanishes from the inventory" interaction. Nothing here
 * implements that yet - it needs the culprit identified first.
 */
public class MmmmHandler {

    /** {@code ln(5)}, the closed form of upstream's power-of-five table. */
    private static final double LN_5 = Math.log(5.0D);

    /** Upstream's magic "fill it completely" argument to {@code FoodStats.addStats}. */
    private static final int FILL_HUNGER = 2000;

    /** Vanilla refuses potion amplifiers beyond this; upstream clamps to the same value. */
    private static final int MAX_AMPLIFIER = 20;

    @SubscribeEvent
    public void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!ModConfig.modules.enableMmmm) {
            return;
        }
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world == null || player.world.isRemote) {
            return;
        }

        // Finish hands us a copy of the active stack taken before it shrank, so the enchantment
        // NBT is still on it.
        ItemStack stack = event.getItem();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        // Re-check the item type rather than trusting canApply: a command or another mod can write
        // the enchantment onto anything, and this port is food-only by design.
        if (!(stack.getItem() instanceof ItemFood)) {
            return;
        }

        int level = EnchantmentMmmm.getLevel(stack);
        if (level <= 0) {
            return;
        }

        MmmmCategory cfg = ModConfig.enchantments.mmmm;
        int power = level * cfg.powerPerLevel;
        double steps = 1.0D + (double) player.experienceLevel * power;
        long duration = (long) (cfg.baseDurationTicks + steps * LN_5 * cfg.durationMultiplier);
        if (cfg.maxDurationTicks > 0 && duration > cfg.maxDurationTicks) {
            duration = cfg.maxDurationTicks;
        }
        if (duration <= 0L) {
            return;
        }

        if (cfg.fillHungerBar) {
            player.getFoodStats().addStats(FILL_HUNGER, 0.0F);
        }
        if (ModPotions.NOURISHED != null) {
            // Amplifier is the effective power, matching upstream. Anything at or above 1 already
            // pins saturation to the cap, so this only changes how fast it tops back up (and the
            // roman numeral in the HUD, which is why it reads one higher than the enchant level).
            player.addPotionEffect(new PotionEffect(ModPotions.NOURISHED, (int) duration,
                    Math.min(MAX_AMPLIFIER, power)));
        }
    }
}
