# Sanctum Revision 1.25.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the first-playtest revision of the Sunken Arcane Sanctum and the Petrified Anchor. Spec §12 covers the full list:
- reliable anchors with a delayed release, a return home and an `awake` stage;
- a two-level layout with themed rooms and a sealed-stone ritual chamber;
- EBW Arcane Lock on every container until the anchors fall;
- the new loot (spell books, a bauble chance, no nucleus);
- the ladder chime and command help.

**Architecture:**
- The pure `SanctumLayout` is rewritten to the two-level geometry and now also lists furniture (props), so it can say up front which positions are containers.
- `SanctumWorldData` gains per-sanctum records: remaining anchors, containers, cleared flag.
- `SanctumLocks` puts EBW's native `arcaneLockOwner` on containers and lifts it.
- The anchor tile entity keeps its own copy of the mob and spawns it itself.
- `AnchorLeashHandler` adds the return home and death tracking.

**Tech Stack:** Forge 1.12.2, EBW 4.3.19 (ArcaneLock / PacketSyncArcaneLock / Spell API), ASC 1.8.3 blocks, Baubles API (optional, isolated), JUnit 4.12.

Spec: `docs/superpowers/specs/2026-10-20-sunken-arcane-sanctum-design.md` §12.
Gradle needs JDK 8: `./gradlew "-Dorg.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot" …`.

**Verified facts (bytecode, 2026-10-21):**

- **EBW lock.**
  - `ArcaneLock.NBT_KEY = "arcaneLockOwner"` sits in `TileEntity.getTileData()`.
  - EBW's static handlers refuse right-click, left-click, break, mob destroy and explosion for non-owners.
  - `TileEntityShrineCore.conquer()` unlocks a container by removing the key.
  - `PacketSyncArcaneLock.Message(BlockPos, boolean, UUID)` **always writes the UUID**, so it must not be null. Send the sanctum's own UUID with `locked=false`.
- **EBW bookshelf.** `BlockBookshelf extends BlockHorizontal` (`FACING`); `TileEntityBookshelf extends TileEntityLockableLoot`.
- **EBW spells.**
  - `Spell.getSpells(java.util.function.Predicate<Spell>)`, plus `getTier()`, `getElement()`, `metadata()` and `isEnabled(SpellProperties.Context...)`.
  - `Context.TREASURE` exists; tiers go `NOVICE`, `APPRENTICE`, `ADVANCED`, `MASTER`.
  - A spell book is `WizardryItems.spell_book` with meta `spell.metadata()`.
- **SRP.** `EntityParasiteBase.cannotDespawn(boolean)` sets `canD`, which `canDespawn()` returns, so `false` means persistent.
- **Block and item ids:**
  - EBW: `WizardryBlocks.arcane_workbench`, `oak_bookshelf`;
  - ASC: `ancientspellcraft:arcane_anvil`, `scribing_desk`, `sage_desk`, `sage_lectern`;
  - EBW armour-upgrade items: `resplendent_thread`, `crystal_silver_plating`, `ethereal_crystalweave`, `blank_scroll`;
  - SRP 1.10.9 items: `ada_*_drop`, `assimilated_flesh`, `vile_shell`, `infectious_blade_fragment`.

**🚨 Working-tree note:** `en_us.lang` carries uncommitted work from another session (JEE effect descriptions). Stage only this plan's hunks of that file, via an LF-normalised `diff -u` and `git apply --cached`.

---

### Task 1: Layout v2 (TDD)

- [ ] **Step 1: Replace the test.**

File: `insanetweaks/src/test/java/com/spege/insanetweaks/worldgen/sanctum/SanctumLayoutTest.java`
```java
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
```

- [ ] **Step 2:** Run the tests. Expected: compile FAIL (missing members).

- [ ] **Step 3: Replace the layout.**

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumLayout.java`
```java
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
```

- [ ] **Step 4:** Run the tests. Expected: they pass. **Do not commit yet**: the builder, generator and command still reference the old layout fields, and are fixed in Tasks 3–5.

---

### Task 2: Config, lang, loot tables

- [ ] **`ArcaneSanctumCategory`:**
  - change the defaults to `floorYMin = 22` and `floorYMax = 30`, and update the Floor Y comment to say "Level 1 floor; the ritual level is 10 lower";
  - append the fields:

```java
    @Config.Comment("Mobs pinned on the side rooms' anchors, as 'modid:entity;weight'.")
    @Config.Name("Room Anchor Pool")
    public String[] roomAnchorPool = { "insanetweaks:sim_wizard;3", "insanetweaks:sim_battlemage;1" };

    @Config.Comment("Chance (0-1) that the main chest also holds one random bauble/artefact from any mod.")
    @Config.Name("Main Chest Bauble Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double mainChestBaubleChance = 0.25;

    @Config.Comment("Item ids never picked as the main chest's bauble.")
    @Config.Name("Bauble Blacklist")
    public String[] baubleBlacklist = { "enigmaticlegacy:cursed_ring" };

    @Config.Comment("Chance (0-1) per roll that an EBW bookshelf gets a spell book.")
    @Config.Name("Bookshelf Book Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double bookshelfBookChance = 0.05;

    @Config.Comment("Rolls per EBW bookshelf. Books: 50% Abomination, 25% any Master, 25% any Advanced spell.")
    @Config.Name("Bookshelf Book Rolls")
    @Config.RangeInt(min = 0, max = 12)
    public int bookshelfBookRolls = 3;
```

- [ ] **`EntitiesCategory.PetrifiedAnchor`**, after `leashRadius`:
```java
        @Config.Comment("Ticks between the statue shattering and its mob appearing. Read live.")
        @Config.Name("Release Delay Ticks")
        @Config.RangeInt(min = 0, max = 200)
        public int releaseDelayTicks = 10;

        @Config.Comment({ "An anchor's mob with no player this close for 'Return Seconds' teleports back to its",
                "anchor and heals 'Return Heal Fraction' of its missing health. Read live." })
        @Config.Name("Return Radius")
        @Config.RangeDouble(min = 4.0, max = 256.0)
        public double returnRadius = 40.0;

        @Config.Name("Return Seconds")
        @Config.RangeInt(min = 1, max = 3600)
        public int returnSeconds = 40;

        @Config.Name("Return Heal Fraction")
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double returnHealFraction = 0.25;
```

- [ ] **Lang** (own hunks only), after `msg.insanetweaks.anchor.sealed=…`:
```
msg.insanetweaks.anchor.sealed_force=The statue will not yield to force
msg.insanetweaks.sanctum.locked=An arcane lock holds - defeat the sanctum's guardians
msg.insanetweaks.sanctum.unsealed=The sanctum's seals fade
```

- [ ] **Loot tables:** overwrite `arcane_sanctum.json` and add `arcane_sanctum_forge.json` and `arcane_sanctum_workshop.json`.

File: `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/chests/arcane_sanctum.json`
```json
{
  "pools": [
    { "name": "crystals", "rolls": 1, "entries": [
      { "type": "item", "name": "ebwizardry:magic_crystal", "weight": 1,
        "functions": [ { "function": "set_count", "count": { "min": 3, "max": 8 } } ] } ] },
    { "name": "abomination", "rolls": { "min": 1, "max": 2 }, "entries": [
      { "type": "item", "name": "ebwizardry:spectral_dust", "weight": 3,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 3 } },
                       { "function": "insanetweaks:abomination_meta" } ] },
      { "type": "item", "name": "ebwizardry:magic_crystal", "weight": 2,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 2 } },
                       { "function": "insanetweaks:abomination_meta" } ] } ] },
    { "name": "parasite_parts", "rolls": { "min": 1, "max": 3 }, "entries": [
      { "type": "item", "name": "srparasites:assimilated_flesh", "weight": 4,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 4 } } ] },
      { "type": "item", "name": "srparasites:vile_shell", "weight": 3 },
      { "type": "item", "name": "srparasites:infectious_blade_fragment", "weight": 2 },
      { "type": "item", "name": "srparasites:ada_longarms_drop", "weight": 1 },
      { "type": "item", "name": "srparasites:ada_manducater_drop", "weight": 1 },
      { "type": "item", "name": "srparasites:ada_summoner_drop", "weight": 1 } ] },
    { "name": "wizard_tower", "rolls": 1, "entries": [
      { "type": "loot_table", "name": "ebwizardry:chests/wizard_tower", "weight": 1 } ] }
  ]
}
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/chests/arcane_sanctum_forge.json`
```json
{
  "pools": [
    { "name": "components", "rolls": { "min": 1, "max": 2 }, "entries": [
      { "type": "item", "name": "ebwizardry:resplendent_thread", "weight": 3 },
      { "type": "item", "name": "ebwizardry:crystal_silver_plating", "weight": 3 },
      { "type": "item", "name": "ebwizardry:ethereal_crystalweave", "weight": 2 },
      { "type": "empty", "weight": 2 } ] },
    { "name": "materials", "rolls": { "min": 2, "max": 4 }, "entries": [
      { "type": "item", "name": "minecraft:iron_ingot", "weight": 4,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 4 } } ] },
      { "type": "item", "name": "minecraft:gold_ingot", "weight": 2,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 3 } } ] },
      { "type": "item", "name": "ebwizardry:magic_crystal", "weight": 3,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 3 } } ] },
      { "type": "item", "name": "minecraft:coal", "weight": 2,
        "functions": [ { "function": "set_count", "count": { "min": 2, "max": 6 } } ] } ] }
  ]
}
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/chests/arcane_sanctum_workshop.json`
```json
{
  "pools": [
    { "name": "scrolls", "rolls": { "min": 1, "max": 3 }, "entries": [
      { "type": "item", "name": "ebwizardry:blank_scroll", "weight": 3,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 4 } } ] },
      { "type": "item", "name": "minecraft:paper", "weight": 2,
        "functions": [ { "function": "set_count", "count": { "min": 2, "max": 6 } } ] },
      { "type": "item", "name": "minecraft:dye", "weight": 1, "functions": [ { "function": "set_data", "data": 0 } ] } ] },
    { "name": "arcana", "rolls": 1, "entries": [
      { "type": "item", "name": "ebwizardry:magic_crystal", "weight": 3,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 3 } } ] },
      { "type": "item", "name": "ebwizardry:spectral_dust", "weight": 1,
        "functions": [ { "function": "insanetweaks:abomination_meta" } ] } ] }
  ]
}
```

---

### Task 3: Anchor block stages, tile entity, guard, leash, registry

File: `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/BlockPetrifiedAnchor.java`
```java
package com.spege.insanetweaks.anchor;

