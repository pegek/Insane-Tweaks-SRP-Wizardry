# Farming Freeze Fix & Ray of Purification vs Beckons — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two independent items. (1) Fix the `ThrallAIFarming` NAVIGATING↔WORKING freeze caused by a corner-vs-center distance mismatch. (2) Make EBW's Ray of Purification deal one unavoidable absolute-damage hit to each SRP Beckon per continuous cast, bypassing SRP's per-hit damage cap.

**Architecture:** Task 1 is confined to `entities/ai/ThrallAIFarming.java` — three surgical edits (one constant, one reordered/retargeted arrival check, one post-teleport recheck). No new fields, no signature changes. Task 2 adds a late mixin on EBW's `RayOfPurification.onEntityHit` (via the existing `mixins.insanetweaks.late.json` spell-mixin infrastructure) plus one server-side helper class `util/RayBeckonPurge.java` that owns once-per-cast bookkeeping and the cap-bypassing damage application. No source files outside these are modified; the mixin JSON gains exactly one entry. Task 3 appends the two deferred in-game checks to `NEXT_SESSION_SPELLS.md`.

**Tech Stack:** Java 8, Minecraft 1.12.2 Forge `1.12.5.2860`, EBW 4.3.19 (CurseMaven dev jar, fg.deobf), SRParasites 1.10.7 (`libs/`, fg.deobf), Cleanroom MixinBooter (late loader), ForgeGradle 3. No test suite, no lint task.

**Spec:** `docs/superpowers/specs/2026-07-07-farming-freeze-ray-beckon-design.md`

**Testing note:** No automated test suite exists (per CLAUDE.md). Per-task verification = `./gradlew build` green. In-game testing is deferred by the user; the manual checklist is extended in `NEXT_SESSION_SPELLS.md` at the final task.

---

## Key API facts (verified against sources, do not re-derive)

### Task 1 — Farming freeze

- **The two distance checks disagree.** In `ThrallAIFarming` (current tree):
  - Constants (lines ~54-56): `CLOSE_ENOUGH_SQ = 4.0` (arrival), `WORK_RANGE_SQ = 4.0` (WORKING drift) — **equal**, no hysteresis.
  - NAVIGATING arrival (line ~573): `double distSq = thrall.getDistanceSq(targetPos);` — **corner-based** `Entity.getDistanceSq(BlockPos)` measures to the block's integer corner `(x, y, z)`.
  - WORKING drift (line ~662): `thrall.getDistanceSq(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5)` — **center-based** (`+0.5`), ~0.7 blocks farther on a diagonal than the corner.
  - `navTimer++` (line ~579) sits **below** the arrival short-circuit (`if (distSq <= CLOSE_ENOUGH_SQ) { startWorking(); return; }`, lines ~574-577), so it never advances while oscillating on the reach boundary.
  - WORKING→NAVIGATING drift branch resets `navTimer = 0` (line ~668).
  - **Oscillation:** approaching a tile from the −X/−Z diagonal, the corner check `<= 4.0` passes (→ WORKING) while the center check `> 4.0` immediately fails (→ NAVIGATING, `navTimer = 0`). The teleport fallback never fires (navTimer stuck at 0). Only an external position change (a physical push) breaks it — exactly the reported symptom.
- **A second arrival check exists** at line ~598 (post-teleport recheck inside the NAV_TIMEOUT block): `if (thrall.getDistanceSq(targetPos) <= CLOSE_ENOUGH_SQ)` — also corner-based. Made center-based too, for consistency (same edit family).
- **NAVIGATING already reaches any distance** — teleport fallback after `NAV_TIMEOUT_TICKS` handles far/blocked targets; no change needed there. The home-fallback scan (F-1, `searchAround`) is already present in the current tree from a prior session — **not touched**.

### Task 2 — Ray of Purification vs Beckons

