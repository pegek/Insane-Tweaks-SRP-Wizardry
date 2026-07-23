package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityPrimitiveYelloweyeMinion;

import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellSummonPrimitiveYelloweye extends AbstractSrpSummonSpell<EntityPrimitiveYelloweyeMinion> {

    public SpellSummonPrimitiveYelloweye() {
        super(InsaneTweaksMod.MODID, "summon_primitive_yelloweye", EntityPrimitiveYelloweyeMinion::new);
        this.flying(true);
    }

    @Override
    protected void customizeMinion(EntityPrimitiveYelloweyeMinion minion, World world, SpellModifiers modifiers) {
        minion.setProjectileDamageMultiplier(modifiers.get(POTENCY_ATTRIBUTE_MODIFIER));
    }
}
