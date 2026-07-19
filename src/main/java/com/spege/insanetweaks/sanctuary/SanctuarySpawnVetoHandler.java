package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.Entity;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class SanctuarySpawnVetoHandler {

    private static final String SRP_PARASITE_BASE =
            "com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!ModConfig.sanctuary.vetoNaturalSpawn) {
            return;
        }
        if (event.getResult() == Event.Result.DENY) {
            return; // already denied by someone else
        }
        Entity e = event.getEntityLiving();
        if (e == null || !isSrpParasite(e)) {
            return;
        }
        if (SanctuaryRegionHelper.isProtected(event.getWorld(),
                (int) Math.floor(event.getX()), (int) Math.floor(event.getZ()))) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static boolean isSrpParasite(Entity e) {
        Class<?> c = e.getClass();
        while (c != null) {
            if (c.getName().equals(SRP_PARASITE_BASE)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }
}
