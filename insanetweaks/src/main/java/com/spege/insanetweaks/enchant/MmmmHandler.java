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
 * <li>Refills the hunger bar outright, {@code addStats(2000, 0)}, i.e. food to 20 with no saturation
 *     of its own. This lands <i>after</i> the food's normal healing, because
 *     {@code LivingEntityUseItemEvent.Finish} fires at the end of {@code onItemUseFinish}.</li>
 * <li>Applies {@code insanetweaks:nourished} for a short duration, which keeps saturation pinned so
 *     hunger does not start draining again until it runs out.</li>
 * </ol>
 *
 * <h3>The duration, and what used to be here</h3>
 * {@code duration = baseDurationTicks + (level - 1) * durationPerLevelTicks}, and the amplifier is
 * {@code level - 1}. Both depend on the enchantment level and on nothing else; at the defaults that
 * is Nourished I for 10 seconds at level I and Nourished II for 15 at level II.
 *
 * <p>🚨 This deliberately drops upstream's curve. Ambrosia computes
 * {@code BASE + ln(5^(1 + xpLevel * level)) * MULTIPLIER}, i.e. the duration grows linearly and
 * without bound in the <i>eater's XP level</i> - the port inherited that along with the tier-strength
 * machinery that only existed to soften it, and it meant a level-30 player stayed sated for nearly
 * four minutes and a level-100 player for eleven. Nothing about this enchantment is supposed to care
 * how much XP the eater happens to be carrying. Do not put an XP term back in.
 *
 * <p>Vanilla draws a potion amplifier one higher than its value, so amplifier {@code level - 1} is
 * what makes level I read "Nourished I". Anything at or above 0 already pins saturation to the cap;
 * the amplifier only changes how fast it tops back up, and the roman numeral in the HUD.
 *
 * <p>Registered unconditionally in {@code InsaneTweaksMod#init} and gated live on
 * {@code modules.enableMmmm} below, so the flag can be flipped without a restart. The enchantment
 * and the Nourished effect also register unconditionally - gating a registry object on a config
 * flag means turning the flag off deletes the entry from an existing world.
 *
 * <p>The enchantment is also the hook for protecting food against the pack's "food rots or vanishes
 * from the inventory" interaction, whose culprit turned out to be
 * {@code EntityParasiteBase.attackEntityAsMobFood}. See
 * {@link #protectsAgainstParasiteContamination} and the mixin that consults it.
 */
public class MmmmHandler {

    /**
     * Whether this stack is currently shielded from Scape and Run: Parasites' food contamination.
     *
     * <p>Kept here rather than on {@link EnchantmentMmmm} on purpose: this is runtime policy, read
     * from config on every call so both toggles stay live, while the enchantment itself is a
     * registry object that should not depend on config at all.
     *
     * <p>Deliberately does <i>not</i> re-check {@link ItemFood} - the caller has already
     * established that, and a non-food stack cannot carry the enchantment through any supported
     * route anyway.
     */
    public static boolean protectsAgainstParasiteContamination(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!ModConfig.modules.enableMmmm
                || !ModConfig.enchantments.mmmm.protectFromParasiteContamination) {
            return false;
        }
        return EnchantmentMmmm.getLevel(stack) > 0;
    }

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
        // Level, and only level. A stack can legitimately carry a level above maxLevel - Sentient
        // Codex boosts held items past getMaxLevel(), and lowering maxLevel later leaves old stacks
        // behind - so this is written to stay sane above the cap rather than to clamp to it.
        long duration = (long) cfg.baseDurationTicks
                + (long) (level - 1) * cfg.durationPerLevelTicks;
        if (duration <= 0L) {
            return;
        }
        if (duration > Integer.MAX_VALUE) {
            duration = Integer.MAX_VALUE;
        }

        if (cfg.fillHungerBar) {
            // Upstream's "just fill it" magic number; vanilla clamps whatever is already there.
            player.getFoodStats().addStats(FILL_HUNGER, 0.0F);
        }
        if (ModPotions.NOURISHED != null) {
            // level - 1, because vanilla draws the amplifier one higher: level I reads "Nourished I".
            player.addPotionEffect(new PotionEffect(ModPotions.NOURISHED, (int) duration,
                    Math.min(MAX_AMPLIFIER, Math.max(0, level - 1))));
        }
    }
}
