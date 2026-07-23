package com.spege.insanetweaks.entities.projectile;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;

@SuppressWarnings("null")
public abstract class EntityYelloweyeProjectileBase extends EntityFireball {

    public EntityYelloweyeProjectileBase(World worldIn) {
        super(worldIn);
        this.setSize(0.5F, 0.5F);
    }

    public EntityYelloweyeProjectileBase(World worldIn, EntityLivingBase shooter, double accelX, double accelY,
            double accelZ) {
        super(worldIn, shooter, accelX, accelY, accelZ);
        this.setSize(0.5F, 0.5F);
    }

    @Override
    public boolean isBurning() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected EnumParticleTypes getParticleType() {
        return EnumParticleTypes.SLIME;
    }
}
