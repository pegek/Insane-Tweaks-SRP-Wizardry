# Farming Freeze Fix & Ray of Purification vs Beckons (2026-07-07)

Approved by the user (2026-07-07) after in-game testing of the thrall-fixes batch. Two independent items.

## 1. Farming NAVIGATING↔WORKING freeze (bugfix)

**Root cause (confirmed by code trace):** `ThrallAIFarming` uses corner-based distance (`Entity.getDistanceSq(BlockPos)`) for the NAVIGATING arrival check but center-based distance (`+0.5`) for the WORKING drift check, with both thresholds equal (`CLOSE_ENOUGH_SQ = WORK_RANGE_SQ = 4.0`). Approaching a tile from the −X/−Z diagonal satisfies the corner check while failing the center check, producing a permanent NAVIGATING↔WORKING oscillation in which the thrall never moves. The `navTimer` teleport fallback never fires because the WORKING→NAVIGATING branch resets it and NAVIGATING short-circuits before `navTimer++`. A physical push changes the position and breaks the cycle — matching the reported symptom exactly. Woodcutting, Mineshaft and Collecting do not share the pattern.

**Fix (all in `entities/ai/ThrallAIFarming.java`):**
1. NAVIGATING arrival check uses the same center-based distance as WORKING: `thrall.getDistanceSq(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5)`.
2. Hysteresis: `CLOSE_ENOUGH_SQ` stays 4.0 (2.0 blocks, arrival), `WORK_RANGE_SQ` becomes 6.25 (2.5 blocks, drift). Arrival radius strictly smaller than drift radius makes the oscillation geometrically impossible.
3. `navTimer++` moves above the arrival check in the NAVIGATING branch so the teleport fallback always counts up during borderline approaches.

No other behavior changes; scan logic (including the home fallback) untouched.

## 2. Ray of Purification deals absolute damage to Beckons

Ray of Purification is EBW's continuous `SpellRay` (`electroblob.wizardry.spell.RayOfPurification`); its `onEntityHit` ticks `damage × potency` RADIANT magic damage every 10 use-ticks. SRP Beckons shrug this off via their own damage caps/immunities. New behavior, per user decisions:

1. **Trigger:** once per cast per Beckon — the FIRST time a given Beckon (`SrpPurificationHelper.isBeckon`) is hit during a single continuous cast of the ray. Re-triggering requires releasing and re-casting. Sweeping onto a second Beckon mid-cast gives that Beckon its own first-hit proc.
2. **Damage:** linear scale from **20 at potency 1.0** to **80 at potency ≥ 1.2** (`damage = 20 + (min(max(potency, 1.0), 1.2) − 1.0) × 300`); potency below 1.0 clamps to 20.
3. **Unavoidable:** the damage must actually land despite SRP mitigation — bypass armor and SRP's per-hit damage caps. Mechanism to be verified during planning against decompiled SRP 1.10.7 sources (how `EntityParasiteBase`/Beckon classes clamp incoming damage in `attackEntityFrom`); preferred: a `DamageSource` with `setDamageBypassesArmor().setDamageIsAbsolute()`; fallback if SRP clamps regardless of source: direct `setHealth` reduction with proper hurt feedback (`performHurtAnimation`/sound) and death handling via a minimal final `attackEntityFrom` for the killing blow.
4. **Hook:** late mixin on `RayOfPurification.onEntityHit` via the existing EBW spell-mixin infrastructure (`mixins.insanetweaks.late.json`, package `com.spege.insanetweaks.mixins`); if injection proves awkward, a `LivingHurtEvent`-based fallback keyed on RADIANT `MagicDamage` + `isBeckon` is acceptable, with once-per-cast tracking. Planning decides based on the existing mixin examples.
5. Normal ray behavior against everything that is not a Beckon is untouched. Blindness effect and regular per-10-ticks damage continue as EBW ships them (the once-per-cast hit is an ADDITIONAL absolute-damage application on Beckons).
6. Gate the handler/mixin behavior behind the same module flag as other spell tweaks if the infrastructure allows; a mixin that always applies is acceptable if flag-gating inside the injected code checks `ModConfig`.

## Must NOT change

Thrall invariants (see thrall-fixes spec); EBW spell JSONs; SRP jars/config defaults; existing mixin configs' manifest wiring (late list may gain an entry).

## Verification

`./gradlew build` green per task. In-game (deferred, appended to the NEXT_SESSION_SPELLS.md checklist): farming thrall approached tiles from all diagonals without freezing (no state flip-flop in debug logs, nav-timeout still functional); ray on a Beckon procs one 20–80 hit per cast (test potency 1.0 and ≥1.2), re-cast re-procs, non-Beckon targets unaffected.
