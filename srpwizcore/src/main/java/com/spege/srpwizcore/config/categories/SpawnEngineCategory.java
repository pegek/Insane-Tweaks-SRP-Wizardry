package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * SpawnEngine v1 — native per-dimension spawn control (spec:
 * {@code notes/spawnengine_v1_spec_2026-07-27.md} in the DEv 1.2 instance).
 *
 * <p>Every value is read live: {@code mixins.srpwizcore.early.json} declares no
 * {@code IMixinConfigPlugin}, so {@code MixinWorldEntitySpawner} applies unconditionally and
 * each flag below is a runtime early-return. That is also why none of them carries
 * {@code @Config.RequiresMcRestart}. The string lists are re-parsed by
 * {@code SpawnEngine.reload()} from the {@code OnConfigChangedEvent} handler.
 */
public class SpawnEngineCategory {

    @Config.Comment({
            "Master switch for the population budgets, the pass throttle and the refill",
            "buckets. The vanilla hostile mob-cap is BYPASSED in dim 150 by an unidentified",
            "mechanism (measured 2026-07-21: 1600-2000 hostiles at cap 55), so the engine does",
            "not repair vanilla's comparison - it REPLACES it with its own, executed before.",
            "Does NOT gate 'Strip SRParasites In Dim 150' or 'Diag Logging'. Read live."
    })
    @Config.Name("Enable Spawn Engine")
    public boolean enableSpawnEngine = true;

    @Config.Comment({
            "Dimensions under engine control. Every other dimension is untouched (except the",
            "SRParasites strip below, which is dim-150-only and independent of this list)."
    })
    @Config.Name("Engine Dims")
    public int[] engineDims = { 150 };

    @Config.Comment({
            "Run the natural-spawn pass in engine dims only every N ticks (1 = vanilla",
            "cadence). The every-400-ticks animal pass is never throttled, so any value is",
            "safe. Start at 1 (parity); raise only after the F1 measurement."
    })
    @Config.Name("Hostile Pass Interval (ticks)")
    @Config.RangeInt(min = 1, max = 100)
    public int hostilePassInterval = 1;

    @Config.Comment({
            "Per-dim TOTAL population budgets, replacing the bypassed vanilla comparison.",
            "Format: dim:TYPE=N  (TYPE: MONSTER/CREATURE/AMBIENT/WATER_CREATURE).",
            "Counted exactly like vanilla's own World.countEntities(type, true), i.e. every",
            "loaded entity matching Entity.isCreatureType(type, true).",
            "The AMBIENT=15 / WATER_CREATURE=5 defaults reproduce vanilla's own base caps for",
            "a single player (vanilla cap = base * eligibleChunks / 289, and one player caps",
            "eligibleChunks at 289). They exist so the whole-pass stop below can ever fire -",
            "see 'Ungoverned Types Veto Stop'."
    })
    @Config.Name("Type Budgets")
    public String[] typeBudgets = {
            "150:MONSTER=170",
            "150:CREATURE=25",
            "150:AMBIENT=15",
            "150:WATER_CREATURE=5"
    };

    @Config.Comment({
            "How the whole-pass stop treats a creature type that has NO budget in 'Type",
            "Budgets' (and no refill bucket) for that dimension.",
            "true  (default, safe): such a type keeps the pass alive - the pass is skipped only",
            "      when every type vanilla would process this tick is at its budget. Nothing",
            "      that could legally spawn is ever suppressed.",
            "false (aggressive): unbudgeted types are ignored when deciding to skip, so a",
            "      capped MONSTER budget alone stops the whole pass. Cheaper, but ambient/water",
            "      spawns stop too while monsters are capped.",
            "Read live."
    })
    @Config.Name("Ungoverned Types Veto Stop")
    public boolean ungovernedTypesVetoStop = true;

    @Config.Comment({
            "Per-namespace / per-entity budgets enforced on the candidate LIST",
            "(WorldEvent.PotentialSpawns) - vanilla never constructs what is not on the list.",
            "Format: dim:namespace=N  or  dim:modid:entity=N (full id = per-type).",
            "INVARIANT: namespace-level 'iceandfire' entries are REJECTED with an ERROR",
            "(a namespace cap would delete unique nest dragons) - iceandfire only per-type."
    })
    @Config.Name("Namespace Budgets")
    public String[] namespaceBudgets = {
            "150:defiledlands=65",
            "150:deeperdepths=45",
            "150:babymobs=15",
            "150:mutantbeasts=4",
            "150:minecraft=150",
            "150:defiledlands:slime_defiled=8",
            "150:iceandfire:if_troll=12",
            "150:iceandfire:if_cockatrice=12",
            "150:iceandfire:deathworm=8",
            "150:iceandfire:dread_lich=2"
    };

    @Config.Comment({
            "E4 - population REBUILD rate limit (token bucket), the anti-infinite-horde",
            "mechanism: a cap limits the LEVEL, this limits the RATE of return to it. Without",
            "it, killing a dozen mobs frees a dozen cap slots that the very next pass refills.",
            "Format: dim=tokensPerMinute. v1 supports the MONSTER bucket only, because the",
            "value consumed is findChunksForSpawning's return value - one number covering the",
            "whole pass, with no per-type breakdown. 0 or absent = no rate limit for that dim."
    })
    @Config.Name("Monster Refill Rate (per minute)")
    public String[] monsterRefillRate = { "150=12" };

    @Config.Comment({
            "E4 bucket capacity = the largest burst allowed after a long quiet period.",
            "Format: dim=N. Absent = 20."
    })
    @Config.Name("Monster Refill Burst")
    public String[] monsterRefillBurst = { "150=24" };

    @Config.Comment({
            "Hard invariant: strip srparasites:* entries from dim-150 spawn candidate lists.",
            "Independent of the master switch - works with the engine disabled. Read live."
    })
    @Config.Name("Strip SRParasites In Dim 150")
    public boolean stripSrpInDim150 = true;

    @Config.Comment({
            "F0 diagnostics: throttled logging of per-type counts vs budgets, bucket tokens and",
            "pass results in engine dims. Each line also carries vanilla's OWN count",
            "(World.countEntities) next to the base cap from EnumCreatureType, which is the",
            "ground truth for the dim-150 cap-bypass investigation: a count far above the cap",
            "means vanilla's comparison is being satisfied by an inflated eligible-chunk",
            "count, not by an undercount. Also active while the engine is disabled."
    })
    @Config.Name("Diag Logging")
    public boolean diagLogging = false;
}
