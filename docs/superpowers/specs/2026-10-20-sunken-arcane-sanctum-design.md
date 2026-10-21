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

## 12. Revision 1.25.0 (2026-10-21): first playtest feedback

### 12.1 Anchors

- **Reliability (feedback 4).** When an anchor creates its mob, it stores a copy of the mob's NBT
  (`MobData`). On waking it removes the statue **without** EBW's release and spawns the mob
  itself from that copy. This removes the failure where a freshly loaded statue's `creature` had
  not yet been rebuilt and the break released nothing.
  - Anchors built before 1.25.0 have no copy, so they fall back to EBW's release.
  - A statue standing on an active anchor can **never** be broken or blown up; the old SRP phase
    gate decided only *when* it wakes. The guard message differs depending on whether the gate is
    open.
- **Release delay (feedback 9).** After `Crack Ticks` the statue shatters (debris burst). The mob
  appears `Release Delay Ticks` later, 10 by default.
- **Persistence.** The spawned mob gets `enablePersistence()` and, for SRP parasites,
  `cannotDespawn(false)`: SRP's `canD` flag, where false means it cannot despawn.
- **Block stages.** `PropertyBool awakened` is replaced by `PropertyEnum stage`. Metadata stays
  compatible with existing saves.

  | Stage | Meta | Tile entity | Can be mined | Look |
  |---|---|---|---|---|
  | `dormant` | 0 | yes | no | intact |
  | `spent` | 1 | no | yes, drops cobble | cracked |
  | `awake` | 2 | yes, tracks its mob | no | cracked |

- **Tile entity phases.**
  - `EMPTY` 0, `DORMANT` 1, `WAKING` 2 and `SPENT` 3 keep their old numbers, so saved anchors still
    load.
  - New `RELEASING` 4 is the delay before the mob appears.
  - New `AWAKE` 5: the anchor watches its mob by UUID. It moves to `SPENT` when the mob dies (a
    `LivingDeathEvent` reaches the anchor if it is loaded, or the sanctum record otherwise), or when
    the mob cannot be found for 60 s.
- **Return home (feedback 1).** Handled by `AnchorLeashHandler` every 40 ticks.
  - A tagged mob with no non-spectator player within `Return Radius` (40) for `Return Seconds` (40)
    is teleported next to its anchor and heals `Return Heal Fraction` (0.25) of its **missing**
    health. Its target and path are cleared.
  - The 32-block leash stays as the hard limit.

### 12.2 Layout: two levels (feedback 5, 8, 9 and the revised main room)

Level 1 (`F1`, from config, clamped to ≥ 17) holds:

- the vestibule under the shaft;
- a 13×13 hall;
- 2–4 side rooms, filling seeded slots: two off the hall and two off the vestibule.

The side-room types are drawn without replacement from:

| Type | Furniture | Anchors | Chest |
|---|---|---|---|
| `LIBRARY` | EBW bookshelves facing the room, plus either an enchanting table or an EBW arcane workbench (seeded variant) | yes | library |
| `FORGE` | ASC arcane anvil, anvil, furnaces | yes | forge |
| `WORKSHOP` | ASC scribing desk, sage desk, sage lectern, crafting table | yes | workshop |
| filler: `CRYPT` or `COLLAPSED` | rubble | none | none |

- At most one filler room appears, so a sanctum can come out bigger.
- A room with anchors gets 1, plus a 25% chance of a second, pinned from `Room Anchor Pool`:
  `insanetweaks:sim_wizard;3`, `insanetweaks:sim_battlemage;1`.

A **10-block straight stairway** (stone-brick stairs, 4 blocks of headroom) leads from the hall down to
level 2 (`F2 = F1 − 10`):

- **Antechamber**, with the seal.
- **Ritual chamber**, now **17×17 outer with 8 blocks of headroom**:
  - the whole shell (walls, floor, ceiling) is ASC `sealed_stone`;
  - 3–5 magic-parasite anchors;
  - the same altar dais;
  - a main chest.
- **The seal plug** stays 3×3 with the `unseal_button` at its centre. It is surrounded in the wall
  plane by a ring of chiseled stone bricks, so ASC's unsealing (which spreads through touching
  `sealed_stone`) cannot reach the sealed shell.

The footprint is about 57×29 blocks, within ±28 of the origin.

### 12.3 Arcane Lock (feedback 7)

- **Locking.** Every chest and every EBW bookshelf in a sanctum gets EBW's native lock at
  generation: tile data `arcaneLockOwner` set to a UUID derived from the sanctum id, plus
  `InsaneTweaksSanctum` holding the id itself. EBW's own handlers then refuse opening, breaking,
  mob griefing and explosions, and EBW renders the lock.
- **Registering.** When a start is judged valid, and on `sanctum place`, `SanctumWorldData` records
  the sanctum: its id, every anchor position and every container position.
- **Defeating anchors.** An anchor counts as defeated when its mob dies (the mob carries
  `InsaneTweaksSanctum` too) or when its tile entity gives up after the 60 s timeout.
