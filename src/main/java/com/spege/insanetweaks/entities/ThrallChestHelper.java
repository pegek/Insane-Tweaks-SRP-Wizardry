package com.spege.insanetweaks.entities;

import com.spege.insanetweaks.entities.inventory.ThrallInventory;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless helpers for Thrall ↔ chest interactions: scanning nearby IInventory tile entities,
 * smart-depositing the Thrall's inventory, and pulling specific items from nearby storage.
 *
 * All scans walk world.loadedTileEntityList once instead of per-block getTileEntity lookups,
 * avoiding O(range^2 * vRange*2) HashMap calls.
 */
@SuppressWarnings("null")
public final class ThrallChestHelper {
    private ThrallChestHelper() {}

    /**
     * Walks world.loadedTileEntityList once to find IInventory TEs within range.
     * Snapshots the list to guard against concurrent mutation during scan.
     */
    public static List<IInventory> findNearbyInventories(World world, BlockPos center, int hRange, int vRange) {
        List<IInventory> result = new ArrayList<>();
        double hRangeSq = (double) hRange * hRange;
        int centerX = center.getX(), centerY = center.getY(), centerZ = center.getZ();
        TileEntity[] snapshot = world.loadedTileEntityList.toArray(new TileEntity[0]);
        for (TileEntity te : snapshot) {
            if (te == null || te.isInvalid() || !(te instanceof IInventory)) continue;
            BlockPos p = te.getPos();
            if (Math.abs(p.getY() - centerY) > vRange) continue;
            double dx = p.getX() - centerX, dz = p.getZ() - centerZ;
            if (dx * dx + dz * dz <= hRangeSq) result.add((IInventory) te);
        }
        return result;
    }

    /** Single-method stack filter for {@link #withdrawFromChests} (avoids guava/j.u.f mixing). */
    public interface StackMatcher {
        boolean matches(ItemStack stack);
    }

    /**
     * Pulls up to {@code max} items matching {@code matcher} from chests near {@code center}
     * into the thrall's bag (spec 2026-07-16 depot-supply trips). Returns the count pulled.
     */
    public static int withdrawFromChests(EntityThrallMinion thrall, BlockPos center,
            int hRange, int vRange, StackMatcher matcher, int max) {
        ThrallInventory inv = thrall.getThrallInventory();
        int pulled = 0;
        List<IInventory> chests = findNearbyInventories(thrall.world, center, hRange, vRange);
        for (IInventory chest : chests) {
            for (int i = 0; i < chest.getSizeInventory() && pulled < max; i++) {
                if (inv.isFull()) return pulled;
                ItemStack s = chest.getStackInSlot(i);
                if (s.isEmpty() || !matcher.matches(s)) continue;

                int take = Math.min(max - pulled, s.getCount());
                ItemStack working = s.copy();
                working.setCount(take);
                int requested = working.getCount();
                inv.addItemStackToInventory(working);
                int actuallyTaken = requested - working.getCount();
                if (actuallyTaken > 0) {
                    s.shrink(actuallyTaken);
                    if (s.isEmpty()) {
                        chest.setInventorySlotContents(i, ItemStack.EMPTY);
                    }
                    chest.markDirty();
                    pulled += actuallyTaken;
                }
            }
        }
        return pulled;
    }

    /**
     * Deposits Thrall inventory into nearby chests (within 30 blocks of center).
     * Prefers chests that already contain matching item types.
     * @return true if Thrall inventory contains only torches (or is empty) afterward.
     */
    public static boolean smartDeposit(EntityThrallMinion thrall, BlockPos center) {
        return smartDeposit(thrall, center, 30, 4, true);
    }

