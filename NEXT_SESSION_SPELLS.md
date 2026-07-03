# Next session: Spell architecture rework

**Stan na 2026-07-03 (po południu).** Implementacja W TOKU — wykonywana metodą subagent-driven wg planu `docs/superpowers/plans/2026-07-03-spell-architecture-rework.md`.

## Postęp implementacji

- ✅ Task 1: `AbstractSrpSummonSpell` utworzony, martwe placeholdery usunięte (commit 87ebcef) — spec+quality review OK
- ✅ Task 2: FerCow (5d0b4a9) — spec+quality OK
- ✅ Task 3: PrimitiveSummoner (5722170) — spec+quality OK
- ✅ Task 4: PrimitiveYelloweye (cd75598) — spec+quality OK
- ✅ Task 5: Wizard (4c29a21) — spec+quality OK
- ✅ Task 6: CallOfDemise (2b7c5e9) — spec+quality OK
- ✅ Task 7: SpellCastFeedback + ParasiteShroud (c5cc87c) — spec+quality OK
- ⬜ Task 8: SpellImmuneBond → RayTracer + SpellCastFeedback (pełny kod docelowy w planie, Task 8)
- ⬜ Task 9: manualna weryfikacja w runClient (checklista w planie) + finalny przegląd całości

Wznowienie: kontynuować skill superpowers:subagent-driven-development od Taska 8 (SpellImmuneBond — pełny docelowy kod w planie; implementer → spec review → quality review), potem Task 9 (manualna weryfikacja runClient) i finalny przegląd całości. Build był zielony po każdym tasku.

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