- **EBW `RayOfPurification` is a singleton `SpellRay` (continuous).** `onEntityHit` signature (dev/fg.deobf names, EBW's own method — NOT SRG):
  `protected boolean onEntityHit(World world, Entity target, Vec3d hit, EntityLivingBase caster, Vec3d origin, int ticksInUse, SpellModifiers modifiers)`.
  Source: `notes/decompiled_mods/ebwizardry_source/decompiled_src/electroblob/wizardry/spell/RayOfPurification.java` (lines 67-86) and `SpellRay.java` (abstract decl line 196; called from `shootSpell` line 178).
- **Call cadence.** `SpellRay.shootSpell` calls `onEntityHit` **every tick the ray traces onto an entity** (RayTracer returns the single first entity hit per tick). Within `onEntityHit`, EBW applies its RADIANT damage + blindness only when `ticksInUse % 10 == 0` and prints a resist message when `ticksInUse == 1`. **We do not touch that path** — our proc is additive.
- **`ticksInUse` semantics for continuous casts.** It is the channel-hold counter: monotonically **increases by 1 per game tick within one continuous cast**, and **resets toward 0 when the player releases and re-casts**. This makes it the natural once-per-cast session key: a drop (`ticksInUse <= lastSeen`) means a new cast.
- **`SpellModifiers.POTENCY = "potency"`** (verified in `SpellModifiers.java` line 24). `modifiers.get(SpellModifiers.POTENCY)` returns the potency multiplier as a `float`.
- **`SrpPurificationHelper.isBeckon(Entity)`** (our codebase, `util/SrpPurificationHelper.java` lines 66-69) returns true for `srparasites:beckon_si|sii|siii|siv` via `EntityList.getKey`. Reused verbatim to identify Beckons.
- **SRP DAMAGE-CAP VERDICT — an absolute DamageSource does NOT bypass the cap.** Verified in `EntityParasiteBase.func_70097_a` (`notes/decompiled_mods/srp_sourcecode_analis/.../entity/ai/misc/EntityParasiteBase.java`, lines 664-813):
  - `flagCap = this.damageCap > 1 && this.geneDamcap` (line 738). When set and the attacker's item/mob is not on `damageCapBlackList*`, SRP computes `damage = maxHealth/damageCap + (maxHealth % damageCap)*0.5` (line 799) and **returns `super.func_70097_a(source, Math.min(amount, damage))`** (line 810). This clamp is applied to the raw `amount` **before** it reaches vanilla `super`, so `setDamageIsAbsolute()` / `setDamageBypassesArmor()` (which only affect vanilla's armor/absorption math inside `super`) **cannot lift the clamp**. A big absolute magic hit is still clamped to `maxHealth/damageCap`.
  - The **only** unconditional full-damage bypass in `func_70097_a` is `source == DamageSource.field_76380_i` (`OUT_OF_WORLD`, lines 682-684). SRP itself uses exactly this to force-kill a Beckon: `EntityPBeckon.func_70636_d` calls `this.func_70097_a(DamageSource.field_76380_i, 5000000.0f)` (`EntityPBeckon.java` line 34). This confirms the cap is real and OUT_OF_WORLD is the recognized escape hatch — but OUT_OF_WORLD misattributes the kill and prints "fell out of the world", so we do not use it.
  - **Chosen mechanism (spec fallback): direct `setHealth` reduction.** Non-lethal portion: `beckon.setHealth(hp - dmg)` + vanilla hurt feedback via `world.setEntityState(beckon, (byte) 2)` (byte 2 = "entity hurt": red flash + hurt sound; this is the same channel SRP itself uses in `attackEntityAsMobMinimum`, line 906). Lethal portion: set health to 1 then deliver a real `attackEntityFrom(RADIANT magic, Float.MAX_VALUE)` — from 1 HP even the cap-clamped result (`min(MAX, cap)`, and `cap >= ~1`) is lethal, so the caster gets proper kill credit, loot, COTH/kill-count handling and the correct death message. This is source-independent and therefore robust whether or not a given Beckon variant sets `damageCap > 1`.
  - **Damage formula (spec):** `dmg = 20 + (min(max(potency, 1.0), 1.2) − 1.0) × 300` → 20 at potency ≤ 1.0, 80 at potency ≥ 1.2, linear between.
