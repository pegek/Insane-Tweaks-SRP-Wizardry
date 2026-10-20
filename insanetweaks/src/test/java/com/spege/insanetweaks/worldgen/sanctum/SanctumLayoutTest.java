package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        assertEquals(a.decor.size(), b.decor.size());
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
            Set<SanctumLayout.RoomType> seen = new HashSet<SanctumLayout.RoomType>();
            for (SanctumLayout.Room r : l.rooms) {
                if (r.type.isSideRoom()) {
                    side++;
                    assertTrue("duplicate side room type, seed " + s, seen.add(r.type));
                }
                if (r.type == SanctumLayout.RoomType.CRYPT || r.type == SanctumLayout.RoomType.COLLAPSED) {
                    fillers++;
                }
            }
            assertEquals(5, l.rooms.size() - side);
            assertTrue("seed " + s, side >= 2 && side <= 5);
            assertTrue(fillers <= 1);
            assertEquals(SanctumLayout.RoomType.CHAMBER, l.rooms.get(0).type);
            assertEquals(SanctumLayout.RoomType.VESTIBULE, l.rooms.get(4).type);
        }
    }

    @Test
    public void everySideRoomTypeAppearsSomewhere() {
        Set<SanctumLayout.RoomType> seen = new HashSet<SanctumLayout.RoomType>();
        for (long s = 0; s < SEEDS; s++) {
            for (SanctumLayout.Room r : layout(s).rooms) {
                seen.add(r.type);
            }
        }
        assertTrue(seen.contains(SanctumLayout.RoomType.TELEPORT));
        assertTrue(seen.contains(SanctumLayout.RoomType.LIBRARY));
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
    public void chamberHoldsThreeToSixAnchorsAndRoomsOneOrTwo() {
        boolean sawSix = false;
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
            assertTrue("seed " + s, chamber >= 3 && chamber <= 6);
            sawSix |= chamber == 6;
            int anchorRooms = 0;
            for (SanctumLayout.Room r : l.rooms) {
                if (r.type.hasAnchors()) {
                    anchorRooms++;
                }
            }
            assertTrue(room >= anchorRooms && room <= 2 * anchorRooms);
        }
        assertTrue(sawSix);
    }

    @Test
    public void chamberHasOneOrTwoMainChests() {
        boolean sawTwo = false;
        for (long s = 0; s < SEEDS; s++) {
            int main = 0;
            for (int[] c : layout(s).chests) {
                if (c[4] == SanctumLayout.CHEST_MAIN) {
                    main++;
                }
            }
            assertTrue(main >= 1 && main <= 2);
            sawTwo |= main == 2;
        }
        assertTrue(sawTwo);
    }

    @Test
    public void everythingPlacedSitsInsideAnInterior() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            for (List<int[]> list : Arrays.asList(l.anchors, l.chests, l.props, l.decor, l.magicLights)) {
                for (int[] p : list) {
                    assertTrue("seed " + s + " cell " + p[0] + "," + p[1] + "," + p[2], inside(l, p));
                }
            }
        }
    }

    @Test
    public void noTwoThingsShareACell() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            Set<String> used = new HashSet<String>();
            for (List<int[]> list : Arrays.asList(l.anchors, l.chests, l.props, l.decor)) {
                for (int[] p : list) {
                    assertTrue("seed " + s + " cell " + key(p), used.add(key(p)));
                }
            }
            for (int[] d : l.decor) {
                if (d[3] == SanctumLayout.DECOR_BED) {
                    int[] head = SanctumLayout.bedHead(d);
                    assertTrue("bed head seed " + s, used.add(key(head)));
                    assertTrue(inside(l, head));
                }
            }
        }
    }

    @Test
    public void trapDispensersSitInAShellCellNotAnInterior() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            for (int[] d : l.trapDispensers) {
                assertFalse("seed " + s, inside(l, d));
                boolean inShell = false;
                for (SanctumLayout.Room r : l.rooms) {
                    inShell |= r.outer.contains(d[0], d[1], d[2]);
                }
                assertTrue(inShell);
            }
        }
    }

    @Test
    public void trapRoomsHaveTwoHooksAndFiveStrings() {
        boolean sawTrap = false;
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            int hooks = 0;
            int strings = 0;
            for (int[] p : l.props) {
                hooks += p[3] == SanctumLayout.PROP_TRIP_HOOK ? 1 : 0;
                strings += p[3] == SanctumLayout.PROP_TRIPWIRE ? 1 : 0;
            }
            assertEquals(2 * l.trapDispensers.size(), hooks);
            assertEquals(5 * l.trapDispensers.size(), strings);
            sawTrap |= !l.trapDispensers.isEmpty();
        }
        assertTrue(sawTrap);
    }

    @Test
    public void teleportRingIsEightOrTwelveStones() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            for (SanctumLayout.Room r : l.rooms) {
                if (r.type != SanctumLayout.RoomType.TELEPORT) {
                    continue;
                }
                int stones = 0;
                for (int[] p : l.props) {
                    if (p[3] == SanctumLayout.PROP_TRANSPORT_STONE && r.outer.contains(p[0], p[1], p[2])) {
                        stones++;
                    }
                }
                assertTrue("seed " + s + " stones " + stones, stones == 8 || stones == 12);
            }
        }
    }

    @Test
    public void decorStaysOffDoorLinesAndTrapRows() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            for (int[] d : l.decor) {
                for (SanctumBox door : l.carves) {
                    boolean near = d[0] >= door.minX - 2 && d[0] <= door.maxX + 2 && d[2] >= door.minZ - 2
                            && d[2] <= door.maxZ + 2 && d[1] >= door.minY && d[1] <= door.maxY;
                    assertFalse("seed " + s + " decor " + key(d), near);
                }
                for (int[] p : l.props) {
                    if (p[3] == SanctumLayout.PROP_TRIPWIRE || p[3] == SanctumLayout.PROP_TRIP_HOOK) {
                        assertFalse(p[0] == d[0] && p[2] == d[2] && p[1] == d[1]);
                    }
                }
            }
        }
    }

    @Test
    public void paintingsHangOnAWall() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            for (int[] d : l.decor) {
                if (d[3] != SanctumLayout.DECOR_PAINTING) {
                    continue;
                }
                int[] wall = { d[0] - dx(d[4]), d[1], d[2] - dz(d[4]) };
                assertFalse("seed " + s, inside(l, wall));
                boolean inShell = false;
                for (SanctumLayout.Room r : l.rooms) {
                    inShell |= r.outer.contains(wall[0], wall[1], wall[2]);
                }
                assertTrue(inShell);
            }
        }
    }

    @Test
    public void noSageDesk() {
        for (long s = 0; s < SEEDS; s++) {
            for (int[] p : layout(s).props) {
                assertFalse(p[3] == SanctumLayout.PROP_SAGE_DESK);
            }
        }
    }

    @Test
    public void sealSitsInTheChamberWall() {
        for (long s = 0; s < 50; s++) {
            SanctumLayout l = layout(s);
            SanctumBox chamber = l.rooms.get(0).outer;
            assertNotNull(chamber.intersection(l.seal));
            assertFalse(chamber.shrink(1).intersects(l.seal));
            assertTrue(l.seal.contains(l.button[0], l.button[1], l.button[2]));
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

    private static int dx(int facing) {
        return facing == SanctumLayout.EAST ? 1 : facing == SanctumLayout.WEST ? -1 : 0;
    }

    private static int dz(int facing) {
        return facing == SanctumLayout.SOUTH ? 1 : facing == SanctumLayout.NORTH ? -1 : 0;
    }

    private static String key(int[] p) {
        return p[0] + "," + p[1] + "," + p[2];
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
