# commandsuggest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Zbudować mod `commandsuggest` — popup podpowiadający komendy pod polem czatu w Minecraft 1.12.2 — w miejsce brigo, bez ani jednego mixina.

**Architecture:** Cztery warstwy. `core` to czysta Java 8 bez jednej klasy Minecrafta (model drzewa, parser wejścia, matcher, kodeki JSON i binarny) i jako jedyna ma testy jednostkowe. `server` buduje drzewo z `CommandHandler.getCommands()`, przycina po uprawnieniach i wysyła je graczowi przy loginie. `client` zaczepia się o czat czterema eventami `GuiScreenEvent` i jedną podmianą pola `GuiChat.tabCompleter` na własną podklasę `TabCompleter`. `net` to jeden pakiet S→C.

**Tech Stack:** Java 8, Forge 1.12.2-14.23.5.2860, ForgeGradle 3, Gson (z MC), JUnit 4.12, LWJGL 2 (klawiatura/mysz przez `org.lwjgl.input.*`).

**Spec:** [docs/superpowers/specs/2026-08-10-commandsuggest-design.md](../specs/2026-08-10-commandsuggest-design.md) — czytaj przed startem, zwłaszcza §3 (dlaczego akurat te haki) i §11 (ryzyka).

---

## Zanim zaczniesz — czego ten plan wymaga, a czego nie

- **Java 8. Bez `var`, bez rekordów, bez `List.of`.** Lambdy są dozwolone (nie ma tu mixinów, w których byłyby zakazane).
- **TDD dotyczy wyłącznie `core`** (zadania 2–7). Reszta to kod dotykający Minecrafta i nie da się go sensownie odpalić poza grą — tam weryfikacją jest kompilacja plus scenariusz ręczny z zadania 18. Nie udawaj testów jednostkowych dla warstwy klienta.
- **Commituj po każdym zadaniu.** Komunikaty po polsku, bez polskich znaków diakrytycznych (konwencja repo — patrz `git log`).
- **Gałąź:** `feat/commandsuggest`, już istnieje i jest odbita od `main`.

### Trzy odstępstwa od specu, świadome

1. Spec §4 mówi `CmdArg.props` jako `Map<String,Object>`. Plan używa konkretnych pól `choices` / `min` / `max` — typowane, bez opakowywania i rzutowania. Zakres funkcji ten sam.
2. Spec §4 mówi `CommandTree.prune(Predicate<String>)`. `CommandTree` to **jedna** komenda, a przycinanie po uprawnieniach usuwa **całe komendy**, więc metoda mieszka w `CommandIndex` (kolekcja drzew). To poprawka błędu w specu, nie zmiana zakresu.
3. Spec §8 nazywa klasę `@Mod` `CommandSuggestMod`. Plan nazywa ją `CommandSuggest` — zgodnie z najnowszą konwencją repo (`EnchantEraser`, nie `EnchantEraserMod`).

---

## Struktura plików

| Plik | Odpowiedzialność |
|---|---|
| `settings.gradle` | +`include 'commandsuggest'` |
| `commandsuggest/build.gradle` | podprojekt, zero `fg.deobf`, zero mixinbootera, `test` z JUnit 4 |
| `…/CommandSuggest.java` | `@Mod`, stałe, `LOGGER`, `@SidedProxy`, cykl życia |
| `…/CommonProxy.java`, `…/client/ClientProxy.java` | rejestracja stron; **wszystko klienckie tylko tutaj** |
| `…/config/CommandSuggestConfig.java` | siedem pól w `general` |
| `…/core/ArgType.java` | enum typów + skąd się rozwiązują |
| `…/core/CmdArg.java`, `CmdNode.java`, `CommandTree.java`, `CommandIndex.java` | model, niemutowalny |
| `…/core/InputParser.java`, `ParsedInput.java` | podział linii identyczny z waniliowym |
| `…/core/SuggestionEngine.java`, `Suggestions.java`, `Suggestion.java` | spacer po drzewie → lista podpowiedzi |
| `…/core/JsonTreeReader.java` | JSON → `CommandTree` |
| `…/core/TreeCodec.java`, `VarInt.java` | drzewo ⇄ `byte[]`, pula stringów, GZIP |
| `…/net/PacketHandler.java`, `S2CCommandTree.java` | kanał i jeden pakiet |
| `…/client/net/TreeApplier.java` | klient-only odbiór pakietu (`@SideOnly`) |
| `…/server/DescriptorLoader.java` | JSON z jara i z `config/` |
| `…/server/TreeBuilder.java` | `ICommand` → `CommandIndex`, filtr uprawnień |
| `…/server/CommandCommandSuggest.java` | `/commandsuggest reload\|refresh` |
| `…/server/LoginHandler.java` | wysyłka drzewa na `PlayerLoggedInEvent` |
| `…/client/ClientTreeCache.java` | drzewo na sesję, okno handshake'u |
| `…/client/SpyTabCompleter.java` | przechwytuje odpowiedź serwera zamiast mutować tekst |
| `…/client/LocalValueSource.java` | wartości z rejestrów klienta |
| `…/client/ChatScreenHandler.java` | cztery eventy, refleksja, stan sesji czatu |
| `…/client/SuggestionPopup.java` | rysowanie i nawigacja |
| `…/resources/…` | `mcmod.info`, `pack.mcmeta`, lang, wbudowane opisy + `index.json` |
| `commandsuggest/src/test/java/…/core/` | JUnit 4, wyłącznie `core` |

---

## Task 1: Podprojekt, który się buduje

**Files:**
- Modify: `settings.gradle`
- Create: `commandsuggest/build.gradle`
- Create: `commandsuggest/src/main/resources/mcmod.info`
- Create: `commandsuggest/src/main/resources/pack.mcmeta`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/CommandSuggest.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/CommonProxy.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/client/ClientProxy.java`

- [ ] **Step 1: Dopisz podprojekt**

`settings.gradle` — dodaj na końcu:

```groovy
include 'commandsuggest'
```

- [ ] **Step 2: `commandsuggest/build.gradle`**

```groovy
apply plugin: 'net.minecraftforge.gradle'
apply plugin: 'eclipse'
apply plugin: 'maven-publish'

repositories {
    maven { url = 'https://maven.cleanroommc.com' }
    mavenCentral()
}

version = '1.0.0'
group = 'com.spege.commandsuggest'
archivesBaseName = 'commandsuggest'

// Jedno zrodlo prawdy dla wersji: mcmod.info i manifest bierza ja z 'version' powyzej.
processResources {
    inputs.property 'version', project.version
    inputs.property 'mcversion', '1.12.2'

    from(sourceSets.main.resources.srcDirs) {
        include 'mcmod.info'
        expand 'version': project.version, 'mcversion': '1.12.2'
    }
    from(sourceSets.main.resources.srcDirs) {
        exclude 'mcmod.info'
    }
}

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

    // ZADNEGO fg.deobf i zadnego mixinbootera. Ten mod nie linkuje sie do zadnego moda
    // i nie ma ani jednego mixina - patrz spec 3.
    testImplementation 'junit:junit:4.12'
}

// Pakiet core nie zna ani jednej klasy Minecrafta, wiec chodzi na zwyklej JVM.
// To JEDYNY podprojekt w repo z testami - patrz zadanie 19 (aktualizacja CLAUDE.md).
test {
    useJUnit()
    testLogging { events 'passed', 'skipped', 'failed' }
}

jar {
    manifest {
        attributes([
            "Specification-Title": "Command Suggest",
            "Specification-Vendor": "spege",
            "Specification-Version": "${version}",
            "Implementation-Title": project.name,
            "Implementation-Version": "${version}",
            "Implementation-Vendor": "spege",
            "Implementation-Timestamp": new Date().format("yyyy-MM-dd'T'HH:mm:ssZ")
            // Zadnego 'MixinConfigs' - ten mod nie ma mixinow i miec nie ma.
        ])
    }
}

jar.finalizedBy('reobfJar')

publishing {
    publications { mavenJava(MavenPublication) { artifact jar } }
    repositories { maven { url "file:///${rootProject.projectDir}/mcmodsrepo" } }
}
```

- [ ] **Step 3: `mcmod.info`**

```json
[
{
  "modid": "commandsuggest",
  "name": "Command Suggest",
  "description": "A command suggestion popup for the chat box. Shows matching commands and their arguments as you type, with argument types coloured and a usage line underneath. The server sends a permission-filtered command tree at login; without it the mod falls back to vanilla tab-completion, so it degrades cleanly on any server. Command descriptions are data-driven JSON in config/commandsuggest/commands, so covering a new mod's commands needs no code. No mixins, no ASM.",
  "version": "${version}",
  "mcversion": "${mcversion}",
  "authorList": ["Isuthhh"],
  "dependencies": []
}
]
```

- [ ] **Step 4: `pack.mcmeta`**

```json
{
    "pack": {
        "description": "commandsuggest resources",
        "pack_format": 3,
        "_comment": "Load-bearing, not boilerplate: without this file the jar's assets/ never becomes a resource domain, so assets/commandsuggest/lang/en_us.lang is never read and every key falls through to its own name - silently, with nothing in any log. Three of the four extractions from insanetweaks forgot it (fixed 2026-08-04), so this mod ships it from the first commit. pack_format 3 is the 1.11+ value and is what makes lowercase en_us.lang correct."
    }
}
```

- [ ] **Step 5: Klasa `@Mod` i proxy**

`CommandSuggest.java`:

```java
package com.spege.commandsuggest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Command Suggest — podpowiedzi komend pod polem czatu, bez ani jednego mixina.
 *
 * <p>Zastepuje brigo, ktory wisial na czterech mixinach i padl 2026-08-10 na regresji CleanMix
 * w sciezce INJECT_PREPARE_LEGACY. Cztery eventy {@code GuiScreenEvent} plus jedna podmiana pola
 * {@code GuiChat.tabCompleter} daja to samo bez ASM — uzasadnienie w specu, sekcja 3.
 *
 * <p>{@code acceptableRemoteVersions = "*"} jest tu wymagane, nie kosmetyczne: mod ma dzialac
 * na serwerze, ktory go nie ma (tryb zdegradowany), wiec nie moze zadac zgodnosci wersji.
 */
@Mod(modid = CommandSuggest.MODID,
        name = CommandSuggest.NAME,
        version = CommandSuggest.VERSION,
        acceptableRemoteVersions = "*")
public class CommandSuggest {

    public static final String MODID = "commandsuggest";
    public static final String NAME = "Command Suggest";
    /** 🚨 Bumpuj RAZEM z 'version' w build.gradle — to jest ta wartosc, ktora widac w liscie modow. */
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.spege.commandsuggest.client.ClientProxy",
                serverSide = "com.spege.commandsuggest.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
```

`CommonProxy.java`:

```java
package com.spege.commandsuggest;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** Strona wspolna. Nie wolno tu wpisac ani jednego typu z {@code net.minecraft.client}. */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        // wypelnia zadanie 9 (kanal) i 12 (komenda, login hook)
    }

    public void init(FMLInitializationEvent event) {
    }
}
```

`client/ClientProxy.java`:

```java
package com.spege.commandsuggest.client;

import com.spege.commandsuggest.CommonProxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Wszystkie handlery klienckie rejestrowane TYLKO stad. {@code GuiScreenEvent} jest klasowo
 * {@code @SideOnly(Side.CLIENT)} razem z podklasami, wiec {@code new ChatScreenHandler()} na
 * dedyku bylby crashem przy ladowaniu klasy (SideTransformer), a nie dopiero przy wywolaniu.
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // wypelnia zadanie 15
    }
}
```

- [ ] **Step 6: Zbuduj i sprawdź**

```bash
./gradlew :commandsuggest:build
```

Oczekiwane: `BUILD SUCCESSFUL`, powstaje `commandsuggest/build/libs/commandsuggest-1.0.0.jar`. Sprawdź, że jar ma `pack.mcmeta`:

```bash
unzip -l commandsuggest/build/libs/commandsuggest-1.0.0.jar | grep -E "pack.mcmeta|mcmod.info"
```

Oczekiwane: obie linie obecne.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle commandsuggest/
git commit -m "feat(commandsuggest): szkielet podprojektu, buduje sie do jara"
```

---

## Task 2: `core` — enum `ArgType`

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/ArgType.java`
- Test: `commandsuggest/src/test/java/com/spege/commandsuggest/core/ArgTypeTest.java`

- [ ] **Step 1: Test, który nie przechodzi**

```java
package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import org.junit.Test;

public class ArgTypeTest {

    @Test
    public void byIdRozpoznajeZnaneTypy() {
        assertEquals(ArgType.PLAYER, ArgType.byId("player"));
        assertEquals(ArgType.INT, ArgType.byId("int"));
        assertEquals(ArgType.GREEDY, ArgType.byId("greedy"));
    }

    @Test
    public void byIdNieWywalaSieNaSmieciach() {
        assertEquals(ArgType.UNKNOWN, ArgType.byId("zupelnie-nowy-typ"));
        assertEquals(ArgType.UNKNOWN, ArgType.byId(null));
    }

    /**
     * Wyczerpujaco po values(), nie na probce: kazda nowa stala bedzie MUSIALA sie zadeklarowac,
     * zamiast po cichu przejsc z domyslna flaga.
     */
    @Test
    public void serverResolvedTylkoDlaTrzechTypow() {
        EnumSet<ArgType> serverResolved = EnumSet.of(ArgType.WORD, ArgType.GREEDY, ArgType.UNKNOWN);
        for (ArgType t : ArgType.values()) {
            if (serverResolved.contains(t)) {
                assertTrue(t.name(), t.isServerResolved());
            } else {
                assertFalse(t.name(), t.isServerResolved());
            }
        }
    }

