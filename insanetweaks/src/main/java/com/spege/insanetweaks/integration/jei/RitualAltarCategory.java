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
    /** The grid is inset by one slot so a receptacle fits on every side of it. */
    private static final int GRID_X = 18;
    private static final int GRID_Y = 18;
    /** Receptacle slots N, E, S, W, each next to that side's middle cell - the order the wrapper uses. */
    private static final int[][] RECEPTACLES = { { 36, 0 }, { 72, 36 }, { 36, 72 }, { 0, 36 } };
    private static final int ARROW_X = 98;
    private static final int ARROW_Y = 37;
    private static final int OUTPUT_X = 128;
    private static final int OUTPUT_Y = 36;
    private static final int NOTE_Y = 94;
    private static final int WIDTH = 146;
    /** Room for three wrapped lines of the note under the grid. */
    private static final int HEIGHT = 124;

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
        // Wrapped to the category width: one line of it is wider than the page.
        minecraft.fontRenderer.drawSplitString(I18n.format("integration.jei.insanetweaks.ritual_altar.note"),
                0, NOTE_Y, WIDTH, 0x404040);
    }

    @Override
    public void setRecipe(@Nonnull IRecipeLayout recipeLayout, @Nonnull RitualAltarWrapper wrapper,
            @Nonnull IIngredients ingredients) {
        IGuiItemStackGroup slots = recipeLayout.getItemStacks();

        for (int i = 0; i < RitualRecipe.GRID_SIZE; i++) {
            slots.init(i, true, GRID_X + (i % 3) * SLOT_SIZE, GRID_Y + (i / 3) * SLOT_SIZE);
        }
        for (int r = 0; r < RECEPTACLES.length; r++) {
            slots.init(RitualRecipe.GRID_SIZE + r, true, RECEPTACLES[r][0], RECEPTACLES[r][1]);
        }
        slots.init(RitualRecipe.GRID_SIZE + RECEPTACLES.length, false, OUTPUT_X, OUTPUT_Y);

        // set(IIngredients) maps the input lists onto the input slots in index order (grid, then the
        // receptacles N, E, S, W) and the output onto the output slot - the order the wrapper builds.
        slots.set(ingredients);
    }
}
