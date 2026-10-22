package com.spege.insanetweaks.init;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.api.RitualRecipe;
import com.spege.insanetweaks.api.RitualRegistry;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.ritual.RitualCoverage;

import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
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

    /** Whether srpextra's parts exist, or the Living armour falls back to SRP substitutes. */
    private static final boolean SRPEXTRA = Loader.isModLoaded("srpextra");


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
        if (ModElements.EXTENDED) {
            int abom = ModElements.ABOMINATION.ordinal();
            registerComponents(abom);
            registerLivingGear();
            if (ModConfig.tweaks.altarTestRecipes) {
                registerTestRecipes(abom);
            }
        }

        if (skipped > 0) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Altar rituals: {} ritual(s) were skipped because "
                    + "these ids are not registered in this modpack: {}.",
                    Integer.valueOf(skipped), MISSING_IDS);
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Altar rituals registered: {}.",
                Integer.valueOf(RitualRegistry.getRecipes().size()));
        warnUncovered();
    }

    // ------------------------------------------------------------------------------------------
    // The rituals
    // ------------------------------------------------------------------------------------------

    /**
     * Abomination crystals, two at a time: a plain magic crystal in the centre and four Abomination
     * dust on the sides' middle altars (corners empty), plus the four receptacle dusts.
     *
     * <p>That is the altar's own deal on top of EB's: EB's single altar turns one crystal and four
     * dust into one Abomination crystal, so a 3x3 version could never be CHEAPER for one crystal -
     * the four receptacles alone already cost what EB charges. Doubling the output is what makes the
     * dais worth using for it (user decision 2026-10-22, test value).
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
                new ItemStack(safeItem("ebwizardry", "magic_crystal"), 2, meta),
                NONE, item("ebwizardry", "spectral_dust", meta), NONE,
                item("ebwizardry", "spectral_dust", meta), item("ebwizardry", "magic_crystal", 0),
                item("ebwizardry", "spectral_dust", meta),
                NONE, item("ebwizardry", "spectral_dust", meta), NONE);
    }

    /**
     * The arcane components and the Sanctuary core. Every pattern except the golden book's is a
     * cell-for-cell copy of the item's crafting-table recipe: what moving it to the altar adds is the
     * conquered sanctum and the four receptacle dusts, not a different shopping list.
     */
    private static void registerComponents(int abom) {
        int t = ModConfig.tweaks.ritualDurationTicks;

        ritual("magic_nucleus", t, stack(InsaneTweaksMod.MODID, "magic_nucleus", 1, 0),
                NONE, item("srparasites", "beckon_drop"), NONE,
                item("ebwizardry", "spectral_dust", abom), item("ebwizardry", "magic_crystal", abom),
                item("ebwizardry", "spectral_dust", abom),
                NONE, item("srparasites", "ada_arachnida_drop"), NONE);

        ritual("adaptation_upgrade", t, stack(InsaneTweaksMod.MODID, "adaptation_upgrade", 1, 0),
                NONE, item("srparasites", "vile_shell"), NONE,
                item(InsaneTweaksMod.MODID, "golden_book"), item("ebwizardry", "attunement_upgrade"),
                item(InsaneTweaksMod.MODID, "golden_book"),
                NONE, anyOre(ModOreDict.ORE_MAGIC_NUCLEUS, ModOreDict.ORE_LIVING_NUCLEUS), NONE);

        // Cheaper than the table (which stays): the nether star becomes an Abomination crystal.
        ritual("golden_book", t, stack(InsaneTweaksMod.MODID, "golden_book", 4, 0),
                item("defiledlands", "book_wyrm_scale_golden"), item("ebwizardry", "magic_crystal", abom),
                item("defiledlands", "book_wyrm_scale_golden"),
                item(InsaneTweaksMod.MODID, "rupter_solied"), item("minecraft", "bookshelf"),
                item(InsaneTweaksMod.MODID, "rupter_solied"),
                item("defiledlands", "book_wyrm_scale"), item("ancientspellcraft", "empty_mystic_spell_book"),
                item("defiledlands", "book_wyrm_scale"));

        // The result is whatever backs itLivingNucleus: our clone without swparasites, swparasites'
        // own item with it. Only OUR clone's table recipe is stripped; swparasites' stays.
        ritual("living_nucleus", t, firstOre(ModOreDict.ORE_LIVING_NUCLEUS),
                NONE, item("srparasites", "ada_summoner_drop"), NONE,
                item("srparasites", "assimilated_flesh"), item("srparasites", "living_core"),
                item("srparasites", "assimilated_flesh"),
                NONE, item("srparasites", "ada_manducater_drop"), NONE);

        // The altar's own Sanctuary recipe (user, 2026-10-22), no longer a copy of the table one: one SRP
        // pearl and an eye of ender beside the beacon, ANY runestone instead of necromancy only, and a
        // magic or living nucleus in place of the false apple.
        if (ModBlocks.SANCTUARY_CORE != null) {
            ritual("sanctuary_core", t, new ItemStack(ModBlocks.SANCTUARY_CORE),
                    item("srparasites", "pearl"), item("minecraft", "beacon"), item("minecraft", "ender_eye"),
                    metas("ebwizardry", "runestone", 1, 7), item("srparasites", "levelclock"),
                    metas("ebwizardry", "runestone", 1, 7),
                    item("ancientspellcraft", "devoritium_block"),
                    anyOre(ModOreDict.ORE_MAGIC_NUCLEUS, ModOreDict.ORE_LIVING_NUCLEUS),
                    item("ancientspellcraft", "devoritium_block"));
        }
    }

    /** Living gear, each pattern copied from its crafting-table JSON. */
    private static void registerLivingGear() {
        int t = ModConfig.tweaks.ritualDurationTicks;

        ritual("living_wand", t, stack(InsaneTweaksMod.MODID, "living_wand", 1, 0),
                NONE, anyOre(ModOreDict.ORE_MAGIC_NUCLEUS, ModOreDict.ORE_LIVING_NUCLEUS), NONE,
                item("srparasites", "vile_shell"), item("ebwizardry", "master_wand"), item("srparasites", "vile_shell"),
                item(InsaneTweaksMod.MODID, "golden_book"), item(InsaneTweaksMod.MODID, "rupter_solied"), NONE);

        ritual("living_spellblade", t, stack(InsaneTweaksMod.MODID, "living_spellblade", 1, 0),
                NONE, NONE, anyOre(ModOreDict.ORE_LIVING_NUCLEUS),
                item("srparasites", "vile_shell"), item("ancientspellcraft", "battlemage_sword_master"),
                anyOre(ModOreDict.ORE_INFECTIOUS_LONG_BLADE_FRAGMENT),
                item("srparasites", "hardened_bone_handle"), item("srparasites", "vile_shell"), NONE);

        ritual("living_aegis", t, stack(InsaneTweaksMod.MODID, "living_aegis", 1, 0),
                NONE, anyOre(ModOreDict.ORE_LIVING_NUCLEUS), NONE,
                item("srparasites", "vile_shell"), item("ancientspellcraft", "battlemage_shield"),
                item("srparasites", "vile_shell"),
                plates(), cloth(), plates());

        ritual("living_battlemage_helmet", t, stack(InsaneTweaksMod.MODID, "living_battlemage_helmet", 1, 0),
                plates(), buckle(), plates(),
                plates(), battlemage("helmet"), plates(),
                NONE, item("srparasites", "living_core"), NONE);

        ritual("living_battlemage_chestplate", t,
                stack(InsaneTweaksMod.MODID, "living_battlemage_chestplate", 1, 0),
                cloth(), plates(), cloth(),
                plates(), item("srparasites", "living_core"), plates(),
                plates(), battlemage("chestplate"), plates());

        ritual("living_battlemage_leggings", t, stack(InsaneTweaksMod.MODID, "living_battlemage_leggings", 1, 0),
                cloth(), plates(), cloth(),
                buckle(), item("srparasites", "living_core"), buckle(),
                buckle(), battlemage("leggings"), buckle());

        ritual("living_battlemage_boots", t, stack(InsaneTweaksMod.MODID, "living_battlemage_boots", 1, 0),
                buckle(), cloth(), buckle(),
                plates(), item("srparasites", "living_core"), plates(),
                NONE, battlemage("boots"), NONE);

        // Parasite Mage armour, grown from EB's warlock set.
        ritual("parasite_mage_helmet", t, stack(InsaneTweaksMod.MODID, "parasite_mage_helmet", 1, 0),
                NONE, buckle(), NONE,
                plates(), warlock("hood"), plates(),
                NONE, item("srparasites", "living_core"), NONE);

        ritual("parasite_mage_chestplate", t, stack(InsaneTweaksMod.MODID, "parasite_mage_chestplate", 1, 0),
                NONE, plates(), NONE,
                cloth(), item("srparasites", "living_core"), cloth(),
                plates(), warlock("robe"), plates());

        ritual("parasite_mage_leggings", t, stack(InsaneTweaksMod.MODID, "parasite_mage_leggings", 1, 0),
                cloth(), NONE, cloth(),
                buckle(), item("srparasites", "living_core"), buckle(),
                buckle(), warlock("leggings"), buckle());

        ritual("parasite_mage_boots", t, stack(InsaneTweaksMod.MODID, "parasite_mage_boots", 1, 0),
                NONE, cloth(), NONE,
                plates(), item("srparasites", "living_core"), plates(),
                NONE, warlock("boots"), NONE);

        // The Living nunchaku stays on the crafting table (user, 2026-10-22): it is not a magic item,
        // so there is nothing for the ritual to gate.
    }

    /**
     * Experimental altar routes to other mods' trinket parts. Their own crafting recipes are never
     * touched. Each swaps the original recipe's rarest input for an Abomination material; Bountiful
     * Baubles' two items have no crafting recipe at all (they drop), so theirs are designed fresh.
     */
    private static void registerTestRecipes(int abom) {
        int t = ModConfig.tweaks.ritualDurationTicks;

        ritual("test_twisted_core", t, stack("enigmaticlegacy", "twisted_core", 1, 0),
                NONE, item("minecraft", "ghast_tear"), NONE,
                item("minecraft", "blaze_powder"), item("ebwizardry", "magic_crystal", abom),
                item("minecraft", "blaze_powder"),
                anyOre("dustRedstone"), item("minecraft", "ender_eye"), anyOre("dustRedstone"));

        ritual("test_cosmic_heart", t, stack("enigmaticlegacy", "cosmic_heart", 1, 0),
                item("enigmaticlegacy", "astral_dust"), anyOre(ModOreDict.ORE_MAGIC_NUCLEUS),
                item("enigmaticlegacy", "astral_dust"),
                item("minecraft", "blaze_powder"), anyOre("blockDiamond"), item("minecraft", "blaze_powder"),
                item("enigmaticlegacy", "astral_dust"), item("minecraft", "ender_eye"),
                item("enigmaticlegacy", "astral_dust"));

        ritual("test_enchanter_pearl", t, stack("enigmaticlegacy", "enchanter_pearl", 1, 0),
                NONE, anyOre("gemEmerald"), NONE,
                item("minecraft", "blaze_powder"), item("minecraft", "ender_pearl"), item("minecraft", "blaze_powder"),
                item("ebwizardry", "spectral_dust", abom), item("minecraft", "obsidian"),
                item("ebwizardry", "spectral_dust", abom));

        ritual("test_evil_ingot", t, stack("enigmaticlegacy", "evil_ingot", 1, 0),
                item("minecraft", "ghast_tear"), item("ebwizardry", "spectral_dust", abom),
                item("minecraft", "ghast_tear"),
                item("ebwizardry", "spectral_dust", abom), anyOre("ingotNetherite"),
                item("ebwizardry", "spectral_dust", abom),
                item("minecraft", "ghast_tear"), item("ebwizardry", "spectral_dust", abom),
                item("minecraft", "ghast_tear"));

        ritual("test_witherite_ingot", t, stack("enigmaticlegacy", "witherite_ingot", 1, 0),
                anyOre("ingotGold"), item("minecraft", "obsidian"), anyOre("ingotGold"),
                item("minecraft", "obsidian"), item("ebwizardry", "magic_crystal", abom), item("minecraft", "obsidian"),
                anyOre("ingotGold"), NONE, anyOre("ingotGold"));

        ritual("test_glowing_gem", t, stack("xat", "glowing_gem", 1, 0),
                item("xat", "glowing_ingot"), item("xat", "glowing_powder"), item("xat", "glowing_ingot"),
                item("xat", "glowing_powder"), item("ebwizardry", "magic_crystal", abom), item("xat", "glowing_powder"),
                item("xat", "glowing_ingot"), item("xat", "glowing_powder"), item("xat", "glowing_ingot"));

        ritual("test_mana_crystal", t, stack("xat", "mana_crystal", 1, 0),
                item("xat", "mana_candy"), item("ebwizardry", "spectral_dust", abom), item("xat", "mana_candy"),
                item("ebwizardry", "spectral_dust", abom), item("xat", "glowing_gem"),
                item("ebwizardry", "spectral_dust", abom),
                item("xat", "mana_candy"), item("ebwizardry", "spectral_dust", abom), item("xat", "mana_candy"));

        ritual("test_titan_spirit", t, stack("xat", "titan_spirit", 1, 0),
                item("xat", "glowing_gem"), item("minecraft", "potato"), item("xat", "glowing_gem"),
                item("ebwizardry", "spectral_dust", abom), item("xat", "mana_crystal"),
                item("ebwizardry", "spectral_dust", abom),
                item("xat", "glowing_gem"), item("minecraft", "beef"), item("xat", "glowing_gem"));

        ritual("test_ender_dragon_scale", t, stack("bountifulbaubles", "enderdragonscale", 1, 0),
                item("minecraft", "obsidian"), item("minecraft", "dragon_breath"), item("minecraft", "obsidian"),
                item("minecraft", "dragon_breath"), item("ebwizardry", "magic_crystal", abom),
                item("minecraft", "dragon_breath"),
                item("minecraft", "obsidian"), item("minecraft", "dragon_breath"), item("minecraft", "obsidian"));

        ritual("test_spectral_silt", t, stack("bountifulbaubles", "spectralsilt", 4, 0),
                item("minecraft", "sand"), item("ebwizardry", "spectral_dust", abom), item("minecraft", "sand"),
                item("ebwizardry", "spectral_dust", abom), item("minecraft", "glowstone_dust"),
                item("ebwizardry", "spectral_dust", abom),
                item("minecraft", "sand"), item("ebwizardry", "spectral_dust", abom), item("minecraft", "sand"));
    }

    /**
     * True for the items of THIS mod whose crafting-table recipe gives way to the altar.
     * {@code ModRecipes.removeRecipes} strips only {@code insanetweaks:}-namespaced recipes for these,
     * so another mod's recipe for the same item (swparasites' living nucleus) is never touched.
     * The golden book is deliberately absent: its table recipe stays and the altar is only cheaper.
     */
    public static boolean isAltarExclusive(Item item) {
        return item != null && item != Items.AIR && exclusiveItems().contains(item);
    }

    /** The items {@link #isAltarExclusive} answers true for. */
    private static List<Item> exclusiveItems() {
        List<Item> items = new ArrayList<>();
        items.add(ModItems.MAGIC_NUCLEUS);
        items.add(ModItems.ADAPTATION_UPGRADE);
        items.add(ModItems.LIVING_WAND);
        items.add(ModItems.LIVING_SPELLBLADE);
        items.add(ModItems.LIVING_AEGIS);
        items.add(ModItems.LIVING_BATTLEMAGE_HELMET);
        items.add(ModItems.LIVING_BATTLEMAGE_CHESTPLATE);
        items.add(ModItems.LIVING_BATTLEMAGE_LEGGINGS);
        items.add(ModItems.LIVING_BATTLEMAGE_BOOTS);
        items.add(ModItems.LIVING_NUCLEUS);
        items.add(ModItems.PARASITE_WIZARD_HELMET);
        items.add(ModItems.PARASITE_WIZARD_CHESTPLATE);
        items.add(ModItems.PARASITE_WIZARD_LEGGINGS);
        items.add(ModItems.PARASITE_WIZARD_BOOTS);
        if (ModBlocks.SANCTUARY_CORE != null) {
            items.add(Item.getItemFromBlock(ModBlocks.SANCTUARY_CORE));
        }
        return items;
    }

    /**
     * Whether our table recipes for the {@link #isAltarExclusive} items are stripped. Only when a
     * ritual can actually make them: rituals on, the Abomination element present (no receptacle can
     * count as lit without it) and sanctums enabled (no dais otherwise). Shared with
     * {@code ModRecipes.removeRecipes} so the two can never disagree.
     */
    public static boolean altarExclusivityActive() {
        return ModConfig.tweaks.altarExclusiveRecipes && ModConfig.tweaks.enableAltarRituals
                && ModElements.EXTENDED && ModConfig.arcaneSanctum.enabled;
    }

    /**
     * Warns about every altar-exclusive item whose table recipe was stripped but whose ritual was
     * dropped (a missing ingredient id), because that item now has no recipe anywhere and nothing
     * else would say so. Only items actually in the registry count: the living nucleus clone exists
     * only without swparasites.
     */
    private static void warnUncovered() {
        if (!altarExclusivityActive()) {
            return;
        }
        List<String> exclusive = new ArrayList<>();
        for (Item item : exclusiveItems()) {
            ResourceLocation name = item == null ? null : item.getRegistryName();
            if (name != null && ForgeRegistries.ITEMS.getValue(name) == item) {
                exclusive.add(name.toString());
            }
        }
        List<String> produced = new ArrayList<>();
        for (RitualRecipe recipe : RitualRegistry.getRecipes()) {
            ResourceLocation name = recipe.getResult().getItem().getRegistryName();
            if (name != null) {
                produced.add(name.toString());
            }
        }
        List<String> uncovered = RitualCoverage.uncovered(exclusive, produced);
        if (!uncovered.isEmpty()) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Altar rituals: these items lost their crafting-table "
                    + "recipe to the altar but have NO ritual (see the skipped-ritual lines above), so "
                    + "nothing can make them: {}. Set tweaks.altarExclusiveRecipes=false to restore the "
                    + "table recipes.", uncovered);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /** srpextra:sturdy_armor_plates, or the SRP substitute ModRecipes uses without srpextra. */
    private static Ingredient plates() {
        return SRPEXTRA ? item("srpextra", "sturdy_armor_plates") : item("srparasites", "hive_scrap");
    }

    /** srpextra:tightening_buckle, or its SRP substitute. */
    private static Ingredient buckle() {
        return SRPEXTRA ? item("srpextra", "tightening_buckle") : item("srparasites", "infectious_blade_fragment");
    }

    /** srpextra:flexible_cloth, or its substitute. */
    private static Ingredient cloth() {
        return SRPEXTRA ? item("srpextra", "flexible_cloth") : item(InsaneTweaksMod.MODID, "rupter_solied");
    }

    /** Any of EB's eight elemental battlemage pieces of one slot, at any damage - as the JSON accepts. */
    private static Ingredient battlemage(String slot) {
        return elementalArmour("battlemage", slot);
    }

    /** Same for EB's warlock armour, which the Parasite Mage set is grown from. */
    private static Ingredient warlock(String slot) {
        return elementalArmour("warlock", slot);
    }

    private static Ingredient elementalArmour(String set, String slot) {
        String[] suffixes = { "", "_fire", "_ice", "_lightning", "_necromancy", "_earth", "_sorcery", "_healing" };
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < suffixes.length; i++) {
            Item piece = safeItem("ebwizardry", set + "_" + slot + suffixes[i]);
            if (piece != Items.AIR) {
                stacks.add(new ItemStack(piece, 1, OreDictionary.WILDCARD_VALUE));
            }
        }
        return Ingredient.fromStacks(stacks.toArray(new ItemStack[stacks.size()]));
    }

    /**
     * Everything registered under any of the given ore names, snapshotted now. Records a miss when
     * all of them are empty, so the ritual is dropped with a warn line instead of registering a cell
     * nothing can ever fill - which {@link #ore} (dynamic, never empty-checked) would do silently.
     */
    private static Ingredient anyOre(String... oreNames) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < oreNames.length; i++) {
            stacks.addAll(OreDictionary.getOres(oreNames[i], false));
        }
        if (stacks.isEmpty()) {
            String id = "ore:" + String.join("|", oreNames);
            MISSING_IDS.add(id);
            PENDING_MISSING.add(id);
            return Ingredient.EMPTY;
        }
        return Ingredient.fromStacks(stacks.toArray(new ItemStack[stacks.size()]));
    }

    /**
     * One item at each metadata in {@code from..to} inclusive - e.g. EB runestones 1-7 - listed one by
     * one rather than as a wildcard, which the Codex and JEI would draw at damage 32767 (a missing
     * model for a block).
     *
     * <p>🚨 Spelled out on purpose, not discovered through {@code getSubItems}: EB's
     * {@code BlockRunestone.getSubBlocks} only fills the list for its OWN creative tab, so asking with
     * {@code CreativeTabs.SEARCH} came back empty, and a "fall back to meta 0" filled the cell with
     * runestone:0 - a variant that does not exist (runestones are elements 1-7, MAGIC has none). The
     * Sanctuary ritual could then never match.
     */
    private static Ingredient metas(String modid, String path, int from, int to) {
        Item found = safeItem(modid, path);
        if (found == Items.AIR) {
            return Ingredient.EMPTY; // the miss is recorded; ritual() drops this recipe
        }
        ItemStack[] stacks = new ItemStack[to - from + 1];
        for (int meta = from; meta <= to; meta++) {
            stacks[meta - from] = new ItemStack(found, 1, meta);
        }
        return Ingredient.fromStacks(stacks);
    }

    /** First item under an ore name, as a result; EMPTY (and a recorded miss) when there is none. */
    private static ItemStack firstOre(String oreName) {
        List<ItemStack> ores = OreDictionary.getOres(oreName, false);
        if (ores.isEmpty()) {
            String id = "ore:" + oreName;
            MISSING_IDS.add(id);
            PENDING_MISSING.add(id);
            return ItemStack.EMPTY;
        }
        ItemStack first = ores.get(0).copy();
        if (first.getMetadata() == OreDictionary.WILDCARD_VALUE) {
            first.setItemDamage(0);
        }
        first.setCount(1);
        return first;
    }

    /** A result stack by registry name. Goes through {@link #safeItem}, so a missing id drops the ritual. */
    private static ItemStack stack(String modid, String path, int count, int meta) {
        Item found = safeItem(modid, path);
        return found == Items.AIR ? ItemStack.EMPTY : new ItemStack(found, count, meta);
    }

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
        if (ModItems.isGearDisabled(result.getItem())) {
            return; // gear.availability switched it off; its table recipe is already gone too
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
