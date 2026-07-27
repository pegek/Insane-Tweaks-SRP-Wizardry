package com.spege.srpwizcore.spawnengine;

import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Iterator;
import java.util.List;

/**
 * E2: enforces the namespace/per-type budgets on the spawn CANDIDATE list, before any entity is
 * constructed — the exact opposite of cancel-on-join, which paid the full construction cost
 * first (14.5% of the dim-150 tick, profile #4). An empty list makes vanilla's
 * {@code getSpawnListEntryForTypeAt} return null, which aborts the position attempt outright.
 *
 * <p>{@code LOWEST} priority so this runs AFTER InControl's potentialspawn injections and
 * filters the final list. The srparasites strip for dim 150 is a hard invariant and runs
 * independently of the engine master switch.
 */
public class SpawnListFilterHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPotentialSpawns(WorldEvent.PotentialSpawns event) {
        if (!(event.getWorld() instanceof WorldServer)) {
            return;
        }
        int dim = event.getWorld().provider.getDimension();
        boolean strip = SrpWizCoreConfig.spawnEngine.stripSrpInDim150 && dim == 150;
        boolean engine = SpawnEngine.isEngineDim(dim);
        if (!strip && !engine) {
            return;
        }
        List<Biome.SpawnListEntry> list = event.getList();
        if (list == null || list.isEmpty()) {
            return;
        }
        if (engine) {
            SpawnEngineState.e2Queries++;
        }
        // Whole type at its budget (or out of refill tokens): clear the list so vanilla
        // constructs nothing at this position.
        if (engine && SpawnEngine.typeBlocked(dim, event.getType(), SpawnEngine.tables())) {
            SpawnEngineState.e2Cleared++;
            list.clear();
            return;
        }
        Iterator<Biome.SpawnListEntry> it = list.iterator();
        while (it.hasNext()) {
            Biome.SpawnListEntry entry = it.next();
            ResourceLocation id = SpawnEngineState.idOf(entry.entityClass);
            if (id == null) {
                continue;
            }
            if (strip && "srparasites".equals(id.getResourceDomain())) {
                it.remove();
                continue;
            }
            if (!engine) {
                continue;
            }
            // ROOT-CAUSE FIX (1.6.2, 2026-07-27): InControl's potentialspawn injects its
            // entries into EVERY query type (PotentialSpawnRule never reads getType()), so
            // monsters ride the every-tick AMBIENT/WATER passes whose caps count real
            // ambients (~0) and never bind - the dim-150 cap bypass. Restore vanilla
            // semantics: an entry that does not count as the QUERIED type has no business
            // on this list.
            if (!SpawnEngineState.matchesType(entry.entityClass, event.getType())) {
                SpawnEngineState.e2TypeMismatch++;
                it.remove();
                continue;
            }
            if (SpawnEngineState.overBudget(dim, id)) {
                SpawnEngineState.e2Removed++;
                it.remove();
            }
        }
    }
}
