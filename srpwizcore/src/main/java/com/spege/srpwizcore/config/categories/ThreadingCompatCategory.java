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
}
