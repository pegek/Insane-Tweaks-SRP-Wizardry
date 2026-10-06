# Corrupted Sapling Defense Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the passive 20-minute Corrupted Sapling into an ~8 minute, 4-wave defense event with real parasite targeting, site rules, growth-scaled stats and a boss bar, and shorten the fragment grind (insanetweaks 1.21.0).

**Architecture:** The sapling entity owns the whole event as a state machine persisted in its NBT. Pure arithmetic (timeline, stat interpolation, wave size, fragment pity) lives in Minecraft-free classes that are unit-tested on a plain JVM. Site validation, wave spawning/luring and SRP access live in small helpers under `entities/sapling/`, with every SRP type isolated in a private nested class so the mod still loads without SRParasites.

**Tech Stack:** Minecraft 1.12.2 Forge (FG3, Java 8, MCP snapshot 20171003), SRParasites 1.10.9 (`EntityParasiteBase.setAttackTarget(EntityLivingBase, int)`), JUnit 4.12 for the pure classes.

**Spec:** `docs/superpowers/specs/2026-10-06-corrupted-sapling-defense-design.md`

---

## Conventions for every task

- Working directory: repo root `E:\Isuth\modDev`. Branch: `feat/reskilltweaks-optional-deps` (the current development line).
- Package root below: `insanetweaks/src/main/java/com/spege/insanetweaks/` — written as `…/` in file paths.
- **Gradle needs JDK 8.** Every gradle command below includes
  `"-Dorg.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot"`; written as `$JDK8` for brevity:
  ```bash
  JDK8="-Dorg.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot"
  ```
- All code, comments, lang strings and log lines in **English**. Logs use the content prefix `[InsaneTweaks]` through `InsaneTweaksMod.LOGGER`.
- Java 8 only: no lambdas in mixins (there are none here), no `var`. `@SuppressWarnings("null")` only where the surrounding code already uses it.
- Only pure classes get unit tests (the other code is inseparable from Minecraft — see CLAUDE.md). Minecraft-bound tasks are verified by compiling and by the in-game checklist in Task 13.
- Commit after every task with the message given. End every commit message with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Deviations from the spec (decided while planning)

1. **No `ru_ru.lang` edits.** Vanilla 1.12 `LanguageManager` always loads `en_us` first and overlays the selected language, so a key missing from `ru_ru.lang` already renders in English. The spec's "ru_ru gets the same keys in English" would only add duplicates.
2. **`SiteProblem` also carries `OWNER_AWAY` and `NO_RING`**, so the boss bar and action bar have a single enum of pause reasons with one lang key each.
3. **Site rule order puts the dimension first** (dimension → phase → infestation → ground → roof → crowd → sanctuary). In dim 150 the player should read "this dimension", not "no infestation".
4. The pre-1.21.0 key `msg.insanetweaks.sapling.phase_too_low` is replaced by `msg.insanetweaks.sapling.problem.phase_too_low`.

## File map

| File | Responsibility |
|------|----------------|
| `insanetweaks/build.gradle` | JUnit test setup; version 1.21.0 |
| `…/entities/sapling/SaplingDefensePlan.java` (new, pure) | Timeline: wave start ticks, waves due, progress, legacy migration |
| `…/entities/sapling/SaplingStats.java` (new, pure) | Interpolation of HP/armor/size, synced progress, wave size |
| `…/util/FragmentPity.java` (new, pure) | Fragment drop decision with bad-luck protection |
| `…/entities/sapling/SiteProblem.java` (new) | Enum of refusal/pause reasons + lang keys |
| `…/entities/sapling/SaplingSiteCheck.java` (new) | Site rules 1–7, "one active sapling" rule, collision helper |
| `…/entities/sapling/SaplingWaveSpawner.java` (new) | Lure, parasite count, wave spawning (SRP isolated) |
| `…/entities/EntityCorruptedSapling.java` (rewrite) | Event state machine, stats, pause, boss bar, NBT, uproot |
| `…/client/renderer/entity/RenderCorruptedSapling.java` | Model scale from progress |
| `…/items/fruit/CorruptedSeedItem.java` | Planting through `SaplingSiteCheck`; tooltip |
| `…/items/fruit/CorruptedSeedFragmentItem.java` | Tooltip: 3 fragments |
| `…/events/CorruptedFragmentDropHandler.java` | Pity counter |
| `…/sanctuary/SanctuaryWorldData.java`, `SanctuaryRegionHelper.java` | `isInsideWithMargin` / `isNear` |
| `…/config/categories/BaubleFruitsCategory.java` | New fields; old growth/HP fields removed |
| `insanetweaks/src/main/resources/assets/insanetweaks/recipes/corrupted_seed.json` | 3 fragments |
| `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang` | New keys |
| `…/InsaneTweaksMod.java` | `VERSION = "1.21.0"` |
| `insanetweaks/src/test/java/com/spege/insanetweaks/…` | Unit tests for the three pure classes |

---

### Task 1: JUnit for insanetweaks + `SaplingDefensePlan`

**Files:**
- Modify: `insanetweaks/build.gradle`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/sapling/SaplingDefensePlan.java`
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/entities/sapling/SaplingDefensePlanTest.java`

- [ ] **Step 1: Add the test dependencies to `insanetweaks/build.gradle`**

In the `repositories { … }` block, add as the last entry:

```groovy
    // JUnit for the test source set only. The pure classes under test take no Minecraft types,
    // so they run on a plain JVM; everything else in this mod is verified in game.
    mavenCentral()
```

In the `dependencies { … }` block, add as the last line:

```groovy
    testImplementation 'junit:junit:4.12'
```

After the `jar.finalizedBy('reobfJar')` line, add:

```groovy
test {
    testLogging { events 'passed', 'skipped', 'failed' }
}
```

- [ ] **Step 2: Write the failing test**

Create `insanetweaks/src/test/java/com/spege/insanetweaks/entities/sapling/SaplingDefensePlanTest.java`:

```java
package com.spege.insanetweaks.entities.sapling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SaplingDefensePlanTest {

    /** The shipped defaults: 60 s lure, 4 waves, 105 s apart. */
    private final SaplingDefensePlan plan = new SaplingDefensePlan(60, 4, 105);

    @Test
    public void totalIsLurePlusAllWaveIntervals() {
        assertEquals((60 + 4 * 105) * 20, plan.totalTicks());
    }

    @Test
    public void wavesStartAfterTheLureOneIntervalApart() {
        assertEquals(60 * 20, plan.waveStartTick(1));
        assertEquals(165 * 20, plan.waveStartTick(2));
        assertEquals(270 * 20, plan.waveStartTick(3));
        assertEquals(375 * 20, plan.waveStartTick(4));
    }

    @Test
    public void noWaveIsDueDuringTheLure() {
        assertEquals(0, plan.wavesDueBy(0));
        assertEquals(0, plan.wavesDueBy(60 * 20 - 1));
    }

    @Test
    public void aWaveIsDueExactlyAtItsStartTick() {
        assertEquals(1, plan.wavesDueBy(60 * 20));
        assertEquals(1, plan.wavesDueBy(165 * 20 - 1));
        assertEquals(2, plan.wavesDueBy(165 * 20));
        assertEquals(4, plan.wavesDueBy(375 * 20));
    }

    @Test
    public void wavesDueNeverExceedsTheWaveCount() {
        assertEquals(4, plan.wavesDueBy(plan.totalTicks()));
        assertEquals(4, plan.wavesDueBy(plan.totalTicks() * 3));
    }

    @Test
    public void progressIsClampedToZeroAndOne() {
        assertEquals(0.0D, plan.progress(-100), 1e-9);
        assertEquals(0.5D, plan.progress(plan.totalTicks() / 2), 1e-9);
        assertEquals(1.0D, plan.progress(plan.totalTicks() + 100), 1e-9);
    }

    @Test
    public void completeOnlyAtTheEnd() {
        assertFalse(plan.isComplete(plan.totalTicks() - 1));
        assertTrue(plan.isComplete(plan.totalTicks()));
    }

    @Test
    public void legacyGrowthMapsProportionallyOntoTheNewTimeline() {
        assertEquals(0, plan.migrateLegacyGrowth(0));
        assertEquals(plan.totalTicks() / 2, plan.migrateLegacyGrowth(SaplingDefensePlan.LEGACY_TOTAL_TICKS / 2));
    }

    @Test
    public void legacyGrowthNeverMigratesStraightIntoBloom() {
        assertEquals(plan.totalTicks() - 20, plan.migrateLegacyGrowth(SaplingDefensePlan.LEGACY_TOTAL_TICKS));
        assertEquals(plan.totalTicks() - 20, plan.migrateLegacyGrowth(Integer.MAX_VALUE));
        assertEquals(0, plan.migrateLegacyGrowth(-5));
    }

    @Test
    public void nonsenseConfigIsClampedToAWorkingPlan() {
        SaplingDefensePlan broken = new SaplingDefensePlan(-1, 0, 0);
        assertEquals(1, broken.waveCount());
        assertEquals(20, broken.totalTicks());
        assertEquals(1, broken.wavesDueBy(0));
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :insanetweaks:test "$JDK8" --console=plain`
Expected: compilation FAILS with `cannot find symbol … SaplingDefensePlan`.

- [ ] **Step 4: Write the implementation**

Create `…/entities/sapling/SaplingDefensePlan.java`:

