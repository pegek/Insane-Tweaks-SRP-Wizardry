package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Small performance/correctness guards for third-party pack mods (Doomlike Dungeons, Chocolate
 * Quest Repoured, Raids-Backport, Defiled Lands).
 *
 * <p>Each mixin config is queued only when its mod is present, but none of them declares an
 * {@code IMixinConfigPlugin} — so these flags do <b>not</b> gate mixin application. They are
 * read live inside the handlers, which is why none carries {@code @Config.RequiresMcRestart}.
 */
public class PerfGlueCategory {

    @Config.Comment({
            "Doomlike Dungeons: stop a broken dungeon plan from flooding the log with errors.",
            "When a dungeon fails to lay itself out, the mod throws an error for every single chunk",
            "that dungeon would have covered - hundreds of them in a few seconds, which lags the",
            "server and buries anything useful in the log.",
            "With this ON the failed plan is skipped quietly. No dungeon is added or removed: the",
            "mod could not build that one either way.",
            "Does nothing unless Doomlike Dungeons is installed. No restart needed. Default ON."
    })
    @Config.Name("Doomlike: Null Dungeon Map Guard")
    public boolean doomlikeNullMapGuard = true;

    @Config.Comment({
            "Chocolate Quest Repoured: skip searches for structures that cannot exist.",
            "When something asks CQR for the nearest structure of a type that is switched off in an",
            "OpenTerrainGenerator dimension, CQR searches outward to its limit before giving up - and",
            "that search can take minutes of world-generation time for an answer that was never in",
            "doubt.",
            "With this ON it gives the same 'nothing nearby' answer straight away. Dungeon placement",
            "is unchanged.",
            "Does nothing unless CQR and OTG are both installed. No restart needed. Default ON."
    })
    @Config.Name("CQR: Skip Disabled-Structure Scans (OTG dims)")
    public boolean cqrStructureScanGuard = true;

    @Config.Comment({
            "Defiled Lands: how fast corruption spreads from block to block, as a percentage of",
            "normal. The mod itself has no setting for this, and in a corrupted area the spreading is",
            "one of the more expensive things running on the server.",
            "100 = untouched Defiled Lands behaviour. 50 = half speed and half the cost. 0 = corruption",
            "stops spreading entirely.",
            "Does nothing unless Defiled Lands is installed. No restart needed. Default 100."
    })
    @Config.Name("DefiledLands: Corruption Spread Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int defiledCorruptionSpreadPct = 100;

    @Config.Comment({
            "Raids-Backport: store each dimension's raid data with that dimension instead of globally.",
            "As shipped, every dimension writes its raid data to the same slot, so they overwrite each",
            "other every tick, and the game hits the disk on every world tick looking for it.",
            "With this ON the Overworld keeps its existing raid data; other dimensions start with",
            "clean raid state (it was being overwritten every tick anyway, so there was nothing to",
            "keep). That is a change to saved data, which is why this is off by default.",
            "Does nothing unless Raids-Backport is installed. No restart needed. Default OFF."
    })
    @Config.Name("Raids: Per-World Storage Consistency")
    public boolean raidsPerWorldStorage = false;
}
