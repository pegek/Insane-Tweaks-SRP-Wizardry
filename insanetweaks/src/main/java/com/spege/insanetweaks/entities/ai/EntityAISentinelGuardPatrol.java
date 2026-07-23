package com.spege.insanetweaks.entities.ai;

import java.util.List;

import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.entities.SentinelCommandMode;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.BlockPos;

public class EntityAISentinelGuardPatrol extends EntityAIBase {

    private final EntitySentinel sentinel;
    private final double speed;
    private BlockPos patrolTarget;
    private int idleDelay;

    public EntityAISentinelGuardPatrol(EntitySentinel sentinel, double speed) {
        this.sentinel = sentinel;
        this.speed = speed;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (this.sentinel.getCommandMode() != SentinelCommandMode.GUARD) {
            return false;
        }

        if (this.sentinel.getAttackTarget() != null || this.sentinel.getGuardAnchor() == null) {
            return false;
        }

        if (this.idleDelay > 0) {
            this.idleDelay--;
            return false;
        }

        List<BlockPos> points = this.sentinel.getGuardPatrolPoints();
        if (points.isEmpty()) {
            return false;
        }

        this.patrolTarget = points.get(this.sentinel.getRNG().nextInt(points.size()));
        return this.patrolTarget != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.sentinel.getCommandMode() == SentinelCommandMode.GUARD
                && this.sentinel.getAttackTarget() == null
                && this.patrolTarget != null
                && !this.sentinel.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        if (this.patrolTarget != null) {
            this.sentinel.getNavigator().tryMoveToXYZ(this.patrolTarget.getX() + 0.5D, this.patrolTarget.getY(),
                    this.patrolTarget.getZ() + 0.5D, this.speed);
        }
    }

    @Override
    public void resetTask() {
        this.patrolTarget = null;
        this.idleDelay = 30 + this.sentinel.getRNG().nextInt(50);
    }
}
