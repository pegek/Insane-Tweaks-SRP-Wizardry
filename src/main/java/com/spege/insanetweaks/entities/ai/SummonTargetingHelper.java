package com.spege.insanetweaks.entities.ai;

import electroblob.wizardry.entity.living.EntitySummonedCreature;
import net.minecraft.entity.EntityLivingBase;

public final class SummonTargetingHelper {

    private SummonTargetingHelper() {
    }

    public static void syncCasterPriorityTarget(EntitySummonedCreature summon) {
        EntityLivingBase caster = summon.getCaster();

        if (caster == null || !caster.isEntityAlive()) {
            return;
        }

        EntityLivingBase priorityTarget = getCasterAttackTarget(summon, caster);
        if (priorityTarget != null) {
            summon.setAttackTarget(priorityTarget);
            return;
        }

        EntityLivingBase revengeTarget = getCasterRevengeTarget(summon, caster);
        if (revengeTarget != null) {
            summon.setAttackTarget(revengeTarget);
        }
    }

    private static EntityLivingBase getCasterAttackTarget(EntitySummonedCreature summon, EntityLivingBase caster) {
        EntityLivingBase target = caster.getLastAttackedEntity();
        return isValidPriorityTarget(summon, caster, target) ? target : null;
    }

    private static EntityLivingBase getCasterRevengeTarget(EntitySummonedCreature summon, EntityLivingBase caster) {
        EntityLivingBase target = caster.getRevengeTarget();
        return isValidPriorityTarget(summon, caster, target) ? target : null;
    }

    private static boolean isValidPriorityTarget(EntitySummonedCreature summon, EntityLivingBase caster,
            EntityLivingBase target) {
        if (target == null || !target.isEntityAlive()) {
            return false;
        }

        if (target == summon || target == caster) {
            return false;
        }

        if (summon.isOnSameTeam(target) || caster.isOnSameTeam(target)) {
            return false;
        }

        return true;
    }
}
