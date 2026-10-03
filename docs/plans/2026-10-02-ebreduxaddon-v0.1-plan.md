# EbreduxAddon v0.1: plan implementacji

**Spec:** `docs/specs/2026-10-02-ebreduxaddon-v0.1-design.md`. **Gałąź:** `addon/redux-1.20.1`.

**Stan (2026-10-02):** taski 0–8 zrobione. Następny: 9 (weryfikacja i wydanie).

**Testy w świecie:** `./gradlew runGameTestServer` (wariant a) i `./gradlew runGameTestServer -PwithSpore`
(wariant b). Każdy task z zaklęciem albo przedmiotem dokłada GameTesty w `gametest/`.
Dwie pułapki GameTestów, obie już raz ugryzły: (1) w koordynatach helpera y = 1 to podłoga areny,
moby stawiamy na y = 2, inaczej duszą się w bloku i każda asercja o HP kłamie; (2) testy jednej
partii stoją obok siebie i biegną naraz, więc liczymy tylko własne encje (np. po właścicielu). Serwer
dedykowany (`tools/server-check.sh`) sprawdza już tylko jar produkcyjny: start i log.
Klient: `tools/client-smoke.sh <świat> <katalog>` uruchamia klienta w dev pod Xvfb (Mesa llvmpipe),
wchodzi do świata z datapackiem `tools/smoke-pack` (scena z przedmiotami addonu) i robi zrzuty
ekranu. To jedyna kontrola modeli, tekstur i rendererów bez prawdziwego klienta.

Zasady dla każdego taska:
- Każdy task kończy się zielonym `./gradlew check build runGameTestServer` (z `-PwithSpore` i bez)
  i osobnym commitem. Tam, gdzie zaznaczono
  „serwer”, task wymaga też startu serwera dedykowanego bez błędów.
- Liczby i decyzje bez typów MC trafiają do `core` z testem JUnit.
- Jary Redux i Spore nigdy nie trafiają do repo (`.gitignore`: `libs/*.jar`, `run/`).

