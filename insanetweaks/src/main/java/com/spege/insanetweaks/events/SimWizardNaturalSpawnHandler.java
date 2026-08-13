package com.spege.insanetweaks.events;

import java.util.Collections;
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
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
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
 * question; and the per-dimension cap costs no extra veto handler - above the limit we simply
 * do not append, so there is no offer to cancel. The event fires even when the biome's own list is
 * empty, so appending to a cleared parasite biome works.
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
 *
 * <p>The third subscription, {@link #onEntityJoin}, is the highest-frequency event in this pack,
 * but its body is an {@code isRemote} check and one {@code instanceof} - no allocation, no scan -
 * so it does not need the same defensiveness as the other two. It now also bails out when
 * {@link #spawnBiomes} is empty, matching {@link #onWorldTick}: without that guard the population
 * counter climbs forever on a world where no biome ever resolved, since nothing ever corrects it
 * back down. Harmless today (nothing reads {@code count} in that state) but worth being
 * consistent about now that the diagnostic summary below reads {@code count} too.
 *
 * <h3>Diagnostics ({@code Debug Logging})</h3>
 *
 * <p>Per-event logging on {@link #onPotentialSpawns} was rejected on cost (see "Cost" above), but
 * that left the flag unable to answer the single most likely support question: "nothing is
 * spawning, why not?" A cap-transition line only fires once the feature is ALREADY working, so on
 * a world where nothing ever appends, the log stays empty with the flag on regardless of which of
 * three causes is at fault (no MONSTER query ever reached the handler; queries arrived but never
 * matched a configured biome; or matches happened but the cap or a zero spawn weight blocked every
 * one). {@link Population} carries four cheap counters for exactly that - see its javadoc - and
 * {@link #onWorldTick} prints a one-line-per-dimension summary at most once a minute, resetting
 * them on every print. That keeps the added cost to plain integer increments on the hot path and
 * one extra log line per dimension per minute, never per event.
 */
public class SimWizardNaturalSpawnHandler {

    /**
     * How often the per-dimension population is recounted. Two seconds: a cap does not need tick
     * accuracy, and the scan is O(loaded entities), which is not small in this pack. Deliberately
     * a constant rather than a config field - this is a performance knob, not a balance one.
     */
    private static final int REFRESH_INTERVAL_TICKS = 40;

    /**
     * How often the throttled diagnostic summary (see the class javadoc) is emitted, in ticks.
     * 1200 = 60 real-world seconds. Deliberately independent of {@link #REFRESH_INTERVAL_TICKS}:
     * the population recount needs to be responsive so the cap binds promptly, but the summary is
     * purely a support aid and once a minute is plenty to tell a tester "nothing spawned in the
     * last minute, and here is why" without adding a second per-event cost.
     */
    private static final int SUMMARY_INTERVAL_TICKS = 1200;

    /**
     * Resolved once from config at init. Empty means the feature is inert, and
     * {@link #resolveBiomes()} has already said so in the log.
     *
     * <p>{@code volatile} rather than {@code final} for safe publication: {@link #resolveBiomes()}
     * mutates this after class init, and {@code PotentialSpawns} readers in this pack can run on
     * entity-threading worker threads with no other happens-before edge to that write. Readers
     * take one local snapshot ({@code Set<Biome> biomes = spawnBiomes;}) rather than reading the
     * field twice, so a concurrent reassignment mid-event cannot produce an inconsistent view.
     */
    private static volatile Set<Biome> spawnBiomes = Collections.emptySet();

    private static final Map<Integer, Population> POPULATIONS =
            new ConcurrentHashMap<Integer, Population>();

    /**
     * Per-dimension population snapshot, cap-log edge state, and the throttled diagnostic
     * counters behind {@code Debug Logging} (see the class javadoc).
     *
     * <p>{@code monsterQueries}/{@code inBiome}/{@code appended}/{@code capBlocked} are bumped
     * from {@link #onPotentialSpawns}, which - like {@code count} above - can run on an
     * entity-threading worker thread (see the class javadoc's "Cost" section). They are plain
     * {@code volatile int}, not {@code AtomicInteger}: a lost increment under concurrent bumps
     * makes a summary line off by one or two out of a count in the thousands, which is a fine
     * trade for keeping the spawn path free of anything heavier than a field write. They are
     * bumped unconditionally - even while {@code Debug Logging} is off - because the increment
     * itself is near-free and gating it on the flag would mean re-reading a config field on the
     * hot path for no benefit; {@link #onWorldTick} alone decides whether to ever print them.
     *
     * <p>{@code loggingActive} mirrors {@code cfg.debugLogging} as last observed by
     * {@link #onWorldTick}, so it can detect an off-to-on transition and reset the counters
     * before the first summary after it - otherwise that summary would silently include an
     * unknown amount of time collected while nobody was watching, understating rates like
     * "queries per minute" without saying so.
     */
    private static final class Population {
        volatile int count;
        /** True while the cap is blocking, so the log line fires on transitions only. */
        volatile boolean capReported;

        volatile int monsterQueries;
        volatile int inBiome;
        volatile int appended;
        volatile int capBlocked;
        /** Last debugLogging value observed by {@link #onWorldTick}; drives the reset-on-enable. */
        volatile boolean loggingActive;

        /** Zeroes the four diagnostic counters. Called after every summary print and on every
         * off-to-on transition of {@code Debug Logging} - never touches {@code count} or
         * {@code capReported}, which are not part of the throttled summary window. */
        void resetCounters() {
            monsterQueries = 0;
            inBiome = 0;
            appended = 0;
            capBlocked = 0;
        }
    }

    /**
     * 🚨 The SAME instance must be appended on every fire.
     * {@code WorldServer.canCreatureTypeSpawnHere} fires {@code PotentialSpawns} a second time and
     * asks {@code list.contains(entry)} - and {@code SpawnListEntry} has no {@code equals}, so that
     * is a reference-identity test. A fresh entry per fire is picked by the first fire and then
     * rejected by the second, which spawns nothing AND discards the whole pack attempt instead of
     * re-rolling, quietly eating SRP's own spawns.
     *
     * <p>{@code itemWeight} is public and mutable, so live config edits are honoured by writing to
     * these rather than by rebuilding them.
     */
    private static final Biome.SpawnListEntry WIZARD_ENTRY =
            new Biome.SpawnListEntry(EntitySimWizard.class, 1, 1, 1);

    private static final Biome.SpawnListEntry BATTLEMAGE_ENTRY =
            new Biome.SpawnListEntry(EntitySimBattlemage.class, 1, 1, 1);

    /**
     * Parses {@code Spawn Biomes} into {@link #spawnBiomes}. Call once from
     * {@code InsaneTweaksMod.init}.
     *
     * <p>Every rejection is an ERROR with its reason and the accepted count goes out at INFO,
     * because an empty set is this feature's most likely failure and is otherwise completely
     * silent - exactly the reasoning behind {@code SpawnEngine.reload()} in srpwizcore.
     */
    public static void resolveBiomes() {
        Set<Biome> resolved = new HashSet<Biome>();
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
            resolved.add(biome);
        }
        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks][SimWizard] Natural spawn: {} biome(s) accepted, {} rejected.",
                Integer.valueOf(resolved.size()), Integer.valueOf(rejected));
        if (resolved.isEmpty()) {
            InsaneTweaksMod.LOGGER.warn(
                    "[InsaneTweaks][SimWizard] Natural spawn is ON but no biome resolved"
                            + " - nothing will ever spawn.");
        }
        spawnBiomes = Collections.unmodifiableSet(resolved);
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
        long time = world.getTotalWorldTime();

        // Independent of the recount gate below - the summary must still print on a dimension
        // whose biome set never resolved (that emptiness IS one of the failure modes it exists
        // to surface), so it cannot sit behind the spawnBiomes.isEmpty() return further down.
        if (time % SUMMARY_INTERVAL_TICKS == 0L) {
            maybeLogSummary(world);
        }

        if (time % REFRESH_INTERVAL_TICKS != 0L) {
            return;
        }
        if (spawnBiomes.isEmpty()) {
            return;
        }
        int alive = 0;
        try {
            for (int i = 0; i < world.loadedEntityList.size(); i++) {
                Entity ent = world.loadedEntityList.get(i);
                if (ent == null) {
                    continue;
                }
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

    /**
     * Prints (or silently skips) one dimension's once-a-minute diagnostic line, and always
     * resets that dimension's counters afterward - see {@link Population}. Runs on the main
     * thread ({@link #onWorldTick} already established that), so the counter reads here need no
     * extra synchronization beyond the fields already being {@code volatile}.
     *
     * <p>Emits nothing when {@code Debug Logging} is off, per the config's own promise ("read
     * live"). When it just turned on, resets first so the summary reports a genuine 60-second
     * window rather than counts accumulated since the last emission, however long ago that was.
     */
    private void maybeLogSummary(WorldServer world) {
        EntitiesCategory.NaturalSpawn cfg = ModConfig.entities.assimilatedWizard.naturalSpawn;
        int dim = world.provider.getDimension();
        Population pop = population(dim);

        boolean loggingOn = cfg.debugLogging;
        if (loggingOn && !pop.loggingActive) {
            pop.resetCounters();
        }
        pop.loggingActive = loggingOn;

        if (!loggingOn) {
            return;
        }

        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks][SimWizard] dim {} last 60s: {} monster queries, {} in biome, {}"
                        + " appended, {} cap-blocked, {} alive.",
                Integer.valueOf(dim), Integer.valueOf(pop.monsterQueries),
                Integer.valueOf(pop.inBiome), Integer.valueOf(pop.appended),
                Integer.valueOf(pop.capBlocked), Integer.valueOf(pop.count));
        pop.resetCounters();
    }

    /**
     * Counts a new arrival immediately, so the cap binds within the tick rather than within the
     * next {@link #REFRESH_INTERVAL_TICKS}. Without this the vanilla monster cap - not ours - is
     * what actually limits a burst, and a dimension can run several times over its configured
     * ceiling before the next recount corrects it.
     *
     * <p>This also fires for entities arriving by chunk load rather than by spawning, which is
     * correct here: they are loaded, so they belong in the count. The tick handler REPLACES the
     * count rather than adding to it, so the two can never compound.
     *
     * <p>{@code LOWEST} priority deliberately: {@code EntityJoinWorldEvent} is {@code @Cancelable}
     * and this pack vetoes on it (InControl {@code onjoin} rules, srpwizcore's namespace budgets),
     * so running last means we only count a join that survived every other listener's veto.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntitySimWizard)) {
            return;
        }
        // Same guard as onWorldTick's recount, and for the same reason: with no biome resolved
        // the feature is inert and nothing else ever corrects this count back down, so without
        // this it climbs forever on chunk loads alone.
        if (spawnBiomes.isEmpty()) {
            return;
        }
        population(event.getWorld().provider.getDimension()).count++;
    }

    /**
     * Appends our two entries to the candidate list, unless the dimension is at its cap.
     *
     * <p>Also bumps the four {@link Population} diagnostic counters (see the class javadoc's
     * "Diagnostics" section) - plain increments only, no allocation. Getting {@code dim} requires
     * the {@code WorldServer} cast, so the {@link #population(int)} lookup for
     * {@code monsterQueries} now happens right after that cast rather than after the biome match
     * as before; it is still exactly one lookup per invocation, reused for every counter bumped
     * further down, matching the "no map lookup beyond the one already performed" rule.
     */
    @SubscribeEvent
    public void onPotentialSpawns(WorldEvent.PotentialSpawns event) {
        // Cheapest rejection first - this runs once per candidate spawn position, and every type
        // other than MONSTER is the overwhelming majority of calls.
        if (event.getType() != EnumCreatureType.MONSTER) {
            return;
        }
        if (!(event.getWorld() instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.getWorld();
        int dim = world.provider.getDimension();
        Population pop = population(dim);
        pop.monsterQueries++;

        Set<Biome> biomes = spawnBiomes;
        if (biomes.isEmpty() || !biomes.contains(world.getBiome(event.getPos()))) {
            return;
        }
        pop.inBiome++;

        EntitiesCategory.NaturalSpawn cfg = ModConfig.entities.assimilatedWizard.naturalSpawn;

        if (pop.count >= cfg.maxPerDimension) {
            pop.capBlocked++;
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
        // 🚨 Read each weight ONCE. These are plain ints a config sync can write from another
        // thread (integrated server: client thread saving the config GUI vs. server thread running
        // this spawn pass); re-reading between the guard and the assignment can publish
        // itemWeight = 0 on an entry we then add anyway, and a list whose only entry has weight 0
        // makes WeightedRandom.getRandomItem throw IllegalArgumentException out of the spawn pass.
        boolean anyAppended = false;
        int wizardWeight = cfg.wizardSpawnWeight;
        if (wizardWeight > 0) {
            if (WIZARD_ENTRY.itemWeight != wizardWeight) {
                WIZARD_ENTRY.itemWeight = wizardWeight;
            }
            list.add(WIZARD_ENTRY);
            anyAppended = true;
        }
        int battlemageWeight = cfg.battlemageSpawnWeight;
        if (battlemageWeight > 0) {
            if (BATTLEMAGE_ENTRY.itemWeight != battlemageWeight) {
                BATTLEMAGE_ENTRY.itemWeight = battlemageWeight;
            }
            list.add(BATTLEMAGE_ENTRY);
            anyAppended = true;
        }
        if (anyAppended) {
            pop.appended++;
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
