# manacore — Plan 1: rdzeń Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zbudować ósmy subprojekt `manacore` z działającą pulą many gracza: capability, atrybut `MAX_MANA`, synchronizacja, regen, HUD, config, API i komenda debugowa — bez żadnej integracji z innymi modami.

**Architecture:** Capability na graczu trzyma dwie liczby (`current`, `progressionBonus`). `maxMana` nie jest przechowywana — jest wartością atrybutu `RangedAttribute` z `setShouldWatch(true)`, dzięki czemu Forge synchronizuje ją do klienta sam, a my synchronizujemy wyłącznie `current`. Cała matematyka bez typów Minecrafta ląduje w pakiecie `core`, żeby dała się przetestować JUnitem — wzorem `commandsuggest`.

**Tech Stack:** Java 8, Forge 1.12.2-14.23.5.2860, ForgeGradle 3, mappings `snapshot/20171003-1.12`, MixinBooter 7.1 (tu nieużywany, ale w zależnościach dla spójności z rodziną), JUnit 4.12 dla pakietu `core`.

**Źródło prawdy:** [spec 2026-08-18-manacore-design.md](../specs/2026-08-18-manacore-design.md). Przy każdej rozbieżności wygrywa spec.

---

## Struktura plików

Wszystko względem nowego katalogu `manacore/`.

| Plik | Odpowiedzialność |
|---|---|
| `build.gradle` | konfiguracja subprojektu, zależności, manifest, `reobfJar` |
| `src/main/java/com/spege/manacore/ManaCoreMod.java` | klasa `@Mod`, cykl init, `@SidedProxy` |
| `.../CommonProxy.java` / `client/ClientProxy.java` | rozdział stron; **każda rejestracja klienta tylko tutaj** |
| `.../core/ManaMath.java` | czysta matematyka, **zero typów Minecrafta** |
| `.../cap/IManaPool.java` | interfejs puli |
| `.../cap/ManaPool.java` | implementacja (dwie liczby) |
| `.../cap/ManaPoolStorage.java` | serializacja NBT |
| `.../cap/ManaPoolProvider.java` | `ICapabilitySerializable` |
| `.../cap/ManaCapabilityHandler.java` | attach, klonowanie przy śmierci, sync na login/dim/respawn |
| `.../attr/ManaAttributes.java` | `MAX_MANA` + rejestracja na encji |
| `.../net/ManaNetwork.java` | kanał + rejestracja pakietów |
| `.../net/PacketManaSync.java` | pakiet pełnej synchronizacji `current` |
| `.../client/ManaClientState.java` | mirror klienta (`@SideOnly(CLIENT)`) |
| `.../client/ManaHudRenderer.java` | pasek HUD (`@SideOnly(CLIENT)`) |
| `.../handler/ManaRegenHandler.java` | regen w `PlayerTickEvent` |
| `.../api/ManaAPI.java` | statyczna fasada dla innych modów |
| `.../config/ManaCoreConfig.java` + `config/categories/*.java` | config, `category = ""` |
| `.../command/CommandMana.java` | komenda debugowa |
| `src/main/resources/{mcmod.info,pack.mcmeta}` | metadane; **`pack.mcmeta` jest obowiązkowy** |
| `src/main/resources/assets/manacore/lang/en_us.lang` | teksty |
| `src/main/resources/assets/manacore/textures/gui/bar_mana.png` | tekstura robocza |
| `src/test/java/com/spege/manacore/core/ManaMathTest.java` | testy JUnit |

**Zasada podziału:** wszystko, co da się policzyć bez Minecrafta, idzie do `core` i ma test. Reszta jest cienką warstwą kleju weryfikowaną w grze.

---

## Task 1: Subprojekt Gradle i szkielet moda

**Files:**
- Modify: `settings.gradle`
- Create: `manacore/build.gradle`
- Create: `manacore/src/main/java/com/spege/manacore/ManaCoreMod.java`
- Create: `manacore/src/main/java/com/spege/manacore/CommonProxy.java`
- Create: `manacore/src/main/java/com/spege/manacore/client/ClientProxy.java`
- Create: `manacore/src/main/resources/mcmod.info`
- Create: `manacore/src/main/resources/pack.mcmeta`
- Create: `manacore/src/main/resources/assets/manacore/lang/en_us.lang`

- [ ] **Step 1: Dopisz subprojekt do `settings.gradle`**

Dopisz na końcu pliku:

```groovy
include 'manacore'
```

> Uwaga: `commandsuggest` jest w repo jako katalog, ale **nie ma go w `settings.gradle`**. To osobny bug — nie naprawiaj go w tym planie, nie mieszaj do commitów tego zadania.

- [ ] **Step 2: Utwórz `manacore/build.gradle`**

```groovy
apply plugin: 'net.minecraftforge.gradle'
apply plugin: 'eclipse'
apply plugin: 'maven-publish'

repositories {
    maven { url = 'https://maven.cleanroommc.com' }
    maven { url = 'https://www.cursemaven.com' }
    mavenCentral()
}

version = '0.1.0'
group = 'com.spege.manacore'
archivesBaseName = 'manacore'

sourceCompatibility = targetCompatibility = compileJava.sourceCompatibility = compileJava.targetCompatibility = '1.8'
tasks.withType(JavaCompile) {
    options.encoding = 'UTF-8'
    options.compilerArgs << "-proc:none"
    options.compilerArgs << "-Xlint:all"
}

minecraft {
    mappings channel: 'snapshot', version: '20171003-1.12'
}

dependencies {
    minecraft 'net.minecraftforge:forge:1.12.2-14.23.5.2860'
    implementation 'zone.rong:mixinbooter:7.1'
    // Pakiet `core` nie zawiera ani jednego typu Minecrafta, więc da sie testowac na goÅ‚ej JVM.
    testImplementation 'junit:junit:4.12'
}

jar {
    manifest {
        attributes([
            "Specification-Title": "Mana Core",
            "Specification-Vendor": "spege",
            "Specification-Version": "${version}",
            "Implementation-Title": project.name,
            "Implementation-Version": "${version}",
            "Implementation-Vendor": "spege",
            "Implementation-Timestamp": new Date().format("yyyy-MM-dd'T'HH:mm:ssZ")
        ])
    }
}

jar.finalizedBy('reobfJar')

publishing {
    publications { mavenJava(MavenPublication) { artifact jar } }
    repositories { maven { url "file:///${rootProject.projectDir}/mcmodsrepo" } }
}
```

> Manifest **nie zawiera** `MixinConfigs` — Plan 1 nie ma ani jednego mixina. Mosty z Planu 2 celują w klasy modów, więc pójdą trasą późną (`ILateMixinLoader`), a nie manifestem.

- [ ] **Step 3: Utwórz `pack.mcmeta`**

`manacore/src/main/resources/pack.mcmeta`:

```json
{
  "pack": {
    "description": "Mana Core resources",
    "pack_format": 3
  }
}
```

> To jest ta pułapka, która ugryzła cztery poprzednie ekstrakcje. Bez tego pliku `assets/manacore/` nigdy nie stanie się domeną zasobów, każdy klucz lang wyrenderuje się jako własna nazwa, a **w żadnym logu nie będzie o tym słowa**.

- [ ] **Step 4: Utwórz `mcmod.info`**

