# EbreduxAddon v0.1: design

**Data:** 2026-10-02
**Status:** zaakceptowany. Decyzje autora z 2026-10-02 są wpisane w treść (sekcja 10 to ich zapis).
Plan: `docs/plans/2026-10-02-ebreduxaddon-v0.1-plan.md`.
**Platforma:** Minecraft 1.20.1, Forge 47.3.19+, Java 17.
**Zależności:** Electroblob's Wizardry Redux ≥ 0.8.9 (**wymagana**), Fungal Infection: Spore 2.2.x
(**opcjonalna, w v0.1 bez zależności kompilacji**).
**Research:** `docs/research/2026-10-02-spore-and-landscape.md`.

---

## 1. Czym jest ten mod

Addon do Wizardry Redux inspirowany `insanetweaks` z 1.12.2: magia oczyszczania i pasożytów oraz
sprzęt, który rośnie razem z graczem. To **nie jest port 1:1**. SRParasites nie istnieje na 1.20,
więc wszystko, co na 1.12.2 stało na mobach i atrybutach SRP, jest zastąpione albo pominięte.

Zasada nadrzędna: **addon działa bez Spore.** Spore dokłada tylko warstwę: oczyszczanie jego bloków
i efektów oraz dodatkowy postęp za zabijanie jego mobów. Bez niego treść magiczna jest kompletna.

## 2. Tożsamość i workspace

- Nazwa wyświetlana **EbreduxAddon**, modid `ebreduxaddon`, pakiet `com.spege.ebreduxaddon`,
  prefiks logu `[EbreduxAddon]`.
- Gałąź orphan `addon/redux-1.20.1` w `pegek/Insane-Tweaks-SRP-Wizardry`, wzorem
  `port/tombtweaks-1.20.1`: wrapper Gradle 8.7, ForgeGradle 6, mappings `official`, toolchain Java 17
  przez foojay. MixinGradle **nie** jest potrzebny w v0.1, bo nie ma mixinów.
- Warstwy jak w porcie TombTweaks:
  - `core`: czysta logika bez typów MC (krzywe postępu, bonusy, licznik serii, parser mapy bloków),
    testowana JUnit 5;
  - `platform`: config i adaptery NBT;
  - `feature`: rejestracje, zaklęcia, przedmioty, handlery.
- Redux z Maven Modrinth: `implementation fg.deobf("maven.modrinth:electroblobs-wizardry-redux:0.8.9-forge")`.
  Redux jest na GPL-3.0, ale jar i tak nie trafia do repo. Addon jest na **GPL-3.0**, bo dziedziczy
  po klasach Redux (`WandItem`, `RaySpell`).
- Spore tylko w wariancie testowym serwera (jar ARR, poza repo). W kodzie v0.1 **zero** typów Spore:
  styk to wyłącznie identyfikatory tagów, bloków i efektów z configu.

## 3. Sprzęt

### Nazwy

Spore ma już sprzęt „Living”, więc nasza linia z 1.12.2 dostaje nowe nazwy:

| Linia | Forma bazowa | Po ewolucji |
|---|---|---|
| różdżka | **Symbiotic Wand** (`symbiotic_wand`) | **Sentient Wand** (`sentient_wand`) |
| zbroja (4 części) | **Grafted** helmet / chestplate / leggings / boots | **Sentient** helmet / chestplate / leggings / boots |

Uzasadnienie: zbroja jest „wszczepiona” w ciało, a różdżka żyje z magiem w symbiozie.

### 3.1. Różdżka: Symbiotic → Sentient

- Podklasy `WandItem` z Redux: `SymbioticWandItem(SpellTiers.ADVANCED, Elements.MAGIC)` i
  `SentientWandItem(SpellTiers.MASTER, Elements.MAGIC)`. Mana, ulepszenia i sloty działają jak
  w Redux, bo dziedziczymy całą klasę.
- 🚨 **Blokada ulepszenia tieru tomem.** `WandItem.applyTierUpgrade` podmienia przedmiot na
  `RegistryUtils.getWand(nextTier, element)`, czyli na zwykłą różdżkę Redux, a to skasowałoby nasz
  postęp. Obie klasy nadpisują tę metodę i zwracają różdżkę bez zmian. Symbiotic przechodzi
  w Sentient **tylko** przez ewolucję.
