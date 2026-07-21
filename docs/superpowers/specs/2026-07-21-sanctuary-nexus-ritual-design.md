# Sanctuary Nexus — Ritual Consumption System (Design)

**Date:** 2026-07-21
**Status:** Approved (design), pending implementation plan
**Scope of THIS iteration:** replace the diamond-pyramid multiblock with a progressive
lure-offering ritual that permanently upgrades a single Nexus block. Fuel economy, upgrade
components, crafting recipes, and GUI rework are explicitly OUT of scope (later iterations).

## Motivation

The old sanctuary required a standing diamond-block pyramid (5×5 + 3×3) plus a vanilla beacon,
re-scanned every tick to derive tier/radius. Problems: expensive per-tick multiblock scan, a
static structure that must remain standing, thematically flat (diamonds), and no sense of
progression/ritual. The new design turns building the sanctuary into an escalating sacrifice using
Scape and Run: Parasites' own `evolutionlure` blocks — thematically "feeding the horrible hour to
delay your doom" — and stores the earned tier permanently in the Nexus so no structure must remain.

## Grounding (verified)

- `srparasites:evolutionlure` exists: `BlockEvolutionLure` with `PropertyEnum<EnumType> VARIANT`,
  values `ONE..TEN` → meta **0–9** (bytecode-verified). We use metas **0–5** (variants ONE..SIX,
  the six weakest lures).
- Detection reads meta via `state.getBlock().getMetaFromState(state)` + registry-name check
  `srparasites:evolutionlure` — no import of SRP's `EnumType` needed.
- Note: `evolutionlure` is an ACTIVE SRP block (spawns/lures parasites by evolution phase). Because
  our ritual CONSUMES the lure layers (they never remain standing), this side effect never applies
  to a finished sanctuary.

## Core mechanic

1. Player crafts + places the **Nexus** (our controller block; registry id `sanctuary_core`
   unchanged, display name → "Sanctuary Nexus"). It starts at `progress = 0`, tier 0, inert.
2. The Nexus **demands** the next lure via a chat message.
3. Player builds a **flat 5×5 ring of the demanded lure meta at the Nexus's Y level** — the 24
   blocks surrounding the Nexus (the Nexus occupies the center cell).
4. The Nexus detects a complete, correct ring, plays a short **ritual** (particles + sound,
   ~40–60t), then **consumes** the 24 lure blocks (→ air), increments `progress`, and posts a chat
   message announcing the outcome (and the next demand, or completion).
5. Repeat until all six lures are offered. The finished sanctuary is just the Nexus standing alone;
   the player may freely decorate around it (obsidian, etc.) with no functional effect.

### Progress → tier

`progress` is the count of consumed offerings (0–6), permanent and irreversible.

**Tier cost (the human-readable view):**
- **T1** = Lure 0 + Lure 1 (2 offerings)
- **T2** = + Lure 2 + Lure 3 (4 total)
- **T3** = + Lure 4 (5 total)
- **T4** = + Lure 5 (6 total)

Because 6 offerings map onto 4 tiers, two offerings fall *within* a tier and don't bump it (offering
Lure 0 stays tier 0 until Lure 1 completes T1; offering Lure 2 stays T1 until Lure 3 completes T2).
This is intended (user's original spec).

**Implementation lookup (progress → tier):**

| progress | last consumed | tier |
|---------:|:--------------|:----:|
| 0 | — | 0 (inert) |
| 1 | Lure meta 0 | 0 |
| 2 | Lure meta 1 | **T1** |
| 3 | Lure meta 2 | T1 |
| 4 | Lure meta 3 | **T2** |
| 5 | Lure meta 4 | **T3** |
| 6 | Lure meta 5 | **T4** |

**Demanded lure meta = `progress`** (0 → meta 0, … 5 → meta 5; 6 → complete, no demand).
Because the demand is strictly sequential (weakest first), lure order is inherently enforced — the
Nexus only ever consumes the single meta it currently demands.

## Ritual detection & consumption

- The Nexus TileEntity already ticks (`ITickable`). Server-side, throttled (~every 20t): if
  `progress < 6`, test the ring — all **24** cells at `(dx,dz) ∈ [-2,2]²  \ (0,0)`, same Y as the
  Nexus, must be `srparasites:evolutionlure` with `meta == progress`.
- Ring complete → begin ritual: start a countdown (`ritualTicks`, ~40–60t) and emit particles +
  sound each tick for feedback.
- Countdown reaches 0 AND ring still complete → **consume**: set the 24 cells to air, `progress++`,
  post the chat message(s), reset `ritualTicks`.
- Ring broken (or wrong meta) at any point → cancel the ritual (`ritualTicks = 0`), no consumption.
- Detection trigger: **tick-poll every 20t** (simple; the TE already ticks). Block-place event
  hooking is unnecessary.

## Chat messages (English, in-game)

Posted to the acting/nearest player's **chat** (per user request), not the action bar:

- On placement and after each consumption while `progress < 6`:
  `The Sanctuary demands more — offer Lure {progress} to delay your doom. (Tier {nextTier})`
  (flavor draft; final wording during implementation, keeping the "horrible hour / delay your doom"
  tone the user provided.)
- On a tier increase: `The Sanctuary reaches Tier {tier}.`
- On completion (progress 6): `The Sanctuary is whole. Tier IV.`
- Every SUCCESSFUL ritual posts a chat message (the demand-next and/or tier-up line).

## Effects & integration

- Radius and all existing effects — spawn veto, purge fire, cleanse, dome renderer, block-break
  veto — continue to read the **stored tier** exactly as today. Only the tier SOURCE changes: from
  the pyramid scan to `progress`-derived tier. `SanctuaryWorldData` region registration,
  `SanctuaryRegionHelper`, and `RenderSanctuaryDome` are unchanged.
- The old diamond-pyramid scan and the vanilla-beacon requirement are **removed entirely**.
- Tier 0 = fully inert (no region, no dome, no effects). Protection begins at T1 (progress 2).

## Out of scope (later iterations)

- **Fuel / paliwo** economy and any tier degradation tied to it (this iteration: tier is permanent).
- **Upgrade** components / slots (radius currently still scales by the existing tier→radius config;
  upgrade slots keep working as-is until reworked).
- **Crafting recipes** (Nexus keeps its current recipe; lures are sourced through SRP for now).
- **GUI rework** (the current fuel/upgrade GUI stays; it will be reworked with the fuel/upgrade
  iteration). This iteration may add a status line (progress/tier/next demand) but no new GUI.
- **NBT-preserve-on-pickup** (breaking the Nexus currently loses progress; preserving progress in
  the dropped item is a later nicety).

## Migration

Replacing the pyramid scan resets existing sanctuaries to `progress = 0` (tier 0) on world load —
they must be rebuilt via the ritual. Acceptable for the DEv 1.2 test pack.

## Open implementation notes (for the plan, not blocking)

- Exact ritual duration, particle type, and sound (candidate: a low SRP/parasite ambience or an
  evoker-prepare tone) — tune during implementation.
- Ring test excludes the center (Nexus) cell; all 24 surrounding cells required (no holes — the
  "full solid 5×5, contiguous" answer).
- Multiple Nexus blocks with overlapping rings is an unsupported edge case (ignored).
