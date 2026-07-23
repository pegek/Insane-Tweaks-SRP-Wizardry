package com.spege.insanetweaks.client.gui;

import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.inventory.ThrallInventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Container linking the thrall's 27-slot inventory with the player's 36-slot inventory.
 * Layout matches a standard single chest: 3 rows × 9 columns.
 * Used by GuiThrallInventory.
 */
@SuppressWarnings("null")
public class ThrallContainer extends Container {

    private static final int THRALL_SLOTS = 27;
    private static final int PLAYER_INV_START = THRALL_SLOTS;

    private final int thrallEntityId;

    public ThrallContainer(EntityPlayer player, ThrallInventory inv, int entityId) {
        this.thrallEntityId = entityId;

        // Thrall inventory slots — 3 rows of 9 (standard chest layout)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(new Slot(inv, row * 9 + col, 8 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory (3 rows) — vanilla chest offset formula
        int playerInvY = 3 * 18 + 31; // 85
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9,
                        8 + col * 18, playerInvY + row * 18));
            }
        }

        // Player hotbar
        int hotbarY = 3 * 18 + 89; // 143
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, hotbarY));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        // Allow access if the player is close to the thrall
        net.minecraft.entity.Entity entity = player.world.getEntityByID(thrallEntityId);
        if (!(entity instanceof EntityThrallMinion)) return false;
        EntityThrallMinion thrall = (EntityThrallMinion) entity;
        return thrall.canPlayerCommand(player) && player.getDistanceSq(thrall) < 64.0D;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            itemstack = stack.copy();

            // Shift-click from thrall inv to player inv
            if (index < THRALL_SLOTS) {
                if (!this.mergeItemStack(stack, PLAYER_INV_START, this.inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Shift-click from player inv to thrall inv
                if (!this.mergeItemStack(stack, 0, THRALL_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }

        return itemstack;
    }
}