```java
package com.spege.insanetweaks.entities.sapling;

/**
 * Timeline of one Corrupted Sapling defense, in ticks: a lure period, then {@code waveCount} waves
 * {@code interval} apart; the event completes one interval after the last wave starts.
 *
 * <p>Pure arithmetic with no Minecraft types, so it is unit-tested on a plain JVM
 * ({@code SaplingDefensePlanTest}). Built from the live config each time it is needed; nothing
 * here is stored.
 */
public final class SaplingDefensePlan {

    /** What the pre-1.21.0 sapling counted {@code SaplingGrowth} up to. */
    public static final int LEGACY_TOTAL_TICKS = 24000;

    private final int lureTicks;
    private final int waveCount;
    private final int intervalTicks;

    public SaplingDefensePlan(int lureSeconds, int waveCount, int intervalSeconds) {
        this.lureTicks = Math.max(0, lureSeconds) * 20;
        this.waveCount = Math.max(1, waveCount);
        this.intervalTicks = Math.max(1, intervalSeconds) * 20;
    }

    public int waveCount() {
        return this.waveCount;
    }

    public int totalTicks() {
        return this.lureTicks + this.waveCount * this.intervalTicks;
    }

    /** Tick at which wave {@code wave} (1-based) starts. */
    public int waveStartTick(int wave) {
        return this.lureTicks + (wave - 1) * this.intervalTicks;
    }

    /** How many waves have started by {@code defenseTicks}, 0..waveCount. */
    public int wavesDueBy(int defenseTicks) {
        if (defenseTicks < this.lureTicks) {
            return 0;
        }
        int due = (defenseTicks - this.lureTicks) / this.intervalTicks + 1;
        return Math.min(this.waveCount, due);
    }

    /** 0..1 share of the event that has run. */
    public double progress(int defenseTicks) {
        double p = defenseTicks / (double) totalTicks();
        return Math.max(0.0D, Math.min(1.0D, p));
    }

    public boolean isComplete(int defenseTicks) {
        return defenseTicks >= totalTicks();
    }

    /**
     * Maps a pre-1.21.0 {@code SaplingGrowth} value onto this timeline, proportionally. Never lands
     * on the very end: an old sapling resumes with at least one second left instead of blooming
     * the moment its chunk loads.
     */
    public int migrateLegacyGrowth(int legacyGrowth) {
        int clamped = Math.max(0, Math.min(legacyGrowth, LEGACY_TOTAL_TICKS));
        long scaled = (long) clamped * totalTicks() / LEGACY_TOTAL_TICKS;
        return (int) Math.min(scaled, (long) totalTicks() - 20);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :insanetweaks:test "$JDK8" --console=plain`
Expected: `SaplingDefensePlanTest > … PASSED` ×10, `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add insanetweaks/build.gradle insanetweaks/src/main/java/com/spege/insanetweaks/entities/sapling/SaplingDefensePlan.java insanetweaks/src/test/java/com/spege/insanetweaks/entities/sapling/SaplingDefensePlanTest.java
git commit -m "feat(insanetweaks): sapling defense timeline + JUnit for pure classes

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: `SaplingStats`

**Files:**
- Create: `…/entities/sapling/SaplingStats.java`
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/entities/sapling/SaplingStatsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.spege.insanetweaks.entities.sapling;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SaplingStatsTest {

    @Test
    public void lerpHitsBothEndsAndTheMiddle() {
        assertEquals(50.0D, SaplingStats.lerp(50, 80, 0.0D), 1e-9);
        assertEquals(65.0D, SaplingStats.lerp(50, 80, 0.5D), 1e-9);
        assertEquals(80.0D, SaplingStats.lerp(50, 80, 1.0D), 1e-9);
    }

    @Test
    public void lerpClampsProgress() {
        assertEquals(50.0D, SaplingStats.lerp(50, 80, -1.0D), 1e-9);
        assertEquals(80.0D, SaplingStats.lerp(50, 80, 2.0D), 1e-9);
    }

    @Test
    public void sizeGrowsFromSproutToTwoAndAHalfBlocks() {
        assertEquals(0.6F, SaplingStats.width(0.0D), 1e-6F);
        assertEquals(0.8F, SaplingStats.height(0.0D), 1e-6F);
        assertEquals(1.2F, SaplingStats.width(1.0D), 1e-6F);
        assertEquals(2.5F, SaplingStats.height(1.0D), 1e-6F);
    }

    @Test
    public void syncedProgressRoundTrips() {
        assertEquals(0, SaplingStats.toSynced(0.0D));
        assertEquals(500, SaplingStats.toSynced(0.5D));
        assertEquals(1000, SaplingStats.toSynced(1.0D));
        assertEquals(1000, SaplingStats.toSynced(7.0D));
        assertEquals(0.5D, SaplingStats.fromSynced(500), 1e-9);
    }

    @Test
    public void waveTopsTheAreaUpToBasePlusHalfThePhase() {
        // phase 6: wanted = 4 + 3 = 7; 2 already nearby -> spawn 5
        assertEquals(5, SaplingStats.waveSpawnCount(4, 6, 2, 8));
    }

    @Test
    public void waveSpawnsNothingWhenTheAreaIsAlreadyFull() {
        assertEquals(0, SaplingStats.waveSpawnCount(4, 6, 20, 8));
    }

    @Test
    public void waveNeverExceedsTheCap() {
        assertEquals(8, SaplingStats.waveSpawnCount(4, 10, 0, 8)); // wanted 9
        assertEquals(0, SaplingStats.waveSpawnCount(4, 10, 0, 0));
    }

    @Test
    public void negativePhaseCountsAsZero() {
        assertEquals(4, SaplingStats.waveSpawnCount(4, -3, 0, 8));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :insanetweaks:test "$JDK8" --console=plain`
Expected: FAIL, `cannot find symbol … SaplingStats`.

- [ ] **Step 3: Write the implementation**

```java
package com.spege.insanetweaks.entities.sapling;

/**
 * Everything about the Corrupted Sapling that is a function of its growth progress, plus the wave
 * size rule. Pure arithmetic with no Minecraft types ({@code SaplingStatsTest}).
 */
public final class SaplingStats {

    public static final float WIDTH_START = 0.6F;
    public static final float WIDTH_END = 1.2F;
    public static final float HEIGHT_START = 0.8F;
    public static final float HEIGHT_END = 2.5F;

    /** Progress is synced to clients as 0..SYNC_SCALE in a VARINT data parameter. */
    public static final int SYNC_SCALE = 1000;

    private SaplingStats() {
    }

    public static double lerp(double start, double end, double progress) {
        double p = Math.max(0.0D, Math.min(1.0D, progress));
        return start + (end - start) * p;
    }

    public static float width(double progress) {
        return (float) lerp(WIDTH_START, WIDTH_END, progress);
    }

    public static float height(double progress) {
        return (float) lerp(HEIGHT_START, HEIGHT_END, progress);
    }

    public static int toSynced(double progress) {
        double p = Math.max(0.0D, Math.min(1.0D, progress));
        return (int) Math.round(p * SYNC_SCALE);
    }

    public static double fromSynced(int synced) {
        return Math.max(0, Math.min(SYNC_SCALE, synced)) / (double) SYNC_SCALE;
    }

    /**
     * Parasites one wave adds: tops the area up to {@code base + phase / 2}, counting the ones
     * already there, and never more than {@code max} at once.
     */
    public static int waveSpawnCount(int base, int phase, int alreadyNearby, int max) {
        int wanted = base + Math.max(0, phase) / 2;
        return Math.max(0, Math.min(max, wanted - alreadyNearby));
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :insanetweaks:test "$JDK8" --console=plain`
Expected: all `SaplingStatsTest` and `SaplingDefensePlanTest` tests PASSED.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/entities/sapling/SaplingStats.java insanetweaks/src/test/java/com/spege/insanetweaks/entities/sapling/SaplingStatsTest.java
git commit -m "feat(insanetweaks): sapling stat interpolation and wave size rule

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `FragmentPity`

