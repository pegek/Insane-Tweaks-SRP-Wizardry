package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * Thread-safety patches for multithreaded entity ticking (EntityThreading).
 *
 * <p>EntityThreading-2.2 ticks entities on worker threads; vanilla {@code EntityTracker}
 * keeps its entry set in a plain {@code HashSet}, so worker-side track/untrack racing the
 * server-thread iteration in {@code tick()} corrupts the set (2 confirmed server crashes:
 * 2026-07-21 10:41, 2026-07-22 13:12 — NPE in the set iterator inside the server tick loop).
 *
 * <p>The fix lives in {@code MixinEntityTracker} ({@code mixins.srpwizcore.early.json}):
 * it swaps the set for {@code ConcurrentHashMap.newKeySet()} whose iterators are weakly
 * consistent, so concurrent mutation can never throw CME/NPE.
 *
 * <p>Second fix: EntityThreading's own {@code World.updateEntities} patch also runs on the
 * client world, so client-world entities tick on worker threads too. Its deferral covers
 * only {@code World.playSound}; calls reaching {@code SoundManager} directly from a worker
 * thread mutate the {@code playingSounds} {@code HashBiMap} while the client tick iterates
 * it (CME crash 2026-07-25 03:50). {@code MixinSoundManagerBounce} replays those calls on
 * the client main thread.
 *
 * <p>Third fix: {@code MapStorage} keeps world data in a plain {@code ArrayList}, mutated without
 * synchronization by both {@code setData} and {@code getOrLoadData}. Concurrent registration
 * (NoiseThreader generating structures, EntityThreading ticking entities that create world data)
 * tears the {@code add}, leaving a {@code null} hole that kills {@code saveAllData} halfway
 * through — the world save is silently truncated (crash 2026-07-26 00:36, see
 * {@code notes/crash_mapstorage_null_2026-07-26.md}). {@code MixinMapStorage} hardens the list,
 * skips a null on save, and can name the off-thread caller.
 *
 * <p>Fourth fix, and the one that addresses the cause rather than the symptoms: EntityThreading's
 * own guard layer never applies. Its {@code MixinWorld} dies in {@code prepareInjections} on one
 * handler and takes all 17 of them down with it — on every boot since the mod was enabled
 * (2026-07-21 22:21). Only its raw-ASM {@code World.updateEntities} rewrite survives, so workers
 * tick entities with nothing stopping them from triggering a full chunk generation: a projectile
 * raytracing into ungenerated terrain was enough (crash 2026-07-28 10:28, {@code null} in
 * {@code BiomeCache.cache} — the same torn-{@code ArrayList} signature as the MapStorage crash,
 * see {@code notes/crash_biomecache_race_2026-07-28.md}).
 * {@code MixinChunkProviderServerThreadGuard} closes the choke point:
 * off-thread, ungenerated terrain reads as air instead of being generated.
 */
public class ThreadingCompatCategory {

    @Config.Comment({
            "Stops the server crashing when a mod ticks entities on more than one thread at once",
            "(EntityThreading and similar).",
            "Minecraft's entity-tracking list is not safe to touch from two threads, and when it",
            "breaks the server dies with 'Exception in server tick loop'.",
            "Harmless if you run no threading mod - it only makes that one list safe.",
            "Requires a restart. Default ON."
    })
    @Config.Name("Fix: EntityTracker Concurrent Entries")
    @Config.RequiresMcRestart
    public boolean fixEntityTrackerConcurrent = true;

    @Config.Comment({
            "Stops the game client crashing when a sound is started from the wrong thread.",
            "With a threading mod installed, entities in the world around you can tick on a worker",
            "thread and play their sounds from there, which corrupts the list of currently playing",
            "sounds and crashes the client.",
            "With this ON those sounds are simply played a moment later on the main thread instead.",
            "Harmless if you run no threading mod. Requires a restart. Default ON."
    })
    @Config.Name("Fix: SoundManager Off-Thread Bounce")
    @Config.RequiresMcRestart
    public boolean fixSoundManagerBounce = true;

