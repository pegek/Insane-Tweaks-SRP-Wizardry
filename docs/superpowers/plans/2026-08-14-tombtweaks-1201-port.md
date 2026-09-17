# TombTweaks 1.20.1 — plan implementacji rdzenia

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zbudować `tombtweaks` na Minecraft 1.20.1 / Forge 47 / Corail Tombstone 9.1.4 z trzema featurami: exact-slot grave restore, grave item decay i cooldowny ksiąg.

**Architecture:** Nowe, osobne repo (wymuszone konfliktem wrapperów Gradle z `modDev`). Trzy warstwy w jednym source secie: `core` bez ani jednego typu Minecrafta i Tombstone'a (cała logika decyzyjna, testowana JUnitem na gołej JVM), `platform` (cienkie adaptery), `feature` (handlery i trzy mixiny, tak głupie, jak się da). Restore idzie na publicznym `RestoreInventoryEvent` bez mixina; decay i cooldowny po mixinach w Tombstone'a.

**Tech Stack:** Gradle 8.x, ForgeGradle 6, MixinGradle 0.7, Java 17 (toolchain), mappings `official`, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-14-tombtweaks-1201-port-design.md` w repo `modDev`. Czytaj go przed startem — plan realizuje jego §4, §5, §6, §7.

---

## Zasady obowiązujące w całym planie

1. **`core` musi być składniowo zgodny z Javą 8.** Bez rekordów, bez `var`, bez switch-expression, bez `List.of`. `core` jest jedyną rzeczą, która przy porcie na 1.16.5 (Java 8) przeniesie się kopiuj-wklej — składnia Javy 17 odbiera mu ten jedyny powód istnienia. Warstwy `platform` i `feature` mogą używać pełnej Javy 17.
2. **`core` nie importuje niczego z `net.minecraft`, `net.minecraftforge` ani `ovh.corail`.** Task 2 stawia test, który to wymusza mechanicznie.
3. **Refmapy są włączone.** To odwrotność zasady z `modDev/CLAUDE.md` — tam `-proc:none` wyłącza mixinowy annotation processor. Tutaj **nie wolno** dodawać `-proc:none`, bo refmapa jest potrzebna dla mixina celującego w metodę Minecrafta (`BlockBehaviour.use`).
4. **Każdy mixin celujący w członka Tombstone'a ma `remap = false`.** Klasy Tombstone'a nie są obfuskowane, jego metody nie mają nazw SRG.
5. **Commit po każdym zadaniu.** Zadania są tak dobrane, żeby każde zostawiało repo w stanie, który się kompiluje.

---

## Struktura plików

```
E:\Isuth\tombtweaks-1201\
├─ settings.gradle                     pluginManagement + nazwa projektu
├─ build.gradle                        FG6 + MixinGradle + toolchain 17 + JUnit 5
├─ gradle.properties
├─ gradle/wrapper/                     Gradle 8.x
├─ libs/tombstone-1.20.1-9.1.4.jar     przeniesiony z modDev/TestPort
├─ .gitignore
├─ README.md
├─ src/main/java/com/spege/tombtweaks/
│  ├─ TombTweaks.java                  @Mod: rejestracja configu i handlerów
│  ├─ core/                            ZERO typów MC/Forge/Tombstone, składnia Java 8
│  │  ├─ ItemKey.java                  tożsamość stacka: id + hash NBT
│  │  ├─ SlotEntry.java                jedno zapamiętane miejsce
│  │  ├─ SlotSnapshot.java             plan miejsc z jednej śmierci + stałe kodowania slotów
│  │  ├─ SlotPlan.java                 dopasowanie trójstopniowe, zużywa miejsca
│  │  ├─ SnapshotStore.java            snapshoty oczekujące gracza: wiązanie i przycinanie
│  │  ├─ StackView.java                interfejs: co core wie o stacku
│  │  ├─ ProtectionRules.java          cztery listy ochrony
│  │  ├─ DecaySchedule.java            kiedy grób traci następny przedmiot
│  │  ├─ DecayVictim.java              który slot idzie pod nóż
│  │  ├─ CooldownRules.java            parser configu "id;minuty"
│  │  └─ Cooldown.java                 arytmetyka pozostałego czasu
│  ├─ platform/
│  │  ├─ Config.java                   ForgeConfigSpec, trzy kategorie
│  │  ├─ StackViews.java               ItemStack -> ItemKey / StackView
│  │  ├─ PlayerData.java               dostęp do PlayerPersisted
│  │  └─ SnapshotCodec.java            SnapshotStore <-> CompoundTag
│  ├─ feature/
│  │  ├─ restore/SnapshotCaptureHandler.java   LivingDeathEvent
│  │  ├─ restore/SlotRestoreHandler.java       RestoreInventoryEvent
│  │  ├─ decay/GraveDecayService.java          logika jednego ticku grobu
│  │  ├─ decay/DecayHistory.java               zapis historii po UUID
│  │  └─ cooldown/BookCooldownService.java     odczyt/zapis cooldownu
│  └─ mixin/
│     ├─ MixinBlockEntityPlayerGrave.java      HEAD serverTick -> decay
│     ├─ MixinItemBook.java                    HEAD canEnchant -> blokada
│     └─ MixinBlockDecorativeGrave.java        REDIRECT setEnchant -> start cooldownu
├─ src/main/resources/
│  ├─ META-INF/mods.toml
│  ├─ mixins.tombtweaks.json
│  ├─ pack.mcmeta
│  └─ assets/tombtweaks/lang/en_us.json
└─ src/test/java/com/spege/tombtweaks/core/
   ├─ CoreHasNoForeignTypesTest.java
   ├─ SlotPlanTest.java
   ├─ SnapshotStoreTest.java
   ├─ ProtectionRulesTest.java
   ├─ DecayScheduleTest.java
   ├─ DecayVictimTest.java
   └─ CooldownTest.java
```

---

## Task 1: Bootstrap repozytorium i build

**Files:**
- Create: `E:\Isuth\tombtweaks-1201\settings.gradle`
- Create: `E:\Isuth\tombtweaks-1201\build.gradle`
- Create: `E:\Isuth\tombtweaks-1201\gradle.properties`
- Create: `E:\Isuth\tombtweaks-1201\.gitignore`
- Create: `E:\Isuth\tombtweaks-1201\src\main\java\com\spege\tombtweaks\TombTweaks.java`
- Create: `E:\Isuth\tombtweaks-1201\src\main\resources\META-INF\mods.toml`
- Create: `E:\Isuth\tombtweaks-1201\src\main\resources\pack.mcmeta`
- Move: `E:\Isuth\modDev\TestPort\tombstone-1.20.1-9.1.4.jar` → `E:\Isuth\tombtweaks-1201\libs\`

> **Uwaga o JDK:** na tej maszynie są JDK 8, 18, 21 i 24 — **nie ma 17**, którego wymaga Forge 47. Dlatego blok `toolchain` w `build.gradle` nie jest ozdobą: Gradle sam pobierze JDK 17 przez foojay resolver. Nie próbuj budować pod 21 ani 24.

- [ ] **Step 1: Załóż repo i przenieś jar Tombstone'a**

```bash
mkdir -p /e/Isuth/tombtweaks-1201/libs
cd /e/Isuth/tombtweaks-1201 && git init
mv /e/Isuth/modDev/TestPort/tombstone-1.20.1-9.1.4.jar /e/Isuth/tombtweaks-1201/libs/
```

- [ ] **Step 2: Wygeneruj wrapper Gradle 8.7**

```bash
cd /e/Isuth/tombtweaks-1201 && gradle wrapper --gradle-version 8.7
```

Jeśli w PATH nie ma `gradle`, skopiuj katalog `gradle/wrapper` i `gradlew*` z dowolnego projektu na Gradle 8 i podmień `distributionUrl` na `gradle-8.7-bin.zip`.

Expected: powstaje `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties` z `gradle-8.7-bin.zip`.

- [ ] **Step 3: Napisz `settings.gradle`**

```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { name = 'MinecraftForge'; url = 'https://maven.minecraftforge.net/' }
        maven { name = 'Sponge';         url = 'https://repo.spongepowered.org/repository/maven-public/' }
    }
}

plugins {
    // Pozwala Gradle pobrać JDK 17 samodzielnie. Na tej maszynie 17 nie ma zainstalowanej.
    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
}

rootProject.name = 'tombtweaks'
```

- [ ] **Step 4: Napisz `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.daemon=false
```

- [ ] **Step 5: Napisz `build.gradle`**

```groovy
plugins {
    id 'eclipse'
    id 'net.minecraftforge.gradle' version '[6.0.24,6.2)'
    id 'org.spongepowered.mixin'   version '0.7.+'
}

version = '0.1.0'
group   = 'com.spege.tombtweaks'
base { archivesName = 'tombtweaks-1.20.1' }

// NIE dodawaj -proc:none. Mixinowy annotation processor musi działać, bo
// MixinBlockDecorativeGrave celuje w metode Minecrafta i potrzebuje refmapy.
java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

minecraft {
    mappings channel: 'official', version: '1.20.1'
    copyIdeResources = true

    runs {
        client {
            workingDirectory project.file('run')
            property 'forge.logging.console.level', 'debug'
            mods { tombtweaks { source sourceSets.main } }
        }
        server {
            workingDirectory project.file('run')
            property 'forge.logging.console.level', 'debug'
            mods { tombtweaks { source sourceSets.main } }
        }
    }
}

mixin {
    add sourceSets.main, 'tombtweaks.refmap.json'
    config 'mixins.tombtweaks.json'
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft 'net.minecraftforge:forge:1.20.1-47.2.20'

    // Tombstone: deobf jest konieczny, bo referencje do MC w jego bajtkodzie sa SRG-owe.
    implementation fg.deobf(files('libs/tombstone-1.20.1-9.1.4.jar'))

    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly   'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
    testLogging { events 'passed', 'skipped', 'failed' }
}

tasks.withType(JavaCompile).configureEach {
    options.encoding = 'UTF-8'
}

jar {
    manifest {
        attributes([
            'Specification-Title'   : 'TombTweaks',
            'Specification-Vendor'  : 'spege',
            'Specification-Version' : "${version}",
            'Implementation-Title'  : project.name,
            'Implementation-Version': "${version}",
            'Implementation-Vendor' : 'spege'
        ])
    }
}
```

- [ ] **Step 6: Napisz `.gitignore`**

```gitignore
build/
bin/
run/
.gradle/
.idea/
*.iml
.classpath
.project
.settings/
```

- [ ] **Step 7: Napisz `src/main/resources/META-INF/mods.toml`**

```toml
modLoader="javafml"
loaderVersion="[47,)"
license="MIT"
issueTrackerURL="https://github.com/spege/tombtweaks"

[[mods]]
modId="tombtweaks"
version="${file.jarVersion}"
displayName="TombTweaks"
authors="spege"
description='''
Tweaks for Corail Tombstone: items return to the slots they came from, unvisited graves slowly decay, and the magic books get a cooldown.
'''

[[dependencies.tombtweaks]]
    modId="forge"
    mandatory=true
    versionRange="[47,)"
    ordering="NONE"
    side="BOTH"

[[dependencies.tombtweaks]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.20.1,1.20.2)"
    ordering="NONE"
    side="BOTH"

