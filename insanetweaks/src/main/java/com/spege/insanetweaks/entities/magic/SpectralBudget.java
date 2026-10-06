package com.spege.insanetweaks.entities.magic;

/** How many spectral parasites one Spectral Swarm cast may summon. */
public final class SpectralBudget {

    private SpectralBudget() {
    }

    /**
     * A count picked uniformly from [min, max] by {@code roll} (in [0, 1)), cut down so the
     * Conjurer never has more than {@code limit} alive at once. Never negative.
     */
    public static int toSummon(int min, int max, double roll, int active, int limit) {
        int lo = Math.max(0, Math.min(min, max));
        int hi = Math.max(lo, Math.max(min, max));
        int want = lo + (int) Math.floor(roll * (hi - lo + 1));
        if (want > hi) {
            want = hi;
        }
        return Math.max(0, Math.min(want, limit - active));
    }
}
