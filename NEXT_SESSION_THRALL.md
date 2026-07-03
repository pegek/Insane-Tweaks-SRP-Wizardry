# Prompt dla następnego agenta Claude — Thrall System (kontynuacja)

## Kontekst projektu

**Insane Tweaks SRP&Wizardry** — Minecraft 1.12.2 Forge mod (modid `insanetweaks`, group `com.spege.insanetweaks`).  
Katalog projektu: `E:\Isuth\modDev`  
Build: `./gradlew compileJava` (czyste, 0 błędów)  
Java 8 only — żadnych lambd w mixin, żadnych `var`, żadnych rekordów.

Mod integruje: Electroblob's Wizardry, Scape and Run Parasites, Spartan Weaponry, Ancient Spellcraft w system evolving-gear / parasitic battlemage. Thrall to stały, nieśmiertelny towarzysz utility — **nigdy agresywny**.

---

## System Thrall — stan po T3

### Pliki Thrall (wszystkie w `src/main/java/com/spege/insanetweaks/`)

```
entities/
  EntityThrallMinion.java         ← główna klasa (extends EntityCreature)
  ThrallMode.java                 ← enum: FOLLOW, STAY, WOODCUTTING, MINESHAFT, FARMING, PORTER
  ThrallChestHelper.java          ← stateless: scan IInventory TE, smartDeposit, grabItemFromChests
  ThrallMaterialHelper.java       ← stateless: isMatureCrop, findPlantableSeedSlot, isHoeItem, isBoneMeal
  ThrallSlotManager.java          ← persystencja slotów 1-3 w NBT gracza
  ThrallTorchSupply.java          ← tryResupply/countTorches
  inventory/
    ThrallInventory.java          ← IInventory 27-slot, addItemStackToInventory, putAllItemsToInventory
    ThrallInventoryContainer.java ← ContainerChest-style, Forge sync auto
  ai/
    ThrallAIFollowCaster.java     ← FOLLOW mode, pathfinding + teleport fallback
    ThrallAIGatherItems.java      ← zbieranie EntityItem z podłogi
    ThrallAIWoodcutting.java      ← ścinka drzew
    ThrallAIMineshaft.java        ← spiralny szyb → strip mine, NBT persistence
    ThrallAIFarming.java          ← zbiory/replant/bonemeal/oranie (FARMING mode)
    ThrallAIPorter.java           ← auto-stocker home↔owner (PORTER mode)
    ThrallAIWander.java           ← wander gdy idle

client/
  gui/GuiThrallControl.java       ← 9 przycisków: Follow/Stay/Woodcutting/Mineshaft/Farming/Porter/SetHome/Inventory/Dismiss
  model/entity/ModelThrallMinion.java
  renderer/entity/RenderThrallMinion.java

events/ThrallClientInteractionHandler.java  ← prawy klik → otwiera GuiThrallControl

network/PacketThrallCommand.java  ← ACTION_FOLLOW=0 STAY=1 DISMISS=3 SET_HOME=4 WOODCUTTING=5
                                     MINESHAFT=6 OPEN_INV=7 FARMING=8 PORTER=9

config/ModConfig.java → klasa Thrall:
  maxSlotsPerPlayer(1), enableMineshaftMode, enableWoodcuttingMode, enableFarmingMode,
  farmRadius(12), farmUseBoneMeal(true), enablePorterMode, porterIntervalSeconds(30),
  porterTeleportRange(96), mineshaftDepthMin(5), mineshaftStripLength(50),
  mineshaftBranchSpacing(3), passivePickupRange(2.5), followTeleportDistance(18.0)

  Tweaks.thrallWorkDurationHours(2)  ← 0=wyłączone, 1h = 72000 ticków
```

### initEntityAI() — kolejność tasks (z EntityThrallMinion.java:151)
```java
this.tasks.addTask(0, new EntityAISwimming(this));
this.tasks.addTask(1, new ThrallAIWoodcutting(this));
this.mineshaftAI = new ThrallAIMineshaft(this);
this.tasks.addTask(1, mineshaftAI);
this.tasks.addTask(1, new ThrallAIFarming(this));
this.tasks.addTask(1, new ThrallAIPorter(this));
this.tasks.addTask(3, new ThrallAIGatherItems(this));
this.tasks.addTask(4, new ThrallAIFollowCaster(this));
this.tasks.addTask(6, new ThrallAIWander(this));
this.tasks.addTask(7, new EntityAILookIdle(this));
```
Wszystkie AI tasks pracy używają **mutex bits = 3** (blokują ruch + look).

