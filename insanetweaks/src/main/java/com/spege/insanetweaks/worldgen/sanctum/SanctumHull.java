package com.spege.insanetweaks.worldgen.sanctum;

/** When the builder may lay a sanctum's arcane-wall hull. Pure Java. */
public final class SanctumHull {

    private SanctumHull() {
    }

    /**
     * Only for a sanctum whose record carries its hull range (recorded by 1.28.0 or later - the only
     * ones SanctumWalls can ever dissolve) and that is not cleansed yet: a chunk populated after the
     * cleanse would otherwise get walls nobody removes.
     */
    public static boolean shouldBuild(boolean hullRecorded, boolean cleansed) {
        return hullRecorded && !cleansed;
    }
}
