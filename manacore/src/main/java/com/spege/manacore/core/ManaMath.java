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
     * Regen enforces the ceiling in BOTH directions: a pool that already exceeds `max` is
     * clamped back down to it, not left alone. That matters when the maximum drops - taking
     * off a bauble that granted bonus maximum - and it is what makes bonus maximum a pure
     * cap raise: putting the bauble on does not hand out the mana, taking it off does not
     * let the player keep it. The mana has to be regenerated.
     *
     * <p>The floor at zero also handles a negative `max`, which cannot occur through
     * ManaAttributes.getMaxMana (it clamps) but is cheap to be right about here.
     */
    public static double afterRegen(double current, double max, double amount) {
        return clamp(current + amount, 0.0D, max);
    }

    /**
     * Unlike afterRegen, this never lowers the value: when `current` has already reached
     * or exceeded `cap`, it returns `current` unchanged instead of clamping it down.
     *
     * <p>The two differ on purpose. Exceeding the regen ceiling means holding mana the
     * player is no longer entitled to, so it is taken back. Exceeding a progression cap
     * means an admin lowered the cap in the config after the player had already earned the
     * progress - taking that away retroactively would be punishing them for someone else's
     * config edit, so the banked amount stays and simply stops growing.
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
