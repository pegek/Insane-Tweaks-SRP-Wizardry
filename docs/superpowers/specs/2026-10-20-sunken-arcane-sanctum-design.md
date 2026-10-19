# Sunken Arcane Sanctum — design (insanetweaks 1.24.0)

Status: approved in brainstorming 2026-10-20, awaiting spec review.
Owner mod: **insanetweaks** (content). All code, lang and player-facing text in English.

## 1. Why

This is the first generated structure in content and the showcase for three systems built
earlier:

- the 3x3 Imbuement Altar ritual (`ritual/RitualAltarManager`; the `abomination_crystal` recipe);
- the magic parasites;
- the Petrified Anchor.

It is a sealed underground mage sanctum. Only a few overgrown stones around a narrow shaft show
on the surface. Below, a ritual circle is guarded by petrified magic parasites, and the
structure must keep everything inside it from reaching the surface.

## 2. Player experience

1. **Surface.** A small overgrown ruin, a few broken mossy and cracked stone-brick wall stubs
   around a 1-block hole. Looking down the hole shows a purple shimmer: the Arcane Ward.
2. **Shaft.** The player climbs down an **Arcane Ladder** and passes through the ward. Only
   players can pass the ward.
3. **Vestibule and corridor.** These lead to the ritual chamber, which is closed by a plug of
   ASC `sealed_stone`. The player presses the `unseal_button` beside it, and ASC's unsealing
   turns the plug into breakable stone.
4. **Ritual chamber.** A raised dais holds 3x3 Imbuement Altars, some already carrying
   ingredients (the magic crystal in the centre, a few Abomination dusts). Statues stand on
   anchors around it. They wake (if the SRP phase gate allows) and their drops supply the
   missing dust. The main chest stands behind the dais.
5. **Side chambers.** 2–4 of them, randomly placed and typed, some with more statues or a
   minor chest.

## 3. Scope

In scope:

- Code-generated layout with seeded variation.
- Chunk-by-chunk placement.
- Integrity rule.
- Two new blocks (Arcane Ward, Arcane Ladder).
- Ladder movement tweak.
- Anchor leash.
- Two loot tables.
- Config.
- Debug commands.
- Tests for the pure layout and region maths.

Out of scope:

- Nether and End themes. The config already keys the structure per dimension, so they come
  later as new themes.
- `/locate` support.
- Structure-aware spawn rules.

## 4. New blocks

### 4.1 Arcane Ward (`insanetweaks:arcane_ward`)

- Translucent purple full block, rendered on the `TRANSLUCENT` layer, emitting a few
  `SPELL_WITCH` particles client-side (`randomDisplayTick`).
- **Collision:** `addCollisionBoxToList` adds the full box for every entity **except
  `EntityPlayer`**, so items, projectiles and every mob are stopped. For pathfinding it is solid
  (`isPassable` false), so mobs do not route through it.
- **Climbable for players:** `isLadder` returns true for `EntityPlayer`, so the ladder column
  stays continuous through the ward cell.
- **Unbreakable:**
  - hardness -1;
  - resistance 6,000,000;
  - `canEntityDestroy` false for everything;
  - drops nothing.
- Light passes through; it is not a full cube for rendering or occlusion.
- In the creative tab (MISC), for hand-built vaults.

### 4.2 Arcane Ladder (`insanetweaks:arcane_ladder`)

- `BlockLadder` subclass: same placement and hitbox as vanilla, its own texture.
- **Texture:** generated 16x16, using the lavender-grey palette of ASC `sealed_stone` (about
  `#9A94A1`, darker `#858090`) for the rails and rungs, with a faint purple rune line on the
  rungs. Only the palette is derived; no ASC pixels are copied.
- **Silent:** its `SoundType` has volume 0 for step, place and break. Vanilla ladder steps use
  the block's sound type, so climbing makes no sound.
