package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.fml.common.Loader;

/**
 * Cross-mod interaction toggles that depend on a specific optional mod being present.
 * Each master switch here gates a family of behaviors tied to one dependency.
 */
public class InteractionsCategory {

    @Config.Comment({
            "Master switch for all Enigmatic Legacy interactions.",
            "When ON, the mod integrates with Enigmatic Legacy in two ways:",
            "  - the Cursed Ring minion fix (see 'Enable Cursed Ring Minion Fix' in tweaks), and",
            "  - the Blessed Ring requirement that unlocks Bauble Fruit acquisition",
            "    (Corrupted Seed Fragment drops and Corrupted Sapling growth).",
            "When OFF, none of the above apply, even if Enigmatic Legacy is installed.",
            "Default is auto-detected: ON when Enigmatic Legacy is present, OFF otherwise." })
    @Config.Name("Enable Enigmatic Legacy Interactions")
    public boolean enableEnigmaticLegacyInteractions = Loader.isModLoaded("enigmaticlegacy");

    @Config.Comment({
            "When InfernalMobs is installed: every infernal (elite) mob killed by a player",
            "drops spectral dust of a random element (Electroblob's Wizardry).",
            "Kills without player credit (environment, other mobs, automated farms) drop nothing." })
    @Config.Name("Enable Infernal Spectral Dust Drops")
    @Config.RequiresMcRestart
    public boolean enableInfernalDustDrops = true;

    @Config.Comment("Minimum spectral dust dropped per infernal kill.")
    @Config.Name("Infernal Dust: Min")
    @Config.RangeInt(min = 0, max = 16)
    public int infernalDustMin = 1;

    @Config.Comment("Maximum spectral dust dropped per infernal kill.")
    @Config.Name("Infernal Dust: Max")
    @Config.RangeInt(min = 1, max = 16)
    public int infernalDustMax = 2;

    @Config.Comment({
            "Second-opinion check for NPC spell casts vetoed via SpellCastEvent.Pre.",
            "Some mods (notably Ars Magica 2's EB Wizardry compat) blanket-cancel NPC casts",
            "for reasons that cannot apply to this mod's casters (AM2 burnout/mana). When ON,",
            "a vetoed cast by the Sim Wizard or Sentinel is re-checked against the KNOWN",
            "legitimate veto conditions (EB per-spell NPC disable, EB arcane jammer, ASC",
            "suppression charm, ASC dimensional anchor, MorphSpellPack lich spells, AM2",
            "silence); if none applies, the cast proceeds.",
            "AUTO default: enabled only when Ars Magica 2 is installed." })
    @Config.Name("NPC Cast Veto Second Opinion")
    public NpcVetoSecondOpinion npcCastVetoSecondOpinion = NpcVetoSecondOpinion.AUTO;

    public enum NpcVetoSecondOpinion { AUTO, ON, OFF }
}
