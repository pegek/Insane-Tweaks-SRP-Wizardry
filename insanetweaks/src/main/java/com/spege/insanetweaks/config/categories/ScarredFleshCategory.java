package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the Scarred Flesh trait ({@code compatskills:scarred_flesh}).
 *
 * <p>The trait gets <b>stronger the more parasite afflictions are already stacked on you</b>.
 * "Slot" below means the position of the incoming effect once it lands — an incoming effect
 * that would be your third simultaneous parasite debuff occupies slot 3.
 *
 * <p>Default ladder:
 * <pre>
 *   slot 1-2  no interference
 *   slot 3    amplifier capped at V,   full duration
 *   slot 4    amplifier capped at IV,  85% duration
 *   slot 5    amplifier capped at III, 75% duration
 *   slot 6    amplifier capped at II,  65% duration
 *   slot 7    amplifier capped at I,   55% duration
 *   slot 8+   refused outright
 * </pre>
 *
 * All values are read live inside the handler — no restart needed.
 */
public class ScarredFleshCategory {

    @Config.Name("Free Debuff Slots")
    @Config.Comment("Number of simultaneous parasite afflictions that pass through completely untouched.")
    @Config.RangeInt(min = 0, max = 20)
    public int freeDebuffs = 2;

    @Config.Name("Max Debuff Slots")
    @Config.Comment("Hard ceiling on simultaneous parasite afflictions. Anything beyond this is refused outright.")
    @Config.RangeInt(min = 1, max = 30)
    public int maxDebuffs = 7;

    @Config.Name("Amplifier Caps")
    @Config.Comment({
            "Maximum amplifier allowed at each slot past the free ones, in order.",
            "These are raw amplifiers: 4 displays as level V, 0 as level I.",
            "First entry applies to slot (Free Debuff Slots + 1). If the ladder is shorter than",
            "the slot range, the last entry is reused for every remaining slot." })
    public int[] amplifierCaps = { 4, 3, 2, 1, 0 };

    @Config.Name("Duration Multipliers")
    @Config.Comment({
            "Duration scaling at each slot past the free ones, in order, matching Amplifier Caps.",
            "1.0 keeps full duration, 0.85 cuts 15%. Shorter lists reuse the last entry." })
    public double[] durationMultipliers = { 1.0D, 0.85D, 0.75D, 0.65D, 0.55D };

    @Config.Name("Additional Hostile Effects")
    @Config.Comment({
            "Extra effect ids treated as parasite afflictions, format modid:effect.",
            "The built-in list already covers SRParasites 1.10.7 and SRPExtra 1.10.7.5.",
            "Do NOT add host buffs here (srparasites:the_sign, parate, muscleout) — the trait",
            "would then block effects that help you." })
    public String[] additionalHostileEffects = {};

    @Config.Name("Debug Logging")
    @Config.Comment("Log every capped, shortened or refused affliction to the server log.")
    public boolean debugLogging = false;
}
