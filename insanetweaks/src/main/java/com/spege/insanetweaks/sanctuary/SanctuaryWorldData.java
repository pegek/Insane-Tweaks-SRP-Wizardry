package com.spege.insanetweaks.sanctuary;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Per-world registry of active sanctuary regions. Single source of truth queried by the SRP vetoes.
 *
 * <p>THREAD SAFETY — this class is read from worker threads and must stay safe there.
 * {@code SanctuaryRegionHelper.isProtected} reaches it from {@code MixinBeckonBlockInfestation}
 * (SRP infestation spread, ~1100 calls/s at a large node area) and from
 * {@code SanctuarySpawnVetoHandler} (up to ~387 spawn attempts/s), and EntityThreading ticks both
 * on worker threads. That was caught live on 2026-07-26 06:58:32 — srpwizcore's MapStorage
 * diagnostic logged {@code setData off-thread: id=insanetweaks_sanctuaries thread=EntityThreading-Worker-190}
 * with 29 entries already in the list, i.e. this class was the mod registering world data from a
 * worker while the server thread was using the same structures.
 *
 * <p>Two separate hazards, fixed separately:
 * <ul>
 * <li><b>Duplicate creation.</b> {@code get} was an unsynchronized check-then-create on
 *     {@code MapStorage}, whose backing map is a plain {@code HashMap} with no synchronization of
 *     its own. Two threads could both see {@code null}, both create, both register — and the
 *     second {@code setData} replaces the first, so every region registered so far silently
 *     disappears. The whole method is now serialized on {@link #CREATE_LOCK}.</li>
 * <li><b>Concurrent access to {@code regions}.</b> Now a {@link CopyOnWriteArrayList}: the hot
 *     read paths ({@link #isInside}, {@link #isInsideCapped}, {@link #writeToNBT}) need no lock
 *     and always see a consistent snapshot, while the rare writers (a sanctuary core being placed,
 *     retuned or broken) are serialized on the instance monitor.</li>
 * </ul>
 *
 * <p>The lock below is an ordinary field because this is an ordinary class. The "never allocate in
 * a static field initialiser" rule in CLAUDE.md applies to <em>mixins</em> only — there is no
 * {@code <clinit>} merging here.
 */
public class SanctuaryWorldData extends WorldSavedData {

    private static final String NAME = "insanetweaks_sanctuaries";

    private static final Object CREATE_LOCK = new Object();

    /** each: {x, y, z, radius}. Copy-on-write: lock-free reads, serialized rare writes. */
    private final List<int[]> regions = new CopyOnWriteArrayList<int[]>();

    public SanctuaryWorldData() { super(NAME); }
    public SanctuaryWorldData(String name) { super(name); }

    /**
     * Resolved instance per dimension. The {@code world} field is what makes the entry safe rather
     * than merely fast: a dimension can be unloaded and loaded again, which produces a new
     * {@code World} with a new {@code MapStorage}, and an entry that only matched on dimension id
     * would then hand out data belonging to a world that no longer exists.
     *
     * <p>Both fields are final, so publishing the entry through the map publishes them with it.
     */
    private static final class Resolved {
        final World world;
        final SanctuaryWorldData data;

        Resolved(World world, SanctuaryWorldData data) {
            this.world = world;
            this.data = data;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<Integer, Resolved> RESOLVED =
            new java.util.concurrent.ConcurrentHashMap<Integer, Resolved>();

    /**
     * 🚨 The side has to be part of the key, not just the dimension id. In single player the client
     * world and the integrated server's world are both dimension 0 but are different objects with
     * different {@code MapStorage}s. Keyed on the id alone they would evict each other on every
     * alternating call, the identity check would miss every time, and the lock would be taken on
     * every call again - the optimisation would silently do nothing in exactly the environment it
     * is developed in. Reskillable's {@code PlayerDataHandler.getKey} splits its cache the same way.
     */
    private static Integer cacheKey(World world) {
        return Integer.valueOf((world.provider.getDimension() << 1) | (world.isRemote ? 1 : 0));
    }

    /**
     * 🚨 This is one of the hottest paths in the mod and it used to take a GLOBAL static lock on
     * every single call.
     *
     * <p>{@link #CREATE_LOCK} exists for one reason - {@code MapStorage} does no locking of its own,
     * so the check-then-create has to be atomic or a race loses every region registered so far
     * (that is a real bug, fixed 2026-07-26, see the class javadoc). But after the first successful
     * resolution there is nothing left to protect, and every later call was paying for it anyway.
     *
     * <p>Who was paying: {@code SanctuaryPurgeFireHandler} reaches here for EVERY SRP parasite on
     * EVERY tick - note that {@code isInPurgeRange} resolves the data before it checks whether the
     * position is even inside a dome, so distance from a sanctuary buys nothing - plus
     * {@code MixinBeckonBlockInfestation} (~1100 calls/s at a large node area),
     * {@code SanctuarySpawnVetoHandler} on both {@code CheckSpawn} and, since 1.9.13,
     * {@code EntityJoinWorldEvent} for every entity entering the world. The last three run on
     * EntityThreading worker threads, so this was a single global monitor contended between the
     * server thread and the workers - and a sampling profiler under-reports that, because time
     * blocked on a monitor is not CPU time.
     *
     * <p>Now: a lock-free {@code ConcurrentHashMap} read on the hot path, and the synchronized block
     * only on a miss, with the check repeated inside it so two threads racing on a cold cache still
     * cannot both create. Identical guarantees, paid once per dimension instead of once per call.
     */
    public static SanctuaryWorldData get(World world) {
        Integer key = cacheKey(world);

        Resolved cached = RESOLVED.get(key);
        if (cached != null && cached.world == world) {
            return cached.data;
        }

        synchronized (CREATE_LOCK) {
            // Re-check inside the lock: another thread may have resolved this dimension between our
            // miss above and acquiring the monitor.
            cached = RESOLVED.get(key);
            if (cached != null && cached.world == world) {
                return cached.data;
            }

            MapStorage storage = world.getPerWorldStorage();
            SanctuaryWorldData data = (SanctuaryWorldData) storage.getOrLoadData(SanctuaryWorldData.class, NAME);
            if (data == null) {
                data = new SanctuaryWorldData();
                storage.setData(NAME, data);
            }
            RESOLVED.put(key, new Resolved(world, data));
            return data;
        }
    }

    /**
     * Drops the cached entry when a dimension unloads.
     *
     * <p>The identity check in {@link #get} already makes a stale entry harmless - a reloaded
     * dimension is a different {@code World} object, so it misses and re-resolves. This exists to
     * stop the map holding a dead {@code World} alive, which would pin that dimension's entire
     * object graph for the rest of the session.
     */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.spege.insanetweaks.InsaneTweaksMod.MODID)
    public static final class CacheInvalidator {

        private CacheInvalidator() {}

        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
        public static void onWorldUnload(net.minecraftforge.event.world.WorldEvent.Unload event) {
            World world = event.getWorld();
            if (world == null || world.provider == null) {
                return;
            }
            Integer key = cacheKey(world);
            Resolved cached = RESOLVED.get(key);
            // Only drop OUR entry: on a dimension swap the map may already hold the replacement.
            if (cached != null && cached.world == world) {
                RESOLVED.remove(key, cached);
            }
        }
    }

    /** Insert-or-update the region anchored at pos. radius<=0 removes it. */
    public synchronized void setRegion(BlockPos pos, int radius) {
        for (int i = 0; i < regions.size(); i++) {
            int[] r = regions.get(i);
            if (r[0] == pos.getX() && r[1] == pos.getY() && r[2] == pos.getZ()) {
                if (radius <= 0) {
                    regions.remove(i);
                } else {
                    // Replace rather than mutate r[3] in place: only the copy-on-write set()
                    // publishes the new value to readers on other threads.
                    regions.set(i, new int[] { r[0], r[1], r[2], radius });
                }
                markDirty();
                return;
            }
        }
        if (radius > 0) {
            regions.add(new int[] { pos.getX(), pos.getY(), pos.getZ(), radius });
            markDirty();
        }
    }

    public void removeRegion(BlockPos pos) { setRegion(pos, 0); }

    /** Cylinder test (full height): dx^2 + dz^2 <= r^2 for any active region. */
    public boolean isInside(int x, int z) {
        for (int i = 0; i < regions.size(); i++) {
            int[] r = regions.get(i);
            long dx = x - r[0];
            long dz = z - r[2];
            long rr = (long) r[3] * r[3];
            if (dx * dx + dz * dz <= rr) {
                return true;
            }
        }
        return false;
    }

    /** Cylinder test using min(regionRadius, radiusCap) — for effects with a smaller cap than protection. */
    public boolean isInsideCapped(int x, int z, int radiusCap) {
        for (int i = 0; i < regions.size(); i++) {
            int[] r = regions.get(i);
            int eff = Math.min(r[3], radiusCap);
            long dx = x - r[0];
            long dz = z - r[2];
            long rr = (long) eff * eff;
            if (dx * dx + dz * dz <= rr) {
                return true;
            }
        }
        return false;
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound c) {
        // Stage into a plain list first: one clear + one addAll on the copy-on-write list instead
        // of a fresh array copy per entry.
        NBTTagList list = c.getTagList("regions", 10);
        List<int[]> loaded = new ArrayList<int[]>(list.tagCount());
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            loaded.add(new int[] { t.getInteger("x"), t.getInteger("y"), t.getInteger("z"), t.getInteger("r") });
        }
        regions.clear();
        regions.addAll(loaded);
    }

    /** No lock: iterating the copy-on-write list is a consistent snapshot, so a save can never
     *  interleave with a sanctuary being placed on another thread. */
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        NBTTagList list = new NBTTagList();
        for (int[] r : regions) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("x", r[0]); t.setInteger("y", r[1]); t.setInteger("z", r[2]); t.setInteger("r", r[3]);
            list.appendTag(t);
        }
        c.setTag("regions", list);
        return c;
    }
}
