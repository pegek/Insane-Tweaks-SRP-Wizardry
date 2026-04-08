package com.spege.insanetweaks.entities.projectile;

import java.util.List;

import com.dhanantry.scapeandrunparasites.init.SRPSounds;
import com.spege.insanetweaks.entities.SummonInfectionSafetyHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class EntityYelloweyeNade extends Entity {

    private static final float INITIAL_WIDTH = 0.5F;
    private static final float INITIAL_HEIGHT = 0.5F;
    private static final float MAX_WIDTH = 2.15F;
    private static final float MAX_HEIGHT = 0.22F;

    private int fuseTicks = 3;
    private int lingerTicks = 60;
    private int activeTicks = 0;
    private int lastActiveTime = 0;
    private int timeSinceIgnited = 0;
    private EntityLivingBase owner;
    private float burstDamage = 5.0F;
    private float burstRadius = 1.45F;
    private double anchorX;
    private double anchorZ;

    public EntityYelloweyeNade(World worldIn) {
        super(worldIn);
        this.setSize(INITIAL_WIDTH, INITIAL_HEIGHT);
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
        this.lastActiveTime = this.timeSinceIgnited;

        this.updateVisualSize();

        if (this.ticksExisted <= 3) {
            this.anchorX = this.posX;
            this.anchorZ = this.posZ;

            if (this.ticksExisted == 2) {
                this.playSound(SRPSounds.NADE_S, 1.0F, 1.0F);
            }
        } else {
            this.posX = this.anchorX;
            this.posZ = this.anchorZ;
            if (this.onGround) {
                this.posY += this.rand.nextDouble() * 0.01D;
            }
        }

        if (this.world.isRemote) {
            this.spawnAcidVisuals();
            return;
        }

        if (this.ticksExisted < this.fuseTicks) {
            this.timeSinceIgnited = Math.min(this.timeSinceIgnited + 1, this.fuseTicks);
            return;
        }

        this.timeSinceIgnited = this.fuseTicks;
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
            SummonInfectionSafetyHelper.clearCoth(target);
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

    private void spawnAcidVisuals() {
        this.spawnSmoke();

        float visualRadius = Math.max(0.7F, this.width * 0.55F);
        for (int i = 0; i < 6; i++) {
            this.world.spawnParticle(EnumParticleTypes.SLIME,
                    this.posX + (double) ((this.rand.nextFloat() - 0.5F) * visualRadius * 2.0F),
                    this.posY + 0.08D,
                    this.posZ + (double) ((this.rand.nextFloat() - 0.5F) * visualRadius * 2.0F),
                    this.rand.nextGaussian() * 0.01D,
                    0.005D + this.rand.nextDouble() * 0.01D,
                    this.rand.nextGaussian() * 0.01D);
        }
    }

    private void updateVisualSize() {
        if (this.ticksExisted <= 3) {
            this.setSize(INITIAL_WIDTH, INITIAL_HEIGHT);
            return;
        }

        int growthTicks = Math.max(1, this.fuseTicks);
        float progress = Math.min(1.0F, (float) (this.ticksExisted - 3) / (float) growthTicks);

        float width = INITIAL_WIDTH + (MAX_WIDTH - INITIAL_WIDTH) * progress;
        float height = INITIAL_HEIGHT + (MAX_HEIGHT - INITIAL_HEIGHT) * progress;
        this.setSize(width, height);
    }

    public float getSelfeFlashIntensity(float partialTicks) {
        int denominator = Math.max(1, this.fuseTicks - 2);
        return ((float) this.lastActiveTime + (float) (this.timeSinceIgnited - this.lastActiveTime) * partialTicks * 5.0F)
                / (float) denominator;
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
