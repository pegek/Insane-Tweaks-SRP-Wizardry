# Prompt dla nowej sesji: TODO sanktuarium (poradnik, lore, rytuał 3×3)

Skopiuj wszystko poniżej linii do nowej sesji Claude Code w `E:\Isuth\modDev`.

---

Pracujesz w repo `E:\Isuth\modDev` (Minecraft 1.12.2 Forge, multi-project, 7 modów). Zacznij od
przeczytania `CLAUDE.md` w korzeniu repo. Odpowiadaj po polsku.

## Równoległa sesja — zasady współpracy (ważne)

Inna sesja pracuje w tym samym repo nad **„partią B” Sunken Arcane Sanctum**:
- krawędzie pokoi z EBW runestone;
- palety bloków zależne od wymiaru;
- generowanie w Netherze, Endzie i wymiarze 111.

Ona edytuje `insanetweaks/.../worldgen/sanctum/SanctumBuilder.java`, `SanctumLayout.java`,
`SanctumPlacement.java` i `SanctumGenerator.java`, a także wersję contentu
(`insanetweaks/build.gradle` + `InsaneTweaksMod.VERSION`). Żeby nie wchodzić sobie w drogę:

1. **Pracuj w osobnym worktree i gałęzi**: utwórz ją od bieżącego `HEAD` gałęzi
   `fix/cleanroom07-patches`, np. `feat/sanctum-codex`, przez skill
   `superpowers:using-git-worktrees`.
2. **Nie ruszaj** czterech plików wymienionych wyżej. Jeśli musisz wpiąć się w builder, zrób to
   w osobnej klasie, np. `SanctumCodex.fill(World, BlockPos)`. W raporcie końcowym podaj
   jednolinijkowe wywołanie, które druga sesja doda w builderze. Pozycja lecternu jest już w
   layoucie: `SanctumLayout.codexLectern` (x, y, z, facing).
3. **Nie podbijaj wersji contentu** i **nie wdrażaj jara do paczki**. Merge, wersję i deploy
   zrobimy razem po zakończeniu obu sesji.

## Kontekst: co już istnieje

- **Spec sanktuarium:** `docs/superpowers/specs/2026-10-20-sunken-arcane-sanctum-design.md`.
  Przeczytaj §12–§15. Lista rzeczy odłożonych jest w §15.9, a wcześniejsze w §14.8.
- **Lectern w komnacie:** obok ołtarza 3×3 w komnacie rytualnej stoi pusty lectern
  `ebwizardry:oak_lectern` (od 1.28.1), czekający na poradnik.
- **Gałąź i wersja:** jesteśmy na `fix/cleanroom07-patches`, content ma wersję 1.28.1.
- **Paczka DEv 1.2:** `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\`. Logi są w `logs/`,
  mixiny w `logs/cleanmix.log`.
- **Patchouli** `Patchouli-1.0-23.6.jar` jest w paczce.
- **Akashic Tome** `AkashicTome-1.2-12.jar` przyjmuje przedmiot, którego nazwa (registry name)
  zawiera m.in. `book`, `tome`, `codex`, `guide`, `manual`, `journal` (`config/akashictome.cfg`,
  `Whitelisted Names`).

## Zakres tej sesji (TODO)

Najpierw użyj `superpowers:brainstorming` z użytkownikiem, potem spec, plan i implementacja.
Kolejność i zakres ustala użytkownik. Poniżej lista kandydatów:

1. **Poradnik do magii Abomination / mechanik insanetweaks.**
   - **Forma:** książka zgodna z Akashic Tome. Najprawdopodobniej książka Patchouli w zasobach
     `insanetweaks`, ale sprawdź, jak Patchouli 1.0-23 w 1.12 ładuje książki z jara moda.
   - **Treść na start:** receptury rytuału 3×3 na imbuement altar, z czasem kolejne mechaniki
     moda.
   - **Gdzie trafia:**
     - na lectern `codexLectern` w komnacie rytualnej sanktuarium (przez osobną klasę, patrz
       zasada 2);
     - ewentualnie także recepta albo loot.
2. **Rytuał 3×3 imbuement altar** (odłożony „punkt 10” z wcześniejszych testów):
   - imbuement altar znów niezniszczalny / nieodzyskiwalny zgodnie z wcześniejszym designem.
     Poszukaj w specach i notatkach słów „altar”, „salvage” i „ritual”;
   - książka rytuału;
   - więcej receptur;
   - integracja z JEI.
3. **Książki z lore sanktuarium:** 1–2 na sanktuarium, w skrzyniach. Wątek fabularny od
   użytkownika:
   - to było niegdyś sekretne sanktum akolitów starożytnej magii;
   - badali dziwne stworzenia, które pojawiły się na świecie wraz ze spadającą gwiazdą;
   - chcieli okiełznać te szkaradztwa, by wzmocnić swoją magię i ziemię;
   - przegrali i zapłacili za chciwość;
   - ostatnim desperackim ruchem zapieczętowali sanktum, aby szkaradztwa nie opanowały świata.

   Tekst ma iść przez `en_us.lang`: strony jako komponenty `translate`, tak jak robi to
   `SanctumRitualBook`. Plik `.lang` w 1.12 nie obsługuje nowych linii.

   Książki trafiają do lootu przez funkcję tabeli lootu albo osobną klasę. Bez edycji
   `SanctumBuilder`; jeśli to konieczne, podaj w raporcie jednolinijkowy hook dla drugiej
   sesji.
4. **Opcjonalnie, jeśli użytkownik zechce:** drobiazgi odłożone w przeglądach. Lista jest w
   historii commitów i w specu. Przykłady:
   - cooldown Banisha liczony od wybrania zaklęcia zamiast od rzutu;
   - 10-tickowe okno ochrony przed teleportem;
   - `BEWARE` co 10 minut w trakcie walki.

   Tych, które dotyczą `SanctumBuilder`/`Layout`, **nie ruszaj** — należą do drugiej sesji.

**Poza zakresem tej sesji:** balans mobów i lootu, leże smoka, wejście do struktury.

## Zasady repo (skrót — pełne w CLAUDE.md)

- Java 8, Forge 1.12.2. Build:
  `./gradlew "-Dorg.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot" :insanetweaks:test :insanetweaks:build`.
- Klasy czyste (bez typów MC) testuj JUnitem.
- **Przed każdym mixinem** wywołaj skill `mixins`.
- Bezpieczeństwo dedykowanego serwera: nic klienckiego w klasach wspólnych.
- Nowy lang potrzebuje `pack.mcmeta` — content go ma.
- W Bash heredoc z apostrofami i backslashami się psuje. Skrypty pisz narzędziem Write.
- Commity kończ linią `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
