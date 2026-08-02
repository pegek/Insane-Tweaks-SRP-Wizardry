package com.spege.tombtweaks.slots;

import com.spege.tombtweaks.TombstoneTweaks;
import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.util.BaublesSlotCompat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Records where everything was sitting, at the last moment it is still true.
 *
 * <p>{@code LivingDeathEvent} is the deadline: vanilla empties the inventory in
 * {@code EntityPlayer.onDeath} through {@code dropAllItems()}, which runs after this event, and by
 * the time anything reaches {@code LivingDropsEvent} the items are a flat pile with no indices
 * left. Highest priority so a handler that cancels or short-circuits the death cannot get in
 * first — capturing a snapshot for a death that then does not happen costs one unused list entry,
 * which the store prunes.
 */
public class SlotSnapshotHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world == null || player.world.isRemote) {
            return;
        }
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks || !TombTweaksConfig.tombstone.restoreOriginalSlots) {
            return;
        }
        if (player.world.getGameRules().getBoolean("keepInventory")) {
            return;
        }

        SlotSnapshot snapshot = new SlotSnapshot(System.currentTimeMillis());

        NonNullList<ItemStack> main = player.inventory.mainInventory;
        for (int i = 0; i < main.size(); i++) {
            snapshot.add(i, main.get(i));
        }

        NonNullList<ItemStack> armor = player.inventory.armorInventory;
        for (int i = 0; i < armor.size(); i++) {
            snapshot.add(SlotSnapshot.ARMOR_BASE + i, armor.get(i));
        }

        NonNullList<ItemStack> offhand = player.inventory.offHandInventory;
        if (!offhand.isEmpty()) {
            snapshot.add(SlotSnapshot.OFFHAND_SLOT, offhand.get(0));
        }

        int baubleSlots = BaublesSlotCompat.getSlots(player);
        for (int i = 0; i < baubleSlots; i++) {
            snapshot.add(SlotSnapshot.BAUBLE_BASE + i, BaublesSlotCompat.getStack(player, i));
        }

        if (snapshot.isEmpty()) {
            return;
        }

        SlotSnapshotStore.store(player, snapshot);

        if (TombTweaksConfig.tombstone.slotRestoreDebugLogging) {
            TombstoneTweaks.LOGGER.info("[TombstoneTweaks] Slot snapshot: {} died carrying {} items.",
                    player.getName(), Integer.valueOf(snapshot.getEntries().size()));
        }
    }
}
