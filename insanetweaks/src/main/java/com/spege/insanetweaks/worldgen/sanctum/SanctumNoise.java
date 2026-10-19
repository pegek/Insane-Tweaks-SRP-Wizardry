package com.spege.insanetweaks.worldgen.sanctum;

/**
 * Per-cell randomness that does not depend on the order chunks are generated in: a hash of
 * (seed, x, y, z, channel) mapped to [0, 1). Pure Java.
 */
public final class SanctumNoise {

    private SanctumNoise() {
    }

    public static double at(long seed, int x, int y, int z, int channel) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 27) * 0xBF58476D1CE4E5B9L;
        h ^= y * 0x94D049BB133111EBL;
        h = Long.rotateLeft(h, 31) * 0xD6E8FEB86659FD93L;
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h = Long.rotateLeft(h, 29) * 0x165667B19E3779F9L;
        h ^= channel * 0x27D4EB2F165667C5L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** A seed for one cell, e.g. a chest's loot seed. */
    public static long seedAt(long seed, int x, int y, int z) {
        return Double.doubleToLongBits(at(seed, x, y, z, 99)) ^ seed;
    }
}
