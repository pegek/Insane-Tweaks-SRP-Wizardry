# Auto Lock Picker — design (2026-07-26)

Nowy przedmiot + enchant w mod content `insanetweaks`, integrujący się z modem
**Locks 1.12.2-3.0.0** (`melonslise.locks`, modid `locks`).

## Cel

Przedmiot, który zdejmuje zamek Locks ze skrzyni bez przechodzenia minigry z pinami —
kosztem czasu kanałowania i durability, obu skalowanych trudnością zamka. Do tego własny
enchant trzech poziomów skracający kanałowanie.

## Zakres

W zakresie:
- item `insanetweaks:auto_lock_picker` — kanałowany (hold RMB), zużywa durability
- enchant `insanetweaks:swift_picking` I–III — −15 % czasu kanałowania na poziom
- respektowanie wszystkich trzech enchantów Locks na zamku (Complexity / Sturdy / Shocking)
- pasek postępu na HUD
- konfiguracja w `insanetweaks.cfg`

Świadomie poza zakresem (decyzje użytkownika, 2026-07-26):
- **rozróżnienie zamków z worldgenu od zamków postawionych przez gracza** — picker działa na
  każdym zamku. Funkcja eksperymentalna; Locks nie zapisuje pochodzenia `Lockable`, więc
  ochrona skrzyń gracza wymagałaby własnego rejestru ID w `WorldSavedData` (whitelist worldgenu
  przez mixin w `LockableWorldGenHandler.tryGeneratingLocks`). Do rozważenia w kolejnej iteracji.
- **recepta** — przedmiot dostępny wyłącznie z kreatywki i `/give`.

## Dane wejściowe z moda Locks

`LockItem(length, enchantmentValue, resistance)` — piny sterują wszystkim, co skalujemy:

| zamek | piny | enchantability | resistance |
|---|---|---|---|
| wood | 5 | 15 | 4.0 |
| gold | 6 | 22 | 6.0 |
| iron | 7 | 14 | 12.0 |
| steel | 9 | 12 | 20.0 |
| diamond | 11 | 10 | 100.0 |

Wytrychy Locks (`LockPickItem.strength`): wood 0.2 · gold 0.25 · iron 0.35 · steel 0.7 · diamond 0.85.

Reguły przejęte 1:1 z Locks:
- **Complexity** (max III): `canPick` ⇔ `strength > complexity × 0.25`.
- **Sturdy** (max III): dzielnik siły `0.75 + 0.5 × lvl`.
- **Shocking** (max V): obrażenia `LocksDamageSources.SHOCK` przy nieudanej próbie.

## Architektura

### Granica zależności

Locks jest zależnością **opcjonalną**. Zgodnie z konwencją `util/` (`PlayerManaCompat`,
`SrpInfestationHelper`, …) **jedyną** klasą dotykającą `melonslise.locks.*` jest
`util/LocksCompat`. Jej publiczne API operuje wyłącznie na typach vanilla/prymitywach
(`int id`, `World`, `BlockPos`, `EntityPlayer`), więc żadna Locks-owa klasa nie pojawia się
w sygnaturach ani polach — dzięki temu leniwa rezolucja JVM nie ładuje ich, gdy moda nie ma.
Każde wejście do shima jest bramkowane `LocksCompat.isLoaded()`.

```
util/LocksCompat
  int     findLockedLockableId(World, BlockPos)   // -1 = brak zamkniętego zamka
  int     getPinCount / getComplexityLevel / getSturdyLevel / getShockingLevel(World, int id)
  boolean isStillLocked(World, int id)
  boolean unlock(World, int id)                   // lock.setLocked(false), serwer
  void    shock(World, EntityPlayer, int id, float dmg)
  void    playLockOpen / playRattle(World, int id)
  boolean isWithinRange(World, int id, EntityPlayer, double max)
```

### Brak mixinów i brak własnych pakietów

Dwa fakty ustalone z bajtkodu Locks przed implementacją:

1. `LocksEvents.onRightClick` blokuje interakcję przez **`setUseBlock(Result.DENY)`**, nigdy
   `setCanceled(true)` / `setUseItem(DENY)`. `Item.onItemUse` naszego przedmiotu wykonuje się
   normalnie — mixin niepotrzebny.
2. `Lock extends Observable`, a `LockableHandler.update` wysyła `UpdateLockablePacket` do
   graczy śledzących. `lock.setLocked(false)` na serwerze **sam** synchronizuje klientów —
   własny pakiet niepotrzebny.

`Lockable` jest synchronizowany na klienta, więc obie strony liczą czas kanałowania niezależnie
z tych samych danych; nie ma desynchronizacji paska postępu.

### Przepływ kanałowania (na wbudowanym `setActiveHand`)

