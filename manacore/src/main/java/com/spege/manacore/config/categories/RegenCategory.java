package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class RegenCategory {

    @Config.Comment({
            "Ile many regeneruje sie w jednym cyklu.",
            "Para z `cycleSeconds` sluzy tylko do strojenia (\"5 many co 2 sekundy\" latwiej ogarnac",
            "niz \"0.125 many na tick\") - w praktyce regeneracja jest CIAGLA, rozlozona rownomiernie",
            "na kazdy tick, a nie wyplacana w widocznych porcjach co cykl. Dziala na zywo, bez restartu."
    })
    @Config.RangeDouble(min = 0.0D, max = 1.0E5D)
    public double amountPerCycle = 1.0D;

    @Config.Comment({
            "Dlugosc cyklu regeneracji w sekundach - patrz komentarz przy `amountPerCycle`:",
            "uzywana wylacznie do przeliczenia many-na-tick, regeneracja nie skacze co ten czas.",
            "Dziala na zywo, bez restartu."
    })
    @Config.RangeDouble(min = 0.05D, max = 600.0D)
    public double cycleSeconds = 1.0D;
}
