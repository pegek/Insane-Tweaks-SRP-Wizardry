package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityPrimitiveSummonerMinion;

import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellSummonPrimitiveSummoner extends AbstractSrpSummonSpell<EntityPrimitiveSummonerMinion> {

    public SpellSummonPrimitiveSummoner() {
        super(InsaneTweaksMod.MODID, "summon_primitive_summoner", EntityPrimitiveSummonerMinion::new);
    }

    @Override
    protected void customizeMinion(EntityPrimitiveSummonerMinion minion, World world, SpellModifiers modifiers) {
        minion.setPotencyMultiplier(modifiers.get(POTENCY_ATTRIBUTE_MODIFIER));
    }
}
