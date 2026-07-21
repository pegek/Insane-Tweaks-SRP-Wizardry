package com.spege.insanetweaks.dormant;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.DimensionManager;

/**
 * Persistent registry backing {@link com.spege.insanetweaks.api.DormantWaystoneRegistry}.
 *
 * <p>Unlike {@link com.spege.insanetweaks.sanctuary.SanctuaryWorldData} (per-world storage),
 * this data lives in the <b>Overworld's global {@code MapStorage}</b> so the cross-dimensional
 * pair table (Overworld waystone &lt;-&gt; dim 150 return anchor) is held in a single save file.
 * Waystone positions themselves are still tracked per-dimension (the {@code byDim} map) so the
 * nearest-lookup can filter by the caller's dimension cheaply, without scanning the world.
 *
 * <p>Everything here runs server-side (worldgen, block events, GroovyScript calls). {@link #get()}
 * returns {@code null} when the Overworld is not loaded (e.g. called client-side or before the
 * server has started); callers must treat {@code null} as "unavailable" and no-op.
 */
public class DormantWaystoneWorldData extends WorldSavedData {

    private static final String NAME = "insanetweaks_dormant_waystones";

    /** dim id -> set of BlockPos.toLong() for every registered waystone in that dimension. */
    private final Map<Integer, Set<Long>> byDim = new HashMap<Integer, Set<Long>>();
    /** Overworld waystone pos -> dim-150 return anchor pos (BlockPos.toLong() keys). */
    private final Map<Long, Long> owToReturn = new HashMap<Long, Long>();
    /** dim-150 return anchor pos -> Overworld waystone pos (reverse of owToReturn). */
    private final Map<Long, Long> returnToOw = new HashMap<Long, Long>();

    public DormantWaystoneWorldData() { super(NAME); }
    public DormantWaystoneWorldData(String name) { super(name); }

    /**
     * Fetches (or lazily creates) the single global instance attached to the Overworld.
     * Returns {@code null} if the Overworld world is not currently loaded.
     */
    public static DormantWaystoneWorldData get() {
        World overworld = DimensionManager.getWorld(0);
        if (overworld == null) {
            return null;
        }
        MapStorage storage = overworld.getMapStorage();
        if (storage == null) {
            return null;
        }
        DormantWaystoneWorldData data =
                (DormantWaystoneWorldData) storage.getOrLoadData(DormantWaystoneWorldData.class, NAME);
        if (data == null) {
            data = new DormantWaystoneWorldData();
            storage.setData(NAME, data);
        }
        return data;
    }

    // --- waystone set (per dimension) ---

    public void addWaystone(int dim, BlockPos pos) {
        Set<Long> set = byDim.get(Integer.valueOf(dim));
        if (set == null) {
            set = new HashSet<Long>();
            byDim.put(Integer.valueOf(dim), set);
        }
        if (set.add(Long.valueOf(pos.toLong()))) {
            markDirty();
        }
    }

    /**
     * Removes the waystone at {@code pos} in {@code dim} and clears the ENTIRE pair it belongs to
     * (both directions), if any. A no-op if nothing matched.
     */
    public void removeWaystone(int dim, BlockPos pos) {
        boolean changed = false;
        long key = pos.toLong();
        Set<Long> set = byDim.get(Integer.valueOf(dim));
        if (set != null && set.remove(Long.valueOf(key))) {
            if (set.isEmpty()) {
                byDim.remove(Integer.valueOf(dim));
            }
            changed = true;
        }
        if (clearPairFor(key)) {
            changed = true;
        }
        if (changed) {
            markDirty();
        }
    }

    /** Removes any pair whose Overworld end OR return end is {@code key}. Returns true if changed. */
    private boolean clearPairFor(long key) {
        boolean changed = false;
        Long ret = owToReturn.remove(Long.valueOf(key));
        if (ret != null) {
            returnToOw.remove(ret);
            changed = true;
        }
        Long ow = returnToOw.remove(Long.valueOf(key));
        if (ow != null) {
            owToReturn.remove(ow);
            changed = true;
        }
        return changed;
    }

    /**
     * Nearest registered waystone in {@code dim} to {@code from}, by squared distance.
     * Returns {@code null} if the dimension has no registered waystones.
     */
    public BlockPos nearest(int dim, BlockPos from) {
        Set<Long> set = byDim.get(Integer.valueOf(dim));
        if (set == null || set.isEmpty()) {
            return null;
        }
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (Long l : set) {
            BlockPos p = BlockPos.fromLong(l.longValue());
            double sq = from.distanceSq(p);
            if (sq < bestSq) {
                bestSq = sq;
                best = p;
            }
        }
        return best;
    }

    // --- pair table (bidirectional) ---

    public void linkPair(BlockPos owWaystone, BlockPos d150Return) {
        long ow = owWaystone.toLong();
        long ret = d150Return.toLong();
        owToReturn.put(Long.valueOf(ow), Long.valueOf(ret));
        returnToOw.put(Long.valueOf(ret), Long.valueOf(ow));
        markDirty();
    }

    public BlockPos getReturnPos(BlockPos owWaystone) {
        Long ret = owToReturn.get(Long.valueOf(owWaystone.toLong()));
        return ret == null ? null : BlockPos.fromLong(ret.longValue());
    }

    public BlockPos getOverworldPos(BlockPos d150Return) {
        Long ow = returnToOw.get(Long.valueOf(d150Return.toLong()));
        return ow == null ? null : BlockPos.fromLong(ow.longValue());
    }

    // --- persistence ---

    @Override
    public void readFromNBT(NBTTagCompound c) {
        byDim.clear();
        owToReturn.clear();
        returnToOw.clear();

        NBTTagList waystones = c.getTagList("waystones", 10);
        for (int i = 0; i < waystones.tagCount(); i++) {
            NBTTagCompound t = waystones.getCompoundTagAt(i);
            int dim = t.getInteger("dim");
            long pos = t.getLong("pos");
            Set<Long> set = byDim.get(Integer.valueOf(dim));
            if (set == null) {
                set = new HashSet<Long>();
                byDim.put(Integer.valueOf(dim), set);
            }
            set.add(Long.valueOf(pos));
        }

        NBTTagList pairs = c.getTagList("pairs", 10);
        for (int i = 0; i < pairs.tagCount(); i++) {
            NBTTagCompound t = pairs.getCompoundTagAt(i);
            long ow = t.getLong("ow");
            long ret = t.getLong("ret");
            owToReturn.put(Long.valueOf(ow), Long.valueOf(ret));
            returnToOw.put(Long.valueOf(ret), Long.valueOf(ow));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        NBTTagList waystones = new NBTTagList();
        for (Map.Entry<Integer, Set<Long>> e : byDim.entrySet()) {
            int dim = e.getKey().intValue();
            for (Long pos : e.getValue()) {
                NBTTagCompound t = new NBTTagCompound();
                t.setInteger("dim", dim);
                t.setLong("pos", pos.longValue());
                waystones.appendTag(t);
            }
        }
        c.setTag("waystones", waystones);

        NBTTagList pairs = new NBTTagList();
        for (Map.Entry<Long, Long> e : owToReturn.entrySet()) {
            NBTTagCompound t = new NBTTagCompound();
            t.setLong("ow", e.getKey().longValue());
            t.setLong("ret", e.getValue().longValue());
            pairs.appendTag(t);
        }
        c.setTag("pairs", pairs);
        return c;
    }
}
