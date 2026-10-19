package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SanctumRegionsTest {

    @Test
    public void regionOfFloorsNegatives() {
        assertEquals(0, SanctumRegions.regionOf(31, 32));
        assertEquals(-1, SanctumRegions.regionOf(-1, 32));
        assertEquals(-1, SanctumRegions.regionOf(-32, 32));
        assertEquals(-2, SanctumRegions.regionOf(-33, 32));
    }

    @Test
    public void candidatesAreDeterministic() {
        assertArrayEquals(SanctumRegions.candidate(42L, 3, -7, 32, 4, 1.0D),
                SanctumRegions.candidate(42L, 3, -7, 32, 4, 1.0D));
    }

    @Test
    public void candidatesStayInsideTheRegionMargin() {
        for (int rx = -5; rx <= 5; rx++) {
            for (int rz = -5; rz <= 5; rz++) {
                int[] c = SanctumRegions.candidate(1234L, rx, rz, 32, 4, 1.0D);
                assertNotNull(c);
                assertTrue(c[0] >= rx * 32 + 4 && c[0] <= rx * 32 + 27);
                assertTrue(c[1] >= rz * 32 + 4 && c[1] <= rz * 32 + 27);
            }
        }
    }

    @Test
    public void chanceZeroNeverPlacesAndOneAlwaysDoes() {
        for (int r = 0; r < 50; r++) {
            assertNull(SanctumRegions.candidate(99L, r, r, 32, 4, 0.0D));
            assertNotNull(SanctumRegions.candidate(99L, r, r, 32, 4, 1.0D));
        }
    }

    @Test
    public void anOversizedMarginIsClamped() {
        int[] c = SanctumRegions.candidate(7L, 0, 0, 8, 10, 1.0D);
        assertTrue(c[0] >= 0 && c[0] < 8 && c[1] >= 0 && c[1] < 8);
    }
}
