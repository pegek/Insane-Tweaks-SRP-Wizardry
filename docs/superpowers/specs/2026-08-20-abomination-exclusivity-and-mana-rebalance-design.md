# Abomination exclusivity, the end of the PlayerMana layer, and a mana re-pricing

**Date:** 2026-08-20
**Mod:** `insanetweaks` (content) — all gameplay, content owns every part
**Status:** designed, not implemented
**Target EBW:** 4.3.19 (CurseMaven file id `8320066`) — every number below was read off that jar
**Predecessors:** `2026-08-08-abomination-economy-and-balance-design.md` (this spec replaces its §3
pricing rule outright), `2026-08-08-ebw-abomination-element-design.md`.

## Problem

Three requests that turned out to be one thing.

Now that Abomination is a real `Element`, its spells should be castable **only** from our own foci —
the Living/Sentient wand and spellblade — or from a foreign wand carrying an Adaptation upgrade. The
PlayerMana support (the Arcane Adapted Fruit and everything around it) should go. And the spells cost
far too much mana.

These are not three tasks. `player_mana-1.2.1.jar` is **already disabled in the DEv 1.2 pack**, and
that single fact explains all three:

1. **The exclusivity gate already exists and is already dead.**
   `ArcaneBridgeEventHandler.onSpellCastPre` contains exactly the rule being asked for — but the
   handler opens with
   `if (!ModConfig.modules.enableSrpEbWizardryBridge || !PlayerManaCompat.isAvailable()) return;`.
   With PlayerMana absent that early return fires, so **nothing has been gating Abomination casts for
   as long as the mod has been disabled.** Deleting the PlayerMana layer without moving the gate first
   would delete the feature this spec is meant to deliver.
2. **The fruit and its regen bonus do nothing**, for the same reason.
3. **The costs were tuned against a mana economy that no longer exists.** PlayerMana supplied
   *regeneration*; an EBW wand has none — it is recharged at a workbench with crystals. A price built
   on a regenerating pool is the wrong price for a fixed one.

There is also a fourth problem the request did not name, and it is the largest.

🚨 **The old pricing rule had no external reference point.** The predecessor spec's bands (tool ≤ 5%
of the pool, ritual ≥ 10%) were expressed as a percentage of *our own wand*, so the wand and the
spells could drift together indefinitely with the rule still reporting success. Measured against the
mod we are a guest in, the drift is severe:

| | EB Wizardry | ours |
|---|---|---|
| most expensive spell in the mod | `summon_iron_golem`, **175** | `call_of_demise`, **1800** |
| master-tier mean cost | **88.2** (n=36) | ~700 |
| our "tools" (130–150) | priced like an EBW **master** spell | — |

`call_of_demise` costs more than ten times the most expensive spell EB Wizardry ships.

## Scope

**In scope.** The exclusivity gate (§1), removal of the PlayerMana layer (§2), a re-pricing of all 13
Abomination spells derived from a stated formula (§3), and new wand capacities (§4).

**Out of scope, deliberately.**

- **Cooldowns are not touched.** §3.4 records the consequence — after this change rituals are gated by
  time rather than by mana — and why that was accepted rather than fixed.
- The foreign-focus cost multiplier (`getForeignFocusAbominationCostMultiplier`, every level 1.0
  today) stays as it is. It is a separate lever and switching it on is a separate decision.
- Scrolls. `Source.SCROLL` is not gated; see §1.2.

## §1 The exclusivity gate

### 1.1 Where it goes and why

Move the rule out of `ArcaneBridgeEventHandler` (deleted in §2) into
`events/SpellRestrictionEventHandler`, which already subscribes to `SpellCastEvent.Pre` at
`EventPriority.HIGHEST`, already blocks Abomination spells for EBW's own wizard NPCs, and is
registered behind `ModConfig.modules.enableSpells` — the correct gate, since with spells disabled
there is nothing to restrict, and **no relationship to PlayerMana**.

The rule:

```
spell is Abomination
  AND event source == SpellCastEvent.Source.WAND
  AND caster is an EntityPlayer, not in creative
  AND AdaptationUpgradeHelper.getEffectiveAdaptationLevel(focus) == 0
  -> cancel, and tell the player why
```

The focus comes from `AdaptationUpgradeHelper.findCastingItem(player, spell)`.

**No new logic is needed for "our wands or a wand with the upgrade".**
`getEffectiveAdaptationLevel` already returns 1 for the Living/Sentient wand and spellblade by
identity (`getDefaultAdaptationLevel`) and adds any applied Adaptation upgrade on top, capped at 3.
The requested rule is exactly `> 0` on a function that already exists.

