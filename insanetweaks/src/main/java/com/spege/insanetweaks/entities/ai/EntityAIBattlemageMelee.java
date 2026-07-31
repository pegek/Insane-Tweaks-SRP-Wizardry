package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.EntitySimBattlemage;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIAttackMelee;

/**
 * Melee engagement for {@link EntitySimBattlemage}: swing the spellblade while healthy and close,
 * fall back to spellcasting once wounded or once the target is out of reach.
 *
 * <h3>Why this is a task and not a state inside the cast task</h3>
 * The panic reaction lives INSIDE {@code EntityAISimWizardCombat} because a task added at a higher
 * priority number could never pre-empt it (1.12.2 {@code EntityAITasks} only lets a LOWER-numbered
 * priority interrupt a running task with overlapping mutex bits). Melee is the opposite case: it is
 * registered at priority 2, BELOW the cast task's 3, so it legitimately interrupts casting - which
 * is precisely the behaviour wanted. {@code EntityAIAttackMelee} declares mutex 3, the same bits
 * the cast task uses, so exactly one of the two owns movement at any moment and the "two tasks
 * fight over the navigator" failure mode of v3.x cannot return.
 *
 * <h3>Why the distance gate matters</h3>
 * Without it a healthy battlemage would melee unconditionally and never cast at all. Gating on
 * "already within reach" means it stays an artillery caster at range and becomes a duelist only
 * once the fight is actually close - so it still casts while healthy, just less.
 */
public class EntityAIBattlemageMelee extends EntityAIAttackMelee {

    private final EntitySimBattlemage battlemage;

    public EntityAIBattlemageMelee(EntitySimBattlemage battlemage) {
        super(battlemage, cfg().meleeMoveSpeed, true);
        this.battlemage = battlemage;
    }

    private static EntitiesCategory.Battlemage cfg() {
        return ModConfig.entities.assimilatedWizard.battlemage;
    }

    /**
     * 🚨 Absolute distances only - no decrementing counters anywhere in this class. This task is
     * polled every 3 ticks like every other, and the v3.1 lesson was that a tick counter written
     * outside {@code updateTask} runs at a third of the intended rate.
     */
    private boolean healthyEnough() {
        int pct = cfg().meleeHealthPercent;
        if (pct <= 0) {
            return false;
        }
        return (this.battlemage.getHealth() / this.battlemage.getMaxHealth()) * 100.0F > pct;
    }

    private boolean withinRange(double maxDistance) {
        EntityLivingBase target = this.battlemage.getAttackTarget();
        if (target == null) {
            return false;
        }
        return this.battlemage.getDistance(target) <= maxDistance;
    }

    @Override
    public boolean shouldExecute() {
        return healthyEnough()
                && withinRange(cfg().meleeEngageDistance)
                && super.shouldExecute();
    }

    @Override
    public boolean shouldContinueExecuting() {
        // Wider band than the entry gate so a target backing off a single block does not flip the
        // battlemage between melee and casting every poll.
        double disengage = cfg().meleeEngageDistance * cfg().meleeDisengageMultiplier;
        return healthyEnough()
                && withinRange(disengage)
                && super.shouldContinueExecuting();
    }

    @Override
    public void resetTask() {
        super.resetTask();
        // Handing movement back to the cast task with a stale path would make the battlemage keep
        // walking at its melee target for a few ticks after it decided to start casting again.
        this.battlemage.getNavigator().clearPath();
    }
}
