package com.spege.insanetweaks.spells;

import java.util.List;

import javax.annotation.Nullable;

import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityDispatcherClaw;
import com.spege.insanetweaks.events.DispatcherGraspRootHandler;
import com.spege.insanetweaks.util.SpellCastFeedback;

import electroblob.wizardry.item.SpellActions;
import electroblob.wizardry.spell.SpellRay;
import electroblob.wizardry.util.EntityUtils;
import electroblob.wizardry.util.MagicDamage;
import electroblob.wizardry.util.ParticleBuilder;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Dispatcher Grasp — Master-tier continuous ray.
 *
 * <p>The first tick raytraces; on a living hit it plants an
 * {@link EntityDispatcherClaw} on the victim and hard-roots the caster. From
 * then on the spell locks on: every subsequent tick bypasses the raytrace
 * entirely (the claw itself is the lock-on state — no static maps), tick-damages
 * the held target twice a second, and once the target drops to
 * {@code execute_threshold} of its max health it is finished off through the
 * normal damage pipeline so SRP plays its native death animation.
 *
 * <p>Never {@code setDead()}s the victim: SRP's {@code onDeathUpdate} is what
 * produces the burst/gib animation, and skipping the pipeline skips all of it.
 * Players are exempt from the execute.
 */
@SuppressWarnings("null")
public class SpellDispatcherGrasp extends SpellRay {

    /** Fraction of max health at or below which a non-player target is executed. */
    public static final String EXECUTE_THRESHOLD = "execute_threshold";

    /** Extra slack (blocks) added to the range when looking for our own claw. */
    private static final double CLAW_SEARCH_MARGIN = 8.0D;

    /** Deep arterial red, matching the mod's SRP-flavoured spell palette. */
    private static final int GRASP_COLOUR = 0x8F0C12;

    /** Near-black clot the ray particles fade into, so the beam reads necrotic. */
    private static final int GRASP_FADE_COLOUR = 0x1A0203;

    /** Number of finishing blows attempted — SRP clamps per-hit damage hard. */
    private static final int EXECUTE_ATTEMPTS = 5;

    /** Hard cap on one channelling session (ticks after chargeup): 5 seconds. */
    private static final int MAX_CHANNEL_TICKS = 100;

    public SpellDispatcherGrasp() {
        super(InsaneTweaksMod.MODID, "dispatcher_grasp", SpellActions.POINT, true);
        this.particleVelocity(-0.4D);
        this.particleSpacing(0.5D);
        this.addProperties(DAMAGE, EXECUTE_THRESHOLD);
        this.soundValues(0.8F, 1.0F, 0.1F);
    }

    // -------------------------------------------------------------------------
    // Continuous-spell sound loop. createSounds() and the two playSound
    // overrides are a package deal: playSoundLoop indexes sounds[0..2], so a
    // loop override without the three-element start/loop/end array is an
    // ArrayIndexOutOfBoundsException on the first cast. Verbatim LifeDrain.
    // -------------------------------------------------------------------------

    @Override
    protected SoundEvent[] createSounds() {
        return this.createContinuousSpellSounds();
    }

    @Override
    protected void playSound(World world, EntityLivingBase entity, int ticksInUse, int duration,
            SpellModifiers modifiers, String... sounds) {
        this.playSoundLoop(world, entity, ticksInUse);
    }

    @Override
    protected void playSound(World world, double x, double y, double z, int ticksInUse, int duration,
            SpellModifiers modifiers, String... sounds) {
        this.playSoundLoop(world, x, y, z, ticksInUse, duration);
    }

    @Override
    public boolean cast(World world, EntityPlayer caster, EnumHand hand, int ticksInUse, SpellModifiers modifiers) {
        // One grasp session lasts at most 5 seconds, then the channel is force-
        // ended. stopActiveHand() routes through ItemWand.onPlayerStoppedUsing,
        // which fires finishCasting (root removal) AND applies the JSON cooldown
        // — resetActiveHand() would skip both. Runs on both sides, like cast().
        // Command-cast (/cast) sessions are bounded by the command's own duration
        // argument; the WizardData branch below covers them anyway.
        if (ticksInUse >= MAX_CHANNEL_TICKS) {
            if (caster.isHandActive()) {
                caster.stopActiveHand();
            } else {
                electroblob.wizardry.data.WizardData data = electroblob.wizardry.data.WizardData.get(caster);
                if (data != null && data.currentlyCasting() == this) {
                    data.stopCastingContinuousSpell();
                }
            }
            return false;
        }

        EntityDispatcherClaw claw = findClaw(world, caster);

        if (claw != null) {
            Entity held = world.getEntityByID(claw.getTargetId());

            if (held instanceof EntityLivingBase && held.isEntityAlive()) {
                // Locked on: skip the raytrace entirely so looking away (or a wall
                // moving between caster and victim) does not drop the grab.
                tickOnTarget(world, caster, (EntityLivingBase) held, ticksInUse, modifiers);
                return true;
            }
        }

        return super.cast(world, caster, hand, ticksInUse, modifiers);
    }

