package com.spege.insanetweaks.events;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.entities.EntitySimWizard;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * v3.1: SRP faction integration for the sim_wizard.
 *
 * Root cause of "other parasites attack our sim_wizard": friendly fire. The wizard's AoE
 * spells (spark_bomb chain lightning, force_orb blast) hit nearby SRP parasites fighting
 * the same player. Vanilla {@code EntityAIHurtByTarget} has no parasite exclusion and SRP
 * mobs use it with {@code entityCallsForHelp = true}, so one stray hit turned the whole
 * pack against the wizard - and his own retaliation task kept the civil war going.
 *
 * Fix: cancel all damage between {@link EntitySimWizard} and {@link EntityParasiteBase}
 * in BOTH directions, at {@link LivingAttackEvent}. Cancelling here (not LivingHurtEvent)
 * matters: {@code EntityLivingBase.attackEntityFrom} returns before
 * {@code setRevengeTarget} is called, so the revenge target is never set and
 * {@code EntityAIHurtByTarget} never triggers - the aggro loop cannot even start.
 *
 * Server-side only; registered in InsaneTweaksMod.init gated by
 * {@code ModConfig.entities.assimilatedWizard.spawning.enabled}.
 */
public class SimWizardFactionHandler {

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim == null || victim.world == null || victim.world.isRemote) {
            return;
        }

        Entity trueSource = event.getSource() == null ? null : event.getSource().getTrueSource();
        if (trueSource == null) {
            return;
        }

        // sim_wizard -> any parasite (covers spark_bomb chains, force_orb splash, melee)
        if (victim instanceof EntityParasiteBase && trueSource instanceof EntitySimWizard) {
            event.setCanceled(true);
            return;
        }

        // any parasite -> sim_wizard (stray reeker clouds, AoE from other parasites, etc.)
        if (victim instanceof EntitySimWizard && trueSource instanceof EntityParasiteBase) {
            event.setCanceled(true);
        }
    }
}
