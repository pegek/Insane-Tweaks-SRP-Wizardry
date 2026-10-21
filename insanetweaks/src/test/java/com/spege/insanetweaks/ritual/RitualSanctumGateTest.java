package com.spege.insanetweaks.ritual;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RitualSanctumGateTest {

    private static final int[] DAIS = { 100, 20, -40 };
    private static final int[] HULL = { 10, 35 };

    @Test
    public void noSanctumHere_refused() {
        assertFalse(RitualSanctumGate.allows(false, true, null, null, 100, 20, -40));
    }

    @Test
    public void notCleared_refused() {
        assertFalse(RitualSanctumGate.allows(true, false, DAIS, HULL, 100, 20, -40));
    }

    @Test
    public void recordedDais_runsOnItsCentre() {
        assertTrue(RitualSanctumGate.allows(true, true, DAIS, HULL, 100, 20, -40));
    }

    @Test
    public void recordedDais_refusesAnyOtherCentre() {
        assertFalse(RitualSanctumGate.allows(true, true, DAIS, HULL, 101, 20, -40));
        assertFalse(RitualSanctumGate.allows(true, true, DAIS, HULL, 100, 21, -40));
    }

    @Test
    public void noDais_insideHull_allowed() {
        assertTrue(RitualSanctumGate.allows(true, true, null, HULL, 90, 25, -30));
    }

    @Test
    public void noDais_aboveHull_refused() {
        // A base built on the surface over a cleared sanctum.
        assertFalse(RitualSanctumGate.allows(true, true, null, HULL, 90, 70, -30));
    }

    @Test
    public void noDaisNoHull_footprintIsEnough() {
        assertTrue(RitualSanctumGate.allows(true, true, null, null, 90, 70, -30));
    }
}
