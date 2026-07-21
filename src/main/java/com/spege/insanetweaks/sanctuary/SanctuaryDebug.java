package com.spege.insanetweaks.sanctuary;

import java.util.HashMap;
import java.util.Map;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

/**
 * Throttled, category-keyed debug logger for the Sanctuary module. No-op unless
 * {@code sanctuary.debugLogging} is on; each category prints at most once per
 * {@link #THROTTLE_TICKS} to keep high-frequency events (spawn/grief/cleanse/burn)
 * from flooding the log. Throttle state is global per category (a representative sample).
 */
public final class SanctuaryDebug {

    /** Minimum world-ticks between two logs of the same category (~1s at 20 tps). */
    public static final long THROTTLE_TICKS = 20L;

    private static final Map<String, Long> LAST = new HashMap<String, Long>();

    private SanctuaryDebug() {}

    public static void log(long worldTime, String category, String message) {
        if (!ModConfig.sanctuary.debugLogging) {
            return;
        }
        Long last = LAST.get(category);
        if (last != null && worldTime - last.longValue() < THROTTLE_TICKS) {
            return;
        }
        LAST.put(category, Long.valueOf(worldTime));
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Sanctuary/" + category + ": " + message);
    }
}
