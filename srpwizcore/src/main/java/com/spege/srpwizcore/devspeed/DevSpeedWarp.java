package com.spege.srpwizcore.devspeed;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.spege.srpwizcore.SrpWizCore;

import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraft.world.storage.DerivedWorldInfo;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.DimensionManager;

/**
 * {@code /devspeed warp N} — "clock warp", the second dev-speed mode.
 *
 * <p>Mode 1 (tick rate) runs <em>everything</em> faster and is capped by CPU (~x2-2.5 in this
 * pack). Most of what we test is clock-driven, not entity-driven: day gates read
 * {@code getTotalWorldTime()}, SRP evolution compares {@code totalWorldTime} against stored
 * thresholds, celestial events roll on {@code worldTime / 24000}. Warp advances only the two
 * clocks, by an extra N-1 per server tick, which costs nothing and reaches x50-x100.
 *
 * <p>Three rules keep it from breaking clock-reading code (audit 2026-10-17, see the pack note):
 * <ol>
 * <li><b>Per clock, never twice.</b> Each distinct {@link WorldInfo} is advanced once.
 * {@link DerivedWorldInfo} (Nether/End style) is skipped — it follows the overworld and its
 * setters are no-ops. A dimension with any other info type is checked at runtime: if its clock
 * already moved after the overworld's was advanced, it delegates and is skipped too. Dims 111
 * and 150 have their own clocks (see progression.groovy) and get advanced on their own.</li>
 * <li><b>Land on every multiple of {@link #STOP} (1000 ticks)</b> — day starts, 6000/12000/
 * 13000/18000/23000, everything a {@code worldTime % 24000 == K} check is likely to name.
 * Two observers matter: WorldTickEvent START sees the clock before vanilla's +1, END after it.
 * So the step stops one short of the boundary (END sees it), and the next tick adds nothing
 * extra (START sees it). Costs two slow ticks per 1000 game ticks.</li>
 * <li><b>Step coprime with 210</b> (with 10 below N=11). {@code total % 2 == 0} (SRP ParasiteBarrier),
 * {@code % 10} (SRP SnowGrass), {@code % 20/40/200} (our PseudoTick modules) would fire on
 * every tick or never with an even step, or a multiple of 10. With a step coprime to 2·3·5·7
 * the residues cycle, so such polls keep firing about once per period of <em>real</em> ticks
 * (not faster — they are effectively tick-counted — but not frozen either).</li>
 * </ol>
 *
 * <p>Optional extra spawn passes ({@code /devspeed warp spawns K}, K ≤ 4): runs
 * {@code WorldEntitySpawner.findChunksForSpawning} K more times per tick in every loaded
 * dimension, so spawn saturation keeps up with the faster day. They go through every hook a
 * vanilla pass goes through — InControl, our SpawnEngine budget gate (dim 150), the srparasites
 * strip in dim 150 — so budgets and the dim-150 invariant still hold.
 *
 * <p>Never persisted (state resets with mode 1), but the clocks it moves are saved — warp
 * permanently ages the world, which is why enabling it warns in chat and start/stop are logged
 * with the overworld's {@code totalWorldTime}.
 */
public final class DevSpeedWarp {

    public static final int MIN_WARP = 2;
    public static final int MAX_WARP = 200;
    public static final int MAX_EXTRA_SPAWN_PASSES = 4;

    /** Every multiple of this is observed by both tick phases. */
    static final long STOP = 1000L;

    /** Requested multiplier; 1 = off. */
    private static int requested = 1;
    /** Game ticks per server tick actually used (vanilla 1 included), coprime with 210. */
    private static long increment = 1L;
    private static int extraSpawnPasses;

    private static long startTotal;
    private static long addedTotal;
    private static long ticksActive;
    /** Overworld totalWorldTime after the last warp tick, for the stop log when the world is gone. */
    private static long lastTotal = -1L;

    /** One instance for our passes; its only state is a set cleared at the start of each call. */
    private static WorldEntitySpawner spawner;

    private DevSpeedWarp() {
    }

    public static boolean isActive() {
        return requested > 1;
    }

    public static int requested() {
        return requested;
    }

    public static long increment() {
        return increment;
    }

    public static int extraSpawnPasses() {
        return extraSpawnPasses;
    }