**Files:**
- Create: `…/util/FragmentPity.java`
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/util/FragmentPityTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.spege.insanetweaks.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FragmentPityTest {

    @Test
    public void aLuckyRollDrops() {
        assertTrue(FragmentPity.drops(0.05D, 0.10D, 0, 15));
    }

    @Test
    public void anUnluckyRollBeforeThePityPointDoesNotDrop() {
        assertFalse(FragmentPity.drops(0.99D, 0.10D, 0, 15));
        assertFalse(FragmentPity.drops(0.99D, 0.10D, 13, 15));
    }

    @Test
    public void theFifteenthEligibleKillIsGuaranteed() {
        // 14 dry kills behind us: this is the 15th.
        assertTrue(FragmentPity.drops(0.99D, 0.10D, 14, 15));
    }

    @Test
    public void pityZeroDisablesTheGuarantee() {
        assertFalse(FragmentPity.drops(0.99D, 0.10D, 1000, 0));
    }

    @Test
    public void counterResetsOnDropAndGrowsOtherwise() {
        assertEquals(0, FragmentPity.nextDryKills(7, true));
        assertEquals(8, FragmentPity.nextDryKills(7, false));
    }

    @Test
    public void simulatedRunNeverGoesFifteenKillsDry() {
        int dry = 0;
        int longestDry = 0;
        for (int kill = 0; kill < 1000; kill++) {
            boolean drop = FragmentPity.drops(0.99D, 0.10D, dry, 15); // always unlucky
            dry = FragmentPity.nextDryKills(dry, drop);
            longestDry = Math.max(longestDry, dry);
        }
        assertEquals(14, longestDry);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :insanetweaks:test "$JDK8" --console=plain`
Expected: FAIL, `cannot find symbol … FragmentPity`.

- [ ] **Step 3: Write the implementation**

```java
package com.spege.insanetweaks.util;

/**
 * Corrupted Seed Fragment drop with bad-luck protection: a normal roll, plus a guaranteed drop on
 * the {@code pityKills}-th eligible kill in a row without one. Pure arithmetic; the per-player
 * counter itself lives in persisted player NBT ({@code CorruptedFragmentDropHandler}).
 */
public final class FragmentPity {

    private FragmentPity() {
    }

    /**
     * @param roll           a uniform [0,1) roll
     * @param dryKillsBefore eligible kills since the last drop, not counting this one
     * @param pityKills      0 disables the guarantee
     */
    public static boolean drops(double roll, double chance, int dryKillsBefore, int pityKills) {
        if (roll < chance) {
            return true;
        }
        return pityKills > 0 && dryKillsBefore + 1 >= pityKills;
    }

    public static int nextDryKills(int dryKillsBefore, boolean dropped) {
        return dropped ? 0 : dryKillsBefore + 1;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :insanetweaks:test "$JDK8" --console=plain`
Expected: all three test classes PASSED.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/util/FragmentPity.java insanetweaks/src/test/java/com/spege/insanetweaks/util/FragmentPityTest.java
git commit -m "feat(insanetweaks): fragment drop bad-luck protection

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Config fields

**Files:**
- Modify: `…/config/categories/BaubleFruitsCategory.java`

The old `saplingGrowthTicks`, `saplingMinParasites` and `saplingHpPhase1/4/6/9` stay for now — the
old entity still reads them. Task 9 removes them together with the rewrite, so every commit compiles.

- [ ] **Step 1: Change two existing fields**

Replace the `fragmentDropChance` declaration block with:

```java
    @Config.Comment({"Corrupted Seed Fragment drop chance from high-tier parasites",
            "(only rolls when the killer wears the Blessed Ring or the Ring of the Seven Curses).",
            "Was 0.05 before 1.21.0; existing config files keep their old value."})
    @Config.Name("Fragment Drop Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double fragmentDropChance = 0.10;

    @Config.Comment({"Bad-luck protection: after this many eligible kills in a row without a fragment,",
            "the next eligible kill always drops one. Counted per player, survives death. 0 disables."})
    @Config.Name("Fragment Pity Kills")
    @Config.RangeInt(min = 0, max = 1000)
    public int fragmentPityKills = 15;
```

Replace the `saplingConditionRadius` comment with:

```java
    @Config.Comment({"How far the planting owner may stray from the Corrupted Sapling before the",
            "defense pauses (no growth, no new waves; parasites keep attacking)."})
```

(name, range and default `32` unchanged).

- [ ] **Step 2: Add the new defense fields**

Insert immediately before the `corruptedFruitDoomTicks` block:

```java
    // =========================================================================
    // Corrupted Sapling defense (1.21.0) — all read live
    // =========================================================================

    @Config.Comment({"Seconds after planting during which the sapling only lures nearby parasites,",
            "before the first wave."})
    @Config.Name("Sapling Lure Seconds")
    @Config.RangeInt(min = 0, max = 600)
    public int saplingLureSeconds = 60;

    @Config.Comment({"Number of waves. The last one also brings one elite parasite."})
    @Config.Name("Sapling Wave Count")
    @Config.RangeInt(min = 1, max = 10)
    public int saplingWaveCount = 4;

    @Config.Comment({"Seconds between waves. The sapling bears fruit one interval after the last",
            "wave starts, so the default event is 60 + 4 x 105 s = 8 minutes."})
    @Config.Name("Sapling Wave Interval Seconds")
    @Config.RangeInt(min = 10, max = 600)
    public int saplingWaveIntervalSeconds = 105;

    @Config.Comment({"Radius in which living parasites are pulled onto the sapling (every 2 s) and",
            "counted when sizing a wave."})
    @Config.Name("Sapling Lure Radius")
    @Config.RangeInt(min = 4, max = 64)
    public int saplingLureRadius = 24;

    @Config.Comment({"A wave tops the area up to this many parasites, plus half the SRP evolution",
            "phase, counting the ones already within the lure radius."})
    @Config.Name("Sapling Wave Base Count")
    @Config.RangeInt(min = 0, max = 32)
    public int saplingWaveBaseCount = 4;

    @Config.Comment({"Most parasites one wave may spawn (the elite comes on top)."})
    @Config.Name("Sapling Wave Max Spawn")
    @Config.RangeInt(min = 0, max = 32)
    public int saplingWaveMaxSpawn = 8;

    @Config.Comment({"Wave parasites appear on a ring this many blocks from the sapling (inner edge)."})
    @Config.Name("Sapling Spawn Ring Min")
    @Config.RangeInt(min = 2, max = 48)
    public int saplingSpawnRingMin = 10;

    @Config.Comment({"Outer edge of the wave spawn ring."})
    @Config.Name("Sapling Spawn Ring Max")
    @Config.RangeInt(min = 3, max = 64)
    public int saplingSpawnRingMax = 16;

    @Config.Comment({"Wave parasites at SRP evolution phase 1-3. Full entity ids."})
    @Config.Name("Sapling Wave Pool Early")
    public String[] saplingWavePoolEarly = {
            "srparasites:pri_longarms", "srparasites:pri_manducater", "srparasites:pri_arachnida",
            "srparasites:pri_reeker", "srparasites:rupter", "srparasites:buglin" };

    @Config.Comment({"Wave parasites at SRP evolution phase 4-6. Full entity ids."})
    @Config.Name("Sapling Wave Pool Mid")
    public String[] saplingWavePoolMid = {
            "srparasites:pri_longarms", "srparasites:pri_manducater", "srparasites:ada_longarms",
            "srparasites:ada_manducater", "srparasites:ada_arachnida", "srparasites:ada_reeker" };

    @Config.Comment({"Wave parasites at SRP evolution phase 7 and above. Full entity ids."})
    @Config.Name("Sapling Wave Pool Late")
    public String[] saplingWavePoolLate = {
            "srparasites:ada_longarms", "srparasites:ada_manducater", "srparasites:ada_arachnida",
            "srparasites:ada_reeker", "srparasites:grunt", "srparasites:seeker" };

    @Config.Comment({"Elite added to the last wave at SRP evolution phase 1-3."})
    @Config.Name("Sapling Elite Early")
    public String saplingEliteEarly = "srparasites:ada_manducater";

    @Config.Comment({"Elite added to the last wave at SRP evolution phase 4-6."})
    @Config.Name("Sapling Elite Mid")
    public String saplingEliteMid = "srparasites:ada_bolster";

    @Config.Comment({"Elite added to the last wave at SRP evolution phase 7 and above."})
    @Config.Name("Sapling Elite Late")
    public String saplingEliteLate = "srparasites:marauder";

    @Config.Comment({"Sapling max health when planted. Grows linearly to the end value with the event."})
    @Config.Name("Sapling HP Start")
    @Config.RangeInt(min = 1, max = 1024)
    public int saplingHpStart = 50;

    @Config.Comment({"Sapling max health at full growth."})
    @Config.Name("Sapling HP End")
    @Config.RangeInt(min = 1, max = 1024)
    public int saplingHpEnd = 80;

    @Config.Comment({"Sapling armor at full growth (0 when planted, linear in between)."})
    @Config.Name("Sapling Armor End")
    @Config.RangeInt(min = 0, max = 30)
    public int saplingArmorEnd = 6;

    @Config.Comment({"Sapling armor toughness at full growth (0 when planted, linear in between)."})
    @Config.Name("Sapling Toughness End")
    @Config.RangeInt(min = 0, max = 20)
    public int saplingToughnessEnd = 3;

    @Config.Comment({"The sapling heals 1 HP every this many seconds, paused or not."})
    @Config.Name("Sapling Regen Interval Seconds")
    @Config.RangeInt(min = 1, max = 600)
    public int saplingRegenIntervalSeconds = 10;

    @Config.Comment({"HP restored at once for every wave survived (when the next wave starts)."})
    @Config.Name("Sapling Wave Heal")
    @Config.RangeInt(min = 0, max = 1024)
    public int saplingWaveHeal = 5;

    @Config.Comment({"Ignore damage dealt by players, so the defender's own area spells cannot kill",
            "the sapling. /kill and the void still work; the owner can uproot it with sneak +",
            "right-click on an empty hand."})
    @Config.Name("Sapling Ignore Player Damage")
    public boolean saplingIgnorePlayerDamage = true;

    @Config.Comment({"Outline the sapling (entity glow) while the defense runs, so it stays visible",
            "inside a swarm."})
    @Config.Name("Sapling Glow")
    public boolean saplingGlow = true;

    @Config.Comment({"Planting needs an SRParasites block within this many blocks horizontally."})
    @Config.Name("Sapling Infestation Radius")
    @Config.RangeInt(min = 1, max = 32)
    public int saplingInfestationRadius = 8;

    @Config.Comment({"... and within this many blocks vertically."})
    @Config.Name("Sapling Infestation Height")
    @Config.RangeInt(min = 1, max = 16)
    public int saplingInfestationHeight = 4;

    @Config.Comment({"Dimensions where a Corrupted Seed cannot be planted. Waves spawn SRP parasites,",
            "and dimension 150 must never get any."})
    @Config.Name("Sapling Dimension Blacklist")
    public int[] saplingDimensionBlacklist = { 150 };

    @Config.Comment({"The sapling's own column must be free of solid blocks this many blocks up",
            "(no roof over it)."})
    @Config.Name("Sapling Column Clearance")
    @Config.RangeInt(min = 3, max = 32)
    public int saplingColumnClearance = 8;

    @Config.Comment({"Radius of the open area around the sapling (2 = a 5x5 square)."})
    @Config.Name("Sapling Crowd Radius")
    @Config.RangeInt(min = 1, max = 4)
    public int saplingCrowdRadius = 2;

    @Config.Comment({"Height of that open area above the sapling's base, in blocks."})
    @Config.Name("Sapling Crowd Height")
    @Config.RangeInt(min = 1, max = 6)
    public int saplingCrowdHeight = 3;

    @Config.Comment({"How many solid blocks the open area may contain. Blocks without a collision box",
            "(grass, flowers) never count."})
    @Config.Name("Sapling Crowd Tolerance")
    @Config.RangeInt(min = 0, max = 50)
    public int saplingCrowdTolerance = 3;

    @Config.Comment({"The sapling cannot grow inside a Sanctuary or within this many blocks of one."})
    @Config.Name("Sapling Sanctuary Margin")
    @Config.RangeInt(min = 0, max = 256)
    public int saplingSanctuaryMargin = 32;
```

- [ ] **Step 3: Compile**

Run: `./gradlew :insanetweaks:compileJava "$JDK8" --console=plain -q`
Expected: no `error:` lines.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/BaubleFruitsCategory.java
git commit -m "feat(insanetweaks): config for the sapling defense and fragment pity

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Fragments — pity in the drop handler, 3-fragment recipe, tooltip

**Files:**
- Modify: `…/events/CorruptedFragmentDropHandler.java` (whole class body below the imports)
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/recipes/corrupted_seed.json`
- Modify: `…/items/fruit/CorruptedSeedFragmentItem.java`

- [ ] **Step 1: Rewrite the handler body**

Keep the existing imports and add `import com.spege.insanetweaks.util.FragmentPity;`. Replace everything from the class Javadoc to the end of the file with:

```java
/**
 * Corrupted Seed Fragment drops from high-tier parasites (configurable prefix list),
 * ONLY when the killing player wears a qualifying ring (Blessed Ring or the plain
 * Cursed Ring — full parity per spec 2026-07-10). Registered when both
 * enableBaubleFruits and SRP are present.
 *
 * <p>Since 1.21.0 the roll has bad-luck protection ({@link FragmentPity}): eligible kills without a
 * drop are counted in persisted player NBT and the {@code fragmentPityKills}-th one always drops.
 */
public class CorruptedFragmentDropHandler {

    private static final String HINT_SHOWN_TAG = "InsaneTweaksCorruptedHintShown";
    private static final String PITY_TAG = "InsaneTweaksFragmentPity";

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingDrops(LivingDropsEvent event) {
        Entity killed = event.getEntity();
        if (killed.world.isRemote) return;
        if (!(killed instanceof EntityParasiteBase)) return;
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) return;

        EntityPlayer killer = (EntityPlayer) event.getSource().getTrueSource();
        if (!EnigmaticLegacyCompat.isWearingQualifyingRing(killer)) return;

        ResourceLocation key = EntityList.getKey(killed);
        if (key == null || !isHighTier(key.toString())) return;

        sendOneTimeHint(killer);

        NBTTagCompound persisted = persistedData(killer);
        int dryKills = persisted.getInteger(PITY_TAG);
        boolean drop = FragmentPity.drops(killed.world.rand.nextDouble(),
                ModConfig.baubleFruits.fragmentDropChance, dryKills, ModConfig.baubleFruits.fragmentPityKills);
        persisted.setInteger(PITY_TAG, FragmentPity.nextDryKills(dryKills, drop));
        if (!drop) return;

        event.getDrops().add(new EntityItem(killed.world,
                killed.posX, killed.posY + 0.3D, killed.posZ,
                new ItemStack(ModItems.CORRUPTED_SEED_FRAGMENT)));
    }

    private static boolean isHighTier(String registryName) {
        for (String prefix : ModConfig.baubleFruits.fragmentDropEntities) {
            if (prefix != null && !prefix.isEmpty() && registryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** The player's persisted (death-surviving) Forge data compound, created on first use. */
    private static NBTTagCompound persistedData(EntityPlayer player) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    /** One-time flavor hint (per player, persists through death) the first time a
     *  qualifying-ring wearer kills a fragment-eligible parasite. */
    private static void sendOneTimeHint(EntityPlayer player) {
        NBTTagCompound persisted = persistedData(player);
        if (persisted.getBoolean(HINT_SHOWN_TAG)) return;
        persisted.setBoolean(HINT_SHOWN_TAG, true);
        player.sendMessage(new TextComponentTranslation("msg.insanetweaks.corrupted_hint"));
    }
}
```

- [ ] **Step 2: Recipe — 3 fragments**

Replace `insanetweaks/src/main/resources/assets/insanetweaks/recipes/corrupted_seed.json` with:

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "insanetweaks:corrupted_seed_fragment" },
    { "item": "insanetweaks:corrupted_seed_fragment" },
    { "item": "insanetweaks:corrupted_seed_fragment" }
  ],
  "result": { "item": "insanetweaks:corrupted_seed" }
}
```

- [ ] **Step 3: Fragment tooltip and Javadoc**

In `CorruptedSeedFragmentItem.java` change the Javadoc line `* 4 fragments craft into a Corrupted Seed (recipes/corrupted_seed.json).` to
`* 3 fragments craft into a Corrupted Seed (recipes/corrupted_seed.json).` and the tooltip line:

```java
        tooltip.add(TextFormatting.GRAY + "Combine " + TextFormatting.LIGHT_PURPLE + "3 fragments"
                + TextFormatting.GRAY + " into a Corrupted Seed.");
```

- [ ] **Step 4: Compile**

Run: `./gradlew :insanetweaks:compileJava "$JDK8" --console=plain -q`
Expected: no `error:` lines.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/events/CorruptedFragmentDropHandler.java insanetweaks/src/main/resources/assets/insanetweaks/recipes/corrupted_seed.json insanetweaks/src/main/java/com/spege/insanetweaks/items/fruit/CorruptedSeedFragmentItem.java
git commit -m "balance(insanetweaks): fragments 10% with pity at 15, seed takes 3

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Sanctuary margin query

**Files:**
- Modify: `…/sanctuary/SanctuaryWorldData.java` (after `isInsideCapped`, ~line 217)
- Modify: `…/sanctuary/SanctuaryRegionHelper.java` (after `isProtected(World, BlockPos)`, ~line 37)

- [ ] **Step 1: Add `isInsideWithMargin` to `SanctuaryWorldData`**

Insert after the closing brace of `isInsideCapped`:

```java
    /**
     * Like {@link #isInside} with every region's radius widened by {@code margin} blocks — "inside
     * a sanctuary or close to one". Same index loop over the copy-on-write list, so it is as safe
     * on worker threads as {@code isInside}.
     */
    public boolean isInsideWithMargin(int x, int z, int margin) {
        long extra = Math.max(0, margin);
        for (int i = 0; i < regions.size(); i++) {
            int[] r = regions.get(i);
            long eff = r[3] + extra;
            long dx = x - r[0];
            long dz = z - r[2];
            if (dx * dx + dz * dz <= eff * eff) {
                return true;
            }
        }
        return false;
    }
```

- [ ] **Step 2: Add `isNear` to `SanctuaryRegionHelper`**

Insert after `isProtected(World world, BlockPos pos)`:

```java
    /** True when {@code pos} is inside an active sanctuary or within {@code margin} blocks of one. */
    public static boolean isNear(World world, BlockPos pos, int margin) {
        if (world == null || world.isRemote || pos == null || isDimensionBlacklisted(world)) {
            return false;
        }
        return SanctuaryWorldData.get(world).isInsideWithMargin(pos.getX(), pos.getZ(), margin);
    }
```

- [ ] **Step 3: Compile**

Run: `./gradlew :insanetweaks:compileJava "$JDK8" --console=plain -q`
Expected: no `error:` lines.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryWorldData.java insanetweaks/src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryRegionHelper.java
git commit -m "feat(insanetweaks): sanctuary query with a margin around each region

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: `SiteProblem` and `SaplingSiteCheck`

**Files:**
- Create: `…/entities/sapling/SiteProblem.java`
- Create: `…/entities/sapling/SaplingSiteCheck.java`

`SaplingSiteCheck.hasActiveSapling` calls `EntityCorruptedSapling.getOwnerId()`, which Task 9 keeps in its rewrite.
To keep this commit compiling, Step 3 adds that one getter to the current entity now.

- [ ] **Step 1: Create `SiteProblem`**

```java
package com.spege.insanetweaks.entities.sapling;

/**
 * Why a Corrupted Seed cannot be planted here, or why a running defense is paused. One lang key
 * each ({@code msg.insanetweaks.sapling.problem.<id>}), shared by the planting refusal, the action
 * bar and the boss bar.
 */
public enum SiteProblem {
    OWNER_AWAY("owner_away"),
    NO_RING("no_ring"),
    DIMENSION("dimension"),
    PHASE_TOO_LOW("phase_too_low"),
    NO_INFESTATION("no_infestation"),
    NO_GROUND("no_ground"),
    ROOFED("roofed"),
    CROWDED("crowded"),
    SANCTUARY("sanctuary"),
    ALREADY_ACTIVE("already_active");

    private final String id;

    SiteProblem(String id) {
        this.id = id;
    }

    public String langKey() {
        return "msg.insanetweaks.sapling.problem." + this.id;
    }
}
```

- [ ] **Step 2: Create `SaplingSiteCheck`**

```java
package com.spege.insanetweaks.entities.sapling;

import java.util.UUID;

import javax.annotation.Nullable;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.BaubleFruitsCategory;
import com.spege.insanetweaks.entities.EntityCorruptedSapling;
import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;
import com.spege.insanetweaks.util.SrpPhaseHelper;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/**
 * Where a Corrupted Sapling may stand (spec §3). {@code base} is always the block the sapling's
 * feet occupy, i.e. {@code new BlockPos(sapling)} or the air block above the clicked face.
 *
 * <p>Checked once at planting (refusal, seed kept) and every 100 ticks while the defense runs
 * (pause). Server side only.
 */
public final class SaplingSiteCheck {

    private static final String SRP_DOMAIN = "srparasites";

    private SaplingSiteCheck() {
    }

    /** The first failing site rule, or null when the site is fine. Owner rules are the entity's. */
    @Nullable
    public static SiteProblem checkSite(World world, BlockPos base) {
        BaubleFruitsCategory cfg = ModConfig.baubleFruits;
        if (isBlacklistedDimension(world, cfg)) {
            return SiteProblem.DIMENSION;
        }
        if (SrpPhaseHelper.getEvolutionPhase(world) < 1) {
            return SiteProblem.PHASE_TOO_LOW;
        }
        if (!hasInfestation(world, base, cfg.saplingInfestationRadius, cfg.saplingInfestationHeight)) {
            return SiteProblem.NO_INFESTATION;
        }
        if (!world.getBlockState(base.down()).isSideSolid(world, base.down(), EnumFacing.UP)) {
            return SiteProblem.NO_GROUND;
        }
        if (!isColumnClear(world, base, cfg.saplingColumnClearance)) {
            return SiteProblem.ROOFED;
        }
        if (crowdCount(world, base, cfg.saplingCrowdRadius, cfg.saplingCrowdHeight) > cfg.saplingCrowdTolerance) {
            return SiteProblem.CROWDED;
        }
        if (SanctuaryRegionHelper.isNear(world, base, cfg.saplingSanctuaryMargin)) {
            return SiteProblem.SANCTUARY;
        }
        return null;
    }

    /** True when {@code owner} already has a living Corrupted Sapling in any loaded world. */
    public static boolean hasActiveSapling(UUID owner) {
        for (WorldServer world : DimensionManager.getWorlds()) {
            for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityCorruptedSapling && entity.isEntityAlive()
                        && owner.equals(((EntityCorruptedSapling) entity).getOwnerId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A block that stops movement. Grass, flowers and other pass-through blocks do not count. */
    public static boolean collides(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return state.getCollisionBoundingBox(world, pos) != Block.NULL_AABB;
    }

    private static boolean isBlacklistedDimension(World world, BaubleFruitsCategory cfg) {
        int dim = world.provider.getDimension();
        for (int blocked : cfg.saplingDimensionBlacklist) {
            if (blocked == dim) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInfestation(World world, BlockPos base, int radius, int height) {
        for (BlockPos pos : BlockPos.getAllInBoxMutable(base.add(-radius, -height, -radius),
                base.add(radius, height, radius))) {
            ResourceLocation name = world.getBlockState(pos).getBlock().getRegistryName();
            if (name != null && SRP_DOMAIN.equals(name.getResourceDomain())) {
                return true;
            }
        }
        return false;
    }

    /** Layers base+0 .. base+clearance of the sapling's own column, no exceptions. */
    private static boolean isColumnClear(World world, BlockPos base, int clearance) {
        for (int dy = 0; dy <= clearance; dy++) {
            if (collides(world, base.up(dy))) {
                return false;
            }
        }
        return true;
    }

    /** Solid blocks in the (2r+1)^2 square, layers base+0 .. base+height, own column excluded. */
    private static int crowdCount(World world, BlockPos base, int radius, int height) {
        int count = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int dy = 0; dy <= height; dy++) {
                    if (collides(world, base.add(dx, dy, dz))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
```

- [ ] **Step 3: Add the owner getter to the current entity**

In `…/entities/EntityCorruptedSapling.java`, below `setOwnerId`, add:

```java
    @javax.annotation.Nullable
    public UUID getOwnerId() {
        return this.ownerId;
    }
```

(Task 8 rewrites the file and keeps this method.)

- [ ] **Step 4: Compile**

Run: `./gradlew :insanetweaks:compileJava "$JDK8" --console=plain -q`
Expected: no `error:` lines.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/entities/sapling/SiteProblem.java insanetweaks/src/main/java/com/spege/insanetweaks/entities/sapling/SaplingSiteCheck.java insanetweaks/src/main/java/com/spege/insanetweaks/entities/EntityCorruptedSapling.java
git commit -m "feat(insanetweaks): sapling site rules - roof, crowding, sanctuary, dimension

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: `SaplingWaveSpawner`

**Files:**
- Create: `…/entities/sapling/SaplingWaveSpawner.java`

- [ ] **Step 1: Create the spawner**

```java
package com.spege.insanetweaks.entities.sapling;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.BaubleFruitsCategory;
import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * Brings the hive to the Corrupted Sapling: pulls nearby parasites onto it and spawns the waves.
 *
 * <p>Targeting uses SRP's own {@code EntityParasiteBase.setAttackTarget(target, ticks)}, which sets
 * the target and locks it against changes for {@code ticks} ({@code targetNewCool}). No mixin.
 *
 * <p>Spawning goes through {@code world.spawnEntity}, which bypasses {@code CheckSpawn} and so
 * srpwizmixins' parasite origin gate — the sapling's dimension blacklist is what keeps dimension
 * 150 free of parasites.
 *
 * <p>🚨 Every SRP type stays inside {@link Srp}. Its methods are only reached after
 * {@link #srpLoaded()}, and a nested class referenced from a method body resolves lazily, so this
 * class loads fine without SRParasites.
 */
public final class SaplingWaveSpawner {

    /** Spawned wave parasites stay locked on the sapling this long before their own AI may retarget. */
    private static final int SPAWN_LOCK_TICKS = 200;
    private static final int POSITION_ATTEMPTS = 10;
    private static final int VERTICAL_SEARCH = 6;

    private static final Set<String> WARNED_IDS = new HashSet<String>();
    private static Boolean srpLoaded;

    private SaplingWaveSpawner() {
    }

    private static boolean srpLoaded() {
        if (srpLoaded == null) {
            srpLoaded = Boolean.valueOf(Loader.isModLoaded(InsaneTweaksMod.SRP_MODID));
        }
        return srpLoaded.booleanValue();
    }

    /** Living SRP parasites inside {@code box}; 0 without SRParasites. */
    public static int countParasites(World world, AxisAlignedBB box) {
        return srpLoaded() ? Srp.countLiving(world, box) : 0;
    }

    /** Points every living parasite within {@code radius} that is not already on it at {@code target}. */
    public static void lure(EntityLivingBase target, double radius, int lockTicks) {
        if (!srpLoaded()) {
            return;
        }
        Srp.lure(target.world, target.getEntityBoundingBox().grow(radius), target, lockTicks);
    }

    /**
     * Spawns one wave around {@code target}: {@code count} parasites from the phase's pool, plus the
     * phase's elite when {@code elite}. Mobs that find no valid position are skipped.
     *
     * @return how many were actually spawned
     */
    public static int spawnWave(EntityLivingBase target, int phase, int count, boolean elite) {
        BaubleFruitsCategory cfg = ModConfig.baubleFruits;
        World world = target.world;
        String[] pool = poolFor(cfg, phase);
        BlockPos center = new BlockPos(target);
        int spawned = 0;
        int skipped = 0;
        for (int i = 0; i < count && pool.length > 0; i++) {
            if (spawnOne(world, pool[world.rand.nextInt(pool.length)], center, target, cfg)) {
                spawned++;
            } else {
                skipped++;
            }
        }
        if (elite) {
            if (spawnOne(world, eliteFor(cfg, phase), center, target, cfg)) {
                spawned++;
            } else {
                skipped++;
            }
        }
        if (skipped > 0) {
            InsaneTweaksMod.LOGGER.debug("[InsaneTweaks] Corrupted Sapling wave at {}: {} spawned, {} skipped "
                    + "(no valid position or unknown id).", center, Integer.valueOf(spawned), Integer.valueOf(skipped));
        }
        return spawned;
    }

    private static String[] poolFor(BaubleFruitsCategory cfg, int phase) {
        if (phase >= 7) {
            return cfg.saplingWavePoolLate;
        }
        return phase >= 4 ? cfg.saplingWavePoolMid : cfg.saplingWavePoolEarly;
    }

    private static String eliteFor(BaubleFruitsCategory cfg, int phase) {
        if (phase >= 7) {
            return cfg.saplingEliteLate;
        }
        return phase >= 4 ? cfg.saplingEliteMid : cfg.saplingEliteEarly;
    }

    private static boolean spawnOne(World world, String id, BlockPos center, EntityLivingBase target,
            BaubleFruitsCategory cfg) {
        BlockPos pos = findSpawnPos(world, center, world.rand, cfg.saplingSpawnRingMin, cfg.saplingSpawnRingMax);
        if (pos == null) {
            return false;
        }
        Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(id), world);
        if (!(entity instanceof EntityLiving)) {
            if (WARNED_IDS.add(id)) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Corrupted Sapling wave: '{}' is not a living entity id "
                        + "- skipped. Check the baubleFruits wave pools and elites.", id);
            }
            return false;
        }
        EntityLiving mob = (EntityLiving) entity;
        mob.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                world.rand.nextFloat() * 360.0F, 0.0F);
        mob.onInitialSpawn(world.getDifficultyForLocation(pos), null);
        if (!world.spawnEntity(mob)) {
            return false;
        }
        if (srpLoaded()) {
            Srp.lock(mob, target, SPAWN_LOCK_TICKS);
        }
        return true;
    }

    /** A random standable spot on the ring, outside any sanctuary, or null after 10 misses. */
    @Nullable
    private static BlockPos findSpawnPos(World world, BlockPos center, Random rand, int ringMin, int ringMax) {
        int inner = Math.min(ringMin, ringMax);
        int outer = Math.max(ringMin, ringMax);
        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0D;
            double dist = inner + rand.nextDouble() * (outer - inner);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int dy = VERTICAL_SEARCH; dy >= -VERTICAL_SEARCH; dy--) {
                BlockPos feet = new BlockPos(x, center.getY() + dy, z);
                if (isStandable(world, feet) && !SanctuaryRegionHelper.isProtected(world, feet)) {
                    return feet;
                }
            }
        }
        return null;
    }

    /** Solid top face below, two pass-through non-liquid blocks for the body. */
    private static boolean isStandable(World world, BlockPos feet) {
        BlockPos below = feet.down();
        if (!world.getBlockState(below).isSideSolid(world, below, EnumFacing.UP)) {
            return false;
        }
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos body = feet.up(dy);
            if (SaplingSiteCheck.collides(world, body) || world.getBlockState(body).getMaterial().isLiquid()) {
                return false;
            }
        }
        return true;
    }

    /** The only place SRP types appear. Reached exclusively after {@link #srpLoaded()}. */
    private static final class Srp {
        private Srp() {
        }

        static int countLiving(World world, AxisAlignedBB box) {
            int alive = 0;
            for (com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase p : world.getEntitiesWithinAABB(
                    com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase.class, box)) {
                if (p.isEntityAlive()) {
                    alive++;
                }
            }
            return alive;
        }

        static void lure(World world, AxisAlignedBB box, EntityLivingBase target, int lockTicks) {
            for (com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase p : world.getEntitiesWithinAABB(
                    com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase.class, box)) {
                if (p.isEntityAlive() && p.getAttackTarget() != target) {
                    p.setAttackTarget(target, lockTicks);
                }
            }
        }

        static void lock(Entity mob, EntityLivingBase target, int lockTicks) {
            if (mob instanceof com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase) {
                ((com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase) mob)
                        .setAttackTarget(target, lockTicks);
            }
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :insanetweaks:compileJava "$JDK8" --console=plain -q`
Expected: no `error:` lines.

- [ ] **Step 3: Verify the SRP isolation on bytecode**

```bash
cd insanetweaks/build/classes/java/main/com/spege/insanetweaks/entities/sapling
javap -v SaplingWaveSpawner.class | grep -c "scapeandrunparasites"
javap -v 'SaplingWaveSpawner$Srp.class' | grep -c "scapeandrunparasites"
cd -
```

Expected: first count **0** (the outer class's constant pool never names an SRP type), second count > 0.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/entities/sapling/SaplingWaveSpawner.java
git commit -m "feat(insanetweaks): sapling lure and wave spawner on SRP's target lock

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Rewrite `EntityCorruptedSapling`; drop the old config fields

**Files:**
- Rewrite: `…/entities/EntityCorruptedSapling.java`
- Modify: `…/config/categories/BaubleFruitsCategory.java` (delete old fields)

- [ ] **Step 1: Replace the whole entity file**

```java
package com.spege.insanetweaks.entities;

import java.util.ArrayList;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.BaubleFruitsCategory;
import com.spege.insanetweaks.entities.sapling.SaplingDefensePlan;
import com.spege.insanetweaks.entities.sapling.SaplingSiteCheck;
import com.spege.insanetweaks.entities.sapling.SaplingStats;
import com.spege.insanetweaks.entities.sapling.SaplingWaveSpawner;
import com.spege.insanetweaks.entities.sapling.SiteProblem;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.EnigmaticLegacyCompat;
import com.spege.insanetweaks.util.SrpPhaseHelper;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Corrupted Sapling — a LIVING plant the player must defend for one short event (spec
 * 2026-10-06-corrupted-sapling-defense-design.md). An entity rather than a crop block, so parasites
 * can attack it.
 *
 * <p>Timeline ({@link SaplingDefensePlan}): a lure period, then waves; it bears a Corrupted Fruit
 * when the timer completes. The timer only runs while the owner is near, wears a qualifying ring and
 * the site is still valid ({@link SaplingSiteCheck}); otherwise the event pauses — no growth, no new
 * waves, but luring, regeneration and incoming damage go on.
 *
 * <p>Size, max HP, armor and toughness follow the growth progress ({@link SaplingStats}). Immune to
 * every potion effect and to SRP conversion; ignores player damage (configurable).
 */
@SuppressWarnings("null")
public class EntityCorruptedSapling extends EntityLiving {

    private static final DataParameter<Integer> PROGRESS =
            EntityDataManager.createKey(EntityCorruptedSapling.class, DataSerializers.VARINT);

    /** Forge custom-data key SRP reads; 0 means immune to COTH conversion (SRP 1.10.7+). */
    private static final String SRP_COTH_IMMUNITY = "srpcothimmunity";
    private static final double BOSS_BAR_RANGE = 32.0D;
    /** Lure re-locks nearby parasites for this long; refreshed every 40 ticks. */
    private static final int LURE_LOCK_TICKS = 100;
    /** Site rules are re-checked every this many 20-tick steps (= 100 ticks). */
    private static final int SITE_CHECK_STEPS = 5;

    private static final String NBT_OWNER = "SaplingOwner";
    private static final String NBT_DEFENSE_TICKS = "SaplingDefenseTicks";
    private static final String NBT_WAVES_FIRED = "SaplingWavesFired";
    private static final String NBT_REGEN_TICKS = "SaplingRegenTicks";
    /** Pre-1.21.0 growth counter, read once for migration. */
    private static final String NBT_LEGACY_GROWTH = "SaplingGrowth";

    private final BossInfoServer bossInfo = new BossInfoServer(this.getDisplayName(),
            BossInfo.Color.PURPLE, BossInfo.Overlay.PROGRESS);

    private UUID ownerId;
    private int defenseTicks;
    private int wavesFired;
    private int regenTicks;
    private int siteCheckCooldown;
    @Nullable
    private SiteProblem cachedSiteProblem;
    @Nullable
    private SiteProblem lastPause;

    public EntityCorruptedSapling(World world) {
        super(world);
        this.setSize(SaplingStats.WIDTH_START, SaplingStats.HEIGHT_START);
        this.setNoAI(true);
    }

    public void setOwnerId(UUID id) {
        this.ownerId = id;
    }

    @Nullable
    public UUID getOwnerId() {
        return this.ownerId;
    }

    /** 0..1 growth, synced — drives the hitbox here and the model scale in the renderer. */
    public double getProgress() {
        return SaplingStats.fromSynced(this.dataManager.get(PROGRESS).intValue());
    }

    private static SaplingDefensePlan plan(BaubleFruitsCategory cfg) {
        return new SaplingDefensePlan(cfg.saplingLureSeconds, cfg.saplingWaveCount, cfg.saplingWaveIntervalSeconds);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(PROGRESS, Integer.valueOf(0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(ModConfig.baubleFruits.saplingHpStart);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
    }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        if (PROGRESS.equals(key)) {
            applySize();
        }
    }

    /**
     * Runs on both sides whenever PROGRESS changes. {@code setSize} rebuilds the box from its min
     * corner, so the position is re-applied to keep the box centred on the sapling.
     */
    private void applySize() {
        double progress = getProgress();
        float w = SaplingStats.width(progress);
        float h = SaplingStats.height(progress);
        if (w != this.width || h != this.height) {
            this.setSize(w, h);
            this.setPosition(this.posX, this.posY, this.posZ);
        }
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean isPotionApplicable(@Nonnull PotionEffect effect) {
        return false;
    }

    @Override
    public boolean attackEntityFrom(@Nonnull DamageSource source, float amount) {
        if (ModConfig.baubleFruits.saplingIgnorePlayerDamage
                && (source.getTrueSource() instanceof EntityPlayer || source.getImmediateSource() instanceof EntityPlayer)) {
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }

    /** Sneak + right-click with an empty main hand: the owner uproots it. The seed is lost. */
    @Override
    protected boolean processInteract(@Nonnull EntityPlayer player, @Nonnull EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND || !player.isSneaking() || !player.getHeldItem(hand).isEmpty()
                || this.ownerId == null || !this.ownerId.equals(player.getUniqueID())) {
            return super.processInteract(player, hand);
        }
        if (!this.world.isRemote) {
            player.sendMessage(new TextComponentTranslation("msg.insanetweaks.sapling.uprooted"));
            this.world.playSound(null, this.posX, this.posY, this.posZ, SoundEvents.BLOCK_GRASS_BREAK,
                    SoundCategory.NEUTRAL, 1.0F, 0.8F);
            this.setDead();
        }
        return true;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        this.motionX = 0.0D;
        this.motionZ = 0.0D;

        if (this.world.isRemote || this.ticksExisted % 20 != 0) {
            return;
        }

        BaubleFruitsCategory cfg = ModConfig.baubleFruits;
        SaplingDefensePlan plan = plan(cfg);

        keepImmune();
        regenerate(cfg);
        this.setGlowing(cfg.saplingGlow);
        if (this.ticksExisted % 40 == 0) {
            SaplingWaveSpawner.lure(this, cfg.saplingLureRadius, LURE_LOCK_TICKS);
        }

        SiteProblem pause = pauseReason(cfg);
        announcePause(pause);
        if (pause == null) {
            this.defenseTicks += 20;
            fireDueWaves(plan, cfg);
            applyProgress(plan.progress(this.defenseTicks), cfg);
            if (plan.isComplete(this.defenseTicks)) {
                bearFruit();
                return;
            }
        }
        updateBossBar(plan, pause);
    }

    private void keepImmune() {
        if (!this.getActivePotionEffects().isEmpty()) {
            this.clearActivePotions();
        }
        NBTTagCompound data = this.getEntityData();
        if (!data.hasKey(SRP_COTH_IMMUNITY) || data.getInteger(SRP_COTH_IMMUNITY) != 0) {
            data.setInteger(SRP_COTH_IMMUNITY, 0);
        }
    }

    private void regenerate(BaubleFruitsCategory cfg) {
        this.regenTicks += 20;
        if (this.regenTicks >= cfg.saplingRegenIntervalSeconds * 20) {
            this.regenTicks = 0;
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);
            }
        }
    }

    /** Owner rules first (cheap), then the cached site rules, refreshed every 100 ticks. */
    @Nullable
    private SiteProblem pauseReason(BaubleFruitsCategory cfg) {
        EntityPlayer owner = ownerHere();
        double range = cfg.saplingConditionRadius;
        if (owner == null || !owner.isEntityAlive() || owner.getDistanceSq(this) > range * range) {
            return SiteProblem.OWNER_AWAY;
        }
        if (!EnigmaticLegacyCompat.isWearingQualifyingRing(owner)) {
            return SiteProblem.NO_RING;
        }
        if (--this.siteCheckCooldown <= 0) {
            this.siteCheckCooldown = SITE_CHECK_STEPS;
            this.cachedSiteProblem = SaplingSiteCheck.checkSite(this.world, new BlockPos(this));
        }
        return this.cachedSiteProblem;
    }

    /** Action bar to the owner whenever the pause reason changes (including back to running). */
    private void announcePause(@Nullable SiteProblem pause) {
        if (pause == this.lastPause) {
            return;
        }
        this.lastPause = pause;
        EntityPlayer owner = ownerHere();
        if (owner == null) {
            return;
        }
        if (pause != null) {
            owner.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.sapling.paused",
                    new TextComponentTranslation(pause.langKey())), true);
        } else {
            owner.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.sapling.resumed"), true);
        }
    }

    private void fireDueWaves(SaplingDefensePlan plan, BaubleFruitsCategory cfg) {
        int due = plan.wavesDueBy(this.defenseTicks);
        while (this.wavesFired < due) {
            this.wavesFired++;
            if (this.wavesFired >= 2) {
                this.heal(cfg.saplingWaveHeal); // the previous wave was survived
            }
            int phase = SrpPhaseHelper.getEvolutionPhase(this.world);
            int nearby = SaplingWaveSpawner.countParasites(this.world,
                    this.getEntityBoundingBox().grow(cfg.saplingLureRadius));
            int count = SaplingStats.waveSpawnCount(cfg.saplingWaveBaseCount, phase, nearby, cfg.saplingWaveMaxSpawn);
            SaplingWaveSpawner.spawnWave(this, phase, count, this.wavesFired == plan.waveCount());

            EntityPlayer owner = ownerHere();
            if (owner != null) {
                owner.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.sapling.wave",
                        Integer.valueOf(this.wavesFired), Integer.valueOf(plan.waveCount())), true);
            }
        }
    }

    /** Synced progress, then max HP (healing by any increase), armor and toughness. */
    private void applyProgress(double progress, BaubleFruitsCategory cfg) {
        this.dataManager.set(PROGRESS, Integer.valueOf(SaplingStats.toSynced(progress)));

        IAttributeInstance maxHp = this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        double previous = maxHp.getBaseValue();
        double target = SaplingStats.lerp(cfg.saplingHpStart, cfg.saplingHpEnd, progress);
        if (target != previous) {
            maxHp.setBaseValue(target);
            if (target > previous) {
                this.heal((float) (target - previous));
            }
            if (this.getHealth() > target) {
                this.setHealth((float) target);
            }
        }
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR)
                .setBaseValue(SaplingStats.lerp(0.0D, cfg.saplingArmorEnd, progress));
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS)
                .setBaseValue(SaplingStats.lerp(0.0D, cfg.saplingToughnessEnd, progress));
    }

    private void updateBossBar(SaplingDefensePlan plan, @Nullable SiteProblem pause) {
        ITextComponent title;
        if (pause != null) {
            title = new TextComponentTranslation("boss.insanetweaks.sapling.paused",
                    new TextComponentTranslation(pause.langKey()));
            this.bossInfo.setColor(BossInfo.Color.RED);
        } else if (this.wavesFired == 0) {
            title = new TextComponentTranslation("boss.insanetweaks.sapling.lure");
            this.bossInfo.setColor(BossInfo.Color.PURPLE);
        } else {
            title = new TextComponentTranslation("boss.insanetweaks.sapling.wave",
                    Integer.valueOf(this.wavesFired), Integer.valueOf(plan.waveCount()));
            this.bossInfo.setColor(BossInfo.Color.PURPLE);
        }
        this.bossInfo.setName(title);
        this.bossInfo.setPercent((float) plan.progress(this.defenseTicks));
        refreshBossBarViewers();
    }

    private void refreshBossBarViewers() {
        double rangeSq = BOSS_BAR_RANGE * BOSS_BAR_RANGE;
        for (EntityPlayerMP viewer : new ArrayList<EntityPlayerMP>(this.bossInfo.getPlayers())) {
            if (viewer.isDead || viewer.world != this.world || viewer.getDistanceSq(this) > rangeSq) {
                this.bossInfo.removePlayer(viewer);
            }
        }
        for (EntityPlayer player : this.world.playerEntities) {
            if (player instanceof EntityPlayerMP && player.getDistanceSq(this) <= rangeSq
                    && !this.bossInfo.getPlayers().contains(player)) {
                this.bossInfo.addPlayer((EntityPlayerMP) player);
            }
        }
    }

    private void clearBossBar() {
        for (EntityPlayerMP viewer : new ArrayList<EntityPlayerMP>(this.bossInfo.getPlayers())) {
            this.bossInfo.removePlayer(viewer);
        }
    }

    /** The owner if online and in this world, else null. */
    @Nullable
    private EntityPlayer ownerHere() {
        return this.ownerId == null ? null : this.world.getPlayerEntityByUUID(this.ownerId);
    }

    /** The owner if online in any dimension — for the end-of-event chat messages. */
    @Nullable
    private EntityPlayer ownerAnywhere() {
        MinecraftServer server = this.world.getMinecraftServer();
        if (this.ownerId == null || server == null) {
            return null;
        }
        return server.getPlayerList().getPlayerByUUID(this.ownerId);
    }

    private void bearFruit() {
        this.entityDropItem(new ItemStack(ModItems.CORRUPTED_FRUIT), 0.3f);
        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.SPELL_MOB,
                    this.posX, this.posY + this.height * 0.6D, this.posZ, 30,
                    0.4D, 0.6D, 0.4D, 0.05D);
        }
        this.world.playSound(null, this.posX, this.posY, this.posZ,
                SoundEvents.BLOCK_CHORUS_FLOWER_GROW, SoundCategory.NEUTRAL, 1.0f, 0.7f);
        EntityPlayer owner = ownerAnywhere();
        if (owner != null) {
            owner.sendMessage(new TextComponentTranslation("msg.insanetweaks.sapling.bloom"));
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Corrupted Sapling matured at {},{},{}",
                Integer.valueOf((int) this.posX), Integer.valueOf((int) this.posY), Integer.valueOf((int) this.posZ));
        this.setDead();
    }

    @Override
    public void onDeath(@Nonnull DamageSource cause) {
        super.onDeath(cause);
        if (!this.world.isRemote) {
            EntityPlayer owner = ownerAnywhere();
            if (owner != null) {
                owner.sendMessage(new TextComponentTranslation("msg.insanetweaks.sapling.died"));
            }
        }
    }

    @Override
    public void setDead() {
        super.setDead();
        clearBossBar();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        clearBossBar();
    }

    @Override
    public void writeEntityToNBT(@Nonnull NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (this.ownerId != null) {
            compound.setUniqueId(NBT_OWNER, this.ownerId);
        }
        compound.setInteger(NBT_DEFENSE_TICKS, this.defenseTicks);
        compound.setInteger(NBT_WAVES_FIRED, this.wavesFired);
        compound.setInteger(NBT_REGEN_TICKS, this.regenTicks);
    }

    @Override
    public void readEntityFromNBT(@Nonnull NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasUniqueId(NBT_OWNER)) {
            this.ownerId = compound.getUniqueId(NBT_OWNER);
        }
        SaplingDefensePlan plan = plan(ModConfig.baubleFruits);
        if (compound.hasKey(NBT_DEFENSE_TICKS)) {
            this.defenseTicks = compound.getInteger(NBT_DEFENSE_TICKS);
            this.wavesFired = compound.getInteger(NBT_WAVES_FIRED);
            this.regenTicks = compound.getInteger(NBT_REGEN_TICKS);
        } else if (compound.hasKey(NBT_LEGACY_GROWTH)) {
            // Saved by <= 1.20.3: resume at the same share of the new timeline, with the waves
            // already behind that point counted as fired.
            this.defenseTicks = plan.migrateLegacyGrowth(compound.getInteger(NBT_LEGACY_GROWTH));
            this.wavesFired = plan.wavesDueBy(this.defenseTicks);
        }
        this.dataManager.set(PROGRESS, Integer.valueOf(SaplingStats.toSynced(plan.progress(this.defenseTicks))));
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(@Nonnull DamageSource source) {
        return SoundEvents.BLOCK_WOOD_HIT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_GRASS_BREAK;
    }
}
```

- [ ] **Step 2: Delete the old fields from `BaubleFruitsCategory`**

Delete these six field blocks (each with its `@Config.Comment`, `@Config.Name` and `@Config.RangeInt` lines): `saplingGrowthTicks`, `saplingMinParasites`, `saplingHpPhase1`, `saplingHpPhase4`, `saplingHpPhase6`, `saplingHpPhase9`.

- [ ] **Step 3: Check nothing else used them**

Run: `grep -rn "saplingGrowthTicks\|saplingMinParasites\|saplingHpPhase\|getStage()\|MAX_STAGE" insanetweaks/src/main/java`
Expected: only `client/renderer/entity/RenderCorruptedSapling.java` (fixed in Task 10). Compilation will fail on it until then — that is expected; do not commit yet, continue with Task 10 Step 1, then compile and commit both together (Task 10 Step 3).

---

### Task 10: Renderer scale from progress

**Files:**
- Modify: `…/client/renderer/entity/RenderCorruptedSapling.java:24-29`

- [ ] **Step 1: Replace `preRenderCallback`**

```java
    /** Height of ModelCorruptedSapling in blocks: stem 14 px + bulb 8 px = 22 px. */
    private static final float MODEL_HEIGHT = 22.0F / 16.0F;

    @Override
    protected void preRenderCallback(@Nonnull EntityCorruptedSapling entity, float partialTickTime) {
        // Scale the model around its feet so it is exactly as tall as the synced hitbox.
        float s = com.spege.insanetweaks.entities.sapling.SaplingStats.height(entity.getProgress()) / MODEL_HEIGHT;
        GlStateManager.scale(s, s, s);
    }
