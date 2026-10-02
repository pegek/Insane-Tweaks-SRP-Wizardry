# Sim wizard: naturalny spawn — plan implementacji

> **Dla agenta:** realizuj zadanie po zadaniu, odhaczając kroki (`- [ ]`). Task 0 jest bramką.
> Jego wynik wybiera wariant Tasku 5 i może przestawić jedną wartość domyślną w Tasku 1.
> Nie zaczynaj kodu przed Taskiem 0.

**Cel:** sim wizard (i rzadziej sim battlemage) pojawia się sam na skażonym gruncie, od fazy SRP 2
(battlemage od 4), domyślnie tylko w Overworldzie. Dzięki temu pył abominacji przestaje zależeć
od tego, czy SRP akurat zasymiluje maga EBW.

**Architektura:**
- **Wpis na listach spawnów.** `EntityRegistry.addSpawn(MONSTER)` robione raz, w `postInit`.
- **Bramka.** Jedna klasa obsługuje `LivingSpawnEvent.CheckSpawn` na priorytecie `LOWEST` i
  potrafi wyłącznie odmówić. Sprawdza po kolei wymiar, fazę, skażony grunt i promień wykluczenia.
- **Flaga NBT.** `EntitySimWizard` dostaje flagę „naturalny”, dzięki której może zniknąć, oraz
  opcjonalnie własne `getCanSpawnHere` bez reguły światła.

Nie piszemy własnego spawnera i nie dodajemy mixinów. Blokada Sanctuary, cap SRP, SpawnEngine i
InControl obejmują nowego moba bez dodatkowego kodu.

**Stos:** Minecraft 1.12.2, Forge 14.23.5.2860, Java 8, ForgeGradle 3, mapowania
`snapshot 20171003-1.12`. W tej wersji mapowań nazwy to `getResourceDomain/Path`, a nie
`getNamespace/Path`. SRParasites 1.10.7.

**Spec:** `docs/superpowers/specs/2026-10-02-sim-wizard-native-spawn-design.md` (zaakceptowany).

**Gałąź:** `feat/sim-wizard-natural-spawn` z `main`, **po** zmergowaniu
`claude/dreamy-lamport-75ijpn`. Ta gałąź niesie 1.16.2 i funkcję lootu
`insanetweaks:abomination_meta`, którą sprawdza Task 8.

**Wersja docelowa:** `insanetweaks 1.17.0`.

---

## Zanim zaczniesz

**Nie ma zestawu testów ani linta.** Zamiast TDD, od najsłabszego do najmocniejszego:

1. **`./gradlew :insanetweaks:build`.** Kompilator jest pętlą zwrotną i każdy task z kodem kończy
   się buildem. JDK 8 ustaw w **user** `~/.gradle/gradle.properties`, bo od 1.16.2 repo go nie
   przypina.
2. **Przeczytanie własnego diffu pod kątem zasad z README** („Side safety”). Ten plan dodaje
   wyłącznie kod serwerowy. Jeśli w którymś pliku pojawi się import `net.minecraft.client.*`,
   coś poszło źle.
3. **Uruchomienie DEv 1.2** (Task 8). Obowiązkowe.

Ścieżki są względem korzenia repo. Java moda: `insanetweaks/src/main/java/com/spege/insanetweaks/`,
w taskach skracana do `…/`.

🚨 **Higiena commitów.** `git commit` bierze cały **index**. Każdy commit wymienia ścieżki:
`git commit -m "…" -- ścieżka/a ścieżka/b`. Nowe pliki najpierw `git add -- <ścieżka>`.

---

### Task 0: Sześć odczytów bytecode SRP 1.10.7 (bramka)

W chmurze tego nie zrobimy: proxy odrzuca cursemaven, a `libs/` jest w `.gitignore`. Trzeba to
zrobić lokalnie, na jarze, z którym paczka faktycznie startuje.

Każdą metodę czytaj **do końca**. Handoff Abomination: „pięć błędów miało jedną przyczynę,
przeczytanie fragmentu i uogólnienie”.

- [ ] **Krok 1: Rozpakuj klasy.**

