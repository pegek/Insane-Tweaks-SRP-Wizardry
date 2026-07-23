package com.spege.insanetweaks.entities;

import com.spege.insanetweaks.entities.inventory.ThrallInventory;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * Stateless torch-supply pipeline for Thralls in MINESHAFT mode:
 *   1. Count torches in inventory.
 *   2. If short, grab from chests near home (radius 15).
 *   3. If still short, craft from logs + coal in inventory (1 log + 1 coal = 8 torches).
 *
 * Material identification is delegated to ThrallMaterialHelper (OreDict-based).
 */
@SuppressWarnings("null")
public final class ThrallTorchSupply {
    private ThrallTorchSupply() {}

    /**
     * Tries to ensure the thrall has at least {@code targetCount} torches.
     * @return true if at least 1 torch is available after resupply.
     */
    public static boolean tryResupply(EntityThrallMinion thrall, int targetCount) {
        ThrallInventory inv = thrall.getThrallInventory();
        Item torchItem = Item.getItemFromBlock(Blocks.TORCH);

        int current = countTorches(inv);
        if (current >= targetCount) return true;

        int needed = targetCount - current;
        BlockPos home = thrall.getHomePoint();
        if (home == null) return current > 0;

        // Phase 1: Grab existing torches from chests near HOME
        needed = ThrallChestHelper.grabItemFromChests(thrall, home, torchItem, needed);
        if (needed <= 0) return true;

        // Phase 2: Craft from logs + coal
        needed = craftFromMaterials(thrall, needed);
        return needed <= 0 || countTorches(inv) > 0;
    }

    public static int countTorches(IInventory inv) {
        Item torchItem = Item.getItemFromBlock(Blocks.TORCH);
        int count = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == torchItem) count += s.getCount();
        }
        return count;
    }

    /**
     * Crafts torches from logs + coal in thrall inventory and chests near HOME.
     * Rate: 1 log + 1 coal = 8 torches (simplified vanilla approximation).
     */
    private static int craftFromMaterials(EntityThrallMinion thrall, int needed) {
        ThrallInventory inv = thrall.getThrallInventory();

        BlockPos home = thrall.getHomePoint();
        if (home != null) {
            grabLogsAndCoalFromChests(thrall, home);
        }

        Item torchItem = Item.getItemFromBlock(Blocks.TORCH);
        while (needed > 0) {
            int logSlot = findLogSlot(inv);
            int coalSlot = findCoalSlot(inv);
            if (logSlot < 0 || coalSlot < 0) break;

            inv.getStackInSlot(logSlot).shrink(1);
            if (inv.getStackInSlot(logSlot).isEmpty())
                inv.setInventorySlotContents(logSlot, ItemStack.EMPTY);
            inv.getStackInSlot(coalSlot).shrink(1);
            if (inv.getStackInSlot(coalSlot).isEmpty())
                inv.setInventorySlotContents(coalSlot, ItemStack.EMPTY);

            inv.addItemStackToInventory(new ItemStack(torchItem, 8));
            needed -= 8;
        }
        return Math.max(0, needed);
    }

    /** Pulls up to 8 logs and 8 coal from chests within 15 blocks of the given center into thrall inventory. */
    private static void grabLogsAndCoalFromChests(EntityThrallMinion thrall, BlockPos center) {
        ThrallInventory inv = thrall.getThrallInventory();
        int logsNeeded = 8;
        int coalNeeded = 8;
        for (IInventory chest : ThrallChestHelper.findNearbyInventories(thrall.world, center, 15, 4)) {
            if (logsNeeded <= 0 && coalNeeded <= 0) break;
            for (int i = 0; i < chest.getSizeInventory(); i++) {
                ItemStack chestStack = chest.getStackInSlot(i);
                if (chestStack.isEmpty()) continue;
                if (logsNeeded > 0 && ThrallMaterialHelper.isLogItem(chestStack)) {
                    int take = Math.min(logsNeeded, chestStack.getCount());
                    inv.addItemStackToInventory(chestStack.splitStack(take));
                    chest.markDirty();
                    logsNeeded -= take;
                } else if (coalNeeded > 0 && ThrallMaterialHelper.isCoalItem(chestStack)) {
                    int take = Math.min(coalNeeded, chestStack.getCount());
                    inv.addItemStackToInventory(chestStack.splitStack(take));
                    chest.markDirty();
                    coalNeeded -= take;
                }
            }
        }
    }

    private static int findLogSlot(IInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && ThrallMaterialHelper.isLogItem(s)) return i;
        }
        return -1;
    }

    private static int findCoalSlot(IInventory inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && ThrallMaterialHelper.isCoalItem(s)) return i;
        }
        return -1;
    }
}
