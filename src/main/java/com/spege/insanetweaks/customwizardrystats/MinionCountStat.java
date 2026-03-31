package com.spege.insanetweaks.customwizardrystats;

import electroblob.wizardry.constants.SpellType;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.spell.Spell;

public final class MinionCountStat {

    private static final float TEST_MULTIPLIER = 2.00f;

    private MinionCountStat() {
    }

    public static void applyTestModifier(SpellCastEvent.Pre event) {
        Spell spell = event.getSpell();

        if (spell == null || spell.getType() != SpellType.MINION) {
            return;
        }

        // TESTING NOTE:
        // Base SpellMinion reads minion_count from spell properties, not from
        // SpellModifiers. This stores an experimental value in the modifier bag, but
        // it will not affect default SpellMinion behavior until a custom spell reads
        // it explicitly.
        event.getModifiers().set("minion_count",
                event.getModifiers().get("minion_count") * TEST_MULTIPLIER, false);
    }
}