[[dependencies.tombtweaks]]
    modId="tombstone"
    mandatory=true
    versionRange="[9.0,)"
    ordering="AFTER"
    side="BOTH"

[[mixins]]
config="mixins.tombtweaks.json"
```

- [ ] **Step 8: Napisz `src/main/resources/pack.mcmeta`**

```json
{
  "pack": {
    "description": "TombTweaks resources",
    "pack_format": 15
  }
}
```

- [ ] **Step 9: Napisz klasę `@Mod`**

`src/main/java/com/spege/tombtweaks/TombTweaks.java`:

```java
package com.spege.tombtweaks;

import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TombTweaks.MODID)
public final class TombTweaks {

    public static final String MODID = "tombtweaks";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public TombTweaks() {
        LOGGER.info("[TombTweaks] {} loading", VERSION);
    }
}
```

- [ ] **Step 10: Zbuduj**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, powstaje `build/libs/tombtweaks-1.20.1-0.1.0.jar`. Pierwsze uruchomienie ściąga JDK 17 i dekompiluje Minecrafta — potrwa kilkanaście minut.

- [ ] **Step 11: Odpal klienta i potwierdź, że Tombstone jest obecny**

Run: `./gradlew runClient`
Expected: klient wstaje; w logu jest `[TombTweaks] 0.1.0 loading` oraz wpis o załadowaniu moda `tombstone`. Zamknij klienta.

- [ ] **Step 12: Commit**

```bash
cd /e/Isuth/tombtweaks-1201
git add -A
git commit -m "chore: bootstrap projektu tombtweaks na 1.20.1 (FG6, Java 17, JUnit 5)"
```

---

## Task 2: Test pilnujący czystości `core`

`core` ma sens tylko wtedy, gdy naprawdę nie zawiera typów obcych — inaczej port na 1.16.5 przestanie być kopiuj-wklej i nikt się o tym nie dowie, dopóki nie spróbuje. Test skanuje **skompilowane** klasy, nie źródła, więc łapie też użycie w pełni kwalifikowaną nazwą bez importu.

**Files:**
- Create: `src/test/java/com/spege/tombtweaks/core/CoreHasNoForeignTypesTest.java`
- Create: `src/main/java/com/spege/tombtweaks/core/DecaySchedule.java` (minimalna klasa, żeby katalog `core` w ogóle powstał)
- Create: `src/main/resources/mixins.tombtweaks.json` (z **pustą** listą `mixins`)

> **Poprawka do planu, wykryta po Tasku 1.** Jar deklaruje w manifeście `MixinConfigs: mixins.tombtweaks.json` (robi to plugin MixinGradle), a `mods.toml` ma `[[mixins]] config=...`. Dopóki ten plik nie istnieje, build przechodzi, ale **gra wywala się przy starcie**. To blokowałoby weryfikację w grze w Taskach 9 i 11, które żadnych mixinów nie potrzebują. Dlatego plik powstaje tutaj, z pustą listą `"mixins": []` — co jest w pełni poprawną konfiguracją — a Taski 12 i 13 tylko dopisują do niej wpisy.

- [ ] **Step 1: Napisz test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Pilnuje jedynej wlasnosci, ktora czyni pakiet core wartym istnienia: braku typow obcych.
 * Skanuje skompilowane klasy, bo nazwy typow siedza w constant poolu jako zwykly tekst -
 * dzieki temu lapie tez uzycie w pelni kwalifikowana nazwa, bez importu.
 */
class CoreHasNoForeignTypesTest {

    private static final String[] FORBIDDEN = {
        "net/minecraft", "net/minecraftforge", "ovh/corail"
    };

    @Test
    void coreClassesNameNoForeignTypes() throws IOException {
        Path root = Paths.get("build", "classes", "java", "main",
                              "com", "spege", "tombtweaks", "core");
        assertTrue(Files.isDirectory(root),
                   "brak skompilowanych klas core pod " + root.toAbsolutePath()
                   + " - uruchom test przez ./gradlew test, nie z IDE bez kompilacji");

        List<Path> classFiles;
        try (Stream<Path> files = Files.walk(root)) {
            classFiles = files.filter(p -> p.toString().endsWith(".class"))
                              .collect(java.util.stream.Collectors.toList());
        }

        List<String> offenders = new ArrayList<String>();
        for (Path file : classFiles) {
            String bytes = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            for (String forbidden : FORBIDDEN) {
                if (bytes.contains(forbidden)) {
                    offenders.add(file.getFileName() + " -> " + forbidden);
                }
            }
        }

        assertTrue(offenders.isEmpty(), "core nazywa typy obce: " + offenders);
    }

    @Test
    void theScanWouldActuallyCatchSomething() {
        // Kontrola samego testu: gdyby warunek byl zawsze prawdziwy, test nic nie pilnuje.
        String pretendClassFile = "some bytes net/minecraft/world/item/ItemStack more bytes";
        boolean caught = false;
        for (String forbidden : FORBIDDEN) {
            if (pretendClassFile.contains(forbidden)) caught = true;
        }
        assertTrue(caught);
        assertFalse("com/spege/tombtweaks/core/ItemKey".contains("net/minecraft"));
    }
}
```

- [ ] **Step 2: Napisz `DecaySchedule`, żeby było co skanować**

`src/main/java/com/spege/tombtweaks/core/DecaySchedule.java`:

```java
package com.spege.tombtweaks.core;

/**
 * Kiedy grob traci nastepny przedmiot.
 *
 * <p>Czysta funkcja licznika {@code countTicks} samego grobu, ktory Tombstone zapisuje do NBT
 * i wczytuje przy zaladowaniu chunka. Dzieki temu harmonogram nie potrzebuje wlasnego stanu
 * i przezywa restart serwera oraz wyladowanie chunka.
 *
 * <p>Uwaga na semantyke: {@code countTicks} rosnie tylko wtedy, gdy grob jest zaladowany,
 * wiec {@code startTicks} liczy czas ZALADOWANIA, nie czas zegarowy.
 */
public final class DecaySchedule {

    private DecaySchedule() {
    }

    public static boolean shouldDecay(long countTicks, long startTicks, long intervalTicks) {
        if (intervalTicks <= 0L) {
            return false;
        }
        if (countTicks < startTicks) {
            return false;
        }
        return (countTicks - startTicks) % intervalTicks == 0L;
    }
}
```

- [ ] **Step 3: Uruchom testy**

Run: `./gradlew test`
Expected: PASS, 2 testy.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "test: straznik czystosci pakietu core + DecaySchedule"
```

---

## Task 3: `core` — tożsamość stacka i plan miejsc

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/ItemKey.java`
- Create: `src/main/java/com/spege/tombtweaks/core/SlotEntry.java`
- Create: `src/main/java/com/spege/tombtweaks/core/SlotSnapshot.java`
- Test: `src/test/java/com/spege/tombtweaks/core/SlotSnapshotTest.java`

- [ ] **Step 1: Napisz failujący test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotSnapshotTest {

    @Test
    void itemKeyRoznicujePoNbt() {
        assertEquals(new ItemKey("minecraft:diamond_sword", 7),
                     new ItemKey("minecraft:diamond_sword", 7));
        assertNotEquals(new ItemKey("minecraft:diamond_sword", 7),
                        new ItemKey("minecraft:diamond_sword", 8));
        assertNotEquals(new ItemKey("minecraft:diamond_sword", 7),
                        new ItemKey("minecraft:iron_sword", 7));
    }

    @Test
    void pustyStackNieTrafiaDoSnapshotu() {
        SlotSnapshot snapshot = new SlotSnapshot(1000L);
        assertTrue(snapshot.isEmpty());
        assertEquals(0, snapshot.entries().size());
    }

    @Test
    void kodowanieSlotowNieKoliduje() {
        // Main 0-35 musi konczyc sie przed pancerzem, pancerz przed offhandem,
        // a offhand przed zarezerwowana przestrzenia Curios.
        assertTrue(35 < SlotSnapshot.ARMOR_BASE);
        assertTrue(SlotSnapshot.ARMOR_BASE + 3 < SlotSnapshot.OFFHAND_SLOT);
        assertTrue(SlotSnapshot.OFFHAND_SLOT < SlotSnapshot.CURIOS_BASE);
    }

    @Test
    void snapshotPamietaMiejsceIIlosc() {
        SlotSnapshot snapshot = new SlotSnapshot(1234L);
        snapshot.add(4, new ItemKey("minecraft:bread", 0), 12);
        snapshot.add(SlotSnapshot.OFFHAND_SLOT, new ItemKey("minecraft:shield", 99), 1);

        assertEquals(1234L, snapshot.capturedAt());
        assertEquals(2, snapshot.entries().size());
        assertEquals(4, snapshot.entries().get(0).slot());
        assertEquals(12, snapshot.entries().get(0).count());
        assertEquals(SlotSnapshot.OFFHAND_SLOT, snapshot.entries().get(1).slot());
    }
}
```

- [ ] **Step 2: Uruchom test, potwierdź że nie kompiluje**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class ItemKey`.

- [ ] **Step 3: Napisz `ItemKey`**

```java
package com.spege.tombtweaks.core;

/**
 * Tozsamosc stacka: tyle, zeby go rozpoznac przy powrocie z grobu, i ani grama wiecej.
 *
 * <p>Na 1.12.2 kluczem bylo (nazwa, metadane, hash NBT). Na 1.20.1 metadanych nie ma,
 * a wytrzymalosc siedzi w NBT - stad para (nazwa, hash NBT) i stad tez to, ze dopasowanie
 * musi miec stopien awaryjny: serializacja stacka do grobu i z powrotem potrafi
 * znormalizowac tag, a wtedy hash sie zmienia. Patrz {@link SlotPlan}.
 */
public final class ItemKey {

    private final String id;
    private final int nbtHash;

    public ItemKey(String id, int nbtHash) {
        if (id == null) {
            throw new IllegalArgumentException("id");
        }
        this.id = id;
        this.nbtHash = nbtHash;
    }

    public String id() {
        return id;
    }

    public int nbtHash() {
        return nbtHash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemKey)) {
            return false;
        }
        ItemKey that = (ItemKey) other;
        return this.nbtHash == that.nbtHash && this.id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode() * 31 + nbtHash;
    }

    @Override
    public String toString() {
        return id + "#" + nbtHash;
    }
}
```

- [ ] **Step 4: Napisz `SlotEntry`**

```java
package com.spege.tombtweaks.core;

/** Jedno zapamietane miejsce: co gdzie siedzialo w chwili smierci. */
public final class SlotEntry {

    private final int slot;
    private final ItemKey key;
    private final int count;

    public SlotEntry(int slot, ItemKey key, int count) {
        this.slot = slot;
        this.key = key;
        this.count = count;
    }

    public int slot() {
        return slot;
    }

    public ItemKey key() {
        return key;
    }

    /**
     * Ilosc w chwili smierci. Zapisywana dla diagnostyki, NIGDY nie jest kryterium dopasowania:
     * percentLossOnDeath zmniejsza stacki wewnatrz grobu, zanim ktokolwiek je zobaczy.
     */
    public int count() {
        return count;
    }
}
```

- [ ] **Step 5: Napisz `SlotSnapshot`**

