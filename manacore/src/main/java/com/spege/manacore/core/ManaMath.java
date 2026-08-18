package com.spege.manacore.core;

/**
 * Czysta matematyka puli many. ZERO typów Minecrafta - dzięki temu ten pakiet
 * jest jedyną częścią moda, którą da się przetestować JUnitem.
 */
public final class ManaMath {

    private static final int TICKS_PER_SECOND = 20;

    private ManaMath() {
    }

    /**
     * Ogranicza wartość do zakresu [min, max]. Gdy zakres jest odwrócony (min > max),
     * zwraca min bez próby jego naprawienia.
     */
    public static double clamp(double value, double min, double max) {
        if (min > max) {
            return min;
        }
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    /**
     * Odejmuje koszt od puli, obcinając wynik do zera. Nie sprawdza wypłacalności —
     * wołający, któremu zależy na rozróżnieniu "stać mnie" od "nie stać mnie", musi
     * sam sprawdzić stan puli PRZED wywołaniem (tak robi ManaAPI.spend).
     */
    public static double afterSpend(double current, double cost) {
        double result = current - cost;
        return result < 0.0D ? 0.0D : result;
    }

    /**
     * Regen nigdy nie obniża puli: gdy `current` przekracza `max` (np. po zdjęciu bauble'a),
     * wartość zostaje bez zmian zamiast zostać obcięta.
     */
    public static double afterRegen(double current, double max, double amount) {
        if (current >= max) {
            return current;
        }
        double result = current + amount;
        return result > max ? max : result;
    }

    /**
     * Podobnie jak afterRegen, nigdy nie obniża wartości: gdy `current` już osiągnął
     * albo przekroczył `cap`, zwraca `current` bez zmian zamiast obcinać go w dół.
     */
    public static double afterProgressionGain(double current, double cap, double gain) {
        if (current >= cap) {
            return current;
        }
        double result = current + gain;
        return result > cap ? cap : result;
    }

    /**
     * Przelicza ilość many na cykl regeneracji (w sekundach) na ilość many na tick.
     * Dla niepoprawnej (niedodatniej) długości cyklu zwraca 0 zamiast rzucać wyjątek
     * albo dzielić przez zero.
     */
    public static double regenPerTick(double amountPerCycle, double cycleSeconds) {
        if (cycleSeconds <= 0.0D) {
            return 0.0D;
        }
        return amountPerCycle / (cycleSeconds * TICKS_PER_SECOND);
    }
}