```sh
mkdir -p /tmp/srp && cd /tmp/srp && unzip -o -q <libs>/SRParasites-1.10.7.jar \
  'com/dhanantry/scapeandrunparasites/entity/ai/misc/EntityParasiteBase.class' \
  'com/dhanantry/scapeandrunparasites/entity/monster/infected/EntityInfHuman.class' \
  'com/dhanantry/scapeandrunparasites/init/SRPSpawning$DimensionHandler.class' \
  'com/dhanantry/scapeandrunparasites/block/*.class'
```

Jeśli ścieżka `EntityParasiteBase` się nie zgadza, zlokalizuj klasę przez
`unzip -l … | grep -i parasitebase`. Pakiet znasz z importów `srpwizmixins`.

- [ ] **Krok 2: Odczyty.** Dla każdej klasy: `javap -p -c -constants <Klasa>.class`.

| # | Co | Czego szukasz | Wpływ na plan |
|---|---|---|---|
| P1 | `DimensionHandler.onSpawn` | Czy woła `setResult(ALLOW/DENY)` dla pasożytów i przy jakich warunkach | Tylko informacja. Bramka na LOWEST z samym DENY działa w obu przypadkach. Zapisz wynik do specu. |
| P2 | `EntityParasiteBase` i `EntityInfHuman`: `getCanSpawnHere` (`func_70601_bi`), `isValidLightLevel` (`func_70814_o`), `getBlockPathWeight` (`func_180484_a`) | Czy **któraś** z tych metod jest nadpisana | **Wybiera wariant Tasku 5.** |
| P3 | `EntityParasiteBase.canDespawn` (`func_70692_ba`), `despawnEntity` (`func_70623_bb`) | Od czego zależy despawn (pole „canD”? config SRP?) | Potwierdza tylko, że flaga z Tasku 4 coś zmienia. Task 4 robisz tak czy inaczej. |
| P4 | — | — | **Rozstrzygnięte:** `EntityParasiteBase extends EntityMob`, czyli `IMob`/MONSTER. |
| P5 | `DimensionHandler.onSpawn`, pętla culla | Czy cull bierze pasożyty w pobliżu gracza | Tylko informacja. Mag spawnuje się 24–128 bloków od gracza. |
| P6 | `BlockInfestedStain`, `BlockParasiteSpreading`, `BlockInfestedRemain`: `canCreatureSpawn`, `isSideSolid`, `isTopSolid`, `getBlockFaceShape` | Czy na skażonym bloku można stanąć jako spawn | Jeśli **nie**: w Tasku 1 `requireInfestedUnderfoot = false`. |

- [ ] **Krok 3: Wyniki do specu.** Dopisz pod każdym punktem sekcji 7 specu jedno zdanie
  „**Wynik (data):** …” z odczytaną instrukcją albo offsetem jako dowodem. Commit:

```bash
git commit -m "docs: spec - wyniki odczytow bytecode SRP dla spawnu sim wizarda" -- docs/superpowers/specs/2026-10-02-sim-wizard-native-spawn-design.md
```

🚨 **Jeśli P2 pokaże nadpisane `getCanSpawnHere` w bazie pasożyta z własną logiką** (faza, wymiar,
lista biomów SRP), **zatrzymaj się i wróć do autora.** Ta logika może samodzielnie blokować
nasz spawn, a zastąpienie jej w ciemno wyłączy coś, czego nie rozumiemy.

---

### Task 1: Konfiguracja `natural_spawn`

**Pliki:**
- Modyfikuj: `…/config/categories/EntitiesCategory.java`
- Modyfikuj: `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Krok 1: Pole podkategorii w `AssimilatedWizard`**, po polu `battlemage`:

```java
        @Config.Name("natural_spawn")
        @Config.LangKey("config.insanetweaks.category.entities.assimilated_wizard.natural_spawn")
        @Config.Comment({
                "Natural spawning on SRP-infested ground. Without it the wizard exists only through",
                "assimilation, which the pack does not control."
        })
        public final NaturalSpawn naturalSpawn = new NaturalSpawn();
