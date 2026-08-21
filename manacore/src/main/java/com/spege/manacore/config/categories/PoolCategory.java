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
            "Hard cap on maximum mana after summing all sources. Works live, no restart.",
            "A ceiling on the TOTAL, which is what distinguishes it from the per-source ceilings",
            "(`castProgressionCap`, `itemProgressionCap`, `advancements.cap`): those limit one budget",
            "each, this limits their sum plus the base and anything worn.",
            "Applied when the maximum is read rather than baked into the attribute, so raising this",
            "later restores a trimmed maximum instead of having permanently discarded it - progression",
            "earned while capped is not lost, merely not visible."
    })
    @Config.RangeDouble(min = 1.0D, max = 1.0E7D)
    public double hardCap = 2000.0D;

    @Config.Comment("How much mana is added to permanent progression per successful spell cast. Deliberately symbolic. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double progressionPerCast = 0.05D;

    @Config.Comment("Cap on permanent progression from spellcasting alone. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 1.0E6D)
    public double castProgressionCap = 50.0D;

    @Config.Comment({
            "Cap on permanent progression from consumed items alone (Trinkets and Baubles' Mana Crystal",
            "and anything else that grants permanent maximum through ManaAPI.addItemProgression), no",
            "matter which mod supplies them. This is a real ceiling on that budget - unlike",
            "`castProgressionCap`, which is a separate ceiling on the casting budget, the two never add",
            "past each other because each source has its own field. Works live, no restart."
    })
    @Config.RangeDouble(min = 0.0D, max = 1.0E6D)
    public double itemProgressionCap = 100.0D;

    @Config.Comment({
            "Whether death touches the player's CURRENT mana at all. Permanent maximum always",
            "survives death regardless of this - progression, granted maximum, the config base.",
            "Off means the player keeps exactly the mana they died with. Works live, no restart."
    })
    public boolean resetCurrentOnDeath = true;

    @Config.Comment({
            "What fraction of maximum mana the player respawns with, when `resetCurrentOnDeath` is on.",
            "0.0 respawns them empty, 1.0 respawns them full; the default half is a cost for dying",
            "without leaving them unable to cast their way out of wherever they respawned.",
            "Measured against the PERSISTENT maximum only - worn gear is not counted, because bonus",
            "maximum raises the ceiling rather than handing out mana, and gear is not on the player",
            "at the moment this is computed anyway. Works live, no restart."
    })
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public double manaFractionOnDeath = 0.5D;
}
