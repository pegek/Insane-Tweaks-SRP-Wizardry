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
            "Whether the dragonsteel nunchaku carry RLDragonsteel's on-hit effects: setting the target",
            "alight, freezing it, or arcing lightning off it, depending on the weapon.",
            "Turning this OFF changes nothing else about the weapons - they still exist, still spin,",
            "still build combo and keep every one of their stats. They just land as plain hits.",
            "Does nothing while RLCombat is installed: under RLCombat no dragonsteel weapon fires its",
            "effects here, they all go through that mod's own damage modifier instead.",
            "Read live - no restart needed. Default ON." })
    @Config.Name("Enable Dragonsteel Nunchaku Hit Effects")
    public boolean enableDragonsteelHitEffects = true;

    @Config.Comment({
            "Whether a Sentient Nunchaku calls parasites to its wielder. This mirrors what SRParasites",
            "does with its own Sentient weapons: while the infestation is developed enough, holding one",
            "periodically inflicts Prey on you. The Living tier never does this - the price arrives",
            "together with the power.",
            "Turning this off keeps the weapon's strength and drops its drawback, so leave it on unless",
            "you are deliberately making the line easier.",
            "Read live - no restart needed. Default ON." })
    @Config.Name("Enable Parasite Nunchaku Prey")
    public boolean enableParasiteNunchakuPrey = true;

    @Config.Comment({
            "Whether a parasite nunchaku may force an effect past a parasite's immunity.",
            "SRParasites makes every parasite immune to exactly four effects: Call of the Hive,",
            "Viral, Corrosion and Needler.",
            "THIS IS CURRENTLY DORMANT. The nunchaku inflicts Bleeding and Indeaf, and neither of",
            "those is on that list, so the bypass has nothing to do and defaults to OFF. It is kept",
            "wired up because Viral and Needler were the original design and may come back.",
            "If you do switch the weapon back to a blocked effect, turn this on. The bypass is",
            "deliberately narrow: it opens only while THIS weapon is applying an effect, so parasites",
            "still cannot infect each other with their own area attacks - which is almost certainly",
            "why that immunity exists in the first place.",
            "Read live - no restart needed. Default OFF." })
    @Config.Name("Enable Parasite Nunchaku Immunity Bypass")
    public boolean enableParasiteNunchakuImmunityBypass = false;

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