`manacore/src/main/resources/mcmod.info`:

```json
[
  {
    "modid": "manacore",
    "name": "Mana Core",
    "description": "Unified player mana pool with bridges to Electroblob's Wizardry and Trinkets and Baubles.",
    "version": "0.1.0",
    "mcversion": "1.12.2",
    "authorList": ["Isuthhh"],
    "dependencies": []
  }
]
```

> Trzy szczegóły, każdy zgodny z pozostałymi sześcioma `mcmod.info` w repo: **`authorList` to `Isuthhh`**, nie `spege` — `spege` jest wyłącznie `Vendor` w manifeście jara, czyli identyfikatorem build-owym, a nie nazwą autora widoczną na liście modów gracza. Opis jest **po angielsku**, bo to pole trafia do gracza. Klucz **`dependencies` musi istnieć**, choćby pusty.

- [ ] **Step 5: Utwórz `en_us.lang`**

`manacore/src/main/resources/assets/manacore/lang/en_us.lang`:

```
attribute.name.manacore.maxMana=Max Mana
manacore.message.not_enough=Not enough mana
manacore.command.usage=/mana <get|set|add|setmax> [amount] [player]
manacore.command.report=Mana: %s / %s
```

- [ ] **Step 6: Utwórz proxy**

`manacore/src/main/java/com/spege/manacore/CommonProxy.java`:

```java
package com.spege.manacore;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
    }
}
```

`manacore/src/main/java/com/spege/manacore/client/ClientProxy.java`:

```java
package com.spege.manacore.client;

import com.spege.manacore.CommonProxy;

import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }
}
```

- [ ] **Step 7: Utwórz klasę `@Mod`**

`manacore/src/main/java/com/spege/manacore/ManaCoreMod.java`:

```java
package com.spege.manacore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ManaCoreMod.MODID, name = ManaCoreMod.NAME, version = ManaCoreMod.VERSION,
        acceptableRemoteVersions = "*")
public class ManaCoreMod {

    public static final String MODID = "manacore";
    public static final String NAME = "Mana Core";
    /** MUSI być ręcznie zsynchronizowana z `version` w build.gradle - manifest nie jest widoczny dla @Mod. */
    public static final String VERSION = "0.1.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.spege.manacore.client.ClientProxy",
            serverSide = "com.spege.manacore.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        LOGGER.info("[ManaCore] preInit done");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
```

> Klasa `@Mod` **nie może** nazwać żadnego typu z `net.minecraft.client` ani `fml.client` — weryfikator rozwiązuje je przy ładowaniu klasy, zanim jakikolwiek `if (side == CLIENT)` zdąży się wykonać.
>
> `acceptableRemoteVersions = "*"`, a **nie** `acceptedMinecraftVersions` — tak robi pięć z sześciu pozostałych modów w repo. Ogranicznik wersji Minecrafta nic tu nie wnosi, bo wymusza go już zależność Forge; realnie boli handshake odrzucający połączenie, gdy serwer ma nowszą wersję moda niż klient — a to jest normalny stan w pętli build → kopiuj jar do `mods/`.

- [ ] **Step 8: Zbuduj i sprawdź**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`, powstaje `manacore/build/libs/manacore-0.1.0.jar`.

- [ ] **Step 9: Sprawdź, że klasa `@Mod` nie wciąga typów klienckich**

```bash
javap -v -p manacore/build/classes/java/main/com/spege/manacore/ManaCoreMod.class | grep -E "minecraft/client|fml/client"
```

Oczekiwane: **pusty wynik**. Jakikolwiek wynik = crash na dedykowanym serwerze.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle manacore/build.gradle manacore/src/main/java manacore/src/main/resources
git commit -m "feat(manacore): subprojekt gradle i szkielet moda"
```

---

## Task 2: Czysta matematyka puli (TDD)

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/core/ManaMath.java`
- Test: `manacore/src/test/java/com/spege/manacore/core/ManaMathTest.java`

**Dlaczego osobny pakiet:** to jedyna część rdzenia, którą da się przetestować bez uruchamiania Minecrafta. Reguła: **ani jeden import `net.minecraft.*` w `core`**.

- [ ] **Step 1: Napisz testy (mają nie przechodzić)**

`manacore/src/test/java/com/spege/manacore/core/ManaMathTest.java`:

```java
package com.spege.manacore.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ManaMathTest {

    private static final double EPS = 1.0e-9D;

    @Test
    public void clampTrzymaWartoscWZakresie() {
        assertEquals(5.0D, ManaMath.clamp(5.0D, 0.0D, 10.0D), EPS);
        assertEquals(0.0D, ManaMath.clamp(-3.0D, 0.0D, 10.0D), EPS);
        assertEquals(10.0D, ManaMath.clamp(42.0D, 0.0D, 10.0D), EPS);
    }

    @Test
    public void clampZwracaMinGdyZakresJestOdwrocony() {
        assertEquals(10.0D, ManaMath.clamp(5.0D, 10.0D, 0.0D), EPS);
    }

    @Test
    public void wydatekNieSchodziPonizejZera() {
        assertEquals(0.0D, ManaMath.afterSpend(3.0D, 10.0D), EPS);
        assertEquals(7.0D, ManaMath.afterSpend(10.0D, 3.0D), EPS);
    }

    @Test
    public void regenNiePrzekraczaMaksimum() {
        assertEquals(10.0D, ManaMath.afterRegen(9.5D, 10.0D, 2.0D), EPS);
        assertEquals(6.0D, ManaMath.afterRegen(5.0D, 10.0D, 1.0D), EPS);
    }

    @Test
    public void regenNieObnizaPuliPowyzejMaksimum() {
        // Po zdjeciu bauble'a `current` moze przekraczac nowe `max`. Regen nie ma tego obcinac.
        assertEquals(15.0D, ManaMath.afterRegen(15.0D, 10.0D, 2.0D), EPS);
    }

    @Test
    public void progresjaZatrzymujeSieNaSuficie() {
        assertEquals(4.0D, ManaMath.afterProgressionGain(3.5D, 4.0D, 1.0D), EPS);
        assertEquals(4.0D, ManaMath.afterProgressionGain(4.0D, 4.0D, 1.0D), EPS);
        assertEquals(1.5D, ManaMath.afterProgressionGain(1.0D, 4.0D, 0.5D), EPS);
    }

    @Test
    public void progresjaNieObnizaJuzZbankowanejWartosci() {
        // Administrator obnizyl sufit w configu po tym, jak gracz nabil progresje.
        // Dorobek ma zostac nietkniety, a nie zostac obciety w dol.
        assertEquals(60.0D, ManaMath.afterProgressionGain(60.0D, 50.0D, 5.0D), EPS);
        assertEquals(50.0D, ManaMath.afterProgressionGain(50.0D, 50.0D, 5.0D), EPS);
    }

    @Test
    public void regenNaTickPrzeliczaSekundyNaTicki() {
        // 5 many co 2 sekundy = 5 / 40 ticka
        assertEquals(0.125D, ManaMath.regenPerTick(5.0D, 2.0D), EPS);
    }

    @Test
    public void regenNaTickZwracaZeroDlaNiepoprawnejCzestotliwosci() {
        assertEquals(0.0D, ManaMath.regenPerTick(5.0D, 0.0D), EPS);
        assertEquals(0.0D, ManaMath.regenPerTick(5.0D, -1.0D), EPS);
    }
}
```

- [ ] **Step 2: Uruchom testy i potwierdź, że NIE przechodzą**

```bash
./gradlew :manacore:test
```

Oczekiwane: `FAILED` — kompilacja testów pada na braku klasy `ManaMath`.

- [ ] **Step 3: Napisz minimalną implementację**

`manacore/src/main/java/com/spege/manacore/core/ManaMath.java`:

```java
package com.spege.manacore.core;

