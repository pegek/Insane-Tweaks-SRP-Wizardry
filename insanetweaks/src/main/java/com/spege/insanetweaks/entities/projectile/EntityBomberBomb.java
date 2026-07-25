package com.spege.insanetweaks.entities.projectile;

import java.util.List;

import com.spege.insanetweaks.entities.EntityLightBomberMinion;
import com.spege.insanetweaks.entities.SummonInfectionSafetyHelper;

import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.util.AllyDesignationSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;

/**
 * Bomb dropped by {@link EntityLightBomberMinion}.
 *
 * <p>Physics clone of SRP's {@code EntityBomb}. SRP's own entity is unusable
 * here: its constructor demands an {@code EntityParasiteBase} owner, its
 * explosion skips the whole damage loop when the owner is null, and it spawns
 * an infectious {@code EntityToxicCloud}. This version keeps the fuse/bounce
 * feel but routes damage through a friendly-fire filter (caster, the bomber
 * itself, the caster's other summons and Wizardry allies are skipped).
 */
@SuppressWarnings("null")
public class EntityBomberBomb extends Entity {

    private static final DataParameter<Integer> FUSE = EntityDataManager.createKey(EntityBomberBomb.class,
            DataSerializers.VARINT);

    private static final int DEFAULT_FUSE = 80;
    private static final double DAMAGE_RADIUS = 4.0D;

    /** The bomber that dropped this bomb. May be null after a world reload. */
    private EntityLivingBase ownerRef;
    /** The player (or other caster) that summoned the bomber. May be null. */
    private EntityLivingBase casterRef;

    private float damage = 4.0F;
    private int fuse = DEFAULT_FUSE;

    public EntityBomberBomb(World worldIn) {
        super(worldIn);
        this.setSize(0.68F, 0.68F);
        this.preventEntitySpawning = true;
        this.isImmuneToFire = true;
    }

    public EntityBomberBomb(World worldIn, EntityLightBomberMinion owner) {
        this(worldIn);
        this.ownerRef = owner;
        this.casterRef = owner.getCaster();
        this.setLocationAndAngles(owner.posX, owner.posY + (double) owner.getEyeHeight() - 0.1D, owner.posZ,
                owner.rotationYaw, owner.rotationPitch);
    }

    @Override
    protected void entityInit() {
        this.dataManager.register(FUSE, Integer.valueOf(DEFAULT_FUSE));
    }

    public void setFuse(int fuse) {
        this.fuse = fuse;
        this.dataManager.set(FUSE, Integer.valueOf(fuse));
    }

    public int getFuse() {
        return this.fuse;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setCaster(EntityLivingBase caster) {
        this.casterRef = caster;
    }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);

        if (FUSE.equals(key) && this.world.isRemote) {
            this.fuse = this.dataManager.get(FUSE).intValue();
        }
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !this.isDead;
    }

    @Override
    public float getEyeHeight() {
        return 0.0F;
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (!this.hasNoGravity()) {
            this.motionY -= 0.04D;
        }

        this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);
        this.motionX *= 0.98D;
        this.motionY *= 0.98D;
        this.motionZ *= 0.98D;

        if (this.onGround) {
            this.motionX *= 0.7D;
            this.motionZ *= 0.7D;
            this.motionY *= -0.5D;
        }

        this.fuse--;

        if (this.fuse <= 0) {
            if (this.world.isRemote) {
                this.setDead();
            } else {
                this.explode();
            }
            return;
        }

        this.handleWaterMovement();

        if (this.world.isRemote) {
            this.world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, this.posX, this.posY + 0.5D, this.posZ,
                    0.0D, 0.0D, 0.0D);
        }
    }

    private void explode() {
        // FX only — a real world.createExplosion() would deal unfiltered vanilla
        // AoE damage to everyone nearby (caster included). The filtered loop
        // below is the ONLY damage source of this bomb.
        this.world.playSound(null, this.posX, this.posY, this.posZ, net.minecraft.init.SoundEvents.ENTITY_GENERIC_EXPLODE,
                net.minecraft.util.SoundCategory.HOSTILE, 4.0F,
                (1.0F + (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F) * 0.7F);

        if (this.world instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) this.world).spawnParticle(EnumParticleTypes.EXPLOSION_LARGE,
                    this.posX, this.posY + 0.5D, this.posZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        EntityLivingBase attributedTo = this.casterRef != null ? this.casterRef : this.ownerRef;
        List<EntityLivingBase> nearby = this.world.getEntitiesWithinAABB(EntityLivingBase.class,
                this.getEntityBoundingBox().grow(DAMAGE_RADIUS));

        for (EntityLivingBase entity : nearby) {
            if (entity == this.casterRef || entity == this.ownerRef) {
                continue;
            }

            if (this.casterRef != null && entity instanceof EntitySummonedCreature
                    && ((EntitySummonedCreature) entity).getCaster() == this.casterRef) {
                continue;
            }

            if (this.casterRef != null && AllyDesignationSystem.isAllied(this.casterRef, entity)) {
                continue;
            }

            // Line of sight check, same shape SRP uses: walls stop the shrapnel.
            if (!entity.canEntityBeSeen(this)) {
                continue;
            }

            if (entity.attackEntityFrom(DamageSource.causeThrownDamage(this, attributedTo), this.damage)) {
                SummonInfectionSafetyHelper.onSuccessfulSummonHit(entity);
            }
        }

        this.setDead();
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setShort("Fuse", (short) this.fuse);
        compound.setFloat("Damage", this.damage);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.setFuse(compound.getShort("Fuse"));
        this.damage = compound.getFloat("Damage");
    }
}
