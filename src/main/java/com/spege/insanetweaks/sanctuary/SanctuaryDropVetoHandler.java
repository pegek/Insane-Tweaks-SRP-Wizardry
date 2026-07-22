package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Suppress item drops and XP from SRP parasites that die inside a sanctuary. The dome's own purge
 * (Purge Fire, cleanse) kills parasites without the player's effort, so letting those kills drop
 * loot/XP would turn a sanctuary into a free AFK farm. Server side only; both variants of the
 * sanctuary register a region, so this applies inside either.
 */
public class SanctuaryDropVetoHandler {

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (vetoAt(event.getEntityLiving())) {
            event.setCanceled(true); // SRP's LootHooks/SRPEventHandlerBus add loot via this event
            logVeto(event.getEntityLiving(), "drops");
        }
    }

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (vetoAt(event.getEntityLiving())) {
            event.setCanceled(true); // SRP spawns XP via ForgeEventFactory.getExperienceDrop, which fires this
            logVeto(event.getEntityLiving(), "xp");
        }
    }

    private static void logVeto(EntityLivingBase e, String what) {
        if (ModConfig.sanctuary.debugLogging) {
            com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Sanctuary vetoed parasite " + what + ": " + e.getName()
                    + " @(" + ((int) Math.floor(e.posX)) + "," + ((int) Math.floor(e.posZ)) + ")");
        }
    }

    /** True when this is an SRP parasite dying inside an active sanctuary region. */
    private static boolean vetoAt(EntityLivingBase e) {
        if (!ModConfig.sanctuary.vetoParasiteDrops || e == null) {
            return false;
        }
        World world = e.world;
        if (world == null || world.isRemote) {
            return false;
        }
        if (!SanctuaryRegionHelper.isSrpParasite(e)) {
            return false;
        }
        return SanctuaryRegionHelper.isProtected(world, (int) Math.floor(e.posX), (int) Math.floor(e.posZ));
    }
}