    @Test
    public void idJestStabilnyBoIdzieDoJsona() {
        for (ArgType t : ArgType.values()) {
            assertEquals(t, ArgType.byId(t.getId()));
        }
    }
}
```

- [ ] **Step 2: Odpal, potwierdź że nie kompiluje**

```bash
./gradlew :commandsuggest:test
```

Oczekiwane: `FAILED`, błąd kompilacji `cannot find symbol: class ArgType`.

- [ ] **Step 3: Implementacja**

```java
package com.spege.commandsuggest.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Typ argumentu. Wartosc enuma rozstrzyga <b>skad biora sie podpowiedzi</b> i to jest caly zysk
 * z typowania: {@link #isServerResolved()} == false znaczy "klient wie sam", a wiec zadnego
 * {@code CPacketTabComplete}, a wiec zadnego obcinania listy przez VintageFix do 100 pozycji
 * z linijka smiecia na czacie.
 *
 * <p>{@code id} jest kluczem w plikach JSON — jest danymi na dysku, nie nazwa w kodzie.
 * Zmiana ktoregokolwiek uniewaznia opisy, ktore ludzie maja w {@code config/}.
 */
public enum ArgType {

    /** Jedno slowo, tresc nieznana — pytamy serwer. */
    WORD("word", true),
    /** Wszystko do konca linii — pytamy serwer. */
    GREEDY("greedy", true),
    /** Komenda nieopisana albo typ z przyszlej wersji formatu. */
    UNKNOWN("unknown", true),

    INT("int", false),
    FLOAT("float", false),
    BOOL("bool", false),
    /** Zamknieta lista wartosci, podana wprost w opisie. */
    CHOICE("choice", false),
    PLAYER("player", false),
    ENTITY("entity", false),
    BLOCKPOS("blockpos", false),
    ITEM("item", false),
    BLOCK("block", false),
    DIMENSION("dimension", false);

    private static final Map<String, ArgType> BY_ID;

    static {
        Map<String, ArgType> m = new HashMap<String, ArgType>();
        for (ArgType t : values()) {
            m.put(t.id, t);
        }
        BY_ID = m;
    }

    private final String id;
    private final boolean serverResolved;

    ArgType(String id, boolean serverResolved) {
        this.id = id;
        this.serverResolved = serverResolved;
    }

    public String getId() {
        return this.id;
    }

    /** {@code true} = wartosci trzeba wyprosic waniliowym tab-complete od serwera. */
    public boolean isServerResolved() {
        return this.serverResolved;
    }

    /**
     * Nieznany identyfikator daje {@link #UNKNOWN}, nigdy wyjatku — opisy pisza ludzie.
     *
     * <p>Uwaga: wlasny id {@link #UNKNOWN} to string {@code "unknown"}, wiec
     * {@code byId("unknown")} i {@code byId("literowka")} zwracaja to samo — zeby odroznic
     * literowke od jawnego "unknown" w JSON-ie, wywolujacy musi sam porownac surowy string
     * z {@code ArgType.UNKNOWN.getId()} przed wywolaniem tej metody.
     */
    public static ArgType byId(String id) {
        if (id == null) {
            return UNKNOWN;
        }
        ArgType t = BY_ID.get(id);
        return t != null ? t : UNKNOWN;
    }
}
```

- [ ] **Step 4: Odpal, potwierdź zielone**

```bash
./gradlew :commandsuggest:test
```

Oczekiwane: `BUILD SUCCESSFUL`, cztery testy `passed`.

- [ ] **Step 5: Commit**

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/core/ArgType.java commandsuggest/src/test/java/com/spege/commandsuggest/core/ArgTypeTest.java
git commit -m "feat(commandsuggest): ArgType - typ argumentu rozstrzyga zrodlo podpowiedzi"
```

---

## Task 3: `core` — `InputParser`, podział linii identyczny z waniliowym

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/ParsedInput.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/InputParser.java`
- Test: `commandsuggest/src/test/java/com/spege/commandsuggest/core/InputParserTest.java`

**Dlaczego to jest osobne zadanie i dlaczego ma tyle testów:** jeżeli nasz podział linii różni się od waniliowego choćby o jeden pusty token, podpowiadamy argument o inny indeks niż ten, który komenda naprawdę dostanie. Wanilia robi to w `MinecraftServer.getTabCompletions`: obcina wiodący `/`, potem `input.split(" ", -1)`. Limit `-1` jest tu istotny — zachowuje puste tokeny na końcu, więc `"gamerule "` daje dwa tokeny, nie jeden.

- [ ] **Step 1: Test, który nie przechodzi**

```java
package com.spege.commandsuggest.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InputParserTest {

    @Test
    public void zwyklyTekstToNieKomenda() {
        assertFalse(InputParser.parse("czesc", 5).isCommand());
        assertFalse(InputParser.parse("", 0).isCommand());
    }

    @Test
    public void samUkosnikDajePustyPrefiksNaPozycjiZero() {
        ParsedInput in = InputParser.parse("/", 1);
        assertTrue(in.isCommand());
        assertArrayEquals(new String[] { "" }, in.getTokens());
        assertEquals(0, in.getEditIndex());
        assertEquals("", in.getPrefix());
        assertEquals(1, in.getPrefixStart());
    }

    @Test
    public void nazwaKomendyWTrakciePisania() {
        ParsedInput in = InputParser.parse("/game", 5);
        assertArrayEquals(new String[] { "game" }, in.getTokens());
        assertEquals(0, in.getEditIndex());
        assertEquals("game", in.getPrefix());
        assertEquals(1, in.getPrefixStart());
    }

    @Test
    public void spacjaNaKoncuOtwieraNastepnyArgument() {
        ParsedInput in = InputParser.parse("/gamerule ", 10);
        assertArrayEquals(new String[] { "gamerule", "" }, in.getTokens());
        assertEquals(1, in.getEditIndex());
        assertEquals("", in.getPrefix());
        assertEquals(10, in.getPrefixStart());
    }

    @Test
    public void argumentWTrakciePisania() {
        ParsedInput in = InputParser.parse("/gamerule doD", 13);
        assertArrayEquals(new String[] { "gamerule", "doD" }, in.getTokens());
        assertEquals(1, in.getEditIndex());
        assertEquals("doD", in.getPrefix());
        assertEquals(10, in.getPrefixStart());
    }

    @Test
    public void trzeciArgumentMaPoprawnyOffset() {
        ParsedInput in = InputParser.parse("/gamerule doDaylightCycle tr", 28);
        assertEquals(2, in.getEditIndex());
        assertEquals("tr", in.getPrefix());
        assertEquals(26, in.getPrefixStart());
    }

    @Test
    public void liczySieTylkoTekstPrzedKursorem() {
        // kursor tuz za "doD", reszta linii jest za nim i nie ma prawa wplywac na podpowiedz
        ParsedInput in = InputParser.parse("/gamerule doD aylightCycle true", 13);
        assertArrayEquals(new String[] { "gamerule", "doD" }, in.getTokens());
        assertEquals("doD", in.getPrefix());
    }

    @Test
    public void podwojnaSpacjaDajePustyTokenTakJakWWanilii() {
        // "a  b".split(" ", -1) == ["a", "", "b"] - komenda dostanie dokladnie to samo
        ParsedInput in = InputParser.parse("/cmd  x", 7);
        assertArrayEquals(new String[] { "cmd", "", "x" }, in.getTokens());
        assertEquals(2, in.getEditIndex());
    }

    @Test
    public void kursorNaPoczatkuNieWywalaSie() {
        assertFalse(InputParser.parse("/gamerule", 0).isCommand());
    }
}
```

- [ ] **Step 2: Odpal, potwierdź że nie kompiluje**

```bash
./gradlew :commandsuggest:test --tests '*InputParserTest'
```

Oczekiwane: `FAILED`, `cannot find symbol: class InputParser`.

- [ ] **Step 3: Implementacja**

`ParsedInput.java`:

```java
package com.spege.commandsuggest.core;

/** Wynik podzialu linii czatu. Niemutowalny. */
public final class ParsedInput {

    /** Zwracane dla wszystkiego, co nie zaczyna sie od ukosnika. */
    public static final ParsedInput NOT_A_COMMAND = new ParsedInput(false, new String[0], 0, 0);

    private final boolean command;
    private final String[] tokens;
    private final int editIndex;
    private final int prefixStart;

    ParsedInput(boolean command, String[] tokens, int editIndex, int prefixStart) {
        this.command = command;
        this.tokens = tokens;
        this.editIndex = editIndex;
        this.prefixStart = prefixStart;
    }

    public boolean isCommand() {
        return this.command;
    }

    /** {@code tokens[0]} to nazwa komendy bez ukosnika. Kopia — wolno modyfikowac. */
    public String[] getTokens() {
        return this.tokens.clone();
    }

    /** Indeks tokenu, na ktorym stoi kursor. */
    public int getEditIndex() {
        return this.editIndex;
    }

    /** Tresc edytowanego tokenu — to ona filtruje liste. */
    public String getPrefix() {
        return this.tokens[this.editIndex];
    }

    /** Offset w ORYGINALNEJ linii, pod ktorym zaczyna sie edytowany token. Potrzebny przy podmianie. */
    public int getPrefixStart() {
        return this.prefixStart;
    }
}
```

`InputParser.java`:

```java
package com.spege.commandsuggest.core;

/**
 * Dzieli linie czatu tak samo, jak zrobi to serwer.
 *
 * <p>🚨 Wzorzec jest z {@code MinecraftServer.getTabCompletions}: obetnij wiodacy {@code /},
 * potem {@code split(" ", -1)}. Limit {@code -1} jest load-bearing — bez niego {@code "gamerule "}
 * daje jeden token zamiast dwoch i podpowiadalibysmy nazwe komendy zamiast jej pierwszego
 * argumentu. Zadnych cudzyslowow: wanilia ich nie zna i rozjechalibysmy sie z tym, co komenda
 * naprawde dostanie.
 */
public final class InputParser {

    private InputParser() {
    }

    /**
     * @param raw       cala tresc pola czatu
     * @param cursorPos pozycja kursora; tekst za kursorem jest ignorowany, tak jak w
     *                  {@code TabCompleter.complete()}
     */
    public static ParsedInput parse(String raw, int cursorPos) {
        if (raw == null || cursorPos <= 0 || cursorPos > raw.length()) {
            if (raw == null || cursorPos <= 0) {
                return ParsedInput.NOT_A_COMMAND;
            }
            cursorPos = raw.length();
        }
        String upToCursor = raw.substring(0, cursorPos);
        if (!upToCursor.startsWith("/")) {
            return ParsedInput.NOT_A_COMMAND;
        }

        String body = upToCursor.substring(1);
        String[] tokens = body.split(" ", -1);
        int editIndex = tokens.length - 1;

        int prefixStart = 1; // za ukosnikiem
        for (int i = 0; i < editIndex; i++) {
            prefixStart += tokens[i].length() + 1; // +1 za spacje
        }
        return new ParsedInput(true, tokens, editIndex, prefixStart);
    }
}
```

- [ ] **Step 4: Odpal, potwierdź zielone**

```bash
./gradlew :commandsuggest:test --tests '*InputParserTest'
```

Oczekiwane: `BUILD SUCCESSFUL`, dziewięć testów `passed`.

- [ ] **Step 5: Commit**

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/core/ParsedInput.java commandsuggest/src/main/java/com/spege/commandsuggest/core/InputParser.java commandsuggest/src/test/java/com/spege/commandsuggest/core/InputParserTest.java
git commit -m "feat(commandsuggest): InputParser - podzial linii identyczny z waniliowym"
```

---

## Task 4: `core` — model drzewa i `CommandIndex.prune`

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/CmdArg.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/CmdNode.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/CommandTree.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/CommandIndex.java`
- Test: `commandsuggest/src/test/java/com/spege/commandsuggest/core/CommandIndexTest.java`

- [ ] **Step 1: Test, który nie przechodzi**

```java
package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class CommandIndexTest {

    private static CommandTree drzewo(String nazwa, String... aliasy) {
        CmdNode root = new CmdNode(null, Collections.<CmdArg>emptyList(),
                Collections.<CmdNode>emptyList(), true, null);
        return new CommandTree(nazwa, Arrays.asList(aliasy), root);
    }

    @Test
    public void byNameZnajdujePoNazwieIPoAliasie() {
        CommandIndex idx = new CommandIndex(Arrays.asList(drzewo("gamerule"), drzewo("teleport", "tp")));
        assertEquals("gamerule", idx.byName("gamerule").getName());
        assertEquals("teleport", idx.byName("tp").getName());
        assertNull(idx.byName("nie-ma-takiej"));
    }

    @Test
    public void allNamesZawieraAliasyIJestPosortowane() {
        CommandIndex idx = new CommandIndex(Arrays.asList(drzewo("teleport", "tp"), drzewo("gamerule")));
        assertEquals(Arrays.asList("gamerule", "teleport", "tp"), idx.allNames());
    }

    @Test
    public void pruneUsuwaCaleKomendyPoNazwieKanonicznej() {
        CommandIndex idx = new CommandIndex(Arrays.asList(drzewo("op"), drzewo("teleport", "tp")));
        CommandIndex przyciety = idx.prune(name -> !"op".equals(name));
        assertNull(przyciety.byName("op"));
        assertEquals("teleport", przyciety.byName("tp").getName());
        assertEquals(1, przyciety.getCommands().size());
    }

    @Test
    public void pruneNieRuszaOryginalu() {
        CommandIndex idx = new CommandIndex(Collections.singletonList(drzewo("op")));
        idx.prune(name -> false);
        assertEquals(1, idx.getCommands().size());
    }

    @Test
    public void modelJestNiemutowalnyOdZewnatrz() {
        List<CmdArg> args = new ArrayList<CmdArg>();
        args.add(new CmdArg("a", ArgType.WORD, null, null, null));
        CmdNode node = new CmdNode("lit", args, null, false, null);
        args.clear(); // nie ma prawa wplynac na node
        assertEquals(1, node.getArgs().size());
        try {
            node.getArgs().add(new CmdArg("b", ArgType.WORD, null, null, null));
            fail("lista argumentow ma byc niemodyfikowalna");
        } catch (UnsupportedOperationException expected) {
            // ok
        }
    }

    @Test
    public void nullListyStajaSiePuste() {
        CmdNode node = new CmdNode("lit", null, null, false, null);
        assertTrue(node.getArgs().isEmpty());
        assertTrue(node.getSub().isEmpty());
        CmdArg arg = new CmdArg("a", null, null, null, null);
        assertSame(ArgType.UNKNOWN, arg.getType());
        assertTrue(arg.getChoices().isEmpty());
    }
}
```

- [ ] **Step 2: Odpal, potwierdź że nie kompiluje**

```bash
./gradlew :commandsuggest:test --tests '*CommandIndexTest'
```

Oczekiwane: `FAILED`, `cannot find symbol: class CmdArg`.

- [ ] **Step 3: `CmdArg.java`**

```java
package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Jeden argument. Niemutowalny.
 *
 * <p>Spec mowil o {@code Map<String,Object> props}; tutaj sa konkretne pola, bo caly zbior
 * wlasciwosci to trzy pozycje i nie ma po co ich pakowac ani rzutowac. {@code min}/{@code max}
 * sa {@code Double} takze dla {@link ArgType#INT} — jedna para pol obsluguje oba typy liczbowe,
 * a wartosc calkowita miesci sie w double bez straty do 2^53.
 */
public final class CmdArg {

    private final String name;
    private final ArgType type;
    private final List<String> choices;
    private final Double min;
    private final Double max;

    public CmdArg(String name, ArgType type, List<String> choices, Double min, Double max) {
        this.name = name != null ? name : "arg";
        this.type = type != null ? type : ArgType.UNKNOWN;
        this.choices = choices == null || choices.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(choices));
        this.min = min;
        this.max = max;
    }

    public String getName() {
        return this.name;
    }

    public ArgType getType() {
        return this.type;
    }

    /** Niemodyfikowalna. Pusta dla wszystkiego poza {@link ArgType#CHOICE}. */
    public List<String> getChoices() {
        return this.choices;
    }

    /** {@code null} = brak dolnego ograniczenia. */
    public Double getMin() {
        return this.min;
    }

    /** {@code null} = brak gornego ograniczenia. */
    public Double getMax() {
        return this.max;
    }
}
```

- [ ] **Step 4: `CmdNode.java`**

```java
package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wezel drzewa: jeden literal, po nim uporzadkowana sekwencja argumentow, a po niej rozgalezienie
 * na kolejne literaly. Niemutowalny. Korzen ma {@code literal == null} i jest jedynym takim
 * wezlem w drzewie.
 */
public final class CmdNode {

    private final String literal;
    private final List<CmdArg> args;
    private final List<CmdNode> sub;
    private final boolean executable;
    private final String usage;

    public CmdNode(String literal, List<CmdArg> args, List<CmdNode> sub, boolean executable, String usage) {
        this.literal = literal;
        this.args = args == null || args.isEmpty()
                ? Collections.<CmdArg>emptyList()
                : Collections.unmodifiableList(new ArrayList<CmdArg>(args));
        this.sub = sub == null || sub.isEmpty()
                ? Collections.<CmdNode>emptyList()
                : Collections.unmodifiableList(new ArrayList<CmdNode>(sub));
        this.executable = executable;
        this.usage = usage;
    }

    /** {@code null} tylko w korzeniu. */
    public String getLiteral() {
        return this.literal;
    }

    public List<CmdArg> getArgs() {
        return this.args;
    }

    public List<CmdNode> getSub() {
        return this.sub;
    }

    /** Czy sciezka moze sie tu skonczyc. Dzis informacyjne; v2 uzyje tego do walidacji skladni. */
    public boolean isExecutable() {
        return this.executable;
    }

    /** Gotowa linia usage albo {@code null} — wtedy {@code SuggestionEngine} ja syntezuje. */
    public String getUsage() {
        return this.usage;
    }
}
```

- [ ] **Step 5: `CommandTree.java`**

```java
package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Jedna komenda: nazwa kanoniczna, aliasy i korzen. Niemutowalne. */
public final class CommandTree {

    private final String name;
    private final List<String> aliases;
    private final CmdNode root;

    public CommandTree(String name, List<String> aliases, CmdNode root) {
        this.name = name;
        this.aliases = aliases == null || aliases.isEmpty()
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(aliases));
        this.root = root != null ? root : new CmdNode(null, null, null, true, null);
    }

    public String getName() {
        return this.name;
    }

    public List<String> getAliases() {
        return this.aliases;
    }

    public CmdNode getRoot() {
        return this.root;
    }
}
```

- [ ] **Step 6: `CommandIndex.java`**

```java
package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Wszystkie komendy widoczne dla jednego gracza.
 *
 * <p>🚨 Spec mowil {@code CommandTree.prune(...)}, ale {@code CommandTree} to JEDNA komenda,
 * a przycinanie po uprawnieniach usuwa CALE komendy — dlatego metoda jest tutaj. Predykat
 * dostaje nazwe kanoniczna; {@code TreeBuilder} opakowuje w niego
 * {@code ICommand.checkPermission(server, sender)}, dzieki czemu sam mechanizm przycinania jest
 * testowalny bez ani jednej klasy Minecrafta.
 */
public final class CommandIndex {

    public static final CommandIndex EMPTY = new CommandIndex(Collections.<CommandTree>emptyList());

    private final List<CommandTree> commands;
    private final Map<String, CommandTree> byName;

    public CommandIndex(List<CommandTree> commands) {
        this.commands = commands == null || commands.isEmpty()
                ? Collections.<CommandTree>emptyList()
                : Collections.unmodifiableList(new ArrayList<CommandTree>(commands));
        Map<String, CommandTree> m = new HashMap<String, CommandTree>();
        for (CommandTree t : this.commands) {
            m.put(t.getName(), t);
        }
        for (CommandTree t : this.commands) {
            for (String alias : t.getAliases()) {
                // nazwa kanoniczna zawsze wygrywa z cudzym aliasem
                if (!m.containsKey(alias)) {
                    m.put(alias, t);
                }
            }
        }
        this.byName = Collections.unmodifiableMap(m);
    }

    public List<CommandTree> getCommands() {
        return this.commands;
    }

    /** Po nazwie kanonicznej albo po aliasie. {@code null} gdy nie ma. */
    public CommandTree byName(String name) {
        return name == null ? null : this.byName.get(name);
    }

    /**
     * Nazwy i aliasy, posortowane. Wanilia tez podpowiada aliasy — {@code CommandHandler} trzyma
     * je jako osobne klucze tej samej mapy — wiec robimy tak samo.
     */
    public List<String> allNames() {
        return new ArrayList<String>(new TreeSet<String>(this.byName.keySet()));
    }

    /** Nowy indeks bez komend, ktorych nazwa kanoniczna nie przechodzi predykatu. */
    public CommandIndex prune(Predicate<String> allowed) {
        List<CommandTree> kept = new ArrayList<CommandTree>();
        for (CommandTree t : this.commands) {
            if (allowed.test(t.getName())) {
                kept.add(t);
            }
        }
        return new CommandIndex(kept);
    }
}
```

- [ ] **Step 7: Odpal, potwierdź zielone**

```bash
./gradlew :commandsuggest:test --tests '*CommandIndexTest'
```

Oczekiwane: `BUILD SUCCESSFUL`, sześć testów `passed`.

- [ ] **Step 8: Commit**

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/core/ commandsuggest/src/test/java/com/spege/commandsuggest/core/CommandIndexTest.java
git commit -m "feat(commandsuggest): model drzewa i CommandIndex.prune"
```

