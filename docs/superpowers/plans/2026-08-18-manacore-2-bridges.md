# manacore — Plan 2: mosty do EBW i Trinkets and Baubles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Wymaga ukończenia:** [Plan 1 — rdzeń](2026-08-18-manacore-1-core.md). Wszystkie dziewięć jego kryteriów musi być spełnione, zanim zaczniesz tutaj.

**Goal:** Podpiąć pulę many z Planu 1 pod Electroblob's Wizardry (koszt czarów, bramka, przemapowanie trzech upgrade'ów różdżki) oraz przejąć pulę `MagicStats` z Trinkets and Baubles, tak żeby gracz widział jeden zasób zamiast trzech.

**Architecture:** Koszt jest liczony w czystym pakiecie `core` (testowalnym JUnitem) i pobierany z puli **wyłącznie w `SpellCastEvent$Post`**, który EBW posyła tylko po `Spell.cast() == true`. `Pre` i `Tick` służą jedynie za bramkę. Dwa `@Redirect` na `ItemWand` neutralizują własną manę różdżki, nie ruszając jej wartości w NBT. Pula TaB jest przekierowana mixinem na naszą.

**Tech Stack:** Java 8, Forge 1.12.2-14.23.5.2860, MixinBooter 7.1 (trasa późna, `ILateMixinLoader`), EBW 4.3.19 z CurseMaven, TaB deobfuskowany z `libs/`, JUnit 4.12.

🚨 **Komentarze i javadoc w kodzie piszemy PO ANGIELSKU.** Taka jest konwencja całego repozytorium — sprawdź `tombtweaks`, `enchanteraser`, `srpwizmixins`. Bloki kodu w tym planie mają komentarze po polsku, bo plan jest po polsku; **przy przepisywaniu ich do plików źródłowych przetłumacz komentarze na angielski.** Treść i sens zostają bez zmian, tłumaczy się tylko język.

---

## Fakty z bajtkodu, na których stoi ten plan

Zweryfikowane na EBW 4.3.19 — **nie przyjmuj ich na wiarę po aktualizacji EBW, powtórz `javap`**.

**F1. `ItemWand.cast` posyła `Post` dopiero po udanym caście:**
`Spell.cast()` → `ifeq` (false = wyjście z metody) → `new SpellCastEvent$Post` + `EventBus.post` → dopiero potem `consumeMana` różdżki.

**F2. `ItemWand.canCast` sprawdza manę różdżki PO naszym evencie:**
post `Pre` (lub `Tick` dla ciągłych) → `return false` gdy anulowany → `getCost * modifiers` → `getMana(stack)` i `if_icmpgt` → `tier czaru <= tier różdżki` → cooldown.

**F3. Czary ciągłe też idą przez `cast`, co tick:**
`onUsingTick` → `canCast(...)` → `cast(...)`. Czyli `Post` leci **co tick** dla `isContinuous`, a koszt rozkłada `getDistributedCost`.

**F4. `getDistributedCost(int cost, int castingTick)` (`protected static` na `ItemWand`):**

```java
if (castingTick % 20 == 0) return cost / 2 + cost % 2;
if (castingTick % 10 == 0) return cost / 2;
return 0;
```

Czyli pełny koszt na sekundę, w dwóch ratach. Replikujemy to u siebie (Task 1) zamiast sięgać po metodę `protected`.

**F5. Nazwy pól upgrade'ów w `WizardryItems`:** `storage_upgrade`, `siphon_upgrade`, `condenser_upgrade` (oraz `range_`, `duration_`, `cooldown_`, `blast_`, `attunement_`, `melee_`).

**F6. `SpellCastEvent$Source`:** `WAND`, `SCROLL`, `COMMAND`, `NPC`, `DISPENSER`, `OTHER`. W v1 płaci **tylko `WAND`**.

**F7. `SpellModifiers`** ma stałe `POTENCY`, `COST`, `CHARGEUP`, `PROGRESSION` i metodę `float get(String)`.

**F8. Sygnatury TaB `MagicStats`:** `float getMana()`, `void setMana(float)`, `void addMana(float)`, `boolean spendMana(float)`, `float getMaxMana()`, `void refillMana()`, `boolean needMana()`.

**F9. TaB ma configi `ConfigManaBarHud.shown` i `EntityManaConfig.mana_enabled`** — wyłączenie paska TaB nie wymaga mixina.

---

## Struktura plików

| Plik | Odpowiedzialność |
|---|---|
| `core/CostMath.java` | koszt rozłożony, refund, przelicznik jednostek — **zero typów Minecrafta** |
| `core/ManaCoreLateBooter.java` | `ILateMixinLoader` — bramkuje oba configi mixinów obecnością modu |
| `compat/ebw/SpellCostResolver.java` | jedno miejsce, gdzie powstaje liczba „ile kosztuje ten czar" |
| `compat/ebw/EbwSpellCostHandler.java` | `Pre`/`Post`/`Tick`/`Finish` |
| `compat/ebw/WandUpgradeBridge.java` | `condenser` → regen, `siphon` → mana za zabójstwo, `storage` → refund |
| `compat/wizardryutils/WizardryUtilsBridge.java` | refleksyjny odczyt atrybutów `COST` / `<element>_COST` |
| `compat/tab/TabManaItems.java` | Mana Crystal → +max, Reagent/Candy → current |
| `mixins/ebw/MixinItemWand.java` | dwa `@Redirect` |
| `mixins/tab/MixinMagicStats.java` | przekierowanie puli TaB |
| `resources/mixins.manacore.ebw.json` | config mixinów EBW |
| `resources/mixins.manacore.tab.json` | config mixinów TaB |
| `config/categories/EbwCategory.java` / `TabCategory.java` | nowe kategorie configu |
| `src/test/java/.../core/CostMathTest.java` | testy JUnit |

---

