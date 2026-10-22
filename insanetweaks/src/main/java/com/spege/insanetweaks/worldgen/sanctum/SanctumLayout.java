package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The seeded shape of one Sunken Arcane Sanctum, in world coordinates. Pure Java: everything is
 * laid out in a local frame (the descent rises along local +x) and rotated by a seeded direction.
 *
 * <p>Two levels. Level 1 ({@link #floorY}): vestibule under the shaft, hall, side rooms. Level 2
 * ({@link #floorY2}, ten blocks lower): antechamber with the seal and two more side-room slots,
 * then the sealed-stone ritual chamber. A straight stairway joins them.
 *
 * <p>Room order is fixed: 0 chamber, 1 antechamber, 2 descent, 3 hall, 4 vestibule, then side
 * rooms. The builder decides materials; this class decides geometry and what goes where -
 * furniture, traps, décor, lights - so containers are known before anything is built.
 */
public final class SanctumLayout {

    public enum RoomType {
        CHAMBER, ANTECHAMBER, DESCENT, HALL, VESTIBULE, LIBRARY, FORGE, WORKSHOP, TELEPORT, RITUAL, CRYPT, COLLAPSED;

        public boolean isSideRoom() {
            return ordinal() >= LIBRARY.ordinal();
        }

        public boolean hasAnchors() {
            return this != CHAMBER && this != ANTECHAMBER && this != DESCENT && this != HALL && this != VESTIBULE;
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
    /** Not registered in ASC 1.8.3 - kept only so the numbering stays stable; never placed. */
    public static final int PROP_SAGE_DESK = 7;
    public static final int PROP_SAGE_LECTERN = 8;
    public static final int PROP_CRAFTING_TABLE = 9;
    public static final int PROP_SPHERE_COGNIZANCE = 10;
    public static final int PROP_TRANSPORT_STONE = 11;
    public static final int PROP_GILDED_WOOD = 12;
    public static final int PROP_BOOKSHELF = 13;
    public static final int PROP_SHULKER = 14;
    public static final int PROP_TRIP_HOOK = 15;
    public static final int PROP_TRIPWIRE = 16;

    public static final int DECOR_ARMOR_STAND = 0;
    public static final int DECOR_SHULKER = 1;
    public static final int DECOR_ENDER_CHEST = 2;
    public static final int DECOR_PORTAL_FRAME = 3;
    public static final int DECOR_BED = 4;
    public static final int DECOR_PAINTING = 5;
    public static final int DECOR_SKULL = 6;

    public static final int FRAME_NONE = 0;
    public static final int FRAME_EDGE = 1;
    public static final int FRAME_CORNER = 2;

    public static final int CHEST_MAIN = 0;
    public static final int CHEST_LIBRARY = 1;
    public static final int CHEST_FORGE = 2;
    public static final int CHEST_WORKSHOP = 3;

    public static final int POOL_CHAMBER = 0;
    public static final int POOL_ROOM = 1;

    public static final int LEVEL_DROP = 10;
    public static final int MIN_FLOOR = 17;

    private static final double TRAP_CHANCE = 0.35D;

    private static final int[][] CHAMBER_SPOTS = { { -16, -5 }, { -16, 5 }, { -24, -5 }, { -24, 5 }, { -20, -6 },
            { -20, 6 }, { -26, 3 } };
    /** Side-room slots: centre x, centre z, facing towards the room's door, level (1 or 2). */
    private static final int[][] SLOTS = { { 14, -10, SOUTH, 1 }, { 14, 10, NORTH, 1 }, { 24, -8, SOUTH, 1 },
            { 24, 8, NORTH, 1 }, { -8, -8, SOUTH, 2 }, { -8, 8, NORTH, 2 } };

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
    public int[] button;
    /** The doorway between the antechamber and the bottom stair; four high so the step clears it. */
    public SanctumBox stairDoor;
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
    /** x, y, z of permanent EBW magic lights along room walls - the main lighting. */
    public final List<int[]> magicLights = new ArrayList<int[]>();
    /** x, y, z of ceiling spots the builder fills with glowstone only sporadically. */
    public final List<int[]> glowSpots = new ArrayList<int[]>();
    /** x, y, z, facing of trap dispensers, built into a side wall (shell cells). */
    public final List<int[]> trapDispensers = new ArrayList<int[]>();
    /** x, y, z, kind ({@link #DECOR_ARMOR_STAND} ...), facing, extra. */
    public final List<int[]> decor = new ArrayList<int[]>();
    /** Index into {@link SanctumRituals}, or -1 without a ritual room. */
    public int ritualKind = -1;
    /** x, y, z, ritual, key char of every rune laid in the ritual room. */
    public final List<int[]> runes = new ArrayList<int[]>();
    /** x, y, z of the centrepiece cell, left empty for the player; null without a ritual room. */
    public int[] ritualCentre;
    /** x, y, z, facing of the unlocked chest holding the ritual's hint book; null without one. */
    public int[] hintChest;
    /** The hall's straight walkway between its doors; nothing is placed on it. */
    public SanctumBox hallWalkway;
    /** x, y, z, facing of the (still empty) codex lectern beside the altar. */
    public int[] codexLectern;
    /** Lowest and highest y of the arcane-wall hull. */
    public int hullMinY;
    public int hullMaxY;
    /** Every room's outer box grown by one block, plus the shaft column above the ward. */
    public SanctumBox bounds;

    private final int rot;
    private final Set<String> used = new HashSet<String>();

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

    /** The head cell of a bed décor entry: one step from its foot cell in its facing. */
    public static int[] bedHead(int[] bed) {
        int[] v = vec(bed[4]);
        return new int[] { bed[0] + v[0], bed[1], bed[2] + v[1] };
    }

    private void build(Random r) {
        int f1 = this.floorY;
        int f2 = this.floorY2;

        room(RoomType.CHAMBER, -28, f2 - 1, -8, -12, f2 + 8, 8);
        room(RoomType.ANTECHAMBER, -12, f2 - 1, -4, -4, f2 + 5, 4);
        room(RoomType.DESCENT, -4, f2 - 1, -2, 8, f1 + 4, 2);
        room(RoomType.HALL, 8, f1 - 1, -6, 20, f1 + 5, 6);
        room(RoomType.VESTIBULE, 20, f1 - 1, -4, 28, f1 + 5, 4);
        carve(-4, f2, -1, -4, f2 + 3, 1);
        this.stairDoor = this.carves.get(this.carves.size() - 1);
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

        // Seal plug in the all-sealed-stone chamber wall; unsealing may spread through the shell.
        this.seal = box(-12, f2, -1, -12, f2 + 2, 1);
        this.button = point(-12, f2 + 1, 0);

        int[] shaft = point(24, f1, 0);
        this.shaftX = shaft[0];
        this.shaftZ = shaft[2];
        this.wardY = f1 + 5;
        this.ladderFacing = face(NORTH);
        this.ladderSupport = point(24, f1, 1);

        chamber(r);

        this.glowSpots.add(point(-8, f2 + 4, 0));
        int[][] hallSpots = { { 14, 0 }, { 11, -3 }, { 11, 3 }, { 17, -3 }, { 17, 3 } };
        for (int[] h : hallSpots) {
            this.glowSpots.add(point(h[0], f1 + 4, h[1]));
        }
        this.glowSpots.add(point(22, f1 + 4, -2));
        List<int[]> hall = new ArrayList<int[]>();
        List<int[]> vestibule = new ArrayList<int[]>();
        List<int[]> antechamber = new ArrayList<int[]>();
        for (int side = -1; side <= 1; side += 2) {
            hall.add(point(11, f1 + 3, 5 * side));
            hall.add(point(17, f1 + 3, 5 * side));
            vestibule.add(point(21, f1 + 3, 3 * side));
            vestibule.add(point(27, f1 + 3, 3 * side));
            antechamber.add(point(-6, f2 + 4, 3 * side));
        }
        lights(r, hall, 1, 2);
        lights(r, vestibule, 1, 2);
        lights(r, antechamber, 1, 2);
        // The landing's ceiling is the descent shell at F1+4, so its light stays at F1+3.
        this.magicLights.add(point(7, f1 + 3, 1));

        sideRooms(r);
        hall(r);

        SanctumBox all = grown(this.rooms.get(0).outer);
        this.hullMinY = Integer.MAX_VALUE;
        this.hullMaxY = Integer.MIN_VALUE;
        for (Room room : this.rooms) {
            SanctumBox g = grown(room.outer);
            all = all.union(g);
            this.hullMinY = Math.min(this.hullMinY, g.minY);
            this.hullMaxY = Math.max(this.hullMaxY, g.maxY);
        }
        this.bounds = all.union(new SanctumBox(this.shaftX - 1, this.wardY, this.shaftZ - 1,
                this.shaftX + 1, 255, this.shaftZ + 1));
    }

    private static SanctumBox grown(SanctumBox b) {
        return new SanctumBox(b.minX - 1, b.minY - 1, b.minZ - 1, b.maxX + 1, b.maxY + 1, b.maxZ + 1);
    }

    /**
     * The arcane-wall hull: one layer around every room (each outer box grown by one), never inside
     * any room's outer box, and open in the shaft column above the vestibule - the one way in.
     */
    /**
     * Runestone frame (spec 16.2): a shell cell on two boundary planes of a non-chamber room is an
     * edge, on three a corner. Nothing inside the ritual chamber's outer box is framed - it stays
     * all sealed stone.
     */
    public int frameAt(int x, int y, int z) {
        if (this.rooms.get(0).outer.contains(x, y, z)) {
            return FRAME_NONE;
        }
        int best = FRAME_NONE;
        for (Room room : this.rooms) {
            SanctumBox o = room.outer;
            if (room.type == RoomType.CHAMBER || !o.contains(x, y, z)) {
                continue;
            }
            int planes = (x == o.minX || x == o.maxX ? 1 : 0) + (y == o.minY || y == o.maxY ? 1 : 0)
                    + (z == o.minZ || z == o.maxZ ? 1 : 0);
            if (planes >= 3) {
                return FRAME_CORNER;
            }
            if (planes == 2) {
                best = FRAME_EDGE;
            }
        }
        return best;
    }

    public boolean isHull(int x, int y, int z) {
        if (Math.abs(x - this.shaftX) <= 1 && Math.abs(z - this.shaftZ) <= 1 && y > this.wardY) {
            return false;
        }
        boolean near = false;
        for (Room room : this.rooms) {
            SanctumBox o = room.outer;
            if (o.contains(x, y, z)) {
                return false;
            }
            near |= x >= o.minX - 1 && x <= o.maxX + 1 && y >= o.minY - 1 && y <= o.maxY + 1 && z >= o.minZ - 1
                    && z <= o.maxZ + 1;
        }
        return near;
    }

    /**
     * The level-1 hall: 2-3 room-pool anchors and side-room decor, all off the straight walkway
     * between its doors and off every doorway approach. Local frame: centre (14, 0), front east.
     */
    private void hall(Random r) {
        int f = this.floorY;
        this.hallWalkway = box(9, f, -1, 19, f + 2, 1);
        List<int[]> spots = new ArrayList<int[]>();
        for (int a = -3; a <= 3; a += 3) {
            spots.add(new int[] { a, -3 });
            spots.add(new int[] { a, 3 });
        }
        Collections.shuffle(spots, r);
        int n = 2 + r.nextInt(2);
        for (int i = 0; i < n; i++) {
            addAnchor(rel(14, 0, EAST, spots.get(i)[0], spots.get(i)[1], f), POOL_ROOM);
        }
        List<int[]> free = new ArrayList<int[]>();
        for (int a = -5; a <= 5; a++) {
            for (int b = -5; b <= 5; b++) {
                if (Math.abs(b) <= 1) {
                    continue;
                }
                int[] p = rel(14, 0, EAST, a, b, f);
                if (!this.used.contains(key(p)) && !nearCarve(p)) {
                    free.add(new int[] { p[0], p[1], p[2], a, b });
                }
            }
        }
        Collections.shuffle(free, r);
        if (r.nextDouble() < 0.20D) {
            place(free, DECOR_ARMOR_STAND, face(SOUTH), 0);
        }
        if (r.nextDouble() < 0.08D) {
            place(free, DECOR_SHULKER, face(SOUTH), 0);
        }
        if (r.nextDouble() < 0.10D) {
            place(free, DECOR_ENDER_CHEST, face(SOUTH), 0);
        }
        if (r.nextDouble() < 0.10D) {
            place(free, DECOR_PORTAL_FRAME, face(SOUTH), r.nextDouble() < 0.3D ? 1 : 0);
        }
        if (r.nextDouble() < 0.15D) {
            hallBed(free, f);
        }
        if (r.nextDouble() < 0.20D) {
            place(free, DECOR_SKULL, r.nextInt(4), r.nextDouble() < 0.3D ? 1 : 0);
        }
        if (r.nextDouble() < 0.25D) {
            int[] as = { -4, -3, 3, 4 };
            int a = as[r.nextInt(as.length)];
            int b = r.nextBoolean() ? 5 : -5;
            int[] p = rel(14, 0, EAST, a, b, f + 2);
            // Facing away from the long wall it hangs on: north wall (b = -5) faces south and back.
            if (!nearCarve(p) && this.used.add(key(p))) {
                this.decor.add(withTail(p, DECOR_PAINTING, face(b < 0 ? SOUTH : NORTH), 0));
            }
        }
    }

    /** A bed in the hall: head one step towards the nearer long wall. */
    private void hallBed(List<int[]> free, int f) {
        for (int i = free.size() - 1; i >= 0; i--) {
            int[] c = free.get(i);
            int b2 = c[4] + (c[4] > 0 ? 1 : -1);
            if (Math.abs(b2) > 5) {
                continue;
            }
            int[] foot = { c[0], c[1], c[2] };
            int[] head = rel(14, 0, EAST, c[3], b2, f);
            if (!this.used.contains(key(foot)) && !this.used.contains(key(head)) && !nearCarve(head)) {
                this.used.add(key(foot));
                this.used.add(key(head));
                free.remove(i);
                this.decor.add(withTail(foot, DECOR_BED, face(c[4] > 0 ? SOUTH : NORTH), 0));
                return;
            }
        }
    }

    /** Within two blocks (x/z) of a doorway or stair carve at a height that carve spans. */
    private boolean nearCarve(int[] p) {
        for (SanctumBox d : this.carves) {
            if (p[0] >= d.minX - 2 && p[0] <= d.maxX + 2 && p[2] >= d.minZ - 2 && p[2] <= d.maxZ + 2
                    && p[1] >= d.minY && p[1] <= d.maxY) {
                return true;
            }
        }
        return false;
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
        addChest(point(-25, f2, 0), face(EAST), CHEST_MAIN);
        if (r.nextBoolean()) {
            addChest(point(-25, f2, -2), face(EAST), CHEST_MAIN);
        }
        if (r.nextInt(4) == 0) {
            addProp(point(-25, f2, 2), PROP_SHULKER, face(EAST));
        }
        List<int[]> spots = new ArrayList<int[]>();
        Collections.addAll(spots, CHAMBER_SPOTS);
        Collections.shuffle(spots, r);
        int statues = 4 + (r.nextInt(4) == 0 ? 1 : 0) + (r.nextInt(4) == 0 ? 1 : 0);
        for (int i = 0; i < statues; i++) {
            addAnchor(point(spots.get(i)[0], f2, spots.get(i)[1]), POOL_CHAMBER);
        }
        for (int lx = -24; lx <= -16; lx += 4) {
            for (int lz = -4; lz <= 4; lz += 4) {
                this.glowSpots.add(point(lx, f2 + 7, lz));
            }
        }
        List<int[]> lamps = new ArrayList<int[]>();
        for (int side = -1; side <= 1; side += 2) {
            lamps.add(point(-27, f2 + 4, 4 * side));
            lamps.add(point(-13, f2 + 4, 4 * side));
            lamps.add(point(-24, f2 + 4, 7 * side));
            lamps.add(point(-16, f2 + 4, 7 * side));
        }
        lights(r, lamps, 2, 3);
        // Codex lectern beside the altar, facing it; filled by a later revision.
        int[] lectern = point(-17, f2, 0);
        this.used.add(key(lectern));
        this.codexLectern = withTail(lectern, face(WEST));
    }

    private void sideRooms(Random r) {
        List<RoomType> types = new ArrayList<RoomType>();
        types.add(RoomType.LIBRARY);
        types.add(RoomType.FORGE);
        types.add(RoomType.WORKSHOP);
        types.add(RoomType.TELEPORT);
        types.add(RoomType.RITUAL);
        types.add(r.nextBoolean() ? RoomType.CRYPT : RoomType.COLLAPSED);
        Collections.shuffle(types, r);
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < SLOTS.length; i++) {
            slots.add(Integer.valueOf(i));
        }
        Collections.shuffle(slots, r);
        // 3-6 side rooms over 6 slots and 6 types (one more on average since 1.27.0).
        int k = 3 + r.nextInt(4);
        for (int i = 0; i < k; i++) {
            int[] slot = SLOTS[slots.get(i).intValue()];
            sideRoom(types.get(i), slot[0], slot[1], slot[2], slot[3] == 2 ? this.floorY2 : this.floorY, r);
        }
    }

    private void sideRoom(RoomType type, int cx, int cz, int front, int f, Random r) {
        int[] fv = vec(front);
        room(type, cx - 4, f - 1, cz - 4, cx + 4, f + 5, cz + 4);
        int dx = cx + fv[0] * 4;
        int dz = cz + fv[1] * 4;
        if (fv[0] == 0) {
            carve(dx - 1, f, dz, dx + 1, f + 2, dz);
        } else {
            carve(dx, f, dz - 1, dx, f + 2, dz + 1);
        }
        this.glowSpots.add(point(cx, f + 4, cz));
        int sideA = (front + 3) % 4;
        int sideB = (front + 1) % 4;
        switch (type) {
            case LIBRARY:
                for (int b = -3; b <= 3; b++) {
                    for (int y = f; y <= f + 1; y++) {
                        prop(cx, cz, front, -3, b, y, shelf(r), front);
                    }
                }
                for (int a = -2; a <= 0; a++) {
                    prop(cx, cz, front, a, 3, f, shelf(r), sideA);
                    prop(cx, cz, front, a, -3, f, shelf(r), sideB);
                }
                prop(cx, cz, front, -1, 0, f, r.nextBoolean() ? PROP_ENCHANTING_TABLE : PROP_ARCANE_WORKBENCH, front);
                addChest(rel(cx, cz, front, 2, 3, f), face(sideA), CHEST_LIBRARY);
                break;
            case FORGE:
                prop(cx, cz, front, -2, 0, f, PROP_ARCANE_ANVIL, front);
                prop(cx, cz, front, -2, 2, f, PROP_ANVIL, front);
                prop(cx, cz, front, -3, -3, f, PROP_FURNACE, front);
                prop(cx, cz, front, -3, -2, f, PROP_FURNACE, front);
                addChest(rel(cx, cz, front, -3, 2, f), face(front), CHEST_FORGE);
                break;
            case WORKSHOP:
                // ASC's scribing desk model fronts the opposite way.
                prop(cx, cz, front, -3, -1, f, PROP_SCRIBING_DESK, (front + 2) % 4);
                prop(cx, cz, front, -3, 1, f, PROP_SPHERE_COGNIZANCE, front);
                prop(cx, cz, front, -2, -3, f, PROP_SAGE_LECTERN, sideB);
                prop(cx, cz, front, -3, -3, f, PROP_CRAFTING_TABLE, front);
                addChest(rel(cx, cz, front, -3, 3, f), face(front), CHEST_WORKSHOP);
                break;
            case RITUAL:
                ritual(cx, cz, front, f, r);
                break;
            case TELEPORT:
                boolean small = r.nextBoolean();
                int lo = small ? -1 : -2;
                int hi = 1;
                for (int a = lo; a <= hi; a++) {
                    for (int b = lo; b <= hi; b++) {
                        if (a == lo || a == hi || b == lo || b == hi) {
                            prop(cx, cz, front, a, b, f, PROP_TRANSPORT_STONE, front);
                        }
                    }
                }
                addChest(rel(cx, cz, front, -3, 0, f), face(front), CHEST_LIBRARY);
                break;
            default:
                // Crypts and collapsed rooms: a chest against the back wall, like every side room.
                addChest(rel(cx, cz, front, -3, 0, f), face(front), CHEST_LIBRARY);
                break;
        }
        if (type.hasAnchors()) {
            // Never on an interior edge column: vines grow there and would fill the statue's space.
            // The ritual room's runes fill its middle: its anchors stand against the back wall
            // beside the hint chest, and the builder keeps vines off every anchor column.
            int a = type == RoomType.TELEPORT ? 2 : type == RoomType.RITUAL ? -3 : 1;
            int b = 2;
            addAnchor(rel(cx, cz, front, a, -b, f), POOL_ROOM);
            if (r.nextInt(4) == 0) {
                addAnchor(rel(cx, cz, front, a, b, f), POOL_ROOM);
            }
        }
        List<int[]> lamps = new ArrayList<int[]>();
        lamps.add(rel(cx, cz, front, -1, -3, f + 3));
        lamps.add(rel(cx, cz, front, -1, 3, f + 3));
        if (type != RoomType.LIBRARY && type != RoomType.TELEPORT) {
            lamps.add(rel(cx, cz, front, 2, -3, f + 3));
            lamps.add(rel(cx, cz, front, 2, 3, f + 3));
        }
        lights(r, lamps, 1, 2);
        if (r.nextDouble() < TRAP_CHANCE) {
            trap(cx, cz, front, f);
        }
        decor(cx, cz, front, f, r);
    }

    /** Keeps min..max of a room's candidate magic lights (all of them when it has fewer). */
    private void lights(Random r, List<int[]> candidates, int min, int max) {
        int keep = Math.min(candidates.size(), min + r.nextInt(max - min + 1));
        Collections.shuffle(candidates, r);
        for (int i = 0; i < keep; i++) {
            this.magicLights.add(candidates.get(i));
        }
    }

    /**
     * Lays one ASC ritual on the floor around the room centre, without its centrepiece. Grid cell
     * (row i, column j) goes to a = i - h/2, b = w/2 - j: a proper rotation of ASC's own layout
     * (row i north, column j west of the centrepiece), never a mirror image. The whole grid,
     * blanks included, is kept free; the hint chest stands against the back wall.
     */
    private void ritual(int cx, int cz, int front, int f, Random r) {
        int kind = r.nextInt(SanctumRituals.RITUAL_COUNT);
        this.ritualKind = kind;
        String[] pattern = SanctumRituals.pattern(kind);
        int h = pattern.length;
        int w = pattern[0].length();
        for (int i = 0; i < h; i++) {
            for (int j = 0; j < w; j++) {
                int[] cell = rel(cx, cz, front, i - h / 2, w / 2 - j, f);
                this.used.add(key(cell));
                char c = pattern[i].charAt(j);
                if (i == h / 2 && j == w / 2) {
                    this.ritualCentre = cell;
                } else if (c != ' ') {
                    this.runes.add(withTail(cell, kind, c));
                }
            }
        }
        int[] chest = rel(cx, cz, front, -3, 0, f);
        this.used.add(key(chest));
        this.hintChest = withTail(chest, face(front));
        addChest(rel(cx, cz, front, 2, 3, f), face((front + 3) % 4), CHEST_LIBRARY);
    }

    private static int shelf(Random r) {
        double roll = r.nextDouble();
        return roll < 0.2D ? PROP_GILDED_WOOD : roll < 0.4D ? PROP_BOOKSHELF : PROP_EBW_SHELF;
    }

    /**
     * Tripwire across the row just inside the door; the hook on one side is attached to a dispenser
     * built into that side wall, facing across the room. The dispenser goes on the side whose wall
     * is not the ritual chamber's shell.
     *
     * <p>Order matters: a tripwire placed in the world scans south and west for a hook, and a hook
     * found without its partner reads 41 blocks ahead - past the populated chunks. So the hook at
     * the north/east end (facing south or west) goes first, then the wires, then the other hook,
     * which has no placement scan of its own. The builder places props in list order.
     */
    private void trap(int cx, int cz, int front, int f) {
        int side = -1;
        int[] dispenser = rel(cx, cz, front, 3, -4, f);
        if (this.rooms.get(0).outer.contains(dispenser[0], dispenser[1], dispenser[2])) {
            side = 1;
            dispenser = rel(cx, cz, front, 3, 4, f);
        }
        // Facing from the dispenser side towards the room: +lateral for b = -4, -lateral for b = +4.
        int towardsRoom = side < 0 ? (front + 1) % 4 : (front + 3) % 4;
        int towardsDispenser = (towardsRoom + 2) % 4;
        this.trapDispensers.add(withTail(dispenser, face(towardsRoom)));
        int nearFacing = face(towardsRoom);
        boolean nearFirst = nearFacing == SOUTH || nearFacing == WEST;
        int firstB = nearFirst ? 3 * side : -3 * side;
        prop(cx, cz, front, 3, firstB, f, PROP_TRIP_HOOK, nearFirst ? towardsRoom : towardsDispenser);
        for (int b = -2; b <= 2; b++) {
            prop(cx, cz, front, 3, b, f, PROP_TRIPWIRE, front);
        }
        prop(cx, cz, front, 3, -firstB, f, PROP_TRIP_HOOK, nearFirst ? towardsDispenser : towardsRoom);
    }

    /** Small seeded décor on free floor cells at a <= 1, i.e. away from the door line and trap row. */
    private void decor(int cx, int cz, int front, int f, Random r) {
        List<int[]> free = new ArrayList<int[]>();
        for (int a = -3; a <= 1; a++) {
            for (int b = -3; b <= 3; b++) {
                int[] p = rel(cx, cz, front, a, b, f);
                if (!this.used.contains(key(p))) {
                    free.add(new int[] { p[0], p[1], p[2], a, b });
                }
            }
        }
        Collections.shuffle(free, r);
        int back = (front + 2) % 4;
        if (r.nextDouble() < 0.20D) {
            place(free, DECOR_ARMOR_STAND, face(front), 0);
        }
        if (r.nextDouble() < 0.08D) {
            place(free, DECOR_SHULKER, face(front), 0);
        }
        if (r.nextDouble() < 0.10D) {
            place(free, DECOR_ENDER_CHEST, face(front), 0);
        }
        if (r.nextDouble() < 0.10D) {
            place(free, DECOR_PORTAL_FRAME, face(front), r.nextDouble() < 0.3D ? 1 : 0);
        }
        if (r.nextDouble() < 0.15D) {
            bed(free, cx, cz, front, back, f);
        }
        if (r.nextDouble() < 0.20D) {
            place(free, DECOR_SKULL, r.nextInt(4), r.nextDouble() < 0.3D ? 1 : 0);
        }
        if (r.nextDouble() < 0.25D) {
            int[] p = rel(cx, cz, front, -3, r.nextInt(3) - 1, f + 2);
            if (this.used.add(key(p))) {
                this.decor.add(withTail(p, DECOR_PAINTING, face(front), 0));
            }
        }
    }

    private void place(List<int[]> free, int kind, int facing, int extra) {
        while (!free.isEmpty()) {
            int[] c = free.remove(free.size() - 1);
            int[] p = { c[0], c[1], c[2] };
            if (this.used.add(key(p))) {
                this.decor.add(withTail(p, kind, facing, extra));
                return;
            }
        }
    }

    private void bed(List<int[]> free, int cx, int cz, int front, int back, int f) {
        for (int i = free.size() - 1; i >= 0; i--) {
            int[] c = free.get(i);
            if (c[3] - 1 < -3) {
                continue;
            }
            int[] foot = { c[0], c[1], c[2] };
            int[] head = rel(cx, cz, front, c[3] - 1, c[4], f);
            if (!this.used.contains(key(foot)) && !this.used.contains(key(head))) {
                this.used.add(key(foot));
                this.used.add(key(head));
                free.remove(i);
                this.decor.add(withTail(foot, DECOR_BED, face(back), 0));
                return;
            }
        }
    }

    // ---- registration helpers -------------------------------------------------------------

    private void addAnchor(int[] p, int pool) {
        this.used.add(key(p));
        this.anchors.add(withTail(p, pool));
    }

    private void addChest(int[] p, int facing, int table) {
        this.used.add(key(p));
        this.chests.add(withTail(p, facing, table));
    }

    private void addProp(int[] p, int kind, int facing) {
        this.used.add(key(p));
        this.props.add(withTail(p, kind, facing));
    }

    private void prop(int cx, int cz, int front, int a, int b, int y, int kind, int localFacing) {
        addProp(rel(cx, cz, front, a, b, y), kind, face(localFacing));
    }

    private static String key(int[] p) {
        return p[0] + "," + p[1] + "," + p[2];
    }

    // ---- local-frame helpers --------------------------------------------------------------

    /** A point a steps towards the door and b steps to the door's right, around a room centre. */
    private int[] rel(int cx, int cz, int front, int a, int b, int y) {
        int[] fv = vec(front);
        int[] lv = vec((front + 1) % 4);
        return point(cx + fv[0] * a + lv[0] * b, y, cz + fv[1] * a + lv[1] * b);
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
