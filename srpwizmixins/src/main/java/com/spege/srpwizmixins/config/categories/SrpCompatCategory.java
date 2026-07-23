package com.spege.srpwizmixins.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Scape and Run: Parasites (srparasites) native-patch compatibility module.
 *
 * <p>Ports the most valuable SRPMixins (2.9.5, target SRP 1.9.21) tweaks onto our
 * SRParasites 1.10.7 (community) build as InsaneTweaks mixins. Every fix is gated
 * behind its own toggle and defaults to OFF so the module is inert until opted in.
 *
 * <p>All the SRP-targeting mixins live in {@code mixins.insanetweaks.srp.json}, which
 * {@code LateMixinBooter} only queues when {@code srparasites} is present. Individual
 * mixin handlers additionally self-gate on the flags below at HEAD, so toggling a fix
 * off makes it behave exactly like unmodified SRP.
 */
public class SrpCompatCategory {

    @Config.Comment({
            "Diagnostic logging for the SRP-compat module.",
            "When true, logs the real despawn channel that removes beckons/nexuses and the",
            "dimension-points init sequence (addDim / setTotalKills / choice multiplier).",
            "Read live at runtime - can be toggled without a restart. Verbose; leave OFF in normal play."
    })
    @Config.Name("Debug Logging")
    public boolean debugLogging = false;

    @Config.Comment({
            "Fix A - stop SRP's over-cap parasite purge from deleting flagged entities.",
            "When the parasite population exceeds the spawning mob cap, SRP's custom spawner",
            "(SRPSpawning$DimensionHandler.onSpawn, the 'SOO MANY PARASITES' cull) calls setDead",
            "on nearby parasites one by one - blindly, ignoring the cannot-despawn flag. That is",
            "what silently deletes summoned/important beckons and nexuses (confirmed via diag).",
            "With this ON, the cull always skips beckons/nexuses (SRP registry name beckon/venkrol/",
            "nexus), and additionally skips ANY parasite within 'Cap Purge Protect Radius' of a player",
            "(see below). Parasites far from every player are still culled, so the mob cap is still",
            "enforced where the player can't see it.",
            "Requires MC restart (mixin gate). Default OFF."
    })
    @Config.Name("Fix: Protect Non-Despawnable From Cap Purge")
    @Config.RequiresMcRestart
    public boolean protectNonDespawnableFromCapPurge = false;

    @Config.Comment({
            "Radius (blocks) around each player inside which ORDINARY parasites are spared from the",
            "over-cap 'SOO MANY PARASITES' cull (only applies when the fix above is ON). Parasites",
            "farther than this from every player are still culled. 0 = no near-player protection for",
            "ordinary parasites. Lower it if a huge nearby horde hurts performance. Read live (no restart)."
    })
    @Config.Name("Cap Purge Protect Radius")
    @Config.RangeInt(min = 0, max = 256)
    public int capPurgeProtectRadius = 48;

    @Config.Comment({
            "Radius (blocks) around each player inside which BECKONS/NEXUSES are spared from the",
            "over-cap cull. Separate from (and normally larger than) the ordinary radius so deterrent",
            "structures survive over a wider area - but beyond this distance even they can be culled,",
            "so beckons don't pile up forever far from any player. 0 = beckons/nexuses are never",
            "specially protected (culled like anything else). Read live (no restart)."
    })
    @Config.Name("Beckon/Nexus Cap Purge Radius")
    @Config.RangeInt(min = 0, max = 2048)
    public int beckonCapPurgeRadius = 200;

    @Config.Comment({
            "Fix B - honor the per-dimension starting POINTS token in the SRP config option",
            "'Evolution Phases Dimension Starting Phase List' (format dim;phase;points).",
            "On 1.10.7 the config-init calls setTotalKills with canChangePhase=false, which does",
            "not persist the value, so every fresh dimension keeps 'Default Points Start' (-300)",
            "regardless of the configured points (confirmed via diag: dim 111 configured 600M",
            "ended up -300, then degraded a phase on the first point tick). Phase -2 dimensions",
            "are rejected outright. With this ON, the starting-list points are written directly",
            "at world-data creation so each dimension starts with exactly the configured value.",
            "Requires MC restart (mixin gate). Default OFF."
    })
    @Config.Name("Fix: Apply Starting Points")
    @Config.RequiresMcRestart
    public boolean fixStartingPoints = false;

