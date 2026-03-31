package com.spege.insanetweaks.customwizardrystats;

import electroblob.wizardry.constants.SpellType;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.spell.Spell;

public final class SummonRadiusStat {

    private static final float TEST_MULTIPLIER = 1.50f;

    private SummonRadiusStat() {
    }

    public static void applyTestModifier(SpellCastEvent.Pre event) {
        Spell spell = event.getSpell();

        if (spell == null || spell.getType() != SpellType.MINION) {
            return;
        }

        // TESTING NOTE:
        // Base SpellMinion reads summon_radius from spell properties, not from
        // SpellModifiers. This stores an experimental value in the modifier bag, but
        // it will not affect default SpellMinion behavior until a custom spell reads
        // it explicitly.
        event.getModifiers().set("summon_radius",
                event.getModifiers().get("summon_radius") * TEST_MULTIPLIER, false);
    }
}