```

- [ ] **Step 2: Compile and run the tests**

Run: `./gradlew :insanetweaks:compileJava :insanetweaks:test "$JDK8" --console=plain -q`
Expected: no `error:` lines; tests pass.

- [ ] **Step 3: Commit Tasks 9 and 10 together**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/entities/EntityCorruptedSapling.java insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/BaubleFruitsCategory.java insanetweaks/src/main/java/com/spege/insanetweaks/client/renderer/entity/RenderCorruptedSapling.java
git commit -m "feat(insanetweaks): Corrupted Sapling becomes a 4-wave defense event

Lure, waves, pause with reasons, boss bar, growth-scaled size/HP/armor,
potion and COTH immunity, player-damage immunity, owner uproot, and
migration of saplings saved by 1.20.3.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Planting through the site check; seed tooltip

**Files:**
- Modify: `…/items/fruit/CorruptedSeedItem.java`

- [ ] **Step 1: Replace the phase gate in `onItemUse`**

Replace the block from the comment `// Gate on SRP evolution: …` through its closing `}` (the `if (getEvolutionPhase(world) < 1) { … return EnumActionResult.FAIL; }`) with:

```java
        // Every site rule (dimension, SRP phase, infestation, ground, roof, crowding, sanctuary),
        // then one active sapling per player. A refusal names the reason and keeps the seed.
        com.spege.insanetweaks.entities.sapling.SiteProblem problem =
                com.spege.insanetweaks.entities.sapling.SaplingSiteCheck.checkSite(world, above);
        if (problem == null && com.spege.insanetweaks.entities.sapling.SaplingSiteCheck
                .hasActiveSapling(player.getUniqueID())) {
            problem = com.spege.insanetweaks.entities.sapling.SiteProblem.ALREADY_ACTIVE;
        }
        if (problem != null) {
            player.sendStatusMessage(
                    new net.minecraft.util.text.TextComponentTranslation(problem.langKey()), true);
            return EnumActionResult.FAIL;
        }
```

