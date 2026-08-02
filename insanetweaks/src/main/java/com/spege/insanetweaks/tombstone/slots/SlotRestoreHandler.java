package com.spege.insanetweaks.tombstone.slots;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.tombstone.slots.SlotSnapshot.Entry;
import com.spege.insanetweaks.util.BaublesSlotCompat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import ovh.corail.tombstone.api.event.RestoreInventoryEvent;

/**
 * Puts a recovered grave's contents back where they came from.
 *
 * <p>No mixin is involved. Tombstone fires {@code RestoreInventoryEvent} from
 * {@code TileEntityPlayerGrave.giveInventory} at the one useful moment: after the
 * {@code chanceLossOnDeath} rule has already taken its cut, and before auto-equip, the hotbar
 * tool priority pass, or anything else has handed the player a single item. The listener gets the
 * grave's live {@code ItemStackHandler}, so an item lifted out here is simply not there for the
 * stock distribution to place, and everything left behind is distributed exactly as before.
 *
 * <p>Every placement is conditional on the destination being empty. Anything whose seat was taken
 * — because the player looted, crafted or died again before walking back — is left in the grave
 * and follows the stock path into the first free slot or onto the ground. So the worst case is
 * today's behaviour, never a lost or duplicated item.
 *
 * <p>Baubles are inserted here rather than by Tombstone on purpose:
 * {@code compatibilities.allowBaublesAutoEquip} guesses the slot from the item's declared type and
 * gets it wrong on a pack with re-parented bauble types. A recorded index does not guess.
 */
public class SlotRestoreHandler {

    @SubscribeEvent
    public void onRestoreInventory(RestoreInventoryEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        if (!ModConfig.tombstone.enableTombstoneTweaks || !ModConfig.tombstone.restoreOriginalSlots) {
            return;
        }

        // Only the owner's own layout, so someone else's grave can never pull a snapshot of ours.
        String ownerName = event.getOwnerName();
        if (ownerName == null || !ownerName.equals(player.getGameProfile().getName())) {
            return;
        }

        IItemHandler grave = event.getInventory();
        if (grave == null || grave.getSlots() <= 0) {
            return;
        }

        SlotSnapshot snapshot = SlotSnapshotStore.take(player, event.getOwnerDeathTime());
        if (snapshot == null) {
            return; // Old grave, world from before this feature, or the snapshot already expired.
        }

        int restored = restore(player, grave, snapshot);

        if (restored > 0) {
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
        }

        if (ModConfig.tombstone.slotRestoreDebugLogging) {
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Slot restore: returned {} of {} remembered items to {} at {}.",
                    Integer.valueOf(restored), Integer.valueOf(snapshot.getEntries().size()),
                    player.getName(), event.getGravePos());
        }
    }

    private static int restore(EntityPlayer player, IItemHandler grave, SlotSnapshot snapshot) {
        boolean[] taken = new boolean[grave.getSlots()];
        int restored = 0;

        for (Entry entry : snapshot.getEntries()) {
            int graveSlot = findMatch(grave, taken, entry);
            if (graveSlot < 0) {
                continue;
            }
            ItemStack candidate = grave.getStackInSlot(graveSlot);
            if (!canRestore(player, entry.slot, candidate)) {
                continue;
            }

            ItemStack stack = takeFromGrave(grave, graveSlot);
            if (stack.isEmpty()) {
                continue;
            }
            taken[graveSlot] = true;

            try {
                place(player, entry.slot, stack);
                restored++;
            } catch (RuntimeException e) {
                // Nothing here should throw, but an item half-way between a grave and a player is
                // the one outcome worth writing code against. Put it back and let Tombstone deal.
                putBack(grave, graveSlot, stack);
                taken[graveSlot] = false;
                InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks] Slot restore failed for {} in slot {}; returned it to the grave.",
                        stack.getDisplayName(), Integer.valueOf(entry.slot), e);
            }
        }
        return restored;
    }

    /** First grave slot still holding the item this entry remembers. */
    private static int findMatch(IItemHandler grave, boolean[] taken, Entry entry) {
        for (int i = 0; i < grave.getSlots(); i++) {
            if (!taken[i] && SlotSnapshot.matches(entry, grave.getStackInSlot(i))) {
                return i;
            }
        }
        return -1;
    }

    private static boolean canRestore(EntityPlayer player, int encodedSlot, ItemStack stack) {
        if (encodedSlot >= SlotSnapshot.BAUBLE_BASE) {
            return BaublesSlotCompat.canPlace(player, encodedSlot - SlotSnapshot.BAUBLE_BASE, stack);
        }
        if (encodedSlot == SlotSnapshot.OFFHAND_SLOT) {
            NonNullList<ItemStack> offhand = player.inventory.offHandInventory;
            return !offhand.isEmpty() && offhand.get(0).isEmpty();
        }
        if (encodedSlot >= SlotSnapshot.ARMOR_BASE) {
            int index = encodedSlot - SlotSnapshot.ARMOR_BASE;
            NonNullList<ItemStack> armor = player.inventory.armorInventory;
            return index < armor.size() && armor.get(index).isEmpty();
        }
        NonNullList<ItemStack> main = player.inventory.mainInventory;
        return encodedSlot >= 0 && encodedSlot < main.size() && main.get(encodedSlot).isEmpty();
    }

    private static void place(EntityPlayer player, int encodedSlot, ItemStack stack) {
        if (encodedSlot >= SlotSnapshot.BAUBLE_BASE) {
            BaublesSlotCompat.setStack(player, encodedSlot - SlotSnapshot.BAUBLE_BASE, stack);
        } else if (encodedSlot == SlotSnapshot.OFFHAND_SLOT) {
            player.inventory.offHandInventory.set(0, stack);
        } else if (encodedSlot >= SlotSnapshot.ARMOR_BASE) {
            player.inventory.armorInventory.set(encodedSlot - SlotSnapshot.ARMOR_BASE, stack);
        } else {
            player.inventory.mainInventory.set(encodedSlot, stack);
        }
    }

    /**
     * The event declares {@link IItemHandler}, but the grave hands over its own
     * {@code ItemStackHandler}. The cast is the clean route; {@code extractItem} is the contract
     * fallback for the day that stops being true.
     */
    private static ItemStack takeFromGrave(IItemHandler grave, int slot) {
        ItemStack present = grave.getStackInSlot(slot);
        if (present.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (grave instanceof IItemHandlerModifiable) {
            ItemStack copy = present.copy();
            ((IItemHandlerModifiable) grave).setStackInSlot(slot, ItemStack.EMPTY);
            return copy;
        }
        return grave.extractItem(slot, present.getCount(), false);
    }

    private static void putBack(IItemHandler grave, int slot, ItemStack stack) {
        if (grave instanceof IItemHandlerModifiable) {
            ((IItemHandlerModifiable) grave).setStackInSlot(slot, stack);
        } else {
            grave.insertItem(slot, stack, false);
        }
    }
}
