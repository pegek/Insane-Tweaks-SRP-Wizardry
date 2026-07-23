package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.entities.SentinelCommandMode;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.math.BlockPos;

public class EntityAISentinelReturnToAnchor extends EntityAIBase {

    private final EntitySentinel sentinel;
    private final double speed;

    public EntityAISentinelReturnToAnchor(EntitySentinel sentinel, double speed) {
        this.sentinel = sentinel;
        this.speed = speed;
        this.setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (this.sentinel.getCommandMode() != SentinelCommandMode.GUARD) {
            return false;
        }

        BlockPos anchor = this.sentinel.getGuardAnchor();
        if (anchor == null) {
            return false;
        }

        return this.sentinel.getAttackTarget() == null && this.sentinel.getDistanceSqToGuardAnchor() > 9.0D;
    }

    @Override
    public boolean shouldContinueExecuting() {
        BlockPos anchor = this.sentinel.getGuardAnchor();
        return this.sentinel.getCommandMode() == SentinelCommandMode.GUARD
                && anchor != null
                && this.sentinel.getAttackTarget() == null
                && this.sentinel.getDistanceSqToGuardAnchor() > 4.0D
                && !this.sentinel.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        BlockPos anchor = this.sentinel.getGuardAnchor();
        if (anchor != null) {
            this.sentinel.getNavigator().tryMoveToXYZ(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D,
                    this.speed);
        }
    }

    @Override
    public void updateTask() {
        BlockPos anchor = this.sentinel.getGuardAnchor();
        if (anchor != null && this.sentinel.getNavigator().noPath()) {
            this.sentinel.getNavigator().tryMoveToXYZ(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D,
                    this.speed);
        }
    }
}