import java.util.Random;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/**
 * Petrified Anchor plinth with three stages. DORMANT holds a statue; AWAKE has released its mob
 * and keeps watching it; SPENT is plain cracked stone. Dormant and awake plinths carry the tile
 * entity, cannot be mined in survival and shrug off explosions. Metadata 0/1 match the old
 * awakened=false/true, so placed anchors survive the change.
 */
public class BlockPetrifiedAnchor extends Block {

    public enum Stage implements IStringSerializable {
        DORMANT("dormant"), SPENT("spent"), AWAKE("awake");

        private final String name;

        Stage(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return this.name;
        }
    }

    public static final PropertyEnum<Stage> STAGE = PropertyEnum.create("stage", Stage.class);

    private static final float SPENT_HARDNESS = 2.0F;
    private static final float ACTIVE_RESISTANCE = 6000000.0F;

    public BlockPetrifiedAnchor() {
        super(Material.ROCK);
        this.setSoundType(SoundType.STONE);
        this.setResistance(6.0F);
        this.setDefaultState(this.blockState.getBaseState().withProperty(STAGE, Stage.DORMANT));
        this.setUnlocalizedName(InsaneTweaksMod.MODID + ".petrified_anchor");
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "petrified_anchor"));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, STAGE);
    }

    @SuppressWarnings("deprecation")
    @Override
    public IBlockState getStateFromMeta(int meta) {
        Stage[] stages = Stage.values();
        return this.getDefaultState().withProperty(STAGE, meta >= 0 && meta < stages.length ? stages[meta] : Stage.DORMANT);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(STAGE).ordinal();
    }

    public static boolean active(IBlockState state) {
        return state.getValue(STAGE) != Stage.SPENT;
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getBlockHardness(IBlockState state, World world, BlockPos pos) {
        return active(state) ? -1.0F : SPENT_HARDNESS;
    }

    @Override
    public float getExplosionResistance(World world, BlockPos pos, Entity exploder, Explosion explosion) {
        return active(world.getBlockState(pos)) ? ACTIVE_RESISTANCE
                : super.getExplosionResistance(world, pos, exploder, explosion);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return active(state);
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return active(state) ? new TileEntityPetrifiedAnchor() : null;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return active(state) ? net.minecraft.init.Items.AIR : Item.getItemFromBlock(Blocks.COBBLESTONE);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (state.getValue(STAGE) != Stage.DORMANT || !player.isCreative()) {
            return false;
        }
        ItemStack held = player.getHeldItem(hand);
        boolean egg = held.getItem() instanceof ItemMonsterPlacer;
        boolean reset = held.isEmpty() && player.isSneaking();
        if (!egg && !reset) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityPetrifiedAnchor)) {
            return true;
        }
        if (egg) {
            ResourceLocation id = ItemMonsterPlacer.getNamedIdFrom(held);
            if (id == null) {
                return true;
            }
            ((TileEntityPetrifiedAnchor) te).pin(id.toString());
            String key = EntityList.getTranslationName(id);
            player.sendMessage(new TextComponentTranslation("msg.insanetweaks.anchor.pinned",
                    key != null ? new TextComponentTranslation("entity." + key + ".name") : id.toString()));
        } else {
            ((TileEntityPetrifiedAnchor) te).pin("");
            player.sendMessage(new TextComponentTranslation("msg.insanetweaks.anchor.reset"));
        }
        return true;
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/TileEntityPetrifiedAnchor.java`
```java
package com.spege.insanetweaks.anchor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.google.common.base.Predicate;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;
import com.spege.insanetweaks.worldgen.sanctum.SanctumLocks;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * The anchor's phase machine (server only; EBW renders the statue).
 *
 * <ul>
 *   <li>EMPTY 0 - create a fresh mob (pin or pool), keep a copy of its NBT, petrify it on top.</li>
 *   <li>DORMANT 1 - wait for a survival player in range and an open SRP phase gate.</li>
 *   <li>WAKING 2 - crack, then remove the statue WITHOUT EBW's release.</li>
 *   <li>RELEASING 4 - after the release delay, spawn the mob from the stored copy.</li>
 *   <li>AWAKE 5 - watch the mob by UUID until it dies or stays missing for 60 s.</li>
 *   <li>SPENT 3 - flip the block to its spent stage, which drops this tile entity.</li>
 * </ul>
 *
 * Spawning from our own copy is what makes a wake-up reliable: EBW rebuilds a statue's creature
 * lazily after a chunk load, and a break before that released nothing (playtest 2026-10-20).
 * Phase numbers 0-3 are unchanged from 1.23, so saved anchors keep their state; a pre-1.25 statue
 * without a copy falls back to EBW's release.
 */
public class TileEntityPetrifiedAnchor extends TileEntity implements ITickable {

    static final byte EMPTY = 0;
    static final byte DORMANT = 1;
    static final byte WAKING = 2;
    static final byte SPENT = 3;
    static final byte RELEASING = 4;
    static final byte AWAKE = 5;

    private static final int BUILD_RETRY_TICKS = 100;
    private static final int WATCH_INTERVAL = 10;
    private static final int GUARD_INTERVAL = 20;
    private static final int MISSING_LIMIT = 1200;
    private static final int WARN_AFTER_ATTEMPTS = 3;
    private static final int TARGET_LOCK_TICKS = 60;
    private static final Set<String> WARNED_POOL_LINES = new HashSet<String>();

    private byte phase = EMPTY;
    private String pinnedMob = "";
    private int attempts;
    private int ticks;
    private int crackTimer;
    private int releaseTimer;
    private NBTTagCompound mobData;
    private UUID mobUuid;
    private int missingTicks;
    private boolean mobGone;
    private boolean hasSanctum;
    private long sanctumId;

    @Override
    public void update() {
        if (this.world == null || this.world.isRemote) {
            return;
        }
        this.ticks++;
        switch (this.phase) {
            case EMPTY:
                if (this.ticks == 1 || this.ticks % BUILD_RETRY_TICKS == 0) {
                    tryBuildStatue();
                }
                break;
            case DORMANT:
                if (this.ticks % WATCH_INTERVAL == 0) {
                    watch();
                }
                break;
            case WAKING:
                crack();
                break;
            case RELEASING:
                release();
                break;
            case AWAKE:
                if (this.ticks % GUARD_INTERVAL == 0) {
                    guard();
                }
                break;
            default:
                setStage(BlockPetrifiedAnchor.Stage.SPENT);
                break;
        }
    }

    private EntitiesCategory.PetrifiedAnchor cfg() {
        return ModConfig.entities.petrifiedAnchor;
    }

    private BlockPos statuePos() {
        return this.pos.up();
    }

    private boolean blacklistedDimension() {
        int dim = this.world.provider.getDimension();
        for (int banned : cfg().dimensionBlacklist) {
            if (banned == dim) {
                return true;
            }
        }
        return false;
    }

    // ---- EMPTY ----------------------------------------------------------------------------

