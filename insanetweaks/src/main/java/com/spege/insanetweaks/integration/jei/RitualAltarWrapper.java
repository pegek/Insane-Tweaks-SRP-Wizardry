package com.spege.insanetweaks.integration.jei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.api.RitualRecipe;
import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.registry.WizardryItems;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.item.ItemStack;

/**
 * JEI view of one {@link RitualRecipe}.
 *
 * <p>Shows the pattern in its UNROTATED form. The ritual itself matches all four rotations, but a
 * recipe page has a fixed orientation and showing one canonical layout is both what a player expects
 * from a crafting-style grid and what keeps the page readable - four rotations of the same recipe
 * would be four entries saying the same thing.
 */
public class RitualAltarWrapper implements IRecipeWrapper {

    private final RitualRecipe recipe;

    public RitualAltarWrapper(RitualRecipe recipe) {
        this.recipe = recipe;
    }

    public RitualRecipe getRecipe() {
        return this.recipe;
    }

    @Override
    public void getIngredients(@Nonnull IIngredients ingredients) {
        List<List<ItemStack>> inputs = new ArrayList<>(RitualRecipe.GRID_SIZE);
        for (int i = 0; i < RitualRecipe.GRID_SIZE; i++) {
            // getMatchingStacks() is empty for an empty position, which JEI renders as a blank slot -
            // exactly right, since "this position must stay empty" is a real part of the pattern.
            inputs.add(Arrays.asList(this.recipe.getIngredient(i).getMatchingStacks()));
        }
        // The four receptacles, N, E, S, W - display only, the ritual reads them from the world. One
        // shows Abomination dust (at least one must burn it), the other three cycle every element.
        List<ItemStack> abomination = abominationDust();
        List<ItemStack> anyElement = allElementDusts();
        inputs.add(abomination);
        inputs.add(anyElement);
        inputs.add(anyElement);
        inputs.add(anyElement);
        ingredients.setInputLists(VanillaTypes.ITEM, inputs);
        ingredients.setOutput(VanillaTypes.ITEM, this.recipe.getResult());
    }

    private static List<ItemStack> abominationDust() {
        List<ItemStack> out = new ArrayList<>(1);
        if (ModElements.EXTENDED && ModElements.ABOMINATION != null) {
            out.add(new ItemStack(WizardryItems.spectral_dust, 1, ModElements.ABOMINATION.ordinal()));
        }
        return out;
    }

    /** One spectral dust per element; MAGIC has no dust (metadata 0 is not a valid element dust). */
    private static List<ItemStack> allElementDusts() {
        List<ItemStack> out = new ArrayList<>();
        for (Element element : Element.values()) {
            if (element != Element.MAGIC) {
                out.add(new ItemStack(WizardryItems.spectral_dust, 1, element.ordinal()));
            }
        }
        return out;
    }
}
