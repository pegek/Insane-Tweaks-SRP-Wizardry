# Sanctum Ritual Workshop and Codex Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the 3x3 Imbuement Altar ritual an exclusive workshop of a conquered Sunken Arcane Sanctum (unbreakable altar, sanctum gate, four lit receptacles), give it its recipe set and JEI page, and ship a Patchouli Codex documenting it.

**Architecture:** The existing event-driven `RitualAltarManager` gains two start conditions (sanctum gate via `SanctumWorldData`, receptacle rule via a pure helper) and consumes receptacles on completion. Recipes live in `ModRituals`; moved table recipes are stripped in `ModRecipes.removeRecipes`. The Codex is a Patchouli book in content's assets with one Java processor that reads `RitualRegistry`, so Patchouli stays optional.

**Tech Stack:** Java 8, Forge 1.12.2-14.23.5.2860, ForgeGradle 3, Electroblob's Wizardry 4.3.19, JEI 4.16, Patchouli 1.0-23.6, JUnit 4.12.

**Spec:** `docs/superpowers/specs/2026-10-21-sanctum-ritual-and-codex-design.md`

## Global Constraints

- Work only in worktree `E:\Isuth\modDev\.claude\worktrees\sanctum-codex`, branch `feat/sanctum-codex`.
- Never edit `worldgen/sanctum/SanctumBuilder.java`, `SanctumLayout.java`, `SanctumPlacement.java`, `SanctumGenerator.java`, `insanetweaks/build.gradle`'s `version`, or `InsaneTweaksMod.VERSION`. No jar deploy.
- Never touch another mod's crafting recipe.
- Patchouli must stay optional: only `integration/patchouli/RitualPageProcessor` may reference `vazkii.patchouli.*`.
- Java 8, `-Xlint:all` must stay free of new warnings in touched files; no lambdas in mixins (none are added).
- Paths below are relative to `insanetweaks/src/main/java/com/spege/insanetweaks/` unless they start with `insanetweaks/` or `docs/`.
- Verification commands run from the worktree root: `./gradlew :insanetweaks:test` and `./gradlew :insanetweaks:build`.

## Review Focus

1. **Altar rituals switched off, the Abomination element missing, or sanctums disabled** (`enableAltarRituals=false`, `ModElements.EXTENDED=false` or `arcaneSanctum.enabled=false`): no ritual can ever run, so the moved table recipes must **not** be stripped, or Living gear becomes uncraftable. Pinned in Task 5.
2. **A 3x3 outside any sanctum, or a position whose id `isCleared` does not know**: must be refused (`isCleared` answers true for unknown ids). Pinned in Task 3 by keeping the lookup and the flag in one call path, and in the manual check of Task 9.
3. **A receptacle hanging on the wrong face, or on the altar but pointing inward**: must not count. Pinned in Task 2 (`FACING == s`).
4. **Patchouli absent**: the game must boot, rituals and JEI must work, no Codex recipe, `SanctumCodex.fill` a no-op. Pinned in Tasks 7 and 8.
5. **`gear.availability` disabling a Living item**: no ritual for it, and its table recipe already gone. Pinned in Task 4.

---

### Task 1: Altar untouchable again

