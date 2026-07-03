package com.spege.insanetweaks.entities.logic;

import java.util.List;

import com.spege.insanetweaks.entities.EntityBeckonSivMinion;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

public final class MinionTornadoLogic {

    private static final double MAX_RADIUS = 32.0D;
    private static final double MIN_Y_OFFSET = -5.0D;
    private static final double MAX_HEIGHT = 30.0D;
    private static final double INNER_LIFT_RADIUS = 13.0D;
    private static final double MAX_HEIGHT_BEFORE_BAILOUT = 18.0D;
    private static final double MAX_HORIZONTAL_BAILOUT_DISTANCE = 18.0D;
    private static final double FLING_TRIGGER_HEIGHT = 16.0D;
    private static final double FLING_TRIGGER_RADIUS = 10.0D;
    private static final double FORCE_SCALE = 0.58D;
    private static final int PARTICLE_INTERVAL = 2;
    private static final double PARTICLE_HEIGHT = 18.0D;
    private static final int PARTICLE_LAYERS = 8;
    private static final int PARTICLES_PER_LAYER = 6;

    private MinionTornadoLogic() {
    }

    public static void tickTornadoEffects(EntityBeckonSivMinion minion) {
        if (minion == null) {
            return;
        }

        World world = minion.world;
        if (world == null || world.isRemote) {
            return;
        }

        spawnTornadoParticles(minion);

        AxisAlignedBB box = new AxisAlignedBB(minion.posX - MAX_RADIUS, minion.posY + MIN_Y_OFFSET,
                minion.posZ - MAX_RADIUS, minion.posX + MAX_RADIUS, minion.posY + MAX_HEIGHT,
                minion.posZ + MAX_RADIUS);
        List<Entity> targets = world.getEntitiesWithinAABBExcludingEntity(minion, box);

        if (targets.isEmpty()) {
            return;
        }

        for (Entity entity : targets) {
            if (!(entity instanceof EntityLivingBase)) {
                continue;
            }

            EntityLivingBase target = (EntityLivingBase) entity;
            if (target == minion || !target.isEntityAlive() || target.posY < minion.posY + MIN_Y_OFFSET) {
                continue;
            }

            if (minion.isOnSameTeam(target) || target == minion.getCaster()) {
                continue;
            }

            if (target instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) target;
                if (player.isCreative() || player.isSpectator()) {
                    continue;
                }
            }

            applyTornadoForces(minion, target);
        }
    }

    private static void spawnTornadoParticles(EntityBeckonSivMinion minion) {
        if (!(minion.world instanceof WorldServer) || minion.ticksExisted % PARTICLE_INTERVAL != 0) {
            return;
        }

        WorldServer world = (WorldServer) minion.world;
        double baseAngle = (minion.ticksExisted * 0.18D) % (Math.PI * 2.0D);

        for (int layer = 0; layer < PARTICLE_LAYERS; layer++) {
            double heightProgress = (double) layer / (double) (PARTICLE_LAYERS - 1);
            double y = minion.posY + 0.4D + PARTICLE_HEIGHT * heightProgress;
            double radius = 1.6D + 5.6D * heightProgress;

            for (int step = 0; step < PARTICLES_PER_LAYER; step++) {
                double angle = baseAngle + heightProgress * 1.7D + step * ((Math.PI * 2.0D) / PARTICLES_PER_LAYER);
                double x = minion.posX + Math.cos(angle) * radius;
                double z = minion.posZ + Math.sin(angle) * radius;

                world.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, 1, 0.03D, 0.02D, 0.03D, 0.0D);
                if ((layer + step) % 2 == 0) {
                    world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y, z, 1, 0.02D, 0.03D, 0.02D, 0.0D);
                }
            }
        }

        world.spawnParticle(EnumParticleTypes.CLOUD, minion.posX, minion.posY + 1.0D, minion.posZ, 4,
                0.35D, 0.15D, 0.35D, 0.01D);
    }

    private static void applyTornadoForces(EntityBeckonSivMinion minion, EntityLivingBase target) {
        if (target.isRiding() || target.isDead) {
            return;
        }

        double dx = minion.posX - target.posX;
        double dz = minion.posZ - target.posZ;
        double distSq = dx * dx + dz * dz;

        if (distSq < 1.0E-4D) {
            distSq = 1.0E-4D;
        }

        double horizontalDistance = Math.sqrt(distSq);
        if (horizontalDistance > MAX_RADIUS) {
            return;
        }

        double normX = dx / horizontalDistance;
        double normZ = dz / horizontalDistance;

        double pullTierFactor = horizontalDistance >= 30.0D ? 0.05D
                : (horizontalDistance >= 20.0D ? 0.1D
                        : (horizontalDistance >= 10.0D ? 0.35D
                                : (horizontalDistance >= 5.0D ? 0.55D : 1.0D)));
        double pullStrength = 0.08D * pullTierFactor * FORCE_SCALE;
        double swirlStrength = 0.07D * pullTierFactor * FORCE_SCALE;

        double swirlX = -normZ;
        double swirlZ = normX;
        double liftAcceleration = 0.0D;

        if (horizontalDistance <= INNER_LIFT_RADIUS) {
            double liftFactor = 1.0D - horizontalDistance / INNER_LIFT_RADIUS;
            if (liftFactor < 0.0D) {
                liftFactor = 0.0D;
            } else if (liftFactor > 1.0D) {
                liftFactor = 1.0D;
            }

            liftAcceleration = 0.25D * liftFactor * FORCE_SCALE;
        }

        double heightAboveMinion = target.posY - minion.posY;
        boolean inFlingZone = heightAboveMinion > FLING_TRIGGER_HEIGHT && horizontalDistance < FLING_TRIGGER_RADIUS;

        if (heightAboveMinion > MAX_HEIGHT_BEFORE_BAILOUT
                && horizontalDistance > MAX_HORIZONTAL_BAILOUT_DISTANCE) {
            return;
        }

        double radialDirX = normX;
        double radialDirZ = normZ;

        if (inFlingZone) {
            radialDirX = -normX;
            radialDirZ = -normZ;

            double flingFactor = 1.0D - Math.min(horizontalDistance / FLING_TRIGGER_RADIUS, 1.0D);
            double flingMultiplier = 1.4D + (6.0D - 1.4D) * flingFactor;
            double swirlMultiplier = 1.0D + (2.3D - 1.0D) * flingFactor;

            pullStrength *= flingMultiplier;
            swirlStrength *= swirlMultiplier;

            target.motionX += radialDirX * (1.1D * flingFactor * FORCE_SCALE);
            target.motionZ += radialDirZ * (1.1D * flingFactor * FORCE_SCALE);
            target.motionY += -0.16D * flingFactor * FORCE_SCALE;
            if (target.motionY < -1.6D) {
                target.motionY = -1.6D;
            }
        } else if (liftAcceleration > 0.0D && heightAboveMinion < 25.0D) {
            target.motionY += liftAcceleration;
            if (target.motionY > 1.2D) {
                target.motionY = 1.2D;
            }
            target.fallDistance = 0.0F;
        }

        double awayX = -normX;
        double awayZ = -normZ;
        double dotAway = target.motionX * awayX + target.motionZ * awayZ;
        double forceScale = dotAway > 0.0D ? 1.0D : 0.25D;

        pullStrength *= forceScale;
        target.motionX += radialDirX * pullStrength + swirlX * swirlStrength;
        target.motionZ += radialDirZ * pullStrength + swirlZ * swirlStrength;
        target.velocityChanged = true;
    }
}
