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
            "(only rolls when the killer wears the Blessed Ring)."})
    @Config.Name("Fragment Drop Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double fragmentDropChance = 0.05;

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

    @Config.Comment({"Total valid-condition growth time of the Corrupted Sapling, in ticks",
            "(default 24000 = 20 min). Growth pauses while conditions are unmet."})
    @Config.Name("Sapling Growth Ticks")
    @Config.RangeInt(min = 20)
    public int saplingGrowthTicks = 24000;

    @Config.Comment({"Radius in which the sapling looks for infestation (living parasites)",
            "and for its Ring-wearing owner."})
    @Config.Name("Sapling Condition Radius")
    @Config.RangeInt(min = 4, max = 64)
    public int saplingConditionRadius = 32;

    @Config.Comment({"Minimum living parasites within the radius for the 'active infestation'",
            "condition (alternative: any srparasites block within 8 blocks)."})
    @Config.Name("Sapling Min Parasites")
    @Config.RangeInt(min = 0)
    public int saplingMinParasites = 2;

    @Config.Comment({"Corrupted Sapling max health at SRP evolution phase 1-3 (also the phase-0",
            "fallback, though planting is blocked at phase 0). Read live; re-evaluated every 20t."})
    @Config.Name("Sapling HP Phase 1-3")
    @Config.RangeInt(min = 1)
    public int saplingHpPhase1 = 50;

    @Config.Comment({"Corrupted Sapling max health at SRP evolution phase 4-5."})
    @Config.Name("Sapling HP Phase 4-5")
    @Config.RangeInt(min = 1)
    public int saplingHpPhase4 = 80;

    @Config.Comment({"Corrupted Sapling max health at SRP evolution phase 6-8."})
    @Config.Name("Sapling HP Phase 6-8")
    @Config.RangeInt(min = 1)
    public int saplingHpPhase6 = 100;

    @Config.Comment({"Corrupted Sapling max health at SRP evolution phase 9-10."})
    @Config.Name("Sapling HP Phase 9-10")
    @Config.RangeInt(min = 1)
    public int saplingHpPhase9 = 140;

    @Config.Comment({"Delay in ticks between eating a Corrupted Fruit and the unavoidable death",
            "(default 120 = 6 s)."})
    @Config.Name("Corrupted Fruit Doom Ticks")
    @Config.RangeInt(min = 1)
    public int corruptedFruitDoomTicks = 120;
}