**Files:**
- Delete: `events/ImbuementAltarSalvageHandler.java`, `events/ArtefactSalvageTooltipHandler.java`, `insanetweaks/src/main/resources/assets/insanetweaks/recipes/imbuement_altar.json`
- Modify: `InsaneTweaksMod.java` (remove the `ImbuementAltarSalvageHandler.applyBlockProperties()` call, its `EVENT_BUS.register`, and the client-block `ArtefactSalvageTooltipHandler` registration, with their comments)
- Modify: `config/categories/TweaksCategory.java` (remove `imbuementAltarBreakable`, `imbuementAltarHardness`, `imbuementAltarHarvestLevel`, `imbuementAltarSalvageArtefacts` with their annotations)
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang` (remove `tooltip.insanetweaks.altar_salvage` and its comment lines)

**Interfaces:** none produced.

- [ ] **Step 1: Delete and edit as listed.**
- [ ] **Step 2: Verify nothing references the removed names.**
  Run: `grep -rn "Salvage\|imbuementAltar\|altar_salvage" insanetweaks/src` — Expected: no output.
- [ ] **Step 3: Build.** Run `./gradlew :insanetweaks:build` — Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 4: Commit** `refactor(insanetweaks): imbuement altar unbreakable again, drop salvage and nucleus altar recipe`.

### Task 2: Receptacle rule

**Files:**
- Create: `ritual/RitualReceptacles.java` (pure, no Minecraft imports)
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/ritual/RitualReceptaclesTest.java`
- Modify: `ritual/RitualAltarManager.java`
- Modify: `events/RitualAltarHandler.java`

**Interfaces:**
- Produces: `RitualReceptacles.DARK = -1`; `static boolean lit(int[] ordinals, int abominationOrdinal)` — true iff `ordinals` has length 4, no entry is `DARK`, at least one equals `abominationOrdinal`, and `abominationOrdinal >= 0`.
- Produces (private, in `RitualAltarManager`): `int[] readReceptacles(World world, BlockPos centre)` returning the four element ordinals in N, E, S, W order (`DARK` for an empty receptacle), or `null` when any of the four positions is not a correctly hung receptacle.

- [ ] **Step 1: Write the failing tests** in `RitualReceptaclesTest` (abomination ordinal 8 in all):
  - `allFourAbomination_isLit`: `lit(new int[]{8,8,8,8}, 8)` is true.
  - `oneAbominationThreeOthers_isLit`: `lit(new int[]{1,8,3,5}, 8)` is true.
  - `noAbomination_isNotLit`: `lit(new int[]{1,2,3,4}, 8)` is false.
  - `oneDark_isNotLit`: `lit(new int[]{8, RitualReceptacles.DARK, 3, 5}, 8)` is false.
  - `wrongLengthOrNull_isNotLit`: `lit(null, 8)` and `lit(new int[]{8,8,8}, 8)` are false.
  - `elementMissing_isNotLit`: `lit(new int[]{8,8,8,8}, -1)` is false.
- [ ] **Step 2: Run** `./gradlew :insanetweaks:test --tests "*RitualReceptaclesTest"` — Expected: compilation failure (class missing).
- [ ] **Step 3: Implement `RitualReceptacles`.**
- [ ] **Step 4: Run the tests** — Expected: 6 passed.
- [ ] **Step 5: Wire it into `RitualAltarManager`.**
  - `readReceptacles`: for each `s` in `EnumFacing.HORIZONTALS` ordered N, E, S, W, the block at `centre.offset(s, 2)` must be loaded, be `WizardryBlocks.receptacle`, and have `state.getValue(BlockTorch.FACING) == s`; its `TileEntityReceptacle.getElement()` gives the ordinal (`DARK` for null).
  - `tryStart`: after a recipe match, require `RitualReceptacles.lit(readReceptacles(...), ModElements.ABOMINATION.ordinal())`; on failure play the existing `abort(world, centre)` fizzle and return. (Task 3 adds the sanctum gate in the same spot.)
  - `tickRitual`: abort when the receptacles stop being lit.
  - `complete`: after the result is written, `setElement(null)` on all four `TileEntityReceptacle`s and `markDirty()` them.
- [ ] **Step 6: Mark receptacles dirty in `RitualAltarHandler`.** For right-click, place (`EntityPlaceEvent`) and break on a `WizardryBlocks.receptacle`, call `RitualAltarManager.markDirty(world, pos.offset(state.getValue(BlockTorch.FACING).getOpposite()))` (the altar it hangs on). Keep the existing altar branch.
- [ ] **Step 7: Build and test.** `./gradlew :insanetweaks:test :insanetweaks:build` — Expected: `BUILD SUCCESSFUL`, all tests pass.
- [ ] **Step 8: Commit** `feat(insanetweaks): 3x3 ritual requires four lit receptacles, one Abomination`.

