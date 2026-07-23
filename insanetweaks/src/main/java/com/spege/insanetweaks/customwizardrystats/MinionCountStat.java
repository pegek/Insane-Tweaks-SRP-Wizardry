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
        // InsaneTweaks now patches SpellMinion via compat mixin, so this modifier is
        // consumed globally as flat extra summon count by Wizardry minion spells.
        event.getModifiers().set("minion_count",
                event.getModifiers().get("minion_count") * TEST_MULTIPLIER, false);
    }
}
