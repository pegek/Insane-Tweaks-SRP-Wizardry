# Sanctum Revision 5 (1.29.0, batch B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement spec §16:
- an element variant per sanctum;
- a runestone frame and accents;
- block palettes per dimension;
- generation in dim 111 with a street-level entrance.

**Architecture:**
- **Pure classes (JUnit):** the variant pick, the frame geometry and the palette choice.
  - `SanctumVariants` holds the per-dimension element pools.
  - `SanctumLayout.frameAt` classifies a cell as edge or corner.
  - `SanctumPalettes` holds the per-dimension block ids and mixes.
- **Builder:** resolves block ids through a dimension-aware `Palette`.
- **Record:** `SanctumWorldData` stores the element, which also gates whether a sanctum gets the frame.

**Tech Stack:** Forge 1.12.2, Java 8, JUnit 4.12, EBW 4.3.19, Unseen's Nether Backport 0.9 (`nb`).

**Spec:** `docs/superpowers/specs/2026-10-20-sunken-arcane-sanctum-design.md` §16.

## Global Constraints

- **Version:** content `1.29.0`, in `insanetweaks/build.gradle` and `InsaneTweaksMod.VERSION` (currently 1.28.1).
- **Runestone:**
  - metadata = `Element.ordinal()`;
  - pedestal with `NATURAL=false`;
  - elements are resolved by name through EBW `Element.fromName`, falling back to sorcery;
  - the ritual chamber gets no frame and no accents.
- **Config defaults:**
  - `Dimensions`: `{0, 111}`;
  - `Surface Cap`: `{"111=75"}`;
  - dimensions -1 and 1 are refused in `dimensionAllowed`.
- **Instance config:** add `111` under `Dimensions` in `insanetweaks.cfg`. Back up first and edit byte-exactly.
- **Shell:** Python scripts are written with the Write tool. No heredocs with quotes or backslashes.
- **Deploy:** lock check, a single content jar.

## Review Focus

1. **The frame must never seal a doorway.** Carves are cut after the walls; the frame is part of the wall pass.
   - Test: `frameNeverOnACarveAfterCarving`. A frame cell that is also inside a carve is still carved, which the builder order guarantees.
2. **The chamber stays all sealed stone.** Test: `chamberHasNoFrame`.
3. **The element is deterministic per sanctum**, and every pool element is a valid runestone element (not magic, not abomination).
   - Test: `SanctumVariantsTest`.
4. **The shaft never ends above the cap in a capped dimension.** Its ward stays below the cap and the casing does not stand above it.
   - This is Minecraft-side; it gets a review.
5. **Pre-1.29 sanctums never get half a frame.** The builder gates on the record's element.

---

### Task 1: Pure variant, frame and palette (TDD)

**Files:**
- Create: `worldgen/sanctum/SanctumVariants.java`, `worldgen/sanctum/SanctumPalettes.java`
- Modify: `SanctumLayout.java` (`frameAt`)
- Tests: `SanctumVariantsTest.java`, `SanctumPalettesTest.java`, `SanctumLayoutTest.java`

**Produces:**
- `SanctumVariants.pool(int dimension)` → `String[]`:
  - `{"sorcery","lightning"}` for 0;
  - `{"earth"}` for 111;
  - `{"fire"}` for -1;
  - `{"necromancy"}` for 1;
  - anything else → the dimension-0 pool.
- `SanctumVariants.element(int dimension, long layoutSeed)` → `String`: deterministic, using `new Random(layoutSeed ^ 0x52554E45L)`.
- `SanctumLayout.FRAME_NONE = 0`, `FRAME_EDGE = 1`, `FRAME_CORNER = 2`.
- `SanctumLayout.frameAt(int x, int y, int z)` → int:
  - for a cell on the shell of some non-CHAMBER room: CORNER if it is on 3 of that room's boundary planes, EDGE if on exactly 2, else NONE;
  - a cell that also lies in the CHAMBER's outer box is NONE.
- `SanctumPalettes`: per-palette constants for dimensions 0, 111, -1 and 1 (others use 0).
  - `String wall(int dimension, double noise)`: returns a block id, with a `:meta` suffix allowed (e.g. `minecraft:stonebrick:1`).
  - `String chiseled(int dimension)`.
  - `String stairs(int dimension)`.
  - `boolean silverfish(int dimension)`.
  - Mixes per spec §16.3, for example:
    - dim 0: noise < 0.6 → `minecraft:stonebrick`, < 0.85 → `minecraft:stonebrick:1`, else `minecraft:stonebrick:2`;
    - dim 111: noise < 0.85 → `minecraft:obsidian`, else `nb:cry_obi`.

