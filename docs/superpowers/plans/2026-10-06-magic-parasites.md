# Magic Parasites Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three purple, magic-touched subclasses of SRP primitive parasites: Arcane Blinker, Arcane Warden and Arcane Conjurer. Each has its own abilities, tint and glow rendering, reduced base drops plus a bonus loot table, and its own config.

**Architecture:** Each entity extends its SRP base class (`EntityShyco` / `EntityHull` / `EntityCanra`), keeps SRP's AI and skills, and adds abilities as small AI tasks or overrides:
- Shared server logic lives in `MagicParasiteTraits`.
- Pure maths (`BlinkPlanner`, `ArcaneDamage`, `SpectralBudget`) is Minecraft-free and JUnit-tested.
- Renderers extend SRP's renderers, multiply the colour around the main model pass, and add one glow layer.
- One event handler owns the drop rules and spectral expiry.

**Tech Stack:** Minecraft 1.12.2 Forge (FG3, Java 8), SRParasites 1.10.x (required dependency, deobf from `libs/`), JUnit 4.12.

Spec: `docs/superpowers/specs/2026-10-06-magic-parasites-design.md`.

**Facts verified on bytecode (2026-10-06), relied on below:**
- `EntityCanra.doSpecialSkill(byte)`: byte `1` → private `summon()`; others → super. `getFinished(1)` / `setFinished(1, b)` read and write the private `skillSummon`. `EntityAISkill.updateTask` calls `doSpecialSkill(id)` every tick while active and stops when `getFinished(id)` is true, then calls `setFinished(id, false)`. Native `summon()` runs with `setParasiteStatus(10)` and resets it to 0 at the end.
- SRP base stats are set in `applyEntityAttributes` (`func_110147_ax`). `EntityParasiteBase.onInitialSpawn` rescales them **relatively** (`base * phaseMultiplier`), so multipliers applied in our `applyEntityAttributes` survive it.
- `EntityHull.hasTargetedEntity()` / `getTargetedEntity()` are public. They are the native pull.
- EBW `MagicDamage` / `IndirectMagicDamage` always call `setMagicDamage()`. Type FIRE also calls `setFireDamage()`.
- SRP renderers `RenderShyco` / `RenderHull` / `RenderCanra(RenderManager)` extend `RenderMalleable<T>`. `RenderHull` overrides `renderModel`; the other two do not. All pick a skin variant in `getEntityTexture`.
- Vanilla `EntityArrow.onHit`, when `attackEntityFrom` returns false, multiplies its motion by -0.1 and turns around. Pre-multiplying by 8 therefore yields a -0.8 reflection.
- Tracking ids 100–120 are used, so 121–123 are free.

Deviation from spec §4, deliberate: `IMagicParasite` exposes `getArcaneState()` / `setArcaneState(byte)` rather than `getArcaneGlow(partialTicks)`, and the layer maps the state to alpha. That is the same information with less code. Added config `Conjurer: Spectral Cooldown Seconds` (12). Without it the native skill timer (about 1 s) would recast as soon as a spectral dies.

Build commands need JDK 8: prefix every Gradle call with
`./gradlew "-Dorg.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot" …`.

---

## File map

| File | Responsibility |
|---|---|
| `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/BlinkPlanner.java` | Pure: teleport offsets behind / beside a target |
| `…/entities/magic/ArcaneDamage.java` | Pure: incoming damage multiplier |
| `…/entities/magic/SpectralBudget.java` | Pure: how many spectrals to summon |
| `…/entities/magic/IMagicParasite.java` | Marker + synced arcane state accessors |
| `…/entities/magic/SpectralTags.java` | Entity-data tags for spectral summons |
| `…/entities/magic/MagicParasiteTraits.java` | Shared server/common helpers |
| `…/entities/magic/EntityMagicLongarms.java` | Arcane Blinker |
| `…/entities/magic/EntityMagicManducater.java` | Arcane Warden |
| `…/entities/magic/EntityMagicSummoner.java` | Arcane Conjurer |
| `…/entities/magic/ai/EntityAIBlinkStrike.java` | Blinker's Blink Strike |
| `…/entities/magic/ai/EntityAIArcaneWard.java` | Warden's Arcane Ward + Bastion |
| `…/entities/magic/ai/EntityAIWardAllies.java` | Conjurer's Ward Allies |
| `…/events/MagicParasiteHandler.java` | Drops, XP, spectral expiry |
| `…/client/renderer/entity/magic/ArcaneTextureSource.java` | Lets the layer ask a renderer for the current skin |
| `…/client/renderer/entity/magic/ArcaneTint.java` | GL colour multiply from config |
| `…/client/renderer/entity/magic/LayerArcaneGlow.java` | Additive violet shell |
| `…/client/renderer/entity/magic/RenderMagicLongarms.java` (+ Manducater, Summoner) | SRP renderer + tint + glow |
| `…/config/categories/EntitiesCategory.java` | New `MagicParasites` subcategory |
| `…/InsaneTweaksMod.java` | Entity registration, loot table, handler, version |
| `…/client/ClientProxy.java` | Renderer registration |
| `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/entities/magic_parasite.json` | Bonus loot |
| `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang` | Names |
| `insanetweaks/src/test/java/com/spege/insanetweaks/entities/magic/*Test.java` | JUnit |

---

### Task 1: Pure logic (TDD)

