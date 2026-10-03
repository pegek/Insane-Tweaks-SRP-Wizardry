package com.spege.ebreduxaddon.feature;

import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.item.SentientWandItem;
import com.spege.ebreduxaddon.feature.item.SymbioticWandItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EbreduxAddon.MODID);

    public static final RegistryObject<Item> SYMBIOTIC_WAND = ITEMS.register("symbiotic_wand", SymbioticWandItem::new);
    public static final RegistryObject<Item> SENTIENT_WAND = ITEMS.register("sentient_wand", SentientWandItem::new);

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
