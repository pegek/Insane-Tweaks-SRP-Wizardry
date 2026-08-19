package com.spege.manacore.cap;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

public class ManaPoolStorage implements Capability.IStorage<IManaPool> {

    static final String KEY_CURRENT = "current";
    /** Kept as "progression" (rather than renamed to "castProgression") so the meaning of this
     * existing save key does not change: it always meant "progression from casting". */
    static final String KEY_CAST_PROGRESSION = "progression";
    static final String KEY_ITEM_PROGRESSION = "itemProgression";

    @Override
    public NBTBase writeNBT(Capability<IManaPool> capability, IManaPool instance, EnumFacing side) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble(KEY_CURRENT, instance.getCurrent());
        tag.setDouble(KEY_CAST_PROGRESSION, instance.getCastProgression());
        tag.setDouble(KEY_ITEM_PROGRESSION, instance.getItemProgression());
        return tag;
    }

    @Override
    public void readNBT(Capability<IManaPool> capability, IManaPool instance, EnumFacing side, NBTBase nbt) {
        if (!(nbt instanceof NBTTagCompound)) {
            return;
        }
        NBTTagCompound tag = (NBTTagCompound) nbt;
        instance.setCurrent(tag.getDouble(KEY_CURRENT));
        instance.setCastProgression(tag.getDouble(KEY_CAST_PROGRESSION));
        instance.setItemProgression(tag.getDouble(KEY_ITEM_PROGRESSION));
    }
}
