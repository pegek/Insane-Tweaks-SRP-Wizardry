package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressTest {

    @Test
    void addFloorsFractionsAndIgnoresBadInput() {
        assertEquals(17, Progress.add(10, 7.9));
        assertEquals(10, Progress.add(10, -4));
        assertEquals(10, Progress.add(10, Double.NaN));
        assertEquals(3, Progress.add(-50, 3));
    }

    @Test
    void addSaturates() {
        assertEquals(Long.MAX_VALUE, Progress.add(Long.MAX_VALUE - 1, 1e30));
        assertEquals(Long.MAX_VALUE, Progress.add(Long.MAX_VALUE, Double.POSITIVE_INFINITY));
    }

    @Test
    void evolveAtThreshold() {
        assertFalse(Progress.shouldEvolve(5999, 6000));
        assertTrue(Progress.shouldEvolve(6000, 6000));
        assertFalse(Progress.shouldEvolve(1_000_000, 0));
    }

    @Test
    void shareSplitsBetweenWornPieces() {
        assertEquals(2.5, Progress.share(10, 4), 1e-9);
        assertEquals(0.0, Progress.share(10, 0));
        assertEquals(0.0, Progress.share(-3, 2));
        assertEquals(0.0, Progress.share(Double.POSITIVE_INFINITY, 2));
    }
}