    @Config.Comment({
            "P3 (mobcapmulti) - enable per-dimension parasite mob-cap multipliers.",
            "SRP's spawn caps are global; this lets a dimension use a fraction (or multiple) of the",
            "spawning cap. Scales SRPConfig.worldSpawningMobCap (the population the 'SOO MANY",
            "PARASITES' cull trims down to) for dimensions listed in 'Per-Dimension Mob Cap",
            "Multipliers' below. Dimensions without an entry are unaffected.",
            "Requires MC restart (mixin gate). Default OFF."
    })
    @Config.Name("Enable Per-Dimension Mob Cap")
    @Config.RequiresMcRestart
    public boolean enablePerDimMobCap = false;

    @Config.Comment({
            "Per-dimension parasite spawning-cap multipliers, one entry per line as 'dim=multiplier'.",
            "Only applies when 'Enable Per-Dimension Mob Cap' is ON. Multiplier < 1 lowers the cap,",
            "> 1 raises it. Example: '111=0.75' gives Lost Cities (dim 111) a 25% lower parasite cap.",
            "Read live (no restart). Malformed entries are ignored."
    })
    @Config.Name("Per-Dimension Mob Cap Multipliers")
    public String[] perDimMobCapMultipliers = new String[] { "111=0.75" };

    @Config.Comment({
            "Fix C - route evolution-point writes from worker threads onto the server thread.",
            "EntityThreading ticks entities on worker threads; SRP entity code (e.g. the COTH",
            "duration refresh) then calls SRPSaveData.setTotalKills concurrently with the server",
            "thread, corrupting the parallel ArrayLists that hold per-dimension points/phases.",
            "With this ON, a setTotalKills call arriving off-thread is re-scheduled onto the main",
            "thread (the caller gets 'false'; the write lands a fraction of a tick later).",
            "REQUIRED before unlocking parasites in any dimension while EntityThreading is active.",
            "Requires MC restart (mixin gate). Default OFF."
    })
    @Config.Name("Fix: SaveData Thread Safety")
    @Config.RequiresMcRestart
    public boolean fixSaveDataThreadSafety = false;

    @Config.Comment({
            "Perf - reject doomed setTotalKills calls cheaply at method entry.",
            "SRP computes the 'choice' multiplier and builds debug/CSV log data BEFORE checking",
            "whether the dimension can actually gain/lose points (gaining off, phase -2). Infestation",
            "blocks call this on EVERY random tick (~1100/s at node area ~550), so a locked overworld",
            "burns CPU on calls that are always rejected. With this ON, the exact same rejection",
            "conditions are evaluated first and such calls return false immediately.",
            "Only runs on the server thread (the getters mutate on unknown dims). Behavior is",
            "identical to unmodified SRP - only wasted work is skipped.",
            "Read live at call time (no restart needed). Default OFF."
    })
    @Config.Name("Perf: Early Reject SetTotalKills")
    public boolean perfEarlyRejectSetTotalKills = false;

    @Config.Comment({
            "Perf - infestation spread throttle divisor. SRP's infestation blocks",
            "(BlockParasiteSpreading/BlockInfestedRemain) do their full spread + evolution-point",
            "work on every update tick (~1100/s at node area ~550). With divisor N, only ~1/N of",
            "those ticks run; the rest are skipped at method entry. Visual spread continues at 1/N",
            "pace - throttle, don't disable (the creep IS the atmosphere). 1 = off (vanilla SRP).",
            "Read live at call time (no restart). Default 1."
    })
    @Config.Name("Perf: Infestation Spread Throttle Divisor")
    @Config.RangeInt(min = 1, max = 64)
    public int spreadThrottleDivisor = 1;
}