```java
package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plan miejsc z jednej smierci - nie kopia przedmiotow.
 *
 * <p>Pelny zserializowany ekwipunek w danych gracza to dziesiatki kilobajtow przepisywane
 * przy kazdym autosave i, co gorsza, druga instancja prawdy o tym, co gracz posiadal.
 * Grob jest jedyna instancja prawdy; to jest wylacznie rozsadzenie gosci.
 */
public final class SlotSnapshot {

    /** Main inventory zachowuje wlasne indeksy 0-35. */
    public static final int ARMOR_BASE = 100;
    public static final int OFFHAND_SLOT = 150;
    /** Zarezerwowane pod Curios. Nieuzywane w v1. */
    public static final int CURIOS_BASE = 200;

    private final long capturedAt;
    private final List<SlotEntry> entries;

    public SlotSnapshot(long capturedAt) {
        this(capturedAt, new ArrayList<SlotEntry>());
    }

    public SlotSnapshot(long capturedAt, List<SlotEntry> entries) {
        this.capturedAt = capturedAt;
        this.entries = entries;
    }

    public void add(int slot, ItemKey key, int count) {
        entries.add(new SlotEntry(slot, key, count));
    }

    /** Zegar scienny w milisekundach - ta sama skala co deathDate grobu. */
    public long capturedAt() {
        return capturedAt;
    }

    public List<SlotEntry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
```

- [ ] **Step 6: Uruchom testy**

Run: `./gradlew test`
Expected: PASS, 6 testów.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(core): ItemKey, SlotEntry i SlotSnapshot - plan miejsc zamiast kopii ekwipunku"
```

---

## Task 4: `core` — dopasowanie trójstopniowe

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/SlotPlan.java`
- Test: `src/test/java/com/spege/tombtweaks/core/SlotPlanTest.java`

- [ ] **Step 1: Napisz failujący test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SlotPlanTest {

    private static SlotSnapshot snapshotWith(SlotEntry... items) {
        SlotSnapshot snapshot = new SlotSnapshot(0L);
        for (SlotEntry item : items) {
            snapshot.add(item.slot(), item.key(), item.count());
        }
        return snapshot;
    }

    @Test
    void trafienieDokladne() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(3, new ItemKey("minecraft:diamond_sword", 42), 1)));

        assertEquals(3, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 42)));
    }

    @Test
    void spadekNaDopasowaniePoNazwieGdyNbtSieRozjechalo() {
        // Round-trip przez NBT grobu potrafi znormalizowac tag i zmienic hash.
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(7, new ItemKey("minecraft:diamond_sword", 42), 1)));

        assertEquals(7, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 999)));
    }

    @Test
    void dokladneMaPierwszenstwoPrzedNazwa() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(1, new ItemKey("minecraft:diamond_sword", 11), 1),
                new SlotEntry(2, new ItemKey("minecraft:diamond_sword", 22), 1)));

        assertEquals(2, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 22)));
        assertEquals(1, plan.claimSeat(new ItemKey("minecraft:diamond_sword", 11)));
    }

    @Test
    void dwaIdenticzneStackiDostajaDwaRozneMiejsca() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(4, new ItemKey("minecraft:bread", 0), 8),
                new SlotEntry(9, new ItemKey("minecraft:bread", 0), 8)));

        int first = plan.claimSeat(new ItemKey("minecraft:bread", 0));
        int second = plan.claimSeat(new ItemKey("minecraft:bread", 0));

        assertNotEquals(first, second);
        assertEquals(13, first + second);
    }

    @Test
    void brakDopasowaniaToRezygnacja() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(4, new ItemKey("minecraft:bread", 0), 8)));

        assertEquals(SlotPlan.NO_SEAT, plan.claimSeat(new ItemKey("minecraft:stone", 0)));
    }

    @Test
    void miejsceZuzywaSieRaz() {
        SlotPlan plan = new SlotPlan(snapshotWith(
                new SlotEntry(4, new ItemKey("minecraft:bread", 0), 8)));

        assertEquals(4, plan.claimSeat(new ItemKey("minecraft:bread", 0)));
        assertEquals(SlotPlan.NO_SEAT, plan.claimSeat(new ItemKey("minecraft:bread", 0)));
    }
}
```

- [ ] **Step 2: Uruchom test, potwierdź że nie kompiluje**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class SlotPlan`.

- [ ] **Step 3: Napisz `SlotPlan`**

```java
package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapshot wydawany na jeden grob. Kazde miejsce mozna zajac tylko raz.
 *
 * <p>Dopasowanie jest trojstopniowe, bo klucz (nazwa, hash NBT) jest krychy: stack idzie
 * do grobu przez serializacje do NBT i z powrotem, a round-trip potrafi tag znormalizowac.
 *
 * <ol>
 *   <li>dokladne - nazwa i hash NBT,</li>
 *   <li>awaryjne - sama nazwa, pierwszy jeszcze niezajety wpis,</li>
 *   <li>rezygnacja - {@link #NO_SEAT}, przedmiot zostaje w grobie i idzie sciezka standardowa.</li>
 * </ol>
 *
 * <p>Stopien trzeci jest wazniejszy, niz wyglada: to on gwarantuje, ze najgorszym przypadkiem
 * calego featura jest dzisiejsze zachowanie Tombstone'a, nigdy zgubiony przedmiot.
 */
public final class SlotPlan {

    public static final int NO_SEAT = -1;

    private final List<SlotEntry> remaining;

    public SlotPlan(SlotSnapshot snapshot) {
        this.remaining = new ArrayList<SlotEntry>(snapshot.entries());
    }

    public int claimSeat(ItemKey key) {
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).key().equals(key)) {
                return remaining.remove(i).slot();
            }
        }
        for (int i = 0; i < remaining.size(); i++) {
            if (remaining.get(i).key().id().equals(key.id())) {
                return remaining.remove(i).slot();
            }
        }
        return NO_SEAT;
    }
}
```

- [ ] **Step 4: Uruchom testy**

Run: `./gradlew test`
Expected: PASS, 12 testów.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(core): SlotPlan - dopasowanie trojstopniowe z rezygnacja jako stopniem trzecim"
```

---

## Task 5: `core` — wiązanie snapshotu z grobem

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/SnapshotStore.java`
- Test: `src/test/java/com/spege/tombtweaks/core/SnapshotStoreTest.java`

- [ ] **Step 1: Napisz failujący test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SnapshotStoreTest {

    private static final long TOLERANCE = 5_000L;

    private static SlotSnapshot at(long capturedAt) {
        SlotSnapshot snapshot = new SlotSnapshot(capturedAt);
        snapshot.add(0, new ItemKey("minecraft:stone", 0), 1);
        return snapshot;
    }

    @Test
    void wiazePoNajblizszymCzasie() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));
        store.add(at(50_000L));

        SlotSnapshot claimed = store.claimNearest(50_200L, TOLERANCE);

        assertNotNull(claimed);
        assertEquals(50_000L, claimed.capturedAt());
    }

    @Test
    void zuzywaSnapshotWiecDrugiGrobDostajeSwoj() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));
        store.add(at(50_000L));

        assertEquals(50_000L, store.claimNearest(50_100L, TOLERANCE).capturedAt());
        assertEquals(1_000L, store.claimNearest(1_100L, TOLERANCE).capturedAt());
        assertNull(store.claimNearest(1_100L, TOLERANCE));
    }

    @Test
    void pozaTolerancjaToBrakDopasowania() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));

        assertNull(store.claimNearest(1_000L + TOLERANCE + 1L, TOLERANCE));
    }

    @Test
    void tolerancjaDzialaWObieStrony() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(10_000L));

        assertNotNull(store.claimNearest(10_000L - TOLERANCE, TOLERANCE));
    }

    @Test
    void przycinaPoLiczbie() {
        SnapshotStore store = new SnapshotStore();
        for (int i = 1; i <= 6; i++) {
            store.add(at(i * 1_000L));
        }

        store.prune(100_000L, 3, Long.MAX_VALUE);

        assertEquals(3, store.all().size());
        // zostaja najmlodsze
        assertEquals(4_000L, store.all().get(0).capturedAt());
        assertEquals(6_000L, store.all().get(2).capturedAt());
    }

    @Test
    void przycinaPoWieku() {
        SnapshotStore store = new SnapshotStore();
        store.add(at(1_000L));
        store.add(at(90_000L));

        store.prune(100_000L, 10, 20_000L);

        assertEquals(1, store.all().size());
        assertEquals(90_000L, store.all().get(0).capturedAt());
    }

    @Test
    void pustyMagazynNieWybucha() {
        SnapshotStore store = new SnapshotStore();
        store.prune(100_000L, 3, 1_000L);
        assertNull(store.claimNearest(0L, TOLERANCE));
        assertEquals(0, store.all().size());
    }
}
```

- [ ] **Step 2: Uruchom test, potwierdź że nie kompiluje**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class SnapshotStore`.

- [ ] **Step 3: Napisz `SnapshotStore`**

```java
package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Snapshoty oczekujace jednego gracza.
 *
 * <p>Snapshot powstaje przy smierci, czyli zanim grob istnieje - musi wiec zostac skojarzony
 * z konkretnym grobem dopiero przy odzyskiwaniu. Wiazanie idzie po czasie zegarowym:
 * {@code capturedAt} snapshotu przeciw {@code getOwnerDeathTime()} grobu, ktore Tombstone
 * ustawia z {@code System.currentTimeMillis()} - ta sama skala.
 *
 * <p>Dopasowany snapshot jest ZUZYWANY, zeby gracz z kilkoma nieodwiedzonymi grobami dostal
 * z kazdego jego wlasne rozsadzenie, a nie rozsadzenie z ostatniej smierci.
 */
public final class SnapshotStore {

    private final List<SlotSnapshot> pending = new ArrayList<SlotSnapshot>();

    public void add(SlotSnapshot snapshot) {
        pending.add(snapshot);
    }

    public List<SlotSnapshot> all() {
        return Collections.unmodifiableList(pending);
    }

    /**
     * Najblizszy czasowo snapshot, usuniety z magazynu.
     *
     * @return null gdy nic nie miesci sie w tolerancji - wtedy sciezka standardowa
     */
    public SlotSnapshot claimNearest(long graveDeathTime, long toleranceMillis) {
        int best = -1;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < pending.size(); i++) {
            long distance = Math.abs(pending.get(i).capturedAt() - graveDeathTime);
            if (distance <= toleranceMillis && distance < bestDistance) {
                best = i;
                bestDistance = distance;
            }
        }
        return best < 0 ? null : pending.remove(best);
    }

    /**
     * Przycina z dwoch stron: liczba sztuk i wiek. Bez tego jedna smierc bez powrotu po grob
     * zostawialaby wpis na zawsze.
     */
    public void prune(long nowMillis, int maxEntries, long maxAgeMillis) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            if (nowMillis - pending.get(i).capturedAt() > maxAgeMillis) {
                pending.remove(i);
            }
        }
        while (pending.size() > maxEntries) {
            int oldest = 0;
            for (int i = 1; i < pending.size(); i++) {
                if (pending.get(i).capturedAt() < pending.get(oldest).capturedAt()) {
                    oldest = i;
                }
            }
            pending.remove(oldest);
        }
    }
}
```

- [ ] **Step 4: Uruchom testy**

Run: `./gradlew test`
Expected: PASS, 19 testów.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(core): SnapshotStore - wiazanie po czasie zegarowym, zuzywanie i przycinanie"
```

---

## Task 6: `core` — cztery listy ochrony

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/StackView.java`
- Create: `src/main/java/com/spege/tombtweaks/core/ProtectionRules.java`
- Test: `src/test/java/com/spege/tombtweaks/core/ProtectionRulesTest.java`