---

## Task 5: `core` — `JsonTreeReader`

**Files:**
- Modify: `commandsuggest/build.gradle` (dwie linie z Gsonem)
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/JsonTreeReader.java`
- Test: `commandsuggest/src/test/java/com/spege/commandsuggest/core/JsonTreeReaderTest.java`

- [ ] **Step 1: Dołóż Gsona do build.gradle**

Gson jest w Minecrafcie (`com.google.code.gson:gson:2.8.0`), więc **nie pakujemy go do jara** — ale
konfiguracja `minecraft` nie trafia na classpath testów, a `core` ma się kompilować i testować bez
Minecrafta. Dlatego jawnie, w `dependencies` w `commandsuggest/build.gradle`, tuż pod linią
`testImplementation 'junit:junit:4.12'`:

```groovy
    // Gson dostarcza Minecraft w runtime - stad compileOnly, zeby NIE wladowac go do jara.
    // testImplementation, bo konfiguracja 'minecraft' nie jest na classpathie testow, a core
    // ma sie testowac bez Minecrafta. Wersja rowna tej, ktora wozi MC 1.12.2.
    compileOnly 'com.google.code.gson:gson:2.8.0'
    testImplementation 'com.google.code.gson:gson:2.8.0'
```

- [ ] **Step 2: Test, który nie przechodzi**

```java
package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public class JsonTreeReaderTest {

    private static final String SRPEVOLUTION =
            "{"
            + "\"command\": \"srpevolution\","
            + "\"aliases\": [\"srpevo\"],"
            + "\"sub\": ["
            + "  {\"lit\": \"set\", \"exec\": true, \"args\": ["
            + "      {\"name\": \"target\", \"type\": \"player\"},"
            + "      {\"name\": \"points\", \"type\": \"int\", \"min\": 0}"
            + "  ]},"
            + "  {\"lit\": \"info\", \"exec\": true}"
            + "]}";

    @Test
    public void czytaPrzykladZeSpecu() {
        CommandTree t = JsonTreeReader.read(SRPEVOLUTION);
        assertEquals("srpevolution", t.getName());
        assertEquals(Arrays.asList("srpevo"), t.getAliases());

        CmdNode root = t.getRoot();
        assertNull(root.getLiteral());
        assertTrue(root.getArgs().isEmpty());
        assertEquals(2, root.getSub().size());

        CmdNode set = root.getSub().get(0);
        assertEquals("set", set.getLiteral());
        assertTrue(set.isExecutable());
        assertEquals(2, set.getArgs().size());
        assertEquals("target", set.getArgs().get(0).getName());
        assertSame(ArgType.PLAYER, set.getArgs().get(0).getType());
        assertSame(ArgType.INT, set.getArgs().get(1).getType());
        assertEquals(Double.valueOf(0.0d), set.getArgs().get(1).getMin());
        assertNull(set.getArgs().get(1).getMax());

        CmdNode info = root.getSub().get(1);
        assertEquals("info", info.getLiteral());
        assertTrue(info.getArgs().isEmpty());
    }

    @Test
    public void brakPolaCommandToCzytelnyBlad() {
        try {
            JsonTreeReader.read("{\"sub\": []}");
            fail("mialo rzucic");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("command"));
        }
    }

    @Test
    public void popsutyJsonToTenSamWyjatek() {
        try {
            JsonTreeReader.read("to nie jest json");
            fail("mialo rzucic");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void nieznanyTypArgumentuNieWywalaPliku() {
        CommandTree t = JsonTreeReader.read(
                "{\"command\":\"x\",\"args\":[{\"name\":\"a\",\"type\":\"typ-z-przyszlosci\"}]}");
        assertSame(ArgType.UNKNOWN, t.getRoot().getArgs().get(0).getType());
    }

    @Test
    public void choicesIZakresy() {
        CommandTree t = JsonTreeReader.read(
                "{\"command\":\"x\",\"args\":[{\"name\":\"a\",\"type\":\"choice\","
                + "\"choices\":[\"tak\",\"nie\"]},"
                + "{\"name\":\"b\",\"type\":\"float\",\"min\":-1.5,\"max\":2.5}]}");
        assertEquals(Arrays.asList("tak", "nie"), t.getRoot().getArgs().get(0).getChoices());
        assertEquals(Double.valueOf(-1.5d), t.getRoot().getArgs().get(1).getMin());
        assertEquals(Double.valueOf(2.5d), t.getRoot().getArgs().get(1).getMax());
    }

    @Test
    public void subZagniezdzaSieDowolnieGleboko() {
        CommandTree t = JsonTreeReader.read(
                "{\"command\":\"x\",\"sub\":[{\"lit\":\"a\",\"sub\":[{\"lit\":\"b\",\"sub\":["
                + "{\"lit\":\"c\",\"exec\":true,\"usage\":\"/x a b c\"}]}]}]}");
        CmdNode c = t.getRoot().getSub().get(0).getSub().get(0).getSub().get(0);
        assertEquals("c", c.getLiteral());
        assertEquals("/x a b c", c.getUsage());
    }

    @Test
    public void brakiSaDomyslne() {
        CommandTree t = JsonTreeReader.read("{\"command\":\"x\"}");
        assertTrue(t.getAliases().isEmpty());
        assertTrue(t.getRoot().getArgs().isEmpty());
        assertTrue(t.getRoot().getSub().isEmpty());
        assertFalse(t.getRoot().isExecutable());
        assertNull(t.getRoot().getUsage());
    }
}
```

- [ ] **Step 3: Odpal, potwierdź że nie kompiluje**

```bash
./gradlew :commandsuggest:test --tests '*JsonTreeReaderTest'
```

Oczekiwane: `FAILED`, `cannot find symbol: class JsonTreeReader`.

- [ ] **Step 4: Implementacja**

```java
package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Czyta opis komendy z JSON-a. Format jest w specu, sekcja 5.
 *
 * <p>Czytane recznie z {@link JsonObject}, a nie przez wiazanie refleksyjne Gsona — dzieki temu
 * komunikat bledu mowi, ktorego pola brakuje, a model moze zostac niemutowalny z polami
 * {@code final}, z czym wiazanie refleksyjne sie klóci.
 *
 * <p>Nieznany identyfikator typu daje {@link ArgType#UNKNOWN} zamiast wyjatku: te pliki pisza
 * ludzie recznie, a literowka w jednym argumencie nie ma prawa wywalic calego opisu.
 */
public final class JsonTreeReader {

    private JsonTreeReader() {
    }

    /**
     * @throws IllegalArgumentException gdy to nie jest obiekt JSON albo brak pola {@code command}
     */
    public static CommandTree read(String json) {
        JsonObject o;
        try {
            JsonElement el = new JsonParser().parse(json);
            if (!el.isJsonObject()) {
                throw new IllegalArgumentException("opis komendy musi byc obiektem JSON");
            }
            o = el.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("niepoprawny JSON: " + e.getMessage(), e);
        }

        if (!o.has("command") || !o.get("command").isJsonPrimitive()) {
            throw new IllegalArgumentException("opis komendy nie ma pola 'command'");
        }
        String name = o.get("command").getAsString();

        List<String> aliases = new ArrayList<String>();
        if (o.has("aliases") && o.get("aliases").isJsonArray()) {
            for (JsonElement a : o.getAsJsonArray("aliases")) {
                aliases.add(a.getAsString());
            }
        }
        return new CommandTree(name, aliases, readNode(o, null));
    }

    /** {@code literal == null} tylko dla korzenia. */
    private static CmdNode readNode(JsonObject o, String literal) {
        List<CmdArg> args = new ArrayList<CmdArg>();
        if (o.has("args") && o.get("args").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("args")) {
                args.add(readArg(e.getAsJsonObject()));
            }
        }
        List<CmdNode> sub = new ArrayList<CmdNode>();
        if (o.has("sub") && o.get("sub").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("sub")) {
                JsonObject so = e.getAsJsonObject();
                String lit = so.has("lit") ? so.get("lit").getAsString() : "?";
                sub.add(readNode(so, lit));
            }
        }
        boolean exec = o.has("exec") && o.get("exec").getAsBoolean();
        String usage = o.has("usage") ? o.get("usage").getAsString() : null;
        return new CmdNode(literal, args, sub, exec, usage);
    }

    private static CmdArg readArg(JsonObject o) {
        String name = o.has("name") ? o.get("name").getAsString() : "arg";
        ArgType type = ArgType.byId(o.has("type") ? o.get("type").getAsString() : null);
        List<String> choices = null;
        if (o.has("choices") && o.get("choices").isJsonArray()) {
            choices = new ArrayList<String>();
            JsonArray arr = o.getAsJsonArray("choices");
            for (JsonElement e : arr) {
                choices.add(e.getAsString());
            }
        }
        Double min = o.has("min") ? Double.valueOf(o.get("min").getAsDouble()) : null;
        Double max = o.has("max") ? Double.valueOf(o.get("max").getAsDouble()) : null;
        return new CmdArg(name, type, choices, min, max);
    }
}
```

- [ ] **Step 5: Odpal, potwierdź zielone**

```bash
./gradlew :commandsuggest:test --tests '*JsonTreeReaderTest'
```

Oczekiwane: `BUILD SUCCESSFUL`, siedem testów `passed`.

- [ ] **Step 6: Commit**

```bash
git add commandsuggest/build.gradle commandsuggest/src/main/java/com/spege/commandsuggest/core/JsonTreeReader.java commandsuggest/src/test/java/com/spege/commandsuggest/core/JsonTreeReaderTest.java
git commit -m "feat(commandsuggest): JsonTreeReader - opis komendy z JSON"
```

---

## Task 6: `core` — `TreeCodec`, drzewo ⇄ bajty

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/VarInt.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/TreeCodec.java`
- Test: `commandsuggest/src/test/java/com/spege/commandsuggest/core/TreeCodecTest.java`

**Dlaczego to nie używa `ByteBuf`:** `core` ma się kompilować i testować bez Minecrafta, a `io.netty`
przychodzi razem z nim. Kodek pracuje więc na `DataOutputStream`/`ByteArrayOutputStream` z JDK,
a warstwa pakietu (zadanie 9) wpisuje gotowe `byte[]` do `ByteBuf`.

**Dlaczego test porównuje bajty, a nie obiekty:** równość strukturalna wymagałaby `equals`/`hashCode`
na czterech klasach modelu, czyli sporo kodu utrzymywanego wyłącznie dla testu. Zamiast tego
sprawdzamy własność round-tripu — `encode(decode(encode(x))) == encode(x)` bajt w bajt — plus
punktowe asercje na odkodowanej strukturze. Pula stringów jest budowana w kolejności obchodzenia
drzewa, więc ponowne zakodowanie daje identyczną pulę; ta determinacja jest tym, co czyni
porównanie bajtów sensownym.

- [ ] **Step 1: Test, który nie przechodzi**

```java
package com.spege.commandsuggest.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class TreeCodecTest {

    private static CommandIndex przykladowyIndeks() {
        CmdArg target = new CmdArg("target", ArgType.PLAYER, null, null, null);
        CmdArg points = new CmdArg("points", ArgType.INT, null, Double.valueOf(0.0d), Double.valueOf(99.0d));
        CmdNode set = new CmdNode("set", Arrays.asList(target, points), null, true, null);
        CmdArg rule = new CmdArg("rule", ArgType.CHOICE, Arrays.asList("doFireTick", "keepInventory"), null, null);
        CmdNode gamerule = new CmdNode(null, Collections.singletonList(rule), null, true, "/gamerule <rule>");
        return new CommandIndex(Arrays.asList(
                new CommandTree("srpevolution", Arrays.asList("srpevo"),
                        new CmdNode(null, null, Collections.singletonList(set), false, null)),
                new CommandTree("gamerule", null, gamerule)));
    }

    @Test
    public void varIntPrzezywaRoundTrip() throws Exception {
        int[] wartosci = { 0, 1, 127, 128, 255, 300, 16383, 16384, 1 << 20, Integer.MAX_VALUE };
        for (int v : wartosci) {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(bos);
            VarInt.write(out, v);
            out.flush();
            java.io.DataInputStream in =
                    new java.io.DataInputStream(new java.io.ByteArrayInputStream(bos.toByteArray()));
            assertEquals(v, VarInt.read(in));
        }
    }

    @Test
    public void roundTripZachowujeStrukture() {
        CommandIndex oryginal = przykladowyIndeks();
        CommandIndex odkodowany = TreeCodec.decode(TreeCodec.encode(oryginal));

        assertEquals(2, odkodowany.getCommands().size());
        CommandTree srp = odkodowany.byName("srpevo");
        assertEquals("srpevolution", srp.getName());
        assertEquals(Arrays.asList("srpevo"), srp.getAliases());

        CmdNode set = srp.getRoot().getSub().get(0);
        assertEquals("set", set.getLiteral());
        assertTrue(set.isExecutable());
        assertSame(ArgType.PLAYER, set.getArgs().get(0).getType());
        assertEquals("points", set.getArgs().get(1).getName());
        assertEquals(Double.valueOf(0.0d), set.getArgs().get(1).getMin());
        assertEquals(Double.valueOf(99.0d), set.getArgs().get(1).getMax());

        CommandTree gr = odkodowany.byName("gamerule");
        assertEquals("/gamerule <rule>", gr.getRoot().getUsage());
        assertEquals(Arrays.asList("doFireTick", "keepInventory"), gr.getRoot().getArgs().get(0).getChoices());
        assertNull(gr.getRoot().getArgs().get(0).getMin());
        assertTrue(gr.getAliases().isEmpty());
    }

    @Test
    public void ponowneZakodowanieDajeIdentyczneBajty() {
        byte[] raz = TreeCodec.encode(przykladowyIndeks());
        byte[] dwa = TreeCodec.encode(TreeCodec.decode(raz));
        assertArrayEquals(raz, dwa);
    }

    @Test
    public void pustyIndeksTezPrzechodzi() {
        CommandIndex pusty = TreeCodec.decode(TreeCodec.encode(CommandIndex.EMPTY));
        assertTrue(pusty.getCommands().isEmpty());
    }

    @Test
    public void duzeDrzewoIdzieGzipemIWracaCaleTo() {
        List<CommandTree> duzo = new ArrayList<CommandTree>();
        for (int i = 0; i < 400; i++) {
            CmdArg a = new CmdArg("argument-o-dlugiej-nazwie-" + i, ArgType.WORD, null, null, null);
            duzo.add(new CommandTree("komenda-numer-" + i, Collections.singletonList("alias-" + i),
                    new CmdNode(null, Collections.singletonList(a), null, true,
                            "/komenda-numer-" + i + " <argument>")));
        }
        CommandIndex idx = new CommandIndex(duzo);
        byte[] bajty = TreeCodec.encode(idx);
        assertEquals("powyzej progu ma byc flaga gzipa", 1, bajty[0]);

        CommandIndex odkodowany = TreeCodec.decode(bajty);
        assertEquals(400, odkodowany.getCommands().size());
        assertEquals("/komenda-numer-399 <argument>",
                odkodowany.byName("komenda-numer-399").getRoot().getUsage());
    }

    @Test
    public void maleDrzewoIdzieBezGzipa() {
        byte[] bajty = TreeCodec.encode(przykladowyIndeks());
        assertEquals("ponizej progu bez gzipa", 0, bajty[0]);
    }
}
```

- [ ] **Step 2: Odpal, potwierdź że nie kompiluje**

```bash
./gradlew :commandsuggest:test --tests '*TreeCodecTest'
```

Oczekiwane: `FAILED`, `cannot find symbol: class VarInt`.

- [ ] **Step 3: `VarInt.java`**

```java
package com.spege.commandsuggest.core;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Liczba calkowita w zapisie zmiennej dlugosci, taka sama jak w protokole Minecrafta.
 * Wlasna, a nie {@code PacketBuffer}, bo {@code core} nie ma prawa znac klas Minecrafta.
 */
public final class VarInt {

    private VarInt() {
    }

    public static void write(DataOutput out, int value) throws IOException {
        int v = value;
        while ((v & 0xFFFFFF80) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v & 0x7F);
    }

    public static int read(DataInput in) throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            byte b = in.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt dluzszy niz piec bajtow");
            }
        }
    }
}
```

- [ ] **Step 4: `TreeCodec.java`**