**Files:**
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/entities/magic/BlinkPlannerTest.java`
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/entities/magic/ArcaneDamageTest.java`
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/entities/magic/SpectralBudgetTest.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/BlinkPlanner.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/ArcaneDamage.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/SpectralBudget.java`

- [ ] **Step 1: Write the failing tests**

File: `insanetweaks/src/test/java/com/spege/insanetweaks/entities/magic/BlinkPlannerTest.java`
```java
package com.spege.insanetweaks.entities.magic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BlinkPlannerTest {

    private static final double EPS = 1.0E-9D;

    @Test
    public void behindATargetFacingSouthIsNorthOfIt() {
        // yaw 0 looks towards +z, so behind is -z
        double[][] offsets = BlinkPlanner.behind(0.0F, 2.0D);
        assertEquals(0.0D, offsets[0][0], EPS);
        assertEquals(-2.0D, offsets[0][1], EPS);
    }

    @Test
    public void behindATargetFacingWestIsEastOfIt() {
        // yaw 90 looks towards -x, so behind is +x
        double[][] offsets = BlinkPlanner.behind(90.0F, 2.0D);
        assertEquals(2.0D, offsets[0][0], EPS);
        assertEquals(0.0D, offsets[0][1], EPS);
    }

    @Test
    public void everyCandidateKeepsTheRequestedDistance() {
        double[][] offsets = BlinkPlanner.behind(37.0F, 2.5D);
        assertEquals(5, offsets.length);
        for (double[] o : offsets) {
            assertEquals(2.5D, Math.sqrt(o[0] * o[0] + o[1] * o[1]), EPS);
        }
    }

    @Test
    public void aSidestepIsPerpendicularToTheProjectile() {
        double[][] offsets = BlinkPlanner.sidestep(1.0D, 0.0D, 3.0D, true, 0.0F);
        assertEquals(0.0D, offsets[0][0], EPS);
        assertEquals(3.0D, offsets[0][1], EPS);
        assertEquals(0.0D, offsets[1][0], EPS);
        assertEquals(-3.0D, offsets[1][1], EPS);
    }

    @Test
    public void rightFirstSwapsTheOrder() {
        double[][] offsets = BlinkPlanner.sidestep(1.0D, 0.0D, 3.0D, false, 0.0F);
        assertEquals(-3.0D, offsets[0][1], EPS);
        assertEquals(3.0D, offsets[1][1], EPS);
    }

    @Test
    public void aMotionlessProjectileFallsBackToTheFacing() {
        // facing yaw 0 = +z; perpendicular "left" of +z is -x
        double[][] offsets = BlinkPlanner.sidestep(0.0D, 0.0D, 4.0D, true, 0.0F);
        assertEquals(-4.0D, offsets[0][0], EPS);
        assertEquals(0.0D, offsets[0][1], EPS);
    }

    @Test
    public void lerpMapsTheRollIntoTheBand() {
        assertEquals(1.5D, BlinkPlanner.lerp(1.5D, 2.5D, 0.0D), EPS);
        assertEquals(2.0D, BlinkPlanner.lerp(1.5D, 2.5D, 0.5D), EPS);
    }
}
```

File: `insanetweaks/src/test/java/com/spege/insanetweaks/entities/magic/ArcaneDamageTest.java`
```java
package com.spege.insanetweaks.entities.magic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ArcaneDamageTest {

    @Test
    public void plainDamageIsUntouched() {
        assertEquals(10.0F, ArcaneDamage.scale(10.0F, false, false, 0.75D, 1.25D), 1.0E-6F);
    }

    @Test
    public void magicDamageIsReduced() {
        assertEquals(7.5F, ArcaneDamage.scale(10.0F, true, false, 0.75D, 1.25D), 1.0E-6F);
    }

    @Test
    public void fireDamageIsIncreased() {
        assertEquals(12.5F, ArcaneDamage.scale(10.0F, false, true, 0.75D, 1.25D), 1.0E-6F);
    }

    @Test
    public void magicFireAppliesBothMultipliers() {
        assertEquals(9.375F, ArcaneDamage.scale(10.0F, true, true, 0.75D, 1.25D), 1.0E-6F);
    }
}
```

File: `insanetweaks/src/test/java/com/spege/insanetweaks/entities/magic/SpectralBudgetTest.java`
```java
package com.spege.insanetweaks.entities.magic;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpectralBudgetTest {

    @Test
    public void theRollPicksWithinMinAndMax() {
        assertEquals(2, SpectralBudget.toSummon(2, 3, 0.0D, 0, 4));
        assertEquals(2, SpectralBudget.toSummon(2, 3, 0.49D, 0, 4));
        assertEquals(3, SpectralBudget.toSummon(2, 3, 0.5D, 0, 4));
        assertEquals(3, SpectralBudget.toSummon(2, 3, 0.999D, 0, 4));
    }

    @Test
    public void theLimitCapsTheCount() {
        assertEquals(1, SpectralBudget.toSummon(2, 3, 0.9D, 3, 4));
        assertEquals(0, SpectralBudget.toSummon(2, 3, 0.9D, 4, 4));
        assertEquals(0, SpectralBudget.toSummon(2, 3, 0.9D, 6, 4));
    }

    @Test
    public void swappedBoundsAreTolerated() {
        assertEquals(2, SpectralBudget.toSummon(3, 2, 0.0D, 0, 4));
    }
}
```

- [ ] **Step 2: Run the tests and verify they fail**

Run: `./gradlew "-Dorg.gradle.java.home=…" :insanetweaks:test`
Expected: compilation FAIL ("cannot find symbol BlinkPlanner / ArcaneDamage / SpectralBudget").

- [ ] **Step 3: Implement**

File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/BlinkPlanner.java`
```java
package com.spege.insanetweaks.entities.magic;

/**
 * Where a magic parasite may teleport, as horizontal offsets {@code {dx, dz}} from a base point.
 * Pure maths, no Minecraft types, so it is unit-tested. The caller tries the candidates in order
 * and takes the first that {@code attemptTeleport} accepts.
 *
 * <p>Minecraft yaw: 0 looks towards +z and 90 towards -x, so the look vector is
 * {@code (-sin yaw, cos yaw)}.
 */
public final class BlinkPlanner {

    private BlinkPlanner() {
    }

    /**
     * Five candidates at {@code distance} around a target facing {@code yawDegrees}: straight
     * behind, the two rear diagonals, then the two sides.
     */
    public static double[][] behind(float yawDegrees, double distance) {
        double yaw = Math.toRadians(yawDegrees);
        double bx = Math.sin(yaw);
        double bz = -Math.cos(yaw);
        double[][] dirs = {
                { bx, bz },
                rotate(bx, bz, 45.0D),
                rotate(bx, bz, -45.0D),
                rotate(bx, bz, 90.0D),
                rotate(bx, bz, -90.0D)
        };
        for (double[] d : dirs) {
            d[0] *= distance;
            d[1] *= distance;
        }
        return dirs;
    }

    /**
     * Two candidates perpendicular to a projectile's horizontal motion, {@code distance} away. A
     * projectile with no horizontal motion falls back to the dodger's own facing.
     */
    public static double[][] sidestep(double motionX, double motionZ, double distance, boolean leftFirst,
            float fallbackYawDegrees) {
        double mx = motionX;
        double mz = motionZ;
        double len = Math.sqrt(mx * mx + mz * mz);
        if (len < 1.0E-4D) {
            double yaw = Math.toRadians(fallbackYawDegrees);
            mx = -Math.sin(yaw);
            mz = Math.cos(yaw);
            len = 1.0D;
        }
        double nx = mx / len;
        double nz = mz / len;
        double[] left = { -nz * distance, nx * distance };
        double[] right = { nz * distance, -nx * distance };
        return leftFirst ? new double[][] { left, right } : new double[][] { right, left };
    }

    /** {@code min + (max - min) * roll}, for a roll in [0, 1). */
    public static double lerp(double min, double max, double roll) {
        return min + (max - min) * roll;
    }

    private static double[] rotate(double x, double z, double degrees) {
        double r = Math.toRadians(degrees);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        return new double[] { x * cos - z * sin, x * sin + z * cos };
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/ArcaneDamage.java`
```java
package com.spege.insanetweaks.entities.magic;

/**
 * The "Arcane-touched" passive: magic damage is resisted, fire is a weakness. Applied
 * multiplicatively, so a source that is both (EBW fire spells) gets both.
 */
public final class ArcaneDamage {

    private ArcaneDamage() {
    }

    public static float scale(float amount, boolean magic, boolean fire, double magicMultiplier,
            double fireMultiplier) {
        double m = 1.0D;
        if (magic) {
            m *= magicMultiplier;
        }
        if (fire) {
            m *= fireMultiplier;
        }
        return (float) (amount * m);
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/SpectralBudget.java`
```java
package com.spege.insanetweaks.entities.magic;

/** How many spectral parasites one Spectral Swarm cast may summon. */
public final class SpectralBudget {

    private SpectralBudget() {
    }

    /**
     * A count picked uniformly from [min, max] by {@code roll} (in [0, 1)), cut down so the
     * Conjurer never has more than {@code limit} alive at once. Never negative.
     */
    public static int toSummon(int min, int max, double roll, int active, int limit) {
        int lo = Math.max(0, Math.min(min, max));
        int hi = Math.max(lo, Math.max(min, max));
        int want = lo + (int) Math.floor(roll * (hi - lo + 1));
        if (want > hi) {
            want = hi;
        }
        return Math.max(0, Math.min(want, limit - active));
    }
}
```

- [ ] **Step 4: Run the tests and verify they pass**

Run: `./gradlew "-Dorg.gradle.java.home=…" :insanetweaks:test`
Expected: PASS for the new tests plus the existing 30.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic insanetweaks/src/test/java/com/spege/insanetweaks/entities/magic
git commit -m "feat(insanetweaks): magic parasite pure logic - blink offsets, arcane damage, spectral budget"
```

---

### Task 2: Config and lang

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java` (new field after `sentinel` and new nested class after `Sentinel`)
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Add the subcategory field** after the `sentinel` field:

```java
    @Config.Name("magic_parasites")
    @Config.LangKey("config.insanetweaks.category.entities.magic_parasites")
    @Config.Comment("Arcane Blinker / Warden / Conjurer: purple magic-touched variants of SRP primitive parasites.")
    public final MagicParasites magicParasites = new MagicParasites();
```

- [ ] **Step 2: Add the nested class** after `Sentinel`:

