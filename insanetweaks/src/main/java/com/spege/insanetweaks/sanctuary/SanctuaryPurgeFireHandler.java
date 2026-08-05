package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Purge Fire and Dwell Execution: an active sanctuary ignites and damages parasites inside it, and
 * finishes off anything that stays too long. Event-driven (mirrors {@code AegisEventHandler}'s
 * fast-fire) so cost scales with entity count and is a cheap reject for non-parasites - no per-core
 * AABB scanning. Server side only.
 */
public class SanctuaryPurgeFireHandler {

    /**
     * Ticks this parasite has accumulated inside a dome. Lives in the entity's own persistent NBT
     * rather than a {@code WeakHashMap} keyed on the entity: it survives save/load and chunk
     * unload, and there is no map to leak when a parasite dies far from any sanctuary. Same idiom
     * as {@code ParasiteShroudEventHandler}'s {@code InsaneTweaksParasiteShroudTicks}.
     */
    private static final String DWELL_KEY = "InsaneTweaksSanctuaryDwell";

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
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
        boolean inRange = SanctuaryRegionHelper.isInPurgeRange(world, x, z);

        if (ModConfig.sanctuary.enableDwellExecution && tickDwell(e, world, inRange)) {
            return; // executed - nothing left to set on fire
        }

        if (!ModConfig.sanctuary.enablePurgeFire || !inRange) {
            return;
        }
        if (e.ticksExisted % ModConfig.sanctuary.purgeFireInterval == 0) {
            e.hurtResistantTime = 0; // break i-frames so the DoT actually lands each cadence
            // Flat + percent-of-max-HP, so it scales against SRP's huge parasite HP pools.
            float dmg = (float) (ModConfig.sanctuary.purgeFireDamage
                    + e.getMaxHealth() * ModConfig.sanctuary.purgeFirePercentDamage / 100.0D);
            e.attackEntityFrom(DamageSource.IN_FIRE, dmg);
            SanctuaryDebug.log("purge-fire",
                    e.getName() + " dmg=" + ((int) dmg) + " hp=" + ((int) e.getHealth())
                    + " @(" + x + "," + ((int) Math.floor(e.posY)) + "," + z + ")");
        }
        if (!e.isBurning()) {
            e.setFire(2); // maintain the visual fire between damage ticks
        }
    }

    /**
     * Advances (or decays) the dwell counter.
     *
     * @return true when the parasite was executed this tick
     */
    private static boolean tickDwell(EntityLivingBase e, World world, boolean inRange) {
        NBTTagCompound data = e.getEntityData();

        if (!inRange) {
            // The overwhelming majority of parasites are nowhere near a dome, so the cheap
            // hasKey reject has to come first - this runs for every parasite in the world.
            if (!data.hasKey(DWELL_KEY)) {
                return false;
            }
            int interval = ModConfig.sanctuary.dwellDecayInterval;
            if (e.ticksExisted % interval != 0) {
                return false;
            }
            // Subtracting the interval on the interval's own cadence makes decay exactly as fast
            // as accrual, so stepping outside for a moment costs the moment and nothing more.
            int left = data.getInteger(DWELL_KEY) - interval;
            if (left <= 0) {
                data.removeTag(DWELL_KEY);
            } else {
                data.setInteger(DWELL_KEY, left);
            }
            return false;
        }

        int threshold = ModConfig.sanctuary.dwellExecutionTicks;
        int dwell = data.getInteger(DWELL_KEY) + 1;
        if (dwell < threshold) {
            data.setInteger(DWELL_KEY, dwell);
            return false;
        }
        if (SanctuaryRegionHelper.isExemptEntity(e)) {
            // Park at the threshold instead of letting the counter run away, so flipping the
            // exemption off later takes effect on the next tick rather than after another 2 min.
            data.setInteger(DWELL_KEY, threshold);
            return false;
        }

        data.removeTag(DWELL_KEY);
        e.hurtResistantTime = 0;
        // Deliberately a real damage event, not setDead(): OUT_OF_WORLD bypasses armour and the
        // invulnerable flag but still runs the normal death path, so SRP gets its own kill/point
        // bookkeeping. A finite (if absurd) amount rather than Float.MAX_VALUE, because a third
        // party multiplying the value in LivingHurtEvent would turn MAX_VALUE into Infinity and a
        // NaN health bar. Loot and XP are already vetoed in-dome by SanctuaryDropVetoHandler.
        float lethal = Math.max(e.getMaxHealth(), e.getHealth()) * 100.0F + 1000.0F;
        e.attackEntityFrom(DamageSource.OUT_OF_WORLD, lethal);
        if (e.isEntityAlive()) {
            e.setDead(); // last resort for anything that refuses damage outright
        }
        SanctuaryDebug.log("dwell-execute",
                e.getName() + " after " + threshold + "t @(" + ((int) Math.floor(e.posX)) + ","
                        + ((int) Math.floor(e.posY)) + "," + ((int) Math.floor(e.posZ)) + ")");
        return true;
    }

    // isExecutionExempt moved to SanctuaryRegionHelper.isExemptEntity on 2026-08-04: the join veto
    // in SanctuarySpawnVetoHandler needs the same list, and it is the same question either way -
    // "may a sanctuary delete this entity outright?"
}
