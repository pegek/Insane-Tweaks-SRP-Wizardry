# Sanctum Revision 4 (1.28.0, batch A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement spec §15:
- lights and anchors;
- hall décor and anchors;
- the codex lectern;
- ritual room anchors and a loot chest;
- relic loot and elixirs;
- the arcane-wall hull that dissolves on cleanse;
- the BEWARE warning;
- the Banish guard and cooldown.

**Architecture:**
- **Pure, unit-tested core:** geometry stays in `SanctumLayout`. Two pure classes join it:
  - the hull rule, as a predicate on the layout;
  - the elixir recipe, a new class `SanctumElixirs`.
- **Minecraft side:**
  - the builder;
  - a new `BlockArcaneWall`;
  - `SanctumWalls`, which dissolves the hull, triggered from `SanctumLocks` plus a chunk-load queue;
  - `SanctumFoundHandler`, which also sends the warning;
  - `SanctumTeleportGuard`;
  - a cooldown field in `EntityAISimWizardCombat`.

**Tech Stack:** Forge 1.12.2, Java 8, JUnit 4.12, EBW 4.3.19, ASC 1.8.3, SRP 1.10.9.

**Spec:** `docs/superpowers/specs/2026-10-20-sunken-arcane-sanctum-design.md` §15 (including the 15.8 option (b) and the Banish cooldown).

## Global Constraints

- **Version:** content `1.28.0`, in `insanetweaks/build.gradle` and `InsaneTweaksMod.VERSION` (currently 1.27.0).
- **Pure classes:** keep Minecraft types out of `SanctumLayout`, `SanctumRituals` and `SanctumElixirs`.
- **Worldgen:** positional and deterministic, with no world access outside the clip.
- **Config defaults:**

  | Setting | Value |
  |---|---|
  | `Anchor Artefact Chance Room` | 0.10 |
  | `Main Chest Bauble Chance` | 0.0 |
  | `Sanctum Guarded Spells` | `ebwizardry:banish`, `ebwizardry:blink`, `ebwizardry:phase_step` |
  | `Banish Cooldown (ticks)` | 300 (in the sim `Tuning` category) |

- **Instance config edits:** these two lines change in the instance's `insanetweaks.cfg`. Back the file up, then edit byte-exactly.
  - `Anchor Artefact Chance Room` 0.2 → 0.1;
  - `Main Chest Bauble Chance` 0.25 → 0.0.
- **Shell:** never run a Python heredoc that contains backslash escapes or quotes. Write such scripts with the Write tool.
- **Deploy:** check for a lock first, keep exactly one `insanetweaks-*.jar`, and use literal paths in `rm`.

## Review Focus

1. **The hull must never cut into the structure or seal the entrance.** Its cells must lie outside every room's outer box, and the shaft column above `wardY` must stay open.
   - Test: `hullNeverInsideARoomAndShaftStaysOpen`.
2. **The dissolve must not miss chunks that were unloaded at the cleanse, and must not rescan forever.** Rescans are bounded by the swept-chunk set.
   - This is Minecraft-side, so it gets a review only.
3. **The Banish return must not trap a player who leaves on foot through the shaft.**
   - It only acts within a few ticks of a guarded cast.
   - It only moves an entity that is outside the sanctum's box *and* was inside it at cast time.
4. **Anchors in the ritual room sit on an edge column, so the vine pass must skip anchor columns** in every room.
   - Test: `anchorsNeverSitOnAnInteriorEdgeColumn` exempts the RITUAL room only.
   - The builder skips vines over anchors.
5. **Hall décor and anchors must stay off the walkway and the door lines.**
   - Test: `hallKeepsItsWalkwayClear`, plus the existing `decorStaysOffDoorLinesAndTrapRows`.

---

### Task 1: Layout v5 (pure, TDD)

**Files:**
- Modify: `worldgen/sanctum/SanctumLayout.java`
- Test: `SanctumLayoutTest.java`

**Produces:**
- `lights(r, list, min, max)`: rooms keep 1–2 lights, the chamber 2–3.
- **Hall:**
  - `public SanctumBox hallWalkway`: local x 9..19, z -1..1, y F1..F1+2.
  - Hall anchors: 2–3 of the local spots `(11,±3)`, `(14,±3)`, `(17,±3)`, chosen by seed, `POOL_ROOM`.
  - Hall décor: the same kinds and chances as a side room.
    - Candidates: hall interior floor cells off the walkway, off every carve ±2, and not used.
    - Bed head: toward the nearer long wall.
    - Painting: at `(x ∈ {11..17}, F1+2, ±5)`, facing into the hall.
