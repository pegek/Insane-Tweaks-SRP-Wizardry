package com.spege.insanetweaks.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.item.ItemStack;

/**
 * Public facade for Imbuement Altar rituals - the 3x3 structure crafting system.
 *
 * <p>An addon adds a ritual with a single call from its own init phase:
 *
 * <pre>
 * RitualRegistry.register(new RitualRecipe(
 *         new ResourceLocation("yourmod", "example"),
 *         new Ingredient[] { ... nine entries ... },
 *         new ItemStack(YourItems.THING),
 *         140));
 * </pre>
 *
 * <p>Nothing here touches Electroblob's Wizardry types, so the registry itself is safe to reference
 * from anywhere. The structure detection that consumes it is not - that lives in
 * {@code com.spege.insanetweaks.ritual}.
 *
 * <p>Registration is expected during FML init, on the main thread, and the list is never mutated
 * afterwards. {@link #findMatch} is called from the server tick and only reads.
 */
public final class RitualRegistry {

    private static final List<RitualRecipe> RECIPES = new ArrayList<>();
    private static final List<RitualRecipe> VIEW = Collections.unmodifiableList(RECIPES);

    private RitualRegistry() {
    }

    /**
     * Adds a ritual. A duplicate id is refused with a warn line rather than an exception: a broken
     * addon should not take the host down, and the first registration wins so the loser is
     * identifiable in the log.
     */
    public static void register(RitualRecipe recipe) {
        if (recipe == null) {
            return;
        }
        for (int i = 0; i < RECIPES.size(); i++) {
            if (RECIPES.get(i).getId().equals(recipe.getId())) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Ritual '{}' is already registered - "
                        + "the second registration was ignored.", recipe.getId());
                return;
            }
        }
        RECIPES.add(recipe);
    }

    /** Every registered ritual, in registration order. Unmodifiable; used by JEI and by the matcher. */
    public static List<RitualRecipe> getRecipes() {
        return VIEW;
    }

    /**
     * First ritual whose pattern fits the given grid, in registration order.
     *
     * @param grid nine stacks read from the world, row-major from the north-west corner
     * @return the match, or null if nothing fits
     */
    public static Match findMatch(ItemStack[] grid) {
        for (int i = 0; i < RECIPES.size(); i++) {
            RitualRecipe recipe = RECIPES.get(i);
            int rotation = recipe.match(grid);
            if (rotation >= 0) {
                return new Match(recipe, rotation);
            }
        }
        return null;
    }

    /** A ritual plus the rotation its pattern was found in, which the consume step needs. */
    public static final class Match {

        private final RitualRecipe recipe;
        private final int rotation;

        Match(RitualRecipe recipe, int rotation) {
            this.recipe = recipe;
            this.rotation = rotation;
        }

        public RitualRecipe getRecipe() {
            return this.recipe;
        }

        public int getRotation() {
            return this.rotation;
        }
    }
}
