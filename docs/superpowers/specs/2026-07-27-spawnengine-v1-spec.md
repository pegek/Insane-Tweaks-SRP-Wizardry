# SPEC: SpawnEngine v1 (srpwizcore 1.6.0) — natywna kontrola spawnu per-dim, 2026-07-27

**Status: SPEC zatwierdzany przez usera → potem plan wykonawczy / wykonanie.**
Autor: overseer (rekonesans bytecode + audyt CleanMix 2026-07-27). Zakres v1 uzgodniony
w konwersacji overseer (repo modDev): dwuetapowy plan, v1 = warstwa capów/filtrowania,
v2 (osobno, później) = ewentualna migracja aktywnego spawnera z groovy.

## 0. Problem i dowody (nic tu nie jest hipotezą)

1. **Silnik spawnu = 39,1% ticku w dim 150** przy wysyconej populacji
   (`WorldEntitySpawner.findChunksForSpawning` poddrzewo 41,66 s / 106,5 s, profil #4
   `8lLXkfzCqL`), z czego `dim_mobcap` (cancel-on-join) = 14,5% — cancel na
   `EntityJoinWorldEvent` płaci PEŁNĄ konstrukcję encji. Storm: ~387 odrzuceń/s szczyt,
   74k/sesję. W OW ten sam pipeline = 0,1%.
   → `notes/flare_dim150_spike_report_2026-07-25.md` §6b, §8.
2. **Vanilla hostile mob-cap jest w dim 150 OMIJANY** — zmierzone 2026-07-21: populacja
   licząca się do capa rośnie do 1600–2000+ przy limicie 55 (UT Spawn Caps, mixin
   zweryfikowany jako działający); `doMobSpawning=false` zatrzymuje wzrost, więc to
   ścieżka naturalnego spawnera z ominiętą komparacją. Winowajca NIEZNANY (podejrzani:
   OTG-Core transform, inflacja `eligibleChunks` w formule `cap*chunks/289`).
   → `notes/underneath_v3_doomlike_rc_mobs_spec_2026-07-21.md` §"ODKRYCIE 2026-07-21".
   **Konsekwencja projektowa:** engine NIE naprawia vanilla-owej komparacji (nieznany
   mechanizm = nie wiadomo co naprawiać), tylko ZASTĘPUJE ją własną, wykonywaną PRZED nią.
3. Cancel-on-join zostaje jako pas bezpieczeństwa (lekcja periodic-cull: eksplozja do 1724
   encji, slime-split) — ale po v1 jego koszt spada do ~zera, bo storm znika u źródła.

## 1. Inwarianty (nie do złamania — z pamięci projektu)

- **Dim 150: zero SRParasites, na zawsze.** Tabele/filtry engine'a dla 150 nie zawierają
  SRP; dodatkowo twardy veto-strip (bezpłatny przy okazji filtrowania — §4.3).
- **Nigdy cap po namespace `iceandfire`** — wyłącznie per-typ (unikalne smoki/bossy).
- **OW zostaje na InControl/groovy.** Engine tylko dim 150 (pilot), potem ewentualnie 111.
- **SRP event-spawns (Beckon/flara/nody przez `ParasiteEventWorld`) omijają naturalny
  pipeline** — engine ich nie widzi i nie dotyka (dot. przyszłego dim 111). Per-dim mobcap
  z `srpwizmixins` zostaje jako jedyna warstwa łapiąca własne spawny SRP.
- Routing: engine = pack-glue → **srpwizcore** (prywatny). Zero zależności od contentu.

## 2. Wynik rekonesansu kotwic (2026-07-27, audyt cleanmix.log + javap + joined.srg)