```

- [ ] **Krok 2: Klasa `NaturalSpawn`** jako kolejna `public static class` w `EntitiesCategory`:

```java
    /**
     * Natural spawn on infested ground. Spec:
     * docs/superpowers/specs/2026-10-02-sim-wizard-native-spawn-design.md.
     *
     * <p>Only the spawn-list values need a restart - the lists are built once, in postInit. Every
     * gate below is read on each spawn attempt.
     */
    public static class NaturalSpawn {

        @Config.Comment({
                "Let the Assimilated Wizard spawn on its own on SRP-infested ground.",
                "Also off whenever 'Enable Sim Wizard' is off."
        })
        @Config.Name("Enable Natural Spawn")
        @Config.RequiresMcRestart
        public boolean enabled = true;

        @Config.Comment({
                "Spawn-list weight of sim_wizard in every allowed biome. Zombies sit at 100.",
                "Most picks are refused by the gates below, so this is NOT the real spawn rate -",
                "measure it with 'Debug Log' before changing it. 0 removes the entry."
        })
        @Config.Name("Spawn Weight")
        @Config.RangeInt(min = 0, max = 100)
        @Config.RequiresMcRestart
        public int weight = 4;

        @Config.Comment("Spawn-list weight of sim_battlemage. 0 removes the entry.")
        @Config.Name("Battlemage Spawn Weight")
        @Config.RangeInt(min = 0, max = 100)
        @Config.RequiresMcRestart
        public int battlemageWeight = 1;

        @Config.Comment({
                "Biomes carrying any of these Forge BiomeDictionary types get no entry.",
                "Names are matched case-insensitively; names no biome carries are logged once."
        })
        @Config.Name("Excluded Biome Types")
        @Config.RequiresMcRestart
        public String[] excludedBiomeTypes = { "NETHER", "END", "VOID", "MUSHROOM", "OCEAN", "RIVER" };

        @Config.Comment("true: spawn ONLY in the dimensions listed below. false: everywhere EXCEPT them.")
        @Config.Name("Dimension List Is Whitelist")
        public boolean dimensionWhitelist = true;

        @Config.Comment("Dimension ids for the list above. Start with the Overworld and add after measuring.")
        @Config.Name("Dimensions")
        public int[] dimensions = { 0 };

        @Config.Comment("SRP evolution phase the world must have reached before sim_wizard spawns naturally.")
        @Config.Name("Min Phase")
        @Config.RangeInt(min = 0, max = 10)
        public int minPhase = 2;

        @Config.Comment("Same, for sim_battlemage.")
        @Config.Name("Battlemage Min Phase")
        @Config.RangeInt(min = 0, max = 10)
        public int battlemageMinPhase = 4;

        @Config.Comment({
                "The block the wizard would stand on must itself be SRP-infested.",
                "Turn off if infested ground turns out to forbid spawning on top of it - then the",
                "5x5 count below decides alone."
        })
        @Config.Name("Require Infested Block Underfoot")
        public boolean requireInfestedUnderfoot = true;

        @Config.Comment({
                "How many of the 25 blocks in the 5x5 patch under the wizard's feet must be infested.",
                "0 skips the count."
        })
        @Config.Name("Min Infested Ground (of 25)")
        @Config.RangeInt(min = 0, max = 25)
        public int minInfestedGround = 8;

        @Config.Comment({
                "Spawn regardless of light, so infested ground works by day as well. Only has an",
                "effect on natural spawns; summons, eggs and assimilation never check light."
        })
        @Config.Name("Ignore Light Level")
        public boolean ignoreLightLevel = true;

        @Config.Comment("No natural spawn within this many blocks of another sim_wizard or sim_battlemage. 0 disables.")
        @Config.Name("Exclusion Radius")
        @Config.RangeInt(min = 0, max = 128)
        public int exclusionRadius = 48;

        @Config.Comment({
                "Log once a minute how many natural spawn attempts were refused, by reason, and how",
                "many passed. This is how the weight gets calibrated."
        })
        @Config.Name("Debug Log")
        public boolean debugLog = false;
    }
```

Jeśli Task 0 P6 wykazał, że na skażonym gruncie nie da się spawnować, ustaw
`requireInfestedUnderfoot = false`. Jeśli P2 wybrał wariant 5C, `ignoreLightLevel` nie będzie
niczego czytać. Wtedy usuń to pole, żeby konfig nie obiecywał nieistniejącej funkcji.

- [ ] **Krok 3: Lang.** W `en_us.lang`, pod linią `…assimilated_wizard.spells=Spells`:

```
config.insanetweaks.category.entities.assimilated_wizard.natural_spawn=Natural Spawn
```

Plik **nie** ma `# PARSE_ESCAPES`, więc kropki i dwukropki pisz gołe (handoff, „Twarde fakty”).

