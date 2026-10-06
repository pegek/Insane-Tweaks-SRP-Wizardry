package com.spege.insanetweaks.entities.sapling;

/**
 * Loot penalty for parasites drawn into a Corrupted Sapling defense, so the event is not a farm.
 * Pure arithmetic ({@code SaplingDropPenaltyTest}); the handler supplies the rolls.
 */
public final class SaplingDropPenalty {

    private SaplingDropPenalty() {
    }

    /** One dropped stack survives when the [0,1) roll is at or above the penalty. */
    public static boolean keepsDrop(double roll, double penalty) {
        return roll >= penalty;
    }

    /**
     * Experience scaled by {@code 1 - penalty}. The fractional remainder is paid out by chance, so
     * small orbs are not always rounded down to nothing and the average stays exact.
     */
    public static int reducedXp(int xp, double penalty, double roll) {
        double scaled = Math.max(0, xp) * (1.0D - Math.max(0.0D, Math.min(1.0D, penalty)));
        int whole = (int) Math.floor(scaled);
        return roll < scaled - whole ? whole + 1 : whole;
    }
}
