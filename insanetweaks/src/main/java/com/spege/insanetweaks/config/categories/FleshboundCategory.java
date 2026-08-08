package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the <b>Fleshbound</b> mechanic, i.e. the {@code grip} advanced property: the weapon
 * cannot be thrown away, dragged out of the inventory or knocked out of your hands.
 *
 * <p>Two things live here. The first is the <b>sever penalty</b> paid on death, whose two numbers
 * were hardcoded in {@code FleshboundEventHandler} and could not be tuned at all. The second is the
 * <b>rip-out</b> system: recovering the weapon is no longer unconditional, so a mob that disarms you
 * repeatedly can eventually tear it loose. Rip-out ships OFF, so nothing changes for an existing
 * pack until it is switched on.
 *
 * <p>Everything here is read live. Accessed as {@code ModConfig.fleshbound.*}.
 * The recovery radius fallback stayed in {@code propertyBooks.gripRecoveryRadius} - moving it would
 * silently reset a tuned value in an existing {@code insanetweaks.cfg}.
 */
public class FleshboundCategory {

    // ----------------------------------------------------------------
    // SEVER ON DEATH
    // ----------------------------------------------------------------

    @Config.Comment({
            "How long the bond stays severed after you die, in ticks. 36000 = 30 minutes. During",
            "this the weapon behaves like any other item: it can be dropped and taken. Both this and",
            "'Sever Regrow Kills' must be satisfied before it binds again. Read live."
    })
    @Config.Name("Sever Regrow Ticks")
    @Config.RangeInt(min = 0, max = 432000)
    public int severRegrowTicks = 36000;

    @Config.Comment({
            "How many further kills the weapon must log before the bond returns, on top of the time",
            "above. Counted from the weapon's own SentientKills tag, so a weapon that does not track",
            "kills only ever waits out the timer. Read live."
    })
    @Config.Name("Sever Regrow Kills")
    @Config.RangeInt(min = 0, max = 10000)
    public int severRegrowKills = 50;

    // ----------------------------------------------------------------
    // RIP-OUT
    // ----------------------------------------------------------------

    @Config.Comment({
            "Let a weapon be torn loose. When OFF (the default) Fleshbound recovers the weapon every",
            "single time, which is the behaviour every existing world has. When ON, only",
            "'Recovery Limit' recoveries fit into 'Recovery Window Ticks'; the next disarm past that",
            "goes through, and the weapon really does hit the ground. Read live."
    })
    @Config.Name("Enable Rip Out")
    public boolean enableRipOut = false;

    @Config.Comment({
            "How many recoveries fit inside one window before the weapon can be torn loose.",
            "Only used while 'Enable Rip Out' is ON. Read live."
    })
    @Config.Name("Recovery Limit")
    @Config.RangeInt(min = 1, max = 100)
    public int recoveryLimit = 3;

    @Config.Comment({
            "Length of the recovery window in ticks; 1200 = 60 seconds. The counter resets the first",
            "time a recovery happens after the window has run out, so a slow trickle of disarms never",
            "accumulates into a rip-out. Read live."
    })
    @Config.Name("Recovery Window Ticks")
    @Config.RangeInt(min = 20, max = 72000)
    public int recoveryWindowTicks = 1200;

    @Config.Comment({
            "How long the weapon stays torn loose, in ticks. 600 = 30 seconds. While the timer runs",
            "the weapon is an ordinary item - it can be picked up by hand, and by anyone.",
            "🚨 The minimum is 20 on purpose, not for taste: the dropped item joins the world on the",
            "very next tick, and if the cooldown had already expired by then the recovery handler",
            "would catch it and hand it straight back - the player would eat the rip-out damage and",
            "keep the weapon, which is the one outcome this system must not produce. Read live."
    })
    @Config.Name("Rip Out Cooldown Ticks")
    @Config.RangeInt(min = 20, max = 72000)
    public int ripOutCooldownTicks = 600;

    @Config.Comment({
            "Damage dealt to the owner at the moment the weapon is torn out, as MAGIC damage so",
            "armour does not soak it. 0 disables the hit. Read live."
    })
    @Config.Name("Rip Out Damage")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double ripOutDamage = 4.0D;

    @Config.Comment({
            "Duration in ticks of the bleed effect applied on rip-out. 0 disables it, and so does an",
            "unresolvable 'Rip Out Bleed Potion'. Read live."
    })
    @Config.Name("Rip Out Bleed Ticks")
    @Config.RangeInt(min = 0, max = 12000)
    public int ripOutBleedTicks = 100;

    @Config.Comment({
            "Registry name of the potion used for the rip-out bleed. Looked up by name, so no",
            "particular mod is required: a name nothing registers simply means no bleed effect, and",
            "everything else about the rip-out still happens. The default is Scape and Run:",
            "Parasites' bleed, which is what this pack's other bleeding weapons use. Read live."
    })
    @Config.Name("Rip Out Bleed Potion")
    public String ripOutBleedPotion = "srparasites:bleed";

    @Config.Comment({
            "Duration in ticks of the Weakness applied on rip-out - the stagger from losing your grip.",
            "0 disables it. Read live."
    })
    @Config.Name("Rip Out Weakness Ticks")
    @Config.RangeInt(min = 0, max = 12000)
    public int ripOutWeaknessTicks = 100;

    @Config.Comment({
            "Amplifier of that Weakness. Vanilla draws it one higher, so 1 reads 'Weakness II'.",
            "Read live."
    })
    @Config.Name("Rip Out Weakness Amplifier")
    @Config.RangeInt(min = 0, max = 20)
    public int ripOutWeaknessAmplifier = 1;

    // ----------------------------------------------------------------
    // DISPLAY
    // ----------------------------------------------------------------

    @Config.Comment({
            "Show the Fleshbound status lines on the weapon's tooltip: the regrowth countdown while",
            "the bond is severed, and the rip-out state while 'Enable Rip Out' is on. Client-side",
            "only. Read live."
    })
    @Config.Name("Show Status Tooltip")
    public boolean showStatusTooltip = true;
}