- [ ] **Krok 4: Build i commit.**

```bash
./gradlew :insanetweaks:build
git commit -m "feat(insanetweaks): config for the sim wizard natural spawn" -- insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang
```

---

### Task 2: Wpisy na listach spawnów

**Pliki:**
- Utwórz: `…/init/ModEntitySpawns.java`

- [ ] **Krok 1: Klasa.**

```java
package com.spege.insanetweaks.init;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.EntitySimBattlemage;
import com.spege.insanetweaks.entities.EntitySimWizard;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Puts sim_wizard and sim_battlemage on the MONSTER spawn lists. Called once from postInit, when
 * every mod's biomes are registered.
 *
 * <p>The list entry is only a pre-filter: whether a picked entry actually spawns is decided by
 * {@code SimWizardNaturalSpawnHandler} (phase, infested ground, exclusion radius). Going through
 * the vanilla spawner rather than a spawner of our own is what lets the Sanctuary veto, SRP's
 * parasite cap, srpwizcore's SpawnEngine and InControl all apply without a line of code here.
 *
 * <p>Group size 1-1: an assimilated mage walks alone.
 */
public final class ModEntitySpawns {

    private ModEntitySpawns() {
    }

    public static void register() {
        EntitiesCategory.NaturalSpawn cfg = ModConfig.entities.assimilatedWizard.naturalSpawn;
        if (!ModConfig.entities.assimilatedWizard.spawning.enabled || !cfg.enabled) {
            InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Sim wizard natural spawn is off.");
            return;
        }

        Set<String> excluded = new HashSet<String>();
        for (String name : cfg.excludedBiomeTypes) {
            if (name != null && !name.trim().isEmpty()) {
                excluded.add(name.trim().toUpperCase(Locale.ROOT));
            }
        }

        // Matched against the types biomes actually carry rather than looked up by name:
        // BiomeDictionary.Type.getType(name) CREATES a type that does not exist yet.
        Set<String> seen = new HashSet<String>();
        List<Biome> allowed = new ArrayList<Biome>();
        for (Biome biome : ForgeRegistries.BIOMES) {
            boolean skip = false;
            for (BiomeDictionary.Type type : BiomeDictionary.getTypes(biome)) {
                String name = type.getName().toUpperCase(Locale.ROOT);
                seen.add(name);
                if (excluded.contains(name)) {
                    skip = true;
                }
            }
            if (!skip) {
                allowed.add(biome);
            }
        }
        for (String name : excluded) {
            if (!seen.contains(name)) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Sim wizard spawn: no biome carries the type '{}'"
                        + " listed in 'Excluded Biome Types' - typo, or a type from a mod that is not installed.", name);
            }
        }

        Biome[] biomes = allowed.toArray(new Biome[0]);
        if (cfg.weight > 0) {
            EntityRegistry.addSpawn(EntitySimWizard.class, cfg.weight, 1, 1, EnumCreatureType.MONSTER, biomes);
        }
        if (cfg.battlemageWeight > 0) {
            EntityRegistry.addSpawn(EntitySimBattlemage.class, cfg.battlemageWeight, 1, 1,
                    EnumCreatureType.MONSTER, biomes);
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Sim wizard natural spawn: weight {} / battlemage {} in {} of {}"
                + " biomes.", Integer.valueOf(cfg.weight), Integer.valueOf(cfg.battlemageWeight),
                Integer.valueOf(biomes.length), Integer.valueOf(ForgeRegistries.BIOMES.getValuesCollection().size()));
    }
}
```

- [ ] **Krok 2: Build.** Commit razem z Taskiem 6, bo bez wywołania w `postInit` klasa jest martwa.

---

### Task 3: Bramka `CheckSpawn`

**Pliki:**
- Utwórz: `…/events/SimWizardNaturalSpawnHandler.java`

- [ ] **Krok 1: Klasa.**

