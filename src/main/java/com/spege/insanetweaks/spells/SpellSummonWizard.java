package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityWizardMinion;
import com.spege.insanetweaks.entities.SummonInfectionSafetyHelper;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.ItemWizardArmour;
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
public class SpellSummonWizard extends SpellMinion<EntityWizardMinion> {

    private static final float SUMMON_POTENCY_STEP = 0.20F;
    private static final float SUMMON_DAMAGE_BONUS_PER_STEP = 0.10F;
    private static final ItemWizardArmour.ArmourClass[] SUMMONABLE_WIZARD_CLASSES = new ItemWizardArmour.ArmourClass[] {
            ItemWizardArmour.ArmourClass.WIZARD,
            ItemWizardArmour.ArmourClass.SAGE,
            ItemWizardArmour.ArmourClass.WARLOCK,
            ItemWizardArmour.ArmourClass.BATTLEMAGE
    };

    public SpellSummonWizard() {
        super(InsaneTweaksMod.MODID, "summon_wizard", EntityWizardMinion::new);
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

            if (pos == null) {
                return false;
            }

            EntityWizardMinion minion = this.createMinion(world, caster, modifiers);
            minion.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            minion.setCaster(caster);
            SummonInfectionSafetyHelper.onSummonServerTick(minion);
            minion.setLifetime((int) (this.getProperty(MINION_LIFETIME).floatValue()
                    * modifiers.get(WizardryItems.duration_upgrade)));
            minion.configureLoadout(Element.MAGIC, this.getRandomWizardClass(world),
                    world.rand.nextInt(EntityWizardMinion.TEXTURE_VARIANT_COUNT),
                    this.getWizardMinionSpellPotency(modifiers));

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

    private float getWizardMinionSpellPotency(SpellModifiers modifiers) {
        float basePotency = modifiers.get(SpellModifiers.POTENCY);
        float bonusPotency = Math.max(0.0F, basePotency - 1.0F);

        if (bonusPotency <= 0.0F) {
            return basePotency;
        }

        float summonBonus = bonusPotency * (SUMMON_DAMAGE_BONUS_PER_STEP / SUMMON_POTENCY_STEP);
        return basePotency + summonBonus;
    }

    private ItemWizardArmour.ArmourClass getRandomWizardClass(World world) {
        return SUMMONABLE_WIZARD_CLASSES[world.rand.nextInt(SUMMONABLE_WIZARD_CLASSES.length)];
    }
}
