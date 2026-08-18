package com.spege.manacore.core;

/**
 * Pure mana pool math. ZERO Minecraft types - this is what makes this package
 * the only part of the mod that can be unit-tested with JUnit.
 */
public final class ManaMath {

    private static final int TICKS_PER_SECOND = 20;

    private ManaMath() {
    }

    /**
     * Clamps a value to the range [min, max]. When the range is inverted (min > max),
     * returns min without trying to fix it.
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
     * Subtracts a cost from the pool, clamping the result to zero. Does not check
     * affordability — a caller that cares about distinguishing "can afford it" from
     * "can't afford it" must check the pool state itself BEFORE calling this (that is
     * what ManaAPI.spend does).
     */
    public static double afterSpend(double current, double cost) {
        double result = current - cost;
        return result < 0.0D ? 0.0D : result;
    }

    /**
     * Regen never lowers the pool: when `current` already exceeds `max` (e.g. after
     * unequipping a bauble), the value is left unchanged instead of being clamped down.
     */
    public static double afterRegen(double current, double max, double amount) {
        if (current >= max) {
            return current;
        }
        double result = current + amount;
        return result > max ? max : result;
    }

    /**
     * Like afterRegen, this never lowers the value: when `current` has already reached
     * or exceeded `cap`, it returns `current` unchanged instead of clamping it down.
     */
    public static double afterProgressionGain(double current, double cap, double gain) {
        if (current >= cap) {
            return current;
        }
        double result = current + gain;
        return result > cap ? cap : result;
    }

    /**
     * Converts an amount of mana per regen cycle (in seconds) into an amount of mana
     * per tick. For an invalid (non-positive) cycle length, returns 0 instead of
     * throwing an exception or dividing by zero.
     */
    public static double regenPerTick(double amountPerCycle, double cycleSeconds) {
        if (cycleSeconds <= 0.0D) {
            return 0.0D;
        }
        return amountPerCycle / (cycleSeconds * TICKS_PER_SECOND);
    }
}
