package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class PoolCategory {

    @Config.Comment({
            "Bazowa maksymalna mana gracza, zanim doliczymy jakiekolwiek modyfikatory.",
            "Dziala od nastepnego wejscia gracza do swiata (login/respawn/zmiana wymiaru) - NIE natychmiast",
            "w locie i NIE wymaga restartu serwera. Sprzezone z `hardCap`: ustawienie bazy wyzej niz",
            "sufit bedzie oznaczac, ze pula od razu jest przycinana do `hardCap`."
    })
    @Config.RangeDouble(min = 1.0D, max = 1.0E6D)
    public double baseMaxMana = 100.0D;

    @Config.Comment({
            "Twardy sufit maksymalnej many po zsumowaniu wszystkich zrodel.",
            "NIEAKTYWNE: nic w kodzie jeszcze tego nie czyta - podpiecie w pozniejszym zadaniu.",
            "Docelowo dziala na zywo, bez restartu."
    })
    @Config.RangeDouble(min = 1.0D, max = 1.0E7D)
    public double hardCap = 2000.0D;

    @Config.Comment("Ile many doliczamy do trwalej progresji za jeden udany czar. Celowo symboliczne. Dziala na zywo, bez restartu.")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double progressionPerCast = 0.05D;

    @Config.Comment("Sufit trwalej progresji z samego rzucania czarow. Dziala na zywo, bez restartu.")
    @Config.RangeDouble(min = 0.0D, max = 1.0E6D)
    public double progressionCap = 50.0D;

    @Config.Comment("Czy `current` ma sie zerowac przy smierci. Trwala progresja zawsze przezywa smierc. Dziala na zywo, bez restartu.")
    public boolean resetCurrentOnDeath = true;
}
