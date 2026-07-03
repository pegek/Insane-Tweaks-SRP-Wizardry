package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

/**
 * OreDictionary bridge for crafting components that used to come only from swparasites
 * (SW: Parasites). Each component gets an ore name that resolves to whichever variant is
 * actually present:
 *
 *   - swparasites installed  -> ore name -> swparasites:&lt;item&gt; (our clone is not registered)
 *   - swparasites absent      -> ore name -> insanetweaks:&lt;item&gt; (our fallback clone)
 *
 * All spellblade / aegis / wand / upgrade recipes reference the ore name (via
 * {@code forge:ore_dict} in JSON, or {@link OreIngredient} in {@code ModRecipes}), so the
 * exact same recipes work whether or not swparasites is installed. This is what lets us drop
 * the hard swparasites dependency without breaking crafting.
 *
 * Must be called AFTER items are registered (i.e. from the FML init phase).
 */
public final class ModOreDict {

    public static final String ORE_LIVING_NUCLEUS = "itLivingNucleus";
    public static final String ORE_INFECTIOUS_LONG_BLADE_FRAGMENT = "itInfectiousLongBladeFragment";

    private ModOreDict() {
    }

    public static void register() {
        boolean swp = Loader.isModLoaded("swparasites");

        registerComponent(ORE_LIVING_NUCLEUS,
                swp ? foreign("swparasites", "living_nucleus") : ModItems.LIVING_NUCLEUS);
        registerComponent(ORE_INFECTIOUS_LONG_BLADE_FRAGMENT,
                swp ? foreign("swparasites", "infectious_long_blade_fragment")
                        : ModItems.INFECTIOUS_LONG_BLADE_FRAGMENT);
    }

    private static void registerComponent(String oreName, Item item) {
        if (item == null) {
            InsaneTweaksMod.LOGGER.warn(
                    "[InsaneTweaks] OreDict: no item available for '{}' — recipes using it will not resolve.",
                    oreName);
            return;
        }
        OreDictionary.registerOre(oreName, new ItemStack(item));
    }

    private static Item foreign(String modid, String path) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(modid, path));
    }
}