```java
    /**
     * Ability numbers are read live; multipliers apply when an entity is created (existing ones
     * keep the attributes saved in their NBT).
     */
    public static class MagicParasites {
        @Config.Comment({ "Master switch for the magic parasites' abilities and drop rules.",
                "The three entities stay registered either way (their ids are save-stable);",
                "with this off they fight like their SRP base. Spectral summons always expire." })
        @Config.Name("Enable Magic Parasites")
        public boolean enableMagicParasites = true;

        @Config.Comment("Chance (0-1) that each stack of a magic parasite's normal SRP drops is lost. The bonus loot table is never reduced.")
        @Config.Name("Shared: Base Drop Penalty")
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double baseDropPenalty = 0.3;

        @Config.Comment("Client-side colour multiplier (red, green, blue; 0-1) applied over SRP's own textures.")
        @Config.Name("Shared: Tint RGB")
        public double[] tintRgb = { 0.78, 0.55, 1.0 };

        @Config.Comment("Multiplier on magic damage taken (EBW spells, potions).")
        @Config.Name("Shared: Magic Damage Taken")
        @Config.RangeDouble(min = 0.0, max = 4.0)
        public double magicDamageTaken = 0.75;

        @Config.Comment("Multiplier on fire damage taken. Stacks with the magic multiplier for EBW fire spells.")
        @Config.Name("Shared: Fire Damage Taken")
        @Config.RangeDouble(min = 0.0, max = 4.0)
        public double fireDamageTaken = 1.25;

        @Config.Name("Blinker: Health Multiplier")
        @Config.RangeDouble(min = 0.1, max = 10.0)
        public double blinkerHealthMultiplier = 1.2;

        @Config.Name("Blinker: Damage Multiplier")
        @Config.RangeDouble(min = 0.1, max = 10.0)
        public double blinkerDamageMultiplier = 1.0;

        @Config.Comment("Seconds between Blink Strikes.")
        @Config.Name("Blinker: Blink Cooldown Seconds")
        @Config.RangeInt(min = 1, max = 600)
        public int blinkCooldownSeconds = 8;

        @Config.Name("Blinker: Blink Min Range")
        @Config.RangeDouble(min = 0.0, max = 64.0)
        public double blinkMinRange = 6.0;

        @Config.Name("Blinker: Blink Max Range")
        @Config.RangeDouble(min = 0.0, max = 64.0)
        public double blinkMaxRange = 20.0;

        @Config.Comment("Extra damage (0.3 = +30%) on the first hit within 2 s of a blink.")
        @Config.Name("Blinker: Blink Bonus Damage")
        @Config.RangeDouble(min = 0.0, max = 5.0)
        public double blinkBonusDamage = 0.3;

        @Config.Comment("Chance (0-1) to dodge a projectile by teleporting sideways.")
        @Config.Name("Blinker: Dodge Chance")
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double dodgeChance = 0.3;

        @Config.Name("Blinker: Dodge Cooldown Seconds")
        @Config.RangeInt(min = 0, max = 600)
        public int dodgeCooldownSeconds = 6;

        @Config.Name("Warden: Health Multiplier")
        @Config.RangeDouble(min = 0.1, max = 10.0)
        public double wardenHealthMultiplier = 2.0;

        @Config.Name("Warden: Armor Multiplier")
        @Config.RangeDouble(min = 0.1, max = 10.0)
        public double wardenArmorMultiplier = 2.0;

        @Config.Name("Warden: Damage Multiplier")
        @Config.RangeDouble(min = 0.1, max = 10.0)
        public double wardenDamageMultiplier = 1.0;

        @Config.Comment("Seconds between wards, counted from the end of the previous one.")
        @Config.Name("Warden: Ward Cooldown Seconds")
        @Config.RangeInt(min = 1, max = 600)
        public int wardCooldownSeconds = 20;

        @Config.Name("Warden: Ward Duration Seconds")
        @Config.RangeInt(min = 1, max = 60)
        public int wardDurationSeconds = 6;

        @Config.Comment("Multiplier on non-projectile damage taken while warded. Projectiles are always fully blocked and sent back.")
        @Config.Name("Warden: Ward Damage Taken")
        @Config.RangeDouble(min = 0.0, max = 1.0)
        public double wardDamageTaken = 0.5;

        @Config.Comment("Radius in which other parasites get Resistance I while the ward is up.")
        @Config.Name("Warden: Bastion Radius")
        @Config.RangeDouble(min = 0.0, max = 32.0)
        public double bastionRadius = 6.0;

        @Config.Comment("Seconds of Slowness I put on an entity the Warden starts pulling (0 disables).")
        @Config.Name("Warden: Pull Slowness Seconds")
        @Config.RangeInt(min = 0, max = 60)
        public int pullSlownessSeconds = 3;

        @Config.Name("Conjurer: Health Multiplier")
        @Config.RangeDouble(min = 0.1, max = 10.0)
        public double conjurerHealthMultiplier = 1.25;

        @Config.Name("Conjurer: Damage Multiplier")
        @Config.RangeDouble(min = 0.1, max = 10.0)
        public double conjurerDamageMultiplier = 1.0;

        @Config.Comment("Entity ids a Spectral Swarm picks from. Spectrals drop nothing, give no XP and vanish when they expire.")
        @Config.Name("Conjurer: Spectral Pool")
        public String[] spectralPool = { "srparasites:rupter", "srparasites:buglin" };

        @Config.Name("Conjurer: Spectral Min")
        @Config.RangeInt(min = 0, max = 16)
        public int spectralMin = 2;

        @Config.Name("Conjurer: Spectral Max")
        @Config.RangeInt(min = 0, max = 16)
        public int spectralMax = 3;

        @Config.Comment("Most spectrals one Conjurer keeps alive at once.")
        @Config.Name("Conjurer: Spectral Limit")
        @Config.RangeInt(min = 0, max = 32)
        public int spectralLimit = 4;

        @Config.Name("Conjurer: Spectral Lifetime Seconds")
        @Config.RangeInt(min = 1, max = 600)
        public int spectralLifetimeSeconds = 20;

        @Config.Comment("Seconds between Spectral Swarm casts.")
        @Config.Name("Conjurer: Spectral Cooldown Seconds")
        @Config.RangeInt(min = 1, max = 600)
        public int spectralCooldownSeconds = 12;

        @Config.Comment("Radius in which a hurt parasite (under 50% health) gets Absorption II.")
        @Config.Name("Conjurer: Ward Allies Radius")
        @Config.RangeDouble(min = 0.0, max = 32.0)
        public double wardAlliesRadius = 10.0;

        @Config.Name("Conjurer: Ward Allies Cooldown Seconds")
        @Config.RangeInt(min = 1, max = 600)
        public int wardAlliesCooldownSeconds = 12;
    }
```

- [ ] **Step 3: Lang.** In `en_us.lang`:
  - after `config.insanetweaks.category.entities.sentinel=Sentinel` add
    `config.insanetweaks.category.entities.magic_parasites=Magic Parasites`;
  - after `entity.insanetweaks.sim_battlemage.name=Assimilated Battlemage` add:

```
entity.insanetweaks.magic_longarms.name=Arcane Blinker
entity.insanetweaks.magic_manducater.name=Arcane Warden
entity.insanetweaks.magic_summoner.name=Arcane Conjurer
```

- [ ] **Step 4: Compile** — `./gradlew "-Dorg.gradle.java.home=…" :insanetweaks:compileJava`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit** — `git commit -m "feat(insanetweaks): magic parasites config and names"`.

---

### Task 3: Shared pieces

**Files:**
- Create: `…/entities/magic/IMagicParasite.java`
- Create: `…/entities/magic/SpectralTags.java`
- Create: `…/entities/magic/MagicParasiteTraits.java`

- [ ] **Step 1:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/IMagicParasite.java`

```java
package com.spege.insanetweaks.entities.magic;

/**
 * Implemented by this mod's magic parasite classes only. Never added to SRP's own hierarchy, which
 * must not gain interfaces (see CLAUDE.md, mixin rules). The state is a synced data parameter that
 * the glow layer reads.
 */
public interface IMagicParasite {

    byte STATE_IDLE = 0;
    byte STATE_TELEGRAPH = 1;
    byte STATE_WARD = 2;

    byte getArcaneState();

    void setArcaneState(byte state);
}
```

- [ ] **Step 2:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/SpectralTags.java`

```java
package com.spege.insanetweaks.entities.magic;

import net.minecraft.entity.Entity;

/**
 * Entity-data markers for a Conjurer's spectral summons. These are real SRP mobs, so the marker
 * lives in Forge entity data (saved with the entity) rather than in a class of ours. The expiry
 * is in world time, so it survives a reload even though the Conjurer's own list does not.
 */
public final class SpectralTags {

    /** Boolean: summoned by an Arcane Conjurer. */
    public static final String SPECTRAL = "InsaneTweaksSpectral";
    /** Long: world time at which it dissolves. */
    public static final String UNTIL = "InsaneTweaksSpectralUntil";

    private SpectralTags() {
    }

    public static boolean isSpectral(Entity entity) {
        return entity.getEntityData().getBoolean(SPECTRAL);
    }

    public static void mark(Entity entity, long expiresAt) {
        entity.getEntityData().setBoolean(SPECTRAL, true);
        entity.getEntityData().setLong(UNTIL, expiresAt);
    }

    public static boolean isExpired(Entity entity, long worldTime) {
        return worldTime >= entity.getEntityData().getLong(UNTIL);
    }
}
```

- [ ] **Step 3:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/MagicParasiteTraits.java`

```java
package com.spege.insanetweaks.entities.magic;

