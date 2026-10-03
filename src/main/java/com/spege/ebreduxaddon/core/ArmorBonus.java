package com.spege.ebreduxaddon.core;

/** Bonus zaklec z noszonych czesci Grafted i Sentient. */
public final class ArmorBonus {

    /** Dolna granica mnoznika kosztu z samej zbroi: cztery czesci nie moga zrobic czarow darmowymi. */
    public static final double MIN_COST_MULTIPLIER = 0.5;

    private ArmorBonus() {
    }

    public static double costMultiplier(int grafted, int sentient, double graftedPer, double sentientPer) {
        double reduction = Math.max(0, grafted) * clamp(graftedPer) + Math.max(0, sentient) * clamp(sentientPer);
        return Math.max(MIN_COST_MULTIPLIER, 1.0 - reduction);
    }

    public static double potencyMultiplier(int sentient, double sentientPer) {
        return 1.0 + Math.max(0, sentient) * clamp(sentientPer);
    }

    private static double clamp(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }
}
