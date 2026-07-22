# Sanktuarium / Kopuła ochronna — design spec

**Data:** 2026-07-19. **Projekt:** InsaneTweaks #2 (`notes/insanetweaks_projects.md`).
**Podejście:** C (fazowe — event-veto spawnu + jeden mixin w v1, reszta w v2).

## Context

Paczka DEv 1.2 ma centralną pętlę „wyścigu z czasem": faza wzrostu → **Upadek Overworldu**
(wszystkie suwaki difficulty + faza SRP na MAX) → quest oczyszczenia / ucieczka do
wymiaru-schronienia (`modpack_vision_roadmap_2026-07-18.md` w notes paczki). Sanktuarium to
**overworldowy odpowiednik wymiaru-schronienia**: blok/artefakt tworzący strefę, w której gracz
utrzymuje **bezpieczną enklawę** mimo opanowania świata przez pasożyty. Chroni **teren + naturalny
spawn**, NIE walczy z istniejącymi bytami i NIE blokuje vanilla mobów — to azyl/baza, nie broń.

Feature stoi na tym, że da się wetować spawn/infestację SRParasites (modid `srparasites`, 1.10.7)
w regionie bez dotykania „życia" pasożytów już w środku. Feasibility potwierdzona (patrz niżej).

## Decyzje projektowe (ustalone z userem 2026-07-19)

