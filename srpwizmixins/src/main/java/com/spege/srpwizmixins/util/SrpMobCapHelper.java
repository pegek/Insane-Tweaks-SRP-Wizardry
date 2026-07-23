package com.spege.srpwizmixins.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.spege.srpwizmixins.config.SrpWizMixinsConfig;

/**
 * Parses and caches the per-dimension parasite mob-cap multipliers from
 * {@code SrpWizMixinsConfig.srpCompat.perDimMobCapMultipliers} (entries {@code "dim=multiplier"}).
 *
 * <p>Used by the SRP-compat cap mixin, whose hook fires very frequently, so the parsed map is
 * cached and only rebuilt when Forge swaps the config array (detected by reference identity on
 * config reload) - no per-call string parsing.
 */
public final class SrpMobCapHelper {

    private static volatile String[] cachedSource;
    private static volatile Map<Integer, Float> cache = Collections.emptyMap();

    private SrpMobCapHelper() {
    }

    /** Multiplier for {@code dim}, or 1.0f if the dimension has no configured entry. */
    public static float getMultiplier(int dim) {
        String[] source = SrpWizMixinsConfig.srpCompat.perDimMobCapMultipliers;
        if (source != cachedSource) {
            rebuild(source);
        }
        Float mult = cache.get(Integer.valueOf(dim));
        return mult == null ? 1.0f : mult.floatValue();
    }

    /** Scale a base cap for {@code dim}, clamped to >= 0. Returns {@code base} when unconfigured. */
    public static int scaleCap(int base, int dim) {
        float mult = getMultiplier(dim);
        if (mult == 1.0f) {
            return base;
        }
        int scaled = Math.round(base * mult);
        return scaled < 0 ? 0 : scaled;
    }

    private static synchronized void rebuild(String[] source) {
        if (source == cachedSource) {
            return; // another thread already rebuilt
        }
        Map<Integer, Float> map = new HashMap<Integer, Float>();
        if (source != null) {
            for (String entry : source) {
                if (entry == null) {
                    continue;
                }
                int eq = entry.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                try {
                    int dim = Integer.parseInt(entry.substring(0, eq).trim());
                    float mult = Float.parseFloat(entry.substring(eq + 1).trim());
                    if (mult >= 0.0f) {
                        map.put(Integer.valueOf(dim), Float.valueOf(mult));
                    }
                } catch (NumberFormatException ignored) {
                    // Skip malformed "dim=multiplier" entries silently.
                }
            }
        }
        cache = map;
        cachedSource = source;
    }
}
