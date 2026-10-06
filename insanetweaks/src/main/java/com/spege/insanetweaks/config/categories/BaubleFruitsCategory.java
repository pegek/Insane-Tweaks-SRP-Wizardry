package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the whole Bauble Fruit system: the acquisition loop
 * (fragment drops → Corrupted Seed → Corrupted Sapling → Corrupted Fruit) and the
 * nine typed fruits that expand bauble slots.
 *
 * <p>These fields lived in the {@code tweaks} category up to 1.4.17 and were moved here in
 * 1.4.18 so the system is configured in one place. Forge does not migrate values between
 * categories — the moved fields fall back to their defaults once, in existing config files.
 *
 * <p>Master toggle is {@code modules.enableBaubleFruits}. Everything here is read live inside
 * the handlers, so no restart is needed.
 */
public class BaubleFruitsCategory {

    // =========================================================================
    // Consumption limits — how many fruits a single player may ever eat
    // =========================================================================

    @Config.Comment({"Cap the total number of Bauble Fruits a player may consume, ever.",
            "When false (default) a player can eventually eat all nine.",
            "Eating the same fruit type twice is always refused, regardless of this switch."})
    @Config.Name("Enable Fruits Eaten Cap")
    public boolean enableFruitCap = false;

    @Config.Comment({"Maximum number of Bauble Fruits one player may consume when the cap is on.",
            "Only counts fruits that actually granted a slot — refused ones are not counted."})
    @Config.Name("Max Fruits Eaten")
    @Config.RangeInt(min = 1, max = 9)
    public int maxFruitsEaten = 3;

    @Config.Comment({"Show a live 'Fruits consumed: N' (or N/max) line in every Bauble Fruit tooltip.",
            "The count is synced to the client on login, on respawn and after each fruit eaten."})
    @Config.Name("Show Eaten Counter In Tooltip")
    public boolean showEatenCounter = true;

    @Config.Comment({"Corrupted Fruit rolls its random gift only among fruits you can still receive,",
            "instead of any of the nine. With this off, a roll that lands on an already-consumed",
            "type grants nothing and the death sequence still runs.",
            "When no fruit is left to grant, the Corrupted Fruit refuses to be eaten at all."})
    @Config.Name("Corrupted Fruit Smart Roll")
    public boolean corruptedFruitSmartRoll = true;

    // =========================================================================
    // Acquisition loop — moved from the tweaks category in 1.4.18
    // =========================================================================

