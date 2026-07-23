package com.spege.insanetweaks.customwizardrystats;

import electroblob.wizardry.constants.SpellType;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.spell.Spell;

public final class SummonDurationStat {

    private static final float TEST_MULTIPLIER = 1.20f;

    private SummonDurationStat() {
    }

    public static void applyTestModifier(SpellCastEvent.Pre event) {
        Spell spell = event.getSpell();

        if (spell == null) {
            return;
        }

        if (spell.getType() == SpellType.MINION || spell.getType() == SpellType.CONSTRUCT) {
            event.getModifiers().set("duration",
                    event.getModifiers().get("duration") * TEST_MULTIPLIER, false);
        }
    }
}