### onUpdate() — kluczowe zachowania (EntityThrallMinion.java:252)
1. SRP anti-assimilation tick
2. Jeśli `thrallInventory.isFull() && !returningHome && homePoint != null` → `startReturnHome()`
3. Jeśli `returningHome` → `tickReturnHome()` (smartDeposit + teleport z powrotem)
4. Jeśli owner offline → STAY mode
5. Work timer: `WOODCUTTING|MINESHAFT|FARMING|PORTER` → po `thrallWorkDurationHours` godzinach → return home lub FOLLOW
6. `updateHeldToolVisual()` — zmiana narzędzia w ręce przy zmianie mode
7. FOLLOW teleport co 20 ticków
8. Backup slot NBT co 60 ticków
9. Passive pickup co 5 ticków

### DataManager (klient↔serwer sync)
```
OWNER_UUID   STRING   UUID właściciela
STATUS_TEXT  STRING   tekst nameplate
MODE_ORDINAL VARINT   ThrallMode.ordinal()
HOME_POS     STRING   "x,y,z" lub ""
THRALL_SLOT  VARINT   slot 1-3 (0=niezasignowany)
```

### Wizualne narzędzie (kosmetyczne)
```java
WOODCUTTING → Iron Axe
MINESHAFT   → Iron Pickaxe
FARMING     → Iron Hoe
PORTER      → Ender Chest (Item.getItemFromBlock(Blocks.ENDER_CHEST))
```

---

## Znane błędy do naprawy (priorytet malejący)

### Bug C3 — NPE w ThrallAIPorter.pullFromOwner (KRYTYCZNY)
**Plik:** `entities/ai/ThrallAIPorter.java:226`  
**Problem:** `owner.inventoryContainer.detectAndSendChanges()` jest wywoływane bez sprawdzenia null.  
Gdy gracz ma otwarty inny kontener GUI (np. własny chest), `inventoryContainer` może być inny niż `playerInventory`, ale nie jest nullem — jednak w teorii może się zdarzyć edge case na serwerze modded.  
**Fix:** otocz null-checkiem:
```java
if (owner.inventoryContainer != null) {
    owner.inventoryContainer.detectAndSendChanges();
}
```

### Bug C4 — Race condition Porter vs auto-return (ŚREDNI)
**Plik:** `EntityThrallMinion.java:261` + `ThrallAIPorter.java:104`  
**Problem:** Gdy Porter teleportuje do ownera i bierze dużo itemów, inventory może się zapełnić. W następnym ticku `onUpdate()` widzi `isFull()` i wywołuje `startReturnHome()`, które nadpisuje mode na STAY i teleportuje. Porter jest w połowie cyklu i nie może go dokończyć.  
**Fix:** Dodać flagę `private boolean isPorterCycleActive` (lub sprawdzać getMode() == PORTER w warunku isFull):
```java
// EntityThrallMinion.onUpdate() — warunek full:
if (thrallInventory.isFull() && !returningHome && getHomePoint() != null
        && getMode() != ThrallMode.PORTER) {   // ← Porter zarządza się sam
    startReturnHome();
}
```

### Bug M4 — Porter bierze za dużo (WYSOKI — balans)
**Plik:** `ThrallAIPorter.java:209`  
**Problem:** Manifest to lista *typów* jakie są w chestach, nie *ile brakuje*. Porter bierze cały stack z gracza nawet gdy chest jest już prawie pełny. Gracz może stracić 64 żelaza gdy w cheście jest miejsce tylko na 5.  
**Fix:** Przed pullem policzyć wolne miejsce w chestach dla każdego typu i ograniczyć transfer:
```java
// Zamiast brać cały stack s, wziąć tylko min(s.getCount(), freeSpaceInChests(item)):
int free = countFreeSpaceForItem(home, s);
if (free <= 0) continue;
int toTake = Math.min(s.getCount(), free);
// ... shrink s o toTake, dodaj do inv
```
Potrzebujesz nowej metody `countFreeSpaceForItem(BlockPos home, ItemStack template)` która:
1. Skanuje chestyblisko home przez `ThrallChestHelper.findNearbyInventories`
2. Dla każdego chesta: zlicza obecny count danego itemstacka + sumuje wolne sloty pasujące do tego itemstacka

