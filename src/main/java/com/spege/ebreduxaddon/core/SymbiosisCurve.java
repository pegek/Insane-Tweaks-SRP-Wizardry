package com.spege.ebreduxaddon.core;

/**
 * Bonusy Symbiotic Wand jako funkcja postepu. Liniowo od 0 do pelnego bonusu przy progu ewolucji.
 *
 * <p>Odporna na popsuty config: NaN, ujemne i zerowe wartosci daja "brak bonusu", nigdy mnoznik,
 * ktory zrobilby zaklecie darmowym albo ujemnym.
 */
public final class SymbiosisCurve {

    private SymbiosisCurve() {
    }

    /** Postep w [0, 1]. Prog &lt;= 0 traktujemy jak "nigdy", czyli 0. */
    public static double progress(long points, long evolveAt) {
        if (evolveAt <= 0 || points <= 0) {
            return 0.0;
        }
        return points >= evolveAt ? 1.0 : (double) points / (double) evolveAt;
    }

    /** Mnoznik kosztu: 1 - maxReduction * p. Redukcja przycieta do [0, 0.9]. */
    public static double costMultiplier(double progress, double maxReduction) {
        return 1.0 - clamp(maxReduction, 0.0, 0.9) * clamp(progress, 0.0, 1.0);
    }

    /** Mnoznik czasu trwania: 1 + maxBonus * p. Bonus przyciety do [0, 10]. */
    public static double durationMultiplier(double progress, double maxBonus) {
        return 1.0 + clamp(maxBonus, 0.0, 10.0) * clamp(progress, 0.0, 1.0);
    }

    static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(v)) {
            return lo;
        }
        return Math.max(lo, Math.min(hi, v));
    }
}