## Task 1: Matematyka kosztu (TDD)

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/core/CostMath.java`
- Test: `manacore/src/test/java/com/spege/manacore/core/CostMathTest.java`

- [ ] **Step 1: Napisz testy (mają nie przechodzić)**

`manacore/src/test/java/com/spege/manacore/core/CostMathTest.java`:

```java
package com.spege.manacore.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CostMathTest {

    private static final double EPS = 1.0e-9D;

    // --- distributedCost: replikacja ItemWand.getDistributedCost (fakt F4) ---

    @Test
    public void kosztRozlozonyPlaciPolowkiCoPolSekundy() {
        assertEquals(3, CostMath.distributedCost(5, 20));  // 5/2 + 5%2 = 2 + 1
        assertEquals(2, CostMath.distributedCost(5, 10));  // 5/2 = 2
        assertEquals(0, CostMath.distributedCost(5, 7));
    }

    @Test
    public void kosztRozlozonyDlaTikuZeroLiczyPelnaRate() {
        // 0 % 20 == 0, wiec pierwszy tick kanalowania placi wieksza polowke
        assertEquals(3, CostMath.distributedCost(5, 0));
    }

    @Test
    public void kosztRozlozonySumujeSieDoPelnegoKosztuNaSekunde() {
        int total = 0;
        for (int tick = 20; tick < 40; tick++) {
            total += CostMath.distributedCost(7, tick);
        }
        assertEquals(7, total);
    }

    @Test
    public void kosztRozlozonyDlaZerowegoKosztuJestZerowy() {
        assertEquals(0, CostMath.distributedCost(0, 20));
    }

    // --- resolveCost ---

    @Test
    public void kosztMnozySiePrzezWszystkieWspolczynniki() {
        // 10 bazowo * 1.5 modyfikator czaru * 2.0 config * 0.5 wizardryutils = 15
        assertEquals(15.0D, CostMath.resolveCost(10, 1.5D, 2.0D, 0.5D), EPS);
    }

    @Test
    public void kosztNigdyNieSchodziPonizejZera() {
        assertEquals(0.0D, CostMath.resolveCost(10, -1.0D, 1.0D, 1.0D), EPS);
    }

    @Test
    public void zerowyWspolczynnikDajeCzarZaDarmo() {
        assertEquals(0.0D, CostMath.resolveCost(10, 1.0D, 0.0D, 1.0D), EPS);
    }

    // --- refund ze `storage` ---

    @Test
    public void refundRosnieZPojemnosciaRozdzki() {
        // 100 pojemnosci bazowej, 700 faktycznej, wspolczynnik 0.05 na kazde 100 nadwyzki
        // (700-100)/100 * 0.05 = 0.30
        assertEquals(0.30D, CostMath.refundFraction(700, 100, 100, 0.05D), EPS);
    }

    @Test
    public void refundJestZerowyGdyRozdzkaNieMaNadwyzki() {
        assertEquals(0.0D, CostMath.refundFraction(100, 100, 100, 0.05D), EPS);
        assertEquals(0.0D, CostMath.refundFraction(50, 100, 100, 0.05D), EPS);
    }

    @Test
    public void refundJestObcinanyDoStuProcent() {
        assertEquals(1.0D, CostMath.refundFraction(100000, 100, 100, 0.05D), EPS);
    }

    @Test
    public void refundJestZerowyDlaNiepoprawnegoKroku() {
        assertEquals(0.0D, CostMath.refundFraction(700, 100, 0, 0.05D), EPS);
    }

    // --- przelicznik jednostek TaB ---

    @Test
    public void przelicznikDzialaWObieStrony() {
        assertEquals(50.0F, CostMath.toForeignUnits(100.0D, 0.5D), 1.0e-6F);
        assertEquals(100.0D, CostMath.fromForeignUnits(50.0F, 0.5D), EPS);
    }

    @Test
    public void przelicznikZeroTraktujemyJakJedenDoJednego() {
        assertEquals(100.0F, CostMath.toForeignUnits(100.0D, 0.0D), 1.0e-6F);
        assertEquals(100.0D, CostMath.fromForeignUnits(100.0F, 0.0D), EPS);
    }
}
```

- [ ] **Step 2: Uruchom testy i potwierdź, że NIE przechodzą**

```bash
./gradlew :manacore:test
```

Oczekiwane: `FAILED` — brak klasy `CostMath`.

- [ ] **Step 3: Implementacja**

`manacore/src/main/java/com/spege/manacore/core/CostMath.java`:

```java
package com.spege.manacore.core;

/**
 * Czysta matematyka kosztu czaru. ZERO typów Minecrafta.
 */
public final class CostMath {

    private CostMath() {
    }

    /**
     * Replikacja ItemWand.getDistributedCost(int, int) z EBW 4.3.19 (fakt F4 planu).
     * Nie wołamy oryginału, bo jest `protected static` - a duplikat i tak musi byc
     * przetestowany, zeby zmiana po stronie EBW dala sie wykryc.
     */
    public static int distributedCost(int cost, int castingTick) {
        if (castingTick % 20 == 0) {
            return cost / 2 + cost % 2;
        }
        if (castingTick % 10 == 0) {
            return cost / 2;
        }
        return 0;
    }

    /**
     * @param baseCost        Spell.getCost()
     * @param spellMultiplier SpellModifiers.get(SpellModifiers.COST)
     * @param configMultiplier nasz mnoznik z manacore.cfg
     * @param foreignMultiplier mnoznik z wizardryutils (1.0 gdy modu nie ma)
     */
    public static double resolveCost(int baseCost, double spellMultiplier,
            double configMultiplier, double foreignMultiplier) {
        double cost = baseCost * spellMultiplier * configMultiplier * foreignMultiplier;
        return cost < 0.0D ? 0.0D : cost;
    }

    /**
     * Ulamek many zwracanej po udanym caście, wyliczony z NADWYZKI pojemnosci rozdzki
     * ponad pojemnosc bazowa. Zawsze w [0, 1].
     *
     * @param wandCapacity    getManaCapacity(stack), czyli tier + upgrade'y `storage`
     * @param baselineCapacity pojemnosc, ponizej ktorej nie ma zadnego refundu
     * @param capacityStep    co ile punktow nadwyzki naliczamy `fractionPerStep`
     */
    public static double refundFraction(int wandCapacity, int baselineCapacity,
            int capacityStep, double fractionPerStep) {
        if (capacityStep <= 0 || fractionPerStep <= 0.0D) {
            return 0.0D;
        }
        int surplus = wandCapacity - baselineCapacity;
        if (surplus <= 0) {
            return 0.0D;
        }
        double fraction = ((double) surplus / capacityStep) * fractionPerStep;
        return fraction > 1.0D ? 1.0D : fraction;
    }

    /** Nasza mana -> jednostki obcego moda. Skala <= 0 znaczy 1:1. */
    public static float toForeignUnits(double ours, double scale) {
        return scale <= 0.0D ? (float) ours : (float) (ours * scale);
    }

    /** Jednostki obcego moda -> nasza mana. Skala <= 0 znaczy 1:1. */
    public static double fromForeignUnits(float foreign, double scale) {
        return scale <= 0.0D ? foreign : foreign / scale;
    }
}
```

- [ ] **Step 4: Uruchom testy**

```bash
./gradlew :manacore:test
```

Oczekiwane: `BUILD SUCCESSFUL`, 21 testów zielonych łącznie (8 z `ManaMathTest` + 13 z `CostMathTest`).

- [ ] **Step 5: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/core/CostMath.java manacore/src/test
git commit -m "feat(manacore): matematyka kosztu czaru z testami"
```

---

## Task 2: Zależności, config mixinów i late booter

**Files:**
- Modify: `manacore/build.gradle`
- Create: `manacore/src/main/java/com/spege/manacore/core/ManaCoreLateBooter.java`
- Create: `manacore/src/main/resources/mixins.manacore.ebw.json`
- Create: `manacore/src/main/resources/mixins.manacore.tab.json`
- Create: `manacore/src/main/java/com/spege/manacore/config/categories/EbwCategory.java`
- Create: `manacore/src/main/java/com/spege/manacore/config/categories/TabCategory.java`
- Modify: `manacore/src/main/java/com/spege/manacore/config/ManaCoreConfig.java`

- [ ] **Step 1: Dodaj zależności**

W `manacore/build.gradle`, w bloku `dependencies`, po `implementation 'zone.rong:mixinbooter:7.1'`:

```groovy
    // Ten sam koordynat, ktorego uzywaja insanetweaks i reskilltweaks. NIE dodawaj jara EBW do libs/.
    implementation fg.deobf('curse.maven:ElectroblobsWizardry-265642:8320066')
    // TaB: mixin nazywa typ MagicStats w sygnaturze, wiec jar musi byc deobfuskowany.
    implementation fg.deobf(files(rootProject.file('libs/Trinkets and Baubles-Forge-1.12.2-0.33.1.jar')))
```

> `libs/` ma TaB **0.33.1**, a pack **0.33.3**. Zanim zaczniesz Task 7, skopiuj jar 0.33.3 z `mods/` instancji do `libs/`, usuń 0.33.1 i zaktualizuj tę linijkę — kompilacja przeciwko starszej wersji to dokładnie ten rodzaj driftu, który potem wychodzi dopiero w runtime.

- [ ] **Step 2: Nowe kategorie configu**

`.../config/categories/EbwCategory.java`:

```java
package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class EbwCategory {

    @Config.Comment("Czy podpinac koszt czarow EBW pod pule gracza.")
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment("Globalny mnoznik kosztu czarow, na wierzchu modyfikatorow samego EBW.")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double costMultiplier = 1.0D;

    @Config.Comment("Czy respektowac atrybuty COST z moda wizardryutils, gdy jest obecny.")
    public boolean useWizardryUtilsAttributes = true;

    @Config.Comment("Pojemnosc rozdzki, ponizej ktorej upgrade `storage` nie daje zadnego refundu.")
    @Config.RangeInt(min = 0, max = 100000)
    public int refundBaselineCapacity = 100;

    @Config.Comment("Co ile punktow nadwyzki pojemnosci naliczamy `refundFractionPerStep`.")
    @Config.RangeInt(min = 1, max = 100000)
    public int refundCapacityStep = 100;

    @Config.Comment("Ulamek kosztu zwracany za kazdy krok nadwyzki pojemnosci rozdzki.")
    @Config.RangeDouble(min = 0.0D, max = 1.0D)
    public double refundFractionPerStep = 0.05D;

    @Config.Comment("Ile many na sekunde daje jeden poziom upgrade'u `condenser`.")
    @Config.RangeDouble(min = 0.0D, max = 1000.0D)
    public double condenserRegenPerLevel = 0.5D;

    @Config.Comment("Ile many daje jeden poziom upgrade'u `siphon` za zabojstwo.")
    @Config.RangeDouble(min = 0.0D, max = 10000.0D)
    public double siphonManaPerLevel = 5.0D;
}
```

`.../config/categories/TabCategory.java`:

```java
package com.spege.manacore.config.categories;

import net.minecraftforge.common.config.Config;

public class TabCategory {

    @Config.Comment("Czy przejmowac pule many Trinkets and Baubles.")
    @Config.RequiresMcRestart
    public boolean enabled = true;

    @Config.Comment("Ile jednostek many TaB odpowiada jednej naszej. 0 lub mniej znaczy 1:1.")
    @Config.RangeDouble(min = 0.0D, max = 1000.0D)
    public double unitScale = 1.0D;

    @Config.Comment("Ile trwalego maksimum dodaje zjedzenie Mana Crystal.")
    @Config.RangeDouble(min = 0.0D, max = 10000.0D)
    public double manaCrystalMaxBonus = 5.0D;

    @Config.Comment("Sufit trwalego maksimum z samych Mana Crystal.")
    @Config.RangeDouble(min = 0.0D, max = 1.0E6D)
    public double manaCrystalCap = 100.0D;
}
```

> 🚨 **Dług projektowy do rozstrzygnięcia w tym zadaniu, nie do przeoczenia.** `manaCrystalCap` i `pool.progressionCap` to **dwa niezależne sufity nałożone na to samo pole** `progressionBonus`. Wartownia w `ManaMath.afterProgressionGain` („nigdy nie obniżaj") sprawia, że nic nie ginie — bez niej kryształy podbijające pulę do 80 zostałyby skasowane do 50 przy pierwszym rzuconym czarze. Ale to nadal znaczy, że żaden z dwóch sufitów nie jest prawdziwym sufitem: gracz może przekroczyć `progressionCap` kryształami i odwrotnie.
>
> Trzy wyjścia, do wyboru przy implementacji: (a) rozdzielić na dwa pola w capability, każde z własnym sufitem, sumowane w atrybucie — najczystsze, ale zmienia format NBT; (b) jeden wspólny sufit i skasowanie `manaCrystalCap`; (c) świadomie zostawić, dokumentując, że sufity są miękkie i dotyczą tylko *przyrostu z danego źródła*. Nie implementuj (c) po cichu — jeśli je wybierasz, zapisz to w komentarzu configu.

- [ ] **Step 3: Podepnij kategorie do roota configu**

W `ManaCoreConfig` dodaj dwa pola obok istniejących trzech:

```java
    public static EbwCategory ebw = new EbwCategory();
    public static TabCategory tab = new TabCategory();
```

wraz z importami `com.spege.manacore.config.categories.EbwCategory` i `...TabCategory`.

> Pola nadal są **wyłącznie obiektami kategorii**, więc `category = ""` zostaje poprawne. Gdybyś dołożył tu pole proste albo tablicę, `ConfigManager.sync` rzuci twardym wyjątkiem przy konstrukcji moda.

- [ ] **Step 4: Configi mixinów**

`manacore/src/main/resources/mixins.manacore.ebw.json`:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.spege.manacore.mixins.ebw",
  "refmap": "",
  "target": "@env(DEFAULT)",
  "compatibilityLevel": "JAVA_8",
  "mixins": [
    "MixinItemWand"
  ]
}
```

`manacore/src/main/resources/mixins.manacore.tab.json`:

```json
{
  "required": true,
  "minVersion": "0.8",
  "package": "com.spege.manacore.mixins.tab",
  "refmap": "",
  "target": "@env(DEFAULT)",
  "compatibilityLevel": "JAVA_8",
  "mixins": [
    "MixinMagicStats"
  ]
}
```

> `"refmap": ""` jest obowiązkowe — cały repo kompiluje się z `-proc:none`, więc procesor adnotacji mixina nie działa i żaden refmap nie powstaje. Dopasowanie idzie po jawnych nazwach z `remap = false`.
> `minVersion` **0.8**, nie niżej: konfiguracje deklarujące mniej trafiają na ścieżkę `INJECT_PREPARE_LEGACY`, w której regresja CleanMixa zabiła kiedyś moda `brigo`.

- [ ] **Step 5: Late booter**

`manacore/src/main/java/com/spege/manacore/core/ManaCoreLateBooter.java`:

```java
package com.spege.manacore.core;

import java.util.ArrayList;
import java.util.List;

import com.spege.manacore.ManaCoreMod;

import net.minecraftforge.fml.common.Loader;
import zone.rong.mixinbooter.ILateMixinLoader;

/**
 * Trasa POZNA - oba configi celuja w klasy modow, wiec musza byc bramkowane obecnoscia
 * tych modow. Ta klasa MUSI zyc poza pakietem `mixins`: Mixin zabrania Class.forName()
 * klas niebedacych mixinami wewnatrz `*.mixins.*`.
 */
