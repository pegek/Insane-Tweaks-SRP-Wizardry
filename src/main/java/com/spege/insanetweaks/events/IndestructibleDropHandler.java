package com.spege.insanetweaks.events;

import com.spege.insanetweaks.entities.EntityItemIndestructible;
import com.spege.insanetweaks.util.LegendaryDropHelper;

import net.minecraft.entity.item.EntityItem;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Applies the legendary-drop hardening rules to protected Living/Sentient gear
 * as soon as the vanilla EntityItem joins the world.
 */
@SuppressWarnings("null")
public class IndestructibleDropHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote) {
            return;
        }

        if (!(event.getEntity() instanceof EntityItem)
                || event.getEntity() instanceof EntityItemIndestructible) {
            return;
        }

        EntityItem entityItem = (EntityItem) event.getEntity();
        if (entityItem.getItem().isEmpty()) {
            return;
        }

        if (LegendaryDropHelper.isLegendaryDropItem(entityItem.getItem().getItem())) {
            event.setCanceled(true);
            event.getWorld().spawnEntity(LegendaryDropHelper.createLegendaryDropEntity(entityItem));
        }
    }
}
