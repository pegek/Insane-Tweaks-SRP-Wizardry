package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class PoolCategory {

    @Config.Comment({
            "Base maximum mana for a player, before any modifiers are added.",
            "Takes effect the next time the player enters the world (login/respawn/dimension change) - NOT",
            "instantly and NOT requiring a server restart. Coupled with `hardCap`: setting the base higher",
            "than the cap will mean the pool is immediately clamped down to `hardCap`."
    })
    @Config.RangeDouble(min = 1.0D, max = 1.0E6D)
    public double baseMaxMana = 100.0D;

    @Config.Comment({
            "Hard cap on maximum mana after summing all sources.",
            "INACTIVE: nothing in the code reads this yet - wiring it up is a later task.",
            "Intended to work live, without a restart."
    })
    @Config.RangeDouble(min = 1.0D, max = 1.0E7D)
    public double hardCap = 2000.0D;

    @Config.Comment("How much mana is added to permanent progression per successful spell cast. Deliberately symbolic. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double progressionPerCast = 0.05D;

    @Config.Comment("Cap on permanent progression from spellcasting alone. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 1.0E6D)
    public double progressionCap = 50.0D;

    @Config.Comment("Whether `current` resets on death. Permanent progression always survives death. Works live, no restart.")
    public boolean resetCurrentOnDeath = true;
}
