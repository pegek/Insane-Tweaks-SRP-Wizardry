package com.spege.insanetweaks.events;

import com.spege.insanetweaks.skills.TraitBase;
import net.minecraft.entity.Entity;
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
import net.minecraft.entity.EntityList;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Implements the "Assimilated Warfare" trait mechanic.
 *
 * SRParasites bypasses Vanilla XP drop logic at high evolution phases
 * (see EntityParasiteBase.onDeathUpdateOG), so LivingExperienceDropEvent never
 * fires and the player gets zero XP from parasites after a certain threshold.
 *
 * With this trait, the player's kills are tracked in a 2-tick deferred map:
 *  1. LivingDeathEvent (LOWEST): if the dying entity is from "srparasites" and the
 *     killer is a player with "assimilated_warfare", it's recorded in the pending map.
 *  2. LivingExperienceDropEvent: if SRP dropped XP naturally (low phase), the entry
 *     is removed — no double XP.
 *  3. WorldTickEvent (POST, after 2 ticks): if the entry is still pending, SRP
 *     suppressed XP — we spawn a fixed amount (5 XP, equal to a vanilla Zombie).
 */
public class ParasiteXPFixHandler {

    // XP value matching a vanilla Zombie: EntityZombie.getExperiencePoints() == 5
    private static final int PARASITE_XP_GRANT = 5;

    // Ticks to wait — SRP drops XP synchronously inside onDeath() in the same tick.
    private static final int TICK_DELAY = 2;

    /**
     * Pending deaths table.
     * Key   : entity ID
     * Value : [World world, double posX, double posY, double posZ, int ticksLeft, int xpToGive]
     */
    private final Map<Integer, Object[]> pending = new HashMap<>();

    // -----------------------------------------------------------------------
    // Step 1 — record parasite deaths where killer has the trait
    // -----------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onParasiteDeath(LivingDeathEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote) return;

        // Filter: only srparasites entities
        ResourceLocation id = EntityList.getKey(entity);
        if (id == null || !"srparasites".equals(id.getResourceDomain())) return;

        // Wyjątki XP
        int xpToGive = PARASITE_XP_GRANT;
        String path = id.getResourcePath();

        if ("buglin".equals(path)) {
            xpToGive = 4;
        }
        // Jeśli przetestujesz i zechcesz zablokować jakiegoś mobka (0 xp):
        // else if ("nazwa_moba".equals(path)) {
        //     xpToGive = 0;
        // }

        if (xpToGive <= 0) return;

        // Filter: only player kills with the trait
        Entity trueSource = event.getSource().getTrueSource();
        if (!(trueSource instanceof EntityPlayer)) return;
        EntityPlayer killer = (EntityPlayer) trueSource;
        if (!TraitBase.hasTrait(killer, "reskillable:attack", "compatskills:assimilated_warfare")) return;

        pending.put(entity.getEntityId(), new Object[]{
            entity.world,
            entity.posX,
            entity.posY,
            entity.posZ,
            TICK_DELAY,
            xpToGive
        });
    }

    // -----------------------------------------------------------------------
    // Step 2 — SRP dropped XP naturally; cancel our deferred spawn
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntityLiving() == null) return;
        // Remove from pending — XP is being handled by SRP/Vanilla, nothing to do.
        pending.remove(event.getEntityLiving().getEntityId());
    }

    // -----------------------------------------------------------------------
    // Step 3 — deferred tick: spawn XP if SRP suppressed it
    // -----------------------------------------------------------------------

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;
        if (pending.isEmpty()) return;

        Iterator<Map.Entry<Integer, Object[]>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Object[]> entry = it.next();
            Object[] data = entry.getValue();

            World world = (World) data[0];
            if (world != event.world) continue; // Different dimension — skip this tick

            int ticksLeft = (int) data[4];
            ticksLeft--;

            if (ticksLeft <= 0) {
                // SRP suppressed XP — grant the trait owner their reward
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
