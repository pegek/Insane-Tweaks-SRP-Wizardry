package com.spege.insanetweaks.entities;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;

import net.minecraft.entity.EntityLivingBase;

@SuppressWarnings("null")
public final class SummonInfectionSafetyHelper {

    private SummonInfectionSafetyHelper() {
    }

    public static void clearCoth(EntityLivingBase target) {
        if (target != null && target.isPotionActive(SRPPotions.COTH_E)) {
            target.removePotionEffect(SRPPotions.COTH_E);
        }
    }
}
