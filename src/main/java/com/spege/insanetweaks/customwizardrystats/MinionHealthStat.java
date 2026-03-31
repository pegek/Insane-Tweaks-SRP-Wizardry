package com.spege.insanetweaks.customwizardrystats;

import electroblob.wizardry.constants.SpellType;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.spell.Spell;

public final class MinionHealthStat {

    private static final float TEST_MULTIPLIER = 1.20f;

    private MinionHealthStat() {
    }

    public static void applyTestModifier(SpellCastEvent.Pre event) {
        Spell spell = event.getSpell();

        if (spell == null || spell.getType() != SpellType.MINION) {
            return;
        }

        event.getModifiers().set("minion_health",
                event.getModifiers().get("minion_health") * TEST_MULTIPLIER, false);
    }
}
