# Sunken Arcane Sanctum Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A seeded, code-generated underground sanctum (ritual chamber with a 3x3 Imbuement Altar circle, petrified-anchor guardians, side rooms), entered through a 1-wide Arcane Ladder shaft that only players can pass (Arcane Ward), and generated chunk by chunk without cascading worldgen.

**Architecture:**
- **Pure Java, unit-tested:** `SanctumBox` (int boxes), `SanctumRegions` (region candidates), `SanctumLayout` (rooms, doors, contents), `SanctumNoise` (per-cell hashing).
- **Placement:** `SanctumBuilder` writes the part of a layout that falls inside one clip box, in three passes. `SanctumGenerator` (`IWorldGenerator`) clips to the vanilla structure populate box `[c*16+8, c*16+23]`. `SanctumPlacement` decides validity once per start (dimension, biome, integrity) and persists it in `SanctumWorldData`.
- **New blocks:** `BlockArcaneWard` and `BlockArcaneLadder`.
- **Handlers:** `ArcaneLadderHandler` (climb speed) and `AnchorLeashHandler` (pull anchor mobs home).

**Tech Stack:** Minecraft 1.12.2 Forge (FG3, Java 8), EBW 4.3.19, Ancient Spellcraft 1.8.3 (both required dependencies), JUnit 4.12, Python PIL for the two textures.

Spec: `docs/superpowers/specs/2026-10-20-sunken-arcane-sanctum-design.md`.

Every Gradle call needs JDK 8:
`./gradlew "-Dorg.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot" …`.

**Deviations from the spec, decided while planning:**

1. **Unseal button placement.** `ancientspellcraft:unseal_button` is a plain full `Block` (no facing), and on activation it unseals its **six neighbours**. It therefore sits at the **centre of the 3×3 plug**: 8 `sealed_stone` around it, with unsealing spreading from there.
2. **Ward position.** The ward is at the **bottom** of the shaft (the vestibule-ceiling hole cell), not at H−2.
   - Shaft walls are built up to each wall column's own surface height, because a writer box may not hold the shaft column. A ward near the surface could therefore be bypassed through a side gap on sloped terrain. A ward at the bottom cannot.
   - The surface hole simply shows the shaft. The shimmer is visible from inside the vestibule.
3. **Shaft walls and the ruin ring follow their own columns.** They use the surface height of their own column, never the shaft column's, so a writer never reads terrain outside its own generated chunks.
4. **Footprint.** The footprint is ±26 blocks around O, with corridor `L ∈ [8,12]` instead of `[10,16]`; the spec's ±24 does not fit five slots. Writers within ±3 chunks of the start are checked.
5. **Room decoration.** `CRYPT` uses bone blocks instead of skulls (skulls need a tile entity with rotation). No vines.

---

## File map

All paths under `insanetweaks/src/main/java/com/spege/insanetweaks/` unless noted.

| File | Responsibility |
|---|---|
| `worldgen/sanctum/SanctumBox.java` | Pure inclusive int box |
| `worldgen/sanctum/SanctumRegions.java` | Pure region → candidate start chunk |
| `worldgen/sanctum/SanctumNoise.java` | Pure per-cell hash → [0,1) |
| `worldgen/sanctum/SanctumLayout.java` | Pure seeded layout (rooms, doors, seal, shaft, contents) |
| `worldgen/sanctum/SanctumBuilder.java` | Writes a layout clipped to a box |
| `worldgen/sanctum/SanctumPlacement.java` | Dimension/biome/integrity, layout cache |
| `worldgen/sanctum/SanctumWorldData.java` | Persisted per-start decisions |
| `worldgen/sanctum/SanctumGenerator.java` | `IWorldGenerator` |
| `blocks/BlockArcaneWard.java`, `blocks/BlockArcaneLadder.java` | New blocks |
| `events/ArcaneLadderHandler.java` | Climb/descent speed |
| `anchor/AnchorLeash.java` + `anchor/AnchorLeashHandler.java` | Tag + pull home |
| `config/categories/ArcaneSanctumCategory.java` (+ fields in Tweaks/Entities) | Config |
| `init/ModBlocks.java`, `InsaneTweaksMod.java`, `commands/CommandInsaneTweaks.java` | Registration |
| `src/main/resources/assets/insanetweaks/...` | Blockstates, models, textures, loot, lang |
| `src/test/java/com/spege/insanetweaks/worldgen/sanctum/*Test.java` | JUnit |

---

### Task 1: Pure geometry, regions and noise (TDD)

- [ ] **Step 1: Write the failing tests.**

File: `insanetweaks/src/test/java/com/spege/insanetweaks/worldgen/sanctum/SanctumBoxTest.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SanctumBoxTest {

    @Test
    public void cornersAreNormalised() {
        SanctumBox b = new SanctumBox(5, 6, 7, 1, 2, 3);
        assertEquals(1, b.minX);
        assertEquals(7, b.maxZ);
    }

    @Test
    public void touchingBoxesIntersect() {
        assertTrue(new SanctumBox(0, 0, 0, 4, 4, 4).intersects(new SanctumBox(4, 4, 4, 8, 8, 8)));
        assertFalse(new SanctumBox(0, 0, 0, 4, 4, 4).intersects(new SanctumBox(5, 0, 0, 8, 4, 4)));
    }

    @Test
    public void intersectionClipsOrIsNull() {
        SanctumBox c = new SanctumBox(0, 0, 0, 10, 10, 10).intersection(new SanctumBox(8, 2, -5, 20, 3, 2));
        assertEquals(new SanctumBox(8, 2, 0, 10, 3, 2), c);
        assertNull(new SanctumBox(0, 0, 0, 1, 1, 1).intersection(new SanctumBox(3, 3, 3, 4, 4, 4)));
    }

    @Test
    public void shrinkGivesTheInteriorOrNull() {
        assertEquals(new SanctumBox(1, 1, 1, 3, 3, 3), new SanctumBox(0, 0, 0, 4, 4, 4).shrink(1));
        assertNull(new SanctumBox(0, 0, 0, 1, 4, 4).shrink(1));
    }

    @Test
    public void containsIsInclusive() {
        SanctumBox b = new SanctumBox(0, 0, 0, 2, 2, 2);
        assertTrue(b.contains(2, 2, 2));
        assertFalse(b.contains(3, 2, 2));
    }
}
```