- **Chamber:**
  - Anchors: `4 + (nextInt(4)==0 ? 1 : 0) + (nextInt(4)==0 ? 1 : 0)` of the 7 spots.
  - `public int[] codexLectern`: `x, y, z, facing` at local `(-17, F2, 0)`, facing WEST (toward the altar). Marked used.
- **RITUAL room:**
  - `hasAnchors()` includes RITUAL.
  - Anchors at room-local `(-3,-2)` always and `(-3,2)` at 25%.
  - A chest `CHEST_LIBRARY` at `(2,3)` facing sideA. It is added to `chests`, so it is locked and gets the minor loot.
- **Hull:**
  - `public boolean isHull(int x, int y, int z)`: true iff the cell is in some room's outer box grown by 1, is not in any room's outer box, and is not in the shaft column (`|x-shaftX|<=1 && |z-shaftZ|<=1 && y > wardY`).
  - `public int hullMinY, hullMaxY`: the min outer minY − 1 and max outer maxY + 1.
  - `bounds` = the union of every room's outer box grown by 1, plus the shaft column.

- [ ] **Step 1: Tests (RED).**
  - `lightsAreTwoOrThreePerRoom` → rename to `lightsPerRoom`:
    - chamber 2..3, descent ≤ 1, every other room 1..2.
  - `chamberHoldsThreeToSixAnchorsAndRoomsOneOrTwo` → `chamberHoldsFourToSix…`:
    - chamber 4..6, and 6 is seen;
    - room-pool count: each anchor room 1–2, and the hall 2–3, so the range is `anchorRooms + 2` .. `2*anchorRooms + 3`.
  - `hallKeepsItsWalkwayClear`:
    - no décor, anchor or prop inside `hallWalkway`;
    - the hall holds 2..3 anchors, all `POOL_ROOM`.
  - `codexLecternBesideTheAltar`:
    - not null, inside the chamber interior, within 3 blocks of altar index 4;
    - included in `noTwoThingsShareACell`.
  - `ritualRoomHasAnchorsAndALockedChest`:
    - where RITUAL exists, 1..2 anchors and one `CHEST_LIBRARY` chest lie inside its interior;
    - no anchor lies on a rune or on the centre.
  - `anchorsNeverSitOnAnInteriorEdgeColumn`: skip the RITUAL room.
  - `hullNeverInsideARoomAndShaftStaysOpen`, for each seed:
    - every cell on the surface of each grown box that is not inside any outer box and not in the shaft column is `isHull`;
    - nothing inside any outer box is `isHull`;
    - `isHull(shaftX, wardY+1, shaftZ)` is false;
    - every hull cell lies in `bounds` and within `hullMinY..hullMaxY`.
  - `footprintStaysNearTheOrigin`: ±29.
- [ ] **Step 2: Run** `:insanetweaks:test --tests '*SanctumLayoutTest'`. Expected: RED (compile).
- [ ] **Step 3: Implement.** The builder keeps compiling, because it uses only existing fields plus `chests` and `anchors`.
- [ ] **Step 4: Run** `:insanetweaks:test`. Expected: all green.
- [ ] **Step 5: Commit** `feat(insanetweaks): sanctum layout v5 - fewer lights, hall anchors and decor, codex lectern, hull rule`.

### Task 2: Arcane wall — block, build, dissolve

**Files:**
- Create:
  - `blocks/BlockArcaneWall.java`
  - `worldgen/sanctum/SanctumWalls.java`
  - assets: blockstate, block and item models, lang
- Modify:
  - `init/ModBlocks.java`
  - `worldgen/sanctum/SanctumWorldData.java`
  - `SanctumPlacement.register`
  - `SanctumLocks.anchorDone`
  - `SanctumBuilder`
  - `InsaneTweaksMod` (register `SanctumWalls`)

**Produces:**
- `BlockArcaneWall`:
  - `Material.ROCK`, `setBlockUnbreakable()`, `setResistance(6000000F)`;
  - `getMobilityFlag` → `EnumPushReaction.BLOCK`;
  - drops nothing (`quantityDropped` 0);
  - `isOpaqueCube` false;
  - client-only `getBlockLayer` → TRANSLUCENT, as an `@SideOnly(CLIENT)` method;
  - registry name `insanetweaks:arcane_wall`, registered like `ARCANE_WARD` (block, ItemBlock, model).
- Assets:
  - `blockstates/arcane_wall.json` → model `insanetweaks:arcane_wall`;
  - the model is `cube_all` with texture `ancientspellcraft:blocks/arcane_wall_0`;
  - item model with parent `insanetweaks:block/arcane_wall`;
  - lang `tile.insanetweaks.arcane_wall.name=Arcane Wall`.
