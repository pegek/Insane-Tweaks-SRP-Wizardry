package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityFerCowMinion;

@SuppressWarnings("null")
public class SpellSummonFerCow extends AbstractSrpSummonSpell<EntityFerCowMinion> {

    public SpellSummonFerCow() {
        super(InsaneTweaksMod.MODID, "summon_fer_cow", EntityFerCowMinion::new);
    }

    @Override
    protected boolean appliesAttackDamageModifier() {
        return true;
    }
}
