package com.spege.insanetweaks.entities.projectile;

import java.util.List;

import com.dhanantry.scapeandrunparasites.init.SRPSounds;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class EntityYelloweyeNade extends Entity {

    private int fuseTicks = 3;
    private int lingerTicks = 60;
    private int activeTicks = 0;
    private EntityLivingBase owner;
    private float burstDamage = 5.0F;
    private float burstRadius = 1.45F;
    private double anchorX;
    private double anchorY;
    private double anchorZ;

    public EntityYelloweyeNade(World worldIn) {
        super(worldIn);
        this.setSize(0.5F, 0.5F);
        this.noClip = true;
        this.isImmuneToFire = true;
    }

    public EntityYelloweyeNade(World worldIn, int fuseTicks, int lingerTicks, float burstDamage) {
        this(worldIn);
        this.fuseTicks = fuseTicks;
        this.lingerTicks = lingerTicks;
        this.burstDamage = burstDamage;
    }

    public void setOwner(EntityLivingBase owner) {
        this.owner = owner;
    }

    @Override
    protected void entityInit() {
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (this.ticksExisted <= 3) {
            this.anchorX = this.posX;
            this.anchorY = this.posY;
            this.anchorZ = this.posZ;

            if (this.ticksExisted == 2) {
                this.playSound(SRPSounds.NADE_S, 1.0F, 1.0F);
            }
        } else {
            this.posX = this.anchorX;
            this.posZ = this.anchorZ;
        }

        if (this.world.isRemote) {
            this.spawnSmoke();
            return;
        }

        if (this.ticksExisted < this.fuseTicks) {
            return;
        }

        this.activeTicks++;
        this.damageNearbyTargets();

        if (this.activeTicks > this.lingerTicks) {
            this.setDead();
        }
    }

    private void damageNearbyTargets() {
        float radius = Math.max(this.burstRadius, this.width / 2.0F);
        AxisAlignedBB area = new AxisAlignedBB(this.posX - (double) radius, this.posY - 0.25D,
                this.posZ - (double) radius, this.posX + (double) radius, this.posY + (double) this.height + 1.25D,
                this.posZ + (double) radius);
        List<EntityLivingBase> targets = this.world.getEntitiesWithinAABB(EntityLivingBase.class, area);

        for (EntityLivingBase target : targets) {
            if (this.owner != null && (target == this.owner || this.owner.isOnSameTeam(target))) {
                continue;
            }

            target.attackEntityFrom(DamageSource.MAGIC, this.burstDamage);
        }
    }

    private void spawnSmoke() {
        for (int i = 0; i < 5; i++) {
            this.world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                    this.posX + (double) (this.rand.nextFloat() * this.width * 2.0F) - (double) this.width,
                    this.posY + 0.5D + (double) (this.rand.nextFloat() * this.height),
                    this.posZ + (double) (this.rand.nextFloat() * this.width * 2.0F) - (double) this.width,
                    this.rand.nextGaussian() * 0.02D, this.rand.nextGaussian() * 0.02D,
                    this.rand.nextGaussian() * 0.02D);
        }

        for (int i = 0; i < 2; i++) {
            this.world.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                    this.posX + (double) (this.rand.nextFloat() * this.width * 2.0F) - (double) this.width,
                    this.posY + 0.5D + (double) (this.rand.nextFloat() * this.height),
                    this.posZ + (double) (this.rand.nextFloat() * this.width * 2.0F) - (double) this.width,
                    this.rand.nextGaussian() * 0.02D, this.rand.nextGaussian() * 0.02D,
                    this.rand.nextGaussian() * 0.02D);
        }
    }

    @Override
    public void applyEntityCollision(Entity entityIn) {
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox() {
        return new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
    }
}
