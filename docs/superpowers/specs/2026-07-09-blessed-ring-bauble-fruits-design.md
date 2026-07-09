# Spec: Blessed Ring — Bauble Fruit acquisition loop

Date: 2026-07-09
Status: approved by user (brainstorming session 2026-07-09)

## Goal

Bauble Fruits (the 9 slot-unlocking consumables in `items/fruit/`) currently have **no
survival acquisition path**. This spec defines one, gated on the player wearing the
**Blessed Ring** (`enigmaticlegacy:blessed_ring`, from `libs/enigmaticlegacy-legacy-2.6.0.jar`).

Gate semantics (user decision): the Ring gates **acquisition only** — obtaining fragments
and growing the plant. Eating an already-obtained fruit does not require the Ring.

## The loop

```
high-tier SRP parasite kill (killer wears Blessed Ring)
  → Corrupted Seed Fragment (rare drop)
  → craft: 4 fragments → Corrupted Seed
  → plant on infested ground, defend the living sapling
  → Corrupted Fruit
  → EITHER purify at the Imbuement Altar (9 typed recipes) → typed Bauble Fruit
    OR eat it corrupted (random slot + certain death)
```

### 1. Corrupted Seed Fragment (new item)

- Drops from **high-tier SRP parasites** (the exact tier list — e.g. Adapted-class and
  Beckon-class mobs — is finalized during implementation from the SRP sources and recorded
  in the plan; configurable drop chance, default 5 %).
- Drop condition: the killing player is wearing `enigmaticlegacy:blessed_ring` in a baubles
  slot. Checked via a new `EnigmaticLegacyCompat` util (follows the existing compat-shim
  pattern in `util/`); no drop when Enigmatic Legacy is absent.

### 2. Corrupted Seed (new item)

- Shapeless craft: **4 × Corrupted Seed Fragment → 1 Corrupted Seed**.
- Tooltip doubles as the **how-to-grow instruction** plus atmospheric flavor text in the
  established Living/Sentient gear style (via `PropertyDescriptions`-like tooltip lines).

### 3. Growing — the hard part

- The planted seed becomes a **living entity**, `EntityCorruptedSapling` (has HP, hitbox,
  hurt/death logic) — deliberately an entity rather than a crop block, so SRP parasite AI
  can naturally target and attack it. The player must defend it.
- Growth conditions, both required each growth tick (growth pauses when unmet):
  1. **Active SRP infestation nearby** — exact criterion finalized during implementation
     from SRP sources (preferred: SRP infested/colony blocks in radius; fallback: ≥ N living
     parasites within radius).
  2. **The owner, wearing the Blessed Ring, within radius** (default 32 blocks).
- Several visible growth stages; total growth time default 20 min of valid conditions
  (configurable). Sapling death (killed by anything) drops nothing — the seed is lost.
- On maturity the sapling "bears" and drops a **Corrupted Fruit**, then despawns.

### 4. Corrupted Fruit (new item) — two paths

**Path A — purification (intended):** the EB Wizardry **Imbuement Altar** (we already ship
`MixinTileEntityImbuementAltar`) gets **9 recipes**: Corrupted Fruit + a slot-defining
catalyst item → the corresponding typed Bauble Fruit (ring/amulet/body/head/charm/belt/
elytra/totem/trinket). The catalyst table (one sensible thematic item per slot type) is
proposed in the implementation plan.

**Path B — temptation (eat it corrupted):**

1. Immediately invokes the **same slot-unlock function as eating one random typed Bauble
   Fruit** (`BaseBaubleFruitItem` path — same limits apply, no way to bypass caps; the
   unlock persists through death as fruit unlocks already do).
2. Then an **unavoidable death sequence**: the player is rooted in place, vision heavily
   limited (blindness + nausea), unavoidable negative effects (cannot be cleansed, milked,
   or totem-saved), and after ~6 s (120 ticks) the player **dies unconditionally**
   (kill mechanism must bypass Totem of Undying — technique settled in the plan).
3. At the death position an **SRP Beckon Stage V** spawns.

## Config (under the existing `enableBaubleFruits` module)

| Option | Default |
|---|---|
| Fragment drop chance | 0.05 |
| Fragments per seed | 4 (recipe constant, documented) |
| Growth time (min of valid conditions) | 20 |
| Infestation check radius | 32 |
| Owner/Ring check radius | 32 |
| Sapling max HP | 40 |

## Components

- Items: `CorruptedSeedFragmentItem`, `CorruptedSeedItem`, `CorruptedFruitItem` (+ models,
  textures, lang en_us + ru_ru with flavor text).
- Entity: `EntityCorruptedSapling` (+ tracking ID appended after existing IDs — never reuse),
  model/renderer under `client/renderer/entity/`, growth-stage visuals.
- `EnigmaticLegacyCompat` util (Blessed Ring detection via Baubles inventory scan).
- Drop handler (`LivingDropsEvent`) for fragments, gated on module flag + EL + SRP presence.
- Imbuement Altar recipe extension via the existing mixin/compat path.
- Death-sequence handler for the corrupted eat path (root, effects, forced kill, Beckon V spawn).

## Error handling

- Enigmatic Legacy absent: no fragment drops; loop unobtainable (module still registers,
  items visible in creative). SRP absent: module effectively dormant (no drops, no growth).
- Sapling chunk-unloaded mid-growth: entity + growth progress persist via entity NBT.
- Corrupted eat in creative: sequence still applies (design: death is absolute); creative
  players can obviously survive via game mode, not our concern.

## Testing (manual, runClient)

1. Kill high-tier parasite with/without Ring → fragment drops only with Ring.
2. Grow a sapling: conditions on/off pause growth; parasites attack it; death loses the seed.
3. Purify at altar with each catalyst → correct typed fruit.
4. Eat corrupted fruit → random slot unlocked (caps respected), forced death (totem in hand
   does not save), Beckon Stage V spawns at corpse; slot persists after respawn.
