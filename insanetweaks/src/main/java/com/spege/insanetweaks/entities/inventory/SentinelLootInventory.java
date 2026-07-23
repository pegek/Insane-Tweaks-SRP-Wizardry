package com.spege.insanetweaks.entities.inventory;

import com.spege.insanetweaks.entities.EntitySentinel;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nonnull;

/**
 * IInventory view over the Sentinel's 20-slot loot list, used by the control GUI's
 * container so the player gets vanilla slot sync and shift-click withdrawal
 * (F4, spec 2026-07-10 — replaces the old read-only NBT-snapshot loot screen).
 */
@SuppressWarnings("null")
public class SentinelLootInventory implements IInventory {

    private final EntitySentinel sentinel;

    public SentinelLootInventory(EntitySentinel sentinel) {
        this.sentinel = sentinel;
    }

    @Override
    public int getSizeInventory() {
        return sentinel.getLootInventoryList().size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : sentinel.getLootInventoryList()) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    @Nonnull
    public ItemStack getStackInSlot(int index) {
        return sentinel.getLootInventoryList().get(index);
    }

    @Override
    @Nonnull
    public ItemStack decrStackSize(int index, int count) {
        return ItemStackHelper.getAndSplit(sentinel.getLootInventoryList(), index, count);
    }

    @Override
    @Nonnull
    public ItemStack removeStackFromSlot(int index) {
        return ItemStackHelper.getAndRemove(sentinel.getLootInventoryList(), index);
    }

    @Override
    public void setInventorySlotContents(int index, @Nonnull ItemStack stack) {
        sentinel.getLootInventoryList().set(index, stack);
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        // Entity NBT save serializes the list directly; nothing to flush here.
    }

    @Override
    public boolean isUsableByPlayer(@Nonnull EntityPlayer player) {
        return !sentinel.isDead && sentinel.canPlayerCommand(player)
                && player.getDistanceSq(sentinel) < 64.0D;
    }

    @Override
    public void openInventory(@Nonnull EntityPlayer player) {
    }

    @Override
    public void closeInventory(@Nonnull EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(int index, @Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {
    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {
        for (int i = 0; i < getSizeInventory(); i++) {
            sentinel.getLootInventoryList().set(i, ItemStack.EMPTY);
        }
    }

    @Override
    @Nonnull
    public String getName() {
        return "container.insanetweaks.sentinel_loot";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    @Nonnull
    public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }
}