- **Faster and lighter:** `ArcaneLadderHandler` handles `PlayerTickEvent` at END, on both sides
  because the local player's movement is client-authoritative. While the player is on an Arcane
  Ladder:
  - climbing (`collidedHorizontally` or jump held) sets `motionY` to `climbSpeed`, default 0.28
    against vanilla's effective ~0.2;
  - sliding down is clamped to `-maxDescent`, default 0.10 against vanilla's 0.15.

  Both values are in config.
- Hardness 0.4, mineable, drops itself. In the creative tab.

## 5. Layout

All coordinates are relative to the start's origin `O = (startChunkX*16+8, floorY,
startChunkZ*16+8)`. `floorY` is seeded from `[Floor Y Min, Floor Y Max]`, default 18–26. Boxes
are given as outer size, walls included; a room's interior is its outer box shrunk by 1.

The pure, unit-tested `SanctumLayout.generate(long seed)` returns a list of `SanctumPiece`s, each
an int box with a type. It has no Minecraft types.

| Piece | Outer size (x×y×z) | Placement |
|---|---|---|
| Ritual chamber | 13×9×13 | centred on O |
| Corridor | 5×6×L, L∈[10,16] | out of the chamber wall in a seeded direction d |
| Vestibule | 9×7×9 | at the corridor's far end |
| Shaft | 3×(to surface)×3 | above the vestibule's centre |
| Side rooms | 9×7×9 each, k∈[2,4] | seeded slots: the chamber's 3 non-d sides (3-long connector) and the corridor's 2 sides |

Layout rules:

- **Uniqueness.** Side-room slots are drawn without replacement, so rooms never overlap each
  other or the chamber. `SanctumLayoutTest` checks every seed in a sample for no overlap.
- **Footprint.** Within ±24 blocks of O, so at most a 4×4-chunk area.
- **Side-room types**, weighted:
  - `GUARD_HALL` (2 anchors), weight 3;
  - `LIBRARY` (EBW `oak_bookshelf` walls, webs, minor chest), weight 2;
  - `CRYPT` (webs, cobble rubble, skulls on the floor), weight 2;
  - `COLLAPSED` (gravel and cobble piles), weight 1.

  A side room opening into the ritual chamber is only reachable through it. One on the corridor
  is reachable before the seal.
- **Seal.** Where the corridor meets the chamber wall, the 3×3 doorway is filled with ASC
  `sealed_stone`. A `unseal_button` sits on the corridor side, in front of the plug's middle
  block. The plug touches no other `sealed_stone`, so ASC's unsealing (which spreads through
  connected `sealed_stone`) cannot reach the chamber's `sealed_stone` pillars.

### 5.1 Ritual chamber contents

- **Floor:** stone bricks with a 5×5 raised dais (one block) in the middle.
- **Altars:** 3×3 `ebwizardry:imbuement_altar` on the dais. The centre altar holds an
  `ebwizardry:magic_crystal` (meta 0). Between 3 and 5 of the 8 outer altars (seeded) hold
  `ebwizardry:spectral_dust` with Abomination meta. The stacks are set through
  `TileEntityImbuementAltar.setStack`.

  The ritual does not start by itself: `RitualAltarManager` only reacts to right-click, place and
  break. When the player adds the missing dust by right-click, the recipe completes. Without the
  Abomination element (`ModElements.EXTENDED` false), the altars are left empty.
- **Statues:** 2–4 Petrified Anchors (seeded) on the dais corners and flanks, each with 4 free
  blocks above for the statue.
- **Pillars:** four `sealed_stone` pillars in the corners. Chiseled stone-brick accents.
- **Chest:** one on the far side of the dais (loot `insanetweaks:chests/arcane_sanctum`).
- **Light:** glowstone recessed in the ceiling every 4 blocks, to cut natural spawns inside.
  SRP's `EntityAIBlockLight` may break some of it. That is accepted: it only makes the room
  darker and never opens the shell, because glowstone is set into the ceiling cells of the
  interior and not into the shell.

### 5.2 Palette

- **Walls:** stone bricks, with each block seeded to plain 60%, mossy 25% or cracked 15%.
- **Shell:** every piece's outer box is wall. Interiors are air, then decoration.
- **Webs:** in corners, about 1 per 20 interior cells. Vines on walls where a room touches the
  shaft.
- **Foreign blocks:** ASC and EBW blocks are looked up by id; if one is missing, it is replaced
  with chiseled stone brick and one warning is logged.

### 5.3 Surface ruin

- Built in the entrance chunk, around the shaft top at the real surface height H (the top solid
  non-leaf block at the shaft column).
- A 5×5 ring: 3–5 seeded wall stubs of mossy or cracked stone brick, 1–2 high, plus vines and a
  few cobblestone blocks half sunk into the ground.
- **No ruined altar.**

### 5.4 Shaft

- **Shape:** 1×1 interior with 3×3 stone-brick walls, from the vestibule ceiling up to H. It is
  the only way in, and most parasites (width > 1) cannot fit.
- **Ladder:** Arcane Ladders on the shaft's south inner wall over its full height.
- **Ward:** the Arcane Ward replaces the ladder cell at H−2. It is climbable for players, so the
  column stays continuous.
- **Top:** the cell at H stays open (the hole).

## 6. Generation

**Regions.** The world is divided into regions of `Region Size` chunks (default 32).

- Each region has one candidate start chunk, seeded from `(worldSeed, regionX, regionZ)` and
  kept 4 chunks away from the region edges.
- The candidate is used with `Chance` (default 0.5).
- The rule lives in the pure, unit-tested `SanctumRegions`.

**Validity, decided once per start.**

- The world's dimension is in `Dimensions` (default `{0}`; 150 always refused).
- The biome at O, from the biome provider (no chunk generation), is not one of the BiomeDictionary
  types `OCEAN`, `RIVER` or `BEACH`.
- **Integrity rule.** When the first chunk of a start is populated, every other chunk the start
  covers must be either not yet generated (`ChunkProviderServer.isChunkGeneratedAt` false) or
  loaded and not yet populated. Anything else counts as already populated, which makes the start
  invalid. In an already-explored world this skips a start instead of producing a half-built,
  leaking structure. The decision (valid or invalid) is stored per start in `SanctumWorldData`
  (`WorldSavedData`, per dimension, key `insanetweaks_sanctums`).

**Chunk-by-chunk placement.**

- `SanctumGenerator` implements `IWorldGenerator`. For chunk (cx, cz) it finds the starts whose
  footprint intersects the populate box `x∈[cx*16+8, cx*16+23]`, `z` likewise (the vanilla
  structure convention). These boxes tile the world, so every block is written exactly once and
  never outside the already-generated area.
- For each valid start, `SanctumBuilder` writes only the part inside that box, in three passes
  over all pieces: walls, then interiors, then decoration and tile entities. Doorways therefore
  cut through neighbouring walls.
- Writes use flag 2. Tile-entity content (altar stacks, chest loot table and seed) is set right
  after its block.
- Caves are carved before a chunk is populated, so a cave crossing the structure is walled off
  by the shell in that same chunk.
- Layouts are cached in a small LRU keyed by the start's chunk position, so the four chunks of
  one start do not regenerate it four times.

**Debug commands** (`/itweaks`, op level 2):

- `sanctum place` builds a full sanctum with its origin at the player's position, ignoring the
  integrity rule and validity, in already-loaded chunks;
- `sanctum nearest` prints the nearest valid start in the current dimension within 16 regions,
  using the same validity rules except the integrity rule, which can only be decided at
  generation time.

## 7. Safety: keeping parasites in

1. **Shell.** Every piece is fully walled. SRP parasites do not dig (bytecode survey: the only
   block breaking is explosions, which respect `canEntityDestroy` and hardness, and
   `EntityAIBlockLight`), so an intact shell is a barrier.
2. **Ward.** The only exit, the 1-wide shaft, is closed to every non-player entity.
3. **Leash** (applies to every Petrified Anchor, not only those in the sanctum).
   - **Tagging:** when an anchor creates its mob it writes `InsaneTweaksAnchorHome` (the anchor's
     position, long) and `InsaneTweaksAnchorDim` into the mob's entity data. EBW saves the whole
     entity NBT into the statue, so the tags survive into the released mob however it was
     released.
   - **Check:** `AnchorLeashHandler`, on `LivingUpdateEvent` every 40 ticks for tagged entities,
     server only. A mob more than `Leash Radius` blocks (default 32) from home, in the same
     dimension, is teleported back next to the anchor; it tries `attemptTeleport` around
     `home.up()` and falls back to `setPositionAndUpdate`.
   - **Off switch:** 0 disables the leash.
4. **Seal and phase gate.** The ritual chamber's statues are behind the plug, and they stay
   sealed until the SRP phase gate opens (Petrified Anchor 1.23.1).

## 8. Loot

- **`chests/arcane_sanctum`** (main):
  - `ebwizardry:magic_crystal` 3–8;
  - Abomination `spectral_dust` 1–3 (with `insanetweaks:abomination_meta`);
  - `insanetweaks:magic_nucleus`, 40% for 1;
  - one pool drawing from `ebwizardry:chests/wizard_tower` (`loot_table` entry).
- **`chests/arcane_sanctum_minor`:**
  - `ebwizardry:magic_crystal` 1–3;
  - paper or book 1–3;
  - gold nuggets 2–6;
  - 25% for one Abomination dust.
- Both registered with `LootTableList.register`, so they are not silently empty.

## 9. Config (new root category `arcaneSanctum`, `ArcaneSanctumCategory`)

| Key | Default | Notes |
|---|---|---|
| `Enabled` | true | requires restart; registers the generator |
| `Dimensions` | `{0}` | 150 always refused |
| `Region Size` | 32 | chunks, 8–128 |
| `Chance` | 0.5 | |
| `Floor Y Min` / `Floor Y Max` | 18 / 26 | |
| `Excluded Biome Types` | `OCEAN`, `RIVER`, `BEACH` | |
| `Leash Radius` | 32 | 0 = off; in `entities.petrifiedAnchor` |
| `Climb Speed` / `Max Descent` | 0.28 / 0.10 | in `tweaks` |

## 10. Lang (en_us)

```
tile.insanetweaks.arcane_ward.name=Arcane Ward
tile.insanetweaks.arcane_ladder.name=Arcane Ladder
config.insanetweaks.category.arcane_sanctum=Sunken Arcane Sanctum
```

## 11. Testing

- **JUnit:**
  - `SanctumRegionsTest`: the same seed and region give the same candidate; candidates stay
    inside the region margin; the chance boundary works.
  - `SanctumLayoutTest`: over 500 seeds, there are no overlapping rooms; the footprint stays
    within ±24; every room is reachable from the vestibule by door adjacency; the seal sits
    between the corridor and the chamber; there are 2–4 side rooms.
  - `SanctumBoxTest`: intersection and clipping.
- **Bytecode checks:** side safety for the generator, builder, blocks and handlers. The ward and
  ladder blocks reference no client types except in `@SideOnly` methods (`randomDisplayTick`,
  `getBlockLayer`).
- **In game, DEv 1.2:**
  - `sanctum place`: walk the whole structure. The shell is complete (spectator), the ward stops a
    spawned zombie and parasites, and the ladder is silent and faster.
  - The seal opens with the button, and the altars hold the pre-placed ingredients.
  - Adding the missing dust starts the ritual.
  - The leash pulls a statue mob back when it is lured 40 blocks away.
  - In a new world, `sanctum nearest` finds a start; walk there and find it generated whole.
  - In an old world, a start next to explored chunks is skipped (the integrity rule).
