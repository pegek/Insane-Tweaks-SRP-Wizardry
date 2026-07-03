# Next session: Spell architecture rework

**Stan na 2026-07-03.** Spec zatwierdzony przez użytkownika, implementacja NIE rozpoczęta.

## Gdzie jesteśmy

1. Brainstorming zakończony, projekt zatwierdzony: `docs/superpowers/specs/2026-07-03-spell-architecture-rework-design.md` — przeczytaj go w całości przed czymkolwiek innym.
2. Etap 0 (aktualizacja zależności do SRP 1.10.7 + EBW 4.3.19) jest **zrobiony i zacommitowany**: podmienione jary w `libs/`, nowy koordynat CurseMaven w `build.gradle`, dwa fixy zgodności (`ParasiteXPFixHandler` — nowy parametr `srcID`; `SummonInfectionSafetyHelper` — odwrócona semantyka `srpcothimmunity`, szczegóły w specu). `./gradlew build` przechodzi.
3. Zdekompilowane źródła referencyjne (poza gitem, per urządzenie!): `notes/decompiled_mods/ebwizardry_source/decompiled_src` (EBW 4.3.19) i `notes/decompiled_mods/srp_sourcecode_analis/decompiled_src` (SRP 1.10.7). Na nowym urządzeniu trzeba je odtworzyć: jary + CFR 0.152 + wzorzec `decompile.py` leżą w `notes/decompiled_mods/` (folder gitignored — skopiuj ręcznie albo zdekompiluj ponownie z jarów modów).

## Następny krok

Użyć skilla **writing-plans** (superpowers) do rozpisania planu implementacji ze speca, potem implementacja:
- `AbstractSrpSummonSpell<T>` w `spells/` + migracja 5 summonów,
- wymiana ray-trace w `SpellImmuneBond` na `RayTracer` z EBW,
- helper `util/SpellCastFeedback`,
- usunięcie `InsaneTweaksSpellMinion.java`.

Weryfikacja: build + manualny `runClient` (lista przypadków w specu).

## Po tym etapie

Osobny brainstorming/spec na część 2: rework mechanik i wizualiów zaklęć (nowe particle SRP 1.10.7: flash RGB, blood, dot; nowe gettery `ItemWizardArmour` w EBW 4.3.19).
