package com.spege.insanetweaks.entities;

/**
 * Power tier of an Assimilated Wizard. Stored as data on the entity, NOT as separate entity types:
 * one registry object, one tracking id, one renderer.
 *
 * <p>🚨 The ordinal is the wire and save value. APPEND new tiers at the end only - inserting one
 * would silently re-tier every wizard in an existing world.
 *
 * <p>{@link #NOVICE} is the identity tier: all of its multipliers default to exactly 1.0, so a
 * wizard loaded from a pre-tier save (no {@code WizardTier} NBT key, hence NOVICE) is statistically
 * unchanged.
 */
public enum SimWizardTier {

    NOVICE,
    ADEPT,
    MASTER;

    /** Clamps out-of-range ids to {@link #NOVICE} rather than throwing on a corrupt save. */
    public static SimWizardTier fromId(int id) {
        SimWizardTier[] values = values();
        if (id < 0 || id >= values.length) {
            return NOVICE;
        }
        return values[id];
    }

    public boolean isAtLeast(SimWizardTier other) {
        return other == null || this.ordinal() >= other.ordinal();
    }

    /**
     * Case-insensitive lookup for config parsing.
     *
     * @return the tier, or {@code null} when the name is not one (the caller logs and skips).
     */
    public static SimWizardTier byName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        for (SimWizardTier tier : values()) {
            if (tier.name().equalsIgnoreCase(trimmed)) {
                return tier;
            }
        }
        return null;
    }
}
