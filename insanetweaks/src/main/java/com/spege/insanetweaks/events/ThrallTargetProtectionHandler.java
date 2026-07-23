package com.spege.insanetweaks.events;

import com.spege.insanetweaks.entities.EntityThrallMinion;

import net.minecraft.entity.EntityLiving;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Guarantees invariant B: EVERY mob ignores the immortal utility thrall.
 *
 * <p>Whenever any {@link EntityLiving} sets its attack target to a thrall, this handler clears that
 * target (and the aggressor's revenge target) on the same tick. The event is generic across all
 * mods' EntityLiving mobs — vanilla hostiles, SRP parasites, and anything else that routes through
 * the vanilla targeting system. Server-side only (targeting AI never runs client-side).
 *
 * <p>This is the always-on primary layer. SRP additionally gets a config-blacklist append at startup
 * (see InsaneTweaksMod / SRPConfig.mobattackingBlackList) so its selectors never even build a path
 * to the thrall, but that append can be undone by a mid-game config reload — this handler cannot.
 */
public class ThrallTargetProtectionHandler {

    @SubscribeEvent
    public void onSetAttackTarget(LivingSetAttackTargetEvent event) {
        if (!(event.getTarget() instanceof EntityThrallMinion)) {
            return;
        }
        if (!(event.getEntityLiving() instanceof EntityLiving)) {
            return;
        }

        EntityLiving aggressor = (EntityLiving) event.getEntityLiving();
        if (aggressor.world.isRemote) {
            return;
        }

        aggressor.setAttackTarget(null);
        aggressor.setRevengeTarget(null);
        // Deliberate: stops pursuit of the thrall instantly. A mob with other prey re-plans
        // its path next AI tick, so the worst case is a brief hitch, not lost targeting.
        aggressor.getNavigator().clearPath();
    }
}
