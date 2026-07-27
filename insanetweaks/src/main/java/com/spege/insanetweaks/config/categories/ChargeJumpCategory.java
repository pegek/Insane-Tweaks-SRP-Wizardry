package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the Coiled Spring trait ({@code compatskills:coiled_spring}).
 * All values are read live inside the handlers — no restart needed.
 */
public class ChargeJumpCategory {

    @Config.Name("Max Charge Ticks")
    @Config.Comment("Ticks of held Sneak + Jump needed for a full-power leap. 20 ticks = 1 second.")
    @Config.RangeInt(min = 5, max = 200)
    public int maxChargeTicks = 30;

    @Config.Name("Max Jump Multiplier")
    @Config.Comment({
            "Upward velocity multiplier at full charge. Vanilla jump apex is 1.25 blocks.",
            "3.2 reaches roughly 10 blocks; 2.5 -> ~6.5 blocks; 2.0 -> ~4.3 blocks.",
            "Apex scales close to the square of this value, so small changes matter a lot." })
    @Config.RangeDouble(min = 1.0D, max = 6.0D)
    public double maxJumpMultiplier = 3.2D;

    @Config.Name("Minimum Charge To Launch")
    @Config.Comment({
            "Charge fraction below which the leap is skipped entirely.",
            "Keep this at 0. While Sneak + Jump are held the normal jump is suppressed, so a",
            "non-zero threshold makes quick crouch-jumps (bridging, ledge work) silently fail",
            "instead of jumping. At 0 a brief tap just produces a near-vanilla hop." })
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public double minChargeToLaunch = 0.0D;

    @Config.Name("Fall Damage Reduction")
    @Config.Comment({
            "Fraction of fall distance waived after a leap, scaled by the charge actually spent.",
            "1.0 means a full-charge leap takes no fall damage at all. 0.0 disables the waiver." })
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public double fallDamageReduction = 1.0D;

    @Config.Name("Fall Protection Window")
    @Config.Comment("Ticks after launch during which the fall-damage waiver stays armed. A 10-block leap lands in roughly 60 ticks.")
    @Config.RangeInt(min = 20, max = 400)
    public int fallProtectTicks = 120;

    @Config.Name("Show Charge Bar")
    @Config.Comment("Draw the charge meter above the hotbar while coiling.")
    public boolean showChargeBar = true;
}
