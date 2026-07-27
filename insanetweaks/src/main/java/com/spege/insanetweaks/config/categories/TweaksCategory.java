package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class TweaksCategory {

    @Config.Comment({
            "Prevents the Enigmatic Legacy 'Cursed Ring' from forcing summoned creatures (e.g. Fer Cow Minion) to attack their own caster.",
            "When enabled, all Electroblob's Wizardry summoned creatures correctly report as being 'on the same team' as their owner,",
            "which makes the Cursed Ring's anger loop skip them entirely.",
            "Safe to leave enabled even if Enigmatic Legacy is not installed — the fix costs nothing when the ring is absent.",
            "This is a sub-toggle of 'Enable Enigmatic Legacy Interactions' (interactions category): if that master switch is OFF, this fix is off regardless." })
    @Config.Name("Enable Cursed Ring Minion Fix")
    public boolean enableCursedRingFix = true;

    @Config.Comment({
            "Additional effects removed by the CLEANSE effect, on top of (a) all effects where",
            "isBeneficial() == false and (b) the built-in parasite-effect list shipped in the mod",
            "(all harmful SRParasites/SRPExtra effects are already covered by the built-in list).",
            "Add effect IDs here only if some other mod's negative effect is not removed automatically.",
            "Example: minecraft:glowing" })
    @Config.Name("Cleanse Effect List")
    public String[] cleanseAdditionalEffects = {};

    @Config.Comment({"Master toggle for the Zhonya's Hourglass artefact (the player-stasis one).",
            "When false (default), the artefact is inert: right-click does nothing and the",
            "tooltip shows a 'disabled' line. (It is also flagged disabled to EB Wizardry's",
            "artefact API as future-proofing.)",
            "The item stays registered, so existing copies in worlds are unaffected.",
            "The Restoration Hourglass is NOT affected by this switch."})
    @Config.Name("Enable Zhonya's Hourglass")
    @Config.RequiresMcRestart
    public boolean enableZhonya = false;

    @Config.Comment({"Zhonya's Hourglass: cooldown after activation, in ticks.",
            "Default 216000 = 3 hours."})
    @Config.Name("Zhonya Cooldown Ticks")
    @Config.RangeInt(min = 0)
    public int zhonyaCooldownTicks = 216000;

    @Config.Comment({"Zhonya's Hourglass: Gilded Stasis duration in ticks (default 60 = 3 s)."})
    @Config.Name("Zhonya Stasis Duration Ticks")
    @Config.RangeInt(min = 1)
    public int zhonyaStasisTicks = 60;

    @Config.Comment({"Zhonya's Hourglass: aggro-loss window in ticks (default 100 = 5 s).",
            "During this window all mobs targeting the user are de-aggroed every tick."})
    @Config.Name("Zhonya Aggro Loss Ticks")
    @Config.RangeInt(min = 0)
    public int zhonyaAggroLossTicks = 100;

    @Config.Comment({"Zhonya's Hourglass: minimum CURRENT mana required to activate.",
            "Activation always drains ALL current mana."})
    @Config.Name("Zhonya Minimum Mana")
    @Config.RangeInt(min = 0)
    public int zhonyaMinMana = 100;

    @Config.Comment({"Zhonya's Hourglass: when player_mana is NOT installed, fall back to a built-in",
            "Electroblob's Wizardry mana pool stored on the item itself. Activation then requires",
            "the item to be fully charged and drains it to zero (recharge with a Mana Flask while",
            "the artefact is held). The long cooldown still applies on top. When OFF and player_mana",
            "is absent, the artefact is inert."})
    @Config.Name("Zhonya EB-Mana Fallback")
    public boolean zhonyaEbManaFallback = true;

    @Config.Comment({"Zhonya's Hourglass: capacity of the built-in EB mana pool used by the fallback",
            "(default 3000). A full charge is required to activate and is fully consumed."})
    @Config.Name("Zhonya EB-Mana Capacity")
    @Config.RangeInt(min = 1)
    public int zhonyaEbManaCapacity = 3000;

    // The Bauble Fruit acquisition loop (fragment drops, sapling, corrupted fruit) moved to the
    // dedicated 'baubleFruits' category in 1.4.18 — see BaubleFruitsCategory.
}
