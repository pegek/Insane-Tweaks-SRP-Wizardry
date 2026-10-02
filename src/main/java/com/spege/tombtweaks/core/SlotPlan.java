package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Rozsadza zawartosc jednego grobu wedlug snapshotu - dla calego grobu naraz.
 *
 * <p>Dopasowanie ma dwa przebiegi po CALYM grobie, nie po jednym przedmiocie:
 * <ol>
 *   <li>dokladny - nazwa i hash NBT,</li>
 *   <li>awaryjny - sama nazwa, tylko dla przedmiotow, ktore nie trafily w pierwszym.</li>
 * </ol>
 * Klucz jest kruchy (round-trip stacka przez NBT grobu potrafi znormalizowac tag i zmienic
 * hash), stad przebieg drugi. Kolejnosc przebiegow jest istotna: dopasowanie zachlanne,
 * przedmiot po przedmiocie, pozwalalo przedmiotowi z rozjechanym hashem zajac miejsce,
 * ktore pozniejszy przedmiot trafial dokladnie.
 *
 * <p>Ma to znaczenie w praktyce, bo Tombstone SCALA groby: druga smierc w promieniu 20 blokow
 * od nieodebranego grobu dorzuca przedmioty do niego i przestawia jego date smierci na
 * najnowsza. Taki grob wiaze sie tylko ze snapshotem ostatniej smierci, a zawiera tez
 * przedmioty z poprzedniej, ktorych ten snapshot nie opisuje.
 *
 * <p>Miejsca zajete u gracza nie biora udzialu w dopasowaniu wcale - inaczej przedmiot
 * dostalby miejsce, ktorego nie da sie uzyc, i nie sprobowalby zadnego innego.
 *
 * <p>{@link #NO_SEAT} niczego sam nie gwarantuje. Gwarancja "najgorszy przypadek = zachowanie
 * standardowe, nigdy zgubiony ani zdublowany przedmiot" pochodzi od WOLAJACEGO: musi
 * wstawiac wylacznie do pustego slotu i sprawdzic to ponownie tuz przed wstawieniem.
 */
public final class SlotPlan {

    public static final int NO_SEAT = -1;

    private SlotPlan() {
    }

    /**
     * @param snapshot  rozsadzenie z chwili smierci
     * @param graveKeys klucz kazdego slotu grobu, w kolejnosci grobu; null = slot pusty
     * @param seatFree  czy miejsce u gracza jest wolne; zajete nie biora udzialu w dopasowaniu
     * @return seats[i] = miejsce dla graveKeys.get(i), albo {@link #NO_SEAT}
     */
    public static int[] assign(SlotSnapshot snapshot, List<ItemKey> graveKeys, IntPredicate seatFree) {
        List<SlotEntry> free = new ArrayList<SlotEntry>();
        for (SlotEntry entry : snapshot.entries()) {
            if (seatFree.test(entry.slot())) {
                free.add(entry);
            }
        }

        int[] seats = new int[graveKeys.size()];
        Arrays.fill(seats, NO_SEAT);

        for (int i = 0; i < seats.length; i++) {
            ItemKey key = graveKeys.get(i);
            if (key == null) {
                continue;
            }
            for (int j = 0; j < free.size(); j++) {
                if (free.get(j).key().equals(key)) {
                    seats[i] = free.remove(j).slot();
                    break;
                }
            }
        }

        for (int i = 0; i < seats.length; i++) {
            ItemKey key = graveKeys.get(i);
            if (key == null || seats[i] != NO_SEAT) {
                continue;
            }
            for (int j = 0; j < free.size(); j++) {
                if (free.get(j).key().id().equals(key.id())) {
                    seats[i] = free.remove(j).slot();
                    break;
                }
            }
        }

        return seats;
    }
}
