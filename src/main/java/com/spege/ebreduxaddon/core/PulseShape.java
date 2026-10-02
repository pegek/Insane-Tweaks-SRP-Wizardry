package com.spege.ebreduxaddon.core;

/** Rozmiar fali Purifying Pulse, jak na 1.12.2. */
public final class PulseShape {

    private static final double POTENCY_STEP = 0.45;
    private static final int RADIUS_PER_STEP = 2;

    private PulseShape() {
    }

    /** base + 2 za kazde pelne +45% potency ponad 1.0. Base &lt; 1 traktowane jak 1; NaN jak brak bonusu. */
    public static int radius(int base, double potency) {
        int steps = Double.isNaN(potency) ? 0 : Math.max(0, (int) Math.floor((potency - 1.0) / POTENCY_STEP + 1.0E-6));
        return Math.max(1, base) + Math.min(steps, 64) * RADIUS_PER_STEP;
    }

    /** Polowa promienia, nie mniej niz 4. */
    public static int verticalRange(int radius) {
        return Math.max(4, radius / 2);
    }
}
