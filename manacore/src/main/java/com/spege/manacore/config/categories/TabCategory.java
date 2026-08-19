package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class TabCategory {

    @Config.Comment("Whether to take over the Trinkets and Baubles mana pool.")
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment("How many units of TaB mana correspond to one of ours; 0 or less means 1:1. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 1000.0D)
    public double unitScale = 1.0D;

    @Config.Comment("How much permanent maximum eating a Mana Crystal adds. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 10000.0D)
    public double manaCrystalMaxBonus = 5.0D;

    @Config.Comment({
            "Cap on permanent maximum from Mana Crystals alone. Works live, no restart.",
            "WARNING: this cap is INDEPENDENT of `pool.progressionCap` - both add to the same permanent",
            "progression field, so neither one is a hard cap on the total. This is a known design debt,",
            "to be resolved in a later task; this comment is deliberately honest about what the field",
            "actually does rather than promising an overall ceiling it does not enforce."
    })
    @Config.RangeDouble(min = 0.0D, max = 1.0E6D)
    public double manaCrystalCap = 100.0D;

    @Config.Comment("How much current mana a Mana Reagent or Mana Candy restores. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 10000.0D)
    public double restoreItemAmount = 25.0D;
}
