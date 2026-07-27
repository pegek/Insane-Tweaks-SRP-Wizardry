package com.spege.srpwizcore.spawnengine;

import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;

/**
 * E3: rebuilds the per-dim population snapshot every 20 ticks, for engine dims only.
 *
 * <p>One indexed walk of {@code loadedEntityList} per dimension per second — the groovy
 * cancel-on-join layer this replaces did the equivalent work on every single entity join.
 *
 * <p>Type counts follow vanilla's {@code World.countEntities(type, true)} exactly: every loaded
 * entity, matched with {@code Entity.isCreatureType(type, true)} (which already excludes
 * persistent mobs), with no liveness filter. Namespace counts, by contrast, only consider
 * {@link EntityLivingBase} — the budgets are about mob populations, and counting items or
 * projectiles would blow through a namespace budget such as {@code 150:minecraft=150}.
 */
public class SpawnEngineTickHandler {

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER
                || !(event.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        int dim = world.provider.getDimension();
        // Counts are also kept while the engine is off, as long as diagnostics are on (F0).
        boolean wanted = SpawnEngine.isEngineDim(dim)
                || (SrpWizCoreConfig.spawnEngine.diagLogging && SpawnEngine.isListedDim(dim));
        if (!wanted || world.getTotalWorldTime() % 20L != 0L) {
            return;
        }

        Map<String, Integer> byKey = new HashMap<String, Integer>();
        int[] byType = new int[SpawnEngine.TYPES.length];
        try {
            for (int i = 0; i < world.loadedEntityList.size(); i++) {
                Entity ent = world.loadedEntityList.get(i);
                if (ent == null) {
                    continue;
                }
                for (int t = 0; t < SpawnEngine.TYPES.length; t++) {
                    if (ent.isCreatureType(SpawnEngine.TYPES[t], true)) {
                        byType[t]++;
                    }
                }
                if (!(ent instanceof EntityLivingBase) || ent.isDead) {
                    continue;
                }
                ResourceLocation id = SpawnEngineState.idOf(ent.getClass());
                if (id == null) {
                    continue; // players and unregistered classes
                }
                increment(byKey, id.getResourceDomain());
                increment(byKey, id.toString());
            }
        } catch (IndexOutOfBoundsException e) {
            return; // list mutated by EntityThreading / chunk load — skip this cycle
        } catch (ConcurrentModificationException e) {
            return;
        }
        SpawnEngineState.publish(dim, new SpawnEngineState.DimCounts(byKey, byType));
    }

    private static void increment(Map<String, Integer> map, String key) {
        Integer n = map.get(key);
        map.put(key, Integer.valueOf(n == null ? 1 : n.intValue() + 1));
    }
}