    /**
     * Range/torch-aware variant. Porter passes its configured scan range and {@code keepTorches=false},
     * mining/woodcutting modes use the legacy 30-block default with torches kept.
     */
    public static boolean smartDeposit(EntityThrallMinion thrall, BlockPos center,
                                       int hRange, int vRange, boolean keepTorches) {
        World world = thrall.world;
        ThrallInventory inv = thrall.getThrallInventory();
        List<IInventory> candidates = findNearbyInventories(world, center, hRange, vRange);
        if (candidates.isEmpty()) return false;

        candidates.sort((a, b) -> getMatchScore(b, inv) - getMatchScore(a, inv));

        boolean depositedAny = false;
        for (IInventory chest : candidates) {
            int countBefore = inv.nonEmptySlotCount();
            inv.putAllItemsToInventory(chest, keepTorches);
            if (inv.nonEmptySlotCount() < countBefore) depositedAny = true;
        }

        if (depositedAny) {
            world.playSound(null, thrall.posX, thrall.posY, thrall.posZ,
                    SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.NEUTRAL, 0.5F, 1.0F);
        }

        if (keepTorches) {
            Item torchItem = Item.getItemFromBlock(Blocks.TORCH);
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && s.getItem() != torchItem) return false;
            }
            return true;
        }
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            if (!inv.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    /**
     * Grabs up to {@code needed} of the specified item from chests within 15 blocks.
     * @return remaining needed count.
     */
    public static int grabItemFromChests(EntityThrallMinion thrall, BlockPos center, Item item, int needed) {
        ThrallInventory inv = thrall.getThrallInventory();
        for (IInventory chest : findNearbyInventories(thrall.world, center, 15, 4)) {
            for (int i = 0; i < chest.getSizeInventory() && needed > 0; i++) {
                ItemStack chestStack = chest.getStackInSlot(i);
                if (!chestStack.isEmpty() && chestStack.getItem() == item) {
                    int take = Math.min(needed, chestStack.getCount());
                    inv.addItemStackToInventory(chestStack.splitStack(take));
                    chest.markDirty();
                    needed -= take;
                }
            }
            if (needed <= 0) break;
        }
        return needed;
    }

    /**
     * Counts how many units of {@code template} can still fit into chests near {@code center}.
     * Sums two contributions per chest:
     *   - Stacks of the same item (NBT-aware) with room left, capped by min(stack max, chest stack limit).
     *   - Empty slots usable for this item, each contributing min(stack max, chest stack limit).
     *
     * Used by Porter to cap player→chest pulls so it never strips more than the home depot can absorb.
     */
    public static int countFreeSpaceForItem(World world, BlockPos center, ItemStack template, int hRange, int vRange) {
        if (template.isEmpty()) return 0;
        int free = 0;
        int itemMax = template.getMaxStackSize();
        for (IInventory chest : findNearbyInventories(world, center, hRange, vRange)) {
            int chestLimit = Math.min(itemMax, chest.getInventoryStackLimit());
            for (int i = 0; i < chest.getSizeInventory(); i++) {
                ItemStack slot = chest.getStackInSlot(i);
                if (slot.isEmpty()) {
                    if (chest.isItemValidForSlot(i, template)) free += chestLimit;
                } else if (ItemStack.areItemsEqual(slot, template)
                        && ItemStack.areItemStackTagsEqual(slot, template)) {
                    int room = chestLimit - slot.getCount();
                    if (room > 0) free += room;
                }
            }
        }
        return free;
    }

    /** Counts how many item types in thrallInv are already present in target. Higher = better deposit match. */
    public static int getMatchScore(IInventory target, IInventory thrallInv) {
        int score = 0;
        for (int i = 0; i < thrallInv.getSizeInventory(); i++) {
            ItemStack thrallStack = thrallInv.getStackInSlot(i);
            if (thrallStack.isEmpty()) continue;
            for (int j = 0; j < target.getSizeInventory(); j++) {
                ItemStack chestStack = target.getStackInSlot(j);
                if (!chestStack.isEmpty() && ItemStack.areItemsEqual(chestStack, thrallStack)) {
                    score++;
                    break;
                }
            }
        }
        return score;
    }
}
