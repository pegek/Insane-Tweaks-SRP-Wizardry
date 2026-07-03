# Insane Tweaks — Thrall System T3 Changelog
> Stan na: 2026-04-24 | Gałąź: `main` | Build: czysty (`compileJava` OK)

---

## Spis treści
1. [Architektura ogólna (E1 Refactor)](#1-architektura-ogólna-e1-refactor)
2. [EntityThrallMinion — rdzeń](#2-entitythrallminion--rdzeń)
3. [ThrallMode — tryby pracy](#3-thrallmode--tryby-pracy)
4. [Helpery (stateless utils)](#4-helpery-stateless-utils)
5. [AI Tasks](#5-ai-tasks)
6. [Inventory](#6-inventory)
7. [GUI — GuiThrallControl](#7-gui--guithrallcontrol)
8. [Sieć — PacketThrallCommand](#8-sieć--packetthrallcommand)
9. [Konfiguracja — ModConfig.Thrall](#9-konfiguracja--modconfigthrall)
10. [Klient — model, renderer, interakcja](#10-klient--model-renderer-interakcja)
11. [Lang — en_us.lang](#11-lang--en_uslang)
12. [Znane ograniczenia i candydaty do dalszej pracy](#12-znane-ograniczenia-i-candydaty-do-dalszej-pracy)

---

## 1. Architektura ogólna (E1 Refactor)

**Problem przed refaktorem:** `EntityThrallMinion` był god-objectem (~1500 LOC) łączącym AI, logikę chestów, torchów, materiałów i persystencję NBT.

**Rozwiązanie:** rozbicie na stateless helpery:

| Klasa | Odpowiedzialność |
|---|---|
| `ThrallChestHelper` | Skanowanie `IInventory` TE, smartDeposit, grabItemFromChests |
| `ThrallMaterialHelper` | isMatureCrop, isBoneMeal, isHoeItem, findPlantableSeedSlot |
| `ThrallTorchSupply` | tryResupply, countTorches |
| `ThrallSlotManager` | Persystencja slotów 1–3 w NBT gracza, clearSlotUUID |
| `ThrallInventory` | Implementacja IInventory (27 slotów, isFull, addItemStackToInventory, putAllItemsToInventory) |

`EntityThrallMinion` deleguje do tych helperów; sam trzyma tylko DataManager i stan per-entita.

---

## 2. EntityThrallMinion — rdzeń

**Plik:** `entities/EntityThrallMinion.java`

### Nieśmiertelność i brak agresji
```java
attackEntityFrom()      → zawsze false
isEntityInvulnerable()  → zawsze true (void, /kill, magia — wszystko)
setAttackTarget()       → no-op
canDespawn()            → false
```

### DataManager (synchronizowane klient↔serwer)
| Klucz | Typ | Opis |
|---|---|---|
| `OWNER_UUID` | `STRING` | UUID właściciela |
| `STATUS_TEXT` | `STRING` | Tekst nameplate |
| `MODE_ORDINAL` | `VARINT` | Aktualny tryb (`ThrallMode.ordinal()`) |
| `HOME_POS` | `STRING` | `"x,y,z"` lub `""` |
| `THRALL_SLOT` | `VARINT` | Numer slotu 1–3 (0 = niezasignowany) |

### Cykl onUpdate (serwer)
1. SRP anti-assimilation tick
2. Jeśli inventory full → `startReturnHome()`
3. Jeśli `returningHome` → `tickReturnHome()` (teleport + smartDeposit + powrót do pracy)
4. Jeśli owner offline → wymuszenie `STAY`
5. Work timer — po skonfigurowanej liczbie godzin → powrót do domu lub `FOLLOW`
6. `updateHeldToolVisual()` — zmiana wizualnego narzędzia gdy zmienia się mode
7. `tickFollowTeleport()` co 20 ticków w trybie FOLLOW
8. Backup slotu do NBT gracza co 60 ticków
9. Passive item pickup co 5 ticków

### Trzymanie narzędzia (kosmetyczne, nie dropowane)
| Mode | Item w ręce |
|---|---|
| WOODCUTTING | Iron Axe |
| MINESHAFT | Iron Pickaxe |
| FARMING | Iron Hoe |
| PORTER | Ender Chest |
| inne | empty |

### Work timer
Dotyczy: `WOODCUTTING`, `MINESHAFT`, `FARMING`, `PORTER`.  
Czas z `ModConfig.tweaks.thrallWorkDurationHours` (0 = wyłączony).  
Po upłynięciu → teleport do home lub powrót do FOLLOW.

### NBT — persystowane pola
`ThrallOwnerUUID`, `ThrallMode`, `ThrallStatus`, `ThrallHome`, `ThrallInventory`, `ThrallReturning`, `ThrallResumePos`, `ThrallResumeMode`, `ThrallWorkStart`, `ThrallSlot`, `ThrallMineshaft`

---

## 3. ThrallMode — tryby pracy

**Plik:** `entities/ThrallMode.java`

```
FOLLOW      – podąża za właścicielem, teleportuje gdy za daleko
STAY        – stoi w miejscu, brak nawigacji
WOODCUTTING – ścina drzewa w promieniu od thralla
MINESHAFT   – kopie spiralny szyb → strip mine
FARMING     – zbiory, replant, bonemeal, oranie
PORTER      – auto-stocker między właścicielem a chestami home
```

---

## 4. Helpery (stateless utils)

### ThrallChestHelper
- **`findNearbyInventories(world, center, hRange, vRange)`** — jeden przebieg przez `world.loadedTileEntityList` (snapshotowany), bez O(n²) getTileEntity
- **`smartDeposit(thrall, center)`** — sortuje chestyby match score (ile typów itemów już mają), zostawia pochodnie
- **`grabItemFromChests(thrall, center, item, needed)`** — pobiera konkretny item z chestów (torch resupply, porter)
- **`getMatchScore(chest, thrallInv)`** — heurystyka pasowania

### ThrallMaterialHelper
- **`isMatureCrop(world, pos, state)`** — sprawdza `BlockCrops.isMaxAge` lub `IGrowable.canGrow(false)` 
- **`findPlantableSeedSlot(inv, world, pos, cropBlock)`** — vanilla fast-path, potem `IPlantable` dla modowanych upraw
- **`isHoeItem(stack)`** — `instanceof ItemHoe`
- **`isBoneMeal(stack)`** — DYE damage 15

### ThrallTorchSupply
- **`tryResupply(thrall, targetCount)`** — pobiera pochodnie z chestów wokół home żeby utrzymać target
- **`countTorches(inv)`**

### ThrallSlotManager
- **`saveSlot(player, thrall)`** — zapisuje stan slotu (UUID, homePos, inventory backup) do NBT gracza
- **`clearSlotUUID(player, slot)`** — kasuje UUID przy dismiss (zachowuje backup inventory do re-summon)
- **`restoreFromSlot(player, slot, world)`** — re-summon z zapisanego stanu

---

## 5. AI Tasks

Wszystkie AI tasks używają **mutex bits = 3** (blokują ruch i look). Każdy task aktywuje się wyłącznie w swoim trybie.

### ThrallAIFollowCaster
- Aktywny w FOLLOW mode
- Pathfinding do właściciela, po 3 failurach pathfindingu + za daleko → teleport

### ThrallAIGatherItems
- Aktywny zawsze (poza trybami pracy mutex)
- Chodzi do leżących EntityItem w zasięgu i zbiera do inv

### ThrallAIWoodcutting
- Skanuje drzewa (log blocks) wokół thralla
- Ścina od najniższego logu w górę
- Replantuje sadzonkę jeśli ma w inv
- Zabezpieczenie: nie ścina jeśli brak sapling lub inv za pełne

### ThrallAIMineshaft
- Stany: `SPIRAL_DOWN` → `STRIP_MINE` → `BRANCHING`
- Spiralny szyb 2×1 aż do `ModConfig.thrall.mineshaftDepthMin`
- Strip mine o długości `mineshaftStripLength`
- Branche co `mineshaftBranchSpacing` bloków
- Automatyczny resupply pochodni (`tryResupplyTorches`)
- Zabezpieczenie płynów (cobblestone z inv)
- NBT persistence stanu (pozycja w shaft przeżywa restart serwera)

### ThrallAIFarming *(D1 — DONE)*
Trzy fazy w SEARCHING:

**Faza 1 — Harvest:**
- Skanuje `farmRadius` wokół home point
- Szuka dojrzałych upraw na BlockFarmland (`IGrowable` + `isMatureCrop`)
- Po dotarciu: drops do inv, replant przez `findPlantableSeedSlot` (vanilla + `IPlantable`)
- Blok zbijany z animacją (sendBlockBreakProgress)

**Faza 2 — BoneMeal** *(jeśli `farmUseBoneMeal = true` i thrall ma bonemeal)*:
- Szuka niedojrzałych upraw
- Używa `ItemDye.applyBonemeal` → działa z każdym `IGrowable`
- Spawns event 2005 (particle burst)

**Faza 3 — Tilling** *(jeśli thrall ma motykę i `tillsThisShift < 16`)*:
- Szuka DIRT/GRASS z powietrzem nad spodem i wodą w promieniu 4 bloków
- Zamienia na FARMLAND
- Uszkadza motykę (w razie złamania — dźwięk item break + clear slotu)

### ThrallAIPorter *(D2 — DONE)*
**Cykl co `porterIntervalSeconds`:**

1. SmartDeposit jeśli coś w inv
2. Buduje **manifest** — unikalne `(item, meta, NBT)` z chestów w 15 blokach od home
3. Sprawdza właściciela: online? ta sama dimensja? `distanceSq(home) ≤ porterTeleportRange²`?
4. Pre-scan: czy właściciel ma cokolwiek pasującego do manifestu?
5. Teleport do właściciela (SMOKE_LARGE + Enderman sound × 2)
6. Pull itemów ze slotów 9–35 inventory gracza (main inv, **skip hotbar 0–8, skip armor/offhand**)
7. Teleport z powrotem do home (SMOKE_LARGE + Enderman sound × 2)
8. SmartDeposit
9. Status: `"Stocked N"` / `"Standing by..."` / `"Awaiting owner"` / `"Owner away"` / `"No manifest"` / `"Full"`

**Limity:** max 16 stosów per cykl, manifest match przez `areItemsEqual + areItemStackTagsEqual` (NBT-aware).

---

## 6. Inventory

**Plik:** `entities/inventory/ThrallInventory.java`

- 27 slotów (jeden chest), `ItemStack.EMPTY` jako sentinel
- `addItemStackToInventory` — merge z istniejącymi stackami, potem empty sloty
- `putAllItemsToInventory` — transfer do IInventory z obsługą double-chest (`adjacentChestX/Z`)
- `isFull` — każdy slot zajęty do maxStackSize
- `dropAllItems` — scatter world items przy dismiss

**GUI kontener:** `entities/inventory/ThrallInventoryContainer.java`
- `ContainerChest`-style, Forge sync server→client automatyczny
- Otwierany przez `IGuiHandler.getServerGuiElement` z ID `GUI_ID_THRALL_INV = 1`

---

## 7. GUI — GuiThrallControl

**Plik:** `client/gui/GuiThrallControl.java`

9 przycisków (160×20px, gap 4px):
```
Follow        (id=0)
Stay          (id=1)
Woodcutting   (id=2)
Create Mineshaft (id=3)
Farming       (id=8)  ← nowy
Porter        (id=9)  ← nowy
Set Home      (id=4)
Inventory     (id=6)
[czerwony] Dismiss (id=7)
```
- Tytuł: `cy - 117`
- Inventory info `(X/27 filled)`: `cy + 130`
- Home coords: `cy + 142`
- Auto-zamknięcie gdy thrall zginie lub zostanie despawniony

---

## 8. Sieć — PacketThrallCommand

**Plik:** `network/PacketThrallCommand.java`

| Stała | Wartość | Akcja |
|---|---|---|
| ACTION_FOLLOW | 0 | FOLLOW mode |
| ACTION_STAY | 1 | STAY mode |
| ACTION_DROP_ITEMS | 2 | (zarezerwowane) |
| ACTION_DISMISS | 3 | spawn smoke, backup slot, drop inv, setDead |
| ACTION_SET_HOME | 4 | setHomePoint + smartDeposit |
| ACTION_WOODCUTTING | 5 | WOODCUTTING mode |
| ACTION_MINESHAFT | 6 | resetMineshaftAI + MINESHAFT mode |
| ACTION_OPEN_INV | 7 | openGui → IGuiHandler |
| ACTION_FARMING | 8 | FARMING mode (sprawdza config) |
| ACTION_PORTER | 9 | PORTER mode (sprawdza config) |

Handler zawsze weryfikuje `thrall.canPlayerCommand(player)` przed wykonaniem.

---

## 9. Konfiguracja — ModConfig.Thrall

**Plik:** `config/ModConfig.java` → klasa `Thrall`

| Klucz | Domyślnie | Zakres | Opis |
|---|---|---|---|
| `maxSlotsPerPlayer` | 1 | 1–5 | Max równoległych Thralli |
| `enableMineshaftMode` | true | — | Master toggle Mineshaft |
| `enableWoodcuttingMode` | true | — | Master toggle Woodcutting |
| `enableFarmingMode` | true | — | Master toggle Farming |
| `farmRadius` | 12 | 4–32 | Promień skanowania upraw |
| `farmUseBoneMeal` | true | — | Użycie bonemeal na niedojrzałe uprawy |
| `enablePorterMode` | true | — | Master toggle Porter |
| `porterIntervalSeconds` | 30 | 5–300 | Interwał cyklu portera (sek.) |
| `porterTeleportRange` | 96 | 16–256 | Max zasięg teleportu do ownera (bloki) |
| `mineshaftDepthMin` | 5 | 1–60 | Min Y szybu |
| `mineshaftStripLength` | 50 | 8–200 | Długość tunelu strip-mine |
| `mineshaftBranchSpacing` | 3 | 2–8 | Co ile bloków branch |
| `passivePickupRange` | 2.5 | 1–8 | Zasięg biernego zbierania itemów |
| `followTeleportDistance` | 18.0 | 6–64 | Przy jakiej odległości teleport w FOLLOW |

Osobno w `Tweaks`:
| `thrallWorkDurationHours` | 2 | 0–24 | Czas pracy przed powrotem do home (0 = bez limitu) |

---

## 10. Klient — model, renderer, interakcja

| Plik | Opis |
|---|---|
| `client/model/entity/ModelThrallMinion.java` | Model humanoidalny |
| `client/renderer/entity/RenderThrallMinion.java` | Renderer z nameplate |
| `events/ThrallClientInteractionHandler.java` | Klik prawym na thralla → otwiera GuiThrallControl |

**Cząsteczki (klient, `onLivingUpdate`):**  
Dwa PORTAL particles per tick wokół ciała — efekt Endermana zawsze aktywny.

**Dźwięki teleportu (`playTeleportSound`):**  
`ENTITY_ENDERMEN_TELEPORT`, `SoundCategory.NEUTRAL`, pitch ±10% losowo.  
Wywoływany 2× na każdym teleporcie (departure + arrival).

---

## 11. Lang — en_us.lang

Dodane klucze Thralla (wybór kluczowych):
```
gui.insanetweaks.thrall.title
gui.insanetweaks.thrall.action.follow / stay / woodcutting / mineshaft / farming / porter
gui.insanetweaks.thrall.action.set_home / inventory / dismiss
gui.insanetweaks.thrall.mode.follow / stay / woodcutting / mineshaft / farming / porter
gui.insanetweaks.thrall.mode.disabled
gui.insanetweaks.thrall.inventory          (X/27 filled)
gui.insanetweaks.thrall.home.none / home.coords
gui.insanetweaks.thrall.action.dismiss.done
gui.insanetweaks.thrall.action.set_home.done
```

---

## 12. Znane ograniczenia i candydaty do dalszej pracy

### Krytyczne / mogą powodować bugi w grze

| # | Problem | Gdzie | Sugerowane rozwiązanie |
|---|---|---|---|
| C1 | **Porter nie invaliduje manifestu** gdy chest jest pusty między cyklami | `ThrallAIPorter.buildManifest` | Brak problemu funkcjonalnego, ale może teleportować na próżno — dodać early-exit jeśli po pull nic nie wzięto |
| C2 | **Farming: replant używa `block.getDefaultState()`** — modowane uprawy mogą mieć wiek w defaultState = 0, OK, ale niektóre mody mogą wymagać konkretnego IPlantable.getPlant() zamiast defaultState | `ThrallAIFarming.finishHarvest` | Dla vanilla i większości modów działa; do zbadania gdy pojawi się bug z konkretnym modem |
| C3 | **ThrallAIPorter: `owner.inventoryContainer.detectAndSendChanges()`** — wywołane ze świata serwera, poprawne, ale InventoryContainer może być null jeśli gracz ma otwarty inny kontener | `ThrallAIPorter.pullFromOwner` | Dodać null-check `if (owner.inventoryContainer != null)` |
| C4 | **startReturnHome vs Porter** — gdy Porter teleportuje i inv jest full (bo wziął dużo), `onUpdate` może wywołać dodatkowy `startReturnHome()` zanim Porter sam zdeponuje | `EntityThrallMinion.onUpdate` | Porter wywołuje `smartDeposit` sam; rozważyć flagę `isPorterCycleActive` żeby blokować auto-return |

### Ulepszenia mechaniczne

| # | Pomysł | Priorytet |
|---|---|---|
| M1 | **Farming: obsługa Sugar Cane / Cactus / Melon / Pumpkin** — nie rosną na Farmland, obecny skaner je pomija | Średni |
| M2 | **Farming: nieograniczone tilling per shift** — obecny limit 16 może być za mały dla dużych farm | Niski |
| M3 | **Porter: konfigurowalna lista wykluczeń** — gracz mógłby zaznaczyć sloty "nie bierz stąd" | Średni |
| M4 | **Porter: częściowy transfer** — zamiast brać whole stack, brać tylko tyle ile brakuje w chestach (compare manifest quantity vs chest quantity) | Wysoki — obecna logika może wyciągać za dużo |
| M5 | **Mineshaft: obsługa nether/end rudy** — obecny materiał-helper może nie rozpoznawać niektórych bloków modowanych rud | Niski |
| M6 | **Multi-slot UX** — przy maxSlotsPerPlayer > 1 brak UI do wyboru thralla; klik otwiera GUI pierwszego napotkanego | Wysoki jeśli multi-slot będzie używany |

### UX / jakość życia

| # | Pomysł |
|---|---|
| U1 | Nameplate z ikoną trybu (♦ FARM, ⛏ MINE, 🪵 WOOD, 📦 PORTER) — obecnie tylko tekst statusu |
| U2 | Dźwięk powiadomienia gdy Thrall wraca z pełnym inventory (zamiast cichego teleportu) |
| U3 | `/itweaks thrall status` — sub-komenda wyświetlająca stan wszystkich aktywnych Thralli gracza |
| U4 | Cooldown na dismiss (zapobiega przypadkowemu zamknięciu GUI i kliknięciu Dismiss) |

### Techniczne do posprzątania

| # | Co |
|---|---|
| T1 | `ACTION_DROP_ITEMS = 2` w PacketThrallCommand jest zarezerwowane ale nigdy nie używane — albo usunąć albo zaimplementować |
| T2 | `GuiThrallControl.drawScreen` czyta inventory thralla po stronie klienta — działa bo inventory nie jest synced przez ContainerChest gdy GUI jest zamknięte; bezpieczne ale niesymetryczne z innymi GUI modów |
| T3 | Sounds JSON dla `thrall/` — sprawdzić czy ścieżki sound events są zarejestrowane w `sounds.json` |

---

*Raport wygenerowany 2026-04-24. Compile status: `BUILD SUCCESSFUL` (0 errors, 12 warnings — wszystkie pre-existujące).*