- **Once-per-cast tracker (exact data structure & reset).** Server-side `WeakHashMap<EntityLivingBase caster, CastState>` in `util/RayBeckonPurge`. `CastState { int lastTicksInUse = Integer.MIN_VALUE; Set<Integer> procced = new HashSet<>(); }`. On each server-side Beckon hit for a caster:
  1. Fetch/create the caster's `CastState`.
  2. **Reset condition:** if `ticksInUse <= state.lastTicksInUse`, clear `state.procced` (a new cast has begun — continuous `ticksInUse` only rises within one channel).
  3. `state.lastTicksInUse = ticksInUse`.
  4. `return state.procced.add(beckon.getEntityId())` — `Set.add` returns true exactly once per Beckon id per cast → that is the proc trigger.
  Weak keys mean dead/unloaded casters are garbage-collected; no manual pruning needed. Java 8, no lambdas.
- **Existing late-mixin registration & remap.** `LateMixinBooter` (`ILateMixinLoader`, `@zone.rong.mixinbooter.MixinLoader`) queues `mixins.insanetweaks.late.json` **unconditionally**. That JSON's `mixins` array holds the common/server EBW+SRP mixins (`MixinSpell`, `MixinSpellMinion`, `MixinEntityParasiteBase`, …). EBW spell mixins use `@Mixin(value = ..., remap = false)` and `@Inject(method = "<ebwOwnName>", remap = false)` because EBW is a fg.deobf dependency whose own method names are stable (only MC methods get SRG-remapped). `onEntityHit` is EBW's own method → **`remap = false`**, name-only match (`method = "onEntityHit"`), which resolves uniquely under `defaultRequire = 1`. No refmap entry is needed for the EBW method name; the MC-typed parameters are handled by Mixin's descriptor resolution automatically. This exactly mirrors `MixinSpell`/`MixinSpellMinion`.
- **Module gate.** `ModConfig.modules.enableSpells` (`config/ModConfig.java` line 63, category `Modules`) is the flag that gates the whole SRP↔EBW spell bridge and all custom-spell registration. Other spell mixins are always loaded but this behaviour is new, so per spec 6 the injected code early-returns when `!ModConfig.modules.enableSpells`, leaving vanilla EBW behaviour intact.

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIFarming.java` | Modify | Task 1: center-based arrival (both checks), `WORK_RANGE_SQ` 4.0→6.25 hysteresis, `navTimer++` above the arrival short-circuit |
| `src/main/java/com/spege/insanetweaks/util/RayBeckonPurge.java` | Create | Task 2: server-side once-per-cast tracker + cap-bypassing damage application |
| `src/main/java/com/spege/insanetweaks/mixins/MixinRayOfPurification.java` | Create | Task 2: late mixin `@Inject` at HEAD of `RayOfPurification.onEntityHit`, orchestrates the proc |
| `src/main/resources/mixins.insanetweaks.late.json` | Modify | Task 2: add `"MixinRayOfPurification"` to the `mixins` array |
| `NEXT_SESSION_SPELLS.md` | Modify | Task 3: append the two deferred in-game checks from the spec's Verification section |

Not touched: EBW/SRP jars & spell JSONs, SRP config defaults, manifest `MixinConfigs`, `LateMixinBooter` (already queues `late.json` unconditionally), `SrpPurificationHelper` (reused), any thrall file other than `ThrallAIFarming.java`.

**Working-tree note:** the tree has two unrelated *deleted* files staged for removal by an earlier session — `CHANGES_THRALL_T3.md` and `NEXT_SESSION_THRALL.md`, plus an untracked `src/main/java/com/spege/insanetweaks/config/ReskillableConfigSwapper.java.new`. **Never `git add` any of these.** Every task commits only its own listed files by explicit path.

---

## Task 1: Farming — unify target distance to block center + hysteresis

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIFarming.java`

Fixes the NAVIGATING↔WORKING freeze. Three edits, all inside this file.

- [ ] **Step 1: Give `WORK_RANGE_SQ` hysteresis (drift radius strictly larger than arrival)**

Locate the two distance constants (currently both `4.0`). Replace:

```java
    /** Distance at which the thrall considers itself within reach to start working a target. */
    private static final double CLOSE_ENOUGH_SQ      = 4.0; // ~2 blocks
    private static final double WORK_RANGE_SQ        = 4.0;
```

