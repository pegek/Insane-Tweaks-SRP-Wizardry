package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.items.GoldenBookItem;
import com.spege.insanetweaks.items.bridge.ArcaneAdaptedFruitItem;
import com.spege.insanetweaks.items.core.WizardryCoreItem;
import com.spege.insanetweaks.items.core.WizardryCoreItems;
import com.spege.insanetweaks.items.fruit.AmuletFruitItem;
import com.spege.insanetweaks.items.fruit.BeltFruitItem;
import com.spege.insanetweaks.items.fruit.BodyFruitItem;
import com.spege.insanetweaks.items.fruit.CharmFruitItem;
import com.spege.insanetweaks.items.fruit.ElytraFruitItem;
import com.spege.insanetweaks.items.fruit.HeadFruitItem;
import com.spege.insanetweaks.items.fruit.RingFruitItem;
import com.spege.insanetweaks.items.fruit.TotemFruitItem;
import com.spege.insanetweaks.items.fruit.TrinketFruitItem;
import com.spege.insanetweaks.items.spellblade.LivingSpellblade;
import com.spege.insanetweaks.items.spellblade.SentientSpellblade;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.versioning.DefaultArtifactVersion;

@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
@SuppressWarnings("null")
public class ModItems {

    public static final WizardryCoreItem COST_CORE = WizardryCoreItems.COST_CORE;
    public static final WizardryCoreItem POTENCY_CORE = WizardryCoreItems.POTENCY_CORE;
    public static final WizardryCoreItem SPEEDCAST_CORE = WizardryCoreItems.SPEEDCAST_CORE;
    public static final WizardryCoreItem MINION_HEALTH_CORE = WizardryCoreItems.MINION_HEALTH_CORE;
    public static final WizardryCoreItem MINION_COUNT_CORE = WizardryCoreItems.MINION_COUNT_CORE;
    public static final WizardryCoreItem SUMMON_RADIUS_CORE = WizardryCoreItems.SUMMON_RADIUS_CORE;
    public static final WizardryCoreItem SUMMON_DURATION_CORE = WizardryCoreItems.SUMMON_DURATION_CORE;
    public static final Item GOLDEN_BOOK = new GoldenBookItem().setRegistryName("insanetweaks", "golden_book");
    public static final Item RUPTER_SOLIED = new Item().setRegistryName("insanetweaks", "rupter_solied")
            .setUnlocalizedName("rupter_solied").setCreativeTab(CreativeTabs.MISC);
    public static final Item LIVING_SPELLBLADE = new LivingSpellblade();
    public static final Item SENTIENT_SPELLBLADE = new SentientSpellblade();
    public static final Item LIVING_WAND = new com.spege.insanetweaks.items.wand.LivingWandItem();
    public static final Item SENTIENT_WAND = new com.spege.insanetweaks.items.wand.SentientWandItem();
    public static final Item ARCANE_ADAPTED_FRUIT = new ArcaneAdaptedFruitItem();

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
    public static final Item INFERNAL_CROWN = new com.spege.insanetweaks.baubles.ItemInfernalCrownArtefact();

    // Bauble Fruits  Eall 6 slot types
    public static final Item BAUBLE_FRUIT_RING   = new RingFruitItem();
    public static final Item BAUBLE_FRUIT_AMULET = new AmuletFruitItem();
    public static final Item BAUBLE_FRUIT_BODY   = new BodyFruitItem();
    public static final Item BAUBLE_FRUIT_HEAD   = new HeadFruitItem();
    public static final Item BAUBLE_FRUIT_CHARM  = new CharmFruitItem();
    public static final Item BAUBLE_FRUIT_BELT   = new BeltFruitItem();
    public static final Item BAUBLE_FRUIT_ELYTRA = new ElytraFruitItem();
    public static final Item BAUBLE_FRUIT_TOTEM  = new TotemFruitItem();
    public static final Item BAUBLE_FRUIT_TRINKET= new TrinketFruitItem();

    /** All Bauble Fruit items  Efor convenient bulk registration / model registration. */
    private static final Item[] ALL_BAUBLE_FRUITS = {
        BAUBLE_FRUIT_RING, BAUBLE_FRUIT_AMULET, BAUBLE_FRUIT_BODY,
        BAUBLE_FRUIT_HEAD, BAUBLE_FRUIT_CHARM,  BAUBLE_FRUIT_BELT,
        BAUBLE_FRUIT_ELYTRA, BAUBLE_FRUIT_TOTEM, BAUBLE_FRUIT_TRINKET
    };