/**
 * Czysta matematyka puli many. ZERO typów Minecrafta - dzięki temu ten pakiet
 * jest jedyną częścią moda, którą da się przetestować JUnitem.
 */
public final class ManaMath {

    public static final int TICKS_PER_SECOND = 20;

    private ManaMath() {
    }

    public static double clamp(double value, double min, double max) {
        if (min > max) {
            return min;
        }
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    public static double afterSpend(double current, double cost) {
        double result = current - cost;
        return result < 0.0D ? 0.0D : result;
    }

    /**
     * Regen nigdy nie obniża puli: gdy `current` przekracza `max` (np. po zdjęciu bauble'a),
     * wartość zostaje bez zmian zamiast zostać obcięta.
     */
    public static double afterRegen(double current, double max, double amount) {
        if (current >= max) {
            return current;
        }
        double result = current + amount;
        return result > max ? max : result;
    }

    /**
     * Nigdy nie obniża wartości: przy `current >= cap` zwraca `current` bez zmian.
     * Chroni zbankowaną progresję przed obcięciem, gdy administrator obniży sufit w configu.
     * Kolejność argumentów celowo taka sama jak w `afterRegen`: (current, sufit, delta).
     */
    public static double afterProgressionGain(double current, double cap, double gain) {
        if (current >= cap) {
            return current;
        }
        double result = current + gain;
        return result > cap ? cap : result;
    }

    public static double regenPerTick(double amountPerCycle, double cycleSeconds) {
        if (cycleSeconds <= 0.0D) {
            return 0.0D;
        }
        return amountPerCycle / (cycleSeconds * TICKS_PER_SECOND);
    }
}
```

- [ ] **Step 4: Uruchom testy i potwierdź, że przechodzą**

```bash
./gradlew :manacore:test
```

Oczekiwane: `BUILD SUCCESSFUL`, 8 testów zielonych.

- [ ] **Step 5: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/core manacore/src/test
git commit -m "feat(manacore): czysta matematyka puli many z testami"
```

---

## Task 3: Capability puli many

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/cap/IManaPool.java`
- Create: `manacore/src/main/java/com/spege/manacore/cap/ManaPool.java`
- Create: `manacore/src/main/java/com/spege/manacore/cap/ManaPoolStorage.java`
- Create: `manacore/src/main/java/com/spege/manacore/cap/ManaPoolProvider.java`
- Create: `manacore/src/main/java/com/spege/manacore/cap/ManaCapabilities.java`
- Modify: `manacore/src/main/java/com/spege/manacore/ManaCoreMod.java`

- [ ] **Step 1: Interfejs**

`.../cap/IManaPool.java`:

```java
package com.spege.manacore.cap;

public interface IManaPool {

    double getCurrent();

    /** Podnosi flagę `dirty`, ale tylko gdy nowa wartość faktycznie różni się od dotychczasowej. */
    void setCurrent(double value);

    double getProgressionBonus();

    /** Podnosi flagę `dirty`, ale tylko gdy nowa wartość faktycznie różni się od dotychczasowej. */
    void setProgressionBonus(double value);

    /**
     * Flagę podnoszą settery przy każdej realnej zmianie wartości.
     * Warstwa sieci wyłącznie ją zeruje, po wysłaniu synchronizacji do klienta.
     */
    boolean isDirty();

    void setDirty(boolean dirty);
}
```

- [ ] **Step 2: Implementacja**

`.../cap/ManaPool.java`:

```java
package com.spege.manacore.cap;

public class ManaPool implements IManaPool {

    private double current;
    private double progressionBonus;
    private boolean dirty;

    @Override
    public double getCurrent() {
        return this.current;
    }

    @Override
    public void setCurrent(double value) {
        if (this.current != value) {
            this.current = value;
            this.dirty = true;
        }
    }

    @Override
    public double getProgressionBonus() {
        return this.progressionBonus;
    }

    @Override
    public void setProgressionBonus(double value) {
        if (this.progressionBonus != value) {
            this.progressionBonus = value;
            this.dirty = true;
        }
    }

    @Override
    public boolean isDirty() {
        return this.dirty;
    }

    @Override
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}
```

- [ ] **Step 3: Serializacja NBT**

`.../cap/ManaPoolStorage.java`:

```java
package com.spege.manacore.cap;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;

public class ManaPoolStorage implements Capability.IStorage<IManaPool> {

    static final String KEY_CURRENT = "current";
    static final String KEY_PROGRESSION = "progression";

    @Override
    public NBTBase writeNBT(Capability<IManaPool> capability, IManaPool instance, EnumFacing side) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble(KEY_CURRENT, instance.getCurrent());
        tag.setDouble(KEY_PROGRESSION, instance.getProgressionBonus());
        return tag;
    }

    @Override
    public void readNBT(Capability<IManaPool> capability, IManaPool instance, EnumFacing side, NBTBase nbt) {
        if (!(nbt instanceof NBTTagCompound)) {
            return;
        }
        NBTTagCompound tag = (NBTTagCompound) nbt;
        instance.setCurrent(tag.getDouble(KEY_CURRENT));
        instance.setProgressionBonus(tag.getDouble(KEY_PROGRESSION));
    }
}
```

- [ ] **Step 4: Provider**

`.../cap/ManaPoolProvider.java`:

```java
package com.spege.manacore.cap;

import javax.annotation.Nullable;

import com.spege.manacore.ManaCoreMod;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

public class ManaPoolProvider implements ICapabilitySerializable<NBTTagCompound> {

    public static final ResourceLocation KEY = new ResourceLocation(ManaCoreMod.MODID, "mana_pool");

    private final IManaPool instance = new ManaPool();

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == ManaCapabilities.MANA_POOL;
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == ManaCapabilities.MANA_POOL) {
            return ManaCapabilities.MANA_POOL.cast(this.instance);
        }
        return null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return (NBTTagCompound) ManaCapabilities.MANA_POOL.getStorage()
                .writeNBT(ManaCapabilities.MANA_POOL, this.instance, null);
    }

    /**
     * Odczyt idzie przez settery, więc świeżo wczytany gracz zostaje oznaczony jako `dirty`.
     * To jest celowe: po wczytaniu z dysku klient i tak wymaga synchronizacji.
     */
    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        ManaCapabilities.MANA_POOL.getStorage()
                .readNBT(ManaCapabilities.MANA_POOL, this.instance, null, nbt);
    }
}
```

> Bez `getInstance()` — dostęp do puli idzie wyłącznie przez `ManaCapabilities.get(player)`, czyli oficjalne API capability. Dodatkowy getter na providerze byłby drugą, równoległą drogą do tych samych danych i martwym kodem.

- [ ] **Step 5: Uchwyt capability i rejestracja**

`.../cap/ManaCapabilities.java`:

```java
package com.spege.manacore.cap;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

