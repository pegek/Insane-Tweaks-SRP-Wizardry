package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/** Tunables for the Sanctuary Dome module (see docs/superpowers/specs/2026-07-19-sanctuary-dome-design.md). */
public class SanctuaryCategory {

    @Config.Comment({"Base protection radius (blocks) per pyramid tier 1-4. Index 0 = tier 1.",
            "Read live (no restart)."})
    @Config.Name("Tier Radii")
    public int[] tierRadii = new int[] { 16, 32, 48, 64 };

    @Config.Comment({"Blocks allowed in the pyramid layers, by registry name (e.g. minecraft:iron_block).",
            "A layer counts only if fully built from these. Read live."})
    @Config.Name("Pyramid Blocks")
    public String[] pyramidBlocks = new String[] { "minecraft:iron_block", "minecraft:diamond_block" };

    @Config.Comment({"Fuel items for the cleanse function, one per line as 'registry=value'.",
            "'value' = how many cleanse-conversions one item powers. Read live. Malformed lines ignored."})
    @Config.Name("Fuel Items")
    public String[] fuelItems = new String[] { "minecraft:emerald=64" };

    @Config.Comment("Infested blocks the cleanse reverts per tick (spread load). Read live.")
    @Config.Name("Cleanse Blocks Per Tick")
    @Config.RangeInt(min = 1, max = 256)
    public int cleanseBlocksPerTick = 8;

    @Config.Comment({"Cylinder positions the cleanse scan EXAMINES per tick (cheap block-state reads).",
            "Separate from Cleanse Blocks Per Tick (actual reverts). Higher = faster sweep of a large dome.",
            "Read live (no restart)."})
    @Config.Name("Cleanse Scan Per Tick")
    @Config.RangeInt(min = 64, max = 65536)
    public int cleanseScanPerTick = 4096;

    @Config.Comment("Ticks between pyramid re-validations in the core TE. Read live.")
    @Config.Name("Pyramid Revalidate Interval")
    @Config.RangeInt(min = 20, max = 1200)
    public int pyramidRevalidateInterval = 40;

    @Config.Comment("Extra radius (blocks) granted per radius-upgrade item in the core. Read live.")
    @Config.Name("Upgrade Radius Bonus")
    @Config.RangeInt(min = 0, max = 128)
    public int upgradeRadiusBonus = 16;

    @Config.Comment({"Dimension IDs where the dome is INERT (parasite dimensions stay hostile).",
            "Read live."})
    @Config.Name("Dimension Blacklist")
    public int[] dimensionBlacklist = new int[] { 111 };

    @Config.Comment("Master switch for the natural-spawn veto (Forge CheckSpawn). Read live.")
    @Config.Name("Veto Natural Spawn")
    public boolean vetoNaturalSpawn = true;

    @Config.Comment({"Master switch for the block-infestation veto (mixin on BeckonBlockInfestation).",
            "Requires MC restart (mixin gate)."})
    @Config.Name("Veto Block Infestation")
    @Config.RequiresMcRestart
    public boolean vetoBlockInfestation = true;

    @Config.Comment("Whether cleanse is ON by default on a freshly placed core. Read live.")
    @Config.Name("Cleanse Enabled By Default")
    public boolean cleanseEnabledByDefault = true;

    @Config.Comment("Client: render the particle border of active domes. Read live.")
    @Config.Name("Particle Border")
    public boolean particleBorder = true;

    @Config.Comment({"Log per-core sanctuary state to the game log on tier/status change.",
            "For debugging whether a pyramid is detected. Read live (no restart)."})
    @Config.Name("Debug Logging")
    public boolean debugLogging = false;

    @Config.Comment("Purge Fire: an active sanctuary ignites/damages parasites inside it. Read live.")
    @Config.Name("Enable Purge Fire")
    public boolean enablePurgeFire = true;

    @Config.Comment("Fire damage dealt to each parasite per Purge Fire cadence. Read live.")
    @Config.Name("Purge Fire Damage")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double purgeFireDamage = 1.0D;

    @Config.Comment("Ticks between Purge Fire damage applications (10 = 0.5s, Aegis parity). Read live.")
    @Config.Name("Purge Fire Interval")
    @Config.RangeInt(min = 1, max = 200)
    public int purgeFireInterval = 10;

    @Config.Comment("Hard cap (blocks) on the Purge Fire radius, regardless of protection radius. Read live.")
    @Config.Name("Purge Fire Radius Cap")
    @Config.RangeInt(min = 1, max = 128)
    public int purgeFireRadiusCap = 128;

    @Config.Comment("Veto parasite block-breaking/griefing inside an active sanctuary. Read live.")
    @Config.Name("Veto Block Break")
    public boolean vetoBlockBreak = true;
}
