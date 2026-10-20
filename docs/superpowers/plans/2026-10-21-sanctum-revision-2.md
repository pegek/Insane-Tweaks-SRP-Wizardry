# Sanctum Revision 1.26.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the second-playtest revision of the Sunken Arcane Sanctum (spec §13): working Arcane Lock, return after chunk unload, an all-sealed chamber with extra loot, magic-light lighting, the teleport room, traps, vines and silverfish, décor, better loot with ASC knowledge items.

**Architecture:**
- `SanctumLayout` (pure, tested) gains new lists:
  - `magicLights` and `glowSpots`, replacing `lights`;
  - `trapDispensers` and `decor`;
  - new prop kinds;
  - a sixth side-room slot pair on level 2 and the `TELEPORT` room type;
  - `sealFrame` is removed.
- `SanctumBuilder` writes the new lists. Two small MC-side helpers hold the per-item choices: `SanctumTraps` (scroll spell) and `SanctumPotions` (strong potions).
- Anchor fixes live in `TileEntityPetrifiedAnchor` and `AnchorLeashHandler`.
- The lock fix lives in `SanctumLocks`.

**Tech Stack:** Forge 1.12.2, EBW 4.3.19, ASC 1.8.3, JUnit 4.12.

**Spec:** `docs/superpowers/specs/2026-10-20-sunken-arcane-sanctum-design.md` §13 (with §12 as the base).

## Global Constraints

- Java 8, no lambdas in mixins (none touched here). English code and lang.
- Version 1.26.0 in **both** `insanetweaks/build.gradle` and `InsaneTweaksMod.VERSION`.
- No client types in common classes; verify with `javap -v | grep -cE 'minecraft/client|fml/client'`.
- Loot tables and ids may only name items that exist in the DEv 1.2 pack. The ASC ids come from ASC's own `chests/ancient_vault.json`, and the EBW subsets are `ebwizardry:subsets/{arcane_tomes,wand_upgrades,wizard_armour,armour_upgrades}`.
- Walls stay stone brick (runestone deferred, §13.6).
- Gradle needs JDK 8: `"-Dorg.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot"`.

## Review Focus

1. **A hanging painting whose wall block belongs to another writer box** would pop off as an item. Spawn a painting only when both its air cell and its wall block are inside the current clip. Test: layout puts every painting's wall cell (`x,y,z` stepped against `facing`) in a room's outer shell, and the builder checks `clip.contains(wall)`.
2. **A trap dispenser on a slot whose side wall is shared with another room's interior** would fire into that room. Test (`SanctumLayoutTest.trapDispensersSitInAShellCellNotAnInterior`): no dispenser lies inside any room interior.
3. **Décor blocking the trap row or the door line**, so the player can never walk through. Test (`decorStaysOffDoorLinesAndTrapRows`): no décor cell at `a ≥ 2` with `|b| ≤ 1`, and none on the trap row `a = 3`.
4. **A bed's head cell overlapping another object.** The `noTwoThingsShareACell` test includes the bed's second cell.
5. **Old 1.25.0 sanctums.** Their locks are string-typed and unreadable by EBW, and `SanctumLocks.sanctumOf` would treat them as unlocked. That is accepted and needs no test: sanctums generated before 1.26.0 simply have no working lock.

---

### Task 1: Layout v3 (pure, TDD)

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumLayout.java`
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/worldgen/sanctum/SanctumLayoutTest.java`

**Interfaces — Produces** (all coordinates in world space; facing indices 0 N, 1 E, 2 S, 3 W):

- **Removed:** `sealFrame` and `lights`.
- **`RoomType`:** add `TELEPORT`. `isSideRoom()` covers `LIBRARY..COLLAPSED` plus `TELEPORT`, and `hasAnchors()` is true for `LIBRARY`, `FORGE`, `WORKSHOP` and `TELEPORT`. Put `TELEPORT` after `WORKSHOP` in the enum so `ordinal() >= LIBRARY` still selects side rooms.
- **New prop kinds**, appended after `PROP_CRAFTING_TABLE` (9):

  | Constant | Value | Block |
  |---|---|---|
  | `PROP_SPHERE_COGNIZANCE` | 10 | replaces sage desk |
  | `PROP_TRANSPORT_STONE` | 11 | |
  | `PROP_GILDED_WOOD` | 12 | |
  | `PROP_BOOKSHELF` | 13 | vanilla |
  | `PROP_SHULKER` | 14 | |
  | `PROP_TRIP_HOOK` | 15 | |
  | `PROP_TRIPWIRE` | 16 | |

  `PROP_SAGE_DESK` (7) is removed from use; keep the constant.