- `SanctumWorldData` record:
  - adds `int[] hullY` (`hullMinY`, `hullMaxY`) and `Set<Long> swept` (chunk longs);
  - NBT keys `"HullY"` (int array) and `"Swept"` (long list);
  - `register(...)` gains `hullMinY, hullMaxY`;
  - `public int[] box(long id)` returns `{minX, minZ, maxX, maxZ, hullMinY, hullMaxY}` or null;
  - `public List<Long> clearedNeedingSweep(int chunkX, int chunkZ)` returns the sanctum ids of cleared records whose box intersects the chunk, have a `hullY`, and have not swept it;
  - `public void markSwept(long id, long chunk)`.
- Builder:
  - first pass: for each room's grown box, cells on its surface, inside the clip, with `l.isHull(...)` → `ModBlocks.ARCANE_WALL`;
  - vines skip any (x, z) column that holds an anchor.
- `SanctumWalls`:
  - `static void dissolveLoaded(World, long id)`: for every chunk of the box that is loaded, sweep it and mark it swept. Called from `SanctumLocks.anchorDone` when the sanctum clears.
  - `@SubscribeEvent onChunkLoad(ChunkEvent.Load)`: server side; queue `(dim, chunkX, chunkZ)` when `clearedNeedingSweep` is non-empty.
  - `@SubscribeEvent onWorldTick(WorldTickEvent END)`: drain the queue for that world and sweep.
  - Sweep: within the box ∩ chunk, at y in `hullMinY..hullMaxY`, every `ARCANE_WALL` becomes `Blocks.STONE` (flag 2).

- [ ] **Step 1:** Implement.
  - This Minecraft-side code has no unit test. Its geometry is tested in Task 1 through `isHull`.
  - Ruling to ledger: the block and handler code is untestable in this setup.
- [ ] **Step 2: Run** `:insanetweaks:test :insanetweaks:compileJava`. Expected: green, and no new warnings in touched files.
- [ ] **Step 3: Commit** `feat(insanetweaks): arcane wall hull around the sanctum, dissolved to stone on cleanse`.

### Task 3: Elixirs, relic loot, config defaults (TDD for the recipe)

**Files:**
- Create: `worldgen/sanctum/SanctumElixirs.java` (pure)
- Modify:
  - `worldgen/sanctum/SanctumPotions.java` (fills with elixirs)
  - the 4 chest loot tables
  - `ArcaneSanctumCategory`
  - lang
- Test: `SanctumElixirsTest.java`

**Produces:**
- `SanctumElixirs.recipe(double[] noise)` returns `List<String[]>`, one entry per effect: `{id, amplifier, ticks}`.
  - `noise` is ≥ 16 values in [0, 1), drawn by the caller from `SanctumNoise` channels 150–165.
  - **Vanilla:** 3–4 distinct ids from `minecraft:speed`, `strength`, `regeneration`, `resistance`, `fire_resistance`, `haste`, `jump_boost`, `absorption`, `night_vision`.
    - Amplifier 1, except 0 for fire_resistance and night_vision.
    - Ticks 4800..9600.
  - **Insanetweaks:** with 50%, `insanetweaks:cleanse` (amplifier 0) or `insanetweaks:nourished` (amplifier 1), 2400..4800 ticks.
  - **EBW:** with 50%, one of `ebwizardry:ward`, `empowerment`, `font_of_mana`, `fireskin`, `ice_shroud`, `static_aura`, `sixth_sense`, `frost_step`, amplifier 0, 2400..4800 ticks.
- `SanctumPotions.fill`: 2–4 elixirs.
  - Each is a `POTIONITEM` or `SPLASH_POTION` with NBT `CustomPotionEffects`, built from `Potion.getPotionFromResourceLocation(id)`; unknown ids are skipped.
  - `CustomPotionColor` 0x6A1B9A.
  - `setTranslatableName("item.insanetweaks.sanctum_elixir.name")`.
- **Loot tables:** each gets a pool `"parasite_relic"`, rolls 1.
  - Entries: the 8 SRP ids from spec §15.5, weight 1 each.
  - `empty` weight 8 in the main table (50%), 24 in the other three (25%).
- **Config:** `mainChestBaubleChance` 0.0, `anchorArtefactChanceRoom` 0.10.
- **Lang:** `item.insanetweaks.sanctum_elixir.name=Sanctum Elixir`.