Creative is exempt, matching the SRP-stage gate already in that class.

### 1.2 What is deliberately not gated

- **NPCs.** `Source.NPC` passes. Our own `sim_wizard` casts `dispatcher_grasp`, holds no wand of ours,
  and is made of the same stuff the element is named after; requiring it to carry an adapted focus
  would undo half of what the 2026-08-13 spawn work added. This is the whole reason the condition
  tests `Source.WAND` rather than testing the spell alone.
- **Scrolls.** Our spell JSONs carry `"scroll": true`, so a scroll is a real bypass today. It is left
  open because closing it takes the point out of scrolls for our whole family, which is a separate
  design decision. Recorded so the gap is known rather than discovered.
- **Commands and dispensers.** Same reasoning; neither is a progression path.

## §2 Removing the PlayerMana layer

### 2.1 What goes

| file | note |
|---|---|
| `items/bridge/ArcaneAdaptedFruitItem.java` | registered item — see §2.3 |
| `util/ArcaneAdaptedFruitHelper.java` | |
| `util/PlayerManaCompat.java` | file is deleted; **two of its methods move rather than die — see §2.2** |
| `util/PlayerManaContext.java` | |
| `events/ArcaneBridgeEventHandler.java` | entire class |
| `mixins/playermana/MixinPlayerManaEventsHandler.java` | |
| `mixins.insanetweaks.playermana.json` | the whole mixin config, plus its declaration in `core/LateMixinBooter` |
| `models/item/arcane_adapted_fruit.json`, `textures/items/arcane_adapted_fruit.png` | |
| the `item.arcane_adapted_fruit.name` line in `en_us.lang` **and** `ru_ru.lang` | |

Plus the call sites: `ModItems` (field, registration, model), `CommandInsaneTweaks` (the
`/claimarcanefruit` handling), `ItemZhonyasHourglassArtefact`, `items/spellblade/BridgeSpellblade`,
`events/WandEventHandler`, and `InsaneTweaksMod`'s registration of `ArcaneBridgeEventHandler`.

`TweaksCategory.zhonyaEbManaFallback` also goes: with PlayerMana gone the "fallback" is the only
path, and a flag that selects between one option is worse than no flag.

### 2.2 🚨 Two things must survive the deletion

**`getConsumedMana` and `getActualCostMultiplier` are not PlayerMana support.** They are the
machinery that converts mana spent into **Living Wand evolution points** — `WandEventHandler`
consumes them for exactly that. Both already carry a complete non-PlayerMana path, and since the mod
is disabled that path is the one running today. Deleting `PlayerManaCompat` wholesale would silently
stop the Living Wand from evolving.

Move both to a new `util/SpellManaAccounting`, stripped of their `isAvailable()` branches. Keep the
continuous-spell arithmetic verbatim — `(baseCost * ticks) / 20.0` for a `SpellCastEvent.Finish` —
it is not obvious and it is load-bearing.

**The `obtain_living_sentient_gear` advancement survives on its own.** It is granted by
`ArcaneBridgeEventHandler.checkGearAchievement`, which is being deleted — but its JSON uses four
vanilla `minecraft:inventory_changed` criteria under a single OR requirement, so vanilla awards it
without any help from us. The code path is redundant. **Confirm this in game after the change**
rather than trusting the reading: pick up a Living Wand on a fresh profile and check the toast fires.

### 2.3 🚨 The fruit is a registered item

`ARCANE_ADAPTED_FRUIT` is registered in `ModItems`, so simply deleting it gives a missing-registry
screen on every world where one sits in a chest — the same trap the economy spec recorded for spells.

Add a `RegistryEvent.MissingMappings<Item>` handler that calls `ignore()` for
`insanetweaks:arcane_adapted_fruit`. `events/LegacyDormantRemapHandler` is the working precedent in
this repo for exactly this event, including its string-only lookup with no compile dependency.

`ignore()`, not `remap()`: there is nothing to remap it to, and `ignore()` leaves the slot dead
without disturbing other ids.

## §3 Re-pricing

### 3.1 The formula

Two indices, both normalised against EB Wizardry, both computable from data we already have.

**Burst index** — what one cast takes out of a full wand:

```
P = (cost * 0.8 / wandCapacity) / 0.07
```

`0.8` is the cost multiplier a fully-evolved Living Wand or any Sentient Wand applies (both cap at a
0.20 reduction). `0.07` is EB Wizardry's own ceiling: its most expensive spell, `summon_iron_golem` at
175, against its master wand's 2500 pool. **`P = 1.0` means "costs what the most expensive spell in
EB Wizardry costs".**

