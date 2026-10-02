package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot wydawany na jeden grob. Kazde miejsce mozna zajac tylko raz.
 *
 * <p>Dopasowanie jest trojstopniowe, bo klucz (nazwa, hash NBT) jest kruchy: stack idzie
 * do grobu przez serializacje do NBT i z powrotem, a round-trip potrafi tag znormalizowac.
 *
 * <ol>
 *   <li>dokladne - nazwa i hash NBT,</li>
 *   <li>awaryjne - sama nazwa, pierwszy jeszcze niezajety wpis,</li>
 *   <li>rezygnacja - {@link #NO_SEAT}, przedmiot zostaje w grobie i idzie sciezka standardowa.</li>
 * </ol>
 *
 * <p>Stopien trzeci jest wazniejszy, niz wyglada: to on gwarantuje, ze najgorszym przypadkiem
 * calego featura jest dzisiejsze zachowanie Tombstone'a, nigdy zgubiony przedmiot.
 */
public final class SlotPlan {

    public static final int NO_SEAT = -1;

    private final List<SlotEntry> remaining;

    public SlotPlan(SlotSnapshot snapshot) {
        this.remaining = new ArrayList<SlotEntry>(snapshot.entries());
    }

    public int claimSeat(ItemKey key) {
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).key().equals(key)) {
                return remaining.remove(i).slot();
            }
        }
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).key().id().equals(key.id())) {
                return remaining.remove(i).slot();
            }
        }
        return NO_SEAT;
    }
}