import java.util.Random;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.sapling.SaplingSiteCheck;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * What the three magic parasites share: config access, the Arcane-touched damage passive, the
 * purple particle language, teleporting and finding a free spot. No client types. Particles from
 * the server go through {@link WorldServer#spawnParticle}; the client aura uses the common
 * {@link World#spawnParticle}, which is a no-op on a server.
 */
public final class MagicParasiteTraits {

    private static final int POSITION_ATTEMPTS = 10;
    private static final int VERTICAL_SEARCH = 3;

    private MagicParasiteTraits() {
    }

    public static EntitiesCategory.MagicParasites cfg() {
        return ModConfig.entities.magicParasites;
    }

    public static boolean enabled() {
        return cfg().enableMagicParasites;
    }

    /** Multiplies an attribute's base value; called from applyEntityAttributes, after SRP's. */
    public static void multiplyBase(EntityLivingBase entity, IAttribute attribute, double multiplier) {
        IAttributeInstance instance = entity.getEntityAttribute(attribute);
        if (instance != null && multiplier != 1.0D) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }

    /** Arcane-touched: less magic damage, more fire damage. */
    public static float scaleIncoming(DamageSource source, float amount) {
        if (!enabled()) {
            return amount;
        }
        return ArcaneDamage.scale(amount, source.isMagicDamage(), source.isFireDamage(), cfg().magicDamageTaken,
                cfg().fireDamageTaken);
    }

    /** A puff of purple witch particles, sent from the server. */
    public static void burst(World world, double x, double y, double z, int count, double spread) {
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y, z, count, spread, spread,
                    spread, 0.02D);
        }
    }

    /** The telegraph cue around a caster's body. */
    public static void telegraph(EntityLivingBase entity) {
        burst(entity.world, entity.posX, entity.posY + entity.height * 0.6D, entity.posZ, 6, entity.width * 0.6D);
    }

    /** A dotted line of particles, e.g. Ward Allies' beam. */
    public static void beam(World world, double x1, double y1, double z1, double x2, double y2, double z2) {
        int points = 10;
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            burst(world, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, 1, 0.05D);
        }
    }

    public static void sound(Entity entity, SoundEvent sound, float pitch) {
        entity.world.playSound(null, entity.posX, entity.posY, entity.posZ, sound, SoundCategory.HOSTILE, 1.0F,
                pitch);
    }

    /**
     * Tries each offset around a base point with vanilla's {@code attemptTeleport} (searches down
     * for ground, refuses solid or liquid collisions and resets the position on failure). Returns
     * whether one succeeded.
     */
    public static boolean tryTeleport(EntityLivingBase entity, double baseX, double baseY, double baseZ,
            double[][] offsets) {
        double fromX = entity.posX;
        double fromY = entity.posY;
        double fromZ = entity.posZ;
        for (double[] o : offsets) {
            if (entity.attemptTeleport(baseX + o[0], baseY, baseZ + o[1])) {
                burst(entity.world, fromX, fromY + entity.height * 0.5D, fromZ, 16, 0.4D);
                burst(entity.world, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ, 16, 0.4D);
                sound(entity, SoundEvents.ENTITY_ENDERMEN_TELEPORT, 1.3F);
                return true;
            }
        }
        return false;
    }

    /** Removes a spectral with a purple poof. {@code setDead} skips onDeath: no drops, no SRP points. */
    public static void dispel(Entity entity) {
        burst(entity.world, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ, 20, 0.4D);
        entity.world.playSound(null, entity.posX, entity.posY, entity.posZ, SoundEvents.ENTITY_ILLAGER_MIRROR_MOVE,
                SoundCategory.HOSTILE, 0.8F, 1.4F);
        entity.setDead();
    }

    /** Client-side aura: sparse while idle, dense while an ability is up. */
    public static void clientAura(EntityLivingBase entity, boolean active) {
        if (!entity.world.isRemote || entity.ticksExisted % (active ? 1 : 5) != 0) {
            return;
        }
        Random rand = entity.getRNG();
        entity.world.spawnParticle(EnumParticleTypes.SPELL_WITCH,
                entity.posX + (rand.nextDouble() - 0.5D) * entity.width,
                entity.posY + rand.nextDouble() * entity.height,
                entity.posZ + (rand.nextDouble() - 0.5D) * entity.width, 0.0D, 0.0D, 0.0D);
    }

    /** A spot with solid ground and two free blocks, {@code min}-{@code max} blocks from center. */
    public static BlockPos findStandable(World world, BlockPos center, Random rand, int min, int max) {
        int inner = Math.min(min, max);
        int outer = Math.max(min, max);
        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0D;
            double dist = inner + rand.nextDouble() * (outer - inner);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int dy = VERTICAL_SEARCH; dy >= -VERTICAL_SEARCH; dy--) {
                BlockPos feet = new BlockPos(x, center.getY() + dy, z);
                if (isStandable(world, feet)) {
                    return feet;
                }
            }
        }
        return null;
    }

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
}
```

- [ ] **Step 4: Compile** — `:insanetweaks:compileJava`. Expected: BUILD SUCCESSFUL. If `SoundEvents.ENTITY_ILLAGER_MIRROR_MOVE` does not exist in these mappings, use `SoundEvents.ENTITY_ILLAGER_CAST_SPELL`; if that is missing too, use `SoundEvents.ENTITY_ENDERMEN_TELEPORT`.

- [ ] **Step 5: Commit** — `git commit -m "feat(insanetweaks): magic parasite shared traits and spectral tags"`.

---

### Task 4: Arcane Blinker

**Files:**
- Create: `…/entities/magic/EntityMagicLongarms.java`
- Create: `…/entities/magic/ai/EntityAIBlinkStrike.java`

- [ ] **Step 1:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/EntityMagicLongarms.java`

```java
package com.spege.insanetweaks.entities.magic;

import java.util.UUID;

import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityShyco;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.ai.EntityAIBlinkStrike;

import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Arcane Blinker: SRP's primitive longarms with Blink Strike (telegraphed teleport behind its
 * target, the next hit within 2 s is stronger) and Phase Dodge (a projectile may make it blink
 * sideways instead of landing). Native shockwave / launch skills are untouched.
 */
public class EntityMagicLongarms extends EntityShyco implements IMagicParasite {

    private static final DataParameter<Byte> ARCANE_STATE = EntityDataManager.createKey(EntityMagicLongarms.class,
            DataSerializers.BYTE);
    private static final UUID BLINK_BONUS_ID = UUID.fromString("5f3c9a1e-7b2d-4c8e-9a61-2d4f8b0c6e13");
    /** Op 2 multiplies the final value: 0.3 = +30%. */
    private static final int OP_MULTIPLY_TOTAL = 2;

    // No initialisers: initEntityAI runs from the superclass constructor, before field initialisers.
    private long blinkBonusUntil;
    private long nextDodgeTime;

    public EntityMagicLongarms(World world) {
        super(world);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(ARCANE_STATE, Byte.valueOf(STATE_IDLE));
    }

    @Override
    protected void initEntityAI() {
        super.initEntityAI();
        this.tasks.addTask(1, new EntityAIBlinkStrike(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.MAX_HEALTH, c.blinkerHealthMultiplier);
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.ATTACK_DAMAGE, c.blinkerDamageMultiplier);
    }

    @Override
    public byte getArcaneState() {
        return this.dataManager.get(ARCANE_STATE).byteValue();
    }

    @Override
    public void setArcaneState(byte state) {
        this.dataManager.set(ARCANE_STATE, Byte.valueOf(state));
    }

    /** Called by Blink Strike after a successful teleport. */
    public void grantBlinkBonus(long until) {
        IAttributeInstance damage = this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        damage.removeModifier(BLINK_BONUS_ID);
        damage.applyModifier(new AttributeModifier(BLINK_BONUS_ID, "Blink Strike",
                MagicParasiteTraits.cfg().blinkBonusDamage, OP_MULTIPLY_TOTAL));
        this.blinkBonusUntil = until;
    }

    private void clearBlinkBonus() {
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).removeModifier(BLINK_BONUS_ID);
        this.blinkBonusUntil = 0L;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (this.world.isRemote) {
            MagicParasiteTraits.clientAura(this, getArcaneState() != STATE_IDLE);
        } else if (this.world.getTotalWorldTime() > this.blinkBonusUntil
                && this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getModifier(BLINK_BONUS_ID) != null) {
            // Also clears a modifier that was saved mid-window and reloaded (blinkBonusUntil is 0 then).
            clearBlinkBonus();
        }
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        boolean hit = super.attackEntityAsMob(target);
        if (hit && this.blinkBonusUntil != 0L) {
            clearBlinkBonus();
        }
        return hit;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (!this.world.isRemote && tryPhaseDodge(source)) {
            return false;
        }
        return super.attackEntityFrom(source, MagicParasiteTraits.scaleIncoming(source, amount));
    }

    private boolean tryPhaseDodge(DamageSource source) {
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        if (!c.enableMagicParasites || !source.isProjectile() || this.isEntityInvulnerable(source)) {
            return false;
        }
        long now = this.world.getTotalWorldTime();
        if (now < this.nextDodgeTime || this.rand.nextDouble() >= c.dodgeChance) {
            return false;
        }
        Entity projectile = source.getImmediateSource();
        double mx = projectile != null ? projectile.motionX : 0.0D;
        double mz = projectile != null ? projectile.motionZ : 0.0D;
        double distance = BlinkPlanner.lerp(3.0D, 5.0D, this.rand.nextDouble());
        double[][] offsets = BlinkPlanner.sidestep(mx, mz, distance, this.rand.nextBoolean(), this.rotationYaw);
        if (!MagicParasiteTraits.tryTeleport(this, this.posX, this.posY, this.posZ, offsets)) {
            return false;
        }
        this.nextDodgeTime = now + c.dodgeCooldownSeconds * 20L;
        return true;
    }
}
```

- [ ] **Step 2:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/ai/EntityAIBlinkStrike.java`

```java
package com.spege.insanetweaks.entities.magic.ai;

import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.BlinkPlanner;
import com.spege.insanetweaks.entities.magic.EntityMagicLongarms;
import com.spege.insanetweaks.entities.magic.IMagicParasite;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.SoundEvents;

/**
 * Blink Strike: with a target 6-20 blocks away and in sight, telegraph for 0.75 s, then teleport
 * behind it (or beside it if behind is blocked). Mutex 0: it never claims the movement or look
 * slots, so SRP's own tasks keep running and the telegraph is a cue, not a freeze. Reads the
 * target, never picks one.
 */
public class EntityAIBlinkStrike extends EntityAIBase {

    private static final int TELEGRAPH_TICKS = 15;
    private static final long BONUS_WINDOW_TICKS = 40L;

    private final EntityMagicLongarms blinker;
    private int ticks;
    private long nextReady;

