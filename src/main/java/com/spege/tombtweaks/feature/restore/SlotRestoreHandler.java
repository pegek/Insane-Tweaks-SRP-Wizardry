package com.spege.tombtweaks.feature.restore;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.SlotPlan;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.SnapshotCodec;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import ovh.corail.tombstone.api.event.RestoreInventoryEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Odklada zawartosc odzyskanego grobu tam, skad przyszla.
 *
 * <p>Bez mixina. Tombstone odpala {@code RestoreInventoryEvent} w jedynym uzytecznym momencie:
 * po tym, jak chanceLossOnDeath wzial swoja czesc, i przed auto-equipem oraz cala reszta
 * rozdzialu. Dostajemy zywy ItemStackHandler grobu, wiec przedmiot wyjety tutaj po prostu
 * nie istnieje dla sciezki standardowej.
 *
 * <p>Rozsadzenie liczy {@link SlotPlan#assign} dla calego grobu naraz (dwa przebiegi), bo
 * Tombstone scala groby: druga smierc w promieniu 20 blokow dorzuca przedmioty do starego
 * grobu, a ten wiaze sie tylko ze snapshotem ostatniej smierci.
 *
 * <p>Kazde wstawienie jest warunkowe na tym, ze docelowy slot jest pusty, sprawdzone ponownie
 * tuz przed wstawieniem. To ten warunek - nie {@code NO_SEAT} - gwarantuje, ze najgorszym
 * przypadkiem jest zachowanie standardowe, nigdy zgubiony ani zdublowany przedmiot.
 */
@Mod.EventBusSubscriber(modid = TombTweaks.MODID)
public final class SlotRestoreHandler {

    /** Ile ms moze dzielic capturedAt snapshotu od deathDate grobu. */
    private static final long TOLERANCE_MILLIS = 10_000L;

    private SlotRestoreHandler() {
    }

    @SubscribeEvent
    public static void onRestoreInventory(RestoreInventoryEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.restoreEnabled.get()) {
            return;
        }
        // Tombstone woła giveInventory takze dla gracza z kluczem do grobu i przy tomb raidingu,
        // a getPlayer() to ten, kto OTWIERA grob. Snapshot jest per gracz, wiec dla obcego grobu
        // wzielibysmy cudzy snapshot (np. dwoch graczy z jednego wybuchu, w 10 s tolerancji).
        // Porownanie po nazwie, a nie przez BlockWritableGrave.isOwner: isOwner zwraca true
        // dla grobu bez ownerId, a sciezka /tbrestoreinventory nie ma grobu w tym wymiarze.
        if (!player.getGameProfile().getName().equals(event.getOwnerName())) {
            return;
        }

        SnapshotStore store = SnapshotCodec.load(player);
        SlotSnapshot snapshot = store.claimNearest(event.getOwnerDeathTime(), TOLERANCE_MILLIS);
        if (snapshot == null) {
            if (Config.INSTANCE.restoreDebugLogging.get()) {
                TombTweaks.LOGGER.info("[TombTweaks] no snapshot within {} ms of grave death time {}",
                        TOLERANCE_MILLIS, event.getOwnerDeathTime());
            }
            return;
        }
        SnapshotCodec.save(player, store);

        IItemHandler grave = event.getInventory();
        Inventory inventory = player.getInventory();

        List<ItemKey> graveKeys = new ArrayList<>(grave.getSlots());
        for (int i = 0; i < grave.getSlots(); i++) {
            ItemStack inGrave = grave.getStackInSlot(i);
            graveKeys.add(inGrave.isEmpty() ? null : StackViews.keyOf(inGrave));
        }
        int[] seats = SlotPlan.assign(snapshot, graveKeys, seat -> isSeatFree(inventory, seat));

        int seated = 0;
        for (int i = 0; i < seats.length; i++) {
            int seat = seats[i];
            // Ponowne sprawdzenie to warunek niezmiennika, nie ostroznosc na zapas.
            if (seat == SlotPlan.NO_SEAT || !isSeatFree(inventory, seat)) {
                continue;
            }
            ItemStack inGrave = grave.getStackInSlot(i);
            if (inGrave.isEmpty()) {
                continue;
            }
            ItemStack taken = grave.extractItem(i, inGrave.getCount(), false);
            if (taken.isEmpty()) {
                continue;
            }
            place(inventory, seat, taken);
            seated++;
        }

        if (Config.INSTANCE.restoreDebugLogging.get()) {
            TombTweaks.LOGGER.info("[TombTweaks] seated {} of {} recorded stacks for {}",
                    seated, snapshot.entries().size(), player.getGameProfile().getName());
        }
    }

    private static boolean isSeatFree(Inventory inventory, int seat) {
        if (seat == SlotSnapshot.OFFHAND_SLOT) {
            return inventory.offhand.get(0).isEmpty();
        }
        if (seat >= SlotSnapshot.ARMOR_BASE && seat < SlotSnapshot.ARMOR_BASE + inventory.armor.size()) {
            return inventory.armor.get(seat - SlotSnapshot.ARMOR_BASE).isEmpty();
        }
        if (seat >= 0 && seat < inventory.items.size()) {
            return inventory.items.get(seat).isEmpty();
        }
        return false; // np. zarezerwowana przestrzen Curios - nieobslugiwana w v1
    }

    private static void place(Inventory inventory, int seat, ItemStack stack) {
        if (seat == SlotSnapshot.OFFHAND_SLOT) {
            inventory.offhand.set(0, stack);
        } else if (seat >= SlotSnapshot.ARMOR_BASE && seat < SlotSnapshot.ARMOR_BASE + inventory.armor.size()) {
            inventory.armor.set(seat - SlotSnapshot.ARMOR_BASE, stack);
        } else {
            inventory.items.set(seat, stack);
        }
    }
}
