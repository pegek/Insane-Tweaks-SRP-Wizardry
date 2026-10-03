package com.spege.ebreduxaddon.feature;

import com.spege.ebreduxaddon.EbreduxAddon;
import com.spege.ebreduxaddon.feature.item.AddonArmorItem;
import com.spege.ebreduxaddon.feature.item.ModArmorMaterials;
import com.spege.ebreduxaddon.feature.item.SentientWandItem;
import com.spege.ebreduxaddon.feature.item.SymbioticWandItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, EbreduxAddon.MODID);

    public static final RegistryObject<Item> SYMBIOTIC_WAND = ITEMS.register("symbiotic_wand", SymbioticWandItem::new);
    public static final RegistryObject<Item> SENTIENT_WAND = ITEMS.register("sentient_wand", SentientWandItem::new);

    public static final RegistryObject<Item> GRAFTED_HELMET = armor("grafted_helmet", ModArmorMaterials.GRAFTED, ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> GRAFTED_CHESTPLATE = armor("grafted_chestplate", ModArmorMaterials.GRAFTED, ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> GRAFTED_LEGGINGS = armor("grafted_leggings", ModArmorMaterials.GRAFTED, ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> GRAFTED_BOOTS = armor("grafted_boots", ModArmorMaterials.GRAFTED, ArmorItem.Type.BOOTS);
    public static final RegistryObject<Item> SENTIENT_HELMET = armor("sentient_helmet", ModArmorMaterials.SENTIENT, ArmorItem.Type.HELMET);
    public static final RegistryObject<Item> SENTIENT_CHESTPLATE = armor("sentient_chestplate", ModArmorMaterials.SENTIENT, ArmorItem.Type.CHESTPLATE);
    public static final RegistryObject<Item> SENTIENT_LEGGINGS = armor("sentient_leggings", ModArmorMaterials.SENTIENT, ArmorItem.Type.LEGGINGS);
    public static final RegistryObject<Item> SENTIENT_BOOTS = armor("sentient_boots", ModArmorMaterials.SENTIENT, ArmorItem.Type.BOOTS);

    private static RegistryObject<Item> armor(String name, ModArmorMaterials material, ArmorItem.Type type) {
        return ITEMS.register(name, () -> new AddonArmorItem(material, type));
    }

    /** Czesc Sentient tego samego typu - cel ewolucji czesci Grafted. */
    public static Item sentientFor(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> SENTIENT_HELMET.get();
            case CHESTPLATE -> SENTIENT_CHESTPLATE.get();
            case LEGGINGS -> SENTIENT_LEGGINGS.get();
            case BOOTS -> SENTIENT_BOOTS.get();
        };
    }

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