### Task 3: Conquered-sanctum gate

**Files:**
- Modify: `worldgen/sanctum/SanctumWorldData.java`
- Modify: `ritual/RitualAltarManager.java`

**Interfaces:**
- Produces: `public Long sanctumAt(int x, int z)` in `SanctumWorldData` — the id of the first record whose `bounds` (`[minX, minZ, maxX, maxZ]`, same layout `nearSanctum` reads) contain x/z inclusive, or `null`.
- Produces (private, in `RitualAltarManager`): `boolean inConqueredSanctum(World world, BlockPos centre)` — `sanctumAt` then `isCleared` on that id; `false` when `sanctumAt` returns null.

- [ ] **Step 1: Implement `sanctumAt`** with a javadoc stating that callers must use it before `isCleared`, because `isCleared` answers true for an unknown id.
- [ ] **Step 2: Gate `tryStart`** with `inConqueredSanctum` next to the receptacle check from Task 2, same fizzle on refusal. Order: recipe match → gate → receptacles.
- [ ] **Step 3: Build and test** — Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 4: Commit** `feat(insanetweaks): 3x3 ritual only runs in a conquered sanctum`.

### Task 4: Ritual recipe set

**Files:**
- Modify: `init/ModRituals.java`
- Modify: `init/ModRecipes.java` (make `bladeFrag()` and `hiveScrap()` package-private so `ModRituals` reuses the srpextra substitution)
- Modify: `config/categories/TweaksCategory.java` (add `altarTestRecipes`)

**Interfaces:**
- Consumes: `ModRituals.ritual(String path, int duration, ItemStack result, Ingredient... pattern)`, `item(...)`, `ore(...)`, `NONE` (existing).
- Produces: `public static boolean isAltarExclusive(Item item)` in `ModRituals` — true for `MAGIC_NUCLEUS`, `ADAPTATION_UPGRADE`, `LIVING_WAND`, `LIVING_SPELLBLADE`, `LIVING_AEGIS`, the four `LIVING_BATTLEMAGE_*`, `ModItems.LIVING_NUCLEUS`, and `Item.getItemFromBlock(ModBlocks.SANCTUARY_CORE)` when that block is non-null. Not `GOLDEN_BOOK`.
- Produces: config `tweaks.altarTestRecipes` (boolean, default `true`, `@Config.RequiresMcRestart`, name "Altar Test Recipes").

- [ ] **Step 1: Moved rituals (spec §7.1).** One `ritual(...)` per result, each a cell-for-cell copy of today's table pattern (JSON files in `insanetweaks/src/main/resources/assets/insanetweaks/recipes/` and `ModRecipes` entries `magic_nucleus`, `sanctuary_nexus`, `living_nucleus`), duration `ModConfig.tweaks.ritualDurationTicks`. A blank table cell becomes `NONE`. Rules:
  - skip any result where `ModItems.isGearDisabled(item)` is true;
  - battlemage inputs: one `Ingredient` over the eight elemental `ebwizardry:battlemage_*` items at wildcard meta (32767);
  - srpextra parts: `tightening_buckle`/`sturdy_armor_plates`/`flexible_cloth` when `Loader.isModLoaded("srpextra")`, otherwise `bladeFrag()`, `hiveScrap()`, `insanetweaks:rupter_solied`;
  - living nucleus result: the first stack of `OreDictionary.getOres(ModOreDict.ORE_LIVING_NUCLEUS)`, skipped with a warn line when empty;
  - `sanctuary_core`: only when `ModBlocks.SANCTUARY_CORE != null`.
