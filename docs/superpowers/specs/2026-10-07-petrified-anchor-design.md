# Petrified Anchor — design (insanetweaks 1.23.0)

Status: approved in brainstorming 2026-10-07, awaiting spec review.
Owner mod: **insanetweaks** (content). All code, lang and player-facing text in English.

## 1. Why

Structures need a way to place a dormant enemy that reads as part of the scenery: a stone statue
of a monster on a plinth that comes to life when a player walks up to it. This anchor is the
first building block of the planned imbuement-altar structure, and it is the main way the magic
parasites (spec `2026-10-06-magic-parasites-design.md`) will enter the world.

## 2. Player experience

1. A stone statue of a parasite stands on a carved stone plinth. It looks like decoration.
2. A player comes within 4 blocks. The statue shudders for one second: stone debris, purple
   sparks and a cracking sound.
3. The statue breaks apart, the mob steps out and goes for that player.
4. The plinth stays behind, visibly cracked and inert, and can be mined.

Before it wakes, the plinth cannot be mined in survival, so the ambush cannot be dug out. The
statue itself behaves as in EBW: breaking it from range, or blowing it up, releases the mob, and
the anchor counts that as waking.

## 3. Scope

In scope:

- One block `insanetweaks:petrified_anchor` with an `awakened` property, its tile entity and
  creative item.
- Mob choice from a weighted config pool, or a creative pin made with a spawn egg.
- Proximity wake-up with a telegraph.
- Config, lang, and tests for the pure pool logic.

Out of scope:

- The structure itself (next spec).
- Chain wake-ups, line-of-sight checks, rewards tied to the plinth.

## 4. Reused EBW mechanism (bytecode-verified, EBW 4.3.19)

- `WizardryBlocks.petrified_stone` is a `BlockStatue` with `TileEntityStatue`.
  `BlockStatue.convertToStatue(EntityLiving mob, EntityLivingBase caster, int lifetime)`:
  - places 1–3 statue blocks at the mob's position (more for tall mobs, which need free space
    above);
  - stores the mob's NBT in the tile entity and sets the `petrified` entity-data flag;
  - kills the entity object.

  `RenderStatue` draws the real mob model in stone.
- `BlockStatue.breakBlock` on the bottom part releases the mob: it removes `petrified`, resets
  `isDead` and calls `spawnEntity`. That is our "wake" action. Explosions and mining release it
  the same way.
- `TileEntityStatue` breaks itself when `timer > lifetime` (checked every 200 ticks). We pass
  `Integer.MAX_VALUE`, so it never expires on its own.
- With `caster == null`, EBW's placement check uses `Settings.dispenserBlockDamage` (`true` in
  DEv 1.2) instead of the mob-griefing gamerule, so the statue does not depend on mobGriefing.
  If a pack sets it to false, conversion fails and §6's retry and warning cover it.

The mob is **never spawned into the world** while dormant. It is created, positioned, given
`onInitialSpawn` (equipment, SRP stats) and converted straight away, so it costs no mob cap, no
AI ticks and no SRP bookkeeping until it wakes. Creating it fresh at that moment, rather than
storing it in the structure, gives every copy of a structure its own UUID.

## 5. Architecture

Package `com.spege.insanetweaks.anchor`:

- `BlockPetrifiedAnchor`:
  - stone material, `PropertyBool AWAKENED`; has a tile entity only while not awakened;
  - hardness -1 (unbreakable) while active and 2.0 once awakened;
  - drops cobblestone when awakened, nothing while active;
  - `onBlockActivated` handles the creative pin (§7).
- `TileEntityPetrifiedAnchor` (`ITickable`):
  - server-side state machine (§6);
  - NBT: `PinnedMob` (string, optional), `Phase` (byte), `StatueY` (int offset of the statue's
    bottom block), `Attempts` (byte).
- `AnchorPool` (pure, no Minecraft types, unit-tested):
  - parses `id;weight` lines and ignores and reports malformed ones;
  - `pick(double roll)` returns an id by weight, or null for an empty pool.
- `AnchorStatues` holds the only code that names EBW types (`BlockStatue`,
  `WizardryBlocks.petrified_stone`, `TileEntityStatue`):
  - `petrify(World, BlockPos above, EntityLiving)`;
  - `isStatueAt(World, BlockPos)`;
  - `release(World, BlockPos)` (`destroyBlock` on the bottom part);
  - `discard(World, BlockPos)` (§7, removes the statue without releasing the mob).

  EBW is a `required-after` dependency, so this is for clarity, not class-loading safety.

Registration follows `init/ModBlocks`:
- the block plus its `ItemBlock`, in the creative MISC tab like the sanctuary blocks;
- `GameRegistry.registerTileEntity`;
- blockstate JSON with `awakened=false` / `awakened=true` variants;
- item model;
- two textures, `petrified_anchor` (intact plinth) and `petrified_anchor_cracked`.

## 6. Tile entity state machine (server only)

