package com.spege.insanetweaks.events;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModElements;
import com.spege.insanetweaks.util.AdaptationUpgradeHelper;
import com.spege.insanetweaks.util.ArcaneAdaptedFruitHelper;
import com.spege.insanetweaks.util.PlayerManaCompat;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class ArcaneBridgeEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellCastPre(SpellCastEvent.Pre event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge || !PlayerManaCompat.isAvailable()) {
            return;
        }

        EntityLivingBase caster = event.getCaster();
        if (!(caster instanceof EntityPlayer) || caster.world.isRemote || event.getSpell() == null
                || event.getSpell().getRegistryName() == null) {
            return;
        }

        EntityPlayer player = (EntityPlayer) caster;
        net.minecraft.item.ItemStack castingStack = AdaptationUpgradeHelper.findCastingItem(player, event.getSpell());
        int adaptationLevel = AdaptationUpgradeHelper.getEffectiveAdaptationLevel(castingStack);

        if (ModElements.isAbomination(event.getSpell())) {
            if (ArcaneAdaptedFruitHelper.hasConsumedFruit(player)) {
                ArcaneAdaptedFruitHelper.activateFruitRegen(player, ArcaneAdaptedFruitHelper.FRUIT_REGEN_DURATION_TICKS);
            }

            if (event.getSource() == SpellCastEvent.Source.WAND && adaptationLevel <= 0) {
                event.setCanceled(true);
                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        net.minecraft.util.text.TextFormatting.DARK_RED
                                + "This focus has not adapted to Abomination magic."));
                return;
            }

            // Casting our magic from someone else's focus - one that only qualifies through an
            // applied Adaptation upgrade, not by being our own item. Off by default: every level
            // multiplies by 1.0 until the config says otherwise.
            if (AdaptationUpgradeHelper.getDefaultAdaptationLevel(castingStack) == 0) {
                float multiplier = AdaptationUpgradeHelper.getForeignFocusAbominationCostMultiplier(
                        AdaptationUpgradeHelper.getAppliedAdaptationUpgradeLevel(castingStack));
                if (multiplier != 1.0f) {
                    event.getModifiers().set(SpellModifiers.COST,
                            event.getModifiers().get(SpellModifiers.COST) * multiplier, false);
                }
            }
            return;
        }

        // An adapted focus no longer surcharges foreign magic - that whole mechanic went away with
        // the real element. The early return stays because it used to shadow the fruit penalty
        // below, and dropping it would silently start charging adapted foci.
        if (adaptationLevel > 0) {
            return;
        }

        if (ArcaneAdaptedFruitHelper.hasConsumedFruit(player)) {
            float currentCost = event.getModifiers().get(SpellModifiers.COST);
            event.getModifiers().set(SpellModifiers.COST,
                    currentCost * ArcaneAdaptedFruitHelper.FOREIGN_SPELL_COST_MULTIPLIER, false);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge || event.player.world.isRemote
                || !(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (ArcaneAdaptedFruitHelper.hasPendingFruit(player)) {
            ArcaneAdaptedFruitHelper.sendPendingClaimMessage(player);
        }

        checkGearAchievement(player);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge || event.player.world.isRemote) {
            return;
        }

        ArcaneAdaptedFruitHelper.clearFruitRegen(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge || event.player.world.isRemote) {
            return;
        }

        ArcaneAdaptedFruitHelper.clearFruitRegen(event.player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge || event.phase != TickEvent.Phase.END
                || event.player.world.isRemote || !(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;

        if (player.ticksExisted % 20 != 0) {
            return;
        }

        checkGearAchievement(player);
    }

    private void checkGearAchievement(EntityPlayerMP player) {
        if (ArcaneAdaptedFruitHelper.hasUnlockedGearAchievement(player)
                || !ArcaneAdaptedFruitHelper.playerHasQualifyingGear(player)) {
            return;
        }

        ArcaneAdaptedFruitHelper.markGearAchievementUnlocked(player);
        ArcaneAdaptedFruitHelper.grantAdvancement(player);
        ArcaneAdaptedFruitHelper.sendAchievementMessage(player);

        if (!ArcaneAdaptedFruitHelper.hasRewardedFruit(player)) {
            ArcaneAdaptedFruitHelper.setFruitRewarded(player, true);
            ArcaneAdaptedFruitHelper.tryGiveFruit(player);
        }
    }
}
