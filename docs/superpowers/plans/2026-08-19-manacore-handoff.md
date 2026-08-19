# manacore — stan przekazania (2026-08-19)

Gałąź `feat/manacore`. Spec: [2026-08-18-manacore-design.md](../specs/2026-08-18-manacore-design.md).
Plany: [Plan 1 — rdzeń](2026-08-18-manacore-1-core.md), [Plan 2 — mosty](2026-08-18-manacore-2-bridges.md).

## Gdzie jesteśmy

**Plan 1 (rdzeń) i Plan 2 (mosty) są zamknięte w kodzie.** Mod wstaje w instancji DEv 1.2, wszystkie mixiny się aplikują, brak błędów w logach. Rozgrywka nie została jeszcze przetestowana.

Ósmy subprojekt: modid `manacore`, grupa `com.spege.manacore`, wersja `0.1.0`.

## Co działa (potwierdzone w runtime)

Z `logs/cleanmix.log`:
```
APPLY mixins.manacore.ebw.json:MixinItemWand   -> electroblob.wizardry.item.ItemWand
APPLY mixins.manacore.tab.json:MixinMagicStats -> xzeroair.trinkets.capabilities.magic.MagicStats
APPLY mixins.manacore.tab.json:MixinManaGui    -> xzeroair.trinkets.client.gui.hud.mana.ManaGui
```

Z `logs/latest.log`:
```
[ManaCore] preInit done
[ManaCore] wizardryutils COST attribute bound
[ManaCore] EBW bridge registered
[ManaCore] TaB bridge registered
```

`config/manacore.cfg` generuje się z pięcioma kategoriami na poziomie głównym (`ebw`, `hud`, `pool`, `regen`, `tab`) — nic pod `general`, czyli kontrakt `category = ""` się trzyma.

Testy jednostkowe: **30** (`ManaMathTest` 10, `CostMathTest` 20), `./gradlew :manacore:test`.

## Architektura w skrócie

- **Pula** — capability na graczu, dwa pola trwałej progresji (`castProgression`, `itemProgression`) plus `current`. `maxMana` **nie jest przechowywana**.
- **Maksimum** — atrybut `RangedAttribute` `manacore.maxMana` z `setShouldWatch(true)`, więc Forge sam synchronizuje je do klienta. Baza z configu, ustawiana w `EntityJoinWorldEvent` (nie w `EntityConstructing` — wanilla nadpisałaby ją z NBT).
- **Sieć** — wozi wyłącznie `current`. Dwa kontrakty: `syncNow` (bezwarunkowo) i `syncIfDirty` (tylko przy zmianie). Interwał należy do wołającego okresowego, nie do kanału.
- **EBW** — `Pre`/`Tick` to bramki, odjęcie w `Post` (leci dopiero po `Spell.cast() == true`). Czary ciągłe używają `spendQuiet`, jednorazowe `spend`.
- **TaB** — `MagicStats` przekierowane w całości na naszą pulę, `onUpdate` anulowane (jego regen dublował nasz).

## Czego NIE przetestowano

Rozgrywki. Do sprawdzenia, w tej kolejności:

1. **Heal przy pełnym HP** — czar się nie odpala, pasek many **nie może drgnąć**. To jest powód powstania całego projektu.
2. **Zwykły czar** — mana schodzi z puli, mana różdżki w NBT **nietknięta** (F3+H).
3. **Pusta różdżka** — rozładuj w Arcane Workbench, `/mana set 100`, czar ma zadziałać. Dowód, że wszystkie trzy bramki na manie różdżki są zneutralizowane.
4. **Pasek HUD** — rysowany bez ani jednego uruchomienia. `hud.offsetX`/`offsetY` do korekty.
5. **Skala jednostek TaB** — `tab.unitScale = 1.0` to zgadywanka; jeśli TaB liczy inaczej, jego przedmioty magiczne będą kosztować absurdalnie.

