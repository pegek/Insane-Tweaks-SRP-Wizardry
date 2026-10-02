package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plan miejsc z jednej smierci - nie kopia przedmiotow.
 *
 * <p>Pelny zserializowany ekwipunek w danych gracza to dziesiatki kilobajtow przepisywane
 * przy kazdym autosave i, co gorsza, druga instancja prawdy o tym, co gracz posiadal.
 * Grob jest jedyna instancja prawdy; to jest wylacznie rozsadzenie gosci.
 */
public final class SlotSnapshot {

    /** Main inventory zachowuje wlasne indeksy 0-35. */
    public static final int ARMOR_BASE = 100;
    public static final int OFFHAND_SLOT = 150;
    /** Zarezerwowane pod Curios. Nieuzywane w v1. */
    public static final int CURIOS_BASE = 200;

    private final long capturedAt;
    private final List<SlotEntry> entries;

    public SlotSnapshot(long capturedAt) {
        this(capturedAt, new ArrayList<SlotEntry>());
    }

    public SlotSnapshot(long capturedAt, List<SlotEntry> entries) {
        this.capturedAt = capturedAt;
        this.entries = entries;
    }

    public void add(int slot, ItemKey key, int count) {
        entries.add(new SlotEntry(slot, key, count));
    }

    /** Zegar scienny w milisekundach - ta sama skala co deathDate grobu. */
    public long capturedAt() {
        return capturedAt;
    }

    public List<SlotEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
