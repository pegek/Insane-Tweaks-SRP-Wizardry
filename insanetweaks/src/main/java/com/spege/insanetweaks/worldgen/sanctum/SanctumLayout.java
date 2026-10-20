package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The seeded shape of one Sunken Arcane Sanctum, in world coordinates. Pure Java: everything is
 * laid out in a local frame (the descent rises along local +x) and rotated by a seeded direction.
 *
 * <p>Two levels. Level 1 ({@link #floorY}): vestibule under the shaft, hall, 2-4 side rooms.
 * Level 2 ({@link #floorY2}, ten blocks lower): antechamber with the seal, then the sealed-stone
 * ritual chamber. A straight stairway joins them.
 *
 * <p>Room order is fixed: 0 chamber, 1 antechamber, 2 descent, 3 hall, 4 vestibule, then side
 * rooms. The builder decides materials; this class decides geometry and what goes where,
 * including furniture, so containers are known before anything is built.
 */
public final class SanctumLayout {

    public enum RoomType {
        CHAMBER, ANTECHAMBER, DESCENT, HALL, VESTIBULE, LIBRARY, FORGE, WORKSHOP, CRYPT, COLLAPSED;

        public boolean isSideRoom() {
            return ordinal() >= LIBRARY.ordinal();
        }

        public boolean hasAnchors() {
            return this == LIBRARY || this == FORGE || this == WORKSHOP;
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

    public static final int PROP_EBW_SHELF = 0;
    public static final int PROP_ENCHANTING_TABLE = 1;
    public static final int PROP_ARCANE_WORKBENCH = 2;
    public static final int PROP_ARCANE_ANVIL = 3;
    public static final int PROP_ANVIL = 4;
    public static final int PROP_FURNACE = 5;
    public static final int PROP_SCRIBING_DESK = 6;
    public static final int PROP_SAGE_DESK = 7;
    public static final int PROP_SAGE_LECTERN = 8;
    public static final int PROP_CRAFTING_TABLE = 9;

    public static final int CHEST_MAIN = 0;
    public static final int CHEST_LIBRARY = 1;
    public static final int CHEST_FORGE = 2;
    public static final int CHEST_WORKSHOP = 3;

    public static final int POOL_CHAMBER = 0;
    public static final int POOL_ROOM = 1;

    public static final int LEVEL_DROP = 10;
    public static final int MIN_FLOOR = 17;

    private static final int[][] CHAMBER_SPOTS = { { -16, -5 }, { -16, 5 }, { -24, -5 }, { -24, 5 }, { -20, -6 },
            { -20, 6 }, { -26, 3 } };
    /** Side-room slots: centre x, centre z, facing towards the room's door. */
    private static final int[][] SLOTS = { { 14, -10, SOUTH }, { 14, 10, NORTH }, { 24, -8, SOUTH }, { 24, 8, NORTH } };

    public final long seed;
    public final int originX;
    public final int originZ;
    public final int floorY;
    public final int floorY2;
    public final int direction;
    public final List<Room> rooms = new ArrayList<Room>();
    /** Boxes cut to air after every room interior: doorways and the stairway's headroom. */
    public final List<SanctumBox> carves = new ArrayList<SanctumBox>();
    public SanctumBox seal;
    public final List<int[]> sealFrame = new ArrayList<int[]>();
    public int[] button;
    /** x, y, z of every stair block; all face {@link #stairFacing}. */
    public final List<int[]> stairs = new ArrayList<int[]>();
    public int stairFacing;
    public int shaftX;
    public int shaftZ;
    public int wardY;
    public int ladderFacing;
    public int[] ladderSupport;
    public SanctumBox dais;
    /** 9 altars; index 4 is the centre. */
    public final List<int[]> altars = new ArrayList<int[]>();
    public final List<int[]> dustAltars = new ArrayList<int[]>();
    /** x, z of each sealed-stone pillar; they span the chamber interior height. */
    public final List<int[]> pillars = new ArrayList<int[]>();
    /** x, y, z, pool ({@link #POOL_CHAMBER} or {@link #POOL_ROOM}). */
    public final List<int[]> anchors = new ArrayList<int[]>();
    /** x, y, z, facing, table ({@link #CHEST_MAIN} ...). */
    public final List<int[]> chests = new ArrayList<int[]>();
    /** x, y, z, kind ({@link #PROP_EBW_SHELF} ...), facing. */
    public final List<int[]> props = new ArrayList<int[]>();
    public final List<int[]> lights = new ArrayList<int[]>();
    public SanctumBox bounds;

    private final int rot;

    private SanctumLayout(long seed, int originX, int originZ, int floorY, int direction) {
        this.seed = seed;
        this.originX = originX;
        this.originZ = originZ;
        this.floorY = floorY;
        this.floorY2 = floorY - LEVEL_DROP;
        this.direction = direction;
        this.rot = direction == EAST ? 0 : direction == SOUTH ? 1 : direction == WEST ? 2 : 3;
    }

    public static SanctumLayout generate(long seed, int originX, int originZ, int floorMin, int floorMax) {
        Random r = new Random(seed);
        int lo = Math.min(floorMin, floorMax);
        int hi = Math.max(floorMin, floorMax);
        int floor = Math.max(MIN_FLOOR, lo + r.nextInt(hi - lo + 1));
        SanctumLayout l = new SanctumLayout(seed, originX, originZ, floor, r.nextInt(4));
        l.build(r);
        return l;
    }

    private void build(Random r) {
        int f1 = this.floorY;
        int f2 = this.floorY2;

        room(RoomType.CHAMBER, -28, f2 - 1, -8, -12, f2 + 8, 8);
        room(RoomType.ANTECHAMBER, -12, f2 - 1, -4, -4, f2 + 5, 4);
        room(RoomType.DESCENT, -4, f2 - 1, -2, 8, f1 + 4, 2);
        room(RoomType.HALL, 8, f1 - 1, -6, 20, f1 + 5, 6);
        room(RoomType.VESTIBULE, 20, f1 - 1, -4, 28, f1 + 5, 4);
        carve(-4, f2, -1, -4, f2 + 2, 1);
        carve(8, f1, -1, 8, f1 + 2, 1);
        carve(20, f1, -1, 20, f1 + 2, 1);

        // Stairway: ten steps rising along local +x, four blocks of headroom, then a landing.
        for (int i = 0; i <= 9; i++) {
            int x = -3 + i;
            int top = f2 + i;
            for (int z = -1; z <= 1; z++) {
                this.stairs.add(point(x, top, z));
            }
            carve(x, top + 1, -1, x, top + 4, 1);
        }
        carve(7, f1, -1, 7, f1 + 3, 1);
        this.stairFacing = face(EAST);

        // Seal plug in the chamber wall, ringed by a frame so unsealing cannot spread into the shell.
        this.seal = box(-12, f2, -1, -12, f2 + 2, 1);
        this.button = point(-12, f2 + 1, 0);
        for (int z = -2; z <= 2; z++) {
            for (int y = f2 - 1; y <= f2 + 3; y++) {
                if (Math.abs(z) <= 1 && y >= f2 && y <= f2 + 2) {
                    continue;
                }
                this.sealFrame.add(point(-12, y, z));
            }
        }

        int[] shaft = point(24, f1, 0);
        this.shaftX = shaft[0];
        this.shaftZ = shaft[2];
        this.wardY = f1 + 5;
        this.ladderFacing = face(NORTH);
        this.ladderSupport = point(24, f1, 1);

        chamber(r);

        this.lights.add(point(-8, f2 + 4, 0));
        int[][] hallLights = { { 14, 0 }, { 11, -3 }, { 11, 3 }, { 17, -3 }, { 17, 3 } };
        for (int[] h : hallLights) {
            this.lights.add(point(h[0], f1 + 4, h[1]));
        }
        this.lights.add(point(22, f1 + 4, -2));

        sideRooms(r);

        SanctumBox all = this.rooms.get(0).outer;
        for (Room room : this.rooms) {
            all = all.union(room.outer);
        }
        this.bounds = all.union(new SanctumBox(this.shaftX - 1, this.wardY, this.shaftZ - 1,
                this.shaftX + 1, 255, this.shaftZ + 1));
    }

    private void chamber(Random r) {
        int f2 = this.floorY2;
        this.dais = box(-22, f2, -2, -18, f2, 2);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                this.altars.add(point(-20 + dx, f2 + 1, dz));
            }
        }
        List<int[]> outer = new ArrayList<int[]>(this.altars);
        outer.remove(4);
        Collections.shuffle(outer, r);
        int dust = 3 + r.nextInt(3);
        for (int i = 0; i < dust; i++) {
            this.dustAltars.add(outer.get(i));
        }
        int[][] pillarCells = { { -27, -7 }, { -27, 7 }, { -13, -7 }, { -13, 7 } };
        for (int[] p : pillarCells) {
            int[] w = point(p[0], f2, p[1]);
            this.pillars.add(new int[] { w[0], w[2] });
        }
        this.chests.add(withTail(point(-25, f2, 0), face(EAST), CHEST_MAIN));
        List<int[]> spots = new ArrayList<int[]>();
        Collections.addAll(spots, CHAMBER_SPOTS);
        Collections.shuffle(spots, r);
        int statues = 3 + r.nextInt(3);
        for (int i = 0; i < statues; i++) {
            this.anchors.add(withTail(point(spots.get(i)[0], f2, spots.get(i)[1]), POOL_CHAMBER));
        }
        for (int lx = -24; lx <= -16; lx += 4) {
            for (int lz = -4; lz <= 4; lz += 4) {
                this.lights.add(point(lx, f2 + 7, lz));
            }
        }
    }

    private void sideRooms(Random r) {
        List<RoomType> types = new ArrayList<RoomType>();
        types.add(RoomType.LIBRARY);
        types.add(RoomType.FORGE);
        types.add(RoomType.WORKSHOP);
        types.add(r.nextBoolean() ? RoomType.CRYPT : RoomType.COLLAPSED);
        Collections.shuffle(types, r);
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < SLOTS.length; i++) {
            slots.add(Integer.valueOf(i));
        }
        Collections.shuffle(slots, r);
        int k = 2 + r.nextInt(3);
        for (int i = 0; i < k; i++) {
            int[] slot = SLOTS[slots.get(i).intValue()];
            sideRoom(types.get(i), slot[0], slot[1], slot[2], r);
        }
    }

    private void sideRoom(RoomType type, int cx, int cz, int front, Random r) {
        int f1 = this.floorY;
        // Outer 9x7x9 box, sharing the wall the door is cut through.
        int[] fv = vec(front);
        room(type, cx - 4, f1 - 1, cz - 4, cx + 4, f1 + 5, cz + 4);
        int dx = cx + fv[0] * 4;
        int dz = cz + fv[1] * 4;
        if (fv[0] == 0) {
            carve(dx - 1, f1, dz, dx + 1, f1 + 2, dz);
        } else {
            carve(dx, f1, dz - 1, dx, f1 + 2, dz + 1);
        }
        this.lights.add(point(cx, f1 + 4, cz));
        int sideA = (front + 3) % 4;
        int sideB = (front + 1) % 4;
        switch (type) {
            case LIBRARY:
                for (int b = -3; b <= 3; b++) {
                    for (int y = f1; y <= f1 + 1; y++) {
                        prop(cx, cz, front, -3, b, y, PROP_EBW_SHELF, front);
                    }
                }
                for (int a = -2; a <= 0; a++) {
                    prop(cx, cz, front, a, 3, f1, PROP_EBW_SHELF, sideA);
                    prop(cx, cz, front, a, -3, f1, PROP_EBW_SHELF, sideB);
                }
                prop(cx, cz, front, -1, 0, f1, r.nextBoolean() ? PROP_ENCHANTING_TABLE : PROP_ARCANE_WORKBENCH, front);
                chest(cx, cz, front, 2, 3, f1, sideA, CHEST_LIBRARY);
                break;
            case FORGE:
                prop(cx, cz, front, -2, 0, f1, PROP_ARCANE_ANVIL, front);
                prop(cx, cz, front, -2, 2, f1, PROP_ANVIL, front);
                prop(cx, cz, front, -3, -3, f1, PROP_FURNACE, front);
                prop(cx, cz, front, -3, -2, f1, PROP_FURNACE, front);
                chest(cx, cz, front, -3, 2, f1, front, CHEST_FORGE);
                break;
            case WORKSHOP:
                prop(cx, cz, front, -3, -1, f1, PROP_SCRIBING_DESK, front);
                prop(cx, cz, front, -3, 1, f1, PROP_SAGE_DESK, front);
                prop(cx, cz, front, -2, -3, f1, PROP_SAGE_LECTERN, sideB);
                prop(cx, cz, front, -3, -3, f1, PROP_CRAFTING_TABLE, front);
                chest(cx, cz, front, -3, 3, f1, front, CHEST_WORKSHOP);
                break;
            default:
                break;
        }
        if (type.hasAnchors()) {
            this.anchors.add(withTail(rel(cx, cz, front, 1, -2, f1), POOL_ROOM));
            if (r.nextInt(4) == 0) {
                this.anchors.add(withTail(rel(cx, cz, front, 1, 2, f1), POOL_ROOM));
            }
        }
    }

    // ---- local-frame helpers --------------------------------------------------------------

    private void prop(int cx, int cz, int front, int a, int b, int y, int kind, int localFacing) {
        this.props.add(withTail(rel(cx, cz, front, a, b, y), kind, face(localFacing)));
    }

    private void chest(int cx, int cz, int front, int a, int b, int y, int localFacing, int table) {
        this.chests.add(withTail(rel(cx, cz, front, a, b, y), face(localFacing), table));
    }

    /** A point a steps towards the door and b steps to the door's right, around a room centre. */
    private int[] rel(int cx, int cz, int front, int a, int b, int y) {
        int[] f = vec(front);
        int[] l = vec((front + 1) % 4);
        return point(cx + f[0] * a + l[0] * b, y, cz + f[1] * a + l[1] * b);
    }

    private static int[] vec(int facing) {
        switch (facing) {
            case NORTH:
                return new int[] { 0, -1 };
            case EAST:
                return new int[] { 1, 0 };
            case SOUTH:
                return new int[] { 0, 1 };
            default:
                return new int[] { -1, 0 };
        }
    }

    private void room(RoomType type, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.rooms.add(new Room(type, box(x1, y1, z1, x2, y2, z2)));
    }

    private void carve(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.carves.add(box(x1, y1, z1, x2, y2, z2));
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

    private static int[] withTail(int[] p, int... tail) {
        int[] out = new int[3 + tail.length];
        System.arraycopy(p, 0, out, 0, 3);
        System.arraycopy(tail, 0, out, 3, tail.length);
        return out;
    }
}
