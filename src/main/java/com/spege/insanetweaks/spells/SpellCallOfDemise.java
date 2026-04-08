package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityBeckonSivMinion;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.SpellMinion;
import electroblob.wizardry.util.BlockUtils;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellCallOfDemise extends SpellMinion<EntityBeckonSivMinion> {

    public SpellCallOfDemise() {
        super(InsaneTweaksMod.MODID, "call_of_demise", EntityBeckonSivMinion::new);
    }

    @Override
    protected boolean spawnMinions(World world, EntityLivingBase caster, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        // We strictly spawn only 1 boss minion to balance the spell.
        // We do not scale with MINION_COUNT upgrades to prevent lag and OPness.
        int totalCount = 1;

        for (int i = 0; i < totalCount; i++) {
            int summonRadius = this.getProperty(SUMMON_RADIUS).intValue();
            BlockPos pos = BlockUtils.findNearbyFloorSpace(caster, summonRadius, summonRadius * 2);

            if (pos == null) {
                return false;
            }

            EntityBeckonSivMinion minion = this.createMinion(world, caster, modifiers);
            minion.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            minion.setCaster(caster);
            
            // For Call of Demise, the minion has a very long lifetime.
            // If the default property isn't high enough, the JSON should handle it.
            minion.setLifetime((int) (this.getProperty(MINION_LIFETIME).floatValue()
                    * modifiers.get(WizardryItems.duration_upgrade)));

            // Apply potency adjustments if any
            IAttributeInstance damage = minion.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
            if (damage != null) {
                damage.applyModifier(new AttributeModifier(POTENCY_ATTRIBUTE_MODIFIER,
                        modifiers.get(POTENCY_ATTRIBUTE_MODIFIER) - 1.0F, 2));
            }

            // Apply health upgrades
            IAttributeInstance health = minion.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
            if (health != null) {
                health.applyModifier(new AttributeModifier(HEALTH_MODIFIER, modifiers.get(HEALTH_MODIFIER) - 1.0F, 2));
                minion.setHealth(minion.getMaxHealth());
            }

            this.addMinionExtras(minion, pos, caster, modifiers, i);
            world.spawnEntity(minion);
        }

        return true;
    }
}
