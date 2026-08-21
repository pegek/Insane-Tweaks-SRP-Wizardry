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

Testy jednostkowe: **51** (`ManaMathTest` 11, `CostMathTest` 20, `AdvancementBonusTableTest` 20), `./gradlew :manacore:test`.

## Architektura w skrócie

- **Pula** — capability na graczu, dwa pola trwałej progresji (`castProgression`, `itemProgression`) plus `current`. `maxMana` **nie jest przechowywana**.
- **Maksimum** — suma **dwóch** atrybutów `RangedAttribute` z `setShouldWatch(true)`, więc Forge sam synchronizuje je do klienta (od 0.2.0):
  - `manacore.maxMana` — **trwały**: baza z configu (ustawiana w `EntityJoinWorldEvent`, nie w `EntityConstructing` — wanilla nadpisałaby ją z NBT), obie progresje, oraz `grantedMax` (płaskie nadania: `/mana setmax`, w przyszłości achievementy).
  - `manacore.bonusMana` — **dynamiczny**: noszone artefakty. Modyfikatory `setSaved(false)`, więc nigdy nie trafiają do NBT i nie przeżywają śmierci.
  - 🚨 **Sam modyfikator atrybutu NIE przeżywa śmierci.** Wanilla buduje na respawnie nową encję gracza i nie kopiuje mapy atrybutów. Każde trwałe źródło musi trzymać wartość w capability i być odtwarzane przez `refreshPersistentModifiers`. To był błąd `/mana setmax` do 2026-08-19.
- **Sieć** — wozi wyłącznie `current`. Dwa kontrakty: `syncNow` (bezwarunkowo) i `syncIfDirty` (tylko przy zmianie). Interwał należy do wołającego okresowego, nie do kanału.
- **Sufit puli** — `ManaRegenHandler` egzekwuje go **bezwarunkowo, przed bramką regenu**: nadmiar ponad maksimum jest konfiskowany. `bonusMana` podnosi więc wyłącznie limit, nigdy nie wypłaca many. Wyjątkiem jest `afterProgressionGain`, które celowo nie obniża zbankowanej progresji, gdy admin zetnie cap w configu.
- **Śmierć** — `current` wraca do `pool.manaFractionOnDeath` (domyślnie 0.5) maksimum **trwałego**, liczonego ze **starej** encji w `PlayerEvent.Clone`, bo nowa nie ma jeszcze odbudowanych modyfikatorów.
- **EBW** — `Pre`/`Tick` to bramki, odjęcie w `Post` (leci dopiero po `Spell.cast() == true`). Czary ciągłe używają `spendQuiet`, jednorazowe `spend`. 🚨 Wszystkie cztery nasłuchy mają `EventPriority.LOWEST`: ASC modyfikuje koszt w **swoim** `SpellCastEvent.Pre` (`amulet_mana` −10%, `charm_mana_orb` −15%, `ring_mana_cost` −7,5%, `ring_blast`/`duration`/`range` +25%), a przy domyślnym priorytecie kolejność zależałaby od kolejności ładowania modów.
- **TaB** — `MagicStats` przekierowane w całości na naszą pulę, `onUpdate` anulowane (jego regen dublował nasz).
- **Osiągnięcia** — `AdvancementManaHandler` przelicza bonus z rzeczywistego stanu osiągnięć gracza i nakłada **jeden** modyfikator na `manacore.maxMana`. 🚨 Świadomie NIE przez `ManaAPI.addGrantedMax`: tamten bufor jest jednokierunkowy, a stan osiągnięć jest odwracalny (`/advancement revoke`) i przeliczalny (zmiana configu). Tabela `advancements.bonuses` przyjmuje dowolne ID, nie tylko EBW. Spec: [2026-08-20-manacore-advancement-mana-design.md](../specs/2026-08-20-manacore-advancement-mana-design.md).

## Czego NIE przetestowano

Rozgrywki. Do sprawdzenia, w tej kolejności:

1. **Heal przy pełnym HP** — czar się nie odpala, pasek many **nie może drgnąć**. To jest powód powstania całego projektu.
2. **Zwykły czar** — mana schodzi z puli, mana różdżki w NBT **nietknięta** (F3+H).
3. **Pusta różdżka** — rozładuj w Arcane Workbench, `/mana set 100`, czar ma zadziałać. Dowód, że wszystkie trzy bramki na manie różdżki są zneutralizowane.
4. **Pasek HUD** — rysowany bez ani jednego uruchomienia. `hud.offsetX`/`offsetY` do korekty.
5. **Skala jednostek TaB** — `tab.unitScale = 1.0` to zgadywanka; jeśli TaB liczy inaczej, jego przedmioty magiczne będą kosztować absurdalnie.

Komenda debugowa: `/mana <get|set|add|setmax|addprog|addprogitem> [ilość] [gracz]`, poziom operatora.

