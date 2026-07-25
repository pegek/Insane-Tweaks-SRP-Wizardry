package com.spege.insanetweaks.entities;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.init.ModSpells;

import electroblob.wizardry.util.EntityUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * The visual/mechanical grabbing claw of the Dispatcher Grasp spell — a
 * stripped-down clone of SRP's {@code EntityNak} grab.
 *
 * <p>Deliberately extends {@link EntityLiving} rather than
 * {@code EntitySummonedCreature}: it must NOT count as one of the caster's
 * minions, must not be targetable, damageable, pushable or persistable. It is
 * pure spell state made visible — all of its lifetime is owned by the spell,
 * and it holds no NBT at all ({@link #writeToNBTOptional} returns {@code false},
 * so the chunk saver skips it entirely; {@link #readEntityFromNBT} kills any
 * claw that somehow made it into a save from an older build).
 *
 * <p>Server tick: resolves caster/target by entity id, despawns on every loss
 * condition (caster or target gone/dead, target dragged more than
 * {@link #MAX_GRAB_DISTANCE} away, caster stopped channelling for longer than
 * {@link #CAST_GRACE_TICKS}, or the {@link #MAX_LIFETIME} hard cap), and
 * otherwise re-applies SRP's Nak grab to the target verbatim.
 */
@SuppressWarnings("null")
public class EntityDispatcherClaw extends EntityLiving {

    private static final DataParameter<Integer> CASTER_ID = EntityDataManager
            .createKey(EntityDispatcherClaw.class, DataSerializers.VARINT);
    private static final DataParameter<Integer> TARGET_ID = EntityDataManager
            .createKey(EntityDispatcherClaw.class, DataSerializers.VARINT);

    /** Beyond this distance (blocks) between claw and target the grab breaks. */
    public static final double MAX_GRAB_DISTANCE = 8.0D;
    private static final double MAX_GRAB_DISTANCE_SQ = MAX_GRAB_DISTANCE * MAX_GRAB_DISTANCE;

    /** Hard cap so a claw can never leak, whatever goes wrong upstream. */
    private static final int MAX_LIFETIME = 1200;

    /** Ticks the caster may be "not casting" before the claw lets go. */
    private static final int CAST_GRACE_TICKS = 10;

    private int graceTicks;

    public EntityDispatcherClaw(World world) {
        super(world);
        this.setSize(0.7F, 2.5F);
        this.setNoAI(true);
        this.setEntityInvulnerable(true);
        this.setNoGravity(true);
        this.experienceValue = 0;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(CASTER_ID, Integer.valueOf(-1));
        this.dataManager.register(TARGET_ID, Integer.valueOf(-1));
    }

    @Override
    public boolean isPotionApplicable(PotionEffect effect) {
        // Pure spell state — no SRP effect (COTH included) may ever stick to it.
        return false;
    }

    public int getCasterId() {
        return this.dataManager.get(CASTER_ID).intValue();
    }

    public void setCasterId(int id) {
        this.dataManager.set(CASTER_ID, Integer.valueOf(id));
    }

    public int getTargetId() {
        return this.dataManager.get(TARGET_ID).intValue();
    }

    public void setTargetId(int id) {
        this.dataManager.set(TARGET_ID, Integer.valueOf(id));
    }

    // -------------------------------------------------------------------------
    // Inertness: no collision, no pushing, no damage, no sound, no despawn.
    // -------------------------------------------------------------------------

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBeAttackedWithItem() {
        return false;
    }

    @Override
    protected void collideWithNearbyEntities() {
    }

    @Override
    public boolean attackEntityFrom(@Nonnull DamageSource source, float amount) {
        // OUT_OF_WORLD (void / /kill) is the one way out — everything else bounces.
        if (source == DamageSource.OUT_OF_WORLD) {
            return super.attackEntityFrom(source, amount);
        }
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(@Nonnull DamageSource damageSourceIn) {
        return null;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    /** Vanilla distance/age despawn is disabled — the spell owns this lifetime. */
    @Override
    protected void despawnEntity() {
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
    }

    @Override
    protected void playStepSound(@Nonnull BlockPos pos, @Nonnull Block blockIn) {
    }

    // -------------------------------------------------------------------------
    // Non-persistence. Belt: writeToNBTOptional == false means the chunk saver
    // never writes us (verified against 1.12.2 Entity#func_70039_c — "if this
    // returns false the entity is not saved on disk"). Braces: any claw that
    // does get read back (older save, /summon) dies on the spot.
    // -------------------------------------------------------------------------

    @Override
    public boolean writeToNBTOptional(@Nonnull NBTTagCompound compound) {
        return false;
    }

    @Override
    public void readEntityFromNBT(@Nonnull NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.setDead();
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @Override
    public void onUpdate() {
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;

        super.onUpdate();

        // Zero again after the vanilla tick so nothing (gravity, water, knockback
        // from the target's own AI) can drift the anchor.
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.setNoGravity(true);

        if (this.world.isRemote) {
            return;
        }

        Entity casterEntity = this.getCasterId() < 0 ? null : this.world.getEntityByID(this.getCasterId());
        Entity targetEntity = this.getTargetId() < 0 ? null : this.world.getEntityByID(this.getTargetId());

        if (!(casterEntity instanceof EntityLivingBase) || !casterEntity.isEntityAlive()) {
            this.setDead();
            return;
        }

        if (!(targetEntity instanceof EntityLivingBase) || !targetEntity.isEntityAlive()) {
            this.setDead();
            return;
        }

        if (this.ticksExisted > MAX_LIFETIME) {
            this.setDead();
            return;
        }

        EntityLivingBase caster = (EntityLivingBase) casterEntity;
        EntityLivingBase target = (EntityLivingBase) targetEntity;

        if (target.getDistanceSq(this) > MAX_GRAB_DISTANCE_SQ) {
            this.setDead();
            return;
        }

        // Only player casters are gated on the channel — an NPC/other caster has
        // no WizardData channel state to read, so it relies on the conditions above.
        if (caster instanceof EntityPlayer) {
            if (EntityUtils.isCasting(caster, ModSpells.DISPATCHER_GRASP)) {
                this.graceTicks = 0;
            } else if (++this.graceTicks > CAST_GRACE_TICKS) {
                this.setDead();
                return;
            }
        }

        this.applyGrab(target);
        this.faceTarget(target);
    }

    /** SRP's Nak grab, verbatim: yanked horizontally towards the claw + Slowness III. */
    private void applyGrab(EntityLivingBase target) {
        target.dismountRidingEntity();
        target.motionX += (Math.signum(this.posX - target.posX) * 0.5D - target.motionX) * 0.5D;
        target.motionZ += (Math.signum(this.posZ - target.posZ) * 0.5D - target.motionZ) * 0.5D;
        target.velocityChanged = true;
        target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 20, 2, false, false));
    }

    /** Purely cosmetic: keeps the claw pointing at whatever it is holding. */
    private void faceTarget(EntityLivingBase target) {
        double dx = target.posX - this.posX;
        double dz = target.posZ - this.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        if (horizontal < 0.01D) {
            return;
        }

        double dy = (target.posY + (double) target.getEyeHeight()) - (this.posY + (double) this.getEyeHeight());
        float yaw = (float) (MathHelper.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float pitch = (float) (-(MathHelper.atan2(dy, horizontal) * (180.0D / Math.PI)));

        this.prevRotationYaw = this.rotationYaw;
        this.prevRotationPitch = this.rotationPitch;
        this.prevRotationYawHead = this.rotationYawHead;
        this.prevRenderYawOffset = this.renderYawOffset;

        this.rotationYaw = yaw;
        this.rotationPitch = pitch;
        this.rotationYawHead = yaw;
        this.renderYawOffset = yaw;
    }
}