with:

```java
    /** Distance at which the thrall considers itself within reach to start working a target. */
    private static final double CLOSE_ENOUGH_SQ      = 4.0;  // (2.0 blocks)^2 — arrival radius
    /**
     * WORKING drift radius. Strictly LARGER than {@link #CLOSE_ENOUGH_SQ} so that a thrall which has
     * just arrived (distance ≈ CLOSE_ENOUGH_SQ) cannot immediately trip the drift check and bounce
     * back to NAVIGATING. Equal radii + a corner-vs-center mismatch previously produced a permanent
     * NAVIGATING↔WORKING oscillation on diagonal approaches.
     */
    private static final double WORK_RANGE_SQ        = 6.25; // (2.5 blocks)^2 — drift radius
```

- [ ] **Step 2: Move `navTimer++` above the arrival short-circuit and make arrival center-based**

In `tickNavigating()`, locate the arrival block (immediately after the `stillValid` validation, ~lines 573-579):

```java
        double distSq = thrall.getDistanceSq(targetPos);
        if (distSq <= CLOSE_ENOUGH_SQ) {
            startWorking();
            return;
        }

        navTimer++;
```

Replace it with:

```java
        // Advance the nav timer BEFORE the arrival short-circuit so the teleport fallback keeps
        // counting during borderline approaches. Previously it sat below the `return`, so a thrall
        // oscillating on the reach boundary never accumulated navTimer and never teleported in.
        navTimer++;

        // Center-based distance, matching the WORKING drift check below. The old corner-based
        // getDistanceSq(BlockPos) measured to the block's integer corner, letting a −X/−Z diagonal
        // approach satisfy arrival while the center-based WORKING check failed the same tick —
        // a permanent NAVIGATING↔WORKING freeze. Both checks now use the block center.
        double distSq = thrall.getDistanceSq(
                targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        if (distSq <= CLOSE_ENOUGH_SQ) {
            startWorking();
            return;
        }
```

- [ ] **Step 3: Make the post-teleport recheck center-based too**

Still in `tickNavigating()`, inside the `navTimer >= NAV_TIMEOUT_TICKS` teleport block, locate the recheck (~line 598):

```java
            // Re-check distance; if we landed inside reach, transition to WORKING immediately.
            if (thrall.getDistanceSq(targetPos) <= CLOSE_ENOUGH_SQ) {
                startWorking();
            }
```

Replace it with (center-based, consistent with Step 2 and the WORKING drift check):

```java
            // Re-check distance; if we landed inside reach, transition to WORKING immediately.
            // Center-based to match the arrival + drift checks (avoids re-triggering the freeze
            // right after a teleport that lands on a diagonal).
            if (thrall.getDistanceSq(
                    targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5) <= CLOSE_ENOUGH_SQ) {
                startWorking();
            }
```

The WORKING drift check at line ~662 already uses the center-based form and threshold `WORK_RANGE_SQ` — with Step 1 raising that to 6.25 (> 4.0 arrival), arrival and drift can no longer ping-pong. No edit needed there.

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIFarming.java
git commit -m "fix: unify farming target distance to block center, add hysteresis"
```

---

## Task 2: Ray of Purification deals absolute first-hit damage to Beckons

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/util/RayBeckonPurge.java`
- Create: `src/main/java/com/spege/insanetweaks/mixins/MixinRayOfPurification.java`
- Modify: `src/main/resources/mixins.insanetweaks.late.json`

**Decision — mixin over event handler.** The proc needs `ticksInUse` (the continuous-cast session key for once-per-cast tracking) and the `caster` + `SpellModifiers` in one place. All three are arguments of `RayOfPurification.onEntityHit` and are unavailable from a `LivingHurtEvent` (which cannot see which cast/tick a hit belongs to, forcing fragile world-time-gap heuristics). The existing late-mixin infrastructure already injects into EBW spells with `remap = false` exactly like this, so a mixin on `onEntityHit` is both cleaner and the only place `ticksInUse` is exposed. Mixin chosen.

- [ ] **Step 1: Create the server-side tracker + damage helper**

Full file content — `src/main/java/com/spege/insanetweaks/util/RayBeckonPurge.java`:

```java
package com.spege.insanetweaks.util;

import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import electroblob.wizardry.util.MagicDamage;

/**
 * Server-side bookkeeping and cap-bypassing damage application for the Ray of Purification vs SRP
 * Beckon interaction (spec 2). All state and mutation here is server-authoritative; callers must
 * guard {@code world.isRemote} before invoking (the mixin does).
 *
 * <p><b>Once-per-cast:</b> a Beckon takes the extra hit the FIRST time it is struck during a single
 * continuous cast of the ray. {@code ticksInUse} rises by 1 per game tick within one channel and
 * resets toward 0 on release/re-cast, so a drop signals a fresh cast and clears the per-cast set.
 *
 * <p><b>Cap bypass:</b> SRP's {@code EntityParasiteBase.attackEntityFrom} clamps incoming damage to
 * {@code maxHealth/damageCap} via {@code Math.min(amount, cap)} regardless of an absolute /
 * armor-bypassing DamageSource, so a normal magic hit cannot land the full 20–80. We instead reduce
 * health directly and only route the killing blow through {@code attackEntityFrom} (from 1 HP, where
 * even a capped hit is lethal) so the caster still gets kill credit, loot and death handling.
 */
public final class RayBeckonPurge {

    /** Per-caster continuous-cast state. Weak keys → dead/unloaded casters are GC'd, no pruning. */
    private static final WeakHashMap<EntityLivingBase, CastState> CASTS =
            new WeakHashMap<EntityLivingBase, CastState>();

    private static final float BASE_DAMAGE  = 20.0F;
    private static final float POTENCY_FLOOR = 1.0F;
    private static final float POTENCY_CEIL  = 1.2F;
    private static final float POTENCY_SCALE = 300.0F;

    private RayBeckonPurge() {
    }

    private static final class CastState {
        int lastTicksInUse = Integer.MIN_VALUE;
        final Set<Integer> procced = new HashSet<Integer>();
    }

    /**
     * Records a Beckon hit for {@code caster} at {@code ticksInUse} and returns {@code true} exactly
     * once per continuous cast per Beckon. Server thread only.
     */
    public static boolean shouldProc(EntityLivingBase caster, Entity beckon, int ticksInUse) {
        CastState state = CASTS.get(caster);
        if (state == null) {
            state = new CastState();
            CASTS.put(caster, state);
        }
        if (ticksInUse <= state.lastTicksInUse) {
            // ticksInUse dropped → a new continuous cast started → reset this cast's proc set.
            state.procced.clear();
        }
        state.lastTicksInUse = ticksInUse;
        return state.procced.add(Integer.valueOf(beckon.getEntityId()));
    }

    /** Spec formula: 20 at potency ≤ 1.0, 80 at potency ≥ 1.2, linear between. */
    public static float purgeDamage(float potency) {
        float clamped = Math.max(POTENCY_FLOOR, Math.min(POTENCY_CEIL, potency));
        return BASE_DAMAGE + (clamped - POTENCY_FLOOR) * POTENCY_SCALE;
    }

    /**
     * Applies the unavoidable purge damage to {@code beckon}. Non-lethal portion is a direct
     * setHealth reduction with vanilla hurt feedback (entity-state byte 2 = "entity hurt"); the
     * lethal portion drops the Beckon to 1 HP and delivers a real RADIANT magic {@code
     * attackEntityFrom} so the {@code caster} gets kill credit, loot and correct death handling.
     * Server thread only.
     */
    public static void applyPurge(World world, EntityLivingBase caster, EntityLivingBase beckon, float potency) {
        if (world.isRemote) {
            return;
        }
        float dmg = purgeDamage(potency);
        float hp = beckon.getHealth();
        if (hp - dmg > 0.0F) {
            beckon.setHealth(hp - dmg);
            // Byte 2 = "entity hurt": red flash + hurt sound on clients. Same channel SRP itself
            // uses (EntityParasiteBase.attackEntityAsMobMinimum -> world.setEntityState(target,(byte)2)).
            world.setEntityState(beckon, (byte) 2);
        } else {
            // Drop to 1 HP so even the cap-clamped magic hit is guaranteed lethal, then route the
            // kill through vanilla for proper credit / loot / COTH handling.
            beckon.setHealth(1.0F);
            DamageSource src = MagicDamage.causeDirectMagicDamage(caster, MagicDamage.DamageType.RADIANT);
            beckon.attackEntityFrom(src, Float.MAX_VALUE);
        }
    }
}
```

