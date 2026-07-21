# Sanctuary — "Cost of Power" (upkeep cost + upgrades) design

Date: 2026-07-21
Branch: `feat/sanctuary-dome`
Status: approved (brainstorm), pending implementation plan

## Motivation

The Sanctuary is a very strong mechanic: it vetoes SRP parasite spawns and block
infestation inside its radius, runs a cleanse sweep, and projects a dome. Left free, it
trivializes the parasite threat. This design adds a **real, non-negotiable maintenance
cost** so holding a Sanctuary hurts, plus a 4-slot upgrade path that lets a committed
owner eventually buy the cost away.

### Hard requirement: no vanilla `PotionEffect`

Every debuff must be applied through **direct mechanics** — attribute modifiers, event
cancellation, direct API drain — never a `PotionEffect`. Potions are trivially removed in
this modpack (milk, immunity trinkets, EBW/ASC cleanse), which would defeat the entire
point. A Sanctuary is powerful enough that its price must not be removable with an item.

## Core decisions (locked)

- **Who pays:** presence tax — everyone inside the radius, **including the owner and any
  allies/guests**. Not owner-only.
- **Scaling:** **flat** — identical debuff at every tier. Tier only governs radius /
  protection strength, never personal cost.
- **Fuel:** **time-based upkeep**. The Sanctuary burns mana-fuel every N ticks just for
  existing. Cleanse is decoupled from fuel (it runs while the Sanctuary is powered).
- **Escalation chain when the fuel tank hits zero:** drain EBW mana from **all casters
  inside** → when nobody inside has mana left, drain the **owner's HP** (life essence),
  wherever the owner is.
- **Upgrades:** 4 dedicated slots, each bound to one specific item. The capstone (U4)
  removes all cost.

## Three-layer cost model (the "presence tax")

### Layer A — always active, for every player inside

| Debuff | Mechanic (non-potion) | Default |
|---|---|---|
| Max-HP tithe | `AttributeModifier` on `SharedMonsterAttributes.MAX_HEALTH`, stable UUID, applied on enter / removed on exit | −5 max HP (−2.5 hearts) |
| Suppressed regen | cancel `LivingHealEvent` for the player while inside | all passive healing off* |

\* "Passive" healing = hunger-driven natural regen **and** the Regeneration potion — both
flow through `LivingHealEvent`, so both are suppressed. Instant Health (splash/drink)
bypasses `LivingHealEvent` (it sets health directly), so **instant heals still work**.
This is intentional and honest: the Sanctuary suppresses *regeneration*, not emergency
healing.

### Layer B — escalation when the fuel tank is empty

- Drain EBW mana from **every caster inside the dome** each tick, via the existing
  `PlayerManaCompat` helper. Default ≈ 2 mana / tick per present caster.

### Layer C — backstop when no one inside has mana

- Drain the **owner's HP** wherever the owner is, via a custom
  `DamageSource("sanctuary_tithe")` — magic-typed, unblockable, bypasses armor. Default
  ≈ 1 HP / second.

The chain is strictly ordered: **fuel in tank → caster mana → owner HP**. Owner blood is
the last resort; mana is communal.

## Fuel = mana, consumed as upkeep

- The fuel tank (`fuelStored`, repurposed from the old cleanse-conversion counter) is
  spent `upkeepPerInterval` every `upkeepIntervalTicks`, purely for existing.
- When the tank runs low, one item is auto-consumed from `SLOT_FUEL` and its value is
  added to the tank.
- `manaFuelItems` (config, item → units) — default entries:
  - `ebwizardry:crystal_shard` — small
  - `ebwizardry:magic_crystal` — baseline
  - `ebwizardry:grand_crystal` — large
  - `ebwizardry:astral_diamond` — huge
  - `ebwizardry:crystal_flower` — small
- Tank empty **and** no fuel item present → Layer B → Layer C.
- Cleanse no longer consumes fuel; it runs while the Sanctuary is powered (tank > 0 or
  actively draining). **Behavior change** from the old per-block fuel cost — noted so the
  cleanse code path is updated.

