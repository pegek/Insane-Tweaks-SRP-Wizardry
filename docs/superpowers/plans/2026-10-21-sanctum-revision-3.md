# Sanctum Revision 3 (1.27.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement spec §14 (third playtest), covering:

- fewer and higher magic lights;
- stair headroom;
- buffed anchor mobs that call reinforcements;
- anchors that fall and drop artefacts;
- a cleanse sound;
- two advancements;
- the ritual room;
- extra bookshelf books.

**Architecture:**

- Geometry stays in the pure, JUnit-tested `SanctumLayout`. The new pure `SanctumRituals` copies ASC's ritual patterns.
- Small pure decision helpers keep the rules testable:
  - `AnchorRules` decides when a mob calls for help and what the artefact chance is;
  - `SanctumShelfRoll` decides what each extra bookshelf roll yields.
- The Minecraft-side code goes into the existing builder, the anchor tile entity, the leash handler and `SanctumLocks`.
- Sanctum records gain their footprint, so the "found" advancement is detected per player from saved data.

**Tech Stack:** Forge 1.12.2 (FG3), Java 8, JUnit 4.12, EBW 4.3.19, ASC 1.8.3 (compile-time jar in `libs/`).

**Spec:** `docs/superpowers/specs/2026-10-20-sunken-arcane-sanctum-design.md` §14 (and §14.9–14.10).

## Global Constraints

- **Version:** content becomes `1.27.0`, set in two places: `insanetweaks/build.gradle` and `InsaneTweaksMod.VERSION` (both currently 1.26.1).
- **Java 8.** Keep Minecraft types out of the pure classes: `SanctumLayout`, `SanctumRituals`, `AnchorRules`, `SanctumShelfRoll`.
- **Worldgen:**
  - every material and placement decision is positional (`SanctumNoise`) or comes from the layout seed;
  - no world access outside the clip during a build.
- **Buffs must be easy to switch off:**
  - one boolean `Release Buffs` (default true);
  - plus a value each: `Release Health Bonus` 0.20 and `Release Speed Bonus` 0.15;
  - a value of 0 also disables that part.
- **Reinforcements:**
  - one boolean `Reinforcements` (default true);
  - `Reinforcement Health Fraction` 0.25;
  - `Reinforcement Radius` 10.
- **Artefact chances:** `Anchor Artefact Chance Room` 0.20 and `Anchor Artefact Chance Chamber` 0.50, in `arcaneSanctum`.
- **Bookshelf extras:** `Bookshelf Extra Rolls` 2, `Bookshelf Treasure Chance` 0.03 and `Bookshelf Insane Book Chance` 0.04, in `arcaneSanctum`.
- **Room Anchor Pool default:** `insanetweaks:sim_wizard;3`, `insanetweaks:sim_battlemage;2`.
- **Instance config edits:** back up each file first and edit it byte-exactly (Python, CRLF preserved), never with `sed`.
- **Deploy:** check for a lock with a `mv` round-trip, never leave two jars of one modid in `mods`, and use literal paths in `rm`.
- **Shell:** heredocs containing apostrophes break this shell. Write scripts with the Write tool and run them.

## Review Focus

1. **The ritual must start when a player places the centrepiece.**
   - ASC matches the pattern grid centred on the centrepiece rune and compares every cell, so blanks must be empty. It tries 4 rotations and no mirror.
   - A mirrored or off-centre layout would leave the ritual permanently dead.
   - Test: `SanctumRitualsTest.gridCentreIsTheCentrepiece` and `SanctumLayoutTest.ritualRunesFormARotationOfThePattern`.
2. **Elemental Attunement and Warlock Attunement can never match in ASC 1.8.3.** Their grid centre is not their centrepiece rune.
   - The pool is therefore Rejuvenation, Bonfire and Condensing (ruling, spec §14.6 replacement rule).
   - Test: `gridCentreIsTheCentrepiece` covers every pattern we ship.
3. **The hint chest must never be locked or registered as a sanctum container.** If it were, the hint would only open after the cleanse.
   - Test: `SanctumLayoutTest.hintChestIsNotALockedChest`.
4. **Every room keeps 2–3 magic lights at most, and each light sits inside an interior.** A light one block higher could end up in a ceiling, as the descent landing's would. That one stays at F1+3 (ruling).
   - Test: `lightsAreTwoOrThreePerRoom` plus the existing `everythingPlacedSitsInsideAnInterior`.
