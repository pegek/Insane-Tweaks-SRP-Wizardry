package com.spege.insanetweaks.events;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.dhanantry.scapeandrunparasites.world.SRPSaveData;
import com.spege.insanetweaks.skills.TraitBase;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Implements the "Assimilated Warfare" trait mechanic.
 *
 * SRParasites can suppress vanilla XP drops at higher evolution phases, so
 * LivingExperienceDropEvent never fires and the player receives no XP.
 *
 * This handler records parasite deaths for players with the trait, waits briefly
 * to see whether vanilla/SRP XP is dropped naturally, and only then spawns a
 * small fallback XP orb if necessary.
 */
public class ParasiteXPFixHandler {

    private static final int PARASITE_XP_GRANT = 4;
    private static final int TICK_DELAY = 2;
    private static final int ASSIMILATED_WARFARE_EVOLUTION_STAGE = 5;
    private static final int ASSIMILATED_WARFARE_EVOLUTION_DRAIN = 1;

    /**
     * Key: entity id
     * Value: [world, posX, posY, posZ, ticksLeft, xpToGive]
     */
    private final Map<Integer, Object[]> pending = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onParasiteDeath(LivingDeathEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote) {
            return;
        }

        ResourceLocation id = EntityList.getKey(entity);
        if (id == null || !"srparasites".equals(id.getResourceDomain())) {
            return;
        }

        Entity trueSource = event.getSource().getTrueSource();
        if (!(trueSource instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer killer = (EntityPlayer) trueSource;
        if (!TraitBase.hasTrait(killer, "reskillable:attack", "compatskills:assimilated_warfare")) {
            return;
        }

        this.tryDrainEvolutionPoints(entity.world);

        int xpToGive = this.getFallbackXpForParasite(entity, id);
        if (xpToGive <= 0) {
            return;
        }

        pending.put(entity.getEntityId(), new Object[] {
                entity.world,
                entity.posX,
                entity.posY,
                entity.posZ,
                TICK_DELAY,
                xpToGive
        });
    }

    private int getFallbackXpForParasite(EntityLivingBase entity, ResourceLocation id) {
        if (entity == null || id == null) {
            return 0;
        }

        if ("buglin".equals(id.getResourcePath())) {
            return 0;
        }

        String className = entity.getClass().getName();
        if (className.contains(".entity.monster.inborn.")
                || className.contains(".entity.monster.deterrent.")) {
            return 0;
        }

        return PARASITE_XP_GRANT;
    }

    private void tryDrainEvolutionPoints(World world) {
        if (world == null || world.isRemote) {
            return;
        }

        SRPSaveData saveData = SRPSaveData.get(world, 0);
        if (saveData == null) {
            return;
        }

        int dimension = world.provider.getDimension();
        if (saveData.getEvolutionPhase(dimension) < ASSIMILATED_WARFARE_EVOLUTION_STAGE) {
            return;
        }

        // Mirror SRP's own parasite-death subtraction style: reduce the point pool
        // without forcibly dropping the current phase below its floor.
        saveData.setTotalKills(dimension, -ASSIMILATED_WARFARE_EVOLUTION_DRAIN, true, world, false);
    }

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntityLiving() == null) {
            return;
        }

        pending.remove(event.getEntityLiving().getEntityId());
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || pending.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<Integer, Object[]>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Object[]> entry = it.next();
            Object[] data = entry.getValue();

            World world = (World) data[0];
            if (world != event.world) {
                continue;
            }

            int ticksLeft = (int) data[4] - 1;
            if (ticksLeft <= 0) {
                double posX = (double) data[1];
                double posY = (double) data[2];
                double posZ = (double) data[3];
                int xpToGive = (int) data[5];
                world.spawnEntity(new EntityXPOrb(world, posX, posY, posZ, xpToGive));
                it.remove();
            } else {
                data[4] = ticksLeft;
            }
        }
    }
}
