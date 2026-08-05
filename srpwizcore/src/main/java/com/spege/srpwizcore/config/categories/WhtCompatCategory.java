package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * WorseHurtTimer (modid {@code betterhurttimer}) invincibility-frame compatibility.
 *
 * <p>WHT replaces vanilla i-frames wholesale: its own mixin overwrites
 * {@code EntityLivingBase.hurtResistantTime} with a flat config value on every hit, so anything
 * that lengthens invincibility the vanilla way is dead. This module gives the pack one place to
 * ask for longer i-frames that works on every WHT path, and makes the base values WHT computes
 * deterministic.
 *
 * <p>The mixins live in {@code mixins.srpwizcore.betterhurttimer.json}, which
 * {@code SrpWizCoreLateBooter} only queues when {@code betterhurttimer} is present. Each mixin
 * additionally self-gates on {@link #enabled}, so switching it off makes WHT behave exactly as
 * unmodified WHT does.
 */
public class WhtCompatCategory {

    @Config.Comment({
            "Master switch for the WorseHurtTimer invincibility-frame layer.",
            "OFF leaves WorseHurtTimer working exactly as it does without this mod.",
            "Does nothing unless WorseHurtTimer is installed. No restart needed. Default ON."
    })
    @Config.Name("Enabled")
    public boolean enabled = true;

    @Config.Comment({
            "Invincibility frames, in ticks, used as the starting point for two things:",
            " - a player being hit in melee by an attacker holding no attack-speed weapon,",
            " - any damage source that is NOT listed in betterhurttimer.cfg's damageSource table.",
            "20 reproduces WorseHurtTimer's own numbers, so leaving it alone changes nothing.",
            "The second case is a bug fix: unmodified WorseHurtTimer takes that value from",
            "whichever entity that damage source happened to hit first in the session, and then",
            "reuses it globally for everyone.",
            "Raising this makes EVERY player tougher, before any multiplier. 20 ticks = 1 second."
    })
    @Config.Name("Base I-Frame Ticks")
    @Config.RangeInt(min = 1, max = 200)
    public int baseIFrameTicks = 20;

    @Config.Comment({
            "Ceiling on the combined multiplier when a player carries several sources of longer",
            "invincibility at once. They multiply, so two 1.8x items would be 3.24x without this.",
            "Default 3.0."
    })
    @Config.Name("Max Multiplier")
    @Config.RangeDouble(min = 1.0D, max = 10.0D)
    public double maxMultiplier = 3.0D;

    @Config.Comment({
            "How much longer invincibility the Cross Necklace (bountifulbaubles:amuletcross)",
            "grants while worn in a baubles slot. Applies to melee, arrows, magic, fire and",
            "everything else - which is what its tooltip promises and what plain WorseHurtTimer",
            "does not deliver.",
            "1.0 disables the item's effect. Does nothing unless Bountiful Baubles is installed.",
            "Default 1.8, which is the ratio the item was originally written with (20 -> 36)."
    })
    @Config.Name("Cross Necklace Multiplier")
    @Config.RangeDouble(min = 1.0D, max = 10.0D)
    public double crossNecklaceMultiplier = 1.8D;

    @Config.Comment({
            "Collect diagnostics about what WorseHurtTimer actually does: how often its limiter",
            "refuses an attack, which damage sources appear, and what invincibility windows get",
            "opened. Counts events instead of narrating them, and always records the target -",
            "WorseHurtTimer's own logging does neither.",
            "Costs one boolean read per attack while off. Default OFF."
    })
    @Config.Name("Diagnostics: Enabled")
    public boolean diagEnabled = false;

    @Config.Comment({
            "On top of the counters, write one log line per event.",
            "Only for short sterile tests - in a real fight this is thousands of lines a minute.",
            "Default OFF."
    })
    @Config.Name("Diagnostics: Verbose")
    public boolean diagVerbose = false;

    @Config.Comment({
            "Only record events whose target is a player. Turning this off also counts mob-on-mob",
            "combat, which is a great deal of noise unless that is what you are investigating.",
            "Default ON."
    })
    @Config.Name("Diagnostics: Players Only")
    public boolean diagPlayersOnly = true;

    @Config.Comment({
            "Write the counters to the log every N seconds. 0 disables the interval dump and",
            "leaves /whtdiag as the only way to see them.",
            "Default 60."
    })
    @Config.Name("Diagnostics: Dump Interval Seconds")
    @Config.RangeInt(min = 0, max = 3600)
    public int diagDumpIntervalSeconds = 60;
}