    /**
     * Nearest step coprime with 210 (= 2*3*5*7), upward on a tie. Nothing below 11 is, so small
     * warps settle for coprime with 10 (3, 7, 9): odd and not a multiple of 5 is what keeps the
     * {@code % 2} / {@code % 10} / {@code % 20} polls from freezing.
     */
    static long coprimeStep(int n) {
        long modulus = n < 11 ? 10L : 210L;
        for (int d = 0; ; d++) {
            if (gcd(n + d, modulus) == 1L) {
                return n + d;
            }
            if (n - d > 1 && gcd(n - d, modulus) == 1L) {
                return n - d;
            }
        }
    }

    private static long gcd(long a, long b) {
        while (b != 0L) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    // ------------------------------------------------------------------ changes

    public static void start(MinecraftServer server, int warp, String who) {
        WorldServer overworld = server.getWorld(0);
        boolean wasActive = isActive();
        requested = warp;
        increment = coprimeStep(warp);
        DevSpeed.restartMeasurement();
        if (!wasActive) {
            startTotal = overworld.getTotalWorldTime();
            addedTotal = 0L;
            ticksActive = 0L;
        }
        SrpWizCore.LOGGER.warn("[srpwizcore] devspeed: WARP x{} (step {}) {} at overworld "
                + "totalWorldTime={} (day {}) by {}", warp, increment,
                wasActive ? "changed" : "STARTED", overworld.getTotalWorldTime(),
                overworld.getTotalWorldTime() / 24000L, who);
        broadcast(server, String.format(Locale.ROOT,
                "[devspeed] WARP x%d (step %d game ticks/tick) - by %s", warp, increment, who));
        if (!wasActive) {
            broadcast(server, "[devspeed] !! WARNING: warp PERMANENTLY ages this save "
                    + "(world age and time of day are saved). It moves only the clocks - "
                    + "mobs, crops, weather and per-tick counters run at normal speed. "
                    + "'/devspeed warp off' to stop.");
        }
    }

    public static void setExtraSpawnPasses(MinecraftServer server, int passes, String who) {
        extraSpawnPasses = passes;
        SrpWizCore.LOGGER.info("[srpwizcore] devspeed: warp extra spawn passes = {} (by {})",
                passes, who);
        broadcast(server, String.format(Locale.ROOT,
                "[devspeed] warp extra spawn passes: %d per tick%s - by %s", passes,
                isActive() ? "" : " (applies while warp is on)", who));
    }

    /** Back to off. {@code server} may be null at shutdown paths with nothing to tell. */
    public static void stop(MinecraftServer server, String reason) {
        if (!isActive()) {
            extraSpawnPasses = 0;
            return;
        }
        long end = lastTotal;
        if (server != null) {
            WorldServer overworld = server.getWorld(0);
            if (overworld != null) {
                end = overworld.getTotalWorldTime();
            }
        }
        SrpWizCore.LOGGER.warn("[srpwizcore] devspeed: WARP x{} STOPPED ({}) - overworld "
                + "totalWorldTime {} -> {}, warp added {} ticks (~{} days) over {} server ticks",
                requested, reason, startTotal, end, addedTotal,
                String.format(Locale.ROOT, "%.2f", addedTotal / 24000.0D), ticksActive);
        requested = 1;
        increment = 1L;
        extraSpawnPasses = 0;
        if (server != null && server.getPlayerList() != null) {
            broadcast(server, String.format(Locale.ROOT,
                    "[devspeed] warp off (%s) - the save aged %.2f extra days", reason,
                    addedTotal / 24000.0D));
            syncTime(server, DimensionManager.getWorlds());
        }
    }

    /** Server start: forget everything without a word (nothing can be running yet). */
    public static void clear() {
        requested = 1;
        increment = 1L;
        extraSpawnPasses = 0;
        lastTotal = -1L;
    }

    private static void broadcast(MinecraftServer server, String line) {
        server.getPlayerList().sendMessage(new TextComponentString(line));
    }

    // ------------------------------------------------------------------ per tick

    /** Server tick END: every world has ticked, vanilla's +1 is already in. */
    public static void onServerTick(MinecraftServer server) {
        if (!isActive()) {
            return;
        }
        ticksActive++;
        WorldServer[] worlds = DimensionManager.getWorlds();
        WorldServer overworld = server.getWorld(0);
        if (overworld == null) {
            return;
        }

        // Snapshot every independent clock before touching any of them.
        Map<WorldInfo, WorldServer> owners = new IdentityHashMap<WorldInfo, WorldServer>();
        Map<WorldInfo, Long> before = new IdentityHashMap<WorldInfo, Long>();
        List<WorldInfo> order = new ArrayList<WorldInfo>();
        WorldInfo overworldInfo = overworld.getWorldInfo();
        owners.put(overworldInfo, overworld);
        before.put(overworldInfo, Long.valueOf(overworldInfo.getWorldTotalTime()));
        order.add(overworldInfo);
        for (WorldServer world : worlds) {
            WorldInfo info = world.getWorldInfo();
            if (info instanceof DerivedWorldInfo || owners.containsKey(info)) {
                continue;
            }
            owners.put(info, world);
            before.put(info, Long.valueOf(info.getWorldTotalTime()));
            order.add(info);
        }

        for (WorldInfo info : order) {
            if (info != overworldInfo
                    && info.getWorldTotalTime() != before.get(info).longValue()) {
                // This clock moved when the overworld's did - it delegates. Skip it.
                continue;
            }
            WorldServer world = owners.get(info);
            boolean daylight = world.getGameRules().getBoolean("doDaylightCycle");
            long total = info.getWorldTotalTime();
            long time = info.getWorldTime();
            long step = increment - 1L;
            step = Math.min(step, room(total));
            if (daylight) {
                step = Math.min(step, room(time));
            }
            if (step <= 0L) {
                continue;
            }
            info.setWorldTotalTime(total + step);
            if (daylight) {
                info.setWorldTime(time + step);
            }
            if (info == overworldInfo) {
                addedTotal += step;
            }
        }
        lastTotal = overworldInfo.getWorldTotalTime();

        syncTime(server, worlds);

        if (extraSpawnPasses > 0) {
            extraSpawns(server, worlds);
        }
    }

    /**
     * Largest extra step that does not let the next END observation jump past a stop. On a stop
     * itself the answer is 0, so that the next START observation sees it too.
     */
    static long room(long clock) {
        long phase = Math.floorMod(clock, STOP);
        if (phase == 0L) {
            return 0L;
        }
        return STOP - phase - 1L;
    }

    /** Vanilla syncs time every 20 ticks; at warp speed that would be a jumpy sky. */
    private static void syncTime(MinecraftServer server, WorldServer[] worlds) {
        for (WorldServer world : worlds) {
            server.getPlayerList().sendPacketToAllPlayersInDimension(
                    new SPacketTimeUpdate(world.getTotalWorldTime(), world.getWorldTime(),
                            world.getGameRules().getBoolean("doDaylightCycle")),
                    world.provider.getDimension());
        }
    }

    /**
     * Mirrors the guard around the vanilla pass in {@code WorldServer.tick}: gamerule
     * {@code doMobSpawning}, not the debug world type, and the same hostile/peaceful switches
     * {@code MinecraftServer.setDifficultyForAllWorlds} would hand the world. The persistent-
     * animal flag stays false — that pass is a 400-tick cadence, not something to multiply.
     */
    private static void extraSpawns(MinecraftServer server, WorldServer[] worlds) {
        if (spawner == null) {
            spawner = new WorldEntitySpawner();
        }
        for (WorldServer world : worlds) {
            if (!world.getGameRules().getBoolean("doMobSpawning")
                    || world.getWorldInfo().getTerrainType() == WorldType.DEBUG_ALL_BLOCK_STATES
                    || world.playerEntities.isEmpty()) {
                continue;
            }
            boolean hardcore = world.getWorldInfo().isHardcoreModeEnabled();
            boolean hostile = hardcore
                    || (world.getDifficulty() != EnumDifficulty.PEACEFUL && server.allowSpawnMonsters());
            boolean peaceful = hardcore || server.getCanSpawnAnimals();
            for (int i = 0; i < extraSpawnPasses; i++) {
                spawner.findChunksForSpawning(world, hostile, peaceful, false);
            }
        }
    }

    // ------------------------------------------------------------------ reporting

    public static List<String> statusLines() {
        List<String> lines = new ArrayList<String>();
        if (!isActive()) {
            lines.add("[devspeed] warp: off");
            return lines;
        }
        lines.add(String.format(Locale.ROOT,
                "[devspeed] warp: x%d (step %d), extra spawn passes %d, added %.2f days so far",
                requested, increment, extraSpawnPasses, addedTotal / 24000.0D));
        return lines;
    }
}
