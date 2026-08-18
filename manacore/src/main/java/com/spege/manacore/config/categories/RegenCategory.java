package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class RegenCategory {

    @Config.Comment({
            "How much mana regenerates in one cycle.",
            "Paired with `cycleSeconds` purely for ease of tuning (\"5 mana every 2 seconds\" is easier",
            "to reason about than \"0.125 mana per tick\") - in practice regeneration is CONTINUOUS,",
            "spread evenly over every tick, not paid out in visible chunks once per cycle. Works live, no restart."
    })
    @Config.RangeDouble(min = 0.0D, max = 1.0E5D)
    public double amountPerCycle = 1.0D;

    @Config.Comment({
            "Length of the regen cycle in seconds - see the comment on `amountPerCycle`:",
            "used solely to convert into mana-per-tick, regeneration does not jump once per this interval.",
            "Works live, no restart."
    })
    @Config.RangeDouble(min = 0.05D, max = 600.0D)
    public double cycleSeconds = 1.0D;
}