public final class ManaCapabilities {

    @CapabilityInject(IManaPool.class)
    public static Capability<IManaPool> MANA_POOL = null;

    private ManaCapabilities() {
    }

    public static void register() {
        CapabilityManager.INSTANCE.register(IManaPool.class, new ManaPoolStorage(), ManaPool::new);
    }

    @Nullable
    public static IManaPool get(@Nullable EntityPlayer player) {
        if (player == null || MANA_POOL == null) {
            return null;
        }
        return player.getCapability(MANA_POOL, null);
    }
}
```

> `ManaPool::new` to referencja do konstruktora, nie lambda w mixinie — jest legalna, bo to zwykła klasa.

- [ ] **Step 6: Zawołaj rejestrację z `preInit`**

W `ManaCoreMod.preInit`, przed `proxy.preInit(event)`, dodaj:

```java
        com.spege.manacore.cap.ManaCapabilities.register();
```

- [ ] **Step 7: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore
git commit -m "feat(manacore): capability puli many z serializacja NBT"
```

---

## Task 4: Atrybut `MAX_MANA`

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/attr/ManaAttributes.java`
- Modify: `manacore/src/main/java/com/spege/manacore/ManaCoreMod.java`

- [ ] **Step 1: Klasa atrybutu**

`.../attr/ManaAttributes.java`:

```java
package com.spege.manacore.attr;

import java.util.UUID;

import javax.annotation.Nullable;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
public final class ManaAttributes {

    /**
     * setShouldWatch(true) sprawia, ze Forge sam synchronizuje wartosc atrybutu do klienta.
     * Dzieki temu sami synchronizujemy WYLACZNIE `current` (patrz Task 6).
     */
    public static final IAttribute MAX_MANA = new RangedAttribute(
            (IAttribute) null, "manacore.maxMana", 100.0D, 0.0D, 1.0E7D)
            .setDescription("Max Mana")
            .setShouldWatch(true);

    /**
     * NIE ZMIENIAJ TEJ STALEJ. Modyfikator jest identyfikowany wylacznie po tym UUID,
     * a wanilla serializuje AttributeMap RAZEM z modyfikatorami do NBT gracza.
     * Zmiana tej wartosci nie usunie starego modyfikatora z istniejacych swiatow -
     * zostanie osierocony i dalej doliczany, a nowy kod dolozy drugi ze swiezym UUID.
     * Skutek: trwale podwojony bonus, nie do naprawienia bez ingerencji w zapis.
     */
    private static final UUID PROGRESSION_MODIFIER_ID =
            UUID.fromString("6b7a1d54-3f6c-4a0e-9a1a-2f9c5b8e7d10");
    private static final String PROGRESSION_MODIFIER_NAME = "manacore.progression";

    private ManaAttributes() {
    }

