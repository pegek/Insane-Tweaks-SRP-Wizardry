# EbreduxAddon 0.2: zarażony mag

**Data:** 2026-10-03
**Status:** zaimplementowany jako 0.2.0 (2026-10-03).
**Poprzednio:** spec v0.1, sekcja 5 („poza v0.1”), i research Spore.
**Zależności:** jak v0.1, plus Spore 2.2.0j (Forge 1.20.1, Modrinth `PbOZOahW`) jako **zależność
kompilacji `compileOnly`**. Jar Spore nadal nigdy nie trafia do repo.

## 1. Cel

Mag Redux (`ebwizardry:wizard`, `ebwizardry:evil_wizard`), który umiera z infekcją Spore
(`spore:mycelium_ef`), wstaje jako **zarażony mag** `ebreduxaddon:infected_wizard`:
- jest członkiem roju Spore;
- rzuca zaklęcia Redux i nasz Spine Volley;
- rośnie w siłę razem z infekcją świata.

Bez Spore moba po prostu nie ma.

## 2. Ustalenia z bajtkodu (Spore 2.2.0j)

| Ustalenie | Skutek |
|---|---|
| Konwersje mobów są **danymi**: `SporeMobConversionReloadListener` czyta `data/<dowolny namespace>/spore_mob_conversion/*.json`, format `"źródło": "cel"` albo `"#tag": "cel"` (plik `tutorial` w jarze). | Konwersja magów to **jeden plik JSON**, bez naszego handlera `LivingDeathEvent`. Spec v0.1 zakładał handler. Bez Spore folder nie jest czytany. |
| `Infection.onEntityDeath` dla wyniku `SporeMobConversionData.getResult(type)`: `create`, nazwa, pozycja, `finalizeSpawn(…, MobSpawnType.CONVERSION, …)`, `setOrigin(encodeId zmarłego)`, `addFreshEntity`, `discard` oryginału. | Losowanie żywiołu, tieru i zaklęć robimy w `finalizeSpawn`. Origin ustawia Spore. |
| `Infected` (extends `Monster`): `registerGoals` = `addTargettingGoals()` + `addRegularGoals()` (namierzanie z predykatem roju, głód, szukanie, panika, pływanie, jedzenie szczątków, podążanie za swoimi). Podklasa dokłada swoje cele i woła `super.registerGoals()` (wzorzec `InfectedHuman`). | Dokładamy `AttackSpellGoal` z Redux. Resztę zachowania roju dostajemy za darmo. |
| `Infected.finalizeSpawn` → `setDefaultLinkage` (link z Hivemindem przez `SporeSavedData.getDataLocation(ServerLevel).getAmountOfHiveminds()`), `spawnWithPoints`. | `SporeSavedData` ma i `get`, i `getDataLocation`; Spore sam używa `getDataLocation`, więc my też. Próg Proto World to `SConfig.SERVER.proto_spawn_world_mod`. |
| Atrybuty mobów Spore: `Mob.createMobAttributes()` + HP/obrażenia/pancerz z configu × globalne mnożniki Spore (`global_health`, `global_damage`, `global_armor`). | Nasz mob mnoży swoje bazy przez te same globalne mnożniki, więc konfiguracja trudności Spore działa też na niego. |
| `Infected.getDropList()` bez nadpisania zwraca listę z configu podklasy. | Nadpisujemy pustą listą, a łup idzie z naszej tabeli łupu. |

## 3. Mob

- `InfectedWizardEntity extends Infected implements ISpellCaster` (Redux).
- **Atrybuty:** 30 HP, 4 obrażeń wręcz, 4 pancerza i prędkość 0,26, każde mnożone przez globalne
  mnożniki Spore. Zasięg śledzenia 32.
- **Zaklęcia:** `AttackSpellGoal(this, 0.6, 14, 30, 50)` z Redux (cele, patrzenie, zaklęcia ciągłe,
  pakiety do klienta). Pod spodem jest melee na wypadek, gdy zaklęcia nie pasują.
- **Żywioł:** losowy z rejestru Redux bez `magic` i `healing`. Zaklęcia leczące i buffy dla moba
  roju nie mają sensu.
