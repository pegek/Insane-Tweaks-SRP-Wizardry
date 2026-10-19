package com.spege.srpwizcore.devspeed;

/**
 * Shared state of the dev-only game-speed tool ({@code /devspeed}).
 *
 * <p>Deliberately free of every Minecraft and Forge type. Three different callers read or
 * write it: the two tick hooks ({@link #serverTickMillis} inside {@code MinecraftServer.run},
 * the mixin handler in {@code Timer.updateTimer}), the network handler, and
 * {@code DevSpeedMixinPlugin.postApply},
 * which runs <em>inside the transformer while {@code MinecraftServer} itself is being
 * defined</em>. A field or signature typed with a Minecraft class here would make the plugin's
 * first touch of this class resolve that type mid-transformation — a {@code
 * ClassCircularityError} waiting to happen. Keep it primitives only.
 *
 * <p>Server and client halves are separate on purpose: in single player both live in one JVM,
 * and the client half must follow the packet, not the integrated server's field, so that the
 * dedicated-server and integrated paths behave identically.
 */
public final class DevSpeedState {

    /** Vanilla milliseconds per tick (20 TPS). */
    public static final long VANILLA_MS_PER_TICK = 50L;

    /** Value returned to every {@code 50L} in {@code MinecraftServer.run}. */
    public static volatile long serverMsPerTick = VANILLA_MS_PER_TICK;

    /** The multiplier the user asked for (display only; {@link #serverMsPerTick} is the truth). */
    public static volatile double serverMultiplier = 1.0D;

    /**
     * Client timer override in milliseconds per tick, {@code 0} = leave the timer alone.
     * Written by the sync packet, read by the {@code Timer} mixin on every frame.
     */
    public static volatile float clientMsPerTick = 0.0F;

    /** Set by the server mixin handler whenever the tick loop runs through it. */
    public static volatile boolean serverHookFired;

    /** Set by the client mixin handler whenever the frame timer runs through it. */
    public static volatile boolean clientHookFired;

    /**
     * How many {@code 50L} constants in {@code MinecraftServer.run} were wired to the handler,
     * as counted on the patched bytecode by the mixin plugin. {@code -1} = the class was never
     * transformed with this mixin (toggle off, or the mixin did not apply at all).
     */
    public static volatile int serverPatchedConstants = -1;

    /** Same for the {@code tickLength} read in {@code Timer.updateTimer}; {@code -1} = not applied. */
    public static volatile int clientPatchedReads = -1;

    private DevSpeedState() {
    }

    /**
     * Called in place of every {@code 50L} in {@code MinecraftServer.run} — the plugin rewrites
     * the constants to {@code INVOKESTATIC} of this method. Lives here, not in the mixin, so the
     * rewrite needs no handler that Mixin would have to merge.
     */
    public static long serverTickMillis() {
        serverHookFired = true;
        return serverMsPerTick;
    }

    /** Milliseconds per tick for a multiplier; rounded, never below 1 ms. */
    public static long msPerTickFor(double multiplier) {
        long ms = Math.round(VANILLA_MS_PER_TICK / multiplier);
        return ms < 1L ? 1L : ms;
    }

    /** Ticks per second the loop aims for at a given step. */
    public static double tpsFor(long msPerTick) {
        return 1000.0D / msPerTick;
    }

    /** Client-side: adopt the server's step. {@code 50} (or anything invalid) clears it. */
    public static void applyClient(long msPerTick) {
        clientMsPerTick = (msPerTick <= 0L || msPerTick == VANILLA_MS_PER_TICK)
                ? 0.0F : (float) msPerTick;
    }
}
