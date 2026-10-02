# Sim Wizard Natural Spawn Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `sim_wizard` and `sim_battlemage` a real population by spawning them in SRP's two parasite biomes, and fix the SRP evolution-phase lookup that has silently disabled all tier scaling.

**Architecture:** Spawn entries are injected per candidate position through `WorldEvent.PotentialSpawns` rather than registered permanently, because SRP wipes those biomes' spawn lists from a runtime command. A per-dimension population count is maintained on the server tick and read (never computed) on the spawn path. Everything lives in content (`insanetweaks`); no mixins are involved.

**Tech Stack:** Minecraft 1.12.2, Forge 14.23.5.2860, Java 8. Forge event bus (`WorldEvent.PotentialSpawns`, `TickEvent.WorldTickEvent`), Forge `@Config`.

**Spec:** `docs/superpowers/specs/2026-08-13-sim-wizard-natural-spawn-design.md`. Read it first — it carries the bytecode evidence for every non-obvious decision below.

---

## About testing in this repo

🚨 **Do not look for a unit test task. There is none, and that is not an oversight.**

Per CLAUDE.md, `insanetweaks` has no test source set and no test framework. The only subproject with tests is `commandsuggest`, and only because its `core` package deliberately contains no Minecraft types. Every line in this plan touches `World`, `Biome`, `Entity` or Forge config — none of it is reachable from a plain JVM.

The verification ladder that replaces TDD here, in order of strength:

1. **Compile.** `./gradlew :insanetweaks:build` with `-Xlint:all`. This is a real gate, not a formality — it catches wrong imports, wrong signatures and unsafe types.
2. **Startup log.** Task 4 adds a mandatory INFO line reporting how many biomes resolved. A silently empty set is the single most likely failure of this feature, so it must be impossible to miss.
3. **In-game.** Task 7 is the real test, and SRP ships a command that lets us create the conditions on demand instead of waiting for infestation to spread.

Each task still ends in a commit.

---

## File structure

| file | responsibility |
|---|---|
| `insanetweaks/src/main/java/com/spege/insanetweaks/entities/EntitySimWizard.java` | **modify** — one-line phase-lookup fix |
| `insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java` | **modify** — new `NaturalSpawn` subcategory; two spell-pool entries; one corrected comment |
| `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang` | **modify** — lang key for the new config subcategory |
| `insanetweaks/src/main/java/com/spege/insanetweaks/events/SimWizardNaturalSpawnHandler.java` | **create** — the whole spawn mechanism: population tracking + entry injection |
| `insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` | **modify** — conditional registration + biome resolution call |
| `insanetweaks/build.gradle` | **modify** — version bump |

🚨 **Filename note.** The spec's §1.5 and §7 name this file `SimWizardPotentialSpawnHandler`. The final name is **`SimWizardNaturalSpawnHandler`**, because the class ended up owning two handlers (population tracking and entry injection), not just the one. Task 1 updates the spec so the two documents do not drift.

---

## Task 1: Fix the spec's filename references

**Files:**
- Modify: `docs/superpowers/specs/2026-08-13-sim-wizard-natural-spawn-design.md`

- [ ] **Step 1: Update the §1.5 heading line**

In §1.5, replace:

```
`insanetweaks/src/main/java/com/spege/insanetweaks/events/SimWizardPotentialSpawnHandler.java`

One handler. Server-side only by nature of the event; it references no client type, so it needs no
```

with:

```
`insanetweaks/src/main/java/com/spege/insanetweaks/events/SimWizardNaturalSpawnHandler.java`

Two handlers in one class - the population tracker on `TickEvent.WorldTickEvent` and the entry
injector on `WorldEvent.PotentialSpawns`. They share one private map and change together, so they
share a file. Server-side only by nature of both events; the class references no client type, so it needs no
```

- [ ] **Step 2: Update the §7 file table**

In §7, replace the row:

```
| `events/SimWizardPotentialSpawnHandler.java` | **new** — the whole spawn mechanism (§1) |
```

with:

```
| `events/SimWizardNaturalSpawnHandler.java` | **new** — the whole spawn mechanism (§1) |
```

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/specs/2026-08-13-sim-wizard-natural-spawn-design.md
git commit -m "docs: align spec filename with the implementation plan"
```

---

## Task 2: The phase lookup fix

This is spec §4. It is independent of everything else in this plan and is committed on its own so it can be reverted on its own if the playtest says the difficulty jump is too much.

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/entities/EntitySimWizard.java` (inside `readSrpPhase()`)
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java` (the `srpSaveDataId` comment)

- [ ] **Step 1: Fix the argument**

In `EntitySimWizard.readSrpPhase()`, find:

```java
            int rawPhase = data.getEvolutionPhase(cfg.srpSaveDataId) & 0xFF;
```

Replace with:

```java
            // 🚨 getEvolutionPhase takes a DIMENSION id, not the save-data id. SRP passes
            // world.provider.getDimension() at every one of its own call sites (verified in
            // SRPEventHandlerBus, 1.10.7). Passing srpSaveDataId here meant the phase read 0 in
            // every dimension except 104, so rollTier() always did exactly one roll and
            // cachedPhaseBonus never left 1.0 - i.e. "Enable SRP Phase Scaling" did nothing at all.
            int rawPhase = data.getEvolutionPhase(this.world.provider.getDimension()) & 0xFF;
```

- [ ] **Step 2: Correct the now-false config comment**

In `EntitiesCategory.Spawning`, find:

```java
        @Config.Comment({
                "SRP save-data dimension/data id used for evolution phase lookup.",
                "Matches the value used by SrpWizardryAssimilationHelper for conversion (104).",
                "Only touch this if your SRP install uses a different shared data id."
        })
        @Config.Name("SRP Save Data ID")
```

Replace the comment block with:

```java
        @Config.Comment({
                "Id passed to SRPSaveData.get(world, id). It does NOT select the evolution phase -",
                "that is read from the entity's own dimension. SRPSaveData.get is a singleton over",
                "MapStorage and forwards this int only to createData, so the value decides very",
                "little; it is kept to match SrpWizardryAssimilationHelper (104).",
                "Only touch this if your SRP install uses a different shared data id."
        })
        @Config.Name("SRP Save Data ID")
```

- [ ] **Step 3: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/entities/EntitySimWizard.java insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java
git commit -m "fix(simwizard): read the evolution phase of the entity's own dimension

readSrpPhase passed srpSaveDataId (104) where getEvolutionPhase wants a
dimension id, so outside dimension 104 the phase always read 0: rollTier
did exactly one roll regardless of infestation progress and
cachedPhaseBonus never left 1.0, making Enable SRP Phase Scaling a flag
that did nothing. SRP itself passes world.provider.getDimension() at all
three of its own call sites.

Also corrects the srpSaveDataId comment, which claimed to control the
phase lookup. It does not - SRPSaveData.get is a singleton over
MapStorage and only forwards the int to createData."
```

---

## Task 3: The `NaturalSpawn` config subcategory