- [ ] **Step 2: Create the mixin**

Full file content — `src/main/java/com/spege/insanetweaks/mixins/MixinRayOfPurification.java`:

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.RayBeckonPurge;
import com.spege.insanetweaks.util.SrpPurificationHelper;

import electroblob.wizardry.spell.RayOfPurification;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Ray of Purification vs SRP Beckons (spec 2).
 *
 * <p>EBW's own behaviour (RADIANT damage every 10 use-ticks + blindness) is left completely intact.
 * This injects at HEAD (non-cancellable) to ADD one unavoidable absolute-damage application the
 * FIRST time each Beckon is struck during a single continuous cast — see {@link RayBeckonPurge} for
 * the once-per-cast tracking and the SRP damage-cap bypass.
 *
 * <p>{@code remap = false}: {@code onEntityHit} is EBW's own (fg.deobf) method name, not an
 * SRG-mapped MC method — same convention as {@code MixinSpell} / {@code MixinSpellMinion}. The
 * name-only match resolves uniquely under {@code defaultRequire = 1} in late.json.
 */
@Mixin(value = RayOfPurification.class, remap = false)
public abstract class MixinRayOfPurification {

    @Inject(method = "onEntityHit", at = @At("HEAD"), remap = false)
    private void insanetweaks$purgeBeckonOnFirstHit(World world, Entity target, Vec3d hit,
            EntityLivingBase caster, Vec3d origin, int ticksInUse, SpellModifiers modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        // Module gate (spec 6): behave exactly like vanilla EBW when the spell bridge is off.
        if (!ModConfig.modules.enableSpells) {
            return;
        }
        // Server-authoritative only; a caster is required for the damage source + kill credit.
        if (world.isRemote || caster == null) {
            return;
        }
        if (!(target instanceof EntityLivingBase) || !SrpPurificationHelper.isBeckon(target)) {
            return;
        }
        // First hit on THIS Beckon during THIS cast?
        if (!RayBeckonPurge.shouldProc(caster, target, ticksInUse)) {
            return;
        }
        float potency = modifiers.get(SpellModifiers.POTENCY);
        RayBeckonPurge.applyPurge(world, caster, (EntityLivingBase) target, potency);
    }
}
```

Notes:
- **Not `cancellable`** — we never cancel `onEntityHit`; EBW's per-10-tick damage and blindness proceed normally. Our proc is purely additive.
- If our proc kills the Beckon, the remainder of EBW's `onEntityHit` runs harmlessly on a dead entity (`attackEntityFrom`/potion on a dead target is a no-op).

- [ ] **Step 3: Register the mixin in the late config**

In `src/main/resources/mixins.insanetweaks.late.json`, add `"MixinRayOfPurification"` to the `mixins` array (common list, matching `MixinSpell`/`MixinSpellMinion`; the mixin body guards `world.isRemote` so common placement is correct). Replace:

```json
  "mixins": [
    "MixinEntityParasiteBase",
    "MixinEntitySummonedCreature",
    "MixinParasiteEventEntity",
    "MixinSpell",
    "MixinSpellMinion",
    "MixinUnlockableReskillable"
  ],
```

with:

```json
  "mixins": [
    "MixinEntityParasiteBase",
    "MixinEntitySummonedCreature",
    "MixinParasiteEventEntity",
    "MixinRayOfPurification",
    "MixinSpell",
    "MixinSpellMinion",
    "MixinUnlockableReskillable"
  ],
