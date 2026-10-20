package com.spege.insanetweaks.enchant;

import static com.spege.insanetweaks.enchant.WitsHungerMath.NONE;
import static com.spege.insanetweaks.enchant.WitsHungerMath.attackDamageModifier;
import static com.spege.insanetweaks.enchant.WitsHungerMath.attackSpeedModifier;
import static com.spege.insanetweaks.enchant.WitsHungerMath.digSpeedMultiplier;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Wit's Hunger level N weakens like vanilla Weakness N + Mining Fatigue N, and never stacks with the
 * real effects: whichever is stronger counts. These tests pin "the stronger counts" as a difference -
 * the curse only adds what is missing on top of what the vanilla effect already does.
 */
public class WitsHungerMathTest {

    private static final double EPS = 1e-6;

    @Test
    public void attackDamageAloneMatchesVanillaWeakness() {
        assertEquals(-4.0, attackDamageModifier(0, NONE), EPS);   // Weakness I
        assertEquals(-16.0, attackDamageModifier(3, NONE), EPS);  // Weakness IV
    }

    @Test
    public void attackDamageOnlyTopsUpAWeakerRealWeakness() {
        assertEquals(-8.0, attackDamageModifier(3, 1), EPS);  // real II (-8) + ours -8 = IV (-16)
        assertEquals(0.0, attackDamageModifier(1, 3), EPS);   // real IV is stronger: ours adds nothing
        assertEquals(0.0, attackDamageModifier(2, 2), EPS);
    }

    @Test
    public void attackSpeedAloneMatchesVanillaMiningFatigue() {
        assertEquals(-0.1, attackSpeedModifier(0, NONE), EPS);
        assertEquals(-0.4, attackSpeedModifier(3, NONE), EPS);
    }

    @Test
    public void attackSpeedMultipliesUpToTheStrongerOnly() {
        // real Fatigue I leaves 0.9; ours must bring it to Fatigue II's 0.8: 0.9 * (1 + x) = 0.8
        assertEquals(0.8 / 0.9 - 1.0, attackSpeedModifier(1, 0), EPS);
        assertEquals(0.0, attackSpeedModifier(0, 2), EPS);
    }

    @Test
    public void attackSpeedNeverDividesByZeroAtHugeLevels() {
        assertEquals(-1.0, attackSpeedModifier(19, NONE), EPS); // factor clamps at 0: no swing left
        assertEquals(0.0, attackSpeedModifier(19, 10), EPS);    // real effect already at the floor
    }

    @Test
    public void digSpeedAloneMatchesVanillaMiningFatigue() {
        assertEquals(0.3f, digSpeedMultiplier(0, NONE), EPS);
        assertEquals(0.09f, digSpeedMultiplier(1, NONE), EPS);
        assertEquals(0.0027f, digSpeedMultiplier(2, NONE), EPS);
        assertEquals(8.1e-4f, digSpeedMultiplier(3, NONE), EPS);
    }

    @Test
    public void digSpeedOnlyTopsUpAWeakerRealFatigue() {
        assertEquals(0.0027f / 0.3f, digSpeedMultiplier(2, 0), 1e-4);
        assertEquals(1.0f, digSpeedMultiplier(0, 1), EPS);
        assertEquals(1.0f, digSpeedMultiplier(NONE, NONE), EPS);
    }
}
