package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallChestHelper;
import com.spege.insanetweaks.entities.ThrallMode;
import com.spege.insanetweaks.entities.inventory.ThrallInventory;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI task: Porter mode (auto-stocker).
 *
 * The Thrall stays anchored at its home point. Every {@code porterIntervalSeconds}, it:
 *   1. Builds a "manifest" by scanning chests near home for stored item types.
 *   2. If the owner is online in the same dimension and within {@code porterTeleportRange},
 *      teleports to the owner, pulls matching items from the owner's main inventory
 *      (skipping hotbar/armor/offhand), teleports back home, and deposits via smartDeposit.
 *
 * Cross-dimension delivery is not supported. If no chests, no matching items, or owner
 * offline/out of range, the Thrall idles silently until the next cycle.
 */
@SuppressWarnings("null")
public class ThrallAIPorter extends EntityAIBase {

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallPorter");

    /** Vertical range (blocks) for chest manifest scan; horizontal range comes from config. */
    private static final int MANIFEST_VRANGE = 4;

    /** Player main-inventory slot range (skip hotbar 0-8, armor, offhand). */
    private static final int PLAYER_MAIN_INV_START = 9;
    private static final int PLAYER_MAIN_INV_END   = 35;

    /** Cap on number of stacks transferred per cycle to keep behavior incremental. */
    private static final int MAX_TRANSFERS_PER_CYCLE = 16;

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

    // ------------------------------------------------------------------
    // Cycle
    // ------------------------------------------------------------------

