package com.spege.insanetweaks.spells;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityWizardMinion;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class SpellSummonWizard extends AbstractSrpSummonSpell<EntityWizardMinion> {

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
    protected void customizeMinion(EntityWizardMinion minion, World world, SpellModifiers modifiers) {
        minion.configureLoadout(Element.MAGIC, this.getRandomWizardClass(world),
                world.rand.nextInt(EntityWizardMinion.TEXTURE_VARIANT_COUNT),
                this.getWizardMinionSpellPotency(modifiers));
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
