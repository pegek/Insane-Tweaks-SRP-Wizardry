package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class TweaksCategory {

    @Config.Comment({
            "Prevents the Enigmatic Legacy 'Cursed Ring' from forcing summoned creatures (e.g. Fer Cow Minion) to attack their own caster.",
            "When enabled, all Electroblob's Wizardry summoned creatures correctly report as being 'on the same team' as their owner,",
            "which makes the Cursed Ring's anger loop skip them entirely.",
            "Safe to leave enabled even if Enigmatic Legacy is not installed — the fix costs nothing when the ring is absent." })
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
}
