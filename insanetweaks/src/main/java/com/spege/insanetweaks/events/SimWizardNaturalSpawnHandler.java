package com.spege.insanetweaks.events;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.EntitySimBattlemage;
import com.spege.insanetweaks.entities.EntitySimWizard;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Natural spawning for {@link EntitySimWizard} and {@link EntitySimBattlemage} in SRP's parasite
 * biomes.
 *
 * <h3>Why the entry is injected rather than registered</h3>
 *
 * <p>The obvious implementation is {@code EntityRegistry.addSpawn}, which puts a permanent
 * {@code SpawnListEntry} in each biome's MONSTER list. That entry does not survive:
 * {@code SRPBiomes.clearMobSpawnList()} calls {@code mobListClear()} on both parasite biomes,
 * which calls {@code List.clear()} on all four spawn lists - and its callers are SRP's
 * {@code CommonProxy} during init AND {@code SRPCommandRoot}, i.e. a command available at
 * runtime. One admin command and the feature stops for the rest of the session with nothing in
 * any log.
 *
 * <p>🚨 Do not conclude from {@code SRPSpawning.removeInit()} that the list is safe. That sweep
 * removes entries whose {@code entityClass.toString()} contains {@code "scapeandrunparasites"},
 * which ours does not - it is {@code mobListClear} that kills a permanent entry, not
 * {@code removeInit}.
 *
 * <p>So the entry is appended per candidate position instead, on
 * {@code WorldEvent.PotentialSpawns}, which Forge fires from
 * {@code WorldServer.getSpawnListEntryForTypeAt} immediately before the weighted pick. Nothing of
 * ours lives in any biome list, so there is nothing for SRP to clear; there is no init-ordering
 * question; and the per-dimension cap costs no extra handler, because above the limit we simply
 * do not append. The event fires even when the biome's own list is empty, so appending to a
 * cleared parasite biome works.
 *
 * <p>That we leave no trace is guaranteed by the event itself rather than by our restraint: the
 * {@code PotentialSpawns} constructor does {@code this.list = new ArrayList<>(oldList)}, so
 * {@code getList()} hands out a COPY. Appending to it cannot reach the biome's own list even by
 * accident.
 *
 * <p>This composes with {@code srpwizcore}'s SpawnEngine, which hooks the same event at
 * {@code LOWEST} - i.e. after us - and can still trim us by its per-dimension namespace budget.
 * The stricter of the two limits wins, and no dependency between the mods is created.
 *
 * <h3>Cost</h3>
 *
 * <p>{@code PotentialSpawns} fires once per candidate spawn position, which is a hot path. Hence
 * the ordering in {@link #onPotentialSpawns}: creature type first, then biome, then the cap - the
 * first two reject almost every invocation. And hence the population count is maintained on the
 * world tick and only READ here; a scan of {@code loadedEntityList} per event would be a real
 * cost on the server thread.
 *
 * <p>Keeping the scan on {@code TickEvent.WorldTickEvent} also settles a threading question:
 * SpawnEngine's own javadoc notes that {@code PotentialSpawns} readers can run on EntityThreading
 * workers, and walking {@code loadedEntityList} from one of those is how you get a
 * {@code ConcurrentModificationException} nobody can reproduce. The tick handler is main-thread
 * by construction.
 */
public class SimWizardNaturalSpawnHandler {

    /**
     * How often the per-dimension population is recounted. Two seconds: a cap does not need tick
     * accuracy, and the scan is O(loaded entities), which is not small in this pack. Deliberately
     * a constant rather than a config field - this is a performance knob, not a balance one.
     */
    private static final int REFRESH_INTERVAL_TICKS = 40;

    /**
     * Resolved once from config at init. Empty means the feature is inert, and
     * {@link #resolveBiomes()} has already said so in the log.
     *
     * <p>Read-only after init, so a plain {@link HashSet} is safe even though readers may be off
     * the main thread.
     */
    private static final Set<Biome> SPAWN_BIOMES = new HashSet<Biome>();

    private static final Map<Integer, Population> POPULATIONS =
            new ConcurrentHashMap<Integer, Population>();

    /** Per-dimension population snapshot plus the edge state for cap logging. */
    private static final class Population {
        volatile int count;
        /** True while the cap is blocking, so the log line fires on transitions only. */
        volatile boolean capReported;
    }

    /**
     * Parses {@code Spawn Biomes} into {@link #SPAWN_BIOMES}. Call once from
     * {@code InsaneTweaksMod.init}.
     *
     * <p>Every rejection is an ERROR with its reason and the accepted count goes out at INFO,
     * because an empty set is this feature's most likely failure and is otherwise completely
     * silent - exactly the reasoning behind {@code SpawnEngine.reload()} in srpwizcore.
     */
    public static void resolveBiomes() {
        SPAWN_BIOMES.clear();
        int rejected = 0;
        for (String raw : ModConfig.entities.assimilatedWizard.naturalSpawn.spawnBiomes) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String id = raw.trim();
            Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(id));
            if (biome == null) {
                rejected++;
                InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks][SimWizard] Spawn Biomes: '{}' is not a registered biome"
                                + " - IGNORED.", id);
                continue;
            }
            SPAWN_BIOMES.add(biome);
        }
        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks][SimWizard] Natural spawn: {} biome(s) accepted, {} rejected.",
                Integer.valueOf(SPAWN_BIOMES.size()), Integer.valueOf(rejected));
        if (SPAWN_BIOMES.isEmpty()) {
            InsaneTweaksMod.LOGGER.warn(
                    "[InsaneTweaks][SimWizard] Natural spawn is ON but no biome resolved"
                            + " - nothing will ever spawn.");
        }
    }

    /**
     * Recounts one dimension's population every {@link #REFRESH_INTERVAL_TICKS}.
     *
     * <p>Indexed walk with both mutation exceptions caught and the cycle skipped, matching
     * {@code SpawnEngineTickHandler}: the list can be mutated under us by chunk loading or by an
     * entity-threading mod, and a count that is two seconds stale is worth strictly more than a
     * crashed tick.
     */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER
                || !(event.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        if (world.getTotalWorldTime() % REFRESH_INTERVAL_TICKS != 0L) {
            return;
        }
        int alive = 0;
        try {
            for (int i = 0; i < world.loadedEntityList.size(); i++) {
                Entity ent = world.loadedEntityList.get(i);
                // EntitySimBattlemage extends EntitySimWizard, so this one test is the shared cap.
                if (ent instanceof EntitySimWizard && !ent.isDead) {
                    alive++;
                }
            }
        } catch (IndexOutOfBoundsException e) {
            return;
        } catch (ConcurrentModificationException e) {
            return;
        }
        population(world.provider.getDimension()).count = alive;
    }

    /** Appends our two entries to the candidate list, unless the dimension is at its cap. */
    @SubscribeEvent
    public void onPotentialSpawns(WorldEvent.PotentialSpawns event) {
        // Cheapest rejections first - this runs once per candidate spawn position.
        if (event.getType() != EnumCreatureType.MONSTER || SPAWN_BIOMES.isEmpty()) {
            return;
        }
        if (!(event.getWorld() instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.getWorld();
        if (!SPAWN_BIOMES.contains(world.getBiome(event.getPos()))) {
            return;
        }

        EntitiesCategory.NaturalSpawn cfg = ModConfig.entities.assimilatedWizard.naturalSpawn;
        int dim = world.provider.getDimension();
        Population pop = population(dim);

        if (pop.count >= cfg.maxPerDimension) {
            if (!pop.capReported) {
                pop.capReported = true;
                if (cfg.debugLogging) {
                    InsaneTweaksMod.LOGGER.info(
                            "[InsaneTweaks][SimWizard] dim {}: natural spawn now CAPPED"
                                    + " ({} alive, max {}).",
                            Integer.valueOf(dim), Integer.valueOf(pop.count),
                            Integer.valueOf(cfg.maxPerDimension));
                }
            }
            return;
        }
        if (pop.capReported) {
            pop.capReported = false;
            if (cfg.debugLogging) {
                InsaneTweaksMod.LOGGER.info(
                        "[InsaneTweaks][SimWizard] dim {}: natural spawn resumed"
                                + " ({} alive, max {}).",
                        Integer.valueOf(dim), Integer.valueOf(pop.count),
                        Integer.valueOf(cfg.maxPerDimension));
            }
        }

        List<Biome.SpawnListEntry> list = event.getList();
        if (cfg.wizardSpawnWeight > 0) {
            list.add(new Biome.SpawnListEntry(EntitySimWizard.class, cfg.wizardSpawnWeight, 1, 1));
        }
        if (cfg.battlemageSpawnWeight > 0) {
            list.add(new Biome.SpawnListEntry(
                    EntitySimBattlemage.class, cfg.battlemageSpawnWeight, 1, 1));
        }
    }

    private static Population population(int dim) {
        Integer key = Integer.valueOf(dim);
        Population pop = POPULATIONS.get(key);
        if (pop == null) {
            pop = new Population();
            Population raced = POPULATIONS.putIfAbsent(key, pop);
            if (raced != null) {
                pop = raced;
            }
        }
        return pop;
    }
}