    private void runCycle() {
        BlockPos home = thrall.getHomePoint();
        if (home == null) return;

        if (ModConfig.thrall.porter.porterDirection
                == com.spege.insanetweaks.config.categories.ThrallCategory.PorterDirection.FROM_HOME) {
            runReverseCycle(home);
            return;
        }

        int range = ModConfig.thrall.porter.porterChestScanRange;

        // First, drop anything we already carry into home chests so we have room for new pulls.
        // keepTorches=false: porter never mines, so any stray torches just go to chests like everything else.
        ThrallInventory inv = thrall.getThrallInventory();
        if (inv.containsItems()) {
            ThrallChestHelper.smartDeposit(thrall, home, range, MANIFEST_VRANGE, false);
        }

        // Active chest sorting — consolidate misplaced item types into their dominant chest.
        if (ModConfig.thrall.porter.enablePorterSorting) {
            int sorted = consolidateChests(home, range);
            if (sorted > 0) {
                thrall.setStatusText("Sorting...");
                if (debugLogs()) LOG.info("[Thrall#{}] Porter consolidated {} stacks across chests",
                        thrall.getEntityId(), sorted);
            }
        }

        if (inv.isFull()) {
            thrall.setStatusText("Full");
            return;
        }

        // Build manifest from nearby chests.
        List<ItemStack> manifest = buildManifest(home, range);
        if (manifest.isEmpty()) {
            thrall.setStatusText("No manifest");
            return;
        }

        // Find owner.
        EntityLivingBase casterEntity = thrall.getCaster();
        if (!(casterEntity instanceof EntityPlayer)) {
            thrall.setStatusText("Awaiting owner");
            return;
        }
        EntityPlayer owner = (EntityPlayer) casterEntity;
        if (!owner.isEntityAlive()) {
            thrall.setStatusText("Awaiting owner");
            return;
        }
        if (owner.world.provider.getDimension() != thrall.world.provider.getDimension()) {
            thrall.setStatusText("Owner away");
            return;
        }

        double rangeSq = (double) ModConfig.thrall.porter.porterTeleportRange * ModConfig.thrall.porter.porterTeleportRange;
        if (owner.getDistanceSq(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > rangeSq) {
            thrall.setStatusText("Owner away");
            return;
        }

        // Quick pre-scan: anything to take?
        if (!ownerHasManifestItem(owner, manifest)) {
            thrall.setStatusText("Standing by...");
            return;
        }

        // Snapshot per-chest capacity so empty slots aren't double-counted across manifest entries.
        // Without this, two pulls of different types could both lay claim to the same single empty slot,
        // leading to over-pull and items stuck in the thrall's inventory after deposit.
        ChestBudgetPool pool = ChestBudgetPool.snapshot(thrall.world, home, range, MANIFEST_VRANGE);

        thrall.setStatusText("Fetching...");
        teleportToOwner(owner);
        int transferred = pullFromOwner(owner, manifest, pool);
        teleportToHome(home);

        if (transferred > 0) {
            ThrallChestHelper.smartDeposit(thrall, home, range, MANIFEST_VRANGE, false);
            thrall.setStatusText("Stocked " + transferred);
            if (debugLogs()) LOG.info("[Thrall#{}] Porter cycle delivered {} stacks", thrall.getEntityId(), transferred);
        } else {
            thrall.setStatusText("Standing by...");
        }
    }

    /**
     * FROM_HOME reverse restock (spec 4.3). Tops up the owner's existing partial main-inventory
     * stacks from home chests. Never introduces new item types (only types the owner already
     * carries with a non-full stack qualify) and never touches hotbar/armour/offhand. Leftovers
     * the owner couldn't absorb are deposited back into home chests on return.
     */
    private void runReverseCycle(BlockPos home) {
        int range = ModConfig.thrall.porter.porterChestScanRange;
        ThrallInventory inv = thrall.getThrallInventory();

        // Clear anything we might still be carrying so the pulled items don't mix with stale loot.
        // If a prior aborted top-up left stock matching a still-open need, this deposits it and the
        // pull below re-draws it from the same chests — accepted redundancy (cheap, no teleport).
        if (inv.containsItems()) {
            ThrallChestHelper.smartDeposit(thrall, home, range, MANIFEST_VRANGE, false);
        }
        if (inv.isFull()) {
            thrall.setStatusText("Full");
            return;
        }

        // Owner presence / range checks (mirror the TO_HOME path).
        EntityLivingBase casterEntity = thrall.getCaster();
        if (!(casterEntity instanceof EntityPlayer)) {
            thrall.setStatusText("Awaiting owner");
            return;
        }
        EntityPlayer owner = (EntityPlayer) casterEntity;
        if (!owner.isEntityAlive()) {
            thrall.setStatusText("Awaiting owner");
            return;
        }
        if (owner.world.provider.getDimension() != thrall.world.provider.getDimension()) {
            thrall.setStatusText("Owner away");
            return;
        }
        double rangeSq = (double) ModConfig.thrall.porter.porterTeleportRange * ModConfig.thrall.porter.porterTeleportRange;
        if (owner.getDistanceSq(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > rangeSq) {
            thrall.setStatusText("Owner away");
            return;
        }

        // Build the top-up manifest: one entry per owner main-inventory type that has a NON-FULL stack,
        // with the total missing count (how much would fill every partial stack of that type).
        Map<Sig, Integer> needed = buildRestockNeeds(owner);
        if (needed.isEmpty()) {
            thrall.setStatusText("Standing by...");
            return;
        }

        // Pull from home chests only those types, capped at the needed amount, into our bag.
        int pulled = pullRestockFromChests(home, range, needed);
        if (pulled <= 0) {
            thrall.setStatusText("No stock");
            return;
        }

        thrall.setStatusText("Restocking...");
        teleportToOwner(owner);
        int topped = topUpOwner(owner);
        teleportToHome(home);

        // Return any leftover (types the owner filled up before we drained our bag) to home chests.
        if (inv.containsItems()) {
            ThrallChestHelper.smartDeposit(thrall, home, range, MANIFEST_VRANGE, false);
        }

        if (topped > 0) {
            thrall.setStatusText("Restocked " + topped);
            if (debugLogs()) LOG.info("[Thrall#{}] Porter FROM_HOME topped up {} stacks", thrall.getEntityId(), topped);
        } else {
            thrall.setStatusText("Standing by...");
        }
    }

    /**
     * Maps each owner MAIN-inventory item type (slots 9-35) that has at least one non-full stack to
     * the total count needed to fill every partial stack of that type. Hotbar/armour/offhand ignored.
     */
    private Map<Sig, Integer> buildRestockNeeds(EntityPlayer owner) {
        Map<Sig, Integer> needs = new HashMap<>();
        for (int i = PLAYER_MAIN_INV_START; i <= PLAYER_MAIN_INV_END; i++) {
            ItemStack s = owner.inventory.mainInventory.get(i);
            if (s.isEmpty()) continue;
            int room = s.getMaxStackSize() - s.getCount();
            if (room <= 0) continue; // full stack — not eligible
            Sig sig = Sig.of(s);
            Integer prev = needs.get(sig);
            needs.put(sig, (prev == null ? 0 : prev) + room);
        }
        return needs;
    }

    /**
     * Walks home chests and pulls up to the needed amount of each manifest type into the thrall bag.
     * Decrements the needs map as it pulls so we never over-draw beyond what the owner can absorb.
     * Returns the total item count pulled.
     */
    private int pullRestockFromChests(BlockPos home, int range, Map<Sig, Integer> needed) {
        ThrallInventory inv = thrall.getThrallInventory();
        int pulled = 0;
        List<IInventory> chests = ThrallChestHelper.findNearbyInventories(thrall.world, home, range, MANIFEST_VRANGE);
        for (IInventory chest : chests) {
            for (int i = 0; i < chest.getSizeInventory(); i++) {
                if (inv.isFull()) return pulled;
                ItemStack s = chest.getStackInSlot(i);
                if (s.isEmpty()) continue;
                Sig sig = Sig.of(s);
                Integer need = needed.get(sig);
                if (need == null || need <= 0) continue;

                int take = Math.min(need, s.getCount());
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
                    needed.put(sig, need - actuallyTaken);
                    pulled += actuallyTaken;
                }
            }
        }
        return pulled;
    }

    /**
     * Tops up the owner's existing partial MAIN-inventory stacks (slots 9-35) from the thrall bag.
     * Only merges into stacks that already exist (never fills empty slots, never creates a new type,
     * never touches hotbar/armour/offhand). Returns the number of owner slots that received items.
     */
    private int topUpOwner(EntityPlayer owner) {
        ThrallInventory inv = thrall.getThrallInventory();
        int slotsToppedUp = 0;
        for (int i = PLAYER_MAIN_INV_START; i <= PLAYER_MAIN_INV_END; i++) {
            ItemStack dst = owner.inventory.mainInventory.get(i);
            if (dst.isEmpty()) continue;
            int room = dst.getMaxStackSize() - dst.getCount();
            if (room <= 0) continue;

            int moved = drainFromBag(inv, dst, room);
            if (moved > 0) {
                dst.grow(moved);
                slotsToppedUp++;
            }
        }
        if (slotsToppedUp > 0 && owner.inventoryContainer != null) {
            owner.inventoryContainer.detectAndSendChanges();
        }
        return slotsToppedUp;
    }

    /**
     * Removes up to {@code want} items matching {@code template} (item+meta+NBT) from the thrall bag.
     * Returns the count removed. Does not modify {@code template}; caller grows the destination stack.
     */
    private int drainFromBag(ThrallInventory inv, ItemStack template, int want) {
        int drained = 0;
        for (int i = 0; i < inv.getSizeInventory() && drained < want; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (!ItemStack.areItemsEqual(s, template) || !ItemStack.areItemStackTagsEqual(s, template)) continue;
            int take = Math.min(want - drained, s.getCount());
            s.shrink(take);
            if (s.isEmpty()) {
                inv.setInventorySlotContents(i, ItemStack.EMPTY);
            }
            drained += take;
        }
        return drained;
    }

    // ------------------------------------------------------------------
    // Manifest
    // ------------------------------------------------------------------

    /** Walks chests near home and collects one representative ItemStack per unique (item+meta+NBT). */
    private List<ItemStack> buildManifest(BlockPos home, int range) {
        List<ItemStack> manifest = new ArrayList<>();
        List<IInventory> chests = ThrallChestHelper.findNearbyInventories(
                thrall.world, home, range, MANIFEST_VRANGE);
        for (IInventory chest : chests) {
            for (int i = 0; i < chest.getSizeInventory(); i++) {
                ItemStack s = chest.getStackInSlot(i);
                if (s.isEmpty()) continue;
                if (!containsSignature(manifest, s)) {
                    manifest.add(s.copy());
                }
            }
        }
        return manifest;
    }

    private static boolean containsSignature(List<ItemStack> manifest, ItemStack stack) {
        for (ItemStack m : manifest) {
            if (ItemStack.areItemsEqual(m, stack) && ItemStack.areItemStackTagsEqual(m, stack)) return true;
        }
        return false;
    }

    private boolean ownerHasManifestItem(EntityPlayer owner, List<ItemStack> manifest) {
        for (int i = PLAYER_MAIN_INV_START; i <= PLAYER_MAIN_INV_END; i++) {
            ItemStack s = owner.inventory.mainInventory.get(i);
            if (s.isEmpty()) continue;
            if (containsSignature(manifest, s)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Pull from owner
    // ------------------------------------------------------------------

    private int pullFromOwner(EntityPlayer owner, List<ItemStack> manifest, ChestBudgetPool pool) {
        ThrallInventory inv = thrall.getThrallInventory();
        int transfers = 0;
        // Hotbar exclusion (spec 4.1): slots 0-8 are the hotbar, 36-39 armour, 40 offhand — none are
        // ever read. PLAYER_MAIN_INV_START is pinned to 9 so the porter only manages the main inventory.
        for (int i = PLAYER_MAIN_INV_START; i <= PLAYER_MAIN_INV_END && transfers < MAX_TRANSFERS_PER_CYCLE; i++) {
            ItemStack s = owner.inventory.mainInventory.get(i);
            if (s.isEmpty()) continue;
            if (!containsSignature(manifest, s)) continue;
            if (inv.isFull()) break;

            int available = pool.available(s);
            if (available <= 0) continue;
            int toTake = Math.min(s.getCount(), available);

            ItemStack working = s.copy();
            working.setCount(toTake);
            int requested = working.getCount();
            inv.addItemStackToInventory(working);
            int taken = requested - working.getCount();
            if (taken > 0) {
                // Decrement budget BEFORE shrink — once s.shrink hits 0 the stack reports AIR and
                // its signature would no longer match the manifest entry we pulled against.
                pool.consume(s, taken);
                s.shrink(taken);
                if (s.isEmpty()) {
                    owner.inventory.mainInventory.set(i, ItemStack.EMPTY);
                }
                if (owner.inventoryContainer != null) {
                    owner.inventoryContainer.detectAndSendChanges();
                }
                transfers++;
            }
        }
        return transfers;
    }

    // ------------------------------------------------------------------
    // Teleport
    // ------------------------------------------------------------------

    private void teleportToOwner(EntityPlayer owner) {
        World world = thrall.world;
        int baseX = MathHelper.floor(owner.posX) - 2;
        int baseZ = MathHelper.floor(owner.posZ) - 2;
        int baseY = MathHelper.floor(owner.getEntityBoundingBox().minY);

        for (int dx = 1; dx <= 3; dx++) {
            for (int dz = 1; dz <= 3; dz++) {
                BlockPos candidate = new BlockPos(baseX + dx, baseY, baseZ + dz);
                if (world.getBlockState(candidate.down()).isSideSolid(world, candidate.down(), net.minecraft.util.EnumFacing.UP)
                        && world.isAirBlock(candidate) && world.isAirBlock(candidate.up())) {
                    teleportTo(candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5);
                    return;
                }
            }
        }
        // Fallback: snap directly to owner's feet (best-effort)
        teleportTo(owner.posX, owner.getEntityBoundingBox().minY, owner.posZ);
    }

    private void teleportToHome(@Nullable BlockPos home) {
        if (home == null) return;
        teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
    }

    private void teleportTo(double x, double y, double z) {
        World world = thrall.world;
        thrall.playTeleportSound();
        world.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                thrall.posX, thrall.posY + 1.0, thrall.posZ, 0.0, 0.0, 0.0);
        thrall.setPositionAndUpdate(x, y, z);
        world.spawnParticle(EnumParticleTypes.SMOKE_LARGE,
                x, y + 1.0, z, 0.0, 0.0, 0.0);
        thrall.playTeleportSound();
        thrall.getNavigator().clearPath();
    }

    // ------------------------------------------------------------------
    // Chest sorting
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
        List<IInventory> chests = ThrallChestHelper.findNearbyInventories(world, home, range, MANIFEST_VRANGE);
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

    // ------------------------------------------------------------------
    // Budget pool (per-cycle chest capacity tracking)
    // ------------------------------------------------------------------

    /**
     * Snapshots per-chest free space at the start of a cycle, broken down by signature
     * (partial-stack room) and shared empty-slot room. Decrementing partial-stack room is
     * signature-specific; decrementing empty-slot room converts that slot into "partial of this sig"
     * for any leftover capacity. This avoids the over-count that would happen if every manifest
     * entry naively counted the same empty slot toward its own budget.
     */
    private static final class ChestBudgetPool {
        private final List<ChestBudget> budgets;

        private ChestBudgetPool(List<ChestBudget> budgets) { this.budgets = budgets; }

        static ChestBudgetPool snapshot(World world, BlockPos center, int hRange, int vRange) {
            List<IInventory> chests = ThrallChestHelper.findNearbyInventories(world, center, hRange, vRange);
            List<ChestBudget> budgets = new ArrayList<>(chests.size());
            for (IInventory chest : chests) budgets.add(ChestBudget.snapshot(chest));
            return new ChestBudgetPool(budgets);
        }

        int available(ItemStack template) {
            Sig sig = Sig.of(template);
            int itemMax = template.getMaxStackSize();
            int total = 0;
            for (ChestBudget cb : budgets) {
                int chestStackLimit = Math.min(itemMax, cb.stackLimit);
                Integer partial = cb.partialFree.get(sig);
                if (partial != null) total += partial;
                total += cb.emptySlots * chestStackLimit;
            }
            return total;
        }

        void consume(ItemStack template, int amount) {
            Sig sig = Sig.of(template);
            int itemMax = template.getMaxStackSize();
            for (ChestBudget cb : budgets) {
                if (amount <= 0) break;
                int chestStackLimit = Math.min(itemMax, cb.stackLimit);
                Integer partial = cb.partialFree.get(sig);
                if (partial != null && partial > 0) {
                    int take = Math.min(partial, amount);
                    cb.partialFree.put(sig, partial - take);
                    amount -= take;
                }
                while (amount > 0 && cb.emptySlots > 0) {
                    int take = Math.min(chestStackLimit, amount);
                    cb.emptySlots--;
                    int leftover = chestStackLimit - take;
                    if (leftover > 0) {
                        Integer p = cb.partialFree.get(sig);
                        cb.partialFree.put(sig, (p == null ? 0 : p) + leftover);
                    }
                    amount -= take;
                }
            }
        }
    }

    private static final class ChestBudget {
        final int stackLimit;
        int emptySlots;
        final Map<Sig, Integer> partialFree;

        private ChestBudget(int stackLimit, int emptySlots, Map<Sig, Integer> partialFree) {
            this.stackLimit = stackLimit;
            this.emptySlots = emptySlots;
            this.partialFree = partialFree;
        }

        static ChestBudget snapshot(IInventory chest) {
            int stackLimit = chest.getInventoryStackLimit();
            int emptySlots = 0;
            Map<Sig, Integer> partials = new HashMap<>();
            for (int i = 0; i < chest.getSizeInventory(); i++) {
                ItemStack slot = chest.getStackInSlot(i);
                if (slot.isEmpty()) {
                    emptySlots++;
                } else {
                    int chestStackLimit = Math.min(slot.getMaxStackSize(), stackLimit);
                    int room = chestStackLimit - slot.getCount();
                    if (room > 0) {
                        Sig sig = Sig.of(slot);
                        Integer prev = partials.get(sig);
                        partials.put(sig, (prev == null ? 0 : prev) + room);
                    }
                }
            }
            return new ChestBudget(stackLimit, emptySlots, partials);
        }
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
