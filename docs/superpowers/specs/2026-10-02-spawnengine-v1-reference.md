# SpawnEngine v1 (`srpwizcore`): referencja odtworzona z kodu

**Status:** dokument referencyjny. Opisuje to, co jest w kodzie `srpwizcore` 1.15.1, a nie plan.
**Data:** 2026-10-02.

**Oryginalny spec jest obok:** `2026-07-27-spawnengine-v1-spec.md`, skopiowany 2026-10-02 z
`pegek/SRP-Wizardry` (`notes/spawnengine_v1_spec_2026-07-27.md`), gdzie leżał dotąd. Javadoki
`srpwizcore` odsyłały do `notes/` w repo modów, którego tu nie ma. Teraz wskazują na kopię obok.

Ten dokument to krótsze streszczenie stanu spisane z kodu `srpwizcore` 1.15.1, a nie z planu.
Gdy rozjedzie się ze specem, rację ma **kod**: spec opisuje zamiar z 2026-07-27, a kod obejmuje
też poprawki 1.6.1 i 1.6.2.

## Po co

W wymiarze 150 vanilla mob cap przestał działać: 2026-07-21 zmierzono 1600–2000 hostyli przy
capie 55, a `findChunksForSpawning` zjadał 39,1% ticka. Silnik nie naprawia vanillowego
sprawdzenia. Uruchamia własne **przed nim**, tylko w wymiarach z listy `Engine Dims`.

Przyczynę obejścia capu znaleziono później (1.6.2, 2026-07-27). `potentialspawn` w InControl
wstrzykuje wpisy do **każdego** zapytania `PotentialSpawns`, niezależnie od typu. Potwory
spawnowały się więc w przebiegach AMBIENT/WATER, które odpalają co tick, a ich capy liczą
prawdziwe ambienty (około 0) i nigdy nie blokują.

## Cztery elementy

| | Gdzie | Co robi |
|---|---|---|
| **E1** gate | `MixinWorldEntitySpawner` HEAD → `SpawnEngine.gateFindChunks` | Anuluje cały przebieg (`setReturnValue(0)`), gdy każdy typ, który vanilla przetworzyłaby w tym ticku, jest już na budżecie. Nie skanuje chunków i nie konstruuje żadnej encji. |
| **E2** filtr listy | `SpawnListFilterHandler`, `WorldEvent.PotentialSpawns` na `LOWEST` | Czyści listę kandydatów przed konstrukcją encji. Typ pełny → pusta lista. Wpis, którego prawdziwy typ ≠ typ zapytania → usunięty (to jest fix InControl). Namespace albo id ponad budżetem → usunięty. |
| **E3** spis | `SpawnEngineTickHandler`, co 20 ticków | Jeden przebieg po `loadedEntityList`. Typy liczy tak jak `countEntities(type, true)`. Namespace'y i pełne id liczy tylko dla żywych `EntityLivingBase`. |
| **E4** wiadro | `MixinWorldEntitySpawner` RETURN → `onPassCompleted` | Wartość zwracana przez przebieg to faktyczna liczba spawnów (sprawdzone w bytecode). Pobiera ją z wiadra tokenów MONSTER. Dolny limit długu to `-burst`, więc maksymalna blokada trwa `burst/rate`. |

Pierwsza sekunda w świeżo załadowanym wymiarze nie ma jeszcze spisu, więc E1 blokuje przebieg.
Gdyby go przepuścić, dostalibyśmy falę startową i fantomowe obciążenia wiadra (1.6.1).

Przebieg zwierząt (co 400 ticków) **nigdy** nie jest dławiony przez `Hostile Pass Interval`.
Inaczej CREATURE głodowałby przy każdym interwale, który nie dzieli 400.

## Konfiguracja (`SpawnEngineCategory`, wszystko czytane na żywo)

- `Enable Spawn Engine` (domyślnie OFF) i `Engine Dims` (domyślnie `{150}`).
- `Type Budgets`: `dim:TYPE=N`. `Namespace Budgets`: `dim:modid=N` albo `dim:modid:entity=N`.
  Klucz to wszystko po **pierwszym** dwukropku. Budżet na cały namespace `iceandfire` jest
  odrzucany celowo, bo kasowałby unikalne smoki z gniazd.
- `Ungoverned Types Veto Stop` (ON = bezpiecznie): typ bez budżetu zawsze podtrzymuje przebieg.
- `Monster Refill Rate` (na minutę) i `Monster Refill Burst` (domyślnie 20, gdy brak wpisu).
- `Strip SRParasites In Dim 150`: niezależny od głównego przełącznika. Wycina **namespace
  `srparasites`**. Moby `insanetweaks`, które są pasożytami SRP (sim wizard, sim battlemage),
  **nie** podlegają temu wycięciu.
- `Diag Logging`: działa też przy wyłączonym silniku, żeby dało się zmierzyć stan przed
  konfiguracją.

## Co to znaczy dla moba z innego moda

- Mob spawnujący się naturalnie w wymiarze silnika przechodzi przez E2. Podlega budżetowi
  swojego namespace'u i pełnego id oraz budżetowi swojego `EnumCreatureType`.
- Mob wstawiony przez `World.spawnEntity` (summon, konwersja, własny spawner) omija E1 i E2.
  **Liczy się** jednak w E3 i może zablokować naturalne spawny innych.
- Wątki: tabele i spisy są niemutowalne, publikowane przez `volatile`. Czytelnicy
  `PotentialSpawns` mogą działać na workerach EntityThreading.