- **Unlocking.** When the last anchor is defeated, the record is marked cleared. Every loaded
  container is unlocked: the key is removed and EBW's `PacketSyncArcaneLock` is sent to nearby
  players. Players within 64 blocks get the message "The sanctum's seals fade."
- **Unloaded containers.** A container that was not loaded at that moment is unlocked lazily: a
  highest-priority interaction handler strips the lock before EBW's handler sees it.

### 12.4 Loot (feedback 3 and the main-room bauble)

- **Chests never contain `magic_nucleus`.**
  - The main chest holds: crystals; Abomination crystal and dust (`magic_crystal` and
    `spectral_dust`, both with `insanetweaks:abomination_meta`); SRP parasite parts (`ada_*_drop`,
    `assimilated_flesh`, `vile_shell`, `infectious_blade_fragment`); and the EBW wizard-tower pool.
  - The forge and workshop chests have their own tables: armour-upgrade components for the forge,
    scrolls and ink materials for the workshop.
- **Main-chest bauble.** With `Main Chest Bauble Chance` (0.25), the main chest also holds one
  random item implementing `baubles.api.IBauble`. That covers EBW artefacts, rings and amulets from
  any mod. Items without a creative tab, and those on `Bauble Blacklist`, are skipped.
- **EBW bookshelves.** They face into the room. Each gets `Bookshelf Book Rolls` (3) tries at
  `Bookshelf Book Chance` (0.05) for a spell book, drawn from spells enabled for books and treasure:
  - 50%: an Abomination spell;
  - 25%: any Master spell;
  - 25%: any Advanced spell;
  - an empty bucket falls back to Advanced.

### 12.5 Small items

- **Arcane Ladder sound (feedback 6).** Every 8 ticks of actual vertical movement on an Arcane
  Ladder, a soft note-block chime plays at a random pitch between 1.4 and 1.9, at volume 0.25. It
  is heard by everyone nearby.
- **Command help (feedback 2).** `/itweaks` help lists `sanctum place|nearest`, and tab completion
  covers the subcommands.
- **Deferred (feedback 10).** Making the Imbuement Altar unminable again, a ritual book, more recipes
  and a richer JEI page all belong to a separate conversation.

## 13. Revision 1.26.0 (2026-10-21): second playtest

### 13.1 Fixes

- **Arcane Lock never engaged.**
  - EBW reads the owner with `NBTTagCompound.hasUniqueId` / `getUniqueId("arcaneLockOwner")`, which
    means two longs, `arcaneLockOwnerMost` and `arcaneLockOwnerLeast`. 1.25.0 wrote a string, so
    neither the lock nor the purple overlay ever engaged. The lock is now written with
    `setUniqueId`, and unlocking removes both longs.
  - EBW's `canBypassLocks` lets creative players through, so the lock must be tested in survival.
- **Return after a chunk unload.**
  - The 40 s countdown runs only while the mob is ticked.
  - When an anchor mob joins the world (`EntityJoinWorldEvent`, including chunk loads) more than 4
    blocks from home with no player within `Return Radius`, it is flagged. On its next 40-tick check
    it returns home and heals the configured fraction.
  - It is flagged rather than moved on the spot, because moving an entity during its own join can
    leave it in the wrong chunk list.
- **Missing-mob timeout.** An `awake` anchor remembers its mob's last known position. It counts the
  60 s only while that position's chunk is loaded. An anchor in a loaded chunk can therefore no
  longer give up on a mob that sits in an unloaded one, which would wrongly count it as defeated
  and lift the lock.
- **`ancientspellcraft:sage_desk`** is not registered in ASC 1.8.3 (the blockstate is an orphan). The
  workshop uses `ancientspellcraft:sphere_cognizance` instead.
- **Scribing desk.** ASC's model fronts the opposite way, so it is placed facing away from the
  room's door.

### 13.2 Ritual chamber

- **Seal.** The chiseled frame around the seal is removed and the whole chamber is `sealed_stone`.
  Unsealing may now spread through the shell; that is accepted, because the parasites are held by
  their anchors (leash and return).
- **Second chest.** 50% chance of a second main-loot chest (`-25, F2, -2`), not adjacent to the first.
- **Shulker box.** 25% chance of a purple shulker box (`-25, F2, 2`) with 2–4 strong potions. It is
  locked like the chests.
- **Anchors.** 3–5 anchors, plus a 25% chance of a sixth, from the 7 spots.

### 13.3 Lighting

- **Main light.** Permanent EBW `magic_light` blocks along the walls of every room, at fixed spots per
  room type. The tile entity's `setLifetime(-1)` makes them permanent.
- **Glowstone.** The old ceiling glowstone spots remain only as a sporadic extra: each is placed with
  a 15% seeded chance.

### 13.4 Side rooms

- **Slots.** Six instead of four. The two new ones sit on level 2, north and south of the
  antechamber, before the seal. A sanctum gets 2–5 rooms of distinct types:
  - library;
  - forge;
  - workshop;
  - **teleport**;
  - one filler: crypt or collapsed room.
