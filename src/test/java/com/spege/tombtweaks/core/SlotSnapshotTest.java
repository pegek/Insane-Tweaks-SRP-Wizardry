package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotSnapshotTest {

    @Test
    void itemKeyRoznicujePoNbt() {
        assertEquals(new ItemKey("minecraft:diamond_sword", 7),
                     new ItemKey("minecraft:diamond_sword", 7));
        assertNotEquals(new ItemKey("minecraft:diamond_sword", 7),
                        new ItemKey("minecraft:diamond_sword", 8));
        assertNotEquals(new ItemKey("minecraft:diamond_sword", 7),
                        new ItemKey("minecraft:iron_sword", 7));
    }

    @Test
    void pustyStackNieTrafiaDoSnapshotu() {
        SlotSnapshot snapshot = new SlotSnapshot(1000L);
        assertTrue(snapshot.isEmpty());
        assertEquals(0, snapshot.entries().size());
    }

    @Test
    void kodowanieSlotowNieKoliduje() {
        // Main 0-35 musi konczyc sie przed pancerzem, pancerz przed offhandem,
        // a offhand przed zarezerwowana przestrzenia Curios.
        assertTrue(35 < SlotSnapshot.ARMOR_BASE);
        assertTrue(SlotSnapshot.ARMOR_BASE + 3 < SlotSnapshot.OFFHAND_SLOT);
        assertTrue(SlotSnapshot.OFFHAND_SLOT < SlotSnapshot.CURIOS_BASE);
    }

    @Test
    void snapshotPamietaMiejsceIIlosc() {
        SlotSnapshot snapshot = new SlotSnapshot(1234L);
        snapshot.add(4, new ItemKey("minecraft:bread", 0), 12);
        snapshot.add(SlotSnapshot.OFFHAND_SLOT, new ItemKey("minecraft:shield", 99), 1);

        assertEquals(1234L, snapshot.capturedAt());
        assertEquals(2, snapshot.entries().size());
        assertEquals(4, snapshot.entries().get(0).slot());
        assertEquals(12, snapshot.entries().get(0).count());
        assertEquals(SlotSnapshot.OFFHAND_SLOT, snapshot.entries().get(1).slot());
    }
}
