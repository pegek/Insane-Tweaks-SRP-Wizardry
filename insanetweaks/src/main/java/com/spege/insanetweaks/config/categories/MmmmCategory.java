package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the Mmmm enchantment (native port of UniqueEnchantments' Ambrosia): eating enchanted
 * food fills the hunger bar and grants the Nourished effect for an XP-scaled duration.
 *
 * <p>Accessed as {@code ModConfig.enchantments.mmmm.*}. The master toggle is
 * {@code modules.enableMmmm}.
 */
public class MmmmCategory {

    @Config.Comment({
            "Maximum level of Mmmm. The TOP tier is always the full-strength one - it is worth",
            "(this * 'Power Per Level') levels of the original Ambrosia, so the defaults 2 and 1 make",
            "our level II behave exactly like upstream Ambrosia II. Every tier below the top is",
            "weakened by 'Lower Tier Strength'. Read at registration - requires a MC restart."
    })
    @Config.Name("Max Level")
    @Config.RangeInt(min = 1, max = 10)
    @Config.RequiresMcRestart
    public int maxLevel = 2;

    @Config.Comment({
            "How many levels of the original Ambrosia one level here is worth. It feeds two things,",
            "and NOT in the same way:",
            "  - the Nourished amplifier is (enchantment level * this), so it does step down per tier;",
            "  - the XP-scaled duration curve is evaluated once at the TOP tier's power",
            "    ('Max Level' * this) and is therefore the same shape at every level - what separates",
            "    the tiers there is 'Lower Tier Strength' alone.",
            "That split is deliberate: it stops the two nerfs compounding, so a lower tier is exactly",
            "as much shorter as 'Lower Tier Strength' says. Read live."
    })
    @Config.Name("Power Per Level")
    @Config.RangeInt(min = 1, max = 10)
    public int powerPerLevel = 1;

    @Config.Comment({
            "How strong a tier is relative to the one above it, as a multiplier on the Nourished",
            "duration and on the instant hunger refill. The top tier is always 1.0; each step down",
            "multiplies by this again, so at the default 2 tiers level II is full strength and level I",
            "is 0.65 - about 35% weaker. (The Nourished amplifier is not scaled by this: it is an",
            "integer and steps down on its own via 'Power Per Level'.) Read live."
    })
    @Config.Name("Lower Tier Strength")
    @Config.RangeDouble(min = 0.05, max = 1.0)
    public double lowerTierStrength = 0.65;

    @Config.Comment({
            "Flat duration of the Nourished effect in ticks, before the XP-scaled part is added.",
            "600 ticks = 30 seconds, the upstream default. Read live."
    })
    @Config.Name("Base Duration Ticks")
    @Config.RangeInt(min = 0, max = 72000)
    public int baseDurationTicks = 600;

    @Config.Comment({
            "Scales the XP-driven part of the duration: the effect lasts",
            "  (base + (1 + xpLevel * topPower) * ln(5) * this) * tierStrength   ticks,",
            "where topPower is 'Max Level' * 'Power Per Level'. Each multiplier point is worth about",
            "1.6 ticks per (xpLevel * topPower). At the defaults a level-30 player gets roughly 4500",
            "ticks (~3.75 minutes) from the top tier and 2950 from the one below. Read live."
    })
    @Config.Name("Duration Multiplier")
    @Config.RangeInt(min = 0, max = 1000)
    public int durationMultiplier = 40;

    @Config.Comment({
            "Hard cap on the Nourished duration in ticks, or 0 for no cap. The formula above is",
            "linear in XP level and unbounded - the original has no cap either - so a very",
            "high-level player can end up sated for the better part of an hour. Set e.g. 24000",
            "(20 minutes) to bound it. Read live."
    })
    @Config.Name("Max Duration Ticks")
    @Config.RangeInt(min = 0, max = 432000)
    public int maxDurationTicks = 0;

    @Config.Comment({
            "Fill the hunger bar completely the moment the enchanted food is eaten, on top of what",
            "the food itself restores. This is upstream behaviour; turn it off to leave the food's",
            "own healing alone and let Nourished do all the work. Read live."
    })
    @Config.Name("Fill Hunger Bar")
    public boolean fillHungerBar = true;

    @Config.Comment({
            "Make Mmmm-enchanted food immune to Scape and Run: Parasites' food contamination - the",
            "hit that eats part of a food stack and drops Infected Flesh on the ground in its place.",
            "Enchanted stacks become invisible to that scan, so the parasite simply moves on to the",
            "next food stack in the inventory; it does not stop the contamination outright. Requires",
            "SRParasites, and does nothing while modules.enableMmmm is off. Read live."
    })
    @Config.Name("Protect Food From Parasite Contamination")
    public boolean protectFromParasiteContamination = true;

    @Config.Comment({
            "Protect the enchantment's CARRIER - the food stack it sits on - from being swapped for",
            "one of the 'Forbidden Carriers' below by any other mod interaction. This is the general",
            "form of 'Protect Food From Parasite Contamination' above, which only covers the one SRP",
            "vector we could name; this one covers whatever else turns your food into rot.",
            "Does nothing while modules.enableMmmm is off. Read live."
    })
    @Config.Name("Protect Carrier From Swap")
    public boolean protectCarrierFromSwap = true;

    @Config.Comment({
            "Items Mmmm refuses to be carried by, as registry names. A stack of one of these that is",
            "found carrying Mmmm gets turned back into the food it came from when we know it, and",
            "otherwise has the enchantment stripped off it - either way the enchantment never ends up",
            "owned by a rot item. These items also stop accepting Mmmm on an anvil.",
            "Format: modid:path, or modid:path#meta to pin a single metadata value.",
            "Add e.g. srparasites:infected_drop here to cover SRP's Infected Flesh too.",
            "Empty = the guard is inert. Read live."
    })
    @Config.Name("Forbidden Carriers")
    public String[] forbiddenCarriers = { "minecraft:rotten_flesh" };

    @Config.Comment({
            "Log one INFO line the first time the carrier guard catches each item at each route.",
            "Diagnostic: use it to find out WHICH interaction is trying to rot your enchanted food,",
            "so it can be fixed at the source. Deduplicated, so it will not flood the log. Read live."
    })
    @Config.Name("Log Carrier Guard")
    public boolean logCarrierGuard = false;
}