```java
package com.spege.commandsuggest.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Drzewo komend do bajtow i z powrotem, z pula stringow i opcjonalnym gzipem.
 *
 * <p>Uklad: jeden bajt flagi (0 = surowo, 1 = gzip), a dalej — po ewentualnej dekompresji —
 * pula stringow (varint licznik + {@code writeUTF}), potem komendy. Pula placi za siebie od razu:
 * identyfikatory typow powtarzaja sie w kazdym argumencie, a nazwy komend w aliasach.
 *
 * <p>Identyfikator typu idzie jako STRING z puli, nie jako {@code ordinal()} — dopisanie
 * wartosci do {@link ArgType} nie ma prawa uniewaznic pakietu ani opisow na dysku.
 */
public final class TreeCodec {

    /** Ponizej tego rozmiaru gzip kosztuje wiecej, niz oszczedza. */
    private static final int GZIP_THRESHOLD = 4096;

    private static final int NODE_HAS_LITERAL = 1;
    private static final int NODE_EXECUTABLE = 2;
    private static final int NODE_HAS_USAGE = 4;

    private static final int ARG_HAS_MIN = 1;
    private static final int ARG_HAS_MAX = 2;
    private static final int ARG_HAS_CHOICES = 4;

    private TreeCodec() {
    }

    public static byte[] encode(CommandIndex index) {
        try {
            Map<String, Integer> pool = new LinkedHashMap<String, Integer>();
            for (CommandTree t : index.getCommands()) {
                intern(pool, t.getName());
                for (String a : t.getAliases()) {
                    intern(pool, a);
                }
                collectNode(t.getRoot(), pool);
            }

            ByteArrayOutputStream body = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(body);
            VarInt.write(out, pool.size());
            for (String s : pool.keySet()) {
                out.writeUTF(s);
            }
            VarInt.write(out, index.getCommands().size());
            for (CommandTree t : index.getCommands()) {
                VarInt.write(out, pool.get(t.getName()).intValue());
                VarInt.write(out, t.getAliases().size());
                for (String a : t.getAliases()) {
                    VarInt.write(out, pool.get(a).intValue());
                }
                writeNode(out, t.getRoot(), pool);
            }
            out.flush();
            byte[] payload = body.toByteArray();

            ByteArrayOutputStream result = new ByteArrayOutputStream(payload.length + 1);
            if (payload.length >= GZIP_THRESHOLD) {
                result.write(1);
                GZIPOutputStream gz = new GZIPOutputStream(result);
                gz.write(payload);
                gz.close();
            } else {
                result.write(0);
                result.write(payload);
            }
            return result.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("nie udalo sie zakodowac drzewa komend", e);
        }
    }

    public static CommandIndex decode(byte[] data) {
        try {
            if (data == null || data.length < 1) {
                return CommandIndex.EMPTY;
            }
            ByteArrayInputStream raw = new ByteArrayInputStream(data);
            int flag = raw.read();
            DataInputStream in = new DataInputStream(flag == 1 ? new GZIPInputStream(raw) : raw);

            int poolSize = VarInt.read(in);
            String[] pool = new String[poolSize];
            for (int i = 0; i < poolSize; i++) {
                pool[i] = in.readUTF();
            }
            int count = VarInt.read(in);
            List<CommandTree> trees = new ArrayList<CommandTree>(count);
            for (int i = 0; i < count; i++) {
                String name = pool[VarInt.read(in)];
                int aliasCount = VarInt.read(in);
                List<String> aliases = new ArrayList<String>(aliasCount);
                for (int a = 0; a < aliasCount; a++) {
                    aliases.add(pool[VarInt.read(in)]);
                }
                trees.add(new CommandTree(name, aliases, readNode(in, pool)));
            }
            return new CommandIndex(trees);
        } catch (IOException e) {
            throw new IllegalStateException("nie udalo sie odkodowac drzewa komend", e);
        }
    }

    private static void intern(Map<String, Integer> pool, String s) {
        if (!pool.containsKey(s)) {
            pool.put(s, Integer.valueOf(pool.size()));
        }
    }

    /** Kolejnosc MUSI byc ta sama, co w {@link #writeNode} — inaczej indeksy sie rozjada. */
    private static void collectNode(CmdNode n, Map<String, Integer> pool) {
        if (n.getLiteral() != null) {
            intern(pool, n.getLiteral());
        }
        if (n.getUsage() != null) {
            intern(pool, n.getUsage());
        }
        for (CmdArg a : n.getArgs()) {
            intern(pool, a.getName());
            intern(pool, a.getType().getId());
            for (String c : a.getChoices()) {
                intern(pool, c);
            }
        }
        for (CmdNode s : n.getSub()) {
            collectNode(s, pool);
        }
    }

    private static void writeNode(DataOutputStream out, CmdNode n, Map<String, Integer> pool)
            throws IOException {
        int flags = 0;
        if (n.getLiteral() != null) {
            flags |= NODE_HAS_LITERAL;
        }
        if (n.isExecutable()) {
            flags |= NODE_EXECUTABLE;
        }
        if (n.getUsage() != null) {
            flags |= NODE_HAS_USAGE;
        }
        out.writeByte(flags);
        if (n.getLiteral() != null) {
            VarInt.write(out, pool.get(n.getLiteral()).intValue());
        }
        if (n.getUsage() != null) {
            VarInt.write(out, pool.get(n.getUsage()).intValue());
        }
        VarInt.write(out, n.getArgs().size());
        for (CmdArg a : n.getArgs()) {
            writeArg(out, a, pool);
        }
        VarInt.write(out, n.getSub().size());
        for (CmdNode s : n.getSub()) {
            writeNode(out, s, pool);
        }
    }

    private static CmdNode readNode(DataInputStream in, String[] pool) throws IOException {
        int flags = in.readByte();
        String literal = (flags & NODE_HAS_LITERAL) != 0 ? pool[VarInt.read(in)] : null;
        String usage = (flags & NODE_HAS_USAGE) != 0 ? pool[VarInt.read(in)] : null;
        int argCount = VarInt.read(in);
        List<CmdArg> args = new ArrayList<CmdArg>(argCount);
        for (int i = 0; i < argCount; i++) {
            args.add(readArg(in, pool));
        }
        int subCount = VarInt.read(in);
        List<CmdNode> sub = new ArrayList<CmdNode>(subCount);
        for (int i = 0; i < subCount; i++) {
            sub.add(readNode(in, pool));
        }
        return new CmdNode(literal, args, sub, (flags & NODE_EXECUTABLE) != 0, usage);
    }

    private static void writeArg(DataOutputStream out, CmdArg a, Map<String, Integer> pool)
            throws IOException {
        VarInt.write(out, pool.get(a.getName()).intValue());
        VarInt.write(out, pool.get(a.getType().getId()).intValue());
        int flags = 0;
        if (a.getMin() != null) {
            flags |= ARG_HAS_MIN;
        }
        if (a.getMax() != null) {
            flags |= ARG_HAS_MAX;
        }
        if (!a.getChoices().isEmpty()) {
            flags |= ARG_HAS_CHOICES;
        }
        out.writeByte(flags);
        if (a.getMin() != null) {
            out.writeDouble(a.getMin().doubleValue());
        }
        if (a.getMax() != null) {
            out.writeDouble(a.getMax().doubleValue());
        }
        if (!a.getChoices().isEmpty()) {
            VarInt.write(out, a.getChoices().size());
            for (String c : a.getChoices()) {
                VarInt.write(out, pool.get(c).intValue());
            }
        }
    }

    private static CmdArg readArg(DataInputStream in, String[] pool) throws IOException {
        String name = pool[VarInt.read(in)];
        ArgType type = ArgType.byId(pool[VarInt.read(in)]);
        int flags = in.readByte();
        Double min = (flags & ARG_HAS_MIN) != 0 ? Double.valueOf(in.readDouble()) : null;
        Double max = (flags & ARG_HAS_MAX) != 0 ? Double.valueOf(in.readDouble()) : null;
        List<String> choices = null;
        if ((flags & ARG_HAS_CHOICES) != 0) {
            int n = VarInt.read(in);
            choices = new ArrayList<String>(n);
            for (int i = 0; i < n; i++) {
                choices.add(pool[VarInt.read(in)]);
            }
        }
        return new CmdArg(name, type, choices, min, max);
    }
}
```

- [ ] **Step 5: Odpal, potwierdź zielone**

```bash
./gradlew :commandsuggest:test --tests '*TreeCodecTest'
```

Oczekiwane: `BUILD SUCCESSFUL`, sześć testów `passed`.

- [ ] **Step 6: Commit**

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/core/VarInt.java commandsuggest/src/main/java/com/spege/commandsuggest/core/TreeCodec.java commandsuggest/src/test/java/com/spege/commandsuggest/core/TreeCodecTest.java
git commit -m "feat(commandsuggest): TreeCodec - drzewo do bajtow, pula stringow, gzip"
```

---

## Task 7: `core` — `SuggestionEngine`

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/Suggestion.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/Suggestions.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/core/SuggestionEngine.java`
- Test: `commandsuggest/src/test/java/com/spege/commandsuggest/core/SuggestionEngineTest.java`

**Podział pracy, który tu zapada.** `core` rozwiązuje tylko to, co siedzi w samych danych: nazwy
komend, literały z `sub`, `choice` i `bool`. Wartości typu `player` czy `item` wymagają rejestrów
klienta, których `core` nie zna — więc zamiast ich listy zwraca **typ edytowanego argumentu**
w `Suggestions.getArgType()`, a warstwa klienta (zadanie 14) go rozwija. Gdy typ jest
`isServerResolved()`, klient zamiast tego strzela `CPacketTabComplete`.

- [ ] **Step 1: Test, który nie przechodzi**

```java
package com.spege.commandsuggest.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class SuggestionEngineTest {

    private static CommandIndex indeks() {
        // /gamerule <choice rule> <bool value>
        CmdArg rule = new CmdArg("rule", ArgType.CHOICE,
                Arrays.asList("doFireTick", "doDaylightCycle", "keepInventory"), null, null);
        CmdArg value = new CmdArg("value", ArgType.BOOL, null, null, null);
        CommandTree gamerule = new CommandTree("gamerule", null,
                new CmdNode(null, Arrays.asList(rule, value), null, true, null));

        // /cs reload | /cs give <player> <item>
        CmdNode reload = new CmdNode("reload", null, null, true, null);
        CmdNode give = new CmdNode("give",
                Arrays.asList(new CmdArg("who", ArgType.PLAYER, null, null, null),
                        new CmdArg("what", ArgType.ITEM, null, null, null)),
                null, true, null);
        CommandTree cs = new CommandTree("commandsuggest", Arrays.asList("cs"),
                new CmdNode(null, null, Arrays.asList(reload, give), false, null));

        // nieopisana komenda z generycznym ogonem
        CommandTree obca = new CommandTree("obcamoda", null,
                new CmdNode(null,
                        Collections.singletonList(new CmdArg("args", ArgType.GREEDY, null, null, null)),
                        null, true, "/obcamoda <args>"));

        return new CommandIndex(Arrays.asList(gamerule, cs, obca));
    }

    private static Suggestions dla(String linia) {
        return SuggestionEngine.suggest(indeks(), InputParser.parse(linia, linia.length()));
    }

    private static List<String> teksty(Suggestions s) {
        List<String> out = new ArrayList<String>();
        for (Suggestion x : s.getItems()) {
            out.add(x.getText());
        }
        return out;
    }

    @Test
    public void zwyklyTekstNiczegoNiePodpowiada() {
        Suggestions s = SuggestionEngine.suggest(indeks(), InputParser.parse("czesc", 5));
        assertTrue(s.getItems().isEmpty());
        assertFalse(s.needsServerQuery());
    }

    @Test
    public void samUkosnikDajeWszystkieNazwyIAliasy() {
        assertEquals(Arrays.asList("commandsuggest", "cs", "gamerule", "obcamoda"), teksty(dla("/")));
    }

    @Test
    public void prefiksFiltrujeNazwyBezWzgleduNaWielkoscLiter() {
        assertEquals(Arrays.asList("gamerule"), teksty(dla("/GaMe")));
    }

    @Test
    public void nieznanaKomendaOddajeSprawaSerwerowi() {
        Suggestions s = dla("/cosnieznanego cos");
        assertTrue(s.getItems().isEmpty());
        assertTrue(s.needsServerQuery());
    }

    @Test
    public void choiceRozwiazujeSieNaMiejscu() {
        Suggestions s = dla("/gamerule do");
        assertEquals(Arrays.asList("doFireTick", "doDaylightCycle"), teksty(s));
        assertSame(ArgType.CHOICE, s.getArgType());
        assertFalse(s.needsServerQuery());
        assertEquals(10, s.getReplaceStart());
    }

    @Test
    public void boolTezRozwiazujeSieNaMiejscu() {
        Suggestions s = dla("/gamerule keepInventory ");
        assertEquals(Arrays.asList("true", "false"), teksty(s));
        assertSame(ArgType.BOOL, s.getArgType());
    }

    @Test
    public void literalyZSubSaPodpowiadane() {
        Suggestions s = dla("/cs ");
        assertEquals(Arrays.asList("reload", "give"), teksty(s));
        assertNull("literal nie ma typu argumentu", s.getArgType());
    }

    @Test
    public void poWejsciuWLiteralIdziemyWJegoArgumenty() {
        Suggestions s = dla("/cs give ");
        assertTrue("player rozwiazuje klient, nie core", s.getItems().isEmpty());
        assertSame(ArgType.PLAYER, s.getArgType());
        assertFalse("player jest lokalny, zadnego pakietu", s.needsServerQuery());
    }

    @Test
    public void drugiArgumentPoLiterale() {
        Suggestions s = dla("/cs give Steve ");
        assertSame(ArgType.ITEM, s.getArgType());
    }

    @Test
    public void greedyZjadaWszystkoIPytaSerwer() {
        Suggestions s = dla("/obcamoda cokolwiek dalej ");
        assertSame(ArgType.GREEDY, s.getArgType());
        assertTrue(s.needsServerQuery());
        assertEquals("/obcamoda <args>", s.getUsage());
    }

    @Test
    public void nieznanyLiteralNieStrzelaDoSerwera() {
        Suggestions s = dla("/cs nieistniejacy ");
        assertTrue(s.getItems().isEmpty());
        assertFalse("drzewo znamy, wiec wiemy ze nie ma czego podpowiadac", s.needsServerQuery());
    }

    @Test
    public void wyczerpanaSciezkaNiczegoNieProponuje() {
        Suggestions s = dla("/cs reload ");
        assertTrue(s.getItems().isEmpty());
        assertFalse(s.needsServerQuery());
    }

    @Test
    public void usageJestSyntezowaneGdyOpisGoNieMa() {
        assertEquals("/gamerule <rule> <value>", dla("/gamerule do").getUsage());
        assertEquals("/commandsuggest give <who> <what>", dla("/cs give ").getUsage());
    }
}
```

- [ ] **Step 2: Odpal, potwierdź że nie kompiluje**

```bash
./gradlew :commandsuggest:test --tests '*SuggestionEngineTest'
```

Oczekiwane: `FAILED`, `cannot find symbol: class SuggestionEngine`.

- [ ] **Step 3: `Suggestion.java`**

```java
package com.spege.commandsuggest.core;

/** Jedna pozycja na liscie. {@code type == null} znaczy literal (nazwa komendy albo podkomenda). */
public final class Suggestion {

    private final String text;
    private final ArgType type;

    public Suggestion(String text, ArgType type) {
        this.text = text;
        this.type = type;
    }

    public String getText() {
        return this.text;
    }

    /** {@code null} dla literalu — klient koloruje go inaczej niz wartosc argumentu. */
    public ArgType getType() {
        return this.type;
    }
}
```

- [ ] **Step 4: `Suggestions.java`**

```java
package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Wynik jednego przeliczenia podpowiedzi. Niemutowalny. */
public final class Suggestions {

    public static final Suggestions EMPTY =
            new Suggestions(null, null, "", 0, null);

    private final List<Suggestion> items;
    private final ArgType argType;
    private final String prefix;
    private final int replaceStart;
    private final String usage;

    public Suggestions(List<Suggestion> items, ArgType argType, String prefix, int replaceStart,
            String usage) {
        this.items = items == null || items.isEmpty()
                ? Collections.<Suggestion>emptyList()
                : Collections.unmodifiableList(new ArrayList<Suggestion>(items));
        this.argType = argType;
        this.prefix = prefix != null ? prefix : "";
        this.replaceStart = replaceStart;
        this.usage = usage;
    }

    /** Juz rozwiazane pozycje: nazwy komend, literaly, {@code choice}, {@code bool}. */
    public List<Suggestion> getItems() {
        return this.items;
    }

    /**
     * Typ edytowanego argumentu albo {@code null}, gdy edytowany jest literal lub nie ma czego
     * podpowiadac. Klient uzywa tego, zeby dolozyc wartosci z rejestrow — {@code core} ich nie zna.
     */
    public ArgType getArgType() {
        return this.argType;
    }

    /** Tresc edytowanego tokenu. */
    public String getPrefix() {
        return this.prefix;
    }

    /** Offset w linii czatu, od ktorego podmieniamy tekst po zatwierdzeniu. */
    public int getReplaceStart() {
        return this.replaceStart;
    }

    /** Linia pod lista albo {@code null}. */
    public String getUsage() {
        return this.usage;
    }

    /** Czy klient ma wyslac waniliowy {@code CPacketTabComplete}. */
    public boolean needsServerQuery() {
        return this.argType != null && this.argType.isServerResolved();
    }
}
```

- [ ] **Step 5: `SuggestionEngine.java`**

