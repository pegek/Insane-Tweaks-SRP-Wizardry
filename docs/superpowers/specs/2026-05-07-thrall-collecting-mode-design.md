# Thrall COLLECTING Mode — Design Spec

**Date:** 2026-05-07
**Module:** `insanetweaks` — Minecraft 1.12.2 Forge mod
**Status:** Draft for implementation

## 1. Goal

Add a sixth Thrall work mode, `COLLECTING`. Player gives the thrall 1–4 block types (by tossing the corresponding items at it), the thrall locks in those targets, then explores the world via teleportation, harvests matching blocks for up to 2 hours, and returns home when time expires or its inventory is full.

## 2. User-flow summary

1. Player clicks `COLLECTING` in the thrall command menu → status: *"Waiting for items..."*
2. Player tosses 1–4 distinct block-items (e.g. iron_ore, oak_log).
   - Thrall accepts each unique `(item, metadata)` signature; duplicates are ignored.
   - Lock-in trigger: **4 unique types collected** OR **12 seconds elapsed since the first accepted item**.
3. State transitions to `SEARCHING`. Status: *"Searching..."*
4. Each cycle: random teleport in a ring around home, local sphere-scan for targets, harvest with vein-BFS, repeat.
5. Termination: **2 h elapsed** OR **inventory full** OR **N consecutive empty cycles** (cold-start safety) → TP home, smartDeposit, switch to `STAY`. Status: *"Done collecting (X items)"*

## 3. Identity & target selection