- [ ] **Step 1: Napisz failujący test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionRulesTest {

    /** Atrapa stacka - core nie zna ItemStacka i nie ma o czym wiedziec. */
    private static final class FakeStack implements StackView {
        private final String id;
        private final Set<String> enchants;
        private final Map<String, String> nbt;

        FakeStack(String id) {
            this(id, Collections.<String>emptySet(), Collections.<String, String>emptyMap());
        }

        FakeStack(String id, Set<String> enchants, Map<String, String> nbt) {
            this.id = id;
            this.enchants = enchants;
            this.nbt = nbt;
        }

        public String itemId() { return id; }
        public Set<String> enchantmentIds() { return enchants; }
        public String nbtString(String key) { return nbt.get(key); }
    }

    private static List<String> none() {
        return new ArrayList<String>();
    }

    @Test
    void pustaKonfiguracjaNieChroniNiczego() {
        ProtectionRules rules = ProtectionRules.of(none(), none(), none(), none());
        assertFalse(rules.isProtected(new FakeStack("minecraft:diamond_sword")));
    }

    @Test
    void pelnaNazwaChroniTylkoSiebie() {
        ProtectionRules rules = ProtectionRules.of(
                Arrays.asList("minecraft:diamond_sword"), none(), none(), none());

        assertTrue(rules.isProtected(new FakeStack("minecraft:diamond_sword")));
        assertFalse(rules.isProtected(new FakeStack("minecraft:diamond_shovel")));
    }

    @Test
    void prefiksLapieCalaRodzine() {
        ProtectionRules rules = ProtectionRules.of(
                none(), Arrays.asList("mymod:relic_"), none(), none());

        assertTrue(rules.isProtected(new FakeStack("mymod:relic_blade")));
        assertTrue(rules.isProtected(new FakeStack("mymod:relic_crown")));
        assertFalse(rules.isProtected(new FakeStack("mymod:common_blade")));
    }

    @Test
    void enchantChroniNiezaleznieOdItemu() {
        ProtectionRules rules = ProtectionRules.of(
                none(), none(), Arrays.asList("mymod:soulbound"), none());

        Set<String> enchants = new HashSet<String>(Arrays.asList("mymod:soulbound"));
        assertTrue(rules.isProtected(
                new FakeStack("minecraft:stick", enchants, Collections.<String, String>emptyMap())));
        assertFalse(rules.isProtected(new FakeStack("minecraft:stick")));
    }

    @Test
    void matcherNbtPorownujeWartosc() {
        ProtectionRules rules = ProtectionRules.of(
                none(), none(), none(), Arrays.asList("properties=ashen_legacy"));

        Map<String, String> matching = new HashMap<String, String>();
        matching.put("properties", "ashen_legacy");
        Map<String, String> different = new HashMap<String, String>();
        different.put("properties", "something_else");

        assertTrue(rules.isProtected(
                new FakeStack("minecraft:stick", Collections.<String>emptySet(), matching)));
        assertFalse(rules.isProtected(
                new FakeStack("minecraft:stick", Collections.<String>emptySet(), different)));
        assertFalse(rules.isProtected(new FakeStack("minecraft:stick")));
    }

    @Test
    void popsutyWpisNbtJestIgnorowanyANiefatalny() {
        ProtectionRules rules = ProtectionRules.of(
                none(), none(), none(),
                Arrays.asList("bez_rownosci", "=bez_klucza", "klucz=", "dobry=wpis"));

        Map<String, String> matching = new HashMap<String, String>();
        matching.put("dobry", "wpis");

        assertTrue(rules.isProtected(
                new FakeStack("minecraft:stick", Collections.<String>emptySet(), matching)));
        assertFalse(rules.isProtected(new FakeStack("minecraft:stick")));
    }

    @Test
    void bialeZnakiWKonfiguracjiSaWybaczane() {
        ProtectionRules rules = ProtectionRules.of(
                Arrays.asList("  minecraft:stick  ", "", "   "), none(), none(), none());

        assertTrue(rules.isProtected(new FakeStack("minecraft:stick")));
    }
}
```

- [ ] **Step 2: Uruchom test, potwierdź że nie kompiluje**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class StackView`.

- [ ] **Step 3: Napisz `StackView`**

```java
package com.spege.tombtweaks.core;

import java.util.Set;

/**
 * Co core wie o jednym stacku. Implementowane w warstwie platform, zeby core
 * nie musial znac ItemStacka.
 */
public interface StackView {

    /** Nazwa rejestrowa, np. "minecraft:diamond_sword". Nigdy null. */
    String itemId();

    /** Nazwy rejestrowe enchantow stacka. Pusty zbior gdy brak. */
    Set<String> enchantmentIds();

    /** Wartosc stringowego klucza NBT najwyzszego poziomu, albo null gdy go nie ma. */
    String nbtString(String key);
}
```

- [ ] **Step 4: Napisz `ProtectionRules`**

```java
package com.spege.tombtweaks.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Czego rozkladajacy sie grob nie ma prawa zjesc.
 *
 * <p>Cztery niezalezne reguly; stack jest chroniony, gdy pasuje KTORAKOLWIEK.
 *
 * <p>Zasada, ktorej nie wolno zlamac: <b>pusta lista nie chroni niczego</b>. Odwrotna
 * interpretacja zamienilaby domyslna konfiguracje w grob, ktory nigdy nic nie traci,
 * czyli w feature wygladajacy na dzialajacy i nierobiacy nic.
 */
public final class ProtectionRules {

    private final Set<String> items;
    private final List<String> prefixes;
    private final Set<String> enchantments;
    private final List<String[]> nbtPairs;

    private ProtectionRules(Set<String> items, List<String> prefixes,
                            Set<String> enchantments, List<String[]> nbtPairs) {
        this.items = items;
        this.prefixes = prefixes;
        this.enchantments = enchantments;
        this.nbtPairs = nbtPairs;
    }

    public static ProtectionRules of(List<String> items, List<String> prefixes,
                                     List<String> enchantments, List<String> nbtStrings) {
        Set<String> parsedItems = new HashSet<String>();
        for (String raw : items) {
            String value = raw.trim();
            if (!value.isEmpty()) {
                parsedItems.add(value);
            }
        }

        List<String> parsedPrefixes = new ArrayList<String>();
        for (String raw : prefixes) {
            String value = raw.trim();
            if (!value.isEmpty()) {
                parsedPrefixes.add(value);
            }
        }

        Set<String> parsedEnchantments = new HashSet<String>();
        for (String raw : enchantments) {
            String value = raw.trim();
            if (!value.isEmpty()) {
                parsedEnchantments.add(value);
            }
        }

        List<String[]> parsedPairs = new ArrayList<String[]>();
        for (String raw : nbtStrings) {
            int equals = raw.indexOf('=');
            if (equals <= 0 || equals == raw.length() - 1) {
                continue; // popsuty wpis jest ignorowany, nigdy fatalny
            }
            String key = raw.substring(0, equals).trim();
            String value = raw.substring(equals + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                parsedPairs.add(new String[] { key, value });
            }
        }

        return new ProtectionRules(parsedItems, parsedPrefixes, parsedEnchantments, parsedPairs);
    }

    public boolean isProtected(StackView stack) {
        String id = stack.itemId();

        if (items.contains(id)) {
            return true;
        }
        for (int i = 0; i < prefixes.size(); i++) {
            if (id.startsWith(prefixes.get(i))) {
                return true;
            }
        }
        if (!enchantments.isEmpty()) {
            for (String enchantment : stack.enchantmentIds()) {
                if (enchantments.contains(enchantment)) {
                    return true;
                }
            }
        }
        for (int i = 0; i < nbtPairs.size(); i++) {
            String[] pair = nbtPairs.get(i);
            String value = stack.nbtString(pair[0]);
            if (value != null && value.equals(pair[1])) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 5: Uruchom testy**

Run: `./gradlew test`
Expected: PASS, 26 testów.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(core): ProtectionRules - cztery listy, pusta lista nie chroni niczego"
```

---

## Task 7: `core` — harmonogram i wybór ofiary

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/DecayVictim.java`
- Test: `src/test/java/com/spege/tombtweaks/core/DecayScheduleTest.java`
- Test: `src/test/java/com/spege/tombtweaks/core/DecayVictimTest.java`

- [ ] **Step 1: Napisz test harmonogramu**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DecayScheduleTest {

    private static final long START = 6_000L;
    private static final long INTERVAL = 1_200L;

    @Test
    void nicPrzedProgiem() {
        assertFalse(DecaySchedule.shouldDecay(0L, START, INTERVAL));
        assertFalse(DecaySchedule.shouldDecay(START - 1L, START, INTERVAL));
    }

    @Test
    void pierwszeOdpalenieDokladnieNaProgu() {
        assertTrue(DecaySchedule.shouldDecay(START, START, INTERVAL));
    }

    @Test
    void dokladnieJednoOdpalenieNaInterwal() {
        int fired = 0;
        for (long tick = START; tick < START + 10L * INTERVAL; tick++) {
            if (DecaySchedule.shouldDecay(tick, START, INTERVAL)) {
                fired++;
            }
        }
        assertEquals(10, fired);
    }

    @Test
    void zerowyInterwalNieDzieliPrzezZero() {
        assertFalse(DecaySchedule.shouldDecay(999_999L, START, 0L));
        assertFalse(DecaySchedule.shouldDecay(999_999L, START, -5L));
    }

    @Test
    void cofnietyLicznikMilczy() {
        // countTicks moze wrocic do zera, gdy grob zostanie postawiony na nowo.
        assertFalse(DecaySchedule.shouldDecay(10L, START, INTERVAL));
    }
}
```

- [ ] **Step 2: Uruchom test**

Run: `./gradlew test --tests '*DecayScheduleTest*'`
Expected: PASS — `DecaySchedule` powstał już w Tasku 2, więc te testy przechodzą od razu. To celowe: potwierdzają zachowanie, którego nikt jeszcze nie sprawdził.

- [ ] **Step 3: Napisz failujący test wyboru ofiary**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecayVictimTest {

    /** Deterministyczna "losowosc": zawsze pierwszy element. */
    private static final IntUnaryOperator FIRST = bound -> 0;
    /** Zawsze ostatni. */
    private static final IntUnaryOperator LAST = bound -> bound - 1;

    private static List<Integer> none() {
        return new ArrayList<Integer>();
    }

    @Test
    void pustyGrobNieOddajeNiczego() {
        assertEquals(DecayVictim.NOTHING, DecayVictim.pick(none(), none(), true, FIRST));
        assertEquals(DecayVictim.NOTHING, DecayVictim.pick(none(), none(), false, FIRST));
    }

    @Test
    void niechronioneIdaPierwsze() {
        List<Integer> unprotected = Arrays.asList(3, 4);
        List<Integer> guarded = Arrays.asList(9);

        assertEquals(3, DecayVictim.pick(unprotected, guarded, true, FIRST));
        assertEquals(4, DecayVictim.pick(unprotected, guarded, true, LAST));
    }

    @Test
    void samoChronioneIZwolnienieOznaczaZatrzymanie() {
        assertEquals(DecayVictim.NOTHING,
                     DecayVictim.pick(none(), Arrays.asList(9, 10), true, FIRST));
    }

    @Test
    void samoChronioneIKolejnoscOznaczaZjedzenieChronionego() {
        assertEquals(9, DecayVictim.pick(none(), Arrays.asList(9, 10), false, FIRST));
        assertEquals(10, DecayVictim.pick(none(), Arrays.asList(9, 10), false, LAST));
    }

    @Test
    void losowanieMiesciSieWZakresie() {
        List<Integer> unprotected = Arrays.asList(1, 2, 3);
        for (int i = 0; i < 3; i++) {
            final int fixed = i;
            int picked = DecayVictim.pick(unprotected, none(), true, bound -> fixed);
            assertTrue(unprotected.contains(picked));
        }
    }
}
```

- [ ] **Step 4: Uruchom test, potwierdź że nie kompiluje**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class DecayVictim`.

