package com.spege.tombtweaks.core;

/** Jedno zapamietane miejsce: co gdzie siedzialo w chwili smierci. */
public final class SlotEntry {

    private final int slot;
    private final ItemKey key;
    private final int count;

    public SlotEntry(int slot, ItemKey key, int count) {
        this.slot = slot;
        this.key = key;
        this.count = count;
    }

    public int slot() {
        return slot;
    }

    public ItemKey key() {
        return key;
    }

    /**
     * Ilosc w chwili smierci. Zapisywana dla diagnostyki, NIGDY nie jest kryterium dopasowania:
     * percentLossOnDeath zmniejsza stacki wewnatrz grobu, zanim ktokolwiek je zobaczy.
     */
    public int count() {
        return count;
    }
}