| Phase | Behaviour |
|---|---|
| `EMPTY` (0) | First tick and every 100 ticks after that: if the dimension is on the anchor's blacklist, do nothing. Otherwise choose the mob (pin, else `AnchorPool.pick`) and create it with `EntityList.createEntityByIDFromName`. Position it centred on `pos.up()`, facing a random yaw, then call `onInitialSpawn` and `AnchorStatues.petrify`. On success go to `DORMANT`. On failure (no room above, unknown id, EBW refused) increase `Attempts`; at 3, log one warning with the position and the reason, and keep retrying quietly. |
| `DORMANT` (1) | Every 10 ticks: if the statue is gone (broken, exploded), go to `SPENT`. Otherwise look for the nearest player within `wakeRadius` who is not in creative or spectator (`World.getClosestPlayer` with a predicate). If one is there, go to `WAKING`. |
| `WAKING` (2) | For `crackTicks` (20) ticks: stone-debris (`BLOCK_CRACK` with stone) and `SPELL_WITCH` particles around the statue, plus a stone-hit sound every 5 ticks. Then call `AnchorStatues.release`, which breaks the bottom statue block so EBW spawns the mob. Find the released mob (newest living entity of the chosen type within 2 blocks of the statue). If it is an `EntityParasiteBase`, call `setAttackTarget(player, 60)`; otherwise call plain `setAttackTarget(player)`. Go to `SPENT`. |
| `SPENT` (3) | Set the block state to `awakened=true`. This removes the tile entity. |

The tile entity ticks on the server only. The client needs no state, because the statue renders
through EBW.

If the chunk unloads during `WAKING`, the phase is saved and the next load continues from
`WAKING`.

## 7. Creative pinning

- Right-clicking an **active** anchor with a spawn egg (`ItemMonsterPlacer`) sets `PinnedMob` to
  the egg's entity id, using `ItemMonsterPlacer.getNamedIdFrom`. Creative mode only.
  - If a statue is already standing, it is removed without releasing the mob. Setting the blocks
    to air is **not** enough on its own, because in 1.12 `Chunk.setBlockState` calls the old
    block's `breakBlock`, which is EBW's release. So `AnchorStatues.discard` first nulls the
    bottom tile entity's public `creature` field (`breakBlock` releases only when it is
    non-null), then sets the statue blocks to air.
  - The anchor then returns to `EMPTY` and builds the new statue on its next check.
  - The player gets a chat message: `Anchor pinned to <name>`.
- Sneak plus right click with an empty hand (creative) clears the pin the same way:
  `Anchor reset to the random pool`.
- Pins survive structure templates, because they live in the tile entity's NBT.

## 8. Config

New subcategory `entities.petrifiedAnchor` (`EntitiesCategory.PetrifiedAnchor`), all values read
live:

| Setting | Default |
|---|---|
| `Pool` | `insanetweaks:magic_longarms;1`, `insanetweaks:magic_manducater;1`, `insanetweaks:magic_summoner;1` |
| `Wake Radius` | 4.0 |
| `Crack Ticks` | 20 |
| `Dimension Blacklist` | `{150}`. Dim 150 never gets SRP; an anchor there stays an empty plinth (memory `dim150-no-srparasites-invariant`). |

## 9. Lang (en_us)

```
tile.insanetweaks.petrified_anchor.name=Petrified Anchor
msg.insanetweaks.anchor.pinned=Anchor pinned to %s
msg.insanetweaks.anchor.reset=Anchor reset to the random pool
config.insanetweaks.category.entities.petrified_anchor=Petrified Anchor
```

## 10. Safety and invariants

- No client types in the block, tile entity or helpers, verified with `javap` like the other
  content classes.
- Spawning goes through EBW's `spawnEntity` on release, which bypasses CheckSpawn just like
  every other content spawn. The dimension blacklist is the guard.
- The tile entity holds no entity reference while dormant, only ids and the phase, so no stale
  entity can leak across chunk reloads.
- A missing or removed EBW statue never strands the anchor: `DORMANT` checks `isStatueAt` every
  pass.

## 11. Testing

- **JUnit `AnchorPoolTest`:**
  - parsing (valid, malformed, zero or negative weights ignored);
  - weighted pick at roll boundaries;
  - empty pool returns null.
- **Bytecode checks:** side safety; the tile entity is registered; the block has no client
  references.
- **In game, DEv 1.2:**
  - Place an anchor in creative. A random magic-parasite statue appears on top.
  - Pin it with a spawn egg: the statue changes. Clear the pin: it re-rolls.
  - Switch to survival and walk up. The statue cracks for about one second, the mob comes out
    and attacks, and the plinth turns cracked and becomes mineable.
  - With no room above, nothing appears and the log shows one warning after 3 attempts.
  - Breaking the statue from a distance releases the mob, and the plinth turns cracked.
  - Save and reload while dormant: the statue stays and still wakes.
  - In dimension 150, the anchor stays an empty plinth.
