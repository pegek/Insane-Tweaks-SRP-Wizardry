package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class SanctuarySpawnVetoHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!ModConfig.sanctuary.vetoNaturalSpawn) {
            return;
        }
        if (event.getResult() == Event.Result.DENY) {
            return; // already denied by someone else
        }
        Entity e = event.getEntityLiving();
        if (e == null || !SanctuaryRegionHelper.isSrpParasite(e)) {
            return;
        }
        if (SanctuaryRegionHelper.isProtected(event.getWorld(),
                (int) Math.floor(event.getX()), (int) Math.floor(event.getZ()))) {
            event.setResult(Event.Result.DENY);
            SanctuaryDebug.log(event.getWorld().getTotalWorldTime(), "spawn-vetoed",
                    e.getName() + " @(" + ((int) Math.floor(event.getX())) + ","
                    + ((int) Math.floor(event.getY())) + "," + ((int) Math.floor(event.getZ())) + ")");
        }
    }

    /**
     * Second gate, for parasites that never go through {@code CheckSpawn}.
     *
     * <p>{@code CheckSpawn} above only covers the vanilla natural-spawn path. SRP places parasites
     * with its own spawner - node relays, {@code ParasiteEventWorld}, summons from other parasites -
     * and those go straight to {@code World.spawnEntity}, which fires only this event. That is why a
     * dome with "Veto Natural Spawn" on and 300+ parasites inside logged zero {@code spawn-vetoed}
     * lines on 2026-08-04: nothing was arriving through the path we were watching. The same trap is
     * why InControl ships a separate {@code onjoin} rule alongside its spawn rules.
     *
     * <p>🚨 This event has no way to tell a fresh spawn from a chunk being loaded. Forge 1.12.2's
     * {@code EntityJoinWorldEvent} carries only the entity and the world - the {@code loadedFromDisk}
     * flag does not exist in this version - and its own javadoc lists {@code World.loadEntities} as
     * a source. So cancelling here ALSO removes parasites that already existed and whose chunk is
     * loading inside a dome, rather than letting them burn.
     *
     * <p>That is accepted deliberately. In-zone parasites already yield nothing: drops and XP are
     * vetoed by {@code SanctuaryDropVetoHandler}, and purge fire would kill them in seconds anyway.
     * Removing them on load is the same outcome, reached cheaper - and for a dome sitting on a node
     * it is the difference between a few hundred entities ticking and none. The exemption list is
     * honoured so a boss can never vanish this way.
     *
     * <p>This does not make purge fire redundant: walking into a dome fires no event at all, so the
     * two mechanisms cover different arrivals. Gated by its own config flag so it can be switched
     * off without also disabling the {@code CheckSpawn} veto.
     */
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!ModConfig.sanctuary.vetoParasiteJoin) {
            return;
        }
        net.minecraft.world.World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }
        Entity e = event.getEntity();
        // Fires for every entity in the game - items, arrows, XP orbs - so the parasite test has to
        // be the first thing that runs. Since 1.9.10 it is a single subtype check.
        if (e == null || !SanctuaryRegionHelper.isSrpParasite(e)) {
            return;
        }
        int x = (int) Math.floor(e.posX);
        int z = (int) Math.floor(e.posZ);
        if (!SanctuaryRegionHelper.isProtected(world, x, z)) {
            return;
        }
        if (SanctuaryRegionHelper.isExemptEntity(e)) {
            return;
        }
        event.setCanceled(true);
        SanctuaryDebug.log(world.getTotalWorldTime(), "join-vetoed",
                e.getName() + " @(" + x + "," + ((int) Math.floor(e.posY)) + "," + z + ")");
    }
}
