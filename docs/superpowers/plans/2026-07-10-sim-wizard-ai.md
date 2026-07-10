# Sim Wizard Casting Diagnosis + Combat-AI Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Instrument the sim_wizard casting pipeline so the persistent "rarely casts, only magic_missile" bug is root-caused from runtime data, then consolidate cast/kite/melee into one state-machine task (pure caster, no SRP melee).

**Architecture:** E1 adds a config-gated diagnostic log layer (pool contents, shouldExecute rejections, pickSpell decisions, cast results, cadence). E2 replaces three competing tasks (`EntityAISimWizardCast` mutex 3 / `EntityAISimWizardKite` mutex 1 / SRP `EntityAIAttackMeleeStatus` mutex 3) with a single `EntityAISimWizardCombat` (priority 3, mutex 3) that owns movement AND casting, porting telegraph/channel/pickSpell logic 1:1.

**Tech Stack:** Forge 1.12.2, Java 8. No test suite — verification is `./gradlew build` + user playtest with debug logs (production instance).

**Spec:** `docs/superpowers/specs/2026-07-10-sim-wizard-ai-design.md`

**Facts already established (static analysis 2026-07-10 — do not re-check):**
- `Spell.get(String)` accepts namespaced ids (decompiled EB `Spell.java:489`).
- No SRP AI class overrides `isInterruptible` (grepped decompiled sources).
- `SpellProjectile.cast(NPC overload)` cannot return false with a non-null target.
- Attack targets come from SRP's `EntityAINearestAttackableTargetStatus` tasks inherited from `EntityPInfected`'s constructor (see `EntitySimWizard.initEntityAI` javadoc) — a "Status"-gated targeting layer is a prime remaining suspect; the logging below MUST record no-target rejections.
- Dev `run/` has no `insanetweaks.cfg`; the user playtests on a production instance whose config must be collected alongside logs.

---

### Task 1 (E1): Diagnostic logging layer

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/ClientCategory.java`
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntitySimWizard.java`
- Modify: `src/main/java/com/spege/insanetweaks/entities/ai/EntityAISimWizardCast.java`

- [ ] **Step 1: Config flag**

Append to `ClientCategory`:

```java
    @Config.Comment({
            "Verbose diagnostic logging for the Sim Wizard casting pipeline: spell pool contents,",
            "target/cooldown gate rejections (throttled), spell picks and cast results.",
            "Turn on to diagnose 'wizard is not casting' reports, then send the log to the mod author." })
    @Config.Name("Sim Wizard Debug Logs")
    public boolean enableSimWizardDebugLogs = false;
```

- [ ] **Step 2: Log the built pool**

In `EntitySimWizard.ensureSpellPool()` (line ~266), at the very END of the method (after the fallback-fill logic, when `this.spells` is final), add:

```java
        if (ModConfig.client.enableSimWizardDebugLogs) {
            StringBuilder sb = new StringBuilder();
            for (Spell s : this.spells) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(s.getRegistryName());
            }
            InsaneTweaksMod.LOGGER.info("[InsaneTweaks][SimWizard#{}] spell pool built ({}): [{}]",
                    this.getEntityId(), this.spells.size(), sb);
        }
```

(Import `InsaneTweaksMod` if not already imported.)

- [ ] **Step 3: Log gates, picks and results in the cast task**

In `EntityAISimWizardCast` add fields + helper:

```java
    /** E1 diagnostics: last world time a rejection reason was logged (1 line/sec throttle). */
    private long lastRejectLogTime;
    private long lastSuccessfulCastTime;

    private void logReject(String reason) {
        if (!ModConfig.client.enableSimWizardDebugLogs) return;
        long now = this.wizard.world.getTotalWorldTime();
        if (now - this.lastRejectLogTime < 20L) return;
        this.lastRejectLogTime = now;
        com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks][SimWizard#{}] cast gate: {}", this.wizard.getEntityId(), reason);
    }
```

Rewrite `shouldExecute()` (line ~97) to name its rejection:

```java
    @Override
    public boolean shouldExecute() {
        if (!isOffCooldown()) {
            logReject("cooldown (" + (this.nextCastReadyTime - this.wizard.world.getTotalWorldTime()) + "t left)");
            return false;
        }
        EntityLivingBase target = this.wizard.getAttackTarget();
        if (target == null) {
            logReject("no attack target");
            return false;
        }
        if (!isValidSpellTarget(target, this.wizard)) {
            logReject("target invalid: " + target.getName());
            return false;
        }
        if (this.wizard.getDistanceSq(target) > this.decisionRangeSq) {
            logReject("target out of decision range (" + String.format("%.1f", this.wizard.getDistance(target)) + " blocks)");
            return false;
        }
        return true;
    }
```

In `updateTask()`, right after `Spell spell = pickSpell(target);` (line ~181) add:

```java
        if (ModConfig.client.enableSimWizardDebugLogs) {
            com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks][SimWizard#{}] pickSpell -> {} (dist {})",
                    this.wizard.getEntityId(),
                    spell == null ? "null" : spell.getRegistryName(),
                    String.format("%.1f", this.wizard.getDistance(target)));
        }
```

In `fireCommittedCast()`, right after `boolean cast = spell.cast(...)` (line ~238) add:

```java
        if (ModConfig.client.enableSimWizardDebugLogs) {
            long now = this.wizard.world.getTotalWorldTime();
            com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks][SimWizard#{}] cast {} -> {}{}",
                    this.wizard.getEntityId(), spell.getRegistryName(), cast,
                    cast && this.lastSuccessfulCastTime > 0
                            ? " (" + (now - this.lastSuccessfulCastTime) + "t since previous)" : "");
            if (cast) this.lastSuccessfulCastTime = now;
        }
```

- [ ] **Step 4: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java
git commit -m "feat: sim wizard diagnostic logging (E1) behind enableSimWizardDebugLogs"
```

- [ ] **Step 5: Playtest data request (user action — do not block on it)**

Ask the user to run their production instance with `enableSimWizardDebugLogs=true`, fight a sim_wizard for a few minutes, then provide `logs/fml-client-latest.log` (or latest.log) AND their `config/insanetweaks.cfg`. Analysis of the dominant rejection reason (cooldown vs no-target vs invalid vs out-of-range) plus the pool line identifies the root cause. **Record the finding in `notes/sim_wizard_v1.md` (new v4 section) — acceptance criterion 1 forbids "fixed by rewrite, cause unknown".** E2 proceeds regardless.

### Task 2 (E2): EntityAISimWizardCombat — consolidated state machine

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/entities/ai/EntityAISimWizardCombat.java`

- [ ] **Step 1: Create the class**

This is a PORT, not a redesign: telegraph, channel, cooldown, pickSpell and all helpers are copied from `EntityAISimWizardCast` verbatim unless noted. New: the task stays active whenever a valid target exists (even during cast cooldown) and runs kite-style movement in the gaps (constants from the retired `EntityAISimWizardKite`: retreat < 7, approach > 18, repath every 10t).

