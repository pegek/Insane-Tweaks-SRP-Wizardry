package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class TabCategory {

    @Config.Comment({
            "Whether to take over the Trinkets and Baubles mana pool.",
            "This does NOT switch off the TaB mixin - it applies whenever TaB is installed, because",
            "a config value cannot gate mixin application. The flag controls only whether ManaCore",
            "registers its own event handlers, which is why it needs a restart."})
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment("How many units of TaB mana correspond to one of ours; 0 means 1:1. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 1000.0D)
    public double unitScale = 1.0D;

    @Config.Comment({
            "How much permanent maximum eating a Mana Crystal adds. Works live, no restart.",
            "Capped by `pool.itemProgressionCap`, not by anything in this category - that is where",
            "the ceiling on permanent maximum from consumed items lives, shared across every source."
    })
    @Config.RangeDouble(min = 0.0D, max = 10000.0D)
    public double manaCrystalMaxBonus = 5.0D;

    @Config.Comment("How much current mana a Mana Reagent or Mana Candy restores. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 10000.0D)
    public double restoreItemAmount = 25.0D;
}