- [ ] **Step 2: Golden book (spec §7.2).** `golden_book.json`'s pattern with `minecraft:nether_star` replaced by the Abomination magic crystal (`ebwizardry:magic_crystal` at meta `ModElements.ABOMINATION.ordinal()`), result 4 `insanetweaks:golden_book`.
- [ ] **Step 3: Test rituals (spec §7.3)**, registered only when `tweaks.altarTestRecipes`. "Abom dust" = `ebwizardry:spectral_dust` at the Abomination meta, "Abom crystal" = the Abomination magic crystal.

  | Path | Result | Pattern rows (NW→SE) |
  |---|---|---|
  | `test_twisted_core` | `enigmaticlegacy:twisted_core` | `· ghast_tear ·` / `blaze_powder, Abom crystal, blaze_powder` / `ore dustRedstone, ender_eye, ore dustRedstone` |
  | `test_cosmic_heart` | `enigmaticlegacy:cosmic_heart` | `astral_dust, ore itMagicNucleus, astral_dust` / `blaze_powder, ore blockDiamond, blaze_powder` / `astral_dust, ender_eye, astral_dust` |
  | `test_enchanter_pearl` | `enigmaticlegacy:enchanter_pearl` | `· ore gemEmerald ·` / `blaze_powder, ender_pearl, blaze_powder` / `Abom dust, obsidian, Abom dust` |
  | `test_evil_ingot` | `enigmaticlegacy:evil_ingot` | `ghast_tear, Abom dust, ghast_tear` / `Abom dust, ore ingotNetherite, Abom dust` / `ghast_tear, Abom dust, ghast_tear` |
  | `test_witherite_ingot` | `enigmaticlegacy:witherite_ingot` | `gold, obsidian, gold` / `obsidian, Abom crystal, obsidian` / `gold, ·, gold` (gold = `ore ingotGold`) |
  | `test_glowing_gem` | `xat:glowing_gem` | `glowing_ingot, glowing_powder, glowing_ingot` / `glowing_powder, Abom crystal, glowing_powder` / `glowing_ingot, glowing_powder, glowing_ingot` |
  | `test_mana_crystal` | `xat:mana_crystal` | `mana_candy, Abom dust, mana_candy` / `Abom dust, xat:glowing_gem, Abom dust` / `mana_candy, Abom dust, mana_candy` |
  | `test_titan_spirit` | `xat:titan_spirit` | `glowing_gem, potato, glowing_gem` / `Abom dust, mana_crystal, Abom dust` / `glowing_gem, beef, glowing_gem` |
  | `test_ender_dragon_scale` | `bountifulbaubles:enderdragonscale` | `obsidian, dragon_breath, obsidian` / `dragon_breath, Abom crystal, dragon_breath` / `obsidian, dragon_breath, obsidian` |
  | `test_spectral_silt` | `bountifulbaubles:spectralsilt` (count 4) | `sand, Abom dust, sand` / `Abom dust, glowstone_dust, Abom dust` / `sand, Abom dust, sand` |

  Bountiful Baubles has no table recipe for its two items (they drop); their patterns are the assistant's design, not swaps.
- [ ] **Step 4: Add `isAltarExclusive`.**
- [ ] **Step 5: Build** — Expected: `BUILD SUCCESSFUL`. Start nothing in game here.
- [ ] **Step 6: Commit** `feat(insanetweaks): ritual recipe set (Living gear, components, golden book, test trinket parts)`.

### Task 5: Strip moved table recipes

**Files:**
- Modify: `init/ModRecipes.java` (`removeRecipes`)
- Modify: `config/categories/TweaksCategory.java` (add `altarExclusiveRecipes`)

**Interfaces:**
- Consumes: `ModRituals.isAltarExclusive(Item)`.
- Produces: config `tweaks.altarExclusiveRecipes` (boolean, default `true`, `@Config.RequiresMcRestart`, name "Altar Exclusive Recipes").

