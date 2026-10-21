# Sanctum Ritual Workshop and Codex — design (2026-10-21)

Branch `feat/sanctum-codex`, worktree `.claude/worktrees/sanctum-codex`, based on `9c63d5f`
(`fix/cleanroom07-patches`, content 1.28.1). Brainstormed with the user on 2026-10-21.

A parallel session owns Sunken Arcane Sanctum "batch B" and the files
`worldgen/sanctum/SanctumBuilder.java`, `SanctumLayout.java`, `SanctumPlacement.java`,
`SanctumGenerator.java`, plus the content version (`insanetweaks/build.gradle`,
`InsaneTweaksMod.VERSION`). **This work does not edit any of them, does not bump the version and
does not deploy a jar.** Everything that needs those files goes to the other session as the note in
§11.

## 1. Intent

The 3x3 Imbuement Altar dais that generates in the Sunken Arcane Sanctum is the player's **exclusive
reward for conquering the structure**. Once the sanctum is cleared, the dais becomes a workshop the
player returns to: the place where Living gear, the mod's arcane components and some trinket parts
are made. A player must not be able to build a working 3x3 in their own base. (A very hard,
end-game way to do that may come later; it is not part of this work.)

A guide book, the **Codex**, explains the ritual and its recipes first, then the mod's mechanics and
its more notable content. It is found in the sanctum and can also be crafted.

## 2. Goals, non-goals, anti-goals

