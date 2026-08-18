package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class RegenCategory {

    @Config.Comment("Ile many regeneruje sie w jednym cyklu.")
    @Config.RangeDouble(min = 0.0D, max = 1.0E5D)
    public double amountPerCycle = 1.0D;

    @Config.Comment("Dlugosc cyklu regeneracji w sekundach.")
    @Config.RangeDouble(min = 0.05D, max = 600.0D)
    public double cycleSeconds = 1.0D;
}
