package com.spege.srpwizcore.spawnengine;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;
import com.spege.srpwizcore.config.categories.SpawnEngineCategory;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.WorldServer;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SpawnEngine v1 core: config parsing/validation and the {@code findChunksForSpawning} gate
 * (E1) plus the refill token bucket (E4). Spec:
 * {@code notes/spawnengine_v1_spec_2026-07-27.md} in the DEv 1.2 instance.
 *
 * <p>Lives outside the mixin package on purpose — {@code MixinWorldEntitySpawner} is pure
 * delegation with no fields, so nothing is merged into the target's {@code <clinit>}.
 *
 * <p>Threading: the parsed tables are immutable and swapped atomically through a volatile
 * field, because {@code PotentialSpawns} readers can run on EntityThreading workers. Bucket
 * state lives in {@link SpawnEngineState} and is keyed in a concurrent map, since
 * {@link #reload()} may run off the server thread (config GUI).
 */
public final class SpawnEngine {

    static final EnumCreatureType[] TYPES = EnumCreatureType.values();

    /** Immutable parsed view of the config. */
    static final class Tables {
        final Set<Integer> engineDims;
        final Map<Integer, Map<EnumCreatureType, Integer>> typeBudgets;
        /** Key is either a bare namespace ("defiledlands") or a full id ("iceandfire:if_troll"). */
        final Map<Integer, Map<String, Integer>> namespaceBudgets;
        /** Tokens per tick, already converted from the per-minute config value. */
        final Map<Integer, Double> monsterRatePerTick;
        final Map<Integer, Integer> monsterBurst;

        Tables(Set<Integer> dims, Map<Integer, Map<EnumCreatureType, Integer>> tb,
                Map<Integer, Map<String, Integer>> nb, Map<Integer, Double> rate,
                Map<Integer, Integer> burst) {
            this.engineDims = Collections.unmodifiableSet(dims);
            this.typeBudgets = Collections.unmodifiableMap(tb);
            this.namespaceBudgets = Collections.unmodifiableMap(nb);
            this.monsterRatePerTick = Collections.unmodifiableMap(rate);
            this.monsterBurst = Collections.unmodifiableMap(burst);
        }
    }

    private static volatile Tables tables = new Tables(
            new HashSet<Integer>(), new HashMap<Integer, Map<EnumCreatureType, Integer>>(),
            new HashMap<Integer, Map<String, Integer>>(),
            new HashMap<Integer, Double>(), new HashMap<Integer, Integer>());

    private static int diagCounter;

    private SpawnEngine() {
    }

    static Tables tables() {
        return tables;
    }

    /** True when the dimension is engine-controlled AND the master switch is on. */
    public static boolean isEngineDim(int dim) {
        return SrpWizCoreConfig.spawnEngine.enableSpawnEngine
                && tables.engineDims.contains(Integer.valueOf(dim));
    }

    /** True when the dimension is listed, regardless of the master switch (diag/tick handler). */
    public static boolean isListedDim(int dim) {
        return tables.engineDims.contains(Integer.valueOf(dim));
    }

    // ------------------------------------------------------------------ reload

    /**
     * Parses and validates the config into a fresh immutable snapshot. Called once at init and
     * again from {@code OnConfigChangedEvent}. Every rejected entry is logged with its reason;
     * the accepted counts go out at INFO so a silently empty table is impossible to miss.
     */
    public static void reload() {
        SpawnEngineCategory cfg = SrpWizCoreConfig.spawnEngine;

        Set<Integer> dims = new HashSet<Integer>();
        for (int d : cfg.engineDims) {
            dims.add(Integer.valueOf(d));
        }

        Map<Integer, Map<EnumCreatureType, Integer>> tb =
                new HashMap<Integer, Map<EnumCreatureType, Integer>>();
        int tbOk = 0;
        for (String raw : cfg.typeBudgets) {
            String[] eq = raw.trim().split("=");
            int colon = eq[0].indexOf(':');
            if (eq.length != 2 || colon < 0) {
                SrpWizCore.LOGGER.error("[srpwizcore] SpawnEngine: bad Type Budget entry '{}'"
                        + " (want dim:TYPE=N) - IGNORED", raw);
                continue;
            }
            try {
                Integer dim = Integer.valueOf(eq[0].substring(0, colon).trim());
                EnumCreatureType type =
                        EnumCreatureType.valueOf(eq[0].substring(colon + 1).trim());
                Integer max = Integer.valueOf(eq[1].trim());
                Map<EnumCreatureType, Integer> perDim = tb.get(dim);
                if (perDim == null) {
                    perDim = new HashMap<EnumCreatureType, Integer>();
                    tb.put(dim, perDim);
                }
                perDim.put(type, max);
                tbOk++;
            } catch (IllegalArgumentException e) {
                SrpWizCore.LOGGER.error("[srpwizcore] SpawnEngine: bad Type Budget entry '{}'"
                        + " ({}) - IGNORED", raw, e.toString());
            }
        }

        Map<Integer, Map<String, Integer>> nb = new HashMap<Integer, Map<String, Integer>>();
        int nbOk = 0;
        for (String raw : cfg.namespaceBudgets) {
            String[] eq = raw.trim().split("=");
            int colon = eq[0].indexOf(':');
            if (eq.length != 2 || colon < 0) {
                SrpWizCore.LOGGER.error("[srpwizcore] SpawnEngine: bad Namespace Budget entry"
                        + " '{}' (want dim:key=N) - IGNORED", raw);
                continue;
            }
            // Only the FIRST colon separates the dimension; the rest of the key may itself be a
            // "modid:entity" pair, which we keep verbatim as the map key.
            String key = eq[0].substring(colon + 1).trim();
            if ("iceandfire".equals(key)) {
                SrpWizCore.LOGGER.error("[srpwizcore] SpawnEngine: namespace-level iceandfire"
                        + " budget '{}' is FORBIDDEN (it would delete unique nest dragons);"
                        + " use per-type entries such as 150:iceandfire:if_troll=12 - IGNORED",
                        raw);
                continue;
            }
            try {
                Integer dim = Integer.valueOf(eq[0].substring(0, colon).trim());
                Integer max = Integer.valueOf(eq[1].trim());
                Map<String, Integer> perDim = nb.get(dim);
                if (perDim == null) {
                    perDim = new HashMap<String, Integer>();
                    nb.put(dim, perDim);
                }
                perDim.put(key, max);
                nbOk++;
            } catch (NumberFormatException e) {
                SrpWizCore.LOGGER.error("[srpwizcore] SpawnEngine: bad Namespace Budget entry"
                        + " '{}' ({}) - IGNORED", raw, e.toString());
            }
        }

        // Per-minute -> per-tick (1200 ticks per minute at 20 TPS).
        Map<Integer, Double> rate = new HashMap<Integer, Double>();
        for (String raw : cfg.monsterRefillRate) {
            parseDimValue(raw, "Monster Refill Rate", rate, 1.0D / 1200.0D);
        }
        Map<Integer, Double> burstTmp = new HashMap<Integer, Double>();
        for (String raw : cfg.monsterRefillBurst) {
            parseDimValue(raw, "Monster Refill Burst", burstTmp, 1.0D);
        }
        Map<Integer, Integer> burst = new HashMap<Integer, Integer>();
        for (Map.Entry<Integer, Double> e : burstTmp.entrySet()) {
            burst.put(e.getKey(), Integer.valueOf((int) e.getValue().doubleValue()));
        }

        Tables fresh = new Tables(dims, tb, nb, rate, burst);
        tables = fresh;
        SpawnEngineState.onTablesReloaded(fresh);
        SrpWizCore.LOGGER.info("[srpwizcore] SpawnEngine reloaded: dims={}, typeBudgets={},"
                + " namespaceBudgets={}, monsterBuckets={}, enabled={}",
                dims, Integer.valueOf(tbOk), Integer.valueOf(nbOk),
                Integer.valueOf(rate.size()),
                Boolean.valueOf(cfg.enableSpawnEngine));
    }

    /** Parses a {@code dim=value} entry; non-positive values are treated as "feature off". */
    private static void parseDimValue(String raw, String what, Map<Integer, Double> out,
            double scale) {
        String[] eq = raw.trim().split("=");
        if (eq.length != 2) {
            SrpWizCore.LOGGER.error("[srpwizcore] SpawnEngine: bad {} entry '{}'"
                    + " (want dim=N) - IGNORED", what, raw);
            return;
        }
        try {
            double v = Double.parseDouble(eq[1].trim()) * scale;
            if (v > 0.0D) {
                out.put(Integer.valueOf(eq[0].trim()), Double.valueOf(v));
            }
        } catch (NumberFormatException e) {
            SrpWizCore.LOGGER.error("[srpwizcore] SpawnEngine: bad {} entry '{}' - IGNORED",
                    what, raw);
        }
    }

    // ------------------------------------------------------------------ E1 gate

    /**
     * HEAD gate of {@code findChunksForSpawning}. Returns true to cancel the whole pass
     * ({@code setReturnValue(0)}), skipping the chunk scan and every entity construction.
     * Server thread only.
     *
     * <p>The pass is cancelled only when every creature type vanilla would process this tick is
     * already at its budget — so the three boolean arguments are evaluated exactly the way the
     * vanilla method does (verified against the 1.12.2 bytecode, offsets 300-334), including
     * {@code spawnOnSetTickRate}, which gates the animal types.
     */
    public static boolean gateFindChunks(WorldServer world, boolean spawnHostile,
            boolean spawnPeaceful, boolean spawnOnSetTickRate) {
        int dim = world.provider.getDimension();
        Tables t = tables;
        if (!t.engineDims.contains(Integer.valueOf(dim))) {
            return false;
        }
        SpawnEngineCategory cfg = SrpWizCoreConfig.spawnEngine;
        long time = world.getTotalWorldTime();

        // Diagnostics run even with the engine off — that is the F0 ground-truth capture for
        // the cap-bypass investigation.
        if (cfg.diagLogging && (++diagCounter % 100) == 0) {
            SpawnEngineState.logDiag(world, dim, t);
        }
        if (!cfg.enableSpawnEngine) {
            return false;
        }

        // 1.6.1: no population snapshot yet (first ~second in a freshly loaded dim) = block
        // the pass. Letting it run unfiltered caused the initial-fill burst AND phantom
        // bucket charges (join-cancelled spawns still count in the pass return value).
        if (!SpawnEngineState.hasSnapshot(dim)) {
            return true;
        }

        // Never throttle the every-400-ticks animal pass: skipping it would starve CREATURE
        // spawns entirely for any interval that does not divide 400.
        int interval = cfg.hostilePassInterval;
        if (interval > 1 && !spawnOnSetTickRate && time % (long) interval != 0L) {
            return true;
        }

        SpawnEngineState.refillBucket(dim, time, t);

        boolean anyActive = false;
        for (int i = 0; i < TYPES.length; i++) {
            EnumCreatureType type = TYPES[i];
            if (!vanillaWouldProcess(type, spawnHostile, spawnPeaceful, spawnOnSetTickRate)) {
                continue;
            }
            anyActive = true;
            if (typeBlocked(dim, type, t)) {
                continue;
            }
            if (!governed(dim, type, t) && !cfg.ungovernedTypesVetoStop) {
                continue;
            }
            return false; // this type can still spawn — let vanilla run the pass
        }
        return anyActive;
    }

    /**
     * Mirrors the three skip conditions at the top of vanilla's per-type loop. CREATURE is the
     * only animal type, so it is the only one gated on {@code spawnOnSetTickRate}.
     */
    static boolean vanillaWouldProcess(EnumCreatureType type, boolean spawnHostile,
            boolean spawnPeaceful, boolean spawnOnSetTickRate) {
        if (type.getPeacefulCreature() ? !spawnPeaceful : !spawnHostile) {
            return false;
        }
        return !type.getAnimal() || spawnOnSetTickRate;
    }

    /** True when this dim/type pair has a TOTAL budget or (MONSTER only) a refill bucket. */
    static boolean governed(int dim, EnumCreatureType type, Tables t) {
        Map<EnumCreatureType, Integer> perDim = t.typeBudgets.get(Integer.valueOf(dim));
        if (perDim != null && perDim.containsKey(type)) {
            return true;
        }
        return type == EnumCreatureType.MONSTER
                && t.monsterRatePerTick.containsKey(Integer.valueOf(dim));
    }

    /** Type fully blocked: the TOTAL budget is reached, or (MONSTER only) the bucket is empty. */
    static boolean typeBlocked(int dim, EnumCreatureType type, Tables t) {
        Map<EnumCreatureType, Integer> perDim = t.typeBudgets.get(Integer.valueOf(dim));
        if (perDim != null) {
            Integer max = perDim.get(type);
            if (max != null && SpawnEngineState.typeCount(dim, type) >= max.intValue()) {
                return true;
            }
        }
        return type == EnumCreatureType.MONSTER
                && t.monsterRatePerTick.containsKey(Integer.valueOf(dim))
                && SpawnEngineState.bucketTokens(dim) <= 0.0D;
    }

    // ------------------------------------------------------------------ E4 consume

    /**
     * RETURN hook: charges the MONSTER bucket with the pass's actual spawn count. The return
     * value of {@code findChunksForSpawning} is that count (verified against the 1.12.2
     * bytecode: the pack counter is added into the returned local at every successful
     * {@code spawnEntity}), so no join-side heuristic is needed to tell a natural spawn from a
     * Doomlike spawner or a summon.
     */
    public static void onPassCompleted(WorldServer world, int spawned) {
        if (spawned <= 0 || !SrpWizCoreConfig.spawnEngine.enableSpawnEngine) {
            return;
        }
        int dim = world.provider.getDimension();
        Tables t = tables;
        Integer key = Integer.valueOf(dim);
        if (!t.engineDims.contains(key) || !t.monsterRatePerTick.containsKey(key)) {
            return;
        }
        // 1.6.1: debt floor = -burst. Charges the filter could not prevent (spawns whose
        // constructions bypass PotentialSpawns) must not lock natural spawning out for tens
        // of minutes; the max lockout is burst/rate.
        Integer burstObj = t.monsterBurst.get(key);
        double debtFloor = -(burstObj == null ? 20.0D : burstObj.doubleValue());
        double left = SpawnEngineState.consumeTokens(dim, spawned, debtFloor);
        if (SrpWizCoreConfig.spawnEngine.diagLogging) {
            SrpWizCore.LOGGER.info("[srpwizcore] SpawnEngine dim {}: pass spawned {},"
                    + " bucket now {}", Integer.valueOf(dim), Integer.valueOf(spawned),
                    String.format("%.1f", Double.valueOf(left)));
        }
    }
}
