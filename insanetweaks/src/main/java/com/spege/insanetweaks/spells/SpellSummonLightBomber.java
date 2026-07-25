package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityLightBomberMinion;

import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellSummonLightBomber extends AbstractSrpSummonSpell<EntityLightBomberMinion> {

    public SpellSummonLightBomber() {
        super(InsaneTweaksMod.MODID, "summon_light_bomber", EntityLightBomberMinion::new);
        this.flying(true);
    }

    @Override
    protected void customizeMinion(EntityLightBomberMinion minion, World world, SpellModifiers modifiers) {
        minion.setBombDamageMultiplier(modifiers.get(POTENCY_ATTRIBUTE_MODIFIER));
    }

    /** The bomber rams as well as bombs, so potency scales its melee hit too. */
    @Override
    protected boolean appliesAttackDamageModifier() {
        return true;
    }
}
