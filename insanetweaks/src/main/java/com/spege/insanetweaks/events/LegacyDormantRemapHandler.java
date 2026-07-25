package com.spege.insanetweaks.events;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Permanent legacy shim: the dormant waystone moved to srpwizcore in 1.4.7 (new ID
 * {@code srpwizcore:dormant_waystone}). Worlds created before that have
 * {@code insanetweaks:dormant_waystone} placed/in inventories — remap them to the new block by
 * a plain registry lookup (string only, NO compile dependency on srpwizcore). If srpwizcore is
 * absent (public content build), the entries are silently dropped instead of prompting the user.
 */
@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public final class LegacyDormantRemapHandler {

    private static final String OLD_PATH = "dormant_waystone";
    private static final ResourceLocation NEW_ID = new ResourceLocation("srpwizcore", "dormant_waystone");

    private LegacyDormantRemapHandler() {}

    @SubscribeEvent
    public static void onMissingBlocks(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> m : event.getMappings()) {
            if (OLD_PATH.equals(m.key.getResourcePath())) {
                Block target = ForgeRegistries.BLOCKS.getValue(NEW_ID);
                if (target != null && target != net.minecraft.init.Blocks.AIR) {
                    m.remap(target);
                } else {
                    m.ignore();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMissingItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> m : event.getMappings()) {
            if (OLD_PATH.equals(m.key.getResourcePath())) {
                Item target = ForgeRegistries.ITEMS.getValue(NEW_ID);
                if (target != null) {
                    m.remap(target);
                } else {
                    m.ignore();
                }
            }
        }
    }
}