    @Config.Comment({
            "Protects your world save from being silently cut short.",
            "Minecraft keeps per-world data (map markers, structure data, mod save data) in a list",
            "that is not safe to write from two threads. If a mod registers data from a worker thread",
            "while the game saves, the list can end up with a hole - and everything after that hole",
            "never reaches the disk. You lose data with no error message.",
            "Turn this ON if you run any mod that generates chunks or ticks entities on worker threads.",
            "Requires a restart. Default OFF."
    })
    @Config.Name("Fix: MapStorage Concurrent Data List")
    @Config.RequiresMcRestart
    public boolean fixMapStorageConcurrent = false;

    @Config.Comment({
            "A second safety net for the same problem: if the save data list does have a hole, skip",
            "over it and save the rest instead of aborting the whole save.",
            "Works on its own, whether or not the fix above is on. Writes one detailed report to the",
            "log the first time it happens so the cause can be tracked down.",
            "With this OFF the game behaves exactly like vanilla and the save still fails.",
            "No restart needed. Default OFF."
    })
    @Config.Name("Fix: Skip Null World Data On Save")
    public boolean skipNullOnSave = false;

    @Config.Comment({
            "Diagnostic for the two options above: log every time world data is registered from a",
            "worker thread, with a stack trace so you can see which mod did it (capped at 40 entries",
            "per session).",
            "An empty log is a useful result too - it rules this out as the cause.",
            "No restart needed. Default OFF."
    })
    @Config.Name("Diag: Log Off-Thread MapStorage.setData")
    public boolean diagOffThreadSetData = false;

    @Config.Comment({
            "Stops biome data getting corrupted when two threads generate chunks at the same time.",
            "Terrain generation borrows scratch memory from one shared pool. When a worker thread and",
            "the server thread both generate at once, one of them recycles the memory the other is",
            "still using, and you get impossible biome values and a crash.",
            "With this ON every thread gets its own pool. Nothing about generation changes otherwise.",
            "Requires a restart. Default ON."
    })
    @Config.Name("Fix: IntCache Thread Local")
    @Config.RequiresMcRestart
    public boolean fixIntCacheThreadLocal = true;

    @Config.Comment({
            "Only ever generate new terrain on the main server thread.",
            "When a threading mod ticks an entity on a worker thread and that entity reaches into",
            "terrain that does not exist yet - an arrow flying past the edge of the generated world,",
            "a mob looking for a path - the game starts generating a chunk from the wrong thread.",
            "That is what corrupts biome data and the world save.",
            "With this ON such a request gets air instead of new terrain. The cost is cosmetic: an",
            "arrow flies on through, a mob finds no path out there.",
            "With it OFF the guard only writes a warning to the log and blocks nothing, which is a",
            "good way to find out whether anything on your setup is even doing this.",
            "No restart needed. Default ON."
    })
    @Config.Name("Fix: Block Off-Thread Chunk Generation")
    public boolean blockOffThreadChunkGen = true;

    @Config.Comment({
            "While the guard above is blocking, still hand back terrain that is already loaded, so",
            "only genuinely ungenerated chunks read as air. This is the friendlier of the two modes.",
            "Turn it OFF for the strictest behaviour: never look anything up, always return an empty",
            "chunk.",
            "No restart needed. Default ON."
    })
    @Config.Name("Fix: Read Loaded Chunk Off-Thread")
    public boolean readLoadedChunkOffThread = true;

    @Config.Comment({
            "Threads allowed to generate terrain anyway, matched by the start of the thread name.",
            "Empty by default, because on a normal setup nothing legitimately generates chunks off the",
            "main thread. This exists so a world-pregenerator or async worldgen mod can be let through",
            "without a new build - take the name from the warning the guard writes to the log.",
            "No restart needed."
    })
    @Config.Name("Diag: Off-Thread Chunk Gen Allowed Threads")
    public String[] offThreadChunkGenAllowedThreads = new String[0];
}
