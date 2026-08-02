package com.spege.srpwizmixins.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Scape and Run: Parasites (srparasites) native-patch compatibility module.
 *
 * <p>Ports the most valuable SRPMixins (2.9.5, target SRP 1.9.21) tweaks onto our
 * SRParasites 1.10.7 (community) build as InsaneTweaks mixins. Every fix is gated
 * behind its own toggle and defaults to OFF so the module is inert until opted in.
 *
 * <p>All the SRP-targeting mixins live in {@code mixins.srpwizmixins.json}, which
 * {@code SrpWizMixinsLateBooter} only queues when {@code srparasites} is present.
 *
 * <p>IMPORTANT — the flags below are NOT mixin gates. {@code mixins.srpwizmixins.json} declares no
 * {@code plugin}, so all eight mixins are applied unconditionally once SRP is present; every flag
 * here is an early-return at the top of the injected handler. Two consequences:
 * <ul>
 *   <li>Toggling a flag takes effect immediately — none of them need a restart.</li>
 *   <li>Anything that can go wrong at mixin <em>application</em> time (a {@code VerifyError} from a
 *       bad merged {@code <clinit>}, a missing injection point) happens whether the flag is on or
 *       off. A crash is never excused by "but that fix was disabled".</li>
 * </ul>
 * Turning a fix off still makes the patched method behave exactly like unmodified SRP. Making these
 * real application gates would need an {@code IMixinConfigPlugin}; see the note in
 * {@code com.spege.srpwizmixins.util.SrpLocks} for why the current arrangement is the safer one.
 */
public class SrpCompatCategory {

    @Config.Comment({
            "Extra logging for this mod, for when something is not behaving and you want to know why.",
            "Logs what actually removed a Beckon or Nexus, and how each dimension's evolution points",
            "were set up at world load.",
            "Very noisy - only turn it on while investigating something. No restart needed. Default OFF."
    })
    @Config.Name("Debug Logging")
    public boolean debugLogging = false;

    @Config.Comment({
            "Stops parasites vanishing in front of you when the population hits its cap.",
            "When there are too many parasites in a dimension, SRP deletes some of them - and it does",
            "not check whether they were meant to stay, so a Beckon or Nexus you were fighting can",
            "disappear mid-fight.",
            "With this ON, Beckons and Nexuses are never deleted this way, and no parasite is deleted",
            "while it is close to a player (see the two radius options below). Parasites far from",
            "everyone are still removed, so the cap still does its job.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: Protect Non-Despawnable From Cap Purge")
    public boolean protectNonDespawnableFromCapPurge = false;

    @Config.Comment({
            "How close (in blocks) an ordinary parasite has to be to a player to be spared from the",
            "over-population cull. Only used when the fix above is ON.",
            "Lower this if a big horde right next to you is hurting performance. 0 = no protection",
            "for ordinary parasites at all. No restart needed."
    })
    @Config.Name("Cap Purge Protect Radius")
    @Config.RangeInt(min = 0, max = 256)
    public int capPurgeProtectRadius = 48;

    @Config.Comment({
            "The same thing for Beckons and Nexuses, which normally deserve a wider safety zone so",
            "they are not wiped out from across the map. Beyond this distance even they can be culled,",
            "so they do not pile up forever in chunks nobody visits.",
            "0 = they get no special protection. No restart needed."
    })
    @Config.Name("Beckon/Nexus Cap Purge Radius")
    @Config.RangeInt(min = 0, max = 2048)
    public int beckonCapPurgeRadius = 200;

    @Config.Comment({
            "Makes SRP actually use the starting evolution points you configured per dimension.",
            "In SRP's own config you can write 'dimension;phase;points' to give a dimension a head",
            "start - but on 1.10.7 the points part is thrown away, so every new dimension silently",
            "falls back to the global default instead.",
            "With this ON, the configured value is written when the dimension is first created.",
            "Set it up in SRP's config first; this only makes that setting stick.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: Apply Starting Points")
    public boolean fixStartingPoints = false;

    @Config.Comment({
            "Lets you set a different parasite population cap per dimension.",
            "SRP's cap is a single global number, so a parasite dimension and your home dimension",
            "have to share it. With this ON, the dimensions you list below get their own multiplier.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Enable Per-Dimension Mob Cap")
    public boolean enablePerDimMobCap = false;

    @Config.Comment({
            "One entry per line, written as 'dimension=multiplier'.",
            "Below 1 lowers the cap, above 1 raises it. For example '111=0.75' gives dimension 111",
            "a quarter fewer parasites than the global cap allows.",
            "Only used when the option above is ON. Dimensions you do not list are unaffected.",
            "Bad entries are ignored. No restart needed."
    })
    @Config.Name("Per-Dimension Mob Cap Multipliers")
    public String[] perDimMobCapMultipliers = new String[] { "111=0.75" };

    @Config.Comment({
            "Required if you run a mod that ticks entities on more than one thread (EntityThreading).",
            "SRP keeps each dimension's evolution points and phase in lists that are not safe to write",
            "from two threads at once, so a parasite ticked on a worker thread can corrupt them - your",
            "world ends up with wrong phases or points that jump around.",
            "With this ON, such writes are handed back to the main thread and land a fraction of a",
            "second later instead of corrupting anything.",
            "Turn this ON before unlocking parasites in more than one dimension while a threading mod",
            "is installed. Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: SaveData Thread Safety")
    public boolean fixSaveDataThreadSafety = false;

    @Config.Comment({
            "The companion to the fix above, for the moment SRP's save data is first created.",
            "That code is not thread-safe either: two threads can create it at the same time, which",
            "either loses every point written into the discarded copy, or corrupts the world's save-data",
            "list badly enough to cut the world save short.",
            "With this ON that step runs one thread at a time. Nothing else changes.",
            "Turn it on together with the fix above if you run a threading mod.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Fix: SaveData Get Race")
    public boolean fixSaveDataGetRace = false;

    @Config.Comment({
            "Performance: stop SRP doing expensive bookkeeping for point updates it is going to throw",
            "away anyway.",
            "Infestation blocks ask SRP to add evolution points on nearly every block tick - roughly a",
            "thousand times a second in a developed infestation - and in a dimension where parasites",
            "cannot gain points those requests are all rejected, but only after the work is done.",
            "With this ON the rejection happens first. The result is identical to unmodified SRP,",
            "it just costs less.",
            "Takes effect immediately, no restart. Default OFF."
    })
    @Config.Name("Perf: Early Reject SetTotalKills")
    public boolean perfEarlyRejectSetTotalKills = false;

    @Config.Comment({
            "Slows down how fast parasite infestation spreads across blocks, and cuts the server load",
            "it causes by the same amount. In a heavily infested world this is one of the biggest tick",
            "costs in the game.",
            "2 = half speed, 4 = quarter speed, and so on. 1 = untouched SRP behaviour.",
            "The creep still spreads and still looks the same, it just takes longer - which is usually",
            "what you want rather than turning it off.",
            "Takes effect immediately, no restart. Default 1."
    })
    @Config.Name("Perf: Infestation Spread Throttle Divisor")
    @Config.RangeInt(min = 1, max = 64)
    public int spreadThrottleDivisor = 1;
}