- **`List<int[]> magicLights`:** x, y, z. Wall-side light cells inside interiors.
- **`List<int[]> glowSpots`:** x, y, z. The former ceiling spots; the builder keeps 15%.
- **`List<int[]> trapDispensers`:** x, y, z, facing. Shell cells.
- **`List<int[]> decor`:** x, y, z, kind, facing, extra. Kinds:

  | Constant | Value | `extra` |
  |---|---|---|
  | `DECOR_ARMOR_STAND` | 0 | |
  | `DECOR_SHULKER` | 1 | |
  | `DECOR_ENDER_CHEST` | 2 | |
  | `DECOR_PORTAL_FRAME` | 3 | 1 = with eye |
  | `DECOR_BED` | 4 | foot cell; head at foot + facing |
  | `DECOR_PAINTING` | 5 | air cell; wall at cell − facing |
  | `DECOR_SKULL` | 6 | 1 = wither |

- **`public static int[] bedHead(int[] bed)`:** returns the head cell `{x, y, z}`.

**Values (spec §13):**

- **Chamber:**
  - anchors are `3 + nextInt(3)`, plus 1 when `nextInt(4) == 0` (range 3–6 from the 7 spots);
  - second `CHEST_MAIN` at local `(-25, F2, -2)` facing E when `nextBoolean()`;
  - `PROP_SHULKER` at local `(-25, F2, 2)` facing E when `nextInt(4) == 0`.
- **Slots:** the existing four plus `{-8, -8, SOUTH, level 2}` and `{-8, 8, NORTH, level 2}`. A slot's floor is `floorY` on level 1 and `floorY2` on level 2.
- **Types:** `LIBRARY, FORGE, WORKSHOP, TELEPORT`, plus one filler (`CRYPT`/`COLLAPSED`), shuffled, `k = 2 + nextInt(4)` (2–5) rooms.
- **WORKSHOP:**
  - the scribing desk keeps its spot `(-3, -1)` but faces `(front + 2) % 4`;
  - `(-3, 1)` becomes `PROP_SPHERE_COGNIZANCE`, facing front.
- **LIBRARY:** each shelf cell rolls `r.nextDouble()`: below 0.2 gives `PROP_GILDED_WOOD`, below 0.4 gives `PROP_BOOKSHELF`, else `PROP_EBW_SHELF`.
- **TELEPORT:** when `nextBoolean()`, a 3×3 ring (offsets a, b ∈ [−1, 1] without the centre); otherwise a 4×4 ring (a, b ∈ [−2, 1], border only). Anchors at `(2, −3)` always and `(2, 3)` with 25%.
- **Other anchor rooms:** anchors stay at `(1, −2)` and `(1, 2)` with 25%.
- **Trap** (all side rooms, `nextDouble() < 0.35`):
  - `PROP_TRIP_HOOK` at `(3, −3)` facing `(front + 1) % 4`, and at `(3, 3)` facing `(front + 3) % 4`;
  - `PROP_TRIPWIRE` at `(3, b)` for b in −2..2;
  - a dispenser at `(3, −4)` facing `(front + 1) % 4`.
- **Magic lights:**
  - side rooms: `(−1, ±3, F+2)`, plus `(2, ±3, F+2)` when the room is not a library;
  - chamber: `(−27, F2+3, z)` and `(−13, F2+3, z)` for z ∈ {−4, 4}, plus `(x, F2+3, ±7)` for x ∈ {−24, −16};
  - hall: `(11, F1+2, ±5)` and `(17, F1+2, ±5)`;
  - vestibule: `(21, F1+2, ±3)` and `(27, F1+2, ±3)`;
  - antechamber: `(−6, F2+3, ±3)`;
  - stair landing: `(7, F1+3, 1)`.