File: `insanetweaks/src/test/java/com/spege/insanetweaks/worldgen/sanctum/SanctumRegionsTest.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SanctumRegionsTest {

    @Test
    public void regionOfFloorsNegatives() {
        assertEquals(0, SanctumRegions.regionOf(31, 32));
        assertEquals(-1, SanctumRegions.regionOf(-1, 32));
        assertEquals(-1, SanctumRegions.regionOf(-32, 32));
        assertEquals(-2, SanctumRegions.regionOf(-33, 32));
    }

    @Test
    public void candidatesAreDeterministic() {
        assertArrayEquals(SanctumRegions.candidate(42L, 3, -7, 32, 4, 1.0D),
                SanctumRegions.candidate(42L, 3, -7, 32, 4, 1.0D));
    }

    @Test
    public void candidatesStayInsideTheRegionMargin() {
        for (int rx = -5; rx <= 5; rx++) {
            for (int rz = -5; rz <= 5; rz++) {
                int[] c = SanctumRegions.candidate(1234L, rx, rz, 32, 4, 1.0D);
                assertNotNull(c);
                assertTrue(c[0] >= rx * 32 + 4 && c[0] <= rx * 32 + 27);
                assertTrue(c[1] >= rz * 32 + 4 && c[1] <= rz * 32 + 27);
            }
        }
    }

    @Test
    public void chanceZeroNeverPlacesAndOneAlwaysDoes() {
        for (int r = 0; r < 50; r++) {
            assertNull(SanctumRegions.candidate(99L, r, r, 32, 4, 0.0D));
            assertNotNull(SanctumRegions.candidate(99L, r, r, 32, 4, 1.0D));
        }
    }

    @Test
    public void anOversizedMarginIsClamped() {
        int[] c = SanctumRegions.candidate(7L, 0, 0, 8, 10, 1.0D);
        assertTrue(c[0] >= 0 && c[0] < 8 && c[1] >= 0 && c[1] < 8);
    }
}
```

File: `insanetweaks/src/test/java/com/spege/insanetweaks/worldgen/sanctum/SanctumNoiseTest.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SanctumNoiseTest {

    @Test
    public void sameInputSameValue() {
        assertEquals(SanctumNoise.at(5L, 1, 2, 3, 0), SanctumNoise.at(5L, 1, 2, 3, 0), 0.0D);
    }

    @Test
    public void valuesAreInUnitRangeAndSpread() {
        double sum = 0.0D;
        int n = 0;
        for (int x = -20; x < 20; x++) {
            for (int z = -20; z < 20; z++) {
                double v = SanctumNoise.at(77L, x, 30, z, 1);
                assertTrue(v >= 0.0D && v < 1.0D);
                sum += v;
                n++;
            }
        }
        double mean = sum / n;
        assertTrue("mean " + mean, mean > 0.4D && mean < 0.6D);
    }
}
```

- [ ] **Step 2:** `./gradlew … :insanetweaks:test`. Expected: compile FAIL.

- [ ] **Step 3: Implement.**

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumBox.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

/** An inclusive integer box. Pure Java, so the layout maths is unit-tested. */
public final class SanctumBox {

    public final int minX;
    public final int minY;
    public final int minZ;
    public final int maxX;
    public final int maxY;
    public final int maxZ;