    private void tryBuildStatue() {
        if (blacklistedDimension()) {
            return;
        }
        String id = chooseMob();
        String failure = null;
        if (id == null) {
            failure = "the mob pool is empty";
        } else if (!this.world.isAirBlock(statuePos())) {
            failure = "the block above the plinth is not free";
        } else {
            Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(id), this.world);
            if (!(entity instanceof EntityLiving)) {
                failure = "'" + id + "' is not a living entity id";
            } else {
                EntityLiving mob = (EntityLiving) entity;
                float yaw = this.world.rand.nextFloat() * 360.0F;
                BlockPos at = statuePos();
                mob.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, yaw, 0.0F);
                mob.rotationYawHead = yaw;
                mob.renderYawOffset = yaw;
                mob.onInitialSpawn(this.world.getDifficultyForLocation(at), null);
                mob.enablePersistence();
                if (mob instanceof EntityParasiteBase) {
                    ((EntityParasiteBase) mob).cannotDespawn(false);
                }
                AnchorLeash.tag(mob, this.pos, this.world.provider.getDimension(), this.hasSanctum, this.sanctumId);
                NBTTagCompound data = new NBTTagCompound();
                if (!mob.writeToNBTAtomically(data)) {
                    failure = "the mob could not be saved";
                } else if (!AnchorStatues.petrify(mob)) {
                    failure = "EBW refused the statue (no room for a tall mob, or block placement denied)";
                } else {
                    this.mobData = data;
                }
            }
        }
        if (failure == null) {
            this.phase = DORMANT;
            this.attempts = 0;
            markDirty();
            return;
        }
        this.attempts++;
        if (this.attempts == WARN_AFTER_ATTEMPTS) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Petrified Anchor at {} (dim {}) cannot make its statue: {}. "
                    + "Retrying every 5 s.", this.pos, Integer.valueOf(this.world.provider.getDimension()), failure);
        }
        markDirty();
    }

    private String chooseMob() {
        if (!this.pinnedMob.isEmpty()) {
            return this.pinnedMob;
        }
        AnchorPool pool = AnchorPool.parse(cfg().pool);
        for (String bad : pool.rejected()) {
            if (WARNED_POOL_LINES.add(bad)) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Petrified Anchor pool: ignoring malformed line '{}'.", bad);
            }
        }
        return pool.pick(this.world.rand.nextDouble());
    }

    // ---- DORMANT / WAKING / RELEASING ------------------------------------------------------

    private void watch() {
        if (!AnchorStatues.isStatueAt(this.world, statuePos())) {
            // Removed by something other than us (e.g. /setblock): release from the copy if we have one.
            if (this.mobData != null) {
                spawnMob(null);
            } else {
                finish();
            }
            return;
        }
        if (wakeAllowed(this.world) && nearestWaker() != null) {
            this.phase = WAKING;
            this.crackTimer = 0;
            markDirty();
        }
    }

    /**
     * The SRP phase gate: statues stay petrified until the evolution phase (per
     * {@code tweaks.srpPhaseSource}) reaches {@code minPhaseToWake}. Also read by AnchorStatueGuard.
     */
    public static boolean wakeAllowed(World world) {
        int min = ModConfig.entities.petrifiedAnchor.minPhaseToWake;
        return min <= 0 || com.spege.insanetweaks.util.SrpPhaseHelper.getGatingPhase(world) >= min;
    }

    private EntityPlayer nearestWaker() {
        BlockPos at = statuePos();
        return this.world.getClosestPlayer(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, cfg().wakeRadius,
                new Predicate<Entity>() {
                    @Override
                    public boolean apply(Entity e) {
                        return e instanceof EntityPlayer && !((EntityPlayer) e).isCreative()
                                && !((EntityPlayer) e).isSpectator();
                    }
                });
    }

    private void crack() {
        BlockPos at = statuePos();
        if (!AnchorStatues.isStatueAt(this.world, at)) {
            watch();
            return;
        }
        this.crackTimer++;
        debris(at, 6);
        if (this.crackTimer % 5 == 0) {
            this.world.playSound(null, at, SoundEvents.BLOCK_STONE_HIT, SoundCategory.HOSTILE, 1.0F,
                    0.6F + this.world.rand.nextFloat() * 0.3F);
        }
        if (this.crackTimer < cfg().crackTicks) {
            return;
        }
        if (this.mobData == null) {
            // Pre-1.25 statue: no copy, so let EBW release its creature.
            EntityLiving legacy = AnchorStatues.creatureAt(this.world, at);
            AnchorStatues.release(this.world, at);
            if (legacy != null && legacy.isEntityAlive()) {
                track(legacy, nearestWaker());
            } else {
                finish();
            }
            return;
        }
        AnchorStatues.discard(this.world, at);
        debris(at, 40);
        this.world.playSound(null, at, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.2F, 0.7F);
        this.phase = RELEASING;
        this.releaseTimer = 0;
        markDirty();
    }

    private void release() {
        this.releaseTimer++;
        BlockPos at = statuePos();
        MagicParasiteTraits.burst(this.world, at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D, 3, 0.5D);
        if (this.releaseTimer >= cfg().releaseDelayTicks) {
            spawnMob(nearestWaker());
        }
    }

    private void spawnMob(EntityPlayer waker) {
        Entity entity = this.mobData == null ? null : EntityList.createEntityFromNBT(this.mobData, this.world);
        if (!(entity instanceof EntityLiving)) {
            finish();
            return;
        }
        BlockPos at = statuePos();
        entity.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, entity.rotationYaw, 0.0F);
        if (!this.world.spawnEntity(entity)) {
            finish();
            return;
        }
        MagicParasiteTraits.burst(this.world, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ, 24, 0.5D);
        this.world.playSound(null, at, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.HOSTILE, 1.0F, 0.6F);
        track((EntityLiving) entity, waker);
    }

    private void track(EntityLiving mob, EntityPlayer waker) {
        this.mobUuid = mob.getUniqueID();
        this.mobData = null;
        this.missingTicks = 0;
        if (waker != null) {
            aim(mob, waker);
        }
        this.phase = AWAKE;
        setStage(BlockPetrifiedAnchor.Stage.AWAKE);
        markDirty();
    }

    /** SRP parasites take the target-lock overload, so their own retargeting cannot drop the player at once. */
    private static void aim(EntityLiving mob, EntityPlayer target) {
        if (mob instanceof EntityParasiteBase) {
            ((EntityParasiteBase) mob).setAttackTarget(target, TARGET_LOCK_TICKS);
        } else {
            mob.setAttackTarget(target);
        }
    }

    // ---- AWAKE ----------------------------------------------------------------------------

    private void guard() {
        if (this.mobGone || this.mobUuid == null
                || (this.hasSanctum && SanctumLocks.isAnchorDone(this.world, this.sanctumId, this.pos))) {
            finish();
            return;
        }
        Entity mob = this.world instanceof WorldServer ? ((WorldServer) this.world).getEntityFromUuid(this.mobUuid)
                : null;
        if (mob != null && mob.isDead) {
            finish();
            return;
        }
        if (mob == null) {
            this.missingTicks += GUARD_INTERVAL;
            if (this.missingTicks >= MISSING_LIMIT) {
                finish();
            }
        } else {
            this.missingTicks = 0;
        }
    }

    /** Called by AnchorLeashHandler when this anchor's mob dies. */
    void markMobGone() {
        this.mobGone = true;
        markDirty();
    }

    private void finish() {
        this.phase = SPENT;
        markDirty();
        if (this.hasSanctum) {
            SanctumLocks.anchorDone(this.world, this.sanctumId, this.pos);
        }
        setStage(BlockPetrifiedAnchor.Stage.SPENT);
    }

    private void setStage(BlockPetrifiedAnchor.Stage stage) {
        IBlockState state = this.world.getBlockState(this.pos);
        if (state.getBlock() instanceof BlockPetrifiedAnchor && state.getValue(BlockPetrifiedAnchor.STAGE) != stage) {
            this.world.setBlockState(this.pos, state.withProperty(BlockPetrifiedAnchor.STAGE, stage), 3);
        }
    }

    private void debris(BlockPos at, int count) {
        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.BLOCK_CRACK, at.getX() + 0.5D,
                    at.getY() + 1.0D, at.getZ() + 0.5D, count, 0.35D, 0.8D, 0.35D, 0.05D,
                    Block.getStateId(Blocks.STONE.getDefaultState()));
        }
        MagicParasiteTraits.burst(this.world, at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D, 2, 0.4D);
    }

    // ---- configuration from worldgen and creative -----------------------------------------

    /** Creative pin: remember the id and rebuild the statue from it. Empty string clears the pin. */
    void pin(String id) {
        this.pinnedMob = id == null ? "" : id;
        if (this.phase == DORMANT || this.phase == WAKING) {
            AnchorStatues.discard(this.world, statuePos());
        }
        this.phase = EMPTY;
        this.mobData = null;
        this.attempts = 0;
        this.ticks = 0;
        markDirty();
    }

    /** Worldgen: pin a mob before the first statue is built. */
    public void pinAtGeneration(String id) {
        this.pinnedMob = id == null ? "" : id;
        markDirty();
    }

    /** Worldgen: this anchor belongs to a sanctum (Arcane Lock bookkeeping). */
    public void setSanctum(long id) {
        this.hasSanctum = true;
        this.sanctumId = id;
        markDirty();
    }

    /** Same-block stage changes keep this tile entity except the change to SPENT. */
    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock() || !newState.getBlock().hasTileEntity(newState);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.phase = compound.getByte("Phase");
        this.pinnedMob = compound.getString("PinnedMob");
        this.attempts = compound.getByte("Attempts");
        this.mobData = compound.hasKey("MobData", 10) ? compound.getCompoundTag("MobData") : null;
        this.mobUuid = compound.hasUniqueId("Mob") ? compound.getUniqueId("Mob") : null;
        this.missingTicks = compound.getInteger("Missing");
        this.mobGone = compound.getBoolean("MobGone");
        this.hasSanctum = compound.getBoolean("HasSanctum");
        this.sanctumId = compound.getLong("Sanctum");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setByte("Phase", this.phase);
        compound.setString("PinnedMob", this.pinnedMob);
        compound.setByte("Attempts", (byte) Math.min(this.attempts, 127));
        if (this.mobData != null) {
            compound.setTag("MobData", this.mobData);
        }
        if (this.mobUuid != null) {
            compound.setUniqueId("Mob", this.mobUuid);
        }
        compound.setInteger("Missing", this.missingTicks);
        compound.setBoolean("MobGone", this.mobGone);
        compound.setBoolean("HasSanctum", this.hasSanctum);
        compound.setLong("Sanctum", this.sanctumId);
        return compound;
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/AnchorLeash.java`
```java
package com.spege.insanetweaks.anchor;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Tags an anchor writes into its mob before petrifying it: home, dimension and, inside a sanctum,
 * the sanctum id. They are part of the stored NBT copy, so the released mob carries them.
 */