| fakt | dowód |
|---|---|
| **NIKT nie miksuje w `WorldEntitySpawner`** — kotwica bezkolizyjna | grep `cleanmix.log` `APPLY.*WorldEntitySpawner` = 0 trafień (pełna sesja 2026-07-27 05:10) |
| UT Spawn Caps siedzi na `EnumCreatureType` (`UTSpawnCapsMixin`, ModifyArg w `<clinit>` — zmienia stałe enuma; Monster=55) | javap UT-1.20.1 + `Universal Tweaks - Tweaks.cfg` |
| `WorldServer` ma 7 cudzych mixinów (CQR/deeperdepths/raids/UT×2/entitythreader/stellarcore) — **unikać kotwic w WorldServer**; sprawdzone: żaden nie dotyka spawn-pipeline'u (deeperdepths = piorunochrony) | audyt + javap |
| SRG: `findChunksForSpawning` = `func_77192_a(Lnet/minecraft/world/WorldServer;ZZZ)I` (instancyjna); `EnumCreatureType.getMaxNumberOfCreature` = `func_75601_b` | joined.srg mcp_stable/39 |
| `PotentialSpawns` (Forge event, `WorldEvent.PotentialSpawns`) odpala się w `WorldServer.getSpawnListEntryForTypeAt` (`func_175734_a`) i `canCreatureTypeSpawnHere` (`func_175732_a`) — event, zero mixina, zero kolizji | Forge 1.12.2; ⚠️ wykonawca potwierdza javap-em na dev-jarze przed kodem |
| RLTweaker `patchEnchantments` nie dotyka spawnów; InControl działa na CheckSpawn/PotentialSpawns **eventach** (współistnienie przez priorytety, §4.2) | audyt configów |

## 2b. WYMÓG GAMEPLAYOWY (user, 2026-07-27) — koniec z nieskończonymi hordami

Obecny układ (spawner + hard capy) daje patologię **cap-driven refill**: cap to POZIOM,
nie TEMPO — gracz zabija kilkanaście encji, cap natychmiast widzi wolne sloty i spawner
je wypełnia w następnych przebiegach. Zero oddechu, walka nigdy się nie kończy.
Vanilla-feeling = po wybiciu okolicy jest CISZA, populacja odbudowuje się STOPNIOWO.

**Rozwiązanie: E4 — limiter tempa odbudowy (token bucket per dim×typ).** Cap dalej
ogranicza poziom, bucket ogranicza TEMPO powrotu do niego. Po fali walki populacja
zostaje niska i rośnie w konfigurowalnym tempie (np. 12 spawnów/min), zamiast skokowo.
To jest czwarty mechanizm architektury (patrz §3/§4.6) i JEST w zakresie v1.

## 3. Architektura v1 — cztery mechanizmy, wszystkie PRZED konstrukcją encji

```
                    ┌──────────────────────────────────────────────────────┐
 tick serwera ─────►│ E1  MixinWorldEntitySpawner (HEAD func_77192_a)      │
                    │     dim w engineDims?                                │
                    │       throttle: co N ticków (hostilePassInterval)    │
                    │       budżet TOTAL per typ pełny? → return 0 (STOP)  │
                    │       E4: tokeny odbudowy ≤ 0? → return 0 (STOP)     │
                    │     (RETURN-inject: spawny przebiegu konsumują tokeny)│
                    └──────────────┬───────────────────────────────────────┘
                                   ▼ (budżet częściowo wolny)
                    ┌──────────────────────────────────────────────────────┐
                    │ vanilla: skan pozycji → getSpawnListEntryForTypeAt   │
                    │   └─► E2  handler PotentialSpawns (priority LOWEST)  │
                    │        filtr wpisów: namespace/typ na limicie → OUT  │
                    │        dim150: strip srparasites (veto-inwariant)    │
                    │        pusta lista → vanilla NIC nie konstruuje      │
                    └──────────────┬───────────────────────────────────────┘
                                   ▼ (tylko wpisy z wolnym budżetem)
                    konstrukcja encji → CheckSpawn (InControl) → join
                                   ▼
                    dim_mobcap.groovy (pas bezpieczeństwa, koszt ~0 po E1/E2)
```

- **E1** — mixin early (vanilla target, manifest `MixinConfigs`), HEAD-inject z
  `cancellable=true`: dla wymiarów engine'a decyzja capowa należy do NAS i wykonuje się
  przed jakimkolwiek skanem chunków. Odporny na bypass z §0.2 niezależnie od jego
  mechanizmu. `setReturnValue(0)` = koszt przebiegu ≈ 0.