**Sustained index** — what it costs to keep casting:

```
S = (cost / (cooldown / 20)) / 7.0
```

`7.0` mana/second is EB Wizardry's master-tier mean. **`S = 1.0` means "same sustained price as an
average EBW master spell".**

The formula deliberately does **not** attempt to measure a spell's power. The power of a summon and
the power of a damage spell do not reduce to a shared unit without inventing one. It normalises price
only, and leaves power to judgement — that is its honest limit, and pretending otherwise would give
the numbers a false authority.

### 3.2 What the formula found, and why it matters

EB Wizardry holds its sustained rate nearly flat across all four tiers — a higher tier costs more per
cast but waits proportionally longer:

| tier | n | mean cost | max | mean cooldown | mana/s |
|---|---|---|---|---|---|
| novice | 21 | 6.2 | 10 | 14.8 | 8.4 |
| apprentice | 55 | 17.2 | 40 | 37.7 | 9.1 |
| advanced | 77 | 34.2 | 100 | 102.4 | 6.7 |
| master | 36 | 88.2 | 175 | 253.1 | **7.0** |

🚨 **Measured this way our sustained pricing was never the problem.** `summon_thrall` sat at 8.7
mana/s against EBW's 7.0, and the expensive rituals were *cheaper* to sustain than EBW's average
(3.0–4.2 mana/s). What was broken was burst: `call_of_demise` took **36%** of a full wand in one cast
where EBW's most expensive spell takes 7%. We had built "huge burst plus huge cooldown" against a mod
built on "moderate burst plus moderate cooldown".

This is why a flat multiplier would have been the wrong instrument, and why the formula splits the two
axes instead of collapsing them into one number.

### 3.3 New values

Calibrated with the Living Wand at 3000 (§4), which makes `raw cost = P * 262.5`. Relative ordering is
preserved throughout — no spell overtakes another.

| spell | old | **new** | cut | P | S |
|---|---|---|---|---|---|
| `dispatcher_grasp` | 130 | **70** | 1.9x | 0.27 | 1.00 |
| `immune_bond` | 140 | **75** | 1.9x | 0.29 | 0.71 |
| `yelloweye_gland` | 150 | **80** | 1.9x | 0.30 | 0.95 |
| `summon_thrall` | 520 | **200** | 2.6x | 0.76 | 0.48 |
| `summon_fer_cow` | 540 | **210** | 2.6x | 0.80 | 0.46 |
| `parasite_shroud` | 600 | **230** | 2.6x | 0.88 | 0.37 |
| `summon_wizard` | 640 | **245** | 2.6x | 0.93 | 0.44 |
| `summon_primitive_yelloweye` | 700 | **270** | 2.6x | 1.03 | 0.43 |
| `cleanse` | 760 | **290** | 2.6x | 1.10 | 0.23 |
| `summon_light_bomber` | 760 | **290** | 2.6x | 1.10 | 0.46 |
| `summon_primitive_summoner` | 850 | **325** | 2.6x | 1.24 | 0.36 |
| `purifying_pulse` | 1100 | **350** | 3.1x | 1.33 | 0.17 |
| `call_of_demise` | 1800 | **500** | 3.6x | 1.90 | 0.12 |

`test_projectile` (15) is untouched — it is a development spell and not part of the family.

The three tools land at `S` ≈ 1.0, i.e. exactly EB Wizardry's sustained price. That is the intended
result: a tool is meant to be cast during a fight, so it should cost what a fight-cast spell costs.
`call_of_demise` stays the most expensive spell in the game at `P = 1.90`, just under twice EBW's
ceiling rather than more than five times it.

### 3.4 The consequence we are accepting

Cutting costs while leaving cooldowns untouched drops every ritual's `S` to 0.12–0.48. **Rituals
become gated by time, not by mana.** A 500-mana `call_of_demise` is 7.5 casts out of a full wand, but
its 600-second cooldown means you will cast it once.

Accepted, because that is what a ritual is: the cooldown is the constraint and the cost is a tax. The
alternative — cutting cooldowns proportionally to restore mana as a real limit — would raise how often
summons and `purifying_pulse` appear, which is a difficulty change nobody asked for. If a playtest
says rituals now feel free, the lever is the cooldown, not a return to four-figure costs.

## §4 Wand capacities

| wand | old | **new** |
|---|---|---|
| Living Wand | 4000 | **3000** |
| Sentient Wand | 6500 | **3500** |

Both are `GearCategory.Wands` config values (`livingManaCapacity`, `sentientManaCapacity`), so this is
a default change, not a code change.