    public EntityAIBlinkStrike(EntityMagicLongarms blinker) {
        this.blinker = blinker;
        this.setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        if (!c.enableMagicParasites || this.blinker.world.getTotalWorldTime() < this.nextReady
                || !this.blinker.onGround) {
            return false;
        }
        EntityLivingBase target = this.blinker.getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            return false;
        }
        double distance = this.blinker.getDistance(target);
        return distance >= c.blinkMinRange && distance <= c.blinkMaxRange
                && this.blinker.getEntitySenses().canSee(target);
    }

    @Override
    public void startExecuting() {
        this.ticks = 0;
        this.blinker.setArcaneState(IMagicParasite.STATE_TELEGRAPH);
        this.blinker.getNavigator().clearPath();
        MagicParasiteTraits.sound(this.blinker, SoundEvents.EVOCATION_ILLAGER_PREPARE_ATTACK, 1.4F);
    }

    @Override
    public boolean shouldContinueExecuting() {
        EntityLivingBase target = this.blinker.getAttackTarget();
        return this.ticks < TELEGRAPH_TICKS && target != null && target.isEntityAlive();
    }

    @Override
    public void updateTask() {
        this.ticks++;
        EntityLivingBase target = this.blinker.getAttackTarget();
        if (target == null) {
            return;
        }
        this.blinker.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        if (this.ticks % 3 == 0) {
            MagicParasiteTraits.telegraph(this.blinker);
        }
        if (this.ticks >= TELEGRAPH_TICKS) {
            blink(target);
        }
    }

    @Override
    public void resetTask() {
        this.blinker.setArcaneState(IMagicParasite.STATE_IDLE);
        this.nextReady = this.blinker.world.getTotalWorldTime() + MagicParasiteTraits.cfg().blinkCooldownSeconds * 20L;
    }

    private void blink(EntityLivingBase target) {
        double distance = BlinkPlanner.lerp(1.5D, 2.5D, this.blinker.getRNG().nextDouble());
        double[][] offsets = BlinkPlanner.behind(target.rotationYaw, distance);
        if (MagicParasiteTraits.tryTeleport(this.blinker, target.posX, target.posY, target.posZ, offsets)) {
            this.blinker.faceEntity(target, 360.0F, 360.0F);
            this.blinker.grantBlinkBonus(this.blinker.world.getTotalWorldTime() + BONUS_WINDOW_TICKS);
        }
    }
}
```

- [ ] **Step 3: Compile** — `:insanetweaks:compileJava`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `git commit -m "feat(insanetweaks): Arcane Blinker - blink strike and phase dodge"`.

---

### Task 5: Arcane Warden

**Files:**
- Create: `…/entities/magic/EntityMagicManducater.java`
- Create: `…/entities/magic/ai/EntityAIArcaneWard.java`

- [ ] **Step 1:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/EntityMagicManducater.java`

```java
package com.spege.insanetweaks.entities.magic;

import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityHull;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.ai.EntityAIArcaneWard;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Arcane Warden: SRP's primitive manducater as a tank and support. Arcane Ward halves incoming
 * damage, blocks and returns projectiles, and gives nearby parasites Resistance (Bastion). The
 * native pull also slows the pulled entity (Anchor Pull).
 */
public class EntityMagicManducater extends EntityHull implements IMagicParasite {

    private static final DataParameter<Byte> ARCANE_STATE = EntityDataManager.createKey(EntityMagicManducater.class,
            DataSerializers.BYTE);
    /**
     * Vanilla's EntityArrow, when its hit is refused, multiplies its own motion by -0.1 after
     * attackEntityFrom returns. Pre-multiplying by 8 makes the net result a -0.8 reflection.
     */
    private static final double ARROW_PRE_REFLECT = 8.0D;
    private static final double REFLECT = -0.8D;

    private boolean wasPulling;

    public EntityMagicManducater(World world) {
        super(world);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(ARCANE_STATE, Byte.valueOf(STATE_IDLE));
    }

    @Override
    protected void initEntityAI() {
        super.initEntityAI();
        this.tasks.addTask(1, new EntityAIArcaneWard(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.MAX_HEALTH, c.wardenHealthMultiplier);
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.ARMOR, c.wardenArmorMultiplier);
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.ATTACK_DAMAGE, c.wardenDamageMultiplier);
    }

    @Override
    public byte getArcaneState() {
        return this.dataManager.get(ARCANE_STATE).byteValue();
    }

    @Override
    public void setArcaneState(byte state) {
        this.dataManager.set(ARCANE_STATE, Byte.valueOf(state));
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (this.world.isRemote) {
            MagicParasiteTraits.clientAura(this, getArcaneState() != STATE_IDLE);
            return;
        }
        boolean pulling = this.hasTargetedEntity();
        if (pulling && !this.wasPulling && MagicParasiteTraits.enabled()) {
            int ticks = MagicParasiteTraits.cfg().pullSlownessSeconds * 20;
            EntityLivingBase pulled = this.getTargetedEntity();
            if (pulled != null && ticks > 0) {
                pulled.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, ticks, 0));
            }
        }
        this.wasPulling = pulling;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        float scaled = amount;
        if (!this.world.isRemote && getArcaneState() == STATE_WARD && MagicParasiteTraits.enabled()) {
            if (source.isProjectile()) {
                reflect(source.getImmediateSource());
                return false;
            }
            scaled = (float) (scaled * MagicParasiteTraits.cfg().wardDamageTaken);
        }
        return super.attackEntityFrom(source, MagicParasiteTraits.scaleIncoming(source, scaled));
    }

    private void reflect(Entity projectile) {
        if (projectile == null) {
            return;
        }
        double factor = REFLECT;
        if (projectile instanceof EntityArrow) {
            ((EntityArrow) projectile).shootingEntity = this;
            factor = ARROW_PRE_REFLECT;
        }
        projectile.motionX *= factor;
        projectile.motionY *= factor;
        projectile.motionZ *= factor;
        projectile.velocityChanged = true;
        MagicParasiteTraits.burst(this.world, projectile.posX, projectile.posY, projectile.posZ, 8, 0.2D);
        MagicParasiteTraits.sound(this, SoundEvents.ITEM_SHIELD_BLOCK, 1.3F);
    }
}
```

- [ ] **Step 2:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/ai/EntityAIArcaneWard.java`

```java
package com.spege.insanetweaks.entities.magic.ai;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.EntityMagicManducater;
import com.spege.insanetweaks.entities.magic.IMagicParasite;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;

/**
 * Arcane Ward: when hurt below 70% or with a target within 10 blocks, telegraph for 0.5 s, then
 * hold the ward (STATE_WARD, which EntityMagicManducater.attackEntityFrom reads) for the configured
 * time. Resistance I is refreshed every second on nearby parasites (Bastion). The cooldown counts
 * from the end. Mutex 0, like the other abilities.
 */
public class EntityAIArcaneWard extends EntityAIBase {

    private static final int TELEGRAPH_TICKS = 10;
    private static final float HEALTH_TRIGGER = 0.7F;
    private static final double TARGET_TRIGGER_SQ = 10.0D * 10.0D;
    private static final int RING_POINTS = 12;

    private final EntityMagicManducater warden;
    private int ticks;
    private long nextReady;

    public EntityAIArcaneWard(EntityMagicManducater warden) {
        this.warden = warden;
        this.setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        if (!MagicParasiteTraits.enabled() || this.warden.world.getTotalWorldTime() < this.nextReady) {
            return false;
        }
        if (this.warden.getHealth() < this.warden.getMaxHealth() * HEALTH_TRIGGER) {
            return true;
        }
        EntityLivingBase target = this.warden.getAttackTarget();
        return target != null && target.isEntityAlive() && this.warden.getDistanceSq(target) <= TARGET_TRIGGER_SQ;
    }

    @Override
    public void startExecuting() {
        this.ticks = 0;
        this.warden.setArcaneState(IMagicParasite.STATE_TELEGRAPH);
        MagicParasiteTraits.sound(this.warden, SoundEvents.EVOCATION_ILLAGER_PREPARE_WOLOLO, 0.8F);
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.warden.isEntityAlive() && this.ticks < TELEGRAPH_TICKS + wardTicks();
    }

    @Override
    public void updateTask() {
        this.ticks++;
        if (this.ticks < TELEGRAPH_TICKS) {
            if (this.ticks % 2 == 0) {
                MagicParasiteTraits.telegraph(this.warden);
            }
            return;
        }
        if (this.ticks == TELEGRAPH_TICKS) {
            this.warden.setArcaneState(IMagicParasite.STATE_WARD);
            MagicParasiteTraits.sound(this.warden, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, 0.7F);
        }
        int warded = this.ticks - TELEGRAPH_TICKS;
        if (warded % 4 == 0) {
            ring();
        }
        if (warded % 20 == 0) {
            bastion();
        }
    }

    @Override
    public void resetTask() {
        this.warden.setArcaneState(IMagicParasite.STATE_IDLE);
        this.nextReady = this.warden.world.getTotalWorldTime() + MagicParasiteTraits.cfg().wardCooldownSeconds * 20L;
    }

    private static int wardTicks() {
        return MagicParasiteTraits.cfg().wardDurationSeconds * 20;
    }

    private void ring() {
        double radius = this.warden.width * 0.5D + 0.8D;
        double y = this.warden.posY + this.warden.height * 0.5D;
        double spin = this.ticks * 0.15D;
        for (int i = 0; i < RING_POINTS; i++) {
            double a = spin + Math.PI * 2.0D * i / RING_POINTS;
            MagicParasiteTraits.burst(this.warden.world, this.warden.posX + Math.cos(a) * radius, y,
                    this.warden.posZ + Math.sin(a) * radius, 1, 0.0D);
        }
    }

