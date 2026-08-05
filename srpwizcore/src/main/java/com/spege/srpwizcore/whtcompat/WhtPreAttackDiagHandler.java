package com.spege.srpwizcore.whtcompat;

import arekkuusu.betterhurttimer.api.event.PreLivingAttackEvent;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Records non-melee attack outcomes from WorseHurtTimer's own {@code PreLivingAttackEvent}.
 *
 * <p>Kept in a class of its own, registered only behind a {@code Loader.isModLoaded} check,
 * because its listener method's parameter type comes from WorseHurtTimer. Forge reflects over
 * parameter types when registering, so folding this into {@link WhtDiagHandler} would make that
 * whole handler fail to register on a pack without the mod.
 *
 * <p>{@link EventPriority#LOWEST} with {@code receiveCanceled} puts us after WHT's own handler,
 * which runs at the default priority — so {@code isCanceled()} is its verdict, not a half-formed
 * one. See {@link WhtDiag#recordPreAttack} for why this listener has to exist at all.
 */
public class WhtPreAttackDiagHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onPreLivingAttack(PreLivingAttackEvent event) {
        if (!WhtDiag.ENABLED) {
            return;
        }
        final EntityLivingBase victim = event.getEntityLiving();
        if (victim == null || victim.world.isRemote) {
            return;
        }
        WhtDiag.recordPreAttack(victim, event.getSource().getTrueSource(),
                event.getSource().getDamageType(), event.isCanceled(), event.wasStalled());
    }
}