- [ ] **Step 1: In the existing collect-then-remove loop**, also collect a recipe when **all** hold: `altarExclusiveRecipes`, `enableAltarRituals`, `ModElements.EXTENDED`, `ModConfig.arcaneSanctum.enabled`, the recipe's registry name namespace is `insanetweaks`, and `isAltarExclusive(output.getItem())`. Log one info line with the count, like the `gear.availability` one.
- [ ] **Step 2: Build** — Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 3: Commit** `feat(insanetweaks): moved items lose their table recipes (code kept, config-gated)`.

### Task 6: JEI receptacle slots

**Files:**
- Modify: `integration/jei/RitualAltarWrapper.java`
- Modify: `integration/jei/RitualAltarCategory.java`
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang`

**Interfaces:**
- Produces: wrapper input lists in this order: 9 grid cells, then receptacles N, E, S, W. N = Abomination spectral dust only; E, S, W = every element's spectral dust (all `Element.values()` except `MAGIC`).

- [ ] **Step 1: Wrapper** builds the four extra input lists.
- [ ] **Step 2: Category layout.** Grid origin (18, 18); receptacle slots N (36, 0), E (72, 36), S (36, 72), W (0, 36); arrow at (98, 37); output (128, 36); background 146 x 104. Slot indices 0–8 grid, 9–12 receptacles, 13 output.
- [ ] **Step 3: Note line.** `drawExtras` draws `I18n.format("integration.jei.insanetweaks.ritual_altar.note")` at (0, 94), colour `0x404040`, no shadow. Lang: `integration.jei.insanetweaks.ritual_altar.note=Conquered sanctum only. Receptacles are consumed.`
- [ ] **Step 4: Build** — Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 5: Commit** `feat(insanetweaks): JEI ritual page shows the four receptacles`.

### Task 7: Codex book and ritual pages

**Files:**
- Modify: `insanetweaks/build.gradle` dependencies only: add `'Patchouli-*.jar'` to the deobf `fileTree` exclude list and add `compileOnly files(rootProject.file('libs/Patchouli-1.0-23.6.jar'))` with a comment (non-deobf is safe because the processor only names Patchouli's own methods and MC class names, which SRG does not rename).
- Copy: `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\Patchouli-1.0-23.6.jar` → `libs/` (gitignored; note: `libs/` is a junction shared with the main checkout).
- Create: `integration/patchouli/RitualPageProcessor.java`
- Create: `insanetweaks/src/main/resources/assets/insanetweaks/patchouli_books/codex/book.json`
- Create: `.../codex/en_us/templates/ritual.json`
- Create: `.../codex/en_us/categories/ritual.json`, `mechanics.json`, `content.json`
- Create: `.../codex/en_us/entries/ritual/*.json`, `entries/mechanics/*.json`, `entries/content/*.json`
- Modify: `InsaneTweaksMod.java` (at init, when `Loader.isModLoaded("patchouli")`: `PatchouliFlags.apply()`, see below)
- Create: `integration/patchouli/PatchouliFlags.java` — the only other class touching Patchouli, loaded only behind the `isModLoaded` check; calls `PatchouliAPI.instance.setConfigFlag("insanetweaks:altar_test_recipes", ModConfig.tweaks.altarTestRecipes)`. (Add it to the Global Constraints exception.)

**Interfaces:**
- Produces: book id `insanetweaks:codex`; template `insanetweaks:ritual` taking page field `"ritual": "<ritual id>"`; processor keys `s0`…`s8` (serialized ingredient, `""` for an empty cell), `rN`, `rE`, `rS`, `rW` (N = Abomination dust, others = all element dusts), `result` (serialized stack), `name` (result display name).

- [ ] **Step 1: Gradle + jar.** Run `./gradlew :insanetweaks:compileJava` — Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 2: Processor.** `setup` looks up the recipe in `RitualRegistry.getRecipes()` by `vars.get("ritual")`; `process` serializes with `PatchouliAPI.instance.serializeIngredient` / `serializeItemStack`; unknown recipe → every key `""`.
- [ ] **Step 3: Book files.** `book.json`: name "Insane Codex", landing text, `"model": "patchouli:book_brown"`, `"show_progress": false`, `"version": 1`. Template `ritual.json`: `"processor": "com.spege.insanetweaks.integration.patchouli.RitualPageProcessor"`, item components laid out as a 3x3 with the four receptacle items around it and the result to the right, plus a header with `#name#`. Category `ritual` entries: `nine_altars` (structure, receptacles and duszki, the conquered-sanctum gate, rotations, what aborts; add a `multiblock` page only if it renders, else text), `components` (pages: magic nucleus, adaptation upgrade, golden book, living nucleus, sanctuary core), `living_gear` (wand, spellblade, aegis, 4 armour), `trinket_parts` (the ten test rituals, entry `"flag": "insanetweaks:altar_test_recipes"`). Category `mechanics`: `abomination_magic`, `living_sentient_gear`, `adaptation_upgrade`, `bauble_fruits`. Category `content`: `sanctuary`, `sunken_arcane_sanctum`. Facts for every text page come from the code and the specs under `docs/superpowers/specs/` (Abomination exclusivity 2026-08-20, sanctum 2026-10-20, bauble fruits 2026-07-09, sanctuary 2026-07-21); pages are short, English, `$(br)` for breaks.
- [ ] **Step 4: Validate JSON.** Run `python -I -c "import json,glob,io;[json.load(io.open(f,encoding='utf-8')) for f in glob.glob('insanetweaks/src/main/resources/assets/insanetweaks/patchouli_books/**/*.json', recursive=True)];print('ok')"` — Expected: `ok`.
- [ ] **Step 5: Build** — Expected: `BUILD SUCCESSFUL`; `javap -c` of `InsaneTweaksMod` shows no `vazkii/patchouli` reference.
- [ ] **Step 6: Commit** `feat(insanetweaks): Insane Codex (Patchouli) with ritual pages read from RitualRegistry`.

