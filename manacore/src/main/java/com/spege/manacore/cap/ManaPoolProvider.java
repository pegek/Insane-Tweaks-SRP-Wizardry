package com.spege.manacore.cap;

import javax.annotation.Nullable;

import com.spege.manacore.ManaCoreMod;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

public class ManaPoolProvider implements ICapabilitySerializable<NBTTagCompound> {

    public static final ResourceLocation KEY = new ResourceLocation(ManaCoreMod.MODID, "mana_pool");

    private final IManaPool instance = new ManaPool();

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == ManaCapabilities.MANA_POOL;
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == ManaCapabilities.MANA_POOL) {
            return ManaCapabilities.MANA_POOL.cast(this.instance);
        }
        return null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return (NBTTagCompound) ManaCapabilities.MANA_POOL.getStorage()
                .writeNBT(ManaCapabilities.MANA_POOL, this.instance, null);
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        ManaCapabilities.MANA_POOL.getStorage()
                .readNBT(ManaCapabilities.MANA_POOL, this.instance, null, nbt);
    }

    public IManaPool getInstance() {
        return this.instance;
    }
}
