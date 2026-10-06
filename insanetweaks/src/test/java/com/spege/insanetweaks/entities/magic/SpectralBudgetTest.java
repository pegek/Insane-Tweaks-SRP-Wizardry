package com.spege.insanetweaks.entities.magic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpectralBudgetTest {

    @Test
    public void theRollPicksWithinMinAndMax() {
        assertEquals(2, SpectralBudget.toSummon(2, 3, 0.0D, 0, 4));
        assertEquals(2, SpectralBudget.toSummon(2, 3, 0.49D, 0, 4));
        assertEquals(3, SpectralBudget.toSummon(2, 3, 0.5D, 0, 4));
        assertEquals(3, SpectralBudget.toSummon(2, 3, 0.999D, 0, 4));
    }

    @Test
    public void theLimitCapsTheCount() {
        assertEquals(1, SpectralBudget.toSummon(2, 3, 0.9D, 3, 4));
        assertEquals(0, SpectralBudget.toSummon(2, 3, 0.9D, 4, 4));
        assertEquals(0, SpectralBudget.toSummon(2, 3, 0.9D, 6, 4));
    }

    @Test
    public void swappedBoundsAreTolerated() {
        assertEquals(2, SpectralBudget.toSummon(3, 2, 0.0D, 0, 4));
    }
}