- [ ] **Step 1: Tests (RED).**
  - `SanctumVariantsTest`:
    - `poolsPerDimension`;
    - `elementIsDeterministicAndInPool` (seeds 0..999);
    - `overworldSeesBothElements`;
    - `noMagicNoAbomination`.
  - `SanctumPalettesTest`:
    - `overworldMixMatchesTheOldWalls`: noise 0.5 → plain, 0.7 → mossy, 0.9 → cracked;
    - `dim111IsObsidianAndCryingObsidian`;
    - `onlyOverworldHasSilverfish`;
    - `unknownDimensionFallsBackToOverworld`.
  - `SanctumLayoutTest`:
    - `chamberHasNoFrame`: no cell of the chamber's outer box has `frameAt` ≠ NONE;
    - `everyNonChamberCornerIsACorner`: for each non-chamber room, its 8 outer-box corners give CORNER unless inside the chamber box;
    - `edgeMidpointsAreEdges`;
    - `faceCentresAreNotFramed`.
- [ ] **Step 2:** Run. Expected: RED (compile).
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `:insanetweaks:test` green.
- [ ] **Step 5:** Commit `feat(insanetweaks): sanctum variants, frame geometry and dimension palettes (pure)`.

### Task 2: Builder, record, config, shaft cap

**Files:**
- Modify:
  - `SanctumBuilder.java` (Palette and passes)
  - `SanctumWorldData.java` (`Element`)
  - `SanctumPlacement.java` (register, `dimensionAllowed`)
  - `ArcaneSanctumCategory.java` (`dimensions` {0, 111}, `surfaceCap`)
  - `CommandInsaneTweaks` (register call, if its signature changes)

**Produces:**
- **`SanctumWorldData`:**
  - `register(..., String element)` stores `"Element"`;
  - `String element(long id)` returns the element or null.
- **`SanctumPlacement`:**
  - `register` passes `SanctumVariants.element(dim, l.seed)`;
  - `dimensionAllowed` refuses -1 and 1;
  - `static int surfaceCap(World)` returns the cap or `Integer.MAX_VALUE`, parsed from `surfaceCap` entries `"dim=y"`, with malformed lines ignored.
- **Builder:**
  - `Palette(int dimension, String elementOrNull)` resolves:
    - wall: id or meta through `Block.getBlockFromName` and `getStateFromMeta`;
    - chiseled, stairs, ruin;
    - runestone and pedestal states of the element via `withProperty(ELEMENT, Element.fromName(...))`, pedestal `NATURAL=false`;
    - a missing block falls back to the Overworld block and logs one WARN.
  - **Wall pass (non-chamber rooms):** each shell cell gets:
    - the frame when `palette.hasElement()` and `frameAt` ≠ NONE: an edge gets runestone; a corner gets a pedestal if noise channel 14 < 0.6, else runestone;
    - otherwise, an accent runestone when `hasElement` and noise channel 15 < 0.04, on non-chamber rooms only;
    - otherwise, the palette wall, with silverfish only when `silverfish(dim)`.
  - **Dais and stairs:** from the palette.
  - **Shaft:** `top = min(surface, cap)`.
    - When `surface > cap`, the ruin is skipped.
    - The column and casing loops use `top` instead of `surface`.
- **Config:**
  - `dimensions = {0, 111}`;
  - `surfaceCap = {"111=75"}` with a comment;
  - the `dimensions` comment is updated (refuses 150, -1 and 1).

- [ ] **Step 1:** Implement.
  - Ruling: the builder is Minecraft-bound. Its geometry and palettes are tested in Task 1.
- [ ] **Step 2:** `:insanetweaks:test :insanetweaks:build` green, with no new warnings in touched files.
- [ ] **Step 3:** Commit `feat(insanetweaks): runestone frame, dimension palettes, dim 111 sanctums with street-level shaft`.

### Task 3: Version, instance config, deploy

- [ ] Bump to 1.29.0 and build.
- [ ] Bytecode check: no client references in `worldgen/sanctum`.
- [ ] Instance `insanetweaks.cfg`: add `111` under `I:Dimensions <`. Back up first; cmp shows only that line.
- [ ] Deploy with the lock check.
- [ ] Commit `chore(insanetweaks): 1.29.0`.
- [ ] Final review by a fresh reviewer.