- Targets are stored as `(Block, metadata)` pairs (NBT ignored — block-form items don't carry NBT).
- Resolved on item pickup: `Block.getBlockFromItem(itemstack.getItem())` + `itemstack.getMetadata()`.
- Items that don't resolve to a non-air block are rejected (status: *"Not a block"*).
- Up to 4 distinct pairs accepted. Duplicates of an already-locked pair refresh the 12s timer but don't add a slot.

## 4. State machine

```
WAITING_FOR_ITEMS  ── 4 types OR 12s ──> SEARCHING
SEARCHING          ── targets in scan ─> HARVESTING
SEARCHING          ── empty scan ──────> SEARCHING (next TP)
HARVESTING         ── local list done ─> SEARCHING
any                ── 2h | inv-full | N empty cycles ──> RETURNING
RETURNING          ── deposit done ────> DONE (setMode(STAY))
```

**Mutex bits:** `3` (matches all other Thrall AI tasks).

**Persistence:** entire state lives in NBT under `CollectingState` compound. Cleared on `DONE` and on mode-switch when no resume window is active (see §10).

## 5. Random-TP exploration

Per-cycle:
1. Pick polar coordinates: `angle = rand(0, 2π)`, `dist = rand(MIN_DIST, MAX_DIST)`. Defaults `30..150` blocks.
2. Compute `(x, z)` relative to **home point**.
3. Choose `Y` adaptively (see §6).
4. Resolve a safe spawn slot: empty 2-block air column with solid floor; if not found within ±4 of computed Y, retry with a new `(x, z)`.
5. Teleport. Increment `searchCycleCount`.

**Cycle pacing:** one TP per `SEARCH_TICK_INTERVAL` (default 40 ticks = 2 s). Scan happens immediately after TP.

**Cold-start safety:** if `consecutiveEmptyCycles >= MAX_EMPTY_CYCLES` (default 30) → `RETURNING`. Prevents infinite spin when targets are extinct in the area.

## 6. Adaptive Y heuristic

Each target gets a `YHint` enum on intake: `SURFACE`, `UNDERGROUND`, `ANYWHERE`.

- `SURFACE`: `Y = world.getHeight(x, z)` then probe up for first air.
- `UNDERGROUND`: `Y = rand(5, 40)`.
- `ANYWHERE`: alternate between SURFACE and `rand(5, 60)`.

**Classification rule (best-effort, no per-mod hardcoding):**
- If `block instanceof BlockOre || block instanceof BlockStone || block.getMaterial() == Material.ROCK` → `UNDERGROUND`.
- Else if `block instanceof BlockLog || block instanceof BlockLeaves || material is GRASS / GROUND / WOOD` → `SURFACE`.
- Else → `ANYWHERE`.

Mixed target list: pick a Y-hint round-robin from the targets list, so each cycle favors a different target. This avoids the thrall ignoring half the list.

## 7. Sphere scan + vein-BFS

After TP:
1. **Sphere scan:** iterate every `(dx, dy, dz)` in `|d| <= SCAN_RADIUS` (default 8), filter to `Math.sqrt(...) <= SCAN_RADIUS`. Match against target set by `(block, metadata)`. Cap result list at `MAX_SCAN_HITS` (default 64) to avoid runaway iteration in dense veins.
2. If scan empty → `consecutiveEmptyCycles++`, return to `SEARCHING`.
3. Else enter `HARVESTING` with a queue of scan hits.

**Per-block harvest:**
1. TP adjacent to target block (offset by closest face, fallback: TP into the block's column at +1 Y).
2. Wait `MINING_TICKS = max(3, hardness * MINING_SPEED_MULTIPLIER)` ticks (same formula as `ThrallAIMineshaft`).
3. Drop via `block.dropBlockAsItem(world, pos, state, 0)` → all drops are auto-picked by `passiveItemPickup` within the next 5 ticks.
4. **Vein-BFS:** push 26 neighbors of the just-broken block onto the harvest queue if they match the same `(block, metadata)`. Limits: `VEIN_MAX_BLOCKS = 50` per visit, BFS frontier depth `30` blocks from the BFS root.

When the harvest queue empties → reset `consecutiveEmptyCycles = 0`, transition back to `SEARCHING`.

## 8. Termination & deposit

Termination triggers (checked once per tick):
- `(now - sessionStartTick) >= COLLECTING_DURATION_TICKS` (default 2h = 144000 ticks).
- `thrallInventory.isFull()`.
- `consecutiveEmptyCycles >= MAX_EMPTY_CYCLES`.

On termination → `RETURNING`:
1. TP to `homePoint`.
2. `ThrallChestHelper.smartDeposit(thrall, homePoint, ModConfig.thrall.collectingChestScanRange, 4, false)` — same chest-search as Porter, no torch retention.
3. Set status: *"Done collecting (N items)"* where `N = totalItemsHarvestedThisSession`.
4. `setMode(STAY)`. NBT `CollectingState` cleared.

## 9. Damage & interruption

- **Damage:** thrall fights in place via existing damage handler (no behavior change). After kill, resumes `SEARCHING` from current position (next TP relative to home).
- **Player command interruption** (player picks `FOLLOW`/`STAY`/another mode): `CollectingState` is **preserved in NBT** with `pausedAtTick = now`.

## 10. Resume window

If the player re-clicks `COLLECTING` while `pausedAtTick` is non-zero AND `(now - pausedAtTick) <= RESUME_WINDOW_TICKS` (default 5 min = 6000 ticks):
- Restore the previous target list, `sessionStartTick`, `totalItemsHarvestedThisSession`, etc.
- Adjust `sessionStartTick += (now - pausedAtTick)` so the 2h budget effectively pauses during the break.
- Skip `WAITING_FOR_ITEMS`, jump straight to `SEARCHING`.
- Status: *"Resuming..."*

Otherwise (no saved state OR window expired): standard `WAITING_FOR_ITEMS` start, prior state cleared.

## 11. Item-pickup hook

`EntityThrallMinion.passiveItemPickup` already pulls nearby `EntityItem`s. We add a single hook **before the existing pickup loop** for the COLLECTING-WAITING case:

```java
if (mode == COLLECTING && collectingState.phase == WAITING_FOR_ITEMS) {
    for (EntityItem ei : nearby) {
        ItemStack s = ei.getItem();
        Block b = Block.getBlockFromItem(s.getItem());
        if (b == Blocks.AIR) {
            // not a block — skip; will fall through to normal pickup
            continue;
        }
        boolean accepted = collectingState.tryAddTarget(b, s.getMetadata(), thrall);
        if (accepted) {
            // shrink stack by 1, mark item dead if empty (consume one item per pickup)
            s.shrink(1);
            if (s.isEmpty()) ei.setDead();
            // refresh status / lock-in trigger checks
        }
    }
}
```

Items NOT matching block-form and items beyond the 4-target cap fall through to `passiveItemPickup` normally (so the thrall doesn't refuse food etc.).

## 12. Configs (`ModConfig.thrall`)

| Field | Default | Range/Type | Comment |
|---|---|---|---|
| `enableCollectingMode` | `true` | bool | `@Config.RequiresMcRestart` |
| `collectingDurationMinutes` | `120` | 5..480 | session length cap |
| `collectingItemPickupTimeoutSeconds` | `12` | 3..60 | lock-in timer |
| `collectingMaxTargets` | `4` | 1..8 | distinct target cap |
| `collectingMinTpDistance` | `30` | 8..256 | inner ring radius |
| `collectingMaxTpDistance` | `150` | 16..1024 | outer ring radius |
| `collectingScanRadius` | `8` | 4..16 | sphere-scan radius |
| `collectingVeinMaxBlocks` | `50` | 1..256 | BFS cap per root |
| `collectingMaxEmptyCycles` | `30` | 5..200 | cold-start abort |
| `collectingTickInterval` | `40` | 10..200 | ticks per TP cycle |
| `collectingChestScanRange` | `40` | 8..64 | for final deposit (matches Porter default) |
| `collectingResumeWindowMinutes` | `5` | 0..60 | 0 = no resume |

## 13. Files touched

| File | Change |
|---|---|
| `entities/ThrallMode.java` | `+ COLLECTING("collecting")` enum entry |
| `entities/ai/ThrallAICollecting.java` | **new** — full state machine, ~600 LOC |
| `entities/EntityThrallMinion.java` | wire AI task into `initEntityAI` (gated by config); add COLLECTING-WAITING branch in `passiveItemPickup`; persist resume fields |
| `network/PacketThrallCommand.java` | `+ ACTION_COLLECTING` packet handler |
| `config/ModConfig.java` | 13 new fields under `thrall.collecting*` |
| `client/gui/...` (thrall command menu) | `+ "Collecting"` button (mirrors existing buttons; locate existing menu file during impl) |
| `resources/assets/insanetweaks/lang/en_us.lang` | `gui.insanetweaks.thrall.mode.collecting=...` plus status keys |
| `resources/assets/insanetweaks/lang/pl_pl.lang` | matching Polish strings |

## 14. Testing checklist

- Click COLLECTING with no items thrown → 12s later transitions to SEARCHING, immediately enters cold-start abort path → returns home with empty inv.
- Throw 1 item, wait 12s → starts with 1 target.
- Throw 4 items → instant lock-in, no 12s wait.
- Throw 5 items → 5th rejected, status *"Targets full (4)"* or similar.
- Throw a non-block item (sword, food) → falls through to normal pickup, not added to targets.
- Surface target (oak_log): TP lands on grass, scan finds tree, vein-BFS clears whole tree.
- Underground target (iron_ore): TP lands at low Y, finds vein, clears it.
- Mixed list (oak_log + iron_ore): cycles alternate Y-hints.
- 2h timeout: returns home, deposits, status shows count.
- Inv full: returns home early.
- FOLLOW interrupt + re-COLLECTING within 5 min: resumes with same targets, time budget unaffected by break.
- FOLLOW interrupt + re-COLLECTING after 6 min: starts fresh, asks for items again.
- Damage during SEARCHING: fights, then resumes TP cycle.
- Optional mod compat: vein-BFS over modded ores (deepslate iron, etc.) works because we match by `(block, metadata)` — both vanilla `BlockOre` and modded blocks are matched the same way.

## 15. Out-of-scope (explicit non-goals)

- No filter/whitelist UI — only block-toss interaction.
- No tool requirement / pickaxe gating — drops are auto-yielded (matches Mineshaft).
- No tunneling through stone to reach buried targets — TP-only access.
- No chunk-loading of distant TPs — relies on engine-loaded chunks; if a TP target is in unloaded space, the safe-spot resolver fails and the cycle retries.
- No persistence across server restart of the *paused* state — resume window only spans live gameplay (NBT is written, but the 5-min wall-clock window uses world tick time, so reload effectively counts as elapsed).