@SuppressWarnings("deprecation")
@zone.rong.mixinbooter.MixinLoader
public class ManaCoreLateBooter implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        List<String> configs = new ArrayList<String>();
        configs.add("mixins.manacore.ebw.json");
        configs.add("mixins.manacore.tab.json");
        return configs;
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        if ("mixins.manacore.ebw.json".equals(mixinConfig)) {
            boolean present = Loader.isModLoaded("ebwizardry");
            ManaCoreMod.LOGGER.info("[ManaCore] EBW mixins queue = {}", Boolean.valueOf(present));
            return present;
        }
        if ("mixins.manacore.tab.json".equals(mixinConfig)) {
            boolean present = Loader.isModLoaded("xat");
            ManaCoreMod.LOGGER.info("[ManaCore] TaB mixins queue = {}", Boolean.valueOf(present));
            return present;
        }
        return false;
    }
}
```

> Log przy **odmowie** jest tu celowy. Bez niego cichy brak mixina wygląda dokładnie tak samo jak mixin, którego cel nie został jeszcze załadowany — a to dwie zupełnie różne diagnozy.
>
> 🚨 **Adnotacja to `@zone.rong.mixinbooter.MixinLoader`, NIE `@LateMixin`** — pierwotny tekst tego planu był tu błędny. MixinBooter 7.1 (wersja, na której stoi całe repo) zawiera dokładnie trzy klasy: `IEarlyMixinLoader`, `ILateMixinLoader` i `MixinLoader`. `LateMixin` nie istnieje i kompilacja pada na `cannot find symbol`. `MixinLoader` jest oznaczone jako deprecated, stąd `@SuppressWarnings("deprecation")` — i dokładnie tak robi pozostałe sześć modów w tym repo (`enchanteraser`, `insanetweaks`, `reskilltweaks`, `srpwizcore`, `srpwizmixins`, `tombtweaks`). Sprawdź `grep -rn "MixinLoader" --include=*.java */src/main/java`, zanim napiszesz kolejny booter.

- [ ] **Step 6: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL` (mixiny jeszcze nie istnieją — configi wskazują na klasy, które dodasz w Task 3 i 7; jeśli build padnie na braku klas, dokończ Task 3 i wróć tu po commit).

- [ ] **Step 7: Commit**

```bash
git add manacore/build.gradle manacore/src/main/resources manacore/src/main/java/com/spege/manacore/core manacore/src/main/java/com/spege/manacore/config
git commit -m "feat(manacore): zaleznosci EBW/TaB, configi mixinow i late booter"
```

---

## Task 3: Mixin na `ItemWand`

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/mixins/ebw/MixinItemWand.java`

- [ ] **Step 1: Potwierdź cele na bajtkodzie ZANIM napiszesz mixin**

```bash
javap -p -c -cp libs/ElectroblobsWizardry-4.3.19.jar electroblob.wizardry.item.ItemWand > /tmp/itemwand.txt
```

Następnie przypisz każdy `invoke` do metody, w której siedzi — **nie używaj `grep -A`**, bo przelewa się przez granice metod i wskaże złą metodę, co kończy się crashem „Scanned 0 target(s)":

```bash
awk '/^  [a-zA-Z].*\(.*\);$/{m=$0} /getMana|consumeMana/{print m" || "$0}' /tmp/itemwand.txt
```

Oczekiwane: `getMana` wewnątrz `canCast`, `consumeMana` wewnątrz `cast` (oraz osobne `consumeMana` w `func_77644_a`, którego **nie** ruszamy — to obrażenia w zwarciu).

- [ ] **Step 2: Napisz mixin**

`manacore/src/main/java/com/spege/manacore/mixins/ebw/MixinItemWand.java`:

```java
package com.spege.manacore.mixins.ebw;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.manacore.config.ManaCoreConfig;

import electroblob.wizardry.item.ItemWand;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/**
 * Neutralizuje wlasna mane rozdzki, NIE dotykajac jej wartosci w NBT - dzieki temu
 * odinstalowanie moda wraca do stanu wyjsciowego.
 *
 * Oba @Redirect maja wlascieciela `ItemWand`, czyli dokladnie ta klase, ktora jest
 * wlascicielem instrukcji invoke. To jest wymog @Redirect: receiver musi byc dokladnym
 * wlascicielem wywolania, nie nadtypem ani interfejsem.
 */
@Mixin(value = ItemWand.class, remap = false)
public abstract class MixinItemWand {

    /**
     * Bramka `koszt > mana rozdzki` w canCast. Zwracamy pojemnosc, a NIE Integer.MAX_VALUE -
     * dzieki temu limit tieru rozdzki nadal dziala jako sufit pojedynczego czaru.
     */
    @Redirect(
            method = "canCast",
            at = @At(value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/ItemWand;getMana(Lnet/minecraft/item/ItemStack;)I"),
            remap = false)
    private int manacore$bypassWandManaGate(ItemWand self, ItemStack stack) {
        if (!ManaCoreConfig.ebw.enabled) {
            return self.getMana(stack);
        }
        return self.getManaCapacity(stack);
    }

    /** Rozdzka nie traci wlasnej many przy udanym caście. */
    @Redirect(
            method = "cast",
            at = @At(value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/ItemWand;consumeMana(Lnet/minecraft/item/ItemStack;ILnet/minecraft/entity/EntityLivingBase;)V"),
            remap = false)
    private void manacore$skipWandManaConsumption(ItemWand self, ItemStack stack, int cost, EntityLivingBase caster) {
        if (!ManaCoreConfig.ebw.enabled) {
            self.consumeMana(stack, cost, caster);
        }
    }
}
```

> `ManaCoreConfig.ebw.enabled` ma `@RequiresMcRestart`, ale **to nie znaczy, że bramkuje mixin** — mixin aplikuje się zawsze, gdy EBW jest obecne. Flaga jest wczesnym wyjściem w ciele handlera, dokładnie jak tutaj. Ten config **nie** deklaruje `IMixinConfigPlugin`.

- [ ] **Step 3: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/mixins
git commit -m "feat(manacore): mixin neutralizujacy mane rozdzki EBW"
```

---

## Task 4: Resolver kosztu i most do wizardryutils

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/compat/wizardryutils/WizardryUtilsBridge.java`
- Create: `manacore/src/main/java/com/spege/manacore/compat/ebw/SpellCostResolver.java`

- [ ] **Step 1: Refleksyjny most do wizardryutils**

`.../compat/wizardryutils/WizardryUtilsBridge.java`:

```java
package com.spege.manacore.compat.wizardryutils;

import java.lang.reflect.Field;

import javax.annotation.Nullable;

import com.spege.manacore.ManaCoreMod;

import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Loader;

/**
 * Soft-integracja: wizardryutils rejestruje wlasne RangedAttribute na graczu
 * (COST oraz warianty per-zywiol). Czytamy je REFLEKSYJNIE, zeby nie brac zaleznosci
 * kompilacyjnej od moda, ktorego moze nie byc.
 */
public final class WizardryUtilsBridge {

    private static final String MODID = "wizardryutils";
    private static final String ATTRIBUTES_CLASS = "com.windanesz.wizardryutils.server.Attributes";
    private static final String COST_FIELD = "COST";

    private static boolean initialized;
    private static boolean available;
    @Nullable
    private static IAttribute costAttribute;

    private WizardryUtilsBridge() {
    }

    private static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!Loader.isModLoaded(MODID)) {
            ManaCoreMod.LOGGER.info("[ManaCore] wizardryutils absent - spell cost attributes not used");
            return;
        }

        try {
            Class<?> clazz = Class.forName(ATTRIBUTES_CLASS);
            Field field = clazz.getDeclaredField(COST_FIELD);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof IAttribute) {
                costAttribute = (IAttribute) value;
                available = true;
                ManaCoreMod.LOGGER.info("[ManaCore] wizardryutils COST attribute bound");
            } else {
                ManaCoreMod.LOGGER.warn("[ManaCore] wizardryutils COST is not an IAttribute - skipping");
            }
        } catch (Exception e) {
            ManaCoreMod.LOGGER.warn("[ManaCore] failed to bind wizardryutils COST attribute", e);
        }
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    /** Zwraca 1.0, gdy modu nie ma albo atrybut jest niedostepny - czyli "brak wplywu". */
    public static double getCostMultiplier(@Nullable EntityPlayer player) {
        init();
        if (!available || player == null || costAttribute == null) {
            return 1.0D;
        }
        IAttributeInstance instance = player.getEntityAttribute(costAttribute);
        return instance == null ? 1.0D : instance.getAttributeValue();
    }
}
```

- [ ] **Step 2: Resolver kosztu**

`.../compat/ebw/SpellCostResolver.java`:

```java
package com.spege.manacore.compat.ebw;