Komenda debugowa: `/mana <get|set|add|setmax|addprog|addprogitem> [ilość] [gracz]`, poziom operatora.

## Znane długi, świadome

- **Bonusy rasowe TaB przepadają.** `MagicStats.getMaxMana` czytało `MagicAttributes.MAX_MANA` i skalowało przez `getMagicAffinity()`; nadpisanie tego kasuje wkład ras. Odtworzenie — faza 6.
- **`Finish` nie leci, gdy kanałowanie przerwie brak many.** EBW woła wtedy `resetActiveHand()`, a nie `stopActiveHand()`, więc `onPlayerStoppedUsing` się nie wykonuje. Skutek: brak refundu i progresji za ten czar. Zostawione, bo waniliowe EBW zachowuje się identycznie przy pustej różdżce.
- **Efekty mana-owe innych modów** (ASC `PotionManaRegeneration`, bonus setowy SpellBundle, mana leech Necromancer's Delight, ArcaneApprentices) nadal celują w manę **itemu**, nie w pulę. Faza 5.
- **`spellarchives`** pokazuje `Cost: %d mana` z niewłaściwego źródła.
- **`pool.hardCap` jest zadeklarowane, ale nic go nie czyta.** Komentarz w configu to mówi.
- **Bonus melee różdżki stał się stały**, bo bramkuje go „różdżka nie jest pusta", a nic jej już nie rozładowuje.

## Pułapki, które kosztowały czas — nie powtarzać

- **`required: true` w configu mixinów przy braku klasy mixina = crash w fazie `CONSTRUCTING`**, nie ciche pominięcie. Wszystkie configi bramkowane modami w tym repo mają `false`. Cena: mixin, który się nie zaaplikował, milczy — jedynym dowodem jest `cleanmix.log`.
- **`@LateMixin` nie istnieje w MixinBooter 7.1.** Adnotacja to `@zone.rong.mixinbooter.MixinLoader`, oznaczona jako deprecated. Sprawdź `grep -rn "MixinLoader" --include=*.java */src/main/java`.
- **`SpellCastEvent$Tick`/`$Finish` mają `getCount()`, nie `getCastingTick()`.** `$Post` nie ma nic — numer ticka bierze się z `player.getItemInUseMaxCount()`, co działa tylko dlatego, że wanilla dekrementuje `activeItemStackUseCount` **po** powrocie z `onUsingTick`.
- **EBW czyta manę różdżki w TRZECH miejscach**, nie dwóch: `canCast`, `cast` i `func_77615_a`. Trzecie bramkuje zdarzenie `Finish`.
- **`Math.round(Infinity)` → `Long.MAX_VALUE`, a `(int)` z tego → `-1`**, nie `Integer.MAX_VALUE`. Ujemny koszt w `spendQuiet` **dodaje** manę.
- **`amount <= 0.0D` nie odrzuca `NaN`** — każde porównanie z `NaN` jest fałszywe, więc warunek wygląda na obsłużony i nie jest.
- **`getEntity()`, nie `getObject()`** — getter właściciela w `CapabilityEntityBase` z TaB.
- **`xat:mana_candy2/3/4` nie istnieją w rejestrze.** To warianty modelu wybierane przez `ItemMeshDefinition` po rozmiarze stosu; wszystkie to jeden `xat:mana_candy`.
- **Mixin na klasę rozszerzającą typ kliencki idzie do sekcji `"client"`** configu mixinów, nie `"mixins"` — nawet jeśli sam mixin nie nazywa żadnego typu z `net.minecraft.client`.

## Co dalej

Fazy z §10 specu, nierozpoczęte: normalizacja kosztów między modami (4), agregacja cudzych efektów mana-owych (5), źródła progresji — drzewko Reskillable, achievementy, baubles, enchanty, QualityTools (6), system capów (7).

Przed publikacją: zastąpić roboczą teksturę `bar_mana.png` pożyczoną z `player_mana` własną.
