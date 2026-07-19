package com.spege.insanetweaks.sanctuary;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Per-world registry of active sanctuary regions. Single source of truth queried by the SRP vetoes. */
public class SanctuaryWorldData extends WorldSavedData {

    private static final String NAME = "insanetweaks_sanctuaries";

    private final List<int[]> regions = new ArrayList<int[]>(); // each: {x, y, z, radius}

    public SanctuaryWorldData() { super(NAME); }
    public SanctuaryWorldData(String name) { super(name); }

    public static SanctuaryWorldData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        SanctuaryWorldData data = (SanctuaryWorldData) storage.getOrLoadData(SanctuaryWorldData.class, NAME);
        if (data == null) {
            data = new SanctuaryWorldData();
            storage.setData(NAME, data);
        }
        return data;
    }

    /** Insert-or-update the region anchored at pos. radius<=0 removes it. */
    public void setRegion(BlockPos pos, int radius) {
        for (int i = 0; i < regions.size(); i++) {
            int[] r = regions.get(i);
            if (r[0] == pos.getX() && r[1] == pos.getY() && r[2] == pos.getZ()) {
                if (radius <= 0) { regions.remove(i); } else { r[3] = radius; }
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

    @Override
    public void readFromNBT(NBTTagCompound c) {
        regions.clear();
        NBTTagList list = c.getTagList("regions", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            regions.add(new int[] { t.getInteger("x"), t.getInteger("y"), t.getInteger("z"), t.getInteger("r") });
        }
    }

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
