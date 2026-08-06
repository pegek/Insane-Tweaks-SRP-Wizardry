package com.spege.reskilltweaks.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Tunables for the Scarred Flesh trait ({@code compatskills:scarred_flesh}).
 *
 * <p>The trait gives the player a <b>total affliction budget</b>. Every hostile parasite effect
 * costs its displayed level — Viral VII costs 7, Fear IV costs 4 — and the sum across all of them
 * may never exceed {@link #totalLevelBudget}. An incoming affliction is admitted at whatever level
 * still fits, and refused outright once nothing does.
 *
 * <p>Worked example at the default budget of 15, with Viral VII + Fear IV + Coth III already on
 * the player (7 + 4 + 3 = 14):
 * <pre>
 *   incoming Needler III   1 of 15 left  -&gt; lands as Needler I
 *   incoming Nexus         0 of 15 left  -&gt; refused outright
 * </pre>
 *
 * <p>Budgeting in displayed levels rather than raw amplifiers is deliberate: it is the number on
 * the player's status bar, so the ceiling is something they can count for themselves.
 *
 * <h3>Legacy behaviour (pre-2026-07-28)</h3>
 * The trait used to work per-slot instead of per-level: the first N afflictions passed untouched,
 * each further one had its amplifier capped and duration cut along a configurable ladder, and past
 * a hard slot ceiling it was refused. The knobs were {@code freeDebuffs} (2), {@code maxDebuffs}
 * (7), {@code amplifierCaps} ({@code 4,3,2,1,0}) and {@code durationMultipliers}
 * ({@code 1.0,0.85,0.75,0.65,0.55}), producing:
 * <pre>
 *   slot 1-2  untouched
 *   slot 3    amplifier capped at V,   full duration
 *   slot 4    amplifier capped at IV,  85% duration
 *   slot 5    amplifier capped at III, 75% duration
 *   slot 6    amplifier capped at II,  65% duration
 *   slot 7    amplifier capped at I,   55% duration
 *   slot 8+   refused outright
 * </pre>
 * Kept on record because the shape may return in another form. Its weakness was that it counted
 * afflictions rather than weighing them: seven level-I effects hit the ceiling exactly as hard as
 * seven level-VII ones.
 *
 * <p>All values are read live inside the handler — no restart needed.
 */
public class ScarredFleshCategory {

    @Config.Name("Total Level Budget")
    @Config.Comment({
            "Combined displayed level of all hostile parasite afflictions the player may carry.",
            "Displayed level, not amplifier: Viral VII costs 7. An incoming affliction is admitted",
            "at whatever level still fits under this ceiling, and refused once nothing does.",
            "Refreshing an affliction already active is measured against the others only, so a",
            "refresh never competes with itself." })
    @Config.RangeInt(min = 1, max = 200)
    public int totalLevelBudget = 15;

    @Config.Name("Additional Hostile Effects")
    @Config.Comment({
            "Extra effect ids treated as parasite afflictions, format modid:effect.",
            "The built-in list already covers SRParasites 1.10.7 and SRPExtra 1.10.7.5.",
            "Do NOT add host buffs here (srparasites:the_sign, parate, muscleout) — the trait",
            "would then block effects that help you." })
    public String[] additionalHostileEffects = {};

    @Config.Name("Debug Logging")
    @Config.Comment("Log every reduced or refused affliction, with the budget arithmetic, to the server log.")
    public boolean debugLogging = false;
}
