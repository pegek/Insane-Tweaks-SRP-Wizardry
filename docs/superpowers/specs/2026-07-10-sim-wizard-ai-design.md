# Spec: Sim Wizard — casting-bug diagnosis + combat-AI consolidation

Date: 2026-07-10
Status: approved by user (brainstorming session 2026-07-10)

## Problem

Persistent playtest symptom across v3.1–v3.3: the sim_wizard **very rarely
casts, and when it does it is almost always magic_missile**. Multiple fixes
(world-time cooldowns, band randomization, config pool) did not kill the bug.

Static analysis done during brainstorming (2026-07-10) ruled out the cheap
suspects: `Spell.get` accepts namespaced ids (decompiled EB source), SRP AI
tasks do not override `isInterruptible`, `SpellProjectile.cast(NPC)` cannot
fail with a non-null target, cast/kite/melee mutex-priority interplay is
formally correct. Root cause is therefore **not statically visible** — runtime
data is required. Note: the dev `run/` has no `insanetweaks.cfg`; playtests
happen on the user's production instance whose config may differ.

## E1 — Diagnostic pass (before any rework)

New flag `client.enableSimWizardDebugLogs`. When on, log:

- spell pool contents right after `ensureSpellPool` builds it (what actually
  parsed from config);
- every `shouldExecute` rejection with reason (cooldown remaining / no attack
  target / target invalid / out of decision range) — throttled to ~1 line/s;
- every `pickSpell` decision: distance, band, situational override taken,
  chosen spell;
- every `spell.cast(...)` result (true/false) and the cooldown applied;
- effective time between successful casts.

Playtest protocol: user runs their production instance with the flag on,
provides the log plus their `insanetweaks.cfg`. Root cause is identified from
data before E2 lands (E2 proceeds regardless, but the fix must be explainable —
if the cause lies outside the AI task, e.g. SRP targeting or config, it must
be fixed separately or the rework will not help).

## E2 — Combat-AI consolidation (single state-machine task)

The recurring source of regressions is three competing tasks (cast mutex 3 /
kite mutex 1 / SRP melee mutex 3) layered over native SRP AI. Replace them with
**one task** owning both movement and casting:

- `EntityAISimWizardCombat` (priority 3, mutex 3), states:
  - **APPROACH** — target beyond decision range: move toward it;
  - **HOLD** — 7–18 blocks: stand / micro-kite (back off under 7, sidestep
    occasionally), look at target;
  - **TELEGRAPH** — wind-up (existing signal + particles + sound), stationary;
  - **FIRE / CHANNEL** — existing fireCommittedCast / tickChannel logic moved
    over 1:1 (it works when it runs);
  - **COOLDOWN** — movement allowed within the same task (kite behaviour),
    transitions back to TELEGRAPH when ready.
- `EntityAISimWizardKite` and the SRP `EntityAIAttackMeleeStatus` fallback are
  **removed**. The wizard is a pure caster; banish remains its close-quarters
  answer. Melee attribute stays registered but unused.
- Kept untouched: telegraph/channel mechanics, pickSpell heuristics, spell pool
  config, phase scaling, faction handling (`MixinEntityParasiteBase`,
  `SimWizardFactionHandler`, `isOnSameTeam`), all visuals/audio, floating focus.
- Old task classes deleted; `initEntityAI` priorities re-documented in
  `notes/sim_wizard_v1.md` (new section v4).

## Acceptance criteria

1. With debug logs on, root cause of the old symptom is named and written into
   `notes/sim_wizard_v1.md` (v4 section) — no "fixed by rewrite, cause unknown".
2. Playtest: wizard casts within its cadence config (default ~2.5–6.5 s between
   casts) and uses at least 4 distinct spells over a few minutes of combat.
3. Wizard never runs into melee range voluntarily; keeps 7–18 block band.
4. No parasite-vs-wizard friendly fire regressions.
