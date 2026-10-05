# EbreduxAddon 0.3: ewolucja zarażonego maga (Mykomanta)

**Data:** 2026-10-05
**Status:** projekt.
**Poprzednio:** spec v0.2 (zarażony mag), lista ograniczeń 0.2.0 („zarażony mag nie ewoluuje
dalej, brak `EvolvingInfected`”).
**Zależności:** bez zmian. Spore 2.2.0j (Forge 1.20.1, Modrinth `PbOZOahW`) jako `compileOnly`, a cała
nowa treść leży za bramką `SporeCompat`.

## 1. Cel

Zarażony mag z 0.2 zachowuje się jak każdy podstawowy zarażony Spore:
- zabija;
- zbiera punkty ewolucji;
- po czasie ewoluuje.

Wynikiem jest nowy mob **Mykomanta** (`ebreduxaddon:mycomancer`), czyli ewoluowany zarażony
(`EvolvedInfected`):
- ma pełny arsenał mistrza swojego żywiołu;
- osłania zarażonych wokół siebie.

To odpowiednik ewolucji z 1.12.2 (sim wizard → faza SRP), ale na zasadach Spore, bez własnego
licznika.

## 2. Ustalenia z bajtkodu (Spore 2.2.0j)

| Ustalenie | Skutek |
|---|---|
| `EvolvingInfected` to interfejs z metodami domyślnymi. `tickEvolution(infected, lista, wariantScampera)`: co 20 ticków, gdy `getEvoPoints() >= min_kills` (domyślnie 1), licznik `getEvolutionCoolDown()` rośnie o 1 (nie rośnie pod `frostbite`), a przy `evolution_age_human` (domyślnie 300 s) wołane jest `Evolve`. | Implementujemy interfejs i wołamy `tickEvolution` w `tick()`, jak `InfectedWitch`. Progi i czasy bierzemy z configu Spore, więc gracz stroi je w jednym miejscu. |
| Domyślne `Evolve`: z szansą 90% losowy typ z listy, w 10% `Scamper` z wariantem. Przenosi nazwę, efekty, `kills`, `evoPoints`, `searchPos` i `linked`, woła `finalizeSpawn(CONVERSION)`, a oryginał robi `discard`. | Nadpisujemy `Evolve` własną wersją z tym samym przeniesieniem stanu. Dzięki temu Mykomanta dziedziczy żywioł i zaklęcia **przed** `finalizeSpawn` i nie losuje ich od nowa. |
| `Infected.awardKillScore`: każde zabójstwo daje +1 `kills` i +1 `evoPoints`. `spawnWithPoints`: 30% szans na start z `min_kills`, gdy klasa jest `EvolvingInfected` (i `at_mob` jest wyłączone). `setDefaultLinkage` przy ≥ progu Proto World: 30% szans na start z gotową ewolucją. | Za darmo dostajemy rytm Spore. W świecie z Proto Hivemindami część magów ewoluuje szybko. |
| `Infected.removeWhenFarAway` zwraca false dla `EvolvingInfected` z punktami. | Mag, który zaczął ewolucję, nie znika. |
| `EvolvedInfected`: limit obrażeń `maxHP/3` na trudnym (opcja `damagecap`), brak głodu, wytrzymałość na zimno `EVOLVED`, dźwięk `EVOLVE_HURT`, despawn tylko gdy niezlinkowany. | Mykomanta dziedziczy po `EvolvedInfected` i dostaje to wszystko. |

## 3. Ewolucja zarażonego maga

- `InfectedWizardEntity implements EvolvingInfected`; w `tick()` woła
  `tickEvolution(this, Config.infectedWizard.evolutions, ScamperVariants.VILLAGER)`.
- **Nasze `Evolve`:**
  - z szansą `evolveChance` (domyślnie 0,9) typ z listy `evolutions` (domyślnie tylko
    `ebreduxaddon:mycomancer`), w przeciwnym razie `Scamper` jak u Spore;
  - przenosi stan dokładnie jak Spore;
  - gdy wynik to Mykomanta, dodatkowo dostaje ona żywioł i zaklęcia maga.
- Na liście może być dowolny typ Spore (np. `spore:knight`). Nieznane id są pomijane z jednym
  ostrzeżeniem w logu, a pusta lista po odfiltrowaniu oznacza zawsze `Scamper`.
- Wyłącznik: `evolutionEnabled`. Gdy jest false, `tickEvolution` nie jest wołane.

## 4. Mykomanta

- `MycomancerEntity extends EvolvedInfected implements ISpellCaster`.
- **Atrybuty** (każdy × globalne mnożniki Spore):
  - 60 HP, 6 obrażeń wręcz, 8 pancerza;
  - prędkość 0,24, zasięg śledzenia 48;
  - odrzut 0,5.
