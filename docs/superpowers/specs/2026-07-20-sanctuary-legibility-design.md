# Sanctuary Dome — Legibility Redesign (Design Spec)

**Date:** 2026-07-20
**Branch:** `feat/sanctuary-dome`
**Follows:** `docs/superpowers/specs/2026-07-19-sanctuary-dome-design.md` (original build)

## Problem

Play-test feedback: the Sanctuary Core "probably doesn't work" (`chyba nie działa`).
The user built two valid pyramids (diamond 5×5+3×3+core; iron 3×3+core) and saw no
evidence of function.

**Root cause (diagnosed, not a mechanic bug):** the `TileEntitySanctuaryCore` server
state (`tier`, `effectiveRadius`, `cleanseEnabled`, `cleanseStalled`, `fuelStored`)
**never reaches the client.** The TE implements no `getUpdateTag`/`getUpdatePacket`;
the `ContainerSanctuaryCore` implements no `detectAndSendChanges`/`IContainerListener`
field sync. The GUI reads client-side TE fields that are always their defaults, so it
**always displays Tier 0 / R 0** regardless of real server state.

Grounding check against the actual scan (`scanPyramidTier`): layer L = (2L+1)² at
`y = coreY − L`, blocks must be in `pyramidBlocks` (`minecraft:iron_block`,
`minecraft:diamond_block`). Both test pyramids satisfy this →
diamond build = **Tier 2 / R32**, iron build = **Tier 1 / R16** server-side. The
mechanic is live; the player simply cannot see it. This redesign is ~95% legibility,
and touches **no** existing mechanic (`scanPyramidTier`, `SanctuaryWorldData`, spawn
veto, cleanse, upkeep are unchanged).

## Goals

1. Make server truth visible: GUI shows the real tier / radius / protection / cleanse / fuel.
2. Make "why is it inactive" legible, not a bare Tier 0.
3. Show the protected region in-world (particle border).
4. Give instant feedback on interaction and on state transitions.
5. Give us (devs) a server-side diagnostic log to confirm truth independent of the client.
6. Replace the borrowed vanilla dispenser GUI with a dedicated Sanctuary panel showing
   only the slots we use.

Non-goals: AI-growth mixins, meteor veto, any change to detection/cleanse/veto math.

## 1. Sync mechanism (chosen: hybrid TE-update + Container window-properties)

- **TileEntity → client (render + at-a-glance state).** Implement `getUpdateTag()`,
  `getUpdatePacket()` (returns `SPacketUpdateTileEntity`), `onDataPacket()`,
  `handleUpdateTag()`. Synced fields: `tier`, `effectiveRadius`, `protectionActive`
  (bool), `cleanseState` (enum ordinal: `RUNNING`/`STALLED`/`OFF`), `statusCode` (enum
  ordinal: `ACTIVE`/`NO_PYRAMID`/`DIM_BLACKLISTED`). Whenever `revalidateAndSync`
  changes any synced field on the server, call
  `world.notifyBlockUpdate(pos, state, state, 3)` so the update packet is sent. This
  populates the **client** TE, which drives both the GUI text and the particle border.
- **Container → open GUI (fast-changing, GUI-only value).** Implement
  `detectAndSendChanges()` + `addListener()` and push **`fuelStored`** via
  `IContainerListener.sendWindowProperty(this, id, value)` (short 0–32767; clamp).
  `updateProgressBar(id, value)` stores it client-side. Fuel changes every burn tick and
  only matters with the GUI open, so it does not belong on the block-update path.

Rationale: the particle border needs the radius on the client even when no GUI is open,
which only the TE-update path provides; fuel is GUI-only and cheap via window property.
No new custom packet; consistent with every other TE/GUI in the mod.

## 2. Status model (split — the core of the legibility win)

Two independent, separately-displayed facts instead of one "Tier":

- **Protection** = ON when `tier ≥ 1` (region registered in `SanctuaryWorldData` →
  spawn-veto + infestation-veto active). **Independent of fuel.**
- **Cleanse** = `RUNNING` / `STALLED (out of fuel)` / `OFF (disabled)`. **Only cleanse
  depends on fuel.**

`SanctuaryStatus` enum (for the "why inactive" line): `ACTIVE`, `NO_PYRAMID`,
`DIM_BLACKLISTED`. `CleanseState` enum: `RUNNING`, `STALLED`, `OFF`. Both synced as
ordinals via the TE update tag.

## 3. Dedicated GUI (channels 1 + 2)

**Custom panel, not the vanilla dispenser/furnace look.** Only the slots we use.

- **Texture:** new asset `assets/insanetweaks/textures/gui/sanctuary_core.png`
  (256×256 canvas, used region 176×166 — standard container width keeps the player-inv
  math unchanged). Themed to the Sanctuary (dark stone / eldritch-ward palette), **not**
  a 3×3 dispenser grid. Draws exactly: one fuel slot socket, four upgrade slot sockets,
  a status text panel region at top, a fuel-gauge frame, and the player-inventory
  sockets. Texture art is a follow-up asset task (same pipeline the user uses for
  block/item art); a plain functional placeholder panel ships first so the layout is
  testable before final art. Slot coordinates below are fixed so art and code align.
- **Slot layout (unchanged coordinates, so container code stays put):**
  - Fuel slot: `(26, 35)`
  - Upgrade slots ×4: `(80,35) (98,35) (116,35) (134,35)`
  - Player inventory: standard `(8, 84)` grid + hotbar `(8, 142)`
