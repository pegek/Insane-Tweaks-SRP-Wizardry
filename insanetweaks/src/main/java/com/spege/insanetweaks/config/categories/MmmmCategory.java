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
            "Maximum level of Mmmm. Deliberately 1: one tier is all there is, and 'Power Per Level'",
            "below makes that single tier as strong as the original Ambrosia's level II, so there is",
            "no weaker step to find first. Read at registration - requires a MC restart."
    })
    @Config.Name("Max Level")
    @Config.RangeInt(min = 1, max = 10)
    @Config.RequiresMcRestart
    public int maxLevel = 1;

    @Config.Comment({
            "How many levels of the original Ambrosia each level here is worth. The duration formula",
            "and the Nourished amplifier both use (enchantment level * this) in place of the level,",
            "so the default 2 makes our level I behave exactly like upstream Ambrosia II.",
            "Read live."
    })
    @Config.Name("Power Per Level")
    @Config.RangeInt(min = 1, max = 10)
    public int powerPerLevel = 2;

    @Config.Comment({
            "Flat duration of the Nourished effect in ticks, before the XP-scaled part is added.",
            "600 ticks = 30 seconds, the upstream default. Read live."
    })
    @Config.Name("Base Duration Ticks")
    @Config.RangeInt(min = 0, max = 72000)
    public int baseDurationTicks = 600;

    @Config.Comment({
            "Scales the XP-driven part of the duration: the effect lasts",
            "  base + (1 + xpLevel * power) * ln(5) * this   ticks,",
            "so each multiplier point is worth about 1.6 ticks per (xpLevel * power). At the",
            "defaults a level-30 player gets roughly 4500 ticks (~3.75 minutes). Read live."
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
}
