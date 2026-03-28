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

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        // Guard: only register fallbacks if srpextra is absent
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
        // N = swparasites:living_nucleus
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
                        'N', new ItemStack(safeItem("swparasites", "living_nucleus")),
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
        String[] helmetVariants = {
                "battlemage_helmet", "battlemage_helmet_fire", "battlemage_helmet_ice",
                "battlemage_helmet_lightning", "battlemage_helmet_necromancy",
                "battlemage_helmet_earth", "battlemage_helmet_sorcery"
        };
        for (String variant : helmetVariants) {
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
        String[] chestVariants = {
                "battlemage_chestplate", "battlemage_chestplate_fire", "battlemage_chestplate_ice",
                "battlemage_chestplate_lightning", "battlemage_chestplate_necromancy",
                "battlemage_chestplate_earth", "battlemage_chestplate_sorcery"
        };
        for (String variant : chestVariants) {
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
        String[] leggingVariants = {
                "battlemage_leggings", "battlemage_leggings_fire", "battlemage_leggings_ice",
                "battlemage_leggings_lightning", "battlemage_leggings_necromancy",
                "battlemage_leggings_earth", "battlemage_leggings_sorcery"
        };
        for (String variant : leggingVariants) {
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
        String[] bootsVariants = {
                "battlemage_boots", "battlemage_boots_fire", "battlemage_boots_ice",
                "battlemage_boots_lightning", "battlemage_boots_necromancy",
                "battlemage_boots_earth", "battlemage_boots_sorcery"
        };
        for (String variant : bootsVariants) {
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