- **Tier maksymalny** losowany z wag zależnych od liczby Hivemindów `h` (`core.InfectedTiers`):

  | h | apprentice | advanced | master |
  |---|---|---|---|
  | 0 | 70 | 30 | 0 |
  | 1–2 | 30 | 50 | 20 |
  | ≥ próg Proto World (config Spore, domyślnie 3) | 0 | 50 | 50 |

  Próg czytamy z configu Spore, a gdyby to się nie udało, przyjmujemy 3. To odpowiednik faz SRP
  z sim wizarda.
- **Zestaw:** 3 zaklęcia żywiołu z tierem ≤ tier maksymalny, `canCastByEntity`, włączone
  w kontekście NPC, plus **zawsze Spine Volley**.
- **Wygląd:** model maga Redux (ta sama siatka, własna warstwa modelu) i tekstury maga Redux
  przebarwione na „zarażone” (`tools/infected_textures.py`). W ręce trzyma różdżkę Redux swojego
  żywiołu i tieru (szansa upuszczenia 5%).
- **NBT:** żywioł, tier, lista zaklęć, indeks tekstury i flaga „wylosowany”. Indeks tekstury jest
  synchronizowany do klienta.
- 🚨 **`/summon` z NBT i spawner z danymi nie wołają `finalizeSpawn`.** Taki mag nie miałby zaklęć
  ani różdżki (wyszło na teście klienta). Jeśli nic jeszcze nie losowało, losowanie odbywa się przy
  pierwszym ticku serwera.
- **Łup:** `data/ebreduxaddon/loot_tables/entities/infected_wizard.json`, czyli kryształy magii,
  zgniłe mięso i rzadko magiczny pył. Bez przedmiotów Spore, żeby tabela ładowała się też bez niego.
- **Tag:** dopisany do `spore:fungus_entities` jako wpis `required: false`, więc bez Spore nie ma
  błędu tagu. Dzięki temu Purifying Pulse go rani, a zabicie go daje punkty symbiozy.
- **Jajko spawnu** (`ForgeSpawnEggItem`) w zakładce addonu, gdy Spore jest obecny.

## 4. Bramka Spore

- Każda klasa z typami Spore leży w `com.spege.ebreduxaddon.compat.spore` i jest ładowana wyłącznie
  przez `SporeCompat`, gdy `ModList.isLoaded("spore")`. Dotyczy to rejestracji `EntityType`,
  atrybutów, jajka i renderera.
- **Bez Spore** typ encji nie istnieje. Świat zapisany ze Spore traci zarażonych magów
  z ostrzeżeniem Forge (decyzja 5 z v0.1).
- `mods.toml`: zależność `spore` zostaje opcjonalna, z zakresem zawężonym do `[2.2,2.3)`, bo
  dziedziczymy po jego klasie.

## 5. Config

Nowa sekcja `[infectedWizard]`:
- `spellCount` (3);
- `alwaysSpineVolley` (true);
- `wandDropChance` (0,05);
- mnożniki bazowych atrybutów.

Wagi tierów zostają w kodzie z testem. W 0.2 nie ma ich w configu, żeby nie mnożyć opcji.

## 6. Weryfikacja

- JUnit: `InfectedTiers`, czyli wagi, granice i brak mastera przy h = 0.
- GameTesty (z `-PwithSpore`, bez Spore tylko test „typu nie ma”):
  - mag Redux z `mycelium_ef` zabity → w pobliżu pojawia się `infected_wizard`, a mag znika;
  - `finalizeSpawn` daje żywioł, tier i co najmniej 1 zaklęcie plus Spine Volley;
  - zarażony mag rani wrogi cel zaklęciem w ciągu kilku sekund;
  - zarażony człowiek Spore nie namierza zarażonego maga i odwrotnie;
  - zapis i odczyt NBT zachowuje żywioł, tier, zaklęcia i flagę;
  - mag bez `finalizeSpawn` dolosowuje się sam przy pierwszym ticku.
- Serwer dedykowany, wariant (a) bez Spore: start czysty, bez błędów tagu. Warianty (b) i (c): start
  czysty.
- Klient pod Xvfb ze Spore: zarażony mag na scenie testowej.
