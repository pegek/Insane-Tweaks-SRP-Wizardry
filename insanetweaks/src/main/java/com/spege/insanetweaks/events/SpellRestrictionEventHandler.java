package com.spege.insanetweaks.events;

import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.init.ModElements;
import com.spege.insanetweaks.util.AdaptationUpgradeHelper;

import electroblob.wizardry.event.SpellCastEvent;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@SuppressWarnings("null")
public class SpellRestrictionEventHandler {

    private static final ResourceLocation CALL_OF_DEMISE_ID = new ResourceLocation(InsaneTweaksMod.MODID, "call_of_demise");
    private static final ResourceLocation PARASITE_SHROUD_ID = new ResourceLocation(InsaneTweaksMod.MODID, "parasite_shroud");
    private static final ResourceLocation WIZARD_ID = new ResourceLocation("ebwizardry", "wizard");
    private static final ResourceLocation EVIL_WIZARD_ID = new ResourceLocation("ebwizardry", "evil_wizard");
    private static final int REQUIRED_CALL_OF_DEMISE_STAGE = 7;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellCastPre(SpellCastEvent.Pre event) {
        if (event.getSpell() == null) {
            return;
        }

        ResourceLocation spellId = event.getSpell().getRegistryName();
        EntityLivingBase caster = event.getCaster();

        if (spellId == null || caster == null) {
            return;
        }

        if (ModElements.isAbomination(event.getSpell()) && isBlockedWizardCaster(caster)) {
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

        if (isUnadaptedAbominationCast(event, player)) {
            event.setCanceled(true);
            player.sendStatusMessage(
                    new TextComponentString(TextFormatting.DARK_RED
                            + "This focus has not adapted to Abomination magic."),
                    true);
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
        return -1;
    }

    private String getStageRequirementMessage(ResourceLocation spellId, int requiredStage) {
        if (PARASITE_SHROUD_ID.equals(spellId)) {
            return "Parasite Shroud requires SRP evolution stage " + requiredStage + ".";
        }
        return "Call of Demise requires SRP evolution stage " + requiredStage + ".";
    }

    /**
     * Abomination magic is castable from OUR foci - the Living/Sentient wand and spellblade - or
     * from any wand carrying an Adaptation upgrade, and from nothing else.
     *
     * <p>No new logic is needed for that rule: {@code getEffectiveAdaptationLevel} already returns 1
     * for our four items by identity and adds any applied upgrade on top, so the rule is exactly
     * "level 0 means no".
     *
     * <p>🚨 Keyed on {@code Source.WAND}, not on the spell alone, and that is load-bearing rather
     * than incidental. Our own {@code sim_wizard} casts {@code dispatcher_grasp} and holds no wand
     * of ours; gating the spell itself would silence the element on the very mobs it is named after.
     * Scrolls, commands and dispensers pass for the same reason - see the design spec's §1.2, which
     * records the scroll bypass as known and accepted rather than missed.
     *
     * <p>This check previously lived in {@code ArcaneBridgeEventHandler}, behind an early return on
     * {@code PlayerManaCompat.isAvailable()} - so it stopped working the day player_mana was
     * disabled in the pack, silently. It lives here now because this handler is gated only on
     * {@code modules.enableSpells} and has no relationship to that mod.
     */
    private boolean isUnadaptedAbominationCast(SpellCastEvent.Pre event, EntityPlayer player) {
        if (!ModElements.isAbomination(event.getSpell())
                || event.getSource() != SpellCastEvent.Source.WAND) {
            return false;
        }
        return AdaptationUpgradeHelper.getEffectiveAdaptationLevel(
                AdaptationUpgradeHelper.findCastingItem(player, event.getSpell())) <= 0;
    }

    private boolean isBlockedWizardCaster(EntityLivingBase caster) {
        ResourceLocation casterId = EntityList.getKey(caster);
        return WIZARD_ID.equals(casterId) || EVIL_WIZARD_ID.equals(casterId);
    }
}