- [ ] **Step 5: Napisz `DecayVictim`**

```java
package com.spege.tombtweaks.core;

import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Ktory zajety slot rozkladajacy sie grob oddaje nastepny.
 *
 * <p>Dwie listy kandydatow, nie jedna. Stacki niechronione sa zjadane pierwsze; chronione
 * sa w ogole nieosiagalne, dopoki zostalo cokolwiek innego. Gdy zostaly tylko one, decyduje
 * {@code neverDecay}: prawda = ochrona jest zwolnieniem (grob staje w miejscu),
 * falsz = ochrona byla tylko kolejnoscia.
 */
public final class DecayVictim {

    public static final int NOTHING = -1;

    private DecayVictim() {
    }

    /**
     * @param unprotected indeksy slotow ze stackiem niechronionym
     * @param guarded     indeksy slotow ze stackiem chronionym
     * @param neverDecay  czy ochrona jest zwolnieniem, czy tylko kolejnoscia
     * @param random      bound -&gt; wartosc z przedzialu [0, bound)
     */
    public static int pick(List<Integer> unprotected, List<Integer> guarded,
                           boolean neverDecay, IntUnaryOperator random) {
        List<Integer> pool = unprotected;
        if (pool.isEmpty()) {
            if (guarded.isEmpty() || neverDecay) {
                return NOTHING;
            }
            pool = guarded;
        }
        return pool.get(random.applyAsInt(pool.size())).intValue();
    }
}
```

> `IntUnaryOperator` jest w `java.util.function`, obecnym od Javy 8 — nie łamie zasady 1.

- [ ] **Step 6: Uruchom testy**

Run: `./gradlew test`
Expected: PASS, 36 testów.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(core): DecayVictim + testy harmonogramu decay"
```

---

## Task 8: `core` — cooldowny

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/core/CooldownRules.java`
- Create: `src/main/java/com/spege/tombtweaks/core/Cooldown.java`
- Test: `src/test/java/com/spege/tombtweaks/core/CooldownTest.java`

- [ ] **Step 1: Napisz failujący test**

```java
package com.spege.tombtweaks.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownTest {

    private static final long MINUTE = 60_000L;

    @Test
    void parserCzytaParyNazwaMinuty() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList(
                "tombstone:book_of_disenchantment;6",
                "tombstone:book_of_magic_impregnation;12"));

        assertEquals(6L * MINUTE, rules.millisFor("tombstone:book_of_disenchantment"));
        assertEquals(12L * MINUTE, rules.millisFor("tombstone:book_of_magic_impregnation"));
    }

    @Test
    void nieznanaKsiegaNieMaCooldownu() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;5"));
        assertEquals(0L, rules.millisFor("tombstone:book_of_oblivion"));
    }

    @Test
    void zeroMinutOznaczaWylaczony() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;0"));
        assertEquals(0L, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void popsuteWpisySaPomijaneANieFatalne() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList(
                "bez_srednika",
                "tombstone:book_of_scribe;nie_liczba",
                ";7",
                "tombstone:book_of_scribe;5"));

        assertEquals(5L * MINUTE, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void minutySaOgraniczoneDoDwunastuGodzin() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;9000"));
        assertEquals(720L * MINUTE, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void ujemneMinutyToBrakCooldownu() {
        CooldownRules rules = CooldownRules.parse(Arrays.asList("tombstone:book_of_scribe;-3"));
        assertEquals(0L, rules.millisFor("tombstone:book_of_scribe"));
    }

    @Test
    void pustaKonfiguracjaJestPusta() {
        assertTrue(CooldownRules.parse(new ArrayList<String>()).isEmpty());
    }

    @Test
    void pozostalyCzasMalejeIDochodziDoZera() {
        long lastUse = 1_000_000L;
        long cooldown = 10L * MINUTE;

        assertEquals(cooldown, Cooldown.remaining(lastUse, cooldown, lastUse));
        assertEquals(cooldown - MINUTE, Cooldown.remaining(lastUse, cooldown, lastUse + MINUTE));
        assertEquals(0L, Cooldown.remaining(lastUse, cooldown, lastUse + cooldown));
        assertEquals(0L, Cooldown.remaining(lastUse, cooldown, lastUse + cooldown + 1L));
    }

    @Test
    void brakUzyciaToBrakCooldownu() {
        assertEquals(0L, Cooldown.remaining(0L, 10L * MINUTE, 5_000_000L));
        assertFalse(Cooldown.isActive(0L, 10L * MINUTE, 5_000_000L));
    }

    @Test
    void cofnietyZegarNieBlokujeGracza() {
        // Zegar systemowy moze sie cofnac (zmiana czasu, synchronizacja NTP).
        // Nie wolno przez to zamknac graczowi ksiegi na wieki.
        assertEquals(0L, Cooldown.remaining(5_000_000L, 10L * MINUTE, 1_000L));
        assertFalse(Cooldown.isActive(5_000_000L, 10L * MINUTE, 1_000L));
    }

    @Test
    void aktywnyDopokiCosZostalo() {
        long lastUse = 1_000_000L;
        assertTrue(Cooldown.isActive(lastUse, 10L * MINUTE, lastUse + MINUTE));
        assertFalse(Cooldown.isActive(lastUse, 10L * MINUTE, lastUse + 10L * MINUTE));
    }
}
```

- [ ] **Step 2: Uruchom test, potwierdź że nie kompiluje**

Run: `./gradlew test`
Expected: FAIL — `cannot find symbol: class CooldownRules`.

- [ ] **Step 3: Napisz `CooldownRules`**

```java
package com.spege.tombtweaks.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ile trwa cooldown ktorej ksiegi.
 *
 * <p>Format wpisu: {@code nazwa_rejestrowa;minuty}. Popsuty wpis jest pomijany, nigdy fatalny -
 * blad w configu nie ma prawa wywalic serwera. Minuty sa przycinane do 12 godzin, bo
 * wieksza wartosc to prawie na pewno pomylka, a nie zamiar.
 */
public final class CooldownRules {

    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MAX_MINUTES = 720L;

    private final Map<String, Long> millisById;

    private CooldownRules(Map<String, Long> millisById) {
        this.millisById = millisById;
    }

    public static CooldownRules parse(List<String> entries) {
        Map<String, Long> parsed = new HashMap<String, Long>();
        for (String raw : entries) {
            int separator = raw.indexOf(';');
            if (separator <= 0 || separator == raw.length() - 1) {
                continue;
            }
            String id = raw.substring(0, separator).trim();
            if (id.isEmpty()) {
                continue;
            }
            long minutes;
            try {
                minutes = Long.parseLong(raw.substring(separator + 1).trim());
            } catch (NumberFormatException malformed) {
                continue;
            }
            if (minutes <= 0L) {
                continue;
            }
            if (minutes > MAX_MINUTES) {
                minutes = MAX_MINUTES;
            }
            parsed.put(id, Long.valueOf(minutes * MILLIS_PER_MINUTE));
        }
        return new CooldownRules(parsed);
    }

    /** @return dlugosc cooldownu w ms, albo 0 gdy ta ksiega go nie ma */
    public long millisFor(String itemId) {
        Long millis = millisById.get(itemId);
        return millis == null ? 0L : millis.longValue();
    }

    public boolean isEmpty() {
        return millisById.isEmpty();
    }
}
```

- [ ] **Step 4: Napisz `Cooldown`**

```java
package com.spege.tombtweaks.core;

/** Arytmetyka pozostalego czasu. Zegar scienny w milisekundach. */
public final class Cooldown {

    private Cooldown() {
    }

    /**
     * @return ile ms zostalo, 0 gdy wolne
     */
    public static long remaining(long lastUseMillis, long cooldownMillis, long nowMillis) {
        if (cooldownMillis <= 0L || lastUseMillis <= 0L) {
            return 0L;
        }
        if (nowMillis < lastUseMillis) {
            return 0L; // zegar sie cofnal - nie zamykamy gracza na wieki
        }
        long elapsed = nowMillis - lastUseMillis;
        return elapsed >= cooldownMillis ? 0L : cooldownMillis - elapsed;
    }

    public static boolean isActive(long lastUseMillis, long cooldownMillis, long nowMillis) {
        return remaining(lastUseMillis, cooldownMillis, nowMillis) > 0L;
    }
}
```

- [ ] **Step 5: Uruchom testy**

Run: `./gradlew test`
Expected: PASS, 47 testów. Cały `core` gotowy.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(core): CooldownRules + Cooldown, odporne na popsuty config i cofniety zegar"
```

---

## Task 9: `platform` — config

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/platform/Config.java`
- Modify: `src/main/java/com/spege/tombtweaks/TombTweaks.java`

- [ ] **Step 1: Napisz `Config`**

