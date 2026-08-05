package com.spege.insanetweaks.sanctuary;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

/**
 * Deduplicating debug logger for the Sanctuary module. No-op unless {@code sanctuary.debugLogging} is
 * on; an identical line then prints at most once per {@code sanctuary.debugRepeatSeconds}.
 *
 * <p>The key is the whole line, category and message together, and that is the point. The previous
 * version throttled per <em>category</em>, so one mob grinding against the dome forever emitted a line
 * every single second and buried everything else in the log — while a genuinely new event in the same
 * category arriving inside the same window was dropped. Keying on the message inverts both halves: a
 * repeat goes quiet, and a veto for a different mob or a different position is reported at once.
 *
 * <p>Wall-clock, not {@code getTotalWorldTime()}. World time stops while a single-player game is paused
 * and is not something a debug logger should depend on; the only cost is that a line suppressed just
 * before a pause may reprint just after one.
 *
 * <p>Deduplication alone is not enough, and the second guard is not redundant with it. A horde joining
 * the dome in one tick is a hundred <em>different</em> lines — different mob, different position — so
 * every one of them is new and every one gets through. Measured: one Succor wave produced 169 lines in
 * a single second. {@code sanctuary.debugBurstLimit} therefore caps how many lines a category may print
 * per {@link #BURST_WINDOW_MILLIS}. Nothing vanishes quietly: crossing the cap logs that it was crossed,
 * and the next line of that category reports how many were dropped meanwhile.
 *
 * <p>Bounded on purpose. The dedup key contains coordinates, so the input is effectively unbounded and
 * the table is an LRU capped at {@link #MAX_TRACKED} — walking across fresh chunks evicts the oldest
 * entries instead of growing without limit. An eviction can only cost an extra log line, never a missing
 * one.
 *
 * <p>Synchronised: {@code EntityJoinWorldEvent} reaches {@link SanctuarySpawnVetoHandler} off the server
 * thread while EntityThreading is active, and an access-ordered {@link LinkedHashMap} mutates on
 * {@code get} as well as {@code put}, so reads need the lock too. One lock covers both tables.
 */
public final class SanctuaryDebug {

    /** Hard cap on remembered lines. Sized to cover a busy dome without being worth tuning. */
    private static final int MAX_TRACKED = 512;

    /** Window the burst limit is counted over. */
    private static final long BURST_WINDOW_MILLIS = 10_000L;

    private static final Map<String, Long> LAST_SEEN = new LruMap();

    /** Per-category burst accounting. One entry per category, so it needs no eviction. */
    private static final Map<String, Burst> BURSTS = new HashMap<String, Burst>();

    private SanctuaryDebug() {}

    public static void log(String category, String message) {
        if (!ModConfig.sanctuary.debugLogging) {
            return;
        }
        long now = System.currentTimeMillis();
        long repeatMillis = ModConfig.sanctuary.debugRepeatSeconds * 1000L;
        int burstLimit = ModConfig.sanctuary.debugBurstLimit;

        // Built under the lock, emitted outside it - logging while holding a lock an off-thread
        // entity join also wants is how a tick stall turns into a deadlock-shaped stall.
        String dropped = null;

        synchronized (LAST_SEEN) {
            if (repeatMillis > 0L) {
                String key = category + '|' + message;
                Long last = LAST_SEEN.get(key);
                if (last != null && now - last.longValue() < repeatMillis) {
                    return;
                }
                LAST_SEEN.put(key, Long.valueOf(now));
            }
            if (burstLimit > 0) {
                Burst burst = BURSTS.get(category);
                if (burst == null) {
                    burst = new Burst();
                    BURSTS.put(category, burst);
                }
                if (now - burst.windowStart >= BURST_WINDOW_MILLIS) {
                    if (burst.suppressed > 0) {
                        dropped = "(" + burst.suppressed + " more suppressed)";
                    }
                    burst.windowStart = now;
                    burst.printed = 0;
                    burst.suppressed = 0;
                } else if (burst.printed >= burstLimit) {
                    burst.suppressed++;
                    return;
                }
                burst.printed++;
                if (burst.printed == burstLimit) {
                    // Say so at the moment the cap bites, so a burst that ends here is never silent.
                    message = message + " - burst limit reached, further " + category
                            + " lines suppressed for up to " + (BURST_WINDOW_MILLIS / 1000L) + "s";
                }
            }
        }

        if (dropped != null) {
            InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Sanctuary/" + category + ": " + dropped);
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Sanctuary/" + category + ": " + message);
    }

    /** Mutable per-category counters. Only ever touched under the {@code LAST_SEEN} lock. */
    private static final class Burst {
        long windowStart;
        int printed;
        int suppressed;
    }

    /** Named rather than anonymous so it can declare {@code serialVersionUID} and keep -Xlint quiet. */
    private static final class LruMap extends LinkedHashMap<String, Long> {

        private static final long serialVersionUID = 1L;

        LruMap() {
            super(64, 0.75f, true); // access order
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_TRACKED;
        }
    }
}
