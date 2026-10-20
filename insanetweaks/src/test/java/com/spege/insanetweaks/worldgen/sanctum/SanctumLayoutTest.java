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
        return SanctumLayout.generate(seed, 1000, -2000, 22, 30);
    }

    @Test
    public void sameSeedSameLayout() {
        SanctumLayout a = layout(5L);
        SanctumLayout b = layout(5L);
        assertEquals(a.rooms.size(), b.rooms.size());
        assertEquals(a.bounds, b.bounds);
        assertEquals(a.anchors.size(), b.anchors.size());
    }

    @Test
    public void roomInteriorsNeverOverlap() {
        for (long s = 0; s < SEEDS; s++) {
            List<SanctumLayout.Room> rooms = layout(s).rooms;
            for (int i = 0; i < rooms.size(); i++) {
                for (int j = i + 1; j < rooms.size(); j++) {
                    assertFalse("seed " + s + " rooms " + rooms.get(i).type + "/" + rooms.get(j).type,
                            rooms.get(i).outer.shrink(1).intersects(rooms.get(j).outer.shrink(1)));
                }
            }
        }
    }

    @Test
    public void footprintStaysNearTheOrigin() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            assertTrue(l.bounds.minX >= 1000 - 28 && l.bounds.maxX <= 1000 + 28);
            assertTrue(l.bounds.minZ >= -2000 - 28 && l.bounds.maxZ <= -2000 + 28);
        }
    }

    @Test
    public void corePiecesSideRoomsAndFillers() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            int side = 0;
            int fillers = 0;
            int libraries = 0;
            for (SanctumLayout.Room r : l.rooms) {
                if (r.type.isSideRoom()) {
                    side++;
                }
                if (r.type == SanctumLayout.RoomType.CRYPT || r.type == SanctumLayout.RoomType.COLLAPSED) {
                    fillers++;
                }
                if (r.type == SanctumLayout.RoomType.LIBRARY) {
                    libraries++;
                }
            }
            assertEquals(5, l.rooms.size() - side);
            assertTrue("seed " + s, side >= 2 && side <= 4);
            assertTrue(fillers <= 1 && libraries <= 1);
            assertEquals(SanctumLayout.RoomType.CHAMBER, l.rooms.get(0).type);
            assertEquals(SanctumLayout.RoomType.VESTIBULE, l.rooms.get(4).type);
        }
    }

    @Test
    public void levelsAreTenApartAndFloorIsClamped() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            assertEquals(l.floorY - SanctumLayout.LEVEL_DROP, l.floorY2);
            assertTrue(l.floorY >= 22 && l.floorY <= 30);
        }
        assertTrue(SanctumLayout.generate(1L, 0, 0, 5, 5).floorY >= SanctumLayout.MIN_FLOOR);
    }

    @Test
    public void chamberHoldsThreeToFiveAnchorsAndRoomsOneOrTwo() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            int chamber = 0;
            int room = 0;
            for (int[] a : l.anchors) {
                if (a[3] == SanctumLayout.POOL_CHAMBER) {
                    chamber++;
                } else {
                    room++;
                }
            }
            assertTrue("seed " + s, chamber >= 3 && chamber <= 5);
            int anchorRooms = 0;
            for (SanctumLayout.Room r : l.rooms) {
                if (r.type.hasAnchors()) {
                    anchorRooms++;
                }
            }
            assertTrue(room >= anchorRooms && room <= 2 * anchorRooms);
        }
    }

    @Test
    public void everythingPlacedSitsInsideAnInterior() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            for (int[] a : l.anchors) {
                assertTrue("anchor seed " + s, inside(l, a));
            }
            for (int[] c : l.chests) {
                assertTrue("chest seed " + s, inside(l, c));
            }
            for (int[] p : l.props) {
                assertTrue("prop seed " + s, inside(l, p));
            }
        }
    }

    @Test
    public void noTwoThingsShareACell() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            java.util.Set<String> used = new java.util.HashSet<String>();
            for (List<int[]> list : java.util.Arrays.asList(l.anchors, l.chests, l.props)) {
                for (int[] p : list) {
                    assertTrue("seed " + s + " cell " + p[0] + "," + p[1] + "," + p[2],
                            used.add(p[0] + "," + p[1] + "," + p[2]));
                }
            }
        }
    }

    @Test
    public void sealSitsInTheChamberWallInsideItsFrame() {
        for (long s = 0; s < 50; s++) {
            SanctumLayout l = layout(s);
            SanctumBox chamber = l.rooms.get(0).outer;
            assertNotNull(chamber.intersection(l.seal));
            assertFalse(chamber.shrink(1).intersects(l.seal));
            assertTrue(l.seal.contains(l.button[0], l.button[1], l.button[2]));
            assertEquals(16, l.sealFrame.size());
            for (int[] f : l.sealFrame) {
                assertFalse(l.seal.contains(f[0], f[1], f[2]));
            }
        }
    }

    @Test
    public void stairsClimbTenLevels() {
        for (long s = 0; s < 50; s++) {
            SanctumLayout l = layout(s);
            assertEquals(30, l.stairs.size());
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int[] st : l.stairs) {
                min = Math.min(min, st[1]);
                max = Math.max(max, st[1]);
            }
            assertEquals(l.floorY2, min);
            assertEquals(l.floorY - 1, max);
        }
    }

    @Test
    public void shaftSitsAboveTheVestibuleCentre() {
        for (long s = 0; s < 50; s++) {
            SanctumLayout l = layout(s);
            SanctumBox vest = l.rooms.get(4).outer;
            assertEquals(vest.maxY, l.wardY);
            assertEquals((vest.minX + vest.maxX) / 2, l.shaftX);
            assertEquals((vest.minZ + vest.maxZ) / 2, l.shaftZ);
        }
    }

    private static boolean inside(SanctumLayout l, int[] p) {
        for (SanctumLayout.Room r : l.rooms) {
            SanctumBox in = r.outer.shrink(1);
            if (in != null && in.contains(p[0], p[1], p[2])) {
                return true;
            }
        }
        return false;
    }
}