This is spec §2.

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java`
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Add the subcategory class**

In `EntitiesCategory.java`, add this class immediately after the closing brace of `public static class Spawning { ... }`:

```java
    /**
     * Natural spawning in SRP's parasite biomes, so the sim wizards have a population that does
     * not depend on SRP happening to assimilate an EB Wizardry wizard.
     *
     * <p>Injected per candidate position through {@code WorldEvent.PotentialSpawns} rather than
     * registered as a permanent biome entry - SRP's {@code clearMobSpawnList} wipes those lists
     * and is reachable from a runtime command. See the design spec for the bytecode.
     */
    public static class NaturalSpawn {

        @Config.Comment({
                "Master switch for natural spawning of sim_wizard and sim_battlemage in SRP's",
                "parasite biomes.",
                "Does nothing where those biomes do not exist, so on an uninfested world it is",
                "inert regardless of this setting.",
                "Gates the event handler's registration, hence the restart requirement."
        })
        @Config.Name("Enable Natural Spawn")
        @Config.RequiresMcRestart
        public boolean enableNaturalSpawn = true;

        @Config.Comment({
                "Biomes to spawn in, by registry name. SRP registers exactly TWO parasite biomes",
                "and both are listed by default (BiomeParasiteBoils and BiomeParasiteDemen are",
                "classes in the SRP jar that nothing ever instantiates).",
                "Unknown names are logged as errors during startup and skipped - if nothing ever",
                "spawns, that log line is the first place to look. Read at startup only."
        })
        @Config.Name("Spawn Biomes")
        @Config.RequiresMcRestart
        public String[] spawnBiomes = {
                "srparasites:biomeparasite_shrouded",
                "srparasites:biomeparasite_harlequin"
        };

        @Config.Comment({
                "Spawn weight for sim_wizard, relative to whatever else competes at that position.",
                "🚨 This number controls less than it looks like it does. SRP CLEARS the monster",
                "spawn list of its own parasite biomes and re-adds its parasites per evolution",
                "phase, so in phases where its list is short we are most or all of the list no",
                "matter what this says. 'Max Per Dimension' below is the real lever."
        })
        @Config.Name("Wizard Spawn Weight")
        @Config.RangeInt(min = 0, max = 100)
        public int wizardSpawnWeight = 10;

        @Config.Comment({
                "Spawn weight for sim_battlemage. Deliberately well below the wizard's: it carries",
                "an ADEPT tier floor and a shield, so it is the elite of the zone rather than its",
                "rank and file. 0 disables it without disabling the wizard."
        })
        @Config.Name("Battlemage Spawn Weight")
        @Config.RangeInt(min = 0, max = 100)
        public int battlemageSpawnWeight = 3;

        @Config.Comment({
                "Hard ceiling on how many sim_wizards may exist per dimension.",
                "sim_battlemage is a SUBCLASS of sim_wizard and counts against this same number,",
                "so the cap is shared: a battlemage occupies a wizard's slot.",
                "This is the primary balance lever for the whole feature. Read live."
        })
        @Config.Name("Max Per Dimension")
        @Config.RangeInt(min = 0, max = 200)
        public int maxPerDimension = 12;

        @Config.Comment({
                "Log one line when the per-dimension cap starts blocking spawns, and one when it",
                "stops. Deliberately edge-triggered rather than per-attempt: PotentialSpawns fires",
                "once per candidate spawn position, and a line per event is a measurable cost on",
                "the server thread. Read live."
        })
        @Config.Name("Debug Logging")
        public boolean debugLogging = false;
    }
```

- [ ] **Step 2: Wire it into `AssimilatedWizard`**

In `public static class AssimilatedWizard`, add after the `spawning` field:

```java
        @Config.Name("natural_spawn")
        @Config.LangKey("config.insanetweaks.category.entities.assimilated_wizard.natural_spawn")
        @Config.Comment("Natural spawning in SRP parasite biomes, and the per-dimension cap on it.")
        public final NaturalSpawn naturalSpawn = new NaturalSpawn();
```

- [ ] **Step 3: Add the lang key**

In `en_us.lang`, find:

```
config.insanetweaks.category.entities.assimilated_wizard.spells=Spells
```

Add immediately after it:

```
config.insanetweaks.category.entities.assimilated_wizard.natural_spawn=Natural Spawn
```

- [ ] **Step 4: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "feat(simwizard): natural_spawn config subcategory

Six fields for the spawn feature, in their own subcategory rather than
appended to 'spawning' - which despite its name holds attribute
multipliers, phase scaling and the assimilation map, and would only get
more misleading with six more entries.

Enable Natural Spawn gates handler registration, so it carries
RequiresMcRestart; Max Per Dimension and Debug Logging are read live."
```

---

## Task 4: The spawn handler