public final class AnchorLeash {

    public static final String HOME = "InsaneTweaksAnchorHome";
    public static final String DIM = "InsaneTweaksAnchorDim";
    public static final String SANCTUM = "InsaneTweaksSanctum";
    public static final String ALONE = "InsaneTweaksAnchorAlone";

    private AnchorLeash() {
    }

    public static void tag(Entity mob, BlockPos anchor, int dimension, boolean inSanctum, long sanctumId) {
        NBTTagCompound data = mob.getEntityData();
        data.setLong(HOME, anchor.toLong());
        data.setInteger(DIM, dimension);
        if (inSanctum) {
            data.setLong(SANCTUM, sanctumId);
        }
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/AnchorLeashHandler.java`
```java
package com.spege.insanetweaks.anchor;

import com.google.common.base.Predicate;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;
import com.spege.insanetweaks.worldgen.sanctum.SanctumLocks;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Keeps a Petrified Anchor's mob tied to its anchor, every 40 ticks, server only:
 * <ul>
 *   <li>Leash - further than {@code leashRadius}: pulled straight back (hard limit).</li>
 *   <li>Return - no player within {@code returnRadius} for {@code returnSeconds}: teleports home
 *   and heals {@code returnHealFraction} of its missing health.</li>
 *   <li>Death - tells the anchor (if loaded) and the sanctum record (always), which is what
 *   lifts a sanctum's Arcane Lock.</li>
 * </ul>
 */
public class AnchorLeashHandler {

    private static final int CHECK_INTERVAL = 40;
    private static final double HOME_SLACK_SQ = 16.0D;

    private static final Predicate<Entity> NOT_SPECTATOR = new Predicate<Entity>() {
        @Override
        public boolean apply(Entity e) {
            return e instanceof EntityPlayer && !((EntityPlayer) e).isSpectator();
        }
    };

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote || entity.ticksExisted % CHECK_INTERVAL != 0 || !(entity instanceof EntityLiving)) {
            return;
        }
        NBTTagCompound data = entity.getEntityData();
        if (!data.hasKey(AnchorLeash.HOME) || data.getInteger(AnchorLeash.DIM) != entity.dimension) {
            return;
        }
        EntitiesCategory.PetrifiedAnchor cfg = ModConfig.entities.petrifiedAnchor;
        BlockPos home = BlockPos.fromLong(data.getLong(AnchorLeash.HOME));
        double dist = entity.getDistanceSq(home);
        int radius = cfg.leashRadius;
        if (radius > 0 && dist > (double) radius * radius) {
            sendHome((EntityLiving) entity, home, 0.0D);
            return;
        }
        EntityPlayer near = entity.world.getClosestPlayer(entity.posX, entity.posY, entity.posZ, cfg.returnRadius,
                NOT_SPECTATOR);
        if (near != null) {
            data.setInteger(AnchorLeash.ALONE, 0);
            return;
        }
        int alone = data.getInteger(AnchorLeash.ALONE) + CHECK_INTERVAL;
        if (alone >= cfg.returnSeconds * 20) {
            alone = 0;
            if (dist > HOME_SLACK_SQ) {
                sendHome((EntityLiving) entity, home, cfg.returnHealFraction);
            }
        }
        data.setInteger(AnchorLeash.ALONE, alone);
    }

    private static void sendHome(EntityLiving mob, BlockPos home, double healFraction) {
        MagicParasiteTraits.burst(mob.world, mob.posX, mob.posY + mob.height * 0.5D, mob.posZ, 16, 0.4D);
        double x = home.getX() + 0.5D;
        double y = home.getY() + 1.0D;
        double z = home.getZ() + 0.5D;
        if (!mob.attemptTeleport(x, y, z)) {
            mob.setPositionAndUpdate(x, y, z);
        }
        mob.getNavigator().clearPath();
        mob.setAttackTarget(null);
        if (healFraction > 0.0D) {
            mob.heal((float) ((mob.getMaxHealth() - mob.getHealth()) * healFraction));
        }
        MagicParasiteTraits.burst(mob.world, mob.posX, mob.posY + mob.height * 0.5D, mob.posZ, 16, 0.4D);
    }

    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        World world = entity.world;
        NBTTagCompound data = entity.getEntityData();
        if (world.isRemote || !data.hasKey(AnchorLeash.HOME) || data.getInteger(AnchorLeash.DIM) != entity.dimension) {
            return;
        }
        BlockPos home = BlockPos.fromLong(data.getLong(AnchorLeash.HOME));
        if (world.isBlockLoaded(home)) {
            TileEntity te = world.getTileEntity(home);
            if (te instanceof TileEntityPetrifiedAnchor) {
                ((TileEntityPetrifiedAnchor) te).markMobGone();
            }
        }
        if (data.hasKey(AnchorLeash.SANCTUM)) {
            SanctumLocks.anchorDone(world, data.getLong(AnchorLeash.SANCTUM), home);
        }
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/AnchorStatueGuard.java`
```java
package com.spege.insanetweaks.anchor;

import java.util.Iterator;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * A statue standing on a dormant anchor can never be broken or blown up in survival: the anchor
 * itself releases its mob (from its own copy), and a statue broken by hand would bypass that
 * (playtest 2026-10-20). Creative players may still break it. Statues from EBW's own Petrify spell
 * are untouched.
 */
public class AnchorStatueGuard {

