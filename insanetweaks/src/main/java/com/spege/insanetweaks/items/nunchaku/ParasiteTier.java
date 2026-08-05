package com.spege.insanetweaks.items.nunchaku;

/**
 * Dwa stopnie pasożytniczego nunchaku. Wszystkie liczby są wyprowadzone z pomiarów broni
 * natywnych SRParasites — nie zmieniaj ich w oderwaniu od tamtej tabeli.
 */
public enum ParasiteTier {

    /** Bezpieczny stopień: Viral do amplifiera 1, bez Needlera, bez Prey. */
    LIVING(1.0D, 1, false, false),

    /** Ewolucja: Viral do 2, Needler, i cena w postaci Prey (calling=true jak w SRP). */
    SENTIENT(0.778D, 2, true, true);

    private final double speedMultiplier;
    private final int viralMaxAmplifier;
    private final boolean needler;
    private final boolean calling;

    ParasiteTier(double speedMultiplier, int viralMaxAmplifier, boolean needler, boolean calling) {
        this.speedMultiplier = speedMultiplier;
        this.viralMaxAmplifier = viralMaxAmplifier;
        this.needler = needler;
        this.calling = calling;
    }

    /**
     * Mnożnik nakładany NA prędkość wyliczoną przez Better Survival (−2,4 × 0,36 → 3,14 ataku/s).
     * SENTIENT ma 0,778, bo SRP zwalnia swojego Sentienta dokładnie w tej proporcji (0,90 → 0,70),
     * a my musimy to powtórzyć, żeby przy podwojonych obrażeniach DPS został na parytecie.
     */
    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public int getViralMaxAmplifier() {
        return viralMaxAmplifier;
    }

    public boolean appliesNeedler() {
        return needler;
    }

    /** Odpowiednik pola {@code calling} z {@code WeaponToolMeleeBase} — czy broń woła pasożyty. */
    public boolean isCalling() {
        return calling;
    }
}