- **Postęp** to licznik `ebreduxaddon:symbiosis` w NBT:
  - punkty = koszt many rzuconego zaklęcia, liczony w `SpellCastEvent.Post` dla rzutów z tej różdżki;
  - zabójstwo moba z tagu `spore:fungus_entities` w trakcie trzymania różdżki daje
    `wand.fungalKillPoints` (domyślnie 40). Bez Spore tag jest pusty, więc ta ścieżka nic nie daje.
- **Ewolucja** przy `wand.evolveAt` punktach (domyślnie 6000). Po stronie serwera stack w ręce jest
  podmieniany na Sentient Wand z **kopią całego NBT** (zaklęcia, ulepszenia, mana, progres Redux),
  z dźwiękiem i komunikatem. Wzorzec z 1.12.2: ewolucja to podmiana przedmiotu, nie flaga.
- **Bonusy** w `SpellCastEvent.Pre` (tylko gdy rzucamy jedną z naszych różdżek), przez
  `SpellModifiers`. Pre widzi koszt, cooldown, potency, zasięg i czas trwania, **nie** chargeup
  (ten liczy się wcześniej, patrz spec etapu 2 TombTweaks, §2):

  | | koszt | czas trwania | potency |
  |---|---|---|---|
  | Symbiotic, postęp p ∈ [0, 1] | × (1 − 0,15·p) | × (1 + 0,25·p) | — |
  | Sentient | × 0,80 | × 1,30 | × 1,10 |

  Wszystkie liczby są w configu. Krzywa jest liniowa i liczona w `core`.

### 3.2. Zbroja: Grafted → Sentient

- `ArmorItem` z własnymi `ArmorMaterial`: Grafted na poziomie żelaza, Sentient na poziomie
  diamentu. Przepis na Grafted: zbroja żelazna + `ebwizardry:magic_crystal` (zakuwanie w stole
  rzemieślniczym, bez kowala).
- **Bonusy magiczne** przez atrybuty Redux (`EBAttributes`: `CAST_COST`, `CAST_COOLDOWN`,
  `CAST_POTENCY`), przypięte do slotu w `getAttributeModifiers`. Redux sam zamienia je na
  modyfikatory zaklęć (`WizardryAttributeModifier`), więc nie trzeba własnego kodu zdarzeń.
  Domyślnie na jedną część: Grafted −3% koszt, Sentient −5% koszt i +3% potency.
- **Postęp:** obrażenia pochłonięte przez noszącego, liczone w `LivingDamageEvent` i rozdzielane
  na noszone części Grafted. Każda część ma licznik w NBT i ewoluuje osobno przy `armor.evolveAt`
  (domyślnie 1500, jak na 1.12.2). Podmiana stacka w slocie zachowuje NBT (enchanty, nazwę,
  uszkodzenie).
- **Last Stand**, bonus pełnego zestawu z 1.12.2 (tam „Grave Defiance”, aktualna wersja
  w `ArmorEventHandler.onLivingDeath`, a nie zarchiwizowany hardcap trafień). Gdy wszystkie cztery
  sloty zajmuje Grafted lub Sentient, w dowolnym miksie, `LivingDeathEvent` jest anulowany jak
  przez Totem Nieśmiertelności. Noszący wstaje z 3 HP i dostaje 10 ticków pełnej nietykalności
  oraz efekt Cleansing na 40 ticków (ten z Cleanse, więc obejmuje też efekty Spore). Gra dźwięk
  totemu. Cooldown 90 s w tickach gry (`getGameTime`, wspólny dla wymiarów), zapisany w persistent
  data gracza. Źródła omijające nietykalność (`/kill`, pustka) nie są zatrzymywane, żeby nie palić
  cooldownu na nieuniknioną śmierć.

## 4. Zaklęcia (4)

Rejestracja przez `DeferredRegister.create(EBRegistries.SPELL, MODID)`, bo Redux tworzy ten rejestr
z `disableSaving()`. Usunięcie zaklęcia w przyszłości nie zepsuje więc światów (inaczej niż na
1.12.2, gdzie trzeba było `MissingMappings`). Każde zaklęcie ma klasę z `properties()` i JSON
w `data/ebreduxaddon/spells/`, który `PropertiesDataManager` Redux wczytuje i który nadpisuje
wartości z kodu. Lang w `spell.ebreduxaddon.<id>` i `.desc`. Ikona: `textures/spells/<id>.png`.

