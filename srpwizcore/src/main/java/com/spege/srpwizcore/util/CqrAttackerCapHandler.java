package com.spege.srpwizcore.util;

import java.util.List;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Experimental attacker cap (user request 2026-08-01): at most {@code attackerCap} CQR
 * mobs may target the same player simultaneously — the reach/standoff/siege changes make
 * each mob individually stronger, so the swarm gets a headcount limit.
 *
 * <p>{@code LivingSetAttackTargetEvent} fires from every {@code setAttackTarget} call
 * ({@code AbstractEntityCQR} overrides it but calls super — verified in bytecode) — AI
 * acquisition AND hurt retaliation both funnel through it. The event is not cancellable,
 * so an over-cap acquisition is reverted by immediately nulling the target
 * (reentrancy-guarded — the null set fires the event again).
 *
 * <p>The count is AUTHORITATIVE, not book-kept (v2 after the 1.8.8 WeakHashMap ledger
 * missed live state): every acquisition scans loaded CQR mobs within 48 blocks of the
 * player and counts those whose CURRENT attack target is that player. The scan only runs
 * for team.cqr.* entities acquiring a player target, so the cost lives entirely inside
 * dungeon fights. Strict cap: a 7th mob will not fight back even when hit, it retries
 * every tick until a slot frees (death, despawn, target change all free slots inherently).
 * Flag read live; {@code attackerCap = 0} disables.
 */
public class CqrAttackerCapHandler {

    /** Mobs farther than this from the player never block a slot (stale far chasers). */
    private static final double COUNT_RADIUS = 48.0D;

    private static boolean reverting;
    private static long denials;

    @SubscribeEvent
    public void onSetAttackTarget(LivingSetAttackTargetEvent event) {
        if (reverting) {
            return;
        }
        EntityLivingBase ent = event.getEntityLiving();
        if (ent.world.isRemote || !(ent instanceof EntityLiving)) {
            return;
        }
        if (!ent.getClass().getName().startsWith("team.cqr.")) {
            return;
        }
        EntityLivingBase target = event.getTarget();
        if (!(target instanceof EntityPlayer)) {
            return;
        }
        int cap = SrpWizCoreConfig.cqrIntegration.attackerCap;
        if (cap <= 0) {
            return;
        }
        EntityLiving mob = (EntityLiving) ent;
        try {
            List<EntityLiving> nearby = target.world.getEntitiesWithinAABB(EntityLiving.class,
                    target.getEntityBoundingBox().grow(COUNT_RADIUS));
            int attackers = 0;
            for (EntityLiving other : nearby) {
                if (other != mob && other.isEntityAlive() && other.getAttackTarget() == target
                        && other.getClass().getName().startsWith("team.cqr.")) {
                    attackers++;
                    if (attackers >= cap) {
                        break;
                    }
                }
            }
            if (attackers < cap) {
                if (SrpWizCoreConfig.cqrIntegration.debugLogging) {
                    SrpWizCore.LOGGER.info("[srpwizcore] cqr attacker cap: {} -> {} ACCEPT ({}/{})",
                            mob.getClass().getSimpleName(), target.getName(),
                            Integer.valueOf(attackers + 1), Integer.valueOf(cap));
                }
                return;
            }
            reverting = true;
            try {
                mob.setAttackTarget(null);
            } finally {
                reverting = false;
            }
            denials++;
            if (SrpWizCoreConfig.cqrIntegration.debugLogging || denials == 1L) {
                SrpWizCore.LOGGER.info(
                        "[srpwizcore] cqr attacker cap: {} -> {} DENIED at {}/{} (denials: {})",
                        mob.getClass().getSimpleName(), target.getName(), Integer.valueOf(attackers),
                        Integer.valueOf(cap), Long.valueOf(denials));
            }
        } catch (Throwable t) {
            SrpWizCore.LOGGER.error("[srpwizcore] cqr attacker cap failed: {}", t.toString());
        }
    }
}
