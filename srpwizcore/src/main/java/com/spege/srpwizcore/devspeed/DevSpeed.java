package com.spege.srpwizcore.devspeed;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.network.SrpWizCoreNetwork;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;

/**
 * Server-side logic of the dev-only game-speed tool: changing the step, resetting it, syncing
 * it to clients, and measuring whether the server actually keeps up.
 *
 * <p>Everything here runs on the server thread (commands, tick and player events), so the
 * sample ring needs no locking. The step itself lives in {@link DevSpeedState}, which the mixin
 * reads from the same thread.
 */
public final class DevSpeed {

    public static final double MIN_MULTIPLIER = 0.5D;
    public static final double MAX_MULTIPLIER = 4.0D;

    /** Measurement window for "measured TPS". */
    private static final long WINDOW_NANOS = 5_000_000_000L;

    /** 512 tick timestamps cover the 5 s window up to ~100 TPS. */
    private static final long[] TICK_NANOS = new long[512];
    /** Overworld totalWorldTime at the same ticks - gives the game-clock rate (both modes). */
    private static final long[] TICK_TOTAL = new long[512];
    private static int tickHead;
    private static int tickCount;

    /** Ticks since server start, for the one-shot hook self-check. */
    private static int ticksSinceStart;
    private static boolean selfCheckDone;

    /** When to post the automatic "did it keep up" line after a change; 0 = none pending. */
    private static long reportAtNanos;

    private DevSpeed() {
    }

    // ------------------------------------------------------------------ changes

    /** Sets a new multiplier, syncs every client, announces it to everyone. */
    public static void set(MinecraftServer server, double multiplier, String who) {
        long ms = DevSpeedState.msPerTickFor(multiplier);
        DevSpeedState.serverMultiplier = multiplier;
        DevSpeedState.serverMsPerTick = ms;
        clearSamples();
        syncAll(server);
        boolean normal = ms == DevSpeedState.VANILLA_MS_PER_TICK;
        reportAtNanos = normal ? 0L : System.nanoTime() + WINDOW_NANOS + 1_000_000_000L;
        String line = normal
                ? String.format(Locale.ROOT, "[devspeed] Game speed back to normal (x1, 20 TPS) - by %s", who)
                : String.format(Locale.ROOT, "[devspeed] Game speed x%.2f -> target %.1f TPS (%d ms/tick) - by %s",
                        multiplier, DevSpeedState.tpsFor(ms), ms, who);
        SrpWizCore.LOGGER.info("[srpwizcore] devspeed: multiplier {} -> {} ms/tick (set by {})",
                multiplier, ms, who);
        server.getPlayerList().sendMessage(new TextComponentString(line));
    }

    /**
     * Back to x1. {@code server} may be null (nothing to sync then). Logs only when the speed
     * actually was changed, so the reset at every server start stays quiet.
     */
    public static void reset(MinecraftServer server, String reason) {
        boolean wasChanged = DevSpeedState.serverMsPerTick != DevSpeedState.VANILLA_MS_PER_TICK;
        DevSpeedState.serverMultiplier = 1.0D;
        DevSpeedState.serverMsPerTick = DevSpeedState.VANILLA_MS_PER_TICK;
        reportAtNanos = 0L;
        clearSamples();
        if (wasChanged) {
            SrpWizCore.LOGGER.info("[srpwizcore] devspeed: reset to x1 ({})", reason);
            if (server != null) {
                syncAll(server);
            }
        }
    }

    /** Called at every server start: fresh state and a fresh self-check. */
    public static void onServerStart() {
        reset(null, "server start");
        DevSpeedWarp.clear();
        ticksSinceStart = 0;
        selfCheckDone = false;
    }

    // ------------------------------------------------------------------ sync

    public static void syncAll(MinecraftServer server) {
        if (server.getPlayerList().getCurrentPlayerCount() == 0) {
            return;
        }
        SrpWizCoreNetwork.channel().sendToAll(new PacketDevSpeed(DevSpeedState.serverMsPerTick));
    }

    /** Joining players always get the current step, x1 included, so no stale value survives. */
    public static void syncTo(EntityPlayerMP player) {
        SrpWizCoreNetwork.channel().sendTo(new PacketDevSpeed(DevSpeedState.serverMsPerTick), player);
    }

    // ------------------------------------------------------------------ measuring

