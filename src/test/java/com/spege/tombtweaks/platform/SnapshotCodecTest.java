package com.spege.tombtweaks.platform;

import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.SlotEntry;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SnapshotCodecTest {

    @Test
    void roundTripZachowujeWszystkiePola() {
        SnapshotStore store = new SnapshotStore();
        SlotSnapshot first = new SlotSnapshot(1_700_000_000_123L);
        first.add(4, new ItemKey("minecraft:bread", 0), 12);
        first.add(SlotSnapshot.OFFHAND_SLOT, new ItemKey("minecraft:shield", -987654321), 1);
        store.add(first);
        SlotSnapshot second = new SlotSnapshot(42L);
        second.add(SlotSnapshot.ARMOR_BASE + 3, new ItemKey("minecraft:diamond_helmet", 7), 1);
        store.add(second);

        SnapshotStore back = SnapshotCodec.read(SnapshotCodec.write(store));

        assertEquals(2, back.all().size());
        SlotSnapshot b1 = back.all().get(0);
        assertEquals(1_700_000_000_123L, b1.capturedAt());
        assertEquals(2, b1.entries().size());
        SlotEntry bread = b1.entries().get(0);
        assertEquals(4, bread.slot());
        assertEquals(new ItemKey("minecraft:bread", 0), bread.key());
        assertEquals(12, bread.count());
        SlotEntry shield = b1.entries().get(1);
        assertEquals(SlotSnapshot.OFFHAND_SLOT, shield.slot());
        assertEquals(new ItemKey("minecraft:shield", -987654321), shield.key());
        assertEquals(1, shield.count());
        SlotSnapshot b2 = back.all().get(1);
        assertEquals(42L, b2.capturedAt());
        assertEquals(SlotSnapshot.ARMOR_BASE + 3, b2.entries().get(0).slot());
    }

    @Test
    void pustyMagazynToPustaLista() {
        assertEquals(0, SnapshotCodec.write(new SnapshotStore()).size());
        assertEquals(0, SnapshotCodec.read(SnapshotCodec.write(new SnapshotStore())).all().size());
    }
}
