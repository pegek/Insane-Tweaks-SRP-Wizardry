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
 */
public class ThreadingCompatCategory {

    @Config.Comment({
            "Replace EntityTracker's entry HashSet with a concurrent set so that entity",
            "tracking survives multithreaded entity ticking (EntityThreading).",
            "Fixes 'Exception in server tick loop' NPE/CME crashes in EntityTracker.tick.",
            "Requires MC restart (applied when a world's EntityTracker is constructed). Default ON."
    })
    @Config.Name("Fix: EntityTracker Concurrent Entries")
    @Config.RequiresMcRestart
    public boolean fixEntityTrackerConcurrent = true;

    @Config.Comment({
            "Bounce off-thread SoundManager.playSound/stopSound/stopAllSounds calls to the",
            "client main thread. EntityThreading ticks CLIENT-world entities on worker threads",
            "and its own deferral only covers World.playSound - direct SoundHandler/SoundManager",
            "calls from entity ticks mutate the playingSounds HashBiMap while the client tick",
            "iterates it (CME crash 2026-07-25 03:50). Requires MC restart. Default ON."
    })
    @Config.Name("Fix: SoundManager Off-Thread Bounce")
    @Config.RequiresMcRestart
    public boolean fixSoundManagerBounce = true;

    @Config.Comment({
            "Replace MapStorage's world-data ArrayList with a CopyOnWriteArrayList so that",
            "registering world data from a worker thread cannot corrupt it.",
            "MapStorage is not thread-safe: setData AND getOrLoadData both mutate the list with no",
            "synchronization, so two concurrent adds can leave a null hole (size is incremented",
            "before the element lands in the array). saveAllData then walks the list by index and",
            "dies on that null - which silently TRUNCATES the world save: everything registered",
            "after the bad entry never reaches disk (server crash 2026-07-26 00:36).",
            "Off-thread registrants in this pack: NoiseThreader (structures register their data",
            "while chunks generate) and EntityThreading (entity ticks that create world data).",
            "Requires MC restart (applied when a MapStorage is constructed). Default OFF."
    })
    @Config.Name("Fix: MapStorage Concurrent Data List")
    @Config.RequiresMcRestart
    public boolean fixMapStorageConcurrent = false;

    @Config.Comment({
            "Second line of defence: skip a null entry while saving world data instead of dying",
            "on it, so the rest of the list still reaches disk. Independent of the fix above -",
            "it protects against any other producer of a null (e.g. a mod poking the list",
            "reflectively). With this OFF the behaviour is byte-for-byte vanilla: a null still",
            "throws the NPE. Logs a one-shot forensic dump (null index, list size, list-vs-map",
            "diff, stack trace) the first time it fires. Read live (no restart)."
    })
    @Config.Name("Fix: Skip Null World Data On Save")
    public boolean skipNullOnSave = false;

    @Config.Comment({
            "Diagnostic: log every MapStorage.setData call arriving from a thread other than the",
            "main server/client threads, with data id, type, list size and a stack trace",
            "(capped at 40 lines per game run). This is the decisive test for who registers world",
            "data off-thread - an empty log is also a result, it rules the race out and points at",
            "a single mod instead. Read live (no restart). Default OFF."
    })
    @Config.Name("Diag: Log Off-Thread MapStorage.setData")
    public boolean diagOffThreadSetData = false;

    @Config.Comment({
            "Give every thread its own IntCache array pools, so concurrent chunk generation",
            "cannot corrupt biome data.",
            "IntCache is the static int[] pool shared by the whole GenLayer chain. Its methods are",
            "synchronized, so the pools never corrupt structurally - but resetIntCache() recycles",
            "EVERY in-use array globally. When cascading chunk gen runs on a worker thread and the",
            "server thread at once, one thread's reset pulls the arrays out from under the other's",
            "GenLayer chain, which then reads whatever the first thread wrote over them. Symptom:",
            "garbage biome/climate ids - server crash 2026-07-26 03:28, 'Climate lookup failed",
            "climateOrdinal: 92' in GenLayerBiomeBOP (valid BoP ordinals are 0-14), right after a",
            "Roguelike dungeon generated on EntityThreading-Worker-192.",
            "Vanilla semantics are preserved exactly - only the pools become per-thread.",
            "This one is a REAL mixin gate (IntCacheMixinPlugin.shouldApplyMixin reads this key",
            "straight out of this file), so the mixin is not even applied when it is off.",
            "Requires MC restart. Default ON."
    })
    @Config.Name("Fix: IntCache Thread Local")
    @Config.RequiresMcRestart
    public boolean fixIntCacheThreadLocal = true;
}
