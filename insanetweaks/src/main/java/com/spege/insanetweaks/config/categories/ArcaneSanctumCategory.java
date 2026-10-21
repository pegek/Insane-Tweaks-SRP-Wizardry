package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class ArcaneSanctumCategory {

    @Config.Comment("Generate Sunken Arcane Sanctums. Registers the world generator, so it needs a restart.")
    @Config.Name("Enabled")
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment({ "Dimensions sanctums generate in. 150 is always refused (no-SRP dimension); the Nether (-1)",
            "and the End (1) are refused until they have placement rules of their own. 111 gets the",
            "obsidian palette and earth runestone; any other listed dimension uses the Overworld ones." })
    @Config.Name("Dimensions")
    public int[] dimensions = { 0, 111 };

    @Config.Comment({ "Lowest street level, as 'dimension=y', for dimensions with buildings over the ground",
            "(Lost Cities: its ground level). There the entrance shaft ends on the first floor at or above",
            "this y with two blocks of headroom - a street or a building's ground floor - instead of on a",
            "roof, and the surface ruin is left out when something stands over it. Read live." })
    @Config.Name("Street Level")
    public String[] streetLevel = { "111=71" };

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
    public String[] roomAnchorPool = { "insanetweaks:sim_wizard;3", "insanetweaks:sim_battlemage;2" };

    @Config.Comment({ "Chance (0-1) that the main chest also holds one random bauble/artefact from any mod.",
            "0 since 1.28.0: sanctum chests roll parasite relics instead (loot tables)." })
    @Config.Name("Main Chest Bauble Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double mainChestBaubleChance = 0.0;

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

    @Config.Comment("Extra rolls per EBW bookshelf for an enchanted book (see the two chances below).")
    @Config.Name("Bookshelf Extra Rolls")
    @Config.RangeInt(min = 0, max = 12)
    public int bookshelfExtraRolls = 2;

    @Config.Comment("Chance (0-1) per extra roll of a treasure-allowed enchanted book (level 30 enchanting).")
    @Config.Name("Bookshelf Treasure Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double bookshelfTreasureChance = 0.03;

    @Config.Comment({ "Chance (0-1) per extra roll of an InsaneTweaks book: any of its enchantments at level 1",
            "(curses included) or a book-grantable Property Book. A deliberate exception to the quest gate." })
    @Config.Name("Bookshelf Insane Book Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double bookshelfInsaneBookChance = 0.04;

    @Config.Comment("Chance (0-1) that a fallen room anchor (sim wizard or battlemage) drops an artefact.")
    @Config.Name("Anchor Artefact Chance Room")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double anchorArtefactChanceRoom = 0.10;

    @Config.Comment("Chance (0-1) that a fallen ritual-chamber anchor (parasite) drops an artefact.")
    @Config.Name("Anchor Artefact Chance Chamber")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double anchorArtefactChanceChamber = 0.50;

    @Config.Comment({ "Stop Roguelike Dungeons from generating a dungeon within ~8 chunks of a sanctum start - its",
            "rooms would cut through the sanctum's walls and let the parasites out. Read live." })
    @Config.Name("Block Foreign Dungeons")
    public boolean blockForeignDungeons = true;

    @Config.Comment({ "Teleport spells a sanctum guardian may cast but cannot use to take itself or its target out",
            "of a sanctum that is not yet cleansed: whoever lands outside is put back. Read live." })
    @Config.Name("Sanctum Guarded Spells")
    public String[] sanctumGuardedSpells = { "ebwizardry:banish", "ebwizardry:blink", "ebwizardry:phase_step" };

    @Config.Comment({ "Spells a trap dispenser fires (as scrolls), one picked per dispenser by position.",
            "Ids that do not resolve, or spells EBW does not allow from a dispenser, are skipped." })
    @Config.Name("Trap Spells")
    public String[] trapSpells = { "ebwizardry:magic_missile", "ebwizardry:firebolt", "ebwizardry:ice_shard",
            "ebwizardry:poison" };
}
