package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.items.GoldenBookItem;
import com.spege.insanetweaks.items.spellblade.LivingSpellblade;
import com.spege.insanetweaks.items.spellblade.SentientSpellblade;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
@SuppressWarnings("null")
public class ModItems {

    public static final Item COST_CORE = new Item().setRegistryName("insanetweaks", "cost_core")
            .setUnlocalizedName("cost_core").setCreativeTab(CreativeTabs.MISC).setMaxStackSize(16);
    public static final Item POTENCY_CORE = new Item().setRegistryName("insanetweaks", "potency_core")
            .setUnlocalizedName("potency_core").setCreativeTab(CreativeTabs.MISC).setMaxStackSize(16);
    public static final Item SPEEDCAST_CORE = new Item().setRegistryName("insanetweaks", "speedcast_core")
            .setUnlocalizedName("speedcast_core").setCreativeTab(CreativeTabs.MISC).setMaxStackSize(16);
    public static final Item GOLDEN_BOOK = new GoldenBookItem().setRegistryName("insanetweaks", "golden_book");
    public static final Item RUPTER_SOLIED = new Item().setRegistryName("insanetweaks", "rupter_solied")
            .setUnlocalizedName("rupter_solied").setCreativeTab(CreativeTabs.MISC);
    public static final Item LIVING_SPELLBLADE = new LivingSpellblade();
    public static final Item SENTIENT_SPELLBLADE = new SentientSpellblade();

    // Battlemage Armor
    public static final Item BATTLEMAGE_HELMET = new com.spege.insanetweaks.items.armor.BattleMageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.HEAD);
    public static final Item BATTLEMAGE_CHESTPLATE = new com.spege.insanetweaks.items.armor.BattleMageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.CHEST);
    public static final Item BATTLEMAGE_LEGGINGS = new com.spege.insanetweaks.items.armor.BattleMageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.LEGS);
    public static final Item BATTLEMAGE_BOOTS = new com.spege.insanetweaks.items.armor.BattleMageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.FEET);

    // Parasite Wizard Armor
    public static final Item PARASITE_WIZARD_HELMET = new com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.HEAD);
    public static final Item PARASITE_WIZARD_CHESTPLATE = new com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.CHEST);
    public static final Item PARASITE_WIZARD_LEGGINGS = new com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.LEGS);
    public static final Item PARASITE_WIZARD_BOOTS = new com.spege.insanetweaks.items.armor.ParasiteWizardArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.FEET);

    public static final Item LIVING_AEGIS = new com.spege.insanetweaks.items.shield.LivingAegisItem();
    public static final Item SENTIENT_AEGIS = new com.spege.insanetweaks.items.shield.SentientAegisItem();

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // Items gated by Golden Book module
        if (com.spege.insanetweaks.config.ModConfig.enableSrpEbWizardryBridge) {
            event.getRegistry().registerAll(LIVING_SPELLBLADE, SENTIENT_SPELLBLADE);
            event.getRegistry().registerAll(GOLDEN_BOOK, RUPTER_SOLIED, LIVING_AEGIS, SENTIENT_AEGIS);
            event.getRegistry().registerAll(
                BATTLEMAGE_HELMET, BATTLEMAGE_CHESTPLATE, BATTLEMAGE_LEGGINGS, BATTLEMAGE_BOOTS,
                PARASITE_WIZARD_HELMET, PARASITE_WIZARD_CHESTPLATE, PARASITE_WIZARD_LEGGINGS, PARASITE_WIZARD_BOOTS
            );
        }

        // Cores — always gated by their own config
        if (com.spege.insanetweaks.config.ModConfig.enableCustomCores) {
            event.getRegistry().registerAll(COST_CORE, POTENCY_CORE, SPEEDCAST_CORE);
        }
    }

    @SubscribeEvent
    public static void registerModels(net.minecraftforge.client.event.ModelRegistryEvent event) {
        if (com.spege.insanetweaks.config.ModConfig.enableSrpEbWizardryBridge) {
            registerModel(GOLDEN_BOOK);
            registerModel(RUPTER_SOLIED);
            ((com.spege.insanetweaks.items.spellblade.BridgeSpellblade)LIVING_SPELLBLADE).registerModel();
            ((com.spege.insanetweaks.items.spellblade.BridgeSpellblade)SENTIENT_SPELLBLADE).registerModel();

            // Armor Models
            registerModel(BATTLEMAGE_HELMET);
            registerModel(BATTLEMAGE_CHESTPLATE);
            registerModel(BATTLEMAGE_LEGGINGS);
            registerModel(BATTLEMAGE_BOOTS);

            registerModel(PARASITE_WIZARD_HELMET);
            registerModel(PARASITE_WIZARD_CHESTPLATE);
            registerModel(PARASITE_WIZARD_LEGGINGS);
            registerModel(PARASITE_WIZARD_BOOTS);

            registerModel(LIVING_AEGIS);
            registerModel(SENTIENT_AEGIS);
        }

        if (com.spege.insanetweaks.config.ModConfig.enableCustomCores) {
            registerModel(COST_CORE);
            registerModel(POTENCY_CORE);
            registerModel(SPEEDCAST_CORE);
        }
    }

    private static void registerModel(Item item) {
        if (item != null) {
            net.minecraft.util.ResourceLocation regName = item.getRegistryName();
            if (regName != null) {
                net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(item, 0, 
                    new net.minecraft.client.renderer.block.model.ModelResourceLocation(regName, "inventory"));
            }
        }
    }
}
