package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallChestHelper;
import com.spege.insanetweaks.entities.ThrallMode;
import com.spege.insanetweaks.entities.inventory.ThrallInventory;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI task: Porter mode (chest sorter). REWORK 2026-07-10: the porter no longer ferries
 * items to/from the owner — it is a pure depot keeper anchored at HOME. Every
 * {@code porterIntervalSeconds} it:
 *   1. Deposits anything in its own bag into home chests (smartDeposit).
 *   2. Consolidates chest contents: each item type migrates into the chest where that
 *      type already has the most stacks (bounded per cycle).
 */
@SuppressWarnings("null")
public class ThrallAIPorter extends EntityAIBase {

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallPorter");

    /** Vertical range (blocks) for the chest scan; horizontal range comes from config. */
    private static final int SCAN_VRANGE = 4;

    /** Cap on chest-to-chest sort moves per cycle. Sorting is bounded so a messy depot
     *  takes a few cycles to settle rather than locking up a single tick. */
    private static final int MAX_SORT_TRANSFERS_PER_CYCLE = 8;

    private final EntityThrallMinion thrall;
    private long lastCycleTick;

    public ThrallAIPorter(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(3);
    }

    private static boolean debugLogs() {
        return ModConfig.client.enableThrallDebugLogs;
    }

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() != ThrallMode.PORTER) return false;
        if (!ModConfig.thrall.porter.enablePorterMode) return false;
        return thrall.getHomePoint() != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        lastCycleTick = 0;
        thrall.getNavigator().clearPath();
        thrall.setStatusText("Standing by...");
    }

    @Override
    public void resetTask() {
        thrall.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        long now = thrall.world.getTotalWorldTime();
        long intervalTicks = (long) ModConfig.thrall.porter.porterIntervalSeconds * 20L;
        if (lastCycleTick != 0 && now - lastCycleTick < intervalTicks) {
            return;
        }
        lastCycleTick = now;

        runCycle();
    }

    private void runCycle() {
        BlockPos home = thrall.getHomePoint();
        if (home == null) return;

        int range = ModConfig.thrall.porter.porterChestScanRange;

        // Drop anything we carry into home chests first.
        ThrallInventory inv = thrall.getThrallInventory();
        if (inv.containsItems()) {
            ThrallChestHelper.smartDeposit(thrall, home, range, SCAN_VRANGE, false);
        }

        int sorted = consolidateChests(home, range);
        if (sorted > 0) {
            thrall.setStatusText("Sorting...");
            if (debugLogs()) LOG.info("[Thrall#{}] Porter consolidated {} stacks across chests",
                    thrall.getEntityId(), sorted);
        } else {
            thrall.setStatusText(inv.isFull() ? "Full" : "Standing by...");
        }
    }

    // ------------------------------------------------------------------
    // Chest sorting  (unchanged logic from the pre-rework porter)
    // ------------------------------------------------------------------

    /**
     * Walks chests near home and migrates non-primary stacks into the chest where their type
     * already has the most stacks. Each item type "settles" into its dominant chest over a few
     * cycles; user never has to label chests. Bounded by {@link #MAX_SORT_TRANSFERS_PER_CYCLE}.
     *
     * <p>Tie-breaking uses strict-greater designation, which is stable across cycles even if the
     * tile-entity scan order changes — only a true plurality wins, so two chests with the same
     * count of an item won't flip-flop the destination between cycles.
     */
    private int consolidateChests(BlockPos home, int range) {
        World world = thrall.world;
        List<IInventory> chests = ThrallChestHelper.findNearbyInventories(world, home, range, SCAN_VRANGE);
        if (chests.size() < 2) return 0;

        Map<Sig, Designation> designations = new HashMap<>();
        for (IInventory chest : chests) {
            Map<Sig, Integer> counts = countStacksBySig(chest);
            for (Map.Entry<Sig, Integer> e : counts.entrySet()) {
                Designation existing = designations.get(e.getKey());
                if (existing == null || e.getValue() > existing.count) {
                    designations.put(e.getKey(), new Designation(chest, e.getValue()));
                }
            }
        }

        int transfers = 0;
        outer:
        for (IInventory src : chests) {
            for (int i = 0; i < src.getSizeInventory(); i++) {
                if (transfers >= MAX_SORT_TRANSFERS_PER_CYCLE) break outer;
                ItemStack stack = src.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                Designation dest = designations.get(Sig.of(stack));
                if (dest == null || dest.chest == src) continue;
                if (transferBetweenChests(src, i, dest.chest) > 0) transfers++;
            }
        }
        return transfers;
    }

    private static Map<Sig, Integer> countStacksBySig(IInventory chest) {
        Map<Sig, Integer> counts = new HashMap<>();
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            ItemStack s = chest.getStackInSlot(i);
            if (s.isEmpty()) continue;
            Sig sig = Sig.of(s);
            Integer prev = counts.get(sig);
            counts.put(sig, prev == null ? 1 : prev + 1);
        }
        return counts;
    }

    private static final class Designation {
        final IInventory chest;
        final int count;
        Designation(IInventory chest, int count) { this.chest = chest; this.count = count; }
    }

    /**
     * Moves as much of {@code src[srcSlot]} as fits into {@code dst}. Tries partial-merge first,
     * then empty slots. Returns count actually moved. Mirrors {@code ThrallInventory.addStackToInventory}
     * but operates on two arbitrary IInventories, not the thrall's bag.
     */
    private static int transferBetweenChests(IInventory src, int srcSlot, IInventory dst) {
        ItemStack stack = src.getStackInSlot(srcSlot);
        if (stack.isEmpty()) return 0;
        int original = stack.getCount();
        int chestStackLimit = Math.min(stack.getMaxStackSize(), dst.getInventoryStackLimit());

        for (int i = 0; i < dst.getSizeInventory() && stack.getCount() > 0; i++) {
            ItemStack slot = dst.getStackInSlot(i);
            if (slot.isEmpty()) continue;
            if (!ItemStack.areItemsEqual(slot, stack) || !ItemStack.areItemStackTagsEqual(slot, stack)) continue;
            int room = chestStackLimit - slot.getCount();
            if (room <= 0) continue;
            int take = Math.min(room, stack.getCount());
            slot.grow(take);
            stack.shrink(take);
        }
        for (int i = 0; i < dst.getSizeInventory() && stack.getCount() > 0; i++) {
            if (!dst.getStackInSlot(i).isEmpty()) continue;
            if (!dst.isItemValidForSlot(i, stack)) continue;
            int take = Math.min(chestStackLimit, stack.getCount());
            ItemStack copy = stack.copy();
            copy.setCount(take);
            dst.setInventorySlotContents(i, copy);
            stack.shrink(take);
        }

        int moved = original - stack.getCount();
        if (moved > 0) {
            dst.markDirty();
            if (stack.isEmpty()) {
                src.setInventorySlotContents(srcSlot, ItemStack.EMPTY);
            }
            src.markDirty();
        }
        return moved;
    }

    /** Compact (item + meta + NBT) signature suitable as a HashMap key. */
    private static final class Sig {
        private final Item item;
        private final int meta;
        @Nullable private final NBTTagCompound nbt;
        private final int hash;

        static Sig of(ItemStack s) { return new Sig(s.getItem(), s.getMetadata(), s.getTagCompound()); }

        private Sig(Item item, int meta, @Nullable NBTTagCompound nbt) {
            this.item = item;
            this.meta = meta;
            this.nbt = nbt;
            int h = item.hashCode();
            h = 31 * h + meta;
            h = 31 * h + (nbt != null ? nbt.hashCode() : 0);
            this.hash = h;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Sig)) return false;
            Sig s = (Sig) o;
            return meta == s.meta && item == s.item && Objects.equals(nbt, s.nbt);
        }

        @Override public int hashCode() { return hash; }
    }
}
