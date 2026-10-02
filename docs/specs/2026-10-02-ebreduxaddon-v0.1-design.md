# EbreduxAddon v0.1: design

**Data:** 2026-10-02
**Status:** decyzje autora przyjęte 2026-10-02 (sekcja 9). Następny krok: korekta specu według nich i plan.
**Platforma:** Minecraft 1.20.1, Forge 47.3.19+, Java 17.
**Zależności:** Electroblob's Wizardry Redux ≥ 0.8.9 (**wymagana**), Fungal Infection: Spore 2.2.x
(**opcjonalna**).
**Research:** `docs/research/2026-10-02-spore-and-landscape.md`.

---

## 1. Czym jest ten mod

Addon do Wizardry Redux inspirowany `insanetweaks` z 1.12.2: magia skażenia, sprzęt, który rośnie
razem z graczem, i most do moda infekcji. To **nie jest port 1:1**. SRParasites nie istnieje na
1.20, a wszystko, co na 1.12.2 stało na mobach SRP, zostaje zastąpione albo pominięte.

Zasada nadrzędna: **addon działa bez Spore.** Spore dokłada warstwę: zarażonego maga, oczyszczanie
i skalowanie z postępem infekcji. Bez niego treść magiczna jest kompletna.

## 2. Tożsamość i workspace

- Nazwa wyświetlana **EbreduxAddon**, modid `ebreduxaddon`, pakiet `com.spege.ebreduxaddon`,
  prefiks logu `[EbreduxAddon]`.
- Gałąź orphan `addon/redux-1.20.1` w `pegek/Insane-Tweaks-SRP-Wizardry`, wzorem
  `port/tombtweaks-1.20.1`: własny wrapper Gradle 8.7, ForgeGradle 6, MixinGradle 0.7, mappings
  `official`, toolchain Java 17.
- Warstwy jak w porcie TombTweaks: `core` (logika bez typów MC, JUnit, składnia Javy 8 nie jest tu
  wymagana, bo 1.16.5 nie jest celem), `platform` (adaptery), `feature` (rejestracje i handlery).
- Zależności z Maven Modrinth, jary **nigdy w repo**:
  - `implementation fg.deobf("maven.modrinth:electroblobs-wizardry-redux:0.8.9-forge")`
  - `compileOnly fg.deobf("maven.modrinth:fungal-infectionspore:2.2.0j")` (sprawdzone: HTTP 200) plus
    `runtimeOnly` tylko w dev. Spore jest ARR, więc czytamy go wyłącznie dla interoperacyjności.

## 3. Ewoluujący sprzęt

### Kolizja nazw

Spore ma już Living Exoskeleton, Living Upgraded Chestplate i Living Guns. Nasza linia nie może się
nazywać „Living”, bo gracz z obydwoma modami będzie miał dwa różne „Living” w JEI. Propozycje
w decyzji 2.

### Różdżka: forma bazowa → Sentient

- Podklasa `WandItem` z Redux (`WandItem(SpellTier, Element)`, element MAGIC). Mana różdżki jest
  liczona przez durability, jak na 1.12.2, a własną pojemność daje nadpisanie
  `IManaItem.getManaCapacity`.
- **Postęp:** licznik w NBT. Rośnie z many wydanej na zaklęcia (`SpellCastEvent.Post`, koszt z
  modyfikatorów), a zabójstwa mobów z tagu `spore:fungus_entities` liczą się podwójnie, gdy Spore
  jest obecne.
- **Ewolucja** po progu z configu: przedmiot zamienia się w formę Sentient z zachowaniem NBT
  (zaklęcia, ulepszenia, mana, progres tieru Redux). Wzorzec z 1.12.2: ewolucja to podmiana
  przedmiotu, nie flaga.
- **Bonusy** rosną z postępem: zniżka kosztu, wydłużenie czasu trwania, siła przywołań. Wszystko
  przez `SpellModifiers` w `SpellCastEvent.Pre`, gdy rzucamy tą różdżką.
- Ulepszenia Redux działają normalnie, bo `WandItem.applyUpgrade` pilnuje limitu tieru.

