package com.spege.insanetweaks.sanctuary;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public class TileEntitySanctuaryCore extends TileEntity implements ITickable {

    public static final int SLOT_FUEL = 0;
    public static final int UPGRADE_SLOTS = 4; // slots 1..4

    private final ItemStackHandler inventory = new ItemStackHandler(1 + UPGRADE_SLOTS) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };

    private int tier;            // 0 = inactive (no/incomplete pyramid)
    private int effectiveRadius; // computed from tier + upgrades
    private boolean cleanseEnabled;
    private boolean cleanseStalled; // true when cleanse wants to run but fuel == 0
    private int fuelStored;      // remaining cleanse-conversions from consumed fuel items
    private boolean initialized; // first-tick default for cleanseEnabled

    public ItemStackHandler getInventory() { return inventory; }
    public int getTier() { return tier; }
    public int getEffectiveRadius() { return effectiveRadius; }
    public boolean isCleanseEnabled() { return cleanseEnabled; }
    public boolean isCleanseStalled() { return cleanseStalled; }
    public void setCleanseEnabled(boolean v) { this.cleanseEnabled = v; markDirty(); }

    // setters used by tick logic (Task 5/9)
    void setTier(int t) { this.tier = t; }
    void setEffectiveRadius(int r) { this.effectiveRadius = r; }
    void setCleanseStalled(boolean v) { this.cleanseStalled = v; }
    int getFuelStored() { return fuelStored; }
    void setFuelStored(int v) { this.fuelStored = v; }
    boolean isInitialized() { return initialized; }
    void markInitialized() { this.initialized = true; }

    @Override public void update() { /* filled in Task 5 (scan+sync) and Task 9 (upkeep+cleanse) */ }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        inventory.deserializeNBT(c.getCompoundTag("inv"));
        tier = c.getInteger("tier");
        effectiveRadius = c.getInteger("radius");
        cleanseEnabled = c.getBoolean("cleanse");
        cleanseStalled = c.getBoolean("stalled");
        fuelStored = c.getInteger("fuel");
        initialized = c.getBoolean("init");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        c.setTag("inv", inventory.serializeNBT());
        c.setInteger("tier", tier);
        c.setInteger("radius", effectiveRadius);
        c.setBoolean("cleanse", cleanseEnabled);
        c.setBoolean("stalled", cleanseStalled);
        c.setInteger("fuel", fuelStored);
        c.setBoolean("init", initialized);
        return c;
    }

    @Override
    public boolean hasCapability(Capability<?> cap, net.minecraft.util.EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> cap, net.minecraft.util.EnumFacing side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) inventory;
        }
        return super.getCapability(cap, side);
    }
}
