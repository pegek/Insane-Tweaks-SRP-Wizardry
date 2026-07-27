package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Small performance/correctness guards for third-party pack mods (Doomlike Dungeons, Chocolate
 * Quest Repoured, Raids-Backport).
 *
 * <p>Each mixin config is queued only when its mod is present, but none of them declares an
 * {@code IMixinConfigPlugin} — so these flags do <b>not</b> gate mixin application. They are
 * read live inside the handlers, which is why none carries {@code @Config.RequiresMcRestart}.
 */
public class PerfGlueCategory {

    @Config.Comment({
            "Doomlike Dungeons: skip building dungeon plans whose map is null instead of",
            "throwing an NPE per chunk the plan spans (364 exceptions / 6393 log lines in one",
            "three-minute burst, dim 150, 2026-07-26). Adds and removes no dungeons — the",
            "original code could not build those plans either, it only threw on the way out.",
            "Read live at call time (no restart). Default ON."
    })
    @Config.Name("Doomlike: Null Dungeon Map Guard")
    public boolean doomlikeNullMapGuard = true;

    @Config.Comment({
            "CQR: skip getNearestStructurePos ring scans in OpenTerrainGenerator dimensions for",
            "structures disabled in that dimension's WorldConfig. Such a scan can never find",
            "anything and always runs to the attempt limit (measured 143.6 s = 49.4% of the",
            "dim-150 worldgen profile, 2026-07-26). The guard returns the same 'nothing in",
            "range' answer a completed scan would have, so dungeon placement is unchanged.",
            "Read live at call time (no restart). Default ON."
    })
    @Config.Name("CQR: Skip Disabled-Structure Scans (OTG dims)")
    public boolean cqrStructureScanGuard = true;

    @Config.Comment({
            "DefiledLands: percentage of CorruptionHelper.spread calls allowed through the",
            "HEAD gate. The mod has NO config for the block-corruption pace (conversionRate is",
            "unrelated), and spread was 13.5% of dim-150 spike time in the 2026-07-27 re-profile.",
            "Cuts the corruption speed and the tick cost by the same factor: 50 = half pace,",
            "100 = untouched vanilla behaviour, 0 = corruption spread fully frozen.",
            "Read live at call time (no restart). Default 50."
    })
    @Config.Name("DefiledLands: Corruption Spread Percent")
    @Config.RangeInt(min = 0, max = 100)
    public int defiledCorruptionSpreadPct = 50;

    @Config.Comment({
            "Raids-Backport: register WorldDataRaids in the per-world storage it is actually",
            "read from, instead of the global one. Fixes a per-world-tick disk stat() (384 ms",
            "in the 2026-07-25 overworld profile), a per-tick allocation, and a cross-dimension",
            "overwrite of the shared 'raids' save key. Dim 0 keeps its existing raids.dat;",
            "other dimensions start with clean raid state (it was overwritten every tick",
            "anyway). Read live at call time (no restart). Default ON."
    })
    @Config.Name("Raids: Per-World Storage Consistency")
    public boolean raidsPerWorldStorage = true;
}
