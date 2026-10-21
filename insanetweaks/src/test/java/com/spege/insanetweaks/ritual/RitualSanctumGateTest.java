package com.spege.insanetweaks.ritual;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RitualSanctumGateTest {

    private static final int[] HULL = { 10, 35 };

    @Test
    public void noSanctumHere_refused() {
        assertFalse(RitualSanctumGate.allows(false, true, null, 20));
    }

    @Test
    public void notCleared_refused() {
        assertFalse(RitualSanctumGate.allows(true, false, HULL, 20));
    }

    @Test
    public void anywhereInsideHull_allowed() {
        assertTrue(RitualSanctumGate.allows(true, true, HULL, 10));
        assertTrue(RitualSanctumGate.allows(true, true, HULL, 35));
    }

    @Test
    public void aboveHull_refused() {
        // A base built on the surface over a cleared sanctum.
        assertFalse(RitualSanctumGate.allows(true, true, HULL, 70));
    }

    @Test
    public void belowHull_refused() {
        assertFalse(RitualSanctumGate.allows(true, true, HULL, 5));
    }

    @Test
    public void noHull_footprintIsEnough() {
        assertTrue(RitualSanctumGate.allows(true, true, null, 70));
    }
}
