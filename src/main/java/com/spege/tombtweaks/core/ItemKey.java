package com.spege.tombtweaks.core;

/**
 * Tozsamosc stacka: tyle, zeby go rozpoznac przy powrocie z grobu, i ani grama wiecej.
 *
 * <p>Na 1.12.2 kluczem bylo (nazwa, metadane, hash NBT). Na 1.20.1 metadanych nie ma,
 * a wytrzymalosc siedzi w NBT - stad para (nazwa, hash NBT) i stad tez to, ze dopasowanie
 * musi miec stopien awaryjny: serializacja stacka do grobu i z powrotem potrafi
 * znormalizowac tag, a wtedy hash sie zmienia. Patrz {@link SlotPlan}.
 *
 * <p>{@code nbtHash} musi byc stabilny miedzy restartami JVM, bo snapshoty sa zapisywane
 * miedzy sesjami. {@code CompoundTag.hashCode()} jest oparty na zawartosci i stabilny;
 * hash tozsamosciowy (identity) nie bylby.
 */
public final class ItemKey {

    private final String id;
    private final int nbtHash;

    public ItemKey(String id, int nbtHash) {
        if (id == null) {
            throw new IllegalArgumentException("id");
        }
        this.id = id;
        this.nbtHash = nbtHash;
    }

    public String id() {
        return id;
    }

    public int nbtHash() {
        return nbtHash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemKey)) {
            return false;
        }
        ItemKey that = (ItemKey) other;
        return this.nbtHash == that.nbtHash && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + nbtHash;
    }

    @Override
    public String toString() {
        return id + "#" + nbtHash;
    }
}
