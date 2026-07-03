package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.entities.EntitySimWizard;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.Vec3d;

/**
 * v3.2: caster movement discipline. Replaces the SRP melee chase as the wizard's default
 * combat movement - a caster should hold its ground and cast, not sprint into fist range.
 *
 * Behaviour by distance to the attack target:
 *   - closer than {@link #RETREAT_DISTANCE}: back away (kite) at a slight speed bonus
 *   - farther than {@link #APPROACH_DISTANCE}: close in slowly to stay inside cast range
 *   - in the sweet spot: stand still and face the target (the cast task, priority 3 with
 *     mutex 3, freely takes over whenever its cooldown is ready)
 *
 * Hand-off to melee: {@link #shouldExecute()} yields when the target is within
 * {@link #MELEE_HANDOFF_DISTANCE}. The SRP melee task sits BELOW this one (priority 5 vs 4)
 * with a conflicting mutex, so it can only run when this task declines - i.e. the wizard
 * claws only when genuinely cornered, which is exactly the "much less eager to melee"
 * behaviour requested after the v3.1 playtest.
 *
 * Mutex 1 (movement only) so the look control stays free for the cast/look tasks.
 */
public class EntityAISimWizardKite extends EntityAIBase {

    /** Inside this distance the task yields and the cornered melee fallback may run. */
    private static final double MELEE_HANDOFF_DISTANCE = 3.0D;
    /** Closer than this -> back away. */
    private static final double RETREAT_DISTANCE = 7.0D;
    /** Farther than this -> approach. */
    private static final double APPROACH_DISTANCE = 18.0D;
    /** Recompute the path only this often (ticks) - pathing every tick wastes server time. */
    private static final int REPATH_INTERVAL = 10;

    private final EntitySimWizard wizard;
    private final double speed;
    private int repathTimer;

    public EntityAISimWizardKite(EntitySimWizard wizard, double speed) {
        this.wizard = wizard;
        this.speed = speed;
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = this.wizard.getAttackTarget();
        if (!EntityAISimWizardCast.isValidSpellTarget(target, this.wizard)) {
            return false;
        }
        return this.wizard.getDistance(target) > MELEE_HANDOFF_DISTANCE;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        this.repathTimer = 0;
    }

    @Override
    public void resetTask() {
        this.wizard.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = this.wizard.getAttackTarget();
        if (target == null) {
            return;
        }

        // Always face the fight even while repositioning.
        this.wizard.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);

        if (--this.repathTimer > 0) {
            return;
        }
        this.repathTimer = REPATH_INTERVAL;

        double dist = this.wizard.getDistance(target);

        if (dist < RETREAT_DISTANCE) {
            // Back away from the target; slight speed bonus so kiting actually opens distance.
            Vec3d away = RandomPositionGenerator.findRandomTargetBlockAwayFrom(this.wizard, 8, 4,
                    new Vec3d(target.posX, target.posY, target.posZ));
            if (away != null) {
                this.wizard.getNavigator().tryMoveToXYZ(away.x, away.y, away.z, this.speed * 1.25D);
            }
        } else if (dist > APPROACH_DISTANCE) {
            this.wizard.getNavigator().tryMoveToEntityLiving(target, this.speed);
        } else {
            // Sweet spot: hold position, let the cast task do the talking.
            this.wizard.getNavigator().clearPath();
        }
    }
}
