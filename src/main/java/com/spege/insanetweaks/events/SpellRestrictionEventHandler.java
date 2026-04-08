package com.spege.insanetweaks.events;

import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.spege.insanetweaks.InsaneTweaksMod;

import electroblob.wizardry.event.SpellCastEvent;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class SpellRestrictionEventHandler {

    private static final ResourceLocation CALL_OF_DEMISE_ID = new ResourceLocation(InsaneTweaksMod.MODID, "call_of_demise");
    private static final ResourceLocation PARASITE_SHROUD_ID = new ResourceLocation(InsaneTweaksMod.MODID, "parasite_shroud");
    private static final ResourceLocation WIZARD_ID = new ResourceLocation("ebwizardry", "wizard");
    private static final ResourceLocation EVIL_WIZARD_ID = new ResourceLocation("ebwizardry", "evil_wizard");
    private static final int REQUIRED_CALL_OF_DEMISE_STAGE = 7;
    private static final int REQUIRED_PARASITE_SHROUD_STAGE = 5;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellCastPre(SpellCastEvent.Pre event) {
        if (event.getSpell() == null || event.getSpell().getRegistryName() == null || event.getCaster() == null) {
            return;
        }

        ResourceLocation spellId = event.getSpell().getRegistryName();
        EntityLivingBase caster = event.getCaster();

        if (InsaneTweaksMod.MODID.equals(spellId.getResourceDomain()) && isBlockedWizardCaster(caster)) {
            event.setCanceled(true);
            return;
        }

        if (caster.world == null || caster.world.isRemote
                || !(caster instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) caster;
        if (player.isCreative()) {
            return;
        }

        int requiredStage = getRequiredStage(spellId);
        if (requiredStage < 0) {
            return;
        }

        SRPSaveData saveData = SRPSaveData.get(caster.world, 0);
        if (saveData == null) {
            event.setCanceled(true);
            player.sendStatusMessage(
                    new TextComponentString(TextFormatting.DARK_RED
                            + getStageRequirementMessage(spellId, requiredStage)),
                    true);
            return;
        }

        int dimension = caster.world.provider.getDimension();
        int phase = saveData.getEvolutionPhase(dimension);
        if (phase < requiredStage) {
            event.setCanceled(true);
            player.sendStatusMessage(
                    new TextComponentString(TextFormatting.DARK_RED
                            + getStageRequirementMessage(spellId, requiredStage)),
                    true);
        }
    }

    private int getRequiredStage(ResourceLocation spellId) {
        if (CALL_OF_DEMISE_ID.equals(spellId)) {
            return REQUIRED_CALL_OF_DEMISE_STAGE;
        }
        if (PARASITE_SHROUD_ID.equals(spellId)) {
            return REQUIRED_PARASITE_SHROUD_STAGE;
        }
        return -1;
    }

    private String getStageRequirementMessage(ResourceLocation spellId, int requiredStage) {
        if (PARASITE_SHROUD_ID.equals(spellId)) {
            return "Parasite Shroud requires SRP evolution stage " + requiredStage + ".";
        }
        return "Call of Demise requires SRP evolution stage " + requiredStage + ".";
    }

    private boolean isBlockedWizardCaster(EntityLivingBase caster) {
        ResourceLocation casterId = EntityList.getKey(caster);
        return WIZARD_ID.equals(casterId) || EVIL_WIZARD_ID.equals(casterId);
    }
}
