# TombTweaks na 1.20.1 — port rdzenia

**Data:** 2026-08-14
**Status:** zatwierdzony design, przed planem implementacji
**Zakres:** pierwsza działająca wersja `tombtweaks` na Minecraft 1.20.1 / Forge 47 / Corail Tombstone 9.1.4

---

## 1. Cel i granice

`tombtweaks` istnieje dziś wyłącznie na 1.12.2 jako jeden z siedmiu modów w repo `modDev`. Ten spec opisuje **pierwszy etap** przeniesienia go na nowsze wersje Minecrafta: publiczny, general-purpose mod na 1.20.1, złożony z trzech featurów, które nie mają w 9.1.4 natywnego odpowiednika.

Kolejne etapy, **poza zakresem tego specu**:

- **Etap 2** — integracje: Enigmatic Legacy (złagodzenie Ring of the Seven Curses, perk Relief for the Damned), Electroblob's Wizardry Redux (soulbind różdżki), Curios.
- **Etap 3** — tuning natywnych perków.
- **Etap 4** — port na 1.16.5 (Tombstone 7.0.0).

Featury świadomie pominięte w v1, z uzasadnieniem, dlaczego nie są pilne, są wypisane w §8.

### To nie jest port kodu

Z 1.12.2 na 1.20.1 przenosi się **semantyka, schemat configu i decyzje projektowe**, a nie linijki. Warstwa dotykająca Minecrafta jest pisana od nowa. Tam, gdzie 1.20.1 pozwala zrobić coś prościej niż oryginał, spec wybiera prostsze rozwiązanie i mówi to wprost — trzy takie miejsca są opisane w §4, §5 i §6.

---

## 2. Ustalenia o Tombstone 9.1.4 (zweryfikowane w bajtkodzie)

Wszystko poniżej potwierdzone przez `javap` na `tombstone-1.20.1-9.1.4.jar` (JDK 21, bo klasy są w wersji formatu 65).

| Ustalenie | Znaczenie |
|---|---|
| `ovh.corail.tombstone.api.event.RestoreInventoryEvent` istnieje, ma ten sam kształt co na 1.12.2 | exact-slot restore nie potrzebuje ani jednego mixina |
| `BlockEntityPlayerGrave.giveInventory` rozdziela przez `ItemHandlerHelper.giveItemToPlayer` | **sloty nadal się gubią** — feature jest wciąż potrzebny |
| `EventFactory.onRestoreInventory` odpala się po cięciu `chanceLossOnDeath`/`percentLossOnDeath`, a przed auto-equipem i priorytetem narzędzi | ten sam użyteczny moment co na 1.12.2 |
| Po evencie leci pass `PlayerPreference.isReverseInventorySorting()`, przepisujący `Inventory.items` w odwrotnej kolejności | **nowe ryzyko**, patrz §4 |
| `BlockEntityPlayerGrave.serverTick(...)` — publiczna statyczna | hook decay bez skanowania TE |
| `BlockWritableGrave.countTicks` — `public long`, **zapisywany** w `writeShared` i wczytywany w `load`; `serverTick` woła `commonTick` jako pierwszą instrukcję | harmonogram decay może być bezstanowy i przeżywa restart |
| `getOwnerDeathTime()` zwraca `deathDate`, ustawiane z `TimeHelper.systemTime()` = `System.currentTimeMillis()` | wiązanie snapshotu z grobem idzie po czasie zegarowym w ms, ta sama skala co `capturedAt` |
| `ItemBook implements ISoulConsumer` i deklaruje `canEnchant(...)`, ale **nie** `setEnchant(...)` — tę mają dopiero konkretne księgi | cooldown potrzebuje dwóch punktów zaczepienia, nie jednego, patrz §6 |
| `ISoulConsumer.canEnchant` i `.setEnchant` są wołane z **dokładnie jednego** miejsca: `BlockDecorativeGrave.use`, 18 instrukcji od siebie | jeden punkt na ustawienie cooldownu, obejmujący wszystkie księgi |
| `ConsumeResult` to rekord z `result()` → `Result.SUCCESS` / `Result.FAIL` | sukces jest zwracany wprost, bez zgadywania po zmianie stacku |
| `CooldownHandler.CooldownType` to enum z 4 wartościami (`NEXT_PRAY`, `RESET_PERKS`, `TELEPORT_DEATH`, `REQUEST_TELEPORT`) | natywny system cooldownów nierozszerzalny bez mixina, nie używamy go |
| `ConfigTombstone.general.unhandledBeneficialEffects` / `unhandledHarmfulEffects` | natywne czarne listy losowych efektów — nasze whitelisty są częściowo zbędne, stąd wypadły z v1 |
| `ConfigTombstone.compatibility.curioAutoEquip` | Curios ma natywne wsparcie; nasz feature to etap 2, nie luka |
| `Perk` przeniesiony z API do `ovh.corail.tombstone.perk.Perk`, doszedł `PerkBranch` (4 gałęzie) + `branchTier` + `getParent()`, doszły perki Archeologist / Channeler / Glyphographer / Guardian | tuning perków to przepisanie od zera, nie port — stąd osobny etap |