- **E2** — czysty Forge event handler (bez mixina): egzekwuje budżety per-namespace
  i per-typ na poziomie LISTY kandydatów. Vanilla nie konstruuje tego, czego nie ma na
  liście — dokładnie odwrotność cancel-on-join. Priorytet `LOWEST` + brak `receiveCanceled`:
  InControl (dodaje wpisy w potentialspawn) wykonuje się wcześniej, my filtrujemy WYNIK.
- **E3** — licznik populacji: jedna przebieżka `loadedEntityList` per engine-dim co 20
  ticków (`ServerTickEvent`/`WorldTickEvent`), buduje mapy namespace→N, fullId→N,
  typ→N(forSpawnCount). Snapshot w `util/SpawnEngineState`. ~500 encji/przebieg = pomijalne
  (dzisiejszy groovy robi to samo per-join!).

**Czego v1 świadomie NIE robi:** nie spawnuje niczego aktywnie (roster lavacow/mocreatures
zostaje w `underneath_spawner.groovy`), nie rusza OW, nie rusza InControl configów, nie
wyłącza dim_mobcap. Migracja rosteru + `opts` (cap/chance/post) = v2, po pomiarach v1.

## 4. Projekt szczegółowy

### 4.1 Config (kategoria `spawnEngine` w `srpwizcore.cfg`)

Format stringowy w konwencji Forge-cfg (parsowany do struktur przy load/OnConfigChanged):

```
spawnengine {
    B:"Enable Spawn Engine"=true                # master, read live
    S:"Engine Dims" <
        150
    >
    I:"Hostile Pass Interval (ticks)"=10        # E1 throttle; 1 = jak vanilla
    # TOTAL per (dim, typ) - zastepuje omijana vanilla-komparacje.
    # Format: dim:TYP=N   (TYP: MONSTER/CREATURE/AMBIENT/WATER_CREATURE)
    S:"Type Budgets" <
        150:MONSTER=170
        150:CREATURE=25
    >
    # Budzety namespace/typ egzekwowane na liscie kandydatow (E2).
    # Format: dim:namespace=N  albo  dim:modid:entity=N (pelne id = per-typ).
    # UWAGA: iceandfire WOLNO tylko per-typ (inwariant) - walidacja odrzuca
    # "dim:iceandfire=N" z ERROR-em w logu.
    S:"Namespace Budgets" <
        150:defiledlands=65
        150:deeperdepths=45
        150:babymobs=15
        150:mutantbeasts=4
        150:minecraft=150
        150:defiledlands:slime_defiled=8
        150:iceandfire:if_troll=12
        150:iceandfire:if_cockatrice=12
        150:iceandfire:deathworm=8
        150:iceandfire:dread_lich=2
    >
    # E4 - tempo odbudowy populacji (token bucket). Format: dim:TYP=tokenow_na_minute.
    # Bucket startuje PELNY (= pojemnosc burst), spawny go oprozniaja w tempie
    # faktycznych spawnow (return value przebiegu), odnawia sie liniowo.
    # 0 lub brak wpisu = bez limitu tempa (zachowanie sprzed E4).
    S:"Refill Rates (per minute)" <
        150:MONSTER=12
        150:CREATURE=4
    >
    # Pojemnosc bucketa (maks. "fala" po dlugiej ciszy). Format: dim:TYP=N.
    S:"Refill Burst" <
        150:MONSTER=24
        150:CREATURE=8
    >
    B:"Strip SRParasites In Dim 150"=true       # veto-inwariant, read live
    B:"Diag Logging"=false                      # E1 loguje i4/k/cap + tokeny (throttled)
}
```

Wartości startowe = **1:1 z `dim_mobcap.groovy` v1.6** (żeby parity-test porównywał
mechanizm, nie liczby). `150:MONSTER=170` ≈ suma dzisiejszych capów wrogich — celowo
powyżej ich sumy, bo E2 i tak tnie per-namespace; TOTAL jest bezpiecznikiem na bypass.
🚨 Master-toggle i wszystkie budżety **read live** (bez `RequiresMcRestart`) — mixin E1
aplikuje się zawsze, flaga jest early-returnem (lekcja: flaga ≠ bramka aplikacji bez
`IMixinConfigPlugin`).