- [ ] **Step 2: Update the Javadoc lines of `getActiveAdvProperties`**

Replace `Four fragments went into this seed and a sapling has to survive twenty minutes after it.` with
`Three fragments went into this seed and a sapling has to survive a siege after it.`

- [ ] **Step 3: Replace `addInformation`**

```java
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        com.spege.insanetweaks.config.categories.BaubleFruitsCategory cfg =
                com.spege.insanetweaks.config.ModConfig.baubleFruits;
        int minutes = Math.max(1, Math.round(new com.spege.insanetweaks.entities.sapling.SaplingDefensePlan(
                cfg.saplingLureSeconds, cfg.saplingWaveCount, cfg.saplingWaveIntervalSeconds).totalTicks() / 1200.0F));

        tooltip.add(TextFormatting.DARK_PURPLE + "It pulses. It listens. It waits for tainted soil.");
        tooltip.add("");
        tooltip.add(TextFormatting.GOLD + "How to grow:");
        tooltip.add(TextFormatting.GRAY + "1. Plant it on parasite-infested ground, in the open:");
        tooltip.add(TextFormatting.GRAY + "   nothing above it, room around it, no Sanctuary nearby.");
        tooltip.add(TextFormatting.GRAY + "2. Stay within " + cfg.saplingConditionRadius + " blocks wearing the "
                + TextFormatting.LIGHT_PURPLE + "Blessed Ring" + TextFormatting.GRAY + " or the");
        tooltip.add("   " + TextFormatting.DARK_RED + "Ring of the Seven Curses" + TextFormatting.GRAY + ".");
        tooltip.add(TextFormatting.GRAY + "3. The hive comes for it: " + TextFormatting.RED + "survive "
                + cfg.saplingWaveCount + " waves" + TextFormatting.GRAY + " (~" + minutes + " min).");
        tooltip.add(TextFormatting.GRAY + "4. It bears a " + TextFormatting.DARK_PURPLE
                + "Corrupted Fruit" + TextFormatting.GRAY + ".");
        tooltip.add("");
        tooltip.add(TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC
                + "If it dies, the seed is lost with it.");
        tooltip.add(TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC
                + "Sneak + right-click it with an empty hand to uproot it.");
    }
```