```

(`LateMixinBooter` already queues `mixins.insanetweaks.late.json` unconditionally — no booter change. `required: false` in the JSON means a missing EBW target won't crash startup; with EBW present, `defaultRequire = 1` requires the single `onEntityHit` injection to bind.)

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. (EBW is on the compile classpath via CurseMaven fg.deobf, so `RayOfPurification`, `SpellModifiers`, `MagicDamage` resolve; `SrpPurificationHelper` and `ModConfig` are in-project.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/util/RayBeckonPurge.java src/main/java/com/spege/insanetweaks/mixins/MixinRayOfPurification.java src/main/resources/mixins.insanetweaks.late.json
git commit -m "feat: Ray of Purification deals absolute first-hit damage to Beckons"
```

---

## Task 3: Deferred-testing checklist

**Files:**
- Modify: `NEXT_SESSION_SPELLS.md`

- [ ] **Step 1: Append the two deferred in-game checks**

Add to the end of the existing deferred checklist in `NEXT_SESSION_SPELLS.md` (do NOT stage the two pre-existing deleted THRALL files or the untracked `*.java.new`):

```markdown

## Farming-freeze & Ray-vs-Beckon (plan `2026-07-07-farming-freeze-ray-beckon.md`) — deferred manual checklist

Build green after every task; in-game testing deferred by the user (2026-07-07). Verify in `runClient`:

- [ ] Farming no-freeze: a FARMING thrall approaches crop tiles from all four diagonals without a NAVIGATING↔WORKING flip-flop (enable Thrall debug logs and confirm no rapid state churn on the reach boundary); the nav-timeout teleport still fires when a tile is genuinely unreachable.
- [ ] Ray vs Beckon proc: channel Ray of Purification onto an SRP Beckon — it takes ONE extra hit per cast (~20 damage at potency 1.0, ~80 at potency ≥ 1.2); releasing and re-casting procs again; sweeping onto a second Beckon mid-cast gives that Beckon its own first-hit proc; non-Beckon targets (and normal per-10-tick RADIANT damage + blindness) are unaffected; with `Enable Spells = false` the ray behaves exactly like vanilla EBW.
```

- [ ] **Step 2: Commit**

```bash
git add NEXT_SESSION_SPELLS.md
git commit -m "docs: extend deferred checklist with farming-freeze and ray-beckon checks"
```

---

## Self-review results

**Spec coverage (every numbered requirement → task/step):**

| Spec item | Where |
|---|---|
| 1.1 NAVIGATING arrival uses center-based distance | Task 1 Step 2 (arrival at ~573) + Step 3 (post-teleport recheck at ~598) |
| 1.2 hysteresis: `CLOSE_ENOUGH_SQ` 4.0 (arrival) < `WORK_RANGE_SQ` 6.25 (drift) | Task 1 Step 1 |
| 1.3 `navTimer++` above the arrival check | Task 1 Step 2 |
| 1 "no other behavior changes; scan logic untouched" | Task 1 touches only the two distance checks + one constant; `searchAround`/home-fallback (F-1, already in tree) untouched |
| 2.1 trigger: once per cast per Beckon, first hit; re-cast re-procs; second Beckon its own proc | Task 2 Step 1 (`shouldProc` + `WeakHashMap<caster,CastState>`; reset on `ticksInUse <= last`; per-Beckon `Set.add`) |
| 2.2 damage `20 + (clamp(potency,1.0,1.2)−1.0)×300` | Task 2 Step 1 (`purgeDamage`) |
| 2.3 unavoidable — bypass armor AND SRP cap | Task 2 Step 1 (`applyPurge` via `setHealth` + 1-HP lethal `attackEntityFrom`); verdict in Key API facts (absolute source is clamped by `Math.min(amount, cap)`) |
| 2.4 hook = late mixin on `RayOfPurification.onEntityHit`; mixin-vs-event decided in planning | Task 2 Steps 2-3; decision recorded (mixin, because `ticksInUse` is only exposed there) |
| 2.5 normal ray behaviour on non-Beckons untouched; proc is ADDITIVE (blindness + per-10-tick damage continue) | Task 2 Step 2 (non-cancellable HEAD inject; early-returns for non-Beckons) |
| 2.6 gate behind spell module flag inside injected code | Task 2 Step 2 (`if (!ModConfig.modules.enableSpells) return;`) |
| "Must NOT change" (thrall invariants, EBW/SRP JSONs, SRP jars/config, manifest wiring) | Honored — only `late.json` `mixins` array gains one entry; manifest configs untouched |
| Verification checklist appended to NEXT_SESSION_SPELLS.md | Task 3 |