```java
package com.spege.tombtweaks.platform;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Trzy kategorie, plik config/tombtweaks-common.toml.
 *
 * <p>Bez master switcha: przy trzech featurach z wlasnymi przelacznikami byl juz tylko
 * dodatkowym stanem, w ktorym da sie utknac.
 *
 * <p>Domyslne listy ochrony sa PUSTE. Te z 1.12.2 naleza do konkretnego packa,
 * a publiczny mod nie ma prawa ich zakladac.
 */
public final class Config {

    public final ForgeConfigSpec.BooleanValue restoreEnabled;
    public final ForgeConfigSpec.BooleanValue restoreDebugLogging;

    public final ForgeConfigSpec.BooleanValue decayEnabled;
    public final ForgeConfigSpec.IntValue decayStartTicks;
    public final ForgeConfigSpec.IntValue decayIntervalTicks;
    public final ForgeConfigSpec.IntValue decayMaxHistory;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedItems;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedItemPrefixes;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedEnchantments;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> decayProtectedNbtStrings;
    public final ForgeConfigSpec.BooleanValue decayProtectedNeverDecay;

    public final ForgeConfigSpec.BooleanValue cooldownEnabled;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> cooldownBooks;

    private Config(ForgeConfigSpec.Builder builder) {
        builder.comment("Items return to the slots they came from when a grave is recovered.")
               .push("restore");
        restoreEnabled = builder
                .comment("Read live - no restart needed.",
                         "NOTE: Tombstone's own per-player 'reverse inventory sorting' preference",
                         "runs AFTER this and rewrites the whole inventory order. If a player has it",
                         "on, their restored layout will come back reversed.")
                .define("enabled", true);
        restoreDebugLogging = builder
                .comment("Log what each death recorded and what each recovered grave put back.")
                .define("debugLogging", false);
        builder.pop();

        builder.comment("An unvisited grave slowly drops its contents on the ground.")
               .push("decay");
        decayEnabled = builder
                .comment("Off by default. Read live - no restart needed.")
                .define("enabled", false);
        decayStartTicks = builder
                .comment("Ticks the grave must have been LOADED before decay starts.",
                         "This is Tombstone's own countTicks, so it does not advance while the",
                         "chunk is unloaded. Vanilla item despawn = 6000 (5 min). 24000 = 1 MC day.")
                .defineInRange("startTicks", 6000, 0, Integer.MAX_VALUE);
        decayIntervalTicks = builder
                .comment("Ticks between each item removal. 1200 = 60 seconds. 0 disables decay.")
                .defineInRange("intervalTicks", 1200, 0, Integer.MAX_VALUE);
        decayMaxHistory = builder
                .comment("Max number of decay records kept per player.")
                .defineInRange("maxHistory", 10, 0, 1000);
        decayProtectedItems = builder
                .comment("Full registry names that decay must not eat, e.g. \"mymod:relic_blade\".",
                         "An empty list protects NOTHING.")
                .defineList("protectedItems", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedItemPrefixes = builder
                .comment("Registry-name prefixes, e.g. \"mymod:relic_\".")
                .defineList("protectedItemPrefixes", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedEnchantments = builder
                .comment("Registry names of enchantments that protect whatever carries them.")
                .defineList("protectedEnchantments", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedNbtStrings = builder
                .comment("Top-level string NBT tags, as \"key=value\". A malformed entry is ignored.")
                .defineList("protectedNbtStrings", Collections.<String>emptyList(), o -> o instanceof String);
        decayProtectedNeverDecay = builder
                .comment("true  = protection is an exemption; a grave holding only protected items stops decaying.",
                         "false = protection is only an ordering; protected items are eaten last.")
                .define("protectedNeverDecay", true);
        builder.pop();

        builder.comment("Cooldowns for Tombstone's magic books.")
               .push("cooldown");
        cooldownEnabled = builder
                .comment("Read live - no restart needed.",
                         "NOTE: the mixins are applied regardless of this flag; it is an early return",
                         "inside the handler, not a gate on mixin application.")
                .define("enabled", true);
        cooldownBooks = builder
                .comment("One entry per book, as \"registry_name;minutes\".",
                         "Minutes are clamped to 720 (12 h). 0 or a malformed entry means no cooldown.",
                         "Any Tombstone book works here, not just the two below.")
                .defineList("books", Arrays.asList(
                        "tombstone:book_of_disenchantment;6",
                        "tombstone:book_of_magic_impregnation;6"), o -> o instanceof String);
        builder.pop();
    }

    public static final Config INSTANCE;
    public static final ForgeConfigSpec SPEC;

    static {
        Pair<Config, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }
}
```

- [ ] **Step 2: Zarejestruj config w klasie `@Mod`**

Zastąp treść `TombTweaks.java`:

```java
package com.spege.tombtweaks;

import com.spege.tombtweaks.platform.Config;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(TombTweaks.MODID)
public final class TombTweaks {

    public static final String MODID = "tombtweaks";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public TombTweaks() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC, "tombtweaks-common.toml");
        LOGGER.info("[TombTweaks] {} loading", VERSION);
    }
}
```

- [ ] **Step 3: Zbuduj i odpal klienta**

Run: `./gradlew runClient`
Expected: klient wstaje, a w `run/config/` powstaje `tombtweaks-common.toml` z trzema kategoriami i `[decay] enabled = false`. Sprawdź plik, zamknij klienta.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat(platform): ForgeConfigSpec z trzema kategoriami"
```

---

## Task 10: `platform` — adaptery i kodek

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/platform/StackViews.java`
- Create: `src/main/java/com/spege/tombtweaks/platform/PlayerData.java`
- Create: `src/main/java/com/spege/tombtweaks/platform/SnapshotCodec.java`

- [ ] **Step 1: Napisz `StackViews`**

```java
package com.spege.tombtweaks.platform;

import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.StackView;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Jedyne miejsce, w ktorym ItemStack zamienia sie w cos, co rozumie core. */
public final class StackViews {

    private static final String UNKNOWN = "minecraft:air";

    private StackViews() {
    }

    public static String idOf(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? UNKNOWN : id.toString();
    }

    public static ItemKey keyOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return new ItemKey(idOf(stack), tag == null ? 0 : tag.hashCode());
    }

    public static StackView view(final ItemStack stack) {
        return new StackView() {
            @Override
            public String itemId() {
                return idOf(stack);
            }

            @Override
            public Set<String> enchantmentIds() {
                Map<Enchantment, Integer> found = EnchantmentHelper.getEnchantments(stack);
                if (found.isEmpty()) {
                    return Collections.emptySet();
                }
                Set<String> ids = new HashSet<>();
                for (Enchantment enchantment : found.keySet()) {
                    ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
                    if (id != null) {
                        ids.add(id.toString());
                    }
                }
                return ids;
            }

            @Override
            public String nbtString(String key) {
                CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains(key, Tag.TAG_STRING)) {
                    return null;
                }
                return tag.getString(key);
            }
        };
    }
}
```

- [ ] **Step 2: Napisz `PlayerData`**

```java
package com.spege.tombtweaks.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * Jedyny pod-tag danych gracza, ktory Forge przenosi przez smierc.
 * Wszystko, co ma przetrwac respawn, musi siedziec tutaj.
 */
public final class PlayerData {

    private PlayerData() {
    }

    public static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
```

- [ ] **Step 3: Napisz `SnapshotCodec`**

```java
package com.spege.tombtweaks.platform;

import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.SlotEntry;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/** SnapshotStore w obie strony przez NBT gracza. */
public final class SnapshotCodec {

    private static final String ROOT = "tombtweaks_slot_snapshots";

    private SnapshotCodec() {
    }

    public static SnapshotStore load(Player player) {
        SnapshotStore store = new SnapshotStore();
        CompoundTag persisted = PlayerData.persisted(player);
        if (!persisted.contains(ROOT, Tag.TAG_LIST)) {
            return store;
        }
        ListTag snapshots = persisted.getList(ROOT, Tag.TAG_COMPOUND);
        for (int i = 0; i < snapshots.size(); i++) {
            CompoundTag tag = snapshots.getCompound(i);
            List<SlotEntry> entries = new ArrayList<>();
            ListTag seats = tag.getList("e", Tag.TAG_COMPOUND);
            for (int s = 0; s < seats.size(); s++) {
                CompoundTag seat = seats.getCompound(s);
                entries.add(new SlotEntry(
                        seat.getInt("s"),
                        new ItemKey(seat.getString("i"), seat.getInt("h")),
                        seat.getInt("n")));
            }
            store.add(new SlotSnapshot(tag.getLong("c"), entries));
        }
        return store;
    }

    public static void save(Player player, SnapshotStore store) {
        ListTag snapshots = new ListTag();
        for (SlotSnapshot snapshot : store.all()) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("c", snapshot.capturedAt());
            ListTag seats = new ListTag();
            for (SlotEntry entry : snapshot.entries()) {
                CompoundTag seat = new CompoundTag();
                seat.putInt("s", entry.slot());
                seat.putString("i", entry.key().id());
                seat.putInt("h", entry.key().nbtHash());
                seat.putInt("n", entry.count());
                seats.add(seat);
            }
            tag.put("e", seats);
            snapshots.add(tag);
        }
        PlayerData.persisted(player).put(ROOT, snapshots);
    }
}
```

- [ ] **Step 4: Zbuduj**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(platform): StackViews, PlayerData i SnapshotCodec"
```

---

## Task 11: Feature — exact-slot restore

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/feature/restore/SnapshotCaptureHandler.java`
- Create: `src/main/java/com/spege/tombtweaks/feature/restore/SlotRestoreHandler.java`
- Modify: `src/main/java/com/spege/tombtweaks/TombTweaks.java`

- [ ] **Step 1: Napisz `SnapshotCaptureHandler`**

```java
package com.spege.tombtweaks.feature.restore;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.SnapshotCodec;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Zapisuje rozklad ekwipunku w ostatnim momencie, w ktorym jest jeszcze prawda.
 *
 * <p>{@code Player.die()} oproznia ekwipunek zaraz po tym evencie, a wszystko, co dociera
 * do {@code LivingDropsEvent}, jest juz plaska kupka bez indeksow.
 *
 * <p>Priorytet HIGHEST, zeby handler anulujacy smierc nie wszedl przed nami. Snapshot dla
 * smierci, ktora sie nie wydarzyla, kosztuje jeden nieuzyty wpis, ktory magazyn przycina.
 */
@Mod.EventBusSubscriber(modid = TombTweaks.MODID)
public final class SnapshotCaptureHandler {

    private static final int MAX_PENDING = 5;
    private static final long MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1000L; // 30 dni

    private SnapshotCaptureHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.restoreEnabled.get()) {
            return;
        }
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }

        long now = System.currentTimeMillis();
        SlotSnapshot snapshot = new SlotSnapshot(now);
        Inventory inventory = player.getInventory();

        for (int i = 0; i < inventory.items.size(); i++) {
            record(snapshot, i, inventory.items.get(i));
        }
        for (int i = 0; i < inventory.armor.size(); i++) {
            record(snapshot, SlotSnapshot.ARMOR_BASE + i, inventory.armor.get(i));
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            record(snapshot, SlotSnapshot.OFFHAND_SLOT + i, inventory.offhand.get(i));
        }

        if (snapshot.isEmpty()) {
            return;
        }

        SnapshotStore store = SnapshotCodec.load(player);
        store.add(snapshot);
        store.prune(now, MAX_PENDING, MAX_AGE_MILLIS);
        SnapshotCodec.save(player, store);

        if (Config.INSTANCE.restoreDebugLogging.get()) {
            TombTweaks.LOGGER.info("[TombTweaks] recorded {} seats for {} at {}",
                    snapshot.entries().size(), player.getGameProfile().getName(), now);
        }
    }

    private static void record(SlotSnapshot snapshot, int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        snapshot.add(slot, StackViews.keyOf(stack), stack.getCount());
    }
}
```

- [ ] **Step 2: Napisz `SlotRestoreHandler`**