| # | Task | Pliki (główne) | Weryfikacja |
|---|---|---|---|
| 0 | **Szkielet.** Wrapper Gradle 8.7 z portu TombTweaks, `build.gradle` (FG6, official 1.20.1, Forge 47.3.19, Redux z Maven Modrinth `exclusiveContent`), `mods.toml` (GPL-3.0, `ebwizardry [0.8.9,)` mandatory, `spore` optional AFTER), `EbreduxAddon` (@Mod), `ModConfig` (4 sekcje, puste wartości domyślne), `LICENSE`, `CREDITS.txt`. | `build.gradle`, `settings.gradle`, `src/main/java/com/spege/ebreduxaddon/EbreduxAddon.java`, `platform/ModConfig.java`, `META-INF/mods.toml`, `pack.mcmeta` | build + serwer (a): mod załadowany, config wygenerowany |
| 1 | **Core.** `SymbiosisCurve` (bonusy z postępu, klamry 0..1, odporna na NaN i ujemne), `Progress` (nasycone dodawanie, próg ewolucji, podział obrażeń na części), `EveryNth` (licznik serii), `PairList` (parser `"a\|b"`, błędne wpisy zwracane osobno), `LastStand` (warunki, cooldown odporny na cofnięty zegar, okno nietykalności). | `core/*.java`, `src/test/java/.../core/*Test.java` | JUnit |
| 2 | **Cleanse** i efekt **Cleansing**. `DeferredRegister` zaklęć i efektów. Rozwiązywanie listy `spore.cleansedEffects` z cache i jednym ostrzeżeniem. | `feature/spell/CleanseSpell.java`, `feature/effect/CleansingEffect.java`, `feature/ModSpells.java`, `feature/ModEffects.java`, `platform/IdLists.java`, `data/ebreduxaddon/spells/cleanse.json`, lang | build + serwer: zaklęcie w rejestrze, JSON wczytany |
| 3 | **Spine Volley.** `SpineEntity extends MagicArrowEntity` (DartEntity ma typ zaszyty na `EntityType<DartEntity>`) z flagą kałuży, własny `EntityType`, `MagicArrowRenderer` Redux po stronie klienta. Licznik serii w persistent data rzucającego. | `feature/spell/SpineVolleySpell.java`, `feature/entity/SpineEntity.java`, `feature/ModEntities.java`, `client/ClientSetup.java`, JSON | build + serwer |
| 4 | **Grasp.** Ciągły `RaySpell`, stan chwytu w mapie po stronie serwera (`GraspState`), czyszczonej w `endCast` i przez sweeper porzuconych chwytów. Root przez modyfikator `MOVEMENT_SPEED` z UUID, zdejmowany przy końcu i przy śmierci celu. | `feature/spell/GraspSpell.java`, `feature/GraspState.java`, JSON | build + serwer |
| 5 | **Purifying Pulse.** `PurifyingWaveEntity` (bez modelu, `NoopRenderer`), fala przez 20 ticków, mapa bloków z `PairList`, tag `spore:fungus_entities`. Zamiana bloków tylko po stronie serwera i z limitem na tick. | `feature/spell/PurifyingPulseSpell.java`, `feature/entity/PurifyingWaveEntity.java`, `platform/BlockPurifier.java`, JSON | build + serwer (b): bez ostrzeżeń o nieznanych id Spore |
| 6 | **Różdżki.** `SymbioticWandItem` / `SentientWandItem` (blokada `applyTierUpgrade`), `WandBonusHandler` (Pre: mnożniki, Post: punkty), zabójstwa z tagu, ewolucja z kopią NBT. Słuchacze przez `WizardryEventBus`. | `feature/item/*WandItem.java`, `feature/WandBonusHandler.java`, `feature/ModItems.java`, `platform/WandProgress.java` | build + serwer |
| 7 | **Zbroje.** `ModArmorMaterials`, `AddonArmorItem` (Grafted / Sentient), bonus do zaklęć przez słuchacz `Pre` ze znacznikiem (nie `EBAttributes` - błąd Redux, research §3), `ArmorProgressHandler` (`LivingDamageEvent`), Last Stand (`LivingDeathEvent` + `LivingAttackEvent` dla nietykalności, tick w persistent data). | `feature/item/*ArmorItem.java`, `feature/ArmorHandler.java` | build + serwer |
| 8 | **Zasoby.** Modele, placeholdery tekstur (generowane skryptem z tekstur vanilla i Redux), ikony zaklęć, przepisy (wand i 4 części Grafted), zakładka kreatywna, lang `en_us`. | `assets/ebreduxaddon/**`, `data/ebreduxaddon/recipes/**`, `tools/placeholders.py` | build + serwer: zero błędów receptur |
| 9 | **Weryfikacja i wydanie 0.1.0.** Skrypt serwera dla wariantów (a), (b), (c), `docs/in-game-checklist.md`, README z instalacją i listą treści. | `tools/server-check.sh`, `docs/in-game-checklist.md`, `README.md` | trzy warianty zielone |

## Ryzyka znane z góry

- **`DartEntity` może nie dać się rozszerzyć** (konstruktor, prywatne pola, renderer przypięty do
  typu Redux). Plan B: własna encja `MagicProjectileEntity` z rendererem kopiującym podejście Redux
  i teksturą placeholder.
- **Stan Grasp po stronie klienta.** Ciągłe zaklęcia Redux tickują na obu stronach, a root musi
  działać tylko na serwerze, inaczej klient i serwer rozjadą się przy pozycji.
- **Podmiana stacka przy ewolucji w trakcie castu.** Ewolucja jest odkładana do końca ticka
  (`TickEvent.PlayerTickEvent`), nigdy w środku `Post`, żeby Redux nie pisał już po podmianie do
  starego stacka.
