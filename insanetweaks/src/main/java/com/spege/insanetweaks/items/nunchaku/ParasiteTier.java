package com.spege.insanetweaks.items.nunchaku;

/**
 * Dwa stopnie pasożytniczego nunchaku. Wszystkie liczby są wyprowadzone z pomiarów broni
 * natywnych SRParasites — nie zmieniaj ich w oderwaniu od tamtej tabeli.
 */
public enum ParasiteTier {

    /** Bezpieczny stopień: Bleeding do amplifiera 1, bez Indeaf, bez Prey. */
    LIVING(1.10D, 1, false, false),

    /** Ewolucja: Bleeding do 2, Indeaf, i cena w postaci Prey (calling=true jak w SRP). */
    SENTIENT(0.90D, 2, true, true);

    private final double speedMultiplier;
    private final int bleedMaxAmplifier;
    private final boolean indeaf;
    private final boolean calling;

    ParasiteTier(double speedMultiplier, int bleedMaxAmplifier, boolean indeaf, boolean calling) {
        this.speedMultiplier = speedMultiplier;
        this.bleedMaxAmplifier = bleedMaxAmplifier;
        this.indeaf = indeaf;
        this.calling = calling;
    }

    /**
     * Mnożnik nakładany NA prędkość wyliczoną przez Better Survival (−2,4 × 0,36 → 3,14 ataku/s).
     * Obie wartości podniesiono na życzenie usera: Living 1,00 → 1,10 (3,45 ataku/s),
     * Sentient 0,778 → 0,90 (2,82/s). Sentient dalej jest wolniejszy od Livinga, bo tak robi
     * cała rodzina SRP, ale różnica jest już mniejsza niż kanoniczne 22%.
     */
    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    /** Sufit amplifiera Bleeding. Wyżej nie dociskamy, choćby rzut kośćmi wypadał w kółko. */
    public int getBleedMaxAmplifier() {
        return bleedMaxAmplifier;
    }

    /** Czy ten stopień zakorzenia cel (Indeaf). Tylko Sentient. */
    public boolean appliesIndeaf() {
        return indeaf;
    }

    /** Odpowiednik pola {@code calling} z {@code WeaponToolMeleeBase} — czy broń woła pasożyty. */
    public boolean isCalling() {
        return calling;
    }
}