- [ ] **Step 1: Tests (RED).**
  - `vanillaThreeOrFourDistinct`: over 2000 random noise arrays, there are 3..4 vanilla entries and their ids are distinct.
  - `durationsInRange`: vanilla ticks are in 4800..9600; extra effects in 2400..4800.
  - `extrasAtMostOneEach`: at most one `insanetweaks:` entry and at most one `ebwizardry:` entry; both are seen over the run.
  - `noImmuneBondNoStasisNoCurse`: no entry has id `insanetweaks:immune_bond`, `gilded_stasis` or `wits_hunger`.
- [ ] **Step 2: Run** `--tests '*SanctumElixirsTest'`. Expected: RED (compile).
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `:insanetweaks:test`. Expected: green. Also validate the JSON with Python.
- [ ] **Step 5: Commit** `feat(insanetweaks): sanctum elixirs, parasite relics in chests`.

### Task 4: BEWARE warning, Banish guard, Banish cooldown

**Files:**
- Create: `worldgen/sanctum/SanctumTeleportGuard.java`
- Modify:
  - `SanctumFoundHandler`
  - `SanctumWorldData` (`nearUnclearedSanctum`)
  - `ArcaneSanctumCategory` (`sanctumGuardedSpells`)
  - `EntitiesCategory.Tuning` (`banishCooldownTicks` 300)
  - `entities/ai/EntityAISimWizardCombat`
  - `InsaneTweaksMod`
  - lang

**Produces:**
- `SanctumWorldData.nearUnclearedSanctum(int x, int z, int margin)`: like `nearSanctum`, but skips cleared records.
- `SanctumFoundHandler` warning:
  - same 40-tick check;
  - if `nearUnclearedSanctum(px, pz, 8)` and the player's last warning is ≥ 600000 ms ago (static `Map<UUID, Long>`), it sends:
    - `SPacketTitle` TIMES (10, 80, 20);
    - TITLE `translate:msg.insanetweaks.sanctum.beware.title`, DARK_RED, bold;
    - SUBTITLE `…beware.subtitle`, RED;
    - chat `…beware.chat`, DARK_PURPLE.
  - Lang:
    - title "BEWARE";
    - subtitle "A sealed sanctum sleeps beneath";
    - chat: one paragraph covering wealth and power, mortal danger, and that what sleeps there threatens the world and may hasten the parasites.
- `SanctumTeleportGuard`:
  - `onCast(SpellCastEvent.Pre)`:
    - server side; the caster is an `EntityLiving` with `AnchorLeash.SANCTUM`;
    - `!SanctumWorldData.isCleared(id)`;
    - the spell's registry name is in the config list.
    - Then it records `Pending{world dim, sanctum id, entity id, x, y, z, expires = worldTime + 10}` for the caster, and for its attack target if non-null.
  - `onWorldTick(END)`: for each pending entry of that world:
    - the entity is alive and outside `box(id)`, where outside means x/z outside the footprint or y outside the hull range;
    - and its recorded position was inside;
    - then `setPositionAndUpdate(recorded)`.
    - Entries are removed when expired.
- `EntityAISimWizardCombat`: field `private long nextDisplaceReadyTime`.
  - DISPLACE is skipped (as if absent) in `firePanic`'s fallback and in the close-range special while `now < nextDisplaceReadyTime`.
  - On choosing DISPLACE (special) or casting it (panic), `nextDisplaceReadyTime = now + tuning().banishCooldownTicks`.

- [ ] **Step 1:** Implement.
  - Ruling: event and AI code is not unit-testable here; `nearUnclearedSanctum` is trivial.
- [ ] **Step 2: Run** `:insanetweaks:test :insanetweaks:compileJava`. Expected: green.
- [ ] **Step 3: Commit** `feat(insanetweaks): sanctum BEWARE warning, banish guard and banish cooldown`.

### Task 5: Builder bits, version, deploy

- [ ] **Step 1: Builder.**
  - Codex lectern: `ancientspellcraft:sage_lectern` via the palette lookup, `withFacing`, inside the clip. Not locked, not registered.
  - The ritual-room chest comes through the existing `chests` path automatically.
  - The hall's anchors and décor come through the existing `anchors` and `decor` paths.
- [ ] **Step 2: Version and checks.**
  - Bump to 1.28.0 and run `:insanetweaks:test :insanetweaks:build`.
  - javap: no client references in `worldgen/sanctum/*`, `anchor/*` or `blocks/BlockArcaneWall`, except the stripped `@SideOnly` method.
  - The jar holds the arcane_wall assets.
- [ ] **Step 3: Instance config.** Apply the two edits from Global Constraints. Diff against the backup.
- [ ] **Step 4: Deploy** with the lock check.
- [ ] **Step 5: Commit** `chore(insanetweaks): 1.28.0`.