    /** Our own live claw, if this caster already has one. */
    @Nullable
    private EntityDispatcherClaw findClaw(World world, EntityLivingBase caster) {
        double reach = this.getProperty(RANGE).doubleValue() + CLAW_SEARCH_MARGIN;
        List<EntityDispatcherClaw> claws = world.getEntitiesWithinAABB(EntityDispatcherClaw.class,
                caster.getEntityBoundingBox().grow(reach));

        for (EntityDispatcherClaw claw : claws) {
            if (!claw.isDead && claw.getCasterId() == caster.getEntityId()) {
                return claw;
            }
        }

        return null;
    }

    @Override
    protected boolean onEntityHit(World world, Entity target, Vec3d hit, @Nullable EntityLivingBase caster,
            Vec3d origin, int ticksInUse, SpellModifiers modifiers) {
        if (!EntityUtils.isLiving(target)) {
            return false;
        }

        // Stop the ray on a living hit even for a casterless (dispenser) cast —
        // there is simply nobody to root or to own the claw in that case.
        if (caster != null && !world.isRemote) {
            EntityDispatcherClaw claw = new EntityDispatcherClaw(world);
            claw.setPosition(target.posX, target.posY, target.posZ);
            claw.setCasterId(caster.getEntityId());
            claw.setTargetId(target.getEntityId());
            world.spawnEntity(claw);

            if (caster instanceof EntityPlayer) {
                DispatcherGraspRootHandler.applyRootModifier((EntityPlayer) caster);
            }
        }

        return true;
    }

    @Override
    protected boolean onBlockHit(World world, BlockPos pos, EnumFacing side, Vec3d hit,
            @Nullable EntityLivingBase caster, Vec3d origin, int ticksInUse, SpellModifiers modifiers) {
        return true;
    }

    @Override
    protected boolean onMiss(World world, @Nullable EntityLivingBase caster, Vec3d origin, Vec3d direction,
            int ticksInUse, SpellModifiers modifiers) {
        return true;
    }

    /** Per-tick logic while the claw holds a victim. Runs on both sides. */
    private void tickOnTarget(World world, EntityLivingBase caster, EntityLivingBase target,
            int ticksInUse, SpellModifiers modifiers) {
        if (world.isRemote) {
            spawnHoldParticles(world, target);
            return;
        }

        // Idempotent — cheap, and it survives anything that strips the modifier
        // mid-channel (another mod's attribute reset, a re-login).
        if (caster instanceof EntityPlayer) {
            DispatcherGraspRootHandler.applyRootModifier((EntityPlayer) caster);
        }

        if (ticksInUse % 10 == 0) {
            float damage = this.getProperty(DAMAGE).floatValue() * modifiers.get(SpellModifiers.POTENCY);
            EntityUtils.attackEntityWithoutKnockback(target,
                    MagicDamage.causeDirectMagicDamage(caster, MagicDamage.DamageType.MAGIC), damage);
        }

        if (!(target instanceof EntityPlayer) && target.getHealth() > 0.0F
                && target.getHealth() <= this.getProperty(EXECUTE_THRESHOLD).floatValue() * target.getMaxHealth()) {
            executeTarget(world, caster, target);
        }
    }