import javax.annotation.Nullable;

import com.spege.manacore.compat.wizardryutils.WizardryUtilsBridge;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.CostMath;

import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.player.EntityPlayer;

/** Jedyne miejsce w kodzie, gdzie powstaje liczba "ile kosztuje ten czar". */
public final class SpellCostResolver {

    private SpellCostResolver() {
    }

    public static double resolve(@Nullable EntityPlayer player, Spell spell, SpellModifiers modifiers) {
        double foreign = ManaCoreConfig.ebw.useWizardryUtilsAttributes
                ? WizardryUtilsBridge.getCostMultiplier(player)
                : 1.0D;

        return CostMath.resolveCost(
                spell.getCost(),
                modifiers.get(SpellModifiers.COST),
                ManaCoreConfig.ebw.costMultiplier,
                foreign);
    }

    /**
     * Koszt pojedynczego ticka czaru ciaglego, rozlozony tak samo jak robi to EBW.
     *
     * 🚨 `continuousSecondCost`, a NIE `(int) Math.round(full)`. Zaokraglenie do najblizszej
     * calkowitej zamienia czar o koszcie 0.4 na sekunde w TRWALE DARMOWY, a przy wartosci
     * nieskonczonej daje -1 (Math.round -> Long.MAX_VALUE, rzutowanie na int -> -1), czyli
     * ujemny koszt, ktory w ManaAPI.spendQuiet DODAJE mane zamiast ja odejmowac.
     */
    public static double resolveContinuousTick(@Nullable EntityPlayer player, Spell spell,
            SpellModifiers modifiers, int castingTick) {
        double full = resolve(player, spell, modifiers);
        return CostMath.distributedCost(CostMath.continuousSecondCost(full), castingTick);
    }
}
```

- [ ] **Step 3: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/compat
git commit -m "feat(manacore): resolver kosztu czaru z soft-integracja wizardryutils"
```

---

## Task 5: Handler zdarzeń EBW

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/compat/ebw/EbwSpellCostHandler.java`
- Modify: `manacore/src/main/java/com/spege/manacore/ManaCoreMod.java`

- [ ] **Step 1: Handler**

`.../compat/ebw/EbwSpellCostHandler.java`:

```java
package com.spege.manacore.compat.ebw;

import com.spege.manacore.ManaCoreMod;
import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.CostMath;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.ItemWand;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Zasada nadrzedna: Pre i Tick sa WYLACZNIE bramkami, odjecie many nastepuje w Post.
 * EBW posyla Post dopiero po Spell.cast() == true, wiec czar, ktory sie nie odpalil,
 * nic nie kosztuje. To jest glowna roznica wobec moda player_mana.
 */
public class EbwSpellCostHandler {

    @SubscribeEvent
    public void onSpellPre(SpellCastEvent.Pre event) {
        if (!applies(event)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getCaster();
        double cost = SpellCostResolver.resolve(player, event.getSpell(), event.getModifiers());

        if (ManaAPI.getMana(player) < cost) {
            event.setCanceled(true);
            if (!player.world.isRemote) {
                player.sendStatusMessage(new TextComponentTranslation("manacore.message.not_enough"), true);
            }
        }
    }

    @SubscribeEvent
    public void onSpellTick(SpellCastEvent.Tick event) {
        if (!applies(event)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getCaster();
        double cost = SpellCostResolver.resolveContinuousTick(
                player, event.getSpell(), event.getModifiers(), event.getCastingTick());

        if (cost > 0.0D && ManaAPI.getMana(player) < cost) {
            event.setCanceled(true);
        }
    }

    /**
     * Odjecie. Dla czarow ciaglych ten event leci CO TICK (bo onUsingTick wola cast()),
     * wiec koszt musi byc rozlozony dokladnie tak, jak robi to EBW.
     */
    @SubscribeEvent
    public void onSpellPost(SpellCastEvent.Post event) {
        if (!applies(event)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getCaster();
        if (player.world.isRemote) {
            return;
        }

        boolean continuous = event.getSpell().isContinuous;
        double cost = continuous
                ? SpellCostResolver.resolveContinuousTick(player, event.getSpell(), event.getModifiers(),
                        player.getItemInUseMaxCount())
                : SpellCostResolver.resolve(player, event.getSpell(), event.getModifiers());

        // 🚨 Czary ciagle: `Post` leci CO TICK, wiec `spend` z natychmiastowa synchronizacja
        // dalby pakiet na gracza na tick przez caly czas kanalowania. `spendQuiet` odejmuje
        // i zostawia pule dirty - okresowy `syncIfDirty` z handlera regenu doslе ja w ciagu
        // pol sekundy. Dla czarow jednorazowych zostaje zwykly `spend`, zeby gracz zobaczyl
        // ubytek natychmiast po rzuceniu.
        if (cost > 0.0D) {
            if (continuous) {
                ManaAPI.spendQuiet(player, cost);
            } else {
                ManaAPI.spend(player, cost);
            }
        }

        // Refund ze `storage` - tylko dla czarow jednorazowych. Dla ciaglych rozlicza go Finish,
        // zeby refund per-tick nie zamienil sie w drugi regen.
        if (!continuous) {
            applyRefund(player, cost);
        }

        ManaAPI.addProgression(player, ManaCoreConfig.pool.progressionPerCast);
    }

    @SubscribeEvent
    public void onSpellFinish(SpellCastEvent.Finish event) {
        if (!applies(event)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getCaster();
        if (player.world.isRemote) {
            return;
        }
        double totalCost = SpellCostResolver.resolve(player, event.getSpell(), event.getModifiers())
                * (event.getCastingTick() / 20.0D);
        applyRefund(player, totalCost);
    }

    private void applyRefund(EntityPlayer player, double cost) {
        if (cost <= 0.0D) {
            return;
        }
        ItemStack stack = player.getHeldItem(EnumHand.MAIN_HAND);
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemWand)) {
            return;
        }
        ItemWand wand = (ItemWand) stack.getItem();

        double fraction = CostMath.refundFraction(
                wand.getManaCapacity(stack),
                ManaCoreConfig.ebw.refundBaselineCapacity,
                ManaCoreConfig.ebw.refundCapacityStep,
                ManaCoreConfig.ebw.refundFractionPerStep);

        if (fraction > 0.0D) {
            ManaAPI.add(player, cost * fraction);
        }
    }

    /** Wylacznie Source.WAND i wylacznie gdy casterem jest gracz. */
    private boolean applies(SpellCastEvent event) {
        if (!ManaCoreConfig.ebw.enabled) {
            return false;
        }
        if (event.getSource() != SpellCastEvent.Source.WAND) {
            return false;
        }
        EntityLivingBase caster = event.getCaster();
        return caster instanceof EntityPlayer;
    }

    static {
        ManaCoreMod.LOGGER.info("[ManaCore] EBW spell cost handler loaded");
    }
}
```

- [ ] **Step 2: Zarejestruj handler warunkowo**

W `ManaCoreMod.init`, przed `proxy.init(event)`:

```java
        if (net.minecraftforge.fml.common.Loader.isModLoaded("ebwizardry")
                && com.spege.manacore.config.ManaCoreConfig.ebw.enabled) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.manacore.compat.ebw.EbwSpellCostHandler());
            LOGGER.info("[ManaCore] EBW bridge registered");
        } else {
            LOGGER.info("[ManaCore] EBW bridge NOT registered (mod present={}, enabled={})",
                    Boolean.valueOf(net.minecraftforge.fml.common.Loader.isModLoaded("ebwizardry")),
                    Boolean.valueOf(com.spege.manacore.config.ManaCoreConfig.ebw.enabled));
        }
