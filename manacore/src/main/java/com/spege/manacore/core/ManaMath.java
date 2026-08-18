package com.spege.manacore.core;

/**
 * Czysta matematyka puli many. ZERO typów Minecrafta - dzięki temu ten pakiet
 * jest jedyną częścią moda, którą da się przetestować JUnitem.
 */
public final class ManaMath {

    public static final int TICKS_PER_SECOND = 20;

    private ManaMath() {
    }

    public static double clamp(double value, double min, double max) {
        if (min > max) {
            return min;
        }
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

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

    public static double afterProgressionGain(double current, double gain, double cap) {
        double result = current + gain;
        return result > cap ? cap : result;
    }

    public static double regenPerTick(double amountPerCycle, double cycleSeconds) {
        if (cycleSeconds <= 0.0D) {
            return 0.0D;
        }
        return amountPerCycle / (cycleSeconds * TICKS_PER_SECOND);
    }
}
