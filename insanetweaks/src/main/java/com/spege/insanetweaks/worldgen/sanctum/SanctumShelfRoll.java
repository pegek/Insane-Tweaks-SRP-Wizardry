package com.spege.insanetweaks.worldgen.sanctum;

/** What one extra sanctum-bookshelf roll yields. Pure Java. */
public final class SanctumShelfRoll {

    public static final int NONE = 0;
    /** An enchanted book from treasure-allowed random enchanting. */
    public static final int TREASURE = 1;
    /** A level-1 insanetweaks enchanted book or a Property Book. */
    public static final int INSANE = 2;

    private SanctumShelfRoll() {
    }

    public static int kind(double roll, double treasureChance, double insaneChance) {
        if (roll < treasureChance) {
            return TREASURE;
        }
        return roll < treasureChance + insaneChance ? INSANE : NONE;
    }
}