```java
package com.spege.tombtweaks.feature.restore;

import com.spege.tombtweaks.TombTweaks;
import com.spege.tombtweaks.core.SlotPlan;
import com.spege.tombtweaks.core.SlotSnapshot;
import com.spege.tombtweaks.core.SnapshotStore;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.SnapshotCodec;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import ovh.corail.tombstone.api.event.RestoreInventoryEvent;

/**
 * Odklada zawartosc odzyskanego grobu tam, skad przyszla.
 *
 * <p>Bez mixina. Tombstone odpala {@code RestoreInventoryEvent} w jedynym uzytecznym momencie:
 * po tym, jak chanceLossOnDeath wzial swoja czesc, i przed auto-equipem oraz cala reszta
 * rozdzialu. Dostajemy zywy ItemStackHandler grobu, wiec przedmiot wyjety tutaj po prostu
 * nie istnieje dla sciezki standardowej.
 *
 * <p>Kazde wstawienie jest warunkowe na tym, ze docelowy slot jest pusty. Dzieki temu
 * najgorszym przypadkiem jest zachowanie standardowe, nigdy zgubiony ani zduplikowany przedmiot.
 */
@Mod.EventBusSubscriber(modid = TombTweaks.MODID)
public final class SlotRestoreHandler {

    /** Ile ms moze dzielic capturedAt snapshotu od deathDate grobu. */
    private static final long TOLERANCE_MILLIS = 10_000L;

    private SlotRestoreHandler() {
    }

    @SubscribeEvent
    public static void onRestoreInventory(RestoreInventoryEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.restoreEnabled.get()) {
            return;
        }

        SnapshotStore store = SnapshotCodec.load(player);
        SlotSnapshot snapshot = store.claimNearest(event.getOwnerDeathTime(), TOLERANCE_MILLIS);
        if (snapshot == null) {
            if (Config.INSTANCE.restoreDebugLogging.get()) {
                TombTweaks.LOGGER.info("[TombTweaks] no snapshot within {} ms of grave death time {}",
                        TOLERANCE_MILLIS, event.getOwnerDeathTime());
            }
            return;
        }
        SnapshotCodec.save(player, store);

        SlotPlan plan = new SlotPlan(snapshot);
        IItemHandler grave = event.getInventory();
        Inventory inventory = player.getInventory();
        int seated = 0;

        for (int i = 0; i < grave.getSlots(); i++) {
            ItemStack inGrave = grave.getStackInSlot(i);
            if (inGrave.isEmpty()) {
                continue;
            }
            int seat = plan.claimSeat(StackViews.keyOf(inGrave));
            if (seat == SlotPlan.NO_SEAT || !isSeatFree(inventory, seat)) {
                continue;
            }
            ItemStack taken = grave.extractItem(i, inGrave.getCount(), false);
            if (taken.isEmpty()) {
                continue;
            }
            place(inventory, seat, taken);
            seated++;
        }

        if (Config.INSTANCE.restoreDebugLogging.get()) {
            TombTweaks.LOGGER.info("[TombTweaks] seated {} of {} recorded stacks for {}",
                    seated, snapshot.entries().size(), player.getGameProfile().getName());
        }
    }

    private static boolean isSeatFree(Inventory inventory, int seat) {
        if (seat == SlotSnapshot.OFFHAND_SLOT) {
            return inventory.offhand.get(0).isEmpty();
        }
        if (seat >= SlotSnapshot.ARMOR_BASE && seat < SlotSnapshot.ARMOR_BASE + inventory.armor.size()) {
            return inventory.armor.get(seat - SlotSnapshot.ARMOR_BASE).isEmpty();
        }
        if (seat >= 0 && seat < inventory.items.size()) {
            return inventory.items.get(seat).isEmpty();
        }
        return false; // np. zarezerwowana przestrzen Curios - nieobslugiwana w v1
    }

    private static void place(Inventory inventory, int seat, ItemStack stack) {
        if (seat == SlotSnapshot.OFFHAND_SLOT) {
            inventory.offhand.set(0, stack);
        } else if (seat >= SlotSnapshot.ARMOR_BASE && seat < SlotSnapshot.ARMOR_BASE + inventory.armor.size()) {
            inventory.armor.set(seat - SlotSnapshot.ARMOR_BASE, stack);
        } else {
            inventory.items.set(seat, stack);
        }
    }
}
```

- [ ] **Step 3: Zbuduj**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Weryfikacja w grze**

Run: `./gradlew runClient`

Zrób w grze:
1. `/gamerule keepInventory false`
2. Ustaw charakterystyczny układ: miecz w slocie 1, chleb w slocie 9, coś w offhandzie, pełny pancerz, jeden pusty slot w środku hotbara.
3. Zabij się (`/kill`).
4. Wróć po grób i odzyskaj go.

Expected: wszystko wraca **na swoje miejsca**, łącznie z pancerzem i offhandem; pusty slot zostaje pusty.

Powtórz z `debugLogging = true` i sprawdź w logu parę linii `recorded N seats` / `seated N of M`.

- [ ] **Step 5: Weryfikacja `isReverseInventorySorting`**

W GUI ustawień Tombstone'a sprawdź, czy „reverse inventory sorting" jest domyślnie wyłączone. Włącz je, powtórz test ze Stepu 4 i **zanotuj wynik w README** — to jedyne otwarte założenie ze speca. Nie naprawiaj tego teraz.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(restore): exact-slot grave restore na RestoreInventoryEvent, bez mixina"
```

---

## Task 12: Feature — grave decay

**Files:**
- Modify: `src/main/resources/mixins.tombtweaks.json` (dopisz wpis do pustej listy założonej w Tasku 2)
- Create: `src/main/java/com/spege/tombtweaks/mixin/MixinBlockEntityPlayerGrave.java`
- Create: `src/main/java/com/spege/tombtweaks/feature/decay/GraveDecayService.java`
- Create: `src/main/java/com/spege/tombtweaks/feature/decay/DecayHistory.java`

- [ ] **Step 1: Dopisz mixin do `mixins.tombtweaks.json`**

Plik istnieje od Tasku 2 z pustą listą. Zmień samą listę `mixins` na:

```json
  "mixins": [
    "MixinBlockEntityPlayerGrave"
  ],
```

> Dopisujesz **tylko** mixin z tego zadania. Przy `"required": true` klasa wymieniona, a nieistniejąca, to twardy crash przy starcie — dwa mixiny cooldownu dopisze Task 13 Step 1, gdy już będą istnieć.

- [ ] **Step 2: Napisz `DecayHistory`**

```java
package com.spege.tombtweaks.feature.decay;

import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Co grob juz stracil.
 *
 * <p>Wersja z 1.12.2 zapisywala historie przez wyszukanie gracza PO NAZWIE, wiec grob
 * gracza offline rozkladal sie bez sladu - dokladnie w scenariuszu, dla ktorego feature
 * powstal. Tutaj kluczem jest UUID, a dla gracza offline wpis idzie na razie tylko do logu.
 */
public final class DecayHistory {

    private static final String KEY = "tombtweaks_decay_history";

    private DecayHistory() {
    }

    public static void record(Level level, BlockPos pos, UUID ownerId, ItemStack lost) {
        MinecraftServer server = level.getServer();
        if (server == null || ownerId == null) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            com.spege.tombtweaks.TombTweaks.LOGGER.info(
                    "[TombTweaks] grave at {} lost {} x{} (owner {} offline)",
                    pos, lost.getDescriptionId(), lost.getCount(), ownerId);
            return;
        }

        CompoundTag persisted = PlayerData.persisted(owner);
        ListTag history = persisted.getList(KEY, Tag.TAG_COMPOUND);

        CompoundTag entry = new CompoundTag();
        entry.putLong("at", System.currentTimeMillis());
        entry.putString("pos", pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + "," + level.dimension().location());
        entry.put("stack", lost.save(new CompoundTag()));
        history.add(entry);

        int max = Config.INSTANCE.decayMaxHistory.get();
        while (history.size() > max) {
            history.remove(0);
        }
        persisted.put(KEY, history);
    }
}
```

- [ ] **Step 3: Napisz `GraveDecayService`**

```java
package com.spege.tombtweaks.feature.decay;

import com.spege.tombtweaks.core.DecaySchedule;
import com.spege.tombtweaks.core.DecayVictim;
import com.spege.tombtweaks.core.ProtectionRules;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import ovh.corail.tombstone.block.entity.BlockEntityPlayerGrave;

import java.util.ArrayList;
import java.util.List;

/** Jeden tick jednego grobu. Wszystkie decyzje podejmuje core; tu jest tylko wykonanie. */
public final class GraveDecayService {

    private GraveDecayService() {
    }

    @SuppressWarnings("unchecked")
    public static void tick(Level level, BlockPos pos, BlockEntityPlayerGrave grave) {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (!Config.INSTANCE.decayEnabled.get()) {
            return;
        }
        if (!DecaySchedule.shouldDecay(grave.countTicks,
                                       Config.INSTANCE.decayStartTicks.get(),
                                       Config.INSTANCE.decayIntervalTicks.get())) {
            return;
        }

        IItemHandler inventory = grave.getInventory();
        if (inventory == null) {
            return;
        }

        ProtectionRules rules = ProtectionRules.of(
                (List<String>) Config.INSTANCE.decayProtectedItems.get(),
                (List<String>) Config.INSTANCE.decayProtectedItemPrefixes.get(),
                (List<String>) Config.INSTANCE.decayProtectedEnchantments.get(),
                (List<String>) Config.INSTANCE.decayProtectedNbtStrings.get());

        List<Integer> unprotected = new ArrayList<>();
        List<Integer> guarded = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (rules.isProtected(StackViews.view(stack))) {
                guarded.add(Integer.valueOf(slot));
            } else {
                unprotected.add(Integer.valueOf(slot));
            }
        }

        int victim = DecayVictim.pick(unprotected, guarded,
                Config.INSTANCE.decayProtectedNeverDecay.get().booleanValue(),
                bound -> level.random.nextInt(bound));
        if (victim == DecayVictim.NOTHING) {
            return;
        }

        ItemStack lost = inventory.extractItem(victim, inventory.getStackInSlot(victim).getCount(), false);
        if (lost.isEmpty()) {
            return;
        }

        Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, lost);
        DecayHistory.record(level, pos, grave.getOwnerId(), lost);
    }
}
```

- [ ] **Step 4: Napisz mixin**

```java
package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.decay.GraveDecayService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ovh.corail.tombstone.block.entity.BlockEntityPlayerGrave;

/**
 * Wpina rozklad w wlasny ticker grobu.
 *
 * <p>remap = false, bo serverTick to metoda Tombstone'a, ktorej nazwa nie jest obfuskowana.
 */
@Mixin(BlockEntityPlayerGrave.class)
public class MixinBlockEntityPlayerGrave {

    @Inject(method = "serverTick", at = @At("HEAD"), remap = false)
    private static void tombtweaks$decay(Level level, BlockPos pos, BlockState state,
                                         BlockEntityPlayerGrave grave, CallbackInfo ci) {
        GraveDecayService.tick(level, pos, grave);
    }
}
```

- [ ] **Step 5: Zbuduj**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. Jeśli pada `Scanned 0 target(s)` — sprawdź `javap -p ovh/corail/tombstone/block/entity/BlockEntityPlayerGrave.class`, czy sygnatura `serverTick` się nie zmieniła.

- [ ] **Step 6: Weryfikacja w grze**

Run: `./gradlew runClient`

1. W `run/config/tombtweaks-common.toml` ustaw `[decay] enabled = true`, `startTicks = 100`, `intervalTicks = 40`. Zrestartuj klienta.
2. Sprawdź w logu linię `Mixing MixinBlockEntityPlayerGrave from mixins.tombtweaks.json into ovh.corail.tombstone.block.entity.BlockEntityPlayerGrave`. **Brak tej linii = mixin nie zadziałał, nawet jeśli gra wstała.**
3. Zginij z pełnym ekwipunkiem, stój przy grobie i nie otwieraj go.

Expected: co ~2 sekundy jeden stack wypada z grobu na ziemię.

4. Dodaj `"minecraft:diamond_sword"` do `protectedItems`, powtórz. Miecz musi zostać na końcu, a przy `protectedNeverDecay = true` — zostać na zawsze.
5. Wyloguj się z serwera (single player: wyjdź do menu), wróć — grób ma kontynuować rozkład od miejsca, w którym stanął, bez resetu.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(decay): rozklad grobu na wlasnym tickerze, bezstanowy harmonogram, historia po UUID"
```

