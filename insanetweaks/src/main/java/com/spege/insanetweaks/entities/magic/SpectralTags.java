package com.spege.insanetweaks.entities.magic;

import net.minecraft.entity.Entity;

/**
 * Entity-data markers for a Conjurer's spectral summons. These are real SRP mobs, so the marker
 * lives in Forge entity data (saved with the entity) rather than in a class of ours. The expiry
 * is in world time, so it survives a reload even though the Conjurer's own list does not.
 */
public final class SpectralTags {

    /** Boolean: summoned by an Arcane Conjurer. */
    public static final String SPECTRAL = "InsaneTweaksSpectral";
    /** Long: world time at which it dissolves. */
    public static final String UNTIL = "InsaneTweaksSpectralUntil";

    private SpectralTags() {
    }

    public static boolean isSpectral(Entity entity) {
        return entity.getEntityData().getBoolean(SPECTRAL);
    }

    public static void mark(Entity entity, long expiresAt) {
        entity.getEntityData().setBoolean(SPECTRAL, true);
        entity.getEntityData().setLong(UNTIL, expiresAt);
    }

    public static boolean isExpired(Entity entity, long worldTime) {
        return worldTime >= entity.getEntityData().getLong(UNTIL);
    }
}
