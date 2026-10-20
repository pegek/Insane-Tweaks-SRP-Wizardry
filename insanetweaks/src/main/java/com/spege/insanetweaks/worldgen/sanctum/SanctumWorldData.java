package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Per-dimension sanctum bookkeeping:
 * <ul>
 *   <li>decisions - whether a start was judged valid (integrity rule);</li>
 *   <li>records - each sanctum's remaining anchors and its containers, and whether it is cleared
 *   (all anchors defeated, Arcane Lock lifted).</li>
 * </ul>
 * An unknown sanctum counts as cleared: a lock nobody can lift is worse than a free chest.
 */
public class SanctumWorldData extends WorldSavedData {

    public static final String NAME = "insanetweaks_sanctums";
    private static final int TAG_LONG = 4;
    private static final int TAG_COMPOUND = 10;

    private final Map<Long, Boolean> decisions = new HashMap<Long, Boolean>();
    private final Map<Long, Record> records = new HashMap<Long, Record>();

    private static final class Record {
        final Set<Long> anchors = new HashSet<Long>();
        final List<Long> containers = new ArrayList<Long>();
        boolean cleared;
        /** minX, minZ, maxX, maxZ of the footprint; null for records written before 1.27.0. */
        int[] bounds;
        /** hullMinY, hullMaxY of the arcane-wall hull; null for records written before 1.28.0. */
        int[] hullY;
        /** Chunks whose hull has been dissolved since the sanctum was cleared. */
        final Set<Long> swept = new HashSet<Long>();
    }

    public SanctumWorldData() {
        this(NAME);
    }

    public SanctumWorldData(String name) {
        super(name);
    }

