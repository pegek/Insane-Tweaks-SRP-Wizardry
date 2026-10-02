package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SymbiosisCurveTest {

    @Test
    void progressIsLinearAndClamped() {
        assertEquals(0.0, SymbiosisCurve.progress(0, 6000));
        assertEquals(0.5, SymbiosisCurve.progress(3000, 6000), 1e-9);
        assertEquals(1.0, SymbiosisCurve.progress(6000, 6000));
        assertEquals(1.0, SymbiosisCurve.progress(Long.MAX_VALUE, 6000));
        assertEquals(0.0, SymbiosisCurve.progress(-5, 6000));
    }

    @Test
    void nonPositiveThresholdMeansNoProgress() {
        assertEquals(0.0, SymbiosisCurve.progress(100, 0));
        assertEquals(0.0, SymbiosisCurve.progress(100, -1));
    }

    @Test
    void costMultiplierFollowsSpecTable() {
        assertEquals(1.0, SymbiosisCurve.costMultiplier(0.0, 0.15), 1e-9);
        assertEquals(0.925, SymbiosisCurve.costMultiplier(0.5, 0.15), 1e-9);
        assertEquals(0.85, SymbiosisCurve.costMultiplier(1.0, 0.15), 1e-9);
    }

    @Test
    void brokenConfigNeverMakesSpellsFreeOrNegative() {
        assertEquals(0.1, SymbiosisCurve.costMultiplier(1.0, 5.0), 1e-9);
        assertEquals(1.0, SymbiosisCurve.costMultiplier(1.0, -1.0), 1e-9);
        assertEquals(1.0, SymbiosisCurve.costMultiplier(1.0, Double.NaN), 1e-9);
        assertEquals(1.0, SymbiosisCurve.costMultiplier(Double.NaN, 0.15), 1e-9);
        assertEquals(1.0, SymbiosisCurve.durationMultiplier(1.0, -3.0), 1e-9);
    }

    @Test
    void durationMultiplierGrows() {
        assertEquals(1.125, SymbiosisCurve.durationMultiplier(0.5, 0.25), 1e-9);
        assertEquals(1.25, SymbiosisCurve.durationMultiplier(2.0, 0.25), 1e-9);
    }
}
