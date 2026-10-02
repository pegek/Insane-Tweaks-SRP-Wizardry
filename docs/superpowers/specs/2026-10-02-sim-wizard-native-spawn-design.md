# Sim wizard: naturalny spawn w strefach skażenia (design)

**Status:** szkic do akceptacji. Sekcja 8 to decyzje autora, a sekcja 7 to fakty z SRP, które
trzeba **przeczytać w bytecode przed planem**.
**Data:** 2026-10-02. **Mod:** `insanetweaks` (1.17.0, bo to nowa funkcja).
**Kontekst:** handoff `docs/superpowers/plans/2026-08-11-abomination-magic-system-handoff.md`,
punkt C. Ekonomia: `docs/superpowers/specs/2026-08-08-abomination-economy-and-balance-design.md`.

---

## 1. Problem

Sim wizard i sim battlemage mają być **głównym źródłem** pyłu abominacji i zaklęć abominacji.
Dziś powstają wyłącznie z konwersji: SRP musi zarazić `ebwizardry:wizard` / `evil_wizard` albo
klasowego maga ASC, a `SrpWizardryAssimilationHelper` podmienia ofiarę. Podaż jest więc iloczynem
dwóch rzeczy, na które nie mamy wpływu:

1. jak często EBW i ASC spawnują magów (wieże, bardzo rzadkie spawny naturalne),
2. jak często pasożyt akurat dopadnie maga i go zasymiluje.

Dopóki tak jest, ekonomia stoi na recepturze zapasowej, która **celowo** jest gorsza od dropu.

**Cel:** sim wizard pojawia się sam, tam gdzie skażenie SRP jest widoczne w świecie. Ma to być
przewidywalne dla gracza („idę na zainfekowany teren po pył”) i sterowalne z konfiguracji.
Konwersja zostaje bez zmian, jako drugie, fabularne źródło.

**Poza zakresem:** AI, nowe zaklęcia, wygląd. Tiery i siła są poruszone tylko w sekcji 6,
bo spawn zmienia, jak często gracz ich doświadcza.

## 2. Co już wiemy z kodu (sprawdzone 2026-10-02)

- `EntitySimWizard extends EntityInfHuman`, czyli to **pełny pasożyt SRP**.
  `EntitySimBattlemage` dziedziczy po `EntitySimWizard`. Wynikają z tego cztery rzeczy, które
  dostajemy za darmo:
  - **Sanctuary już go blokuje** na obu ścieżkach. `SanctuarySpawnVetoHandler.onCheckSpawn`
    (LOWEST, DENY) i `onEntityJoinWorld` testują `SanctuaryRegionHelper.isSrpParasite`, czyli
    `instanceof` bazy pasożyta. Nie trzeba ani linijki.
  - **Handler spawnu SRP** (`SRPSpawning$DimensionHandler.onSpawn`, `CheckSpawn`) widzi go jak
    każdego pasożyta: cap, cull „SOO MANY PARASITES” i mnożnik per wymiar z `srpwizmixins`.
    Szczegóły w sekcji 7, punkt 1.
  - **Kolektyw SRP** (CircleGroup, przyciąganie followerów, liczniki w save data) działa jak
    przy konwersji.
  - **Pętla dropu jest gotowa:** trzy tablice per tier, `onInitialSpawn` z rzutem tieru
    zależnym od fazy, skalowanie HP i pancerza.
- Faza SRP jest dostępna przez `SrpPhaseHelper.getEvolutionPhase(world)`. Jest bezpieczna bez
  SRP i używa tego samego id save data (104) co tier roll.
- „Skażenie” umiemy już rozpoznać. `SrpPurificationHelper.isSrpInfested(IBlockState)` to
  heurystyka, z której korzysta cleanse Sanctuary. `SrpNativePurifyHelper` to mapa samego SRP.
- `SpawnEngine` (`srpwizcore`, opis w `2026-10-02-spawnengine-v1-reference.md`) w swoich
  wymiarach liczy naszego moba pod kluczem `insanetweaks` i `insanetweaks:sim_wizard`.
  Opcja `Strip SRParasites In Dim 150` **nie** go obejmuje, bo wycina tylko namespace
  `srparasites`.
- Asymilacja wstawia moba przez `world.spawnEntity`. **Nie** przechodzi przez `CheckSpawn`,
  więc nic z tego specu jej nie dotyka.

## 3. Mechanizm: wpis na liście spawnów i bramka w `CheckSpawn`

### Rozważone

