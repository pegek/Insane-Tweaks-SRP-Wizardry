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

    @Config.Name("Floor Y Min")
    @Config.RangeInt(min = 6, max = 120)
    public int floorYMin = 18;

    @Config.Name("Floor Y Max")
    @Config.RangeInt(min = 6, max = 120)
    public int floorYMax = 26;

    @Config.Comment("BiomeDictionary types a sanctum's centre may not be in.")
    @Config.Name("Excluded Biome Types")
    public String[] excludedBiomeTypes = { "OCEAN", "RIVER", "BEACH" };
}
