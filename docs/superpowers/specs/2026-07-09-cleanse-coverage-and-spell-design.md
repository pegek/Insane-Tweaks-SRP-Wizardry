# Spec: Cleanse — parasite effect coverage + Spell Cleanse

Date: 2026-07-09
Status: approved by user (brainstorming session 2026-07-09)

## Goal

1. Make the Cleanse effect (`PotionCleanse`) reliably remove **all** negative effects from the
   parasite mod ecosystem (SRParasites, SRPExtra, SW: Parasites), including those the mods
   register incorrectly as beneficial/neutral.
2. Add a new castable spell **Cleanse** that applies our Cleanse effect to a target entity
   or to the caster.

## Part 1 — Effect coverage audit

### Audit scope

Decompile/inspect every `Potion` registration in:

- `libs/SRParasites-1.10.7.jar`
- `libs/SRPExtra-1.10.7.5.jar`
- `libs/swparasites-v4.jar`

Classify each effect into exactly one bucket:

| Bucket | Criterion | Action |
|---|---|---|
| (1) Already covered | `isBeneficial() == false` | none — existing pass 1 removes it |
| (2) Harmful but mis-registered | harms the player but reports beneficial/neutral | add to new built-in list |
| (3) Protective / mechanic | protects or tracks (e.g. `SRPPotions.EPEL_E`) | **explicitly excluded** — must never be cleansed |

Special case: `SRPPotions.COTH_E` **is removed** by cleanse — consistent with the existing
project rule that InsaneTweaks systems always clear COTH from victims (spell_theory.md §21).
Note that COTH removal semantics interact with `srpcothimmunity` NBT (0 = immune); cleanse
only removes the potion effect, it does not touch the NBT flag.

The full classified effect table (all three jars) is produced during implementation and
recorded in the implementation plan / PR description as the audit artifact.

### Code changes

- `PotionCleanse.performEffect()` gains **pass 1.5** between the existing two passes:
  removal of a hardcoded built-in `Set<ResourceLocation>` `BUILT_IN_CLEANSED_EFFECTS`
  holding the bucket-(2) results. Resolution against `ForgeRegistries.POTIONS` at first use
  (lazy), so missing optional mods cost nothing.
- `ModConfig.tweaks.cleanseAdditionalEffects` stays unchanged as the user extension channel.
  Its current defaults (`srparasites:novision`, `prey`, `viral`) migrate into the built-in
  list; the config default becomes an empty array (existing config files keep working —
  duplicate removal is a harmless no-op).

Rationale for hardcode-over-config: Forge `@Config` values already written to disk never
pick up new defaults, so shipping the audit results via config defaults would not reach
existing installs.

## Part 2 — Spell Cleanse

### Identity

- Class: `SpellCleanse extends SpellRay` (pattern reference: EB `Petrify`, our `SpellPurifyingPulse`).
- Registry: `insanetweaks:cleanse`; registered in `init/ModSpells` (gated on `enableSpells`).
- Tier: **MASTER**. JSON element: `magic` — displayed as **Abomination** (light red) by the
  existing display mixins; no new display work needed.

### Behavior

- Ray-trace at a living entity: apply `PotionCleanse` to it for **200 ticks (10 s)**
  → 20 cleanse pulses (one per 10 ticks).
- On miss (no entity hit): apply the same effect to the **caster** (self-cast fallback).
- Works on any `EntityLivingBase` (players, allies, mobs). No damage component.

### Balance numbers (initial, all tunable in the spell JSON)

Per the Abomination model (spell_theory.md §0): massively higher costs than vanilla Wizardry.

| Property | Value |
|---|---|
| Mana cost | 350 |
| Chargeup | 60 ticks (3 s) |
| Cooldown | 3600 ticks (3 min) |
| Applied effect duration | 200 ticks (10 s) |

Final calibration against the other Master-tier insanetweaks spells happens in the
implementation plan (compare existing spell JSONs).

### Assets

- `assets/insanetweaks/spells/cleanse.json` (properties incl. the table above)
- Spell icon texture
- Lang entries in `en_us.lang` **and** `ru_ru.lang` (name + description)

## Error handling

- Unknown/unregistered effect IDs in the built-in list: skipped silently (same as pass 2 today).
- Spell cast on entity that has no removable effects: still succeeds and consumes mana
  (effect applied; pulses simply find nothing).

## Testing

No test suite exists in this project. Manual verification in `runClient`:

1. `/effect` self with SRP debuffs from each bucket-(2) entry → hardcap/spell removes them.
2. Verify `EPEL_E` survives a cleanse pulse.
3. Cast with and without a target; verify self-fallback, chargeup, cooldown, Abomination display.