## Upgrades — 4 dedicated slots

Each of the 4 existing upgrade slots is bound to one specific item. A slot's upgrade is
**active** only when it holds the required item (U2 needs a count).

| Slot | Required item | Effect while active |
|---|---|---|
| **U1** | `insanetweaks:adaptation_upgrade` ×1 | upkeep burn ×0.5 **and** Layer-B mana drain / Layer-C HP drain ×0.5 |
| **U2** | `minecraft:golden_apple` meta 1 (enchanted) **×20** | **owner's** max-HP tithe −5 → −2.5 (halved). Non-owners still pay full. |
| **U3** | `srparasites:trophy_void_orb` ×1 | +16 radius |
| **U4** | `srparasites:trophy_boom_orb` ×1 **and U1, U2, U3 all active** | cancels **ALL** Sanctuary penalties for **everyone** in the dome; **no fuel consumed ever** (infinite uptime); +16 radius |

- **"Infinite" = infinite uptime with zero fuel.** Radius is still merely +16 from U4 (not
  an infinite radius).
- **Ghost hint icons:** each empty upgrade slot renders a translucent (alpha ≈ 0.3) copy
  of its required item, so the player can read what each slot wants.
- **Slot validation:** each slot's `Slot.isItemValid` accepts only its bound item. U2's
  upgrade counts as active only at ≥20 enchanted golden apples.
- Radius from upgrades: U3 → +16, U4 → +16, so at most +32 from upgrades combined. The old
  `countUpgradeRadiusItems() * upgradeRadiusBonus` path is replaced by this dedicated
  scheme.

## Implementation surfaces

- **Presence detection:** a server-side `PlayerTickEvent` (Phase END) handler tests each
  player against active Sanctuary regions (`SanctuaryWorldData` for the region, the owning
  TE for owner id + active upgrades). Enter/exit transitions drive attribute-modifier
  apply/remove.
- **Max-HP tithe:** `AttributeModifier` with a stable UUID on `MAX_HEALTH`; applied on
  enter, removed on exit and on world-leave/logout to avoid a stuck modifier.
- **Suppressed regen:** subscribe `LivingHealEvent`; cancel when the entity is a player
  inside an active, non-U4 Sanctuary.
- **Mana drain:** `PlayerManaCompat` (already wraps EBW `WizardData`). Guarded by
  `Loader.isModLoaded("ebwizardry")` — if EBW is absent, Layer B is a no-op and the chain
  falls straight to Layer C.
- **Owner HP drain:** resolve the owner entity from `ownerId` on the server; apply the
  custom `DamageSource`. If the owner is offline/unresolved, the drain simply waits (no
  effect that tick).
- **Config:** new `SanctuaryCostCategory` (all numbers live-tunable, no MC restart): max-HP
  penalty, regen-suppression toggle, upkeep interval + amount, mana-drain per tick, owner
  HP-drain per second, U1 multipliers, U2 halved value, U2 required count, `manaFuelItems`
  map, upgrade radius bonus.

## Assumptions confirmed by user (2026-07-21)

1. U4 cancels penalties for **everyone** in the dome (full ascension), not just the owner.
2. Regen suppression blocks passive healing (natural + Regeneration potion); Instant Health
   splash still works.
3. "Infinite" = infinite uptime / no fuel; radius stays +16 (not infinite radius).
4. Counts: U1 ×1, U3 ×1, U4 ×1; **U2 = 20** enchanted golden apples. Base numbers −5 max
   HP, ≈2 mana/tick/caster, ≈1 HP/s owner — all config, tuned in-game.
5. Layer A applies to allies/guests inside too, not only the owner. U2 relieves the owner
   only; guests pay full until U4.

## Out of scope (later)

- Balancing pass on the default numbers (done in-game against DEv 1.2).
- Any brand-new upgrade item / recipe / texture — this design reuses existing items only.
- Visual FX for the drain (particle when a caster is being mana-tapped, etc.).
