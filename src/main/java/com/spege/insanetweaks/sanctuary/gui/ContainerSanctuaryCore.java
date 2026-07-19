package com.spege.insanetweaks.sanctuary.gui;

import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerSanctuaryCore extends Container {

    private final TileEntitySanctuaryCore te;

    public ContainerSanctuaryCore(InventoryPlayer playerInv, TileEntitySanctuaryCore te) {
        this.te = te;
        addSlotToContainer(new SlotItemHandler(te.getInventory(), TileEntitySanctuaryCore.SLOT_FUEL, 26, 35));
        for (int i = 0; i < TileEntitySanctuaryCore.UPGRADE_SLOTS; i++) {
            addSlotToContainer(new SlotItemHandler(te.getInventory(), 1 + i, 80 + i * 18, 35));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    public TileEntitySanctuaryCore getTe() { return te; }

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
