package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallMode;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * AI task: Follow the caster.
 * Active only in FOLLOW mode.
 * Uses tick-based timers (world.getTotalWorldTime()), NOT System.currentTimeMillis().
 */
@SuppressWarnings("null")
public class ThrallAIFollowCaster extends EntityAIBase {

    private static final double FOLLOW_SPEED = 0.82D;
    private static final float MIN_DIST = 3.0F;
    private static final float MAX_DIST = 12.0F;
    private static final float TELEPORT_DIST_SQ = 32.0F * 32.0F;
    private static final int PATH_UPDATE_INTERVAL = 30; // ticks

    private final EntityThrallMinion thrall;
    private int updateTimer;
    private int pathFailCount;

    public ThrallAIFollowCaster(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() != ThrallMode.FOLLOW) return false;
        EntityLivingBase caster = thrall.getCaster();
        if (caster == null || caster.isDead) return false;
        double distSq = thrall.getDistanceSq(caster);
        return distSq > (MIN_DIST * MIN_DIST);
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (thrall.getMode() != ThrallMode.FOLLOW) return false;
        EntityLivingBase caster = thrall.getCaster();
        if (caster == null || caster.isDead) return false;
        return !thrall.getNavigator().noPath()
                && thrall.getDistanceSq(caster) > (MAX_DIST * MAX_DIST);
    }

    @Override
    public void startExecuting() {
        updateTimer = 0;
        pathFailCount = 0;
    }

    @Override
    public void resetTask() {
        thrall.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        EntityLivingBase caster = thrall.getCaster();
        if (caster == null) return;

        thrall.getLookHelper().setLookPositionWithEntity(caster, 10.0F, (float) thrall.getVerticalFaceSpeed());

        if (--updateTimer <= 0) {
            updateTimer = PATH_UPDATE_INTERVAL;

            boolean pathSuccess = thrall.getNavigator().tryMoveToEntityLiving(caster, FOLLOW_SPEED);

            if (!pathSuccess) {
                pathFailCount++;
                if (pathFailCount >= 3 && thrall.getDistanceSq(caster) > TELEPORT_DIST_SQ) {
                    teleportNearCaster(caster);
                    pathFailCount = 0;
                }
            } else {
                pathFailCount = 0;
            }
        }
    }

    private void teleportNearCaster(EntityLivingBase caster) {
        World world = thrall.world;
        int x = MathHelper.floor(caster.posX) - 2;
        int z = MathHelper.floor(caster.posZ) - 2;
        int y = MathHelper.floor(caster.getEntityBoundingBox().minY);

        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                // Avoid corners (stay 1 block from edge)
                if (dx < 1 || dz < 1 || dx > 3 || dz > 3) continue;
                BlockPos pos = new BlockPos(x + dx, y, z + dz);
                if (world.getBlockState(pos.down()).isSideSolid(world, pos.down(), net.minecraft.util.EnumFacing.UP)
                        && world.isAirBlock(pos) && world.isAirBlock(pos.up())) {
                    thrall.playTeleportSound();
                    thrall.setPositionAndUpdate(x + dx + 0.5, y, z + dz + 0.5);
                    thrall.playTeleportSound();
                    thrall.getNavigator().clearPath();
                    return;
                }
            }
        }
    }
}
