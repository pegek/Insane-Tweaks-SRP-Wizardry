package com.spege.insanetweaks.entities.ai;

import java.util.ArrayList;
import java.util.List;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntitySimWizard;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.spell.SpellBuff;
import electroblob.wizardry.spell.SpellMinion;
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
 *   HOLD      cooldown pending       -> kite movement: retreat / approach / stand, and close in
 *                                       unconditionally while line of sight is broken
 *
 * <p>Spell choice goes through {@link SpellRole} / {@link SpellRoleResolver} rather than registry
 * names, and every distance, cooldown and threshold below comes from
 * {@code ModConfig.entities.assimilatedWizard.tuning}, read live on use.
 *
 * Diagnostics: with {@code client.enableSimWizardDebugLogs} on, every gate rejection
 * (throttled), spell pick and cast result is logged — see spec E1.
 */
public class EntityAISimWizardCombat extends EntityAIBase {

    private final EntitySimWizard wizard;
    private final double decisionRange;
    private final double decisionRangeSq;

    /** Telegraph countdown. > 0 = charging up, 0 = ready to fire. */
    private int telegraphTicksLeft;
    /** Spell chosen at start of telegraph - reused when the firing tick lands. */
    private Spell pendingSpell;
    /** Target locked at start of telegraph. */
    private EntityLivingBase pendingTarget;

    /** Channel countdown for continuous spells. > 0 = actively channeling. */
    private int channelTicksLeft;
    private Spell channelSpell;
    private EntityLivingBase channelTarget;
    /** ticksInUse counter passed to the continuous spell's per-tick cast. */
    private int channelCounter;

    private int repathTimer;

    /**
     * Absolute world time at which the target was last actually visible. Deliberately a
     * TIMESTAMP, not a countdown: {@code shouldExecute} is polled on the AI tick rate (every 3
     * ticks), so any counter decremented outside {@code updateTask} runs at the wrong speed -
     * the v3.1 "cooldowns were silently tripled" bug. Seeded far in the past so the grace window
     * cannot spuriously pass before the target has ever been seen.
     */
    private long lastSeenTime = Long.MIN_VALUE / 2;

    /** Health sampled last tick; negative until the first sample. Drives the panic trigger. */
    private float lastHealth = -1.0F;
    /** World time of the last blow large enough to count as heavy. */
    private long lastHeavyDamageTime = Long.MIN_VALUE / 2;
    /** World time before which no further panic reaction may fire. */
    private long nextPanicReadyTime;

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

    /**
     * Live view of the tuning values. Deliberately NOT cached in a field: caching would silently
     * break the "read live, no restart" promise made by every comment in that config category.
     */
    private static com.spege.insanetweaks.config.categories.EntitiesCategory.Tuning tuning() {
        return ModConfig.entities.assimilatedWizard.tuning;
    }

    /**
     * Approach distance, clamped to stay above the retreat distance. Forge's {@code @Config} has
     * no cross-field validation, so a user who sets approach below retreat would otherwise create
     * a band where the wizard is told to advance and withdraw on alternating repaths.
     */
    private static double approachDistance() {
        return Math.max(tuning().approachDistance, tuning().retreatDistance + 1.0D);
    }

    // The cast gate lives on the ENTITY, not here: the ally-support task deliberately runs on a
    // non-overlapping mutex, so this shared gate is the only thing stopping the two from casting
    // in the same tick.

    private boolean isOffCooldown() {
        return this.wizard.isCastGateReady();
    }

    private void setCooldown(int ticks) {
        this.wizard.setCastGate(ticks);
    }

    private void logDiag(String message) {
        if (!ModConfig.client.enableSimWizardDebugLogs) return;
        com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks][SimWizard#{}] {}", this.wizard.getEntityId(), message);
    }

    // ------------------------------------------------------------------
    // Line of sight
    // ------------------------------------------------------------------

    /**
     * Whether this spell is aimed at something other than the caster and therefore needs a clear
     * shot. Buffs apply to the caster and minions are spawned beside it, so neither cares about
     * terrain between wizard and target.
     */
    private static boolean requiresLineOfSight(Spell spell) {
        return !(spell instanceof SpellBuff) && !(spell instanceof SpellMinion);
    }