- **Text panel (foreground layer):**
  ```
  Sanctuary Core
  Tier: 2 / 4
  Protection: ● ACTIVE  (R = 32)      ← or "○ INACTIVE — build a pyramid below"
  Cleanse:    ● RUNNING               ← / "⚠ STALLED — needs fuel" / "○ OFF"
  Fuel: [■■■■□□□□] 128                 ← gauge drawn as a filled rect proportional
                                          to fuel/maxDisplayFuel, + numeric
  ```
  Tier/Protection/Cleanse/radius/status read from the **client TE** (now synced);
  fuel reads from the **window property**.
- When `tier == 0`: replace the radius/cleanse lines with the hint
  `Build a 3×3+ pyramid of iron/diamond blocks directly under the core.` and, for
  `DIM_BLACKLISTED`, `This dimension is warded against sanctuaries.`
- **Slot tooltips / labels:** fuel slot → `Fuel`; upgrade slots → `Radius Upgrade`.
- **Bug fix folded in:** call `drawDefaultBackground()` at the start of `drawScreen`
  (currently missing → HEI warns "GUI did not draw the dark background layer").

## 4. Particle border (channel 3)

Client-side, in the TE tick when `world.isRemote && protectionActive &&
ModConfig.sanctuary.particleBorder`:

- Every ~15 ticks emit a **bounded** number of particles (≤16 per emit) on the circle of
  radius `effectiveRadius` centred on the core (at core Y, optionally ±1), choosing only
  the arc segment(s) nearest the client player so a large dome (R64) never spawns a full
  ring at once (no lag).
- Particle: `ENUM ENCHANTMENT_TABLE` or `END_ROD` (final pick during implementation).
- Fully gated by the existing `sanctuary.particleBorder` config flag.

## 5. Interaction & transition feedback (channel 4)

- **Right-click (normal):** opens the GUI (unchanged).
- **Shift-right-click:** prints a one-line server-truth status to that player's chat, e.g.
  `Sanctuary: Tier 2, R32, Protection ON, Cleanse RUNNING, Fuel 128`.
- **State transitions** (server-side, when a synced field flips: tier change,
  protection on/off, cleanse enters/leaves STALLED): send a short message to players
  within the region, e.g. `Sanctuary activated — Tier 2, radius 32`,
  `Sanctuary stalled — out of fuel`, `Sanctuary deactivated — pyramid broken`.
  **Only on transitions** (compare previous vs new synced snapshot), never per-tick, so
  no chat spam.

## 6. Server-side diagnostics (channel 5)

New live config flag `sanctuary.debugLogging` (default false; no restart). When on, on
every revalidation that changes state (and at most once per revalidate interval), log:

```
[InsaneTweaks] Sanctuary @ (x,y,z) dim0: tier=2 radius=32 protection=ON cleanse=RUNNING fuel=128
```

Plus, on a tier change, log the scan outcome (first layer that failed and why:
`layer 2 incomplete at (x,y,z): minecraft:cobblestone not allowed`). These lines are
picked up by the DEv 1.2 `debug.log` monitor, letting us confirm server truth
immediately and independently of the GUI. This is the tool that turns "chyba nie działa"
into a verifiable fact.

## 7. Config additions

- `sanctuary.debugLogging` (boolean, default false, live) — section 6.
- Everything else reuses existing `SanctuaryCategory` flags (`particleBorder`,
  `tierRadii`, `pyramidBlocks`, …). No mechanic tunables change.

## Components touched

| File | Change |
|------|--------|
| `sanctuary/TileEntitySanctuaryCore.java` | add TE-sync methods; track `protectionActive`/`cleanseState`/`statusCode`; `notifyBlockUpdate` on change; transition messages; client particle emit; diag logging |
| `sanctuary/gui/ContainerSanctuaryCore.java` | `detectAndSendChanges` + `addListener` + `updateProgressBar` for `fuelStored` |
| `sanctuary/gui/GuiSanctuaryCore.java` | dedicated texture + status panel + fuel gauge + `drawDefaultBackground` + tier-0 hints + slot tooltips |
| `sanctuary/BlockSanctuaryCore.java` | shift-right-click → chat status |
| `config/categories/SanctuaryCategory.java` | add `debugLogging` |
| `assets/insanetweaks/textures/gui/sanctuary_core.png` | new (placeholder → final art) |
| `assets/insanetweaks/lang/en_us.lang` | GUI strings, status/hint lines |

New enums: `SanctuaryStatus`, `CleanseState` (in `sanctuary/`).

## Verification (end-to-end, no unit tests)

1. `./gradlew build`, swap jar into DEv 1.2 (remove old-version jar).
2. Place core on diamond 5×5+3×3 → GUI shows **Tier 2 / R32 / Protection ACTIVE**;
   `debug.log` (with `debugLogging` on) shows `tier=2 radius=32 protection=ON`.
3. Iron 3×3 → **Tier 1 / R16**.
4. No pyramid → **Protection ○ INACTIVE** + build hint; blacklisted dim → ward message.
5. Remove fuel → Cleanse **STALLED**; add fuel → **RUNNING**; transition chat lines fire once each.
6. Particle border traces the real radius near the player; toggles with `particleBorder`.
7. Shift-right-click prints the status line.
8. HEI no longer warns about the missing dark background.
