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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;

/**
 * Primary attack task for {@link EntitySimWizard}.
 *
 * Two phases per cast:
 *   1. TELEGRAPH - charge-up window ({@code ModConfig.simWizard.castTelegraphTicks}) with
 *      vocalization + particle ring so the player has a dodge window.
 *   2. FIRE - resolves the chosen spell, swings arm, applies post-cast slowness.
 *
 * v3.1 CRITICAL timing fix: cooldowns are now ABSOLUTE world-time gates
 * ({@code nextCastReadyTime} vs {@code getTotalWorldTime()}), NOT counters decremented in
 * {@code shouldExecute()}. Vanilla {@code EntityAITasks} polls {@code shouldExecute} of
 * non-executing tasks only every {@code tickRate = 3} ticks, so a counter decremented there
 * runs 3x slower than intended - the previous 80-200 tick cooldowns actually took 12-30
 * seconds, which is why the wizard "rarely cast anything".
 *
 * v3.1 variety fix: distance bands collect CANDIDATE lists and pick randomly (previously
 * strict priority made every long-range fight 100% magic_missile), and the low-HP branch
 * picks randomly among heal + summons (previously heal always shadowed the summons, so
 * summon_fer_cow / summon_primitive_yelloweye never fired).
 */
public class EntityAISimWizardCast extends EntityAIBase {

    private static final int MIN_RETRY_COOLDOWN = 5;
    private static final int FAILED_CAST_COOLDOWN = 10;
    private static final int EVENT_BLOCK_COOLDOWN = 20;
    private static final int CAST_ANIMATION_TICKS = 14;
    private static final int POST_CAST_SLOWNESS_TICKS = 20;
    private static final int POST_CAST_SLOWNESS_AMPLIFIER = 1;
    /** How long a continuous spell (life_drain) is channeled, in ticks. */
    private static final int CHANNEL_DURATION_TICKS = 40;
    /** Max distance for the banish special (close-quarters "get off me"). */
    private static final double BANISH_MAX_DISTANCE = 4.5D;
    /** life_drain special window upper bound - stays inside the ray's ~10 block base range. */
    private static final double LIFE_DRAIN_MAX_DISTANCE = 9.0D;

    private final EntitySimWizard wizard;
    private final double decisionRange;
    private final double decisionRangeSq;

    /** Absolute world time (getTotalWorldTime) before which no new cast may start. */
    private long nextCastReadyTime;

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

    public EntityAISimWizardCast(EntitySimWizard wizard) {
        this.wizard = wizard;
        this.decisionRange = ModConfig.simWizard.decisionRange * ModConfig.simWizard.rangeMultiplier;
        this.decisionRangeSq = this.decisionRange * this.decisionRange;
        this.setMutexBits(3);
    }

    private boolean isOffCooldown() {
        return this.wizard.world.getTotalWorldTime() >= this.nextCastReadyTime;
    }

    private void setCooldown(int ticks) {
        this.nextCastReadyTime = this.wizard.world.getTotalWorldTime() + Math.max(0, ticks);
    }

    @Override
    public boolean shouldExecute() {
        if (!isOffCooldown()) {
            return false;
        }

        EntityLivingBase target = this.wizard.getAttackTarget();
        if (!isValidSpellTarget(target, this.wizard)) {
            return false;
        }

        return this.wizard.getDistanceSq(target) <= this.decisionRangeSq;
    }

    @Override
    public boolean shouldContinueExecuting() {
        // Stay active while telegraph is winding down or a channel is running (commitment).
        if (this.telegraphTicksLeft > 0 || this.channelTicksLeft > 0) {
            return true;
        }
        if (!isOffCooldown()) {
            return false;
        }
        EntityLivingBase target = this.wizard.getAttackTarget();
        if (!isValidSpellTarget(target, this.wizard)) {
            return false;
        }
        return this.wizard.getDistanceSq(target) <= this.decisionRangeSq;
    }

    @Override
    public void startExecuting() {
        this.wizard.getNavigator().clearPath();
        this.telegraphTicksLeft = 0;
        this.pendingSpell = null;
        this.pendingTarget = null;
    }

    @Override
    public void resetTask() {
        // Never SHORTEN an already-set cooldown, only guarantee a small floor.
        long floor = this.wizard.world.getTotalWorldTime() + MIN_RETRY_COOLDOWN;
        if (this.nextCastReadyTime < floor) {
            this.nextCastReadyTime = floor;
        }
        this.telegraphTicksLeft = 0;
        this.pendingSpell = null;
        this.pendingTarget = null;
        // CRITICAL: always end an in-flight channel here, or the continuous-spell visual
        // (getContinuousSpell drives isCastingSpellVisual) loops forever after interruption.
        if (this.channelTicksLeft > 0 || this.channelSpell != null) {
            this.endChannel(false);
        }
    }

