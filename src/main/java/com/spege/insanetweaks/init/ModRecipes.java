package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import javax.annotation.Nonnull;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * Registers fallback crafting recipes for all items dependent on srpextra.
 *
 * These recipes are ONLY registered when "srpextra" is NOT loaded.
 * When srpextra IS present, the JSON recipes in assets/insanetweaks/recipes/
 * take over.
 *
 * Approach: Podejście B — JSON files remain the primary recipes.
 * Java fallbacks are registered programmatically via
 * RegistryEvent.Register<IRecipe>.
 *
 * srpextra substitutions (fallback items when srpextra is absent):
 * srpextra:tightening_buckle    → srparasites:infectious_blade_fragment
 * srpextra:sturdy_armor_plates  → srparasites:hive_scrap
 * srpextra:flexible_cloth       → insanetweaks:rupter_solied
 *
 * Items covered (5):
 * - living_aegis
 * - parasite_mage_helmet
 * - parasite_mage_chestplate
 * - parasite_mage_leggings
 * - parasite_mage_boots
 */
@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
@SuppressWarnings("null") // safeItem() guarantees non-null via IllegalStateException; IDE cannot infer this
public class ModRecipes {

    /**
     * OreDictionary bridge for the swparasites crafting components. Registered here, on the
     * Recipe event, rather than on the Item event: Register&lt;IRecipe&gt; is guaranteed to fire
     * AFTER every mod's Register&lt;Item&gt; has completed, so the {@code swparasites:*} lookup in
     * {@link ModOreDict} always resolves when swparasites is present (during the Item event the
     * cross-mod handler order is not guaranteed, which is why the bridge previously ended up
     * empty and our Living weapons became uncraftable with swparasites installed).
     *
     * HIGHEST priority so the ore names are populated before {@link #registerRecipes} builds its
     * {@code ShapedOreRecipe} fallbacks. (Forge's {@code OreIngredient} is dynamic and shares the
     * ore's backing list, so JSON {@code forge:ore_dict} recipes resolve regardless of order, but
     * running first keeps the Java fallback path unambiguous too.)
     */
    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGHEST)
    public static void registerOreEntries(RegistryEvent.Register<IRecipe> event) {
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) {
            ModOreDict.register();
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST)
    public static void removeRecipes(RegistryEvent.Register<IRecipe> event) {
        if (com.spege.insanetweaks.config.ModConfig.tombstone.enableTombstoneTweaks && 
            com.spege.insanetweaks.config.ModConfig.tombstone.disableEnchantKeyRecipe) {
            net.minecraftforge.registries.IForgeRegistryModifiable<IRecipe> modRegistry = 
                (net.minecraftforge.registries.IForgeRegistryModifiable<IRecipe>) event.getRegistry();
            modRegistry.remove(new ResourceLocation("tombstone", "enchanted_grave_key"));
        }
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!Loader.isModLoaded("defiledlands")) {
            registerFallback(event, "golden_book_fallback",
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "golden_book_fallback"),
                            new ItemStack(safeItem(InsaneTweaksMod.MODID, "golden_book"), 2),
                            "GNG",
                            "RkR",
                            "TET",
                            'G', new ItemStack(safeItem("minecraft", "gold_block")),
                            'N', new ItemStack(safeItem("minecraft", "nether_star")),
                            'R', new ItemStack(safeItem(InsaneTweaksMod.MODID, "rupter_solied")),
                            'k', new ItemStack(safeItem("minecraft", "bookshelf")),
                            'T', new ItemStack(safeItem("minecraft", "enchanting_table")),
                            'E', new ItemStack(safeItem("ancientspellcraft", "empty_mystic_spell_book"))));
        }

        // ---------------------------------------------------------------------
        // Crafting-component clone recipes. Registered ONLY when swparasites is absent,
        // matching the conditional item registration in ModItems. When swparasites is present
        // its own items + acquisition are used (the ore-dict bridge routes recipes to them), so
        // registering these would just spam "unknown result item" errors for the unregistered
        // clones. Placed BEFORE the srpextra early-return because they don't depend on srpextra.
        // ---------------------------------------------------------------------
        if (!Loader.isModLoaded("swparasites")) {
            // living_nucleus: 1:1 clone of the swparasites recipe (living_core + adapted drops
            // + assimilated flesh) so the crafting tier/cost matches the original exactly.
            registerFallback(event, "living_nucleus",
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "living_nucleus"),
                            new ItemStack(safeItem(InsaneTweaksMod.MODID, "living_nucleus")),
                            " S ",
                            "FLF",
                            " M ",
                            'S', new ItemStack(safeItem("srparasites", "ada_summoner_drop")),
                            'L', new ItemStack(safeItem("srparasites", "living_core")),
                            'M', new ItemStack(safeItem("srparasites", "ada_manducater_drop")),
                            'F', new ItemStack(safeItem("srparasites", "assimilated_flesh"))));

            // infectious_long_blade_fragment: swparasites has NO recipe for it (obtained via SRP
            // configurable loot there). We give it a sensible thematic recipe - two small
            // infectious blade fragments welded onto a hardened bone handle into one long blade.
            registerFallback(event, "infectious_long_blade_fragment",
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "infectious_long_blade_fragment"),
                            new ItemStack(safeItem(InsaneTweaksMod.MODID, "infectious_long_blade_fragment")),
                            "B",
                            "B",
                            "H",
                            'B', new ItemStack(safeItem("srparasites", "infectious_blade_fragment")),
                            'H', new ItemStack(safeItem("srparasites", "hardened_bone_handle"))));
        }

        // ---------------------------------------------------------------------
        // Sanctuary Nexus. Ingredients come from SRP + Ancient Spellcraft, so gate on both
        // (safeItem throws if an ingredient is missing). Output is the sanctuary_core ItemBlock,
        // which only exists when the Sanctuary module + SRP are on. Registered BEFORE the srpextra
        // early-return below (this recipe is independent of srpextra; the pack ships srpextra).
        //   Eye of Beholder | Beacon      | Eye of Beholder
        //   (empty)         | Level Clock | (empty)
        //   Devoritium Blk  | False Apple | Devoritium Blk
        // ---------------------------------------------------------------------
        if (ModBlocks.SANCTUARY_CORE != null
                && Loader.isModLoaded("srparasites") && Loader.isModLoaded("ancientspellcraft")) {
            registerFallback(event, "sanctuary_nexus",
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "sanctuary_nexus"),
                            new ItemStack(ModBlocks.SANCTUARY_CORE),
                            "PBP",
                            " C ",
                            "DFD",
                            'P', new ItemStack(safeItem("srparasites", "pearl")),
                            'B', new ItemStack(safeItem("minecraft", "beacon")),
                            'C', new ItemStack(safeItem("srparasites", "levelclock")),
                            'D', new ItemStack(safeItem("ancientspellcraft", "devoritium_block")),
                            'F', new ItemStack(safeItem("srparasites", "false_apple"))));
        }

        // Guard: only register srpextra fallbacks if srpextra is absent
        if (Loader.isModLoaded("srpextra")) {
            return;
        }

        // Fallback item mapping (srpextra substitutes)
        ItemStack bladeFrag  = new ItemStack(safeItem("srparasites", "infectious_blade_fragment")); // tightening_buckle
        ItemStack hiveScrap  = new ItemStack(safeItem("srparasites", "hive_scrap"));                // sturdy_armor_plates
        ItemStack rupter     = new ItemStack(safeItem(InsaneTweaksMod.MODID, "rupter_solied"));      // flexible_cloth

        // =====================================================================
        // living_aegis (srpextra items: sturdy_armor_plates x2, flexible_cloth x1)
        // JSON pattern:
        // " N "
        // "VSV"
        // "PCP"
        // N = ore-dict itLivingNucleus (swparasites:living_nucleus or insanetweaks:living_nucleus)
        // V = srparasites:vile_shell
        // S = ancientspellcraft:battlemage_shield
        // P = srpextra:sturdy_armor_plates → dirt
        // C = srpextra:flexible_cloth → dirt
        // =====================================================================
        registerFallback(event, "living_aegis_fallback",
                new ShapedOreRecipe(
                        new ResourceLocation(InsaneTweaksMod.MODID, "living_aegis_fallback"),
                        new ItemStack(safeItem(InsaneTweaksMod.MODID, "living_aegis")),
                        " N ",
                        "VSV",
                        "PCP",
                        'N', ModOreDict.ORE_LIVING_NUCLEUS, // ore-dict: swparasites clone or insanetweaks fallback
                        'V', new ItemStack(safeItem("srparasites", "vile_shell")),
                        'S', new ItemStack(safeItem("ancientspellcraft", "battlemage_shield")),
                        'P', hiveScrap,   // srpextra:sturdy_armor_plates
                        'C', rupter));    // srpextra:flexible_cloth

        // =====================================================================
        // parasite_mage_helmet (srpextra items: tightening_buckle x1,
        // sturdy_armor_plates x2)
        // JSON pattern:
        // " B "
        // "PSP"
        // " L "
        // B = srpextra:tightening_buckle → dirt
        // P = srpextra:sturdy_armor_plates → dirt
        // S = any ebwizardry:battlemage_helmet variant (oredict workaround via OreDict
        // or first entry)
        // L = srparasites:living_core
        // Note: JSON uses a list for 'S'. Java recipes don't natively support lists.
        // We register one recipe per ebwizardry helmet variant.
        // =====================================================================
        String[] warlockHelmetVariants = {
                "warlock_helmet", "warlock_helmet_fire", "warlock_helmet_ice",
                "warlock_helmet_lightning", "warlock_helmet_necromancy",
                "warlock_helmet_earth", "warlock_helmet_sorcery", "warlock_helmet_healing"
        };
        for (String variant : warlockHelmetVariants) {
            net.minecraft.item.Item helmetItem = ForgeRegistries.ITEMS
                    .getValue(new ResourceLocation("ebwizardry", variant));
            if (helmetItem == null)
                continue;
            registerFallback(event, "parasite_mage_helmet_fallback_" + variant,
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "parasite_mage_helmet_fallback"),
                            new ItemStack(ModItems.PARASITE_WIZARD_HELMET),
                            " B ",
                            "PSP",
                            " L ",
                            'B', bladeFrag, // srpextra:tightening_buckle
                            'P', hiveScrap, // srpextra:sturdy_armor_plates
                            'S', new ItemStack(helmetItem, 1, 32767),
                            'L', new ItemStack(safeItem("srparasites", "living_core"))));
        }

        // =====================================================================
        // parasite_mage_chestplate (srpextra items: sturdy_armor_plates x3,
        // flexible_cloth x2)
        // JSON pattern:
        // " P "
        // "FCF"
        // "PSP"
        // P = srpextra:sturdy_armor_plates → dirt
        // F = srpextra:flexible_cloth → dirt
        // C = srparasites:living_core
        // S = any ebwizardry:battlemage_chestplate variant
        // =====================================================================
        String[] warlockChestVariants = {
                "warlock_chestplate", "warlock_chestplate_fire", "warlock_chestplate_ice",
                "warlock_chestplate_lightning", "warlock_chestplate_necromancy",
                "warlock_chestplate_earth", "warlock_chestplate_sorcery", "warlock_chestplate_healing"
        };
        for (String variant : warlockChestVariants) {
            net.minecraft.item.Item chestItem = ForgeRegistries.ITEMS
                    .getValue(new ResourceLocation("ebwizardry", variant));
            if (chestItem == null)
                continue;
            registerFallback(event, "parasite_mage_chestplate_fallback_" + variant,
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "parasite_mage_chestplate_fallback"),
                            new ItemStack(ModItems.PARASITE_WIZARD_CHESTPLATE),
                            " P ",
                            "FCF",
                            "PSP",
                            'P', hiveScrap, // srpextra:sturdy_armor_plates
                            'F', rupter,    // srpextra:flexible_cloth
                            'C', new ItemStack(safeItem("srparasites", "living_core")),
                            'S', new ItemStack(chestItem, 1, 32767)));
        }

        // =====================================================================
        // parasite_mage_leggings (srpextra items: flexible_cloth x2, tightening_buckle
        // x4)
        // JSON pattern:
        // "F F"
        // "BCB"
        // "BSB"
        // F = srpextra:flexible_cloth → dirt
        // B = srpextra:tightening_buckle → dirt
        // C = srparasites:living_core
        // S = any ebwizardry:battlemage_leggings variant
        // =====================================================================
        String[] warlockLeggingVariants = {
                "warlock_leggings", "warlock_leggings_fire", "warlock_leggings_ice",
                "warlock_leggings_lightning", "warlock_leggings_necromancy",
                "warlock_leggings_earth", "warlock_leggings_sorcery", "warlock_leggings_healing"
        };
        for (String variant : warlockLeggingVariants) {
            net.minecraft.item.Item legItem = ForgeRegistries.ITEMS
                    .getValue(new ResourceLocation("ebwizardry", variant));
            if (legItem == null)
                continue;
            registerFallback(event, "parasite_mage_leggings_fallback_" + variant,
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "parasite_mage_leggings_fallback"),
                            new ItemStack(ModItems.PARASITE_WIZARD_LEGGINGS),
                            "F F",
                            "BCB",
                            "BSB",
                            'F', rupter,    // srpextra:flexible_cloth
                            'B', bladeFrag, // srpextra:tightening_buckle
                            'C', new ItemStack(safeItem("srparasites", "living_core")),
                            'S', new ItemStack(legItem, 1, 32767)));
        }

        // =====================================================================
        // parasite_mage_boots (srpextra items: flexible_cloth x1, sturdy_armor_plates
        // x2)
        // JSON pattern:
        // " F "
        // "PCP"
        // " S "
        // F = srpextra:flexible_cloth → dirt
        // P = srpextra:sturdy_armor_plates → dirt
        // C = srparasites:living_core
        // S = any ebwizardry:battlemage_boots variant
        // =====================================================================
        String[] warlockBootsVariants = {
                "warlock_boots", "warlock_boots_fire", "warlock_boots_ice",
                "warlock_boots_lightning", "warlock_boots_necromancy",
                "warlock_boots_earth", "warlock_boots_sorcery", "warlock_boots_healing"
        };
        for (String variant : warlockBootsVariants) {
            net.minecraft.item.Item bootsItem = ForgeRegistries.ITEMS
                    .getValue(new ResourceLocation("ebwizardry", variant));
            if (bootsItem == null)
                continue;
            registerFallback(event, "parasite_mage_boots_fallback_" + variant,
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "parasite_mage_boots_fallback"),
                            new ItemStack(ModItems.PARASITE_WIZARD_BOOTS),
                            " F ",
                            "PCP",
                            " S ",
                            'F', rupter,    // srpextra:flexible_cloth
                            'P', hiveScrap, // srpextra:sturdy_armor_plates
                            'C', new ItemStack(safeItem("srparasites", "living_core")),
                            'S', new ItemStack(bootsItem, 1, 32767)));
        }

        // =====================================================================
        // living_battlemage fallbacks
        // =====================================================================
        String[] battlemageHelmetVariants = {
                "battlemage_helmet", "battlemage_helmet_fire", "battlemage_helmet_ice",
                "battlemage_helmet_lightning", "battlemage_helmet_necromancy",
                "battlemage_helmet_earth", "battlemage_helmet_sorcery", "battlemage_helmet_healing"
        };
        for (String variant : battlemageHelmetVariants) {
            net.minecraft.item.Item helmetItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ebwizardry", variant));
            if (helmetItem == null) continue;
            registerFallback(event, "living_battlemage_helmet_fallback_" + variant,
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "living_battlemage_helmet_fallback"),
                            new ItemStack(ModItems.LIVING_BATTLEMAGE_HELMET),
                            "PBP", "PSP", " L ",
                            'B', bladeFrag, 'P', hiveScrap,
                            'S', new ItemStack(helmetItem, 1, 32767), 'L', new ItemStack(safeItem("srparasites", "living_core"))));
        }

        String[] battlemageChestVariants = {
                "battlemage_chestplate", "battlemage_chestplate_fire", "battlemage_chestplate_ice",
                "battlemage_chestplate_lightning", "battlemage_chestplate_necromancy",
                "battlemage_chestplate_earth", "battlemage_chestplate_sorcery", "battlemage_chestplate_healing"
        };
        for (String variant : battlemageChestVariants) {
            net.minecraft.item.Item chestItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ebwizardry", variant));
            if (chestItem == null) continue;
            registerFallback(event, "living_battlemage_chestplate_fallback_" + variant,
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "living_battlemage_chestplate_fallback"),
                            new ItemStack(ModItems.LIVING_BATTLEMAGE_CHESTPLATE),
                            "FPF", "PCP", "PSP",
                            'P', hiveScrap, 'F', rupter,
                            'C', new ItemStack(safeItem("srparasites", "living_core")), 'S', new ItemStack(chestItem, 1, 32767)));
        }

        String[] battlemageLeggingVariants = {
                "battlemage_leggings", "battlemage_leggings_fire", "battlemage_leggings_ice",
                "battlemage_leggings_lightning", "battlemage_leggings_necromancy",
                "battlemage_leggings_earth", "battlemage_leggings_sorcery", "battlemage_leggings_healing"
        };
        for (String variant : battlemageLeggingVariants) {
            net.minecraft.item.Item legItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ebwizardry", variant));
            if (legItem == null) continue;
            registerFallback(event, "living_battlemage_leggings_fallback_" + variant,
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "living_battlemage_leggings_fallback"),
                            new ItemStack(ModItems.LIVING_BATTLEMAGE_LEGGINGS),
                            "FPF", "BCB", "BSB",
                            'F', rupter, 'B', bladeFrag, 'P', hiveScrap,
                            'C', new ItemStack(safeItem("srparasites", "living_core")), 'S', new ItemStack(legItem, 1, 32767)));
        }

        String[] battlemageBootsVariants = {
                "battlemage_boots", "battlemage_boots_fire", "battlemage_boots_ice",
                "battlemage_boots_lightning", "battlemage_boots_necromancy",
                "battlemage_boots_earth", "battlemage_boots_sorcery", "battlemage_boots_healing"
        };
        for (String variant : battlemageBootsVariants) {
            net.minecraft.item.Item bootsItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ebwizardry", variant));
            if (bootsItem == null) continue;
            registerFallback(event, "living_battlemage_boots_fallback_" + variant,
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "living_battlemage_boots_fallback"),
                            new ItemStack(ModItems.LIVING_BATTLEMAGE_BOOTS),
                            "BFB", "PCP", " S ",
                            'F', rupter, 'P', hiveScrap, 'B', bladeFrag,
                            'C', new ItemStack(safeItem("srparasites", "living_core")), 'S', new ItemStack(bootsItem, 1, 32767)));
        }

    }

    /**
     * Null-safe item lookup from ForgeRegistries.
     * Throws IllegalStateException during init if an item is missing, which is the
     * correct behavior — a missing required ingredient means the modpack is
     * misconfigured.
     * This also satisfies @Nonnull contracts and eliminates IDE @Nullable warnings.
     */
    @Nonnull
    private static Item safeItem(String modid, String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(modid, path));
        if (item == null) {
            throw new IllegalStateException(
                    "[InsaneTweaks] ModRecipes: required item not found: " + modid + ":" + path);
        }
        return item;
    }

    /**
     * Helper — assigns a unique ResourceLocation to the recipe and registers it.
     * Using a unique name per recipe is mandatory in 1.12.2; duplicate names cause
     * silent overwrite or FMLMissingMappingsEvent warnings.
     */
    private static void registerFallback(RegistryEvent.Register<IRecipe> event,
            String name, ShapedOreRecipe recipe) {
        recipe.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, name));
        event.getRegistry().register(recipe);
    }
}