### Zbroja: 4 części, forma bazowa → Sentient

- `ArmorItem` z własnym `ArmorMaterial`. **Bonusy magiczne przez atrybuty Redux**: `EBAttributes`
  `CAST_COST`, `CAST_COOLDOWN`, `CAST_POTENCY`… Redux sam zamienia je na modyfikatory zaklęć
  (`WizardryAttributeModifier`), więc nie potrzeba własnego kodu zdarzeń.
- **Postęp:** pochłonięte obrażenia per część (1.12.2: 1500), liczone w `LivingDamageEvent`.
- **Sentient:** silniejsze atrybuty. Bonus pełnego zestawu przeniesiony z 1.12.2: poniżej 25% HP
  trafienie ≥ 10 albo śmiertelne zostaje ścięte do 2.0 i zdejmuje negatywne efekty, z cooldownem
  90 s. Ze Spore zdejmuje też `mycelium_ef`.

## 4. Zaklęcia (4)

Wszystkie są zarejestrowane przez `DeferredRegister.create(EBRegistries.SPELL, MODID)`, mają JSON
w `data/ebreduxaddon/spells/` i klasę `Spell` (`cast(PlayerCastContext)`, `properties()`).
Rejestr ma `disableSaving()`, więc usunięcie zaklęcia w przyszłości nie zepsuje światów.

| Zaklęcie | Odpowiednik z 1.12.2 | Działanie | Ze Spore |
|---|---|---|---|
| **Cleanse** | Cleanse | kanałowane, zdejmuje negatywne efekty z celu i chwilowo daje na nie odporność | zdejmuje `mycelium_ef`, `corrosion`, `marker`, `madness` i inne |
| **Purifying Pulse** | Purifying Pulse | fala w punkcie, leczenie i odpychanie | zamienia bloki z `spore:fungal_blocks` na czyste odpowiedniki i rani `Organoid` w zasięgu |
| **Grasp** (Dispatcher Grasp) | Dispatcher Grasp | szpon unieruchamia cel; rzucający też stoi, dopóki trzyma | — |
| **Spine Volley** (Yelloweye Gland) | Yelloweye Gland | ładowany pocisk z kolcami, co czwarty rzut zostawia kałużę | — |

Żywioły v0.1 to istniejące żywioły Redux (decyzja 3). **Własny żywioł nie wchodzi do v0.1**:
`AbstractWizard.chooseElement` losuje z rejestru i szuka zbroi tego żywiołu
(`RegistryUtils.getArmor`), więc nowy żywioł wymaga kompletu zbroi i różdżek albo zabezpieczenia.
To ta sama klasa pułapki co na 1.12.2.

## 5. Most do Spore (opcjonalny)

Każda klasa dotykająca typów Spore jest ładowana **wyłącznie** przez bramkę
`ModList.isLoaded("spore")` w osobnej klasie. Bez Spore żadna z nich się nie ładuje.

### 5.1. Zarażony mag: `ebreduxaddon:infected_wizard`

- Dziedziczy po `com.Harbinger.Spore.Sentities.BaseEntities.Infected`. To **jedyny sposób** na
  członkostwo w roju: Spore nie atakuje swoich po klasie (`TARGET_SELECTOR_PREDICATE`). Dostaje też
  głód, zabójstwa i powiązanie z Hivemind.
- Rzuca zaklęcia Redux przez `AttackSpellGoal` z Redux i `ISpellCaster`. Pula zaklęć pochodzi
  z configu i jest filtrowana tierem.
- **Konwersja:** własny handler `LivingDeathEvent` dla `ebwizardry:wizard` i `ebwizardry:evil_wizard`
  z efektem `spore:mycelium_ef`, robiony tak jak u Spore: `create`, pozycja, nazwa,
  `finalizeSpawn`, `setOrigin`. **Nie edytujemy configu Spore**, a Spore tych magów nie ma na swojej
  liście, więc nie konwertuje ich drugi raz.
