package com.spege.insanetweaks.worldgen.sanctum;

import java.util.Random;

/**
 * Where sanctums may start. The world is cut into square regions of {@code size} chunks; each
 * region has one candidate start chunk seeded from the world seed, kept {@code margin} chunks from
 * the region edges, and used with {@code chance}. Pure Java, deterministic, unit-tested.
 */
public final class SanctumRegions {

    private static final long SALT = 0x5A6C7D1E2F3B4C5DL;
    private static final long LAYOUT_SALT = 0x2B992DDFA23249D6L;

    private SanctumRegions() {
    }

    public static int regionOf(int chunk, int size) {
        return Math.floorDiv(chunk, size);
    }

    /** {chunkX, chunkZ} of the region's start, or null when the region rolled no sanctum. */
    public static int[] candidate(long worldSeed, int regionX, int regionZ, int size, int margin, double chance) {
        int m = Math.max(0, Math.min(margin, (size - 1) / 2));
        Random r = new Random(mix(worldSeed, regionX, regionZ, SALT));
        double roll = r.nextDouble();
        int span = Math.max(1, size - 2 * m);
        int cx = regionX * size + m + r.nextInt(span);
        int cz = regionZ * size + m + r.nextInt(span);
        return roll < chance ? new int[] { cx, cz } : null;
    }

    /** The layout seed of a start, independent of the region size. */
    public static long layoutSeed(long worldSeed, int chunkX, int chunkZ) {
        return mix(worldSeed, chunkX, chunkZ, LAYOUT_SALT);
    }

    private static long mix(long seed, int a, int b, long salt) {
        return seed ^ (a * 341873128712L) ^ (b * 132897987541L) ^ salt;
    }
}