### Założenia jeszcze niepotwierdzone

Zostało jedno, niesprawdzalne bajtkodem: **`PlayerPreference.isReverseInventorySorting()`** to prywatne pole per-gracz, przestawiane z GUI klienta — czyli część graczy może je mieć włączone niezależnie od domyślnej wartości. Do sprawdzenia w grze (§9 punkt 2): jaka jest wartość domyślna i co dokładnie robi z odzyskanym ekwipunkiem po włączeniu.

---

## 3. Kształt projektu

### Workspace

**Osobne repo git: `E:\Isuth\tombtweaks-1201\`**, poza `modDev`.

To nie jest kwestia gustu, tylko twardej przeszkody: `modDev` ma wrapper **Gradle 4.9 + ForgeGradle 3**, a ForgeGradle 6 (wymagany pod 1.20.1) potrzebuje **Gradle 8.1+**. Jedno repo = jeden wrapper. Ta sama przeszkoda dotyczy przyszłego portu na 1.16.5 (FG5 + Gradle 7), więc **każda wersja Minecrafta dostaje własny workspace**.

Toolchain: Gradle 8.x, ForgeGradle 6, Java 17, mappings `official` + Parchment.

Jary z `E:\Isuth\modDev\TestPort\` przenoszą się do `tombtweaks-1201/libs/`; katalog `TestPort/` po tym znika. Do v1 potrzebny jest tylko `tombstone-1.20.1-9.1.4.jar`; `EnigmaticLegacy-2.30.1.jar` i `electroblobs-wizardry-redux-0.8.6-forge.jar` zostają odłożone na etap 2, a `tombstone-1.16.5-7.0.0.jar` na etap 4.

### Tożsamość

- modid `tombtweaks`, group `com.spege.tombtweaks`, prefiks logu `[TombTweaks]`
- Ten sam modid co na 1.12.2 — różne wersje Minecrafta nigdy się nie spotkają, a nazwa jest już zajęta przez ten sam projekt.
- `pack.mcmeta` **nie jest** potrzebny (to wymóg 1.12.2); na 1.20.1 resource domain bierze się z `META-INF/mods.toml`. Odpowiednikiem tej pułapki jest poprawny `pack.mcmeta` datapacka tylko wtedy, gdy dorzucamy datapack — w v1 nie dorzucamy.

### Trzy warstwy

| Warstwa | Zawartość | Testy |
|---|---|---|
| `core` | **Zero typów Minecrafta i zero typów Tombstone'a.** Schemat configu jako zwykłe obiekty, parser i ewaluator list ochrony, arytmetyka harmonogramu decay, arytmetyka cooldownu, model snapshotu slotów na `int`/`String` | JUnit 5, `./gradlew test` |
| `platform` | Cienkie adaptery: `ItemStack` → klucz matchera, `CompoundTag` ↔ snapshot, `ForgeConfigSpec` → obiekty `core` | pośrednio |
| `feature` | Trzy handlery + dwa mixiny, tak głupie, jak się da — decyzje podejmuje `core` | ręcznie w grze |

Podział jest praktyczny, nie estetyczny. `core` przy porcie na 1.16.5 idzie kopiuj-wklej razem z testami, a to w nim siedzi cała logika, którą łatwo po cichu zepsuć. Wzorzec sprawdzony w tym samym repo przez `commandsuggest` (72 testy na gołej JVM).

### Mixiny

Standardowy mechanizm Forge 1.20.1: `mixins.tombtweaks.json` wskazany z `META-INF/mods.toml`, bez MixinBooter, bez `ILateMixinLoader`.

**Refmap generuje się normalnie i ma się generować.** To odwrotność zasady z `modDev/CLAUDE.md`, gdzie `-proc:none` wyłącza mixinowy annotation processor i dopasowanie idzie po jawnych nazwach SRG. Ta zasada **nie przenosi się** na nowy workspace i nie należy jej tu powielać.

Klasy Tombstone'a nie są obfuskowane, więc cele mixinów piszemy zwykłymi nazwami; `remap = false` tylko dla członków należących do Tombstone'a.

### Zależności

- `fg.deobf(files('libs/tombstone-1.20.1-9.1.4.jar'))` — deobf jest konieczny, bo w bajtkodzie Tombstone'a referencje do Minecrafta są SRG-owe (`m_58904_` itd.).
- Nic poza tym w v1.

---

## 4. Feature: exact-slot grave restore

### Problem

Tombstone oddaje zawartość grobu przez `ItemHandlerHelper.giveItemToPlayer`, czyli do pierwszego wolnego slotu. Po śmierci ekwipunek wraca kompletny, ale przemieszany.

### Zapis — `LivingDeathEvent`, priorytet `HIGHEST`

To ostatni moment, w którym rozkład ekwipunku jest jeszcze prawdą: `Player.die()` opróżnia ekwipunek zaraz potem, a wszystko, co dociera do `LivingDropsEvent`, jest już płaską kupką bez indeksów. Priorytet `HIGHEST`, żeby handler anulujący śmierć nie wszedł przed nami — snapshot dla śmierci, która się nie wydarzyła, kosztuje jeden nieużyty wpis, który magazyn i tak przycina.

Zapisujemy **plan miejsc, nie kopię przedmiotów**: dla każdego niepustego slotu tylko tyle, żeby przedmiot rozpoznać przy powrocie. Pełny zserializowany ekwipunek w danych gracza to dziesiątki kilobajtów przepisywane przy każdym autosave i — co gorsza — druga instancja prawdy o tym, co gracz posiadał. **Grób jest jedyną instancją prawdy; snapshot to rozsadzenie gości.**

Warunki wyjścia: strona serwera, `gamerule keepInventory` wyłączone, `[restore] enabled`.

Kodowanie slotów (przeniesione z 1.12.2, bo się sprawdziło):

```
0-35    main inventory (własne indeksy gracza)
100+    pancerz
150     offhand
200+    zarezerwowane pod Curios (etap 2)
```

### Odczyt — `RestoreInventoryEvent`

Event daje żywy `IItemHandler` grobu, więc przedmiot wyjęty w handlerze po prostu nie istnieje dla standardowej ścieżki rozdziału, a wszystko, czego nie ruszymy, rozdziela się dokładnie jak dotąd. **Zero mixinów.**

### Wiązanie snapshotu z grobem

Snapshot powstaje przy śmierci, czyli **zanim grób istnieje** — rodzi się więc jako „oczekujący" i musi zostać skojarzony z konkretnym grobem dopiero przy odzyskiwaniu.

Wiązanie idzie po czasie zegarowym: `capturedAt` snapshotu (moment `LivingDeathEvent`) przeciw `getOwnerDeathTime()` grobu, które jest datą śmierci w milisekundach — ta sama skala. Przy odzyskaniu wybieramy snapshot o najbliższym `capturedAt`, z tolerancją kilku sekund, i **zużywamy go** (usuwamy z listy). Dzięki temu gracz z kilkoma nieodwiedzonymi grobami dostaje z każdego jego własne rozsadzenie, a nie rozsadzenie z ostatniej śmierci.

Brak dopasowania w tolerancji = brak snapshotu = ścieżka standardowa, zgodnie z niezmiennikiem poniżej.

Lista oczekujących snapshotów jest przycinana z dwóch stron: limit sztuk na gracza i wiek (snapshot starszy niż grób może być, czyli po prostu bardzo stary, jest wyrzucany). Bez tego jedna śmierć bez powrotu po grób zostawiałaby wpis na zawsze.

Skala jest potwierdzona: `deathDate` grobu bierze się z `TimeHelper.systemTime()`, czyli wprost z `System.currentTimeMillis()`.

### Klucz dopasowania — tu port się różni

Na 1.12.2 kluczem jest `(registry name, metadata, hash NBT)`. Na 1.20.1 **metadanych nie ma, a damage siedzi w NBT**, więc klucz to `(registry name, hash NBT)` — i przez to jest bardziej kruchy: stack trafia do grobu przez serializację do NBT i z powrotem, a round-trip potrafi tag znormalizować (dopisać `Damage:0`, wyrzucić pusty tag), co zmienia hash.

Dlatego port dostaje **dopasowanie trójstopniowe**, którego oryginał nie ma:

1. **dokładne** — nazwa + hash NBT,
2. **awaryjne** — sama nazwa, pierwszy jeszcze niedopasowany wpis snapshotu,
3. **rezygnacja** — przedmiot zostaje w grobie i idzie ścieżką standardową.

Stack size jest zapisywany, ale **nigdy nie jest kryterium dopasowania**: `percentLossOnDeath` zmniejsza stacki wewnątrz grobu, zanim ktokolwiek je zobaczy, więc liczba zapisana przy śmierci nie jest liczbą, która dożywa odzyskania.

### Niezmiennik

**Każde wstawienie jest warunkowe na tym, że docelowy slot jest pusty.** Jeśli gracz w międzyczasie coś zlootował, wykraftował albo zginął po raz drugi, miejsce jest zajęte i przedmiot zostaje w grobie. Z tego wynika gwarancja, którą trzeba trzymać przez cały rozwój featura:

> Najgorszy przypadek to dzisiejsze zachowanie Tombstone'a — nigdy zgubiony ani zduplikowany przedmiot.

### Znane ryzyko: `isReverseInventorySorting`

9.1.4 uruchamia **po** naszym evencie pass `PlayerPreference.isReverseInventorySorting()`, który przepisuje cały `Inventory.items` w odwróconej kolejności. To preferencja per-gracz. Jeśli włączona — odwraca nasze rozsadzenie.

Postępowanie: zweryfikować w grze (§9 punkt 2), udokumentować wprost w komentarzu configu. Obejście przez trzeci mixin **nie wchodzi do v1** — najpierw dowód, że to realny problem, a nie hipotetyczny.

---

## 5. Feature: grave item decay

### Cel

Grób, po który nikt nie wraca, powoli gubi zawartość, zamiast stać wiecznie jako magazyn. Cztery listy ochrony pozwalają wyjąć spod tego wybrane przedmioty.

### Hook — `@Inject` w `BlockEntityPlayerGrave.serverTick(...)`

Tu port wychodzi wyraźnie lepiej niż oryginał, bo znika cały aparat pomocniczy:

| 1.12.2 | 1.20.1 |
|---|---|
| Skan `world.loadedTileEntityList` co 40 ticków + porównanie nazwy klasy stringiem | ticker samego grobu, jeden na grób |
| Refleksja na `getInventory` / `countTicks` / `getOwnerName` | wszystkie trzy publiczne, kompilacja wprost |
| `WeakHashMap<TileEntity, Long>` na czas ostatniego rozkładu — ginie przy restarcie i wyładowaniu chunka | **stan zerowy** |

Harmonogram jest czystą funkcją, bo `countTicks` jest zapisywane w NBT grobu:

```
rozkład, gdy  countTicks >= startTicks
              && (countTicks - startTicks) % intervalTicks == 0