| # | metoda | strona | działanie |
|---|---|---|---|
| 1 | `onItemUse` | obie | `findLockedLockableId`; brak → `PASS` |
| 2 | `onItemUse` | obie | brama Complexity: `pickStrength > complexity × 0.25`; fail → status *too complex*, `PASS` |
| 3 | `onItemUse` | obie | `channelTicks = (base + perPin × piny) × (1 − 0.15 × swift)` → NBT stacka; `setActiveHand`; `SUCCESS` |
| 4 | `getMaxItemUseDuration` | obie | czyta NBT z kroku 3 |
| 5 | `onUsingTick` | obie | rewalidacja (zamek nadal zamknięty, gracz w zasięgu) → inaczej `resetActiveHand`; dźwięk `locks:lock.rattle` co sekundę |
| 6 | `onItemUseFinish` | serwer | rewalidacja → `unlock(id)`; `damageItem(cost, player)`; dźwięk `locks:lock.open` |
| 7 | `onPlayerStoppedUsing` | serwer | przerwane: Shocking → obrażenia; **zero durability** |

`EnumAction.NONE`. Odrzucone: `BOW` (animacja napinania łuku) oraz `BLOCK` — ta druga przełącza
`EntityLivingBase.isActiveItemStackBlocking()`, co dałoby graczowi darmowe blokowanie obrażeń jak
tarczą na czas całego kanałowania. `NONE` zachowuje vanillowe spowolnienie ruchu bez tego skutku
ubocznego; sprzężenie zwrotne daje pasek na HUD, dźwięk `lock.rattle` i cząsteczki.

`resetActiveHand()` celowo **nie** wywołuje `onPlayerStoppedUsing`, więc przerwanie przez
zniknięcie celu nie razi gracza Shockingiem — rażony jest tylko ten, kto sam puści przycisk.

### Liczby (wszystkie w cfg)

- czas: `20 + 20 × piny` ticków → wood 6 s · gold 7 s · iron 8 s · steel 10 s · **diamond 12 s**;
  ze Swift Picking III (×0.55) diamond → 6.6 s
- durability: `ceil(piny × 1 × (1 + 0.5 × sturdy))` → diamond 11, ze Sturdy III 27
- pula durability 250 ≈ 22 diamentowe zamki bez Unbreaking
- `pickStrength = 0.7` → Complexity I/II przechodzą, **Complexity III blokuje**

**Unbreaking działa bez dodatkowego kodu** — zużycie idzie przez `stack.damageItem(cost, player)`,
a vanilla `ItemStack.attemptDamageItem` sama stosuje `EnchantmentDurability`.

### Enchant

`insanetweaks:swift_picking`, Rarity `RARE`, max lvl 3 (cfg), własny `EnumEnchantmentType`
utworzony przez `EnumHelper.addEnchantmentType("INSANETWEAKS_AUTO_LOCK_PICK", item -> item instanceof AutoLockPickerItem)` —
dokładnie ten wzorzec, którego Locks używa dla swojego `LOCK_TYPE`. Dostępny na stole, w książkach
i na kowadle.

### Rejestracja

Item i enchant rejestrowane **bezwarunkowo**; `modules.enableAutoLockPicker` +
`Loader.isModLoaded("locks")` bramkują wyłącznie *zachowanie*. Odbiega to od `ModEnchantments`
(gdzie `SENTIENT_CODEX` rejestruje się tylko przy włączonej fladze) i jest celowe: bramkowanie
obiektu rejestru flagą oznacza, że jej wyłączenie kasuje wpis z istniejącego świata.

## Nowe i zmienione pliki

Nowe:
- `util/LocksCompat.java`
- `items/AutoLockPickerItem.java`
- `enchant/EnchantmentSwiftPicking.java`
- `config/categories/AutoLockPickerCategory.java`
- `config/categories/SwiftPickingCategory.java`
- `client/AutoLockPickerHudHandler.java`
- `assets/insanetweaks/models/item/auto_lock_picker.json`
- `assets/insanetweaks/textures/items/auto_lock_picker.png`

Zmienione: `ModConfig`, `ModulesCategory`, `EnchantmentsCategory`, `ModItems`,
`ModEnchantments`, `InsaneTweaksMod` (`after:locks`, `VERSION`), `en_us.lang`,
`build.gradle`, `mcmod.info`.

Wersja contentu **1.4.8 → 1.4.9** (cztery miejsca).

## Ryzyka

1. **`Locks-1.12.2-3.0.0.jar` ma spakowany cały `org.spongepowered.asm` + ASM.** Content deobfuje
   `fileTree(libs)`, więc trafi to na classpath obok MixinBooter 7.1. Jeśli kompilacja się zagryzie —
   dopisać `Locks-*.jar` do `exclude` i wciągnąć go osobnym, przefiltrowanym wpisem.
2. **Mixinów nie ma, ale weryfikacja i tak musi być na żywym launchu.** Założenie o
   `setUseBlock(DENY)` potwierdzone z bajtkodu, nie z uruchomienia — sprawdzić w DEv 1.2, że
   `onItemUse` faktycznie dostaje sterowanie na zamkniętej skrzyni.
3. `LocksUtil.intersecting` zakłada obecność capability świata — shim opakowuje wywołania
   w `try/catch` i zwraca „brak zamka” zamiast propagować wyjątek.
