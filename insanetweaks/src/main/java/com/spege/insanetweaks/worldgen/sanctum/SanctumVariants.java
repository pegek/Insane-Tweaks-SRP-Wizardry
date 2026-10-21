package com.spege.insanetweaks.worldgen.sanctum;

import java.util.Random;

/**
 * A sanctum's variant: one EBW element for the whole structure, picked from its dimension's pool
 * by the layout seed, so every chunk of the sanctum agrees. It drives the runestone frame today and
 * is stored in the sanctum record for later per-variant loot, rooms and enemies. Pure Java.
 */
public final class SanctumVariants {

    private static final String[] OVERWORLD = { "sorcery", "lightning" };
    private static final String[] LOST_CITIES = { "earth" };
    private static final String[] NETHER = { "fire" };
    private static final String[] END = { "necromancy" };
    private static final long SALT = 0x52554E45L;

    private SanctumVariants() {
    }

    public static String[] pool(int dimension) {
        switch (dimension) {
            case 111:
                return LOST_CITIES.clone();
            case -1:
                return NETHER.clone();
            case 1:
                return END.clone();
            default:
                return OVERWORLD.clone();
        }
    }

    public static String element(int dimension, long layoutSeed) {
        String[] pool = pool(dimension);
        // Scrambled first: java.util.Random's first small nextInt barely moves for nearby seeds.
        long z = layoutSeed + SALT;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return pool[new Random(z).nextInt(pool.length)];
    }
}
