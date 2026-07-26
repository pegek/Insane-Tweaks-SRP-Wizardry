package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the Swift Picking enchantment, which shortens the Auto Lock Picker's channel time.
 * The per-level reduction itself lives with the rest of the item's numbers in
 * {@code ModConfig.autoLockPicker.swiftReductionPerLevel} — only the registration-time values are
 * here.
 *
 * <p>Accessed as {@code ModConfig.enchantments.swiftPicking.*}.
 */
public class SwiftPickingCategory {

    @Config.Comment({
            "Maximum level of Swift Picking. At the default 3 and the default -15%/level, level III",
            "channels at 55% of the base time. Read at registration - requires a MC restart."
    })
    @Config.Name("Max Level")
    @Config.RangeInt(min = 1, max = 10)
    @Config.RequiresMcRestart
    public int maxLevel = 3;

    @Config.Comment({
            "Enchantability cost of level I. Each further level adds 'Enchantability Per Level'.",
            "Read at registration - requires a MC restart."
    })
    @Config.Name("Min Enchantability")
    @Config.RangeInt(min = 1, max = 200)
    @Config.RequiresMcRestart
    public int minEnchantability = 5;

    @Config.Comment({
            "Enchantability added per level above I. Read at registration - requires a MC restart."
    })
    @Config.Name("Enchantability Per Level")
    @Config.RangeInt(min = 0, max = 100)
    @Config.RequiresMcRestart
    public int enchantabilityPerLevel = 12;

    @Config.Comment({
            "Width of each level's enchantability window (max = min + this). Read at registration."
    })
    @Config.Name("Enchantability Window")
    @Config.RangeInt(min = 1, max = 200)
    @Config.RequiresMcRestart
    public int enchantabilityWindow = 25;
}
