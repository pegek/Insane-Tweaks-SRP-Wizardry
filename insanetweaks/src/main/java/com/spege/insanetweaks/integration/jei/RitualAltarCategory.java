package com.spege.insanetweaks.integration.jei;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.api.RitualRecipe;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

/**
 * JEI category for the 3x3 Imbuement Altar ritual.
 *
 * <p>Laid out to read as a crafting table on purpose - a 3x3 grid, an arrow, one output - because
 * that is the comparison the mechanic is built on and a player should not have to be told twice.
 *
 * <p>Ships no texture of its own. The background is a blank drawable (JEI draws each slot's frame
 * itself from {@code init}) and the arrow is borrowed from vanilla's crafting table GUI, so there is
 * no new PNG to keep in sync with a resolution or a resource pack.
 */
public class RitualAltarCategory implements IRecipeCategory<RitualAltarWrapper> {

    public static final String UID = "insanetweaks:ritual_altar";

    private static final ResourceLocation CRAFTING_GUI =
            new ResourceLocation("minecraft", "textures/gui/container/crafting_table.png");

    private static final int SLOT_SIZE = 18;
    private static final int GRID_X = 0;
    private static final int GRID_Y = 0;
    private static final int ARROW_X = 62;
    private static final int ARROW_Y = 19;
    private static final int OUTPUT_X = 92;
    private static final int OUTPUT_Y = 18;
    private static final int WIDTH = 110;
    private static final int HEIGHT = 54;

    private final IDrawable background;
    private final IDrawable arrow;

    public RitualAltarCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        // Vanilla crafting arrow: u=90, v=35, 22x15 in crafting_table.png.
        this.arrow = helper.createDrawable(CRAFTING_GUI, 90, 35, 22, 15);
    }

    @Override
    @Nonnull
    public String getUid() {
        return UID;
    }

    @Override
    @Nonnull
    public String getTitle() {
        return I18n.format("integration.jei.category.insanetweaks:ritual_altar");
    }

    @Override
    @Nonnull
    public String getModName() {
        return "Insane Tweaks";
    }

    @Override
    @Nonnull
    public IDrawable getBackground() {
        return this.background;
    }

    @Override
    public void drawExtras(@Nonnull Minecraft minecraft) {
        this.arrow.draw(minecraft, ARROW_X, ARROW_Y);
    }

    @Override
    public void setRecipe(@Nonnull IRecipeLayout recipeLayout, @Nonnull RitualAltarWrapper wrapper,
            @Nonnull IIngredients ingredients) {
        IGuiItemStackGroup slots = recipeLayout.getItemStacks();

        for (int i = 0; i < RitualRecipe.GRID_SIZE; i++) {
            slots.init(i, true, GRID_X + (i % 3) * SLOT_SIZE, GRID_Y + (i / 3) * SLOT_SIZE);
        }
        slots.init(RitualRecipe.GRID_SIZE, false, OUTPUT_X, OUTPUT_Y);

        // set(IIngredients) maps the input list onto the input slots in order and the output onto the
        // single output slot, which is exactly the order the wrapper builds them in.
        slots.set(ingredients);
    }
}