    /** Server tick END. One {@code nanoTime} and one array store. */
    public static void onServerTick(MinecraftServer server) {
        long now = System.nanoTime();
        TICK_NANOS[tickHead] = now;
        net.minecraft.world.WorldServer overworld = server.getWorld(0);
        TICK_TOTAL[tickHead] = overworld != null ? overworld.getTotalWorldTime() : 0L;
        tickHead = (tickHead + 1) % TICK_NANOS.length;
        if (tickCount < TICK_NANOS.length) {
            tickCount++;
        }

        if (!selfCheckDone && ++ticksSinceStart >= 40) {
            selfCheckDone = true;
            if (!DevSpeedState.serverHookFired) {
                SrpWizCore.LOGGER.error("[srpwizcore] devspeed: ENABLED in config, but the "
                        + "MinecraftServer.run tick-loop hook never ran ({} constants wired at "
                        + "class load). /devspeed cannot change the tick rate. Check "
                        + "logs/cleanmix.log for MixinMinecraftServerDevSpeed, and whether another "
                        + "tick-rate mod (tickmod/TickRate) replaced the loop.",
                        DevSpeedState.serverPatchedConstants);
            }
        }

        if (reportAtNanos != 0L && now - reportAtNanos >= 0L) {
            reportAtNanos = 0L;
            for (String line : measurementLines(server)) {
                server.getPlayerList().sendMessage(new TextComponentString(line));
            }
        }
    }

    /** Fresh measurement plus the automatic report ~6 s from now (after any mode change). */
    static void restartMeasurement() {
        clearSamples();
        reportAtNanos = System.nanoTime() + WINDOW_NANOS + 1_000_000_000L;
    }

    private static void clearSamples() {
        tickHead = 0;
        tickCount = 0;
    }

    /**
     * Ticks per second over the newest samples spanning at most {@link #WINDOW_NANOS}.
     * Returns {@code {tps, seconds, gameTicksPerSecond}} or null with fewer than 20 samples.
     */
    private static double[] measure() {
        if (tickCount < 20) {
            return null;
        }
        int newestIdx = (tickHead - 1 + TICK_NANOS.length) % TICK_NANOS.length;
        long newest = TICK_NANOS[newestIdx];
        long oldest = newest;
        int oldestIdx = newestIdx;
        int span = 0;
        for (int i = 1; i < tickCount; i++) {
            int idx = (newestIdx - i + TICK_NANOS.length) % TICK_NANOS.length;
            long t = TICK_NANOS[idx];
            if (newest - t > WINDOW_NANOS) {
                break;
            }
            oldest = t;
            oldestIdx = idx;
            span = i;
        }
        if (span < 19 || newest == oldest) {
            return null;
        }
        double seconds = (newest - oldest) / 1.0E9D;
        double gameTicks = TICK_TOTAL[newestIdx] - TICK_TOTAL[oldestIdx];
        return new double[] { span / seconds, seconds, gameTicks / seconds };
    }

    // ------------------------------------------------------------------ reporting

    /** The full {@code /devspeed} status block. */
    public static List<String> statusLines(MinecraftServer server) {
        List<String> lines = new ArrayList<String>();
        long ms = DevSpeedState.serverMsPerTick;
        lines.add(String.format(Locale.ROOT, "[devspeed] x%.2f -> target %.1f TPS (%d ms/tick)%s",
                DevSpeedState.serverMultiplier, DevSpeedState.tpsFor(ms), ms,
                ms == DevSpeedState.VANILLA_MS_PER_TICK ? " = normal" : ""));
        lines.addAll(DevSpeedWarp.statusLines());
        lines.addAll(measurementLines(server));
        if (!DevSpeedState.serverHookFired) {
            lines.add("[devspeed] !! tick-loop hook is NOT active - changes do nothing (see latest.log)");
        }
        return lines;
    }

    private static List<String> measurementLines(MinecraftServer server) {
        List<String> lines = new ArrayList<String>();
        double meanTickMs = MathHelper.average(server.tickTimeArray) * 1.0E-6D;
        double sustainable = meanTickMs > 0.0D ? 1000.0D / meanTickMs : 0.0D;
        double target = DevSpeedState.tpsFor(DevSpeedState.serverMsPerTick);
        double[] m = measure();
        if (m == null) {
            lines.add(String.format(Locale.ROOT,
                    "[devspeed] measured: not enough samples yet; mean tick %.1f ms (can sustain ~%.0f TPS)",
                    meanTickMs, sustainable));
            return lines;
        }
        lines.add(String.format(Locale.ROOT,
                "[devspeed] measured %.1f TPS over %.1f s (%.0f%% of target), mean tick %.1f ms (can sustain ~%.0f TPS)",
                m[0], m[1], 100.0D * m[0] / target, meanTickMs, sustainable));
        lines.add(String.format(Locale.ROOT,
                "[devspeed] game clock (overworld world age): x%.1f real speed",
                m[2] / 20.0D));
        if (m[0] < target * 0.95D) {
            lines.add(String.format(Locale.ROOT,
                    "[devspeed] !! SERVER CANNOT KEEP UP: x%.2f asked, real speed ~x%.2f",
                    DevSpeedState.serverMultiplier, m[0] / 20.0D));
        }
        return lines;
    }
}
