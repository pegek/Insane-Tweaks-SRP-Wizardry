package com.spege.insanetweaks.init;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.api.RitualRecipe;
import com.spege.insanetweaks.api.RitualRegistry;
import com.spege.insanetweaks.config.ModConfig;

import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreIngredient;

/**
 * Registers this mod's own Imbuement Altar rituals, and carries the helpers a ritual is written with.
 * Call once from FML init, after items exist.
 *
 * <p>Third-party code does not go through here - it calls {@link RitualRegistry#register} directly.
 * This class only holds what Insane Tweaks itself ships.
 *
 * <h2>Writing a ritual</h2>
 *
 * <pre>
 * ritual("living_wand", 200, new ItemStack(ModItems.LIVING_WAND),
 *         NONE,                          item("srparasites", "vile_shell"), NONE,
 *         ore(ModOreDict.ORE_MAGIC_NUCLEUS), item("ebwizardry", "master_wand"), ore(ModOreDict.ORE_MAGIC_NUCLEUS),
 *         NONE,                          item("insanetweaks", "golden_book"), NONE);
 * </pre>
 *
 * <p>Nine ingredients, written in three rows exactly as they sit on the ground, north-west first.
 *
 * <h2>Why the ingredient helpers exist</h2>
 *
 * <p>Same reason {@code ModRecipes.safeItem} does, and the same failure it prevents. A ritual that
 * names {@code srparasites:something} which this SRParasites build does not register must not take the
 * game down at startup - three separate crash reports came from exactly that in the crafting recipes.
 * {@link #item} records the miss and hands back AIR; {@link #ritual} sees that a miss was recorded
 * while its arguments were being evaluated and drops that ONE ritual with a warn line, leaving the
 * rest registered.
 *
 * <p>🚨 That only works because the arguments are evaluated immediately before the {@code ritual()}
 * call that consumes them. Do NOT hoist an {@code item(...)} into a local shared by two rituals: the
 * miss would be recorded once, charged to the first ritual, and the second would register with an AIR
 * ingredient - a ritual that can never fire and says nothing about why.
 */
public final class ModRituals {

    /** A position that must stay EMPTY. Not a placeholder - the matcher enforces it. */
    public static final Ingredient NONE = Ingredient.EMPTY;

    /** Every distinct id missing this run, in discovery order. For the closing summary. */
    private static final Set<String> MISSING_IDS = new LinkedHashSet<>();

    /** Ids missing since the last {@link #ritual} call, i.e. those of the ritual being built. */
    private static final List<String> PENDING_MISSING = new ArrayList<>();

    private static int skipped;

    private ModRituals() {
    }