---

## Propozycje nowych feature'ów (do omówienia z userem)

### Feature F1 — Porter: tryb "tylko donosi, nie zabiera" vs "dwukierunkowy"
Obecny porter bierze od gracza i dostarcza do domu. Alternatywnie: brać z domu i dawać graczowi (np. dostarczanie jedzenia/arrow na wyprawę). Dodać config bool `porterFromPlayerToHome` (default true) lub enum `PorterDirection`.

### Feature F2 — Farming: Sugar Cane, Cactus, Melon, Pumpkin
Obecny scanner szuka bloków na BlockFarmland. Sugar Cane rośnie na piasku/ziemi, Cactus na piasku, Melony/Dynie mają osobny blok "stem" i oddzielny blok "owoc". Każde wymaga oddzielnej fazy skanowania w `tickSearching()`.

### Feature F3 — STAY mode: Thrall jako strażnik wejścia
Thrall stoi w miejscu (home point), obraca się twarzą do gracza gdy player jest blisko. Czysto kosmetyczne — nie walczy, tylko "pilnuje". Już częściowo działa przez istniejący STAY + LookIdle, ale można dodać lock na kierunek look do ownera gdy w zasięgu.

### Feature F4 — `/itweaks thrall status` sub-komenda
W `commands/CommandInsaneTweaks.java` dodać sub-komendę wypisującą dla każdego slotu gracza: slot#, mode, homePos, inventory count, statusText. Przydatne do debugowania.

---

## Niezmienne zasady designu (MUST NOT BREAK)

1. **Thrall jest nieśmiertelny** — `attackEntityFrom` zawsze false, `isEntityInvulnerable` zawsze true
2. **Thrall nigdy agresywny** — `setAttackTarget` jest no-op, brak combat AI
3. **Inventory: dokładnie 27 slotów** (jeden chest) — nie zmieniać SIZE w ThrallInventory
4. **Każdy teleport**: SMOKE_LARGE departure + arrival, `playTeleportSound()` × 2 (Enderman sound)
5. **PORTAL particles** zawsze na kliencie (onLivingUpdate — 2 cząsteczki per tick)
6. **Java 8** — no lambdas w mixin, no var, no records
7. **Mutex bits = 3** na wszystkich AI tasks pracy
8. **GUARD mode** — nie implementować, user explicitly excluded

---

## Workflow

```bash
# Kompilacja (wymagane po każdej zmianie .java):
./gradlew compileJava

# Oczekiwany wynik: BUILD SUCCESSFUL, 0 errors, ~12 warnings (pre-existujące)

# Pełny build z reobf:
./gradlew build
```

Przy dodawaniu nowego AI task:
1. Stwórz klasę w `entities/ai/`
2. Dodaj `import` w `EntityThrallMinion.java`
3. Dodaj `this.tasks.addTask(priorytet, new ThrallAIXxx(this))` w `initEntityAI()`
4. Jeśli nowy mode: dodaj do `ThrallMode.java`, `PacketThrallCommand.java`, `GuiThrallControl.java`, `ModConfig.Thrall`, `en_us.lang`
5. Sprawdź czy work-timer w `onUpdate()` i `setMode()` obejmuje nowy mode

---

## Sugestia priorytetu na następną sesję

**Zacząć od bugfixów** przed nowymi feature'ami:
1. Fix C3 (null-check) — trivialny, 1 linia
2. Fix C4 (Porter vs auto-return) — 1 linii zmiana warunku w `onUpdate`
3. Fix M4 (Porter bierze za dużo) — wymaga nowej metody `countFreeSpaceForItem` w `ThrallChestHelper`, modyfikacja `pullFromOwner`
4. Potem feature do omówienia z userem: F2 (Farming + Sugar Cane/Melon) lub F4 (status komenda)