**Goals**
- The Imbuement Altar cannot be broken or reclaimed by anyone, by any means (back to EBW's design).
- The 3x3 ritual only runs on a dais inside a **cleared** sanctum.
- The ritual additionally requires four lit receptacles, at least one of them Abomination.
- A starting recipe set (§7), with JEI showing the receptacle requirement.
- Table recipes of **our** items that move to the altar are disabled, but their code stays.
- A Patchouli Codex, Akashic Tome compatible, in a sanctum bookshelf and craftable.

**Non-goals**
- The Non-Euclidean Cube slot and the top-tier bauble fruit (next session).
- Developing Abomination magic further.
- Lore books, review leftovers, loot balance (later sessions).
- Any change to other mods' recipes.

**Anti-goals** (would count as failure even if everything technically works)
- A working 3x3 ritual anywhere outside a cleared sanctum, including an unregistered position that
  slips through `isCleared` (see §5).
- Removing or breaking the single-altar EBW imbuement (one altar + four receptacles), which players
  still build at home with Ancient Spellcraft's altar recipe.
- Touching another mod's crafting recipe.
- A hard dependency on Patchouli.

## 3. What exists today (commit `1edaf02`, content 1.20.1)

- `api/RitualRecipe`, `api/RitualRegistry`: a 3x3 shaped pattern of `Ingredient`s (row-major from
  the north-west corner, index 4 = centre), matched in all four rotations, never mirrored; the centre
  is both an ingredient slot and the output slot.
- `ritual/RitualAltarManager`: transient, server-side, event-driven detection (no world polling).
  Right-click, place and break on an altar mark a position dirty; the next world tick scans the nine
  candidate centres around it. Plays the ritual over `ritualDurationTicks` (default 140), aborts with a
  low sound if the grid stops matching, then consumes one item per required slot and writes the result
  to the centre altar.
- `events/RitualAltarHandler`, `init/ModRituals` (with the `safeItem`/`item()`/`ore()`/`ritual()`
  helpers that drop one ritual with a warn line instead of crashing on a missing id).
- One reference recipe, `insanetweaks:abomination_crystal` (8 Abomination dust around a plain magic
  crystal). The sanctum pre-loads the dais with the crystal and 3–5 of the dusts.
- JEI category `insanetweaks:ritual_altar` (3x3 grid, arrow, output; catalyst = the altar).
- **Salvage**: `events/ImbuementAltarSalvageHandler` makes the altar minable (hardness 3, pickaxe 2)
  and lets it drop only for a player wearing `ebwizardry:charm_silk_touch`;
  `events/ArtefactSalvageTooltipHandler` adds a tooltip line to that charm.
- `assets/insanetweaks/recipes/imbuement_altar.json`: a second altar recipe accepting magic nucleus
  instead of a charged astral diamond.

## 4. The altar is untouchable again

Remove:
- `ImbuementAltarSalvageHandler` and its two registration lines in `InsaneTweaksMod.init`.
- `ArtefactSalvageTooltipHandler` and its client-block registration.
- The config fields `imbuementAltarBreakable`, `imbuementAltarHardness`,
  `imbuementAltarHarvestLevel`, `imbuementAltarSalvageArtefacts` in `TweaksCategory`. Forge keeps
  their now-orphaned entries in existing `insanetweaks.cfg` files; that is harmless and needs no
  migration.
- The lang key `tooltip.insanetweaks.altar_salvage`.
- `assets/insanetweaks/recipes/imbuement_altar.json` (the cheaper nucleus alternative works against
  the exclusivity goal).

Keep Ancient Spellcraft's own `ancientspellcraft:imbuement_altar` recipe untouched: single altars
stay craftable for EBW's normal imbuement.

With the salvage handler gone, EBW's own `setBlockUnbreakable()` (hardness -1) is in force again;
nothing else needs doing.

## 5. Gate: a cleared sanctum only

Before a ritual **starts**, the manager checks that the centre altar lies inside a registered
sanctum that is cleared:

1. Find the sanctum whose stored bounds contain the centre's x/z. Add a lookup to
   `SanctumWorldData` for this (e.g. `Long sanctumAt(int x, int z)` returning the id or null).
   `SanctumWorldData` is not one of the parallel session's files.
2. Only if a sanctum was found, test `isCleared(id)`.

🚨 `SanctumWorldData.isCleared(id)` returns **true for an unknown id** (`r == null || r.cleared`).
Calling it without step 1 would let any 3x3 anywhere count as cleared. "Cleared" means every anchor
of that sanctum has been defeated (`anchorDone` empties the set).

The gate is checked once, at start. Cleared never reverts, so a running ritual does not need to
re-check it.

*Assistant's call:* when a full structure holds a matching recipe but the gate refuses it, play one
low "fizzle" at the centre (the same sound as an abort) so the player learns it is the place, not
the recipe, that is wrong. The Codex explains why.

The reference ritual `abomination_crystal` stays as it is.

## 6. Receptacle requirement

Applies to **every** ritual (it is a property of the structure, not of a recipe).

- **Positions.** For each horizontal direction `s` (N, E, S, W): the block at `centre + 2·s` must be
  `ebwizardry:receptacle` whose `FACING` equals `s`. That is a receptacle hanging on the outer face
  of the middle altar of that side, pointing away from the structure — exactly the test EBW uses for
  its own single altar (`state.getValue(FACING) == s` on `altar.offset(s)`).
- **Lit.** All four `TileEntityReceptacle.getElement()` are non-null, and at least one equals
  `ModElements.ABOMINATION`.
- **No clash with EBW.** A side's middle altar has one receptacle neighbour, not four, so EBW never
  turns it ACTIVE and never runs its own imbuement on it.
- **Consumed on completion** (*assistant's call*, mirroring EBW's `consumeReceptacleContents`): all
  four receptacles are set to `null`. Every ritual therefore costs four dust on top of its pattern.
- **Interrupts** (*assistant's call*): a receptacle that goes dark, or disappears, during a ritual
  aborts it, like a removed ingredient.
- **Dirty marking.** A receptacle sits two blocks from the centre, outside the current ±1 scan.
  Right-click, place and break on a receptacle must therefore mark the block it hangs on
  (`pos.offset(FACING.getOpposite())`, the side's middle altar), whose neighbourhood contains the
  centre.
- **Testability** (*assistant's call*): keep the "four lit, at least one Abomination" decision in a
  Minecraft-free helper taking element ordinals, so it gets a JUnit test like the other pure classes.

## 7. Recipes

Shared rules:
- One item per altar, so a ritual takes at most nine items; cost comes from what the items are.
- Register through `ModRituals.ritual(...)` with its missing-id safety.
- **Respect `gear.availability`**: no ritual for an item `ModItems.isGearDisabled` reports.
- Every ritual also pays the four receptacle dusts (§6) and requires a cleared sanctum (§5).

### 7.1 Moved from our crafting table (table recipe disabled, see §8)

*Assistant's call:* each ritual uses **the same 3x3 layout and ingredients as today's table recipe**.
The move itself (a cleared sanctum plus four dusts per craft) is the added cost. The user can raise
individual costs later.

| Result | Pattern source |
|---|---|
| `insanetweaks:magic_nucleus` | `ModRecipes` `magic_nucleus` (beckon drop, 2 Abomination dust, Abomination crystal, arachnida drop) |
| `insanetweaks:adaptation_upgrade` | `recipes/adaptation_upgrade.json` |
| `insanetweaks:living_wand` | `recipes/living_wand.json` |
| `insanetweaks:living_spellblade` | `recipes/living_spellblade.json` |
| `insanetweaks:living_aegis` | `recipes/living_aegis.json` |
| `insanetweaks:living_battlemage_helmet/chestplate/leggings/boots` | the four `recipes/living_battlemage_*.json` |
| `insanetweaks:sanctuary_core` | `ModRecipes` `sanctuary_nexus` |
| living nucleus | see below |

Details the builder must carry over:
- The battlemage pieces accept any of the eight elemental EBW battlemage items at wildcard meta,
  exactly as the JSON does.
- The Living armour and the aegis use `srpextra` parts; when `srpextra` is absent, `ModRecipes`
  substitutes them (`tightening_buckle` → `srparasites:infectious_blade_fragment`,
  `sturdy_armor_plates` → `srparasites:hive_scrap`, `flexible_cloth` → `insanetweaks:rupter_solied`).
  The rituals use the same substitution so they exist in both setups.
- **Living nucleus.** `insanetweaks:living_nucleus` is a clone registered only **without**
  `swparasites`; the DEv 1.2 pack has `swparasites`, where the item is `swparasites:living_nucleus` and
  its recipe belongs to that mod. So: the ritual produces whichever item backs ore
  `itLivingNucleus` (same pattern as our clone recipe); our clone's table recipe is disabled; the
  `swparasites` table recipe is **not** touched.
- `sanctuary_core` only exists with the Sanctuary module and SRP; skip the ritual otherwise.

### 7.2 Golden book: cheaper alternative, table stays

The table recipe (`recipes/golden_book.json`, yields 4) stays enabled. *Assistant's call* for the
altar version: the same layout with the nether star replaced by one Abomination magic crystal,
yielding the same 4.

### 7.3 Test recipes for other mods' trinket parts (their table recipes untouched)

Behind their own config toggle (§12), so they can be switched off without a rebuild. *Assistant's
call* on every pattern: the original layout with its rarest input swapped for an Abomination
material. All are explicitly test values.

| Result | Original (abridged) | Altar swap |
|---|---|---|
| `enigmaticlegacy:twisted_core` | ghast tear, 2 blaze powder, `earth_heart@1`, 2 redstone, ender eye | `earth_heart` → Abomination crystal |
| `enigmaticlegacy:cosmic_heart` | 4 astral dust, nether star, 2 blaze powder, diamond block, ender eye | nether star → magic nucleus |
| `enigmaticlegacy:enchanter_pearl` | emerald, 2 blaze powder, ender pearl, 2 evil essence, obsidian | 2 evil essence → 2 Abomination dust |
| `enigmaticlegacy:evil_ingot` | netherite ingot, 4 ghast tear, 4 evil essence | 4 evil essence → 4 Abomination dust |
| `enigmaticlegacy:witherite_ingot` | (shapeless) skull, 3 obsidian, 4 gold ingot | skull → Abomination crystal, laid out as a 3x3 |
| `xat:glowing_gem` | 4 glowing ingot, 4 glowing powder, diamond block | diamond block → Abomination crystal |
| `xat:mana_crystal` | 8 mana candy, glowing gem | 4 of the candies → 4 Abomination dust |
| `xat:titan_spirit` | 2 glowing gem, mana crystal, 2 dragon's breath, potato, beef | 2 dragon's breath → 2 Abomination dust |
| `bountifulbaubles:spectralsilt` | registered in code, not checked | builder looks it up and applies the same rule |
| `bountifulbaubles:enderdragonscale` | registered in code, not checked | same |

### 7.4 Later, and never

- Later: the Enigmatic Legacy hearts (`earth_heart`, `golem_heart`, `magma_heart`,
  `guardian_heart`, `abyssal_heart`), `evil_essence`, `etherium_ingot`.
- Never: `xat:dragon_gem`.

### 7.5 Bauble fruit

The existing route (corrupted fruit on a single EBW altar) stays. The dais is reserved for the single
strongest fruit, the one granting the Non-Euclidean Cube slot, designed next session.

## 8. Disabling the moved table recipes

- Extend `ModRecipes.removeRecipes` (already LOWEST priority, already removes by output for
  `gear.availability`) to also remove, when `tweaks.altarExclusiveRecipes` is on, every recipe that
  **is registered under the `insanetweaks` namespace** and whose output is one of the §7.1 results.
- The namespace filter is what keeps other mods' recipes (e.g. `swparasites:living_nucleus`) safe.
- *Assistant's call:* exclusivity only applies when a ritual can actually happen. The recipes are
  removed only if `enableAltarRituals`, `ModElements.EXTENDED` and `arcaneSanctum.enabled` are all
  true. Otherwise the table recipes stay, so a pack without sanctums or without the Abomination element
  is not left with uncraftable Living gear.
- This covers both the JSON recipes and the `*_fallback*` Java recipes, which produce the same
  outputs. The JSON files and the Java code stay in the repository.
- `golden_book` is **not** in the set.

## 9. JEI

- Four receptacle slots around the 3x3 grid, one per side, next to that side's middle cell.
- One of them shows Abomination spectral dust; the other three cycle through every element's
  spectral dust. They are display only.
- A short line on the page: requires a conquered Sunken Arcane Sanctum, and the four receptacles are
  consumed.
- Only the unrotated pattern is shown (as today).

## 10. The Codex (Patchouli)

**Verified against `Patchouli-1.0-23.6.jar` in the pack:**
- Books are discovered automatically in every active mod at
  `assets/<modid>/patchouli_books/<book>/book.json` (`BookRegistry.init` → `CraftingHelper.findFiles`).
  No registration code.
- **There is no `i18n` mode in this version.** Text lives in per-language folders
  (`<book>/en_us/categories|entries|templates/*.json`), and a missing language falls back to `en_us`.
  A Russian translation is a `ru_ru/` folder later. Line breaks are Patchouli's `$(br)`.
- Custom page templates with a Java `IComponentProcessor` (`setup(IVariableProvider<String>)`,
  `process(String)`) are supported, and `PatchouliAPI` offers `serializeIngredient`,
  `serializeItemStack`, `getBookStack` and `makeMultiblock`.
- The book item is `patchouli:guide_book` with NBT `patchouli:book = "insanetweaks:codex"`. Its
  registry name contains `book`, so Akashic Tome's default whitelist accepts it.
- Working prior art in the same pack: Trinkets and Baubles ships `assets/xat/patchouli_books/book/`.

**Book**
- Id `insanetweaks:codex`, `assets/insanetweaks/patchouli_books/codex/`. Name "Insane Codex" is the
  assistant's placeholder.
- Optional dependency: nothing outside the processor class references Patchouli types, and that class
  is loaded by Patchouli itself (by name, from the template JSON). With Patchouli absent the folder is
  inert.
- Compile access to the Patchouli API is the builder's call. Note that `libs/` in this worktree is a
  junction to the main checkout's `libs/`, and content deobfuscates every jar there, so a jar added to
  `libs/` lands on the parallel session's classpath too. Prefer a plain `compileOnly` that does not
  touch `libs/` behaviour for content, or coordinate.

**Contents**
1. *The Ritual of the Nine Altars:* what it is, where it works (a conquered sanctum), the 3x3 plus
   four receptacles (a multiblock page if it renders well, otherwise text), lighting the receptacles,
   rotations, what aborts a ritual.
2. *Rituals:* one entry per registered ritual. Each page is a custom template (3x3 grid, four
   receptacle icons, result) filled by a processor that reads the recipe **from `RitualRegistry` by
   id**, so the book can never drift from the code. Test recipes (§7.3) appear only when their
   toggle is on.
3. *Mechanics:* short entries on the mod's systems.
4. *Notable content:* Sanctuary, the Sunken Arcane Sanctum and similar.

The assistant drafts all text in English; the user edits it.

**Acquisition**
- **Sanctum:** the parallel session replaces the codex lectern with an `ebwizardry:oak_bookshelf` and
  calls `SanctumCodex.fill(World, BlockPos)`, which puts one Codex in the shelf's first empty slot.
  One per sanctum. *Assistant's call:* the shelf is **not** under the Arcane Lock (the same as the
  earlier decision for the lectern, and the same as the free hint chest), so the Codex can be read
  before the sanctum is cleared. EBW's lectern turned out to hold no item at all (`TileEntityLectern` has no slot;
  right-click opens EBW's spell browser for books in nearby shelves), which is why it is replaced.
- **EBW shelves refuse unknown items.** `TileEntityBookshelf.isItemValidForSlot` only accepts
  `ContainerBookshelf.isBook`. At init, when Patchouli is loaded, call
  `ContainerBookshelf.registerBookItem(patchouli:guide_book)`. The book then shows with EBW's default
  shelf-book model (EBW builds its model list earlier); that is cosmetic and accepted.
- **Crafting:** shapeless, four Abomination spectral dust + `minecraft:writable_book` → the Codex.
  Registered only when Patchouli is loaded and the Abomination element exists.

## 11. Note for the Sunken Sanctum session

To be sent when this design is approved:

1. **Receptacles on the dais.** For each horizontal direction `s`, place `ebwizardry:receptacle` at
   `altarCentre + 2·s` (on the dais rim, at the altars' Y), with `FACING = s`. `altarCentre` is
   `SanctumLayout.altars.get(4)`, **not** `ritualCentre` (that one is the ASC rune ritual room).
   Light a random 1–4 of them; at least one is always Abomination (`ModElements.ABOMINATION`), so the
   player can perform at least one ritual. Element via `TileEntityReceptacle.setElement`, written like
   the altar stacks. Without the Abomination element (`ModElements.EXTENDED` false) leave them dark.
2. **Codex shelf.** At `SanctumLayout.codexLectern`, place `ebwizardry:oak_bookshelf` (same facing)
   instead of the lectern, then call `SanctumCodex.fill(world, pos)`.
3. The comment on `codexLectern()` in `SanctumBuilder` ("EBW's lectern - it holds books") is wrong;
   see §10.

## 12. Config (`tweaks`)

| Field | Default | Restart | Purpose |
|---|---|---|---|
| `enableAltarRituals` | true | yes | existing |
| `ritualDurationTicks` | 140 | yes | existing |
| `altarExclusiveRecipes` | true | yes | disable our moved table recipes (§8) |
| `altarTestRecipes` | true | yes | register the §7.3 test rituals |

The four salvage fields are removed (§4).

## 13. Verification

- `./gradlew :insanetweaks:test` (new pure helper from §6) and `:insanetweaks:build` clean under
  `-Xlint:all`.
- In game (after the joint merge): altar cannot be mined; a home-built 3x3 with receptacles fizzles;
  the sanctum dais works only after clearing; receptacles consumed; JEI page; the Codex opens, its
  ritual pages show the registered recipes, it sits in an EBW shelf and in an Akashic Tome; the moved
  items have no table recipe while golden book still does; `latest.log` warns for any skipped ritual.

## 14. Calls made by the assistant (for the user to check)

- Receptacles are consumed on completion and their loss interrupts a ritual (§6).
- A refused gate fizzles audibly (§5).
- Moved rituals keep today's table layouts (§7.1).
- Golden book altar version: nether star → Abomination crystal, yields 4 (§7.2).
- Every test-recipe swap in §7.3.
- One config toggle each for disabling table recipes and for test recipes (§12).
- The placeholder book name.
- Table recipes stay when no ritual is possible (§8).
- The Codex shelf is not locked (§10).
- Sanctum records written before bounds were stored (pre-1.27.0, test worlds only) cannot host a
  ritual; no recovery.
- The starter chapter list for Mechanics (Abomination magic, Living and Sentient gear, Adaptation
  Upgrade, Bauble Fruits) and Notable content (Sanctuary, Sunken Arcane Sanctum).
