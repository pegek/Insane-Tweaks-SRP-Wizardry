package com.spege.tombtweaks.init;

import com.spege.tombtweaks.TombstoneTweaks;

import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Recipe removals this mod performs on Tombstone's own recipes.
 *
 * <p>Removing a recipe is save-safe — recipes are not stored in a world — which is why a disabled
 * recipe is expressed this way rather than by skipping registration. Runs at {@code LOWEST} so
 * every other mod's recipes, including the JSON ones, are already in the registry.
 */
@Mod.EventBusSubscriber(modid = TombstoneTweaks.MODID)
public class TombTweaksRecipes {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void removeRecipes(RegistryEvent.Register<IRecipe> event) {
        @SuppressWarnings("unchecked")
        net.minecraftforge.registries.IForgeRegistryModifiable<IRecipe> modRegistry =
                (net.minecraftforge.registries.IForgeRegistryModifiable<IRecipe>) event.getRegistry();

        if (com.spege.tombtweaks.config.TombTweaksConfig.tombstone.enableTombstoneTweaks
                && com.spege.tombtweaks.config.TombTweaksConfig.tombstone.disableEnchantKeyRecipe) {
            modRegistry.remove(new ResourceLocation("tombstone", "enchanted_grave_key"));
        }
    }
}
