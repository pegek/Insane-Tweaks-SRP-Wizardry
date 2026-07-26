package com.spege.srpwizmixins.util;

/**
 * Lock monitors shared by this mod's SRP mixins. Plain class, deliberately OUTSIDE
 * {@code com.spege.srpwizmixins.mixins} — and it must stay that way.
 *
 * <p>A monitor object cannot live in the mixin that uses it. Mixin merges the mixin's
 * {@code <clinit>} into the target class and, while doing so, rewrites {@code invokespecial} on the
 * mixin's own superclass into the <em>target's</em> superclass constructor, because that is how it
 * retargets a super-constructor call. Every mixin here implicitly extends {@link Object}, so
 * {@code private static final Object LOCK = new Object()} compiles to
 * {@code new java/lang/Object} + {@code invokespecial java/lang/Object.<init>} — the
 * {@code invokespecial} gets remapped, the {@code new} does not, and the merged {@code <clinit>}
 * ends up doing {@code new Object} followed by {@code invokespecial WorldSavedData.<init>}. The JVM
 * rejects that with a {@link VerifyError} at class-verification time, i.e. long before any config
 * flag in the mixin body can be read.
 *
 * <p>That is a general rule, not a one-off: never write {@code new Object()} (or a {@code new} of
 * whatever the mixin declares as its superclass) in a mixin's static or instance field
 * initialiser. Put it here instead.
 *
 * <p>Constructing any <em>other</em> type in a mixin initialiser is fine — e.g.
 * {@code new ThreadLocal<>()} emits {@code invokespecial java/lang/ThreadLocal.<init>}, whose owner
 * is not the mixin's superclass, so it is left alone by the remapper.
 *
 * <p>This class holds no state beyond the monitors and is never instantiated.
 */
public final class SrpLocks {

    /**
     * Serialises the check-then-create path of {@code SRPSaveData.get} — see
     * {@code MixinSrpSaveDataGetRace}. {@code SRPSaveData.get} is static and unsynchronised, and
     * EntityThreading calls it from worker threads, so two threads can both see {@code null} and
     * both create an instance.
     */
    public static final Object SAVEDATA_CREATE = new Object();

    private SrpLocks() {
    }
}
