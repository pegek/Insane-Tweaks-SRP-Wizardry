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

    public static SanctuaryWorldData get(World world) {
        // Serialized in full: MapStorage does no locking of its own, so the check-then-create
        // has to be atomic or a race loses every region registered so far.
        synchronized (CREATE_LOCK) {
            MapStorage storage = world.getPerWorldStorage();
            SanctuaryWorldData data = (SanctuaryWorldData) storage.getOrLoadData(SanctuaryWorldData.class, NAME);
            if (data == null) {
                data = new SanctuaryWorldData();
                storage.setData(NAME, data);
            }
            return data;
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
