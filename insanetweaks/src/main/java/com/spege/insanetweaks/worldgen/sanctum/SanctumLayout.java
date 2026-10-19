package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The seeded shape of one Sunken Arcane Sanctum, in world coordinates. Pure Java: everything is
 * computed in a local frame (corridor along local +x) and rotated by the corridor direction.
 * The builder decides block materials; this class decides only geometry and what goes where.
 *
 * <p>Room order is fixed: 0 chamber, 1 corridor, 2 vestibule, then connectors and side rooms.
 */
public final class SanctumLayout {

    public enum RoomType {
        CHAMBER, CORRIDOR, VESTIBULE, CONNECTOR, GUARD_HALL, LIBRARY, CRYPT, COLLAPSED;

        public boolean isSideRoom() {
            return this == GUARD_HALL || this == LIBRARY || this == CRYPT || this == COLLAPSED;
        }
    }

    public static final class Room {
        public final RoomType type;
        public final SanctumBox outer;

        Room(RoomType type, SanctumBox outer) {
            this.type = type;
            this.outer = outer;
        }
    }

    /** Facing indices: 0 north (-z), 1 east (+x), 2 south (+z), 3 west (-x). */
    public static final int NORTH = 0;
    public static final int EAST = 1;
    public static final int SOUTH = 2;
    public static final int WEST = 3;

    private static final int CH_N = 0;
    private static final int CH_S = 1;
    private static final int CH_W = 2;
    private static final int CO_N = 3;
    private static final int CO_S = 4;

    public final long seed;
    public final int originX;
    public final int originZ;
    public final int floorY;
    public final int direction;
    public final List<Room> rooms = new ArrayList<Room>();
    public final List<SanctumBox> doorways = new ArrayList<SanctumBox>();
    public SanctumBox seal;
    public int[] button;
    public int shaftX;
    public int shaftZ;
    public int wardY;
    public int ladderFacing;
    public int[] ladderSupport;
    public SanctumBox dais;
    /** 9 altars; index 4 is the centre. */
    public final List<int[]> altars = new ArrayList<int[]>();
    public final List<int[]> dustAltars = new ArrayList<int[]>();
    public final List<int[]> anchors = new ArrayList<int[]>();
    /** x, y, z, facing, main (1) or minor (0). */
    public final List<int[]> chests = new ArrayList<int[]>();
    /** x, z of each sealed-stone pillar; they span the chamber interior height. */
    public final List<int[]> pillars = new ArrayList<int[]>();
    public final List<int[]> lights = new ArrayList<int[]>();
    /** Union of every room, plus the shaft column up to the build height. */
    public SanctumBox bounds;

    private final int rot;

    private SanctumLayout(long seed, int originX, int originZ, int floorY, int direction) {
        this.seed = seed;
        this.originX = originX;
        this.originZ = originZ;
        this.floorY = floorY;
        this.direction = direction;
        this.rot = direction == EAST ? 0 : direction == SOUTH ? 1 : direction == WEST ? 2 : 3;
    }

    public static SanctumLayout generate(long seed, int originX, int originZ, int floorMin, int floorMax) {
        Random r = new Random(seed);
        int lo = Math.min(floorMin, floorMax);
        int hi = Math.max(floorMin, floorMax);
        int floor = lo + r.nextInt(hi - lo + 1);
        SanctumLayout l = new SanctumLayout(seed, originX, originZ, floor, r.nextInt(4));
        l.build(r);
        return l;
    }

