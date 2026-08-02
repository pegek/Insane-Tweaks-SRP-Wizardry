package com.spege.tombtweaks.effects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Nullable;

import com.spege.tombtweaks.TombstoneTweaks;
import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.config.categories.TombstoneCategory.EffectPoolsConfig;

import net.minecraft.potion.Potion;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * The whitelist Tombstone's random-effect rolls draw from, parsed out of
 * {@code tombtweaks.cfg → tombstone.effectpools}.
 *
 * <p>Why this exists: Tombstone rolls straight out of {@code ForgeRegistries.POTIONS}, which in
 * this pack is 301 effects from 32 mods — plenty of them buggy, absurdly strong or outright
 * hostile. Tombstone's own config offers only blacklists, and those are read by Scroll of
 * Preservation, Magic Siphon, Mercy and the <i>alchemist</i> perk too, so blacklisting an effect
 * there also stops it being <i>preserved</i> on death. A whitelist applied to the rolling paths
 * only is the one thing that separates the two.
 *
 * <p>Pools are built <b>lazily</b>, on first use. Parsing at class-load would race the potion
 * registry; by the time any of Tombstone's rolls fire, the registry is long frozen.
 * {@link #invalidate()} drops the cache so config edits apply without a restart.
 */
public final class EffectPoolRegistry {

    /** Per-pool RNG. Allocating here is fine — this is a plain class, not a mixin. */
    private static final Random RANDOM = new Random();

    private static final Object BUILD_LOCK = new Object();

    /** Published as a whole, never mutated in place, so readers need no lock. */
    private static volatile Map<EffectPoolId, List<EffectPoolEntry>> pools;

    private EffectPoolRegistry() {
    }

    /**
     * Whether the whitelist should take over the roll at all. Read live on every roll — the
     * mixins that call this apply unconditionally, so the flag is a runtime branch, not a gate.
     */
    public static boolean isActive() {
        return TombTweaksConfig.tombstone.enableTombstoneTweaks
                && TombTweaksConfig.tombstone.effectPools.enableEffectWhitelist;
    }

    /** Drops the parsed pools; the next roll rebuilds them. Called from the config-change handler. */
    public static void invalidate() {
        pools = null;
    }

    /**
     * Draws a weighted entry from a pool.
     *
     * @param allowInstant mirrors Tombstone's own third parameter — when false, instant effects
     *                     are excluded, the same way it appends {@code .and(p -> !p.isInstant())}
     * @return the drawn entry, or {@code null} when the pool holds nothing eligible, in which case
     *         the caller must let Tombstone's original roll proceed
     */
    @Nullable
    public static EffectPoolEntry pick(EffectPoolId id, boolean allowInstant) {
        List<EffectPoolEntry> pool = get(id);
        if (pool.isEmpty()) {
            return null;
        }

        int totalWeight = 0;
        for (EffectPoolEntry entry : pool) {
            if (allowInstant || !entry.potion.isInstant()) {
                totalWeight += entry.weight;
            }
        }
        if (totalWeight <= 0) {
            return null;
        }

        int roll = RANDOM.nextInt(totalWeight);
        for (EffectPoolEntry entry : pool) {
            if (!allowInstant && entry.potion.isInstant()) {
                continue;
            }
            roll -= entry.weight;
            if (roll < 0) {
                return entry;
            }
        }
        // Unreachable while totalWeight was computed over the same predicate; kept as a safety net.
        return null;
    }

    private static List<EffectPoolEntry> get(EffectPoolId id) {
        Map<EffectPoolId, List<EffectPoolEntry>> current = pools;
        if (current == null) {
            synchronized (BUILD_LOCK) {
                current = pools;
                if (current == null) {
                    current = build();
                    pools = current;
                }
            }
        }
        List<EffectPoolEntry> pool = current.get(id);
        return pool == null ? Collections.<EffectPoolEntry>emptyList() : pool;
    }

    private static Map<EffectPoolId, List<EffectPoolEntry>> build() {
        EffectPoolsConfig cfg = TombTweaksConfig.tombstone.effectPools;
        Map<EffectPoolId, List<EffectPoolEntry>> built = new EnumMap<EffectPoolId, List<EffectPoolEntry>>(
                EffectPoolId.class);

        List<EffectPoolEntry> beneficial = parse(cfg.beneficialPool, "Beneficial Pool");
        List<EffectPoolEntry> harmful = parse(cfg.harmfulPool, "Harmful Pool");
        List<EffectPoolEntry> magicScroll = parse(cfg.magicScrollPool, "Magic Scroll Pool");

        // An empty per-item pool inherits the base one, so the common case needs a single list.
        if (magicScroll.isEmpty()) {
            magicScroll = beneficial;
        }

        built.put(EffectPoolId.BENEFICIAL, beneficial);
        built.put(EffectPoolId.HARMFUL, harmful);
        built.put(EffectPoolId.MAGIC_SCROLL, magicScroll);

        TombstoneTweaks.LOGGER.info(
                "[TombstoneTweaks] Tombstone effect whitelist built: {} beneficial, {} harmful, {} magic scroll (enabled={}).",
                Integer.valueOf(beneficial.size()), Integer.valueOf(harmful.size()),
                Integer.valueOf(magicScroll.size()),
                Boolean.valueOf(cfg.enableEffectWhitelist));

        return built;
    }

    /**
     * Parses {@code modid:effect[;weight][;maxAmplifier]}. A bad entry is dropped with a warning —
     * a typo or an uninstalled mod must never take the game down, and the rest of the pool still
     * works.
     */
    private static List<EffectPoolEntry> parse(String[] raw, String poolName) {
        List<EffectPoolEntry> out = new ArrayList<EffectPoolEntry>();
        if (raw == null) {
            return out;
        }

        for (String line : raw) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            String[] parts = trimmed.split(";");
            String name = parts[0].trim();
            if (name.isEmpty()) {
                continue;
            }

            Potion potion = lookup(name);
            if (potion == null) {
                TombstoneTweaks.LOGGER.warn(
                        "[TombstoneTweaks] Tombstone effect whitelist: '{}' in {} is not a registered potion effect — skipping it.",
                        name, poolName);
                continue;
            }

            int weight = parseInt(parts, 1, 1, 1, name, poolName, "weight");
            int maxAmplifier = parseInt(parts, 2, -1, 0, name, poolName, "max amplifier");

            out.add(new EffectPoolEntry(potion, weight, maxAmplifier));
        }
        return out;
    }

    @Nullable
    private static Potion lookup(String name) {
        try {
            return ForgeRegistries.POTIONS.getValue(new ResourceLocation(name));
        } catch (RuntimeException e) {
            // ResourceLocation is lenient in 1.12.2, but a hand-edited config can still be creative.
            return null;
        }
    }

    private static int parseInt(String[] parts, int index, int fallback, int min, String name,
            String poolName, String what) {
        if (parts.length <= index) {
            return fallback;
        }
        String token = parts[index].trim();
        if (token.isEmpty()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(token);
            if (value < min) {
                TombstoneTweaks.LOGGER.warn(
                        "[TombstoneTweaks] Tombstone effect whitelist: {} '{}' for '{}' in {} is below {} — using {}.",
                        what, token, name, poolName, Integer.valueOf(min), Integer.valueOf(fallback));
                return fallback;
            }
            return value;
        } catch (NumberFormatException e) {
            TombstoneTweaks.LOGGER.warn(
                    "[TombstoneTweaks] Tombstone effect whitelist: '{}' is not a valid {} for '{}' in {} — using {}.",
                    token, what, name, poolName, Integer.valueOf(fallback));
            return fallback;
        }
    }
}