- **Glow spots:** the previous chamber, hall, vestibule and antechamber ceiling cells, plus each side room's centre ceiling.
- **Décor** (side rooms only). Use the free floor cells of the room, at local a, b ∈ [−3, 3] interior, excluding:
  - occupied cells;
  - the trap row `a = 3`;
  - the door line `a ≥ 2 && |b| ≤ 1`;

  then shuffle them. Each piece takes the next cell:

  | Piece | Chance | Facing | Details |
  |---|---|---|---|
  | Armour stand | 0.20 | front | |
  | Shulker | 0.08 | front | |
  | Ender chest | 0.10 | front | |
  | Portal frame | 0.10 | front | extra 1 at 30% |
  | Bed | 0.15 | | the first free cell whose neighbour in direction `(front + 2) % 4` is also free; facing = that direction |
  | Skull | 0.20 | rotation from extra | extra 1 at 30% |
  | Painting | 0.25 | front | on the back wall: cell `(−3, b, F+2)` with b chosen from {−1, 0, 1}, not at a library (shelves reach F+1 only, so F+2 is free) |

- **Containers for the lock:** every `chests` entry, every `PROP_EBW_SHELF`, every `PROP_SHULKER` and every `DECOR_SHULKER`.

- [ ] **Step 1: Update the tests.**
  - Drop the frame test.
  - In `corePiecesSideRoomsAndFillers`, side rooms become 2–5 and `TELEPORT` counts as a side room.
  - In `chamberHoldsThreeToFiveAnchorsAndRoomsOneOrTwo`, chamber anchors become 3–6.
  - `everythingPlacedSitsInsideAnInterior` also covers `decor` and `magicLights`.
  - `noTwoThingsShareACell` covers anchors, chests, props, decor and each bed's head cell.
  - Add:
    - `trapDispensersSitInAShellCellNotAnInterior`: a dispenser is in no room's interior and is in some room's outer box;
    - `trapRoomsHaveTwoHooksAndFiveStrings`: per trap, 2 `PROP_TRIP_HOOK` and 5 `PROP_TRIPWIRE`, all in the same room;
    - `teleportRingIsEightOrTwelveWithEmptyCentre`: count of `PROP_TRANSPORT_STONE` per teleport room ∈ {8, 12};
    - `chamberHasOneOrTwoMainChests`: `CHEST_MAIN` count ∈ {1, 2};
    - `decorStaysOffDoorLinesAndTrapRows` (Review Focus 3);
    - `paintingsHangOnAWall`: the cell `(x − dx(facing), y, z − dz(facing))` is inside a room outer box but not in any interior;
    - `noFrameAndNoSageDesk`: no prop has kind `PROP_SAGE_DESK`.
- [ ] **Step 2:** `./gradlew … :insanetweaks:test --tests '*SanctumLayoutTest'`. Expected: compile FAIL.
- [ ] **Step 3:** Implement the layout to the values above.
- [ ] **Step 4:** Run the tests. Expected: all pass. The builder will not compile until Task 4; do not commit yet.

### Task 2: Anchor fixes

**Files:** modify `anchor/TileEntityPetrifiedAnchor.java` and `anchor/AnchorLeashHandler.java`; `anchor/AnchorLeash.java` gets a constant.

**Interfaces:**
- `AnchorLeash.RECALL = "InsaneTweaksAnchorRecall"` (boolean entity data).
- In the tile entity: `private long lastMobPos` (NBT `"LastMobPos"`) and `private boolean lastMobKnown` (NBT `"LastMobKnown"`).

- [ ] **Step 1: `guard()`.**
  - When the mob is found, store `lastMobPos = mob.getPosition().toLong()`.
  - When it is not found, add to `missingTicks` **only if** `!lastMobKnown || world.isBlockLoaded(BlockPos.fromLong(lastMobPos))`.
  - `track()` seeds `lastMobPos` from the spawned mob.
