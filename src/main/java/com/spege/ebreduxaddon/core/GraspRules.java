package com.spege.ebreduxaddon.core;

/** Decyzje Grasp bez typow MC. */
public final class GraspRules {

    private GraspRules() {
    }

    /**
     * Egzekucja ponizej progu HP. Gracze nigdy (jak na 1.12.2). Prog przyciety do [0, 1]; popsuty
     * (NaN) wylacza egzekucje.
     */
    public static boolean shouldExecute(boolean targetIsPlayer, double health, double maxHealth, double threshold) {
        if (targetIsPlayer || !(maxHealth > 0.0) || !(health > 0.0) || Double.isNaN(threshold)) {
            return false;
        }
        double t = Math.max(0.0, Math.min(1.0, threshold));
        return health <= maxHealth * t;
    }

    /** Chwyt trzyma, dopoki cel jest w zasiegu z luzem: luz nie pozwala zerwac go jednym krokiem. */
    public static boolean inReach(double distanceSq, double range, double slack) {
        double reach = Math.max(0.0, range) * Math.max(1.0, slack);
        return distanceSq <= reach * reach;
    }

    /** Porzucony chwyt: ani jednego ticku zaklecia od {@code staleAfter} tickow. */
    public static boolean stale(long nowTick, long lastTick, long staleAfter) {
        return nowTick < lastTick || nowTick - lastTick > staleAfter;
    }
}