### 4.2 E1 — `MixinWorldEntitySpawner` (early, do `mixins.srpwizcore.early.json`)

```java
@Mixin(WorldEntitySpawner.class)
public class MixinWorldEntitySpawner {

    @Inject(method = {"findChunksForSpawning", "func_77192_a"}, at = @At("HEAD"),
            cancellable = true, remap = false)
    private void srpwizcore$engineGate(WorldServer world, boolean spawnHostile,
            boolean spawnPeaceful, boolean spawnOnSetTickRate,
            CallbackInfoReturnable<Integer> cir) {
        // cala logika w SpawnEngine.gate(...) (klasa poza pakietem mixinowym);
        // mixin = 3 linie, zero pol statycznych, zero <clinit>
        if (com.spege.srpwizcore.spawnengine.SpawnEngine.gateFindChunks(
                world, spawnHostile, spawnPeaceful)) {
            cir.setReturnValue(Integer.valueOf(0));
        }
    }
}
```

`SpawnEngine.gateFindChunks`: dim nie-engine → false (przezroczyste). Engine-dim:
throttle (world.getTotalWorldTime() % interval — per-dim czas jest tu OK, bo tylko
modulo w obrębie jednego wymiaru), potem snapshot z `SpawnEngineState`: jeśli KAŻDY
aktywny typ (MONSTER przy spawnHostile, CREATURE przy spawnPeaceful) ma count ≥ TOTAL
budżet → true (pełny stop). Diag: przy `Diag Logging` loguje co ~200 wywołań
count/budżet — to zarazem instrumentacja, która **rozstrzygnie mechanizm bypassu**
(porównanie naszego countu z zachowaniem vanilla po bramce).

⚠️ Dual selector `{"findChunksForSpawning","func_77192_a"}` — działa i w dev, i w SRG
runtime (wzorzec `MixinEntityParasiteBase`).

### 4.2b E4 — limiter tempa odbudowy (w tym samym mixinie)

Drugi inject w `MixinWorldEntitySpawner`:

```java
    @Inject(method = {"findChunksForSpawning", "func_77192_a"}, at = @At("RETURN"),
            remap = false)
    private void srpwizcore$consumeRefillTokens(WorldServer world, boolean spawnHostile,
            boolean spawnPeaceful, boolean spawnOnSetTickRate,
            CallbackInfoReturnable<Integer> cir) {
        SpawnEngine.onPassCompleted(world, cir.getReturnValue().intValue());
    }
```

**Wartość zwracana `findChunksForSpawning` = liczba encji FAKTYCZNIE zespawnowanych
w przebiegu** — konsumpcja tokenów jest więc mierzona bez heurystyk (żadnego zgadywania
w `EntityJoinWorldEvent`, czy join to natural spawn czy Doomlike-spawner/summon).
⚠️ Wykonawca potwierdza semantykę return value javap-em/źródłem dev-jara przed kodem
(vanilla 1.12.2: `j4 += ...` zliczane per udany spawn; jeśli Cleanroom to zmienił,
fallback: licznik joinów natural-only — decyzja wtedy, nie teraz).

Mechanika bucketa (w `SpawnEngine`, per dim×typ, stan w snapshotcie E3):
- pojemność = `Refill Burst`, start pełny; odnowa liniowa `rate/min` (przeliczana
  z `world.getTotalWorldTime()` delty — modulo w obrębie jednego wymiaru, bez cross-dim);
