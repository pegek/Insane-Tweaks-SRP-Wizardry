package com.spege.insanetweaks.entities.sapling;

import net.minecraft.entity.Entity;

/**
 * Forge entity-data markers a Corrupted Sapling defense leaves on the parasites it touches. Plain
 * {@code Entity} API, no SRP types, so the drop handlers can read them without loading SRP classes.
 * Neither marker is cleared when the event ends: a parasite that was part of a defense stays one.
 */
public final class SaplingTags {

    /** Boolean: this parasite was lured or spawned by a sapling defense (drop penalty applies). */
    public static final String BOUND = "InsaneTweaksSaplingBound";
    /** Long: world time until which the lure leaves this parasite on the player who hit it. */
    public static final String PROVOKED_UNTIL = "InsaneTweaksSaplingProvokedUntil";

    private SaplingTags() {
    }

    public static boolean isBound(Entity entity) {
        return entity.getEntityData().getBoolean(BOUND);
    }

    public static void markBound(Entity entity) {
        if (!isBound(entity)) {
            entity.getEntityData().setBoolean(BOUND, true);
        }
    }

    public static boolean isProvoked(Entity entity, long worldTime) {
        return entity.getEntityData().getLong(PROVOKED_UNTIL) > worldTime;
    }

    public static void markProvoked(Entity entity, long untilWorldTime) {
        entity.getEntityData().setLong(PROVOKED_UNTIL, untilWorldTime);
    }
}
