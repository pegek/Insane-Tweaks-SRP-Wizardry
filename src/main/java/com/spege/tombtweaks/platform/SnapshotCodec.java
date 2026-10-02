package com.spege.tombtweaks.platform;

import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.SlotEntry;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * SnapshotStore w obie strony przez NBT gracza.
 *
 * <p>Konwersja jest rozdzielona od dostepu do gracza ({@link #write}/{@link #read}), zeby
 * round-trip dalo sie przetestowac bez instancji Playera - pomylka w kluczu zepsulaby
 * odtwarzanie slotow po cichu, bez sladu w logach.
 */
public final class SnapshotCodec {

    private static final String ROOT = "tombtweaks_slot_snapshots";

    private SnapshotCodec() {
    }

    public static SnapshotStore load(Player player) {
        CompoundTag persisted = PlayerData.persisted(player);
        if (!persisted.contains(ROOT, Tag.TAG_LIST)) {
            return new SnapshotStore();
        }
        return read(persisted.getList(ROOT, Tag.TAG_COMPOUND));
    }

    public static void save(Player player, SnapshotStore store) {
        PlayerData.persisted(player).put(ROOT, write(store));
    }

    static SnapshotStore read(ListTag snapshots) {
        SnapshotStore store = new SnapshotStore();
        for (int i = 0; i < snapshots.size(); i++) {
            CompoundTag tag = snapshots.getCompound(i);
            List<SlotEntry> entries = new ArrayList<>();
            ListTag seats = tag.getList("e", Tag.TAG_COMPOUND);
            for (int s = 0; s < seats.size(); s++) {
                CompoundTag seat = seats.getCompound(s);
                entries.add(new SlotEntry(
                        seat.getInt("s"),
                        new ItemKey(seat.getString("i"), seat.getInt("h")),
                        seat.getInt("n")));
            }
            store.add(new SlotSnapshot(tag.getLong("c"), entries));
        }
        return store;
    }

    static ListTag write(SnapshotStore store) {
        ListTag snapshots = new ListTag();
        for (SlotSnapshot snapshot : store.all()) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("c", snapshot.capturedAt());
            ListTag seats = new ListTag();
            for (SlotEntry entry : snapshot.entries()) {
                CompoundTag seat = new CompoundTag();
                seat.putInt("s", entry.slot());
                seat.putString("i", entry.key().id());
                seat.putInt("h", entry.key().nbtHash());
                seat.putInt("n", entry.count());
                seats.add(seat);
            }
            tag.put("e", seats);
            snapshots.add(tag);
        }
        return snapshots;
    }
}
