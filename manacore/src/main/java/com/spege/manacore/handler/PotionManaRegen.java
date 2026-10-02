package com.spege.manacore.handler;

import java.util.Collection;
import java.util.Map;

import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.BonusTable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

/**
 * Turns another mod's mana-regeneration potion into regeneration of the player's unified pool.
 *
 * <p>🚨 The effects this exists for do NOT feed the player - they refill an ITEM. Ancient
 * Spellcraft's {@code PotionManaRegeneration} calls {@code refillMana(EntityPlayer, ItemStack)},
 * topping up whatever mana-storing item is held; our own {@code meditation} trait does the same
 * with {@code rechargeMana(stack, 2)}. Once spells are paid for out of the player's pool, item
 * mana is a number nothing spends, so those effects became silently useless the moment this mod
 * was installed. This bridges them back to something that matters.
 *
 * <p>Config-driven rather than coded per mod, so a potion from a mod nobody here has heard of
 * works with one line and no build dependency. Nothing is done to STOP the original effect from
 * also topping up the item - it is harmless, and suppressing it would mean a mixin per mod for no
 * gain.
 *
 * <p>Note {@code ebwizardry:font_of_mana} is deliberately absent from the defaults. Despite the
 * name it has nothing to do with mana in EBW 4.3.19: its only listener divides the
 * {@code cooldown_upgrade} modifier, so it shortens spell cooldowns. Adding it here would invent
 * a mechanic rather than repair one.
 */
final class PotionManaRegen {

    private PotionManaRegen() {
    }

    /**
     * Mana per second granted by the player's currently active effects, or zero.
     *
     * <p>Iterates the player's active effects and looks each one up in the table, rather than
     * walking the table and querying each potion: a player normally has a handful of effects and
     * the table can grow to any length, so this way the cost tracks what is actually on the
     * player. The table is only parsed when there is at least one effect to match it against.
     */
    static double perSecond(EntityPlayer player) {
        if (!ManaCoreConfig.effects.enabled) {
            return 0.0D;
        }
        Collection<PotionEffect> active = player.getActivePotionEffects();
        if (active.isEmpty()) {
            return 0.0D;
        }

        Map<String, Double> table = BonusTable.parse(ManaCoreConfig.effects.potionRegen).bonuses();
        if (table.isEmpty()) {
            return 0.0D;
        }

        double total = 0.0D;
        for (PotionEffect effect : active) {
            ResourceLocation id = effect.getPotion().getRegistryName();
            if (id == null) {
                continue;
            }
            Double amount = table.get(id.toString());
            if (amount == null) {
                continue;
            }
            // getAmplifier() is 0 for a level I effect, so the multiplier is amplifier + 1 - level
            // I grants the configured amount, level II twice it, and so on.
            total += ManaCoreConfig.effects.scaleWithAmplifier
                    ? amount.doubleValue() * (effect.getAmplifier() + 1)
                    : amount.doubleValue();
        }
        return total;
    }
}