```

`serverTick` odpala się raz na tick na grób, a `countTicks` rośnie o jeden na tick, więc warunek trafia dokładnie raz na interwał. Bez mapy, bez utraty stanu przy restarcie.

Semantyka `startTicks` bez zmian względem 1.12.2: to ticki, przez które grób **był załadowany**, nie czas zegarowy — bo to ten sam licznik co tam.

### Wybór ofiary — bez zmian

Dwie listy kandydatów, nie jedna:

1. Stacki niechronione zjadane są pierwsze.
2. Chronione są nieosiągalne, dopóki zostało cokolwiek innego.
3. Gdy zostały tylko chronione, decyduje `protectedNeverDecay`: `true` = ochrona jest zwolnieniem (grób staje w miejscu), `false` = ochrona jest tylko kolejnością.

Wybór wewnątrz listy jest losowy. Wyjęty stack ląduje jako `ItemEntity` nad grobem.

Cztery rodzaje ochrony — pełne nazwy itemów, prefiksy nazw, enchanty, matchery NBT `klucz=wartość` — idą **w całości do `core`** i dostają testy. To jest dokładnie ta logika, którą łatwo po cichu zepsuć: pusta lista musi znaczyć „nic nie chronione", nie „wszystko chronione".

### Naprawiany błąd oryginału

Na 1.12.2 historia rozkładu (`GraveDecayHistory`, obsługująca `/tombtweaks restore`) zapisuje się przez `getPlayerByUsername`, który zwraca `null` dla gracza offline — czyli **grób gracza offline rozkłada się bez śladu w historii**, dokładnie w scenariuszu, dla którego feature powstał.

W porcie historia idzie po `ownerId` (UUID). Gdy właściciel jest online — do jego danych; gdy offline — do `WorldSavedData` moda, skąd zostaje przeniesiona przy najbliższym logowaniu.

---

## 6. Feature: cooldowny ksiąg

### Hook — dwa mixiny, każdy o jednej odpowiedzialności

`ItemBook` implementuje publiczne API `ISoulConsumer`, ale deklaruje z niego **tylko `canEnchant`**; `setEnchant` mają dopiero konkretne księgi. Stąd dwa punkty, nie jeden:

1. **`MixinItemBook`** — `@Inject` na głowie `canEnchant(Level, BlockPos, Player, ItemStack)`, `cancellable = true`, `remap = false` (cel to metoda Tombstone'a na klasie Tombstone'a, nic do remapowania). Zwraca `false` plus komunikat z pozostałym czasem, gdy cooldown trwa. **Blokada.**
2. **`MixinBlockDecorativeGrave`** — `@Redirect` na wywołaniu `ISoulConsumer.setEnchant` wewnątrz `use(...)`. Przepuszcza wywołanie, czyta zwrócony `ConsumeResult` i przy `Result.SUCCESS` ustawia cooldown. **Start cooldownu.**

Rozdzielenie jest celowe: blokada siedzi na typie księgi, więc działa niezależnie od tego, skąd ktoś ją zawoła; start cooldownu siedzi na jedynym istniejącym miejscu wywołania, więc obejmuje wszystkie księgi jednym mixinem.

Uwaga na remapowanie w mixinie nr 2: selektor `method = "use"` celuje w **metodę Minecrafta** (`BlockBehaviour.use`, w runtime `m_6227_`), więc musi być remapowany — i to on jest powodem, dla którego refmapy w tym repo są włączone (§3). Wewnętrzny `@At(target = "L…/ISoulConsumer;setEnchant…")` celuje w członka Tombstone'a i dostaje własne `remap = false`.

Zgodnie z zasadą, którą `modDev` ma zapisaną osobno: **receiver `@Redirect` musi być dokładnie właścicielem wywołania** — tu wywołanie to `invokeinterface ISoulConsumer.setEnchant`, więc pierwszym parametrem handlera jest `ISoulConsumer`, nie konkretna księga.

To zastępuje całą maszynerię z 1.12.2 (`PlayerInteractEvent` + odroczone sprawdzenie „czy stack się zmniejszył po ticku", żeby zgadnąć, czy użycie się powiodło). Tutaj sukces jest zwracany wprost jako wartość rekordu.

### Konsekwencja: config staje się mapą

Skoro hook siedzi na klasie bazowej, obejmuje **wszystkie** księgi Tombstone'a. Config przestaje więc być dwoma polami i staje się listą wpisów `registry name;minuty`, domyślnie wypełnioną dwiema pozycjami z 1.12.2. Zero dodatkowego kodu, większy zasięg.

### Przechowywanie

`PlayerPersisted` w danych gracza, klucz per księga: `tombtweaks:cooldown/<registry name>`.

**Klucze startują od zera.** Prefiks `InsaneTweaks_` z oryginału istnieje tam wyłącznie po to, żeby nie zresetować cooldownów istniejącym graczom przy podziale modów w R4 — świat 1.12.2 nigdy nie spotka świata 1.20.1, więc tutaj nie ma czego migrować i nie ma powodu nieść tej nazwy dalej.

### Dlaczego nie natywny `CooldownHandler`

Tombstone 9.1.4 ma własny, persystowany i synchronizowany do klienta system cooldownów. Nie nadaje się: `CooldownType` to enum z czterema wartościami, nierozszerzalny bez mixina, a jego jedyna przewaga — synchronizacja do klienta — jest nam niepotrzebna, bo komunikat wysyłamy serwerowo.

---

## 7. Config

Plik `config/tombtweaks-common.toml`, budowany ręcznie przez `ForgeConfigSpec` (system `@Config` z adnotacjami nie istnieje na 1.20.1).

```toml
[restore]
  enabled          = true
  debugLogging     = false

