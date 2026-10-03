package com.spege.ebreduxaddon.core;

/**
 * Tier maksymalny zarazonego maga z liczby Hivemindow w swiecie - odpowiednik faz SRP z sim wizarda.
 *
 * <pre>
 *  h                   apprentice  advanced  master
 *  0                       70          30       0
 *  1 .. prog-1             30          50      20
 *  &gt;= prog (Proto World)    0          50      50
 * </pre>
 */
public final class InfectedTiers {

    public static final int APPRENTICE = 1;
    public static final int ADVANCED = 2;
    public static final int MASTER = 3;

    private InfectedTiers() {
    }

    /**
     * @param roll losowa liczba z [0, 1); spoza zakresu jest przycinana
     * @param protoThreshold prog Proto World ze Spore; &lt;= 1 traktujemy jak 1
     */
    public static int maxTier(int hiveminds, int protoThreshold, double roll) {
        double r = Double.isNaN(roll) ? 0.0 : Math.max(0.0, Math.min(0.999999, roll));
        int threshold = Math.max(1, protoThreshold);
        if (hiveminds <= 0) {
            return r < 0.70 ? APPRENTICE : ADVANCED;
        }
        if (hiveminds < threshold) {
            return r < 0.30 ? APPRENTICE : r < 0.80 ? ADVANCED : MASTER;
        }
        return r < 0.50 ? ADVANCED : MASTER;
    }
}
