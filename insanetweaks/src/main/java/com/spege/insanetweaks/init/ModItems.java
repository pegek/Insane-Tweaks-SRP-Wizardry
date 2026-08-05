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

    // Auto Lock Picker (Locks integration). Registered UNCONDITIONALLY - unlike the modules above,
    // this one is not gated on its config flag, because gating a registry object means turning the
    // flag off later deletes the entry from existing worlds. modules.enableAutoLockPicker and
    // Loader.isModLoaded("locks") gate the item's BEHAVIOUR instead, inside AutoLockPickerItem.
    public static final Item AUTO_LOCK_PICKER = new com.spege.insanetweaks.items.AutoLockPickerItem();

    // Property Book. Registered UNCONDITIONALLY, for the same reason as the Auto Lock Picker above:
    // modules.enablePropertyBooks gates the ANVIL HANDLER, never this registry entry, so switching
    // the module off leaves books already in a world intact (just inapplicable).
    public static final Item PROPERTY_BOOK = new com.spege.insanetweaks.items.PropertyBookItem();

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
        // Always registered - see the field comment for why this one is not config-gated.
        event.getRegistry().register(AUTO_LOCK_PICKER);

        // Also always registered. A registry object hidden behind a config flag DISAPPEARS from any
        // world saved while the flag was on, which is the one thing config must never do - so the
        // flag has to gate behaviour, not registration. This one had a second, live symptom: the
        // passive restore path in EntityPurifyingWave runs regardless of enableSrpEbWizardryBridge,
        // so with the bridge off it was handing an unregistered Item to ItemArtefact.isArtefactActive.
        event.getRegistry().register(RESTORATION_HOURGLASS);
        event.getRegistry().register(PROPERTY_BOOK);

        // Items gated by Golden Book module
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
            event.getRegistry().registerAll(LIVING_SPELLBLADE, SENTIENT_SPELLBLADE);
            event.getRegistry().registerAll(LIVING_WAND, SENTIENT_WAND);
            event.getRegistry().registerAll(ADAPTATION_UPGRADE, ARCANE_ADAPTED_FRUIT);
            event.getRegistry().registerAll(GOLDEN_BOOK, RUPTER_SOLIED, LIVING_AEGIS, SENTIENT_AEGIS, INFERNAL_CROWN, ZHONYAS_HOURGLASS);

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

        // Dragonsteelowe nunchaku - modules.enableDragonsteelNunchaku ORAZ oba mody bazowe obecne;
        // jedno i drugie siedzi w shouldRegister(). Straż MUSI być tutaj, przed pierwszym
        // dotknięciem DragonsteelNunchakuItems - patrz javadoc tamtej klasy.
        // MUSI też stać PRZED applyGearAvailability(): to ono zdejmuje wyłączonym broniom zakładkę
        // kreatywną, a nie ma czego zdejmować, dopóki bronie nie powstały. Zamiana tych dwóch
        // bloków miejscami sprawia, że gear.availability.dragonsteelNunchaku po cichu nic nie robi.
        if (com.spege.insanetweaks.items.nunchaku.DragonsteelNunchakuItems.shouldRegister()) {
            com.spege.insanetweaks.items.nunchaku.DragonsteelNunchakuItems.register(event.getRegistry());
        }

        applyGearAvailability();

        // NOTE: OreDictionary bridge registration is NOT done here. During the Item event the
        // cross-mod handler order is not guaranteed, so a swparasites:* lookup can still return
        // null (its Register<Item> may run after ours even with 'after:swparasites'). It is done
        // in ModRecipes at Register<IRecipe> instead, which is guaranteed to run after every
        // mod's Register<Item> completes. See ModRecipes.registerOreEntries.
    }

    @SubscribeEvent
    public static void registerModels(net.minecraftforge.client.event.ModelRegistryEvent event) {
        registerModel(AUTO_LOCK_PICKER);
        registerModel(RESTORATION_HOURGLASS); // registered unconditionally - see registerItems
        registerModel(PROPERTY_BOOK);

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

        // Ten sam warunek co przy rejestracji - inaczej rozjeżdżają się strony klient/serwer w
        // czytaniu. Sama pętla w registerModels() i tak chodzi po items(), więc przy wyłączonym
        // module byłaby pusta, ale guard ma mówić prawdę o tym, dlaczego jej nie ma.
        if (com.spege.insanetweaks.items.nunchaku.DragonsteelNunchakuItems.shouldRegister()) {
            com.spege.insanetweaks.items.nunchaku.DragonsteelNunchakuItems.registerModels();
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

    // =====================================================================
    // gear.availability
    //
    // "Disabled" here NEVER means unregistered. A registry object hidden behind a config flag
    // disappears from every world that was saved while the flag was on - the one thing config must
    // never do, and the same reasoning that already keeps AUTO_LOCK_PICKER and PROPERTY_BOOK
    // registered unconditionally above. So a disabled piece of gear stays in the registry, keeps
    // working in the hands of anyone who already has one, and keeps its NBT (kill counts, armour
    // adaptation, wand evolution). What goes away is the ways to GET one: hidden from the creative
    // menu and from recipe viewers here, and stripped of its recipes in ModRecipes.removeRecipes.
    // =====================================================================

    /** Items switched off in gear.availability. Populated during item registration. */
    private static final java.util.Set<Item> DISABLED_GEAR = new java.util.HashSet<>();

    /** @return true when {@code item} is gear the pack has switched off. */
    public static boolean isGearDisabled(Item item) {
        return item != null && DISABLED_GEAR.contains(item);
    }

    private static void applyGearAvailability() {
        com.spege.insanetweaks.config.categories.GearCategory.Availability cfg =
                com.spege.insanetweaks.config.ModConfig.gear.availability;

        hideIfDisabled(cfg.livingSpellblade, LIVING_SPELLBLADE);
        hideIfDisabled(cfg.sentientSpellblade, SENTIENT_SPELLBLADE);
        hideIfDisabled(cfg.livingAegis, LIVING_AEGIS);
        hideIfDisabled(cfg.sentientAegis, SENTIENT_AEGIS);
        hideIfDisabled(cfg.livingWand, LIVING_WAND);
        hideIfDisabled(cfg.sentientWand, SENTIENT_WAND);
        hideIfDisabled(cfg.livingWarlockSet, PARASITE_WIZARD_HELMET, PARASITE_WIZARD_CHESTPLATE,
                PARASITE_WIZARD_LEGGINGS, PARASITE_WIZARD_BOOTS);
        hideIfDisabled(cfg.sentientWarlockSet, SENTIENT_WARLOCK_HELMET, SENTIENT_WARLOCK_CHESTPLATE,
                SENTIENT_WARLOCK_LEGGINGS, SENTIENT_WARLOCK_BOOTS);
        hideIfDisabled(cfg.livingBattlemageSet, LIVING_BATTLEMAGE_HELMET, LIVING_BATTLEMAGE_CHESTPLATE,
                LIVING_BATTLEMAGE_LEGGINGS, LIVING_BATTLEMAGE_BOOTS);
        hideIfDisabled(cfg.sentientBattlemageSet, SENTIENT_BATTLEMAGE_HELMET,
                SENTIENT_BATTLEMAGE_CHESTPLATE, SENTIENT_BATTLEMAGE_LEGGINGS,
                SENTIENT_BATTLEMAGE_BOOTS);

        // enabled() pyta WYŁĄCZNIE o cfg.dragonsteelNunchaku. modules.enableDragonsteelNunchaku
        // rozstrzygnęło się już wyżej, przy rejestracji - gdy jest wyłączony, items() zwraca pustą
        // tablicę i nie ma tu czego ukrywać. Nie dopisuj go z powrotem do tego warunku: to był
        // dubel, dwa przełączniki o identycznym skutku.
        // Oba wołania są bezpieczne bez Better Survival i RLDragonsteel - żadne nie dotyka klasy
        // z opcjonalnego moda (typ zwracany to Item[], nie ItemDragonsteelNunchaku[]).
        hideIfDisabled(com.spege.insanetweaks.items.nunchaku.DragonsteelNunchakuItems.enabled(),
                com.spege.insanetweaks.items.nunchaku.DragonsteelNunchakuItems.items());

        if (!DISABLED_GEAR.isEmpty()) {
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] gear.availability: {} item(s) hidden from creative and left "
                            + "without a recipe. They stay registered, so existing ones keep working.",
                    Integer.valueOf(DISABLED_GEAR.size()));
        }
    }

    /**
     * Takes the items out of the creative menu (and so out of JEI, which builds its list from the
     * same call) by clearing their creative tab. Pure item state - it touches no registry.
     */
    private static void hideIfDisabled(boolean enabled, Item... items) {
        if (enabled) {
            return;
        }
        for (Item item : items) {
            if (item != null) {
                item.setCreativeTab(null);
                DISABLED_GEAR.add(item);
            }
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