    private static final Item[] ALL_WIZARDRY_CORES = WizardryCoreItems.ALL_CORES;

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // Items gated by Golden Book module
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
            event.getRegistry().registerAll(LIVING_SPELLBLADE, SENTIENT_SPELLBLADE);
            event.getRegistry().registerAll(LIVING_WAND, SENTIENT_WAND);
            event.getRegistry().registerAll(ARCANE_ADAPTED_FRUIT);
            event.getRegistry().registerAll(GOLDEN_BOOK, RUPTER_SOLIED, LIVING_AEGIS, SENTIENT_AEGIS, INFERNAL_CROWN);
            event.getRegistry().registerAll(
                BATTLEMAGE_HELMET, BATTLEMAGE_CHESTPLATE, BATTLEMAGE_LEGGINGS, BATTLEMAGE_BOOTS,
                PARASITE_WIZARD_HELMET, PARASITE_WIZARD_CHESTPLATE, PARASITE_WIZARD_LEGGINGS, PARASITE_WIZARD_BOOTS
            );
        }

        // Cores  Ealways gated by their own config
        if (com.spege.insanetweaks.config.ModConfig.modules.enableCustomCores) {
            event.getRegistry().registerAll(ALL_WIZARDRY_CORES);
        }

        // Bauble Fruits  Eany Baubles version triggers registration (BaublesEX or legacy).
        // The dual-path logic (BaublesEX vs luck fallback) is handled in BaseBaubleFruitItem.
        if (com.spege.insanetweaks.config.ModConfig.modules.enableBaubleFruits
                && net.minecraftforge.fml.common.Loader.isModLoaded("baubles")) {
            event.getRegistry().registerAll(ALL_BAUBLE_FRUITS);
        }
    }

    @SubscribeEvent
    public static void registerModels(net.minecraftforge.client.event.ModelRegistryEvent event) {
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
            registerModel(GOLDEN_BOOK);
            registerModel(RUPTER_SOLIED);
            ((com.spege.insanetweaks.items.spellblade.BridgeSpellblade)LIVING_SPELLBLADE).registerModel();
            ((com.spege.insanetweaks.items.spellblade.BridgeSpellblade)SENTIENT_SPELLBLADE).registerModel();
            
            registerModel(LIVING_WAND);
            registerModel(SENTIENT_WAND);
            registerModel(ARCANE_ADAPTED_FRUIT);

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
            registerModel(INFERNAL_CROWN);
        }

        if (com.spege.insanetweaks.config.ModConfig.modules.enableCustomCores) {
            for (Item core : ALL_WIZARDRY_CORES) {
                registerModel(core);
            }
        }

        if (com.spege.insanetweaks.config.ModConfig.modules.enableBaubleFruits
                && net.minecraftforge.fml.common.Loader.isModLoaded("baubles")) {
            for (Item fruit : ALL_BAUBLE_FRUITS) {
                // Universal Texture System:
                // Zamiast każdego owocu szukajāEego swojego osobnego pliku "bauble_fruit_ring.json" etc., 
                // przekierowujemy renderowanie ich grafiki do jednego pliku "bauble_fruit.json",
                // oszczędzajāE czas na duplikowaniu JSONów dla nowo dodanych przedmiotów.
                net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(
                    fruit, 0, new net.minecraft.client.renderer.block.model.ModelResourceLocation("insanetweaks:bauble_fruit", "inventory")
                );
            }
        }
    }

    /**
     * Checks that the installed Baubles mod is actually BaublesEX (v2.0.0+).
     * The original Azanor Baubles uses the same modid "baubles" with version 1.5.x.
     * BaublesEX starts at 2.0.0 and provides the AttributeManager API required
     * by Bauble Fruits. Without this check, the mod would crash at runtime when
     * trying to call BaublesEX-specific classes.
     *
     * @return true if BaublesEX v2.0.0 or higher is loaded.
     */
    public static boolean isBaublesExPresent() {
        if (!Loader.isModLoaded("baubles")) return false;
        ModContainer baubles = Loader.instance().getIndexedModList().get("baubles");
        if (baubles == null) return false;
        DefaultArtifactVersion current = new DefaultArtifactVersion(baubles.getVersion());
        DefaultArtifactVersion minRequired = new DefaultArtifactVersion("2.0.0");
        return current.compareTo(minRequired) >= 0;
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