### Task 8: Codex acquisition

**Files:**
- Create: `integration/patchouli/CodexBook.java` (no Patchouli types)
- Create: `worldgen/sanctum/SanctumCodex.java`
- Modify: `InsaneTweaksMod.java` (init, when Patchouli is loaded: `ContainerBookshelf.registerBookItem(guideBook)`)
- Modify: `init/ModRecipes.java` (Codex recipe inside the `ModElements.EXTENDED` block)

**Interfaces:**
- Produces: `CodexBook.stack()` → `ItemStack` of `patchouli:guide_book` with NBT string `patchouli:book = "insanetweaks:codex"`, or `ItemStack.EMPTY` when that item is not registered.
- Produces: `SanctumCodex.fill(World world, BlockPos pos)` — puts `CodexBook.stack()` in the first empty slot of the `IInventory` tile entity at `pos`; does nothing when the stack is empty, the tile entity is not an `IInventory`, or the inventory already holds a Codex.

- [ ] **Step 1: `CodexBook`, `SanctumCodex`, shelf registration.**
- [ ] **Step 2: Recipe.** `registerFallback(event, "codex", new ShapelessOreRecipe(...))` with result `CodexBook.stack()`, ingredients four Abomination spectral dust + `minecraft:writable_book`; only when `Loader.isModLoaded("patchouli")`.
- [ ] **Step 3: Build** — Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 4: Commit** `feat(insanetweaks): Codex in sanctum shelves (SanctumCodex.fill) and craftable`.

### Task 9: Verification and hand-off

- [ ] **Step 1:** `./gradlew :insanetweaks:test :insanetweaks:build` — Expected: `BUILD SUCCESSFUL`, all tests pass, no new warnings in touched files.
- [ ] **Step 2:** Whole-branch review against the spec (one fresh reviewer on the most capable model).
- [ ] **Step 3:** Write the in-game checklist from spec §13 into the final report, plus the one-line hooks for the Sunken Sanctum session: `SanctumCodex.fill(world, pos);` and the receptacle placement from spec §11. Do not deploy.
