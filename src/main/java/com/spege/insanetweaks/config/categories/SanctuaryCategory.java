package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/** Tunables for the Sanctuary Dome module (see docs/superpowers/specs/2026-07-19-sanctuary-dome-design.md). */
public class SanctuaryCategory {

    @Config.Comment({"Base protection radius (blocks) per pyramid tier 1-4. Index 0 = tier 1.",
            "Read live (no restart)."})
    @Config.Name("Tier Radii")
    public int[] tierRadii = new int[] { 16, 32, 48, 64 };

    @Config.Comment({"Mana-fuel items for the Sanctuary's upkeep, one per line as 'registry=value'.",
            "'value' = how many upkeep units one item is worth (see sanctuaryCost.Upkeep). Cleanse is NOT",
            "fuel-gated any more. Read live. Malformed lines ignored."})
    @Config.Name("Fuel Items")
    public String[] fuelItems = new String[] {
            "ebwizardry:crystal_shard=4",
            "ebwizardry:magic_crystal=36",
            "ebwizardry:grand_crystal=180",
            "ebwizardry:astral_diamond=360",
            "ebwizardry:crystal_flower=8" };

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

    @Config.Comment("Ticks between Nexus tier/radius/region re-validations in the core TE. Read live.")
    @Config.Name("Revalidate Interval")
    @Config.RangeInt(min = 20, max = 1200)
    public int revalidateInterval = 40;

    @Config.Comment({"Registry name of the block the Nexus ritual consumes (SRP evolution lure).",
            "Read live."})
    @Config.Name("Lure Block Id")
    public String lureBlockId = "srparasites:evolutionlure";

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

    @Config.Comment({"Veto SRP parasite-node structure generation inside a sanctuary (mixin on",
            "WorldGenParasiteNodeCore). Stops the node-vs-cleanse block-update storm at the source.",
            "Read live (the mixin is always loaded; this only gates its effect)."})
    @Config.Name("Veto Node Generation")
    public boolean vetoNodeGeneration = true;

    @Config.Comment("Whether cleanse is ON by default on a freshly placed core. Read live.")
    @Config.Name("Cleanse Enabled By Default")
    public boolean cleanseEnabledByDefault = true;

    @Config.Comment({"Use SRP's own authoritative infested->vanilla block map (PurifyMappings) for the",
            "cleanse instead of our heuristic, when SRP is present. Falls back to the heuristic if a",
            "block is unmapped or SRP's classes are absent. Read live."})
    @Config.Name("Native Block Purify")
    public boolean nativeBlockPurify = true;

    @Config.Comment({"Periodically reset parasite BIOMES to natural inside the dome via SRP's own",
            "throttled queue (killBiome) - stops biome-driven spread at the root. Blocks are still",
            "handled by the cleanse. Read live."})
    @Config.Name("Native Biome Reset")
    public boolean nativeBiomeReset = true;

    @Config.Comment("Ticks between native biome-reset / node-purge passes (100 = 5s). Read live.")
    @Config.Name("Biome Reset Interval Ticks")
    @Config.RangeInt(min = 20, max = 6000)
    public int biomeResetIntervalTicks = 100;

    @Config.Comment({"PREVENTION: kill any SRP parasite node / colony heart whose position falls inside",
            "the dome, using SRP's own removal + airing the block. Stops a source at the root before it",
            "infests, instead of only healing after. Runs on the biome-reset cadence. Read live."})
    @Config.Name("Purge Nodes In Zone")
    public boolean purgeNodesInZone = true;

    @Config.Comment("Client: render the translucent protection dome (full sphere) around active cores. Read live.")
    @Config.Name("Render Dome")
    public boolean renderDome = true;

    @Config.Comment({"Client: emit a small pulsing 'ping' sphere at ACTIVE sanctuaries so the block is easy",
            "to locate (separate from the full dome). Read live."})
    @Config.Name("Pulse Locator")
    public boolean pulseLocator = true;

    @Config.Comment({"Radius (blocks) the locator ping expands to each pulse. A few blocks - it marks the",
            "block, not the whole dome. Read live."})
    @Config.Name("Pulse Radius")
    @Config.RangeDouble(min = 1.0D, max = 16.0D)
    public double pulseRadius = 3.5D;

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

    @Config.Comment("Enforce a per-player cap on ritual Sanctuaries (the Creative Sanctuary never counts). Read live.")
    @Config.Name("Limit Sanctuaries Per Player")
    public boolean enableSanctuaryLimit = true;

    @Config.Comment("Maximum ritual Sanctuaries one player may own (within the scope below). Read live.")
    @Config.Name("Max Sanctuaries Per Player")
    @Config.RangeInt(min = 1, max = 64)
    public int maxSanctuariesPerPlayer = 1;

    @Config.Comment({"Does the per-player limit count across EVERY dimension, or per single dimension?",
            "True  = every dimension: 1 sanctuary total anywhere (build in the Overworld -> can't build in the Nether).",
            "False = single dimension: the limit applies separately per dimension. Read live."})
    @Config.Name("Limit Counts Every Dimension")
    public boolean limitEveryDimension = true;
}