- [ ] **Step 4: Remove the now-unused `EntityCorruptedSapling` import if the compiler flags it**

The planting code still does `new EntityCorruptedSapling(world)`, so the import stays. Compile:

Run: `./gradlew :insanetweaks:compileJava "$JDK8" --console=plain -q`
Expected: no `error:` lines.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/items/fruit/CorruptedSeedItem.java
git commit -m "feat(insanetweaks): seed planting obeys the sapling site rules

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Lang keys

**Files:**
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Remove the old key**

Delete the line starting `msg.insanetweaks.sapling.phase_too_low=`.

- [ ] **Step 2: Append the new keys at the end of the file**

```
# --- Corrupted Sapling defense (1.21.0) ---
msg.insanetweaks.sapling.problem.owner_away=its keeper is too far away
msg.insanetweaks.sapling.problem.no_ring=its keeper wears no Blessed Ring or Ring of the Seven Curses
msg.insanetweaks.sapling.problem.dimension=nothing of the hive may take root in this dimension
msg.insanetweaks.sapling.problem.phase_too_low=the parasite hive has not yet begun to evolve
msg.insanetweaks.sapling.problem.no_infestation=the soil here is not infested
msg.insanetweaks.sapling.problem.no_ground=it needs solid ground beneath it
msg.insanetweaks.sapling.problem.roofed=something is blocking the sky above it
msg.insanetweaks.sapling.problem.crowded=it needs open space around it to grow
msg.insanetweaks.sapling.problem.sanctuary=a Sanctuary is too close
msg.insanetweaks.sapling.problem.already_active=you are already tending a Corrupted Sapling
msg.insanetweaks.sapling.paused=§cThe sapling stops growing: %s
msg.insanetweaks.sapling.resumed=§5The sapling grows again.
msg.insanetweaks.sapling.wave=§5The hive answers - wave %s/%s
msg.insanetweaks.sapling.bloom=§5The Corrupted Sapling bears fruit.
msg.insanetweaks.sapling.died=§cThe Corrupted Sapling has died. The seed is lost.
msg.insanetweaks.sapling.uprooted=§7You tear the sapling out. The seed is lost.
boss.insanetweaks.sapling.lure=Corrupted Sapling - the hive stirs
boss.insanetweaks.sapling.wave=Corrupted Sapling - wave %s/%s
boss.insanetweaks.sapling.paused=Corrupted Sapling - paused: %s
```