| | Opis | Za | Przeciw |
|---|---|---|---|
| **A** (rekomendowane) | `EntityRegistry.addSpawn(MONSTER)` w wybranych biomach plus bramka skażenia w `LivingSpawnEvent.CheckSpawn` | Dziedziczy **wszystkie** istniejące bramki: Sanctuary, cap SRP, SpawnEngine E1/E2, reguły InControl. Brak własnego ticka. Despawn jak u wszystkich. Zero nowego kodu wątkowego. | Biom jest tylko wstępnym filtrem, a waga wpisu rozcieńcza się przez odrzucone próby. Trzeba ją dostroić pomiarem (sekcja 5). |
| **B** | Własny spawner: tick co N sekund, losowy punkt przy graczu, test skażenia, `spawnEntity` | Pełna kontrola częstotliwości, niezależność od biomów | Omija Sanctuary `CheckSpawn` (zostaje tylko join-veto), cap SRP, SpawnEngine i InControl. Każdą z tych bramek trzeba by odtworzyć ręcznie, a ten repo już raz płacił za spawny omijające właściwą ścieżkę (Sanctuary, 2026-08-04). |
| **C** | Dopisać się do listy spawnów samego SRP | Najbardziej „natywnie” | Nie wiemy, czy SRP 1.10.7 ma taką listę dla obcych mobów. Wymaga mixina w cudzy spawner. |

**Wybór: A.** Jedyny jego koszt, kalibracja wagi, jest mierzalny. Koszty B i C to nowe klasy
błędów.

### Bramka

Jedna klasa `events/SimWizardNaturalSpawnHandler`, `@SubscribeEvent(priority = LOWEST)` na
`LivingSpawnEvent.CheckSpawn`:

1. Jeśli encja to nie `EntitySimWizard` (sprawdzenie typu jako **pierwsze**, bo event leci dla
   każdego moba) albo wynik już jest `DENY`, to wyjście.
2. Wymiar musi przejść filtr wymiarów (sekcja 4). W przeciwnym razie `DENY`.
3. Wymagamy `SrpPhaseHelper.getEvolutionPhase(world) >= minPhase` (dla battlemage'a
   `battlemageMinPhase`). W przeciwnym razie `DENY`.
4. **Test skażenia** na pozycji `(x, y-1, z)`, czyli tam, gdzie mob stoi. Blok pod stopami musi
   być skażony **i** co najmniej `minInfestedGround` z 25 bloków w kwadracie 5×5 pod stopami musi
   być skażonych. To daje 26 odczytów `getBlockState` na próbę, wyłącznie dla naszego moba.
   Sprawdzamy `isBlockLoaded` przy krawędzi.
5. **Gęstość:** żadnego innego `EntitySimWizard` (battlemage też się liczy) w promieniu
   `exclusionRadius`. W przeciwnym razie `DENY`.
6. W przeciwnym razie **nie dotykamy wyniku.** Nie ustawiamy `ALLOW`, bo `ALLOW` w Forge 1.12
   pomija `getCanSpawnHere` (światło, kolizje). Decyzja o reszcie zostaje przy vanilli i SRP.

Bramka działa tylko na tę jedną ścieżkę: naturalny spawn przez `WorldEntitySpawner`. Spawn eggi,
`/summon`, konwersja i spawnery bloków **nie** odpalają `CheckSpawn` z tymi samymi danymi albo nie
odpalają go wcale. Zostają nietknięte i to jest zamierzone.

🚨 **LOWEST i tylko DENY.** Jeśli `DimensionHandler.onSpawn` SRP ustawia `ALLOW` dla pasożytów
(do sprawdzenia, sekcja 7, punkt 1), a my bylibyśmy na wyższym priorytecie, SRP nadpisałby nasze
`DENY`. Na LOWEST piszemy ostatni. Sanctuary też jest na LOWEST i też tylko odmawia, więc
kolejność między nami nie ma znaczenia.

### Rejestracja wpisu

W `postInit`, bo dopiero wtedy biomy innych modów są w rejestrze. Iterujemy `ForgeRegistries.BIOMES`
i dodajemy `EntityRegistry.addSpawn(EntitySimWizard.class, weight, 1, 1, MONSTER, biomes...)`. Grupa
1–1, bo mag chodzi sam. Pomijamy biomy z typami `BiomeDictionary` z listy wykluczeń
(domyślnie `NETHER, END, VOID, MUSHROOM, OCEAN, RIVER`). Battlemage dostaje osobny wpis i osobną,
niższą wagę.