`setmax` celuje w połowę **trwałą**, więc bonus z ekwipunku dodaje się na wierzchu zamiast zostać zabankowany w trwały grant. `get` pokazuje rozbicie tylko wtedy, gdy bonus ≠ 0.

## Znane długi, świadome

- **Bonusy rasowe TaB przepadają.** `MagicStats.getMaxMana` czytało `MagicAttributes.MAX_MANA` i skalowało przez `getMagicAffinity()`; nadpisanie tego kasuje wkład ras. Odtworzenie — faza 6.
- **`Finish` nie leci, gdy kanałowanie przerwie brak many.** EBW woła wtedy `resetActiveHand()`, a nie `stopActiveHand()`, więc `onPlayerStoppedUsing` się nie wykonuje. Skutek: brak refundu i progresji za ten czar. Zostawione, bo waniliowe EBW zachowuje się identycznie przy pustej różdżce.
- **Efekty many innych modów — stan po 2026-08-21.** `ebwizardry:font_of_mana` **nie dotyczy many** (jego jedyny nasłuch dzieli `cooldown_upgrade` — skraca cooldown, mimo nazwy); nic z nim nie robimy. `ancientspellcraft:mana_regeneration` ładowało manę itemu → przepięte przez kategorię `effects` w `manacore.cfg`. Trait `meditation` robił to samo → przepięty przez `ManaCoreBridge` w `reskilltweaks` (refleksja z cache'owaną `Method`, **zero deskryptorów typów manacore w bajtkodzie** — mody zostają rozłączne).
- 🚨 **Przedmioty „paliwo, gdy różdżka nie ma many" są martwe przez nasz mod.** `ancientspellcraft:ring_mana_lesser`/`ring_mana_greater`/`charm_majestic_mana` (klasa `ItemManaArtefact`), `body_clockwork_heart` (1500 many), `charm_voltaic_vessel` (500), `ebwizardry:charm_hunger_casting`. Wszystkie bramkują się na „różdżka wyczerpana", a nasze przekierowania zamrażają manę różdżki — więc albo nie odpalą się nigdy (różdżka naładowana), albo bez przerwy (pusta od początku).
- **Bezcelowe, ale nieszkodliwe** — ładują manę różdżki, której nikt nie zużywa: `ebwizardry:condenser_upgrade`, `ring_condensing`, `amulet_arcane_defence`, trzy `mana_flask`, `ancientspellcraft:charm_mana_flask`, `ancient_mana_flask`.
- **Kolizji mixinów z ASC nie ma** (sprawdzone 2026-08-21). ASC ma cztery configi mixinów; skompilowany `MixinItemWand` leży w jarze, ale **nie jest wymieniony w żadnym z nich** — martwa klasa. Nic z ASC nie dotyka `canCast`/`cast`/`consumeMana`.
- **Efekty mana-owe innych modów** (ASC `PotionManaRegeneration`, bonus setowy SpellBundle, mana leech Necromancer's Delight, ArcaneApprentices) nadal celują w manę **itemu**, nie w pulę. Faza 5.
- **`spellarchives`** pokazuje `Cost: %d mana` z niewłaściwego źródła.
- **`pool.hardCap` jest zadeklarowane, ale nic go nie czyta.** Komentarz w configu to mówi.
- **`grantedMax` to jedna wspólna liczba, nie rejestr per źródło.** Nadaje się wyłącznie dla nagród jednokierunkowych. Źródło przeliczalne (poziom Reskillable, noszony item) nie potrafiłoby odjąć swojego poprzedniego wkładu — musi mieć własny modyfikator z własnym UUID.
- **Bonus melee różdżki stał się stały**, bo bramkuje go „różdżka nie jest pusta", a nic jej już nie rozładowuje.

## Pułapki, które kosztowały czas — nie powtarzać

- **`required: true` w configu mixinów przy braku klasy mixina = crash w fazie `CONSTRUCTING`**, nie ciche pominięcie. Wszystkie configi bramkowane modami w tym repo mają `false`. Cena: mixin, który się nie zaaplikował, milczy — jedynym dowodem jest `cleanmix.log`.
- **`@LateMixin` nie istnieje w MixinBooter 7.1.** Adnotacja to `@zone.rong.mixinbooter.MixinLoader`, oznaczona jako deprecated. Sprawdź `grep -rn "MixinLoader" --include=*.java */src/main/java`.
- **`SpellCastEvent$Tick`/`$Finish` mają `getCount()`, nie `getCastingTick()`.** `$Post` nie ma nic — numer ticka bierze się z `player.getItemInUseMaxCount()`, co działa tylko dlatego, że wanilla dekrementuje `activeItemStackUseCount` **po** powrocie z `onUsingTick`.
- **EBW czyta manę różdżki w TRZECH miejscach**, nie dwóch: `canCast`, `cast` i `func_77615_a`. Trzecie bramkuje zdarzenie `Finish`.
- 🚨 **`SpellCastEvent.Post` leci RAZ na cast, nie co tick.** `ItemWand.cast` jest wołane co tick przy kanałowaniu, ale post zdarzenia jest bramkowany na `castingTick == 0` (`48: ifne 72`). Upkeep czarów ciągłych naliczany z `Post` = czar prawie darmowy. Naliczanie siedzi w `EbwContinuousUpkeep`, wołane z przekierowania `consumeMana` — to jedyny punkt per-tick po `Spell.cast() == true`.
- **`player.getItemInUseMaxCount()` nie zastąpi `castingTick`** — `cast` woła `setActiveHand` **po** miejscu naliczania, więc w pierwszym ticku kanału czyta 0.
- **Rozkład kosztu w EBW jest bezstratny.** `getDistributedCost`: `cost/2 + cost%2` co 20 ticków, `cost/2` co 10 — sumuje się dokładnie do `cost`/s także dla nieparzystych. Na 189 czarów EBW najtańszy po `none` (0) i `snowball` (1) kosztuje 5, koszty idą co 5.
- **`Math.round(Infinity)` → `Long.MAX_VALUE`, a `(int)` z tego → `-1`**, nie `Integer.MAX_VALUE`. Ujemny koszt w `spendQuiet` **dodaje** manę.
- **`amount <= 0.0D` nie odrzuca `NaN`** — każde porównanie z `NaN` jest fałszywe, więc warunek wygląda na obsłużony i nie jest.
- **`getEntity()`, nie `getObject()`** — getter właściciela w `CapabilityEntityBase` z TaB.
- **`xat:mana_candy2/3/4` nie istnieją w rejestrze.** To warianty modelu wybierane przez `ItemMeshDefinition` po rozmiarze stosu; wszystkie to jeden `xat:mana_candy`.
- **Mixin na klasę rozszerzającą typ kliencki idzie do sekcji `"client"`** configu mixinów, nie `"mixins"` — nawet jeśli sam mixin nie nazywa żadnego typu z `net.minecraft.client`.

## Kierunek do przemyślenia — zmiana zamysłu z 2026-08-19

Po pierwszych testach w grze pojawił się pomysł, żeby **odejść od mnożenia ścieżek zwiększania puli** na rzecz mechaniki zmęczenia. Nie jest to jeszcze decyzja, ale zmienia sens kilku rzeczy już zbudowanych, więc warto go rozważyć przed fazą 6.

**Mniej hooków na `+max mana`.** Spec §10 planował sześć źródeł bonusu (bauble EBW, enchant zbroi, quality z QualityTools, drzewko Reskillable, achievementy, bonusy rasowe). Jeśli pula ma zostać stosunkowo płaska, większość z nich traci rację bytu. Architektura to znosi bez bólu — każde źródło jest jednym `AttributeModifier` przez `ManaAPI.addMaxModifier`, więc rezygnacja to po prostu nienapisanie kodu, a nie usuwanie go.

**Mana exhaustion zamiast refundu z tieru różdżki.** Dziś upgrade `storage` jest przemapowany na procentowy zwrot many po udanym caście (`CostMath.refundFraction`, konfigurowalny w `ebw.refund*`). Propozycja: zastąpić to ograniczeniem tempa — gracz nie może rzucić wielu zaklęć w krótkim czasie, a **wyższy tier różdżki łagodzi debuffy zmęczenia** zamiast zwracać manę.

Co to znaczy dla istniejącego kodu:
- Refund ze `storage` znika. `CostMath.refundFraction` i cztery pola `ebw.refund*` stają się martwe — do usunięcia, nie do zostawienia jako nieaktywne.
- Znika też problem opisany wyżej, że `Finish` nie leci przy przerwanym kanałowaniu, bo to właśnie refund i progresja na nim wisiały.
- Zmęczenie potrzebuje **stanu per gracz z czasem** — czyli nowego pola w capability, obok `current` i dwóch progresji. Format zapisu znów najtańszy do zmiany teraz.
- Debuffy to naturalne miejsce na integrację z PotionCore, który dotąd nie miał w tym projekcie żadnej roli mimo pierwotnego założenia.

Otwarte pytania, gdyby to wchodziło: czy zmęczenie liczy się od liczby rzuconych czarów, od wydanej many, czy od kosztu ostatniego czaru; czy jest widoczne na HUD osobno, czy jako stan paska; i czy dotyczy też czarów ciągłych, gdzie „liczba rzutów" nie ma sensu.

## Co dalej

Fazy z §10 specu, nierozpoczęte: normalizacja kosztów między modami (4), agregacja cudzych efektów mana-owych (5), źródła progresji — drzewko Reskillable, achievementy, baubles, enchanty, QualityTools (6), system capów (7). **Fazę 6 przemyśleć w świetle sekcji wyżej.**

Przed publikacją: zastąpić roboczą teksturę `bar_mana.png` pożyczoną z `player_mana` własną.