```java
package com.spege.commandsuggest.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Spacer po drzewie: z podzielonej linii robi liste podpowiedzi.
 *
 * <p>Rozwiazuje wylacznie to, co siedzi w danych — nazwy komend, literaly {@code sub},
 * {@code choice} i {@code bool}. Dla wszystkiego innego oddaje TYP argumentu i zostawia
 * rozwiniecie warstwie klienta (rejestry) albo serwerowi (waniliowy tab-complete).
 *
 * <p>🚨 Nieznany literal NIE pyta serwera. Skoro mamy opis tej komendy, to wiemy, ze nie ma tam
 * czego podpowiadac — strzal w serwer dolozylby tylko obcinanie listy przez VintageFix i linijke
 * smiecia na czacie. Serwera pytamy tylko wtedy, gdy naprawde nie wiemy: komenda spoza drzewa
 * albo argument typu {@code word}/{@code greedy}/{@code unknown}.
 */
public final class SuggestionEngine {

    private static final List<String> BOOLEANY =
            Collections.unmodifiableList(Arrays.asList("true", "false"));

    private SuggestionEngine() {
    }

    public static Suggestions suggest(CommandIndex index, ParsedInput in) {
        if (index == null || in == null || !in.isCommand()) {
            return Suggestions.EMPTY;
        }
        String[] tokens = in.getTokens();
        String prefix = in.getPrefix();
        int start = in.getPrefixStart();
        int editIndex = in.getEditIndex();

        if (editIndex == 0) {
            List<Suggestion> items = new ArrayList<Suggestion>();
            for (String name : index.allNames()) {
                if (matches(name, prefix)) {
                    items.add(new Suggestion(name, null));
                }
            }
            return new Suggestions(items, null, prefix, start, null);
        }

        CommandTree tree = index.byName(tokens[0]);
        if (tree == null) {
            // komendy nie znamy - jedyne, co zostaje, to zapytac serwer
            return new Suggestions(null, ArgType.UNKNOWN, prefix, start, null);
        }

        StringBuilder path = new StringBuilder("/").append(tree.getName());
        CmdNode node = tree.getRoot();
        int i = 1;

        while (true) {
            for (CmdArg arg : node.getArgs()) {
                if (arg.getType() == ArgType.GREEDY || i == editIndex) {
                    return forArg(arg, prefix, start, usageOf(node, path));
                }
                i++;
            }
            if (node.getSub().isEmpty()) {
                return new Suggestions(null, null, prefix, start, usageOf(node, path));
            }
            if (i == editIndex) {
                List<Suggestion> items = new ArrayList<Suggestion>();
                for (CmdNode s : node.getSub()) {
                    if (matches(s.getLiteral(), prefix)) {
                        items.add(new Suggestion(s.getLiteral(), null));
                    }
                }
                return new Suggestions(items, null, prefix, start, usageOf(node, path));
            }
            CmdNode child = findChild(node.getSub(), tokens[i]);
            if (child == null) {
                return new Suggestions(null, null, prefix, start, usageOf(node, path));
            }
            path.append(' ').append(child.getLiteral());
            node = child;
            i++;
        }
    }

    private static Suggestions forArg(CmdArg arg, String prefix, int start, String usage) {
        List<String> zrodlo = null;
        if (arg.getType() == ArgType.CHOICE) {
            zrodlo = arg.getChoices();
        } else if (arg.getType() == ArgType.BOOL) {
            zrodlo = BOOLEANY;
        }
        if (zrodlo == null) {
            return new Suggestions(null, arg.getType(), prefix, start, usage);
        }
        List<Suggestion> items = new ArrayList<Suggestion>();
        for (String v : zrodlo) {
            if (matches(v, prefix)) {
                items.add(new Suggestion(v, arg.getType()));
            }
        }
        return new Suggestions(items, arg.getType(), prefix, start, usage);
    }

    private static CmdNode findChild(List<CmdNode> sub, String literal) {
        for (CmdNode s : sub) {
            if (s.getLiteral() != null && s.getLiteral().equalsIgnoreCase(literal)) {
                return s;
            }
        }
        return null;
    }

    /** Gotowy usage z opisu, a jak go nie ma — sklejony ze sciezki literalow i nazw argumentow. */
    private static String usageOf(CmdNode node, StringBuilder path) {
        if (node.getUsage() != null) {
            return node.getUsage();
        }
        if (node.getArgs().isEmpty()) {
            return path.toString();
        }
        StringBuilder sb = new StringBuilder(path);
        for (CmdArg a : node.getArgs()) {
            sb.append(" <").append(a.getName()).append('>');
        }
        return sb.toString();
    }

    private static boolean matches(String candidate, String prefix) {
        if (candidate == null) {
            return false;
        }
        if (prefix.isEmpty()) {
            return true;
        }
        return candidate.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
```

- [ ] **Step 6: Odpal cały pakiet `core`, potwierdź zielone**

```bash
./gradlew :commandsuggest:test
```

Oczekiwane: `BUILD SUCCESSFUL`, wszystkie testy `passed` (ArgType 4, InputParser 9, CommandIndex 6, JsonTreeReader 7, TreeCodec 6, SuggestionEngine 13 — razem 45).

- [ ] **Step 7: Commit**

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/core/Suggestion.java commandsuggest/src/main/java/com/spege/commandsuggest/core/Suggestions.java commandsuggest/src/main/java/com/spege/commandsuggest/core/SuggestionEngine.java commandsuggest/src/test/java/com/spege/commandsuggest/core/SuggestionEngineTest.java
git commit -m "feat(commandsuggest): SuggestionEngine - spacer po drzewie, core kompletny"
```

> **Koniec warstwy `core`.** Od tego miejsca kończą się testy jednostkowe — wszystko poniżej dotyka
> Minecrafta i weryfikuje się kompilacją plus scenariuszem ręcznym z zadania 18. Nie pisz atrap
> `Minecraft.getMinecraft()`; to nie jest kod, który da się sensownie odpalić poza grą.

---

## Task 8: Konfiguracja

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/config/CommandSuggestConfig.java`

- [ ] **Step 1: Napisz config**

```java
package com.spege.commandsuggest.config;

import com.spege.commandsuggest.CommandSuggest;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Siedem pol w kategorii {@code general} pliku {@code config/commandsuggest.cfg}.
 *
 * <p>🚨 {@code category = "general"}, a NIE {@code ""}. Pusta kategoria znaczy "kazde pole tej
 * klasy jest samo w sobie kategoria" i jest twardym crashem dla pol prostych:
 * {@code ConfigManager.sync} rzuca wtedy
 * {@code "An empty category may not contain anything but objects representing categories!"}
 * juz przy konstruowaniu moda. Siostrzane mody w tym repo maja {@code ""}, bo maja pola-kategorie —
 * tutaj tego nie kopiuj (tak samo jak w {@code enchanteraser}).
 *
 * <p>Zadne z tych pol nie bramkuje rejestracji handlerow — {@code Enabled} jest wczesnym powrotem
 * w srodku handlera — wiec nic tu nie ma {@code @Config.RequiresMcRestart}: wszystko czytane na zywo.
 */
@Config(modid = CommandSuggest.MODID, name = CommandSuggest.MODID, category = "general")
public class CommandSuggestConfig {

    @Config.Name("Enabled")
    @Config.Comment({
            "Master switch. Off = the chat behaves exactly like vanilla, including the",
            "comma-separated completion list printed into chat on TAB. Read live." })
    public static boolean enabled = true;

    @Config.Name("Max Visible Rows")
    @Config.Comment("How many suggestions the popup shows at once; the rest scroll. Read live.")
    @Config.RangeInt(min = 1, max = 20)
    public static int maxVisibleRows = 10;

    @Config.Name("Colour Arguments By Type")
    @Config.Comment({
            "Colour each suggestion by the argument type it belongs to. Off = everything is",
            "plain grey. Read live." })
    public static boolean colourArgumentsByType = true;

    @Config.Name("Show Usage Line")
    @Config.Comment("Show the usage line under the suggestion list. Read live.")
    public static boolean showUsageLine = true;

    @Config.Name("Recompute Debounce Ms")
    @Config.Comment({
            "Wait this long after the last keystroke before recomputing suggestions.",
            "0 recomputes on every change. Read live." })
    @Config.RangeInt(min = 0, max = 1000)
    public static int recomputeDebounceMs = 50;

    @Config.Name("Handshake Timeout Ticks")
    @Config.Comment({
            "How long after joining to wait for the server's command tree before deciding the",
            "server does not have this mod and falling back to vanilla tab-completion.",
            "20 ticks = 1 second. Read live." })
    @Config.RangeInt(min = 20, max = 600)
    public static int handshakeTimeoutTicks = 100;

    @Config.Name("Log Handshake Mode")
    @Config.Comment({
            "Log one INFO line per session saying which mode started and why.",
            "Leave on - it is the cheapest way to answer 'why are there no types here'." })
    public static boolean logHandshakeMode = true;

    @Mod.EventBusSubscriber(modid = CommandSuggest.MODID)
    public static class EventHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (CommandSuggest.MODID.equals(event.getModID())) {
                ConfigManager.sync(CommandSuggest.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
```

- [ ] **Step 2: Zbuduj**

```bash
./gradlew :commandsuggest:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/config/CommandSuggestConfig.java
git commit -m "feat(commandsuggest): config - siedem pol w kategorii general"
```

---

## Task 9: Kanał, pakiet i cache drzewa po stronie klienta

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/net/PacketHandler.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/net/S2CCommandTree.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/client/net/TreeApplier.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/client/ClientTreeCache.java`
- Modify: `commandsuggest/src/main/java/com/spege/commandsuggest/CommonProxy.java`
- Modify: `commandsuggest/src/main/java/com/spege/commandsuggest/client/ClientProxy.java`

- [ ] **Step 1: `PacketHandler.java`**

```java
package com.spege.commandsuggest.net;

import com.spege.commandsuggest.CommandSuggest;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Jeden kanal, jeden pakiet. Rejestrowany z {@code CommonProxy.preInit}, po obu stronach. */
public final class PacketHandler {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(CommandSuggest.MODID);

    private PacketHandler() {
    }

    public static void register() {
        CHANNEL.registerMessage(S2CCommandTree.Handler.class, S2CCommandTree.class, 0, Side.CLIENT);
    }
}
```

- [ ] **Step 2: `S2CCommandTree.java`**

```java
package com.spege.commandsuggest.net;

import com.spege.commandsuggest.client.net.TreeApplier;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Drzewo komend przefiltrowane pod uprawnienia jednego gracza, zakodowane przez
 * {@code TreeCodec} (wlasna flaga gzipa w srodku, wiec tutaj to zwykla tablica bajtow).
 */
public class S2CCommandTree implements IMessage {

    private byte[] payload;

    /** Wymagany przez {@code SimpleNetworkWrapper} — wola go refleksja. */
    public S2CCommandTree() {
    }

    public S2CCommandTree(byte[] payload) {
        this.payload = payload;
    }

    public byte[] getPayload() {
        return this.payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = ByteBufUtils.readVarInt(buf, 5);
        this.payload = new byte[len];
        buf.readBytes(this.payload);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, this.payload.length, 5);
        buf.writeBytes(this.payload);
    }

    /**
     * 🚨 Ta klasa NIE MOZE miec {@code @SideOnly}. {@code registerMessage} wola
     * {@code newInstance()} natychmiast po OBU stronach — koncowy argument {@code Side} wybiera
     * tylko, ktora strona przetwarza wiadomosc, i niczego nie bramkuje. Cala robota kliencka
     * siedzi wiec za jednym {@code invokestatic} do klasy oznaczonej {@code @SideOnly}, ktora
     * weryfikator rozwiaze dopiero przy pierwszym wykonaniu — i tylko na kliencie.
     */
    public static class Handler implements IMessageHandler<S2CCommandTree, IMessage> {

        @Override
        public IMessage onMessage(S2CCommandTree message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            TreeApplier.apply(message.getPayload());
            return null;
        }
    }
}
```

- [ ] **Step 3: `TreeApplier.java`**

```java
package com.spege.commandsuggest.client.net;

import com.spege.commandsuggest.client.ClientTreeCache;
import com.spege.commandsuggest.core.CommandIndex;
import com.spege.commandsuggest.core.TreeCodec;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Klient-only odbior pakietu. Dekodowanie leci na watku sieciowym (jest czyste i moze byc drogie
 * przy duzym drzewie), a do cache'u wchodzimy juz przez {@code addScheduledTask}, bo czyta go
 * watek renderujacy.
 */
@SideOnly(Side.CLIENT)
public final class TreeApplier {

    private TreeApplier() {
    }

    public static void apply(byte[] payload) {
        final CommandIndex index = TreeCodec.decode(payload);
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ClientTreeCache.accept(index);
            }
        });
    }
}
```

- [ ] **Step 4: `ClientTreeCache.java`**

```java
package com.spege.commandsuggest.client;

import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.config.CommandSuggestConfig;
import com.spege.commandsuggest.core.CommandIndex;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Drzewo komend na czas jednej sesji plus okno handshake'u.
 *
 * <p>Nie ma tu przelacznika trybow, bo nie ma dwoch trybow: brak drzewa to po prostu pusty
 * {@link CommandIndex}, a {@code SuggestionEngine} sam wtedy oddaje sprawe waniliowemu
 * tab-complete. Jedyne, co robi licznik ticków, to jedna linia INFO w logu — zeby dalo sie
 * odpowiedziec na pytanie "czemu tu nie ma typow".
 */
@SideOnly(Side.CLIENT)
public final class ClientTreeCache {

    private static CommandIndex index = CommandIndex.EMPTY;
    private static boolean received;
    private static int ticksSinceJoin;
    private static boolean modeLogged;

    private ClientTreeCache() {
    }

    public static CommandIndex get() {
        return index;
    }

    public static boolean hasTree() {
        return received;
    }

    /** Wolane z watku glownego przez {@code TreeApplier}. */
    public static void accept(CommandIndex fresh) {
        index = fresh != null ? fresh : CommandIndex.EMPTY;
        received = true;
        if (CommandSuggestConfig.logHandshakeMode && !modeLogged) {
            modeLogged = true;
            CommandSuggest.LOGGER.info(
                    "Serwer przyslal drzewo komend ({} pozycji) - pelne podpowiedzi z typami.",
                    Integer.valueOf(index.getCommands().size()));
        }
    }

    /** Ten sam obiekt jest handlerem — rejestruje go {@code ClientProxy}. */
    public static final class Events {

        @SubscribeEvent
        public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            index = CommandIndex.EMPTY;
            received = false;
            ticksSinceJoin = 0;
            modeLogged = false;
        }

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            index = CommandIndex.EMPTY;
            received = false;
            modeLogged = false;
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || received || modeLogged) {
                return;
            }
            ticksSinceJoin++;
            if (ticksSinceJoin > CommandSuggestConfig.handshakeTimeoutTicks) {
                modeLogged = true;
                if (CommandSuggestConfig.logHandshakeMode) {
                    CommandSuggest.LOGGER.info(
                            "Brak drzewa komend po {} tickach - serwer nie ma tego moda. "
                            + "Podpowiedzi lecą z waniliowego tab-complete, bez typow.",
                            Integer.valueOf(CommandSuggestConfig.handshakeTimeoutTicks));
                }
            }
        }
    }
}
```

- [ ] **Step 5: Podepnij w proxy**

W `CommonProxy.preInit` — zamiast komentarza:

```java
    public void preInit(FMLPreInitializationEvent event) {
        com.spege.commandsuggest.net.PacketHandler.register();
    }
```

W `ClientProxy.preInit`, po `super.preInit(event)`:

```java
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new com.spege.commandsuggest.client.ClientTreeCache.Events());
```

- [ ] **Step 6: Zbuduj**

```bash
./gradlew :commandsuggest:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/net/ commandsuggest/src/main/java/com/spege/commandsuggest/client/ commandsuggest/src/main/java/com/spege/commandsuggest/CommonProxy.java
git commit -m "feat(commandsuggest): kanal, pakiet S2CCommandTree i cache drzewa na kliencie"
```

---

## Task 10: `server` — `DescriptorLoader`

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/server/DescriptorLoader.java`
- Create: `commandsuggest/src/main/resources/assets/commandsuggest/commands/index.json`

**Dlaczego `index.json`:** wyliczenie plików w katalogu wewnątrz jara wymaga otwierania własnego
źródła jako ZIP-a i jest różne dla jara i dla katalogu w dev. Jawna lista jest o dwa rzędy prostsza
i od razu widać, co jar wiezie. Pliki z `config/` wyliczamy normalnie, bo to zwykły katalog.

- [ ] **Step 1: Pusty `index.json`** (wypełni go zadanie 17)

```json
[]
```

- [ ] **Step 2: `DescriptorLoader.java`**