    @Override
    public void updateTask() {
        // Phase 2: continuous channel in progress (life_drain) - cast every tick.
        if (this.channelTicksLeft > 0) {
            this.tickChannel();
            return;
        }

        // Phase 1: telegraph in progress - just keep looking at target and tick down.
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

        // Phase 0: pick a spell + target and either begin telegraph or fire immediately.
        EntityLivingBase target = this.wizard.getAttackTarget();
        if (target == null) {
            setCooldown(MIN_RETRY_COOLDOWN);
            return;
        }

        this.wizard.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        this.wizard.getNavigator().clearPath();

        Spell spell = pickSpell(target);
        if (spell == null || spell == Spells.none) {
            setCooldown(FAILED_CAST_COOLDOWN);
            return;
        }

        int telegraph = Math.max(0, ModConfig.simWizard.castTelegraphTicks);
        if (telegraph == 0) {
            this.pendingSpell = spell;
            this.pendingTarget = target;
            this.fireCommittedCast();
            return;
        }

        this.pendingSpell = spell;
        this.pendingTarget = target;
        this.telegraphTicksLeft = telegraph;
        this.wizard.signalCastTelegraph(telegraph);
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
            setCooldown(FAILED_CAST_COOLDOWN);
            return;
        }

        // self-heal special case - target is the wizard itself
        boolean isSelfHeal = isHealSpell(spell);
        EntityLivingBase castTarget = isSelfHeal ? this.wizard : target;

        if (!isSelfHeal) {
            if (!isValidSpellTarget(castTarget, this.wizard)
                    || this.wizard.getDistanceSq(castTarget) > this.decisionRangeSq) {
                // Telegraph "wasted" - the target left during wind-up. Short cooldown only.
                setCooldown(FAILED_CAST_COOLDOWN);
                return;
            }
        }

        SpellModifiers modifiers = this.wizard.getModifiers();

        if (MinecraftForge.EVENT_BUS.post(
                new SpellCastEvent.Pre(SpellCastEvent.Source.NPC, spell, this.wizard, modifiers))) {
            setCooldown(EVENT_BLOCK_COOLDOWN);
            return;
        }

        boolean cast = spell.cast(this.wizard.world, this.wizard, EnumHand.MAIN_HAND, 0, castTarget, modifiers);
        if (!cast) {
            setCooldown(FAILED_CAST_COOLDOWN);
            return;
        }

        MinecraftForge.EVENT_BUS.post(
                new SpellCastEvent.Post(SpellCastEvent.Source.NPC, spell, this.wizard, modifiers));

        this.wizard.swingArm(EnumHand.MAIN_HAND);

        // v3.3: continuous spells (life_drain) are channeled - the first cast tick above
        // succeeded, now keep casting every tick for CHANNEL_DURATION_TICKS. The continuous
        // spell is published via ISpellCaster so the glow/aura visuals loop for the duration.
        if (spell.isContinuous) {
            this.channelSpell = spell;
            this.channelTarget = castTarget;
            this.channelCounter = 0;
            this.channelTicksLeft = CHANNEL_DURATION_TICKS;
            this.wizard.setContinuousSpell(spell);
            return; // cooldown + burst are applied when the channel ends
        }

