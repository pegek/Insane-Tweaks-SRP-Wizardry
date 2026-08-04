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

    @Config.Comment({"Drain SRP's 'dead blood' fluid (srparasites:deadblood) inside the dome, turning it to",
            "air. Neither SRP's own PurifyMappings nor our block heuristic recognise it - its registry",
            "path contains none of the infestation keywords - so without this it stays forever. Read live."})
    @Config.Name("Cleanse Dead Blood")
    public boolean cleanseDeadBlood = true;

    @Config.Comment({"Max dead-blood cells drained in one pass. Dead blood is a non-finite BlockFluidClassic:",
            "clearing a single cell just refills from its neighbours' quanta, so the whole connected",
            "cluster has to go at once. When a drain runs it replaces that tick's normal cleanse pass.",
            "Read live."})
    @Config.Name("Dead Blood Drain Per Tick")
    @Config.RangeInt(min = 16, max = 8192)
    public int deadBloodDrainPerTick = 256;

    @Config.Comment({"Make dead blood harmless inside the dome: no damage, no Corrosive/Viral, and no free",
            "healing for parasites standing in it. Worth having even with the drain on, because the",
            "fluid's damage writes health DIRECTLY (setHealth), bypassing armour, i-frames and",
            "LivingHurtEvent entirely - nothing else in the pack can mitigate it.",
            "READ LIVE: this gates the mixin's handler body, NOT whether the mixin is applied."})
    @Config.Name("Neutralize Dead Blood")
    public boolean neutralizeDeadBlood = true;

    @Config.Comment({"Execute any SRP parasite that has spent long enough inside the dome, so a parasite can't",
            "camp indefinitely while Purge Fire chips at it. Read live."})
    @Config.Name("Enable Dwell Execution")
    public boolean enableDwellExecution = true;

    @Config.Comment("Ticks a parasite must accumulate inside the dome before it is executed (2400 = 2min). Read live.")
    @Config.Name("Dwell Execution Ticks")
    @Config.RangeInt(min = 20, max = 72000)
    public int dwellExecutionTicks = 2400;

    @Config.Comment({"How often (in ticks) the dwell counter decays for a parasite that has left the dome.",
            "Decay rather than a hard reset, so stepping outside for a moment does not wipe the timer.",
            "Read live."})
    @Config.Name("Dwell Decay Interval")
    @Config.RangeInt(min = 1, max = 200)
    public int dwellDecayInterval = 20;

    @Config.Comment({"Entity ids a sanctuary may never delete outright, e.g. 'srparasites:overseer'. A bare",
            "entry with no namespace matches that path in ANY namespace, so 'overseer' covers the",
            "SRParasites, SRPExtra and SW: Parasites variants at once.",
            "Covers BOTH dwell execution and the join veto below - the question is the same either way.",
            "An exempt parasite still burns from purge fire, it just cannot be removed outright.",
            "Empty = nothing is exempt. Read live."})
    @Config.Name("Dwell Execution Exempt Entities")
    public String[] dwellExecutionExemptIds = {};

    @Config.Comment({"Remove parasites that JOIN the world inside a dome, instead of waiting for purge fire.",
            "'Veto Natural Spawn' only covers the vanilla natural-spawn path. SRP places most of its",
            "parasites with its own spawner - node relays, world events, summons from other parasites -",
            "and none of that goes through it. This catches those.",
            "TRADE-OFF: Forge 1.12.2 cannot tell a fresh spawn from a chunk being loaded, so this also",
            "removes parasites that already existed when their chunk loads inside the dome, rather than",
            "letting them burn. In-zone parasites already drop nothing, so the outcome is the same one",
            "purge fire would reach - just without hundreds of entities ticking first. Entities on the",
            "exempt list above are never removed this way.",
            "Turn this OFF if you would rather watch them burn. Read live."})
    @Config.Name("Veto Parasite Join In Zone")
    public boolean vetoParasiteJoin = true;

    @Config.Comment("Purge Fire: an active sanctuary ignites/damages parasites inside it. Read live.")
    @Config.Name("Enable Purge Fire")
    public boolean enablePurgeFire = true;

    @Config.Comment("Flat fire damage dealt to each parasite per Purge Fire cadence. Read live.")
    @Config.Name("Purge Fire Damage")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double purgeFireDamage = 1.0D;

    @Config.Comment({"Extra fire damage per cadence as a PERCENT of the parasite's max HP (added to the",
            "flat damage). Scales against SRP's huge HP pools. 1 = 1% of max HP each hit. Read live."})
    @Config.Name("Purge Fire Percent Damage")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double purgeFirePercentDamage = 1.0D;

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

    @Config.Comment({"Suppress item drops AND XP from SRP parasites that die inside a sanctuary, so the",
            "dome's own purge (fire/cleanse) can't be AFK-farmed for free loot. Read live."})
    @Config.Name("Veto Parasite Drops In Zone")
    public boolean vetoParasiteDrops = true;

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