| Zaklęcie | Żywioł | Tier | Typ | Działanie |
|---|---|---|---|---|
| **Cleanse** (`cleanse`) | healing | advanced | defence | Promień (`RaySpell`). Trafiony żywy cel albo rzucający (gdy pudło lub blok) dostaje efekt **Cleansing** na `effect_duration` (200 t). Efekt co 10 ticków zdejmuje wszystkie efekty `HARMFUL` oraz te z listy `spore.cleansedEffects`. Klątwy Redux (`CurseMobEffect`) zostają, tak jak w `CureEffects`. |
| **Purifying Pulse** (`purifying_pulse`) | healing | master | utility | Punkt celowania do 24 bloków. Rozchodząca się fala (encja bez modelu, 20 ticków): leczy sojuszników o `health`, odpycha wrogów, a bloki z mapy `spore.purifiedBlocks` zamienia na czyste odpowiedniki. Moby z `spore:fungus_entities` dostają `damage`. Promień 8 bloków plus 2 na każde +45% potency (jak na 1.12.2). |
| **Grasp** (`grasp`) | necromancy | master | attack | Ciągły promień (`isInstantCast() == false`). Pierwszy trafiony cel jest trzymany: spowolnienie 255 i brak skoku, a rzucający też stoi. Co 10 ticków `damage`, a nie-gracz poniżej `execute_threshold` (20%) HP ginie normalną ścieżką obrażeń. Puszczenie przycisku albo wyjście celu poza `range` kończy chwyt. |
| **Spine Volley** (`spine_volley`) | earth | advanced | attack | Wachlarz 5 kolców (encja na bazie `DartEntity` z Redux, jego renderer). Kolec daje truciznę. Co czwarty rzut z tej samej różdżki (licznik w NBT stacka) środkowy kolec zostawia przy trafieniu chmurę trucizny (`AreaEffectCloud`, 3 bloki, 60 t). |

Koszty i cooldowny startowe (do strojenia w JSON):

- Cleanse 60 / 200 t;
- Purifying Pulse 150 / 1200 t, chargeup 40;
- Grasp 8 na sekundę;
- Spine Volley 25 / 60 t, chargeup 10.

Wartości z 1.12.2 (350 many i 5 minut) były strojone pod pulę ManaCore, a tej w Redux nie ma.

**Własny żywioł nie wchodzi do v0.1.** `AbstractWizard.chooseElement` losuje z rejestru i szuka zbroi
tego żywiołu (`RegistryUtils.getArmor`), więc nowy żywioł wymaga kompletu zbroi i różdżek albo
zabezpieczenia. To ta sama klasa pułapki co na 1.12.2.

## 5. Most do Spore w v0.1: tylko dane

Bez jednej klasy Spore w classpath. Wszystko przechodzi przez identyfikatory w configu, rozwiązywane
w `ForgeRegistries` przy pierwszym użyciu. Nieznane id jest pomijane z jednym ostrzeżeniem w logu.

- **Tag `spore:fungus_entities`:** dodatkowy postęp różdżki i obrażenia od Purifying Pulse.
  Tag jest odczytywany przez `TagKey`, więc bez Spore jest po prostu pusty.
- **`spore.cleansedEffects`:** domyślnie `spore:mycelium_ef`, `spore:marker`, `spore:corrosion`,
  `spore:uneasy`, `spore:madness`, `spore:frostbite`, `spore:biled`, `spore:stunt`,
  `spore:starvation`. Obejmuje je efekt Cleansing, czyli Cleanse i Last Stand.
- **`spore.purifiedBlocks`:** pary `"źródło|cel"`, w tym samym formacie co lista konwersji Spore.
  Domyślnie:
  - `infested_*` → odpowiednik vanilla (dirt, stone, netherrack, soul_sand, end_stone, sand,
    gravel, deepslate, red_sand, clay, cobblestone, cobbled_deepslate);
  - `minecraft:mycelium` → `minecraft:grass_block`;
  - roślinność grzybowa (`growths_*`, `blomfung`, `bloomfung2`, `growth_mycelium`, `fungal_roots`,
    `wall_growths*`, `mycelium_veins`, `fungal_stem*`, `hanging_fungal_stem`, `fungal_stem_sapling`,
    `underwater_fungal_stem_top`) → `minecraft:air`, a podwodny szczyt łodygi → `minecraft:water`.

  Celowo **poza** mapą: `overgrown_spawner`, bloki laboratoriów, `hive_spawn`, `brain_remnants`,
  biomasa i membrany. To elementy struktur i rdzeni roju, a ich zniszczenie zaklęciem byłoby
  skrótem przez zawartość Spore. Mapę można rozszerzyć w configu.
