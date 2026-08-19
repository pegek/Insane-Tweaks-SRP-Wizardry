package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class EbwCategory {

    @Config.Comment({
            "Whether to hook Electroblob's Wizardry spell cost into the player's mana pool.",
            "This does NOT switch off the EBW mixins - those apply whenever EBW is installed,",
            "because a config value cannot gate mixin application. The flag controls only whether",
            "ManaCore registers its own event handlers, which is why it needs a restart."})
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment("Global multiplier on spell cost, applied on top of EBW's own modifiers. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double costMultiplier = 1.0D;

    @Config.Comment("Whether to respect COST attributes from the wizardryutils mod, when present. Works live, no restart.")
    public boolean useWizardryUtilsAttributes = true;

    @Config.Comment({
            "Whether a higher-tier wand refunds part of a spell's cost. Works live, no restart.",
            "OFF by default: in play the refund turned out far too strong, and the mechanic is",
            "under review - the current thinking is to replace it with a fatigue system, where a",
            "better wand softens the penalty for casting rapidly instead of handing mana back.",
            "The `refund*` settings below do nothing while this is off."})
    public boolean refundEnabled = false;

    @Config.Comment({
            "Wand capacity below which the `storage` upgrade grants no refund at all. Works live, no restart.",
            "Setting this above any capacity a wand can actually reach disables the refund entirely,",
            "silently - nothing in game says why it stopped working. Real EBW wand capacities are in",
            "the hundreds, so the upper end of this range is far past anything useful."})
    @Config.RangeInt(min = 0, max = 100000)
    public int refundBaselineCapacity = 100;

    @Config.Comment("Every this many points of capacity surplus, `refundFractionPerStep` is credited. Works live, no restart.")
    @Config.RangeInt(min = 1, max = 100000)
    public int refundCapacityStep = 100;

    @Config.Comment("Fraction of cost refunded per step of wand capacity surplus. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public double refundFractionPerStep = 0.05D;

    @Config.Comment("How much mana per second one level of the `condenser` upgrade grants. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 1000.0D)
    public double condenserRegenPerLevel = 0.5D;

    @Config.Comment("How much mana one level of the `siphon` upgrade grants per kill. Works live, no restart.")
    @Config.RangeDouble(min = 0.0D, max = 10000.0D)
    public double siphonManaPerLevel = 5.0D;
}