- [ ] **Step 2: `AnchorLeashHandler.onJoin(EntityJoinWorldEvent)`.** Server side, for an `EntityLiving` with `HOME` in the same dimension: when it is more than 4 blocks from home and no non-spectator player is within `returnRadius`, set `RECALL = true`.
- [ ] **Step 3: `onLivingUpdate`.** Right after the leash check: if `RECALL` is set, call `sendHome(mob, home, returnHealFraction)`, clear `RECALL` and set `ALONE = 0`.
- [ ] **Step 4:** Compile, but do not commit until Task 4.

### Task 3: Lock fix

**Files:** `worldgen/sanctum/SanctumLocks.java`, `worldgen/sanctum/SanctumPlacement.java`.

- [ ] **Step 1:**
  - `lock`: `data.setUniqueId(ArcaneLock.NBT_KEY, ownerOf(id))`.
  - `sanctumOf`: `data.hasKey(SANCTUM_KEY) && data.hasUniqueId(ArcaneLock.NBT_KEY)`.
  - `unlock`: remove `ArcaneLock.NBT_KEY + "Most"`, `ArcaneLock.NBT_KEY + "Least"` and `SANCTUM_KEY`.
- [ ] **Step 2:** `SanctumPlacement.register` adds `PROP_SHULKER` props and `DECOR_SHULKER` decor to the containers.

### Task 4: Builder v3, helpers, config, loot

**Files:**
- Modify: `worldgen/sanctum/SanctumBuilder.java`, `config/categories/ArcaneSanctumCategory.java`, the 4 loot tables.
- Create: `worldgen/sanctum/SanctumTraps.java`, `worldgen/sanctum/SanctumPotions.java`.

**Interfaces:**
- `SanctumTraps.load(IInventory dispenser, long seed, int x, int y, int z)`: puts 2–4 `WizardryItems.scroll` (meta = spell metadata) of one spell into the dispenser.
  - The spell is chosen by noise from `ModConfig.arcaneSanctum.trapSpells`.
  - Default spells: `ebwizardry:magic_missile`, `ebwizardry:firebolt`, `ebwizardry:ice_shard`, `ebwizardry:poison`.
  - A spell is used only if it resolves via `Spell.registry.getValue(new ResourceLocation(id))`, is not null, and `canBeCastBy((TileEntityDispenser) null)` is true. If none qualifies, the dispenser stays empty.
- `SanctumPotions.fill(IInventory box, long seed, int x, int y, int z)`: 2–4 potions.
  - Types: `PotionTypes.STRONG_HEALING`, `STRONG_REGENERATION`, `STRONG_STRENGTH`, `STRONG_SWIFTNESS`, `LONG_FIRE_RESISTANCE`, `LONG_NIGHT_VISION`.
  - Item `Items.POTIONITEM` or `Items.SPLASH_POTION` (50/50) via `PotionUtils.addPotionToItemStack`.
- **Config:**
  - `bookshelfBookChance` default **0.08**;
  - new `trapSpells` (String[] above);
  - the trap chance stays a layout constant (0.35), not a config field.

