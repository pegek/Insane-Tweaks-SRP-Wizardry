package com.spege.insanetweaks.anchor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnchorPoolTest {

    @Test
    public void equalWeightsSplitTheRollEvenly() {
        AnchorPool pool = AnchorPool.parse(new String[] { "a:x;1", "a:y;1" });
        assertEquals("a:x", pool.pick(0.0D));
        assertEquals("a:x", pool.pick(0.49D));
        assertEquals("a:y", pool.pick(0.5D));
        assertEquals("a:y", pool.pick(0.999D));
    }

    @Test
    public void heavierEntriesTakeMoreOfTheRoll() {
        AnchorPool pool = AnchorPool.parse(new String[] { "a:x;3", "a:y;1" });
        assertEquals("a:x", pool.pick(0.74D));
        assertEquals("a:y", pool.pick(0.75D));
    }

    @Test
    public void aMissingWeightMeansOne() {
        AnchorPool pool = AnchorPool.parse(new String[] { "a:x", "a:y;1" });
        assertEquals("a:x", pool.pick(0.2D));
        assertEquals("a:y", pool.pick(0.7D));
    }

    @Test
    public void malformedAndNonPositiveEntriesAreRejected() {
        AnchorPool pool = AnchorPool.parse(new String[] { "a:x;0", "a:y;-2", "a:z;abc", " ", "a:w;2" });
        assertEquals("a:w", pool.pick(0.0D));
        assertEquals("a:w", pool.pick(0.99D));
        assertEquals(3, pool.rejected().size());
    }

    @Test
    public void anEmptyPoolPicksNothing() {
        AnchorPool pool = AnchorPool.parse(new String[0]);
        assertTrue(pool.isEmpty());
        assertNull(pool.pick(0.5D));
    }

    @Test
    public void whitespaceAroundPartsIsIgnored() {
        AnchorPool pool = AnchorPool.parse(new String[] { "  a:x ; 2 " });
        assertEquals("a:x", pool.pick(0.5D));
    }
}