- **Zaklęcia:**
  - tier zawsze master;
  - dziedziczy zaklęcia maga i dobiera jedno zaklęcie swojego żywiołu, tak żeby zestaw liczył
    `spellCount + 1` (bez Spine Volley);
  - Spine Volley zostaje, gdy był;
  - mob powstały spoza ewolucji (`/summon`, jajko) losuje zestaw sam, od razu jako master.
- **Cel ataku:** `AttackSpellGoal(this, 0.6, 16, 20, 40)`, czyli szybciej i dalej niż mag (30–50 ticków,
  14 bloków). Pod spodem melee.
- **Różdżka:** mistrzowska różdżka Redux swojego żywiołu, szansa upuszczenia `wandDropChance` × 2.
- **Osłona grzybni** (umiejętność):
  - co `wardInterval` ticków (domyślnie 200), gdy ma cel, daje zarażonym Spore w promieniu
    `wardRadius` (12) Resistance I na `wardDuration` (120) ticków;
  - działa na zarażonych bez siebie i bez Mykomant (nie wzmacniają się nawzajem w pętli);
  - towarzyszy jej pierścień cząsteczek i dźwięk;
  - gracze i moby spoza roju nigdy nie dostają efektu, bo filtrem jest `instanceof Infected`;
  - z wyborem celów i licznikiem w `core.WardRules`, z testem.
- **Wygląd:** model maga Redux powiększony do 1,15 i własne tekstury (ciemniejsze przebarwienie z
  narośli, `tools/infected_textures.py` z nowym profilem).
- **Łup:** `loot_tables/entities/mycomancer.json`, czyli kryształy magii 3–6 (looting +0–2), zgniłe
  mięso 1–3 oraz przy zabiciu przez gracza 25% (+5% za poziom looting) na 6–12 kryształów. Bez
  przedmiotów Spore.
- **Tag:** `spore:fungus_entities` (wpis `required: false`, jak mag). Pulse go rani, a zabicie daje
  punkty symbiozy.
- **NBT:** jak mag. Wspólny kod losowania, zapisu i różdżki trafia do jednej klasy `WizardLoadout`,
  której używają oba moby.
- **Jajko spawnu** w zakładce addonu, gdy jest Spore.
- Bez dalszej ewolucji (hyper): to temat na później.

## 5. Config (`[infectedWizard]`)

| Klucz | Domyślnie | Opis |
|---|---|---|
| `evolutionEnabled` | true | Wyłącznik ewolucji maga. |
| `evolveChance` | 0.9 | Szansa na typ z listy zamiast `Scamper`. |
| `evolutions` | `["ebreduxaddon:mycomancer"]` | Typy, w które mag może ewoluować. |
| `wardRadius` | 12.0 | Promień osłony grzybni. |
| `wardInterval` | 200 | Co ile ticków. |
| `wardDuration` | 120 | Czas efektu w tickach. |
| `wardAmplifier` | 0 | Poziom Resistance (0 = I). |

Progi czasu i zabójstw należą do Spore (`Evolution Timer in seconds`, `Minimum amount of kills…`).

## 6. Weryfikacja

Celowo bez GameTestów, które przestawiają config (`evolutionEnabled`, nieznane id na liście). Testy
jednej partii biegną równolegle, więc zmiana configu rozlałaby się na sąsiednie testy. Regułę wyboru
pokrywa JUnit `EvolutionPick`, a ostrzeżenie o nieznanym id sprawdza start serwera z literówką
w configu.

- JUnit:
  - `WardRules`: interwał, filtrowanie celów, brak wzmacniania Mykomant;
  - `EvolutionPick`: podział 90/10, cała lista, pusta lista, przycinanie szansy.
- GameTesty ze Spore:
  - mag z `evoPoints ≥ min_kills` i licznikiem na progu ewoluuje w Mykomantę przy najbliższym
    pełnym 20. ticku; nowa encja ma żywioł i zaklęcia maga plus jedno, tier master, `kills`
    i nazwę;
  - osobno samo przeniesienie stanu (`evolveInto`): żywioł, zaklęcia, master, `kills`, `evoPoints`,
    nazwa i różdżka;
  - osłona: zarażony obok dostaje Resistance, a krowa, druga Mykomanta i ona sama nie;
  - Mykomanta trafia wrogi cel przez `AttackSpellGoal` Redux. Zestaw w teście to sam Spine Volley:
    losowy zestaw mistrza bywa samymi buffami i przywołaniami, co dawało czerwony test bez błędu
    w kodzie. Ta sama poprawka objęła test rzucania zarażonego maga z 0.2;
  - zapis i odczyt NBT; Mykomanta z `/summon` losuje sama jako master;
  - predykat roju: zarażony Spore i Mykomanta się nie atakują.
- GameTest bez Spore: typu `mycomancer` nie ma.
- Serwer dedykowany a/b/c: start czysty.
- Klient pod Xvfb ze Spore: Mykomanta na scenie, skala i tekstury.
