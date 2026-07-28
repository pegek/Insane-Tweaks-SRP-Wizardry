package com.spege.srpwizcore.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

/**
 * Decides what {@code ChunkProviderServer.provideChunk} hands back when it is called from a
 * thread that is not the Minecraft server thread.
 *
 * <p><b>Why.</b> EntityThreading-2.2 ticks entities on worker threads. Its own safety layer —
 * {@code MixinWorld}, which HEAD-cancels {@code World.getChunk} on a worker and returns a
 * snapshot or an empty chunk — <em>has never applied</em>: it dies in {@code prepareInjections}
 * on its {@code onIsChunkLoaded} handler, and that failure takes all 17 of its {@code @Inject}s
 * down with it (every boot since 2026-07-21 22:21, first boot after the mod was enabled). What
 * survives is EntityThreading's raw-ASM {@code WorldClassTransformer}, i.e. parallel entity
 * ticking with no guard layer at all. A projectile raytracing into ungenerated terrain then pulls
 * a full chunk generation — population and structures included — onto a worker thread, and the
 * vanilla {@code ArrayList}s underneath tear: {@code MapStorage.loadedDataList} (crash
 * 2026-07-26 00:36, which silently TRUNCATED the world save) and {@code BiomeCache.cache}
 * (crash 2026-07-28 10:28). Diagnosis: {@code notes/crash_biomecache_race_2026-07-28.md}.
 *
 * <p><b>What.</b> Off-thread, an entity sees ungenerated terrain as air instead of generating it.
 * A projectile flies on through, a mob finds no path — cosmetically worse, but the world save
 * cannot be corrupted by it. This is a choke point, not a whole safety layer: it is strictly
 * narrower than the EntityThreading guards it stands in for (no deferral of {@code setBlockState},
 * {@code spawnEntity}, light or neighbour notifications), so recovering those by fixing the ASM
 * transformer that breaks {@code MixinWorld} remains the better end state — see §6B of the note.
 *
 * <p><b>Not covered.</b> {@code World.getBiome} on a worker reaches {@code BiomeCache} through
 * {@code BiomeProvider} with <em>no chunk generation involved</em>, so this guard never sees it.
 * That path is closed separately by NoiseThreader's {@code ReentrantLock} on
 * {@code BiomeCache.getEntry}/{@code cleanupCache}, which its
 * {@code Multithread BetterCaves Noise Generation} flag arms.
 *
 * <p>Lives outside {@code com.spege.srpwizcore.mixins} on purpose, like {@link IntCacheState} and
 * {@link PerfGlueState}: a mixin must not allocate in its own static initialiser, because Mixin
 * merges that {@code <clinit>} into the target and rewrites the {@code invokespecial} to the
 * <em>target's</em> superclass constructor, which fails verification at load time. With every
 * field here, the mixin has no {@code <clinit>} at all.
 */
public final class OffThreadChunkGuard {

    /** Cap on diagnostic lines per game run, so a hot path cannot flood the log. */
    private static final int LOG_LIMIT = 40;

    /** Thread names already reported. One stack per distinct thread is enough to identify it. */
    private static final Set<String> REPORTED_THREADS = ConcurrentHashMap.<String>newKeySet();

    private static final AtomicInteger LINES_LOGGED = new AtomicInteger();

    /** Total off-thread {@code provideChunk} calls intercepted. Diagnostic only. */
    private static final AtomicLong BLOCKED_TOTAL = new AtomicLong();

    /** Off-thread reads of {@code id2ChunkMap} that threw, i.e. lost a concurrent rehash race. */
    private static final AtomicLong REHASH_RACES = new AtomicLong();

    private static volatile boolean armedLogged;

    private OffThreadChunkGuard() {
    }

