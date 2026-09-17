package com.spege.srpwizmixins.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One meteor countdown per dimension, replacing SRP's single shared one.
 *
 * <p>Lives outside the mixin on purpose. A mixin that declares an initialised field has its
 * initialiser merged into the target's constructor, and this mod has already been bitten once by
 * what that merging does to object creation (see the note in {@code SrpLocks}). A plain static map
 * in an ordinary class has none of that risk and is just as fast to reach.
 *
 * <p>{@link ConcurrentHashMap} rather than a plain one: SRP's world tick is main-thread only, but
 * this mod is built to survive alongside entity-threading mods, and a torn resize here would be a
 * particularly annoying thing to debug for a counter nobody thinks about.
 */
public final class SrpMeteorTimers {

    private static final Map<Integer, Integer> BY_DIMENSION = new ConcurrentHashMap<>();

    private SrpMeteorTimers() {
    }

    public static int get(int dimension) {
        Integer value = BY_DIMENSION.get(dimension);
        return value == null ? 0 : value;
    }

    public static void set(int dimension, int value) {
        BY_DIMENSION.put(dimension, value);
    }

    /** Dimensions come and go; their counters should not outlive them. */
    public static void forget(int dimension) {
        BY_DIMENSION.remove(dimension);
    }
}