Waga jest **`@Config.RequiresMcRestart`**, bo listy spawnów budujemy raz. Pozostałe progi bramki
są czytane na żywo.

## 4. Konfiguracja

Nowa podkategoria `entities.assimilated_wizard.natural_spawn`. To jest istniejący dom wszystkich
ustawień sim wizarda (`EntitiesCategory.AssimilatedWizard`).

| Klucz | Domyślnie | Na żywo | Uwagi |
|---|---|---|---|
| `Enable Natural Spawn` | `true` | restart | Główny przełącznik. Przy `false` nic nie trafia na listy. Jest też wyłączony, gdy `Enable Sim Wizard` = false. |
| `Spawn Weight` | `4` | restart | Dla porównania zombie mają 100. Kalibracja w sekcji 5. |
| `Battlemage Spawn Weight` | `1` | restart | 0 wyłącza. |
| `Excluded Biome Types` | `NETHER, END, VOID, MUSHROOM, OCEAN, RIVER` | restart | Nazwy `BiomeDictionary.Type`. Nieznane nazwy są logowane i pomijane. |
| `Dimension Mode` / `Dimensions` | `WHITELIST` / `{0}` | tak | Decyzja autora (sekcja 8). |
| `Min Phase` | `2` | tak | Faza SRP potrzebna do spawnu. |
| `Battlemage Min Phase` | `4` | tak | |
| `Min Infested Ground (of 25)` | `8` | tak | 0 = wystarczy skażony blok pod stopami. |
| `Exclusion Radius` | `48` | tak | Promień bez drugiego maga. 0 wyłącza test. |
| `Debug Log` | `false` | tak | Liczniki prób, odmów per powód i spawnów, logowane co minutę. Bez tego nie da się skalibrować wagi. |

## 5. Kalibracja: ile pyłu ma wypadać

Skuteczna częstotliwość to waga × odsetek prób, które przejdą bramkę. Odsetek zależy od tego, ile
skażenia jest wokół gracza, i tego nie da się policzyć z biurka. Dlatego:

1. Wydanie z `Debug Log` i wagą domyślną.
2. Pomiar na DEv 1.2 w trzech miejscach: czysty teren, skraj strefy skażenia i środek węzła.
   Po 20 minut w każdym, przy fazie 2 i fazie 4.
3. **Cel do potwierdzenia przez autora:** w środku skażenia przy fazie 2 mniej więcej jeden mag
   na 5–10 minut aktywnego przebywania, a na czystym terenie zero. Przy obecnych tablicach
   (novice: pył 1/3 szansy, 1 szt.) daje to rząd 2–6 pyłu na godzinę z samych novice'ów.
   To mniej niż receptura zapasowa, gdy liczyć surowe ilości, ale bez jej kosztu. Jeśli to za
   mało, dźwignią jest waga, nie tablice lootu.

## 6. Siła i zaklęcia: co zmienia częstszy kontakt

- **Tier roll zostaje bez zmian.** `rollTier` już robi `1 + phase / tierPhaseRollDivisor` rzutów
  i bierze najlepszy, więc faza decyduje o **szansie** na mastera, a nie o jego sile. Naturalny
  spawn nie dostaje `tierFloor`, bo floor jest dla konwersji „zły mag → co najmniej adept”.
- **Zaklęcia abominacji w puli maga to osobna decyzja**, ale ten spec ją przygotowuje. Aby mag
  użył naszego zaklęcia, potrzebne są **dwie** rzeczy: `npcs: true` w JSON (veto w
  `NpcCastVetoArbiter`) **i** wpis w `Spell Pool`. Kandydaci z pasma „narzędzie” (≤ 250 many):

  | zaklęcie | typ | koszt | uwaga |
  |---|---|---|---|
  | `dispatcher_grasp` | attack | 130 | Naturalny kandydat. |
  | `yelloweye_gland` | attack | 150 | Naturalny kandydat. |
  | `immune_bond` | utility | 140 | **Odradzam.** Chroni przed infekcją, a pasożyt rzucający to na sojusznika nie ma sensu. |

  Rytuały (≥ 500) i summony bez `npcs` zostają zamknięte. Trzy summony (`fer_cow`, `light_bomber`,
  `primitive_yelloweye`) już mają `npcs: true` i są bramkowane przez `Include Abomination Summons`.
  Otwarcie flagi `npcs` nie wpuszcza zaklęć do magów EBW, bo `populateSpells` ma redirect, który
  trzyma abominację poza ich generatorem.

## 7. Do przeczytania w bytecode SRP 1.10.7 PRZED planem

