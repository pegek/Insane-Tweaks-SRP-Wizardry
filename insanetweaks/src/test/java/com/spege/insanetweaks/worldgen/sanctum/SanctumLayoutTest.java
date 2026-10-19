package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class SanctumLayoutTest {

    private static final int SEEDS = 500;

    private static SanctumLayout layout(long seed) {
        return SanctumLayout.generate(seed, 1000, -2000, 18, 26);
    }

    @Test
    public void sameSeedSameLayout() {
        SanctumLayout a = layout(5L);
        SanctumLayout b = layout(5L);
        assertEquals(a.rooms.size(), b.rooms.size());
        assertEquals(a.bounds, b.bounds);
        assertEquals(a.floorY, b.floorY);
    }

    @Test
    public void roomInteriorsNeverOverlap() {
        for (long s = 0; s < SEEDS; s++) {
            List<SanctumLayout.Room> rooms = layout(s).rooms;
            for (int i = 0; i < rooms.size(); i++) {
                for (int j = i + 1; j < rooms.size(); j++) {
                    SanctumBox a = rooms.get(i).outer.shrink(1);
                    SanctumBox b = rooms.get(j).outer.shrink(1);
                    assertFalse("seed " + s + " rooms " + i + "/" + j, a.intersects(b));
                }
            }
        }
    }

    @Test
    public void footprintStaysNearTheOrigin() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            assertTrue(l.bounds.minX >= 1000 - 26 && l.bounds.maxX <= 1000 + 26);
            assertTrue(l.bounds.minZ >= -2000 - 26 && l.bounds.maxZ <= -2000 + 26);
        }
    }

    @Test
    public void twoToFourSideRoomsAndOneOfEachCorePiece() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            int side = 0;
            int core = 0;
            for (SanctumLayout.Room r : l.rooms) {
                if (r.type.isSideRoom()) {
                    side++;
                } else if (r.type != SanctumLayout.RoomType.CONNECTOR) {
                    core++;
                }
            }
            assertTrue("seed " + s, side >= 2 && side <= 4);
            assertEquals(3, core);
        }
    }

    @Test
    public void floorStaysInRange() {
        for (long s = 0; s < SEEDS; s++) {
            int f = layout(s).floorY;
            assertTrue(f >= 18 && f <= 26);
        }
    }

    @Test
    public void sealIsInTheChamberWallAndHoldsTheButton() {
        for (long s = 0; s < 50; s++) {
            SanctumLayout l = layout(s);
            SanctumBox chamber = l.rooms.get(0).outer;
            assertEquals(SanctumLayout.RoomType.CHAMBER, l.rooms.get(0).type);
            assertNotNull(chamber.intersection(l.seal));
            assertTrue(l.seal.contains(l.button[0], l.button[1], l.button[2]));
            assertFalse(chamber.shrink(1).intersects(l.seal));
        }
    }

    @Test
    public void contentsSitInsideInteriors() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            assertEquals(9, l.altars.size());
            assertTrue(l.dustAltars.size() >= 3 && l.dustAltars.size() <= 5);
            assertTrue(l.anchors.size() >= 2);
            for (int[] a : l.anchors) {
                assertTrue("seed " + s, insideSomeInterior(l, a[0], a[1], a[2]));
            }
            for (int[] c : l.chests) {
                assertTrue("seed " + s, insideSomeInterior(l, c[0], c[1], c[2]));
            }
        }
    }

    @Test
    public void wardAndShaftSitAboveTheVestibuleCentre() {
        for (long s = 0; s < 50; s++) {
            SanctumLayout l = layout(s);
            SanctumBox vest = l.rooms.get(2).outer;
            assertEquals(SanctumLayout.RoomType.VESTIBULE, l.rooms.get(2).type);
            assertEquals(vest.maxY, l.wardY);
            assertEquals((vest.minX + vest.maxX) / 2, l.shaftX);
            assertEquals((vest.minZ + vest.maxZ) / 2, l.shaftZ);
        }
    }

    private static boolean insideSomeInterior(SanctumLayout l, int x, int y, int z) {
        for (SanctumLayout.Room r : l.rooms) {
            SanctumBox in = r.outer.shrink(1);
            if (in != null && in.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }
}
