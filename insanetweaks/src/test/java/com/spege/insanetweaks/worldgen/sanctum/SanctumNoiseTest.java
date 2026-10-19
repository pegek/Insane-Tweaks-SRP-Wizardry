package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SanctumNoiseTest {

    @Test
    public void sameInputSameValue() {
        assertEquals(SanctumNoise.at(5L, 1, 2, 3, 0), SanctumNoise.at(5L, 1, 2, 3, 0), 0.0D);
    }

    @Test
    public void valuesAreInUnitRangeAndSpread() {
        double sum = 0.0D;
        int n = 0;
        for (int x = -20; x < 20; x++) {
            for (int z = -20; z < 20; z++) {
                double v = SanctumNoise.at(77L, x, 30, z, 1);
                assertTrue(v >= 0.0D && v < 1.0D);
                sum += v;
                n++;
            }
        }
        double mean = sum / n;
        assertTrue("mean " + mean, mean > 0.4D && mean < 0.6D);
    }
}
