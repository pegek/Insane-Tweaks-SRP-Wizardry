package com.spege.insanetweaks.api;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;

/**
 * One Imbuement Altar ritual: a 3x3 shaped pattern laid out on a 3x3 field of
 * {@code ebwizardry:imbuement_altar} blocks, and the item it produces on the centre altar.
 *
 * <p>Reads like a crafting-table recipe on purpose - nine positions, each either an
 * {@link Ingredient} or nothing - because that is the mental model players already have. Two
 * differences follow from the pattern living in the world rather than in a GUI:
 *
 * <ul>
 * <li><b>It matches in all four rotations.</b> A crafting grid has a fixed orientation because the
 * player looks at it head-on; a 3x3 of blocks on the ground does not. Building the structure facing
 * the wrong way is not a mistake a player can even perceive, so it must not be one the recipe
 * punishes. Mirroring is deliberately NOT matched - a mirrored pattern is a genuinely different
 * arrangement and letting it through would make asymmetric recipes collide.</li>
 * <li><b>The centre is an ingredient slot AND the output slot.</b> The result replaces whatever the
 * centre held, which is exactly what EB's own altar does with its single slot
 * ({@code this.stack = result}), so the behaviour reads as native.</li>
 * </ul>
 *
 * <p>Index layout is row-major from the north-west corner, i.e. {@code index = (dz + 1) * 3 + (dx + 1)}
 * relative to the centre block. Index 4 is the centre.
 *
 * <p>An empty position is written as {@link Ingredient#EMPTY}, which is not a placeholder but a real
 * constraint: {@code Ingredient.EMPTY.apply(stack)} answers true only for an empty stack, so a recipe
 * with gaps refuses to fire when the player has put something in one of them. That is what keeps two
 * recipes sharing a sub-pattern from being ambiguous.
 */
public final class RitualRecipe {

    /** Number of positions in the structure. */
    public static final int GRID_SIZE = 9;

    /** Index of the centre altar, which carries the result. */
    public static final int CENTRE_INDEX = 4;

    private final ResourceLocation id;
    private final Ingredient[] pattern;
    private final ItemStack result;
    private final int durationTicks;

    /**
     * @param id            unique id, used for logging and as the JEI recipe identity
     * @param pattern       exactly 9 ingredients, row-major from the north-west corner; use
     *                      {@link Ingredient#EMPTY} for a position that must stay empty
     * @param result        what appears on the centre altar; copied on use, never handed out directly
     * @param durationTicks how long the ritual runs before producing the result
     */
    public RitualRecipe(ResourceLocation id, Ingredient[] pattern, ItemStack result, int durationTicks) {
        if (pattern == null || pattern.length != GRID_SIZE) {
            throw new IllegalArgumentException("Ritual pattern must have exactly " + GRID_SIZE + " entries");
        }
        if (result == null || result.isEmpty()) {
            throw new IllegalArgumentException("Ritual result must not be empty");
        }
        if (durationTicks < 1) {
            throw new IllegalArgumentException("Ritual duration must be at least 1 tick");
        }
        this.id = id;
        this.pattern = new Ingredient[GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            this.pattern[i] = pattern[i] == null ? Ingredient.EMPTY : pattern[i];
        }
        this.result = result.copy();
        this.durationTicks = durationTicks;
    }

    public ResourceLocation getId() {
        return this.id;
    }

    /** The ingredient at a position of the UNROTATED pattern. Used by JEI, which draws it head-on. */
    public Ingredient getIngredient(int index) {
        return this.pattern[index];
    }

    /** A fresh copy of the result. Never returns the stored instance. */
    public ItemStack getResult() {
        return this.result.copy();
    }

    public int getDurationTicks() {
        return this.durationTicks;
    }

    /**
     * Tries this recipe against a grid read from the world, in all four rotations.
     *
     * @param grid nine stacks, row-major from the north-west corner
     * @return the rotation (0-3, quarter turns clockwise) that matched, or -1 for no match
     */
    public int match(ItemStack[] grid) {
        for (int rotation = 0; rotation < 4; rotation++) {
            if (matchesAt(grid, rotation)) {
                return rotation;
            }
        }
        return -1;
    }

    /**
     * Whether the pattern requires an item at this world position, given a rotation that matched.
     * The consume step uses this so that positions the recipe leaves empty are never touched.
     */
    public boolean consumesAt(int gridIndex, int rotation) {
        // Asked as "does this ingredient accept an EMPTY stack", not as "is it the EMPTY constant".
        // Identity would be wrong: Ingredient.fromStacks() with no arguments builds a DIFFERENT
        // instance that behaves identically, and a caller assembling a pattern programmatically can
        // easily produce one.
        return !this.pattern[rotate(gridIndex, rotation)].apply(ItemStack.EMPTY);
    }

    private boolean matchesAt(ItemStack[] grid, int rotation) {
        for (int i = 0; i < GRID_SIZE; i++) {
            if (!this.pattern[rotate(i, rotation)].apply(grid[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Maps a world-grid index to the pattern index it should be compared against, for a given number
     * of quarter turns clockwise.
     *
     * <p>Rotating the LOOKUP rather than building four rotated copies of the pattern keeps the recipe
     * immutable and costs nothing: this is nine integer operations per rotation attempt.
     */
    private static int rotate(int index, int rotation) {
        int row = index / 3;
        int col = index % 3;
        for (int i = 0; i < rotation; i++) {
            int newRow = col;
            int newCol = 2 - row;
            row = newRow;
            col = newCol;
        }
        return row * 3 + col;
    }
}