    public SanctumBox(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean intersects(SanctumBox o) {
        return this.minX <= o.maxX && this.maxX >= o.minX && this.minY <= o.maxY && this.maxY >= o.minY
                && this.minZ <= o.maxZ && this.maxZ >= o.minZ;
    }

    /** The overlap, or null when the boxes do not intersect. */
    public SanctumBox intersection(SanctumBox o) {
        if (!intersects(o)) {
            return null;
        }
        return new SanctumBox(Math.max(this.minX, o.minX), Math.max(this.minY, o.minY), Math.max(this.minZ, o.minZ),
                Math.min(this.maxX, o.maxX), Math.min(this.maxY, o.maxY), Math.min(this.maxZ, o.maxZ));
    }

    public SanctumBox union(SanctumBox o) {
        return new SanctumBox(Math.min(this.minX, o.minX), Math.min(this.minY, o.minY), Math.min(this.minZ, o.minZ),
                Math.max(this.maxX, o.maxX), Math.max(this.maxY, o.maxY), Math.max(this.maxZ, o.maxZ));
    }

    /** The box shrunk by {@code n} on every side (a room's interior), or null if nothing is left. */
    public SanctumBox shrink(int n) {
        if (this.maxX - this.minX < 2 * n || this.maxY - this.minY < 2 * n || this.maxZ - this.minZ < 2 * n) {
            return null;
        }
        return new SanctumBox(this.minX + n, this.minY + n, this.minZ + n, this.maxX - n, this.maxY - n, this.maxZ - n);
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SanctumBox)) {
            return false;
        }
        SanctumBox b = (SanctumBox) o;
        return b.minX == this.minX && b.minY == this.minY && b.minZ == this.minZ && b.maxX == this.maxX
                && b.maxY == this.maxY && b.maxZ == this.maxZ;
    }

    @Override
    public int hashCode() {
        int h = this.minX;
        h = 31 * h + this.minY;
        h = 31 * h + this.minZ;
        h = 31 * h + this.maxX;
        h = 31 * h + this.maxY;
        return 31 * h + this.maxZ;
    }

    @Override
    public String toString() {
        return "[" + this.minX + "," + this.minY + "," + this.minZ + " -> " + this.maxX + "," + this.maxY + ","
                + this.maxZ + "]";
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumRegions.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import java.util.Random;

/**
 * Where sanctums may start. The world is cut into square regions of {@code size} chunks; each
 * region has one candidate start chunk seeded from the world seed, kept {@code margin} chunks from
 * the region edges, and used with {@code chance}. Pure Java, deterministic, unit-tested.
 */
public final class SanctumRegions {

    private static final long SALT = 0x5A6C7D1E2F3B4C5DL;
    private static final long LAYOUT_SALT = 0x2B992DDFA23249D6L;

    private SanctumRegions() {
    }

    public static int regionOf(int chunk, int size) {
        return Math.floorDiv(chunk, size);
    }

    /** {chunkX, chunkZ} of the region's start, or null when the region rolled no sanctum. */
    public static int[] candidate(long worldSeed, int regionX, int regionZ, int size, int margin, double chance) {
        int m = Math.max(0, Math.min(margin, (size - 1) / 2));
        Random r = new Random(mix(worldSeed, regionX, regionZ, SALT));
        double roll = r.nextDouble();
        int span = Math.max(1, size - 2 * m);
        int cx = regionX * size + m + r.nextInt(span);
        int cz = regionZ * size + m + r.nextInt(span);
        return roll < chance ? new int[] { cx, cz } : null;
    }

    /** The layout seed of a start, independent of the region size. */
    public static long layoutSeed(long worldSeed, int chunkX, int chunkZ) {
        return mix(worldSeed, chunkX, chunkZ, LAYOUT_SALT);
    }

    private static long mix(long seed, int a, int b, long salt) {
        return seed ^ (a * 341873128712L) ^ (b * 132897987541L) ^ salt;
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumNoise.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

/**
 * Per-cell randomness that does not depend on the order chunks are generated in: a hash of
 * (seed, x, y, z, channel) mapped to [0, 1). Pure Java.
 */
public final class SanctumNoise {

    private SanctumNoise() {
    }

    public static double at(long seed, int x, int y, int z, int channel) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 27) * 0xBF58476D1CE4E5B9L;
        h ^= y * 0x94D049BB133111EBL;
        h = Long.rotateLeft(h, 31) * 0xD6E8FEB86659FD93L;
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h = Long.rotateLeft(h, 29) * 0x165667B19E3779F9L;
        h ^= channel * 0x27D4EB2F165667C5L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** A seed for one cell, e.g. a chest's loot seed. */
    public static long seedAt(long seed, int x, int y, int z) {
        return Double.doubleToLongBits(at(seed, x, y, z, 99)) ^ seed;
    }
}
```

- [ ] **Step 4:** Run the tests. Expected: they pass.
- [ ] **Step 5:** Commit `feat(insanetweaks): sanctum box, region and noise maths`.

---

### Task 2: SanctumLayout (TDD)

Geometry reference, in the **local frame** (corridor along local +x, F = floorY), with outer boxes:

- **Chamber:** x −6..6, y F−1..F+7, z −6..6. Interior y F..F+6.
- **Corridor:** x 6..6+L, y F−1..F+4, z −2..2.
- **Vestibule:** x 6+L..14+L, y F−1..F+5, z −4..4.
- **Corridor→vestibule doorway:** x 6+L, y F..F+2, z −1..1.
- **Seal plug:** x 6, y F..F+2, z −1..1. The button is at its centre (6, F+1, 0).
- **Shaft:** column sx = 10+L, z 0. Ward at (sx, F+5, 0). Ladders at (sx, F..F+4, 0) and from F+6 up to the surface, facing local N, attached to the wall at local +z. Support column at (sx, F..F+4, 1).
- **Slots:**

  | Slot | Connector | Room |
  |---|---|---|
  | `CH_N` | z −9..−6 | z −17..−9 |
  | `CH_S` | z 6..9 | z 9..17 |
  | `CH_W` | x −9..−6 | x −17..−9 |
  | `CO_N` | x xm−2..xm+2, z −5..−2 | x xm−4..xm+4, z −13..−5 |
  | `CO_S` | x xm−2..xm+2, z 2..5 | x xm−4..xm+4, z 5..13 |

  - `CH_N`/`CH_S` connectors are x −2..2; their rooms are x −4..4, y F−1..F+5.
  - `CH_W` connector is z −2..2; its room is z −4..4.
  - `xm = 6 + L/2`.
  - Connectors span y F−1..F+4, with a doorway at each end (3 wide, y F..F+2).

The rotation from local to world, for corridor direction d (0 N, 1 E, 2 S, 3 W), maps `(lx,lz)` to:

| d | world offset |
|---|---|
| E | `(lx, lz)` |
| S | `(−lz, lx)` |
| W | `(−lx, −lz)` |
| N | `(lz, −lx)` |

Facing index (0 N, 1 E, 2 S, 3 W) becomes `(local + rot) % 4`, with rot E=0, S=1, W=2, N=3.

- [ ] **Step 1: Write the failing test.**

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
```

- [ ] **Step 2:** Run it. Expected: compile FAIL.

- [ ] **Step 3: Implement.**

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumLayout.java`
```java
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
```

- [ ] **Step 4:** Run the tests. Expected: all pass.
- [ ] **Step 5:** Commit `feat(insanetweaks): seeded sanctum layout`.

---

### Task 3: Config

- [ ] **Create** File: `insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/ArcaneSanctumCategory.java`
```java
package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class ArcaneSanctumCategory {

    @Config.Comment("Generate Sunken Arcane Sanctums. Registers the world generator, so it needs a restart.")
    @Config.Name("Enabled")
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment({ "Dimensions sanctums generate in. 150 is always refused (no-SRP dimension).",
            "Nether and End get their own themes later; listing them now uses the overworld theme." })
    @Config.Name("Dimensions")
    public int[] dimensions = { 0 };

    @Config.Comment("Region size in chunks. Each region holds at most one sanctum.")
    @Config.Name("Region Size")
    @Config.RangeInt(min = 8, max = 128)
    public int regionSize = 32;

    @Config.Comment("Chance (0-1) that a region holds a sanctum.")
    @Config.Name("Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double chance = 0.5;

    @Config.Name("Floor Y Min")
    @Config.RangeInt(min = 6, max = 120)
    public int floorYMin = 18;

    @Config.Name("Floor Y Max")
    @Config.RangeInt(min = 6, max = 120)
    public int floorYMax = 26;

    @Config.Comment("BiomeDictionary types a sanctum's centre may not be in.")
    @Config.Name("Excluded Biome Types")
    public String[] excludedBiomeTypes = { "OCEAN", "RIVER", "BEACH" };
}
```

- [ ] **Modify `ModConfig`:** add the field after `tweaks`:
```java
    @Config.Name("arcaneSanctum")
    @Config.LangKey("config.insanetweaks.category.arcane_sanctum")
    @Config.Comment("Sunken Arcane Sanctum: the underground ritual-chamber structure.")
    public static final ArcaneSanctumCategory arcaneSanctum = new ArcaneSanctumCategory();
```
  with `import com.spege.insanetweaks.config.categories.ArcaneSanctumCategory;` if the file imports categories one by one; otherwise use the fully-qualified name.

- [ ] **`TweaksCategory`**, after `srpPhaseSource`:
```java
    @Config.Comment("Arcane Ladder: upward speed while climbing (vanilla ladders ~0.2). Read live.")
    @Config.Name("Arcane Ladder Climb Speed")
    @Config.RangeDouble(min = 0.1, max = 1.0)
    public double arcaneLadderClimbSpeed = 0.28;

    @Config.Comment("Arcane Ladder: fastest slide down (vanilla ladders 0.15). Read live.")
    @Config.Name("Arcane Ladder Max Descent")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double arcaneLadderMaxDescent = 0.10;
```

- [ ] **`EntitiesCategory.PetrifiedAnchor`**, after `minPhaseToWake`:
```java
        @Config.Comment({ "A mob that came out of an anchor is pulled back next to it when it strays further",
                "than this many blocks (checked every 2 s). 0 disables the leash. Read live." })
        @Config.Name("Leash Radius")
        @Config.RangeInt(min = 0, max = 256)
        public int leashRadius = 32;
```

- [ ] **Compile and commit** `feat(insanetweaks): sanctum, arcane ladder and leash config`.

---

### Task 4: Blocks, assets, textures

File: `insanetweaks/src/main/java/com/spege/insanetweaks/blocks/BlockArcaneWard.java`
```java
package com.spege.insanetweaks.blocks;

import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Arcane Ward: a translucent seal only players can pass. Every other entity (mobs, items,
 * projectiles) collides with a full box, pathfinding treats it as solid, it cannot be broken or
 * blown up, and players can climb through it like a ladder, so a ward cell inside a ladder shaft
 * does not break the climb. Used to seal the Sunken Arcane Sanctum's only exit.
 */
public class BlockArcaneWard extends Block {

    public BlockArcaneWard() {
        super(Material.BARRIER);
        this.setBlockUnbreakable();
        this.setResistance(6000000.0F);
        this.setSoundType(SoundType.GLASS);
        this.setLightLevel(0.4F);
        this.setUnlocalizedName(InsaneTweaksMod.MODID + ".arcane_ward");
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "arcane_ward"));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
            List<AxisAlignedBB> collidingBoxes, @Nullable Entity entity, boolean isActualState) {
        if (entity instanceof EntityPlayer) {
            return;
        }
        addCollisionBoxToList(pos, entityBox, collidingBoxes, FULL_BLOCK_AABB);
    }

    @Override
    public boolean isLadder(IBlockState state, IBlockAccess world, BlockPos pos, EntityLivingBase entity) {
        return entity instanceof EntityPlayer;
    }

    @Override
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return net.minecraft.init.Items.AIR;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.TRANSLUCENT;
    }

    @SideOnly(Side.CLIENT)
    @SuppressWarnings("deprecation")
    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getBlockState(pos.offset(side)).getBlock() != this && super.shouldSideBeRendered(state, world, pos, side);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (rand.nextInt(3) == 0) {
            world.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + rand.nextDouble(),
                    pos.getY() + rand.nextDouble(), pos.getZ() + rand.nextDouble(), 0.0D, 0.0D, 0.0D);
        }
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/blocks/BlockArcaneLadder.java`
```java
package com.spege.insanetweaks.blocks;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.block.BlockLadder;
import net.minecraft.block.SoundType;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;

