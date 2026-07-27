package com.spege.srpwizcore.spawnengine;

import com.spege.srpwizcore.SrpWizCore;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mutable runtime state of the SpawnEngine, deliberately OUTSIDE the mixin package.
 *
 * <p>Population snapshots are immutable and published through a volatile field, because the
 * {@code PotentialSpawns} readers may run on EntityThreading workers. The bucket map is a
 * {@link ConcurrentHashMap}: {@link SpawnEngine#reload()} prunes it from whatever thread the
 * config GUI runs on, while the values themselves are only ever mutated from the server thread
 * (both mixin injects sit inside {@code WorldServer.tick}).
 */
public final class SpawnEngineState {

    /** Immutable per-dim population snapshot, rebuilt every 20 ticks. */
    static final class DimCounts {
        /** Namespace counts and full-id counts share one map; the keys cannot collide. */
        final Map<String, Integer> byKey;
        /** Indexed by {@code EnumCreatureType.ordinal()}. */
        final int[] byType;

        DimCounts(Map<String, Integer> byKey, int[] byType) {
            this.byKey = Collections.unmodifiableMap(byKey);
            this.byType = byType;
        }
    }

    private static volatile Map<Integer, DimCounts> snapshots =
            new HashMap<Integer, DimCounts>();

    private static final Map<Class<?>, ResourceLocation> ID_CACHE =
            new ConcurrentHashMap<Class<?>, ResourceLocation>();

    /** Sentinel for "this class is not in the entity registry", so misses stay cached too. */
    private static final ResourceLocation NO_ID = new ResourceLocation("srpwizcore", "no_id");

    /**
     * Entity class -> its REAL creature type (the one {@code countEntities} counts it under).
     * Root-cause 2026-07-27: InControl's potentialspawn injects entries into EVERY
     * PotentialSpawns query regardless of the queried type ({@code PotentialSpawnRule} never
     * reads {@code getType()}), so monsters spawn through the every-tick AMBIENT/WATER passes
     * whose caps count actual ambients (~0) and therefore never bind - the dim-150 cap bypass.
     * E2 removes entries whose real type does not match the query type.
     */
    private static final Map<Class<?>, Integer> TYPE_MASK_CACHE =
            new ConcurrentHashMap<Class<?>, Integer>();
    private static final EnumCreatureType[] ALL_TYPES = EnumCreatureType.values();

    /** Bitmask of the vanilla creature types this entity class counts as (may be several). */
    static int typeMask(Class<?> entityClass) {
        Integer cached = TYPE_MASK_CACHE.get(entityClass);
        if (cached != null) {
            return cached.intValue();
        }
        int mask = 0;
        for (int i = 0; i < ALL_TYPES.length; i++) {
            if (ALL_TYPES[i].getCreatureClass().isAssignableFrom(entityClass)) {
                mask |= 1 << ALL_TYPES[i].ordinal();
            }
        }
        TYPE_MASK_CACHE.put(entityClass, Integer.valueOf(mask));
        return mask;
    }

    /** Does this entity class count as the given creature type (countEntities semantics)? */
    static boolean matchesType(Class<?> entityClass, EnumCreatureType type) {
        return (typeMask(entityClass) & (1 << type.ordinal())) != 0;
    }

    /** dim -> {tokens, lastWorldTime}. */
    private static final Map<Integer, double[]> BUCKETS =
            new ConcurrentHashMap<Integer, double[]>();

    // E2 diagnostics (1.6.1): did the PotentialSpawns filter actually see the queries the
    // spawner made? Deliberately racy (diag only) - written from whatever thread fires the
    // event, read by logDiag on the server thread.
    static volatile long e2Queries;
    static volatile long e2Cleared;
    static volatile long e2Removed;
    static volatile long e2TypeMismatch;

    private SpawnEngineState() {
    }

    // ------------------------------------------------------------------ id cache

    /**
     * Registry id of an entity class, cached; null for classes that are not registered (players,
     * FakePlayer, anything spawned by code without a registry entry).
     */
    public static ResourceLocation idOf(Class<? extends Entity> cls) {
        ResourceLocation rl = ID_CACHE.get(cls);
        if (rl == null) {
            rl = EntityList.getKey(cls);
            ID_CACHE.put(cls, rl == null ? NO_ID : rl);
            return rl;
        }
        return rl == NO_ID ? null : rl;
    }

    // ------------------------------------------------------------------ snapshots

    static void publish(int dim, DimCounts counts) {
        Map<Integer, DimCounts> next = new HashMap<Integer, DimCounts>(snapshots);
        next.put(Integer.valueOf(dim), counts);
        snapshots = next;
    }

    static int typeCount(int dim, EnumCreatureType type) {
        DimCounts c = snapshots.get(Integer.valueOf(dim));
        return c == null ? 0 : c.byType[type.ordinal()];
    }

    /**
     * E2: is this entity id at or over its budget, by namespace or by full id? Returns false
     * while no snapshot exists yet (the first 20 ticks of a dimension), so a cold engine never
     * blocks anything.
     */
    public static boolean overBudget(int dim, ResourceLocation id) {
        Map<String, Integer> budgets =
                SpawnEngine.tables().namespaceBudgets.get(Integer.valueOf(dim));
        if (budgets == null) {
            return false;
        }
        DimCounts c = snapshots.get(Integer.valueOf(dim));
        if (c == null) {
            return false;
        }
        return atLimit(budgets, c, id.getResourceDomain()) || atLimit(budgets, c, id.toString());
    }

    private static boolean atLimit(Map<String, Integer> budgets, DimCounts c, String key) {
        Integer max = budgets.get(key);
        if (max == null) {
            return false;
        }
        Integer n = c.byKey.get(key);
        return n != null && n.intValue() >= max.intValue();
    }

    // ------------------------------------------------------------------ buckets (E4)

    /** Drops buckets for dimensions that no longer have a refill rate; new dims start full. */
    static void onTablesReloaded(SpawnEngine.Tables t) {
        BUCKETS.keySet().retainAll(t.monsterRatePerTick.keySet());
    }

    static void refillBucket(int dim, long worldTime, SpawnEngine.Tables t) {
        Integer key = Integer.valueOf(dim);
        Double rate = t.monsterRatePerTick.get(key);
        if (rate == null) {
            return;
        }
        Integer burstObj = t.monsterBurst.get(key);
        double burst = burstObj == null ? 20.0D : burstObj.doubleValue();
        double[] b = BUCKETS.get(key);
        if (b == null) {
            BUCKETS.put(key, new double[] { burst, (double) worldTime });
            return;
        }
        double dt = (double) worldTime - b[1];
        if (dt > 0.0D) {
            b[0] = Math.min(burst, b[0] + dt * rate.doubleValue());
            b[1] = (double) worldTime;
        }
    }

    /** No bucket for this dim means "tokens available" — the rate limit is simply off. */
    static double bucketTokens(int dim) {
        double[] b = BUCKETS.get(Integer.valueOf(dim));
        return b == null ? 1.0D : b[0];
    }

    static double consumeTokens(int dim, int spawned, double debtFloor) {
        double[] b = BUCKETS.get(Integer.valueOf(dim));
        if (b == null) {
            return 0.0D;
        }
        // Deliberately allowed to go negative: one pass can spawn a whole pack, and the passes
        // that follow stay blocked until the refill climbs back above zero. That gap is exactly
        // the "quiet after a fight" the rate limiter exists for. The floor (-burst) caps the
        // debt so anomalous charges cannot lock spawning out beyond burst/rate.
        b[0] -= spawned;
        if (b[0] < debtFloor) {
            b[0] = debtFloor;
        }
        return b[0];
    }

    /** True once the first population snapshot for this dim has been published. */
    static boolean hasSnapshot(int dim) {
        return snapshots.containsKey(Integer.valueOf(dim));
    }

    // ------------------------------------------------------------------ diag

    /**
     * F0 ground truth. Logs our snapshot next to vanilla's own {@code countEntities} and the
     * base cap from {@code EnumCreatureType}: if vanilla's count sits far above its base cap
     * while spawning continues, the dim-150 bypass is an inflated eligible-chunk count in
     * {@code cap * chunks / 289}, not an undercount.
     */
    static void logDiag(WorldServer world, int dim, SpawnEngine.Tables t) {
        DimCounts c = snapshots.get(Integer.valueOf(dim));
        Map<EnumCreatureType, Integer> budgets = t.typeBudgets.get(Integer.valueOf(dim));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SpawnEngine.TYPES.length; i++) {
            EnumCreatureType type = SpawnEngine.TYPES[i];
            Integer max = budgets == null ? null : budgets.get(type);
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(type.name()).append("=")
                    .append(c == null ? -1 : c.byType[type.ordinal()])
                    .append('/').append(max == null ? "-" : max.toString())
                    .append("(vanilla ").append(world.countEntities(type, true))
                    .append(" cap ").append(type.getMaxNumberOfCreature()).append(')');
        }
        SrpWizCore.LOGGER.info(
                "[srpwizcore] SpawnEngine diag dim {}: {} bucket={} players={} e2=q{}/c{}/r{}/t{}",
                Integer.valueOf(dim), sb.toString(),
                String.format("%.1f", Double.valueOf(bucketTokens(dim))),
                Integer.valueOf(world.playerEntities.size()),
                Long.valueOf(e2Queries), Long.valueOf(e2Cleared), Long.valueOf(e2Removed),
                Long.valueOf(e2TypeMismatch));
    }
}
