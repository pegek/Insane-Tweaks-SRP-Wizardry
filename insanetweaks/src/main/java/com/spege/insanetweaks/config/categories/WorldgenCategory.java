package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/** Worldgen tunables. Currently the discreet dormant-waystone marker (Dormant Eye feature). */
public class WorldgenCategory {

    @Config.Comment({"Master switch for natural Overworld generation of the dormant waystone.",
            "Requires MC restart (gates the world-generator registration).",
            "The block, registry and manual place/break hooks stay active regardless."})
    @Config.Name("Dormant Waystone Worldgen")
    @Config.RequiresMcRestart
    public boolean dormantWaystoneEnabled = true;

    @Config.Comment({"Per-chunk probability of a dormant waystone generating (0.0-1.0).",
            "Kept low so they stay discreet: 0.006 ~= one per ~160 chunks. Read live (no restart)."})
    @Config.Name("Dormant Waystone Chance Per Chunk")
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public double dormantWaystoneChancePerChunk = 0.006D;
}