/**
 * Arcane Ladder: a vanilla ladder in sealed-stone colours that makes no climbing sound (volume 0,
 * which vanilla scales step sounds by) and is faster and gentler to use (ArcaneLadderHandler).
 */
public class BlockArcaneLadder extends BlockLadder {

    /** Silent: every ladder sound at volume 0. */
    public static final SoundType SILENT = new SoundType(0.0F, 1.0F, SoundEvents.BLOCK_LADDER_BREAK,
            SoundEvents.BLOCK_LADDER_STEP, SoundEvents.BLOCK_LADDER_PLACE, SoundEvents.BLOCK_LADDER_HIT,
            SoundEvents.BLOCK_LADDER_FALL);

    public BlockArcaneLadder() {
        super();
        this.setHardness(0.4F);
        this.setSoundType(SILENT);
        this.setUnlocalizedName(InsaneTweaksMod.MODID + ".arcane_ladder");
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "arcane_ladder"));
    }
}
```

**Assets:**

File: `insanetweaks/src/main/resources/assets/insanetweaks/blockstates/arcane_ward.json`
```json
{ "variants": { "normal": { "model": "insanetweaks:arcane_ward" }, "inventory": { "model": "insanetweaks:arcane_ward" } } }
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/models/block/arcane_ward.json`
```json
{ "parent": "block/cube_all", "textures": { "all": "insanetweaks:blocks/arcane_ward" } }
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/models/item/arcane_ward.json`
```json
{ "parent": "insanetweaks:block/arcane_ward" }
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/blockstates/arcane_ladder.json`
```json
{
  "variants": {
    "facing=north": { "model": "insanetweaks:arcane_ladder" },
    "facing=east": { "model": "insanetweaks:arcane_ladder", "y": 90 },
    "facing=south": { "model": "insanetweaks:arcane_ladder", "y": 180 },
    "facing=west": { "model": "insanetweaks:arcane_ladder", "y": 270 }
  }
}
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/models/block/arcane_ladder.json`
```json
{ "parent": "block/ladder", "textures": { "particle": "insanetweaks:blocks/arcane_ladder", "texture": "insanetweaks:blocks/arcane_ladder" } }
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/models/item/arcane_ladder.json`
```json
{ "parent": "item/generated", "textures": { "layer0": "insanetweaks:blocks/arcane_ladder" } }
```

**Textures:** generated with PIL by the script below (run once from the repo root; it writes the two PNGs).

File: `insanetweaks/tools/gen_sanctum_textures.py`
```python
"""Generates the Arcane Ladder and Arcane Ward textures (16x16 RGBA).

The ladder uses the lavender-grey palette of Ancient Spellcraft's sealed_stone (sampled colours
only, no pixels copied) with a faint purple rune line on each rung. The ward is a translucent
violet field with a brighter rune border.
"""
import os
import random
from PIL import Image

OUT = os.path.join("insanetweaks", "src", "main", "resources", "assets", "insanetweaks", "textures", "blocks")
STONE = [(154, 148, 161), (151, 144, 157), (145, 138, 152), (140, 134, 145), (158, 152, 165)]
DARK = (110, 104, 118)
RUNE = (176, 120, 230)


def ladder():
    rnd = random.Random(1337)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in (2, 3, 12, 13):
            px[x, y] = STONE[rnd.randrange(len(STONE))] + (255,)
        px[1, y] = DARK + (255,)
        px[14, y] = DARK + (255,)
    for ry in (1, 5, 9, 13):
        for x in range(1, 15):
            px[x, ry] = DARK + (255,)
            px[x, ry + 1] = STONE[rnd.randrange(len(STONE))] + (255,)
        for x in range(5, 11):
            px[x, ry + 1] = RUNE + (255,)
    im.save(os.path.join(OUT, "arcane_ladder.png"))


def ward():
    rnd = random.Random(4242)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                px[x, y] = (190, 130, 255, 170)
            else:
                v = rnd.randrange(-12, 12)
                px[x, y] = (120 + v, 60 + v, 200 + v, 90)
    for i in range(3, 13):
        px[i, i] = (210, 160, 255, 150)
        px[15 - i, i] = (210, 160, 255, 150)
    im.save(os.path.join(OUT, "arcane_ward.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    ladder()
    ward()
```

- [ ] **`ModBlocks`:** register both blocks, their ItemBlocks and models **before** the sanctuary early return, the same way as `PETRIFIED_ANCHOR`:
  - fields `public static com.spege.insanetweaks.blocks.BlockArcaneWard ARCANE_WARD;` and `public static com.spege.insanetweaks.blocks.BlockArcaneLadder ARCANE_LADDER;`;
  - in `registerBlocks`, after the anchor tile-entity registration:
```java
        ARCANE_WARD = new com.spege.insanetweaks.blocks.BlockArcaneWard();
        ARCANE_WARD.setCreativeTab(CreativeTabs.MISC);
        event.getRegistry().register(ARCANE_WARD);
        ARCANE_LADDER = new com.spege.insanetweaks.blocks.BlockArcaneLadder();
        ARCANE_LADDER.setCreativeTab(CreativeTabs.MISC);
        event.getRegistry().register(ARCANE_LADDER);
```
  - in `registerItemBlocks`, after the anchor item:
```java
        for (Block b : new Block[] { ARCANE_WARD, ARCANE_LADDER }) {
            if (b != null) {
                ItemBlock ib = new ItemBlock(b);
                ib.setRegistryName(b.getRegistryName());
                event.getRegistry().register(ib);
            }
        }
```
  - in `registerModels`, after the anchor model:
```java
        for (Block b : new Block[] { ARCANE_WARD, ARCANE_LADDER }) {
            if (b != null) {
                ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b), 0,
                        new ModelResourceLocation(b.getRegistryName(), "inventory"));
            }
        }
