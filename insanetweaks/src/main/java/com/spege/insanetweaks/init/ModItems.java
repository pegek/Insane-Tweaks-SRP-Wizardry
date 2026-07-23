package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.baubles.ItemZhonyasHourglassArtefact;
import com.spege.insanetweaks.items.GoldenBookItem;
import com.spege.insanetweaks.items.bridge.AdaptationUpgradeItem;
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
    // Fallback clones of the swparasites crafting components. Registered ONLY when swparasites
    // is absent (see registerItems), so they never duplicate the originals. The OreDictionary
    // bridge (ModOreDict) routes recipes to whichever variant actually exists.
    public static final Item LIVING_NUCLEUS = new Item().setRegistryName("insanetweaks", "living_nucleus")
            .setUnlocalizedName("living_nucleus").setCreativeTab(CreativeTabs.MISC);
    public static final Item INFECTIOUS_LONG_BLADE_FRAGMENT = new Item()
            .setRegistryName("insanetweaks", "infectious_long_blade_fragment")
            .setUnlocalizedName("infectious_long_blade_fragment").setCreativeTab(CreativeTabs.MISC);
    public static final Item LIVING_SPELLBLADE = new LivingSpellblade();
    public static final Item SENTIENT_SPELLBLADE = new SentientSpellblade();
    public static final Item LIVING_WAND = new com.spege.insanetweaks.items.wand.LivingWandItem();
    public static final Item SENTIENT_WAND = new com.spege.insanetweaks.items.wand.SentientWandItem();
    public static final Item ADAPTATION_UPGRADE = new AdaptationUpgradeItem();
    public static final Item ARCANE_ADAPTED_FRUIT = new ArcaneAdaptedFruitItem();

    // Sentient Warlock Armor (originally Battlemage)
    public static final Item SENTIENT_WARLOCK_HELMET = new com.spege.insanetweaks.items.armor.SentientWarlockArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.HEAD);
    public static final Item SENTIENT_WARLOCK_CHESTPLATE = new com.spege.insanetweaks.items.armor.SentientWarlockArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.CHEST);
    public static final Item SENTIENT_WARLOCK_LEGGINGS = new com.spege.insanetweaks.items.armor.SentientWarlockArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.LEGS);
    public static final Item SENTIENT_WARLOCK_BOOTS = new com.spege.insanetweaks.items.armor.SentientWarlockArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.FEET);

    // Living Battlemage Armor
    public static final Item LIVING_BATTLEMAGE_HELMET = new com.spege.insanetweaks.items.armor.LivingBattlemageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.HEAD);
    public static final Item LIVING_BATTLEMAGE_CHESTPLATE = new com.spege.insanetweaks.items.armor.LivingBattlemageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.CHEST);
    public static final Item LIVING_BATTLEMAGE_LEGGINGS = new com.spege.insanetweaks.items.armor.LivingBattlemageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.LEGS);
    public static final Item LIVING_BATTLEMAGE_BOOTS = new com.spege.insanetweaks.items.armor.LivingBattlemageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.FEET);

    // Sentient Battlemage Armor
    public static final Item SENTIENT_BATTLEMAGE_HELMET = new com.spege.insanetweaks.items.armor.SentientBattlemageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.HEAD);
    public static final Item SENTIENT_BATTLEMAGE_CHESTPLATE = new com.spege.insanetweaks.items.armor.SentientBattlemageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.CHEST);
    public static final Item SENTIENT_BATTLEMAGE_LEGGINGS = new com.spege.insanetweaks.items.armor.SentientBattlemageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.LEGS);
    public static final Item SENTIENT_BATTLEMAGE_BOOTS = new com.spege.insanetweaks.items.armor.SentientBattlemageArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.FEET);

    // Living Warlock Armor (originally Parasite Mage)
    public static final Item PARASITE_WIZARD_HELMET = new com.spege.insanetweaks.items.armor.LivingWarlockArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.HEAD);
    public static final Item PARASITE_WIZARD_CHESTPLATE = new com.spege.insanetweaks.items.armor.LivingWarlockArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.CHEST);
    public static final Item PARASITE_WIZARD_LEGGINGS = new com.spege.insanetweaks.items.armor.LivingWarlockArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.LEGS);
    public static final Item PARASITE_WIZARD_BOOTS = new com.spege.insanetweaks.items.armor.LivingWarlockArmorItem(
            net.minecraft.inventory.EntityEquipmentSlot.FEET);

    public static final Item LIVING_AEGIS = new com.spege.insanetweaks.items.shield.LivingAegisItem();
    public static final Item SENTIENT_AEGIS = new com.spege.insanetweaks.items.shield.SentientAegisItem();
    public static final Item INFERNAL_CROWN = new com.spege.insanetweaks.baubles.ItemInfernalCrownArtefact();
    public static final Item ZHONYAS_HOURGLASS = new ItemZhonyasHourglassArtefact();
    public static final Item RESTORATION_HOURGLASS = new com.spege.insanetweaks.baubles.ItemRestorationHourglassArtefact();

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

    // Corrupted fruit loop (Blessed Ring gate)
    public static final Item CORRUPTED_SEED_FRAGMENT = new com.spege.insanetweaks.items.fruit.CorruptedSeedFragmentItem();
    public static final Item CORRUPTED_SEED          = new com.spege.insanetweaks.items.fruit.CorruptedSeedItem();
    public static final Item CORRUPTED_FRUIT         = new com.spege.insanetweaks.items.fruit.CorruptedFruitItem();

    /** All Bauble Fruit items  Efor convenient bulk registration / model registration. */
    private static final Item[] ALL_BAUBLE_FRUITS = {
        BAUBLE_FRUIT_RING, BAUBLE_FRUIT_AMULET, BAUBLE_FRUIT_BODY,
        BAUBLE_FRUIT_HEAD, BAUBLE_FRUIT_CHARM,  BAUBLE_FRUIT_BELT,
        BAUBLE_FRUIT_ELYTRA, BAUBLE_FRUIT_TOTEM, BAUBLE_FRUIT_TRINKET
    };

    /** Typed fruits for the corrupted-fruit random unlock. Defensive copy. */
    public static Item[] getAllBaubleFruits() {
        return ALL_BAUBLE_FRUITS.clone();
    }

    private static final Item[] ALL_WIZARDRY_CORES = WizardryCoreItems.ALL_CORES;

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // Items gated by Golden Book module
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
            event.getRegistry().registerAll(LIVING_SPELLBLADE, SENTIENT_SPELLBLADE);
            event.getRegistry().registerAll(LIVING_WAND, SENTIENT_WAND);
            event.getRegistry().registerAll(ADAPTATION_UPGRADE, ARCANE_ADAPTED_FRUIT);
            event.getRegistry().registerAll(GOLDEN_BOOK, RUPTER_SOLIED, LIVING_AEGIS, SENTIENT_AEGIS, INFERNAL_CROWN, ZHONYAS_HOURGLASS, RESTORATION_HOURGLASS);

            // Crafting-component clones: only register when swparasites is NOT present, so the
            // originals aren't duplicated. Recipes resolve via the OreDictionary bridge either way.
            if (!Loader.isModLoaded("swparasites")) {
                event.getRegistry().registerAll(LIVING_NUCLEUS, INFECTIOUS_LONG_BLADE_FRAGMENT);
            }
            event.getRegistry().registerAll(
                SENTIENT_WARLOCK_HELMET, SENTIENT_WARLOCK_CHESTPLATE, SENTIENT_WARLOCK_LEGGINGS, SENTIENT_WARLOCK_BOOTS,
                PARASITE_WIZARD_HELMET, PARASITE_WIZARD_CHESTPLATE, PARASITE_WIZARD_LEGGINGS, PARASITE_WIZARD_BOOTS,
                LIVING_BATTLEMAGE_HELMET, LIVING_BATTLEMAGE_CHESTPLATE, LIVING_BATTLEMAGE_LEGGINGS, LIVING_BATTLEMAGE_BOOTS,
                SENTIENT_BATTLEMAGE_HELMET, SENTIENT_BATTLEMAGE_CHESTPLATE, SENTIENT_BATTLEMAGE_LEGGINGS, SENTIENT_BATTLEMAGE_BOOTS
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
            event.getRegistry().registerAll(CORRUPTED_SEED_FRAGMENT, CORRUPTED_SEED, CORRUPTED_FRUIT);
        }

        // NOTE: OreDictionary bridge registration is NOT done here. During the Item event the
        // cross-mod handler order is not guaranteed, so a swparasites:* lookup can still return
        // null (its Register<Item> may run after ours even with 'after:swparasites'). It is done
        // in ModRecipes at Register<IRecipe> instead, which is guaranteed to run after every
        // mod's Register<Item> completes. See ModRecipes.registerOreEntries.
    }

    @SubscribeEvent
    public static void registerModels(net.minecraftforge.client.event.ModelRegistryEvent event) {
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
            registerModel(GOLDEN_BOOK);
            registerModel(RUPTER_SOLIED);
            if (!Loader.isModLoaded("swparasites")) {
                registerModel(LIVING_NUCLEUS);
                registerModel(INFECTIOUS_LONG_BLADE_FRAGMENT);
            }
            ((com.spege.insanetweaks.items.spellblade.BridgeSpellblade)LIVING_SPELLBLADE).registerModel();
            ((com.spege.insanetweaks.items.spellblade.BridgeSpellblade)SENTIENT_SPELLBLADE).registerModel();
            
            registerModel(LIVING_WAND);
            registerModel(SENTIENT_WAND);
            registerModel(ADAPTATION_UPGRADE);
            registerModel(ARCANE_ADAPTED_FRUIT);

            // Armor Models
            registerModel(SENTIENT_WARLOCK_HELMET);
            registerModel(SENTIENT_WARLOCK_CHESTPLATE);
            registerModel(SENTIENT_WARLOCK_LEGGINGS);
            registerModel(SENTIENT_WARLOCK_BOOTS);

            registerModel(LIVING_BATTLEMAGE_HELMET);
            registerModel(LIVING_BATTLEMAGE_CHESTPLATE);
            registerModel(LIVING_BATTLEMAGE_LEGGINGS);
            registerModel(LIVING_BATTLEMAGE_BOOTS);

            registerModel(SENTIENT_BATTLEMAGE_HELMET);
            registerModel(SENTIENT_BATTLEMAGE_CHESTPLATE);
            registerModel(SENTIENT_BATTLEMAGE_LEGGINGS);
            registerModel(SENTIENT_BATTLEMAGE_BOOTS);

            registerModel(PARASITE_WIZARD_HELMET);
            registerModel(PARASITE_WIZARD_CHESTPLATE);
            registerModel(PARASITE_WIZARD_LEGGINGS);
            registerModel(PARASITE_WIZARD_BOOTS);

            registerModel(LIVING_AEGIS);
            registerModel(SENTIENT_AEGIS);
            registerModel(INFERNAL_CROWN);
            registerModel(ZHONYAS_HOURGLASS);
            registerModel(RESTORATION_HOURGLASS);
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
            registerModel(CORRUPTED_SEED_FRAGMENT);
            registerModel(CORRUPTED_SEED);
            registerModel(CORRUPTED_FRUIT);
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