```java
package com.spege.insanetweaks.entities.ai;

import java.util.ArrayList;
import java.util.List;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntitySimWizard;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;

/**
 * v4 (spec 2026-07-10): the ONE combat task. Replaces EntityAISimWizardCast +
 * EntityAISimWizardKite + the SRP EntityAIAttackMeleeStatus fallback. Owns both combat
 * movement and casting under a single mutex, eliminating the multi-task interplay that
 * plagued v3.x ("rarely casts" class of bugs). The wizard is a pure caster — banish is
 * its close-quarters answer, there is no melee.
 *
 * States (implicit, per tick):
 *   CHANNEL   channelTicksLeft > 0   -> tickChannel(), stationary
 *   TELEGRAPH telegraphTicksLeft > 0 -> countdown, stationary, then fireCommittedCast()
 *   READY     cooldown elapsed       -> pickSpell + start telegraph (or fire instantly)
 *   HOLD      cooldown pending       -> kite movement: retreat <7, approach >18, else stand
 */
public class EntityAISimWizardCombat extends EntityAIBase {

    private static final int MIN_RETRY_COOLDOWN = 5;
    private static final int FAILED_CAST_COOLDOWN = 10;
    private static final int EVENT_BLOCK_COOLDOWN = 20;
    private static final int CAST_ANIMATION_TICKS = 14;
    private static final int POST_CAST_SLOWNESS_TICKS = 20;
    private static final int POST_CAST_SLOWNESS_AMPLIFIER = 1;
    private static final int CHANNEL_DURATION_TICKS = 40;
    private static final double BANISH_MAX_DISTANCE = 4.5D;
    private static final double LIFE_DRAIN_MAX_DISTANCE = 9.0D;

    // Movement band (from the retired EntityAISimWizardKite)
    private static final double RETREAT_DISTANCE = 7.0D;
    private static final double APPROACH_DISTANCE = 18.0D;
    private static final int REPATH_INTERVAL = 10;
    private static final double MOVE_SPEED = 1.0D;
    private static final double RETREAT_SPEED = 1.25D;

    private final EntitySimWizard wizard;
    private final double decisionRange;
    private final double decisionRangeSq;

    private long nextCastReadyTime;
    private int telegraphTicksLeft;
    private Spell pendingSpell;
    private EntityLivingBase pendingTarget;
    private int channelTicksLeft;
    private Spell channelSpell;
    private EntityLivingBase channelTarget;
    private int channelCounter;
    private int repathTimer;

    // E1 diagnostics
    private long lastRejectLogTime;
    private long lastSuccessfulCastTime;

    public EntityAISimWizardCombat(EntitySimWizard wizard) {
        this.wizard = wizard;
        this.decisionRange = ModConfig.entities.assimilatedWizard.combat.decisionRange
                * ModConfig.entities.assimilatedWizard.combat.rangeMultiplier;
        this.decisionRangeSq = this.decisionRange * this.decisionRange;
        this.setMutexBits(3);
    }

    private boolean isOffCooldown() {
        return this.wizard.world.getTotalWorldTime() >= this.nextCastReadyTime;
    }

    private void setCooldown(int ticks) {
        this.nextCastReadyTime = this.wizard.world.getTotalWorldTime() + Math.max(0, ticks);
    }

    private void logDiag(String message) {
        if (!ModConfig.client.enableSimWizardDebugLogs) return;
        com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks][SimWizard#{}] {}", this.wizard.getEntityId(), message);
    }

    private void logRejectThrottled(String reason) {
        if (!ModConfig.client.enableSimWizardDebugLogs) return;
        long now = this.wizard.world.getTotalWorldTime();
        if (now - this.lastRejectLogTime < 20L) return;
        this.lastRejectLogTime = now;
        logDiag("gate: " + reason);
    }

    // ------------------------------------------------------------------
    // Task lifecycle — active whenever a valid target exists (movement included)
    // ------------------------------------------------------------------

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = this.wizard.getAttackTarget();
        if (target == null) {
            logRejectThrottled("no attack target");
            return false;
        }
        if (!isValidSpellTarget(target, this.wizard)) {
            logRejectThrottled("target invalid: " + target.getName());
            return false;
        }
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (this.telegraphTicksLeft > 0 || this.channelTicksLeft > 0) {
            return true;
        }
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        this.telegraphTicksLeft = 0;
        this.pendingSpell = null;
        this.pendingTarget = null;
        this.repathTimer = 0;
    }

    @Override
    public void resetTask() {
        long floor = this.wizard.world.getTotalWorldTime() + MIN_RETRY_COOLDOWN;
        if (this.nextCastReadyTime < floor) {
            this.nextCastReadyTime = floor;
        }
        this.telegraphTicksLeft = 0;
        this.pendingSpell = null;
        this.pendingTarget = null;
        this.wizard.getNavigator().clearPath();
        if (this.channelTicksLeft > 0 || this.channelSpell != null) {
            this.endChannel(false);
        }
    }

    @Override
    public void updateTask() {
        if (this.channelTicksLeft > 0) {
            this.tickChannel();
            return;
        }

        if (this.telegraphTicksLeft > 0) {
            if (this.pendingTarget != null && this.pendingTarget.isEntityAlive()) {
                this.wizard.getLookHelper().setLookPositionWithEntity(this.pendingTarget, 30.0F, 30.0F);
            }
            this.telegraphTicksLeft--;
            if (this.telegraphTicksLeft == 0) {
                this.fireCommittedCast();
            }
            return;
        }

        EntityLivingBase target = this.wizard.getAttackTarget();
        if (target == null || !isValidSpellTarget(target, this.wizard)) {
            return; // shouldContinueExecuting ends the task next poll
        }

        this.wizard.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);

        double distance = this.wizard.getDistance(target);

        // READY: begin a cast if the target is inside decision range.
        if (isOffCooldown() && distance * distance <= this.decisionRangeSq) {
            this.beginCast(target, distance);
            return;
        }

        // HOLD: kite movement while waiting for cooldown / closing distance.
        this.tickMovement(target, distance);
    }

    private void beginCast(EntityLivingBase target, double distance) {
        this.wizard.getNavigator().clearPath();

        Spell spell = pickSpell(target);
        logDiag("pickSpell -> " + (spell == null ? "null" : String.valueOf(spell.getRegistryName()))
                + " (dist " + String.format("%.1f", distance) + ")");
        if (spell == null || spell == Spells.none) {
            setCooldown(FAILED_CAST_COOLDOWN);
            return;
        }

        int telegraph = Math.max(0, ModConfig.entities.assimilatedWizard.combat.castTelegraphTicks);
        this.pendingSpell = spell;
        this.pendingTarget = target;
        if (telegraph == 0) {
            this.fireCommittedCast();
            return;
        }
        this.telegraphTicksLeft = telegraph;
        this.wizard.signalCastTelegraph(telegraph);
    }

    private void tickMovement(EntityLivingBase target, double distance) {
        if (--this.repathTimer > 0) return;
        this.repathTimer = REPATH_INTERVAL;

        if (distance < RETREAT_DISTANCE) {
            Vec3d away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(
                    this.wizard, 8, 5, new Vec3d(target.posX, target.posY, target.posZ));
            if (away != null) {
                this.wizard.getNavigator().tryMoveToXYZ(away.x, away.y, away.z, RETREAT_SPEED);
            }
        } else if (distance > APPROACH_DISTANCE) {
            this.wizard.getNavigator().tryMoveToEntityLiving(target, MOVE_SPEED);
        } else {
            this.wizard.getNavigator().clearPath();
        }
    }
```

