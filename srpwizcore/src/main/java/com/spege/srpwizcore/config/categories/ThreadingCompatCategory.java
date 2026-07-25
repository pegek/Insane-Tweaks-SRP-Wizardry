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
 * <p>The fix lives in {@code MixinEntityTracker} ({@code mixins.insanetweaks.early.json}):
 * it swaps the set for {@code ConcurrentHashMap.newKeySet()} whose iterators are weakly
 * consistent, so concurrent mutation can never throw CME/NPE.
 *
 * <p>Second fix: EntityThreading's own {@code World.updateEntities} patch also runs on the
 * client world, so client-world entities tick on worker threads too. Its deferral covers
 * only {@code World.playSound}; calls reaching {@code SoundManager} directly from a worker
 * thread mutate the {@code playingSounds} {@code HashBiMap} while the client tick iterates
 * it (CME crash 2026-07-25 03:50). {@code MixinSoundManagerBounce} replays those calls on
 * the client main thread.
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
}
