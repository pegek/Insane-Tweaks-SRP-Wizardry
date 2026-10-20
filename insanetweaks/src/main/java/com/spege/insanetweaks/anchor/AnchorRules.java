package com.spege.insanetweaks.anchor;

/** Pure decisions behind the anchor mechanics, kept free of Minecraft types so they can be tested. */
public final class AnchorRules {

    private AnchorRules() {
    }

    /**
     * Whether a hit that leaves the mob at {@code healthAfter} sends it below {@code fraction} of its
     * maximum. A lethal hit counts too; a fraction of 0 (or no maximum) never calls.
     */
    public static boolean shouldCall(float healthAfter, float maxHealth, double fraction) {
        return fraction > 0.0D && maxHealth > 0.0F && healthAfter < maxHealth * fraction;
    }

    /**
     * Chance that a fallen anchor drops an artefact: only sanctum anchors do; a pinned (room) anchor
     * uses the room chance, an unpinned (ritual-chamber, parasite) anchor the chamber chance.
     */
    public static double artefactChance(boolean inSanctum, boolean pinned, double roomChance, double chamberChance) {
        if (!inSanctum) {
            return 0.0D;
        }
        return pinned ? roomChance : chamberChance;
    }
}