**Placeholder scan:** none. Both new files are shown in full; every edit shows exact before/after. No "TBD" / "similar to" / elided bodies.

**Type/signature consistency:**
- `MixinRayOfPurification.insanetweaks$purgeBeckonOnFirstHit` parameter list matches `RayOfPurification.onEntityHit` (`World, Entity, Vec3d, EntityLivingBase, Vec3d, int, SpellModifiers`) plus the trailing `CallbackInfoReturnable<Boolean>` — the return type is `boolean`, so `CallbackInfoReturnable<Boolean>` is correct.
- `RayBeckonPurge.shouldProc(EntityLivingBase, Entity, int) → boolean`, `purgeDamage(float) → float`, `applyPurge(World, EntityLivingBase, EntityLivingBase, float) → void` — all call sites in the mixin match (`target` is cast to `EntityLivingBase` only after the `instanceof` guard; `shouldProc` takes the raw `Entity target`).
- `SrpPurificationHelper.isBeckon(Entity)` — existing signature, called with `target` (Entity) before the cast. ✓
- `MagicDamage.causeDirectMagicDamage(Entity, MagicDamage.DamageType)` and `MagicDamage.DamageType.RADIANT` — verified in `MagicDamage.java` (lines 104, 173). ✓
- `SpellModifiers.POTENCY` (`"potency"`, `float` via `modifiers.get`) — verified. ✓
- `World.setEntityState(Entity, byte)`, `EntityLivingBase.getHealth()/setHealth(float)/attackEntityFrom(DamageSource, float)` — vanilla MC deobf names, available at dev compile time. ✓
- Task 1 constants `CLOSE_ENOUGH_SQ` / `WORK_RANGE_SQ` retain their names and `double` type; only the `WORK_RANGE_SQ` literal (4.0→6.25) and comments change. The WORKING drift check that reads `WORK_RANGE_SQ` is unchanged and already center-based.

**Task ordering / independence:**
- **Task 1 and Task 2 are fully independent** (disjoint files; farming AI vs EBW mixin/util/JSON). Either order is fine.
- **Within Task 2:** Step 1 (helper) before Step 2 (mixin references it) before Step 3 (JSON registers the mixin) before Step 4 (build binds the injection). Build only after all three source/JSON changes exist, or the `defaultRequire = 1` injection would fail / the helper symbol be missing.
- **Task 3 is last** (documents both landed commits). Each task commits only its own files by explicit path; the two staged-deleted THRALL files and the untracked `ReskillableConfigSwapper.java.new` are never staged.

**Resolved-in-planning decisions:**
1. *SRP cap verdict.* Confirmed against `EntityParasiteBase.func_70097_a` that `setDamageIsAbsolute()/setDamageBypassesArmor()` do NOT lift the `Math.min(amount, maxHealth/damageCap)` clamp — SRP applies it before delegating to vanilla `super`. Chose the spec's fallback (`setHealth` + 1-HP lethal `attackEntityFrom`), which is source-independent and works whether or not a Beckon variant sets `damageCap > 1`. OUT_OF_WORLD (the only full bypass, used by SRP itself in `EntityPBeckon`) was rejected — it misattributes the kill and prints the wrong death message.
2. *Mixin vs event handler.* Mixin, because once-per-cast tracking needs `ticksInUse`, which is an `onEntityHit` argument and is not reconstructable from `LivingHurtEvent`.
3. *Cast-reset condition.* `ticksInUse <= lastTicksInUse` (monotonic-increase-within-channel property). Documented as the exact reset trigger; `WeakHashMap` weak-keys handle caster GC so no manual pruning.
4. *Non-cancellable HEAD inject.* Keeps EBW's blindness + per-10-tick RADIANT damage fully intact (spec 2.5); the proc is strictly additive.
5. *Second arrival check (line ~598).* Not mentioned explicitly in the spec's three-point fix list but shares the identical corner-based defect; made center-based in the same task for consistency, preventing a re-freeze immediately after a nav-timeout teleport.
```
