package com.spege.insanetweaks.client.gui;

import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.entities.inventory.SentinelLootInventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Container linking the Sentinel's 20-slot loot inventory (5x4 grid) with the player's
 * 36-slot inventory. Slot coordinates match GuiSentinelControl's background layout.
 * The 5-column grid is aligned to texture columns 2-6 of generic_54.png (x = 44).
 */
@SuppressWarnings("null")
public class SentinelLootContainer extends Container {

    private static final int SENTINEL_SLOTS = 20;
    private static final int COLUMNS = 5;
    private static final int PLAYER_INV_START = SENTINEL_SLOTS;
    /** 4 loot rows -> vanilla chest offset formulas for the player inventory below. */
    private static final int PLAYER_INV_Y = 4 * 18 + 31;  // 103
    private static final int HOTBAR_Y = 4 * 18 + 89;      // 161

    private final int sentinelEntityId;

    public SentinelLootContainer(EntityPlayer player, SentinelLootInventory inv, int entityId) {
        this.sentinelEntityId = entityId;

        // Sentinel loot slots — 4 rows of 5, centered on the chest texture (columns 2-6).
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                this.addSlotToContainer(new Slot(inv, row * COLUMNS + col,
                        44 + col * 18, 18 + row * 18));
            }
        }

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(new Slot(player.inventory, col + row * 9 + 9,
                        8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(player.inventory, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        net.minecraft.entity.Entity entity = player.world.getEntityByID(sentinelEntityId);
        if (!(entity instanceof EntitySentinel)) return false;
        EntitySentinel sentinel = (EntitySentinel) entity;
        return sentinel.canPlayerCommand(player) && player.getDistanceSq(sentinel) < 64.0D;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            itemstack = stack.copy();

            // Shift-click from sentinel loot to player inv
            if (index < SENTINEL_SLOTS) {
                if (!this.mergeItemStack(stack, PLAYER_INV_START, this.inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Shift-click from player inv to sentinel loot
                if (!this.mergeItemStack(stack, 0, SENTINEL_SLOTS, false)) {
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
