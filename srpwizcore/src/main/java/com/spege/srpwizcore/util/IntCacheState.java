package com.spege.srpwizcore.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-thread replacement for the static array pools of {@code net.minecraft.world.gen.layer.IntCache}.
 *
 * <p>Vanilla keeps one global set of pools ({@code intCacheSize} plus four
 * {@code List<int[]>}) and hands the same recycled arrays to whoever asks. All three public
 * methods are {@code synchronized}, so the lists themselves never corrupt — the damage is
 * <em>semantic</em>: {@code resetIntCache()} moves every "in use" array back into the free pool.
 * When two threads generate chunks at once, thread A's reset recycles arrays that thread B is
 * still reading through its GenLayer chain; the next {@code getIntCache} hands one of them to A,
 * which overwrites it under B's feet. B then reads whatever A wrote.
 *
 * <p>That is the crash of 2026-07-26 03:28 (server): Roguelike generated a dungeon on
 * {@code EntityThreading-Worker-192} at 03:28:14, the worker was reported stuck at 03:28:17, and
 * at 03:28:18 the server thread died inside the BoP GenLayer chain with
 * {@code Climate lookup failed climateOrdinal: 92} — {@code BOPClimates.lookup} indexing an
 * array of length 15 with 92. The biome ints were not biome ints any more.
 *
 * <p>Giving every thread its own pools removes the interference entirely: an array handed to a
 * thread can only ever be recycled by that same thread's own {@code resetIntCache()}.
 *
 * <p>The three methods below reproduce vanilla 1.12.2 semantics <em>exactly</em>, verified
 * instruction by instruction against {@code forge-1.12.2-14.23.5.2860-srg.jar} — including the
 * two quirks that look like bugs but are not: {@code resetIntCache} first drops one free array
 * from each pool (vanilla's only shrink mechanism), and a recycled array is handed out
 * <em>without</em> being zeroed (no {@code Arrays.fill} in either recycle path). Callers already
 * overwrite every element they read.
 *
 * <p>Lives outside {@code com.spege.srpwizcore.mixins} on purpose. A mixin class must not hold
 * this state: any {@code new X()} in a mixin's static initialiser is merged into the target's
 * {@code <clinit>} with {@code invokespecial} rewritten to the <em>target's</em> superclass
 * constructor, which fails verification at load time.
 */
public final class IntCacheState {

    /** Vanilla's small/large split point, and the initial large-array size. */
    private static final int SMALL_ARRAY_SIZE = 256;

    /** Diagnostic only: how many threads have ever touched the cache. Never decremented. */
    private static final AtomicInteger POOLS_CREATED = new AtomicInteger();

    private static final class Pools {
        int intCacheSize = SMALL_ARRAY_SIZE;
        final List<int[]> freeSmallArrays = new ArrayList<int[]>();
        final List<int[]> inUseSmallArrays = new ArrayList<int[]>();
        final List<int[]> freeLargeArrays = new ArrayList<int[]>();
        final List<int[]> inUseLargeArrays = new ArrayList<int[]>();
    }

    private static final ThreadLocal<Pools> POOLS = new ThreadLocal<Pools>() {
        @Override
        protected Pools initialValue() {
            POOLS_CREATED.incrementAndGet();
            return new Pools();
        }
    };

    private IntCacheState() {
    }

    /** Mirrors {@code IntCache.getIntCache(int)} ({@code func_76445_a}), per calling thread. */
    public static int[] getIntCache(int size) {
        Pools pools = POOLS.get();

        if (size <= SMALL_ARRAY_SIZE) {
            if (pools.freeSmallArrays.isEmpty()) {
                int[] fresh = new int[SMALL_ARRAY_SIZE];
                pools.inUseSmallArrays.add(fresh);
                return fresh;
            }
            int[] recycled = pools.freeSmallArrays.remove(pools.freeSmallArrays.size() - 1);
            pools.inUseSmallArrays.add(recycled);
            return recycled;
        }

        if (size > pools.intCacheSize) {
            pools.intCacheSize = size;
            pools.freeLargeArrays.clear();
            pools.inUseLargeArrays.clear();
            int[] grown = new int[pools.intCacheSize];
            pools.inUseLargeArrays.add(grown);
            return grown;
        }

        if (pools.freeLargeArrays.isEmpty()) {
            int[] fresh = new int[pools.intCacheSize];
            pools.inUseLargeArrays.add(fresh);
            return fresh;
        }

        int[] recycled = pools.freeLargeArrays.remove(pools.freeLargeArrays.size() - 1);
        pools.inUseLargeArrays.add(recycled);
        return recycled;
    }

    /**
     * Mirrors {@code IntCache.resetIntCache()} ({@code func_76446_a}) — and this is the whole
     * point of the fix: it recycles only the <em>calling</em> thread's arrays, so another
     * thread's in-flight GenLayer chain is never touched.
     */
    public static void resetIntCache() {
        Pools pools = POOLS.get();

        if (!pools.freeLargeArrays.isEmpty()) {
            pools.freeLargeArrays.remove(pools.freeLargeArrays.size() - 1);
        }
        if (!pools.freeSmallArrays.isEmpty()) {
            pools.freeSmallArrays.remove(pools.freeSmallArrays.size() - 1);
        }
        pools.freeLargeArrays.addAll(pools.inUseLargeArrays);
        pools.freeSmallArrays.addAll(pools.inUseSmallArrays);
        pools.inUseLargeArrays.clear();
        pools.inUseSmallArrays.clear();
    }

    /**
     * Mirrors {@code IntCache.getCacheSizes()} ({@code func_85144_b}). Vanilla's four counters
     * keep their exact wording and order (crash reports print this line); the calling thread and
     * the pool count are appended, because with per-thread pools "whose numbers are these?" is
     * the first question anyone reading a crash report will have.
     */
    public static String getCacheSizes() {
        Pools pools = POOLS.get();
        return "cache: " + pools.freeLargeArrays.size()
                + ", tcache: " + pools.freeSmallArrays.size()
                + ", allocated: " + pools.inUseLargeArrays.size()
                + ", tallocated: " + pools.inUseSmallArrays.size()
                + " [srpwizcore thread-local; thread: " + Thread.currentThread().getName()
                + ", pools created: " + POOLS_CREATED.get() + "]";
    }
}