```
- [ ] **Lang** (`en_us.lang`, after `tile.insanetweaks.petrified_anchor.name=…`):
```
tile.insanetweaks.arcane_ward.name=Arcane Ward
tile.insanetweaks.arcane_ladder.name=Arcane Ladder
```
  and after the `config.insanetweaks.category.entities.petrified_anchor` line:
  `config.insanetweaks.category.arcane_sanctum=Sunken Arcane Sanctum`.
- [ ] **Compile and commit** `feat(insanetweaks): arcane ward and arcane ladder blocks`.

---

### Task 5: Ladder handler and anchor leash

File: `insanetweaks/src/main/java/com/spege/insanetweaks/events/ArcaneLadderHandler.java`
```java
package com.spege.insanetweaks.events;

import com.spege.insanetweaks.blocks.BlockArcaneLadder;
import com.spege.insanetweaks.blocks.BlockArcaneWard;
import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Faster, gentler Arcane Ladders. Runs at the END of the player tick, after vanilla's ladder
 * logic, so the motion set here is what the next tick moves by. Both sides: the local player's
 * movement is computed client-side, and the server copy keeps its own motion consistent.
 */
public class ArcaneLadderHandler {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player.isSpectator() || player.capabilities.isFlying || !player.isOnLadder()) {
            return;
        }
        Block block = player.world.getBlockState(new BlockPos(player)).getBlock();
        if (!(block instanceof BlockArcaneLadder) && !(block instanceof BlockArcaneWard)) {
            return;
        }
        double climb = ModConfig.tweaks.arcaneLadderClimbSpeed;
        double descent = ModConfig.tweaks.arcaneLadderMaxDescent;
        if (player.collidedHorizontally) {
            player.motionY = climb;
        } else if (player.motionY < -descent) {
            player.motionY = -descent;
        }
        player.fallDistance = 0.0F;
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
 * The tag an anchor writes into its mob before petrifying it. EBW saves the mob's whole NBT
 * (ForgeData included) into the statue, so the tag survives into the released mob however the
 * statue was broken.
 */
public final class AnchorLeash {

    public static final String HOME = "InsaneTweaksAnchorHome";
    public static final String DIM = "InsaneTweaksAnchorDim";

    private AnchorLeash() {
    }

