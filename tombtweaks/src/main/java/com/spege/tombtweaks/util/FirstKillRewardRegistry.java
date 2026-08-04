package com.spege.tombtweaks.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.spege.tombtweaks.TombstoneTweaks;
import com.spege.tombtweaks.config.TombTweaksConfig;

/**
 * The parsed form of {@code tombstone.firstkillrewards.Rewards}: entity registry name to the number
 * of perk points its first kill is worth.
 *
 * <p>Built lazily and cached, because a {@code LivingDeathEvent} fires for every mob in the world
 * and re-splitting a string array on each one would be a per-death allocation for nothing.
 * {@link #invalidate()} is called from the config-changed handler so an edit applies without a
 * restart, the same way the effect pools work.
 */
public final class FirstKillRewardRegistry {

    private static volatile Map<String, Integer> rewards;

    private FirstKillRewardRegistry() {}

    /** Drops the cache; the next lookup rebuilds it from config. */
    public static void invalidate() {
        rewards = null;
    }

    /** Perk points the first kill of {@code registryName} is worth, or 0 if it is not listed. */
    public static int pointsFor(String registryName) {
        if (registryName == null) return 0;
        Integer points = get().get(registryName);
        return points == null ? 0 : points.intValue();
    }

    /** True when nothing is configured, so the handler can bail before touching the entity. */
    public static boolean isEmpty() {
        return get().isEmpty();
    }

    private static Map<String, Integer> get() {
        Map<String, Integer> local = rewards;
        if (local == null) {
            synchronized (FirstKillRewardRegistry.class) {
                local = rewards;
                if (local == null) {
                    local = build();
                    rewards = local;
                }
            }
        }
        return local;
    }

    private static Map<String, Integer> build() {
        String[] lines = TombTweaksConfig.tombstone.firstKillRewards.rewards;
        if (lines == null || lines.length == 0) {
            return Collections.emptyMap();
        }

        Map<String, Integer> built = new HashMap<String, Integer>();
        for (String line : lines) {
            if (line == null) continue;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            int sep = trimmed.indexOf(';');
            if (sep <= 0 || sep == trimmed.length() - 1) {
                warn(trimmed, "expected modid:entity;perkPoints");
                continue;
            }

            String name = trimmed.substring(0, sep).trim();
            String amount = trimmed.substring(sep + 1).trim();
            if (name.indexOf(':') <= 0) {
                warn(trimmed, "the entity needs a namespace, like mod_lavacow:skeletonking");
                continue;
            }

            int points;
            try {
                points = Integer.parseInt(amount);
            } catch (NumberFormatException e) {
                warn(trimmed, "\"" + amount + "\" is not a whole number of perk points");
                continue;
            }
            if (points <= 0) {
                warn(trimmed, "a reward of " + points + " points would do nothing");
                continue;
            }

            Integer previous = built.put(name, Integer.valueOf(points));
            if (previous != null) {
                TombstoneTweaks.LOGGER.warn(
                        "[TombstoneTweaks] First-kill rewards list names {} twice; {} points wins over {}.",
                        name, Integer.valueOf(points), previous);
            }
        }
        return built;
    }

    private static void warn(String line, String why) {
        TombstoneTweaks.LOGGER.warn("[TombstoneTweaks] Skipping first-kill reward \"{}\": {}.", line, why);
    }
}