    private void bastion() {
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        if (c.bastionRadius <= 0.0D) {
            return;
        }
        double r2 = c.bastionRadius * c.bastionRadius;
        for (EntityParasiteBase ally : this.warden.world.getEntitiesWithinAABB(EntityParasiteBase.class,
                this.warden.getEntityBoundingBox().grow(c.bastionRadius))) {
            if (ally != this.warden && ally.isEntityAlive() && this.warden.getDistanceSq(ally) <= r2) {
                ally.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 30, 0, true, true));
            }
        }
    }
}
```

- [ ] **Step 3: Compile** — `:insanetweaks:compileJava`. Expected: BUILD SUCCESSFUL. If `SoundEvents.EVOCATION_ILLAGER_PREPARE_WOLOLO` is not in these mappings, use `EVOCATION_ILLAGER_PREPARE_ATTACK`.
- [ ] **Step 4: Commit** — `git commit -m "feat(insanetweaks): Arcane Warden - arcane ward, bastion, anchor pull"`.

---

### Task 6: Arcane Conjurer

**Files:**
- Create: `…/entities/magic/EntityMagicSummoner.java`
- Create: `…/entities/magic/ai/EntityAIWardAllies.java`

- [ ] **Step 1:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/EntityMagicSummoner.java`

```java
package com.spege.insanetweaks.entities.magic;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityCanra;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.ai.EntityAIWardAllies;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Arcane Conjurer: SRP's primitive summoner, whose native summon skill (id 1, the vomit summon) is
 * replaced by Spectral Swarm: short-lived, tagged real SRP mobs that drop nothing. Ward Allies
 * shields hurt parasites with Absorption. The native ranged attack stays.
 *
 * <p>Skill protocol (EntityAISkill, bytecode-verified): {@code doSpecialSkill(id)} is called every
 * tick until {@code getFinished(id)} is true, then {@code setFinished(id, false)}. With the feature
 * switched off both calls go straight to SRP's own summon.
 */
public class EntityMagicSummoner extends EntityCanra implements IMagicParasite {

    private static final DataParameter<Byte> ARCANE_STATE = EntityDataManager.createKey(EntityMagicSummoner.class,
            DataSerializers.BYTE);
    private static final byte SKILL_SUMMON = 1;
    private static final int TELEGRAPH_TICKS = 15;
    /** The parasite status SRP's own summon holds while casting. */
    private static final int STATUS_CASTING = 10;
    private static final Set<String> WARNED_IDS = new HashSet<String>();

    /** Transient: who this Conjurer summoned. A reload empties it; the spectrals still expire. */
    private final List<Entity> spectrals = new ArrayList<Entity>();
    private int swarmTicks;
    private boolean swarmFinished;
    private long nextSwarmTime;

    public EntityMagicSummoner(World world) {
        super(world);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(ARCANE_STATE, Byte.valueOf(STATE_IDLE));
    }

    @Override
    protected void initEntityAI() {
        super.initEntityAI();
        this.tasks.addTask(5, new EntityAIWardAllies(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.MAX_HEALTH, c.conjurerHealthMultiplier);
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.ATTACK_DAMAGE, c.conjurerDamageMultiplier);
    }

    @Override
    public byte getArcaneState() {
        return this.dataManager.get(ARCANE_STATE).byteValue();
    }

    @Override
    public void setArcaneState(byte state) {
        this.dataManager.set(ARCANE_STATE, Byte.valueOf(state));
    }

    @Override
    public void doSpecialSkill(byte id) {
        if (id != SKILL_SUMMON || !MagicParasiteTraits.enabled()) {
            super.doSpecialSkill(id);
            return;
        }
        castSwarm();
    }

    @Override
    public boolean getFinished(byte id) {
        if (id == SKILL_SUMMON && MagicParasiteTraits.enabled()) {
            return this.swarmFinished;
        }
        return super.getFinished(id);
    }

    @Override
    public void setFinished(byte id, boolean finished) {
        if (id == SKILL_SUMMON && MagicParasiteTraits.enabled()) {
            this.swarmFinished = finished;
            return;
        }
        super.setFinished(id, finished);
    }

    private void castSwarm() {
        if (this.world.isRemote) {
            return;
        }
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        long now = this.world.getTotalWorldTime();
        if (this.swarmTicks == 0) {
            pruneSpectrals();
            if (now < this.nextSwarmTime || this.spectrals.size() >= c.spectralLimit || this.getAttackTarget() == null) {
                this.swarmFinished = true;
                return;
            }
            this.setParasiteStatus(STATUS_CASTING);
            this.getNavigator().clearPath();
            this.setArcaneState(STATE_TELEGRAPH);
            MagicParasiteTraits.sound(this, SoundEvents.EVOCATION_ILLAGER_PREPARE_SUMMON, 1.2F);
        }
        this.swarmTicks++;
        if (this.swarmTicks % 3 == 0) {
            MagicParasiteTraits.telegraph(this);
        }
        if (this.swarmTicks < TELEGRAPH_TICKS) {
            return;
        }
        summonSpectrals(now);
        this.swarmTicks = 0;
        this.swarmFinished = true;
        this.setParasiteStatus(0);
        this.setArcaneState(STATE_IDLE);
        this.nextSwarmTime = now + c.spectralCooldownSeconds * 20L;
    }

    private void summonSpectrals(long now) {
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        String[] pool = c.spectralPool;
        if (pool.length == 0) {
            return;
        }
        int count = SpectralBudget.toSummon(c.spectralMin, c.spectralMax, this.rand.nextDouble(),
                this.spectrals.size(), c.spectralLimit);
        EntityLivingBase target = this.getAttackTarget();
        BlockPos center = new BlockPos(this);
        for (int i = 0; i < count; i++) {
            BlockPos pos = MagicParasiteTraits.findStandable(this.world, center, this.rand, 2, 4);
            if (pos == null) {
                continue;
            }
            String id = pool[this.rand.nextInt(pool.length)];
            Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(id), this.world);
            if (!(entity instanceof EntityLiving)) {
                if (WARNED_IDS.add(id)) {
                    InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Arcane Conjurer: '{}' is not a living entity id "
                            + "- skipped. Check 'Conjurer: Spectral Pool'.", id);
                }
                continue;
            }
            EntityLiving mob = (EntityLiving) entity;
            mob.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    this.rand.nextFloat() * 360.0F, 0.0F);
            mob.onInitialSpawn(this.world.getDifficultyForLocation(pos), null);
            SpectralTags.mark(mob, now + c.spectralLifetimeSeconds * 20L);
            if (!this.world.spawnEntity(mob)) {
                continue;
            }
            this.spectrals.add(mob);
            MagicParasiteTraits.burst(this.world, mob.posX, mob.posY + mob.height * 0.5D, mob.posZ, 12, 0.3D);
            if (target != null) {
                if (mob instanceof EntityParasiteBase) {
                    ((EntityParasiteBase) mob).setAttackTarget(target, 60);
                } else {
                    mob.setAttackTarget(target);
                }
            }
        }
    }

    private void pruneSpectrals() {
        Iterator<Entity> it = this.spectrals.iterator();
        while (it.hasNext()) {
            if (!it.next().isEntityAlive()) {
                it.remove();
            }
        }
    }

    @Override
    public void onDeath(DamageSource cause) {
        super.onDeath(cause);
        if (!this.world.isRemote) {
            for (Entity spectral : this.spectrals) {
                if (spectral.isEntityAlive()) {
                    MagicParasiteTraits.dispel(spectral);
                }
            }
            this.spectrals.clear();
        }
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (this.world.isRemote) {
            MagicParasiteTraits.clientAura(this, getArcaneState() != STATE_IDLE);
        }
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return super.attackEntityFrom(source, MagicParasiteTraits.scaleIncoming(source, amount));
    }
}
```

- [ ] **Step 2:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/magic/ai/EntityAIWardAllies.java`

```java
package com.spege.insanetweaks.entities.magic.ai;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.EntityMagicSummoner;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;

/**
 * Ward Allies: once a second, the most hurt parasite in range below 50% health (and without
 * Absorption) gets Absorption II for 10 s, with a beam from the Conjurer. A one-shot task: all
 * work is in startExecuting, then it ends.
 */
public class EntityAIWardAllies extends EntityAIBase {

    private static final float HEALTH_TRIGGER = 0.5F;
    private static final int ABSORPTION_TICKS = 200;
    private static final int ABSORPTION_II = 1;

    private final EntityMagicSummoner caster;
    private long nextReady;
    private EntityParasiteBase chosen;

    public EntityAIWardAllies(EntityMagicSummoner caster) {
        this.caster = caster;
        this.setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        if (!MagicParasiteTraits.enabled() || this.caster.ticksExisted % 20 != 0
                || this.caster.world.getTotalWorldTime() < this.nextReady) {
            return false;
        }
        this.chosen = pickAlly();
        return this.chosen != null;
    }