    /**
     * Finishing blow. FX first (so the burst reads even if the very first hit
     * kills), then repeated normal damage — never {@code setDead()}, because
     * SRP's native death animation lives in the damage/death pipeline.
     */
    private void executeTarget(World world, EntityLivingBase caster, EntityLivingBase target) {
        if (target instanceof EntityParasiteBase) {
            EntityParasiteBase parasite = (EntityParasiteBase) target;
            parasite.particleStatus((byte) 6);
            parasite.particleStatus((byte) 13);
        } else {
            SpellCastFeedback.srpBurstAt(world, target, 0.5D, SRPEnumParticle.GSPLASH, GRASP_COLOUR,
                    24, 0.4F, 0.4F, 0.1F);
        }

        for (int i = 0; i < EXECUTE_ATTEMPTS && target.getHealth() > 0.0F && !target.isDead; i++) {
            // Clearing the invulnerability window is what makes the loop mean
            // anything: without it every repeat hit of the same magnitude is
            // swallowed by EntityLivingBase's "amount <= lastDamage" early-out,
            // and a single clamped hit is exactly what the loop exists to beat.
            target.hurtResistantTime = 0;
            target.attackEntityFrom(
                    MagicDamage.causeDirectMagicDamage(caster, MagicDamage.DamageType.MAGIC),
                    target.getMaxHealth() * 2.0F);
        }
    }

    /** Client-side ambience around the held victim. */
    private void spawnHoldParticles(World world, EntityLivingBase target) {
        for (int i = 0; i < 2; i++) {
            double x = target.posX + (world.rand.nextDouble() - 0.5D) * (double) target.width * 1.6D;
            double y = target.posY + world.rand.nextDouble() * (double) target.height;
            double z = target.posZ + (world.rand.nextDouble() - 0.5D) * (double) target.width * 1.6D;
            ParticleBuilder.create(ParticleBuilder.Type.DARK_MAGIC).pos(x, y, z)
                    .clr(GRASP_COLOUR).spawn(world);
        }

        if (world.rand.nextInt(3) == 0) {
            ParticleBuilder.create(ParticleBuilder.Type.SPARKLE)
                    .pos(target.posX, target.posY + (double) target.height * 0.5D, target.posZ)
                    .vel((world.rand.nextDouble() - 0.5D) * 0.05D, 0.02D, (world.rand.nextDouble() - 0.5D) * 0.05D)
                    .time(10 + world.rand.nextInt(6))
                    .clr(GRASP_COLOUR).fade(GRASP_FADE_COLOUR).spawn(world);
        }
    }

    /**
     * The ray itself. {@code particleVelocity(-0.4)} makes every particle drift
     * back down the beam towards the caster, so the ray reads as something being
     * dragged out of the victim rather than fired at it; the fade to
     * {@link #GRASP_FADE_COLOUR} keeps a red beam looking necrotic instead of
     * festive (LifeDrain uses the same trick with a plain dark mote).
     */
    @Override
    protected void spawnParticle(World world, double x, double y, double z, double vx, double vy, double vz) {
        ParticleBuilder.create(ParticleBuilder.Type.SPARKLE).pos(x, y, z).vel(vx, vy, vz)
                .time(8 + world.rand.nextInt(6)).scale(0.9F)
                .clr(GRASP_COLOUR).fade(GRASP_FADE_COLOUR).spawn(world);

        // Half-density offset scatter: gives the beam some thickness without
        // doubling the particle budget on every point along it.
        if (world.rand.nextInt(2) == 0) {
            double jx = (world.rand.nextDouble() - 0.5D) * 0.16D;
            double jy = (world.rand.nextDouble() - 0.5D) * 0.16D;
            double jz = (world.rand.nextDouble() - 0.5D) * 0.16D;
            ParticleBuilder.create(ParticleBuilder.Type.SPARKLE).pos(x + jx, y + jy, z + jz).vel(vx, vy, vz)
                    .time(6 + world.rand.nextInt(5)).scale(0.55F)
                    .clr(GRASP_COLOUR).fade(GRASP_FADE_COLOUR).spawn(world);
        }

        if (world.rand.nextInt(5) == 0) {
            ParticleBuilder.create(ParticleBuilder.Type.DARK_MAGIC).pos(x, y, z)
                    .clr(0.24F, 0.01F, 0.02F).spawn(world);
        }
    }

    @Override
    public void finishCasting(World world, @Nullable EntityLivingBase caster, double x, double y, double z,
            @Nullable EnumFacing direction, int duration, SpellModifiers modifiers) {
        super.finishCasting(world, caster, x, y, z, direction, duration, modifiers);

        if (caster instanceof EntityPlayer) {
            DispatcherGraspRootHandler.removeRootModifier((EntityPlayer) caster);
        }
    }
}