The planting refusal shows a problem key on its own, so each reads as a full sentence fragment;
`msg.insanetweaks.sapling.paused` and the boss bar embed it after a colon.

- [ ] **Step 3: Check no referenced key is missing**

```bash
for k in $(grep -rhoE '"(msg|boss)\.insanetweaks\.sapling\.[a-z_.]+"' insanetweaks/src/main/java | tr -d '"' | sort -u) $(grep -oE '"[a-z_]+"\)' insanetweaks/src/main/java/com/spege/insanetweaks/entities/sapling/SiteProblem.java | tr -d '")' | sed 's/^/msg.insanetweaks.sapling.problem./'); do grep -q "^$k=" insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang || echo "MISSING $k"; done
```

Expected: no output.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "feat(insanetweaks): lang for the sapling defense

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: Version 1.21.0, build, deploy, verify in game

**Files:**
- Modify: `insanetweaks/build.gradle` (`version = '1.20.3'` → `'1.21.0'`)
- Modify: `…/InsaneTweaksMod.java:121` (`VERSION = "1.20.3"` → `"1.21.0"`)

- [ ] **Step 1: Bump both version places**

```bash
sed -i "s/^version = '1.20.3'/version = '1.21.0'/" insanetweaks/build.gradle
sed -i 's/VERSION = "1.20.3"/VERSION = "1.21.0"/' insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
grep -n "^version" insanetweaks/build.gradle; grep -n 'VERSION = ' insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
```

