package com.spege.srpwizcore.util;

/**
 * Mutable counters/flags for the perf-glue mixins (Doomlike / CQR / Raids).
 *
 * <p>Lives OUTSIDE the mixin package on purpose: a mixin must not allocate in its own static
 * initialiser — Mixin merges the mixin's {@code <clinit>} into the target and rewrites the
 * {@code invokespecial} to the <i>target's</i> superclass constructor while leaving the
 * {@code new} alone, which fails verification at load time (crash 2026-07-26). Plain primitive
 * fields here need no allocation at all, so the mixins carry no {@code <clinit>} whatsoever.
 */
public final class PerfGlueState {

    /** Dungeon plans skipped by {@code MixinDoomlikeBuilder} because their map was null. */
    public static int doomlikeNullMapSkips;

    /** CQR structure scans skipped by {@code MixinCqrStructureHelper}. */
    public static int cqrScansSkipped;

    /** Set once the Raids per-world-storage redirect has logged, so it logs only on first use. */
    public static boolean raidsStorageFixLogged;

    private PerfGlueState() {
    }
}
