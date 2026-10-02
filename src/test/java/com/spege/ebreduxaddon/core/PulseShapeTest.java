package com.spege.ebreduxaddon.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PulseShapeTest {

    @Test
    void radiusGrowsInFullPotencySteps() {
        assertEquals(8, PulseShape.radius(8, 1.0));
        assertEquals(8, PulseShape.radius(8, 1.44));
        assertEquals(10, PulseShape.radius(8, 1.45));
        assertEquals(12, PulseShape.radius(8, 1.9));
    }

    @Test
    void weakOrBrokenInput() {
        assertEquals(8, PulseShape.radius(8, 0.5));
        assertEquals(8, PulseShape.radius(8, Double.NaN));
        assertEquals(1, PulseShape.radius(0, 1.0));
        assertEquals(8 + 128, PulseShape.radius(8, 1e9));
    }

    @Test
    void verticalRangeIsHalfButAtLeastFour() {
        assertEquals(4, PulseShape.verticalRange(8));
        assertEquals(6, PulseShape.verticalRange(12));
    }
}
