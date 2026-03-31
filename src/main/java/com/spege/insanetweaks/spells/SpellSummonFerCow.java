package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityFerCowMinion;
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

public class SpellSummonFerCow extends SpellMinion<EntityFerCowMinion> {

    public SpellSummonFerCow() {
        super(InsaneTweaksMod.MODID, "summon_fer_cow", EntityFerCowMinion::new);
    }

    @Override
    protected boolean spawnMinions(World world, EntityLivingBase caster, SpellModifiers modifiers) {
        if (world.isRemote) {
            return true;
        }

        int totalCount = this.getProperty(MINION_COUNT).intValue() + this.getFlatMinionCountBonus(modifiers);

        for (int i = 0; i < totalCount; i++) {
            int summonRadius = this.getProperty(SUMMON_RADIUS).intValue();
            BlockPos pos = BlockUtils.findNearbyFloorSpace(caster, summonRadius, summonRadius * 2);

            if (this.flying) {
                if (pos != null) {
                    pos = pos.up(2);
                } else {
                    pos = caster.getPosition().add(world.rand.nextInt(summonRadius * 2) - summonRadius, 2,
                            world.rand.nextInt(summonRadius * 2) - summonRadius);
                }
            } else if (pos == null) {
                return false;
            }

            EntityFerCowMinion minion = this.createMinion(world, caster, modifiers);
            minion.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            minion.setCaster(caster);
            minion.setLifetime((int) (this.getProperty(MINION_LIFETIME).floatValue()
                    * modifiers.get(WizardryItems.duration_upgrade)));

            IAttributeInstance damage = minion.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
            if (damage != null) {
                damage.applyModifier(new AttributeModifier(POTENCY_ATTRIBUTE_MODIFIER,
                        modifiers.get(POTENCY_ATTRIBUTE_MODIFIER) - 1.0F, 2));
            }

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

    private int getFlatMinionCountBonus(SpellModifiers modifiers) {
        return Math.max(0, Math.round(modifiers.get(MINION_COUNT)) - 1);
    }
}
