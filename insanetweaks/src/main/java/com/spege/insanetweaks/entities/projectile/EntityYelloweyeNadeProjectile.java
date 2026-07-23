package com.spege.insanetweaks.entities.projectile;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class EntityYelloweyeNadeProjectile extends EntityYelloweyeProjectileBase {

    private int fuseTicks = 3;
    private int lingerTicks = 60;
    private float burstDamage = 5.0F;

    public EntityYelloweyeNadeProjectile(World worldIn) {
        super(worldIn);
        this.setSize(0.3F, 0.3F);
    }

    public EntityYelloweyeNadeProjectile(World worldIn, EntityLivingBase shooter, double accelX, double accelY,
            double accelZ, int fuseTicks, int lingerTicks, float burstDamage) {
        super(worldIn, shooter, accelX, accelY, accelZ);
        this.setSize(0.3F, 0.3F);
        this.fuseTicks = fuseTicks;
        this.lingerTicks = lingerTicks;
        this.burstDamage = burstDamage;
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        if (this.world.isRemote) {
            return;
        }

        EntityYelloweyeNade nade = new EntityYelloweyeNade(this.world, this.fuseTicks, this.lingerTicks,
                this.burstDamage);
        nade.setOwner(this.shootingEntity);

        double spawnX = this.posX;
        double spawnY = this.posY;
        double spawnZ = this.posZ;

        if (result != null) {
            if (result.entityHit instanceof EntityLivingBase) {
                EntityLivingBase target = (EntityLivingBase) result.entityHit;
                spawnX = target.posX;
                spawnY = target.getEntityBoundingBox().minY + 0.02D;
                spawnZ = target.posZ;
            } else if (result.hitVec != null) {
                spawnX = result.hitVec.x;
                spawnY = result.hitVec.y + 0.02D;
                spawnZ = result.hitVec.z;
            }
        }

        nade.setLocationAndAngles(spawnX, spawnY, spawnZ, this.rotationYaw, this.rotationPitch);
        this.world.spawnEntity(nade);
        this.setDead();
    }
}