Każdy punkt może zmienić projekt. Handoff Abomination: „pięć błędów miało jedną przyczynę,
przeczytanie fragmentu i uogólnienie”. Te metody czytamy **do końca**.

1. **`SRPSpawning$DimensionHandler.onSpawn`.** Czy ustawia `ALLOW` albo `DENY` dla pasożytów i
   na jakich warunkach (faza, wymiar, cap, światło)? Jeśli dla fazy 0 robi `DENY`, nasz `Min Phase`
   jest drugą bramką. To jest w porządku, ale trzeba to wiedzieć. Jeśli ustawia `ALLOW`, sekcja 3
   jest konieczna dokładnie w tej formie.
2. **`EntityParasiteBase.getCanSpawnHere` / `isValidLightLevel`.** Czy pasożyt wymaga ciemności?
   Jeśli tak, w dzień na otwartej strefie skażenia **nic się nie pojawi**. Wtedy override w
   `EntitySimWizard` z własnymi warunkami jest częścią tego specu.
3. **`EntityParasiteBase.canDespawn` / `despawnEntity`.** Naturalny spawn **musi** despawnować się
   jak potwór, inaczej magowie się kumulują. Sprawdzić też, czy asymilowani są trwali i czy chcemy
   to rozróżnić (NBT flaga „natural”).
4. **`isCreatureType(MONSTER, …)`.** Czy baza pasożyta liczy się jako `IMob`? Jeśli nie, wpis
   `MONSTER` nie przejdzie filtra typu E2 w SpawnEngine (fix InControl z 1.6.2) i w wymiarach
   silnika mag nie pojawi się nigdy.
5. **Cull „SOO MANY PARASITES”.** Przy przekroczonym capie SRP usuwa pasożyty daleko od graczy.
   Mag spawnuje się blisko gracza, więc ryzyko jest małe. Trzeba jednak potwierdzić promień
   ochrony `capPurgeProtectRadius` w `srpwizmixins`.

## 8. Decyzje autora (z moją rekomendacją)

1. **Wymiary.** Rekomendacja: `WHITELIST {0}` na start i dopisywanie wymiarów po pomiarze.
   Wymiar 150 ma własną politykę pasożytów, a Lost Cities 111 ma obniżony cap SRP.
2. **`Min Phase` 2 / battlemage 4.** We wczesnej grze ekonomię niesie receptura zapasowa, a mag
   pojawia się, gdy świat zaczyna wyglądać na przegrany.
3. **Cel podaży z sekcji 5:** 1 mag na 5–10 minut w środku skażenia.
4. **`dispatcher_grasp` i `yelloweye_gland` z `npcs: true`.** Tak, ale jako **osobne** wydanie
   po kalibracji spawnu, żeby nie mieszać dwóch zmian trudności w jednym pomiarze.
5. **Rozróżnienie despawnu** naturalny/asymilowany (zależy od sekcji 7, punkt 3).

## 9. Plan testu (in-game, DEv 1.2)

- Czysty świat, faza 0: zero spawnów, a `Debug Log` pokazuje odmowy z powodem `phase`.
- `/srpevolution` do fazy 2, czysty teren: odmowy `ground`.
- Ręcznie skażona plama 7×7 (infested stain): spawny pojawiają się, a drugi mag w promieniu 48
  dostaje odmowę `exclusion`.
- Sanctuary nad plamą: zero spawnów (log `spawn-vetoed` Sanctuary).
- Wymiar spoza whitelisty: odmowa `dimension`.
- Drop z naturalnego maga: pył abominacji (meta = ordinal, funkcja `insanetweaks:abomination_meta`).
- Despawn po oddaleniu się na ponad 128 bloków.
- Serwer dedykowany: start bez błędów. Handler jest czysto serwerowy, bez typów klienckich.

## 10. Pliki (szkic)

- `events/SimWizardNaturalSpawnHandler.java`: bramka (nowy).
- `init/ModEntitySpawns.java`: `addSpawn` w `postInit` (nowy). Rejestracja encji zostaje
  w `InsaneTweaksMod.init`.
- `config/categories/EntitiesCategory.java`: podkategoria `NaturalSpawn` plus klucze lang dla GUI.
- `InsaneTweaksMod`: rejestracja handlera i **przywrócenie `postInit`**, które zniknęło 2026-08-06
  razem z integracją Reskillable, tylko po to, żeby wywołać `ModEntitySpawns`.
- Ewentualnie `EntitySimWizard`: override `getCanSpawnHere` i despawnu, zależnie od sekcji 7.
