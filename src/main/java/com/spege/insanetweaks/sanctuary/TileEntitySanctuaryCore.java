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

    private int pyramidTickCounter;

    private boolean isPyramidBlock(net.minecraft.block.state.IBlockState state) {
        net.minecraft.util.ResourceLocation rn = state.getBlock().getRegistryName();
        if (rn == null) { return false; }
        String s = rn.toString();
        for (String allowed : com.spege.insanetweaks.config.ModConfig.sanctuary.pyramidBlocks) {
            if (allowed.equalsIgnoreCase(s)) { return true; }
        }
        return false;
    }

    /** Beacon-style: count complete pyramid layers directly below the core. Returns 0..4.
     *  Layer L is the (2L+1)x(2L+1) square of blocks at y = coreY - L. Layers must be
     *  contiguous from the top: the first incomplete layer stops the count. */
    private int scanPyramidTier() {
        int tiers = 0;
        for (int layer = 1; layer <= 4; layer++) {
            int y = pos.getY() - layer;
            boolean complete = true;
            for (int dx = -layer; dx <= layer && complete; dx++) {
                for (int dz = -layer; dz <= layer; dz++) {
                    net.minecraft.util.math.BlockPos p =
                            new net.minecraft.util.math.BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                    if (!isPyramidBlock(world.getBlockState(p))) {
                        complete = false;
                        break;
                    }
                }
            }
            if (complete) { tiers = layer; } else { break; }
        }
        return tiers;
    }

    private int countUpgradeRadiusItems() {
        int n = 0;
        for (int slot = 1; slot <= UPGRADE_SLOTS; slot++) {
            if (!getInventory().getStackInSlot(slot).isEmpty()) { n += getInventory().getStackInSlot(slot).getCount(); }
        }
        return n;
    }

    private void revalidateAndSync() {
        int newTier = scanPyramidTier();
        int radius = 0;
        if (newTier >= 1) {
            int[] radii = com.spege.insanetweaks.config.ModConfig.sanctuary.tierRadii;
            int base = radii[Math.min(newTier, radii.length) - 1];
            radius = base + countUpgradeRadiusItems() * com.spege.insanetweaks.config.ModConfig.sanctuary.upgradeRadiusBonus;
        }
        setTier(newTier);
        setEffectiveRadius(radius);
        SanctuaryWorldData.get(world).setRegion(pos, radius); // radius<=0 removes
        markDirty();
    }

    public void onRemovedFromWorld() {
        if (world != null && !world.isRemote) {
            SanctuaryWorldData.get(world).removeRegion(pos);
        }
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) { return; }
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary) { return; }
        if (com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper.isDimensionBlacklisted(world)) {
            if (getTier() != 0) { setTier(0); setEffectiveRadius(0); SanctuaryWorldData.get(world).removeRegion(pos); }
            return;
        }
        if (!isInitialized()) {
            setCleanseEnabled(com.spege.insanetweaks.config.ModConfig.sanctuary.cleanseEnabledByDefault);
            markInitialized();
        }
        if (++pyramidTickCounter >= com.spege.insanetweaks.config.ModConfig.sanctuary.pyramidRevalidateInterval) {
            pyramidTickCounter = 0;
            revalidateAndSync();
        }
        // Task 9 inserts upkeep + cleanse here.
    }

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
