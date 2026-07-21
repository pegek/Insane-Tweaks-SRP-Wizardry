package com.spege.insanetweaks.sanctuary.gui;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.SanctuaryCostCategory;
import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerSanctuaryCore extends Container {

    private final TileEntitySanctuaryCore te;
    private int lastFuel = -1;      // server-side change tracking
    private int clientFuel = 0;     // client-side mirror (window property)

    public ContainerSanctuaryCore(InventoryPlayer playerInv, TileEntitySanctuaryCore te) {
        this.te = te;
        addSlotToContainer(new SlotItemHandler(te.getInventory(), TileEntitySanctuaryCore.SLOT_FUEL, 26, 104));
        for (int i = 0; i < TileEntitySanctuaryCore.UPGRADE_SLOTS; i++) {
            final int upgradeIndex = i; // 0..3 -> U1..U4, each slot bound to one item
            addSlotToContainer(new SlotItemHandler(te.getInventory(), 1 + i, 80 + i * 18, 104) {
                @Override
                public boolean isItemValid(ItemStack stack) {
                    return isValidUpgrade(upgradeIndex, stack);
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 128 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 186));
        }
    }

    public TileEntitySanctuaryCore getTe() { return te; }

    /** Whether the upgrade slot (0..3 -> U1..U4) accepts the stack. Empty is always allowed. */
    static boolean isValidUpgrade(int index, ItemStack stack) {
        if (stack == null || stack.isEmpty()) { return true; }
        ResourceLocation rn = stack.getItem().getRegistryName();
        if (rn == null) { return false; }
        SanctuaryCostCategory c = ModConfig.sanctuaryCost;
        switch (index) {
            case 0: return rn.toString().equals(c.upgradeItemU1);
            case 1: return rn.toString().equals(c.upgradeItemU2)
                    && (c.u2Meta < 0 || stack.getMetadata() == c.u2Meta);
            case 2: return rn.toString().equals(c.upgradeItemU3);
            case 3: return rn.toString().equals(c.upgradeItemU4);
            default: return true;
        }
    }

    /** The hint stack a GUI ghosts in empty upgrade slot {@code index} (0..3), or EMPTY. */
    public static ItemStack upgradeHintStack(int index) {
        SanctuaryCostCategory c = ModConfig.sanctuaryCost;
        switch (index) {
            case 0: return makeStack(c.upgradeItemU1, 0, 1);
            case 1: return makeStack(c.upgradeItemU2, Math.max(0, c.u2Meta), c.u2Count);
            case 2: return makeStack(c.upgradeItemU3, 0, 1);
            case 3: return makeStack(c.upgradeItemU4, 0, 1);
            default: return ItemStack.EMPTY;
        }
    }

    private static ItemStack makeStack(String regName, int meta, int count) {
        net.minecraft.item.Item item = net.minecraft.item.Item.getByNameOrId(regName);
        if (item == null) { return ItemStack.EMPTY; }
        return new ItemStack(item, count, meta);
    }

    /** Client-side fuel value (0 until first window-property arrives). */
    public int getClientFuel() { return clientFuel; }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        listener.sendWindowProperty(this, 0, clampShort(te.getFuelStored()));
        lastFuel = te.getFuelStored();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int fuel = te.getFuelStored();
        if (fuel != lastFuel) {
            for (IContainerListener l : this.listeners) {
                l.sendWindowProperty(this, 0, clampShort(fuel));
            }
            lastFuel = fuel;
        }
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void updateProgressBar(int id, int data) {
        if (id == 0) { clientFuel = data; }
    }

    private static int clampShort(int v) { return v < 0 ? 0 : (v > 32767 ? 32767 : v); }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return te.getWorld().getTileEntity(te.getPos()) == te
                && player.getDistanceSq(te.getPos()) <= 64.0D;
    }

    @Override
    public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return net.minecraft.item.ItemStack.EMPTY; // minimal: no shift-click transfer in v1
    }
}
