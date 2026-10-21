package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class SanctumEntranceTest {

    /** solid[y] for y in 0..127; everything above top is air. */
    private static boolean[] column(int top) {
        boolean[] solid = new boolean[128];
        for (int y = 0; y <= top; y++) {
            solid[y] = true;
        }
        return solid;
    }

    private static int entrance(boolean[] solid, int street, int measured) {
        return SanctumEntrance.topY(street, measured, y -> y >= 0 && y < solid.length && solid[y]);
    }

    /** Open street at the street level: the shaft ends in the street. */
    @Test
    public void openStreetEndsAtTheStreet() {
        boolean[] s = column(71);
        assertEquals(71, entrance(s, 71, 71));
    }

    /** A ground-floor room: floor at 71, room air above, a ceiling slab at 77 - the roof is far up. */
    @Test
    public void buildingEndsOnItsGroundFloor() {
        boolean[] s = column(71);
        s[77] = true;
        s[83] = true;
        s[100] = true;
        assertEquals(71, entrance(s, 71, 100));
    }

    /** A raised city level: ground continues to 83, street on top of it. */
    @Test
    public void raisedCityLevelEndsOnItsStreet() {
        boolean[] s = column(83);
        assertEquals(83, entrance(s, 71, 83));
    }

    /** A one-block gap (a slab with one air above) is not a floor you can stand in. */
    @Test
    public void needsTwoBlocksOfHeadroom() {
        boolean[] s = column(71);
        s[73] = true;
        assertEquals(73, entrance(s, 71, 73));
    }

    /** Nothing open below the measured top: the measured top is used. */
    @Test
    public void fallsBackToTheMeasuredTop() {
        boolean[] s = column(90);
        assertEquals(90, entrance(s, 71, 90));
    }

    /** The dimension salt leaves the Overworld unchanged and moves other dimensions. */
    @Test
    public void dimensionSeedLeavesTheOverworldAlone() {
        assertEquals(12345L, SanctumRegions.dimensionSeed(12345L, 0));
        assertFalse(12345L == SanctumRegions.dimensionSeed(12345L, 111));
        assertFalse(SanctumRegions.dimensionSeed(12345L, 111) == SanctumRegions.dimensionSeed(12345L, -1));
    }
}