**Lowering a wand's capacity is safe here, and it is worth knowing why it would not have been.**
Mana is stored as item damage and `getMana = capacity - damage`, unclamped, so a wand charged above a
newly-lowered ceiling reports *negative* mana — and `isManaEmpty` tests `== 0`, so it would claim to
be non-empty, keep its melee modifiers and cast nothing. `BaseCustomWandItem.onUpdate` clamps a
negative reading to zero (verified). An existing over-charged wand therefore pins to empty and
recharges normally. **Do not remove that clamp.**

🚨 **The capacity cut eats part of the cost cut, because both live in the same fraction.** Casts of
`call_of_demise` from a full Living Wand:

| | casts |
|---|---|
| today (1800, pool 4000) | 2.8 |
| new cost only (500, pool 4000) | 10.0 |
| new cost and new pool (500, pool 3000) | **7.5** |

**The Sentient Wand keeps a real advantage on purpose.** Its edge over a *fully evolved* Living Wand
is capacity alone — both cap at the same 0.20 cost reduction (`BaseCustomWandItem.calculateModifiers`:
Living is `0.05 + 0.15 * progress`, Sentient a flat `0.20`). At 3000 and 3250 the endgame wand would
have been 8% better than a wand you already own; 3500 makes it 17%, on top of granting the full
discount immediately rather than earning it.

## §5 Verification

No mixin is added or removed except the PlayerMana one, so `cleanmix.log` matters only for confirming
`mixins.insanetweaks.playermana.json` is *gone* — the absence of its `APPLY` lines is the evidence.

1. Launch with an existing world. **No missing-registry screen** — this is §2.3 working.
2. `/give` yourself an EBW master wand with no Adaptation upgrade, put an Abomination spell on it,
   cast: refused with the message. Same wand with an Adaptation upgrade applied: casts.
3. Living Wand and Living Spellblade cast with no upgrade at all — `getDefaultAdaptationLevel` is 1.
4. A `sim_wizard` still casts `dispatcher_grasp`. This is the §1.2 carve-out; if it stops, the gate
   was written against the spell instead of against the source.
5. Cast a continuous spell from a Living Wand and confirm it still gains evolution progress — this is
   §2.2's relocated accounting, and its failure is silent.
6. Pick up a Living Wand on a profile that has never had one: the `obtain_living_sentient_gear`
   advancement still fires (§2.2).
7. A wand charged past the new ceiling reads empty rather than negative, and recharges (§4).
8. Confirm the new costs in game against the table in §3.3, on a fully-evolved wand.

## §6 Files touched

| file | change |
|---|---|
| `events/SpellRestrictionEventHandler.java` | the gate moves in (§1) |
| `events/ArcaneBridgeEventHandler.java` | **deleted** |
| `util/PlayerManaCompat.java`, `util/PlayerManaContext.java` | **deleted** (two methods relocated first, §2.2) |
| `util/SpellManaAccounting.java` | **new** — the two surviving methods (§2.2) |
| `util/ArcaneAdaptedFruitHelper.java`, `items/bridge/ArcaneAdaptedFruitItem.java` | **deleted** |
| `mixins/playermana/MixinPlayerManaEventsHandler.java`, `mixins.insanetweaks.playermana.json` | **deleted** |
| `core/LateMixinBooter.java` | drop the PlayerMana mixin config |
| `events/WandEventHandler.java` | point at `SpellManaAccounting` |
| `events/LegacyDormantRemapHandler.java` | add the fruit to `MissingMappings<Item>` (§2.3) |
| `init/ModItems.java` | drop the field, registration and model |
| `commands/CommandInsaneTweaks.java` | drop `/claimarcanefruit` |
| `baubles/ItemZhonyasHourglassArtefact.java`, `items/spellblade/BridgeSpellblade.java` | drop PlayerMana branches |
| `config/categories/TweaksCategory.java` | drop `zhonyaEbManaFallback` |
| `config/categories/GearCategory.java` | capacities 3000 / 3500 (§4) |
| `InsaneTweaksMod.java` | drop the `ArcaneBridgeEventHandler` registration |
| `assets/insanetweaks/spells/*.json` | 13 new costs (§3.3) |
| `models/item/arcane_adapted_fruit.json`, `textures/items/arcane_adapted_fruit.png` | **deleted** |
| `lang/en_us.lang`, `lang/ru_ru.lang` | drop the fruit name line |

Version bump is two places for content: `insanetweaks/build.gradle` and `InsaneTweaksMod.VERSION`.
This removes a registered item and re-prices every spell in the family — a minor bump at least.