…then append, copied VERBATIM from `EntityAISimWizardCast` (current file), the following members, unchanged: `fireCommittedCast()` (with its E1 cast-result logging from Task 1 — replace the `com.spege...LOGGER.info` block with a call to `logDiag(...)` building the same message), `applySpellCooldown(Spell)`, `tickChannel()`, `endChannel(boolean)`, `pickSpell(EntityLivingBase)`, `countTargetsInFrontCone(double, double)`, `isTargetFastMoving(EntityLivingBase)`, `isHealSpell(Spell)`, `findByName(List, String)`, `addIfPresent(List, List, String)`, `collectSummons(List, List)`, and `isValidSpellTarget(EntityLivingBase, EntitySimWizard)` — and close the class.

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (class not yet wired)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/entities/ai/EntityAISimWizardCombat.java
git commit -m "feat: EntityAISimWizardCombat consolidated caster state machine (E2)"
```

### Task 3 (E2): Wire in, retire the old tasks

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntitySimWizard.java:107-141` (initEntityAI)
- Delete: `src/main/java/com/spege/insanetweaks/entities/ai/EntityAISimWizardCast.java`
- Delete: `src/main/java/com/spege/insanetweaks/entities/ai/EntityAISimWizardKite.java`
- Modify: `notes/sim_wizard_v1.md`

- [ ] **Step 1: Replace the task trio in initEntityAI**

In `EntitySimWizard.initEntityAI()` replace these three lines (current lines 126, 133, 134):

```java
        this.tasks.addTask(3, new EntityAISimWizardCast(this));
        this.tasks.addTask(4, new com.spege.insanetweaks.entities.ai.EntityAISimWizardKite(this, 1.0D));
        this.tasks.addTask(5, new EntityAIAttackMeleeStatus(this, 1.2D, false, 0.0D));
```

with:

```java
        // v4: single combat task owns movement AND casting (mutex 3). No melee — the
        // wizard is a pure caster; banish handles close quarters (spec 2026-07-10).
        this.tasks.addTask(3, new com.spege.insanetweaks.entities.ai.EntityAISimWizardCombat(this));
```

Remove the now-unused `EntityAIAttackMeleeStatus` import (if it was imported rather than fully qualified) and the v3.2 kite comment block (lines 128-132). Update the class javadoc lines 54-56 to describe the v4 single-task layout. Keep `EntityAICircleGroup` (6), `EntityAIGetFollowers` (7), `EntityAILookIdle` (8) untouched — do NOT renumber.

- [ ] **Step 2: Delete the retired tasks**

```bash
git rm src/main/java/com/spege/insanetweaks/entities/ai/EntityAISimWizardCast.java src/main/java/com/spege/insanetweaks/entities/ai/EntityAISimWizardKite.java
```

Then run: `grep -rn "EntityAISimWizardCast\|EntityAISimWizardKite" src/main/java` — the only permitted match is `EntityAISimWizardCombat.java` if a javadoc mentions them historically; fix any code reference (note: `EntityAISimWizardCast.isValidSpellTarget` was public static — the copy now lives in `EntityAISimWizardCombat`; update any external caller, expected none besides the deleted kite).

- [ ] **Step 3: Notes update**

Append a `## v4 — Combat consolidation (2026-07-10)` section to `notes/sim_wizard_v1.md` stating: cast+kite+SRP-melee replaced by `EntityAISimWizardCombat` (priority 3, mutex 3, movement+cast in one task, no melee), diagnostic logging behind `client.enableSimWizardDebugLogs`, and — once the playtest data is in — the named root cause of the "rarely casts / only magic_missile" symptom.

- [ ] **Step 4: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java notes/sim_wizard_v1.md
git commit -m "feat!: sim wizard v4 - single combat state machine, melee removed"
```

### Task 4: Manual verification (runClient + user production playtest)

- [ ] `/summon insanetweaks:sim_wizard` near a villager/player with debug logs ON: log shows pool of 7+ spells; wizard casts within ~2.5-6.5 s cadence; at least 4 distinct spells over a few minutes; telegraph ring visible before each cast.
- [ ] Wizard never runs into melee range voluntarily: stays in the 7-18 band, retreats when approached, banishes when cornered; NEVER claw-attacks.
- [ ] `gate:` log lines: confirm which rejection dominates in the user's production environment and write the root cause into `notes/sim_wizard_v1.md` v4.
- [ ] No parasite-vs-wizard friendly fire (spawn other parasites nearby, let AoE fly).
- [ ] Channeled life_drain still ends cleanly when the target dies mid-channel (no looping glow).
