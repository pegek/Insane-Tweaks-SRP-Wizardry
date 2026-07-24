package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Per-dimension control over Ice&amp;Fire worldgen.
 *
 * <p>Ice&amp;Fire only offers three coarse dimension gates (whole chunk-gen, dragons, snow
 * villages), so a pack cannot say "dragon dens only in dimension X, ores everywhere, sirens
 * nowhere but the Overworld". This category adds that: one list per structure, one line per
 * dimension.
 *
 * <p><b>Nothing is hardcoded and nothing is on by default.</b> Every list starts empty, and a
 * dimension with no entry keeps Ice&amp;Fire's own behaviour, so an untouched config is
 * indistinguishable from not having this module at all.
 *
 * <p>Implemented by {@code MixinIandfStructureGenerator} (redirects on the config-field reads
 * inside {@code StructureGenerator.generate}); parsed by
 * {@link com.spege.srpwizcore.util.IandfWorldgenOverrides}.
 */
public class IandfWorldgenCategory {

    @Config.Comment({
            "Master switch for per-dimension Ice&Fire worldgen control.",
            "When false every list below is ignored and Ice&Fire generates exactly as it would",
            "without this mod (the redirects still run, but each one hands back Ice&Fire's own value).",
            "Read live: toggling it re-parses the lists, no restart needed. Default OFF."
    })
    @Config.Name("Enable I&F Worldgen Control")
    public boolean enableIandfWorldgenControl = false;

    @Config.Comment({
            "Ice&Fire keeps its anti-clustering 'last placed structure' positions in fields that are",
            "SHARED BY ALL DIMENSIONS. Once worldgen runs in more than one dimension, a structure",
            "placed in dimension A eats the minimum-distance budget of the same structure in",
            "dimension B, silently suppressing generation. When true, those positions are tracked",
            "per dimension instead. Only has an effect while the control above is on.",
            "Kept separate so it can be switched off on its own while diagnosing generation.",
            "Read live at generation time. Default ON."
    })
    @Config.Name("Fix: Cross-Dimension Structure Spacing")
    public boolean fixCrossDimStructureSpacing = true;

    // ---------------------------------------------------------------------------------------
    // Structure lists. Syntax documented once here, referenced by every field below.
    // ---------------------------------------------------------------------------------------

    private static final String SYNTAX_1 = "Syntax: one entry per line, \"<dimId>=<enabled>[:<chance>]\".";
    private static final String SYNTAX_2 = "  0=false        -> this structure never generates in dimension 0";
    private static final String SYNTAX_3 = "  150=true:90    -> generates in dimension 150, chance 1 in 90 per chunk";
    private static final String SYNTAX_4 = "  0=true         -> generates in dimension 0 at Ice&Fire's own chance";
    private static final String SYNTAX_5 = "A dimension NOT listed here keeps Ice&Fire's native behaviour (fall-through).";
    private static final String SYNTAX_6 = "chance uses Ice&Fire's '1 in N' meaning: bigger number = rarer. Values are";
    private static final String SYNTAX_7 = "directly portable from iceandfire.cfg.";

    // Ore note, repeated on the five ore fields so nobody has to hunt for it.
    private static final String ORE_1 = "ORES ARE DIFFERENT: Ice&Fire has no chance field for ores, only on/off.";
    private static final String ORE_2 = "Here the number is OUR per-chunk veto divider: \"0=true:6\" means the ore's";
    private static final String ORE_3 = "whole generation pass runs in roughly 1 chunk out of 6 in dimension 0.";
    private static final String ORE_4 = "Same syntax as above, different source of meaning.";

    @Config.Comment({ "Dragon dens (underground dragon caves, all three types).",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7,
            "NOTE: per-biome chances from iceandfire.cfg ('Generate Dragon Dens Biome Name Chance')",
            "take priority over the chance here, exactly as they do over Ice&Fire's own value." })
    @Config.Name("Dragon Dens")
    public String[] dragonDens = new String[0];

    @Config.Comment({ "Dragon roosts (surface dragon nests, all three types).",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7,
            "NOTE: per-biome chances from iceandfire.cfg ('Generate Dragon Roosts Biome Name Chance')",
            "take priority over the chance here, exactly as they do over Ice&Fire's own value." })
    @Config.Name("Dragon Roosts")
    public String[] dragonRoosts = new String[0];

    @Config.Comment({ "Dead dragon skeletons.",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7 })
    @Config.Name("Dragon Skeletons")
    public String[] dragonSkeletons = new String[0];

    @Config.Comment({ "Dread mausoleums.",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7 })
    @Config.Name("Mausoleums")
    public String[] mausoleums = new String[0];

    @Config.Comment({ "Gorgon temples.",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7 })
    @Config.Name("Gorgon Temples")
    public String[] gorgonTemple = new String[0];

    @Config.Comment({ "Cyclops caves.",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7 })
    @Config.Name("Cyclops Caves")
    public String[] cyclopsCaves = new String[0];

    @Config.Comment({ "Wandering cyclopes (the surface variant, separate from cyclops caves).",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7 })
    @Config.Name("Wandering Cyclops")
    public String[] wanderingCyclops = new String[0];

    @Config.Comment({ "Myrmex colonies (jungle and desert hives).",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7 })
    @Config.Name("Myrmex Colonies")
    public String[] myrmexColonies = new String[0];

    @Config.Comment({ "Pixie villages.",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7 })
    @Config.Name("Pixie Villages")
    public String[] pixieVillages = new String[0];

    @Config.Comment({ "Siren islands.",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7 })
    @Config.Name("Siren Islands")
    public String[] sirenIslands = new String[0];

    @Config.Comment({ "Hydra caves.",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5, SYNTAX_6, SYNTAX_7 })
    @Config.Name("Hydra Caves")
    public String[] hydraCaves = new String[0];

    @Config.Comment({ "Snow villages.",
            SYNTAX_1, SYNTAX_2, SYNTAX_3, SYNTAX_4, SYNTAX_5,
            "NOTE: on/off only. Ice&Fire reads the snow village chance inside MapGenSnowVillage,",
            "not in the generator method this module hooks, so a ':<chance>' part is ignored here." })
    @Config.Name("Snow Villages")
    public String[] snowVillages = new String[0];

    @Config.Comment({ "Copper ore.", ORE_1, ORE_2, ORE_3, ORE_4, SYNTAX_5 })
    @Config.Name("Ore: Copper")
    public String[] oreCopper = new String[0];

    @Config.Comment({ "Silver ore.", ORE_1, ORE_2, ORE_3, ORE_4, SYNTAX_5 })
    @Config.Name("Ore: Silver")
    public String[] oreSilver = new String[0];

    @Config.Comment({ "Amethyst ore.", ORE_1, ORE_2, ORE_3, ORE_4, SYNTAX_5 })
    @Config.Name("Ore: Amethyst")
    public String[] oreAmethyst = new String[0];

    @Config.Comment({ "Ruby ore.", ORE_1, ORE_2, ORE_3, ORE_4, SYNTAX_5 })
    @Config.Name("Ore: Ruby")
    public String[] oreRuby = new String[0];

    @Config.Comment({ "Sapphire ore.", ORE_1, ORE_2, ORE_3, ORE_4, SYNTAX_5 })
    @Config.Name("Ore: Sapphire")
    public String[] oreSapphire = new String[0];
}