Expected: both print `1.21.0`.

- [ ] **Step 2: Full build of the mod (tests included)**

Run: `./gradlew :insanetweaks:build "$JDK8" --console=plain`
Expected: tests PASSED, `BUILD SUCCESSFUL`, `insanetweaks/build/libs/insanetweaks-1.21.0.jar` exists.

- [ ] **Step 3: Side-safety check on bytecode**

```bash
cd insanetweaks/build/classes/java/main/com/spege/insanetweaks
for c in entities/EntityCorruptedSapling entities/sapling/SaplingSiteCheck entities/sapling/SaplingWaveSpawner entities/sapling/SaplingStats entities/sapling/SaplingDefensePlan; do echo "$c: $(javap -v $c.class | grep -cE 'minecraft/client|fml/client')"; done
cd -
```

Expected: every count `0`.

- [ ] **Step 4: Commit the bump**

```bash
git add insanetweaks/build.gradle insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "chore(insanetweaks): 1.21.0

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 5: Deploy to the DEv 1.2 instance**

```bash
I="/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2"
mv "$I/mods/insanetweaks-1.20.3.jar" "$I/mods_backup_2026-10-06/"
cp insanetweaks/build/libs/insanetweaks-1.21.0.jar "$I/mods/"
ls "$I/mods" | grep -c "^insanetweaks-"
cp "$I/config/insanetweaks.cfg" "$I/config/insanetweaks.cfg.bak-1.20.3"
sed -i 's/D:"Fragment Drop Chance"=0.05/D:"Fragment Drop Chance"=0.1/' "$I/config/insanetweaks.cfg"
grep -n '"Fragment Drop Chance"' "$I/config/insanetweaks.cfg"
```

Expected: one `insanetweaks-` jar; the config line reads `=0.1`. (Forge never replaces an existing value with a new default; every other new field appears on first launch with its default.)

- [ ] **Step 6: Launch the game, then check the logs**

Ask the user to start the pack and load a world. Then:

```bash
L="/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs"
grep -oE "insanetweaks-[0-9.]+\.jar" "$L/latest.log" | sort -u
grep -nE "at com\.spege|/ERROR\].*\[(InsaneTweaks|insanetweaks)" "$L/latest.log" | head
grep -ciE "InvalidInjection|Scanned 0|VerifyError" "$L/cleanmix.log"
grep -c "APPLY mixins.insanetweaks" "$L/cleanmix.log"
```

Expected: `insanetweaks-1.21.0.jar`; no `at com.spege` / insanetweaks ERROR lines; 0 mixin errors; the same APPLY count as the 1.20.3 launch (29) — this change adds no mixins.

- [ ] **Step 7: In-game checklist (with the user)**

Prepare: `/give @p insanetweaks:corrupted_seed 3`, wear the Blessed Ring, stand in an infested area (SRP phase ≥ 1).

1. **Refusals keep the seed and name the reason:** in dim 150; away from SRP blocks; under a roof block 5 up; with 4 cobblestone blocks inside the 5×5 ring; within 32 blocks of a sanctuary; a second seed while one sapling lives.
2. **Run (phase 1–3):** boss bar "the hive stirs" for 60 s; nearby parasites walk to the sapling and attack it; wave 1 at ~1:00, 2 at ~2:45, 3 at ~4:30, 4 + elite at ~6:15; action bar per wave; bloom at ~8:00 drops one Corrupted Fruit and a chat line.
3. **Pause:** walk >32 blocks away (bar red, "its keeper is too far away"), take the ring off, put a block on top of it, place a sanctuary nearby — each pauses with its reason and resumes ("The sapling grows again.") when fixed; the timer in the bar does not advance while paused.
4. **Stats:** starts at 50 HP, ends near 80; splash poison does nothing; regen ~1 HP / 10 s; +5 HP when waves 2–4 start; your own sword and area spells do no damage.
5. **Size:** model and hitbox grow smoothly to ~2.5 blocks (F3 + B shows the hitbox).
6. **Save/reload mid-wave:** quit and rejoin during wave 2 — the event resumes, wave 2 is not repeated.
7. **Death:** let the parasites kill it — "has died. The seed is lost.", bar disappears.
8. **Uproot:** sneak + right-click with an empty hand as the owner — removed, seed lost message; another player cannot uproot it.
9. **Fragments:** with the ring, kill eligible parasites — a fragment by the 15th kill at the latest; 3 fragments craft a seed (JEI shows 3).

Record anything that fails; fix it in a follow-up task before pushing.
