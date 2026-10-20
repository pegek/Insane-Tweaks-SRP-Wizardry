package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
            for (List<int[]> list : Arrays.asList(l.anchors, l.chests, l.props, l.decor, l.runes)) {
                for (int[] p : list) {
                    assertTrue("seed " + s + " cell " + key(p), used.add(key(p)));
                }
            }
            if (l.ritualCentre != null) {
                assertTrue("ritual centre seed " + s, used.add(key(l.ritualCentre)));
                assertTrue("hint chest seed " + s, used.add(key(l.hintChest)));
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

    /**
     * A tripwire scans south/west for a hook on placement, and a hook without its partner reads 41
     * blocks ahead - outside the populated area. So each trap is emitted north/east-end hook (facing
     * south or west) first, then the five wires, then the south/west-end hook.
     */
    @Test
    public void trapPiecesAreOrderedSoNoWireMeetsAnUnpartneredHook() {
        boolean sawTrap = false;
        for (long s = 0; s < SEEDS; s++) {
            List<int[]> props = layout(s).props;
            for (int i = 0; i < props.size(); i++) {
                int kind = props.get(i)[3];
                if (kind != SanctumLayout.PROP_TRIP_HOOK && kind != SanctumLayout.PROP_TRIPWIRE) {
                    continue;
                }
                sawTrap = true;
                int first = props.get(i)[4];
                assertTrue("seed " + s + " first hook facing " + first,
                        kind == SanctumLayout.PROP_TRIP_HOOK
                                && (first == SanctumLayout.SOUTH || first == SanctumLayout.WEST));
                for (int w = 1; w <= 5; w++) {
                    assertEquals("seed " + s, SanctumLayout.PROP_TRIPWIRE, props.get(i + w)[3]);
                }
                int[] last = props.get(i + 6);
                assertEquals(SanctumLayout.PROP_TRIP_HOOK, last[3]);
                assertTrue(last[4] == SanctumLayout.NORTH || last[4] == SanctumLayout.EAST);
                i += 6;
            }
        }
        assertTrue(sawTrap);
    }

    /** Vines grow on interior edge cells; an anchor there could get its statue space covered. */
    @Test
    public void anchorsNeverSitOnAnInteriorEdgeColumn() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            for (int[] a : l.anchors) {
                for (SanctumLayout.Room r : l.rooms) {
                    SanctumBox in = r.outer.shrink(1);
                    if (in == null || !in.contains(a[0], a[1], a[2])) {
                        continue;
                    }
                    boolean edge = a[0] == in.minX || a[0] == in.maxX || a[2] == in.minZ || a[2] == in.maxZ;
                    assertFalse("seed " + s + " " + r.type + " anchor " + key(a), edge);
                }
            }
        }
    }

    @Test
    public void lightsAreTwoOrThreePerRoom() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            for (SanctumLayout.Room r : l.rooms) {
                SanctumBox in = r.outer.shrink(1);
                int n = 0;
                for (int[] p : l.magicLights) {
                    n += in.contains(p[0], p[1], p[2]) ? 1 : 0;
                }
                if (r.type == SanctumLayout.RoomType.DESCENT) {
                    assertTrue("seed " + s + " descent " + n, n <= 1);
                } else if (r.type == SanctumLayout.RoomType.ANTECHAMBER) {
                    assertEquals("seed " + s, 2, n);
                } else {
                    assertTrue("seed " + s + " " + r.type + " " + n, n >= 2 && n <= 3);
                }
            }
        }
    }

    @Test
    public void stairDoorIsFourHigh() {
        for (long s = 0; s < 50; s++) {
            SanctumLayout l = layout(s);
            assertNotNull(l.stairDoor);
            assertEquals(3, l.stairDoor.maxY - l.stairDoor.minY);
            assertEquals(l.floorY2, l.stairDoor.minY);
        }
    }

    private static SanctumLayout.Room ritualRoom(SanctumLayout l) {
        for (SanctumLayout.Room r : l.rooms) {
            if (r.type == SanctumLayout.RoomType.RITUAL) {
                return r;
            }
        }
        return null;
    }

    @Test
    public void ritualRoomAppearsAndIsIncomplete() {
        boolean saw = false;
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            SanctumLayout.Room room = ritualRoom(l);
            if (room == null) {
                assertTrue(l.runes.isEmpty());
                assertEquals(-1, l.ritualKind);
                continue;
            }
            saw = true;
            assertTrue(l.ritualKind >= 0 && l.ritualKind < SanctumRituals.RITUAL_COUNT);
            int runes = 0;
            for (String row : SanctumRituals.pattern(l.ritualKind)) {
                runes += row.replace(" ", "").length();
            }
            assertEquals("seed " + s, runes - 1, l.runes.size());
            SanctumBox in = room.outer.shrink(1);
            for (int[] r : l.runes) {
                assertFalse(r[0] == l.ritualCentre[0] && r[1] == l.ritualCentre[1] && r[2] == l.ritualCentre[2]);
                assertTrue(in.contains(r[0], r[1], r[2]));
            }
            assertTrue(in.contains(l.ritualCentre[0], l.ritualCentre[1], l.ritualCentre[2]));
        }
        assertTrue(saw);
    }

    /** ASC tries the four rotations of the pattern, never a mirror image. */
    @Test
    public void ritualRunesFormARotationOfThePattern() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            if (l.ritualKind < 0) {
                continue;
            }
            String[] p = SanctumRituals.pattern(l.ritualKind);
            int h = p.length;
            int w = p[0].length();
            java.util.Map<String, Character> placed = new java.util.HashMap<String, Character>();
            for (int[] r : l.runes) {
                placed.put((r[0] - l.ritualCentre[0]) + "," + (r[2] - l.ritualCentre[2]), Character.valueOf((char) r[4]));
            }
            boolean match = false;
            for (int rot = 0; rot < 4 && !match; rot++) {
                boolean ok = true;
                for (int i = 0; i < h && ok; i++) {
                    for (int j = 0; j < w && ok; j++) {
                        // ASC: row i is (i - h/2) blocks north (-z), column j is (j - w/2) blocks west (-x).
                        int dx = -(j - w / 2);
                        int dz = -(i - h / 2);
                        for (int k = 0; k < rot; k++) {
                            int t = dx;
                            dx = -dz;
                            dz = t;
                        }
                        char want = p[i].charAt(j);
                        Character got = placed.get(dx + "," + dz);
                        if (i == h / 2 && j == w / 2) {
                            ok = got == null;
                        } else if (want == ' ') {
                            ok = got == null;
                        } else {
                            ok = got != null && got.charValue() == want;
                        }
                    }
                }
                match = ok;
            }
            assertTrue("seed " + s + " ritual " + l.ritualKind, match);
        }
    }

    @Test
    public void hintChestIsNotALockedChest() {
        for (long s = 0; s < SEEDS; s++) {
            SanctumLayout l = layout(s);
            SanctumLayout.Room room = ritualRoom(l);
            if (room == null) {
                assertNull(l.hintChest);
                continue;
            }
            assertNotNull(l.hintChest);
            assertTrue(room.outer.shrink(1).contains(l.hintChest[0], l.hintChest[1], l.hintChest[2]));
            for (int[] c : l.chests) {
                assertFalse(key(c).equals(key(l.hintChest)));
            }
        }
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