[decay]
  enabled          = false
  startTicks       = 6000
  intervalTicks    = 1200
  maxHistory       = 10
  protectedItems         = []
  protectedItemPrefixes  = []
  protectedEnchantments  = []
  protectedNbtStrings    = []
  protectedNeverDecay     = true

[cooldown]
  enabled = true
  books   = ["tombstone:book_of_disenchantment;6",
             "tombstone:book_of_magic_impregnation;6"]
```

Dwie różnice względem 1.12.2:

- **Nie ma master switcha** `enableTombstoneTweaks`. Miał sens, gdy jeden mod trzymał kilkanaście modułów; przy trzech featurach z własnymi przełącznikami jest tylko dodatkowym stanem, w którym da się utknąć.
- **Domyślne listy ochrony są puste.** Te z 1.12.2 (`insanetweaks_properties=ashen_legacy`, `insanetweaks:sentientcodex`, prefiksy) dotyczą konkretnego packa; publiczny mod nie ma prawa ich zakładać.

Nie ma odpowiednika `@Config.RequiresMcRestart`. `restore` i `decay` czytają config na bieżąco. `cooldown` wymaga restartu tylko dlatego, że mixin już siedzi w klasie — jego komentarz mówi wprost **„czytane na żywo"**, zamiast obiecywać bramkowanie mixina, którego nie ma. To ta sama pułapka, którą `modDev/CLAUDE.md` opisuje dla swoich mixinów: flaga configu nie bramkuje mixina bez `IMixinConfigPlugin`, a tu żadnego nie będzie.

---

## 8. Poza zakresem v1

| Feature | Dlaczego nie teraz |
|---|---|
| Curios w slot restore | Slot curio to para `identifier` + `index`, nie płaski `int` — osobna decyzja projektowa. Tombstone ma własne `curioAutoEquip`, więc to nie jest luka, tylko ulepszenie. Etap 2. |
| Tuning natywnych perków | Przepisanie od zera pod `Perk`/`PerkBranch` i inny zestaw perków. Najdroższy element całości. Etap 3. |
| Whitelisty losowych efektów | 9.1.4 ma natywne czarne listy `unhandledBeneficialEffects`/`unhandledHarmfulEffects`. Nasza wersja daje osobne pule per źródło i pulę magic scroll — realna, ale mała przewaga. |
| Nerf dropu `grave_dust`, usunięcie recepty na Enchanted Grave Key | Drobne, każde wymaga własnej weryfikacji przeciw 9.1.4; nie blokują niczego. |
| Patch Curse of Possession | Najpierw trzeba ustalić, czy exploit w ogóle jeszcze istnieje. |
| First-kill reward, raider alignment | Generyczne i przenośne, ale to dodatki, nie rdzeń. |
| Knowledge tab (Reskillable) | Reskillable nie istnieje na 1.20.1. **Odpada na stałe.** |
| Fix brakujących sprite'ów cząstek | Specyfika 1.12.2. **Odpada na stałe.** |
| Custom perki (Assimilated Knowledge, Relief for the Damned) | Pierwszy zależy od SRParasites (brak na 1.20.1), drugi od Enigmatic Legacy. Etap 2. |
| Soulbind różdżki | Wymaga EBW Redux i odpowiednika `ancientspellcraft:soulbound_upgrade`. Etap 2. |
| Port na 1.16.5 | Własny workspace (FG5 + Gradle 7). `core` przenosi się kopiuj-wklej. Etap 4. |

---

## 9. Testy i weryfikacja

### Testy automatyczne

`./gradlew test`, JUnit 5, wyłącznie warstwa `core`:

| Obszar | Przypadki brzegowe |
|---|---|
| Listy ochrony | prefiks vs pełna nazwa; matcher NBT `klucz=wartość` przy braku tagu; enchant nieistniejącego moda; **pusta lista ≠ „wszystko chronione"** |
| Harmonogram decay | dokładnie jedno odpalenie na interwał; brak odpalenia przed `startTicks`; `intervalTicks = 0`; `countTicks` cofnięty |
| Wybór ofiary | tylko chronione + `protectedNeverDecay=true` → nic; `=false` → chronione; pusty grób → nic |
| Dopasowanie slotów | trafienie dokładne; spadek na dopasowanie po nazwie; dwa identyczne stacki → dwa różne wpisy; brak dopasowania → rezygnacja |
| Cooldown | `0` minut = wyłączony; przekręcenie zegara; błędny wpis `nazwa;minuty` |

Warstwy `platform` i `feature` testów nie mają — to samo ograniczenie co w `modDev`, nazwane wprost, a nie przemilczane.

### Weryfikacja w grze

Kolejność od najtańszej do najdroższej, każdy krok odcina konkretne ryzyko:

1. **Log startowy** — czy oba mixiny się nałożyły. „Gra wstała" nie jest dowodem: mixin, który nie trafił, potrafi po cichu nic nie zrobić.
2. **`[restore]`** — śmierć z charakterystycznym układem ekwipunku, powrót do grobu. Osobno: czy `isReverseInventorySorting` jest domyślnie wyłączone i co się dzieje po włączeniu.
3. **`[decay]`** — `startTicks` ustawione na kilka sekund, obserwacja jednego grobu. Potem to samo z wylogowanym właścicielem (test poprawki historii z §5).
4. **`[cooldown]`** — użycie księgi, drugie użycie w trakcie cooldownu, restart serwera w trakcie cooldownu.

---

## 10. Decyzje, do których nie należy wracać bez powodu

- **Osobny workspace per wersja Minecrafta.** Wymuszone konfliktem wrapperów Gradle, nie preferencją.
- **`core` bez ani jednego typu Minecrafta.** To jedyne, co przy porcie na 1.16.5 przeniesie się bez przepisywania.
- **Grób jest jedyną instancją prawdy o przedmiotach.** Snapshot przechowuje miejsca, nie przedmioty.
- **Najgorszy przypadek slot restore = zachowanie standardowe.** Nigdy zgubiony ani zduplikowany przedmiot.
- **Refmapy na 1.20.1 są włączone.** Zasada `-proc:none` z `modDev/CLAUDE.md` nie obowiązuje w tym repo.