- [ ] **Step 1: Builder.**
  - **Pass 1:** in non-chamber walls, `SanctumNoise.at(seed, x, y, z, 9) < 0.015` places `Blocks.MONSTER_EGG` with the variant matching the wall (stonebrick, mossy or cracked).
  - **`magicLights`:** `WizardryBlocks.magic_light`, then `((TileEntityTimer) te).setLifetime(-1)`.
  - **`glowSpots`:** glowstone when `noise(…, 12) < 0.15`.
  - **Props:**
    - new kinds by id: `ebwizardry:transportation_stone`, `gilded_wood`, vanilla bookshelf, `ancientspellcraft:sphere_cognizance`, `minecraft:purple_shulker_box`;
    - `PROP_SHULKER` gets `SanctumPotions.fill` and is locked;
    - trip hook: `Blocks.TRIPWIRE_HOOK` with `FACING` and `ATTACHED=true`;
    - tripwire: `Blocks.TRIPWIRE` with `ATTACHED=true`.
  - **`trapDispensers`:** `Blocks.DISPENSER` with `BlockDispenser.FACING`, then `SanctumTraps.load`.
  - **Décor** (each only when its cell is in the clip):
    - armour stand: an `EntityArmorStand` at the cell centre with 1–2 pieces from iron, chain or gold, slots chosen by noise;
    - shulker: as `PROP_SHULKER`;
    - ender chest: facing set;
    - portal frame: `BlockEndPortalFrame.FACING` and `EYE`;
    - bed: `Blocks.BED` foot plus head (`PART`, `FACING`), with the colour set on both `TileEntityBed`s through `setColor(EnumDyeColor.byMetadata(noise × 16))`;
    - skull: `Blocks.SKULL` with `BlockSkull.FACING=UP`, then `TileEntitySkull.setType(extra)` and `setSkullRotation(noise × 16)`;
    - painting: `new EntityPainting(world, wallPos, facing)`, spawned only if the wall cell is also in the clip and `onValidSurface()` holds.
  - **Vines:** in `decorate`, an interior air cell next to a wall (not a doorway), at `y` between floor+1 and top−1, with `noise(…, 13) < 0.06`, gets `Blocks.VINE` with the property toward that wall set true.
- [ ] **Step 2: Loot tables** (§13.5 plus the ASC knowledge items). No `gold_nugget`, plain `magic_crystal`, `paper` or `blank_scroll` anywhere.
  - **`arcane_sanctum`:**
    - the Abomination pool;
    - SRP parts;
    - `ebwizardry:spell_book` with `ebwizardry:random_spell` `{"tiers":["advanced","master"]}`;
    - knowledge pool, weighted: the 4 stone tablets at 1 each, `ancient_spellcraft_spell_book` (`random_spell`) 3, `theory_scroll` 3, `runic_plate` (`random_spell`) 3, `ancientspellcraft:subsets/ritual_books` 1, `rare_scrolls` 2;
    - EBW subsets: `arcane_tomes` 2, `wand_upgrades` 2, `wizard_armour` 2;
    - diamonds 1–2 or ender pearls 1–3.
  - **`arcane_sanctum_minor`** (library):
    - `minecraft:book` with `enchant_with_levels` `{"levels":{"min":25,"max":30},"treasure":true}`;
    - `ebwizardry:spell_book` with `random_spell`;
    - knowledge (`ancient_spellcraft_spell_book`, `theory_scroll`, `stone_tablet_small`);
    - `experience_bottle` 2–5.
  - **`arcane_sanctum_forge`:**
    - `ebwizardry:subsets/armour_upgrades` (guaranteed roll);
    - diamond 1–2;
    - an enchanted `iron_sword`, `diamond_sword`, `iron_chestplate` or `diamond_helmet` via `enchant_with_levels` 20–30.
  - **`arcane_sanctum_workshop`:**
    - `ebwizardry:scroll` with `random_spell` 1–2;
    - knowledge (`ancient_spellcraft_scroll` with `random_spell`, `runic_plate` with `random_spell`, `theory_scroll`);
    - Abomination dust;
    - ender pearls 1–2;
    - one strong potion via `minecraft:set_nbt` `{"Potion":"minecraft:strong_healing"}`.
- [ ] **Step 3:** `:insanetweaks:test :insanetweaks:compileJava` green with no new warnings in touched files.
- [ ] **Step 4: Commit in four commits:**
  - layout and tests;
  - anchors;
  - locks and placement;
  - builder, helpers, config and loot.

### Task 5: Version, verify, deploy

- [ ] Bump the version to `1.26.0`. Run test and build.
- [ ] `javap` checks:
  - 0 client references in `worldgen/sanctum/*` and `anchor/*`;
  - `SanctumBaubles` is still isolated.
- [ ] The jar holds the 4 loot tables.
- [ ] **Instance config.** Set `Bookshelf Book Chance` to 0.08 in `insanetweaks.cfg` (Forge does not overwrite the old 0.05). Back the file up first and edit that one line only.
- [ ] Deploy with the lock check, never leaving two jars. Commit `chore(insanetweaks): 1.26.0`.
