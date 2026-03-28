package com.spege.insanetweaks.integration.jei;

import com.spege.insanetweaks.init.ModItems;
import electroblob.wizardry.integration.jei.ImbuementAltarRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Connects Insane Tweaks items and custom Imbuement Altar recipes to the official JEI manager,
 * allowing players to easily browse custom and hidden recipes directly from the JEI GUI.
 */
@JEIPlugin
public class InsaneTweaksJEIPlugin implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        try {
            // Retrieve Spectral Dust item reference safely
            Item spectralDust = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ebwizardry", "spectral_dust"));
            if (spectralDust == null) return;

            // Generate dust items (metadata corresponds to Element ordinal: 1=FIRE, 3=LIGHTNING, 4=NECROMANCY, 5=EARTH, 6=SORCERY, 7=HEALING)
            ItemStack sorceryDust = new ItemStack(spectralDust, 1, 6);
            ItemStack necromancyDust = new ItemStack(spectralDust, 1, 4);
            ItemStack earthDust = new ItemStack(spectralDust, 1, 5);
            ItemStack healingDust = new ItemStack(spectralDust, 1, 7);
            ItemStack fireDust = new ItemStack(spectralDust, 1, 1);
            ItemStack lightningDust = new ItemStack(spectralDust, 1, 3);

            // Potency Core Inputs (2x Sorcery, 2x Necromancy)
            List<List<ItemStack>> potencyDusts = Arrays.asList(
                Collections.singletonList(sorceryDust), Collections.singletonList(necromancyDust),
                Collections.singletonList(sorceryDust), Collections.singletonList(necromancyDust)
            );

            // Cost Core Inputs (2x Earth, 2x Healing)
            List<List<ItemStack>> costDusts = Arrays.asList(
                Collections.singletonList(earthDust), Collections.singletonList(healingDust),
                Collections.singletonList(earthDust), Collections.singletonList(healingDust)
            );

            // Speedcast Core Inputs (2x Fire, 2x Lightning)
            List<List<ItemStack>> speedcastDusts = Arrays.asList(
                Collections.singletonList(fireDust), Collections.singletonList(lightningDust),
                Collections.singletonList(fireDust), Collections.singletonList(lightningDust)
            );

            // Define the resulting items
            ItemStack potencyCore = new ItemStack(ModItems.POTENCY_CORE, 1);
            ItemStack costCore = new ItemStack(ModItems.COST_CORE, 1);
            ItemStack speedcastCore = new ItemStack(ModItems.SPEEDCAST_CORE, 1);

            // List of all master wand resource paths in basic EBWizardry
            String[] masterWandNames = {
                "master_wand", "master_fire_wand", "master_ice_wand", "master_lightning_wand",
                "master_necromancy_wand", "master_earth_wand", "master_sorcery_wand", "master_healing_wand"
            };

            List<ImbuementAltarRecipe> generatedRecipes = new ArrayList<>();

            for (String wandName : masterWandNames) {
                Item wandItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ebwizardry", wandName));
                if (wandItem != null) {
                    ItemStack masterWand = new ItemStack(wandItem, 1);
                    generatedRecipes.add(new ImbuementAltarRecipe(masterWand, potencyDusts, potencyCore));
                    generatedRecipes.add(new ImbuementAltarRecipe(masterWand, costDusts, costCore));
                    generatedRecipes.add(new ImbuementAltarRecipe(masterWand, speedcastDusts, speedcastCore));
                }
            }

            // Inject the generated recipes directly into the official Category UID created by Electroblob's Wizardry
            registry.addRecipes(generatedRecipes, "ebwizardry:imbuement_altar");
            
        } catch (Exception e) {
            // Failsafe: In case "ebwizardry:" item names evaluate unexpectedly, we prevent a complete JEI crash
            System.err.println("[Insane Tweaks] JEI Integration aborted due to missing dependent items.");
            e.printStackTrace();
        }
    }
}