- Puste listy wyłączają odpowiednią część mostu. Główny przełącznik `spore.enabled`.

**Poza v0.1, czyli w 0.2:** zarażony mag `ebreduxaddon:infected_wizard` dziedziczący po
`Infected` (jedyny sposób na członkostwo w roju, bo Spore nie atakuje swoich po klasie),
konwersja magów Redux z efektem `mycelium_ef`, skalowanie tieru z
`SporeSavedData.getAmountOfHiveminds()`. Szczegóły zostają w researchu, §2. Wymaga osobnego specu,
bo wnosi zależność kompilacji, bramkę `ModList` i rejestrację warunkową.

## 6. Config (`ebreduxaddon-common.toml`)

`ForgeConfigSpec`, cztery sekcje, wszystko czytane na bieżąco poza rejestracjami:

- `[wand]`: `evolveAt`, `fungalKillPoints`, mnożniki z tabeli 3.1;
- `[armor]`: `evolveAt`, procenty atrybutów, parametry Last Stand (HP, nietykalność, Cleansing, cooldown);
- `[spells]`: `spineVolleyPuddleEvery` (4) i `cleanseTickInterval` (10). Reszta liczb siedzi
  w JSON zaklęć, czyli tam, gdzie Redux trzyma je dla swoich;
- `[spore]`: `enabled`, `cleansedEffects`, `purifiedBlocks`.

## 7. Grafika

Placeholdery: przemalowane tekstury Redux i vanilla. Tekstury z 1.12.2 pochodzące z Faithful można
przenosić, autor potwierdził licencję. `CREDITS.txt` w zasobach wymienia źródła.

## 8. Weryfikacja

- `./gradlew check build` w chmurze: JUnit dla `core` (krzywe, licznik serii, parser par, Last Stand
  jako czysta funkcja).
- Serwer dedykowany ze skryptu, w trzech wariantach: (a) Redux, (b) Redux i Spore, (c) Redux, Spore
  i TombTweaks 0.1.0. W każdym wariancie:
  - start bez błędów i bez ostrzeżeń `[EbreduxAddon]`;
  - 4 zaklęcia w rejestrze;
  - JSON-y wczytane (brak `No spell found with ID ebreduxaddon:...`);
  - w (a) jedno ostrzeżenie o nieznanych id Spore, a nie dziewiętnaście.
- Testy w grze: `docs/in-game-checklist.md`, tworzona razem z planem. Klient z `runClient` nie jest
  dostępny w chmurze, więc część wizualna i GUI to wyłącznie ta checklista.

## 9. Poza v0.1

- Zarażony mag (0.2, patrz §5).
- Własny żywioł (Abomination albo nowy motyw).
- Przywołania mobów Spore jako sojuszników.
- Sanctuary (osłona przed rozrostem Spore).
- Thrall i Sentinel.
- Bauble Fruits przez Curios.
- Artefakty (Klepsydra Zhonyi).
- Sculk Horde.
- Most perków Tombstone'a (spec etapu 2 TombTweaks).

## 10. Zapis decyzji (2026-10-02)

1. **Zarażony mag po v0.1.** W v0.1 most do Spore działa tylko przez dane (§5).
2. **Nazwy:** Grafted (zbroja) i Symbiotic (różdżka), obie przechodzą w Sentient. Przypisanie
   linii zaproponował Claude w korekcie specu.
3. **Żywioły:** Cleanse i Purifying Pulse → healing, Grasp → necromancy, Spine Volley → earth.
4. **Grafika:** placeholdery, a tekstury z Faithful wolno przenosić.
5. **Świat ze Spore otwarty bez Spore:** dotyczy dopiero 0.2 (zarażony mag znika z ostrzeżeniem
   w logu). Akceptowane.