```

- [ ] **Step 3: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/compat/ebw manacore/src/main/java/com/spege/manacore/ManaCoreMod.java
git commit -m "feat(manacore): koszt czarow EBW pobierany po udanym cascie"
```

---

## Task 6: Przemapowanie upgrade'ów `condenser` i `siphon`

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/compat/ebw/WandUpgradeBridge.java`
- Modify: `manacore/src/main/java/com/spege/manacore/ManaCoreMod.java`

> `storage` jest już obsłużony w Task 5 przez `applyRefund`. Tutaj zostają dwa pozostałe.

- [ ] **Step 1: Handler upgrade'ów**

`.../compat/ebw/WandUpgradeBridge.java`:

```java
package com.spege.manacore.compat.ebw;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.config.ManaCoreConfig;

import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * `condenser` -> regen NASZEJ puli, `siphon` -> mana do NASZEJ puli za zabojstwo.
 * Oba upgrade'y w waniliowym EBW ladowaly mane rozdzki, ktora u nas nie placi juz za czary.
 */
public class WandUpgradeBridge {

    /** Regen naliczamy raz na sekunde, nie co tick - liczenie upgrade'ow to odczyt NBT. */
    private static final int CONDENSER_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world.isRemote) {
            return;
        }
        if (player.ticksExisted % CONDENSER_INTERVAL_TICKS != 0) {
            return;
        }

        int level = getUpgradeLevel(player, WizardryItems.condenser_upgrade);
        if (level <= 0) {
            return;
        }
        double perSecond = level * ManaCoreConfig.ebw.condenserRegenPerLevel;
        if (perSecond > 0.0D) {
            ManaAPI.add(player, perSecond);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().world.isRemote) {
            return;
        }
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getSource().getTrueSource();

        int level = getUpgradeLevel(player, WizardryItems.siphon_upgrade);
        if (level <= 0) {
            return;
        }
        double gain = level * ManaCoreConfig.ebw.siphonManaPerLevel;
        if (gain > 0.0D) {
            ManaAPI.add(player, gain);
        }
    }

    /** Suma poziomow upgrade'u na rozdzkach w obu rekach. */
    private int getUpgradeLevel(EntityPlayer player, net.minecraft.item.Item upgrade) {
        int total = 0;
        total += levelOf(player.getHeldItem(EnumHand.MAIN_HAND), upgrade);
        total += levelOf(player.getHeldItem(EnumHand.OFF_HAND), upgrade);
        return total;
    }

    private int levelOf(ItemStack stack, net.minecraft.item.Item upgrade) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemWand)) {
            return 0;
        }
        return WandHelper.getUpgradeLevel(stack, upgrade);
    }
}
```

> Sumowanie poziomów z obu rąk jest świadome: gracz z różdżką w każdej ręce dostaje regen z obu. Jeśli po testach okaże się to za mocne, zmień `getUpgradeLevel` na `Math.max` zamiast sumy — to jedna linijka i nie rusza niczego innego.

- [ ] **Step 2: Zarejestruj razem z mostem EBW**

W bloku rejestracji z Task 5, obok `EbwSpellCostHandler`:

```java
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.manacore.compat.ebw.WandUpgradeBridge());
```

- [ ] **Step 3: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/compat/ebw/WandUpgradeBridge.java manacore/src/main/java/com/spege/manacore/ManaCoreMod.java
git commit -m "feat(manacore): condenser i siphon zasilaja pule gracza"
```

---

## Task 7: Przejęcie puli Trinkets and Baubles

**Files:**
- Modify: `libs/` — wymiana jara TaB na wersję z packa
- Modify: `manacore/build.gradle`
- Create: `manacore/src/main/java/com/spege/manacore/mixins/tab/MixinMagicStats.java`
- Create: `manacore/src/main/java/com/spege/manacore/compat/tab/TabManaAccess.java`

- [ ] **Step 1: Zamknij drift wersji**

```bash
cp "C:/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/Trinkets and Baubles-Forge-1.12.2-0.33.3.jar" libs/
rm "libs/Trinkets and Baubles-Forge-1.12.2-0.33.1.jar"
```

Zaktualizuj nazwę pliku w `manacore/build.gradle` na `0.33.3`.

```bash
javap -p -cp "libs/Trinkets and Baubles-Forge-1.12.2-0.33.3.jar" xzeroair.trinkets.capabilities.magic.MagicStats | grep -E "getMana|setMana|addMana|spendMana|getMaxMana|refillMana|needMana"
```

Oczekiwane: te same sygnatury co fakt F8. Jeśli któraś się różni — **zatrzymaj się i popraw mixin**, zanim pójdziesz dalej.

- [ ] **Step 2: Pomocnik odczytu gracza z `MagicStats`**

`.../compat/tab/TabManaAccess.java`:

```java
package com.spege.manacore.compat.tab;

import javax.annotation.Nullable;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.CostMath;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Cala logika przekierowania puli TaB. Mixin ma byc cienki i zawierac wylacznie @Overwrite/@Inject,
 * a nie mysleć - dzieki temu aktualizacja TaB wymaga poprawki w jednym, malym pliku.
 */
public final class TabManaAccess {

    private TabManaAccess() {
    }

    public static boolean handles(@Nullable EntityLivingBase owner) {
        return ManaCoreConfig.tab.enabled && owner instanceof EntityPlayer;
    }

    public static float getMana(EntityLivingBase owner) {
        return CostMath.toForeignUnits(ManaAPI.getMana((EntityPlayer) owner), ManaCoreConfig.tab.unitScale);
    }

    public static float getMaxMana(EntityLivingBase owner) {
        return CostMath.toForeignUnits(ManaAPI.getMaxMana((EntityPlayer) owner), ManaCoreConfig.tab.unitScale);
    }

    public static void setMana(EntityLivingBase owner, float value) {
        ManaAPI.setMana((EntityPlayer) owner, CostMath.fromForeignUnits(value, ManaCoreConfig.tab.unitScale));
    }

    public static void addMana(EntityLivingBase owner, float value) {
        ManaAPI.add((EntityPlayer) owner, CostMath.fromForeignUnits(value, ManaCoreConfig.tab.unitScale));
    }

    public static boolean spendMana(EntityLivingBase owner, float value) {
        return ManaAPI.spend((EntityPlayer) owner, CostMath.fromForeignUnits(value, ManaCoreConfig.tab.unitScale));
    }

    public static void refillMana(EntityLivingBase owner) {
        EntityPlayer player = (EntityPlayer) owner;
        ManaAPI.setMana(player, ManaAPI.getMaxMana(player));
    }

    public static boolean needMana(EntityLivingBase owner) {
        EntityPlayer player = (EntityPlayer) owner;
        return ManaAPI.getMana(player) < ManaAPI.getMaxMana(player);
    }
}
```

- [ ] **Step 3: Mixin**

`manacore/src/main/java/com/spege/manacore/mixins/tab/MixinMagicStats.java`:

```java
package com.spege.manacore.mixins.tab;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.manacore.compat.tab.TabManaAccess;

import net.minecraft.entity.EntityLivingBase;
import xzeroair.trinkets.capabilities.magic.MagicStats;

/**
 * Przekierowuje pule many TaB na nasza. Uzywamy @Inject z cancellable zamiast @Overwrite,
 * zeby przy wylaczonym configu oryginalna implementacja TaB dzialala nietknieta.
 */
@Mixin(value = MagicStats.class, remap = false)
public abstract class MixinMagicStats {

    /** Wlasciciel statystyk; getter dziedziczony z CapabilityEntityBase. */
    private EntityLivingBase manacore$owner() {
        return ((xzeroair.trinkets.capabilities.CapabilityEntityBase<?, ?>) (Object) this).getEntity();
    }

    @Inject(method = "getMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$getMana(CallbackInfoReturnable<Float> cir) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            cir.setReturnValue(Float.valueOf(TabManaAccess.getMana(owner)));
        }
    }

    @Inject(method = "getMaxMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$getMaxMana(CallbackInfoReturnable<Float> cir) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            cir.setReturnValue(Float.valueOf(TabManaAccess.getMaxMana(owner)));
        }
    }

    @Inject(method = "setMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$setMana(float value, CallbackInfo ci) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            TabManaAccess.setMana(owner, value);
            ci.cancel();
        }
    }

    @Inject(method = "addMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$addMana(float value, CallbackInfo ci) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            TabManaAccess.addMana(owner, value);
            ci.cancel();
        }
    }

    @Inject(method = "spendMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$spendMana(float value, CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            cir.setReturnValue(Boolean.valueOf(TabManaAccess.spendMana(owner, value)));
        }
    }

    @Inject(method = "refillMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$refillMana(CallbackInfo ci) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            TabManaAccess.refillMana(owner);
            ci.cancel();
        }
    }

    @Inject(method = "needMana", at = @At("HEAD"), cancellable = true, remap = false)
    private void manacore$needMana(CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase owner = manacore$owner();
        if (TabManaAccess.handles(owner)) {
            cir.setReturnValue(Boolean.valueOf(TabManaAccess.needMana(owner)));
        }
    }
}
```

> ✅ **Zweryfikowane na TaB 0.33.3** (`javap -p ... CapabilityEntityBase`): getter nazywa się **`getEntity()`**, nie `getObject()` — pierwotny tekst tego planu był tu błędny i został poprawiony. Sygnatura to `public E getEntity()` przy `E extends EntityLivingBase`, więc przez wildcard `CapabilityEntityBase<?, ?>` zwraca `EntityLivingBase`.
>
> Potwierdzone też, że wszystkie siedem metod z faktu F8 (`getMana`, `setMana`, `addMana`, `spendMana`, `getMaxMana`, `refillMana`, `needMana`) istnieje w 0.33.3 z niezmienionymi sygnaturami. Po kolejnej aktualizacji TaB powtórz oba sprawdzenia — to jedyne dwa miejsca, w których ten mixin zależy od wewnętrznej struktury cudzego moda.

- [ ] **Step 4: Wyłącz pasek TaB w configu instancji**

W `config/` instancji DEv 1.2 ustaw w configu Trinkets and Baubles `shown = false` dla paska many (`ConfigManaBarHud`). To config, nie kod — nie potrzeba mixina.

- [ ] **Step 5: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add manacore/build.gradle manacore/src/main/java/com/spege/manacore/mixins/tab manacore/src/main/java/com/spege/manacore/compat/tab libs
git commit -m "feat(manacore): przejecie puli many Trinkets and Baubles"
```

---

## Task 8: Itemy many z TaB

**Files:**
- Create: `manacore/src/main/java/com/spege/manacore/compat/tab/TabManaItemHandler.java`
- Modify: `manacore/src/main/java/com/spege/manacore/ManaCoreMod.java`

- [ ] **Step 1: Handler itemów**

`.../compat/tab/TabManaItemHandler.java`:

```java
package com.spege.manacore.compat.tab;

import com.spege.manacore.api.ManaAPI;
import com.spege.manacore.cap.IManaPool;
import com.spege.manacore.cap.ManaCapabilities;
import com.spege.manacore.config.ManaCoreConfig;
import com.spege.manacore.core.ManaMath;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Mana Crystal -> trwale +max (przez progresje, wiec objete tym samym sufitem).
 * Mana Reagent / Mana Candy -> doladowanie biezacej many.
 */
public class TabManaItemHandler {

    private static final ResourceLocation MANA_CRYSTAL = new ResourceLocation("xat", "mana_crystal");
    private static final ResourceLocation MANA_REAGENT = new ResourceLocation("xat", "mana_reagent");
    private static final ResourceLocation MANA_CANDY = new ResourceLocation("xat", "mana_candy");
    private static final ResourceLocation MANA_CANDY_2 = new ResourceLocation("xat", "mana_candy2");
    private static final ResourceLocation MANA_CANDY_3 = new ResourceLocation("xat", "mana_candy3");
    private static final ResourceLocation MANA_CANDY_4 = new ResourceLocation("xat", "mana_candy4");

