# Spec: Zhonya's Hourglass rework

Date: 2026-07-09
Status: approved by user (brainstorming session 2026-07-09)

## Goal

Split the current Zhonya's Hourglass into two artefacts:

1. **Hourglass of Restoration** (new item) — receives the *entire current* Zhonya behavior
   (SRP entity restoration via snapshots + Purifying Pulse synergy).
2. **Zhonya's Hourglass** (existing registry name, new behavior) — an active "golden stasis"
   survival artefact inspired by LoL's Zhonya.

## Item identity / registry decisions

- `insanetweaks:zhonyas_hourglass` **keeps its registry name** and gets the new stasis
  behavior. Existing items in player worlds change behavior in place; nothing disappears.
- New artefact registry name: `insanetweaks:restoration_hourglass`
  ("Hourglass of Restoration"). Slot: CHARM (same as today). Texture: placeholder initially;
  final texture supplied by the user later (tracked as a follow-up asset swap, not a blocker).
- `ZhonyasEventHandler` and `SrpOriginSnapshotHelper` (snapshot capture) are item-agnostic
  and stay unchanged; only the `ItemArtefact.isArtefactActive(...)` checks and
  `EntityPurifyingWave` call sites switch to `ModItems.RESTORATION_HOURGLASS`.
- Current restoration logic (`tryRestoreInRange`, right-click ray-trace restore, 6 h restore
  cooldown, tooltip) moves 1:1 into `ItemRestorationHourglassArtefact`.

## New Zhonya behavior (active, right-click)

On right-click (server side), if off cooldown and the player has at least the minimum mana:

### 1. Cost (paid immediately)

- Drains **all** of the player's current mana via `PlayerManaCompat`.
  Activation requires a minimum current mana (default **100**) — below that the click fails
  with a gray chat message and no cooldown is triggered.
- Starts an item cooldown via `CooldownTracker`, default **3 hours** (216 000 ticks).

### 2. Gilded Stasis — 3 s (60 ticks), new `PotionGildedStasis`

- **Immortality:** cancel `LivingAttackEvent`/`LivingHurtEvent` for the player while active.
- **Heal:** heal the player (full heal on activation).
- **Cleanse:** apply `PotionCleanse` for the stasis duration (removes parasite debuffs;
  see the cleanse-coverage spec).
- **Root:** player frozen in place — motion zeroed every tick, item use / attack / block
  interaction blocked while active.
- **Visual (client):** the player model is rendered with a **gold tint overlay**
  (render event / render layer), reading as a frozen golden figure in the player's pose.
  EB Wizardry's real petrification cannot apply to players (it converts `EntityLiving`
  into a `BlockStatue`), so this is our own illusion of petrification, recolored gold.
- Server-side gold/yellow particles around the player for observers.

### 3. Aggro loss — 5 s (100 ticks)

- On activation: every `EntityLiving` within **24 blocks** whose `attackTarget` (or
  `revengeTarget`) is the player gets those targets cleared.
- For the following 5 s, a per-tick handler re-clears any such target that reappears.
  This covers SRP parasites' aggressive re-targeting; if testing shows SRP AI holds targets
  in additional mod-specific fields, those are cleared too (research item for the plan —
  SRP sources are available in `notes/decompiled_mods/`).
- The stasis (3 s) and aggro-loss (5 s) windows both start at activation; aggro suppression
  intentionally outlasts the stasis by 2 s so the player can reposition.

## Config

New options in the `tweaks` category (all `@Config` fields, no restart required —
they are read at use time, not registration time):

| Option | Default |
|---|---|
| Zhonya cooldown (ticks) | 216000 (3 h) |
| Stasis duration (ticks) | 60 (3 s) |
| Aggro-loss duration (ticks) | 100 (5 s) |
| Minimum mana to activate | 100 |

## Components

- `ItemZhonyasHourglassArtefact` — rewritten: activation, cost, effect application.
- `ItemRestorationHourglassArtefact` — new: receives current restoration code.
- `PotionGildedStasis` — new potion (root + immortality flag + HUD icon).
- `ZhonyaStasisHandler` — new event handler: damage cancel, per-tick root/aggro clearing.
  Registered under the existing artefact/armor module gating in `InsaneTweaksMod.init`.
- Client: gold tint render hook (`RenderPlayerEvent` or a render layer), gated `Side.CLIENT`.
- Assets: new item model/texture (`restoration_hourglass` placeholder), potion icon,
  lang entries (en_us + ru_ru) for both items, the potion, and updated tooltips.

## Error handling

- PlayerMana mod absent: `PlayerManaCompat` reports no mana system → activation fails with
  a chat notice (artefact requires mana by design; without the mana mod the active effect
  is unavailable, restoration artefact still works).
- Player logs out / dies mid-stasis: potion effects and NBT windows simply expire;
  no persistent state beyond the potion + cooldown tracker.

## Testing (manual, runClient)

1. Activate with full mana → mana drops to 0, stasis 3 s (frozen, gold, immortal), heal, cleanse.
2. Mobs (vanilla + SRP) attacking the player lose aggro and do not re-acquire for 5 s.
3. Activation below minimum mana → refused, no cooldown consumed.
4. Cooldown enforced for 3 h (test with a lowered config value).
5. Restoration artefact: full regression of old Zhonya flows (wave restore, right-click restore).