```java
package com.spege.insanetweaks.events;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.EntitySimBattlemage;
import com.spege.insanetweaks.entities.EntitySimWizard;
import com.spege.insanetweaks.util.SrpPhaseHelper;
import com.spege.insanetweaks.util.SrpPurificationHelper;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Decides whether a naturally picked sim_wizard / sim_battlemage may appear. Spec:
 * docs/superpowers/specs/2026-10-02-sim-wizard-native-spawn-design.md, section 3.
 *
 * <p>🚨 LOWEST, and only ever DENY. If SRP's own CheckSpawn handler sets ALLOW for parasites, a
 * higher priority here would be overwritten by it; at LOWEST we write last. ALLOW is never set
 * either: in Forge 1.12 it skips getCanSpawnHere and isNotColliding, so a wizard could appear in
 * a wall. The Sanctuary veto shares LOWEST and also only denies, so the order between us is moot.
 *
 * <p>Only the natural spawner reaches this with a sim wizard. Assimilation, spawn eggs and
 * /summon go straight to spawnEntity; spawner blocks are let through untouched below.
 *
 * <p>A wizard that passes is marked natural here rather than in SpecialSpawn: the candidate is a
 * throwaway object, so if a later check (light, collision) refuses it, the mark goes with it.
 */
public class SimWizardNaturalSpawnHandler {

    enum Verdict { DIMENSION, PHASE, UNDERFOOT, GROUND, EXCLUSION, PASSED }

    private static final Verdict[] VERDICTS = Verdict.values();

    // CheckSpawn may run on EntityThreading workers in this pack - hence atomics, not ints.
    private static final AtomicIntegerArray COUNTS = new AtomicIntegerArray(VERDICTS.length);
    private static final AtomicLong LAST_LOG = new AtomicLong(0L);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        // Fires for every mob in the game: the type test has to come first.
        if (!(event.getEntityLiving() instanceof EntitySimWizard)) {
            return;
        }
        if (event.getResult() == Event.Result.DENY || event.isSpawner()) {
            return;
        }
        World world = event.getWorld();
        if (world == null || world.isRemote) {
            return;
        }

        EntitySimWizard wizard = (EntitySimWizard) event.getEntityLiving();
        EntitiesCategory.NaturalSpawn cfg = ModConfig.entities.assimilatedWizard.naturalSpawn;
        Verdict verdict = evaluate(world, wizard, event.getX(), event.getY(), event.getZ(), cfg);
        if (cfg.debugLog) {
            record(verdict);
        }
        if (verdict == Verdict.PASSED) {
            wizard.markNaturalSpawn();
        } else {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Cheapest test first: config lookups, then one block read, then 25, then an entity query. */
    static Verdict evaluate(World world, EntitySimWizard wizard, double x, double y, double z,
            EntitiesCategory.NaturalSpawn cfg) {
        if (!dimensionAllowed(world.provider.getDimension(), cfg)) {
            return Verdict.DIMENSION;
        }
        int minPhase = wizard instanceof EntitySimBattlemage ? cfg.battlemageMinPhase : cfg.minPhase;
        if (SrpPhaseHelper.getEvolutionPhase(world) < minPhase) {
            return Verdict.PHASE;
        }
        BlockPos ground = new BlockPos(x, y, z).down();
        if (cfg.requireInfestedUnderfoot && !isInfested(world, ground)) {
            return Verdict.UNDERFOOT;
        }
        if (cfg.minInfestedGround > 0 && !patchInfested(world, ground, cfg.minInfestedGround)) {
            return Verdict.GROUND;
        }
        int r = cfg.exclusionRadius;
        // The candidate is not in the world yet, so it can never find itself here. The query is
        // by class, so a sim_battlemage (a subclass) blocks a sim_wizard and the other way round.
        if (r > 0 && !world.getEntitiesWithinAABB(EntitySimWizard.class,
                new AxisAlignedBB(x - r, y - r, z - r, x + r, y + r, z + r)).isEmpty()) {
            return Verdict.EXCLUSION;
        }
        return Verdict.PASSED;
    }

    private static boolean dimensionAllowed(int dim, EntitiesCategory.NaturalSpawn cfg) {
        boolean listed = false;
        for (int d : cfg.dimensions) {
            if (d == dim) {
                listed = true;
                break;
            }
        }
        return listed == cfg.dimensionWhitelist;
    }

    private static boolean isInfested(World world, BlockPos pos) {
        // The patch can cross into a neighbouring chunk; an unloaded block counts as clean and
        // must never be read (that would load or generate it).
        return world.isBlockLoaded(pos) && SrpPurificationHelper.isSrpInfested(world.getBlockState(pos));
    }

    /** True once {@code needed} of the 5x5 blocks centred on {@code ground} are infested. */
    private static boolean patchInfested(World world, BlockPos ground, int needed) {
        int found = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                p.setPos(ground.getX() + dx, ground.getY(), ground.getZ() + dz);
                if (isInfested(world, p) && ++found >= needed) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void record(Verdict verdict) {
        COUNTS.incrementAndGet(verdict.ordinal());
        long now = System.currentTimeMillis();
        long last = LAST_LOG.get();
        if (last == 0L) {
            LAST_LOG.compareAndSet(0L, now);
            return;
        }
        if (now - last < 60000L || !LAST_LOG.compareAndSet(last, now)) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Verdict v : VERDICTS) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(v.name().toLowerCase(java.util.Locale.ROOT)).append('=').append(COUNTS.getAndSet(v.ordinal(), 0));
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks][SimWizardSpawn] last minute: {}", sb);
    }
}
```

