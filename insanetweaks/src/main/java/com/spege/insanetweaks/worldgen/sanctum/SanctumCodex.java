package com.spege.insanetweaks.worldgen.sanctum;

import com.spege.insanetweaks.integration.patchouli.CodexBook;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Puts the Insane Codex on the ritual chamber's bookshelf.
 *
 * <p>Called by the sanctum builder once the {@code ebwizardry:oak_bookshelf} at the layout's codex
 * position exists. Kept out of the builder so the builder needs one line, and so a sanctum without
 * Patchouli simply gets an ordinary shelf.
 *
 * <p>EB's shelf only accepts items on its own "book" list; {@code InsaneTweaksMod.init} adds
 * Patchouli's guide book to it. Without that registration {@code isItemValidForSlot} refuses the Codex
 * and this method leaves the shelf as it is.
 */
public final class SanctumCodex {

    private SanctumCodex() {
    }

    /**
     * One Codex into the first empty slot of the inventory at {@code pos}. Does nothing without
     * Patchouli, when there is no inventory there, when no slot takes it, or when a Codex is already
     * on the shelf (so a re-run never stacks up copies).
     */
    public static void fill(World world, BlockPos pos) {
        ItemStack codex = CodexBook.stack();
        if (codex.isEmpty()) {
            return;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof IInventory)) {
            return;
        }
        IInventory inventory = (IInventory) te;
        int free = -1;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack held = inventory.getStackInSlot(i);
            if (CodexBook.isCodex(held)) {
                return;
            }
            if (free < 0 && held.isEmpty() && inventory.isItemValidForSlot(i, codex)) {
                free = i;
            }
        }
        if (free >= 0) {
            inventory.setInventorySlotContents(free, codex);
            te.markDirty();
        }
    }
}