    public static void register() {
        if (!ModConfig.tweaks.enableAltarRituals) {
            return;
        }

        MISSING_IDS.clear();
        PENDING_MISSING.clear();
        skipped = 0;

        registerCrystalCondensing();

        if (skipped > 0) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Altar rituals: {} ritual(s) were skipped because "
                    + "these ids are not registered in this modpack: {}.",
                    Integer.valueOf(skipped), MISSING_IDS);
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Altar rituals registered: {}.",
                Integer.valueOf(RitualRegistry.getRecipes().size()));
    }

    // ------------------------------------------------------------------------------------------
    // The rituals
    // ------------------------------------------------------------------------------------------

    /**
     * Reference ritual: eight Abomination spectral dust in a ring around a plain magic crystal,
     * producing an Abomination crystal.
     *
     * <p>Deliberately chosen to add NO power to the game. EB's own altar already does this conversion
     * for four dust and one crystal; this asks for eight, so it is strictly the worse deal and exists
     * to be looked at, not used - it gives the structure something to do, gives JEI something to
     * show, and gives the mechanic a test case that cannot unbalance anything while the real ritual
     * list is still being decided.
     *
     * <p>Skipped entirely when the Abomination element is not registered - there is no dust to build
     * it from, and a recipe naming a missing item would be a recipe that silently never fires.
     */
    private static void registerCrystalCondensing() {
        if (!ModElements.EXTENDED) {
            InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Altar rituals: the reference ritual was skipped "
                    + "because the Abomination element is not registered.");
            return;
        }
        int meta = ModElements.ABOMINATION.ordinal();

        ritual("abomination_crystal", ModConfig.tweaks.ritualDurationTicks,
                new ItemStack(safeItem("ebwizardry", "magic_crystal"), 1, meta),
                item("ebwizardry", "spectral_dust", meta), item("ebwizardry", "spectral_dust", meta),
                item("ebwizardry", "spectral_dust", meta),
                item("ebwizardry", "spectral_dust", meta), item("ebwizardry", "magic_crystal", 0),
                item("ebwizardry", "spectral_dust", meta),
                item("ebwizardry", "spectral_dust", meta), item("ebwizardry", "spectral_dust", meta),
                item("ebwizardry", "spectral_dust", meta));
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /**
     * Builds and registers one ritual, unless an ingredient id turned out to be missing.
     *
     * @param path      the ritual's own id path, under this mod's namespace
     * @param duration  ticks the ritual runs for
     * @param result    what appears on the centre altar
     * @param pattern   exactly nine ingredients, row-major from the north-west corner
     */
    public static void ritual(String path, int duration, ItemStack result, Ingredient... pattern) {
        if (!PENDING_MISSING.isEmpty()) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Altar ritual '{}' was skipped: {} is not "
                    + "registered in this modpack.", path, PENDING_MISSING);
            PENDING_MISSING.clear();
            skipped++;
            return;
        }
        if (result.isEmpty()) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Altar ritual '{}' was skipped: its result item "
                    + "is not registered.", path);
            skipped++;
            return;
        }
        RitualRegistry.register(new RitualRecipe(
                new ResourceLocation(InsaneTweaksMod.MODID, path), pattern, result, duration));
    }

    /** An ingredient matching one item by registry name, metadata 0. */
    public static Ingredient item(String modid, String path) {
        return item(modid, path, 0);
    }

    /** An ingredient matching one item by registry name and exact metadata. */
    public static Ingredient item(String modid, String path, int meta) {
        Item found = safeItem(modid, path);
        if (found == Items.AIR) {
            // 🚨 There is no ingredient in 1.12.2 that matches NOTHING: Ingredient.EMPTY answers true
            // for an empty stack, and Ingredient.fromStacks(EMPTY) does too (its apply() compares
            // getItem(), and an empty stack's item is AIR). So this placeholder would happily fire on
            // an empty structure. The ONLY thing keeping it out of the registry is ritual() dropping
            // any recipe whose arguments recorded a miss - which is why item() must never be used
            // outside a ritual() argument list.
            return Ingredient.EMPTY;
        }
        return Ingredient.fromStacks(new ItemStack(found, 1, meta));
    }

    /** An ingredient matching anything registered under an ore dictionary name. */
    public static Ingredient ore(String oreName) {
        return new OreIngredient(oreName);
    }

    /**
     * An EB Wizardry spell book for one specific spell.
     *
     * <p>Here because it is the shape the Abomination spell rituals will need: EB stores the spell in
     * the book's metadata ({@code Spell.metadata()}), so a book for a chosen spell is one call and a
     * ritual producing a KNOWN spell is possible at all - which the ruined-spell-book route, being a
     * loot-table roll, is not.
     */
    public static ItemStack spellBook(Spell spell) {
        return new ItemStack(WizardryItems.spell_book, 1, spell.metadata());
    }

    /**
     * Registry lookup that records a miss instead of throwing. Returns {@link Items#AIR} for an id
     * this modpack does not have; the AIR never reaches the registry because {@link #ritual} drops any
     * ritual whose arguments recorded a miss.
     */
    public static Item safeItem(String modid, String path) {
        Item found = ForgeRegistries.ITEMS.getValue(new ResourceLocation(modid, path));
        if (found == null) {
            String id = modid + ":" + path;
            MISSING_IDS.add(id);
            PENDING_MISSING.add(id);
            return Items.AIR;
        }
        return found;
    }
}
