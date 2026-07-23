package com.spege.insanetweaks.events;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.entities.EntitySimWizard;

import electroblob.wizardry.entity.living.ISummonedCreature;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Companion to {@link com.spege.insanetweaks.util.NpcCastVetoArbiter} for the Ars Magica 2
 * workaround. AM2's EBWizardryCompatHandler subscribes to {@code EntityJoinWorldEvent} and,
 * for any EB Wizardry {@link ISummonedCreature} whose caster is at AM2's {@code getMaxSummons()}
 * cap, calls both {@code setDead()} and {@code event.setCanceled(true)} — silently deleting the
 * summon. Our sim wizard / sentinel summons carry no AM2 summon accounting, so that cap can
 * never legitimately apply to them. At LOWEST priority (after AM2's own listener) we revive the
 * just-killed summon before it ever ticks, so it joins the world normally.
 */
public class SummonVetoGuardHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.isCanceled()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof ISummonedCreature)) {
            return;
        }
        EntityLivingBase caster = ((ISummonedCreature) entity).getCaster();
        if (!(caster instanceof EntitySimWizard) && !(caster instanceof EntitySentinel)) {
            return;
        }

        // isDead is a public field in 1.12 — legally revives the entity AM2 just setDead()'d.
        entity.isDead = false;
        event.setCanceled(false);

        if (ModConfig.client.enableSimWizardDebugLogs || ModConfig.client.enableSentinelDebugLogs) {
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Revived summon {} vetoed at world-join (AM2 summon-cap workaround)",
                    entity.getName());
        }
    }
}