- Rejestracja `EntityType` jest warunkowa. Bez Spore mob nie istnieje, a tak zapisany świat
  wymaga obsługi brakującego wpisu (decyzja 5).
- **Skalowanie:** tier rzucany z wagami, przesuwanymi przez
  `SporeSavedData.get(level).getAmountOfHiveminds()`. To odpowiednik faz SRP z sim wizarda.

### 5.2. Pozostałe punkty styku (bez zależności kompilacji)

- Tagi `spore:fungus_entities` i `spore:fungal_blocks` dla zaklęć i postępu sprzętu.
- Identyfikatory efektów Spore dla Cleanse i bonusu zbroi.

## 6. Config (`ebreduxaddon-common.toml`)

Sekcje `[wand]`, `[armor]`, `[spells]`, `[spore]` z głównym przełącznikiem mostu. Wszystkie progi i
mnożniki są w configu i czytane na bieżąco, poza rejestracjami.

## 7. Weryfikacja

- `./gradlew check build` w tym środowisku: JUnit dla `core` (progi, krzywe bonusów, wagi tierów).
- Serwer dedykowany ze skryptu, w trzech wariantach: (a) Redux, (b) Redux i Spore, (c) Redux, Spore
  i TombTweaks. Start bez błędów, zaklęcia w `/ebwizardry` (komenda cast), mob zarejestrowany tylko
  w wariancie (b) i (c).
- Testy w grze: checklista w `docs/in-game-checklist.md`, tworzona razem z planem.

## 8. Poza v0.1

Własny żywioł (Abomination albo nowy motyw), przywołania mobów Spore jako sojuszników, Sanctuary
(osłona przed rozrostem Spore), Thrall i Sentinel, Bauble Fruits przez Curios, artefakty
(Klepsydra Zhonyi), Sculk Horde.

## 9a. Rozstrzygnięcia (2026-10-02)

1. **Zarażony mag: dopiero po v0.1.** W v0.1 most do Spore działa tylko przez tagi i efekty
   (Cleanse i Purifying Pulse). Sekcja 5.1 przechodzi do 0.2.
2. **Nazwy linii sprzętu:** **Grafted** i **Symbiotic** (→ Sentient). Którą linię nazywa która
   nazwa (różdżka czy zbroja), ustalimy w korekcie specu.
3. **Żywioły:** wstępnie Cleanse i Purifying Pulse → healing, Grasp → necromancy,
   Spine Volley → earth.
4. **Grafika:** placeholdery. Tekstury z 1.12.2 pochodzące z Faithful można przenosić, autor
   potwierdził, że licencja nie jest problemem.
5. **Świat ze Spore otwarty bez Spore:** zarażony mag znika z ostrzeżeniem w logu. Akceptowane.

## 9. Decyzje autora

1. **Zakres v0.1** zgodnie z twoim wyborem: różdżka, zbroja, 4 zaklęcia, plus most do Spore z 5.1.
   Zarażony mag to największy pojedynczy kawałek (AI, model, tekstura). Wchodzi do v0.1 czy do 0.2?
   **Rekomendacja: 0.2.** v0.1 ze Spore tylko przez tagi i efekty, czyli Cleanse i Purifying Pulse.
2. **Nazwa linii sprzętu** zamiast „Living”: np. **Grafted → Sentient**, **Mycelic → Sentient**,
   **Symbiotic → Sentient**. Rekomendacja: **Grafted**, bo jest neutralna i nie kłóci się
   z nazewnictwem Spore.
3. **Żywioły zaklęć v0.1:** Cleanse i Purifying Pulse → healing, Grasp → necromancy, Spine Volley →
   earth?
4. **Grafika:** v0.1 z placeholderami (przeróbki tekstur Redux i vanilla), a docelowe tekstury
   później? Część tekstur z 1.12.2 pochodzi z Faithful 32x (`CREDITS.txt`), więc ich licencja
   wymaga sprawdzenia przed przeniesieniem.
5. **Świat zapisany ze Spore, otwarty bez Spore:** zarażony mag zniknie z ostrzeżeniem w logu
   (Forge pomija nieznane encje). Akceptujemy?