    @SubscribeEvent
    public static void onEntityConstructing(EntityEvent.EntityConstructing event) {
        if (!(event.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntity();
        if (player.getAttributeMap().getAttributeInstance(MAX_MANA) == null) {
            player.getAttributeMap().registerAttribute(MAX_MANA);
        }
    }

    public static double getMaxMana(@Nullable EntityPlayer player) {
        if (player == null) {
            return 0.0D;
        }
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        return instance == null ? 0.0D : instance.getAttributeValue();
    }

    /**
     * Przelicza modyfikator progresji na podstawie zapisanej w capability wartosci.
     * Bezpieczna do wolania z dowolnej strony - po stronie klienta nie robi nic.
     */
    public static void refreshProgressionModifier(@Nullable EntityPlayer player) {
        if (player == null || player.world.isRemote) {
            return;
        }
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        IManaPool pool = ManaCapabilities.get(player);
        if (instance == null || pool == null) {
            return;
        }

        AttributeModifier existing = instance.getModifier(PROGRESSION_MODIFIER_ID);
        if (existing != null) {
            instance.removeModifier(existing);
        }

        double bonus = pool.getProgressionBonus();
        if (bonus > 0.0D) {
            instance.applyModifier(new AttributeModifier(
                    PROGRESSION_MODIFIER_ID, PROGRESSION_MODIFIER_NAME, bonus, 0));
        }
    }
}
```

> Operacja `0` w `AttributeModifier` to dodawanie płaskiej wartości. Przyszłe źródła procentowe (faza 6) użyją `1` lub `2` — dlatego `ManaAPI.addMaxModifier` przyjmuje `operation` jako parametr zamiast zaszywać zero.

- [ ] **Step 2: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`, zero ostrzeżeń `-Xlint` o nieużywanych importach.

- [ ] **Step 3: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/attr
git commit -m "feat(manacore): atrybut MAX_MANA z modyfikatorem progresji"
```

---

## Task 5: Config

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/config/ManaCoreConfig.java`
- Create: `manacore/src/main/java/com/spege/manacore/config/categories/PoolCategory.java`
- Create: `manacore/src/main/java/com/spege/manacore/config/categories/RegenCategory.java`
- Create: `manacore/src/main/java/com/spege/manacore/config/categories/HudCategory.java`

- [ ] **Step 1: Kategorie**

`.../config/categories/PoolCategory.java`:

```java
package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class PoolCategory {

    @Config.Comment({"Bazowa maksymalna mana gracza, zanim doliczymy jakiekolwiek modyfikatory.",
            "Zmiana obowiazuje przy nastepnym wejsciu gracza do swiata, nie natychmiast."})
    @Config.RangeDouble(min = 1.0D, max = 1.0E6D)
    public double baseMaxMana = 100.0D;

    @Config.Comment({"Twardy sufit maksymalnej many po zsumowaniu wszystkich zrodel.",
            "JESZCZE NIEAKTYWNE - zostanie podlaczone w pozniejszym zadaniu.",
            "Sprzezone z baseMaxMana: baza wyzsza niz sufit oznacza pule od razu przycinana."})
    @Config.RangeDouble(min = 1.0D, max = 1.0E7D)
    public double hardCap = 2000.0D;

    @Config.Comment("Ile many doliczamy do trwalej progresji za jeden udany czar. Celowo symboliczne.")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double progressionPerCast = 0.05D;

    @Config.Comment("Sufit trwalej progresji z samego rzucania czarow.")
    @Config.RangeDouble(min = 0.0D, max = 1.0E6D)
    public double progressionCap = 50.0D;

    @Config.Comment("Czy `current` ma sie zerowac przy smierci. `progressionBonus` przezywa zawsze.")
    public boolean resetCurrentOnDeath = true;
}
```

`.../config/categories/RegenCategory.java`:

```java
package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class RegenCategory {

    @Config.Comment("Ile many regeneruje sie w jednym cyklu.")
    @Config.RangeDouble(min = 0.0D, max = 1.0E5D)
    public double amountPerCycle = 1.0D;

    @Config.Comment("Dlugosc cyklu regeneracji w sekundach.")
    @Config.RangeDouble(min = 0.05D, max = 600.0D)
    public double cycleSeconds = 1.0D;
}
```

`.../config/categories/HudCategory.java`:

```java
package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class HudCategory {

    @Config.Comment("Czy rysowac pasek many.")
    public boolean showBar = true;

    @Config.Comment("Czy pisac liczbe obok paska.")
    public boolean showNumber = true;

    @Config.Comment("Przesuniecie paska w poziomie, w pikselach GUI.")
    @Config.RangeInt(min = -1000, max = 1000)
    public int offsetX = 0;

    @Config.Comment("Przesuniecie paska w pionie, w pikselach GUI.")
    @Config.RangeInt(min = -1000, max = 1000)
    public int offsetY = 0;
}
```

- [ ] **Step 2: Root configu**

`.../config/ManaCoreConfig.java`:

```java
package com.spege.manacore.config;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.config.categories.HudCategory;
import com.spege.manacore.config.categories.PoolCategory;
import com.spege.manacore.config.categories.RegenCategory;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * category = "" jest OBOWIAZKOWE, bo wszystkie pola sa obiektami kategorii.
 * Pominiecie go wrzuca kategorie pod `general.*` i po cichu ignoruje wstepnie ustawione wartosci.
 * Odwrotnie: gdyby ktores pole bylo wartoscia prosta, tablica lub mapa, `""` bylby twardym crashem
 * w ConfigManager.sync ("An empty category may not contain anything but objects...").
 */
@Config(modid = ManaCoreMod.MODID, name = ManaCoreMod.MODID, category = "")
public class ManaCoreConfig {

    public static PoolCategory pool = new PoolCategory();
    public static RegenCategory regen = new RegenCategory();
    public static HudCategory hud = new HudCategory();

    @Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
    public static class EventHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (ManaCoreMod.MODID.equals(event.getModID())) {
                ConfigManager.sync(ManaCoreMod.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
```

> `ConfigChangedEvent` mieszka w pakiecie `fml.client`, ale ładuje się na serwerze bez problemu — nie dodawaj tu `@SideOnly`.

- [ ] **Step 3: Podłącz bazową wartość atrybutu — i to NIE w `onEntityConstructing`**

🚨 **Ustawienie bazy przy konstrukcji encji nie działa i wygląda, jakby działało.** Wanilla serializuje **wszystkie** zarejestrowane instancje atrybutów do tagu `Attributes` w NBT gracza (`EntityLivingBase.writeEntityToNBT` → `SharedMonsterAttributes.writeBaseAttributeMapToNBT`) i przywraca z niego `Base` przy wczytaniu (`readEntityFromNBT` → `SharedMonsterAttributes.setAttributeModifiers` → `setBaseValue`). Nasza wartość z configu zostaje więc nadpisana zapisem sprzed zmiany, chwilę po tym, jak ją ustawimy. Efekt: zmiana `baseMaxMana` **nigdy** nie dociera do postaci, która choć raz się zalogowała — także po restarcie serwera — i nie zostawia śladu w logu.

Dlatego bazę ustawia osobny handler, **bezwarunkowo, po wczytaniu gracza**. `onEntityConstructing` nadal tylko rejestruje atrybut (bez tego `getEntityAttribute` zwróci `null`).

```java
    /**
     * Wanilla serializuje baze atrybutu do NBT gracza i przywraca ja przy wczytaniu, wiec
     * wartosc ustawiona w EntityConstructing zostaje nadpisana zapisem sprzed zmiany configu.
     * Bez tego handlera zmiana `baseMaxMana` nie dotarlaby NIGDY do istniejacej postaci.
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntity();
        IAttributeInstance instance = player.getEntityAttribute(MAX_MANA);
        if (instance != null) {
            instance.setBaseValue(ManaCoreConfig.pool.baseMaxMana);
        }
    }
```

`EntityJoinWorldEvent` odpala się przy logowaniu, respawnie i zmianie wymiaru, czyli w każdym momencie, w którym gracz wchodzi do świata. To także powód, dla którego `baseMaxMana` **nie** ma `@Config.RequiresMcRestart` — restart nie jest tu potrzebny ani wystarczający, liczy się przelogowanie.

- [ ] **Step 4: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`, bez nowych ostrzeżeń `-Xlint`, 10 testów `ManaMathTest` nadal zielonych.

> 🚨 **Weryfikacja w grze jest odłożona do Tasku 11 — dev-runtime jest obecnie zepsuty i nie z naszej winy.** `insanetweaks/build.gradle` deobfuskuje cały `fileTree` z `libs/`, wykluczając tylko trzy jary, a w `libs/` leżą **dwie wersje Tombstone** (`4.7.6` i `4.8.0`). Dwa mody o tym samym modid to gwarantowany crash przy starcie. Naprawa to jedno dodatkowe wykluczenie w `insanetweaks/build.gradle` albo usunięcie zbędnego jara — do rozstrzygnięcia osobno, bo dotyka cudzej, niezacommitowanej pracy.
>
> Czego oczekujemy po pierwszym udanym uruchomieniu: `run/config/manacore.cfg` z **trzema kategoriami na poziomie głównym** (`pool`, `regen`, `hud`), a **nie** pod `general`. Kategorie pod `general` = `category = ""` zostało zgubione i wstępnie ustawione wartości są po cichu ignorowane.

- [ ] **Step 5: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/config manacore/src/main/java/com/spege/manacore/attr
git commit -m "feat(manacore): config z kategoriami pool/regen/hud"
```

---

## Task 6: Sieć — synchronizacja `current`

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/net/ManaNetwork.java`
- Create: `manacore/src/main/java/com/spege/manacore/net/PacketManaSync.java`
- Create: `manacore/src/main/java/com/spege/manacore/net/ManaSyncClient.java`
- Create: `manacore/src/main/java/com/spege/manacore/client/ManaClientState.java`
- Modify: `manacore/src/main/java/com/spege/manacore/ManaCoreMod.java`

**Uwaga o stronach:** `maxMana` NIE jest synchronizowana tym kanałem — załatwia to `setShouldWatch(true)` na atrybucie. Ten pakiet wozi wyłącznie `current`.

- [ ] **Step 1: Mirror klienta**

`.../client/ManaClientState.java`:

```java
package com.spege.manacore.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Lustro `current` wylacznie na potrzeby HUD-u. Nigdy nie jest zrodlem prawdy. */
@SideOnly(Side.CLIENT)
public final class ManaClientState {

    private static double current;

    private ManaClientState() {
    }

    public static double getCurrent() {
        return current;
    }

    public static void setCurrent(double value) {
        current = value;
    }
}
```

- [ ] **Step 2: Pakiet**

`.../net/PacketManaSync.java`:

```java
package com.spege.manacore.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

public class PacketManaSync implements IMessage {

    private double current;

    public PacketManaSync() {
    }

    public PacketManaSync(double current) {
        this.current = current;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.current = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(this.current);
    }

    /**
     * Klasa handlera NIE MOZE nosic @SideOnly: registerMessage wola newInstance() po OBU stronach,
     * a trailing Side wybiera tylko, ktora strona PRZETWARZA wiadomosc. Praca klienta siedzi
     * w osobnej klasie wolanej jednym invokestatic.
     */
    public static class Handler implements IMessageHandler<PacketManaSync, IMessage> {

        @Override
        public IMessage onMessage(PacketManaSync message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            ManaSyncClient.apply(message.current);
            return null;
        }
    }
}
```

`.../net/ManaSyncClient.java`:

```java
package com.spege.manacore.net;

import com.spege.manacore.client.ManaClientState;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ManaSyncClient {

    private ManaSyncClient() {
    }

    public static void apply(final double current) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ManaClientState.setCurrent(current);
            }
        });
    }
}
```

- [ ] **Step 3: Kanał**

`.../net/ManaNetwork.java`:

```java
package com.spege.manacore.net;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class ManaNetwork {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(ManaCoreMod.MODID);

    private ManaNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(PacketManaSync.Handler.class, PacketManaSync.class, 0, Side.CLIENT);
    }

    public static void sync(EntityPlayerMP player) {
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        CHANNEL.sendTo(new PacketManaSync(pool.getCurrent()), player);
        pool.setDirty(false);
    }
}
```

- [ ] **Step 4: Zawołaj rejestrację z `preInit`**

W `ManaCoreMod.preInit`, po rejestracji capability:

```java
        com.spege.manacore.net.ManaNetwork.register();