    public static SanctumWorldData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        SanctumWorldData data = (SanctumWorldData) storage.getOrLoadData(SanctumWorldData.class, NAME);
        if (data == null) {
            data = new SanctumWorldData();
            storage.setData(NAME, data);
        }
        return data;
    }

    public Boolean decision(long start) {
        return this.decisions.get(Long.valueOf(start));
    }

    public void decide(long start, boolean valid) {
        this.decisions.put(Long.valueOf(start), Boolean.valueOf(valid));
        markDirty();
    }

    /** Records a sanctum once, with its horizontal footprint; later calls for the same id are ignored. */
    public void register(long id, Collection<Long> anchors, Collection<Long> containers, int minX, int minZ, int maxX,
            int maxZ, int hullMinY, int hullMaxY) {
        Long key = Long.valueOf(id);
        if (this.records.containsKey(key)) {
            return;
        }
        Record r = new Record();
        r.anchors.addAll(anchors);
        r.containers.addAll(containers);
        r.cleared = r.anchors.isEmpty();
        r.bounds = new int[] { minX, minZ, maxX, maxZ };
        r.hullY = new int[] { hullMinY, hullMaxY };
        this.records.put(key, r);
        markDirty();
    }

    /** minX, minZ, maxX, maxZ, hullMinY, hullMaxY of a sanctum with a hull, or null. */
    public int[] box(long id) {
        Record r = this.records.get(Long.valueOf(id));
        if (r == null || r.bounds == null || r.hullY == null) {
            return null;
        }
        return new int[] { r.bounds[0], r.bounds[1], r.bounds[2], r.bounds[3], r.hullY[0], r.hullY[1] };
    }

    /** Cleared sanctums with a hull that overlaps this chunk and has not been dissolved there yet. */
    public List<Long> clearedNeedingSweep(int chunkX, int chunkZ) {
        List<Long> out = new ArrayList<Long>();
        Long chunk = Long.valueOf(net.minecraft.util.math.ChunkPos.asLong(chunkX, chunkZ));
        for (Map.Entry<Long, Record> e : this.records.entrySet()) {
            Record r = e.getValue();
            if (!r.cleared || r.bounds == null || r.hullY == null || r.swept.contains(chunk)) {
                continue;
            }
            if ((chunkX << 4) + 15 >= r.bounds[0] && (chunkX << 4) <= r.bounds[2] && (chunkZ << 4) + 15 >= r.bounds[1]
                    && (chunkZ << 4) <= r.bounds[3]) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    public void markSwept(long id, long chunk) {
        Record r = this.records.get(Long.valueOf(id));
        if (r != null && r.swept.add(Long.valueOf(chunk))) {
            markDirty();
        }
    }

    /** Like {@link #nearSanctum}, but only sanctums that are not yet cleared. */
    public boolean nearUnclearedSanctum(int x, int z, int margin) {
        for (Record r : this.records.values()) {
            int[] b = r.bounds;
            if (!r.cleared && b != null && x >= b[0] - margin && x <= b[2] + margin && z >= b[1] - margin
                    && z <= b[3] + margin) {
                return true;
            }
        }
        return false;
    }

    /** Whether x, z lies within {@code margin} blocks of any recorded sanctum's footprint. */
    public boolean nearSanctum(int x, int z, int margin) {
        for (Record r : this.records.values()) {
            int[] b = r.bounds;
            if (b != null && x >= b[0] - margin && x <= b[2] + margin && z >= b[1] - margin && z <= b[3] + margin) {
                return true;
            }
        }
        return false;
    }

    /** Marks one anchor defeated; true exactly when this cleared the sanctum. */
    public boolean anchorDone(long id, long anchor) {
        Record r = this.records.get(Long.valueOf(id));
        if (r == null || r.cleared || !r.anchors.remove(Long.valueOf(anchor))) {
            return false;
        }
        if (r.anchors.isEmpty()) {
            r.cleared = true;
        }
        markDirty();
        return r.cleared;
    }

    public boolean isCleared(long id) {
        Record r = this.records.get(Long.valueOf(id));
        return r == null || r.cleared;
    }

    public boolean isAnchorDone(long id, long anchor) {
        Record r = this.records.get(Long.valueOf(id));
        return r == null || r.cleared || !r.anchors.contains(Long.valueOf(anchor));
    }

    public List<Long> containers(long id) {
        Record r = this.records.get(Long.valueOf(id));
        return r == null ? new ArrayList<Long>() : new ArrayList<Long>(r.containers);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.decisions.clear();
        this.records.clear();
        for (long v : longs(nbt.getTagList("Valid", TAG_LONG))) {
            this.decisions.put(Long.valueOf(v), Boolean.TRUE);
        }
        for (long v : longs(nbt.getTagList("Invalid", TAG_LONG))) {
            this.decisions.put(Long.valueOf(v), Boolean.FALSE);
        }
        NBTTagList list = nbt.getTagList("Sanctums", TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            Record r = new Record();
            r.anchors.addAll(longs(c.getTagList("Anchors", TAG_LONG)));
            r.containers.addAll(longs(c.getTagList("Containers", TAG_LONG)));
            r.cleared = c.getBoolean("Cleared");
            int[] bounds = c.getIntArray("Bounds");
            r.bounds = bounds.length == 4 ? bounds : null;
            int[] hullY = c.getIntArray("HullY");
            r.hullY = hullY.length == 2 ? hullY : null;
            r.swept.addAll(longs(c.getTagList("Swept", TAG_LONG)));
            this.records.put(Long.valueOf(c.getLong("Id")), r);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList valid = new NBTTagList();
        NBTTagList invalid = new NBTTagList();
        for (Map.Entry<Long, Boolean> e : this.decisions.entrySet()) {
            (e.getValue().booleanValue() ? valid : invalid).appendTag(new NBTTagLong(e.getKey().longValue()));
        }
        nbt.setTag("Valid", valid);
        nbt.setTag("Invalid", invalid);
        NBTTagList sanctums = new NBTTagList();
        for (Map.Entry<Long, Record> e : this.records.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("Id", e.getKey().longValue());
            c.setTag("Anchors", toList(e.getValue().anchors));
            c.setTag("Containers", toList(e.getValue().containers));
            c.setBoolean("Cleared", e.getValue().cleared);
            if (e.getValue().bounds != null) {
                c.setIntArray("Bounds", e.getValue().bounds);
            }
            if (e.getValue().hullY != null) {
                c.setIntArray("HullY", e.getValue().hullY);
            }
            if (!e.getValue().swept.isEmpty()) {
                c.setTag("Swept", toList(e.getValue().swept));
            }
            sanctums.appendTag(c);
        }
        nbt.setTag("Sanctums", sanctums);
        return nbt;
    }

    private static List<Long> longs(NBTTagList list) {
        List<Long> out = new ArrayList<Long>();
        for (int i = 0; i < list.tagCount(); i++) {
            out.add(Long.valueOf(((NBTTagLong) list.get(i)).getLong()));
        }
        return out;
    }

    private static NBTTagList toList(Collection<Long> values) {
        NBTTagList list = new NBTTagList();
        for (Long v : values) {
            list.appendTag(new NBTTagLong(v.longValue()));
        }
        return list;
    }
}