    public static void tag(Entity mob, BlockPos anchor, int dimension) {
        NBTTagCompound data = mob.getEntityData();
        data.setLong(HOME, anchor.toLong());
        data.setInteger(DIM, dimension);
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/AnchorLeashHandler.java`
```java
package com.spege.insanetweaks.anchor;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Pulls a mob that came out of a Petrified Anchor back next to it when it strays further than
 * {@code leashRadius}: the third layer of the Sunken Arcane Sanctum's seal, and a guarantee for
 * hand-built vaults too. Checked every 40 ticks, server only, tagged entities only.
 */
public class AnchorLeashHandler {

    private static final int CHECK_INTERVAL = 40;

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote || entity.ticksExisted % CHECK_INTERVAL != 0 || !(entity instanceof EntityLiving)) {
            return;
        }
        int radius = ModConfig.entities.petrifiedAnchor.leashRadius;
        if (radius <= 0) {
            return;
        }
        NBTTagCompound data = entity.getEntityData();
        if (!data.hasKey(AnchorLeash.HOME) || data.getInteger(AnchorLeash.DIM) != entity.dimension) {
            return;
        }
        BlockPos home = BlockPos.fromLong(data.getLong(AnchorLeash.HOME));
        if (entity.getDistanceSq(home) <= (double) radius * radius) {
            return;
        }
        double x = home.getX() + 0.5D;
        double y = home.getY() + 1.0D;
        double z = home.getZ() + 0.5D;
        if (!entity.attemptTeleport(x, y, z)) {
            entity.setPositionAndUpdate(x, y, z);
        }
        ((EntityLiving) entity).getNavigator().clearPath();
    }
}
```

- [ ] **Tag the mob in `TileEntityPetrifiedAnchor.tryBuildStatue`**, right before `if (!AnchorStatues.petrify(mob)) {`:
```java
                AnchorLeash.tag(mob, this.pos, this.world.provider.getDimension());
```
- [ ] **Register both handlers in `InsaneTweaksMod.init`**, next to `AnchorStatueGuard`:
```java
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.anchor.AnchorLeashHandler());
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArcaneLadderHandler());
```
- [ ] **Compile and commit** `feat(insanetweaks): arcane ladder movement and anchor leash`.

---

### Task 6: Builder, world data, placement, generator

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumBuilder.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import java.util.HashSet;
import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.init.ModBlocks;
import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.registry.WizardryBlocks;
import electroblob.wizardry.tileentity.TileEntityImbuementAltar;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Writes the part of a {@link SanctumLayout} that lies inside a clip box. Three passes over every
 * room, so doorways cut through neighbours' walls whatever order the chunks arrive in:
 * (1) every outer box becomes wall, (2) every interior and doorway becomes air, (3) contents.
 * Materials come from {@link SanctumNoise}, which depends on position only, so a structure split
 * across several chunks' populate calls is identical to one built at once.
 *
 * <p>The shaft walls and the surface ruin use the surface height of their OWN column: a writer
 * never reads terrain outside the area its populate call may touch.
 */
public final class SanctumBuilder {

    public static final ResourceLocation MAIN_LOOT = new ResourceLocation(InsaneTweaksMod.MODID, "chests/arcane_sanctum");
    public static final ResourceLocation MINOR_LOOT = new ResourceLocation(InsaneTweaksMod.MODID,
            "chests/arcane_sanctum_minor");

    private static final int FLAGS = 2;
    private static final Set<String> WARNED = new HashSet<String>();

    private SanctumBuilder() {
    }

    public static void build(World world, SanctumLayout l, SanctumBox clip) {
        Palette p = new Palette();
        for (SanctumLayout.Room room : l.rooms) {
            fill(world, l, room.outer, clip, p, true);
        }
        for (SanctumLayout.Room room : l.rooms) {
            SanctumBox in = room.outer.shrink(1);
            if (in != null) {
                fill(world, l, in, clip, p, false);
            }
        }
        for (SanctumBox door : l.doorways) {
            fill(world, l, door, clip, p, false);
        }
        contents(world, l, clip, p);
        for (SanctumLayout.Room room : l.rooms) {
            decorate(world, l, room, clip, p);
        }
        shaft(world, l, clip, p);
    }

    // ---- passes -----------------------------------------------------------------------------

    private static void fill(World world, SanctumLayout l, SanctumBox box, SanctumBox clip, Palette p, boolean wall) {
        SanctumBox c = box.intersection(clip);
        if (c == null) {
            return;
        }
        for (int x = c.minX; x <= c.maxX; x++) {
            for (int y = c.minY; y <= c.maxY; y++) {
                for (int z = c.minZ; z <= c.maxZ; z++) {
                    world.setBlockState(new BlockPos(x, y, z), wall ? p.wall(l.seed, x, y, z) : Blocks.AIR.getDefaultState(),
                            FLAGS);
                }
            }
        }
    }

    private static void contents(World world, SanctumLayout l, SanctumBox clip, Palette p) {
        // Dais, then altars on it.
        SanctumBox dais = l.dais.intersection(clip);
        if (dais != null) {
            for (int x = dais.minX; x <= dais.maxX; x++) {
                for (int z = dais.minZ; z <= dais.maxZ; z++) {
                    set(world, x, dais.minY, z, p.chiseled());
                }
            }
        }
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
        // Pillars span the chamber interior height.
        for (int[] pillar : l.pillars) {
            for (int y = l.floorY; y <= l.floorY + 6; y++) {
                if (clip.contains(pillar[0], y, pillar[1])) {
                    set(world, pillar[0], y, pillar[1], p.sealed());
                }
            }
        }
        // Seal plug with the unseal button at its centre.
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
        for (int[] a : l.anchors) {
            if (clip.contains(a[0], a[1], a[2])) {
                set(world, a[0], a[1], a[2], ModBlocks.PETRIFIED_ANCHOR.getDefaultState());
            }
        }
        for (int[] c : l.chests) {
            if (!clip.contains(c[0], c[1], c[2])) {
                continue;
            }
            BlockPos pos = new BlockPos(c[0], c[1], c[2]);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState().withProperty(BlockChest.FACING, facing(c[3])), FLAGS);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityChest) {
                ((TileEntityChest) te).setLootTable(c[4] == 1 ? MAIN_LOOT : MINOR_LOOT,
                        SanctumNoise.seedAt(l.seed, c[0], c[1], c[2]));
            }
        }
        for (int[] light : l.lights) {
            if (clip.contains(light[0], light[1], light[2])) {
                set(world, light[0], light[1], light[2], Blocks.GLOWSTONE.getDefaultState());
            }
        }
    }

    /** Room flavour: webs everywhere, then the side-room type's furniture or rubble. */
    private static void decorate(World world, SanctumLayout l, SanctumLayout.Room room, SanctumBox clip, Palette p) {
        SanctumBox in = room.outer.shrink(1);
        if (in == null || room.type == SanctumLayout.RoomType.CONNECTOR) {
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
                        continue;
                    }
                    if (y != in.minY) {
                        if (room.type == SanctumLayout.RoomType.LIBRARY && edge && y <= in.minY + 2) {
                            world.setBlockState(pos, n < 0.2D ? p.ebwShelf() : Blocks.BOOKSHELF.getDefaultState(), FLAGS);
                        }
                        continue;
                    }
                    if (room.type == SanctumLayout.RoomType.CRYPT && n < 0.12D) {
                        world.setBlockState(pos, n < 0.05D ? Blocks.BONE_BLOCK.getDefaultState()
                                : Blocks.MOSSY_COBBLESTONE.getDefaultState(), FLAGS);
                    } else if (room.type == SanctumLayout.RoomType.COLLAPSED && n < 0.3D) {
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
        // Support column inside the vestibule, under the shaft's ladder wall.
        for (int y = l.floorY; y < l.wardY; y++) {
            if (clip.contains(l.ladderSupport[0], y, l.ladderSupport[2])) {
                set(world, l.ladderSupport[0], y, l.ladderSupport[2], p.wall(l.seed, l.ladderSupport[0], y,
                        l.ladderSupport[2]));
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = sx + dx;
                int z = sz + dz;
                if (!clip.contains(x, l.wardY, z)) {
                    continue;
                }
                int surface = world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).getY() - 1;
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (ring) {
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

    private static boolean isDust(SanctumLayout l, int[] altar) {
        for (int[] d : l.dustAltars) {
            if (d[0] == altar[0] && d[1] == altar[1] && d[2] == altar[2]) {
                return true;
            }
        }
        return false;
    }

    /** The straight line through every doorway stays clear of rubble and shelves. */
    private static boolean inDoorLine(SanctumLayout l, int x, int z) {
        for (SanctumBox d : l.doorways) {
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
        private final IBlockState ebwShelf = WizardryBlocks.oak_bookshelf.getDefaultState();

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

        IBlockState ebwShelf() {
            return this.ebwShelf;
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

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumWorldData.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Per-dimension record of which sanctum starts were judged valid (integrity rule) and which not. */
public class SanctumWorldData extends WorldSavedData {

    public static final String NAME = "insanetweaks_sanctums";
    private static final int TAG_LONG = 4;

    private final Map<Long, Boolean> decisions = new HashMap<Long, Boolean>();

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

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.decisions.clear();
        read(nbt.getTagList("Valid", TAG_LONG), Boolean.TRUE);
        read(nbt.getTagList("Invalid", TAG_LONG), Boolean.FALSE);
    }

    private void read(NBTTagList list, Boolean value) {
        for (int i = 0; i < list.tagCount(); i++) {
            this.decisions.put(Long.valueOf(((NBTTagLong) list.get(i)).getLong()), value);
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
        return nbt;
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumPlacement.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import java.util.LinkedHashMap;
import java.util.Map;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.ArcaneSanctumCategory;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.common.BiomeDictionary;

/** Where sanctums go and whether a start may be built: dimension, biome, integrity, plus a layout cache. */
public final class SanctumPlacement {

    /** Starts further than this (chunks) from a writer chunk cannot reach it (footprint is ±26 blocks). */
    public static final int REACH = 3;
    private static final int MARGIN = 4;
    private static final int NO_SRP_DIMENSION = 150;
    private static final int CACHE_SIZE = 32;

    private static final Map<String, SanctumLayout> CACHE = new LinkedHashMap<String, SanctumLayout>(16, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, SanctumLayout> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private SanctumPlacement() {
    }

    private static ArcaneSanctumCategory cfg() {
        return ModConfig.arcaneSanctum;
    }

    public static boolean dimensionAllowed(World world) {
        int dim = world.provider.getDimension();
        if (dim == NO_SRP_DIMENSION) {
            return false;
        }
        for (int d : cfg().dimensions) {
            if (d == dim) {
                return true;
            }
        }
        return false;
    }

    /** The start chunk of a region, or null. */
    public static int[] candidate(World world, int regionX, int regionZ) {
        return SanctumRegions.candidate(world.getSeed(), regionX, regionZ, cfg().regionSize, MARGIN, cfg().chance);
    }

    public static synchronized SanctumLayout layout(World world, int chunkX, int chunkZ) {
        String key = world.provider.getDimension() + ":" + chunkX + ":" + chunkZ + ":" + world.getSeed();
        SanctumLayout l = CACHE.get(key);
        if (l == null) {
            l = SanctumLayout.generate(SanctumRegions.layoutSeed(world.getSeed(), chunkX, chunkZ), chunkX * 16 + 8,
                    chunkZ * 16 + 8, cfg().floorYMin, cfg().floorYMax);
            CACHE.put(key, l);
        }
        return l;
    }

    public static boolean biomeAllowed(World world, int x, int z) {
        Biome biome = world.getBiomeProvider().getBiome(new BlockPos(x, 64, z));
        for (String name : cfg().excludedBiomeTypes) {
            for (BiomeDictionary.Type type : BiomeDictionary.Type.getAll()) {
                if (type.getName().equalsIgnoreCase(name.trim()) && BiomeDictionary.hasType(biome, type)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Decided once per start, at its first writer: the biome must be allowed and no OTHER writer
     * chunk of the footprint may have been populated already (else part of the structure would be
     * missing). Persisted, so later writers only read it.
     */
    public static boolean isValid(World world, int startX, int startZ, SanctumLayout l, int writerX, int writerZ) {
        SanctumWorldData data = SanctumWorldData.get(world);
        long key = ChunkPos.asLong(startX, startZ);
        Boolean decided = data.decision(key);
        if (decided != null) {
            return decided.booleanValue();
        }
        boolean ok = biomeAllowed(world, l.originX, l.originZ) && integrityOk(world, l, writerX, writerZ);
        data.decide(key, ok);
        return ok;
    }

    private static boolean integrityOk(World world, SanctumLayout l, int writerX, int writerZ) {
        if (!(world.getChunkProvider() instanceof ChunkProviderServer)) {
            return false;
        }
        ChunkProviderServer provider = (ChunkProviderServer) world.getChunkProvider();
        int minWX = Math.floorDiv(l.bounds.minX - 8, 16);
        int maxWX = Math.floorDiv(l.bounds.maxX - 8, 16);
        int minWZ = Math.floorDiv(l.bounds.minZ - 8, 16);
        int maxWZ = Math.floorDiv(l.bounds.maxZ - 8, 16);
        for (int wx = minWX; wx <= maxWX; wx++) {
            for (int wz = minWZ; wz <= maxWZ; wz++) {
                if ((wx == writerX && wz == writerZ) || !provider.isChunkGeneratedAt(wx, wz)) {
                    continue;
                }
                Chunk chunk = provider.getLoadedChunk(wx, wz);
                if (chunk == null || chunk.isTerrainPopulated()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The populate box of a writer chunk: the vanilla structure convention, offset by 8. */
    public static SanctumBox writerBox(int chunkX, int chunkZ) {
        return new SanctumBox(chunkX * 16 + 8, 0, chunkZ * 16 + 8, chunkX * 16 + 23, 255, chunkZ * 16 + 23);
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumGenerator.java`
```java
package com.spege.insanetweaks.worldgen.sanctum;

import java.util.Random;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraftforge.fml.common.IWorldGenerator;

/**
 * Builds every sanctum chunk by chunk: each populate call writes only the part of nearby layouts
 * inside its own populate box, so no neighbouring chunk is ever forced to generate.
 */
public class SanctumGenerator implements IWorldGenerator {

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkGenerator generator,
            IChunkProvider provider) {
        if (!(world instanceof WorldServer) || !SanctumPlacement.dimensionAllowed(world)) {
            return;
        }
        int size = ModConfig.arcaneSanctum.regionSize;
        SanctumBox clip = SanctumPlacement.writerBox(chunkX, chunkZ);
        int minRX = SanctumRegions.regionOf(chunkX - SanctumPlacement.REACH, size);
        int maxRX = SanctumRegions.regionOf(chunkX + SanctumPlacement.REACH, size);
        int minRZ = SanctumRegions.regionOf(chunkZ - SanctumPlacement.REACH, size);
        int maxRZ = SanctumRegions.regionOf(chunkZ + SanctumPlacement.REACH, size);
        for (int rx = minRX; rx <= maxRX; rx++) {
            for (int rz = minRZ; rz <= maxRZ; rz++) {
                int[] start = SanctumPlacement.candidate(world, rx, rz);
                if (start == null || Math.abs(start[0] - chunkX) > SanctumPlacement.REACH
                        || Math.abs(start[1] - chunkZ) > SanctumPlacement.REACH) {
                    continue;
                }
                SanctumLayout layout = SanctumPlacement.layout(world, start[0], start[1]);
                if (!layout.bounds.intersects(clip)
                        || !SanctumPlacement.isValid(world, start[0], start[1], layout, chunkX, chunkZ)) {
                    continue;
                }
                SanctumBuilder.build(world, layout, clip);
            }
        }
    }
}
```