---

## Task 13: Feature — cooldowny ksiąg

**Files:**
- Create: `src/main/java/com/spege/tombtweaks/feature/cooldown/BookCooldownService.java`
- Create: `src/main/java/com/spege/tombtweaks/mixin/MixinItemBook.java`
- Create: `src/main/java/com/spege/tombtweaks/mixin/MixinBlockDecorativeGrave.java`
- Create: `src/main/resources/assets/tombtweaks/lang/en_us.json`
- Modify: `src/main/resources/mixins.tombtweaks.json`

- [ ] **Step 1: Dopisz dwa mixiny do `mixins.tombtweaks.json`**

Lista `mixins` ma teraz zawierać wszystkie trzy:

```json
  "mixins": [
    "MixinBlockEntityPlayerGrave",
    "MixinItemBook",
    "MixinBlockDecorativeGrave"
  ],
```

- [ ] **Step 2: Napisz `BookCooldownService`**

```java
package com.spege.tombtweaks.feature.cooldown;

import com.spege.tombtweaks.core.Cooldown;
import com.spege.tombtweaks.core.CooldownRules;
import com.spege.tombtweaks.platform.Config;
import com.spege.tombtweaks.platform.PlayerData;
import com.spege.tombtweaks.platform.StackViews;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Odczyt i zapis cooldownu jednej ksiegi. Zegar scienny, ta sama skala co Tombstone. */
public final class BookCooldownService {

    private static final String PREFIX = "tombtweaks_cooldown_";

    private BookCooldownService() {
    }

    @SuppressWarnings("unchecked")
    private static CooldownRules rules() {
        return CooldownRules.parse((List<String>) Config.INSTANCE.cooldownBooks.get());
    }

    /** @return ile ms zostalo, 0 gdy wolne albo gdy feature jest wylaczony */
    public static long remaining(Player player, ItemStack book) {
        if (!Config.INSTANCE.cooldownEnabled.get()) {
            return 0L;
        }
        String id = StackViews.idOf(book);
        long length = rules().millisFor(id);
        if (length <= 0L) {
            return 0L;
        }
        CompoundTag persisted = PlayerData.persisted(player);
        return Cooldown.remaining(persisted.getLong(PREFIX + id), length, System.currentTimeMillis());
    }

    public static void start(Player player, ItemStack book) {
        if (!Config.INSTANCE.cooldownEnabled.get()) {
            return;
        }
        String id = StackViews.idOf(book);
        if (rules().millisFor(id) <= 0L) {
            return;
        }
        PlayerData.persisted(player).putLong(PREFIX + id, System.currentTimeMillis());
    }

    public static void tellRemaining(Player player, long remainingMillis) {
        long seconds = (remainingMillis + 999L) / 1000L;
        player.displayClientMessage(
                Component.translatable("message.tombtweaks.book_cooldown", seconds), true);
    }
}
```

- [ ] **Step 3: Napisz `MixinItemBook` (blokada)**

```java
package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ovh.corail.tombstone.item.ItemBook;

/**
 * Blokada uzycia ksiegi w trakcie cooldownu.
 *
 * <p>Siedzi na klasie bazowej wszystkich ksiag, wiec dziala niezaleznie od tego, skad
 * ktos ja zawola. remap = false: canEnchant to metoda Tombstone'a.
 */
@Mixin(ItemBook.class)
public class MixinItemBook {

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true, remap = false)
    private void tombtweaks$blockDuringCooldown(Level level, BlockPos pos, Player player,
                                                ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (level == null || level.isClientSide() || player == null) {
            return;
        }
        long remaining = BookCooldownService.remaining(player, stack);
        if (remaining > 0L) {
            BookCooldownService.tellRemaining(player, remaining);
            cir.setReturnValue(Boolean.FALSE);
        }
    }
}
```

- [ ] **Step 4: Napisz `MixinBlockDecorativeGrave` (start cooldownu)**

```java
package com.spege.tombtweaks.mixin;

import com.spege.tombtweaks.feature.cooldown.BookCooldownService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ovh.corail.tombstone.api.capability.ISoulConsumer;
import ovh.corail.tombstone.block.BlockDecorativeGrave;

/**
 * Startuje cooldown dokladnie wtedy, gdy uzycie ksiegi sie powiodlo.
 *
 * <p>ISoulConsumer.setEnchant jest wolane z jednego miejsca w calym Tombstonie - stad
 * jeden redirect obejmuje wszystkie ksiegi.
 *
 * <p>Dwa poziomy remapowania: selektor method = "use" celuje w metode Minecrafta
 * (BlockBehaviour.use), wiec MUSI byc remapowany - i to on jest powodem, dla ktorego
 * refmapy w tym projekcie sa wlaczone. Wewnetrzny @At celuje w czlonka Tombstone'a
 * i dostaje wlasne remap = false.
 *
 * <p>Receiver redirectu to ISoulConsumer, bo wywolanie to invokeinterface na tym
 * interfejsie - nie na konkretnej ksiedze.
 */
@Mixin(BlockDecorativeGrave.class)
public class MixinBlockDecorativeGrave {

    @Redirect(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lovh/corail/tombstone/api/capability/ISoulConsumer;setEnchant("
                           + "Lnet/minecraft/world/level/Level;"
                           + "Lnet/minecraft/core/BlockPos;"
                           + "Lnet/minecraft/server/level/ServerPlayer;"
                           + "Lnet/minecraft/world/item/ItemStack;I)"
                           + "Lovh/corail/tombstone/api/capability/ISoulConsumer$ConsumeResult;",
                    remap = false))
    private ISoulConsumer.ConsumeResult tombtweaks$startCooldown(ISoulConsumer consumer,
                                                                 Level level, BlockPos pos,
                                                                 ServerPlayer player,
                                                                 ItemStack stack, int soulStrength) {
        ISoulConsumer.ConsumeResult result = consumer.setEnchant(level, pos, player, stack, soulStrength);
        if (result != null && result.result() == ISoulConsumer.ConsumeResult.Result.SUCCESS) {
            BookCooldownService.start(player, stack);
        }
        return result;
    }
}
```

- [ ] **Step 5: Napisz plik językowy**

`src/main/resources/assets/tombtweaks/lang/en_us.json`:

```json
{
  "message.tombtweaks.book_cooldown": "This book is still recovering — %s s left."
}
```

- [ ] **Step 6: Zbuduj**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. Jeśli redirect pada z `Scanned 0 target(s)`, sprawdź deskryptor:

```bash
"/c/Program Files/Java/jdk-21.0.11/bin/javap" -p -c -cp libs/tombstone-1.20.1-9.1.4.jar ovh.corail.tombstone.block.BlockDecorativeGrave | grep setEnchant
```

- [ ] **Step 7: Weryfikacja w grze**

Run: `./gradlew runClient`

1. Sprawdź w logu **dwie** linie `Mixing MixinItemBook …` i `Mixing MixinBlockDecorativeGrave …`.
2. Zdobądź Book of Disenchantment i ozdobny grób z duszą (`/give` + tryb kreatywny).
3. Użyj księgi — musi zadziałać.
4. Użyj natychmiast drugi raz — musi odmówić, z komunikatem nad hotbarem i sensowną liczbą sekund.
5. Wyjdź do menu i wróć — cooldown ma trwać dalej, nie zresetować się.
6. Ustaw `books = ["tombstone:book_of_disenchantment;0"]` — cooldown ma zniknąć.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(cooldown): blokada na ItemBook + start na jedynym wywolaniu setEnchant"
```

---

## Task 14: README i wydanie 0.1.0

**Files:**
- Create: `E:\Isuth\tombtweaks-1201\README.md`
- Create: `E:\Isuth\tombtweaks-1201\docs\specs\2026-08-14-tombtweaks-1201-port-design.md` (kopia specu)

- [ ] **Step 1: Skopiuj spec do nowego repo**

```bash
mkdir -p /e/Isuth/tombtweaks-1201/docs/specs
cp /e/Isuth/modDev/docs/superpowers/specs/2026-08-14-tombtweaks-1201-port-design.md /e/Isuth/tombtweaks-1201/docs/specs/
```

- [ ] **Step 2: Napisz `README.md`**

```markdown
# TombTweaks (Minecraft 1.20.1)

Trzy tweaki do Corail Tombstone 9.1.4, każdy niezależnie wyłączalny.

| Feature | Co robi | Domyślnie |
|---|---|---|
| `[restore]` | Odzyskany grób oddaje przedmioty **na te same sloty**, z których zginęły — łącznie z pancerzem i offhandem | włączone |
| `[decay]` | Nieodwiedzony grób powoli gubi zawartość; cztery listy ochrony wyjmują wybrane przedmioty spod tego | **wyłączone** |
| `[cooldown]` | Magiczne księgi dostają cooldown, konfigurowalny per księga | włączone |

Config: `config/tombtweaks-common.toml`.

## Budowanie

```bash
./gradlew build      # jar w build/libs/
./gradlew test       # testy pakietu core na gołej JVM
./gradlew runClient  # klient deweloperski
```

Wymaga JDK 17 — Gradle pobiera go sam przez toolchain.

## Architektura

Trzy warstwy: `core` (zero typów Minecrafta, cała logika decyzyjna, testowana JUnitem),
`platform` (adaptery), `feature` (handlery i trzy mixiny). `core` jest celowo napisany
składnią Javy 8, żeby port na 1.16.5 był kopiuj-wklej.

Pełne uzasadnienie decyzji: `docs/specs/2026-08-14-tombtweaks-1201-port-design.md`.

## Znane ograniczenia

- **Curios nieobsługiwane** w slot restore (przestrzeń slotów 200+ jest zarezerwowana).
  Tombstone ma własne `curioAutoEquip`.
- **`reverse inventory sorting`** — preferencja Tombstone'a per gracz, uruchamiana po naszym
  evencie; gdy jest włączona, odwraca odzyskany układ. Wynik weryfikacji: <UZUPEŁNIĆ w Tasku 11 Step 5>.
- Historia rozkładu dla gracza **offline** trafia na razie tylko do logu serwera.
```

- [ ] **Step 3: Uzupełnij wynik weryfikacji z Taska 11 Step 5**

Wpisz do README, co się dzieje przy włączonym `reverse inventory sorting`. Jeśli okaże się, że psuje restore — **nie naprawiaj tego teraz**, dopisz jako znane ograniczenie. To materiał na osobne zadanie.

- [ ] **Step 4: Pełny przebieg testów i budowa**

Run: `./gradlew clean build`
Expected: `BUILD SUCCESSFUL`, 47 testów PASS, jar w `build/libs/tombtweaks-1.20.1-0.1.0.jar`.

- [ ] **Step 5: Commit i tag**

```bash
git add -A
git commit -m "docs: README + kopia specu; wydanie 0.1.0"
git tag v0.1.0
```

---

## Czego ten plan świadomie NIE robi

Wszystko poniżej jest w specu §8 z uzasadnieniem. Nie dopisuj tego po drodze:

Curios w slot restore, tuning perków, whitelisty efektów, nerf grave_dust, usunięcie recepty na
grave key, patch Curse of Possession, first-kill reward, raider alignment, integracje z Enigmatic
Legacy i EBW Redux, port na 1.16.5, obejście `reverse inventory sorting`.
