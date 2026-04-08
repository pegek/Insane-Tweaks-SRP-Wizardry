package com.spege.insanetweaks.entities.projectile;

import java.util.ArrayList;
import java.util.Collections;

import com.dhanantry.scapeandrunparasites.init.SRPSounds;
import com.dhanantry.scapeandrunparasites.util.config.SRPConfigSystems;
import com.spege.insanetweaks.entities.SummonInfectionSafetyHelper;

import electroblob.wizardry.entity.projectile.EntityMagicArrow;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class EntityYelloweyeGlandProjectile extends EntityMagicArrow {

    private static final int DEFAULT_LIFETIME = 24;
    private static final int DEFAULT_NADE_FUSE = 3;
    private static final int DEFAULT_NADE_LINGER = 60;
    private static final float DEFAULT_NADE_DAMAGE = 5.0F;
    private static final float PROJECTILE_SIZE = 0.5F;

    private float baseDamage = 0.0F;
    private int witherDurationTicks;
    private int witherAmplifier;
    private float potencyMultiplier = 1.0F;
    private double gearDamage;
    private boolean explosive;
    private int nadeFuseTicks = DEFAULT_NADE_FUSE;
    private int nadeLingerTicks = DEFAULT_NADE_LINGER;
    private float nadeBurstDamage = DEFAULT_NADE_DAMAGE;

    public EntityYelloweyeGlandProjectile(World world) {
        super(world);
        this.setSize(PROJECTILE_SIZE, PROJECTILE_SIZE);
    }

    @Override
    protected void entityInit() {
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        float f = net.minecraft.util.math.MathHelper.sqrt(x * x + y * y + z * z);
        x = x / (double)f;
        y = y / (double)f;
        z = z / (double)f;
        x = x + this.rand.nextGaussian() * 0.007499999832361937D * (double)inaccuracy;
        y = y + this.rand.nextGaussian() * 0.007499999832361937D * (double)inaccuracy;
        z = z + this.rand.nextGaussian() * 0.007499999832361937D * (double)inaccuracy;
        x = x * (double)velocity;
        y = y * (double)velocity;
        z = z * (double)velocity;
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
        float f1 = net.minecraft.util.math.MathHelper.sqrt(x * x + z * z);
        this.rotationYaw = (float)(net.minecraft.util.math.MathHelper.atan2(x, z) * (180D / Math.PI));
        this.rotationPitch = (float)(net.minecraft.util.math.MathHelper.atan2(y, (double)f1) * (180D / Math.PI));
        this.prevRotationYaw = this.rotationYaw;
        this.prevRotationPitch = this.rotationPitch;
    }

    @Override
    public double getDamage() {
        return this.explosive ? 0.0D : this.baseDamage;
    }

    @Override
    public int getLifetime() {
        return DEFAULT_LIFETIME;
    }

    @Override
    public boolean doGravity() {
        return false;
    }

    @Override
    public boolean doDeceleration() {
        return false;
    }

    public void setBaseDamage(float baseDamage) {
        this.baseDamage = baseDamage;
    }

    public void setDurationAmplifier(int durationSeconds, int amplifier) {
        this.witherDurationTicks = durationSeconds * 20;
        this.witherAmplifier = amplifier - 1;
    }

    public void setGearDamage(double gearDamage) {
        this.gearDamage = gearDamage;
    }

    public void setPotencyMultiplier(float potencyMultiplier) {
        this.potencyMultiplier = potencyMultiplier;
        this.damageMultiplier = potencyMultiplier;
    }

    public void setExplosiveShot(boolean explosiveShot) {
        this.explosive = explosiveShot;
    }

    public void configureNade(int fuseTicks, int lingerTicks, float burstDamage) {
        this.nadeFuseTicks = fuseTicks;
        this.nadeLingerTicks = lingerTicks;
        this.nadeBurstDamage = burstDamage;
    }

    @Override
    protected void onEntityHit(EntityLivingBase target) {
        if (this.explosive) {
            this.spawnNade(target.posX, target.getEntityBoundingBox().minY + 0.02D, target.posZ);
            this.playSound(SRPSounds.EMANA_SHOOTING, 2.0F, 2.0F);
            return;
        }

        SummonInfectionSafetyHelper.clearCoth(target);

        if (this.witherDurationTicks > 0) {
            int baseAmplifier = Math.max(0, this.witherAmplifier);
            target.addPotionEffect(new PotionEffect(MobEffects.WITHER, this.witherDurationTicks,
                    baseAmplifier + this.getPotencyWitherBonus()));
        }

        this.damageArmor(target, this.gearDamage);
    }

    @Override
    protected void onBlockHit(RayTraceResult rayTrace) {
        if (!this.explosive || rayTrace == null || rayTrace.hitVec == null) {
            return;
        }

        this.spawnNade(rayTrace.hitVec.x, rayTrace.hitVec.y + 0.02D, rayTrace.hitVec.z);
        this.playSound(SRPSounds.EMANA_SHOOTING, 2.0F, 2.0F);
    }

    @Override
    protected void tickInAir() {
        if (!this.world.isRemote) {
            return;
        }

        this.world.spawnParticle(EnumParticleTypes.SLIME,
                this.posX + (this.rand.nextDouble() - 0.5D) * 0.30D,
                this.posY + (this.rand.nextDouble() - 0.5D) * 0.30D,
                this.posZ + (this.rand.nextDouble() - 0.5D) * 0.30D,
                0.0D, 0.0D, 0.0D);

        if (this.explosive && this.ticksExisted > 1) {
            this.world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL,
                    this.posX - this.motionX * 0.5D,
                    this.posY - this.motionY * 0.5D,
                    this.posZ - this.motionZ * 0.5D,
                    0.0D, 0.0D, 0.0D);
        }
    }

    private void spawnNade(double x, double y, double z) {
        if (this.world.isRemote) {
            return;
        }

        EntityYelloweyeNade nade = new EntityYelloweyeNade(this.world, this.nadeFuseTicks, this.nadeLingerTicks,
                this.nadeBurstDamage);
        if (this.getCaster() != null) {
            nade.setOwner(this.getCaster());
        }

        nade.setLocationAndAngles(x, y, z, this.rotationYaw, this.rotationPitch);
        this.world.spawnEntity(nade);
    }

    private int getPotencyWitherBonus() {
        float extraPotency = Math.max(0.0F, this.potencyMultiplier - 1.0F);
        int bonus = (int) Math.floor(extraPotency / 0.5F);
        return Math.min(2, bonus);
    }

    private void damageArmor(EntityLivingBase target, double percentage) {
        ArrayList<ItemStack> gear = new ArrayList<ItemStack>();
        Iterable<ItemStack> worn = target.getArmorInventoryList();

        if (worn == null || worn.equals(Collections.emptyList())) {
            return;
        }

        for (ItemStack part : worn) {
            if (!part.isEmpty() && part.isItemStackDamageable()) {
                gear.add(part);
            }
        }

        for (ItemStack part : gear) {
            if ((double) part.getMaxDamage() * SRPConfigSystems.corrNot
                    >= (double) (part.getMaxDamage() - part.getItemDamage())) {
                continue;
            }

            part.damageItem((int) ((double) part.getMaxDamage() * percentage), target);
        }
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setFloat("BaseDamage", this.baseDamage);
        compound.setInteger("WitherDuration", this.witherDurationTicks);
        compound.setInteger("WitherAmplifier", this.witherAmplifier);
        compound.setFloat("PotencyMultiplier", this.potencyMultiplier);
        compound.setDouble("GearDamage", this.gearDamage);
        compound.setBoolean("Explosive", this.explosive);
        compound.setInteger("NadeFuse", this.nadeFuseTicks);
        compound.setInteger("NadeLinger", this.nadeLingerTicks);
        compound.setFloat("NadeBurstDamage", this.nadeBurstDamage);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.baseDamage = compound.getFloat("BaseDamage");
        this.witherDurationTicks = compound.getInteger("WitherDuration");
        this.witherAmplifier = compound.getInteger("WitherAmplifier");
        this.potencyMultiplier = compound.getFloat("PotencyMultiplier");
        this.damageMultiplier = this.potencyMultiplier;
        this.gearDamage = compound.getDouble("GearDamage");
        this.explosive = compound.getBoolean("Explosive");
        this.nadeFuseTicks = compound.getInteger("NadeFuse");
        this.nadeLingerTicks = compound.getInteger("NadeLinger");
        this.nadeBurstDamage = compound.getFloat("NadeBurstDamage");
    }
}
