package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SlotPlanTest {

    private static final IntPredicate ALL_FREE = slot -> true;
    private static final int NO = SlotPlan.NO_SEAT;

    private static SlotSnapshot snapshotWith(SlotEntry... items) {
        SlotSnapshot snapshot = new SlotSnapshot(0L);
        for (SlotEntry item : items) {
            snapshot.add(item.slot(), item.key(), item.count());
        }
        return snapshot;
    }

    private static ItemKey sword(int hash) {
        return new ItemKey("minecraft:diamond_sword", hash);
    }

    private static ItemKey bread() {
        return new ItemKey("minecraft:bread", 0);
    }

    @Test
    void trafienieDokladne() {
        SlotSnapshot s = snapshotWith(new SlotEntry(3, sword(42), 1));
        assertArrayEquals(new int[] { 3 }, SlotPlan.assign(s, Arrays.asList(sword(42)), ALL_FREE));
    }

    @Test
    void spadekNaDopasowaniePoNazwieGdyNbtSieRozjechalo() {
        SlotSnapshot s = snapshotWith(new SlotEntry(7, sword(42), 1));
        assertArrayEquals(new int[] { 7 }, SlotPlan.assign(s, Arrays.asList(sword(999)), ALL_FREE));
    }

    @Test
    void dokladneMaPierwszenstwoPrzedNazwa() {
        SlotSnapshot s = snapshotWith(new SlotEntry(1, sword(11), 1), new SlotEntry(2, sword(22), 1));
        assertArrayEquals(new int[] { 2, 1 },
                SlotPlan.assign(s, Arrays.asList(sword(22), sword(11)), ALL_FREE));
    }

    @Test
    void dwaIdentyczneStackiDostajaDwaRozneMiejsca() {
        SlotSnapshot s = snapshotWith(new SlotEntry(4, bread(), 8), new SlotEntry(9, bread(), 8));
        int[] seats = SlotPlan.assign(s, Arrays.asList(bread(), bread()), ALL_FREE);
        assertNotEquals(seats[0], seats[1]);
        assertEquals(13, seats[0] + seats[1]);
    }

    @Test
    void brakDopasowaniaToRezygnacja() {
        SlotSnapshot s = snapshotWith(new SlotEntry(4, bread(), 8));
        assertArrayEquals(new int[] { NO },
                SlotPlan.assign(s, Arrays.asList(new ItemKey("minecraft:stone", 0)), ALL_FREE));
    }

    @Test
    void miejsceZuzywaSieRaz() {
        SlotSnapshot s = snapshotWith(new SlotEntry(4, bread(), 8));
        assertArrayEquals(new int[] { 4, NO }, SlotPlan.assign(s, Arrays.asList(bread(), bread()), ALL_FREE));
    }

    @Test
    void dryfNieKradnieMiejscaDokladnemu() {
        // Miecz z miejsca 2 ma rozjechany hash (2 -> 99) i stoi w grobie PRZED mieczem z miejsca 1.
        // Zachlannie zabralby miejsce 1 i oba miecze zamienilyby sie slotami.
        SlotSnapshot s = snapshotWith(new SlotEntry(1, sword(1), 1), new SlotEntry(2, sword(2), 1));
        assertArrayEquals(new int[] { 2, 1 },
                SlotPlan.assign(s, Arrays.asList(sword(99), sword(1)), ALL_FREE));
    }

    @Test
    void scalonyGrobNieWpychaStaregoPrzedmiotu() {
        // Grob scalony z dwoch smierci: pierwszy miecz (hash 77) pochodzi ze smierci, ktorej
        // snapshot nie zostal zwiazany z tym grobem. Ma zostac w grobie, a oba miecze
        // z wiazanego snapshotu - trafic na swoje miejsca.
        SlotSnapshot s = snapshotWith(new SlotEntry(1, sword(1), 1), new SlotEntry(2, sword(2), 1));
        assertArrayEquals(new int[] { NO, 1, 2 },
                SlotPlan.assign(s, Arrays.asList(sword(77), sword(1), sword(2)), ALL_FREE));
    }

    @Test
    void zajeteMiejsceNieBierzeUdzialuWDopasowaniu() {
        // Miejsce 1 jest zajete (np. przedmiot soulbound nigdy nie trafil do grobu).
        // Miecz z rozjechanym hashem ma dostac miejsce 2, a nie bezuzyteczne miejsce 1.
        SlotSnapshot s = snapshotWith(new SlotEntry(1, sword(1), 1), new SlotEntry(2, sword(2), 1));
        assertArrayEquals(new int[] { 2 },
                SlotPlan.assign(s, Arrays.asList(sword(99)), slot -> slot != 1));
    }

    @Test
    void pustySlotGrobuNieDostajeMiejsca() {
        SlotSnapshot s = snapshotWith(new SlotEntry(4, bread(), 8));
        assertArrayEquals(new int[] { NO, 4 }, SlotPlan.assign(s, Arrays.asList(null, bread()), ALL_FREE));
    }
}
