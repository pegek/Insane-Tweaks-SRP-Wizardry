# TombTweaks (Minecraft 1.20.1)

Trzy tweaki do [Corail Tombstone](https://www.curseforge.com/minecraft/mc-mods/corail-tombstone) 9.1.x, każdy niezależnie wyłączalny.

| Feature | Co robi | Domyślnie |
|---|---|---|
| `[restore]` | Odzyskany grób oddaje przedmioty **na te same sloty**, z których zginęły — łącznie z pancerzem i offhandem | włączone |
| `[decay]` | Nieodwiedzony grób powoli gubi zawartość na ziemię; cztery listy ochrony wyjmują wybrane przedmioty spod tego | **wyłączone** |
| `[cooldown]` | Magiczne księgi Tombstone'a dostają cooldown, konfigurowalny per księga | włączone (2 księgi × 6 min) |

Config: `config/tombtweaks-common.toml`. Wszystkie wartości są czytane na bieżąco — bez restartu.

Wymagania: Minecraft 1.20.1, Forge 47+, Corail Tombstone **9.1.4 – 9.1.x** (zakres jest celowo wąski: mixiny są zweryfikowane tylko na 9.1.4, a niezgodna wersja wolałaby nie wystartować, niż wywalić grę przy ładowaniu klasy).

## Jak to działa

**Restore.** W chwili śmierci mod zapisuje *plan miejsc* — dla każdego slotu tylko id przedmiotu i hash jego NBT, nie kopię przedmiotu. Przy odzyskaniu grobu przypisuje przedmioty do miejsc w dwóch przebiegach po całym grobie (najpierw dopasowania dokładne, potem po samym id) i wstawia przedmiot **wyłącznie do pustego slotu**. Wszystko, czego nie wstawi, Tombstone rozdziela tak jak zawsze — najgorszy przypadek to zwykłe zachowanie Tombstone'a, nigdy zgubiony ani zdublowany przedmiot.

**Decay.** Licznik `countTicks` grobu (Tombstone go zapisuje) wyznacza harmonogram: po `startTicks` co `intervalTicks` jeden stack wypada na ziemię. Najpierw niechronione; chronione dopiero gdy nic innego nie zostało, a przy `protectedNeverDecay = true` wcale. Licznik rośnie tylko, gdy chunk jest załadowany.

**Cooldown.** Blokada siedzi na `ItemBook.canEnchant`, start cooldownu — na jedynym w Tombstonie wywołaniu `setEnchant`, tylko przy sukcesie.

## Znane ograniczenia

- **Curios** nie są obsługiwane przez restore (przestrzeń slotów 200+ zarezerwowana). Tombstone ma własne `curioAutoEquip`.
- **„Reverse inventory sorting"** — preferencja Tombstone'a per gracz, uruchamiana *po* naszym evencie; gdy włączona, odwraca odzyskany układ. Wynik weryfikacji w grze: *do uzupełnienia*.
- **Restore działa tylko dla właściciela grobu.** Grób otwarty kluczem przez innego gracza rozdziela się standardowo.
- **Scalone groby.** Druga śmierć w promieniu 20 bloków od nieodebranego grobu dokłada przedmioty do niego; rozsadzane są tylko przedmioty z ostatniej śmierci, starsze idą ścieżką standardową. Scalenie zeruje też licznik decay.
- **Historia decay gracza offline** trafia tylko do logu serwera (DEBUG).
- **Dwa komunikaty przy zablokowanej księdze:** nasz (z liczbą sekund, na pasku akcji) i własny Tombstone'a „not allowed" w czacie.

## Budowanie

```bash
./gradlew build      # jar w build/libs/
./gradlew check      # 62 testy + straże czystości i Javy 8 dla pakietu core
```

JDK 17 Gradle pobiera sam przez toolchain. Tombstone pochodzi z CurseMaven — jego jar celowo nie leży w repo (licencja „All rights reserved").

**Po każdej zmianie mixina** sprawdź, że `tombtweaks.refmap.json` w zbudowanym jarze ma wpis dla `MixinBlockDecorativeGrave` → `m_6227_`. Bez refmapy build jest zielony, a gra wywala się dopiero w prawdziwej instancji — `runClient` tego nie pokaże.

## Architektura

| Pakiet | Zawartość | Testy |
|---|---|---|
| `core` | cała logika decyzyjna; zero typów Minecrafta i Tombstone'a; **składnia Javy 8**, żeby port na 1.16.5 był kopiuj-wklej | JUnit 5 |
| `platform` | config, adaptery `ItemStack` → `core`, kodek NBT | round-trip kodeka |
| `feature` | handlery eventów i trzy mixiny | w grze — patrz `docs/in-game-checklist.md` |

Obie zasady `core` są pilnowane mechanicznie: `CoreHasNoForeignTypesTest` skanuje skompilowane klasy, `compileCoreJava8` kompiluje `core` z `--release 8`.

Pełne uzasadnienie decyzji, z miejscami, gdzie implementacja odeszła od projektu: `docs/specs/2026-08-14-tombtweaks-1201-port-design.md`.
