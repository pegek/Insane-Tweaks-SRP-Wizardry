package com.spege.insanetweaks.events;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Iterator;

public class TombstoneDropEventHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDrops(LivingDropsEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.tombstone.enableTombstoneTweaks || !com.spege.insanetweaks.config.ModConfig.tombstone.nerfGraveDustDrop) {
            return;
        }

        // Only process player kills
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }

        int dropChance = com.spege.insanetweaks.config.ModConfig.tombstone.graveDustDropChance;
        if (dropChance >= 100) return; // Vanilla rate

        Iterator<EntityItem> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            EntityItem entityItem = iterator.next();
            ItemStack stack = entityItem.getItem();

            if (!stack.isEmpty() && stack.getItem().getRegistryName() != null) {
                if (stack.getItem().getRegistryName().toString().equals("tombstone:crafting_ingredient") && stack.getMetadata() == 3) { // 3 is GRAVE_DUST
                    if (dropChance <= 0 || event.getEntityLiving().world.rand.nextInt(100) >= dropChance) {
                        iterator.remove(); // Remove grave dust from drops
                    }
                }
            }
        }
    }
}
