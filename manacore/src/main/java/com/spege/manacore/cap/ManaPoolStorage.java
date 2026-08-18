package com.spege.manacore.cap;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

public class ManaPoolStorage implements Capability.IStorage<IManaPool> {

    static final String KEY_CURRENT = "current";
    static final String KEY_PROGRESSION = "progression";

    @Override
    public NBTBase writeNBT(Capability<IManaPool> capability, IManaPool instance, EnumFacing side) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble(KEY_CURRENT, instance.getCurrent());
        tag.setDouble(KEY_PROGRESSION, instance.getProgressionBonus());
        return tag;
    }

    @Override
    public void readNBT(Capability<IManaPool> capability, IManaPool instance, EnumFacing side, NBTBase nbt) {
        if (!(nbt instanceof NBTTagCompound)) {
            return;
        }
        NBTTagCompound tag = (NBTTagCompound) nbt;
        instance.setCurrent(tag.getDouble(KEY_CURRENT));
        instance.setProgressionBonus(tag.getDouble(KEY_PROGRESSION));
    }
}