```

- [ ] **Step 5: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Potwierdź, że handler pakietu nie jest klasowo klienckim**

```bash
javap -v -p "manacore/build/classes/java/main/com/spege/manacore/net/PacketManaSync\$Handler.class" | grep -i "SideOnly"
```

Oczekiwane: **pusty wynik**. Adnotacja na tej klasie = crash rejestracji na serwerze.

- [ ] **Step 7: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/net manacore/src/main/java/com/spege/manacore/client manacore/src/main/java/com/spege/manacore/ManaCoreMod.java
git commit -m "feat(manacore): kanal sieciowy i synchronizacja current"
```

---

## Task 7: Attach, persystencja i cykl życia gracza

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/cap/ManaCapabilityHandler.java`

- [ ] **Step 1: Handler cyklu życia**

`.../cap/ManaCapabilityHandler.java`:

```java
package com.spege.manacore.cap;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.attr.ManaAttributes;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.net.ManaNetwork;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;

@Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
public final class ManaCapabilityHandler {

    private ManaCapabilityHandler() {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(ManaPoolProvider.KEY, new ManaPoolProvider());
        }
    }

    /**
     * Klonowanie przy smierci i przy przejsciu przez End. `progressionBonus` przezywa ZAWSZE -
     * trwaly dorobek nie moze przepasc przez jeden zgon. `current` zeruje sie wedlug configu,
     * ale tylko przy prawdziwej smierci (wasDeath), nie przy powrocie z Endu.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        IManaPool oldPool = ManaCapabilities.get(event.getOriginal());
        IManaPool newPool = ManaCapabilities.get(event.getEntityPlayer());
        if (oldPool == null || newPool == null) {
            return;
        }

        newPool.setProgressionBonus(oldPool.getProgressionBonus());

        if (event.isWasDeath() && ManaCoreConfig.pool.resetCurrentOnDeath) {
            newPool.setCurrent(0.0D);
        } else {
            newPool.setCurrent(oldPool.getCurrent());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerLoggedInEvent event) {
        refreshAndSync(event.player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerRespawnEvent event) {
        refreshAndSync(event.player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerChangedDimensionEvent event) {
        refreshAndSync(event.player);
    }

    private static void refreshAndSync(EntityPlayer player) {
        if (player.world.isRemote || !(player instanceof EntityPlayerMP)) {
            return;
        }
        ManaAttributes.refreshProgressionModifier(player);
        ManaNetwork.sync((EntityPlayerMP) player);
    }
}
```

- [ ] **Step 2: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/cap/ManaCapabilityHandler.java
git commit -m "feat(manacore): attach capability i cykl zycia gracza"
```

---

## Task 8: Regeneracja

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/handler/ManaRegenHandler.java`

- [ ] **Step 1: Handler regenu**

`.../handler/ManaRegenHandler.java`:

```java
package com.spege.manacore.handler;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.attr.ManaAttributes;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.ManaMath;
import com.spege.manacore.net.ManaNetwork;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid = ManaCoreMod.MODID)
public final class ManaRegenHandler {

    /** Jak czesto wolno wyslac pakiet synchronizacji. Log i siec nie sa darmowe na watku serwera. */
    private static final int SYNC_INTERVAL_TICKS = 10;

    private ManaRegenHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world.isRemote || !(player instanceof EntityPlayerMP)) {
            return;
        }

        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }

        double perTick = ManaMath.regenPerTick(
                ManaCoreConfig.regen.amountPerCycle, ManaCoreConfig.regen.cycleSeconds);
        if (perTick > 0.0D) {
            double max = ManaAttributes.getMaxMana(player);
            pool.setCurrent(ManaMath.afterRegen(pool.getCurrent(), max, perTick));
        }

        if (pool.isDirty() && player.ticksExisted % SYNC_INTERVAL_TICKS == 0) {
            ManaNetwork.sync((EntityPlayerMP) player);
        }
    }
}
```

- [ ] **Step 2: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/handler
git commit -m "feat(manacore): regeneracja many z throttlowana synchronizacja"
```

---

## Task 9: HUD

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/client/ManaHudRenderer.java`
- Create: `manacore/src/main/resources/assets/manacore/textures/gui/bar_mana.png`
- Modify: `manacore/src/main/java/com/spege/manacore/client/ClientProxy.java`

- [ ] **Step 1: Skopiuj roboczą teksturę z player_mana**

```bash
unzip -o -j libs/player_mana-1.2.1.jar "assets/player_mana/textures/gui/bar_mana.png" -d manacore/src/main/resources/assets/manacore/textures/gui/
```

Oczekiwane: powstaje `manacore/src/main/resources/assets/manacore/textures/gui/bar_mana.png` (643 bajty).

> Tekstura jest **robocza** — praca jest prywatna. Przed jakąkolwiek publikacją trzeba ją zastąpić własną.

- [ ] **Step 2: Renderer HUD**

`.../client/ManaHudRenderer.java`:

```java
package com.spege.manacore.client;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.attr.ManaAttributes;
import com.spege.manacore.config.ManaCoreConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ManaHudRenderer {

    private static final ResourceLocation BAR =
            new ResourceLocation(ManaCoreMod.MODID, "textures/gui/bar_mana.png");

    private static final int BAR_WIDTH = 81;
    private static final int BAR_HEIGHT = 9;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        if (!ManaCoreConfig.hud.showBar) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || mc.gameSettings.showDebugInfo) {
            return;
        }

        double max = ManaAttributes.getMaxMana(player);
        if (max <= 0.0D) {
            return;
        }
        double current = ManaClientState.getCurrent();
        double fraction = current / max;
        if (fraction < 0.0D) {
            fraction = 0.0D;
        } else if (fraction > 1.0D) {
            fraction = 1.0D;
        }

        ScaledResolution res = new ScaledResolution(mc);
        int x = res.getScaledWidth() / 2 - BAR_WIDTH / 2 + ManaCoreConfig.hud.offsetX;
        int y = res.getScaledHeight() - 50 + ManaCoreConfig.hud.offsetY;

        mc.getTextureManager().bindTexture(BAR);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT * 2);
        int filled = (int) Math.round(BAR_WIDTH * fraction);
        if (filled > 0) {
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, BAR_HEIGHT, filled, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT * 2);
        }

        if (ManaCoreConfig.hud.showNumber) {
            String text = ((int) Math.floor(current)) + " / " + ((int) Math.floor(max));
            int textX = x + BAR_WIDTH / 2 - mc.fontRenderer.getStringWidth(text) / 2;
            mc.fontRenderer.drawStringWithShadow(text, textX, y - 10, 0x55AAFF);
        }
    }
}
```

- [ ] **Step 3: Zarejestruj renderer WYŁĄCZNIE z `ClientProxy`**

`ClientProxy.preInit` w całości:

```java
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ManaHudRenderer());
    }