        this.wizard.signalCastBurst(CAST_ANIMATION_TICKS);
        this.wizard.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,
                POST_CAST_SLOWNESS_TICKS, POST_CAST_SLOWNESS_AMPLIFIER, false, false));

        applySpellCooldown(spell);
    }

    private void applySpellCooldown(Spell spell) {
        ModConfig.SimWizard cfg = ModConfig.simWizard;
        int divisor = Math.max(1, cfg.spellCooldownDivisor);
        int bonus = Math.min(spell.getCooldown() / divisor, cfg.maxSpellCooldownBonusTicks);
        setCooldown(cfg.baseCastCooldownTicks + bonus);
    }

    /** One tick of an active continuous-spell channel. */
    private void tickChannel() {
        Spell spell = this.channelSpell;
        EntityLivingBase target = this.channelTarget;

        // Break the channel if the spell state is gone or the target became invalid/escaped.
        if (spell == null || !isValidSpellTarget(target, this.wizard)
                || this.wizard.getDistanceSq(target) > this.decisionRangeSq) {
            this.endChannel(true);
            return;
        }

        this.wizard.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        this.wizard.getNavigator().clearPath();

        this.channelCounter++;
        boolean stillGoing = spell.cast(this.wizard.world, this.wizard, EnumHand.MAIN_HAND,
                this.channelCounter, target, this.wizard.getModifiers());

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
        this.channelSpell = null;
        this.channelTarget = null;
        this.channelTicksLeft = 0;
        this.channelCounter = 0;
        this.wizard.setContinuousSpell(Spells.none);

        if (spell != null) {
            this.wizard.signalCastBurst(CAST_ANIMATION_TICKS);
            if (applyCooldown) {
                applySpellCooldown(spell);
            }
        }
    }

    /**
     * Spell selection: situational overrides first, then a RANDOM pick among the
     * distance-band candidates (strict priority previously made long-range fights
     * 100% magic_missile).
     */
    private Spell pickSpell(EntityLivingBase target) {
        List<Spell> pool = this.wizard.getSpells();
        if (pool.isEmpty()) {
            return null;
        }

        float hpPct = this.wizard.getHealth() / this.wizard.getMaxHealth();
        boolean lowHp = hpPct * 100.0F <= ModConfig.simWizard.retreatHealthPercent;

        // ---- LOW HP: random among heal + summons, so the summons actually get used ----
        if (lowHp) {
            List<Spell> selfCare = new ArrayList<Spell>(3);
            Spell heal = findByName(pool, "heal");
            if (heal != null) {
                selfCare.add(heal);
            }
            collectSummons(pool, selfCare);
            if (!selfCare.isEmpty()) {
                return selfCare.get(this.wizard.getRNG().nextInt(selfCare.size()));
            }
        }

        double distance = this.wizard.getDistance(target);

        // ---- SPECIAL SPELLS (v3.3): life_drain / banish, gated by a configurable roll ----
        // Deliberately rare (default 20%) so they read as signature moves, not spam:
        //  - banish inside 4.5 blocks: hurl the attacker away instead of melee-scrambling
        //  - life_drain at 4.5-9 blocks: channeled parasitic drain (heals the wizard)
        if (ModConfig.simWizard.specialSpellChancePercent > 0
                && this.wizard.getRNG().nextInt(100) < ModConfig.simWizard.specialSpellChancePercent) {
            if (distance <= BANISH_MAX_DISTANCE) {
                Spell banish = findByName(pool, "banish");
                if (banish != null) {
                    return banish;
                }
            } else if (distance <= LIFE_DRAIN_MAX_DISTANCE) {
                Spell drain = findByName(pool, "life_drain");
                if (drain != null) {
                    return drain;
                }
            }
        }

        // ---- SITUATIONAL OVERRIDES ----
        // 2+ enemies grouped in front cone -> AoE
        int clusterCount = countTargetsInFrontCone(8.0D, Math.PI / 3.0D);
        if (clusterCount >= 2) {
            Spell aoe = findByName(pool, "spark_bomb");
            if (aoe != null) {
                return aoe;
            }
        }

        // Target sprinting or has speed buff -> slow it
        if (isTargetFastMoving(target)) {
            Spell ice = findByName(pool, "ice_shard");
            if (ice != null) {
                return ice;
            }
        }

        // Target full HP + close -> open with force_orb (knockback)
        if (target.getHealth() / target.getMaxHealth() > 0.85F && distance <= 6.0D) {
            Spell force = findByName(pool, "force_orb");
            if (force != null) {
                return force;
            }
        }

        // ---- DISTANCE-BAND CANDIDATES (random pick) ----
        List<Spell> band = new ArrayList<Spell>(3);
        if (distance <= 6.0D) {
            addIfPresent(pool, band, "force_orb");
            addIfPresent(pool, band, "spark_bomb");
        } else if (distance <= 14.0D) {
            addIfPresent(pool, band, "ice_shard");
            addIfPresent(pool, band, "magic_missile");
            addIfPresent(pool, band, "spark_bomb");
        } else {
            addIfPresent(pool, band, "magic_missile");
            addIfPresent(pool, band, "ice_shard");
        }
        if (!band.isEmpty()) {
            return band.get(this.wizard.getRNG().nextInt(band.size()));
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
        // Cheap kinematic check - SRP-style approach speed.
        double dx = target.posX - target.prevPosX;
        double dz = target.posZ - target.prevPosZ;
        return (dx * dx + dz * dz) > 0.04D; // ~0.2 blocks/tick lateral
    }

    private static boolean isHealSpell(Spell spell) {
        return spell != null && spell.getRegistryName() != null
                && "heal".equalsIgnoreCase(spell.getRegistryName().getResourcePath());
    }

    private static Spell findByName(List<Spell> pool, String name) {
        for (Spell s : pool) {
            if (s == null || s.getRegistryName() == null) {
                continue;
            }
            if (s.getRegistryName().getResourcePath().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    private static void addIfPresent(List<Spell> pool, List<Spell> out, String name) {
        Spell s = findByName(pool, name);
        if (s != null && !out.contains(s)) {
            out.add(s);
        }
    }

    private static void collectSummons(List<Spell> pool, List<Spell> out) {
        for (Spell s : pool) {
            if (s == null || s.getRegistryName() == null) {
                continue;
            }
            if (s.getRegistryName().getResourcePath().startsWith("summon_") && !out.contains(s)) {
                out.add(s);
            }
        }
    }

    /**
     * Re-used target validity check (mirrors EntitySimWizard.isValidSpellTarget).
     * Sim_wizard is a full SRP parasite, so {@link EntityParasiteBase} relatives are excluded.
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
