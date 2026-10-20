package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class ArcaneSanctumCategory {

    @Config.Comment("Generate Sunken Arcane Sanctums. Registers the world generator, so it needs a restart.")
    @Config.Name("Enabled")
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment({ "Dimensions sanctums generate in. 150 is always refused (no-SRP dimension).",
            "Nether and End get their own themes later; listing them now uses the overworld theme." })
    @Config.Name("Dimensions")
    public int[] dimensions = { 0 };

    @Config.Comment("Region size in chunks. Each region holds at most one sanctum.")
    @Config.Name("Region Size")
    @Config.RangeInt(min = 8, max = 128)
    public int regionSize = 32;

    @Config.Comment("Chance (0-1) that a region holds a sanctum.")
    @Config.Name("Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double chance = 0.5;

    @Config.Comment("Level 1 floor; the ritual level is 10 lower (level 1 is clamped to at least 17).")
    @Config.Name("Floor Y Min")
    @Config.RangeInt(min = 6, max = 120)
    public int floorYMin = 22;

    @Config.Name("Floor Y Max")
    @Config.RangeInt(min = 6, max = 120)
    public int floorYMax = 30;

    @Config.Comment("BiomeDictionary types a sanctum's centre may not be in.")
    @Config.Name("Excluded Biome Types")
    public String[] excludedBiomeTypes = { "OCEAN", "RIVER", "BEACH" };

    @Config.Comment("Mobs pinned on the side rooms' anchors, as 'modid:entity;weight'.")
    @Config.Name("Room Anchor Pool")
    public String[] roomAnchorPool = { "insanetweaks:sim_wizard;3", "insanetweaks:sim_battlemage;1" };

    @Config.Comment("Chance (0-1) that the main chest also holds one random bauble/artefact from any mod.")
    @Config.Name("Main Chest Bauble Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double mainChestBaubleChance = 0.25;

    @Config.Comment("Item ids never picked as the main chest's bauble.")
    @Config.Name("Bauble Blacklist")
    public String[] baubleBlacklist = { "enigmaticlegacy:cursed_ring" };

    @Config.Comment("Chance (0-1) per roll that an EBW bookshelf gets a spell book.")
    @Config.Name("Bookshelf Book Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double bookshelfBookChance = 0.08;

    @Config.Comment("Rolls per EBW bookshelf. Books: 50% Abomination, 25% any Master, 25% any Advanced spell.")
    @Config.Name("Bookshelf Book Rolls")
    @Config.RangeInt(min = 0, max = 12)
    public int bookshelfBookRolls = 3;

    @Config.Comment("Chance (0-1) that a fallen room anchor (sim wizard or battlemage) drops an artefact.")
    @Config.Name("Anchor Artefact Chance Room")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double anchorArtefactChanceRoom = 0.20;

    @Config.Comment("Chance (0-1) that a fallen ritual-chamber anchor (parasite) drops an artefact.")
    @Config.Name("Anchor Artefact Chance Chamber")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double anchorArtefactChanceChamber = 0.50;

    @Config.Comment({ "Spells a trap dispenser fires (as scrolls), one picked per dispenser by position.",
            "Ids that do not resolve, or spells EBW does not allow from a dispenser, are skipped." })
    @Config.Name("Trap Spells")
    public String[] trapSpells = { "ebwizardry:magic_missile", "ebwizardry:firebolt", "ebwizardry:ice_shard",
            "ebwizardry:poison" };
}
