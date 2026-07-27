package com.spege.insanetweaks.util;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.player.EntityPlayer;

/**
 * The maths behind the "Relief for the Damned" perk, kept free of any Corail Tombstone type.
 *
 * <p>This is the shared point between two callers that must not see the same dependencies:
 * the perk class (which lives in Tombstone's world) and the Enigmatic Legacy mixins (which run
 * whenever Enigmatic Legacy is present, Tombstone or not). Everything Tombstone-shaped is
 * reached through {@link TombstonePerkHelper}, which returns 0 when Tombstone is missing — so
 * with no Tombstone these methods hand back the untouched value and the mixins do nothing.
 *
 * <p>Each of the ring's three penalties is eased by the same fraction of <i>its own</i> value:
 * <ul>
 *   <li>a multiplier above 1.0 (incoming damage) only counts its surcharge as the penalty,
 *       hence {@link #softenMultiplier};</li>
 *   <li>a 0..1 debuff (armour, damage dealt to monsters) is scaled directly, hence
 *       {@link #softenDebuff}.</li>
 * </ul>
 */
public final class CurseReliefHelper {

    private CurseReliefHelper() {
    }

    /** Fraction of every curse penalty waived at this perk level, clamped to [0, 1]. */
    public static double reductionAtLevel(int level) {
        if (level <= 0) {
            return 0.0D;
        }
        double reduction = level * ModConfig.tombstone.reliefForTheDamned.reductionPerLevel;
        if (reduction <= 0.0D) {
            return 0.0D;
        }
        return reduction > 1.0D ? 1.0D : reduction;
    }

    /** Fraction waived for this player right now, or 0 when the perk is off or unowned. */
    public static double getReduction(EntityPlayer player) {
        if (player == null
                || !ModConfig.tombstone.enableTombstoneTweaks
                || !ModConfig.tombstone.reliefForTheDamned.enabled) {
            return 0.0D;
        }
        int level = TombstonePerkHelper.getPerkLevel(
                player, TombstonePerkHelper.PERK_RELIEF_FOR_THE_DAMNED);
        return reductionAtLevel(level);
    }

    /**
     * Eases a penalty expressed as a multiplier above 1.0, such as the ring's 2.0x incoming
     * damage. Only the part above 1.0 is the punishment, so that is all we reduce:
     * 2.0 at 36% relief becomes 1.64, not 1.28.
     */
    public static float softenMultiplier(float base, EntityPlayer player) {
        double reduction = getReduction(player);
        if (reduction <= 0.0D || base <= 1.0F) {
            return base;
        }
        return (float) (1.0D + (base - 1.0D) * (1.0D - reduction));
    }

    /**
     * Eases a penalty expressed as a 0..1 share, such as the ring's 0.3 armour debuff or its
     * 0.5 cut to damage dealt: 0.5 at 36% relief becomes 0.32.
     */
    public static float softenDebuff(float base, EntityPlayer player) {
        double reduction = getReduction(player);
        if (reduction <= 0.0D || base <= 0.0F) {
            return base;
        }
        return (float) (base * (1.0D - reduction));
    }
}
