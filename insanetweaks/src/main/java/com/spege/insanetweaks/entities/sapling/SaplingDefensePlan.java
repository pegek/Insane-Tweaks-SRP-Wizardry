package com.spege.insanetweaks.entities.sapling;

/**
 * Timeline of one Corrupted Sapling defense, in ticks: a lure period, then {@code waveCount} waves
 * {@code interval} apart; the event completes one interval after the last wave starts.
 *
 * <p>Pure arithmetic with no Minecraft types, so it is unit-tested on a plain JVM
 * ({@code SaplingDefensePlanTest}). Built from the live config each time it is needed; nothing
 * here is stored.
 */
public final class SaplingDefensePlan {

    /** What the pre-1.21.0 sapling counted {@code SaplingGrowth} up to. */
    public static final int LEGACY_TOTAL_TICKS = 24000;

    private final int lureTicks;
    private final int waveCount;
    private final int intervalTicks;

    public SaplingDefensePlan(int lureSeconds, int waveCount, int intervalSeconds) {
        this.lureTicks = Math.max(0, lureSeconds) * 20;
        this.waveCount = Math.max(1, waveCount);
        this.intervalTicks = Math.max(1, intervalSeconds) * 20;
    }

    public int waveCount() {
        return this.waveCount;
    }

    public int totalTicks() {
        return this.lureTicks + this.waveCount * this.intervalTicks;
    }

    /** Tick at which wave {@code wave} (1-based) starts. */
    public int waveStartTick(int wave) {
        return this.lureTicks + (wave - 1) * this.intervalTicks;
    }

    /** How many waves have started by {@code defenseTicks}, 0..waveCount. */
    public int wavesDueBy(int defenseTicks) {
        if (defenseTicks < this.lureTicks) {
            return 0;
        }
        int due = (defenseTicks - this.lureTicks) / this.intervalTicks + 1;
        return Math.min(this.waveCount, due);
    }

    /** 0..1 share of the event that has run. */
    public double progress(int defenseTicks) {
        double p = defenseTicks / (double) totalTicks();
        return Math.max(0.0D, Math.min(1.0D, p));
    }

    public boolean isComplete(int defenseTicks) {
        return defenseTicks >= totalTicks();
    }

    /**
     * Maps a pre-1.21.0 {@code SaplingGrowth} value onto this timeline, proportionally. Never lands
     * on the very end: an old sapling resumes with at least one second left instead of blooming
     * the moment its chunk loads.
     */
    public int migrateLegacyGrowth(int legacyGrowth) {
        int clamped = Math.max(0, Math.min(legacyGrowth, LEGACY_TOTAL_TICKS));
        long scaled = (long) clamped * totalTicks() / LEGACY_TOTAL_TICKS;
        return (int) Math.min(scaled, (long) totalTicks() - 20);
    }
}