```java
package com.spege.commandsuggest.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.core.CommandTree;
import com.spege.commandsuggest.core.JsonTreeReader;

/**
 * Opisy komend z dwoch zrodel: wbudowane w jarze i nadpisujace je pliki z
 * {@code config/commandsuggest/commands/}. Klucz to pole {@code command} z pliku.
 *
 * <p>Bledny plik nie zatrzymuje ladowania — leci WARN z nazwa pliku i lecimy dalej. To sa dane
 * pisane recznie i jedna literowka nie ma prawa zabrac podpowiedzi calej reszcie paczki.
 */
public final class DescriptorLoader {

    private static final String BUILTIN_DIR = "/assets/commandsuggest/commands/";

    private static File userDir;
    private static Map<String, CommandTree> descriptors = Collections.emptyMap();

    private DescriptorLoader() {
    }

    /** Wolane z preInit. Tworzy katalog uzytkownika, zeby bylo gdzie wrzucic wlasny opis. */
    public static void init(File configDir) {
        userDir = new File(new File(configDir, CommandSuggest.MODID), "commands");
        if (!userDir.exists() && !userDir.mkdirs()) {
            CommandSuggest.LOGGER.warn("Nie udalo sie utworzyc {}", userDir.getAbsolutePath());
        }
    }

    public static Map<String, CommandTree> get() {
        return descriptors;
    }

    /** Przeladowuje oba zrodla. Wolane przy starcie serwera i z {@code /commandsuggest reload}. */
    public static int reload() {
        Map<String, CommandTree> loaded = new HashMap<String, CommandTree>();
        int builtin = loadBuiltin(loaded);
        int user = loadUser(loaded);
        descriptors = Collections.unmodifiableMap(loaded);
        CommandSuggest.LOGGER.info("Wczytano opisy komend: {} wbudowanych, {} z config/, razem {}.",
                Integer.valueOf(builtin), Integer.valueOf(user), Integer.valueOf(loaded.size()));
        return loaded.size();
    }

    private static int loadBuiltin(Map<String, CommandTree> into) {
        int n = 0;
        InputStream indexStream = DescriptorLoader.class.getResourceAsStream(BUILTIN_DIR + "index.json");
        if (indexStream == null) {
            CommandSuggest.LOGGER.warn("Brak {}index.json w jarze - zero wbudowanych opisow.", BUILTIN_DIR);
            return 0;
        }
        try {
            JsonElement el = new JsonParser().parse(readAll(indexStream));
            JsonArray names = el.getAsJsonArray();
            for (JsonElement nameEl : names) {
                String fileName = nameEl.getAsString();
                InputStream in = DescriptorLoader.class.getResourceAsStream(BUILTIN_DIR + fileName);
                if (in == null) {
                    CommandSuggest.LOGGER.warn("index.json wymienia {}, ktorego nie ma w jarze.", fileName);
                    continue;
                }
                try {
                    CommandTree t = JsonTreeReader.read(readAll(in));
                    into.put(t.getName(), t);
                    n++;
                } catch (RuntimeException e) {
                    CommandSuggest.LOGGER.warn("Wbudowany opis {} jest bledny: {}", fileName, e.getMessage());
                } finally {
                    closeQuietly(in);
                }
            }
        } catch (IOException e) {
            CommandSuggest.LOGGER.warn("Nie udalo sie odczytac index.json: {}", e.getMessage());
        } catch (RuntimeException e) {
            CommandSuggest.LOGGER.warn("index.json jest bledny: {}", e.getMessage());
        } finally {
            closeQuietly(indexStream);
        }
        return n;
    }

    private static int loadUser(Map<String, CommandTree> into) {
        if (userDir == null || !userDir.isDirectory()) {
            return 0;
        }
        File[] files = userDir.listFiles();
        if (files == null) {
            return 0;
        }
        int n = 0;
        for (File f : files) {
            if (!f.isFile() || !f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                continue;
            }
            InputStream in = null;
            try {
                in = new FileInputStream(f);
                CommandTree t = JsonTreeReader.read(readAll(in));
                // config/ nadpisuje wbudowane - to jest cala pointa tego katalogu
                into.put(t.getName(), t);
                n++;
            } catch (IOException e) {
                CommandSuggest.LOGGER.warn("Nie udalo sie odczytac {}: {}", f.getName(), e.getMessage());
            } catch (RuntimeException e) {
                CommandSuggest.LOGGER.warn("Opis {} jest bledny: {}", f.getName(), e.getMessage());
            } finally {
                closeQuietly(in);
            }
        }
        return n;
    }

    private static String readAll(InputStream in) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {
                // nic sensownego nie da sie tu zrobic
            }
        }
    }
}
```

- [ ] **Step 3: Zbuduj i zacommituj**

```bash
./gradlew :commandsuggest:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/server/DescriptorLoader.java commandsuggest/src/main/resources/assets/commandsuggest/commands/index.json
git commit -m "feat(commandsuggest): DescriptorLoader - opisy z jara i z config/"
```

---

## Task 11: `server` — `TreeBuilder`

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/server/TreeBuilder.java`

- [ ] **Step 1: Napisz builder**

```java
package com.spege.commandsuggest.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.core.ArgType;
import com.spege.commandsuggest.core.CmdArg;
import com.spege.commandsuggest.core.CmdNode;
import com.spege.commandsuggest.core.CommandIndex;
import com.spege.commandsuggest.core.CommandTree;

import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

/**
 * Rejestr {@code ICommand} plus opisy JSON daja {@link CommandIndex} dla jednego gracza.
 *
 * <p>Trzy zrodla wiedzy, w kolejnosci: opis z {@link DescriptorLoader} (pelne typy), a jak go nie
 * ma — wezel generyczny {@code literal + greedy} z {@code getUsage()} jako linia usage. Wartosci
 * dla tego drugiego bierze potem klient waniliowym {@code CPacketTabComplete}, dzieki czemu 268
 * modow dziala od pierwszego dnia, tylko bez kolorowania typow.
 *
 * <p>🚨 {@code ICommandManager.getCommands()} zawiera KAZDY alias jako osobny klucz wskazujacy na
 * ten sam obiekt. Grupujemy wiec po tozsamosci obiektu ({@link IdentityHashMap}), nazwa kanoniczna
 * jest z {@code getName()}, a reszta kluczy to aliasy.
 */
public final class TreeBuilder {

    private TreeBuilder() {
    }

    public static CommandIndex build(MinecraftServer server, ICommandSender sender) {
        Map<String, ICommand> registry = server.getCommandManager().getCommands();

        // obiekt komendy -> wszystkie klucze, pod ktorymi siedzi
        Map<ICommand, List<String>> keys = new IdentityHashMap<ICommand, List<String>>();
        for (Map.Entry<String, ICommand> e : registry.entrySet()) {
            List<String> list = keys.get(e.getValue());
            if (list == null) {
                list = new ArrayList<String>(2);
                keys.put(e.getValue(), list);
            }
            list.add(e.getKey());
        }

        Map<String, CommandTree> descriptors = DescriptorLoader.get();
        Map<String, ICommand> canonical = new HashMap<String, ICommand>();
        List<CommandTree> trees = new ArrayList<CommandTree>(keys.size());

        for (Map.Entry<ICommand, List<String>> e : keys.entrySet()) {
            ICommand cmd = e.getKey();
            String name = cmd.getName();
            List<String> aliases = new ArrayList<String>();
            for (String k : e.getValue()) {
                if (!k.equals(name)) {
                    aliases.add(k);
                }
            }
            canonical.put(name, cmd);

            CommandTree described = descriptors.get(name);
            CmdNode root = described != null ? described.getRoot() : genericRoot(cmd, sender);
            trees.add(new CommandTree(name, aliases, root));
        }

        CommandIndex full = new CommandIndex(trees);
        final MinecraftServer srv = server;
        final ICommandSender who = sender;
        final Map<String, ICommand> lookup = canonical;
        return full.prune(name -> {
            ICommand cmd = lookup.get(name);
            if (cmd == null) {
                return false;
            }
            try {
                return cmd.checkPermission(srv, who);
            } catch (RuntimeException ex) {
                // cudzy kod; komenda, ktora wywala sie na sprawdzeniu uprawnien, nie trafia do drzewa
                CommandSuggest.LOGGER.warn("checkPermission komendy '{}' rzucilo {} - pomijam.",
                        name, ex.getClass().getSimpleName());
                return false;
            }
        });
    }

    /**
     * Wezel dla komendy bez opisu. {@code getUsage} zwraca zwykle KLUCZ tlumaczenia, nie gotowy
     * tekst — klient przepuszcza go przez {@code I18n.format}, ktore nieznany klucz oddaje bez zmian,
     * wiec obie postacie sa obsluzone.
     */
    private static CmdNode genericRoot(ICommand cmd, ICommandSender sender) {
        String usage;
        try {
            usage = cmd.getUsage(sender);
        } catch (RuntimeException ex) {
            usage = null;
        }
        CmdArg tail = new CmdArg("args", ArgType.GREEDY, null, null, null);
        return new CmdNode(null, java.util.Collections.singletonList(tail), null, true, usage);
    }
}
```

- [ ] **Step 2: Zbuduj i zacommituj**

```bash
./gradlew :commandsuggest:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/server/TreeBuilder.java
git commit -m "feat(commandsuggest): TreeBuilder - rejestr ICommand plus opisy w drzewo gracza"
```

---

## Task 12: `server` — wysyłka, komenda i podpięcie cyklu życia

Po tym zadaniu drzewo **realnie dolatuje do klienta** — pierwszy punkt, w którym da się to zobaczyć w logu.

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/server/TreeDispatcher.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/server/LoginHandler.java`
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/server/CommandCommandSuggest.java`
- Modify: `commandsuggest/src/main/java/com/spege/commandsuggest/CommandSuggest.java`
- Modify: `commandsuggest/src/main/java/com/spege/commandsuggest/CommonProxy.java`

- [ ] **Step 1: `TreeDispatcher.java`**

```java
package com.spege.commandsuggest.server;

import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.core.CommandIndex;
import com.spege.commandsuggest.core.TreeCodec;
import com.spege.commandsuggest.net.PacketHandler;
import com.spege.commandsuggest.net.S2CCommandTree;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/** Buduje drzewo pod jednego gracza i wysyla mu je. Jedno miejsce, trzy wolajace. */
public final class TreeDispatcher {

    private TreeDispatcher() {
    }

    public static void sendTo(EntityPlayerMP player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        try {
            CommandIndex index = TreeBuilder.build(server, player);
            byte[] payload = TreeCodec.encode(index);
            PacketHandler.CHANNEL.sendTo(new S2CCommandTree(payload), player);
            CommandSuggest.LOGGER.info("Wyslano drzewo komend do {}: {} komend, {} bajtow.",
                    player.getName(), Integer.valueOf(index.getCommands().size()),
                    Integer.valueOf(payload.length));
        } catch (RuntimeException e) {
            // gracz zostaje w trybie zdegradowanym; to nie powod, zeby wywalac serwer
            CommandSuggest.LOGGER.error("Nie udalo sie wyslac drzewa do " + player.getName(), e);
        }
    }

    public static void sendToAll(MinecraftServer server) {
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            sendTo(p);
        }
    }
}
```

- [ ] **Step 2: `LoginHandler.java`**

```java
package com.spege.commandsuggest.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Drzewo idzie raz, przy wejsciu. Zadnych typow klienckich — ta klasa zyje takze na dedyku. */
public class LoginHandler {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            TreeDispatcher.sendTo((EntityPlayerMP) event.player);
        }
    }
}
```

- [ ] **Step 3: `CommandCommandSuggest.java`**

```java
package com.spege.commandsuggest.server;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * {@code /commandsuggest reload} i {@code /commandsuggest refresh}.
 *
 * <p>Bez aliasow. W paczce z 268 modami krotki alias w rodzaju {@code /cs} to zaproszenie do
 * kolizji, a ta komenda jest wolana raz na ruski rok.
 *
 * <p>{@code refresh} istnieje, bo Forge nie ma eventu na zmiane poziomu uprawnien: po {@code /op}
 * drzewo gracza jest nieaktualne az do przelogowania i to jest jedyny sposob, zeby je odswiezyc
 * bez wychodzenia z gry.
 */
public class CommandCommandSuggest extends CommandBase {

    @Override
    public String getName() {
        return "commandsuggest";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commandsuggest.command.usage";
    }

    /** Zero — bramkowanie jest per podkomenda, w {@link #execute}. */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload", "refresh");
        }
        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws net.minecraft.command.CommandException {
        if (args.length != 1) {
            throw new WrongUsageException("commandsuggest.command.usage");
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.canUseCommand(2, getName())) {
                throw new net.minecraft.command.CommandException("commands.generic.permission");
            }
            int n = DescriptorLoader.reload();
            TreeDispatcher.sendToAll(server);
            sender.sendMessage(new TextComponentTranslation("commandsuggest.command.reloaded",
                    Integer.valueOf(n)));
            return;
        }
        if ("refresh".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof EntityPlayerMP)) {
                throw new net.minecraft.command.CommandException("commandsuggest.command.playeronly");
            }
            TreeDispatcher.sendTo((EntityPlayerMP) sender);
            sender.sendMessage(new TextComponentTranslation("commandsuggest.command.refreshed"));
            return;
        }
        throw new WrongUsageException("commandsuggest.command.usage");
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList();
    }
}
```

- [ ] **Step 4: Podepnij cykl życia**

W `CommandSuggest.java` — dopisz import `FMLServerStartingEvent` i dwa handlery, oraz rozszerz `preInit`:

```java
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        com.spege.commandsuggest.server.DescriptorLoader.init(event.getModConfigurationDirectory());
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(net.minecraftforge.fml.common.event.FMLServerStartingEvent event) {
        com.spege.commandsuggest.server.DescriptorLoader.reload();
        event.registerServerCommand(new com.spege.commandsuggest.server.CommandCommandSuggest());
    }
```

W `CommonProxy.init`:

```java
    public void init(FMLInitializationEvent event) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                new com.spege.commandsuggest.server.LoginHandler());
    }
```

- [ ] **Step 5: Zbuduj**

```bash
./gradlew :commandsuggest:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Pierwszy dowód, że to lata — dev client**

```bash
./gradlew :commandsuggest:runClient
```

Wejdź w świat jednoosobowy. W konsoli muszą pojawić się **dwie** linie:

```
[commandsuggest]: Wczytano opisy komend: 0 wbudowanych, 0 z config/, razem 0.
[commandsuggest]: Wyslano drzewo komend do Dev: <N> komend, <M> bajtow.
[commandsuggest]: Serwer przyslal drzewo komend (<N> pozycji) - pelne podpowiedzi z typami.
```

Trzecia linia jest dowodem, że pakiet przeszedł tam i z powrotem i że kodek działa na prawdziwym
drzewie. Jeżeli `<N>` to zero, `getCommands()` zwróciło pustkę — sprawdź, czy `serverStarting`
w ogóle się odpalił.

- [ ] **Step 7: Commit**

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/
git commit -m "feat(commandsuggest): wysylka drzewa przy loginie, komenda reload/refresh"
```

---

## Task 13: `client` — `SpyTabCompleter`

To jest klasa, która zastępuje **dwa mixiny brigo**. Przeczytaj §3 specu, zanim ją napiszesz.

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/client/SpyTabCompleter.java`

- [ ] **Step 1: Napisz podklasę**

```java
package com.spege.commandsuggest.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.network.play.client.CPacketTabComplete;
import net.minecraft.util.TabCompleter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Przechwytuje odpowiedz serwera na tab-complete, zamiast mutowac pole tekstowe.
 *
 * <p>🚨 To jest cala sztuczka, ktora pozwala obejsc sie bez mixinow w
 * {@code NetHandlerPlayClient} i {@code GuiChat$ChatTabCompleter}. Sciezka odpowiedzi to
 * {@code NetHandlerPlayClient.handleTabComplete} → {@code GuiChat.setCompletions(...)} →
 * {@code tabCompleter.setCompletions(...)}. Waniliowa implementacja robi jedna z dwoch rzeczy
 * i zadna nam nie sluzy: przy {@code requestedCompletions == false} nie robi NIC (odpowiedz
 * przepada), a przy {@code true} przepisuje pole tekstowe wspolnym prefiksem i przy jego braku
 * wola {@code complete()}, wstawiajac pierwszego kandydata. Tu obie te sciezki sa zastapione
 * zapisem do listy.
 *
 * <p>{@code complete()} jest nadpisane pustka celowo: nasz handler i tak anuluje TAB, ale gdyby
 * jakikolwiek inny kod je zawolal, ma nie ruszac tekstu gracza.
 */
@SideOnly(Side.CLIENT)
public final class SpyTabCompleter extends TabCompleter {

    private final List<String> captured = new ArrayList<String>();
    private boolean fresh;

    /** {@code false} = "to nie jest komenda blokowa", tak samo jak w {@code GuiChat}. */
    public SpyTabCompleter(GuiTextField textField) {
        super(textField, false);
    }

    /**
     * Wysyla zapytanie o wartosci, ktorych klient nie zna.
     *
     * @param lineUpToCursor cala linia od ukosnika do kursora — dokladnie to, co wysyla wanilia
     *                       w {@code TabCompleter.requestCompletions}
     */
    public void request(String lineUpToCursor) {
        this.captured.clear();
        this.fresh = false;
        if (lineUpToCursor == null || lineUpToCursor.isEmpty()) {
            return;
        }
        // komendy klienckie Forge nie ida przez siec - odpowiadaja od razu, do latestAutoComplete
        ClientCommandHandler.instance.autoComplete(lineUpToCursor);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && mc.player.connection != null) {
            mc.player.connection.sendPacket(
                    new CPacketTabComplete(lineUpToCursor, getTargetBlockPos(), false));
        }
    }

    @Override
    public void setCompletions(String... newCompletions) {
        this.captured.clear();
        String[] fromClientCommands = ClientCommandHandler.instance.latestAutoComplete;
        if (fromClientCommands != null) {
            for (String s : fromClientCommands) {
                if (s != null && !s.isEmpty()) {
                    this.captured.add(s);
                }
            }
        }
        if (newCompletions != null) {
            for (String s : newCompletions) {
                if (s != null && !s.isEmpty()) {
                    this.captured.add(s);
                }
            }
        }
        this.fresh = true;
    }

    /** Celowo pusto — patrz javadoc klasy. */
    @Override
    public void complete() {
        // nic
    }

    @Override
    @Nullable
    public BlockPos getTargetBlockPos() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
            return mc.objectMouseOver.getBlockPos();
        }
        return null;
    }

    /** {@code true} raz, od przyjscia odpowiedzi do jej odebrania przez {@link #drain()}. */
    public boolean isFresh() {
        return this.fresh;
    }

    /** Oddaje przechwycone wartosci i kasuje flage swiezosci. */
    public List<String> drain() {
        this.fresh = false;
        return this.captured.isEmpty()
                ? Collections.<String>emptyList()
                : new ArrayList<String>(this.captured);
    }
}
```

