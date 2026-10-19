package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SanctumBoxTest {

    @Test
    public void cornersAreNormalised() {
        SanctumBox b = new SanctumBox(5, 6, 7, 1, 2, 3);
        assertEquals(1, b.minX);
        assertEquals(7, b.maxZ);
    }

    @Test
    public void touchingBoxesIntersect() {
        assertTrue(new SanctumBox(0, 0, 0, 4, 4, 4).intersects(new SanctumBox(4, 4, 4, 8, 8, 8)));
        assertFalse(new SanctumBox(0, 0, 0, 4, 4, 4).intersects(new SanctumBox(5, 0, 0, 8, 4, 4)));
    }

    @Test
    public void intersectionClipsOrIsNull() {
        SanctumBox c = new SanctumBox(0, 0, 0, 10, 10, 10).intersection(new SanctumBox(8, 2, -5, 20, 3, 2));
        assertEquals(new SanctumBox(8, 2, 0, 10, 3, 2), c);
        assertNull(new SanctumBox(0, 0, 0, 1, 1, 1).intersection(new SanctumBox(3, 3, 3, 4, 4, 4)));
    }

    @Test
    public void shrinkGivesTheInteriorOrNull() {
        assertEquals(new SanctumBox(1, 1, 1, 3, 3, 3), new SanctumBox(0, 0, 0, 4, 4, 4).shrink(1));
        assertNull(new SanctumBox(0, 0, 0, 1, 4, 4).shrink(1));
    }

    @Test
    public void containsIsInclusive() {
        SanctumBox b = new SanctumBox(0, 0, 0, 2, 2, 2);
        assertTrue(b.contains(2, 2, 2));
        assertFalse(b.contains(3, 2, 2));
    }
}
