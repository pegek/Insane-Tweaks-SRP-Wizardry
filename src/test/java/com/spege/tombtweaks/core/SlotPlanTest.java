package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SlotPlanTest {

    private static SlotSnapshot snapshotWith(SlotEntry... items) {
        SlotSnapshot snapshot = new SlotSnapshot(0L);
        for (SlotEntry item : items) {
            snapshot.add(item.slot(), item.key(), item.count());
        }
        return snapshot;
    }

    @Test
    void trafienieDokladne() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(3, new ItemKey("minecraft:diamond_sword", 42), 1)));

        assertEquals(3, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 42)));
    }

    @Test
    void spadekNaDopasowaniePoNazwieGdyNbtSieRozjechalo() {
        // Round-trip przez NBT grobu potrafi znormalizowac tag i zmienic hash.
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(7, new ItemKey("minecraft:diamond_sword", 42), 1)));

        assertEquals(7, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 999)));
    }

    @Test
    void dokladneMaPierwszenstwoPrzedNazwa() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(1, new ItemKey("minecraft:diamond_sword", 11), 1),
                new SlotEntry(2, new ItemKey("minecraft:diamond_sword", 22), 1)));

        assertEquals(2, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 22)));
        assertEquals(1, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 11)));
    }

    @Test
    void dwaIdenticzneStackiDostajaDwaRozneMiejsca() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(4, new ItemKey("minecraft:bread", 0), 8),
                new SlotEntry(9, new ItemKey("minecraft:bread", 0), 8)));

        int first = plan.claimSeat(new ItemKey("minecraft:bread", 0));
        int second = plan.claimSeat(new ItemKey("minecraft:bread", 0));

        assertNotEquals(first, second);
        assertEquals(13, first + second);
    }

    @Test
    void brakDopasowaniaToRezygnacja() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(4, new ItemKey("minecraft:bread", 0), 8)));

        assertEquals(SlotPlan.NO_SEAT, plan.claimSeat(new ItemKey("minecraft:stone", 0)));
    }

    @Test
    void miejsceZuzywaSieRaz() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(4, new ItemKey("minecraft:bread", 0), 8)));

        assertEquals(4, plan.claimSeat(new ItemKey("minecraft:bread", 0)));
        assertEquals(SlotPlan.NO_SEAT, plan.claimSeat(new ItemKey("minecraft:bread", 0)));
    }
}
