package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * "Cost of Power" tunables for the Sanctuary — the presence tax, the mana-fuel upkeep, the
 * empty-tank drain escalation, and the four dedicated upgrade slots.
 * See docs/superpowers/specs/2026-07-21-sanctuary-cost-of-power-design.md. All read live.
 */
public class SanctuaryCostCategory {

    @Config.Comment({"Master switch for the whole cost system (presence tax + fuel upkeep + drain).",
            "OFF = the Sanctuary is free (legacy behaviour). Read live."})
    @Config.Name("Enable Cost System")
    public boolean enableCost = true;

    // --- Layer A: always-on presence tax ---

    @Config.Comment({"Max-HP tithe (in HP, 2 = 1 heart) for every player inside an active Sanctuary.",
            "Applied as a direct attribute modifier, NOT a potion. Read live."})
    @Config.Name("Max HP Penalty")
    @Config.RangeDouble(min = 0.0D, max = 40.0D)
    public double maxHpPenalty = 5.0D;

    @Config.Comment({"Suppress passive healing (natural regen + Regeneration potion) inside the dome by",
            "cancelling LivingHealEvent. Instant Health still works. Read live."})
    @Config.Name("Suppress Regen")
    public boolean suppressRegen = true;

    // --- Fuel upkeep (time-based) ---

    @Config.Comment("Ticks between upkeep charges (100 = 5s). U1 upgrade doubles this. Read live.")
    @Config.Name("Upkeep Interval Ticks")
    @Config.RangeInt(min = 20, max = 12000)
    public int upkeepIntervalTicks = 100;

    @Config.Comment("Mana-fuel units burned each upkeep interval just for existing. Read live.")
    @Config.Name("Upkeep Cost")
    @Config.RangeInt(min = 1, max = 64)
    public int upkeepCost = 1;

    // --- Layer B/C: empty-tank drain escalation ---

    @Config.Comment("Ticks between drain pulses while the fuel tank is empty (20 = 1s). Read live.")
    @Config.Name("Drain Interval Ticks")
    @Config.RangeInt(min = 1, max = 200)
    public int drainIntervalTicks = 20;

    @Config.Comment({"Mana drained per pulse from each EBW wand-carrying player inside (Layer B).",
            "U1 upgrade halves this. Read live."})
    @Config.Name("Mana Drain Amount")
    @Config.RangeInt(min = 0, max = 10000)
    public int manaDrainAmount = 40;

    @Config.Comment({"HP drained from the OWNER per pulse when nobody inside has wand mana (Layer C).",
            "2 = 1 heart. U1 upgrade halves this. Read live."})
    @Config.Name("Owner HP Drain")
    @Config.RangeDouble(min = 0.0D, max = 40.0D)
    public double ownerHpDrain = 1.0D;

    // --- Upgrade slot bindings (each of the 4 upgrade slots is bound to one item) ---

    @Config.Comment("U1 (efficiency) required item. Halves upkeep + drain. Read live.")
    @Config.Name("Upgrade U1 Item")
    public String upgradeItemU1 = "insanetweaks:adaptation_upgrade";

    @Config.Comment("U2 (owner HP relief) required item. Halves the owner's max-HP tithe. Read live.")
    @Config.Name("Upgrade U2 Item")
    public String upgradeItemU2 = "minecraft:golden_apple";

    @Config.Comment("U2 required metadata (1 = enchanted golden apple). -1 = any meta. Read live.")
    @Config.Name("Upgrade U2 Meta")
    @Config.RangeInt(min = -1, max = 32767)
    public int u2Meta = 1;

    @Config.Comment("U2 required stack count in the slot before it counts as active. Read live.")
    @Config.Name("Upgrade U2 Count")
    @Config.RangeInt(min = 1, max = 64)
    public int u2Count = 20;

    @Config.Comment("U3 (+radius) required item. Adds Upgrade Radius Bonus. Read live.")
    @Config.Name("Upgrade U3 Item")
    public String upgradeItemU3 = "srparasites:trophy_void_orb";

    @Config.Comment({"U4 (ascension) required item. Also needs U1+U2+U3 active. Cancels ALL penalties,",
            "removes fuel upkeep entirely, and adds Upgrade Radius Bonus. Read live."})
    @Config.Name("Upgrade U4 Item")
    public String upgradeItemU4 = "srparasites:trophy_boom_orb";
}