    private void build(Random r) {
        int f = this.floorY;
        int len = 8 + r.nextInt(5);
        int xm = 6 + len / 2;

        room(RoomType.CHAMBER, -6, f - 1, -6, 6, f + 7, 6);
        room(RoomType.CORRIDOR, 6, f - 1, -2, 6 + len, f + 4, 2);
        room(RoomType.VESTIBULE, 6 + len, f - 1, -4, 14 + len, f + 5, 4);
        door(6 + len, f, -1, 6 + len, f + 2, 1);

        this.seal = box(6, f, -1, 6, f + 2, 1);
        this.button = point(6, f + 1, 0);

        int sx = 10 + len;
        int[] shaft = point(sx, f, 0);
        this.shaftX = shaft[0];
        this.shaftZ = shaft[2];
        this.wardY = f + 5;
        this.ladderFacing = face(NORTH);
        this.ladderSupport = point(sx, f, 1);

        // Chamber contents.
        this.dais = box(-2, f, -2, 2, f, 2);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                this.altars.add(point(dx, f + 1, dz));
            }
        }
        List<int[]> outer = new ArrayList<int[]>(this.altars);
        outer.remove(4);
        Collections.shuffle(outer, r);
        int dust = 3 + r.nextInt(3);
        for (int i = 0; i < dust; i++) {
            this.dustAltars.add(outer.get(i));
        }
        int[][] pillarCells = { { 5, 5 }, { 5, -5 }, { -5, 5 }, { -5, -5 } };
        for (int[] p : pillarCells) {
            int[] w = point(p[0], f, p[1]);
            this.pillars.add(new int[] { w[0], w[2] });
        }
        this.chests.add(withFacing(point(-4, f, 0), face(EAST), 1));
        int[][] statueSpots = { { 3, -4 }, { 3, 4 }, { -3, -4 }, { -3, 4 }, { 0, -4 }, { 0, 4 } };
        List<int[]> spots = new ArrayList<int[]>();
        Collections.addAll(spots, statueSpots);
        Collections.shuffle(spots, r);
        int statues = 2 + r.nextInt(3);
        for (int i = 0; i < statues; i++) {
            this.anchors.add(point(spots.get(i)[0], f, spots.get(i)[1]));
        }
        for (int lx = -4; lx <= 4; lx += 4) {
            for (int lz = -4; lz <= 4; lz += 4) {
                this.lights.add(point(lx, f + 6, lz));
            }
        }

        // Side rooms: k distinct slots out of five.
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < 5; i++) {
            slots.add(Integer.valueOf(i));
        }
        Collections.shuffle(slots, r);
        int k = 2 + r.nextInt(3);
        for (int i = 0; i < k; i++) {
            sideRoom(slots.get(i).intValue(), pickType(r), xm, r);
        }

        SanctumBox all = this.rooms.get(0).outer;
        for (Room room : this.rooms) {
            all = all.union(room.outer);
        }
        this.bounds = all.union(new SanctumBox(this.shaftX - 1, this.wardY, this.shaftZ - 1,
                this.shaftX + 1, 255, this.shaftZ + 1));
    }

    private static RoomType pickType(Random r) {
        int roll = r.nextInt(8);
        if (roll < 3) {
            return RoomType.GUARD_HALL;
        }
        if (roll < 5) {
            return RoomType.LIBRARY;
        }
        return roll < 7 ? RoomType.CRYPT : RoomType.COLLAPSED;
    }

    private void sideRoom(int slot, RoomType type, int xm, Random r) {
        int f = this.floorY;
        int cx;
        int cz;
        boolean doorAlongZ;
        switch (slot) {
            case CH_N:
                room(RoomType.CONNECTOR, -2, f - 1, -9, 2, f + 4, -6);
                door(-1, f, -6, 1, f + 2, -6);
                door(-1, f, -9, 1, f + 2, -9);
                room(type, -4, f - 1, -17, 4, f + 5, -9);
                cx = 0;
                cz = -13;
                doorAlongZ = true;
                break;
            case CH_S:
                room(RoomType.CONNECTOR, -2, f - 1, 6, 2, f + 4, 9);
                door(-1, f, 6, 1, f + 2, 6);
                door(-1, f, 9, 1, f + 2, 9);
                room(type, -4, f - 1, 9, 4, f + 5, 17);
                cx = 0;
                cz = 13;
                doorAlongZ = true;
                break;
            case CH_W:
                room(RoomType.CONNECTOR, -9, f - 1, -2, -6, f + 4, 2);
                door(-6, f, -1, -6, f + 2, 1);
                door(-9, f, -1, -9, f + 2, 1);
                room(type, -17, f - 1, -4, -9, f + 5, 4);
                cx = -13;
                cz = 0;
                doorAlongZ = false;
                break;
            case CO_N:
                room(RoomType.CONNECTOR, xm - 2, f - 1, -5, xm + 2, f + 4, -2);
                door(xm - 1, f, -2, xm + 1, f + 2, -2);
                door(xm - 1, f, -5, xm + 1, f + 2, -5);
                room(type, xm - 4, f - 1, -13, xm + 4, f + 5, -5);
                cx = xm;
                cz = -9;
                doorAlongZ = true;
                break;
            default:
                room(RoomType.CONNECTOR, xm - 2, f - 1, 2, xm + 2, f + 4, 5);
                door(xm - 1, f, 2, xm + 1, f + 2, 2);
                door(xm - 1, f, 5, xm + 1, f + 2, 5);
                room(type, xm - 4, f - 1, 5, xm + 4, f + 5, 13);
                cx = xm;
                cz = 9;
                doorAlongZ = true;
                break;
        }
        this.lights.add(point(cx, f + 4, cz));
        if (type == RoomType.GUARD_HALL) {
            if (doorAlongZ) {
                this.anchors.add(point(cx - 2, f, cz));
                this.anchors.add(point(cx + 2, f, cz));
            } else {
                this.anchors.add(point(cx, f, cz - 2));
                this.anchors.add(point(cx, f, cz + 2));
            }
        } else if (type == RoomType.LIBRARY) {
            // The minor chest sits one cell off-centre, away from the door line.
            int[] c = doorAlongZ ? point(cx + (r.nextBoolean() ? 2 : -2), f, cz) : point(cx, f, cz + 2);
            this.chests.add(withFacing(c, face(r.nextInt(4)), 0));
        }
    }

    // ---- local-frame helpers --------------------------------------------------------------

    private void room(RoomType type, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.rooms.add(new Room(type, box(x1, y1, z1, x2, y2, z2)));
    }

    private void door(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.doorways.add(box(x1, y1, z1, x2, y2, z2));
    }

    private SanctumBox box(int x1, int y1, int z1, int x2, int y2, int z2) {
        int[] a = point(x1, y1, z1);
        int[] b = point(x2, y2, z2);
        return new SanctumBox(a[0], a[1], a[2], b[0], b[1], b[2]);
    }

    /** Local (lx, y, lz) to world (x, y, z). */
    private int[] point(int lx, int y, int lz) {
        int dx;
        int dz;
        switch (this.direction) {
            case SOUTH:
                dx = -lz;
                dz = lx;
                break;
            case WEST:
                dx = -lx;
                dz = -lz;
                break;
            case NORTH:
                dx = lz;
                dz = -lx;
                break;
            default:
                dx = lx;
                dz = lz;
                break;
        }
        return new int[] { this.originX + dx, y, this.originZ + dz };
    }

    private int face(int localFacing) {
        return (localFacing + this.rot) % 4;
    }

    private static int[] withFacing(int[] p, int facing, int main) {
        return new int[] { p[0], p[1], p[2], facing, main };
    }
}