```

Dodaj import `com.spege.manacore.client.ManaHudRenderer` nie jest potrzebny — klasa jest w tym samym pakiecie.

> `ManaHudRenderer` ma **klasowe** `@SideOnly(Side.CLIENT)`, więc `new ManaHudRenderer()` jest fatalne na serwerze. Jedyne bezpieczne miejsce to `ClientProxy`, którego serwer nigdy nie ładuje.

- [ ] **Step 4: Zbuduj i sprawdź klienta**

```bash
./gradlew :manacore:build
```

```bash
./gradlew runClient
```

Oczekiwane: pasek many widoczny nad paskiem doświadczenia, pełny (regen dobija do maksimum), z liczbą `100 / 100`.

- [ ] **Step 5: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/client manacore/src/main/resources/assets
git commit -m "feat(manacore): pasek many na HUD (robocza tekstura z player_mana)"
```

---

## Task 10: API i komenda debugowa

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/api/ManaAPI.java`
- Create: `manacore/src/main/java/com/spege/manacore/command/CommandMana.java`
- Modify: `manacore/src/main/java/com/spege/manacore/ManaCoreMod.java`

- [ ] **Step 1: Fasada API**

`.../api/ManaAPI.java`:

```java
package com.spege.manacore.api;

import java.util.UUID;

import javax.annotation.Nullable;

import com.spege.manacore.attr.ManaAttributes;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.ManaMath;
import com.spege.manacore.net.ManaNetwork;

import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Jedyny publiczny punkt wejscia dla innych modow. Wszystkie metody sa bezpieczne
 * dla `null` i dla strony klienta (na kliencie zapisy sa ignorowane).
 */
public final class ManaAPI {

    private ManaAPI() {
    }

    public static double getMana(@Nullable EntityPlayer player) {
        IManaPool pool = ManaCapabilities.get(player);
        return pool == null ? 0.0D : pool.getCurrent();
    }

    public static double getMaxMana(@Nullable EntityPlayer player) {
        return ManaAttributes.getMaxMana(player);
    }

    public static boolean hasMana(@Nullable EntityPlayer player, double amount) {
        return getMana(player) >= amount;
    }

    /** Zwraca false i NIC nie zmienia, gdy many nie starcza. */
    public static boolean spend(@Nullable EntityPlayer player, double amount) {
        if (player == null || player.world.isRemote) {
            return false;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null || pool.getCurrent() < amount) {
            return false;
        }
        pool.setCurrent(ManaMath.afterSpend(pool.getCurrent(), amount));
        syncNow(player);
        return true;
    }

    public static void add(@Nullable EntityPlayer player, double amount) {
        if (player == null || player.world.isRemote || amount <= 0.0D || !isFinite(amount)) {
            return;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        double max = getMaxMana(player);
        pool.setCurrent(ManaMath.afterRegen(pool.getCurrent(), max, amount));
        syncNow(player);
    }

    public static void setMana(@Nullable EntityPlayer player, double value) {
        if (player == null || player.world.isRemote || !isFinite(value)) {
            return;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        pool.setCurrent(ManaMath.clamp(value, 0.0D, getMaxMana(player)));
        syncNow(player);
    }

    /** Doklada trwala progresje, twardo ograniczona configowym sufitem. */
    public static void addProgression(@Nullable EntityPlayer player, double amount) {
        if (player == null || player.world.isRemote || amount <= 0.0D || !isFinite(amount)) {
            return;
        }
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        pool.setProgressionBonus(ManaMath.afterProgressionGain(
                pool.getProgressionBonus(), ManaCoreConfig.pool.progressionCap, amount));
        ManaAttributes.refreshProgressionModifier(player);
        syncNow(player);
    }

    /**
     * Zaklada lub podmienia modyfikator maksymalnej many o podanym UUID.
     * To jest punkt wejscia dla WSZYSTKICH przyszlych zrodel bonusu (baubles, enchanty,
     * quality, drzewko umiejetnosci) - rdzen nie potrzebuje o nich wiedziec nic wiecej.
     */
    public static void addMaxModifier(@Nullable EntityPlayer player, UUID id, String name, double amount, int operation) {
        if (player == null) {
            return;
        }
        IAttributeInstance instance = player.getEntityAttribute(ManaAttributes.MAX_MANA);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null) {
            instance.removeModifier(existing);
        }
        if (amount != 0.0D) {
            instance.applyModifier(new AttributeModifier(id, name, amount, operation));
        }
    }

    public static void removeMaxModifier(@Nullable EntityPlayer player, UUID id) {
        addMaxModifier(player, id, "manacore.removed", 0.0D, 0);
    }

    private static void syncNow(EntityPlayer player) {
        if (player instanceof EntityPlayerMP) {
            ManaNetwork.sync((EntityPlayerMP) player);
        }
    }

    /**
     * Bariera przeciw NaN i nieskonczonosciom. Java 8 nie ma Double.isFinite w wersji,
     * ktora chcemy tu miec jawna, wiec sprawdzamy oba warunki wprost.
     */
    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
```

> 🚨 **Dlaczego bariera na `NaN` stoi akurat tutaj, a nie w setterach capability.** `ManaPool.setCurrent` podnosi flagę `dirty` przez porównanie `this.current != value`. Dla `NaN` to porównanie jest **zawsze prawdziwe** (`NaN != NaN`), więc jedna skażona wartość zamieniłaby throttling synchronizacji w stały spam pakietów co tick — i to bez żadnego widocznego objawu poza ruchem sieciowym. `ManaAPI` jest jedyną drogą, którą obce mody (mosty z Planu 2) piszą do puli, więc bariera tutaj zamyka całe wejście, zamiast rozsypywać walidację po setterach.
>
> 🚨 **Nie licz na to, że `amount <= 0.0D` odfiltruje `NaN` — jest dokładnie odwrotnie.** Każde porównanie z `NaN` jest fałszywe, więc `NaN <= 0.0` to `false`, metoda **nie** wychodzi wcześniej i skażona wartość leci dalej. Dlatego `add`, `addProgression` i `setMana` mają jawne `!isFinite(...)` obok warunku na znak. To jest ten rodzaj pułapki, który wygląda na obsłużony i nie jest.

> 🚨 **Po napisaniu `addMaxModifier` przepisz `ManaAttributes.refreshProgressionModifier`, żeby go wołało** — zamiast trzymać drugą, niezależną implementację tego samego wzorca remove-then-apply. Dziś obie istnieją osobno, z subtelnie różnym warunkiem aplikacji (`bonus > 0.0D` tam, `amount != 0.0D` tutaj), a to jest fundament, na którym stanie siedem przyszłych źródeł bonusu. Docelowo ciało tamtej metody to jedna linia:
>
> ```java
>         ManaAPI.addMaxModifier(player, PROGRESSION_MODIFIER_ID, PROGRESSION_MODIFIER_NAME,
>                 pool.getProgressionBonus(), 0);
> ```
>
> Uwaga na kierunek zależności: `ManaAttributes` zacznie wtedy zależeć od `api`, a `ManaAPI` już zależy od `attr`. To cykl między pakietami — jeśli okaże się uciążliwy, przenieś wspólną logikę modyfikatora do prywatnej metody w `ManaAttributes` i niech `ManaAPI` woła ją, a nie odwrotnie.

- [ ] **Step 2: Komenda debugowa**

`.../command/CommandMana.java`:

```java
package com.spege.manacore.command;

import com.spege.manacore.api.ManaAPI;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public class CommandMana extends CommandBase {

    @Override
    public String getName() {
        return "mana";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "manacore.command.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new net.minecraft.command.WrongUsageException(getUsage(sender));
        }

        EntityPlayer target = args.length >= 3
                ? getPlayer(server, sender, args[2])
                : getCommandSenderAsPlayer(sender);

        if ("get".equals(args[0])) {
            sender.sendMessage(new TextComponentString(
                    ManaAPI.getMana(target) + " / " + ManaAPI.getMaxMana(target)));
            return;
        }

        if (args.length < 2) {
            throw new net.minecraft.command.WrongUsageException(getUsage(sender));
        }
        double amount = parseDouble(args[1]);

        if ("set".equals(args[0])) {
            ManaAPI.setMana(target, amount);
        } else if ("add".equals(args[0])) {
            ManaAPI.add(target, amount);
        } else if ("addprog".equals(args[0])) {
            ManaAPI.addProgression(target, amount);
        } else {
            throw new net.minecraft.command.WrongUsageException(getUsage(sender));
        }

        sender.sendMessage(new TextComponentString(
                ManaAPI.getMana(target) + " / " + ManaAPI.getMaxMana(target)));
    }
}
```

- [ ] **Step 3: Zarejestruj komendę**

W `ManaCoreMod` dodaj metodę:

```java
    @Mod.EventHandler
    public void serverStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        event.registerServerCommand(new com.spege.manacore.command.CommandMana());
    }