- [ ] **Step 2: Zbuduj i zacommituj**

```bash
./gradlew :commandsuggest:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/client/SpyTabCompleter.java
git commit -m "feat(commandsuggest): SpyTabCompleter zamiast dwoch mixinow brigo"
```

---

## Task 14: `client` — `LocalValueSource`

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/client/LocalValueSource.java`

- [ ] **Step 1: Napisz źródło wartości**

```java
package com.spege.commandsuggest.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import com.spege.commandsuggest.core.ArgType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Wartosci, ktore klient zna sam — bez ani jednego pakietu.
 *
 * <p>W 1.12.2 rejestry sa synchronizowane przy loginie, wiec nazwy itemow, blokow i encji sa po
 * tej stronie kompletne i zgodne z serwerem. To jest powod, dla ktorego typowanie argumentow
 * zarabia na siebie: opisany {@code item} nie strzela {@code CPacketTabComplete}, a wiec nie
 * dostaje obcinania listy przez VintageFix ani linijki smiecia na czacie.
 *
 * <p>Listy z rejestrow sa cache'owane po pierwszym uzyciu — rejestry sa zamrozone po starcie,
 * a przeliczanie kilkunastu tysiecy {@code ResourceLocation.toString()} przy kazdym wcisnietym
 * klawiszu byloby marnotrawstwem.
 */
@SideOnly(Side.CLIENT)
public final class LocalValueSource {

    /** Sufit na jedno zapytanie. Popup i tak scrolluje, a lista itemow ma pieciocyfrowy rozmiar. */
    private static final int LIMIT = 200;

    private static List<String> items;
    private static List<String> blocks;
    private static List<String> entities;

    private LocalValueSource() {
    }

    public static List<String> resolve(ArgType type, String prefix) {
        if (type == null) {
            return Collections.emptyList();
        }
        switch (type) {
            case PLAYER:
                return filter(players(), prefix);
            case ITEM:
                if (items == null) {
                    items = names(ForgeRegistries.ITEMS.getKeys());
                }
                return filter(items, prefix);
            case BLOCK:
                if (blocks == null) {
                    blocks = names(ForgeRegistries.BLOCKS.getKeys());
                }
                return filter(blocks, prefix);
            case ENTITY:
                if (entities == null) {
                    entities = names(ForgeRegistries.ENTITIES.getKeys());
                }
                return filter(entities, prefix);
            case DIMENSION:
                return filter(dimensions(), prefix);
            case BLOCKPOS:
                return lookedAtBlock();
            default:
                return Collections.emptyList();
        }
    }

    private static List<String> players() {
        NetHandlerPlayClient conn = Minecraft.getMinecraft().getConnection();
        if (conn == null) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<String>();
        for (NetworkPlayerInfo info : conn.getPlayerInfoMap()) {
            out.add(info.getGameProfile().getName());
        }
        Collections.sort(out);
        return out;
    }

    private static List<String> dimensions() {
        List<String> out = new ArrayList<String>();
        for (Integer id : DimensionManager.getIDs()) {
            out.add(String.valueOf(id));
        }
        return out;
    }

    private static List<String> lookedAtBlock() {
        RayTraceResult hit = Minecraft.getMinecraft().objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return Collections.emptyList();
        }
        return Collections.singletonList(
                hit.getBlockPos().getX() + " " + hit.getBlockPos().getY() + " " + hit.getBlockPos().getZ());
    }

    private static List<String> names(Iterable<ResourceLocation> keys) {
        TreeSet<String> sorted = new TreeSet<String>();
        for (ResourceLocation rl : keys) {
            sorted.add(rl.toString());
        }
        return Collections.unmodifiableList(new ArrayList<String>(sorted));
    }

    /**
     * Dopasowanie jak w wanilii: prefiks bez dwukropka trafia takze w sama sciezke, wiec
     * {@code stone} znajduje {@code minecraft:stone}, a {@code minecraft:st} tez dziala.
     */
    private static List<String> filter(List<String> source, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        boolean pathToo = p.indexOf(':') < 0;
        List<String> out = new ArrayList<String>();
        for (String s : source) {
            String low = s.toLowerCase(Locale.ROOT);
            boolean hit = low.startsWith(p);
            if (!hit && pathToo) {
                int colon = low.indexOf(':');
                hit = colon >= 0 && low.startsWith(p, colon + 1);
            }
            if (hit) {
                out.add(s);
                if (out.size() >= LIMIT) {
                    break;
                }
            }
        }
        return out;
    }
}
```

- [ ] **Step 2: Zbuduj i zacommituj**

```bash
./gradlew :commandsuggest:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/client/LocalValueSource.java
git commit -m "feat(commandsuggest): LocalValueSource - wartosci z rejestrow klienta"
```

---

## Task 15: `client` — `SuggestionPopup` (rysowanie i nawigacja)

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/client/SuggestionPopup.java`

- [ ] **Step 1: Napisz popup**

```java
package com.spege.commandsuggest.client;

import java.util.ArrayList;
import java.util.List;

import com.spege.commandsuggest.config.CommandSuggestConfig;
import com.spege.commandsuggest.core.ArgType;
import com.spege.commandsuggest.core.Suggestion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Lista podpowiedzi rysowana nad polem czatu, razem z cala nawigacja po niej.
 *
 * <p>Geometria jest przywiazana do {@code GuiChat.initGui}, ktore tworzy pole jako
 * {@code new GuiTextField(0, fontRenderer, 4, height - 12, width - 4, 12)} z wylaczonym tlem —
 * a przy wylaczonym tle {@code GuiTextField.drawTextBox} rysuje tekst od {@code x}, nie od
 * {@code x + 4}. Stad {@link #CHAT_FIELD_X}. Samo pole jest prywatne i nie ma gettera na {@code x},
 * wiec ta stala jest jedynym sensownym sposobem wyrownania popupu do edytowanego tokenu.
 */
@SideOnly(Side.CLIENT)
public final class SuggestionPopup {

    /** Zgodnie z {@code GuiChat.initGui}. */
    private static final int CHAT_FIELD_X = 4;
    /** Gorna krawedz czarnego prostokata pola czatu: {@code drawRect(2, height - 14, ...)}. */
    private static final int CHAT_FIELD_TOP_FROM_BOTTOM = 14;

    private static final int COLOR_BACKGROUND = 0xE0100010;
    private static final int COLOR_SELECTION = 0xFF4A4A6A;
    private static final int COLOR_SELECTED_TEXT = 0xFFFFFF;
    private static final int COLOR_PLAIN = 0xAAAAAA;
    private static final int COLOR_USAGE = 0x808080;

    private final List<Suggestion> items = new ArrayList<Suggestion>();
    private String usage;
    private int replaceStart;
    private int selected;
    private int scroll;

    // geometria z ostatniego rysowania - potrzebna, zeby klikniecie trafilo we wlasciwy wiersz
    private int lastX;
    private int lastListTop;
    private int lastWidth;
    private int lastRowHeight;
    private int lastVisible;

    public void set(List<Suggestion> fresh, String usageLine, int replaceStartOffset) {
        this.items.clear();
        if (fresh != null) {
            this.items.addAll(fresh);
        }
        this.usage = usageLine;
        this.replaceStart = replaceStartOffset;
        this.selected = 0;
        this.scroll = 0;
    }

    /**
     * Dokłada pozycje do już wyświetlonej listy, nie ruszając zaznaczenia, przewinięcia ani linii
     * usage. Tędy wchodzi odpowiedź serwera, która przychodzi asynchronicznie — kilka klatek po
     * tym, jak popup zdążył się już pokazać z wartościami lokalnymi.
     */
    public void append(List<Suggestion> extra) {
        if (extra == null || extra.isEmpty()) {
            return;
        }
        for (Suggestion s : extra) {
            boolean duplikat = false;
            for (Suggestion existing : this.items) {
                if (existing.getText().equals(s.getText())) {
                    duplikat = true;
                    break;
                }
            }
            if (!duplikat) {
                this.items.add(s);
            }
        }
    }

    public void close() {
        this.items.clear();
        this.usage = null;
        this.selected = 0;
        this.scroll = 0;
    }

    public boolean isOpen() {
        return !this.items.isEmpty();
    }

    public int getReplaceStart() {
        return this.replaceStart;
    }

    public Suggestion getSelected() {
        if (this.items.isEmpty()) {
            return null;
        }
        return this.items.get(Math.max(0, Math.min(this.selected, this.items.size() - 1)));
    }

    /** Strzalki. Zawija sie na obu koncach, tak jak lista w 1.13+. */
    public void move(int delta) {
        if (this.items.isEmpty()) {
            return;
        }
        int n = this.items.size();
        this.selected = ((this.selected + delta) % n + n) % n;
        ensureVisible();
    }

    /** Kolko myszy. Przesuwa okno widoku, nie zaznaczenie. */
    public void scrollBy(int delta) {
        int max = Math.max(0, this.items.size() - visibleRows());
        this.scroll = Math.max(0, Math.min(this.scroll + delta, max));
    }

    private void ensureVisible() {
        int rows = visibleRows();
        if (this.selected < this.scroll) {
            this.scroll = this.selected;
        } else if (this.selected >= this.scroll + rows) {
            this.scroll = this.selected - rows + 1;
        }
    }

    private int visibleRows() {
        return Math.min(this.items.size(), Math.max(1, CommandSuggestConfig.maxVisibleRows));
    }

    /** @return {@code true} gdy klikniecie trafilo w liste (wtedy wolajacy zatwierdza wybor) */
    public boolean click(int mouseX, int mouseY) {
        if (!isOpen() || this.lastRowHeight <= 0) {
            return false;
        }
        if (mouseX < this.lastX || mouseX > this.lastX + this.lastWidth) {
            return false;
        }
        if (mouseY < this.lastListTop || mouseY >= this.lastListTop + this.lastVisible * this.lastRowHeight) {
            return false;
        }
        int row = (mouseY - this.lastListTop) / this.lastRowHeight;
        int index = this.scroll + row;
        if (index < 0 || index >= this.items.size()) {
            return false;
        }
        this.selected = index;
        return true;
    }

    /**
     * @param screenHeight    wysokosc ekranu GUI
     * @param screenWidth     szerokosc ekranu GUI
     * @param textBeforeToken tresc linii czatu OD POCZATKU do {@link #getReplaceStart()} — sluzy
     *                        wylacznie do policzenia, ile pikseli w prawo zaczac rysowac
     */
    public void draw(Minecraft mc, int screenWidth, int screenHeight, String textBeforeToken) {
        if (!isOpen()) {
            return;
        }
        int rowHeight = mc.fontRenderer.FONT_HEIGHT + 2;
        int rows = visibleRows();

        int width = 0;
        for (int i = 0; i < rows; i++) {
            int index = this.scroll + i;
            if (index >= this.items.size()) {
                break;
            }
            width = Math.max(width, mc.fontRenderer.getStringWidth(this.items.get(index).getText()));
        }
        width += 4;

        boolean drawUsage = CommandSuggestConfig.showUsageLine && this.usage != null && !this.usage.isEmpty();
        int usageTop = screenHeight - CHAT_FIELD_TOP_FROM_BOTTOM - (drawUsage ? rowHeight : 0);
        int listBottom = usageTop;
        int listTop = listBottom - rows * rowHeight;

        int x = CHAT_FIELD_X + mc.fontRenderer.getStringWidth(textBeforeToken);
        if (x + width > screenWidth) {
            x = Math.max(0, screenWidth - width);
        }

        this.lastX = x;
        this.lastListTop = listTop;
        this.lastWidth = width;
        this.lastRowHeight = rowHeight;
        this.lastVisible = rows;

        Gui.drawRect(x, listTop, x + width, listBottom, COLOR_BACKGROUND);
        for (int i = 0; i < rows; i++) {
            int index = this.scroll + i;
            if (index >= this.items.size()) {
                break;
            }
            int rowTop = listTop + i * rowHeight;
            boolean isSelected = index == this.selected;
            if (isSelected) {
                Gui.drawRect(x, rowTop, x + width, rowTop + rowHeight, COLOR_SELECTION);
            }
            Suggestion s = this.items.get(index);
            mc.fontRenderer.drawStringWithShadow(s.getText(), x + 2, rowTop + 1,
                    isSelected ? COLOR_SELECTED_TEXT : colorFor(s.getType()));
        }

        if (drawUsage) {
            int usageWidth = mc.fontRenderer.getStringWidth(this.usage) + 4;
            Gui.drawRect(x, usageTop, x + Math.max(width, usageWidth), usageTop + rowHeight,
                    COLOR_BACKGROUND);
            mc.fontRenderer.drawStringWithShadow(this.usage, x + 2, usageTop + 1, COLOR_USAGE);
        }
    }

    /** {@code null} = literal (nazwa komendy albo podkomenda). */
    private static int colorFor(ArgType type) {
        if (type == null) {
            return 0xFFFFFF;
        }
        if (!CommandSuggestConfig.colourArgumentsByType) {
            return COLOR_PLAIN;
        }
        switch (type) {
            case PLAYER:
                return 0x55FF55;
            case ITEM:
            case BLOCK:
                return 0xFFAA00;
            case ENTITY:
                return 0xFF55FF;
            case INT:
            case FLOAT:
            case DIMENSION:
                return 0x55FFFF;
            case BOOL:
            case CHOICE:
                return 0xFFFF55;
            default:
                return COLOR_PLAIN;
        }
    }
}
```

- [ ] **Step 2: Zbuduj i zacommituj**

```bash
./gradlew :commandsuggest:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/client/SuggestionPopup.java
git commit -m "feat(commandsuggest): SuggestionPopup - rysowanie listy i nawigacja"
```

---

## Task 16: `client` — `ChatScreenHandler` (cztery eventy)

**Files:**
- Create: `commandsuggest/src/main/java/com/spege/commandsuggest/client/ChatScreenHandler.java`
- Modify: `commandsuggest/src/main/java/com/spege/commandsuggest/client/ClientProxy.java`

- [ ] **Step 1: Napisz handler**