| Decyzja | Wybór |
|---|---|
| Model aktywacji | **Hybryda**: piramida (bazowy tier zasięgu, jak beacon) + item-upgrade'y w GUI (kształt/+promień/jakość) |
| Kształt strefy | **Cylinder pełnej wysokości** (promień R, `dx²+dz²≤R²`, bedrock→niebo). „Kopuła" = nazwa/wizual |
| Zakres działania | **Prevent + powolny cleanse terenu** (cofanie infestacji → odzyskiwanie overworldu) |
| Upkeep | **Skalujący, z podziałem**: TARCZA tania/pasywna (bez paliwa), CLEANSE pali paliwo |
| Pusty upkeep | **Degradacja**: cleanse gaśnie, tarcza zostaje |
| Wymiary | **Overworld + configurowalna blacklista** (wyłączone w wymiarach-pasożytach: `111`, wymiar #2) |
| Podejście implementacyjne | **C — fazowo**: v1 = event-veto spawnu + 1 mixin (konwersja) + cleanse; v2 = mixiny AI-growth + meteor |

## Feasibility (z eksploracji kodu SRP + modułu SRP-compat)

Wszystkie veto mają czyste choke pointy; żaden nie dotyka liveness pasożytów:
- **Naturalny spawn SRP** — SRP-owy `SRPSpawning$DimensionHandler.onSpawn` to handler Forge
  `LivingSpawnEvent.CheckSpawn`. Możemy wetować **własnym handlerem** (LOWEST priority,
  `setResult(DENY)`) — **bez mixina**.
- **Konwersja/infestacja bloków** (cysty, residue, beckon spread) — jeden statyczny util
  `com.dhanantry.scapeandrunparasites.util.convert.BeckonBlockInfestation.beckonInfestation(World,BlockPos,Random,int,boolean)`,
  wołany z tick-metod infested bloków. NIE jest eventem → **wymaga mixina** (`@Inject HEAD cancellable`).
- **Wzrost struktur napędzany encjami** (v2) — AI: `EntityAINexusGrow`, `EntityAIBlockInfest`,
  `EntityAIBlockResidue`, `EntityAINexusNest`, `EntityPBeckon.generateStructure` (veto na
  `func_75250_a`/`func_75246_d`).
- **Meteor** (v2) — `world.gen.feature.WorldGenParasiteMeteorCrash.func_180709_b` (generate).

**Brak istniejącej infry:** mod nie ma dziś ŻADNEGO custom Blocka, TileEntity ani własnego
WorldSavedData. Do skopiowania: `SrpCompatCategory` (config zasięgu + parser `String[]`),
`SrpInfestationHelper.infestNearbyBlocks` (skan promienia — odwrócić dla cleanse), SRP-owy
`SRPWorldData extends WorldSavedData` (strukturalny wzór rejestru regionów). Istniejący, zawsze
włączony `IndestructibleDropHandler` i moduł `mixins.insanetweaks.srp.json` (queued gdy SRP obecny).

## Architektura — komponenty

Nowy pakiet `com.spege.insanetweaks.sanctuary` (+ `blocks/`, `tile/`, `gui/`). Flaga
`ModConfig.modules.enableSanctuary` (RequiresMcRestart). Cały moduł auto-disable gdy
`!Loader.isModLoaded(SRP_MODID)`.

### v1 (ten spec)

1. **`BlockSanctuaryCore`** + nowe **`init/ModBlocks.java`** — pierwsza szyna rejestracji bloków w
   modzie: `@Mod.EventBusSubscriber` + `RegistryEvent.Register<Block>`, `Register<Item>` (ItemBlock),
   rejestracja modelu w `ModelRegistryEvent`. Wzorzec jak `ModItems`/`ModPotions`.
2. **`TileEntitySanctuaryCore`** (`GameRegistry.registerTileEntity`) — stan: slot(y) paliwa, slot(y)
   upgrade, `tier` (ze skanu piramidy), `effectiveRadius`, flaga `cleanseEnabled`, `cleanseStalled`.
   Serwer-tick: rewalidacja piramidy (co `pyramidRevalidateInterval`), zużycie paliwa, driver cleanse,
   sync do `SanctuaryWorldData`.
3. **GUI** `GuiSanctuaryCore` + `ContainerSanctuaryCore` — slot paliwa, sloty upgrade, odczyt
   tier/promień, przełącznik cleanse. Wpięte w istniejący `IGuiHandler` w `InsaneTweaksMod`
   (**nowe `GUI_ID_SANCTUARY = 2`**; jedyne dotąd to `GUI_ID_THRALL_INV = 1`).
4. **`SanctuaryWorldData extends WorldSavedData`** — jedno źródło prawdy dla haków. Per-świat
   (MapStorage) lista `{BlockPos rdzeń, int promień, flagi}`. `get(World)` (lazy create),
   `add/update/remove(pos,...)`, `isInsideAnySanctuary(x,y,z)` (cylinder). Haki pytają **tani rejestr,
   bez ładowania TE** → działa też przy rozładowanym chunku rdzenia. Wzór: SRP `SRPWorldData`.
5. **`SanctuarySpawnVetoHandler`** (event, bez mixina) — `@SubscribeEvent(priority = LOWEST)` na
   `LivingSpawnEvent.CheckSpawn`: `entity instanceof EntityParasiteBase` **i**
   `SanctuaryWorldData.get(world).isInsideAnySanctuary(x,y,z)` → `event.setResult(DENY)`. Wetuje
   TYLKO SRP (vanilla moby spawnią dalej). Rejestrowany w `init` pod `enableSanctuary` +
   `isModLoaded(SRP_MODID)`.
6. **`MixinBeckonBlockInfestation`** (jedyny mixin v1) — `@Inject(method="beckonInfestation",
   at=@At("HEAD"), cancellable, remap=false)`: rozwiąż `(World,BlockPos)` → w strefie → `ci.cancel()`.
   Gated `ModConfig.sanctuary.vetoBlockInfestation`. Dodany do `mixins.insanetweaks.srp.json`.
   **Descriptor potwierdzić `javap -p -c` przed implementacją** (gotcha CLAUDE.md: SRG-nazwy,
   `remap=false`, atrybucja invoke do metody).
7. **Cleanse driver** (`SanctuaryCleanseHelper` wołany z TE tick) — gdy `cleanseEnabled` + paliwo>0:
   rolling cursor po wolumenie cylindra, `cleanseBlocksPerTick` bloków/tick, cofa infested→naturalny +
   gasi residue/cysty (reuse `SrpPurificationHelper`; odwrócona geometria `SrpInfestationHelper`).
   Pali paliwo ∝ faktycznym konwersjom. Brak infested → idle, **paliwa nie pali**.
8. **`SanctuaryCategory`** (config, niżej) + opcjonalny **particle border** (klient, toggle).

### v2 (osobny spec, później)

Mixiny veto na AI-growth (`NexusGrow`/`BlockInfest`/`BlockResidue`/`NexusNest`/
`EntityPBeckon.generateStructure`) + meteor worldgen. Dokładane, jeśli playtest pokaże przecieki
(istniejące architekty/nexusy w środku dalej rosnące, trafienie meteoru w bazę).

### Podział koszt/moc

**TARCZA** (event-veto spawnu #5 + mixin-veto konwersji #6) = działa zawsze gdy strefa aktywna,
bez paliwa. **CLEANSE** (#7) = pali paliwo; pusty → gaśnie, tarcza zostaje.

## Przepływ runtime

**Skan piramidy → tier → promień** (co `pyramidRevalidateInterval` w TE): rdzeń liczy pełne warstwy
piramidy z `pyramidBlocks` → `tier` 1–4 → bazowy `tierRadii[tier]`; upgrade'y modyfikują
(`+upgradeRadiusBonus`, `upgradeCleanseSpeed`) → `effectiveRadius`. Piramida niekompletna → tier
spada → strefa kurczy się/gaśnie.

**Cykl życia rejestru:** aktywacja (tier≥1 → wpis + `markDirty()`), zmiana (nadpisanie wpisu),
dezaktywacja/rozbicie (`invalidate()`/`breakBlock` → usuń wpis), chunk rozładowany (wpis **zostaje**
w WorldData → tarcza trwa; cleanse pauzuje bo wymaga tickującego TE).

**Pętla ticku TE (serwer):** (1) rewalidacja piramidy → sync rejestru; (2) cleanse jeśli
enabled+paliwo; (3) degradacja: paliwo==0 → `cleanseStalled=true`, cleanse stop, tarcza/rejestr bez
zmian, dotankowanie wznawia.

**Ścieżka veto:** spawn → `CheckSpawn` (LOWEST) → `SanctuaryWorldData.get(world).isInsideAnySanctuary`
→ DENY. konwersja → mixin HEAD → ta sama metoda → cancel. Dim-blacklist: WorldData dla wymiaru na
blackliście zwraca zawsze `false` (albo TE tam nie synchuje).

**Wydajność/MP:** rejestr to kilka–kilkanaście wpisów; `isInsideAnySanctuary` = pętla `dx²+dz²≤R²`,
tania przy wysokiej częstości `CheckSpawn`. Cleanse rozłożony (K bloków/tick) — bez lag-spike'ów.

## Config — `SanctuaryCategory`

- `tierRadii: int[] = {16,32,48,64}` — bazowy promień per tier.
- `pyramidBlocks: String[]` — dozwolone bloki warstw piramidy.
- `fuelItems: String[]` (`registry=wartość`, parser jak `perDimMobCapMultipliers`).
- `cleanseBlocksPerTick`, `cleanseFuelPerConversion`, `pyramidRevalidateInterval`.
- `upgradeRadiusBonus`, `upgradeCleanseSpeed`.
- `dimensionBlacklist: String[]` (domyślnie wymiar-pasożytów + `111`).
- `vetoNaturalSpawn: bool` (live), `vetoBlockInfestation: bool` (**RequiresMcRestart** — brama mixina).
- `particleBorder: bool` (klient), `cleanseEnabledByDefault: bool`.

## Edge-case'y / obsługa błędów

- **SRP niezaładowane** → moduł auto-disable (wzorzec Bauble Fruits→Baubles); blok może istnieć, veto
  no-op + log `[InsaneTweaks]`.
- **Mixin-apply** — `srp.json` `defaultRequire:1`, zła sygnatura = crash startu → `javap` przed impl.
- **Nakładające się kopuły** → unia stref; paliwo/cleanse niezależne per rdzeń.
- **Rdzeń bez piramidy** → tier 0 → brak wpisu → inert; GUI: „zbuduj piramidę".
- **Cleanse — mapowanie infested→oryginał** → reuse `SrpPurificationHelper`; fallback: usuń
  residue/cysty + zamień znane infested-warianty na kamień/ziemię/trawę. *Może wymagać własnej tabeli
  konwersji — do potwierdzenia przy implementacji.*
- **Zakres jawnie** → blokuje SRP spawn+infestację; NIE vanilla moby; NIE dmg pasożytom w środku
  (chodzą i mogą atakować gracza — to nie tarcza bojowa).

## Weryfikacja (build → deploy DEv 1.2 → kreatyw)

1. Postaw rdzeń, buduj tiery → GUI pokazuje tier/promień; upgrade → promień rośnie.
2. W strefie: wymuś infestację / postaw beckon → brak nowych infested bloków (mixin cancel), brak
   naturalnego spawnu SRP (CheckSpawn DENY), vanilla moby spawnią normalnie.
3. Paliwo + cleanse ON → infested cofa się, residue znika, paliwo maleje; opróżnij → cleanse stop,
   tarcza dalej blokuje.
4. Odejdź (rozładuj chunk) → tarcza trzyma; wróć → cleanse wznawia.
5. Dim `111` → kopuła inert (blacklist).
- Diag: markery `[InsaneTweaks]` + istniejące `[SRP EP DEBUG]` / `logs/debug.log`.

## Poza zakresem v1

- v2: mixiny AI-growth + meteor worldgen.
- Receptury bloku/upgrade'ów (JSON vs GroovyScript) — do ustalenia przy implementacji.
- Powiązanie z questem oczyszczenia / Stage VII Beckon (roadmapa #6) — osobny projekt.