- [ ] **Registration in `InsaneTweaksMod.init`**, next to the other handlers:
```java
        if (com.spege.insanetweaks.config.ModConfig.arcaneSanctum.enabled) {
            net.minecraftforge.fml.common.registry.GameRegistry.registerWorldGenerator(
                    new com.spege.insanetweaks.worldgen.sanctum.SanctumGenerator(), 60);
        }
```
  And next to the other `LootTableList.register` lines:
```java
        net.minecraft.world.storage.loot.LootTableList.register(
                com.spege.insanetweaks.worldgen.sanctum.SanctumBuilder.MAIN_LOOT);
        net.minecraft.world.storage.loot.LootTableList.register(
                com.spege.insanetweaks.worldgen.sanctum.SanctumBuilder.MINOR_LOOT);
```

**Loot tables:**

File: `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/chests/arcane_sanctum.json`
```json
{
  "pools": [
    { "name": "crystals", "rolls": 1, "entries": [
      { "type": "item", "name": "ebwizardry:magic_crystal", "weight": 1,
        "functions": [ { "function": "set_count", "count": { "min": 3, "max": 8 } } ] } ] },
    { "name": "abomination_dust", "rolls": 1, "entries": [
      { "type": "item", "name": "ebwizardry:spectral_dust", "weight": 1,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 3 } },
                       { "function": "insanetweaks:abomination_meta" } ] } ] },
    { "name": "nucleus", "rolls": 1, "entries": [
      { "type": "item", "name": "insanetweaks:magic_nucleus", "weight": 2 },
      { "type": "empty", "weight": 3 } ] },
    { "name": "wizard_tower", "rolls": 1, "entries": [
      { "type": "loot_table", "name": "ebwizardry:chests/wizard_tower", "weight": 1 } ] }
  ]
}
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/chests/arcane_sanctum_minor.json`
```json
{
  "pools": [
    { "name": "crystals", "rolls": 1, "entries": [
      { "type": "item", "name": "ebwizardry:magic_crystal", "weight": 1,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 3 } } ] } ] },
    { "name": "scraps", "rolls": { "min": 1, "max": 2 }, "entries": [
      { "type": "item", "name": "minecraft:paper", "weight": 2,
        "functions": [ { "function": "set_count", "count": { "min": 1, "max": 3 } } ] },
      { "type": "item", "name": "minecraft:book", "weight": 1 },
      { "type": "item", "name": "minecraft:gold_nugget", "weight": 2,
        "functions": [ { "function": "set_count", "count": { "min": 2, "max": 6 } } ] } ] },
    { "name": "dust", "rolls": 1, "entries": [
      { "type": "item", "name": "ebwizardry:spectral_dust", "weight": 1,
        "functions": [ { "function": "insanetweaks:abomination_meta" } ] },
      { "type": "empty", "weight": 3 } ] }
  ]
}
```

