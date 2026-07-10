# Spec: Thrall — Porter rework (sort-only) + new Husbandry mode

Date: 2026-07-10
Status: approved by user (brainstorming session 2026-07-10)

## B1. Porter rework — "clean cut" to sorting-only

Porter becomes a pure chest-sorter anchored at HOME. Per cycle
(`porterIntervalSeconds`):

1. Deposit the thrall's own bag into home chests (`ThrallChestHelper.smartDeposit`).
2. Run the existing `consolidateChests` logic (dominant-chest designation,
   strict-greater tie-break, cap of 8 moves/cycle) — this IS the mode now.

**Removed** from `ThrallAIPorter` and config:

- teleport-to-owner + pull-from-owner flow (manifest matching against player
  inventory, `ChestBudgetPool`, hotbar exclusion logic);
- `runReverseCycle` / FROM_HOME restock flow;
- config options `porterDirection`, `porterTeleportRange`, `enablePorterSorting`
  (sorting is no longer optional — it is the mode).

Kept: `enablePorterMode`, `porterIntervalSeconds`, `porterChestScanRange`,
requirement that HOME is set. Status texts reduced to "Sorting..." /
"Standing by..." / "Full".

## B2. New mode: HUSBANDRY (Hodowla)

New `ThrallMode.HUSBANDRY` + GUI button + lang entries. Requires HOME set
(like Porter). Anchored on HOME; work radius `husbandryRadius` (default 16);
cycle every `husbandryIntervalSeconds` (default 30 s).

Cycle order: **collect → cull → breed → deposit**.

### Breeding
- Feed is pulled from home chests; an item qualifies for a species via that
  animal's `isBreedingItem()` (works for modded `EntityAnimal` too).
- Thrall walks to two adult animals of the same species (not in love, not on
  breed cooldown) and applies `setInLove` to both, consuming 2 feed.
- Breeding is skipped for a species at/above the population cap.

### Product collection
- Sheep shearing: requires shears present in a home chest; the thrall uses them
  (durability consumed; broken shears are gone) and collects the wool.
- Ground items in the radius (eggs, wool, drops) are picked up via the existing
  `ThrallAIGatherItems` task and deposited during the deposit step.

### Culling (explicit exception to the "never attacks" invariant)
- Cap: `husbandryPopulationCap` (default 8) **adult** animals per species within
  the radius.
- Excess adults are killed directly (damage dealt via `attackEntityFrom` from
  the thrall — `setAttackTarget` stays a no-op; no combat AI is added).
- Never culls: babies, animals currently in love, tamed/owned animals
  (`EntityTameable` with owner).
- Natural drops are collected and deposited. No Looting (thrall carries no weapon).
- **Invariant note:** "thrall is never aggressive" now reads "never aggressive
  toward mobs/players; may slaughter farm animals in HUSBANDRY mode only."

### Config
New subcategory `thrall.husbandry`: `enableHusbandryMode` (module-gated like the
other thrall modes), `husbandryRadius`, `husbandryIntervalSeconds`,
`husbandryPopulationCap`.

## Testing (manual, runClient)

1. Porter: messy chest cluster at HOME settles into per-type chests over a few
   cycles; thrall bag contents get deposited; no teleports to player ever occur.
2. Husbandry breeding: wheat in chest + 2 cows → hearts, calf; no feed = no breeding.
3. Husbandry cap: 10 adult cows, cap 8 → two culled, beef/leather in chests;
   babies and in-love animals never culled.
4. Shearing: shears in chest + unsheared sheep → wool in chests, shears durability drops.
5. Mode removed cleanly: old worlds with thralls in PORTER mode keep working
   (mode ordinal unchanged — HUSBANDRY appended at the END of the enum).