- E1-HEAD: `tokeny <= 0` → `return 0` (obok warunku budżetu TOTAL);
- E4-RETURN: `tokeny -= liczba_spawnów` (może zejść poniżej zera — pojedynczy przebieg
  potrafi zespawnować więcej niż zostało tokenów; kolejne przebiegi stoją, aż odnowa
  wróci nad zero — właśnie to daje „ciszę po fali");
- brak wpisu w configu = mechanizm wyłączony dla tej pary dim×typ.

**Efekt gameplayowy (wymóg §2b):** po wybiciu ~20 mobów przy `12/min` okolica wraca do
pełnej populacji w ~2 min zamiast w 2–3 przebiegi spawnera; bezpośrednio po walce jest
realna cisza. Doomlike-spawnery w lochach działają bez zmian (nie idą przez naturalny
pipeline) — loch pozostaje gęsty, open-world oddycha.

### 4.3 E2 — `SpawnListFilterHandler` (Forge event, bez mixina)

```java
@SubscribeEvent(priority = EventPriority.LOWEST)
public void onPotentialSpawns(WorldEvent.PotentialSpawns event) {
    // dim nie-engine: tylko veto SRP w 150 (dziala nawet przy wylaczonym enginie,
    // dopoki Strip SRParasites=true); engine-dim: filtr budzetowy
    ...
    Iterator<Biome.SpawnListEntry> it = event.getList().iterator();
    while (it.hasNext()) {
        ResourceLocation id = SpawnEngineState.idOf(it.next().entityClass); // cache Class->RL
        if (id == null) continue;
        if (dim == 150 && strip && "srparasites".equals(id.getNamespace())) { it.remove(); continue; }
        if (!engineDim) continue;
        if (SpawnEngineState.overBudget(dim, id)) it.remove();  // namespace LUB pelne id
    }
    // lista pusta -> vanilla sam nic nie zespawnuje (getSpawnListEntryForTypeAt -> null)
}
```

- `idOf`: `EntityList.getKey(Class)` z cache'em `Map<Class, ResourceLocation>` — zero
  kosztu po rozgrzaniu.
- Event odpala się per pozycja-kandydat — dlatego MUSI być tani: dwa lookupy w mapach
  snapshotu. Zero liczenia w handlerze.
- `overBudget` dla `iceandfire` z definicji tylko per-typ (walidacja configu nie wpuści
  namespace'owego wpisu).

### 4.4 E3 — `SpawnEngineState` + `SpawnEngineTickHandler`

- `WorldTickEvent` (faza END, server): dla engine-dimów co 20 ticków przebieżka
  `world.loadedEntityList`: `EntityLivingBase` żywe → inkrement namespace/fullId/typ
  (typ przez `isCreatureType(type, true)` — ta sama semantyka co vanilla count).
  Snapshot podmieniany atomowo (immutable mapy; czytelnicy E1/E2 na starym snapshot
  do podmiany — EntityThreading czyta z workerów, więc **żadnych mutowalnych map
  współdzielonych**; wzorzec: buduj → publish przez volatile).
- Stan w `com.spege.srpwizcore.spawnengine` (NIE w mixin-pakiecie). Zero `new` w
  jakimkolwiek `<clinit>` klasy mixinowej (E1 nie ma pól — patrz 4.2).

### 4.5 Nowe pliki / zmiany (srpwizcore 1.6.0)

```
srpwizcore/src/main/java/com/spege/srpwizcore/
  mixins/MixinWorldEntitySpawner.java              (early.json, sekcja "mixins")
  spawnengine/SpawnEngine.java                     (gate + parsowanie configu + walidacja)
  spawnengine/SpawnEngineState.java                (snapshot licznikow, cache Class->RL)
  spawnengine/SpawnEngineTickHandler.java          (WorldTickEvent co 20 tickow)
  spawnengine/SpawnListFilterHandler.java          (PotentialSpawns, LOWEST)
  config/categories/SpawnEngineCategory.java
zmiany: SrpWizCoreConfig (+kategoria), SrpWizCore (rejestracja handlerow — TYLKO gdy
  enable, ale mixin w early.json aplikuje sie zawsze), mixins.srpwizcore.early.json,
  build.gradle/mcmod.info/VERSION (1.6.0)
```

## 5. Plan wdrożenia i pomiaru (fazy)

**F0 — diag (pół dnia):** build z E1 w trybie samego `Diag Logging` (budżety nieaktywne,
gate zwraca false). Zbiera i4/k/cap ground-truth w dim 150 → **rozstrzyga mechanizm
bypassu** (inflacja chunków vs pominięta komparacja vs undercount). Nie blokuje F1,
ale wynik wchodzi do tuningu TOTAL.
**F1 — E1+E2+E3 aktywne w dim 150,** budżety 1:1 z dim_mobcap, dim_mobcap ZOSTAJE.
Pomiar: (a) `logs/groovy.log` — odrzucenia dim_mobcap muszą spaść z ~82–387/s do ~0
  (wszystko, co dolatuje do joina, jest już w budżecie);
(b) `!census 150` — populacja per namespace jak przed zmianą (±szum) — **parity**;
(c) flare profil gameplay-saturated jak #4 (te same flagi/okno):
  `findChunksForSpawning` poddrzewo **39,1% → cel <5%**;
(d) inwarianty: `!census 150` zero srparasites; smoki I&F z gniazd żyją;
(e) **test oddechu (wymóg §2b):** user wybija grupę ~20 mobów → przez ≥60 s populacja
  wyraźnie poniżej capu (`!census 150` co 30 s pokazuje stopniowy wzrost ~rate/min,
  nie skokowy powrót); subiektywnie: po walce jest cisza.
**F2 — decyzje po pomiarze:** tuning TOTAL/interval; ewentualna redukcja wag InControl
potentialspawn (już niepotrzebnie niskich po A+D?); dim_mobcap zostaje jako pas
bezpieczeństwa (koszt ~0) — wygaszenie dopiero po tygodniu soaka bez odchyleń census.
**F3 (osobna decyzja, poza v1):** dim 111 — wymaga tabel z predykatem fazy SRP i analizy
interplay z natywnym spawnem SRP; v2 — migracja rosteru z groovy (maski, wagi, opts).

## 6. Ryzyka i mitygacje

| ryzyko | mitygacja |
|---|---|
| Cleanroom/binpatch zmienił bytecode `func_77192_a` (bypass!) — HEAD-inject i tak wejdzie, ale semantyka wnętrza nieznana | E1 nie zależy od wnętrza metody (HEAD+cancel); F0-diag zweryfikuje realne zachowanie; weryfikacja `Mixing`/`APPLY` w `cleanmix.log` (NIE debug.log!) |
| E2 psuje spawny w wymiarach nie-engine | handler poza engine-dims robi wyłącznie strip SRP w 150; wszystkie inne dimy nietknięte (early-return) |
| snapshot wyścigi z EntityThreading | immutable snapshot + volatile publish; brak mutacji współdzielonych map |
| InControl potentialspawn koliduje | my na LOWEST — filtrujemy PO InControl; InControl deny na CheckSpawn działa dalej (za nami w pipeline) |
| budżety źle sparsowane → cicho brak filtra | walidacja przy load: każdy odrzucony wpis = ERROR w logu z powodem; liczba aktywnych wpisów logowana INFO przy starcie |
| mixin cicho nie wszedł (`required:false`) | krok weryfikacji: `cleanmix.log` `APPLY ...MixinWorldEntitySpawner -> net.minecraft.world.WorldEntitySpawner` obowiązkowy przed pomiarami |

## 7. Decyzje usera (2026-07-27) — ZATWIERDZONE

1. ✅ TOTAL `150:MONSTER=170` na start.
2. ✅ `Hostile Pass Interval` start od **1** (czysty parity; throttle podnoszony dopiero
   po pomiarze F1).
3. ✅ F0-diag i F1 w jednym buildzie (diag za flagą).
4. ✅ **Wymóg gameplayowy §2b** (koniec nieskończonych hord) — dodany mechanizm E4
   (token bucket tempa odbudowy). Wartości startowe `12/min, burst 24` dla MONSTER to
   propozycja overseera — **tuning w F1/F2 wg feelingu usera** (to jest główna gałka
   „ile spokoju po walce": rate ↓ = dłuższa cisza).

⚠️ Uwaga do parity-testu po dodaniu E4: census-parity (F1b) mierzyć w steady-state
(bez wybijania) — E4 z definicji ZMIENIA dynamikę po walce, więc „populacja wraca
wolniej" to sukces, nie regresja.
