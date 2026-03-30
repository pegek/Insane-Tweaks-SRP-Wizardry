package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityAssimilatedCowMinion;
import electroblob.wizardry.spell.SpellMinion;

public class SpellSummonPig extends SpellMinion<EntityAssimilatedCowMinion> {

    public SpellSummonPig() {
        super(InsaneTweaksMod.MODID, "summon_pig", EntityAssimilatedCowMinion::new);
    }
}
