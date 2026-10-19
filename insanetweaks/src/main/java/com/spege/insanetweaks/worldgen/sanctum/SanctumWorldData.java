package com.spege.insanetweaks.worldgen.sanctum;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Per-dimension record of which sanctum starts were judged valid (integrity rule) and which not. */
public class SanctumWorldData extends WorldSavedData {

    public static final String NAME = "insanetweaks_sanctums";
    private static final int TAG_LONG = 4;

    private final Map<Long, Boolean> decisions = new HashMap<Long, Boolean>();

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

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.decisions.clear();
        read(nbt.getTagList("Valid", TAG_LONG), Boolean.TRUE);
        read(nbt.getTagList("Invalid", TAG_LONG), Boolean.FALSE);
    }

    private void read(NBTTagList list, Boolean value) {
        for (int i = 0; i < list.tagCount(); i++) {
            this.decisions.put(Long.valueOf(((NBTTagLong) list.get(i)).getLong()), value);
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
        return nbt;
    }
}
