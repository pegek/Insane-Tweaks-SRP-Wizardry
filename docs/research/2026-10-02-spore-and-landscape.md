# Research: następcy SRParasites na 1.20.1 i szczegóły Spore

**Data:** 2026-10-02. **Cel:** wybór moda do integracji dla EbreduxAddon. **Decyzja autora:** na razie
**tylko Fungal Infection: Spore**.

Wszystko w części 2 pochodzi z bajtkodu `spore_1.20.1_2.2.0j.jar` (`javap`, JDK 17) albo z configu
wygenerowanego przez Spore na serwerze dedykowanym Forge 47.4.26. Spore jest „All rights reserved”,
więc **jar nigdy nie trafia do repo**; czytamy go tylko pod kątem interoperacyjności.

---

## 1. Rynek (Modrinth API, 2026-10-02)

| Mod | Pobrania | Licencja | Uwagi |
|---|---|---|---|
| **Fungal Infection: Spore** 2.2.0j | 1,16 mln | ARR, kod zamknięty | Forge i NeoForge, 1.20–1.21.1, aktualizacja 2026-06-29. Najbliższy SRP. **Wybrany.** |
| Sculk Horde 0.12.7 | 277 tys. | Apache-2.0, kod na GitHubie | Tagi dla integracji (`wards_against_infestation`, `sculk_horde_do_not_attack`). Na później. |
| MutationCraft Rematch 1.1 | 64 tys. | ARR, kod na GitHubie | Pasożytnicze mutanty. |
| Phayriosis 1.20.1 | 25 tys. | ARR | Infekcja rosnąca z czasem. |
| Strange Infection | CurseForge | MCreator | Kruchy cel integracji. |
| The Flesh That Hates (SCP-610) | CurseForge | ? | Mięsna infekcja, rój. |

## 2. Spore 2.2.0j

### Hierarchia encji

```
Monster
 └ Infected                (HUNGER, KILLS, EVOLUTION_POINTS, EVOLUTION, LINKED, PERSISTENT, ORIGIN)
    ├ EvolvedInfected
    ├ Hyper
    └ Experiment
PathfinderMob
 └ UtilityEntity
    ├ Calamity             (Enemy, ChunkLoaderMob) – 9 bossów: Gazenbrecher, Grakensenker, Hinderburg,
    │                        Hohlfresser, Howitzer, Leviathan, Sieger, Stahlmorder, Verfalldrachen
    └ Organoid             (Enemy) – Proto (Hivemind), Mound, HiveTumor, Womb, Vigil, Usurper, Umarmer,
                             Delusionare, Brauerei, Verwa, Tentacle
```

### Kogo Spore atakuje

`Utilities.TARGET_SELECTOR_PREDICATE` odrzuca **po klasie** `Infected`, `UtilityEntity` i
`TrueCalamity`; zwierzęta i ryby zależą od configu. Do tego config `"Mobs Not Targeted"` (pełne id
albo prefiks `modid:`). Wniosek: **mob, który ma należeć do roju, musi dziedziczyć po `Infected`**.
Sam wpis w configu tylko powstrzyma ataki w jedną stronę.

### Infekcja i konwersja

- Efekt `spore:mycelium_ef` („Mycelium Infection”). Odporne moby są wymienione w configu.
- `sEvents.Infection.onEntityDeath`: mob umierający z efektem jest zamieniany według listy z configu
  `"Mobs and their infected counterparts"`, w formacie `"źródło|cel"`. Parsowanie: `split("\\|")`,
  potem `ForgeRegistries.ENTITY_TYPES.getValue(...)`, `EntityType.create(level)`, ustawienie
  pozycji i nazwy, `finalizeSpawn` (gdy `instanceof Mob`) i `setOrigin` (gdy `instanceof Infected`).
  **Każde rzutowanie jest chronione `instanceof`, więc celem może być dowolny zarejestrowany mob
  z dowolnego moda.**
- Gracz też może zostać zarażony po śmierci (`"Should the player be infected on death?"`).

### Ewolucja

Config `["Mob Evolutions and Infection System".Evolutions]`: ścieżki per typ, np. Infected Human →
knight, griefer, braiomil albo busser. Timer 300 s (Hyper 600 s), minimum zabójstw 1 (Hyper 7).

### Stan świata

`SporeSavedData` (public): `static get(ServerLevel)` → `getAmountOfHiveminds()`, plus statyczne
listy Proto i Protector. Config `"The amount of needed Proto Hiveminds for the world to change
(Proto World Modifier)" = 3`. **To odpowiednik faz SRP, odczytywalny bez mixina.**

### Tagi

- `spore:fungus_entities`: 83 typy, czyli wszystkie zarażone, organoidy i kalamity.
- `spore:fungal_blocks`: 46 bloków (biomass, remains, infested_dirt…). Dalej: `biomass_to_membrane`,
  `burrowable`, foliage.

### Efekty (`effect.spore.*`)

mycelium_ef (Mycelium Infection), marker, corrosion, symbiosis, uneasy, madness, frostbite, biled,
ignitable, starvation. Rejestr (`Core.Seffects`) ma tych 10. `effect.spore.stunt` istnieje tylko
w pliku językowym, a efekt o takim id nie jest rejestrowany (wyłapał to GameTest addonu).

### Konfiguracja

`config/sporeconfig.toml` ma 1631 linii, a `SConfig$Server` 535 wartości. Ważne dla nas: globalne
mnożniki HP, obrażeń i pancerza, `"Should the infected be weak to cold?"`, listy celów, sekcja
`[Compatibilities]` (FAW, „Sculk Infection”).

### ⚠️ Kolizja nazw

Spore ma już sprzęt **„Living”**: `["Living Exoskeleton"]`, `["Living Upgraded Chestplate"]` i
`["Living Guns"]`. Nasza linia Living → Sentient z 1.12.2 potrzebuje innej nazwy albo świadomie
wspólnego motywu.

### Uruchomienie

Spore 2.2.0j wstaje na Forge 47.4.26 bez błędów krytycznych. Dwa błędy parsowania receptur
(`spore:halogen_light`, `broken_halogen_light`) są szumem samego Spore.

### ⚠️ Maven Modrinth: numer wersji jest niejednoznaczny

`maven.modrinth:fungal-infectionspore:2.2.0j` zwraca **wydanie NeoForge 1.21** (jar ma tylko
`META-INF/neoforge.mods.toml`, więc Forge 1.20.1 po cichu go pomija), bo Spore daje ten sam numer obu
platformom. Wydanie Forge 1.20.1 to ID wersji **`PbOZOahW`** (`spore_1.20.1_2.2.0j.jar`, sha1
`d52e5d362e42e30bbfe05106ca8bf93b46729f37`, ten sam plik co wyżej). Zawsze przypinać ID, nie numer.