```java
package com.spege.commandsuggest.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.config.CommandSuggestConfig;
import com.spege.commandsuggest.core.ArgType;
import com.spege.commandsuggest.core.InputParser;
import com.spege.commandsuggest.core.ParsedInput;
import com.spege.commandsuggest.core.Suggestion;
import com.spege.commandsuggest.core.SuggestionEngine;
import com.spege.commandsuggest.core.Suggestions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Cale zaczepienie o czat: cztery eventy {@code GuiScreenEvent} i jedna podmiana pola.
 *
 * <p>Dwa pola {@code GuiChat} sa siegane refleksja RAZ na otwarcie ekranu, nigdy w petli
 * rysowania: {@code inputField} ({@code field_146415_a}, {@code protected}) do odczytu tekstu
 * i {@code tabCompleter} ({@code field_184096_i}, {@code private}) do podmiany na
 * {@link SpyTabCompleter}. Nazwy MCP sa podane przed SRG, bo {@code ReflectionHelper.findField}
 * probuje ich po kolei — pierwsza dziala w dev, druga w grze.
 *
 * <p>🚨 TAB jest anulowany ZAWSZE, gdy ten handler jest aktywny na {@code GuiChat}, nie tylko przy
 * otwartym popupie. Inaczej doszedlby do {@code GuiChat.keyTyped}, ktore wola
 * {@code tabCompleter.complete()} i przepisuje graczowi tekst pod palcami. Przy okazji znika
 * waniliowe wypisywanie kandydatow przecinkami na czat — to robi
 * {@code GuiChat$ChatTabCompleter.complete()}, ktore juz nigdy sie nie wykona.
 *
 * <p>Enter NIE jest przechwytywany. Zatwierdza wylacznie TAB i klikniecie. Gdyby Enter zatwierdzal
 * podpowiedz, wpisane {@code /home} wyslaloby sie jako {@code /homes} — to najczestsza skarga na
 * tego typu mody i nie ma powodu jej powtarzac.
 */
@SideOnly(Side.CLIENT)
public class ChatScreenHandler {

    private static final Field INPUT_FIELD =
            ReflectionHelper.findField(GuiChat.class, "inputField", "field_146415_a");
    private static final Field TAB_COMPLETER =
            ReflectionHelper.findField(GuiChat.class, "tabCompleter", "field_184096_i");

    private final SuggestionPopup popup = new SuggestionPopup();

    private GuiChat chat;
    private GuiTextField field;
    private SpyTabCompleter spy;

    private String lastText;
    private int lastCursor = -1;
    private long dirtySince;
    private boolean dirty;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        detach();
        if (!CommandSuggestConfig.enabled || !(event.getGui() instanceof GuiChat)) {
            return;
        }
        GuiChat gui = (GuiChat) event.getGui();
        try {
            this.field = (GuiTextField) INPUT_FIELD.get(gui);
            this.spy = new SpyTabCompleter(this.field);
            TAB_COMPLETER.set(gui, this.spy);
            this.chat = gui;
        } catch (ReflectiveOperationException e) {
            CommandSuggest.LOGGER.error("Nie udalo sie podpiac pod GuiChat - podpowiedzi wylaczone "
                    + "do konca sesji.", e);
            detach();
        }
    }

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (this.chat == null || event.getGui() != this.chat) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != this.chat) {
            detach();
            return;
        }
        pollTextChange();
        if (this.spy.isFresh()) {
            appendServerValues(this.spy.drain());
        }
        this.popup.draw(mc, this.chat.width, this.chat.height, textBeforeToken());
    }

    @SubscribeEvent
    public void onKeyboard(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (this.chat == null || event.getGui() != this.chat || !Keyboard.getEventKeyState()) {
            return;
        }
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_TAB) {
            accept();
            event.setCanceled(true);
            return;
        }
        if (!this.popup.isOpen()) {
            return;
        }
        if (key == Keyboard.KEY_UP) {
            this.popup.move(-1);
            event.setCanceled(true);
        } else if (key == Keyboard.KEY_DOWN) {
            this.popup.move(1);
            event.setCanceled(true);
        } else if (key == Keyboard.KEY_ESCAPE) {
            // pierwszy Esc zamyka popup, dopiero drugi zamknie czat
            this.popup.close();
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (this.chat == null || event.getGui() != this.chat || !this.popup.isOpen()) {
            return;
        }
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            this.popup.scrollBy(wheel > 0 ? -1 : 1);
            event.setCanceled(true);
            return;
        }
        if (Mouse.getEventButtonState() && Mouse.getEventButton() == 0) {
            Minecraft mc = Minecraft.getMinecraft();
            int mx = Mouse.getEventX() * this.chat.width / mc.displayWidth;
            int my = this.chat.height - Mouse.getEventY() * this.chat.height / mc.displayHeight - 1;
            if (this.popup.click(mx, my)) {
                accept();
                event.setCanceled(true);
            }
        }
    }

    private void detach() {
        this.chat = null;
        this.field = null;
        this.spy = null;
        this.lastText = null;
        this.lastCursor = -1;
        this.dirty = false;
        this.popup.close();
    }

    /** Przeliczamy tylko przy zmianie tresci albo pozycji kursora, i to po debounce. */
    private void pollTextChange() {
        String text = this.field.getText();
        int cursor = this.field.getCursorPosition();
        if (!text.equals(this.lastText) || cursor != this.lastCursor) {
            this.lastText = text;
            this.lastCursor = cursor;
            this.dirty = true;
            this.dirtySince = Minecraft.getSystemTime();
        }
        if (this.dirty
                && Minecraft.getSystemTime() - this.dirtySince >= CommandSuggestConfig.recomputeDebounceMs) {
            this.dirty = false;
            recompute();
        }
    }

    private void recompute() {
        ParsedInput in = InputParser.parse(this.lastText, this.lastCursor);
        if (!in.isCommand()) {
            this.popup.close();
            return;
        }
        Suggestions s = SuggestionEngine.suggest(ClientTreeCache.get(), in);
        List<Suggestion> items = new ArrayList<Suggestion>(s.getItems());

        if (in.getEditIndex() == 0) {
            addClientCommands(items, in.getPrefix());
        }
        ArgType type = s.getArgType();
        if (type != null && !type.isServerResolved()) {
            for (String value : LocalValueSource.resolve(type, s.getPrefix())) {
                items.add(new Suggestion(value, type));
            }
        }

        String usage = s.getUsage() == null ? null : I18n.format(s.getUsage());
        this.popup.set(items, usage, s.getReplaceStart());

        if (s.needsServerQuery()) {
            this.spy.request(this.lastText.substring(0, this.lastCursor));
        }
    }

    /**
     * Komendy klienckie Forge nie ma prawa byc w drzewie z serwera — {@code CommandHandler} po
     * tamtej stronie ich nie widzi. Dokladamy je tutaj, przy pierwszym tokenie.
     */
    private void addClientCommands(List<Suggestion> items, String prefix) {
        for (String name : ClientCommandHandler.instance.getCommands().keySet()) {
            if (!name.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            boolean juzJest = false;
            for (Suggestion s : items) {
                if (s.getText().equals(name)) {
                    juzJest = true;
                    break;
                }
            }
            if (!juzJest) {
                items.add(new Suggestion(name, null));
            }
        }
    }

    /**
     * Odpowiedz serwera dochodzi asynchronicznie, kilka klatek po tym, jak popup juz sie pokazal.
     * Dokladamy ja do istniejacej listy przez {@code append}, a NIE przez {@code set} — inaczej
     * skasowalibysmy wartosci lokalne, linie usage i zaznaczenie, ktore gracz mogl juz przesunac.
     */
    private void appendServerValues(List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        List<Suggestion> extra = new ArrayList<Suggestion>();
        for (String v : values) {
            extra.add(new Suggestion(v, ArgType.WORD));
        }
        this.popup.append(extra);
    }

    private String textBeforeToken() {
        int start = Math.min(this.popup.getReplaceStart(), this.lastText.length());
        return this.lastText.substring(0, Math.max(0, start));
    }

    private void accept() {
        Suggestion selected = this.popup.getSelected();
        if (selected == null) {
            return;
        }
        String text = this.field.getText();
        int start = Math.max(0, Math.min(this.popup.getReplaceStart(), text.length()));
        int cursor = Math.max(start, Math.min(this.field.getCursorPosition(), text.length()));
        String updated = text.substring(0, start) + selected.getText() + text.substring(cursor);
        this.field.setText(updated);
        this.field.setCursorPosition(start + selected.getText().length());
        this.popup.close();
    }
}
```

- [ ] **Step 2: Zarejestruj w `ClientProxy.init`**

```java
    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new ChatScreenHandler());
    }
```

🚨 Rejestracja musi zostać **w `ClientProxy`**. `GuiScreenEvent` jest klasowo `@SideOnly(Side.CLIENT)`
razem z podklasami, więc `new ChatScreenHandler()` na dedykowanym serwerze byłby crashem
`SideTransformer` przy ładowaniu klasy — nie dopiero przy wywołaniu metody.

- [ ] **Step 3: Zbuduj i zacommituj**

```bash
./gradlew :commandsuggest:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

```bash
git add commandsuggest/src/main/java/com/spege/commandsuggest/client/
git commit -m "feat(commandsuggest): ChatScreenHandler - cztery eventy i podmiana tabCompletera"
```

---

## Task 17: Lang i wbudowane opisy

**Files:**
- Create: `commandsuggest/src/main/resources/assets/commandsuggest/lang/en_us.lang`
- Create: `commandsuggest/src/main/resources/assets/commandsuggest/commands/gamerule.json`
- Create: `commandsuggest/src/main/resources/assets/commandsuggest/commands/commandsuggest.json`
- Modify: `commandsuggest/src/main/resources/assets/commandsuggest/commands/index.json`

- [ ] **Step 1: `en_us.lang`** (cały tekst widziany przez gracza po angielsku — konwencja paczki)

```
commandsuggest.command.usage=/commandsuggest <reload|refresh>
commandsuggest.command.reloaded=Reloaded %s command descriptions and resent the tree to everyone.
commandsuggest.command.refreshed=Command tree refreshed.
commandsuggest.command.playeronly=Only a player can refresh their own command tree.
```

- [ ] **Step 2: `commandsuggest.json`**

```json
{
  "command": "commandsuggest",
  "sub": [
    { "lit": "reload",  "exec": true, "usage": "/commandsuggest reload - reread command descriptions" },
    { "lit": "refresh", "exec": true, "usage": "/commandsuggest refresh - resend your command tree" }
  ]
}
```

- [ ] **Step 3: `gamerule.json`**

Nazwy reguł są waniliowymi stałymi z 1.12.2, więc wolno je zaszyć. Wartość zostaje typu `word`,
a nie `bool`, bo cztery reguły (`randomTickSpeed`, `spawnRadius`, `maxEntityCramming`,
`maxCommandChainLength`) są liczbami — serwer i tak podpowie właściwe wartości.

```json
{
  "command": "gamerule",
  "args": [
    { "name": "rule", "type": "choice", "choices": [
      "announceAdvancements", "commandBlockOutput", "disableElytraMovementCheck",
      "doDaylightCycle", "doEntityDrops", "doFireTick", "doLimitedCrafting", "doMobLoot",
      "doMobSpawning", "doTileDrops", "doWeatherCycle", "gameLoopFunction", "keepInventory",
      "logAdminCommands", "maxCommandChainLength", "maxEntityCramming", "mobGriefing",
      "naturalRegeneration", "randomTickSpeed", "reducedDebugInfo", "sendCommandFeedback",
      "showDeathMessages", "spawnRadius", "spectatorsGenerateChunks"
    ]},
    { "name": "value", "type": "word" }
  ],
  "exec": true,
  "usage": "/gamerule <rule> [value]"
}
```

- [ ] **Step 4: `index.json`**

```json
["commandsuggest.json", "gamerule.json"]
```

- [ ] **Step 5: Commit**

```bash
git add commandsuggest/src/main/resources/assets/
git commit -m "feat(commandsuggest): lang i dwa pierwsze wbudowane opisy komend"
```

> **Opisy dla komend paczki to praca ciągła, nie część v1.** Nie zgaduj składni cudzej komendy.
> Sposób jest jeden i mechaniczny: znajdź jej `ICommand`, przeczytaj `getUsage()` oraz
> `getTabCompletions(...)` (tam widać dokładnie, który indeks argumentu przyjmuje jaką listę),
> i przepisz to na JSON. Kandydaci z DEv 1.2, po jednym pliku na komendę, do dorzucania w miarę
> potrzeb: `/srpevolution`, `/srpvectors`, `/ftbquests`, `/jm`, `/reskillable`, `/tombstone`,
> `/wizardry`. Każdy nowy plik dopisuje się do `index.json` — inaczej nie zostanie wczytany.

---

## Task 18: Zbuduj, wdróż i przejdź scenariusz ręczny

Tu weryfikuje się wszystko, czego nie sprawdzą testy jednostkowe.

- [ ] **Step 1: Pełny build repo**

```bash
./gradlew build
```

Oczekiwane: `BUILD SUCCESSFUL` i **siedem** jarów. Sprawdź, że powstał nasz:

```bash
ls commandsuggest/build/libs/
```

Oczekiwane: `commandsuggest-1.0.0.jar`.

- [ ] **Step 2: Wdróż do instancji**

Skopiuj `commandsuggest/build/libs/commandsuggest-1.0.0.jar` do
`C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\`.

🚨 Przy każdym kolejnym wgraniu **usuń jar poprzedniej wersji** — dwie wersje tego samego modid to
crash duplikatu przy starcie.

Upewnij się też, że **brigo nie wrócił** do `mods/` — dwa mody rysujące popup pod czatem będą
rysować dwa popupy.

- [ ] **Step 3: Start i log**

Odpal instancję i wejdź na świat. W `logs/latest.log` mają być trzy linie z prefiksem
`[commandsuggest]`: wczytane opisy, wysłane drzewo, odebrane drzewo. Zanotuj rozmiar pakietu z
drugiej linii — to jest pomiar, o który prosi §6 specu. Jeżeli przekracza **1 048 576 bajtów**,
zatrzymaj się: potrzebne jest chunkowanie i to jest osobne zadanie.

- [ ] **Step 4: Scenariusz ręczny**

Przejdź po kolei i odhacz:

| # | Co robisz | Czego oczekujesz |
|---|---|---|
| 1 | Otwórz czat, wpisz `/` | Popup z listą wszystkich komend, biały tekst |
| 2 | Dopisz `gam` | Lista zwężona do `gamerule` |
| 3 | TAB | W polu jest `/gamerule`, popup zamknięty |
| 4 | Spacja | Lista 24 reguł, żółte (typ `choice`), pod nią linia `/gamerule <rule> [value]` |
| 5 | ↓ ↓ ↓, potem TAB | Wstawiona trzecia reguła z listy |
| 6 | Kółko myszy nad listą | Lista się przewija, czat **nie** |
| 7 | Klik w pozycję | Wstawiona ta, w którą kliknięto |
| 8 | Esc przy otwartym popupie | Popup znika, czat **zostaje** otwarty; drugi Esc zamyka czat |
| 9 | `/gamerule keepInventory ` i TAB | Wartości od serwera (`true`/`false`) |
| 10 | `/` + nazwa komendy nieopisanej, np. `/tombstone `, TAB | Podpowiedzi z waniliowego tab-complete, linia usage z `getUsage()` |
| 11 | Enter na dowolnej komendzie | Wysyła to, co wpisane — **nie** podmienia na zaznaczoną podpowiedź |
| 12 | TAB w komendzie z >100 kandydatami | Na czacie może pojawić się `(only first 100 shown)` od VintageFix — **oczekiwane** na ścieżce serwerowej, patrz §11 specu |
| 13 | Sprawdź czat po każdym TAB | **Nigdy** nie ma waniliowej listy kandydatów przez przecinki |
| 14 | `/commandsuggest reload` | Komunikat o przeładowaniu, w logu ponowna wysyłka do wszystkich |
| 15 | Wyjdź na serwer bez tego moda (albo odetnij) | Po ~5 s linia INFO o trybie bez drzewa; TAB dalej podpowiada, bez kolorów |

- [ ] **Step 5: Sprawdź kolizję z Chunk-Pregeneratorem**

```bash
grep -n "AdvancedTabCompleter" "C:/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/latest.log"
```

Oczekiwane: brak trafień. Gdyby coś było — Chunk-Pregenerator sięga po ten sam mechanizm
i trzeba ustalić, który z nas ustawia `tabCompleter` jako ostatni.

- [ ] **Step 6: Commit poprawek, jeżeli scenariusz coś wykazał**

```bash
git add -A
git commit -m "fix(commandsuggest): poprawki z pierwszego przejscia scenariusza w grze"
```

---

## Task 19: Zaktualizuj CLAUDE.md

Plan dodaje siódmy mod i **pierwszy w repo zestaw testów**, więc dwa twierdzenia w CLAUDE.md
przestają być prawdziwe. Zostawienie ich to gorsza szkoda niż brak wpisu, bo są cytowane jako fakt.

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: Sekcja „Project"**

Zmień `**SIX mods in one repo:**` na `**SEVEN mods in one repo:**` i dopisz pozycję na końcu listy:

```markdown
- `commandsuggest/` — **public** (modid `commandsuggest`, `com.spege.commandsuggest`): popup
  podpowiadający komendy pod polem czatu, w miejsce brigo (usunięty z paczki 2026-08-10 — jego
  mixin w `GuiChat$ChatTabCompleter` padł na regresji CleanMix w ścieżce `INJECT_PREPARE_LEGACY`).
  **Zero mixinów** — cztery eventy `GuiScreenEvent` plus podmiana prywatnego `GuiChat.tabCompleter`
  na własną podklasę `TabCompleter` załatwiają to, na co brigo miało cztery mixiny. Nie jest
  ekstrakcją z contentu i nie zależy od niego. Projekt:
  `docs/superpowers/specs/2026-08-10-commandsuggest-design.md`.
```

- [ ] **Step 2: Sekcja „Build & Run" — trzy poprawki**

1. `settings.gradle` includes: dopisz `:commandsuggest`.
2. `./gradlew build` — `builds **all six** jars` → `**all seven**`, dopisz ścieżkę
   `commandsuggest/build/libs/`; w ostrzeżeniu o `clean :x:build` „the other five jars" → „the other six".
3. W linijce z `./gradlew :insanetweaks:build` dopisz `:commandsuggest:build` do listy.

- [ ] **Step 3: Popraw „No test suite, no lint task"**

Zastąp to zdanie:

```markdown
No lint task. **Jeden podprojekt ma testy: `commandsuggest`** — jego pakiet `core` nie zna ani
jednej klasy Minecrafta, więc chodzi na zwykłym JUnicie 4.12 (`./gradlew :commandsuggest:test`,
~45 testów: parser wejścia, matcher, kodeki JSON i binarny, `CommandIndex.prune`). Pozostałe sześć
modów testów nie ma i przy obecnym kształcie mieć nie może — ich kod jest nierozłączny od Minecrafta.
```

- [ ] **Step 4: Akapit o `libs/`**

Dopisz na końcu: `commandsuggest` **nie bierze z `libs/` niczego** i nie deobfuskuje niczego —
jedyna zależność spoza Minecrafta to Gson (`compileOnly` + `testImplementation`,
`com.google.code.gson:gson:2.8.0`, ta sama wersja, którą wozi MC), a Gsona dostarcza runtime.

- [ ] **Step 5: Sekcja „Mixins" — routing**

W zdaniu o routingu dopisz, że `commandsuggest` **nie ma pakietu mixinów i mieć nie ma** — a gdyby
kiedyś musiał, to z `minVersion 0.8`, bo regresja, która zabiła brigo, siedzi w ścieżce
`INJECT_PREPARE_LEGACY`. Zmień też nagłówek `— 6 mods, 6 mixin packages` na
`— 7 mods, 6 mixin packages`.

- [ ] **Step 6: Sekcja „Module gating"**

Dopisz, że `commandsuggest.cfg` jest **drugim** configiem po `enchanteraser` z
`category = "general"` zamiast `""`, z tego samego powodu: same pola proste, żadnych pól-kategorii.

- [ ] **Step 7: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: CLAUDE.md - siodmy mod commandsuggest i pierwszy zestaw testow w repo"
```

---

## Definition of done

- `./gradlew build` buduje siedem jarów.
- `./gradlew :commandsuggest:test` — wszystko zielone.
- Scenariusz z zadania 18 przeszedł w całości na instancji DEv 1.2.
- W `logs/latest.log` widać, który tryb się włączył.
- CLAUDE.md nie kłamie o liczbie modów ani o braku testów.
- Zmierzony rozmiar pakietu drzewa zanotowany (§6 specu prosi o pomiar, nie o zgadywanie).
