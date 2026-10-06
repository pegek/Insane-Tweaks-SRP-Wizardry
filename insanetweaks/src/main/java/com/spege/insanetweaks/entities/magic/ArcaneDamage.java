package com.spege.insanetweaks.entities.magic;

/**
 * The "Arcane-touched" passive: magic damage is resisted, fire is a weakness. Applied
 * multiplicatively, so a source that is both (EBW fire spells) gets both.
 */
public final class ArcaneDamage {

    private ArcaneDamage() {
    }

    public static float scale(float amount, boolean magic, boolean fire, double magicMultiplier,
            double fireMultiplier) {
        double m = 1.0D;
        if (magic) {
            m *= magicMultiplier;
        }
        if (fire) {
            m *= fireMultiplier;
        }
        return (float) (amount * m);
    }
}