    /**
     * Refreshes the line-of-sight memory and reports current visibility. Uses
     * {@code EntitySenses.canSee}, which caches its ray trace for the rest of the tick - calling
     * this several times per tick is cheap, a raw {@code world.rayTraceBlocks} would not be.
     */
    private boolean canSeeNow(EntityLivingBase target) {
        if (target == null) {
            return false;
        }
        if (this.wizard.getEntitySenses().canSee(target)) {
            this.lastSeenTime = this.wizard.world.getTotalWorldTime();
            return true;
        }
        return false;
    }

    /** True when the target is visible now, or was within the configured grace window. */
    private boolean hasRecentLineOfSight(EntityLivingBase target) {
        if (!ModConfig.entities.assimilatedWizard.combat.requireLineOfSight) {
            return true;
        }
        if (canSeeNow(target)) {
            return true;
        }
        long grace = Math.max(0, ModConfig.entities.assimilatedWizard.combat.lineOfSightGraceTicks);
        return this.wizard.world.getTotalWorldTime() - this.lastSeenTime <= grace;
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
        // Stay active while telegraph is winding down or a channel is running (commitment).
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
        // Sight memory is per-engagement - carrying it into a fight with a NEW target would hand
        // out a free grace window against someone never actually seen.
        this.lastSeenTime = Long.MIN_VALUE / 2;
    }

    @Override
    public void resetTask() {
        // setCastGate never shortens, so this only guarantees a small floor.
        setCooldown(tuning().minRetryCooldownTicks);
        this.telegraphTicksLeft = 0;
        this.pendingSpell = null;
        this.pendingTarget = null;
        this.lastSeenTime = Long.MIN_VALUE / 2;
        // CRITICAL: clear the commitment flag. An interrupted telegraph that leaves it set would
        // block the ally-support task forever - the same shape as the v3.3 bug where an
        // interrupted channel left the continuous-spell visual looping.
        this.wizard.setCastCommitted(false);
        this.wizard.getNavigator().clearPath();
        // CRITICAL: always end an in-flight channel here, or the continuous-spell visual
        // (getContinuousSpell drives isCastingSpellVisual) loops forever after interruption.
        if (this.channelTicksLeft > 0 || this.channelSpell != null) {
            this.endChannel(false);
        }
    }

    @Override
    public void updateTask() {
        this.trackHealthDrop();

        // EMERGENCY: highest-priority reaction, ahead of everything else so it can abort an
        // in-flight telegraph or channel. Deliberately a STATE here rather than its own AI task:
        // a task at priority 4 or 5 sharing this one's mutex bits could never preempt it, because
        // in 1.12.2 only a LOWER-numbered task interrupts a running one - it would be dead code.
        if (this.shouldPanic()) {
            this.firePanic();
            return;
        }

        // CHANNEL: continuous spell in progress (life_drain) - cast every tick.
        if (this.channelTicksLeft > 0) {
            this.tickChannel();
            return;
        }

        // TELEGRAPH: keep looking at the target and tick down.
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
            return; // shouldContinueExecuting ends the task on the next poll
        }