    @Override
    public void startExecuting() {
        EntityParasiteBase ally = this.chosen;
        this.chosen = null;
        if (ally == null) {
            return;
        }
        ally.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, ABSORPTION_TICKS, ABSORPTION_II));
        MagicParasiteTraits.beam(this.caster.world, this.caster.posX, this.caster.posY + this.caster.height * 0.7D,
                this.caster.posZ, ally.posX, ally.posY + ally.height * 0.5D, ally.posZ);
        MagicParasiteTraits.sound(ally, SoundEvents.ENTITY_ILLAGER_CAST_SPELL, 1.5F);
        this.nextReady = this.caster.world.getTotalWorldTime()
                + MagicParasiteTraits.cfg().wardAlliesCooldownSeconds * 20L;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return false;
    }

    private EntityParasiteBase pickAlly() {
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        double r2 = c.wardAlliesRadius * c.wardAlliesRadius;
        EntityParasiteBase best = null;
        float bestRatio = HEALTH_TRIGGER;
        for (EntityParasiteBase p : this.caster.world.getEntitiesWithinAABB(EntityParasiteBase.class,
                this.caster.getEntityBoundingBox().grow(c.wardAlliesRadius))) {
            if (p == this.caster || !p.isEntityAlive() || p.isPotionActive(MobEffects.ABSORPTION)
                    || this.caster.getDistanceSq(p) > r2) {
                continue;
            }
            float ratio = p.getHealth() / p.getMaxHealth();
            if (ratio < bestRatio) {
                bestRatio = ratio;
                best = p;
            }
        }
        return best;
    }
}
```

- [ ] **Step 3: Compile** — `:insanetweaks:compileJava`. Expected: BUILD SUCCESSFUL. If `ENTITY_ILLAGER_CAST_SPELL` is missing, use `EVOCATION_ILLAGER_CAST_SPELL`.
- [ ] **Step 4: Commit** — `git commit -m "feat(insanetweaks): Arcane Conjurer - spectral swarm and ward allies"`.

---

### Task 7: Drops, spectral expiry, registration

**Files:**
- Create: `…/events/MagicParasiteHandler.java`
- Create: `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/entities/magic_parasite.json`
- Modify: `…/InsaneTweaksMod.java` (entity registration after `sim_battlemage`, loot table next to the sim_wizard tables, handler next to `ParasiteXPFixHandler`)

- [ ] **Step 1:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/events/MagicParasiteHandler.java`

```java
package com.spege.insanetweaks.events;

import java.util.Iterator;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.magic.IMagicParasite;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;
import com.spege.insanetweaks.entities.magic.SpectralTags;
import com.spege.insanetweaks.entities.sapling.SaplingDropPenalty;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Magic parasites' drop rules and the spectral summons' lifetime:
 *
 * <ul>
 *   <li>A magic parasite loses each normal (SRP) drop with {@code baseDropPenalty}, then rolls the
 *   bonus table, which is never reduced. LOWEST priority, so SRP's own drops are already in.</li>
 *   <li>A spectral drops nothing and gives no XP, and dissolves when its time is up, whatever
 *   the master switch says (otherwise switching it off would make spectrals permanent).</li>
 * </ul>
 */
public class MagicParasiteHandler {

    public static final ResourceLocation BONUS_TABLE = new ResourceLocation(InsaneTweaksMod.MODID,
            "entities/magic_parasite");
    private static final int SPECTRAL_CHECK_INTERVAL = 10;

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote || entity.ticksExisted % SPECTRAL_CHECK_INTERVAL != 0
                || !(entity instanceof EntityParasiteBase) || !SpectralTags.isSpectral(entity)) {
            return;
        }
        if (SpectralTags.isExpired(entity, entity.world.getTotalWorldTime())) {
            MagicParasiteTraits.dispel(entity);
        } else {
            MagicParasiteTraits.burst(entity.world, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ, 3,
                    entity.width * 0.5D);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrops(LivingDropsEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (victim.world.isRemote) {
            return;
        }
        if (SpectralTags.isSpectral(victim)) {
            event.getDrops().clear();
            return;
        }
        if (!(victim instanceof IMagicParasite) || !MagicParasiteTraits.enabled()) {
            return;
        }
        double penalty = MagicParasiteTraits.cfg().baseDropPenalty;
        if (penalty > 0.0D) {
            Iterator<EntityItem> drops = event.getDrops().iterator();
            while (drops.hasNext()) {
                drops.next();
                if (!SaplingDropPenalty.keepsDrop(victim.world.rand.nextDouble(), penalty)) {
                    drops.remove();
                }
            }
        }
        addBonusLoot(event, victim);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExperience(LivingExperienceDropEvent event) {
        EntityLivingBase victim = event.getEntityLiving();
        if (!victim.world.isRemote && SpectralTags.isSpectral(victim)) {
            event.setDroppedExperience(0);
        }
    }

    private static void addBonusLoot(LivingDropsEvent event, EntityLivingBase victim) {
        if (!(victim.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) victim.world;
        LootTable table = world.getLootTableManager().getLootTableFromLocation(BONUS_TABLE);
        LootContext.Builder context = new LootContext.Builder(world).withLootedEntity(victim)
                .withDamageSource(event.getSource());
        Entity killer = event.getSource().getTrueSource();
        if (killer instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) killer;
            context.withPlayer(player).withLuck(player.getLuck());
        }
        for (ItemStack stack : table.generateLootForPools(world.rand, context.build())) {
            if (stack.isEmpty()) {
                continue;
            }
            EntityItem item = new EntityItem(world, victim.posX, victim.posY, victim.posZ, stack);
            item.setDefaultPickupDelay();
            event.getDrops().add(item);
        }
    }
}
```

- [ ] **Step 2:** File: `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/entities/magic_parasite.json`

```json
{
  "pools": [
    {
      "name": "abomination_dust",
      "rolls": 1,
      "entries": [
        {
          "type": "item",
          "name": "ebwizardry:spectral_dust",
          "weight": 2,
          "functions": [
            { "function": "set_count", "count": { "min": 1, "max": 1 } },
            { "function": "looting_enchant", "count": { "min": 0, "max": 1 } },
            { "function": "insanetweaks:abomination_meta" }
          ]
        },
        { "type": "empty", "weight": 4 }
      ]
    },
    {
      "name": "arcane_residue",
      "rolls": 1,
      "entries": [
        {
          "type": "item",
          "name": "ebwizardry:magic_crystal",
          "weight": 1,
          "functions": [
            { "function": "set_count", "count": { "min": 0, "max": 2 } },
            { "function": "looting_enchant", "count": { "min": 0, "max": 1 } }
          ]
        }
      ]
    }
  ]
}
```

- [ ] **Step 3: Register the entities** in `InsaneTweaksMod.init` after the `sim_battlemage` registration. Update the "next free" comment:

```java
        // Magic parasites (2026-10-06): purple subclasses of SRP primitives. Egg colours are
        // looked up at render time, never saved.
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "magic_longarms"),
                com.spege.insanetweaks.entities.magic.EntityMagicLongarms.class, "magic_longarms", 121, this, 64, 3,
                true, 0x2B0F3D, 0xB45CFF);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "magic_manducater"),
                com.spege.insanetweaks.entities.magic.EntityMagicManducater.class, "magic_manducater", 122, this, 64,
                3, true, 0x1E0B33, 0x7A3CE0);
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "magic_summoner"),
                com.spege.insanetweaks.entities.magic.EntityMagicSummoner.class, "magic_summoner", 123, this, 64, 3,
                true, 0x33103D, 0xE05CFF);
        // IDs 100-123 used; next free 124.
```

(Replace the old `// IDs 100-120 used; next free 121.` line.)

- [ ] **Step 4: Register the loot table** after the three sim_wizard `LootTableList.register` lines:

```java
        net.minecraft.world.storage.loot.LootTableList.register(
                com.spege.insanetweaks.events.MagicParasiteHandler.BONUS_TABLE);
```

- [ ] **Step 5: Register the handler** inside the existing `if (Loader.isModLoaded(SRP_MODID)) { … ParasiteXPFixHandler … }` block:

```java
            // Magic parasites: drop penalty + bonus loot, spectral summons' expiry and empty drops.
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.MagicParasiteHandler());
```

- [ ] **Step 6: Compile** — `:insanetweaks:compileJava`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 7: Commit** — `git commit -m "feat(insanetweaks): register magic parasites, drop rules and spectral expiry"`.

---

### Task 8: Rendering

**Files:**
- Create: `…/client/renderer/entity/magic/ArcaneTextureSource.java`, `ArcaneTint.java`, `LayerArcaneGlow.java`, `RenderMagicLongarms.java`, `RenderMagicManducater.java`, `RenderMagicSummoner.java`
- Modify: `…/client/ClientProxy.java` (after the `EntitySimBattlemage` registration)

- [ ] **Step 1:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/client/renderer/entity/magic/ArcaneTextureSource.java`

```java
package com.spege.insanetweaks.client.renderer.entity.magic;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** The skin SRP's renderer picked for this entity, which the glow layer re-binds. */
@SideOnly(Side.CLIENT)
public interface ArcaneTextureSource<T> {

    ResourceLocation arcaneTexture(T entity);
}
```

- [ ] **Step 2:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/client/renderer/entity/magic/ArcaneTint.java`

