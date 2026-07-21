package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Purge Fire: an active sanctuary ignites and damages parasites inside it. Event-driven
 * (mirrors {@code AegisEventHandler}'s fast-fire) so cost scales with entity count and is a
 * cheap reject for non-parasites — no per-core AABB scanning. Server side only.
 */
public class SanctuaryPurgeFireHandler {

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!ModConfig.sanctuary.enablePurgeFire) {
            return;
        }
        EntityLivingBase e = event.getEntityLiving();
        World world = e.world;
        if (world == null || world.isRemote) {
            return;
        }
        if (!SanctuaryRegionHelper.isSrpParasite(e)) {
            return;
        }
        int x = (int) Math.floor(e.posX);
        int z = (int) Math.floor(e.posZ);
        if (!SanctuaryRegionHelper.isInPurgeRange(world, x, z)) {
            return;
        }
        if (e.ticksExisted % ModConfig.sanctuary.purgeFireInterval == 0) {
            e.hurtResistantTime = 0; // break i-frames so the DoT actually lands each cadence
            e.attackEntityFrom(DamageSource.IN_FIRE, (float) ModConfig.sanctuary.purgeFireDamage);
            SanctuaryDebug.log(world.getTotalWorldTime(), "purge-fire",
                    e.getName() + " hp=" + ((int) e.getHealth())
                    + " @(" + x + "," + ((int) Math.floor(e.posY)) + "," + z + ")");
        }
        if (!e.isBurning()) {
            e.setFire(2); // maintain the visual fire between damage ticks
        }
    }
}
