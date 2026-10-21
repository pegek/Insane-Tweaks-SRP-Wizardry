package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class SanctumVariantsTest {

    @Test
    public void poolsPerDimension() {
        assertArrayEquals(new String[] { "sorcery", "lightning" }, SanctumVariants.pool(0));
        assertArrayEquals(new String[] { "earth" }, SanctumVariants.pool(111));
        assertArrayEquals(new String[] { "fire" }, SanctumVariants.pool(-1));
        assertArrayEquals(new String[] { "necromancy" }, SanctumVariants.pool(1));
        assertArrayEquals(SanctumVariants.pool(0), SanctumVariants.pool(42));
    }

    @Test
    public void elementIsDeterministicAndInPool() {
        for (long seed = 0; seed < 1000; seed++) {
            for (int dim : new int[] { 0, 111, -1, 1, 7 }) {
                String e = SanctumVariants.element(dim, seed);
                assertEquals(e, SanctumVariants.element(dim, seed));
                assertTrue(dim + " " + e, Arrays.asList(SanctumVariants.pool(dim)).contains(e));
            }
        }
    }

    @Test
    public void overworldSeesBothElements() {
        Set<String> seen = new HashSet<String>();
        for (long seed = 0; seed < 200; seed++) {
            seen.add(SanctumVariants.element(0, seed));
        }
        assertEquals(2, seen.size());
    }

    /** Runestone has no magic variant, and Abomination is not a runestone element. */
    @Test
    public void noMagicNoAbomination() {
        for (int dim : new int[] { 0, 111, -1, 1 }) {
            for (String e : SanctumVariants.pool(dim)) {
                assertFalse(e.equals("magic") || e.equals("abomination"));
            }
        }
    }
}