```java
package com.spege.insanetweaks.client.renderer.entity.magic;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Colour multiply around the main model pass: every SRP skin variant (normal, v/b/h, snow) turns
 * purple without shipping a single SRP texture.
 */
@SideOnly(Side.CLIENT)
public final class ArcaneTint {

    private ArcaneTint() {
    }

    public static void begin() {
        double[] rgb = ModConfig.entities.magicParasites.tintRgb;
        GlStateManager.color(channel(rgb, 0), channel(rgb, 1), channel(rgb, 2), 1.0F);
    }

    public static void end() {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static float channel(double[] rgb, int i) {
        if (rgb == null || i >= rgb.length) {
            return 1.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, rgb[i]));
    }
}
```

- [ ] **Step 3:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/client/renderer/entity/magic/LayerArcaneGlow.java`

```java
package com.spege.insanetweaks.client.renderer.entity.magic;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.entities.magic.IMagicParasite;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Fullbright, additive, slightly inflated violet re-render of the entity's own model and skin (the
 * LayerSimWizardGlow technique). Faint while idle, pulsing during a telegraph, bright while
 * warded. Uses the renderer's main model, so the bones are already posed for this frame.
 */
@SideOnly(Side.CLIENT)
public class LayerArcaneGlow<T extends EntityLivingBase> implements LayerRenderer<T> {

    private static final float[] GLOW_RGB = { 0.58F, 0.18F, 0.90F };
    private static final float ALPHA_IDLE = 0.10F;
    private static final float ALPHA_WARD = 0.80F;
    private static final float SHELL_SCALE = 1.03F;

    private final RenderLivingBase<T> renderer;
    private final ArcaneTextureSource<T> textures;

    public <R extends RenderLivingBase<T> & ArcaneTextureSource<T>> LayerArcaneGlow(R renderer) {
        this.renderer = renderer;
        this.textures = renderer;
    }

    @Override
    public void doRenderLayer(@Nonnull T entity, float limbSwing, float limbSwingAmount, float partialTicks,
            float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (!(entity instanceof IMagicParasite) || entity.isInvisible()) {
            return;
        }
        byte state = ((IMagicParasite) entity).getArcaneState();
        float alpha;
        if (state == IMagicParasite.STATE_WARD) {
            alpha = ALPHA_WARD;
        } else if (state == IMagicParasite.STATE_TELEGRAPH) {
            alpha = 0.35F + 0.25F * (float) Math.sin(ageInTicks * 0.6F);
        } else {
            alpha = ALPHA_IDLE;
        }
        ModelBase model = this.renderer.getMainModel();
        Minecraft.getMinecraft().getTextureManager().bindTexture(this.textures.arcaneTexture(entity));

        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GlStateManager.color(GLOW_RGB[0], GLOW_RGB[1], GLOW_RGB[2], alpha);
            GlStateManager.scale(SHELL_SCALE, SHELL_SCALE, SHELL_SCALE);
            model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        } finally {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            int packed = entity.getBrightnessForRender();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) (packed % 65536),
                    (float) (packed / 65536));
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
```

- [ ] **Step 4:** File: `insanetweaks/src/main/java/com/spege/insanetweaks/client/renderer/entity/magic/RenderMagicLongarms.java`

```java
package com.spege.insanetweaks.client.renderer.entity.magic;

import com.dhanantry.scapeandrunparasites.client.renderer.entity.primitive.RenderShyco;
import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityShyco;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** SRP's longarms renderer, tinted, with the arcane glow on top. */
@SideOnly(Side.CLIENT)
public class RenderMagicLongarms extends RenderShyco implements ArcaneTextureSource<EntityShyco> {

    public RenderMagicLongarms(RenderManager manager) {
        super(manager);
        this.addLayer(new LayerArcaneGlow<EntityShyco>(this));
    }

    @Override
    protected void renderModel(EntityShyco entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scaleFactor) {
        ArcaneTint.begin();
        try {
            super.renderModel(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor);
        } finally {
            ArcaneTint.end();
        }
    }

    @Override
    public ResourceLocation arcaneTexture(EntityShyco entity) {
        return this.getEntityTexture(entity);
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/client/renderer/entity/magic/RenderMagicManducater.java`
```java
package com.spege.insanetweaks.client.renderer.entity.magic;

import com.dhanantry.scapeandrunparasites.client.renderer.entity.primitive.RenderHull;
import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityHull;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** SRP's manducater renderer, tinted, with the arcane glow on top. */
@SideOnly(Side.CLIENT)
public class RenderMagicManducater extends RenderHull implements ArcaneTextureSource<EntityHull> {

    public RenderMagicManducater(RenderManager manager) {
        super(manager);
        this.addLayer(new LayerArcaneGlow<EntityHull>(this));
    }

    @Override
    protected void renderModel(EntityHull entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scaleFactor) {
        ArcaneTint.begin();
        try {
            super.renderModel(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor);
        } finally {
            ArcaneTint.end();
        }
    }

    @Override
    public ResourceLocation arcaneTexture(EntityHull entity) {
        return this.getEntityTexture(entity);
    }
}
```

File: `insanetweaks/src/main/java/com/spege/insanetweaks/client/renderer/entity/magic/RenderMagicSummoner.java`
```java
package com.spege.insanetweaks.client.renderer.entity.magic;

import com.dhanantry.scapeandrunparasites.client.renderer.entity.primitive.RenderCanra;
import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityCanra;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** SRP's summoner renderer, tinted, with the arcane glow on top. */
@SideOnly(Side.CLIENT)
public class RenderMagicSummoner extends RenderCanra implements ArcaneTextureSource<EntityCanra> {

    public RenderMagicSummoner(RenderManager manager) {
        super(manager);
        this.addLayer(new LayerArcaneGlow<EntityCanra>(this));
    }

    @Override
    protected void renderModel(EntityCanra entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scaleFactor) {
        ArcaneTint.begin();
        try {
            super.renderModel(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor);
        } finally {
            ArcaneTint.end();
        }
    }

    @Override
    public ResourceLocation arcaneTexture(EntityCanra entity) {
        return this.getEntityTexture(entity);
    }
}
```

- [ ] **Step 5: ClientProxy** — after the `EntitySimBattlemage` registration:

```java
        RenderingRegistry.registerEntityRenderingHandler(
                com.spege.insanetweaks.entities.magic.EntityMagicLongarms.class,
                new IRenderFactory<com.spege.insanetweaks.entities.magic.EntityMagicLongarms>() {
                    @Override
                    public Render<? super com.spege.insanetweaks.entities.magic.EntityMagicLongarms> createRenderFor(
                            RenderManager manager) {
                        return new com.spege.insanetweaks.client.renderer.entity.magic.RenderMagicLongarms(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(
                com.spege.insanetweaks.entities.magic.EntityMagicManducater.class,
                new IRenderFactory<com.spege.insanetweaks.entities.magic.EntityMagicManducater>() {
                    @Override
                    public Render<? super com.spege.insanetweaks.entities.magic.EntityMagicManducater> createRenderFor(
                            RenderManager manager) {
                        return new com.spege.insanetweaks.client.renderer.entity.magic.RenderMagicManducater(manager);
                    }
                });
        RenderingRegistry.registerEntityRenderingHandler(
                com.spege.insanetweaks.entities.magic.EntityMagicSummoner.class,
                new IRenderFactory<com.spege.insanetweaks.entities.magic.EntityMagicSummoner>() {
                    @Override
                    public Render<? super com.spege.insanetweaks.entities.magic.EntityMagicSummoner> createRenderFor(
                            RenderManager manager) {
                        return new com.spege.insanetweaks.client.renderer.entity.magic.RenderMagicSummoner(manager);
                    }
                });
```

- [ ] **Step 6: Compile** — `:insanetweaks:compileJava`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 7: Commit** — `git commit -m "feat(insanetweaks): magic parasite renderers - tint and arcane glow"`.

---

### Task 9: Version, build, verification, deploy

- [ ] **Step 1:** Bump `insanetweaks/build.gradle` `version = '1.22.0'` and `InsaneTweaksMod.VERSION = "1.22.0"`.
- [ ] **Step 2:** Run `./gradlew "-Dorg.gradle.java.home=…" :insanetweaks:test :insanetweaks:build`. Expected: all tests pass and the build succeeds.
- [ ] **Step 3: Bytecode checks.** Extract the jar to the scratchpad, then:
  - side safety: `for c in entities/magic/*.class entities/magic/ai/*.class events/MagicParasiteHandler.class InsaneTweaksMod.class; do javap -v $c | grep -cE 'minecraft/client|fml/client'; done` must print only zeros;
  - override names in the reobfuscated jar: `javap -p EntityMagicSummoner.class` shows `doSpecialSkill(byte)`, `getFinished(byte)`, `setFinished(byte, boolean)`, `func_110147_ax`, `func_70088_a`, `func_184651_r` and `func_70645_a`; `javap -p RenderMagicLongarms.class` shows `func_77036_a`.
- [ ] **Step 4: Deploy.** Copy `insanetweaks-1.22.0.jar` to `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\` and delete `insanetweaks-1.21.1.jar`.
- [ ] **Step 5: Docs.** In `CLAUDE.md`, extend the insanetweaks test list with `entities/magic/BlinkPlanner`, `ArcaneDamage` and `SpectralBudget`.
- [ ] **Step 6: Commit** — `git commit -m "chore(insanetweaks): 1.22.0 - magic parasites"`.
- [ ] **Step 7:** Give the user the in-game checklist from spec §10.