- **Teleport room.** A ring of `ebwizardry:transportation_stone`, seeded 3×3 or 4×4 with an empty
  centre. It holds 1 anchor plus a 25% chance of a second, placed away from the ring.
- **Traps.** 35% per side room:
  - tripwire hooks with string across the row just inside the door;
  - the hook on one side is attached to a dispenser built into the side wall, facing across the
    room, loaded with 2–4 EBW scrolls of one offensive spell from `Trap Spells` (default: magic
    missile, firebolt, ice shard, poison; ids that are missing or cannot be cast by a dispenser are
    skipped).
- **Walls.** Vines grow on inside walls (about 6% of wall-adjacent cells, attached to the wall).
  About 1.5% of shell blocks are silverfish blocks in a matching stone-brick variant.
- **Décor**, side rooms only, each piece independent and seeded, placed on free floor cells away
  from the door line and the trap row:

  | Piece | Chance | Details |
  |---|---|---|
  | Armour stand | 20% | 1–2 random iron, chain or gold pieces |
  | Shulker box | 8% | strong potions, locked |
  | Ender chest | 10% | |
  | End portal frame | 10% | 30% with an eye |
  | Bed | 15% | random colour |
  | Painting | 25% | on the back wall |
  | Skull | 20% | 30% of them wither skeleton |

- **Library.**
  - 20% of the EBW-bookshelf spots become `ebwizardry:gilded_wood` and 20% vanilla bookshelves; the
    rest stay EBW bookshelves.
  - The book chance per roll rises to 8%.

### 13.5 Loot

No gold nuggets, plain magic crystals, paper or blank scrolls in any sanctum chest.

- **Main chest.**
  - Abomination crystal and dust;
  - SRP parts;
  - one roll of `ebwizardry:random_spell` books (advanced or master);
  - diamonds or ender pearls;
  - the wizard-tower pool.
- **Library chest.**
  - enchanted books (`enchant_with_levels` 25–30, treasure);
  - spell books (`random_spell`);
  - experience bottles.
- **Forge chest.**
  - armour-upgrade components (guaranteed one);
  - diamonds;
  - an enchanted iron or diamond weapon or armour piece.
- **Workshop chest.**
  - spell scrolls (`random_spell`);
  - Abomination dust;
  - ender pearls;
  - a strong potion.
- **Shulker potions.** Strong healing, strong regeneration, strong strength, strong swiftness, long
  fire resistance and long night vision, as splash or plain potions.