This is spec §1. The whole mechanism is one file.

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/events/SimWizardNaturalSpawnHandler.java`

> 🚨 **The code block below is superseded — do not transcribe it.** It was written before
> implementation and review, and it carried two feature-breaking defects plus a crash. The
> authoritative version is the committed file at
> `insanetweaks/src/main/java/com/spege/insanetweaks/events/SimWizardNaturalSpawnHandler.java`
> (commits `bed82e0` → `786cce1` → `f714766`). The block is kept as written so the four findings
> below stay attached to the thing that caused them:
>
> 1. **Feature-breaking.** It built a fresh `Biome.SpawnListEntry` on every event fire.
>    `WorldServer.canCreatureTypeSpawnHere` fires `PotentialSpawns` a **second** time and then does
>    `list.contains(entry)` — and `SpawnListEntry` has no `equals`, so that is reference identity.
>    An entry picked by the first fire is never found by the second: **nothing ever spawns**, and
>    whenever our entry won the roll the whole pack attempt was discarded rather than re-rolled,
>    silently suppressing SRP's own spawns too. Forge documents the requirement on
>    `WorldEvent.PotentialSpawns` itself: *both events must add the same instance*. Fixed with two
>    `static final` singletons whose public `itemWeight` is mutated in place.
> 2. **Unsound thread safety.** `SPAWN_BIOMES` was a `static final HashSet` mutated by
>    `resolveBiomes()` after class init and read from threads that `final` gives no happens-before
>    edge to. Fixed with a `volatile Set<Biome>` built into a local and published once through
>    `Collections.unmodifiableSet`. The original javadoc asserted this was safe, which was worse
>    than saying nothing.
> 3. **The cap did not bind.** Recounting every 40 ticks while `WorldEntitySpawner` runs every tick
>    let a burst reach ~70–80 against a configured 12. Fixed with an `EntityJoinWorldEvent`
>    increment at `LOWEST` priority (so cancelled joins are not counted), with the tick recount
>    still overwriting outright so the two cannot compound.
> 4. **Crash.** Reading `cfg.wizardSpawnWeight` three times across the guard and the assignment let
>    a concurrent config write publish `itemWeight = 0` on an entry that was still added; a list
>    whose only entry has weight 0 makes `WeightedRandom.getRandomItem` throw out of the spawn pass.
>    Fixed by reading each weight once into a local.
>
> Deliberately **not** fixed: the static `POPULATIONS` map is never pruned, so in single-player a
> world change carries the previous world's count for up to 40 ticks. Self-correcting; a fourth
> event subscription is not worth two seconds of a slightly wrong cap.

- [ ] **Step 1: Write the file**

```java
package com.spege.insanetweaks.events;

