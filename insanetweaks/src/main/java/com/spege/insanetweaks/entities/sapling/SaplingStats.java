package com.spege.insanetweaks.entities.sapling;

/**
 * Everything about the Corrupted Sapling that is a function of its growth progress, plus the wave
 * size rule. Pure arithmetic with no Minecraft types ({@code SaplingStatsTest}).
 */
public final class SaplingStats {

    public static final float WIDTH_START = 0.6F;
    public static final float WIDTH_END = 1.2F;
    public static final float HEIGHT_START = 0.8F;
    public static final float HEIGHT_END = 2.5F;

    /** Progress is synced to clients as 0..SYNC_SCALE in a VARINT data parameter. */
    public static final int SYNC_SCALE = 1000;

    private SaplingStats() {
    }

    public static double lerp(double start, double end, double progress) {
        double p = Math.max(0.0D, Math.min(1.0D, progress));
        return start + (end - start) * p;
    }

    public static float width(double progress) {
        return (float) lerp(WIDTH_START, WIDTH_END, progress);
    }

    public static float height(double progress) {
        return (float) lerp(HEIGHT_START, HEIGHT_END, progress);
    }

    public static int toSynced(double progress) {
        double p = Math.max(0.0D, Math.min(1.0D, progress));
        return (int) Math.round(p * SYNC_SCALE);
    }

    public static double fromSynced(int synced) {
        return Math.max(0, Math.min(SYNC_SCALE, synced)) / (double) SYNC_SCALE;
    }

    /**
     * Parasites one wave adds: tops the area up to {@code base + phase / 2}, counting the ones
     * already there, and never more than {@code max} at once.
     */
    public static int waveSpawnCount(int base, int phase, int alreadyNearby, int max) {
        int wanted = base + Math.max(0, phase) / 2;
        return Math.max(0, Math.min(max, wanted - alreadyNearby));
    }
}