- **Ancient Spellcraft "ancient knowledge"** (items used with the Sphere of Cognizance and the
  Scribing Desk; ids taken from ASC's own `chests/ancient_vault` table, so they exist in 1.8.3):
  - **main chest:** one knowledge roll from the four stone tablets (`stone_tablet_small`,
    `stone_tablet`, `stone_tablet_large`, `stone_tablet_grand`), `ancient_spellcraft_spell_book`
    (`random_spell`), `theory_scroll`, `runic_plate` (`random_spell`),
    `ancientspellcraft:subsets/ritual_books` and `ancientspellcraft:subsets/rare_scrolls`;
  - **library chest:** `ancient_spellcraft_spell_book`, `theory_scroll` or `stone_tablet_small`;
  - **workshop chest:** `ancient_spellcraft_scroll`, `runic_plate` or `theory_scroll`.

  EBW subsets add quality without filler: `arcane_tomes`, `wand_upgrades` and `wizard_armour` in the
  main chest, `armour_upgrades` in the forge chest.

### 13.6 Deferred

- **Runestone walls.** The user is still testing; Abomination runestone may be added first. Walls
  stay stone brick for now.

## 14. Revision 1.27.0 (2026-10-21): third playtest

Version note: another session shipped content 1.26.1 (Wit's Hunger) on this branch, so this
revision is **1.27.0**. It also carries the two post-review fixes already committed in `f76e797`
(trap placement order, teleport anchors off the vine column), which never reached the pack.

Why: the 1.26.0 test showed the lock, loot and trap mechanics working. This round is about pacing
and payoff:
- less light;
- an unobstructed stair;
- anchor mobs that hit harder and call for help;
- a visible payoff when a statue falls or the sanctum is cleansed;
- one new side room;
- two advancements that tell the player where they are and what they did.

**Non-goals:**
- No AI changes for sim wizards, sim battlemages or parasites. The user will rework their AI
  separately. Everything below is a *mechanic attached to anchor-spawned mobs*, not behaviour.
- Anchor mobs still teleport home and wait instead of re-petrifying (§12.1). This is accepted as is.

### 14.1 Lighting

- Every room keeps **2–3** of its `magic_light` spots, chosen by the layout seed, instead of all of
  them. "Room" means each side room, and each of the five core pieces separately.
- Every magic light sits **one block higher** than in 1.26.0.
- Glowstone spots are unchanged (sporadic, 15%).

### 14.2 Stair headroom

The doorway between the antechamber and the stairway (local x = -4, z -1..1) is three blocks high.
A player stepping on or off the bottom stair is lifted half a block and hits the three bricks above
it. That carve becomes **four high** (y F2..F2+3). Nothing else changes in the descent.

### 14.3 Anchor mobs

- **Room pool.** `Room Anchor Pool` default becomes `sim_wizard;3`, `sim_battlemage;2`, so
  battlemages are noticeably more common. The DEv 1.2 instance config gets the same edit: back it up
  first and change only that one line.
- **Buffs.** Every mob released from a Petrified Anchor (any anchor, in a sanctum or hand-placed)
  gets **+20% max health**. Sim wizards and sim battlemages also get **+15% movement speed**.
  - Both are named attribute modifiers with fixed UUIDs, so they never stack.
  - The mob spawns at its new full health.
  - The two values are config fields.
- **Reinforcements.** These apply only to mobs released from **ritual-chamber anchors** (the
  unpinned, parasite pool).
  - The first time such a mob drops **below 25% health**, it wakes every still-dormant anchor within
    **10 blocks**.
  - Woken anchors release their mob at once, as if a player had come near, with the usual crack and
    release delay. The phase gate (`minPhaseToWake`) still applies.
  - Each mob calls only once. A reinforcement that is itself a chamber parasite can call in turn.
  - Radius and threshold are config fields.

### 14.4 Payoff: anchor falls, sanctum cleansed

- **Anchor falls (sanctum anchors only).** When the mob of an anchor inside a sanctum dies:
  1. the anchor plays a sound and a particle burst;
  2. the block is removed;
  3. with a chance, **one artefact** drops as an item entity on its spot. There is no chest.
  - **Chance:** **20%** for room anchors (sim wizard or sim battlemage), **50%** for chamber anchors
    (parasites).
  - **Loot table:** a new `insanetweaks:anchor_artefact`, drawing only from the EBW and ASC artefact
    subsets: `ebwizardry:subsets/{uncommon,rare,epic}_artefacts` and
    `ancientspellcraft:subsets/{uncommon,rare,epic}_artefacts`. Weights favour uncommon over rare
    over epic. It is artefacts-only for testing and will be tuned later.
  - Hand-placed anchors outside a sanctum keep today's behaviour (SPENT, cracked pedestal, no drop).
  - The anchor's sanctum record still counts it as done, so lifting the lock works exactly as now.
- **Sanctum cleansed.** Every time the last anchor of a sanctum falls and its locks lift, a sound
  plays for the players near the sanctum, together with the existing "unsealed" message.

### 14.5 Advancements

These form a small `insanetweaks:sanctum/` branch, not a new tab. They hang under the existing
`insanetweaks` advancement root, or under a new root if none exists. Both are granted from code
through an `impossible` criterion.

- **`insanetweaks:sanctum/found`, "Something Sleeps Below".**
  - The description warns that a dangerous and valuable structure lies nearby.
  - It is granted the first time a player is inside a sanctum's horizontal footprint plus an 8-block
    margin, at any height. So it fires at the surface entrance, before the descent.
  - It grants no mana.
- **`insanetweaks:sanctum/cleansed`, "Sanctum Cleansed".** A child of `found`, granted to the players
  near the sanctum when its last anchor falls.
- **Manacore.** Add `insanetweaks:sanctum/cleansed=15` to `advancements.bonuses` in the instance's
  `manacore.cfg`: back it up first and add only that one line. The default bonuses sum to 230
  against a cap of 250, so it fits.

### 14.6 Ritual room (new side room type `RITUAL`)

ASC rituals are flat patterns of placed runes (`ancientspellcraft:placed_rune` with a `TileRune`
holding the rune item), up to 5×5, with one centrepiece. Only the master rune checks the pattern;
an incomplete pattern never starts.

- **Which ritual.** The room holds **one of three rituals**, chosen by seed: **Rejuvenation**,
  **Bonfire** or **Elemental Attunement**.
  - Excluded: Forest (it is underground), Arcane Barrier (needs a 5×5 block platform), Condensing
    (takes days) and Warlock Attunement (too niche).
  - The patterns are read from ASC's own `assets/ancientspellcraft/rituals/*.json`, copied in as
    constants and rotated with the room.
- **Always incomplete.** The **centrepiece rune is never placed**, and nothing in the structure
  supplies it. Elemental Attunement also needs its catalyst: an elemental magic crystal thrown onto
  the centrepiece.
  - Why the centrepiece: the rune the player places carries the player as caster, so the ritual's
    effect has someone to act on. Bonfire binds the caster's spawn point, and Attunement attunes the
    caster.
  - The builder must **verify, in ASC bytecode or in game,** that a player-placed centrepiece makes
    the player the ritual's caster.
  - If it does not, choose the missing rune so that it does. If no choice works for a ritual,
    replace that ritual and say so.
- **The hint book.** A `minecraft:written_book` on an ASC `sage_lectern`, or in a small chest if the
  lectern cannot hold it. Its pages are text components translated from `en_us.lang`. They give:
  - the ritual's name;
  - what it does, in a short paraphrase rather than ASC's own text;
  - how it activates;
  - **exactly what is missing**: the centrepiece rune by name, plus the crystal for Attunement;
  - how to make the missing rune: Blank Rune + Arcane Compound + dye, as in that rune's ASC recipe.
- **Room rules.**
  - It joins the side-room type list, giving up to 6 types for 6 slots.
  - It is not an anchor room.
  - It gets the usual lights, décor and trap chance.
  - Décor stays off the ritual footprint.

### 14.7 EBW bookshelf extras

Each sanctum EBW bookshelf rolls the existing spell-book roll (`Bookshelf Book Rolls`, `Bookshelf
Book Chance`) plus **two extra rolls**. Like the rest of the generation, they are positional and
deterministic. Each extra roll gives:

- **3%:** an enchanted book via treasure-allowed random enchanting at level 30;
- **4%:** a book from the insanetweaks pool, picked uniformly from:
  - every enchantment registered under the `insanetweaks` namespace, at **level 1**, as an enchanted
    book;
  - every Property Book whose property is `bookGrantable`.

Both chances and the roll count are config fields.

### 14.8 Later (recorded, not in this revision)

- A visual overhaul of the structure: runestone walls, possibly Abomination runestone.
- Better loot tables; magic crystals may return to the loot.
- The assimilated-dragon lair as a second main room, after which the sanctum may split into two
  structures.
- Rethinking how the player enters the structure.

### 14.9 Decisions after the builder check

- **The quest gate is deliberately broken here (user).** Insanetweaks enchantments are otherwise
  quest-only (`EnchantSourceGuard` and three mixins). Sanctum bookshelves are an explicit
  exception, and the curse (Sentient Codex) stays in the pool. The bypass must be narrow: books
  written by the sanctum builder straight into a shelf's inventory. Nothing else may loosen the
  guard. Document the exception next to the guard.
- **Reinforcements wake every intact anchor within 10 blocks, through walls (user).** That includes
  side-room sim anchors next to the chamber. The player is expected to clear the side rooms first.
  - Only sanctum chamber anchors call for help; hand-placed anchors do not.
  - A woken mob is aimed at the caller's attack target if that is a player, otherwise at the
    nearest player within 16 blocks (assistant).
- **What counts as an anchor falling (assistant).**
  - An artefact rolls only when the anchor's own mob is confirmed dead. That is the existing
    `markMobGone` path, seen while the anchor is loaded.
  - Any other ending removes the sanctum anchor with the sound and particles but without a roll:
    the missing-mob timeout, a failed spawn, an empty release, or a death noticed only later through
    the sanctum record.
  - No save-format change.
- **The advancements get their own tab (assistant).**
  - `sanctum/found` is the root, with a stone-brick or sealed-stone background.
  - `sanctum/cleansed` is its child.
  - The existing sentient-gear root is left alone.
- **Side rooms become 3–6 (user, 2026-10-20).** One more room on average; RITUAL joins the type pool.
  Six slots and six types, so the footprint is unchanged.
- **No compatibility for half-built sanctums (assistant).** The DEv 1.2 world is a test world, the
  same stance as §13 took on 1.25.0 locks.
- **The hint book is never locked (assistant).** Whatever holds it, lectern or chest, stays outside
  the Arcane Lock, because it exists to help during the run.

### 14.10 Calls made by the assistant (for the user to check)

- The wizard-to-battlemage weight is 3:2.
- The buffs apply to every anchor mob, in or out of a sanctum.
- A reinforcement can chain.
- The ritual pool is Rejuvenation, Bonfire and Elemental Attunement.
- The missing piece is the centrepiece rune.
- The book sits on a sage lectern.
- The "found" trigger is the footprint plus 8 blocks, at any height.
- The artefact table is weighted uncommon over rare over epic.
- The insanetweaks book pool is uniform.
- The version is 1.27.0.

As built, 1.27.0 differs in three ways:
- the pool is Rejuvenation, Bonfire and **Condensing**, because Elemental Attunement can never start
  in ASC 1.8.3;
- the hint book is in an unlocked plain chest, not on a sage lectern;
- side rooms are 3–6.

## 15. Revision 1.28.0 (2026-10-21): fourth playtest, gameplay batch "A"

Why: in 1.27.0 the mechanics work. This round is about three things:
- tightening the sanctum against cheese (digging in, being teleported out);
- making its threat legible before the player commits;
- making its rewards feel worth the risk.

The visual overhaul ("B": runestone edges, per-dimension palettes, Nether/End/dim-111 placement)
is a separate, later revision. So are the lore books and the Akashic-Tome-style guide book. §15.9
lists everything that is deliberately not in this revision.

### 15.1 Lighting

- Every room keeps **1–2** of its magic-light candidates.
- The ritual chamber keeps **2–3**.

Heights stay as in 1.27.0.

### 15.2 The hall (the level-1 room the stairway leads down from)

- **Décor:** it gets the same décor kinds and chances as a side room (§13.4). Two areas stay clear:
  - the straight walkway between its three doors (local z -1..1 for the full hall length);
  - every door line, ±2 cells, as in the side-room rule.

  Paintings hang on the hall's long walls.
- **Anchors:** it gets **2–3 anchors** pinned from the room pool, so sim wizards and battlemages.
  - They go off the walkway and off interior edge columns (§14 vine rule).
  - They are registered with the sanctum like every other anchor.
- **Artefact chance:** a fallen room anchor (sim) now drops an artefact with **10%** chance (was
  20%). Chamber anchors stay at 50%. Room-anchor rooms and the hall share this one value.

### 15.3 Ritual chamber

- **Anchors:** always **4**, plus **two independent 25% rolls** for a fifth and a sixth. The seven
  chamber spots stay as they are.
- **Codex lectern:**
  - an ASC `sage_lectern` stands on the chamber floor beside the 3×3 altar, facing it;
  - it stays **empty** in this revision;
  - the guide book (Akashic Tome compatible, explaining the 3×3 ritual recipe first) and its
    mechanics belong to a separate session;
  - the layout records the lectern's cell, so that session only has to fill it;
  - it is not a container and is not locked.

### 15.4 Ritual room

- **Anchors:** the room now holds anchors like the other room types:
  - one always, plus a second at 25%;
  - pinned from the room pool;
  - off the rune footprint.

  The room is too small for a 5×5 footprint plus non-edge cells, so its anchors may stand on an
  interior edge column. In that case the builder's vine pass must skip every anchor's column, in
  every room.
- **Two chests:**
  - The hint chest stays unlocked and holds only the book.
  - A second, ordinary sanctum chest with the library ("minor") loot table is added. It is locked
    like every other sanctum chest.
- **Décor:** the room keeps the usual side-room décor off the footprint.

### 15.5 Loot

- **Parasite parts instead of the bauble.**
  - The main chest's bauble roll (`Main Chest Bauble Chance`) defaults to **0**. The code stays, so
    a pack can turn it back on.
  - Every sanctum chest table gets a **"parasite relic" pool**: one roll, mostly empty, otherwise
    one of the parts below.
  - The relic pool hits **50%** in the main chest and **25%** in the library, forge and workshop
    chests.
  - The existing main-chest `parasite_parts` pool stays as it is (flesh and common parts). Where a
    part appears in both pools, that is fine.
  - The parts are verified as items in SRP 1.10.9 (by `models/item/`, not by lang):

    | Item id | In-game name |
    |---|---|
    | `srparasites:vile_shell` | Vile Shell |
    | `srparasites:infectious_blade_fragment` | Infectious Blade Fragment |
    | `srparasites:ada_yelloweye_drop` | Yelloweye Bone |
    | `srparasites:ada_summoner_drop` | Summoner Core |
    | `srparasites:ada_manducater_drop` | Manducater Orb |
    | `srparasites:ada_longarms_drop` | Longarm Armor Fragment |
    | `srparasites:ada_bolster_drop` | Bolster Husk |
    | `srparasites:ada_devourer_drop` | Devourer Tentacle |

  - "Stalker Spine" does not exist in SRP 1.10.9 or in SW: Parasites v5. The nearest is
    `swparasites:serrated_spines`. It is left out until the user names the item they meant.
- **Shulker elixirs (placeholder until the loot pass).** A potion shulker holds 2–4 **Sanctum
  Elixirs**, each splash or drinkable, 50/50. An elixir is a `minecraft:potion` /
  `minecraft:splash_potion` carrying `CustomPotionEffects`, a custom colour and a display name.
  - **Vanilla effects:** 3–4 distinct ones at amplifier I (level II), each lasting 4–8 minutes,
    chosen from speed, strength, regeneration, resistance, fire resistance, haste, jump boost,
    absorption and night vision.
  - **Insanetweaks effect:** with 50% chance, one of `insanetweaks:cleanse` or
    `insanetweaks:nourished` (amplifier 1 - Nourished does nothing at 0), lasting 2–4 minutes.
    - Immune Bond is excluded: it only works through its spell's caster binding, so drunk it is an
      empty timer (user, 2026-10-21).
    - Gilded Stasis is excluded: it freezes the drinker.
    - Wit's Hunger is excluded: it is a curse.
  - **EBW buff effect:** with 50% chance, one of `ebwizardry:ward`, `empowerment`, `font_of_mana`,
    `fireskin`, `ice_shroud`, `static_aura`, `sixth_sense` or `frost_step`, lasting 2–4 minutes.
  - All choices are positional and deterministic. The name is a translate key, e.g. "Sanctum
    Elixir".

### 15.6 Arcane wall around the sanctum

ASC's own `arcane_wall` is a tile-entity block tied to a caster, which is the wrong tool for
thousands of hull cells. So content adds **`insanetweaks:arcane_wall`**:
- **Behaviour:**
  - no tile entity;
  - unbreakable in survival (hardness -1, very high blast resistance);
  - push reaction `BLOCK`;
  - drops nothing.
- **Look:** a full cube using ASC's `ancientspellcraft:blocks/arcane_wall_0` texture, translucent
  render layer. It also needs an item model, `en_us` name and a creative-tab entry for testing.

**Hull.** One layer of it surrounds the whole structure:
- every cell of each room's outer box grown by one that does not lie inside any room's outer box;
- this covers sides, under floors and over ceilings;
- **except** the shaft column above the vestibule ceiling (the 3×3 around the shaft at y > `wardY`),
  which is the one way in.

The layout exposes this as a pure, tested rule (a cell predicate), and the builder writes it inside
the clip before the walls. The layout's `bounds` grow by one to include the hull.

**Dissolving.** The wall goes away in the same event that lifts the Arcane Lock (the last anchor
falls):
- every hull cell becomes **stone** (not air, so no 1-wide spawnable gaps);
- cells in loaded chunks are dissolved at once;
- a chunk of the sanctum that is unloaded at that moment is dissolved the next time it loads. The
  work is queued from the chunk-load event and done on the next server tick, never inside the load
  event.
- The sanctum record remembers which chunks are done, so each chunk is swept at most once after the
  clear.
- The record gains the hull's y-range, so a sweep scans only that range, not up to the sky.

Sanctums generated before 1.28.0 have no hull and nothing to dissolve.

### 15.7 Warning before the descent

When a player comes within the footprint plus 8 blocks of a sanctum that is **not cleared**:
- **Title:** a large dark-red **"BEWARE"** title, with a subtitle, through vanilla title packets.
- **Chat:** a longer chat message. It says three things:
  - taming the sanctum can bring great power and wealth;
  - it is deadly;
  - what sleeps there is a danger to the world itself, and waking it may hasten the parasites.

  This is flavour only: no SRP mechanic is added.
- **Frequency:** at most once per player per 10 minutes (wall clock, server memory, not saved).
- It fires in addition to the one-time "Something Sleeps Below" advancement.

All text lives in `en_us.lang`.

### 15.8 Teleport guard

This uses option (b), chosen by the user on 2026-10-21.

In the pack, the sims' only teleport is **Banish**, their close-range reaction; Blink and Phase Step
are not in their spell pool. EBW moves the entity directly (`setPositionAndUpdate`), so nothing can
be caught after the fact. Vetoing Banish would leave a sim helpless at close range. So Banish is
**allowed**, but nobody leaves the sanctum through it:
- **Which spells:** those in the config list `Sanctum Guarded Spells`. The default list is
  `ebwizardry:banish`, `ebwizardry:blink`, `ebwizardry:phase_step`.
- **Before the cast:** on `SpellCastEvent.Pre` with a guarded spell, cast by a mob released from an
  anchor of a sanctum that is not cleared, the server remembers where the caster and its current
  attack target stand.
- **After the cast:** for a few ticks, if either of them is found outside that sanctum's box
  (footprint plus the hull's y-range), it is put back where it stood. A teleport that lands inside
  the sanctum stays.
- **Not touched:** the AI, and teleports a player casts themselves.
- **Banish cooldown (user, 2026-10-21):** a sim may cast a DISPLACE spell (Banish) at most once per
  `Banish Cooldown (ticks)`, default **300** (15 s), everywhere. While it cools down, both the panic
  reaction and the close-range special skip DISPLACE.

### 15.9 Not in this revision (recorded)

- **"B": the visual overhaul.**
  - Room edges framed with EBW runestone. EBW runestone metadata is `Element.ordinal()`: 1 fire,
    2 ice, 3 lightning, 4 necromancy, 5 earth, 6 sorcery, 7 healing.
  - Palettes and runestone variants per dimension:

    | Dimension | Main block | Runestone |
    |---|---|---|
    | Overworld | stone brick | sorcery (6) + lightning (3) |
    | Nether | nether brick | fire (1) |
    | End | end stone brick | necromancy (4) |
    | dim 111 (Lost Cities) | obsidian + `nb:cry_obi` | earth (5) |

  - Placement rules for the Nether, the End and dim 111.
- **Lore books:** 1–2 per sanctum, in chests. They tell how the sanctum was once the secret
  sanctum of acolytes of ancient magic, who studied the creatures that came with the falling star.
  They hoped to tame the abominations and make their magic and land stronger. They failed, paid for
  their greed, and sealed the sanctum as a last act so the horrors would not overrun the world.
- **The Akashic-Tome-style guide book** for the codex lectern, with the 3×3 ritual recipe first.
- **The real loot pass** (replacing the elixir placeholder). Magic crystals may come back.
- **The assimilated-dragon lair** and a possible split into two structures.
- **Rethinking the entrance.**

### 15.10 Calls made by the assistant (for the user to check)

- "The room with the corridor down" is the level-1 hall.
- Hall anchors come from the room pool.
- The relic pool hits 50% in the main chest and 25% in the others.
- The bauble roll defaults to 0 rather than being removed.
- The ritual room's loot chest is separate and locked; the hint chest stays free.
- Hull cells dissolve to stone.
- The warning has a 10-minute cooldown.
- The teleport guard is option (b): return to the pre-cast position (the user's choice).
- The Banish cooldown defaults to 300 ticks. The user asked for a longer one; the value is the
  assistant's.
- The codex lectern stays empty this revision.
- The version is 1.28.0.

As built: in 1.28.1 the codex lectern is `ebwizardry:oak_lectern`, because ASC's sage lectern
cannot hold books. The arcane workbench in the library is also locked, since it can pull books out
of the locked shelves. And a Roguelike Dungeons start near a sanctum is skipped
(`MixinRoguelikeDungeonGenerator`, config `Block Foreign Dungeons`).

## 16. Revision 1.29.0 (2026-10-21): batch "B", element variants and palettes

Why: the sanctum should look like a place of ancient magic rather than a brick bunker. Each
sanctum should also have an identity that later revisions can build on: a different loot table,
different rooms and different enemies per variant. In this revision the variant drives the look
only.

Non-goals:
- No generation in the Nether or the End yet. Their palettes are prepared, but their placement
  rules (entrance, floor height) are a later revision, Nether first and End possibly never.
- No per-variant loot, rooms or enemies yet.

### 16.1 Variant (element)

- **One element per sanctum.** The element is picked from its dimension's pool, deterministically
  from the layout seed, so every chunk of one sanctum agrees.

  | Dimension | Pool |
  |---|---|
  | Overworld (0) | sorcery, lightning |
  | dim 111 (Lost Cities) | earth |
  | Nether (-1) | fire |
  | End (1) | necromancy |
  | Any other | the Overworld pool |

- **Stored.** The layout exposes the element. The sanctum record saves it as a string (`"Element"`)
  for the later per-variant work. Records from before 1.29.0 have none.

### 16.2 Runestone frame

EBW runestone and runestone pedestal of the sanctum's element, metadata `Element.ordinal()`: 1 fire,
2 ice, 3 lightning, 4 necromancy, 5 earth, 6 sorcery, 7 healing.

- **Edges.** The 12 edges of every room's outer box are runestone, **except the ritual chamber**,
  which stays all sealed stone (user, 2026-10-21).
- **Corners.** The 8 corners of every outer box are a **runestone pedestal**, mixed with plain
  runestone by noise. *Assistant's call:* about 60% pedestal.
- **Pedestals are never "natural".** A natural EBW pedestal belongs to a shrine and can start a mob
  wave.
- **Accents.** Some face cells of the stone-brick walls are runestone, by noise. *Assistant's call:*
  about 4%. The brick variants stay the main material. The chamber's sealed faces get no accents.
- **Untouched cells.** Doorway carves, the seal plug and the hull are never framed. A frame cell
  that a carve opens stays open, because carves are cut after the walls.

### 16.3 Palettes per dimension

The palette decides the wall mix and the blocks for the chiseled dais, the stairs and the surface
ruin. The ritual chamber's sealed stone, the ASC and EBW props and the arcane wall hull are the same
everywhere. A missing modded block falls back to the Overworld block, with one warning in the log.

| Dimension | Walls | Chiseled / dais | Stairs | Silverfish |
|---|---|---|---|---|
| Overworld | stone brick 60%, mossy 25%, cracked 15% (as now) | chiseled stone brick | stone brick | yes |
| dim 111 | obsidian 85%, `nb:cry_obi` 15% | `nb:black_stone_bricks` | `nb:black_stone_brick_stairs` | no |
| Nether (prepared) | nether brick 70%, red nether brick 30% | red nether brick | nether brick | no |
| End (prepared) | end stone bricks 75%, purpur block 25% | purpur pillar | purpur | no |

The surface ruin uses the wall mix of its palette.

### 16.4 Generation in dim 111

- `Dimensions` defaults to **0 and 111**.
- The instance config gets 111 added: back up the file, then add one line.
- Lost Cities streets sit around y 65–70 and basements end well above the sanctum's top (about y 36),
  so the layout and floor range are unchanged.
- **Street-level entrance (option a, user 2026-10-21).** Lost Cities builds its buildings before our
  populate, so the measured "surface" is a roof.
  - In dimensions listed in the config `Surface Cap` (default `111=75`, the street level of the
    pack's Lost Cities profile), the shaft, its casing and its ward stop at the cap.
  - The entrance is then a hole in the street or in a building's ground floor.
  - Where the measured surface is above the cap (a building), the surface ruin is skipped.
- **Pre-1.29.0 sanctums get no frame (assistant).** A sanctum whose record has no `"Element"` gets
  no frame and no accents, in any chunk, so it never ends up half framed. Same rule as the hull.
- **The Nether (-1) and the End (1) are refused (assistant).** They are refused like dim 150, even
  if listed in `Dimensions`, until their placement rules exist. Under today's rules a Nether sanctum
  would sit under the lava sea.

### 16.5 Calls made by the assistant (for the user to check)

- About 60% pedestal on corners.
- About 4% runestone accents.
- The chamber gets neither frame nor accents (the user's choice).
- `Surface Cap` defaults to `111=75`.
- dim 111: the chiseled block and the stairs come from Unseen's Nether Backport black stone.
- The Nether's chiseled block is red nether brick, and the End's is purpur pillar.
- The surface ruin follows the palette.
- The element is saved in the record now, for later.
- The version is 1.29.0.