5. **Reinforcement and artefact rules at their edges:**
   - exactly 25% health does not call; one hit straight to 0 from above 25% does call, if the mob had a damage event;
   - hand-placed anchors never call and never drop artefacts.
   - Test: `AnchorRulesTest`.

---

### Task 1: Layout — lights, stair door, ritual room (pure, TDD)

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumRituals.java`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/worldgen/sanctum/SanctumLayout.java`
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/worldgen/sanctum/SanctumRitualsTest.java` (new), `SanctumLayoutTest.java`

**Interfaces — Produces:**

`SanctumRituals`:
- `RITUAL_COUNT = 3`.
- Indices: `REJUVENATION = 0`, `BONFIRE = 1`, `CONDENSING = 2`.
- `static String id(int ritual)`: `"rejuvenation"`, `"bonfire"`, `"condensing"`.
- `static String[] pattern(int ritual)`: rows copied verbatim from ASC's json.
- `static String rune(int ritual, char key)`: the full item id, e.g. `"ancientspellcraft:rune_mannaz"`.
- `static char centrepiece(int ritual)`: the grid-centre character.
- `static String catalyst(int ritual)`: `null`, or the catalyst item id for Condensing.

  Verify Condensing's catalyst id in ASC's `Condensing` class before hardcoding it. ASC calls it "Wand Condensing Upgrade".

`SanctumLayout`:
- `RoomType.RITUAL`, appended after `TELEPORT`, before `CRYPT`. Not an anchor room.
- `public SanctumBox stairDoor`: the antechamber↔stair carve.
- `public int ritualKind = -1`.
- `public final List<int[]> runes`: `x, y, z, ritual, keyChar` (the char as int).
- `public int[] ritualCentre`: `x, y, z`, the empty centrepiece cell, or null.
- `public int[] hintChest`: `x, y, z, facing`, or null.

Patterns (from ASC 1.8.3 `assets/ancientspellcraft/rituals/*.json`):

- **Rejuvenation:** `" M ", "MOM", " M "`, M=mannaz, O=odal, centre O.
- **Bonfire:** `" S ", "APN", " W "`, S=sowilo, A=ansuz, P=peorth, N=naudiz, W=wynn, centre P.
- **Condensing:** `"C O N", " D E ", "  I  ", " S N ", "N   G"`, C=kaunan, O=odal, N=naudiz, D=dagaz, E=ehwaz, I=isaz, S=sowilo, G=gyfu, centre I.

**Placement rules (decided here):**

- **Grid mapping.** Grid cell (row i, col j) maps to room-local `a = i - h/2`, `b = w/2 - j`, with y = floor `f`. This is a proper rotation of ASC's `pos.offset(NORTH, i - h/2).offset(WEST, j - w/2)`, never a mirror.
- **Centre cell.** The cell (h/2, w/2) is the centrepiece. It is never added to `runes`; it is stored in `ritualCentre`.
- **Footprint.** All cells of the grid footprint, blanks included, are marked used, so décor stays off it.
- **Hint chest.** Room-local (-3, 0), facing front. It is not added to `chests` and is not a container.
- **Which ritual.** `ritualKind = r.nextInt(RITUAL_COUNT)`.
- **Type pool.** The side-room type list now has 6 entries:
  - LIBRARY, FORGE, WORKSHOP, TELEPORT and RITUAL;
  - plus one filler (CRYPT or COLLAPSED).
  - `k = 2 + nextInt(4)` is unchanged.
- **Lights.** Each room's candidate light cells are collected first. Then `n = min(count, 2 + r.nextInt(2))` of them, shuffled with `r`, are kept.

  | Room | Candidates | Height |
  |---|---|---|
  | Side room | as in 1.26.0 | f+3 (was f+2) |
  | Chamber | 8 | F2+4 (was F2+3) |
  | Antechamber | 2 | F2+4 |
  | Hall | 4 | F1+3 |
  | Vestibule | 4 | F1+3 |
  | Descent | its 1 landing light | F1+3 (ruling: F1+4 is the shell) |

- **Stair door.** It becomes `carve(-4, f2, -1, -4, f2 + 3, 1)` and is stored in `stairDoor`.

- [ ] **Step 1: Write failing tests.**

  `SanctumRitualsTest`:
  - `gridCentreIsTheCentrepiece`: for each ritual, `pattern(r)[h/2].charAt(w/2) == centrepiece(r)`, and `rune(r, centrepiece(r))` is non-null.
  - `everyKeyResolves`: every non-space char of every pattern resolves through `rune(r, c)` to an id starting with `ancientspellcraft:rune_`.

  `SanctumLayoutTest`:
  - `lightsAreTwoOrThreePerRoom`:
    - for every room, count `magicLights` inside that room's interior;
    - descent ≤ 1, antechamber == 2, every other room 2..3.
  - `stairDoorIsFourHigh`: `stairDoor.maxY - stairDoor.minY == 3`.
  - `ritualRoomAppearsAndIsIncomplete`:
    - over 500 seeds, at least one layout has RITUAL;
    - wherever RITUAL appears:
      - `ritualKind` is in 0..2;
      - `runes.size()` equals the pattern's non-space count minus 1;
      - no rune shares the `ritualCentre` cell;
      - every rune is inside the RITUAL room's interior.
  - `ritualRunesFormARotationOfThePattern`:
    - for each RITUAL layout, map each rune back relative to `ritualCentre`;
    - one of the 4 rotations of the pattern (no mirror) maps every rune's offset to its key char;
    - that same rotation maps every other in-grid cell to a blank or to the centre.
  - `hintChestIsNotALockedChest`:
    - if RITUAL is present, `hintChest != null`, it is inside the room's interior, and no entry of `chests` shares its cell;
    - otherwise `hintChest == null`.
  - Extend `noTwoThingsShareACell`: add `runes`, `ritualCentre` and `hintChest`.
- [ ] **Step 2: Run the tests and confirm RED.**

  `./gradlew "-Dorg.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot" :insanetweaks:test --tests '*Sanctum*'`

  Expected: compile errors (missing symbols).
- [ ] **Step 3: Implement** `SanctumRituals`, then the layout changes above.
- [ ] **Step 4: Run all tests and confirm GREEN.**

  `:insanetweaks:test`

  Expected: all pass. The builder does not need changes yet, because it reads only existing fields.
- [ ] **Step 5: Commit.**

  `feat(insanetweaks): sanctum layout - 2-3 lights per room, taller stair door, ritual room`

### Task 2: Anchor mechanics — buffs, reinforcements, falling anchors

**Files:**
- Create: `anchor/AnchorRules.java` (pure)
- Create: `anchor/AnchorBuffs.java`
- Create: `assets/insanetweaks/loot_tables/anchor_artefact.json`
- Modify:
  - `anchor/TileEntityPetrifiedAnchor.java`
  - `anchor/AnchorLeash.java`
  - `anchor/AnchorLeashHandler.java`
  - `config/categories/EntitiesCategory.java` (`PetrifiedAnchor`)
  - `config/categories/ArcaneSanctumCategory.java`
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/anchor/AnchorRulesTest.java`

**Interfaces — Produces:**

- `AnchorRules.shouldCall(float healthAfter, float maxHealth, double fraction)`:
  - true iff `maxHealth > 0 && healthAfter < maxHealth * fraction`;
  - `fraction <= 0` means never.
- `AnchorRules.artefactChance(boolean inSanctum, boolean pinned, double roomChance, double chamberChance)`:
  - 0 when not in a sanctum;
  - `pinned ? roomChance : chamberChance` otherwise.
- `AnchorLeash.CALLER = "InsaneTweaksAnchorCaller"`: a boolean, set only on mobs released from **unpinned sanctum** anchors.
- `TileEntityPetrifiedAnchor`:
  - `public boolean callToArms(EntityPlayer target)`. If phase is DORMANT, `wakeAllowed(world)` holds and the statue is present:
    - it moves to WAKING;
    - it remembers `target` in a non-saved field;
    - it returns true.
  - `spawnMob` uses `nearestWaker()`, falling back to that target.
- `AnchorBuffs.apply(EntityLiving mob)`:
  - if `Release Buffs` is on:
    - adds a health modifier `+bonus` (op 1, multiply base) under a fixed UUID;
    - adds a speed modifier for `EntitySimWizard` instances (which covers battlemages) under a second fixed UUID;
    - each only when its value > 0;
  - then `mob.setHealth(mob.getMaxHealth())`;
  - modifiers already present are not added again.
  - Called from `track()`, so the legacy release is covered too.
- Falling anchors (`finish(boolean died)` replaces `finish()`):
  - `died` is true only on the `mobGone` path;
  - **sanctum anchors:**
    - sound `SoundEvents.ENTITY_ILLUSION_ILLAGER_MIRROR_MOVE` (1.0, 0.6);
    - a `BLOCK_CRACK` stone burst plus `MagicParasiteTraits.burst`;
    - `anchorDone` as before;
    - then the block is set to air;
    - if `died` and `world.rand.nextDouble() < chance`, the items of `insanetweaks:anchor_artefact` (generated with a `LootContext` from the `WorldServer`) are spawned as `EntityItem`s at the block centre;
  - **non-sanctum anchors:** unchanged (SPENT).
- Reinforcements (`AnchorLeashHandler.onDamage(LivingDamageEvent)`), server side:
  - when the mob has `CALLER` and `shouldCall(health - amount, max, fraction)`:
    1. remove `CALLER`;
    2. the target is the mob's attack target if it is an `EntityPlayer`, else the closest non-spectator player within 16;
    3. for every `TileEntityPetrifiedAnchor` in `world.loadedTileEntityList` within `Reinforcement Radius` of the mob, call `callToArms(target)`.
  - Gated by `Reinforcements`.
- `anchor_artefact.json`:
  - one pool, one roll, entries `loot_table`;
  - `ebwizardry:subsets/uncommon_artefacts` 6, `rare_artefacts` 3, `epic_artefacts` 1;
  - `ancientspellcraft:subsets/uncommon_artefacts` 6, `rare_artefacts` 3, `epic_artefacts` 1.

- [ ] **Step 1: Write `AnchorRulesTest` (RED).**
  - `callsBelowFraction`: `shouldCall(24f, 100f, 0.25)` is true; `shouldCall(25f, 100f, 0.25)` is false.
  - `lethalHitStillCalls`: `shouldCall(-5f, 100f, 0.25)` is true.
  - `zeroFractionNeverCalls`: `shouldCall(1f, 100f, 0.0)` is false.
  - `artefactChanceBySource`:
    - `(true, true, 0.2, 0.5)` → 0.2;
    - `(true, false, 0.2, 0.5)` → 0.5;
    - `(false, false, 0.2, 0.5)` → 0.0.
- [ ] **Step 2: Run** `:insanetweaks:test --tests '*AnchorRulesTest'`. Expected: compile failure.
- [ ] **Step 3: Implement** `AnchorRules`, then config fields (names in Global Constraints, with comments saying "0 disables"), `AnchorBuffs`, the tile-entity changes, the handler, and the loot table.
- [ ] **Step 4: Run** `:insanetweaks:test` green, then `:insanetweaks:compileJava`. Expected: no new warnings in touched files.
- [ ] **Step 5: Commit.**

  `feat(insanetweaks): anchor mobs - switchable buffs, chamber reinforcements, falling anchors drop artefacts`

### Task 3: Sanctum footprint, advancements, cleanse sound

**Files:**
- Create: `worldgen/sanctum/SanctumAdvancements.java`
- Create: `worldgen/sanctum/SanctumFoundHandler.java`
- Create: `assets/insanetweaks/advancements/sanctum/found.json` and `sanctum/cleansed.json`
- Modify:
  - `worldgen/sanctum/SanctumWorldData.java`
  - `worldgen/sanctum/SanctumPlacement.java` (`register`)
  - `worldgen/sanctum/SanctumLocks.java` (`anchorDone`)
  - `InsaneTweaksMod.java` (register the handler when `arcaneSanctum.enabled`)
  - `en_us.lang`

**Interfaces — Produces:**

- `SanctumWorldData`:
  - `register(long id, Collection<Long> anchors, Collection<Long> containers, int minX, int minZ, int maxX, int maxZ)` replaces the old signature; both callers pass `l.bounds`;
  - saved as int array `"Bounds"`; old records have none and are ignored by the next method;
  - `public boolean nearSanctum(int x, int z, int margin)`: true if any bounded record's box, grown by `margin`, contains x, z.
- `SanctumAdvancements.grant(EntityPlayerMP player, String path)`:
  - looks up `new ResourceLocation(MODID, path)` in the server's advancement manager;
  - grants every remaining criterion;
  - no-op if the advancement is missing or done.
- `SanctumFoundHandler.onPlayerTick(TickEvent.PlayerTickEvent)`:
  - server side, END phase, every 40 ticks;
  - for `EntityPlayerMP` in an allowed dimension (`SanctumPlacement.dimensionAllowed`), if `nearSanctum(px, pz, 8)` then `grant(player, "sanctum/found")`.
- `SanctumLocks.anchorDone`: when the sanctum clears, each player within `SYNC_RANGE` of `announce`:
  - hears `SoundEvents.UI_TOAST_CHALLENGE_COMPLETE` at their position (category PLAYERS, 1.0, 1.0);
  - is granted `sanctum/cleansed` (if an `EntityPlayerMP`);
  - and still gets the existing message.
- Advancements:
  - **`found`:** a root. Icon `ancientspellcraft:sealed_stone`, frame `task`, background `minecraft:textures/blocks/stonebrick_mossy.png`, criterion `{"found":{"trigger":"minecraft:impossible"}}`, `announce_to_chat` true.
  - **`cleansed`:** parent `insanetweaks:sanctum/found`, icon `ancientspellcraft:unseal_button` (falling back to `ebwizardry:magic_crystal` if that is not an item), frame `goal`, criterion `impossible`.
  - Titles and descriptions use the translate keys `advancement.insanetweaks:sanctum.found(.desc)` and `advancement.insanetweaks:sanctum.cleansed(.desc)`.
- Lang (`en_us.lang`):
  - `advancement.insanetweaks:sanctum.found=Something Sleeps Below`
  - `advancement.insanetweaks:sanctum.found.desc=A dangerous and valuable structure lies beneath your feet`
  - `advancement.insanetweaks:sanctum.cleansed=Sanctum Cleansed`
  - `advancement.insanetweaks:sanctum.cleansed.desc=Defeat every guardian of a Sunken Arcane Sanctum`

- [ ] **Step 1:** Check that `ancientspellcraft:sealed_stone` and `unseal_button` have item forms. Run `unzip -l libs/AncientSpellcraft-*.jar | grep models/item/` and pick the icons accordingly.
- [ ] **Step 2: Implement.** Both advancement JSONs must parse; validate them with Python `json.load`.
- [ ] **Step 3: Verify.** `:insanetweaks:test` and `:insanetweaks:compileJava` are green.
- [ ] **Step 4: Commit.**

  `feat(insanetweaks): sanctum advancements (found, cleansed) and the cleanse sound`

### Task 4: Builder — ritual room, hint book, bookshelf extras

**Files:**
- Create: `worldgen/sanctum/SanctumShelfRoll.java` (pure)
- Create: `worldgen/sanctum/SanctumRitualBook.java`
- Modify:
  - `worldgen/sanctum/SanctumBuilder.java`
  - `worldgen/sanctum/SanctumBooks.java`
  - `config/categories/ArcaneSanctumCategory.java`
  - `util/EnchantSourceGuard.java` (javadoc only)
  - `en_us.lang`
- Test: `insanetweaks/src/test/java/com/spege/insanetweaks/worldgen/sanctum/SanctumShelfRollTest.java`

**Interfaces — Produces:**

- `SanctumShelfRoll`:
  - `NONE = 0`, `TREASURE = 1`, `INSANE = 2`;
  - `static int kind(double roll, double treasureChance, double insaneChance)`:
    - `roll < treasure` → TREASURE;
    - else `roll < treasure + insane` → INSANE;
    - else NONE.
- `SanctumBooks.fillExtras(IInventory shelf, long seed, int x, int y, int z, int rolls, double treasure, double insane)`:
  - roll i uses noise channels 130+i (kind) and 140+i (pick);
  - **TREASURE:** `EnchantmentHelper.addRandomEnchantment(new Random(SanctumNoise.seedAt(seed, x, y, z) + i), new ItemStack(Items.BOOK), 30, true)`;
  - **INSANE:** a uniform pick over a lazily built list of:
    - every `ForgeRegistries.ENCHANTMENTS` entry with domain `insanetweaks`, as `ItemEnchantedBook.getEnchantedItemStack(new EnchantmentData(e, 1))`;
    - every `AdvPropertyRegistry.getAll()` entry with `bookGrantable`, as `PropertyBookItem.create(ModItems.PROPERTY_BOOK, id)`, only when `ModConfig.modules.enablePropertyBooks` is on;
  - each book goes into the first empty slot; if none is free it is dropped silently.
- `SanctumRitualBook.create(int ritual)` returns `ItemStack`:
  - a `minecraft:written_book`, title `"Ritual Notes"`, author `"A Departed Sage"`, `resolved` 1;
  - `pages` holds JSON strings `{"translate":"book.insanetweaks.ritual.<id>.p<n>"}` for n = 1..3.
- Builder:
  - **Runes:** for each entry of `runes` inside the clip:
    - `setBlockState(ancientspellcraft:placed_rune default)`;
    - then on the `TileRune`: `setRune(Item.getByNameOrId(SanctumRituals.rune(ritual, key)))` and `setDirection(facing of the room front)`.
    - Missing block or item → skip, with one warning via the Palette lookup path.
  - **Hint chest:** a plain chest with the book in slot 13; no loot table, no lock.
  - **Bookshelf extras:** each EBW shelf calls `fillExtras` after `SanctumBooks.fill`.
- Config (`ArcaneSanctumCategory`): `bookshelfExtraRolls` 2, `bookshelfTreasureChance` 0.03, `bookshelfInsaneBookChance` 0.04. `roomAnchorPool` default as in Global Constraints.
- `EnchantSourceGuard` javadoc gains a bullet in "WHAT THIS DOES NOT TOUCH": sanctum EBW bookshelves are written pre-built level-1 books by the builder, including the curse; this is a deliberate exception (spec §14.9).
- Lang: 3 pages per ritual (9 keys). Each set gives:
  - the name;
  - the effect in one sentence;
  - activation;
  - **what is missing**: the centrepiece rune by name, and for Condensing also the Wand Condensing Upgrade thrown on it after the pattern completes;
  - the rune recipe: Blank Rune + Arcane Compound + the dyes, taken from that rune's ASC recipe json. Read them: `rune_odal`, `rune_peorth`, `rune_isaz`.

  Use `\n` line breaks. Pages must stay under 256 characters each.

- [ ] **Step 1: Write `SanctumShelfRollTest` (RED).**
  - `treasureBand`: `kind(0.02, 0.03, 0.04) == TREASURE`.
  - `insaneBand`: `kind(0.05, 0.03, 0.04) == INSANE`.
  - `none`: `kind(0.07, 0.03, 0.04) == NONE`.
  - `zeroChancesNeverHit`: `kind(0.0, 0.0, 0.0) == NONE`.
- [ ] **Step 2: Run** `:insanetweaks:test --tests '*SanctumShelfRollTest'`. Expected: compile failure.
- [ ] **Step 3: Implement everything above.**
- [ ] **Step 4: Run** `:insanetweaks:test` green and `:insanetweaks:build` green.
- [ ] **Step 5: Commit.**

  `feat(insanetweaks): ritual room with hint book, bookshelf treasure and insanetweaks books`

### Task 5: Version, verify, instance config, deploy

- [ ] **Step 1: Version.** Bump to `1.27.0` in both places. Run `:insanetweaks:test :insanetweaks:build`.
- [ ] **Step 2: Bytecode checks.**
  - `javap -v -p` over `worldgen/sanctum/*.class` and `anchor/*.class` shows no `minecraft/client` or `fml/client`.
  - `SanctumBaubles` is still the only class naming the baubles API.
  - The jar contains `anchor_artefact.json` and both advancement files.
- [ ] **Step 3: Instance configs.** Back up each file, then edit byte-exactly:
  - **`insanetweaks.cfg`:** in `Room Anchor Pool`, change `insanetweaks:sim_battlemage;1` to `;2`.
  - **`manacore.cfg`:** add a line `        insanetweaks:sanctum/cleansed=15` inside `S:bonuses <`, right after its first entry. Match the existing indentation and the CRLF line endings.
  - `cmp` against the backup shows only the intended change.
- [ ] **Step 4: Deploy.**
  - The pack currently has `insanetweaks-1.26.1.jar` from another session.
  - Do the lock check, copy `insanetweaks-1.27.0.jar`, then `rm` the 1.26.1 jar with a literal path.
  - Exactly one `insanetweaks-*.jar` remains.
- [ ] **Step 5: Commit** `chore(insanetweaks): 1.27.0`.
