package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshoty oczekujace jednego gracza.
 *
 * <p>Snapshot powstaje przy smierci, czyli zanim grob istnieje - musi wiec zostac skojarzony
 * z konkretnym grobem dopiero przy odzyskiwaniu. Wiazanie idzie po czasie zegarowym:
 * {@code capturedAt} snapshotu przeciw {@code getOwnerDeathTime()} grobu, ktore Tombstone
 * ustawia z {@code System.currentTimeMillis()} - ta sama skala.
 *
 * <p>Tombstone SCALA groby: druga smierc w promieniu 20 blokow od nieodebranego grobu
 * dorzuca przedmioty do niego i przestawia jego date smierci na najnowsza. Taki grob wiaze
 * sie wiec tylko ze snapshotem ostatniej smierci. Starszy snapshot zostaje oczekujacy, az
 * {@link #prune} go usunie, a przedmioty, ktore opisywal, przechodza sciezka standardowa
 * Tombstone.
 *
 * <p>Dopasowany snapshot jest ZUZYWANY, zeby gracz z kilkoma nieodwiedzonymi grobami dostal
 * z kazdego jego wlasne rozsadzenie, a nie rozsadzenie z ostatniej smierci.
 */
public final class SnapshotStore {

    private final List<SlotSnapshot> pending = new ArrayList<SlotSnapshot>();

    public void add(SlotSnapshot snapshot) {
        pending.add(snapshot);
    }

    public List<SlotSnapshot> all() {
        return Collections.unmodifiableList(pending);
    }

    /**
     * Najblizszy czasowo snapshot, usuniety z magazynu. Przy remisie wygrywa snapshot dodany
     * wczesniej.
     *
     * @return null gdy nic nie miesci sie w tolerancji - wtedy sciezka standardowa
     */
    public SlotSnapshot claimNearest(long graveDeathTime, long toleranceMillis) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            long distance = Math.abs(pending.get(i).capturedAt() - graveDeathTime);
            if (distance < 0) {
                continue; // overflow przy skrajnych wartosciach (uszkodzone NBT)
            }
            if (distance <= toleranceMillis && distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best < 0 ? null : pending.remove(best);
    }

    /**
     * Przycina z dwoch stron: liczba sztuk i wiek. Bez tego jedna smierc bez powrotu po grob
     * zostawialaby wpis na zawsze.
     */
    public void prune(long nowMillis, int maxEntries, long maxAgeMillis) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            if (nowMillis - pending.get(i).capturedAt() > maxAgeMillis) {
                pending.remove(i);
            }
        }
        while (!pending.isEmpty() && pending.size() > maxEntries) {
            int oldest = 0;
            for (int i = 1; i < pending.size(); i++) {
                if (pending.get(i).capturedAt() < pending.get(oldest).capturedAt()) {
                    oldest = i;
                }
            }
            pending.remove(oldest);
        }
    }
}