    @Config.Comment({"Corrupted Seed Fragment drop chance from high-tier parasites",
            "(only rolls when the killer wears the Blessed Ring or the Ring of the Seven Curses).",
            "Was 0.05 before 1.21.0; existing config files keep their old value."})
    @Config.Name("Fragment Drop Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double fragmentDropChance = 0.10;

    @Config.Comment({"Bad-luck protection: after this many eligible kills in a row without a fragment,",
            "the next eligible kill always drops one. Counted per player, survives death. 0 disables."})
    @Config.Name("Fragment Pity Kills")
    @Config.RangeInt(min = 0, max = 1000)
    public int fragmentPityKills = 15;

    @Config.Comment({"Registry-name prefixes of parasites that can drop Corrupted Seed Fragments.",
            "Exact names work too (a full name is its own prefix)."})
    @Config.Name("Fragment Drop Entities")
    public String[] fragmentDropEntities = {
            "srparasites:ada_", "srparasites:anc_",
            "srparasites:overseer", "srparasites:vigilante", "srparasites:warden",
            "srparasites:marauder", "srparasites:monarch", "srparasites:grunt",
            "srparasites:bomber_light", "srparasites:bomber_heavy", "srparasites:wraith",
            "srparasites:bogle", "srparasites:haunter", "srparasites:seeker",
            "srparasites:architect", "srparasites:succor", "srparasites:carrier_colony" };

    @Config.Comment({"How far the planting owner may stray from the Corrupted Sapling before the",
            "defense pauses (no growth, no new waves; parasites keep attacking)."})
    @Config.Name("Sapling Condition Radius")
    @Config.RangeInt(min = 4, max = 64)
    public int saplingConditionRadius = 32;

    // =========================================================================
    // Corrupted Sapling defense (1.21.0) — all read live
    // =========================================================================

    @Config.Comment({"Seconds after planting during which the sapling only lures nearby parasites,",
            "before the first wave."})
    @Config.Name("Sapling Lure Seconds")
    @Config.RangeInt(min = 0, max = 600)
    public int saplingLureSeconds = 60;

    @Config.Comment({"Number of waves. The last one also brings one elite parasite."})
    @Config.Name("Sapling Wave Count")
    @Config.RangeInt(min = 1, max = 10)
    public int saplingWaveCount = 4;

    @Config.Comment({"Seconds between waves. The sapling bears fruit one interval after the last",
            "wave starts, so the default event is 60 + 4 x 105 s = 8 minutes."})
    @Config.Name("Sapling Wave Interval Seconds")
    @Config.RangeInt(min = 10, max = 600)
    public int saplingWaveIntervalSeconds = 105;

    @Config.Comment({"Radius in which living parasites are pulled onto the sapling (every 2 s) and",
            "counted when sizing a wave."})
    @Config.Name("Sapling Lure Radius")
    @Config.RangeInt(min = 4, max = 64)
    public int saplingLureRadius = 24;

    @Config.Comment({"A wave tops the area up to this many parasites, plus half the SRP evolution",
            "phase, counting the ones already within the lure radius."})
    @Config.Name("Sapling Wave Base Count")
    @Config.RangeInt(min = 0, max = 32)
    public int saplingWaveBaseCount = 4;

    @Config.Comment({"Most parasites one wave may spawn (the elite comes on top)."})
    @Config.Name("Sapling Wave Max Spawn")
    @Config.RangeInt(min = 0, max = 32)
    public int saplingWaveMaxSpawn = 8;

    @Config.Comment({"Wave parasites appear on a ring this many blocks from the sapling (inner edge)."})
    @Config.Name("Sapling Spawn Ring Min")
    @Config.RangeInt(min = 2, max = 48)
    public int saplingSpawnRingMin = 10;

    @Config.Comment({"Outer edge of the wave spawn ring."})
    @Config.Name("Sapling Spawn Ring Max")
    @Config.RangeInt(min = 3, max = 64)
    public int saplingSpawnRingMax = 16;

    @Config.Comment({"Wave parasites at SRP evolution phase 1-3. Full entity ids."})
    @Config.Name("Sapling Wave Pool Early")
    public String[] saplingWavePoolEarly = {
            "srparasites:pri_longarms", "srparasites:pri_manducater", "srparasites:pri_arachnida",
            "srparasites:pri_reeker", "srparasites:rupter", "srparasites:buglin" };

    @Config.Comment({"Wave parasites at SRP evolution phase 4-6. Full entity ids."})
    @Config.Name("Sapling Wave Pool Mid")
    public String[] saplingWavePoolMid = {
            "srparasites:pri_longarms", "srparasites:pri_manducater", "srparasites:ada_longarms",
            "srparasites:ada_manducater", "srparasites:ada_arachnida", "srparasites:ada_reeker" };

    @Config.Comment({"Wave parasites at SRP evolution phase 7 and above. Full entity ids."})
    @Config.Name("Sapling Wave Pool Late")
    public String[] saplingWavePoolLate = {
            "srparasites:ada_longarms", "srparasites:ada_manducater", "srparasites:ada_arachnida",
            "srparasites:ada_reeker", "srparasites:grunt", "srparasites:seeker" };

    @Config.Comment({"Elite added to the last wave at SRP evolution phase 1-3."})
    @Config.Name("Sapling Elite Early")
    public String saplingEliteEarly = "srparasites:ada_manducater";

    @Config.Comment({"Elite added to the last wave at SRP evolution phase 4-6."})
    @Config.Name("Sapling Elite Mid")
    public String saplingEliteMid = "srparasites:ada_bolster";

    @Config.Comment({"Elite added to the last wave at SRP evolution phase 7 and above."})
    @Config.Name("Sapling Elite Late")
    public String saplingEliteLate = "srparasites:marauder";

    @Config.Comment({"Sapling max health when planted. Grows linearly to the end value with the event."})
    @Config.Name("Sapling HP Start")
    @Config.RangeInt(min = 1, max = 1024)
    public int saplingHpStart = 50;

    @Config.Comment({"Sapling max health at full growth."})
    @Config.Name("Sapling HP End")
    @Config.RangeInt(min = 1, max = 1024)
    public int saplingHpEnd = 80;

    @Config.Comment({"Sapling armor at full growth (0 when planted, linear in between)."})
    @Config.Name("Sapling Armor End")
    @Config.RangeInt(min = 0, max = 30)
    public int saplingArmorEnd = 6;

    @Config.Comment({"Sapling armor toughness at full growth (0 when planted, linear in between)."})
    @Config.Name("Sapling Toughness End")
    @Config.RangeInt(min = 0, max = 20)
    public int saplingToughnessEnd = 3;

    @Config.Comment({"The sapling heals 1 HP every this many seconds, paused or not."})
    @Config.Name("Sapling Regen Interval Seconds")
    @Config.RangeInt(min = 1, max = 600)
    public int saplingRegenIntervalSeconds = 10;

    @Config.Comment({"HP restored at once for every wave survived (when the next wave starts)."})
    @Config.Name("Sapling Wave Heal")
    @Config.RangeInt(min = 0, max = 1024)
    public int saplingWaveHeal = 5;

    @Config.Comment({"Ignore damage dealt by players, so the defender's own area spells cannot kill",
            "the sapling. /kill and the void still work; the owner can uproot it with sneak +",
            "right-click on an empty hand."})
    @Config.Name("Sapling Ignore Player Damage")
    public boolean saplingIgnorePlayerDamage = true;

    @Config.Comment({"Outline the sapling (entity glow) while the defense runs, so it stays visible",
            "inside a swarm."})
    @Config.Name("Sapling Glow")
    public boolean saplingGlow = true;

    @Config.Comment({"Planting needs an SRParasites block within this many blocks horizontally."})
    @Config.Name("Sapling Infestation Radius")
    @Config.RangeInt(min = 1, max = 32)
    public int saplingInfestationRadius = 8;

    @Config.Comment({"... and within this many blocks vertically."})
    @Config.Name("Sapling Infestation Height")
    @Config.RangeInt(min = 1, max = 16)
    public int saplingInfestationHeight = 4;

    @Config.Comment({"Dimensions where a Corrupted Seed cannot be planted. Waves spawn SRP parasites,",
            "and dimension 150 must never get any."})
    @Config.Name("Sapling Dimension Blacklist")
    public int[] saplingDimensionBlacklist = { 150 };

    @Config.Comment({"The sapling's own column must be free of solid blocks this many blocks up",
            "(no roof over it)."})
    @Config.Name("Sapling Column Clearance")
    @Config.RangeInt(min = 3, max = 32)
    public int saplingColumnClearance = 8;

    @Config.Comment({"Radius of the open area around the sapling (2 = a 5x5 square)."})
    @Config.Name("Sapling Crowd Radius")
    @Config.RangeInt(min = 1, max = 4)
    public int saplingCrowdRadius = 2;

    @Config.Comment({"Height of that open area above the sapling's base, in blocks."})
    @Config.Name("Sapling Crowd Height")
    @Config.RangeInt(min = 1, max = 6)
    public int saplingCrowdHeight = 3;

    @Config.Comment({"How many solid blocks the open area may contain. Blocks without a collision box",
            "(grass, flowers) never count."})
    @Config.Name("Sapling Crowd Tolerance")
    @Config.RangeInt(min = 0, max = 50)
    public int saplingCrowdTolerance = 3;

    @Config.Comment({"The sapling cannot grow inside a Sanctuary or within this many blocks of one."})
    @Config.Name("Sapling Sanctuary Margin")
    @Config.RangeInt(min = 0, max = 256)
    public int saplingSanctuaryMargin = 32;

    @Config.Comment({"Delay in ticks between eating a Corrupted Fruit and the unavoidable death",
            "(default 120 = 6 s)."})
    @Config.Name("Corrupted Fruit Doom Ticks")
    @Config.RangeInt(min = 1)
    public int corruptedFruitDoomTicks = 120;
}
