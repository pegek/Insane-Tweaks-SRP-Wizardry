package com.spege.tombtweaks.core;

/**
 * Kiedy grob traci nastepny przedmiot.
 *
 * <p>Czysta funkcja licznika {@code countTicks} samego grobu, ktory Tombstone zapisuje do NBT
 * i wczytuje przy zaladowaniu chunka. Dzieki temu harmonogram nie potrzebuje wlasnego stanu
 * i przezywa restart serwera oraz wyladowanie chunka.
 *
 * <p>Uwaga na semantyke: {@code countTicks} rosnie tylko wtedy, gdy grob jest zaladowany,
 * wiec {@code startTicks} liczy czas ZALADOWANIA, nie czas zegarowy.
 */
public final class DecaySchedule {

    private DecaySchedule() {
    }

    public static boolean shouldDecay(long countTicks, long startTicks, long intervalTicks) {
        if (intervalTicks <= 0L) {
            return false;
        }
        long start = Math.max(0L, startTicks);
        if (countTicks < start) {
            return false;
        }
        return (countTicks - start) % intervalTicks == 0L;
    }
}