        this.wizard.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);

        double distance = this.wizard.getDistance(target);

        // READY: begin a cast if the cooldown elapsed and the target is in decision range.
        if (isOffCooldown() && distance * distance <= this.decisionRangeSq && this.beginCast(target, distance)) {
            return;
        }

        // HOLD: kite movement while waiting for cooldown / closing distance. Also reached when
        // beginCast refused for lack of line of sight, so the wizard repositions instead of
        // standing behind cover doing nothing.
        this.tickMovement(target, distance);
    }

    // ------------------------------------------------------------------
    // EMERGENCY reaction
    // ------------------------------------------------------------------

    /** Whichever target the wizard is currently committed to, or its plain attack target. */
    private EntityLivingBase currentTarget() {
        if (this.channelTicksLeft > 0 && this.channelTarget != null) {
            return this.channelTarget;
        }
        if (this.telegraphTicksLeft > 0 && this.pendingTarget != null) {
            return this.pendingTarget;
        }
        return this.wizard.getAttackTarget();
    }

    /**
     * Samples health once per tick and remembers when a heavy blow landed. A timestamp, not a
     * countdown, for the same reason as {@link #lastSeenTime}.
     */
    private void trackHealthDrop() {
        float health = this.wizard.getHealth();
        if (this.lastHealth < 0.0F) {
            this.lastHealth = health;
            return;
        }
        float drop = this.lastHealth - health;
        this.lastHealth = health;

        float max = this.wizard.getMaxHealth();
        if (max > 0.0F && drop >= max * tuning().panicDamageFraction) {
            this.lastHeavyDamageTime = this.wizard.world.getTotalWorldTime();
        }
    }

    private boolean shouldPanic() {
        if (!tuning().enablePanicReaction) {
            return false;
        }
        long now = this.wizard.world.getTotalWorldTime();
        if (now < this.nextPanicReadyTime) {
            return false;
        }
        if (now - this.lastHeavyDamageTime <= tuning().panicWindowTicks) {
            return true;
        }
        EntityLivingBase target = this.currentTarget();
        return target != null && isValidSpellTarget(target, this.wizard)
                && this.wizard.getDistance(target) <= tuning().panicDistance;
    }

    /**
     * Aborts whatever was in flight and answers with an ESCAPE spell, or a DISPLACE spell when the
     * pool has no escape. Cast WITHOUT a telegraph - a reaction that telegraphs is not a reaction.
     *
     * <p>Even when the pool offers neither, the in-flight telegraph is still cancelled: being
     * interrupted has to cost the wind-up, otherwise a heavy hit would be free.
     */
    private void firePanic() {
        EntityLivingBase target = this.currentTarget();

        if (this.channelTicksLeft > 0 || this.channelSpell != null) {
            this.endChannel(true);
        }
        this.telegraphTicksLeft = 0;
        this.pendingSpell = null;
        this.pendingTarget = null;
        this.wizard.setCastCommitted(false);

        // Arm the panic cooldown unconditionally so a wizard with no answer cannot spend every
        // tick cancelling its own casts.
        this.nextPanicReadyTime = this.wizard.world.getTotalWorldTime() + tuning().panicCooldownTicks;

        List<Spell> pool = this.wizard.getSpells();
        Spell escape = pickByRoles(pool, SpellRole.ESCAPE);
        boolean selfMove = escape != null;
        if (escape == null) {
            escape = pickByRoles(pool, SpellRole.DISPLACE);
        }
        if (escape == null) {
            logDiag("panic: nothing to answer with, telegraph aborted");
            setCooldown(tuning().failedCastCooldownTicks);
            return;
        }

        // ESCAPE moves the caster, DISPLACE shoves the target - so they aim at different entities.
        EntityLivingBase castTarget = selfMove ? this.wizard : target;
        if (castTarget == null) {
            setCooldown(tuning().failedCastCooldownTicks);
            return;
        }

        logDiag("panic -> " + escape.getRegistryName());
        SimWizardCastPipeline.Outcome outcome =
                SimWizardCastPipeline.fire(this.wizard, escape, castTarget, this.wizard.getModifiers());
        if (outcome != SimWizardCastPipeline.Outcome.CAST) {
            setCooldown(tuning().failedCastCooldownTicks);
            return;
        }
        this.wizard.signalCastBurst(tuning().castAnimationTicks);
        applySpellCooldown(escape);
    }

    /**
     * @return true when a cast (or its telegraph) was actually started. False means the caller
     *         should fall through to movement this tick.
     */
    private boolean beginCast(EntityLivingBase target, double distance) {
        Spell spell = pickSpell(target);
        logDiag("pickSpell -> " + (spell == null ? "null" : String.valueOf(spell.getRegistryName()))
                + " (dist " + String.format("%.1f", distance) + ")");
        if (spell == null || spell == Spells.none) {
            setCooldown(tuning().failedCastCooldownTicks);
            return false;
        }

        if (requiresLineOfSight(spell) && !hasRecentLineOfSight(target)) {
            // Short retry, NOT the full failure cooldown - the moment the wizard walks around the
            // obstacle it should fire, otherwise cover would function as a cast-rate nerf.
            logRejectThrottled("no line of sight for " + spell.getRegistryName());
            setCooldown(tuning().minRetryCooldownTicks);
            return false;
        }

        this.wizard.getNavigator().clearPath();

        int telegraph = Math.max(0, ModConfig.entities.assimilatedWizard.combat.castTelegraphTicks);
        this.pendingSpell = spell;
        this.pendingTarget = target;
        if (telegraph == 0) {
            this.fireCommittedCast();
            return true;
        }
        this.telegraphTicksLeft = telegraph;
        this.wizard.setCastCommitted(true);
        this.wizard.signalCastTelegraph(telegraph);
        return true;
    }

    private void tickMovement(EntityLivingBase target, double distance) {
        if (--this.repathTimer > 0) return;
        this.repathTimer = tuning().repathIntervalTicks;

        // No line of sight: close in regardless of the hold band. Sitting in the sweet spot behind
        // a wall reads as a broken turret; moving to regain the shot reads as intent - and it costs
        // nothing, because the senses ray trace for this tick is already cached.
        if (ModConfig.entities.assimilatedWizard.combat.requireLineOfSight && !canSeeNow(target)) {
            this.wizard.getNavigator().tryMoveToEntityLiving(target, tuning().moveSpeed);
            return;
        }

        if (distance < tuning().retreatDistance) {
            Vec3d away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(
                    this.wizard, 8, 5, new Vec3d(target.posX, target.posY, target.posZ));
            if (away != null) {
                this.wizard.getNavigator().tryMoveToXYZ(away.x, away.y, away.z, tuning().retreatSpeed);
            }
        } else if (distance > approachDistance()) {
            this.wizard.getNavigator().tryMoveToEntityLiving(target, tuning().moveSpeed);
        } else {
            this.wizard.getNavigator().clearPath();
        }
    }

    /**
     * Final spell resolution. Called either directly (telegraph disabled) or when the
     * telegraph countdown reaches zero. Re-validates target liveness because the player
     * may have killed or moved out of range during the wind-up.
     */
    private void fireCommittedCast() {
        Spell spell = this.pendingSpell;
        EntityLivingBase target = this.pendingTarget;
        this.pendingSpell = null;
        this.pendingTarget = null;

        if (spell == null || spell == Spells.none) {
            this.wizard.setCastCommitted(false);
            setCooldown(tuning().failedCastCooldownTicks);
            return;
        }

        // Self-targeted spells. SpellBuff's NPC overload calls applyEffects(caster, modifiers) and
        // DISCARDS the target argument entirely, so every SpellBuff is self-only - heal today,
        // ironflesh/oakflesh/etc. the moment someone adds one to the pool. Gating those on a
        // target's validity and range would reject casts that never needed a target.
        boolean isSelfTargeted = spell instanceof SpellBuff;
        EntityLivingBase castTarget = isSelfTargeted ? this.wizard : target;

        if (!isSelfTargeted) {
            if (!isValidSpellTarget(castTarget, this.wizard)
                    || this.wizard.getDistanceSq(castTarget) > this.decisionRangeSq) {
                // Telegraph "wasted" - the target left during wind-up. Short cooldown only.
                logDiag("fire dropped: target left during telegraph (" + spell.getRegistryName() + ")");
                this.wizard.setCastCommitted(false);
                setCooldown(tuning().failedCastCooldownTicks);
                return;
            }
            if (requiresLineOfSight(spell) && !hasRecentLineOfSight(castTarget)) {
                logDiag("fire dropped: lost line of sight during telegraph (" + spell.getRegistryName() + ")");
                this.wizard.setCastCommitted(false);
                setCooldown(tuning().failedCastCooldownTicks);
                return;
            }
        }

        SpellModifiers modifiers = this.wizard.getModifiers();

        SimWizardCastPipeline.Outcome outcome =
                SimWizardCastPipeline.fire(this.wizard, spell, castTarget, modifiers);

        if (ModConfig.client.enableSimWizardDebugLogs) {
            long now = this.wizard.world.getTotalWorldTime();
            boolean cast = outcome == SimWizardCastPipeline.Outcome.CAST;
            logDiag("cast " + spell.getRegistryName() + " -> " + outcome
                    + (cast && this.lastSuccessfulCastTime > 0
                            ? " (" + (now - this.lastSuccessfulCastTime) + "t since previous)" : ""));
            if (cast) this.lastSuccessfulCastTime = now;
        }

        if (outcome == SimWizardCastPipeline.Outcome.VETOED) {
            this.wizard.setCastCommitted(false);
            setCooldown(tuning().eventBlockCooldownTicks);
            return;
        }
        if (outcome != SimWizardCastPipeline.Outcome.CAST) {
            this.wizard.setCastCommitted(false);
            setCooldown(tuning().failedCastCooldownTicks);
            return;
        }

        // Continuous spells (life_drain) are channelled - the first cast tick above succeeded, and
        // the pipeline has already published the continuous spell and told the client. Stay
        // committed for the duration.
        if (spell.isContinuous) {
            this.channelSpell = spell;
            this.channelTarget = castTarget;
            this.channelCounter = 0;
            this.channelTicksLeft = tuning().channelDurationTicks;
            this.wizard.setCastCommitted(true);
            return; // cooldown + burst are applied when the channel ends
        }

        this.wizard.setCastCommitted(false);
        this.wizard.signalCastBurst(tuning().castAnimationTicks);
        this.wizard.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,
                tuning().postCastSlownessTicks, tuning().postCastSlownessAmplifier, false, false));

        applySpellCooldown(spell);
    }

    private void applySpellCooldown(Spell spell) {
        com.spege.insanetweaks.config.categories.EntitiesCategory.Combat cfg = ModConfig.entities.assimilatedWizard.combat;
        int divisor = Math.max(1, cfg.spellCooldownDivisor);
        int bonus = Math.min(spell.getCooldown() / divisor, cfg.maxSpellCooldownBonusTicks);
        setCooldown(cfg.baseCastCooldownTicks + bonus);
    }

    /** One tick of an active continuous-spell channel. */
    private void tickChannel() {
        Spell spell = this.channelSpell;
        EntityLivingBase target = this.channelTarget;

        // Break the channel if the spell state is gone, the target became invalid/escaped, or the
        // target broke sight - a drain beam should not keep working through a wall the player
        // deliberately put between themselves and the caster.
        if (spell == null || !isValidSpellTarget(target, this.wizard)
                || this.wizard.getDistanceSq(target) > this.decisionRangeSq
                || (requiresLineOfSight(spell) && !hasRecentLineOfSight(target))) {
            this.endChannel(true);
            return;
        }

        this.wizard.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        this.wizard.getNavigator().clearPath();

        this.channelCounter++;

        SpellModifiers modifiers = this.wizard.getModifiers();

        // EBW's own continuous loop posts Tick every channel tick and stops the channel when it
        // is cancelled. Ours used to bypass the event entirely, so arcane_jammer and any pack
        // suppression effect were powerless against an in-flight drain. The veto goes through the
        // SAME second-opinion arbiter as Pre - otherwise AM2's burnout false positive would cut
        // every channel after a single tick (v4.2 semantics).
        if (MinecraftForge.EVENT_BUS.post(new SpellCastEvent.Tick(SpellCastEvent.Source.NPC,
                spell, this.wizard, modifiers, this.channelCounter))
                && !com.spege.insanetweaks.util.NpcCastVetoArbiter.shouldOverrideVeto(this.wizard, spell)) {
            logDiag("channel broken: SpellCastEvent.Tick CANCELLED, second opinion UPHELD ("
                    + spell.getRegistryName() + ")");
            this.endChannel(true);
            return;
        }

        boolean stillGoing = spell.cast(this.wizard.world, this.wizard, EnumHand.MAIN_HAND,
                this.channelCounter, target, modifiers);

        this.channelTicksLeft--;
        if (!stillGoing || this.channelTicksLeft <= 0) {
            this.endChannel(true);
        }
    }

    /**
     * Stops the channel and clears the published continuous spell.
     * @param applyCooldown false when called from resetTask (the cooldown floor there
     *                      already covers the interruption case).
     */
    private void endChannel(boolean applyCooldown) {
        Spell spell = this.channelSpell;
        EntityLivingBase lastTarget = this.channelTarget;
        this.channelSpell = null;
        this.channelTarget = null;
        this.channelTicksLeft = 0;
        this.channelCounter = 0;
        this.wizard.setCastCommitted(false);
        SimWizardCastPipeline.setContinuousSpellAndNotify(
                this.wizard, Spells.none, lastTarget, new SpellModifiers());

        if (spell != null) {
            this.wizard.signalCastBurst(tuning().castAnimationTicks);
            if (applyCooldown) {
                applySpellCooldown(spell);
            }
        }
    }

    /**
     * Spell selection. Every branch asks for a tactical ROLE and picks uniformly among the pooled
     * spells carrying it - never by registry name. That is what lets a pack edit
     * {@code spells.spellPool} without silently losing the whole tactical layer.
     *
     * <p>Uniform-random within each branch is deliberate and must stay: a strict priority order
     * once made every long-range fight 100% magic_missile (fixed in v3.1).
     */
    private Spell pickSpell(EntityLivingBase target) {
        List<Spell> pool = this.wizard.getSpells();
        if (pool.isEmpty()) {
            return null;
        }

        float hpPct = this.wizard.getHealth() / this.wizard.getMaxHealth();
        boolean lowHp = hpPct * 100.0F <= ModConfig.entities.assimilatedWizard.combat.retreatHealthPercent;

        // ---- LOW HP: patch yourself up or call in bodies ----
        if (lowHp) {
            Spell selfCare = pickByRoles(pool, SpellRole.SELF_HEAL, SpellRole.SUMMON);
            if (selfCare != null) {
                return selfCare;
            }
        }

        double distance = this.wizard.getDistance(target);

        // ---- SPECIALS, gated by a configurable roll ----
        // Deliberately rare (default 20%) so they read as signature moves, not spam:
        //  - DISPLACE up close: hurl the attacker away instead of melee-scrambling
        //  - DRAIN at mid range: channelled parasitic drain that heals the wizard
        if (ModConfig.entities.assimilatedWizard.combat.specialSpellChancePercent > 0
                && this.wizard.getRNG().nextInt(100) < ModConfig.entities.assimilatedWizard.combat.specialSpellChancePercent) {
            if (distance <= tuning().banishMaxDistance) {
                Spell displace = pickByRoles(pool, SpellRole.DISPLACE);
                if (displace != null) {
                    return displace;
                }
            } else if (distance <= tuning().lifeDrainMaxDistance) {
                Spell drain = pickByRoles(pool, SpellRole.DRAIN);
                if (drain != null) {
                    return drain;
                }
            }
        }

        // ---- SITUATIONAL OVERRIDES ----
        // Each one is rolled, NOT deterministic. A condition that is almost always true would
        // otherwise short-circuit the distance bands on every single decision and collapse the
        // fight onto one spell - which is exactly what the SLOW branch did (see rollSituational).

        // Enemies bunched in the forward cone -> hit them all at once
        if (rollSituational()
                && countTargetsInFrontCone(tuning().clusterConeRadius,
                        Math.toRadians(tuning().clusterConeAngleDegrees)) >= tuning().clusterMinTargets) {
            Spell aoe = pickByRoles(pool, SpellRole.AOE);
            if (aoe != null) {
                return aoe;
            }
        }

        // Target sprinting or hasted -> impair it
        if (rollSituational() && isTargetFastMoving(target)) {
            Spell slow = pickByRoles(pool, SpellRole.SLOW);
            if (slow != null) {
                return slow;
            }
        }

        // Healthy target already in our face -> open by shoving it back out
        if (rollSituational()
                && target.getHealth() / target.getMaxHealth() > tuning().knockbackTargetHealthPercent / 100.0F
                && distance <= tuning().knockbackMaxDistance) {
            Spell knockback = pickByRoles(pool, SpellRole.KNOCKBACK);
            if (knockback != null) {
                return knockback;
            }
        }

        // ---- DISTANCE BANDS ----
        // Role sets chosen to reproduce the previous hardcoded bands exactly for the default pool,
        // while still catching equivalent spells from any other mod.
        Spell band;
        if (distance <= tuning().shortBandDistance) {
            band = pickByRoles(pool, SpellRole.KNOCKBACK, SpellRole.AOE, SpellRole.PROJECTILE_SHORT);
        } else if (distance <= tuning().mediumBandDistance) {
            band = pickByRoles(pool, SpellRole.SLOW, SpellRole.PROJECTILE_LONG, SpellRole.AOE);
        } else {
            band = pickByRoles(pool, SpellRole.PROJECTILE_LONG, SpellRole.SLOW);
        }
        if (band != null) {
            return band;
        }

        return pool.get(this.wizard.getRNG().nextInt(pool.size()));
    }

    private int countTargetsInFrontCone(double radius, double coneHalfAngle) {
        Vec3d facing = this.wizard.getLookVec();
        AxisAlignedBB box = this.wizard.getEntityBoundingBox().grow(radius);
        List<EntityLivingBase> nearby = this.wizard.world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        int count = 0;
        for (EntityLivingBase e : nearby) {
            if (!isValidSpellTarget(e, this.wizard)) {
                continue;
            }
            Vec3d toTarget = new Vec3d(e.posX - this.wizard.posX,
                    e.posY - this.wizard.posY,
                    e.posZ - this.wizard.posZ).normalize();
            double dot = facing.dotProduct(toTarget);
            // dot >= cos(coneHalfAngle) == "in the forward cone"
            if (dot >= Math.cos(coneHalfAngle)) {
                count++;
            }
        }
        return count;
    }

    private boolean isTargetFastMoving(EntityLivingBase target) {
        if (target == null) {
            return false;
        }
        if (target instanceof EntityPlayer && ((EntityPlayer) target).isSprinting()) {
            return true;
        }
        if (target.isPotionActive(MobEffects.SPEED)) {
            return true;
        }
        // Cheap kinematic check for genuinely fast MOBS - players are already handled above by the
        // explicit sprint and Speed-potion tests.
        //
        // 🚨 The old literal here was 0.04, i.e. 0.2 blocks/tick. A vanilla player walks at
        // ~0.216 b/t, so merely WALKING satisfied it. Since this branch ran unconditionally and
        // ice_shard is the only SLOW spell in the default pool, the wizard picked ice_shard on
        // essentially every decision and never reached the distance bands at all - 8 of 9 casts in
        // the 2026-07-31 playtest log. Keep this above vanilla sprint (~0.28 b/t).
        double dx = target.posX - target.prevPosX;
        double dz = target.posZ - target.prevPosZ;
        return (dx * dx + dz * dz) > tuning().fastTargetSpeedSquared;
    }

    /**
     * @return true when a situational override is allowed to pre-empt the distance bands this
     *         decision. Uniform-random per call, so two overrides in a row are independent.
     */
    private boolean rollSituational() {
        int chance = tuning().situationalOverrideChancePercent;
        return chance >= 100 || (chance > 0 && this.wizard.getRNG().nextInt(100) < chance);
    }

    /**
     * Uniform pick among every pooled spell carrying ANY of the given roles.
     *
     * <p>A spell matching several of the roles is offered ONCE, not once per role - otherwise
     * force_orb ({@code KNOCKBACK} and {@code PROJECTILE_SHORT}) would come up twice as often as
     * its neighbours in the close band. {@code SpellRoleResolver.collect} enforces that.
     *
     * @return null when the pool contains nothing with any of these roles, so the caller can fall
     *         through to the next branch.
     */
    private Spell pickByRoles(List<Spell> pool, SpellRole... roles) {
        List<Spell> candidates = new ArrayList<Spell>(pool.size());
        for (SpellRole role : roles) {
            SpellRoleResolver.collect(pool, role, candidates);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(this.wizard.getRNG().nextInt(candidates.size()));
    }

    /**
     * The single target-validity check for this entity, shared by the combat task and by the
     * proactive {@code EntityAINearestAttackableTarget} filter in
     * {@code EntitySimWizard.initEntityAI}. Sim_wizard is a full SRP parasite, so
     * {@link EntityParasiteBase} relatives are excluded.
     *
     * <p>Deliberately contains NO line-of-sight check: this same predicate decides whether an
     * attack target may be ACQUIRED and KEPT, so failing it behind cover would make the wizard
     * drop its target every time the player steps behind a tree - the v4.1 "rarely fights"
     * regression. LOS belongs on the cast gates, not here.
     */
    public static boolean isValidSpellTarget(EntityLivingBase target, EntitySimWizard self) {
        if (target == null || target == self || target.isDead || !target.isEntityAlive()
                || target.dimension != self.dimension) {
            return false;
        }
        if (target instanceof EntityParasiteBase) {
            return false;
        }
        if (target instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) target;
            return !player.isCreative() && !player.isSpectator();
        }
        return true;
    }
}