    @SubscribeEvent
    public void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (!ManaCoreConfig.tab.enabled) {
            return;
        }
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote) {
            return;
        }

        ItemStack stack = event.getItem();
        if (stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        ResourceLocation name = item.getRegistryName();
        if (name == null) {
            return;
        }

        if (MANA_CRYSTAL.equals(name)) {
            grantPermanentMax(player);
        } else if (MANA_REAGENT.equals(name) || MANA_CANDY.equals(name)
                || MANA_CANDY_2.equals(name) || MANA_CANDY_3.equals(name)
                || MANA_CANDY_4.equals(name)) {
            ManaAPI.add(player, ManaCoreConfig.tab.restoreItemAmount);
        }
    }

    /** Krysztal daje trwaly przyrost maksimum, z wlasnym sufitem niezaleznym od progresji z kastowania. */
    private void grantPermanentMax(EntityPlayer player) {
        IManaPool pool = ManaCapabilities.get(player);
        if (pool == null) {
            return;
        }
        double current = pool.getProgressionBonus();
        double next = ManaMath.afterProgressionGain(
                current, ManaCoreConfig.tab.manaCrystalCap, ManaCoreConfig.tab.manaCrystalMaxBonus);
        if (next > current) {
            ManaAPI.addProgression(player, next - current);
        }
    }
}
```

Dodaj do `TabCategory` brakujące pole:

```java
    @Config.Comment("Ile biezacej many przywraca Mana Reagent albo Mana Candy.")
    @Config.RangeDouble(min = 0.0D, max = 10000.0D)
    public double restoreItemAmount = 25.0D;
```

> Sześć identyfikatorów wywiedziono ze ścieżek modeli w jarze TaB (`assets/xat/models/item/mana_crystal.json`, `mana_reagent.json`, `mana_candy.json` … `mana_candy4.json`). **Potwierdź je w grze przez F3+H** — tooltip pokazuje pełne id — i popraw każde, które się różni. Jeśli któryś item okaże się nieistniejący, po prostu usuń jego stałą; nietrafiona `ResourceLocation` nigdy nie dopasuje się do niczego i nie rzuca wyjątku, więc błąd byłby cichy.

- [ ] **Step 2: Zarejestruj razem z mostem TaB**

W `ManaCoreMod.init`:

```java
        if (net.minecraftforge.fml.common.Loader.isModLoaded("xat")
                && com.spege.manacore.config.ManaCoreConfig.tab.enabled) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    new com.spege.manacore.compat.tab.TabManaItemHandler());
            LOGGER.info("[ManaCore] TaB bridge registered");
        }
```

- [ ] **Step 3: Zbuduj**

```bash
./gradlew :manacore:build
```

Oczekiwane: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add manacore/src/main/java/com/spege/manacore/compat/tab manacore/src/main/java/com/spege/manacore/ManaCoreMod.java
git commit -m "feat(manacore): Mana Crystal zwieksza maksymalna pule gracza"
```

---

## Task 9: Weryfikacja

> **Reguła tego repo:** „gra się uruchomiła" **nie jest dowodem**, że mixin się zaaplikował — pod `required:false` no-opuje po cichu, a pod `required:true` może po prostu nie mieć jeszcze załadowanego celu.

- [ ] **Step 1: Zbuduj wszystko**

```bash
./gradlew build
```

Oczekiwane: `BUILD SUCCESSFUL`, 21 testów zielonych, jary wszystkich subprojektów obecne.

- [ ] **Step 2: Wgraj do instancji**

Skopiuj `manacore/build/libs/manacore-0.1.0.jar` do `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\`, **usuwając poprzedni jar `manacore`** (dwie wersje jednego modid = crash na duplikacie).

- [ ] **Step 3: Sprawdź `cleanmix.log`, nie `debug.log`**

```bash
grep -E "manacore" "C:/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log"
```

Oczekiwane **wszystkie cztery**:
- `APPLY mixins.manacore.ebw.json:MixinItemWand from mod manacore -> ...ItemWand`
- `APPLY mixins.manacore.tab.json:MixinMagicStats from mod manacore -> ...MagicStats`
- brak `InvalidInjectionException`
- brak `Scanned 0`

```bash
grep -E "InvalidInjectionException|Scanned 0|VerifyError" "C:/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log"
```

Oczekiwane: pusty wynik.

> Nie greppuj `logs/debug.log` — linii mixinów tam nie ma, a plik potrafi mieć 40+ MB, z czego 99% to SRParasites na DEBUG.

- [ ] **Step 4: Kryteria akceptacji ze speca, po kolei**

1. **Nieudany czar nie kosztuje.** Pełne HP, rzuć Heal. Pasek many **nie drgnie**. To jest ta różnica wobec player_mana — jeśli mana spada, `Post` odpala się w złym miejscu albo handler siedzi na `Pre`.
2. **Udany czar kosztuje raz.** Rzuć czar pociskowy, sprawdź, że ubytek odpowiada koszt × mnożniki, a nie jego wielokrotności.
3. **Czar ciągły pobiera per-tick i przerywa się.** `/mana set 20`, kanałuj coś ciągłego — ma się urwać, gdy pula dojdzie do zera.
4. **Pusta pula blokuje, pusta różdżka nie.** `/mana set 0` → czar odmawia z komunikatem. Potem rozładuj różdżkę do zera w Arcane Workbench, `/mana set 100` → czar **działa**.
5. **Mana różdżki w NBT nie drgnie.** F3+H, obserwuj tooltip różdżki przez kilkanaście czarów.
6. **Upgrade'y.** `storage` → mierzalny zwrot po caście; `condenser` → szybszy regen; `siphon` → skok many po zabójstwie.
7. **Jeden pasek, jedna liczba.** Pasek TaB nie rysuje się, a jego wartość (jeśli gdzieś widoczna) zgadza się z naszą.
8. **Artefakty nietknięte.** Weź artefakt EBW lub ASC z własną maną — ma działać dokładnie jak przed instalacją.
9. **Dedykowany serwer.** `./gradlew runServer` — brak `invalid side SERVER`, `NoClassDefFoundError`, `NoSuchFieldError`.

- [ ] **Step 5: Commit poprawek**

```bash
git add -A manacore
git commit -m "fix(manacore): poprawki po weryfikacji mostow"
```

---

## Kryteria ukończenia Planu 2

1. `./gradlew build` przechodzi; 21 testów zielonych.
2. `cleanmix.log` pokazuje oba `APPLY`, zero `InvalidInjectionException`, zero `Scanned 0`.
3. Wszystkie dziewięć punktów z Task 9 Step 4 potwierdzone ręcznie.
4. `latest.log` zawiera `[ManaCore] EBW bridge registered` i `[ManaCore] TaB bridge registered`.
5. Serwer dedykowany wstaje i pozwala czarować.
6. Przy `ebw.enabled = false` i `tab.enabled = false` gra zachowuje się jak bez moda (dowód, że wyjścia awaryjne działają).

## Co świadomie zostaje niezrobione

Zgodnie z §8 i §10 speca — **to nie są przeoczenia**:

- Bonusy rasowe TaB (`magicAffinity`, `racialAffinity`) przepadają. Faza 6.
- Efekty mana-owe innych modów (ASC `PotionManaRegeneration`, bonus setowy SpellBundle, mana leech Necromancer's Delight, pętla ArcaneApprentices) nadal celują w manę itemu. Faza 5.
- `spellarchives` pokazuje koszt z niewłaściwego źródła. Faza 4 lub 5.
- Normalizacja kosztów między EBW a TaB. Faza 4.
- Źródła progresji: drzewko Reskillable, achievementy, baubles, enchant, QualityTools. Faza 6.