`event.isSpawner()` istnieje w Forge 14.23.5 (konstruktor z `MobSpawnerBaseLogic`). Jeśli build
go nie znajdzie, sprawdź `javap` na `LivingSpawnEvent$CheckSpawn` z jara Forge. **Nie** usuwaj
warunku w ciemno, bo bez niego spawner-blok z magiem (np. z datapacka CQR) podlegałby naszej
bramce.

- [ ] **Krok 2: Build.** Klasa nie kompiluje się bez `markNaturalSpawn()` z Tasku 4, więc najpierw
  zrób Task 4, a potem zbuduj oba razem.

---

### Task 4: Flaga „naturalny” i despawn

**Pliki:**
- Modyfikuj: `…/entities/EntitySimWizard.java`

- [ ] **Krok 1: Pole i setter.** Obok `cachedSrpPhase`:

```java
    /**
     * Set by SimWizardNaturalSpawnHandler for a wizard placed by the natural spawner; false for
     * assimilation, spawn eggs and commands. Natural ones must be able to despawn, or they pile up
     * on every infested field the player has ever walked past.
     */
    private boolean naturalSpawn;

    public void markNaturalSpawn() {
        this.naturalSpawn = true;
    }
```

- [ ] **Krok 2: Despawn.** Nadpisz:

```java
    /**
     * A natural spawn despawns like any monster. Everything else keeps whatever SRP decides for
     * its parasites (spec section 8, decision 5): an assimilated wizard is a story beat, not filler.
     */
    @Override
    protected boolean canDespawn() {
        return this.naturalSpawn || super.canDespawn();
    }
```

Jeśli build zgłosi „attempting to assign weaker access privileges”, SRP rozszerzyło
`canDespawn` do `public`. Zmień wtedy modyfikator na `public` (P3 z Tasku 0 to pokaże).

- [ ] **Krok 3: NBT.** W `writeEntityToNBT` po `WizardTier`:

```java
        compound.setBoolean("WizardNaturalSpawn", this.naturalSpawn);
```

W `readEntityFromNBT` po bloku z tierem:

```java
        // Absent on every wizard saved before 1.17.0 - all of them assimilated, so false is right.
        this.naturalSpawn = compound.getBoolean("WizardNaturalSpawn");
```

- [ ] **Krok 4: Build i commit Tasków 3+4.**

```bash
./gradlew :insanetweaks:build
git add -- insanetweaks/src/main/java/com/spege/insanetweaks/events/SimWizardNaturalSpawnHandler.java
git commit -m "feat(insanetweaks): gate natural sim wizard spawns on phase and infested ground" -- insanetweaks/src/main/java/com/spege/insanetweaks/events/SimWizardNaturalSpawnHandler.java insanetweaks/src/main/java/com/spege/insanetweaks/entities/EntitySimWizard.java
```

---

### Task 5: Światło (wariant z Tasku 0, P2)

**Plik:** `…/entities/EntitySimWizard.java`

**Wariant 5A: baza pasożyta nie nadpisuje żadnej z trzech metod** (dziedziczy reguły `EntityMob`).
W jasnym świetle `EntityMob` odmawia spawnu dwa razy: `isValidLightLevel` oraz
`getBlockPathWeight = 0.5 − jasność` sprawdzane w `EntityCreature.getCanSpawnHere`. Nadpisanie
samego `isValidLightLevel` nie wystarczy. Z kolei nadpisanie `getBlockPathWeight` zmieniłoby
wybór ścieżek AI. Dlatego zastępujemy cały `getCanSpawnHere`, i to wyłącznie przy włączonej opcji:

```java
    /**
     * Infested ground has to work by day too, and EntityMob refuses bright light twice
     * (isValidLightLevel, and getBlockPathWeight = 0.5 - brightness via EntityCreature). With
     * 'Ignore Light Level' on, keep what remains of the chain - peaceful check and the block below
     * - and drop both light tests. Only spawners ever call this; AI pathing is untouched.
     */
    @Override
    public boolean getCanSpawnHere() {
        if (!ModConfig.entities.assimilatedWizard.naturalSpawn.ignoreLightLevel) {
            return super.getCanSpawnHere();
        }
        if (this.world.getDifficulty() == net.minecraft.world.EnumDifficulty.PEACEFUL) {
            return false;
        }
        return this.world.getBlockState(new net.minecraft.util.math.BlockPos(this).down()).canEntitySpawn(this);
    }
```

**Wariant 5B: baza nadpisuje `getCanSpawnHere` własną logiką.** Stop, patrz bramka w Tasku 0.

**Wariant 5C: baza nie wymaga światła** (nadpisuje `isValidLightLevel` → `true` i
`getBlockPathWeight` na wartość nieujemną). Nie rób nic, a w Tasku 1 usuń `ignoreLightLevel`.

- [ ] **Krok 1:** Zastosuj wariant i zapisz w specu, który został wybrany.
- [ ] **Krok 2:** Build i commit `feat(insanetweaks): sim wizards spawn on infested ground by day`.

---

### Task 6: Podpięcie i wersja 1.17.0

**Pliki:**
- Modyfikuj: `…/InsaneTweaksMod.java`
- Modyfikuj: `insanetweaks/build.gradle`

- [ ] **Krok 1: Rejestracja handlera** w `init`, w bloku `if (…spawning.enabled)` zaraz po
  `SimWizardFactionHandler`:

```java
            if (com.spege.insanetweaks.config.ModConfig.entities.assimilatedWizard.naturalSpawn.enabled) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SimWizardNaturalSpawnHandler());
            }
```

- [ ] **Krok 2: Przywróć `postInit`.** Zastąp komentarz „postInit used to live here…” metodą:

```java
    // -------------------------------------------------------------------------
    // postInit
    // -------------------------------------------------------------------------

    @Mod.EventHandler
    public void postInit(net.minecraftforge.fml.common.event.FMLPostInitializationEvent event) {
        // The sim wizard spawn entries go on every allowed biome, so this has to wait until all
        // mods have registered theirs. (postInit was removed on 2026-08-06 when its last user,
        // the Reskillable Effect Twist hook, left for reskilltweaks.)
        com.spege.insanetweaks.init.ModEntitySpawns.register();
    }
```

- [ ] **Krok 3: Wersja.** `build.gradle`: `version = '1.17.0'`. `InsaneTweaksMod.VERSION = "1.17.0"`.

- [ ] **Krok 4: Build i commit.**

```bash
./gradlew :insanetweaks:build
git add -- insanetweaks/src/main/java/com/spege/insanetweaks/init/ModEntitySpawns.java
git commit -m "feat(insanetweaks) 1.17.0: sim wizards spawn naturally on infested ground" -- insanetweaks/src/main/java/com/spege/insanetweaks/init/ModEntitySpawns.java insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java insanetweaks/build.gradle
```

---

### Task 7: Przegląd przed uruchomieniem

- [ ] `git diff main --stat` pokazuje dokładnie pliki z Tasków 1–6. Zero mixinów i zero JSON-ów
  mixinów.
- [ ] W nowych plikach nie ma `net.minecraft.client`.
- [ ] `grep -n "getNamespace\|getPath()" …` po nowych plikach nic nie zwraca (mapowania 20171003).
- [ ] Jar zawiera `com/spege/insanetweaks/events/SimWizardNaturalSpawnHandler.class` i
  `init/ModEntitySpawns.class`: `unzip -l insanetweaks/build/libs/*.jar | grep -E 'NaturalSpawn|EntitySpawns'`.

---

### Task 8: Weryfikacja w DEv 1.2 i kalibracja

`Debug Log = true` w `insanetweaks.cfg`. Scenariusze z sekcji 9 specu. Odhaczaj, zapisując w specu
linię z logu jako dowód.

