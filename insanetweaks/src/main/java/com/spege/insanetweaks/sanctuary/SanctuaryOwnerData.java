package com.spege.insanetweaks.sanctuary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * GLOBAL (cross-dimension) registry of who owns which ritual Sanctuary, used to enforce the
 * per-player limit. Stored on the world's global {@code getMapStorage()} (shared across every
 * dimension, saved in the save root) so a player's sanctuaries can be counted world-wide.
 *
 * <p>Only ritual Nexuses register here (they get an owner on placement); the Creative Sanctuary
 * never sets an owner, so it never counts against the limit.
 */
public class SanctuaryOwnerData extends WorldSavedData {

    private static final String NAME = "insanetweaks_sanctuary_owners";

    /** owner UUID -> list of {dim, x, y, z}. */
    private final Map<UUID, List<int[]>> owned = new HashMap<UUID, List<int[]>>();

    public SanctuaryOwnerData() { super(NAME); }
    public SanctuaryOwnerData(String name) { super(name); }

    public static SanctuaryOwnerData get(World world) {
        MapStorage storage = world.getMapStorage(); // GLOBAL storage, shared across dimensions
        SanctuaryOwnerData data = (SanctuaryOwnerData) storage.getOrLoadData(SanctuaryOwnerData.class, NAME);
        if (data == null) {
            data = new SanctuaryOwnerData();
            storage.setData(NAME, data);
        }
        return data;
    }

    /** How many sanctuaries this owner has: world-wide when everyDimension, else only in {@code dim}. */
    public int count(UUID owner, int dim, boolean everyDimension) {
        List<int[]> list = owned.get(owner);
        if (list == null) {
            return 0;
        }
        if (everyDimension) {
            return list.size();
        }
        int n = 0;
        for (int[] e : list) {
            if (e[0] == dim) { n++; }
        }
        return n;
    }

    /** Register a sanctuary for an owner. Idempotent: a duplicate (dim,pos) is not added twice. */
    public void add(UUID owner, int dim, BlockPos pos) {
        if (owner == null) {
            return;
        }
        List<int[]> list = owned.get(owner);
        if (list == null) {
            list = new ArrayList<int[]>();
            owned.put(owner, list);
        }
        for (int[] e : list) {
            if (e[0] == dim && e[1] == pos.getX() && e[2] == pos.getY() && e[3] == pos.getZ()) {
                return; // already registered
            }
        }
        list.add(new int[] { dim, pos.getX(), pos.getY(), pos.getZ() });
        markDirty();
    }

    /** Remove a sanctuary registration for an owner (on break). */
    public void remove(UUID owner, int dim, BlockPos pos) {
        if (owner == null) {
            return;
        }
        List<int[]> list = owned.get(owner);
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            int[] e = list.get(i);
            if (e[0] == dim && e[1] == pos.getX() && e[2] == pos.getY() && e[3] == pos.getZ()) {
                list.remove(i);
                if (list.isEmpty()) { owned.remove(owner); }
                markDirty();
                return;
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        owned.clear();
        NBTTagList list = c.getTagList("owners", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            if (!t.hasUniqueId("id")) {
                continue;
            }
            UUID id = t.getUniqueId("id");
            List<int[]> entries = owned.get(id);
            if (entries == null) {
                entries = new ArrayList<int[]>();
                owned.put(id, entries);
            }
            entries.add(new int[] { t.getInteger("dim"), t.getInteger("x"), t.getInteger("y"), t.getInteger("z") });
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, List<int[]>> en : owned.entrySet()) {
            for (int[] e : en.getValue()) {
                NBTTagCompound t = new NBTTagCompound();
                t.setUniqueId("id", en.getKey());
                t.setInteger("dim", e[0]);
                t.setInteger("x", e[1]);
                t.setInteger("y", e[2]);
                t.setInteger("z", e[3]);
                list.appendTag(t);
            }
        }
        c.setTag("owners", list);
        return c;
    }
}
