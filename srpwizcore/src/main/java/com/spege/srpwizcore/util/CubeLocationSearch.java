package com.spege.srpwizcore.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;

/**
 * Bounded replacement for the random-destination search of Enigmatic Legacy's Non-Euclidean Cube
 * ({@code keletu.enigmaticlegacy.item.ItemTheCube#findBlockPos}). Applied by
 * {@code com.spege.srpwizcore.mixins.enigmatic.MixinElItemTheCube}; this class deliberately
 * mentions no Enigmatic Legacy type, so it stays loadable with the mod absent.
 *
 * <p><b>Why.</b> Crash 2026-08-02 21:24, {@code Ticking player}, {@code StackOverflowError} in
 * {@code findBlockPos}. That method draws a random point in a disc of radius 100..10000 around the
 * world origin, scans its column for ground, and on a miss <em>calls itself</em>, bailing out only
 * at {@code depth > 10000}. No JVM thread stack holds 10 000 frames, so the bail-out is dead code:
 * a run of misses ends in {@code StackOverflowError}, never in the fallback. The search also runs
 * on an {@code Executors.newCachedThreadPool()} worker, and its
 * {@code catch (Exception)} does not catch an {@code Error} — the failure reaches
 * {@code Future.get()} on the server thread, where {@code onWornTick} rethrows it as a
 * {@code RuntimeException} and kills the server.
 *
 * <p><b>What made a run of misses certain.</b> {@link OffThreadChunkGuard} answers
 * {@code provideChunk} off the server thread with an empty chunk instead of generating one, so
 * every probe on that worker reads air and no probe can ever succeed. Only chunks that happen to
 * be loaded are visible — a few hundred out of the ~380 000 candidates in that disc. The trigger
 * on 2026-08-02 was the Cube's active ability dropping the player in The End, where
 * {@code onWornTick} then resubmits the search every tick because the cached location's dimension
 * matches the player's own.
 *
 * <p><b>How this replaces it.</b> Three changes, each independently sufficient to stop the crash:
 * <ul>
 *   <li>Iteration instead of recursion. The probe budget cannot cost stack depth.</li>
 *   <li>The search runs on the server thread ({@code generateCachedLocation} no longer reaches the
 *       executor), so probing terrain that does not exist yet generates it the legitimate way and
 *       the guard above never sees this caller. That is also what makes the item work again.</li>
 *   <li>Probes that would generate a chunk are metered — {@link #stepBackground} spends
 *       {@code generatedProbesPerTick} of them per tick and returns {@code null} until it has an
 *       answer, so a search costs no visible hitch even in an OTG dimension.</li>
 * </ul>
 *
 * <p><b>Fallback discipline.</b> Enigmatic Legacy's own fallback is the player's current position
 * <em>and current dimension</em>, which is a trap: {@code onWornTick} compares the cached
 * dimension against the player's and regenerates on a match, so that fallback re-runs the search
 * every tick forever. Every {@link Spot} produced here carries a dimension drawn from the item's
 * own pool minus the player's current one, so a completed search always settles.
 */
public final class CubeLocationSearch {

    /** Matches {@code findBlockPos}: a point in a disc of radius 100..10000 around the origin. */
    private static final double MIN_DISTANCE = 100.0D;

    private static final double DISTANCE_SPREAD = 9900.0D;

    /** Vertical clearance demanded above the ground block, in blocks. */
    private static final int HEADROOM = 2;

    /** Enigmatic Legacy's own scan ceiling for the Nether, kept so behaviour there is unchanged. */
    private static final int NETHER_SCAN_TOP = 110;

    /** Cap on fallback reports per game run. */
    private static final int LOG_LIMIT = 10;

    /** Cap on reports of a search that failed outright, which cannot happen once this is armed. */
    private static final int FAILURE_LOG_LIMIT = 5;

    private static final AtomicInteger FALLBACKS_LOGGED = new AtomicInteger();

    private static final AtomicInteger FAILURES_LOGGED = new AtomicInteger();

    /**
     * In-flight background searches, one per player. Server thread only — {@code onWornTick} is
     * the sole caller and this class is never reached from the item's executor, because the
     * mixin cancels the submit that would have started it.
     */
    private static final Map<EntityPlayerMP, Search> SEARCHES = new WeakHashMap<>();

    private CubeLocationSearch() {
    }

    /** A destination the item can teleport to. Never {@code null} once a search has settled. */
    public static final class Spot {

        public final int dimension;

        public final double x;

        public final double y;

        public final double z;

        /** True when no probe succeeded and this is a safe substitute rather than a find. */
        public final boolean fallback;

