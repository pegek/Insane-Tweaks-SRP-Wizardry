package com.spege.insanetweaks.entities;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;

@SuppressWarnings("null")
public final class SummonInfectionSafetyHelper {

    private static final int EPEL_DURATION_TICKS = 400;
    private static final int EPEL_REFRESH_THRESHOLD_TICKS = 100;

    private SummonInfectionSafetyHelper() {
    }

    public static void onSuccessfulSummonHit(EntityLivingBase target) {
        clearCoth(target);
    }

    public static void onSummonServerTick(EntityLivingBase summon) {
        refreshParasiteRepelProtection(summon);
    }

    public static void clearCoth(EntityLivingBase target) {
        if (target != null && target.isPotionActive(SRPPotions.COTH_E)) {
            target.removePotionEffect(SRPPotions.COTH_E);
        }
    }

    private static void refreshParasiteRepelProtection(EntityLivingBase summon) {
        if (summon == null) {
            return;
        }

        PotionEffect repel = summon.getActivePotionEffect(SRPPotions.EPEL_E);
        if (repel == null || repel.getDuration() < EPEL_REFRESH_THRESHOLD_TICKS) {
            summon.addPotionEffect(new PotionEffect(SRPPotions.EPEL_E, EPEL_DURATION_TICKS, 0, false, false));
        }
        
        // Apply hardcoded NBT immunity as EPEL_E is sometimes bypassed by SRP.
        // Since SRP 1.10.7, srpcothimmunity == 0 means immune (base-case COTH
        // termination); any non-zero value marks a tracked COTH victim that
        // PotionCOTH/ParasiteEventEntity will eventually convert into a parasite.
        if (!summon.getEntityData().hasKey("srpcothimmunity")
                || summon.getEntityData().getInteger("srpcothimmunity") != 0) {
            summon.getEntityData().setInteger("srpcothimmunity", 0);
        }
    }
}
