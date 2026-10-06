package com.spege.insanetweaks.entities.sapling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SaplingDropPenaltyTest {

    @Test
    public void aDropSurvivesWhenTheRollClearsThePenalty() {
        assertTrue(SaplingDropPenalty.keepsDrop(0.5D, 0.5D));
        assertTrue(SaplingDropPenalty.keepsDrop(0.9D, 0.5D));
    }

    @Test
    public void aDropIsLostWhenTheRollFallsUnderThePenalty() {
        assertFalse(SaplingDropPenalty.keepsDrop(0.49D, 0.5D));
    }

    @Test
    public void zeroPenaltyKeepsEverythingAndFullPenaltyKeepsNothing() {
        assertTrue(SaplingDropPenalty.keepsDrop(0.0D, 0.0D));
        assertFalse(SaplingDropPenalty.keepsDrop(0.999D, 1.0D));
    }

    @Test
    public void experienceIsReducedByThePenalty() {
        assertEquals(5, SaplingDropPenalty.reducedXp(10, 0.5D, 0.99D));
        assertEquals(10, SaplingDropPenalty.reducedXp(10, 0.0D, 0.0D));
        assertEquals(0, SaplingDropPenalty.reducedXp(10, 1.0D, 0.0D));
    }

    @Test
    public void aFractionalRemainderIsAwardedByChance() {
        // 7 * 0.5 = 3.5 -> 3, plus 1 when the roll lands under the 0.5 remainder
        assertEquals(4, SaplingDropPenalty.reducedXp(7, 0.5D, 0.2D));
        assertEquals(3, SaplingDropPenalty.reducedXp(7, 0.5D, 0.8D));
    }
}
