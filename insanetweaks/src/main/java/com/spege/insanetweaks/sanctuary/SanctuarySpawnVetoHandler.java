package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.Entity;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class SanctuarySpawnVetoHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!ModConfig.sanctuary.vetoNaturalSpawn) {
            return;
        }
        if (event.getResult() == Event.Result.DENY) {
            return; // already denied by someone else
        }
        Entity e = event.getEntityLiving();
        if (e == null || !SanctuaryRegionHelper.isSrpParasite(e)) {
            return;
        }
        if (SanctuaryRegionHelper.isProtected(event.getWorld(),
                (int) Math.floor(event.getX()), (int) Math.floor(event.getZ()))) {
            event.setResult(Event.Result.DENY);
            SanctuaryDebug.log(event.getWorld().getTotalWorldTime(), "spawn-vetoed",
                    e.getName() + " @(" + ((int) Math.floor(event.getX())) + ","
                    + ((int) Math.floor(event.getY())) + "," + ((int) Math.floor(event.getZ())) + ")");
        }
    }
}
