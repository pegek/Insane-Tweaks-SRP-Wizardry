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

    /** Records a sanctum once; later calls for the same id are ignored. */
    public void register(long id, Collection<Long> anchors, Collection<Long> containers) {
        Long key = Long.valueOf(id);
        if (this.records.containsKey(key)) {
            return;
        }
        Record r = new Record();
        r.anchors.addAll(anchors);
        r.containers.addAll(containers);
        r.cleared = r.anchors.isEmpty();
        this.records.put(key, r);
        markDirty();
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
