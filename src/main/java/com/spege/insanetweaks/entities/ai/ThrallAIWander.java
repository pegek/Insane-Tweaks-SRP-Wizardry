package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.entities.EntityThrallMinion;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.Vec3d;

/**
 * AI task: Idle wander for Thrall.
 * Active only when no other task is running.
 * Uses world.getTotalWorldTime() for tick-based intervals (not System.currentTimeMillis()).
 */
@SuppressWarnings("null")
public class ThrallAIWander extends EntityAIBase {

    private static final long WANDER_INTERVAL_TICKS = 200L;
    private static final double WANDER_SPEED = 0.6D;

    private final EntityThrallMinion thrall;
    private double targetX, targetY, targetZ;
    private long nextWanderTime;

    public ThrallAIWander(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(1);
        this.nextWanderTime = 0L;
    }

    @Override
    public boolean shouldExecute() {
        long now = thrall.world.getTotalWorldTime();
        if (now < nextWanderTime) return false;

        Vec3d vec = RandomPositionGenerator.getLandPos(thrall, 10, 7);
        if (vec == null) return false;

        this.targetX = vec.x;
        this.targetY = vec.y;
        this.targetZ = vec.z;
        this.nextWanderTime = now + WANDER_INTERVAL_TICKS + (long)(thrall.getRNG().nextFloat() * 100);
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !thrall.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        thrall.getNavigator().tryMoveToXYZ(targetX, targetY, targetZ, WANDER_SPEED);
    }
}