    /**
     * @return the chunk to return instead of generating one, or {@code null} to let vanilla run.
     */
    public static Chunk provide(ChunkProviderServer provider, int x, int z) {
        if (provider == null) {
            return null;
        }

        MinecraftServer server = provider.world == null ? null : provider.world.getMinecraftServer();
        if (server == null) {
            // Unknown startup window. Never block here — that is how world creation gets bricked.
            return null;
        }
        if (server.isCallingFromMinecraftThread()) {
            return null;
        }

        String thread = Thread.currentThread().getName();
        if (isExempt(thread)) {
            return null;
        }

        if (!armedLogged) {
            armedLogged = true;
            SrpWizCore.LOGGER.info(
                    "[srpwizcore] off-thread chunk guard armed (blocking={}, readLoadedChunk={})",
                    Boolean.valueOf(SrpWizCoreConfig.threadingCompat.blockOffThreadChunkGen),
                    Boolean.valueOf(SrpWizCoreConfig.threadingCompat.readLoadedChunkOffThread));
        }

        if (!SrpWizCoreConfig.threadingCompat.blockOffThreadChunkGen) {
            report(thread, x, z, false);
            return null;
        }

        BLOCKED_TOTAL.incrementAndGet();
        report(thread, x, z, true);

        Chunk loaded = readLoadedChunk(provider, x, z);
        return loaded != null ? loaded : new SrpEmptyChunk(provider.world, x, z);
    }

    /**
     * Reads {@code id2ChunkMap} ({@code field_73244_f}) directly rather than calling
     * {@code getLoadedChunk} — {@code func_186026_b} writes {@code chunk.unloadQueued = false},
     * and an off-thread write could un-queue a chunk the server thread is mid-unload on.
     *
     * <p>The map is a {@code Long2ObjectOpenHashMap} (vintagefix swaps in a subclass of one), i.e.
     * open addressing with linear probing. Its {@code get} captures the {@code key} array into a
     * local once but re-reads {@code mask} from the field on every probe, while {@code rehash}
     * publishes the new {@code mask} <em>before</em> the new {@code key} array. An off-thread read
     * that lands in that window can index past the end of the old array or derive a slot against
     * one table and read the value out of the other. So both guards are load-bearing and neither
     * substitutes for the other: the {@code catch} handles the out-of-bounds, and the coordinate
     * check rejects a chunk belonging to a different position.
     */
    private static Chunk readLoadedChunk(ChunkProviderServer provider, int x, int z) {
        if (!SrpWizCoreConfig.threadingCompat.readLoadedChunkOffThread) {
            return null;
        }
        try {
            Chunk chunk = provider.id2ChunkMap.get(ChunkPos.asLong(x, z));
            if (chunk != null && chunk.x == x && chunk.z == z) {
                return chunk;
            }
        } catch (Throwable rehashRace) {
            long races = REHASH_RACES.incrementAndGet();
            if (races == 1L) {
                SrpWizCore.LOGGER.warn(
                        "[srpwizcore] off-thread read of id2ChunkMap lost a rehash race; falling "
                                + "back to an empty chunk (set 'Fix: Read Loaded Chunk Off-Thread' "
                                + "to false to stop reading the map entirely)",
                        rehashRace);
            }
        }
        return null;
    }

    private static boolean isExempt(String threadName) {
        String[] allowed = SrpWizCoreConfig.threadingCompat.offThreadChunkGenAllowedThreads;
        if (allowed == null) {
            return false;
        }
        for (int i = 0; i < allowed.length; i++) {
            String prefix = allowed[i];
            if (prefix != null && !prefix.isEmpty() && threadName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One line per distinct thread name, with a stack trace. The thread name is the whole point:
     * anything other than {@code EntityThreading-Worker-*} is an off-thread caller that was not
     * predicted, and the stack says whether it needs exempting or investigating.
     */
    private static void report(String thread, int x, int z, boolean blocked) {
        if (!REPORTED_THREADS.add(thread) || LINES_LOGGED.incrementAndGet() > LOG_LIMIT) {
            return;
        }
        SrpWizCore.LOGGER.warn(
                "[srpwizcore] off-thread ChunkProviderServer.provideChunk({}, {}) from thread {} "
                        + "— {}. Add a prefix to 'Diag: Off-Thread Chunk Gen Allowed Threads' if "
                        + "this caller is legitimate.",
                Integer.valueOf(x), Integer.valueOf(z), thread,
                blocked ? "BLOCKED, returning a loaded or empty chunk" : "allowed (blocking is off)",
                new Throwable("off-thread chunk generation"));
    }

    /** Total off-thread {@code provideChunk} calls intercepted this run. */
    public static long getBlockedTotal() {
        return BLOCKED_TOTAL.get();
    }
}
