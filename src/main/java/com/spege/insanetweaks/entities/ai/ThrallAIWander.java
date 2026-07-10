package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallMode;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * AI task: Idle wander for Thrall.
 * Active only when no other task is running.
 * Uses world.getTotalWorldTime() for tick-based intervals (not System.currentTimeMillis()).
 *
 * STAY mode (spec 2026-07-10 A2): the stroll is anchored — targets are confined to
 * thrall.general.stayWanderRadius blocks around the STAY anchor and movement is slower
 * (stayWanderSpeed). If the thrall is pushed outside the radius it paths back to the anchor.
 */
@SuppressWarnings("null")
public class ThrallAIWander extends EntityAIBase {

    private static final long WANDER_INTERVAL_TICKS = 200L;
    private static final double WANDER_SPEED = 0.6D;

    private final EntityThrallMinion thrall;
    private double targetX, targetY, targetZ;
    private double moveSpeed;
    private long nextWanderTime;

    public ThrallAIWander(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(1);
        this.nextWanderTime = 0L;
    }

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() == ThrallMode.STAY && thrall.getStayAnchor() != null) {
            return shouldExecuteStay(thrall.getStayAnchor());
        }
        return shouldExecuteDefault();
    }

    private boolean shouldExecuteDefault() {
        long now = thrall.world.getTotalWorldTime();
        if (now < nextWanderTime) return false;

        Vec3d vec = RandomPositionGenerator.getLandPos(thrall, 10, 7);
        if (vec == null) return false;

        this.targetX = vec.x;
        this.targetY = vec.y;
        this.targetZ = vec.z;
        this.moveSpeed = WANDER_SPEED;
        this.nextWanderTime = now + WANDER_INTERVAL_TICKS + (long)(thrall.getRNG().nextFloat() * 100);
        return true;
    }

    private boolean shouldExecuteStay(BlockPos anchor) {
        int radius = ModConfig.thrall.general.stayWanderRadius;
        double radiusSq = (double) radius * radius;

        // Pushed/displaced outside the radius — return to the anchor immediately.
        if (thrall.getDistanceSqToCenter(anchor) > radiusSq) {
            this.targetX = anchor.getX() + 0.5D;
            this.targetY = anchor.getY();
            this.targetZ = anchor.getZ() + 0.5D;
            this.moveSpeed = ModConfig.thrall.general.stayWanderSpeed;
            return true;
        }

        long now = thrall.world.getTotalWorldTime();
        if (now < nextWanderTime) return false;

        Vec3d vec = RandomPositionGenerator.getLandPos(thrall, radius, 3);
        if (vec == null) return false;
        // getLandPos is centred on the THRALL, not the anchor — reject picks that
        // would drift outside the anchor radius.
        if (anchor.distanceSq(vec.x, vec.y, vec.z) > radiusSq) return false;

        this.targetX = vec.x;
        this.targetY = vec.y;
        this.targetZ = vec.z;
        this.moveSpeed = ModConfig.thrall.general.stayWanderSpeed;
        this.nextWanderTime = now + WANDER_INTERVAL_TICKS + (long)(thrall.getRNG().nextFloat() * 100);
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !thrall.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        thrall.getNavigator().tryMoveToXYZ(targetX, targetY, targetZ, moveSpeed);
    }
}
