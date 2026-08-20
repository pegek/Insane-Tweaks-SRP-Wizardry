package com.spege.insanetweaks.util;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;

/**
 * How much mana a cast actually cost, which is how the Living Wand earns its evolution progress.
 *
 * <p>🚨 This is NOT compatibility code, despite where it used to live. It was carved out of
 * {@code PlayerManaCompat} when the player_mana integration was deleted, because deleting the whole
 * class would have silently stopped the Living Wand from evolving - the one consumer,
 * {@code WandEventHandler}, converts the value returned here into evolution points.
 *
 * <p>What is kept is the branch that ran when player_mana was absent, which is the branch that has
 * been running in the pack ever since the mod was disabled. The player_mana branch is gone with the
 * mod.
 */
public final class SpellManaAccounting {

    private SpellManaAccounting() {
    }

    /**
     * The cost multiplier actually applied to this cast - wand discounts, upgrades and any
     * modifier another mod set.
     */
    public static float getActualCostMultiplier(SpellModifiers modifiers) {
        if (modifiers == null) {
            return 1.0f;
        }
        return modifiers.get(SpellModifiers.COST);
    }

    /**
     * Mana consumed by one cast.
     *
     * <p>🚨 The continuous branch divides by 20 on purpose and the division is load-bearing: a
     * continuous spell's JSON {@code cost} is per SECOND, while {@code SpellCastEvent.Finish}
     * reports how many TICKS the channel lasted. Without the divide, a five-second channel would
     * credit the wand with a hundred casts' worth of evolution.
     */
    public static double getConsumedMana(SpellCastEvent event) {
        if (event == null || event.getSpell() == null) {
            return 0.0D;
        }

        Spell spell = event.getSpell();
        float multiplier = getActualCostMultiplier(event.getModifiers());
        double baseCost = spell.getCost() * multiplier;

        if (spell.isContinuous && event instanceof SpellCastEvent.Finish) {
            return (baseCost * ((SpellCastEvent.Finish) event).getCount()) / 20.0D;
        }

        return baseCost;
    }
}