import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.EntitySimBattlemage;
import com.spege.insanetweaks.entities.EntitySimWizard;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Natural spawning for {@link EntitySimWizard} and {@link EntitySimBattlemage} in SRP's parasite
 * biomes.
 *
 * <h3>Why the entry is injected rather than registered</h3>
 *
 * <p>The obvious implementation is {@code EntityRegistry.addSpawn}, which puts a permanent
 * {@code SpawnListEntry} in each biome's MONSTER list. That entry does not survive:
 * {@code SRPBiomes.clearMobSpawnList()} calls {@code mobListClear()} on both parasite biomes,
 * which calls {@code List.clear()} on all four spawn lists - and its callers are SRP's
 * {@code CommonProxy} during init AND {@code SRPCommandRoot}, i.e. a command available at
 * runtime. One admin command and the feature stops for the rest of the session with nothing in
 * any log.
 *
 * <p>🚨 Do not conclude from {@code SRPSpawning.removeInit()} that the list is safe. That sweep
 * removes entries whose {@code entityClass.toString()} contains {@code "scapeandrunparasites"},
 * which ours does not - it is {@code mobListClear} that kills a permanent entry, not
 * {@code removeInit}.
 *
 * <p>So the entry is appended per candidate position instead, on
 * {@code WorldEvent.PotentialSpawns}, which Forge fires from
 * {@code WorldServer.getSpawnListEntryForTypeAt} immediately before the weighted pick. Nothing of
 * ours lives in any biome list, so there is nothing for SRP to clear; there is no init-ordering
 * question; and the per-dimension cap costs no extra handler, because above the limit we simply
 * do not append. The event fires even when the biome's own list is empty, so appending to a
 * cleared parasite biome works.
 *
 * <p>That we leave no trace is guaranteed by the event itself rather than by our restraint: the
 * {@code PotentialSpawns} constructor does {@code this.list = new ArrayList<>(oldList)}, so
 * {@code getList()} hands out a COPY. Appending to it cannot reach the biome's own list even by
 * accident.
 *
 * <p>This composes with {@code srpwizcore}'s SpawnEngine, which hooks the same event at
 * {@code LOWEST} - i.e. after us - and can still trim us by its per-dimension namespace budget.
 * The stricter of the two limits wins, and no dependency between the mods is created.
 *
 * <h3>Cost</h3>
 *
 * <p>{@code PotentialSpawns} fires once per candidate spawn position, which is a hot path. Hence
 * the ordering in {@link #onPotentialSpawns}: creature type first, then biome, then the cap - the
 * first two reject almost every invocation. And hence the population count is maintained on the
 * world tick and only READ here; a scan of {@code loadedEntityList} per event would be a real
 * cost on the server thread.
 *
 * <p>Keeping the scan on {@code TickEvent.WorldTickEvent} also settles a threading question:
 * SpawnEngine's own javadoc notes that {@code PotentialSpawns} readers can run on EntityThreading
 * workers, and walking {@code loadedEntityList} from one of those is how you get a
 * {@code ConcurrentModificationException} nobody can reproduce. The tick handler is main-thread
 * by construction.
 */
public class SimWizardNaturalSpawnHandler {

    /**
     * How often the per-dimension population is recounted. Two seconds: a cap does not need tick
     * accuracy, and the scan is O(loaded entities), which is not small in this pack. Deliberately
     * a constant rather than a config field - this is a performance knob, not a balance one.
     */
    private static final int REFRESH_INTERVAL_TICKS = 40;

    /**
     * Resolved once from config at init. Empty means the feature is inert, and
     * {@link #resolveBiomes()} has already said so in the log.
     *
     * <p>Read-only after init, so a plain {@link HashSet} is safe even though readers may be off
     * the main thread.
     */
    private static final Set<Biome> SPAWN_BIOMES = new HashSet<Biome>();

    private static final Map<Integer, Population> POPULATIONS =
            new ConcurrentHashMap<Integer, Population>();

    /** Per-dimension population snapshot plus the edge state for cap logging. */
    private static final class Population {
        volatile int count;
        /** True while the cap is blocking, so the log line fires on transitions only. */
        volatile boolean capReported;
    }

    /**
     * Parses {@code Spawn Biomes} into {@link #SPAWN_BIOMES}. Call once from
     * {@code InsaneTweaksMod.init}.
     *
     * <p>Every rejection is an ERROR with its reason and the accepted count goes out at INFO,
     * because an empty set is this feature's most likely failure and is otherwise completely
     * silent - exactly the reasoning behind {@code SpawnEngine.reload()} in srpwizcore.
     */
    public static void resolveBiomes() {
        SPAWN_BIOMES.clear();
        int rejected = 0;
        for (String raw : ModConfig.entities.assimilatedWizard.naturalSpawn.spawnBiomes) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String id = raw.trim();
            Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(id));
            if (biome == null) {
                rejected++;
                InsaneTweaksMod.LOGGER.error(
                        "[InsaneTweaks][SimWizard] Spawn Biomes: '{}' is not a registered biome"
                                + " - IGNORED.", id);
                continue;
            }
            SPAWN_BIOMES.add(biome);
        }
        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks][SimWizard] Natural spawn: {} biome(s) accepted, {} rejected.",
                Integer.valueOf(SPAWN_BIOMES.size()), Integer.valueOf(rejected));
        if (SPAWN_BIOMES.isEmpty()) {
            InsaneTweaksMod.LOGGER.warn(
                    "[InsaneTweaks][SimWizard] Natural spawn is ON but no biome resolved"
                            + " - nothing will ever spawn.");
        }
    }

    /**
     * Recounts one dimension's population every {@link #REFRESH_INTERVAL_TICKS}.
     *
     * <p>Indexed walk with both mutation exceptions caught and the cycle skipped, matching
     * {@code SpawnEngineTickHandler}: the list can be mutated under us by chunk loading or by an
     * entity-threading mod, and a count that is two seconds stale is worth strictly more than a
     * crashed tick.
     */
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != Side.SERVER
                || !(event.world instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.world;
        if (world.getTotalWorldTime() % REFRESH_INTERVAL_TICKS != 0L) {
            return;
        }
        int alive = 0;
        try {
            for (int i = 0; i < world.loadedEntityList.size(); i++) {
                Entity ent = world.loadedEntityList.get(i);
                // EntitySimBattlemage extends EntitySimWizard, so this one test is the shared cap.
                if (ent instanceof EntitySimWizard && !ent.isDead) {
                    alive++;
                }
            }
        } catch (IndexOutOfBoundsException e) {
            return;
        } catch (ConcurrentModificationException e) {
            return;
        }
        population(world.provider.getDimension()).count = alive;
    }

    /** Appends our two entries to the candidate list, unless the dimension is at its cap. */
    @SubscribeEvent
    public void onPotentialSpawns(WorldEvent.PotentialSpawns event) {
        // Cheapest rejections first - this runs once per candidate spawn position.
        if (event.getType() != EnumCreatureType.MONSTER || SPAWN_BIOMES.isEmpty()) {
            return;
        }
        if (!(event.getWorld() instanceof WorldServer)) {
            return;
        }
        WorldServer world = (WorldServer) event.getWorld();
        if (!SPAWN_BIOMES.contains(world.getBiome(event.getPos()))) {
            return;
        }

        EntitiesCategory.NaturalSpawn cfg = ModConfig.entities.assimilatedWizard.naturalSpawn;
        int dim = world.provider.getDimension();
        Population pop = population(dim);

        if (pop.count >= cfg.maxPerDimension) {
            if (!pop.capReported) {
                pop.capReported = true;
                if (cfg.debugLogging) {
                    InsaneTweaksMod.LOGGER.info(
                            "[InsaneTweaks][SimWizard] dim {}: natural spawn now CAPPED"
                                    + " ({} alive, max {}).",
                            Integer.valueOf(dim), Integer.valueOf(pop.count),
                            Integer.valueOf(cfg.maxPerDimension));
                }
            }
            return;
        }
        if (pop.capReported) {
            pop.capReported = false;
            if (cfg.debugLogging) {
                InsaneTweaksMod.LOGGER.info(
                        "[InsaneTweaks][SimWizard] dim {}: natural spawn resumed"
                                + " ({} alive, max {}).",
                        Integer.valueOf(dim), Integer.valueOf(pop.count),
                        Integer.valueOf(cfg.maxPerDimension));
            }
        }

        List<Biome.SpawnListEntry> list = event.getList();
        if (cfg.wizardSpawnWeight > 0) {
            list.add(new Biome.SpawnListEntry(EntitySimWizard.class, cfg.wizardSpawnWeight, 1, 1));
        }
        if (cfg.battlemageSpawnWeight > 0) {
            list.add(new Biome.SpawnListEntry(
                    EntitySimBattlemage.class, cfg.battlemageSpawnWeight, 1, 1));
        }
    }

    private static Population population(int dim) {
        Integer key = Integer.valueOf(dim);
        Population pop = POPULATIONS.get(key);
        if (pop == null) {
            pop = new Population();
            Population raced = POPULATIONS.putIfAbsent(key, pop);
            if (raced != null) {
                pop = raced;
            }
        }
        return pop;
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`.

If it fails on `ForgeRegistries`, confirm the import is `net.minecraftforge.fml.common.registry.ForgeRegistries` — that is the path this codebase uses; `net.minecraftforge.registries.ForgeRegistries` also exists in 1.12.2 and would be inconsistent with every other file here.

- [ ] **Step 3: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/events/SimWizardNaturalSpawnHandler.java
git commit -m "feat(simwizard): natural spawn in SRP parasite biomes

Injects the spawn entries per candidate position on
WorldEvent.PotentialSpawns instead of registering a permanent
SpawnListEntry, because SRPBiomes.clearMobSpawnList wipes all four spawn
lists of both parasite biomes and is reachable from SRPCommandRoot at
runtime - a permanent entry would die silently to one admin command.

The same choice removes any init-ordering question and makes the
per-dimension cap free: above the limit we do not append, so there is no
offer and no spawn. It also composes with srpwizcore's SpawnEngine on the
same event (LOWEST, so after us) with no dependency between the mods.

Population is counted on the world tick, never on the spawn path:
PotentialSpawns fires once per candidate position, and those readers can
run on entity-threading workers where walking loadedEntityList would be a
CME waiting to happen. Cap logging is edge-triggered for the same
hot-path reason."
```

---

## Task 5: Register the handler

This is spec §1.5.

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (in `init`)

- [ ] **Step 1: Add the registration block**

In `init`, find the closing brace of the `if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge) { ... }` block and add immediately after it:

```java
        // Natural spawning for sim_wizard / sim_battlemage. Deliberately NOT inside the
        // enableSrpEbWizardryBridge block above: that flag governs turning EB Wizardry wizards
        // into ours, and a pack may reasonably want the population without the conversion.
        // Biomes are resolved once, here, because the config field is RequiresMcRestart and a
        // registry lookup per spawn attempt would be absurd.
        if (com.spege.insanetweaks.config.ModConfig.entities.assimilatedWizard
                .naturalSpawn.enableNaturalSpawn) {
            com.spege.insanetweaks.events.SimWizardNaturalSpawnHandler.resolveBiomes();
            MinecraftForge.EVENT_BUS.register(
                    new com.spege.insanetweaks.events.SimWizardNaturalSpawnHandler());
        }
```

- [ ] **Step 2: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat(simwizard): register the natural spawn handler

Gated on its own flag rather than enableSrpEbWizardryBridge - that flag
governs converting EB Wizardry wizards into ours, and a pack may want the
population without the conversion. Biomes resolve once here, since the
config field requires a restart anyway."
```

---

## Task 6: Two spells into the default NPC pool

This is spec §3.2.

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java` (the `spellPool` array in `class Spells`)

- [ ] **Step 1: Append the two entries**

Find:

```java
                "insanetweaks:summon_fer_cow",
                "insanetweaks:summon_primitive_yelloweye"
        };
```

Replace with:

```java
                "insanetweaks:summon_fer_cow",
                "insanetweaks:summon_primitive_yelloweye",
                // dispatcher_grasp is the only OFFENSIVE Abomination spell an NPC can cast: it
                // extends SpellRay, which implements the cast(World, EntityLiving, ...) overload
                // that Spell's base returns false from. It is what makes the element visible in
                // combat rather than only in an inventory.
                "insanetweaks:dispatcher_grasp",
                "insanetweaks:summon_light_bomber"
        };
```

- [ ] **Step 2: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java
git commit -m "feat(simwizard): add dispatcher_grasp and summon_light_bomber to the NPC pool

Both already inherit the NPC cast overload (SpellRay and SpellMinion
respectively), so this is a pool edit and not new casting code. The
npcs:false flag on our spell JSONs never gated this - canBeCastBy is
consulted only by EB Wizardry's own wizards and dispensers, and our
ensureSpellPool reads the config list instead.

Four other NPC-capable spells stay out: cleanse puts a BENEFICIAL potion
on whatever it hits and the wizard aims at the player; call_of_demise is
a capstone whose 1800-mana price means nothing to an NPC, which pays no
mana at all; summon_primitive_summoner's recursion is unverified; and
summon_wizard is master-tier, which likewise buys no restraint on an NPC."
```

---

## Task 7: Version bump, build, and in-game verification

This is spec §6. **This is the real test.** Everything before it only proves the code compiles.

**Files:**
- Modify: `insanetweaks/build.gradle`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (`VERSION`)

- [ ] **Step 1: Read the current version**

Run: `grep -n "^version" insanetweaks/build.gradle && grep -n 'String VERSION' insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`

Both must agree before you change anything. 🚨 Per CLAUDE.md these two have drifted before — `InsaneTweaksMod.VERSION` is the one that reports to Forge and shows in the mod list, and it is the one that gets forgotten. If they disagree, fix that first and say so in the commit.

- [ ] **Step 2: Bump both to the next minor**

Content is on the `1.16.x` line after the Abomination element work. Bump the minor, e.g. `1.16.1` → `1.17.0`: this adds a feature and a new config subcategory.

Edit `insanetweaks/build.gradle`:

```groovy
version = '1.17.0'
```

Edit `InsaneTweaksMod.java`:

```java
    public static final String VERSION = "1.17.0";
```

- [ ] **Step 3: Build**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`, and `insanetweaks/build/libs/insanetweaks-1.17.0.jar` exists.

- [ ] **Step 4: Deploy to the test instance**

🚨 Remove the old jar first — two jars of one modid is a duplicate-mod crash.

```bash
rm -f "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/insanetweaks-"*.jar
cp insanetweaks/build/libs/insanetweaks-1.17.0.jar "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/"
```

- [ ] **Step 5: Launch and check the startup log**

Launch the DEv 1.2 instance. Then:

```bash
grep -n "SimWizard" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/latest.log"
```

Expected: a line reading `Natural spawn: 2 biome(s) accepted, 0 rejected.`

If it says `0 biome(s) accepted`, read the ERROR lines above it. Two causes, in order of likelihood:

1. 🚨 **SRP never registered its biomes at all.** `SRPBiomes$RegistrationHandler.onEvent` is gated on SRP's own config flag `SRPConfigWorld.biomeRegster`. With that off, both names are genuinely absent from the registry and nothing this mod does can help. Check `config/srparasites/` before suspecting our code.
2. The registry names are wrong for this SRP build — confirm the real names against the biome registry rather than guessing.

Registry timing is *not* a possible cause: `RegistryEvent.Register<Biome>` fires for every mod strictly before any mod's `preInit`, so the registry is always fully populated by the time `resolveBiomes()` runs in `init`.

🚨 **Do not grep `cleanmix.log` here.** This feature contains no mixin; there is no `APPLY` line to find and its absence proves nothing.

- [ ] **Step 6: Create the test conditions**

SRP ships a command that converts the surrounding biome to Harlequin and refreshes chunks — this is what makes the feature testable without waiting for infestation to spread. It is a **top-level** command, not a subcommand of `/srparasites`. Its usage string, read off `CommandHarlequinHere` in the SRP jar, is:

```
/harlequin_here [radiusChunks=1]
```

Run it with a radius of 2 or 3 so there is room for spawn positions:

```
/harlequin_here 3
```

Expected chat output: `Applied Harlequin to <n> chunk(s) in a <n>x<n> square.` followed by `Server biome at your position now: ...`.

Then confirm the biome with F3. If the command reports `Harlequin biome is not registered (SRPBiomes.biomeHarlequin is null).`, stop — SRP's biomes did not register at all, and nothing in this feature can work until that is resolved.

- [ ] **Step 7: Observe spawns**

Stand in the converted biome at night / in the dark and wait.

Expected: `sim_wizard` appears; `sim_battlemage` appears noticeably less often (weights 10 vs 3).

If nothing appears at all, in order: confirm the biome from step 6, confirm the log line from step 5, then check whether the vanilla monster cap for the dimension is already saturated by SRP.

- [ ] **Step 8: Prove the cap binds**

Set `Max Per Dimension` to `2` in `config/insanetweaks.cfg` (read live — no restart), set `Debug Logging` to `true`, and let two spawn.

Expected: spawning stops, and `latest.log` gets exactly one `natural spawn now CAPPED` line — not one per second. Kill one and confirm a single `natural spawn resumed` line follows.

- [ ] **Step 9: Confirm the drops and the tiers**

Kill wizards of each tier and confirm Abomination spectral dust drops per the economy spec's table (novice ~⅓ chance of 0–1, adept 1–2, master 2–4).

With SRP evolution advanced, confirm tiers above NOVICE now appear — this is Task 2's fix. Before it, every wizard rolled once at 60/30/10 regardless of progress; at phase 4 the distribution should be roughly 22% / 51% / 27%.

🚨 **Watch the difficulty here, and report it.** Task 2's fix also switches on `phaseScalingPerPhase`, which has never been active: at phase 4 that is **+40% health and armour** on top of more frequent high tiers. Spec §4.2 flags this as two curves moving at once. If it is too much, the lever is `phaseScalingPerPhase` (drop to `0.05` or `0`), **not** `tierWeights` — the tier distribution is what the dust economy needs.

- [ ] **Step 10: Commit the version bump**

```bash
git add insanetweaks/build.gradle insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "chore(insanetweaks): 1.17.0 - sim wizard natural spawn"
```

---

## Done when

- `./gradlew :insanetweaks:build` succeeds.
- The startup log reports 2 biomes accepted, 0 rejected.
- Both entity types spawn in a converted parasite biome, with the battlemage visibly rarer.
- Lowering the cap to 2 stops spawning and produces exactly one CAPPED line.
- Tiers above NOVICE appear once SRP evolution has advanced.
- The difficulty observation from Task 7 step 9 has been reported back, whatever it says.
