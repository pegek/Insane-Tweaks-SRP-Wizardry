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
            "Take over natural mob spawning in the dimensions listed below, and hold their population",
            "to the limits set here.",
            "For heavily modded dimensions where the vanilla mob cap stops working and hundreds of",
            "hostiles pile up. Instead of trying to repair the vanilla check, the engine runs its own",
            "before it.",
            "IMPORTANT: everything below ships with values from the pack this was written for",
            "(dimension 150, its mod list, its numbers). Set 'Engine Dims' and the budgets for your own",
            "world before switching this on, or it will limit spawning in a dimension you did not mean.",
            "No restart needed. Default OFF."
    })
    @Config.Name("Enable Spawn Engine")
    public boolean enableSpawnEngine = false;

    @Config.Comment({
            "Which dimensions the engine controls. Every other dimension spawns exactly as normal.",
            "The default is an example from the pack this was written for - replace it with your own."
    })
    @Config.Name("Engine Dims")
    public int[] engineDims = { 150 };

    @Config.Comment({
            "Run the hostile-spawning check only every N ticks in controlled dimensions.",
            "1 = normal Minecraft timing, which is the safe starting point. Raising it saves server",
            "time at the cost of mobs appearing a little less promptly. Animal spawning is never",
            "throttled, so any value here is safe."
    })
    @Config.Name("Hostile Pass Interval (ticks)")
    @Config.RangeInt(min = 1, max = 100)
    public int hostilePassInterval = 1;

    @Config.Comment({
            "How many entities of each kind a dimension may hold at once.",
            "Written as 'dimension:TYPE=number', where TYPE is MONSTER, CREATURE, AMBIENT or",
            "WATER_CREATURE. Counted the same way Minecraft counts them itself.",
            "The AMBIENT and WATER_CREATURE defaults simply reproduce Minecraft's own limits for a",
            "single player - they are there so the engine can tell when nothing is allowed to spawn",
            "at all (see the next option).",
            "These defaults come from the pack this was written for. Replace them with your own."
    })
    @Config.Name("Type Budgets")
    public String[] typeBudgets = {
            "150:MONSTER=170",
            "150:CREATURE=25",
            "150:AMBIENT=15",
            "150:WATER_CREATURE=5"
    };

    @Config.Comment({
            "What to do about kinds of entity you did not give a budget to.",
            "ON (safe): a kind with no budget always keeps spawning working, so nothing that could",
            "legally spawn is ever blocked. The whole spawn pass is only skipped when every kind is",
            "already at its limit.",
            "OFF (aggressive): unbudgeted kinds are ignored, so a full monster budget alone stops the",
            "spawn pass. Cheaper, but ambient and water spawning stops too while monsters are capped.",
            "No restart needed."
    })
    @Config.Name("Ungoverned Types Veto Stop")
    public boolean ungovernedTypesVetoStop = true;

    @Config.Comment({
            "Limits for individual mods or individual mobs, applied before the game even decides what",
            "to spawn.",
            "Written as 'dimension:modid=number' for a whole mod, or 'dimension:modid:entity=number'",
            "for one mob.",
            "Note: a whole-mod limit on Ice and Fire is rejected on purpose - it would delete unique",
            "dragons - so list its mobs individually.",
            "These defaults come from the pack this was written for. Replace them with your own."
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
            "How fast a dimension is allowed to repopulate, in monsters per minute.",
            "A budget limits how many mobs there can be; this limits how fast they come back. Without",
            "it, killing a dozen mobs frees a dozen slots that the very next spawn pass refills, and an",
            "area you just cleared is full again seconds later.",
            "Written as 'dimension=perMinute'. 0 or no entry means no limit for that dimension.",
            "Applies to monsters only."
    })
    @Config.Name("Monster Refill Rate (per minute)")
    public String[] monsterRefillRate = { "150=12" };

    @Config.Comment({
            "How big a burst is allowed after a long quiet spell - the number of spawns saved up while",
            "nothing was spawning.",
            "Written as 'dimension=number'. Not listed means 20."
    })
    @Config.Name("Monster Refill Burst")
    public String[] monsterRefillBurst = { "150=24" };

    @Config.Comment({
            "Prevent Scape and Run: Parasites mobs from spawning naturally in dimension 150.",
            "Very specific to the pack this was written for, where parasites belong elsewhere. Works",
            "independently of the master switch above.",
            "No restart needed. Default OFF."
    })
    @Config.Name("Strip SRParasites In Dim 150")
    public boolean stripSrpInDim150 = false;

    @Config.Comment({
            "Log what the engine is doing: how many of each kind are alive against their budgets, how",
            "much refill allowance is left, and whether each spawn pass ran or was skipped.",
            "Each line also shows Minecraft's own count next to its own cap, which is how you tell",
            "whether the vanilla limit is being respected at all in that dimension.",
            "Works even with the engine off, so you can measure before you configure.",
            "No restart needed. Default OFF."
    })
    @Config.Name("Diag Logging")
    public boolean diagLogging = false;
}
