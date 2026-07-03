package com.spege.insanetweaks.entities.inventory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * Single-chest (27-slot) inventory for EntityThrallMinion.
 * Uses ItemStack.EMPTY (never null) as the empty-slot sentinel.
 */
@SuppressWarnings("null")
public class ThrallInventory implements IInventory {

    private static final int SIZE = 27;

    private final ItemStack[] stacks = new ItemStack[SIZE];

    public ThrallInventory() {
        for (int i = 0; i < SIZE; i++) {
            stacks[i] = ItemStack.EMPTY;
        }
    }

    // -------------------------------------------------------------------------
    // IInventory
    // -------------------------------------------------------------------------

    @Override
    public int getSizeInventory() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : stacks) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        if (index < 0 || index >= SIZE) return ItemStack.EMPTY;
        return stacks[index];
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        if (index < 0 || index >= SIZE || stacks[index].isEmpty()) return ItemStack.EMPTY;
        ItemStack result = stacks[index].splitStack(count);
        if (stacks[index].isEmpty()) stacks[index] = ItemStack.EMPTY;
        markDirty();
        return result;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index < 0 || index >= SIZE) return ItemStack.EMPTY;
        ItemStack old = stacks[index];
        stacks[index] = ItemStack.EMPTY;
        markDirty();
        return old;
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index < 0 || index >= SIZE) return;
        stacks[index] = stack == null ? ItemStack.EMPTY : stack;
        if (!stacks[index].isEmpty() && stacks[index].getCount() > getInventoryStackLimit()) {
            stacks[index].setCount(getInventoryStackLimit());
        }
        markDirty();
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        // Thrall entity handles persistence via writeToNBT
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return true;
    }

    @Override
    public void openInventory(EntityPlayer player) {}

    @Override
    public void closeInventory(EntityPlayer player) {}

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return true;
    }

    @Override
    public int getField(int id) { return 0; }

    @Override
    public void setField(int id, int value) {}

    @Override
    public int getFieldCount() { return 0; }

    @Override
    public void clear() {
        for (int i = 0; i < SIZE; i++) stacks[i] = ItemStack.EMPTY;
    }

    @Override
    public String getName() {
        return "Thrall Inventory";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentTranslation(getName());
    }

    // -------------------------------------------------------------------------
    // Custom helpers
    // -------------------------------------------------------------------------

    /** Returns true if at least one slot is occupied. */
    public boolean containsItems() {
        return !isEmpty();
    }

    /** Counts occupied slots. Used to measure deposit progress without double-iteration. */
    public int nonEmptySlotCount() {
        int count = 0;
        for (ItemStack s : stacks) if (!s.isEmpty()) count++;
        return count;
    }

    /**
     * Returns true when every slot is occupied. Used as the auto-return trigger:
     * once all 27 slots hold something, any new item type cannot be slotted and would
     * either be lost (mining drops on the ground) or stall the porter cycle. Existing
     * partial stacks of matching items can still be merged into via addItemStackToInventory,
     * but the thrall stops generating new work and heads home to deposit.
     */
    public boolean isFull() {
        for (ItemStack s : stacks) {
            if (s.isEmpty()) return false;
        }
        return true;
    }

    /**
     * Tries to add the given stack to inventory. Merges with existing stacks first,
     * then uses empty slots. Modifies the passed stack's count.
     * @return true if at least some was added.
     */
    public boolean addItemStackToInventory(ItemStack toAdd) {
        if (toAdd == null || toAdd.isEmpty()) return false;

        int originalCount = toAdd.getCount();

        // Pass 1: merge with partial stacks of same type
        for (int i = 0; i < SIZE; i++) {
            ItemStack slot = stacks[i];
            if (!slot.isEmpty() && ItemStack.areItemsEqual(slot, toAdd) && ItemStack.areItemStackTagsEqual(slot, toAdd)) {
                int space = slot.getMaxStackSize() - slot.getCount();
                if (space <= 0) continue;
                int take = Math.min(space, toAdd.getCount());
                slot.grow(take);
                toAdd.shrink(take);
                if (toAdd.isEmpty()) {
                    markDirty();
                    return true;
                }
            }
        }

        // Pass 2: fill empty slots
        for (int i = 0; i < SIZE; i++) {
            if (stacks[i].isEmpty()) {
                stacks[i] = toAdd.copy();
                toAdd.setCount(0);
                markDirty();
                return true;
            }
        }

        markDirty();
        return toAdd.getCount() < originalCount; // true if we added at least some
    }

    /**
     * Drops all inventory contents into the world near the given position.
     * Clears the inventory afterwards.
     */
    public void dropAllItems(net.minecraft.world.World world, double x, double y, double z) {
        for (int i = 0; i < SIZE; i++) {
            if (!stacks[i].isEmpty()) {
                net.minecraft.entity.item.EntityItem ent = new net.minecraft.entity.item.EntityItem(
                        world, x, y, z, stacks[i].copy());
                ent.setPickupDelay(20);
                world.spawnEntity(ent);
                stacks[i] = ItemStack.EMPTY;
            }
        }
        markDirty();
    }

    /**
     * Transfers all items to a target IInventory (e.g. chest).
     * Preferred over dropping when a valid target exists.
     * @param target The target inventory.
     * @param ignoreTorches If true, torches will be kept in the thrall's inventory.
     * @return true if all non-ignored items were deposited.
     */
    public boolean putAllItemsToInventory(IInventory target, boolean ignoreTorches) {
        if (target == null) return false;
        boolean allDone = true;
        net.minecraft.item.Item torchItem = net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.TORCH);
        
        for (int i = 0; i < SIZE; i++) {
            if (stacks[i].isEmpty()) continue;
            
            // Skip torches if requested
            if (ignoreTorches && stacks[i].getItem() == torchItem) {
                continue;
            }
            
            if (!addStackToInventory(target, stacks[i])) {
                // Handle double chest
                if (target instanceof net.minecraft.tileentity.TileEntityChest) {
                    net.minecraft.tileentity.TileEntityChest chest = (net.minecraft.tileentity.TileEntityChest) target;
                    boolean deposited = false;
                    if (chest.adjacentChestXNeg != null) deposited = addStackToInventory(chest.adjacentChestXNeg, stacks[i]);
                    if (!deposited && chest.adjacentChestXPos != null) deposited = addStackToInventory(chest.adjacentChestXPos, stacks[i]);
                    if (!deposited && chest.adjacentChestZNeg != null) deposited = addStackToInventory(chest.adjacentChestZNeg, stacks[i]);
                    if (!deposited && chest.adjacentChestZPos != null) deposited = addStackToInventory(chest.adjacentChestZPos, stacks[i]);
                    if (deposited) { stacks[i] = ItemStack.EMPTY; continue; }
                }
                allDone = false;
            } else {
                stacks[i] = ItemStack.EMPTY;
            }
        }
        markDirty();
        return allDone;
    }

    private boolean addStackToInventory(IInventory inv, ItemStack item) {
        int originalCount = item.getCount();
        // Merge with partials first
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (!slot.isEmpty() && ItemStack.areItemsEqual(slot, item) && ItemStack.areItemStackTagsEqual(slot, item)) {
                int space = slot.getMaxStackSize() - slot.getCount();
                if (space <= 0) continue;
                int take = Math.min(space, item.getCount());
                slot.grow(take);
                item.shrink(take);
                inv.markDirty();
                if (item.isEmpty()) return true;
            }
        }
        // Find empty slot
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (inv.getStackInSlot(i).isEmpty() && inv.isItemValidForSlot(i, item)) {
                inv.setInventorySlotContents(i, item.copy());
                item.setCount(0);
                inv.markDirty();
                return true;
            }
        }
        return item.getCount() < originalCount;
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    public NBTTagList writeToNBT() {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SIZE; i++) {
            if (!stacks[i].isEmpty()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("Slot", (byte) i);
                stacks[i].writeToNBT(tag);
                list.appendTag(tag);
            }
        }
        return list;
    }

    public void readFromNBT(NBTTagList list) {
        for (int i = 0; i < SIZE; i++) stacks[i] = ItemStack.EMPTY;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getByte("Slot") & 0xFF;
            if (slot >= 0 && slot < SIZE) {
                stacks[slot] = new ItemStack(tag);
            }
        }
    }
}
