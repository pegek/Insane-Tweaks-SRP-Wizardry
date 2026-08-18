package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class PoolCategory {

    @Config.Comment("Bazowa maksymalna mana gracza, zanim doliczymy jakiekolwiek modyfikatory.")
    @Config.RangeDouble(min = 1.0D, max = 1.0E6D)
    @Config.RequiresMcRestart
    public double baseMaxMana = 100.0D;

    @Config.Comment("Twardy sufit maksymalnej many po zsumowaniu wszystkich zrodel.")
    @Config.RangeDouble(min = 1.0D, max = 1.0E7D)
    public double hardCap = 2000.0D;

    @Config.Comment("Ile many doliczamy do trwalej progresji za jeden udany czar. Celowo symboliczne.")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double progressionPerCast = 0.05D;

    @Config.Comment("Sufit trwalej progresji z samego rzucania czarow.")
    @Config.RangeDouble(min = 0.0D, max = 1.0E6D)
    public double progressionCap = 50.0D;

    @Config.Comment("Czy `current` ma sie zerowac przy smierci. `progressionBonus` przezywa zawsze.")
    public boolean resetCurrentOnDeath = true;
}
