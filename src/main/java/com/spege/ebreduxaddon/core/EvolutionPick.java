package com.spege.ebreduxaddon.core;

/**
 * W co ewoluuje zarazony mag (spec 0.3, sekcja 3). Odwzorowuje regule Spore: z szansa
 * {@code chance} typ z listy, w przeciwnym razie Scamper.
 */
public final class EvolutionPick {

    /** Wynik "Scamper" zamiast indeksu listy. */
    public static final int SCAMPER = -1;

    private EvolutionPick() {
    }

    /**
     * @param roll       losowa liczba z [0, 1) decydujaca lista czy Scamper
     * @param chance     szansa na typ z listy; przycinana do [0, 1]
     * @param size       ile poprawnych typow zostalo na liscie po odfiltrowaniu nieznanych id
     * @param indexRoll  losowa liczba z [0, 1) wybierajaca pozycje z listy
     * @return indeks w liscie albo {@link #SCAMPER}; pusta lista to zawsze Scamper
     */
    public static int choose(double roll, double chance, int size, double indexRoll) {
        if (size <= 0) {
            return SCAMPER;
        }
        double c = Double.isNaN(chance) ? 0.0 : Math.max(0.0, Math.min(1.0, chance));
        double r = Double.isNaN(roll) ? 1.0 : roll;
        if (r >= c) {
            return SCAMPER;
        }
        double i = Double.isNaN(indexRoll) ? 0.0 : Math.max(0.0, Math.min(0.999999, indexRoll));
        return (int) (i * size);
    }
}
