package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityBeckonSivMinion;

import electroblob.wizardry.util.SpellModifiers;

@SuppressWarnings("null")
public class SpellCallOfDemise extends AbstractSrpSummonSpell<EntityBeckonSivMinion> {

    public SpellCallOfDemise() {
        super(InsaneTweaksMod.MODID, "call_of_demise", EntityBeckonSivMinion::new);
    }

    @Override
    protected int getSummonCount(SpellModifiers modifiers) {
        // Strictly one boss minion to balance the spell. Does not scale with
        // MINION_COUNT upgrades to prevent lag and OPness.
        return 1;
    }

    @Override
    protected boolean appliesAttackDamageModifier() {
        return true;
    }
}