    private static final int MAX_PARTS = 3;

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        EntityPlayer player = event.getPlayer();
        if (world.isRemote || (player != null && player.isCreative()) || !isSealed(world, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        if (player != null) {
            player.sendStatusMessage(new TextComponentTranslation(TileEntityPetrifiedAnchor.wakeAllowed(world)
                    ? "msg.insanetweaks.anchor.sealed_force" : "msg.insanetweaks.anchor.sealed"), true);
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        Iterator<BlockPos> it = event.getAffectedBlocks().iterator();
        while (it.hasNext()) {
            if (isSealed(world, it.next())) {
                it.remove();
            }
        }
    }

    /** True for a statue block that stands (directly or via its lower parts) on a dormant anchor. */
    static boolean isSealed(World world, BlockPos pos) {
        if (!AnchorStatues.isStatueAt(world, pos)) {
            return false;
        }
        for (int k = 1; k <= MAX_PARTS; k++) {
            BlockPos below = pos.down(k);
            IBlockState state = world.getBlockState(below);
            if (state.getBlock() instanceof BlockPetrifiedAnchor) {
                return state.getValue(BlockPetrifiedAnchor.STAGE) == BlockPetrifiedAnchor.Stage.DORMANT;
            }
            if (!AnchorStatues.isStatueAt(world, below)) {
                return false;
            }
        }
        return false;
    }
}
```

**Blockstate** (overwrite) File: `insanetweaks/src/main/resources/assets/insanetweaks/blockstates/petrified_anchor.json`
```json
{
  "variants": {
    "stage=dormant": { "model": "insanetweaks:petrified_anchor" },
    "stage=awake": { "model": "insanetweaks:petrified_anchor_cracked" },
    "stage=spent": { "model": "insanetweaks:petrified_anchor_cracked" },
    "inventory": { "model": "insanetweaks:petrified_anchor" }
  }
}
```

---

### Task 4: Sanctum locks, world data, books, baubles

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumWorldData.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Per-dimension sanctum bookkeeping:
 * <ul>
 *   <li>decisions - whether a start was judged valid (integrity rule);</li>
 *   <li>records - each sanctum's remaining anchors and its containers, and whether it is cleared
 *   (all anchors defeated, Arcane Lock lifted).</li>
 * </ul>
 * An unknown sanctum counts as cleared: a lock nobody can lift is worse than a free chest.
 */
public class SanctumWorldData extends WorldSavedData {

    public static final String NAME = "insanetweaks_sanctums";
    private static final int TAG_LONG = 4;
    private static final int TAG_COMPOUND = 10;

    private final Map<Long, Boolean> decisions = new HashMap<Long, Boolean>();
    private final Map<Long, Record> records = new HashMap<Long, Record>();

    private static final class Record {
        final Set<Long> anchors = new HashSet<Long>();
        final List<Long> containers = new ArrayList<Long>();
        boolean cleared;
    }

    public SanctumWorldData() {
        this(NAME);
    }

    public SanctumWorldData(String name) {
        super(name);
    }

    public static SanctumWorldData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        SanctumWorldData data = (SanctumWorldData) storage.getOrLoadData(SanctumWorldData.class, NAME);
        if (data == null) {
            data = new SanctumWorldData();
            storage.setData(NAME, data);
        }
        return data;
    }

    public Boolean decision(long start) {
        return this.decisions.get(Long.valueOf(start));
    }

    public void decide(long start, boolean valid) {
        this.decisions.put(Long.valueOf(start), Boolean.valueOf(valid));
        markDirty();
    }

    /** Records a sanctum once; later calls for the same id are ignored. */
    public void register(long id, Collection<Long> anchors, Collection<Long> containers) {
        Long key = Long.valueOf(id);
        if (this.records.containsKey(key)) {
            return;
        }
        Record r = new Record();
        r.anchors.addAll(anchors);
        r.containers.addAll(containers);
        r.cleared = r.anchors.isEmpty();
        this.records.put(key, r);
        markDirty();
    }

    /** Marks one anchor defeated; true exactly when this cleared the sanctum. */
    public boolean anchorDone(long id, long anchor) {
        Record r = this.records.get(Long.valueOf(id));
        if (r == null || r.cleared || !r.anchors.remove(Long.valueOf(anchor))) {
            return false;
        }
        if (r.anchors.isEmpty()) {
            r.cleared = true;
        }
        markDirty();
        return r.cleared;
    }

    public boolean isCleared(long id) {
        Record r = this.records.get(Long.valueOf(id));
        return r == null || r.cleared;
    }

    public boolean isAnchorDone(long id, long anchor) {
        Record r = this.records.get(Long.valueOf(id));
        return r == null || r.cleared || !r.anchors.contains(Long.valueOf(anchor));
    }

    public List<Long> containers(long id) {
        Record r = this.records.get(Long.valueOf(id));
        return r == null ? new ArrayList<Long>() : new ArrayList<Long>(r.containers);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.decisions.clear();
        this.records.clear();
        for (long v : longs(nbt.getTagList("Valid", TAG_LONG))) {
            this.decisions.put(Long.valueOf(v), Boolean.TRUE);
        }
        for (long v : longs(nbt.getTagList("Invalid", TAG_LONG))) {
            this.decisions.put(Long.valueOf(v), Boolean.FALSE);
        }
        NBTTagList list = nbt.getTagList("Sanctums", TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound c = list.getCompoundTagAt(i);
            Record r = new Record();
            r.anchors.addAll(longs(c.getTagList("Anchors", TAG_LONG)));
            r.containers.addAll(longs(c.getTagList("Containers", TAG_LONG)));
            r.cleared = c.getBoolean("Cleared");
            this.records.put(Long.valueOf(c.getLong("Id")), r);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList valid = new NBTTagList();
        NBTTagList invalid = new NBTTagList();
        for (Map.Entry<Long, Boolean> e : this.decisions.entrySet()) {
            (e.getValue().booleanValue() ? valid : invalid).appendTag(new NBTTagLong(e.getKey().longValue()));
        }
        nbt.setTag("Valid", valid);
        nbt.setTag("Invalid", invalid);
        NBTTagList sanctums = new NBTTagList();
        for (Map.Entry<Long, Record> e : this.records.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong("Id", e.getKey().longValue());
            c.setTag("Anchors", toList(e.getValue().anchors));
            c.setTag("Containers", toList(e.getValue().containers));
            c.setBoolean("Cleared", e.getValue().cleared);
            sanctums.appendTag(c);
        }
        nbt.setTag("Sanctums", sanctums);
        return nbt;
    }

    private static List<Long> longs(NBTTagList list) {
        List<Long> out = new ArrayList<Long>();
        for (int i = 0; i < list.tagCount(); i++) {
            out.add(Long.valueOf(((NBTTagLong) list.get(i)).getLong()));
        }
        return out;
    }

    private static NBTTagList toList(Collection<Long> values) {
        NBTTagList list = new NBTTagList();
        for (Long v : values) {
            list.appendTag(new NBTTagLong(v.longValue()));
        }
        return list;
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumLocks.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import java.util.List;
import java.util.UUID;

import electroblob.wizardry.packet.PacketSyncArcaneLock;
import electroblob.wizardry.packet.WizardryPacketHandler;
import electroblob.wizardry.spell.ArcaneLock;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/**
 * A sanctum's containers wear EBW's own Arcane Lock (tile data {@code arcaneLockOwner}), owned by
 * a UUID derived from the sanctum id, so EBW's handlers refuse opening, breaking and blasting and
 * EBW renders the lock. The lock is lifted - key removed, EBW's sync packet sent - when the last
 * anchor of the sanctum falls, the way EBW's shrine core does it.
 */
public final class SanctumLocks {

    public static final String SANCTUM_KEY = "InsaneTweaksSanctum";
    private static final long OWNER_SALT = 0x53414E4354554DL;
    private static final double SYNC_RANGE = 64.0D;

    private SanctumLocks() {
    }

    public static UUID ownerOf(long sanctumId) {
        return new UUID(sanctumId, OWNER_SALT);
    }

    public static void lock(TileEntity te, long sanctumId) {
        NBTTagCompound data = te.getTileData();
        data.setString(ArcaneLock.NBT_KEY, ownerOf(sanctumId).toString());
        data.setLong(SANCTUM_KEY, sanctumId);
        te.markDirty();
    }

    /** The sanctum id of a sanctum-locked tile entity, or null. */
    public static Long sanctumOf(TileEntity te) {
        NBTTagCompound data = te.getTileData();
        return data.hasKey(SANCTUM_KEY) && data.hasKey(ArcaneLock.NBT_KEY) ? Long.valueOf(data.getLong(SANCTUM_KEY))
                : null;
    }

    public static void unlock(World world, BlockPos pos, TileEntity te) {
        NBTTagCompound data = te.getTileData();
        long id = data.getLong(SANCTUM_KEY);
        data.removeTag(ArcaneLock.NBT_KEY);
        data.removeTag(SANCTUM_KEY);
        te.markDirty();
        WizardryPacketHandler.net.sendToAllAround(new PacketSyncArcaneLock.Message(pos, false, ownerOf(id)),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(), pos.getX() + 0.5D, pos.getY() + 0.5D,
                        pos.getZ() + 0.5D, SYNC_RANGE));
    }

    public static boolean isAnchorDone(World world, long sanctumId, BlockPos anchor) {
        return SanctumWorldData.get(world).isAnchorDone(sanctumId, anchor.toLong());
    }

    /** Records one defeated anchor; lifts every lock of the sanctum when it was the last. */
    public static void anchorDone(World world, long sanctumId, BlockPos anchor) {
        if (world.isRemote || !SanctumWorldData.get(world).anchorDone(sanctumId, anchor.toLong())) {
            return;
        }
        List<Long> containers = SanctumWorldData.get(world).containers(sanctumId);
        BlockPos announce = null;
        for (Long packed : containers) {
            BlockPos pos = BlockPos.fromLong(packed.longValue());
            if (announce == null) {
                announce = pos;
            }
            if (!world.isBlockLoaded(pos)) {
                continue; // unlocked lazily by SanctumLockHandler on first touch
            }
            TileEntity te = world.getTileEntity(pos);
            Long id = te == null ? null : sanctumOf(te);
            if (id != null && id.longValue() == sanctumId) {
                unlock(world, pos, te);
            }
        }
        if (announce == null) {
            announce = anchor;
        }
        for (EntityPlayer player : world.playerEntities) {
            if (player.getDistanceSq(announce) < SYNC_RANGE * SYNC_RANGE) {
                player.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.sanctum.unsealed"), true);
            }
        }
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumLockHandler.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Lazy half of the sanctum lock: a container that was unloaded when its sanctum was cleared is
 * unlocked on first touch, at HIGHEST priority so EBW's lock handlers then see no lock. A still
 * locked container explains itself in the action bar.
 */
public class SanctumLockHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!touch(event.getWorld(), event.getPos()) && !event.getWorld().isRemote) {
            hint(event.getWorld(), event.getPos(), event.getEntityPlayer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        touch(event.getWorld(), event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        touch(event.getWorld(), event.getPos());
    }

    /** True if the block is not (or no longer) sanctum-locked. */
    private static boolean touch(World world, BlockPos pos) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        Long id = te == null ? null : SanctumLocks.sanctumOf(te);
        if (id == null) {
            return true;
        }
        if (SanctumWorldData.get(world).isCleared(id.longValue())) {
            SanctumLocks.unlock(world, pos, te);
            return true;
        }
        return false;
    }

    private static void hint(World world, BlockPos pos, EntityPlayer player) {
        TileEntity te = world.getTileEntity(pos);
        if (te != null && SanctumLocks.sanctumOf(te) != null && player != null) {
            player.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.sanctum.locked"), true);
        }
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumBooks.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import java.util.List;

import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellProperties;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * Rare spell books in a sanctum's EBW bookshelves: per roll a chance for one book, then 50% an
 * Abomination spell, 25% any Master, 25% any Advanced - each drawn from spells enabled for
 * treasure. An empty bucket falls back to Advanced. Deterministic per shelf via SanctumNoise.
 */
public final class SanctumBooks {

    private static List<Spell> abomination;
    private static List<Spell> master;
    private static List<Spell> advanced;

    private SanctumBooks() {
    }

    public static void fill(IInventory shelf, long seed, int x, int y, int z, int rolls, double chance) {
        for (int i = 0; i < rolls; i++) {
            if (SanctumNoise.at(seed, x, y, z, 40 + i) >= chance) {
                continue;
            }
            Spell spell = pick(SanctumNoise.at(seed, x, y, z, 50 + i), SanctumNoise.at(seed, x, y, z, 60 + i));
            if (spell == null) {
                continue;
            }
            for (int slot = 0; slot < shelf.getSizeInventory(); slot++) {
                if (shelf.getStackInSlot(slot).isEmpty()) {
                    shelf.setInventorySlotContents(slot, new ItemStack(WizardryItems.spell_book, 1, spell.metadata()));
                    break;
                }
            }
        }
    }

    private static synchronized void init() {
        if (advanced != null) {
            return;
        }
        abomination = Spell.getSpells(new java.util.function.Predicate<Spell>() {
            @Override
            public boolean test(Spell s) {
                return ModElements.ABOMINATION != null && s.getElement() == ModElements.ABOMINATION && treasure(s);
            }
        });
        master = Spell.getSpells(new java.util.function.Predicate<Spell>() {
            @Override
            public boolean test(Spell s) {
                return s.getTier() == Tier.MASTER && treasure(s);
            }
        });
        advanced = Spell.getSpells(new java.util.function.Predicate<Spell>() {
            @Override
            public boolean test(Spell s) {
                return s.getTier() == Tier.ADVANCED && treasure(s);
            }
        });
    }

    private static boolean treasure(Spell s) {
        return s.isEnabled(SpellProperties.Context.TREASURE);
    }

    private static Spell pick(double bucket, double index) {
        init();
        List<Spell> list = bucket < 0.5D ? abomination : bucket < 0.75D ? master : advanced;
        if (list.isEmpty()) {
            list = advanced;
        }
        if (list.isEmpty()) {
            return null;
        }
        return list.get(Math.min(list.size() - 1, (int) (index * list.size())));
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumBaubles.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/**
 * One random bauble/artefact from any mod for the main chest. Baubles is optional for content,
 * so its type is only named inside {@link Api}, which loads only when Baubles is present.
 */
public final class SanctumBaubles {

    private static List<Item> candidates;

    private SanctumBaubles() {
    }

    public static ItemStack pick(double roll) {
        List<Item> list = candidates();
        if (list.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(list.get(Math.min(list.size() - 1, (int) (roll * list.size()))));
    }

    private static synchronized List<Item> candidates() {
        if (candidates == null) {
            candidates = Loader.isModLoaded("baubles") ? Api.collect() : new ArrayList<Item>();
        }
        return candidates;
    }

    private static final class Api {
        private Api() {
        }

        static List<Item> collect() {
            Set<String> banned = new HashSet<String>(Arrays.asList(ModConfig.arcaneSanctum.baubleBlacklist));
            List<Item> out = new ArrayList<Item>();
            for (Item item : Item.REGISTRY) {
                if (item instanceof baubles.api.IBauble && item.getCreativeTab() != null
                        && item.getRegistryName() != null && !banned.contains(item.getRegistryName().toString())) {
                    out.add(item);
                }
            }
            return out;
        }
    }
}
```

---

### Task 5: Builder, placement, generator, command

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumBuilder.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import java.util.HashSet;
import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.anchor.AnchorPool;
import com.spege.insanetweaks.anchor.TileEntityPetrifiedAnchor;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.ArcaneSanctumCategory;
import com.spege.insanetweaks.init.ModBlocks;
import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.registry.WizardryBlocks;
import electroblob.wizardry.tileentity.TileEntityImbuementAltar;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Writes the part of a {@link SanctumLayout} inside a clip box. Passes: (1) walls - every room in
 * stone brick, then the ritual chamber's shell in sealed stone over any shared plane; (2) every
 * interior except the stairway's, then carves (doorways, stair headroom) to air; (3) seal, dais,
 * altars, pillars, stairs, props, chests, anchors, lights; (4) webs and rubble; (5) shaft and
 * surface ruin. Materials depend on position only (SanctumNoise), so a structure split over many
 * populate calls is identical to one built at once. Containers get the sanctum's Arcane Lock.
 */
public final class SanctumBuilder {

    public static final ResourceLocation MAIN_LOOT = new ResourceLocation(InsaneTweaksMod.MODID, "chests/arcane_sanctum");
    public static final ResourceLocation MINOR_LOOT = new ResourceLocation(InsaneTweaksMod.MODID,
            "chests/arcane_sanctum_minor");
    public static final ResourceLocation FORGE_LOOT = new ResourceLocation(InsaneTweaksMod.MODID,
            "chests/arcane_sanctum_forge");
    public static final ResourceLocation WORKSHOP_LOOT = new ResourceLocation(InsaneTweaksMod.MODID,
            "chests/arcane_sanctum_workshop");

    private static final int FLAGS = 2;
    private static final Set<String> WARNED = new HashSet<String>();

    private SanctumBuilder() {
    }

    public static void build(World world, SanctumLayout l, SanctumBox clip, long sanctumId) {
        Palette p = new Palette();
        for (SanctumLayout.Room room : l.rooms) {
            if (room.type != SanctumLayout.RoomType.CHAMBER) {
                fillWalls(world, l, room.outer, clip, p);
            }
        }
        fill(world, l.rooms.get(0).outer, clip, p.sealed());
        for (SanctumLayout.Room room : l.rooms) {
            SanctumBox in = room.outer.shrink(1);
            if (in != null && room.type != SanctumLayout.RoomType.DESCENT) {
                fill(world, in, clip, Blocks.AIR.getDefaultState());
            }
        }
        for (SanctumBox carve : l.carves) {
            fill(world, carve, clip, Blocks.AIR.getDefaultState());
        }
        contents(world, l, clip, p, sanctumId);
        for (SanctumLayout.Room room : l.rooms) {
            decorate(world, l, room, clip);
        }
        shaft(world, l, clip, p);
    }

    // ---- passes -----------------------------------------------------------------------------

    private static void fillWalls(World world, SanctumLayout l, SanctumBox box, SanctumBox clip, Palette p) {
        SanctumBox c = box.intersection(clip);
        if (c == null) {
            return;
        }
        for (int x = c.minX; x <= c.maxX; x++) {
            for (int y = c.minY; y <= c.maxY; y++) {
                for (int z = c.minZ; z <= c.maxZ; z++) {
                    world.setBlockState(new BlockPos(x, y, z), p.wall(l.seed, x, y, z), FLAGS);
                }
            }
        }
    }

    private static void fill(World world, SanctumBox box, SanctumBox clip, IBlockState state) {
        SanctumBox c = box.intersection(clip);
        if (c == null) {
            return;
        }
        for (int x = c.minX; x <= c.maxX; x++) {
            for (int y = c.minY; y <= c.maxY; y++) {
                for (int z = c.minZ; z <= c.maxZ; z++) {
                    world.setBlockState(new BlockPos(x, y, z), state, FLAGS);
                }
            }
        }
    }

    private static void contents(World world, SanctumLayout l, SanctumBox clip, Palette p, long sanctumId) {
        ArcaneSanctumCategory cfg = ModConfig.arcaneSanctum;
        for (int[] f : l.sealFrame) {
            setIn(world, clip, f[0], f[1], f[2], p.chiseled());
        }
        SanctumBox seal = l.seal.intersection(clip);
        if (seal != null) {
            for (int x = seal.minX; x <= seal.maxX; x++) {
                for (int y = seal.minY; y <= seal.maxY; y++) {
                    for (int z = seal.minZ; z <= seal.maxZ; z++) {
                        boolean centre = x == l.button[0] && y == l.button[1] && z == l.button[2];
                        set(world, x, y, z, centre ? p.button() : p.sealed());
                    }
                }
            }
        }
        fill(world, l.dais, clip, p.chiseled());
        for (int i = 0; i < l.altars.size(); i++) {
            int[] a = l.altars.get(i);
            if (!clip.contains(a[0], a[1], a[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(a[0], a[1], a[2]);
            world.setBlockState(pos, WizardryBlocks.imbuement_altar.getDefaultState(), FLAGS);
            ItemStack stack = i == 4 ? crystal() : isDust(l, a) ? abominationDust() : ItemStack.EMPTY;
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityImbuementAltar && !stack.isEmpty()) {
                ((TileEntityImbuementAltar) te).setStack(stack);
            }
        }
        for (int[] pillar : l.pillars) {
            for (int y = l.floorY2; y <= l.floorY2 + 7; y++) {
                setIn(world, clip, pillar[0], y, pillar[1], p.sealed());
            }
        }
        IBlockState stair = Blocks.STONE_BRICK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING,
                facing(l.stairFacing));
        for (int[] s : l.stairs) {
            setIn(world, clip, s[0], s[1], s[2], stair);
        }
        for (int[] prop : l.props) {
            if (!clip.contains(prop[0], prop[1], prop[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(prop[0], prop[1], prop[2]);
            world.setBlockState(pos, withFacing(p.prop(prop[3]), facing(prop[4])), FLAGS);
            if (prop[3] == SanctumLayout.PROP_EBW_SHELF) {
                TileEntity te = world.getTileEntity(pos);
                if (te instanceof IInventory) {
                    SanctumBooks.fill((IInventory) te, l.seed, prop[0], prop[1], prop[2], cfg.bookshelfBookRolls,
                            cfg.bookshelfBookChance);
                }
                if (te != null) {
                    SanctumLocks.lock(te, sanctumId);
                }
            }
        }
        for (int[] c : l.chests) {
            if (!clip.contains(c[0], c[1], c[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(c[0], c[1], c[2]);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing(c[3])), FLAGS);
            TileEntity te = world.getTileEntity(pos);
            if (!(te instanceof TileEntityChest)) {
                continue;
            }
            TileEntityChest chest = (TileEntityChest) te;
            chest.setLootTable(table(c[4]), SanctumNoise.seedAt(l.seed, c[0], c[1], c[2]));
            if (c[4] == SanctumLayout.CHEST_MAIN && SanctumNoise.at(l.seed, c[0], c[1], c[2], 70) < cfg.mainChestBaubleChance) {
                ItemStack bauble = SanctumBaubles.pick(SanctumNoise.at(l.seed, c[0], c[1], c[2], 71));
                if (!bauble.isEmpty()) {
                    int slot = (int) (SanctumNoise.at(l.seed, c[0], c[1], c[2], 72) * chest.getSizeInventory());
                    chest.setInventorySlotContents(Math.min(slot, chest.getSizeInventory() - 1), bauble);
                }
            }
            SanctumLocks.lock(chest, sanctumId);
        }
        AnchorPool roomPool = AnchorPool.parse(cfg.roomAnchorPool);
        for (int[] a : l.anchors) {
            if (!clip.contains(a[0], a[1], a[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(a[0], a[1], a[2]);
            world.setBlockState(pos, ModBlocks.PETRIFIED_ANCHOR.getDefaultState(), FLAGS);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityPetrifiedAnchor) {
                TileEntityPetrifiedAnchor anchor = (TileEntityPetrifiedAnchor) te;
                anchor.setSanctum(sanctumId);
                if (a[3] == SanctumLayout.POOL_ROOM) {
                    String id = roomPool.pick(SanctumNoise.at(l.seed, a[0], a[1], a[2], 80));
                    if (id != null) {
                        anchor.pinAtGeneration(id);
                    }
                }
            }
        }
        for (int[] light : l.lights) {
            setIn(world, clip, light[0], light[1], light[2], Blocks.GLOWSTONE.getDefaultState());
        }
    }

    /** Webs on top-layer edges everywhere; rubble on the floor of crypts and collapsed rooms. */
    private static void decorate(World world, SanctumLayout l, SanctumLayout.Room room, SanctumBox clip) {
        SanctumBox in = room.outer.shrink(1);
        if (in == null || room.type == SanctumLayout.RoomType.DESCENT) {
            return;
        }
        SanctumBox c = in.intersection(clip);
        if (c == null) {
            return;
        }
        for (int x = c.minX; x <= c.maxX; x++) {
            for (int z = c.minZ; z <= c.maxZ; z++) {
                boolean edge = x == in.minX || x == in.maxX || z == in.minZ || z == in.maxZ;
                for (int y = c.minY; y <= c.maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!world.isAirBlock(pos) || inDoorLine(l, x, z)) {
                        continue;
                    }
                    double n = SanctumNoise.at(l.seed, x, y, z, 7);
                    if (y == in.maxY && edge && n < 0.18D) {
                        world.setBlockState(pos, Blocks.WEB.getDefaultState(), FLAGS);
                    } else if (y == in.minY && room.type == SanctumLayout.RoomType.CRYPT && n < 0.12D) {
                        world.setBlockState(pos, n < 0.05D ? Blocks.BONE_BLOCK.getDefaultState()
                                : Blocks.MOSSY_COBBLESTONE.getDefaultState(), FLAGS);
                    } else if (y == in.minY && room.type == SanctumLayout.RoomType.COLLAPSED && n < 0.3D) {
                        world.setBlockState(pos, n < 0.15D ? Blocks.GRAVEL.getDefaultState()
                                : Blocks.COBBLESTONE.getDefaultState(), FLAGS);
                    }
                }
            }
        }
    }

    /** Shaft walls, ladders, ward and the surface ruin, each from its own column's surface. */
    private static void shaft(World world, SanctumLayout l, SanctumBox clip, Palette p) {
        int sx = l.shaftX;
        int sz = l.shaftZ;
        IBlockState ladder = ModBlocks.ARCANE_LADDER.getDefaultState().withProperty(BlockLadder.FACING,
                facing(l.ladderFacing));
        for (int y = l.floorY; y < l.wardY; y++) {
            setIn(world, clip, l.ladderSupport[0], y, l.ladderSupport[2],
                    p.wall(l.seed, l.ladderSupport[0], y, l.ladderSupport[2]));
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = sx + dx;
                int z = sz + dz;
                if (!clip.contains(x, l.wardY, z)) {
                    continue;
                }
                int surface = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).getY() - 1;
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    ruinCell(world, l, x, z, surface, p);
                } else if (dx == 0 && dz == 0) {
                    for (int y = l.floorY; y <= Math.max(surface, l.wardY + 1); y++) {
                        set(world, x, y, z, y == l.wardY ? ModBlocks.ARCANE_WARD.getDefaultState() : ladder);
                    }
                } else {
                    for (int y = l.wardY; y <= Math.max(surface, l.wardY); y++) {
                        set(world, x, y, z, p.wall(l.seed, x, y, z));
                    }
                }
            }
        }
    }

    private static void ruinCell(World world, SanctumLayout l, int x, int z, int surface, Palette p) {
        double n = SanctumNoise.at(l.seed, x, 0, z, 11);
        if (n < 0.3D) {
            int height = n < 0.12D ? 2 : 1;
            for (int h = 1; h <= height; h++) {
                set(world, x, surface + h, z, p.ruin(l.seed, x, surface + h, z));
            }
        } else if (n < 0.42D) {
            set(world, x, surface, z, Blocks.MOSSY_COBBLESTONE.getDefaultState());
        }
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static ResourceLocation table(int kind) {
        switch (kind) {
            case SanctumLayout.CHEST_MAIN:
                return MAIN_LOOT;
            case SanctumLayout.CHEST_FORGE:
                return FORGE_LOOT;
            case SanctumLayout.CHEST_WORKSHOP:
                return WORKSHOP_LOOT;
            default:
                return MINOR_LOOT;
        }
    }

    private static boolean isDust(SanctumLayout l, int[] altar) {
        for (int[] d : l.dustAltars) {
            if (d[0] == altar[0] && d[1] == altar[1] && d[2] == altar[2]) {
                return true;
            }
        }
        return false;
    }

    /** Doorway approaches stay clear of rubble and webs. */
    private static boolean inDoorLine(SanctumLayout l, int x, int z) {
        for (SanctumBox d : l.carves) {
            if (x >= d.minX - 1 && x <= d.maxX + 1 && z >= d.minZ - 1 && z <= d.maxZ + 1) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack crystal() {
        Item item = Item.getByNameOrId("ebwizardry:magic_crystal");
        return item == null ? ItemStack.EMPTY : new ItemStack(item, 1, 0);
    }

    private static ItemStack abominationDust() {
        Item item = Item.getByNameOrId("ebwizardry:spectral_dust");
        if (item == null || !ModElements.EXTENDED) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, 1, ModElements.ABOMINATION.ordinal());
    }

    private static void set(World world, int x, int y, int z, IBlockState state) {
        world.setBlockState(new BlockPos(x, y, z), state, FLAGS);
    }

    private static void setIn(World world, SanctumBox clip, int x, int y, int z, IBlockState state) {
        if (clip.contains(x, y, z)) {
            set(world, x, y, z, state);
        }
    }

    /** Sets a horizontal "facing" property if the block has one (EBW shelf, ASC stations, anvil...). */
    @SuppressWarnings("unchecked")
    private static IBlockState withFacing(IBlockState state, EnumFacing facing) {
        for (IProperty<?> prop : state.getPropertyKeys()) {
            if ("facing".equals(prop.getName()) && prop.getValueClass() == EnumFacing.class
                    && prop.getAllowedValues().contains(facing)) {
                return state.withProperty((IProperty<EnumFacing>) prop, facing);
            }
        }
        return state;
    }

    private static EnumFacing facing(int index) {
        switch (index) {
            case 0:
                return EnumFacing.NORTH;
            case 1:
                return EnumFacing.EAST;
            case 2:
                return EnumFacing.SOUTH;
            default:
                return EnumFacing.WEST;
        }
    }

    /** Block choices, with id lookups for ASC/EBW blocks and a stone-brick fallback. */
    private static final class Palette {
        private final IBlockState plain = Blocks.STONEBRICK.getDefaultState();
        private final IBlockState mossy = plain.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.MOSSY);
        private final IBlockState cracked = plain.withProperty(BlockStoneBrick.VARIANT, BlockStoneBrick.EnumType.CRACKED);
        private final IBlockState chiseled = plain.withProperty(BlockStoneBrick.VARIANT,
                BlockStoneBrick.EnumType.CHISELED);
        private final IBlockState sealed = lookup("ancientspellcraft:sealed_stone");
        private final IBlockState button = lookup("ancientspellcraft:unseal_button");

        IBlockState wall(long seed, int x, int y, int z) {
            double n = SanctumNoise.at(seed, x, y, z, 3);
            return n < 0.6D ? this.plain : n < 0.85D ? this.mossy : this.cracked;
        }

        IBlockState ruin(long seed, int x, int y, int z) {
            return SanctumNoise.at(seed, x, y, z, 5) < 0.5D ? this.mossy : this.cracked;
        }

        IBlockState chiseled() {
            return this.chiseled;
        }

        IBlockState sealed() {
            return this.sealed;
        }

        IBlockState button() {
            return this.button;
        }

        IBlockState prop(int kind) {
            switch (kind) {
                case SanctumLayout.PROP_EBW_SHELF:
                    return WizardryBlocks.oak_bookshelf.getDefaultState();
                case SanctumLayout.PROP_ENCHANTING_TABLE:
                    return Blocks.ENCHANTING_TABLE.getDefaultState();
                case SanctumLayout.PROP_ARCANE_WORKBENCH:
                    return WizardryBlocks.arcane_workbench.getDefaultState();
                case SanctumLayout.PROP_ARCANE_ANVIL:
                    return lookup("ancientspellcraft:arcane_anvil");
                case SanctumLayout.PROP_ANVIL:
                    return Blocks.ANVIL.getDefaultState();
                case SanctumLayout.PROP_FURNACE:
                    return Blocks.FURNACE.getDefaultState();
                case SanctumLayout.PROP_SCRIBING_DESK:
                    return lookup("ancientspellcraft:scribing_desk");
                case SanctumLayout.PROP_SAGE_DESK:
                    return lookup("ancientspellcraft:sage_desk");
                case SanctumLayout.PROP_SAGE_LECTERN:
                    return lookup("ancientspellcraft:sage_lectern");
                default:
                    return Blocks.CRAFTING_TABLE.getDefaultState();
            }
        }

        private IBlockState lookup(String id) {
            Block block = Block.getBlockFromName(id);
            if (block == null || block == Blocks.AIR) {
                if (WARNED.add(id)) {
                    InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Sunken Arcane Sanctum: block '{}' is missing; "
                            + "using chiseled stone bricks instead.", id);
                }
                return this.chiseled;
            }
            return block.getDefaultState();
        }
    }
}
```

- [ ] **`SanctumPlacement`:**
  - in `isValid`, after computing `ok` and before `data.decide`, add
    `if (ok) { register(world, key, l); }`;
  - add the method:

```java
    /** Records a sanctum's anchors and containers for the Arcane Lock (idempotent). */
    public static void register(World world, long sanctumId, SanctumLayout l) {
        java.util.List<Long> anchors = new java.util.ArrayList<Long>();
        for (int[] a : l.anchors) {
            anchors.add(Long.valueOf(new BlockPos(a[0], a[1], a[2]).toLong()));
        }
        java.util.List<Long> containers = new java.util.ArrayList<Long>();
        for (int[] c : l.chests) {
            containers.add(Long.valueOf(new BlockPos(c[0], c[1], c[2]).toLong()));
        }
        for (int[] p : l.props) {
            if (p[3] == SanctumLayout.PROP_EBW_SHELF) {
                containers.add(Long.valueOf(new BlockPos(p[0], p[1], p[2]).toLong()));
            }
        }
        SanctumWorldData.get(world).register(sanctumId, anchors, containers);
    }
```
- [ ] **`SanctumGenerator`:** the build call becomes `SanctumBuilder.build(world, layout, clip, ChunkPos.asLong(start[0], start[1]));`, with `import net.minecraft.util.math.ChunkPos;`.
- [ ] **`CommandInsaneTweaks`:**
  - in the `place` branch, before `SanctumBuilder.build`, add
    `com.spege.insanetweaks.worldgen.sanctum.SanctumPlacement.register(world, layout.seed, layout);`;
  - the build call becomes `…build(world, layout, new SanctumBox(…), layout.seed);`;
  - the result message reports `layout.floorY` (level 1) and `layout.floorY2` (ritual level);
  - `sendHelp` gets, inside the OP block:

```java
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks sanctum place\u00A77 - Build a Sunken Arcane Sanctum here"));
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks sanctum nearest\u00A77 - Locate the nearest sanctum site"));
```
  - and the class gets the override:

```java
    @Override
    @Nonnull
    public java.util.List<String> getTabCompletions(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
            @Nonnull String[] args, @javax.annotation.Nullable net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "grantbook", "propertybook", "codexpool", "sanctum", "help");
        }
        if (args.length == 2 && "sanctum".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "place", "nearest");
        }
        return java.util.Collections.<String>emptyList();
    }
```
- [ ] **`InsaneTweaksMod`** (own hunks only):
  - next to `AnchorLeashHandler` registration add
    `MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.worldgen.sanctum.SanctumLockHandler());`;
  - next to the two sanctum `LootTableList.register` calls, add `FORGE_LOOT` and `WORKSHOP_LOOT`.
- [ ] **`ArcaneLadderHandler`:** chime. After the `fallDistance` line add:

```java
        if (!player.world.isRemote && player.ticksExisted % 8 == 0 && Math.abs(player.posY - player.prevPosY) > 0.02D) {
            player.world.playSound(null, player.posX, player.posY, player.posZ, net.minecraft.init.SoundEvents.BLOCK_NOTE_CHIME,
                    net.minecraft.util.SoundCategory.PLAYERS, 0.25F, 1.4F + player.getRNG().nextFloat() * 0.5F);
        }
```
- [ ] Run `:insanetweaks:test :insanetweaks:compileJava` until green. Commit in logical steps: layout+tests; anchors; locks/books/baubles; builder/placement/generator/command; config/lang/loot.

---

### Task 6: Version, verify, deploy

- [ ] Bump the version to `1.25.0` (`build.gradle` and `InsaneTweaksMod.VERSION`). Run test and build.
- [ ] Bytecode checks:
  - side safety (0 client references) for `anchor/*`, `worldgen/sanctum/*` and `InsaneTweaksMod`;
  - `SanctumBaubles` itself has no `baubles/api` reference outside `SanctumBaubles$Api` (`javap -v | grep -c baubles/api`).
- [ ] Check the jar contains the 4 loot tables and the new blockstate.
- [ ] Deploy (the old jar may be locked if the game runs; check first, never leave two jars) and commit `chore(insanetweaks): 1.25.0`.
- [ ] Give the user a test checklist.