```

- [ ] **Step 4: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/api manacore/src/main/java/com/spege/manacore/command manacore/src/main/java/com/spege/manacore/ManaCoreMod.java
git commit -m "feat(manacore): publiczne API i komenda debugowa /mana"
```

---

## Task 11: Weryfikacja w grze i na dedykowanym serwerze

**Files:** brak zmian w kodzie, chyba że weryfikacja coś wykaże.

> **Reguła tego repo:** czysty start klienta **niczego nie dowodzi** w kwestii bezpieczeństwa stron. Serwer dedykowany trzeba uruchomić osobno.

- [ ] **Step 1: Zbuduj wszystko i sprawdź, że nie zepsułeś pozostałych modów**

```bash
./gradlew build
```

Oczekiwane: `BUILD SUCCESSFUL`, jary wszystkich subprojektów obecne w swoich `build/libs/`.

- [ ] **Step 2: Klient — pasek, regen, komenda**

> 🚨 **Najpierw napraw dev-runtime, bo inaczej nic nie wystartuje.** `libs/` zawiera dwie wersje Tombstone (`tombstone-1.12.2-4.7.6.jar` i `tombstone-1.12.2-4.8.0.jar`), a `insanetweaks/build.gradle` deobfuskuje cały `fileTree` z `libs/` wykluczając tylko `ElectroblobsWizardry-*`, `dldungeonsjbg-*` i `journeymap-*`. Dwa mody o modid `tombstone` = crash przy starcie. Dopisz czwarte wykluczenie albo usuń zbędny jar. Zwróć uwagę, że `tombtweaks/build.gradle` przypina **4.7.6**, a pack DEv 1.2 chodzi na **4.8.0** — to osobny drift, nie myl go z tym crashem.
>
> Sprawdź też, czy `libs/` nie zebrał w międzyczasie innych duplikatów: `ls libs/ | sed 's/-[0-9].*//' | sort | uniq -d` powinno nic nie zwrócić.

```bash
./gradlew runClient
```

Sprawdź kolejno:
1. Pasek many rysuje się nad paskiem doświadczenia.
2. `/mana set 10` — pasek natychmiast spada do 10, liczba pokazuje `10 / 100`.
3. Pasek sam rośnie do 100 (regen 1/s).
4. `/mana addprog 5` — maksimum rośnie do `105`, pasek się przeskalowuje.
5. `/mana get` zwraca tę samą liczbę, którą widać na pasku.

- [ ] **Step 3: Persystencja**

W tej samej sesji: `/mana set 42`, wyjdź do menu głównego, wejdź z powrotem do świata.
Oczekiwane: `current` wynosi 42 (plus to, co doszło z regenu), `progressionBonus` zachowany.

- [ ] **Step 4: Śmierć**

`/mana set 80`, `/mana addprog 5`, potem `/kill`.
Oczekiwane po respawnie: `current` = 0 (bo `resetCurrentOnDeath = true`), maksimum nadal `105` — **progresja przeżyła zgon**.

- [ ] **Step 5: Serwer dedykowany**

```bash
./gradlew runServer
```

Oczekiwane: serwer wstaje bez wyjątku. Szukaj w logu i potwierdź BRAK:
- `Attempted to load class ... for invalid side SERVER`
- `NoClassDefFoundError`
- `NoSuchFieldError`

- [ ] **Step 6: Test w prawdziwej instancji**

Skopiuj `manacore/build/libs/manacore-0.1.0.jar` do `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\`.
Uruchom instancję, sprawdź `logs/latest.log` pod kątem `[ManaCore]` oraz brak wyjątków.

> Nie ma tu starego jara do usunięcia — to pierwsza wersja. Przy każdej kolejnej **usuń poprzedni jar tego modid**: dwie wersje jednego modid to crash na duplikacie.

- [ ] **Step 7: Commit ewentualnych poprawek**

```bash
git add -A manacore
git commit -m "fix(manacore): poprawki po weryfikacji w grze"
```

---

## Kryteria ukończenia Planu 1

1. `./gradlew build` przechodzi dla **wszystkich** subprojektów.
2. `./gradlew :manacore:test` — 8 testów zielonych.
3. Klient: pasek many widoczny, regeneruje się, reaguje na `/mana`.
4. `current` i `progressionBonus` przeżywają wylogowanie i wejście z powrotem.
5. `progressionBonus` przeżywa śmierć; `current` zeruje się zgodnie z configem.
6. `run/config/manacore.cfg` ma trzy kategorie na poziomie głównym, nie pod `general`.
7. Serwer dedykowany wstaje bez `invalid side SERVER`, `NoClassDefFoundError` i `NoSuchFieldError`.
8. `javap` na klasie `@Mod` nie pokazuje ani jednego typu `minecraft/client` ani `fml/client`.
9. Nazwy z `en_us.lang` renderują się jako tekst, nie jako własne klucze (dowód, że `pack.mcmeta` działa).

Po spełnieniu wszystkich dziewięciu — przejdź do `2026-08-18-manacore-2-bridges.md`.