- [ ] Start: log `Sim wizard natural spawn: weight 4 / battlemage 1 in N of M biomes`. N > 0 i
  brak ostrzeżeń o nieznanym typie biomu.
- [ ] Faza 0, dowolny teren: `[SimWizardSpawn]` pokazuje tylko `phase=` > 0 i `passed=0`.
- [ ] `/srpevolution` do fazy 2, czysty teren: dominuje `underfoot=` (albo `ground=` przy
  `requireInfestedUnderfoot=false`).
- [ ] Ręcznie skażona plama 7×7 (`srparasites:infestedstain`), **dzień**: pojawia się mag.
  Drugi w promieniu 48 dostaje `exclusion=`.
- [ ] Sanctuary nad plamą: zero nowych magów i wpisy `spawn-vetoed` przy `Sanctuary Debug`.
- [ ] Wymiar spoza listy (Nether nie ma wpisu, więc sprawdź np. 111): `dimension=` > 0.
- [ ] Faza 4: pojawia się też battlemage. Przy fazie 2–3 battlemage nie pojawia się nigdy.
- [ ] Śmierć naturalnego maga: pył abominacji z poprawną nazwą (funkcja
  `insanetweaks:abomination_meta` z 1.16.2).
- [ ] Oddalenie się na ponad 128 bloków: mag znika. Asymilowany mag w tych samych warunkach
  zachowuje się jak przed 1.17.0.
- [ ] Save, wyjście, powrót: naturalny mag nadal może zniknąć (NBT `WizardNaturalSpawn`).
- [ ] Serwer dedykowany: start bez błędów.
- [ ] **Kalibracja (sekcja 5 specu).** 20 minut w środku skażenia przy fazie 2. Cel: 1 mag na
  5–10 minut. Wyliczenie: `passed` na minutę × odsetek, który przejdzie `getCanSpawnHere` (z liczby
  realnie widzianych magów). Za mało → podnieś `weight`. Za dużo → obniż. Nową wartość domyślną
  wpisz w Task 1 i do specu, osobnym commitem `balance(insanetweaks): …`.

---

### Task 9: Dokumentacja

- [ ] Spec: status „**implemented** (1.17.0)”, wyniki Tasku 0 i kalibracji na miejscu.
- [ ] Handoff `2026-08-11-abomination-magic-system-handoff.md`, punkt C: „🚨 `EntitySimWizard` nie
  ma własnego spawnu” → przekreślone, z odnośnikiem do specu. Zostaje otwarte: zaklęcia w puli
  (niżej).
- [ ] README `insanetweaks` bez zmian, bo opis moda nie wymienia źródeł spawnu.

---

## Następne wydanie (1.17.1), dopiero po kalibracji: zaklęcia abominacji u magów

Decyzja 4 specu: osobno, żeby nie mierzyć dwóch zmian trudności naraz. Zakres:

1. `dispatcher_grasp.json`, `yelloweye_gland.json`: `"npcs": true`.
2. `EntitiesCategory.Spells.spellPool`: dopisać `insanetweaks:dispatcher_grasp` i
   `insanetweaks:yelloweye_gland`.
3. 🚨 **Filtry tierów to jawne listy id** (`EntitySimWizard.applyTierSpellFilter`). Zaklęcie,
   którego nie ma na liście filtra swojego tieru, zostaje odcięte po cichu, jeśli filtr jest
   niepusty. Rekomendacja: dopisać oba zaklęcia do `adeptSpellFilter` i `masterSpellFilter`,
   a nie do `noviceSpellFilter`, bo oba są tieru master.
4. Uwaga: zmiana domyślnych `String[]` w `@Config` nie nadpisuje istniejących plików `.cfg`.
   Paczka z gotowym `insanetweaks.cfg` dostanie zaklęcia dopiero po ręcznej edycji. Napisz o tym
   w changelogu.

## Pokrycie specu

| Sekcja specu | Task |
|---|---|
| 3 (mechanizm A, bramka, rejestracja) | 2, 3, 6 |
| 4 (konfiguracja) | 1 |
| 5 (kalibracja) | 8 |
| 6 (tiery bez zmian, zaklęcia) | — (bez zmian) / „Następne wydanie” |
| 7 (bytecode) | 0 |
| 8.5 (despawn) | 4 |
| 9 (test) | 8 |
| 10 (pliki) | 1–6 |
