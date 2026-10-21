package com.spege.insanetweaks.ritual;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RitualReceptaclesTest {

    private static final int ABOMINATION = 8;

    @Test
    public void allFourAbomination_isLit() {
        assertTrue(RitualReceptacles.lit(new int[] { 8, 8, 8, 8 }, ABOMINATION));
    }

    @Test
    public void oneAbominationThreeOthers_isLit() {
        assertTrue(RitualReceptacles.lit(new int[] { 1, 8, 3, 5 }, ABOMINATION));
    }

    @Test
    public void noAbomination_isNotLit() {
        assertFalse(RitualReceptacles.lit(new int[] { 1, 2, 3, 4 }, ABOMINATION));
    }

    @Test
    public void oneDark_isNotLit() {
        assertFalse(RitualReceptacles.lit(new int[] { 8, RitualReceptacles.DARK, 3, 5 }, ABOMINATION));
    }

    @Test
    public void wrongLengthOrNull_isNotLit() {
        assertFalse(RitualReceptacles.lit(null, ABOMINATION));
        assertFalse(RitualReceptacles.lit(new int[] { 8, 8, 8 }, ABOMINATION));
    }

    @Test
    public void elementMissing_isNotLit() {
        assertFalse(RitualReceptacles.lit(new int[] { 8, 8, 8, 8 }, -1));
    }
}