- [ ] **Compile and commit** `feat(insanetweaks): sanctum builder, placement and world generator`.

---

### Task 7: Debug commands

- [ ] **`CommandInsaneTweaks`:**
  - add `case "sanctum": handleSanctum(server, sender, args); break;`;
  - add `| sanctum` to the usage string;
  - add the method:

```java
    private void handleSanctum(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!sender.canUseCommand(2, getName())) {
            throw new CommandException("commands.generic.permission");
        }
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("/itweaks sanctum <place | nearest>"));
            return;
        }
        net.minecraft.world.World world = sender.getEntityWorld();
        net.minecraft.util.math.BlockPos at = sender.getPosition();
        if ("place".equalsIgnoreCase(args[1])) {
            com.spege.insanetweaks.worldgen.sanctum.SanctumLayout layout =
                    com.spege.insanetweaks.worldgen.sanctum.SanctumLayout.generate(world.rand.nextLong(), at.getX(),
                            at.getZ(), com.spege.insanetweaks.config.ModConfig.arcaneSanctum.floorYMin,
                            com.spege.insanetweaks.config.ModConfig.arcaneSanctum.floorYMax);
            com.spege.insanetweaks.worldgen.sanctum.SanctumBuilder.build(world, layout,
                    new com.spege.insanetweaks.worldgen.sanctum.SanctumBox(layout.bounds.minX, 0, layout.bounds.minZ,
                            layout.bounds.maxX, 255, layout.bounds.maxZ));
            sender.sendMessage(new TextComponentString("Sanctum built: ritual chamber at " + layout.originX + " "
                    + layout.floorY + " " + layout.originZ + ", shaft at " + layout.shaftX + " " + layout.shaftZ));
            return;
        }
        if ("nearest".equalsIgnoreCase(args[1])) {
            int size = com.spege.insanetweaks.config.ModConfig.arcaneSanctum.regionSize;
            int prx = com.spege.insanetweaks.worldgen.sanctum.SanctumRegions.regionOf(at.getX() >> 4, size);
            int prz = com.spege.insanetweaks.worldgen.sanctum.SanctumRegions.regionOf(at.getZ() >> 4, size);
            double best = Double.MAX_VALUE;
            com.spege.insanetweaks.worldgen.sanctum.SanctumLayout found = null;
            for (int rx = prx - 16; rx <= prx + 16; rx++) {
                for (int rz = prz - 16; rz <= prz + 16; rz++) {
                    int[] s = com.spege.insanetweaks.worldgen.sanctum.SanctumPlacement.candidate(world, rx, rz);
                    if (s == null) {
                        continue;
                    }
                    com.spege.insanetweaks.worldgen.sanctum.SanctumLayout l =
                            com.spege.insanetweaks.worldgen.sanctum.SanctumPlacement.layout(world, s[0], s[1]);
                    if (!com.spege.insanetweaks.worldgen.sanctum.SanctumPlacement.biomeAllowed(world, l.originX,
                            l.originZ)) {
                        continue;
                    }
                    double d = at.distanceSq(l.shaftX, at.getY(), l.shaftZ);
                    if (d < best) {
                        best = d;
                        found = l;
                    }
                }
            }
            if (found == null || !com.spege.insanetweaks.worldgen.sanctum.SanctumPlacement.dimensionAllowed(world)) {
                sender.sendMessage(new TextComponentString("No sanctum candidate within 16 regions in this dimension."));
            } else {
                sender.sendMessage(new TextComponentString("Nearest sanctum shaft: " + found.shaftX + " ~ " + found.shaftZ
                        + " (floor Y " + found.floorY + ", " + (int) Math.sqrt(best) + " blocks). Generated only if its "
                        + "chunks were new when first reached."));
            }
            return;
        }
        sender.sendMessage(new TextComponentString("/itweaks sanctum <place | nearest>"));
    }
```
  (`TextComponentString` is already imported in the command; if not, import `net.minecraft.util.text.TextComponentString`.)
- [ ] **Compile and commit** `feat(insanetweaks): /itweaks sanctum place|nearest`.

---

### Task 8: Version, build, verify, deploy

- [ ] Bump the version to `1.24.0` (`build.gradle` and `InsaneTweaksMod.VERSION`).
- [ ] Run `:insanetweaks:test :insanetweaks:build`. Expected: green.
- [ ] Bytecode checks:
  - `javap -v` over `worldgen/sanctum/*`, `blocks/BlockArcane*`, `anchor/AnchorLeash*`, `events/ArcaneLadderHandler`: client references may appear only in `BlockArcaneWard`, inside the `@SideOnly` methods `getBlockLayer`, `shouldSideBeRendered` and `randomDisplayTick`. Check that with `javap -p -v` that their `RuntimeVisibleAnnotations` resolve to `SideOnly`.
  - The jar contains both loot tables, the two textures, the blockstates and the models.
- [ ] Deploy (replace the old jar; check that the game is closed first, otherwise the old jar is locked).
- [ ] Commit `chore(insanetweaks): 1.24.0 - sunken arcane sanctum`.
- [ ] Give the user the spec §11 in-game checklist.