        Spot(int dimension, double x, double y, double z, boolean fallback) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fallback = fallback;
        }
    }

    /** Per-player state of a background search that spans several ticks. */
    private static final class Search {

        int dimension;

        int generatedProbesLeft;
    }

    /**
     * Whole search in one call, for the interactive path ({@code triggerActiveAbility}): the
     * player pressed the button and is waiting, so the metered budget is spent immediately.
     *
     * @param world  the world to draw coordinates from — Enigmatic Legacy passes the destination
     *               world here and the player's own world on the cached path; either way the
     *               coordinates come from whatever it hands us, which keeps its semantics intact.
     * @param pool   the item's dimension list; the player's current dimension is excluded from it.
     * @return never {@code null}.
     */
    public static Spot searchNow(World world, EntityPlayerMP player, List<Integer> pool) {
        int dimension = pickDimension(pool, player.world.provider.getDimension(), world.rand);
        Spot hit = probe(world, player, dimension,
                SrpWizCoreConfig.enigmaticCompat.cubeLoadedChunkProbes, false);
        if (hit != null) {
            return hit;
        }
        hit = probe(world, player, dimension,
                SrpWizCoreConfig.enigmaticCompat.cubeGeneratedChunkProbes, true);
        return hit != null ? hit : fallback(world, player, dimension);
    }

    /**
     * One tick's worth of a background search.
     *
     * @return the finished destination, or {@code null} while the search still has budget left.
     *         The caller must keep calling this every tick until it answers — Enigmatic Legacy's
     *         {@code onWornTick} does exactly that on its own, because an absent (or
     *         same-dimension) cache entry is what makes it call {@code generateCachedLocation}.
     */
    public static Spot stepBackground(EntityPlayerMP player, List<Integer> pool) {
        World world = player.world;
        Search search = SEARCHES.get(player);

        if (search == null) {
            search = new Search();
            search.dimension = pickDimension(pool, world.provider.getDimension(), world.rand);
            search.generatedProbesLeft = SrpWizCoreConfig.enigmaticCompat.cubeGeneratedChunkProbes;
            // The cheap half of the budget costs a map lookup per probe and a column scan only on
            // the rare probe that lands in a loaded chunk, so it is not worth spreading out.
            Spot hit = probe(world, player, search.dimension,
                    SrpWizCoreConfig.enigmaticCompat.cubeLoadedChunkProbes, false);
            if (hit != null) {
                return hit;
            }
            SEARCHES.put(player, search);
            return null;
        }

        int budget = Math.min(search.generatedProbesLeft,
                Math.max(1, SrpWizCoreConfig.enigmaticCompat.cubeGeneratedProbesPerTick));
        search.generatedProbesLeft -= budget;

        Spot hit = probe(world, player, search.dimension, budget, true);
        if (hit != null) {
            SEARCHES.remove(player);
            return hit;
        }
        if (search.generatedProbesLeft > 0) {
            return null;
        }
        SEARCHES.remove(player);
        return fallback(world, player, search.dimension);
    }

    /**
     * A destination that is always available, for the one path that cannot wait: the mixin's
     * guard on {@code Future.get()}, which must hand back something rather than let a failed
     * search reach the server tick.
     */
    /**
     * Rate limit for the mixin's report of a failed {@code Future}. Lives here rather than in the
     * mixin on purpose: a counter field there would give the mixin a {@code <clinit>}, and Mixin
     * merges that into the target class — the failure mode this repo has already paid a
     * {@code VerifyError} for once (see {@code MixinChunkProviderServerThreadGuard}).
     */
    public static boolean shouldReportFailure() {
        return FAILURES_LOGGED.incrementAndGet() <= FAILURE_LOG_LIMIT;
    }

    public static Spot emergency(EntityPlayerMP player) {
        World world = player.world;
        SEARCHES.remove(player);
        return fallback(world, player, pickDimension(null, world.provider.getDimension(),
                world.rand));
    }

    /**
     * Runs up to {@code attempts} probes and returns the first hit.
     *
     * @param mayGenerate when false, a probe whose chunk is not already loaded is skipped instead
     *                    of generating it — that is the free budget. When true the probe generates,
     *                    which is legitimate here because this only ever runs on the server thread;
     *                    if it somehow does not, generation is dropped rather than handed to
     *                    {@link OffThreadChunkGuard}, whose empty chunk would make every probe miss.
     */
    private static Spot probe(World world, EntityPlayerMP player, int dimension, int attempts,
            boolean mayGenerate) {
        if (attempts <= 0) {
            return null;
        }
        boolean generate = mayGenerate && isServerThread(world);
        WorldBorder border = world.getWorldBorder();
        Random random = world.rand;
        int top = scanTop(world);
        int eyeHeight = MathHelper.ceil(player.getEyeHeight());

        for (int attempt = 0; attempt < attempts; attempt++) {
            double distance = MIN_DISTANCE + random.nextDouble() * DISTANCE_SPREAD;
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = MathHelper.floor(Math.cos(angle) * distance);
            int z = MathHelper.floor(Math.sin(angle) * distance);
            if (!border.contains(new BlockPos(x, 0, z))) {
                continue;
            }

            Chunk chunk;
            if (generate) {
                chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
            } else {
                chunk = world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
                if (chunk == null) {
                    continue;
                }
            }

            int ground = scanColumn(chunk, x, z, top, eyeHeight);
            if (ground >= 0) {
                // +2.5 is Enigmatic Legacy's own landing offset above the block it found.
                return new Spot(dimension, x + 0.5D, ground + 2.5D, z + 0.5D, false);
            }
        }
        return null;
    }

    /**
     * Highest block in the column with a non-air, non-lava top and clear space above it.
     *
     * @return that block's Y, or {@code -1} if the column has none (an ungenerated chunk, a
     *         void column in The End, open ocean floor under a full water column, ...).
     */
    private static int scanColumn(Chunk chunk, int x, int z, int top, int eyeHeight) {
        int clearance = Math.max(HEADROOM, eyeHeight);
        for (int y = top; y > 0; y--) {
            IBlockState state = chunk.getBlockState(x, y, z);
            Material material = state.getMaterial();
            if (material == Material.AIR || material == Material.LAVA) {
                continue;
            }
            boolean clear = true;
            for (int above = 1; above <= clearance; above++) {
                // isFullCube, not isFullBlock: Enigmatic Legacy tests the same thing, and it is
                // the right one — glass and leaves are full cubes you cannot stand inside, but
                // they are not "full blocks" (that also demands opacity).
                if (chunk.getBlockState(x, y + above, z).isFullCube()) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return y;
            }
        }
        return -1;
    }

    /**
     * Substitute destination when every probe missed, tried in order of how much it resembles a
     * real find: the player's own column, the dimension's designated arrival point (The End's
     * obsidian platform, via {@code WorldProvider.getSpawnCoordinate()}), the world spawn, and
     * finally the player's exact position — which is Enigmatic Legacy's own fallback, minus its
     * dimension trap.
     */
    private static Spot fallback(World world, EntityPlayerMP player, int dimension) {
        Spot spot = column(world, player, dimension, MathHelper.floor(player.posX),
                MathHelper.floor(player.posZ));
        if (spot == null) {
            BlockPos arrival = world.provider.getSpawnCoordinate();
            if (arrival != null) {
                spot = column(world, player, dimension, arrival.getX(), arrival.getZ());
            }
        }
        if (spot == null) {
            BlockPos spawn = world.getSpawnPoint();
            spot = column(world, player, dimension, spawn.getX(), spawn.getZ());
        }
        if (spot == null) {
            spot = new Spot(dimension, player.posX, player.posY, player.posZ, true);
        }
        if (FALLBACKS_LOGGED.incrementAndGet() <= LOG_LIMIT) {
            SrpWizCore.LOGGER.info(
                    "[srpwizcore] Non-Euclidean Cube found no random destination in dim {} for {};"
                            + " substituting {} {} {} in dim {}.",
                    Integer.valueOf(world.provider.getDimension()), player.getName(),
                    Double.valueOf(spot.x), Double.valueOf(spot.y), Double.valueOf(spot.z),
                    Integer.valueOf(dimension));
        }
        return spot;
    }

    /** Scans one named column, generating its chunk only if we are on the server thread. */
    private static Spot column(World world, EntityPlayerMP player, int dimension, int x, int z) {
        Chunk chunk = isServerThread(world)
                ? world.getChunkFromChunkCoords(x >> 4, z >> 4)
                : world.getChunkProvider().getLoadedChunk(x >> 4, z >> 4);
        if (chunk == null) {
            return null;
        }
        int ground = scanColumn(chunk, x, z, scanTop(world), MathHelper.ceil(player.getEyeHeight()));
        return ground < 0 ? null : new Spot(dimension, x + 0.5D, ground + 2.5D, z + 0.5D, true);
    }

    /**
     * Enigmatic Legacy starts at 110 in the Nether and 256 everywhere else. The Nether number is
     * kept verbatim — it is what puts the search under the bedrock roof rather than on top of it —
     * while everything else asks the dimension instead of assuming 256.
     */
    private static int scanTop(World world) {
        if (world.provider.getDimension() == -1) {
            return NETHER_SCAN_TOP;
        }
        return Math.min(255, world.getActualHeight() - 1);
    }

    /**
     * Random member of the item's dimension pool other than {@code exclude}. A {@code null} or
     * exhausted pool falls back to the Overworld, and to the Nether if the player is already
     * there: the one thing the result must not be is {@code exclude}, or {@code onWornTick} will
     * treat the finished search as stale and start another one on the next tick.
     */
    private static int pickDimension(List<Integer> pool, int exclude, Random random) {
        List<Integer> candidates = new ArrayList<>(pool == null ? 0 : pool.size());
        if (pool != null) {
            for (Integer dimension : pool) {
                if (dimension != null && dimension.intValue() != exclude) {
                    candidates.add(dimension);
                }
            }
        }
        if (candidates.isEmpty()) {
            return exclude == 0 ? -1 : 0;
        }
        return candidates.get(random.nextInt(candidates.size())).intValue();
    }

    private static boolean isServerThread(World world) {
        MinecraftServer server = world.getMinecraftServer();
        return server != null && server.isCallingFromMinecraftThread();
    }
}
