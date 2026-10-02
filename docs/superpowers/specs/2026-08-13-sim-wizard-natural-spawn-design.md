# Sim wizard natural spawn, and the phase lookup that was never working

**Date:** 2026-08-13
**Mod:** `insanetweaks` (content) — gameplay and its own entities, so content owns all of it
**Status:** designed, not implemented
**Target SRP:** 1.10.7 (`notes/decompiled_mods/srp_sourcecode/fulljar`) — every bytecode claim was read off that jar
**Predecessors:** `2026-08-08-ebw-abomination-element-design.md`,
`2026-08-08-abomination-economy-and-balance-design.md`. The second one declares this work out of
scope and explains why the craftable dust recipe exists until it lands
(§2.3). Read that section before touching the economy.
**Handoff this closes part of:** `docs/superpowers/plans/2026-08-11-abomination-magic-system-handoff.md` §C.

## Problem

`EntitySimWizard` has no spawn of its own. It exists only when SRP assimilates an
`ebwizardry:wizard` / `evil_wizard` or the Ancient Spellcraft equivalents, so its population is the
product of two things this mod does not control. That matters because these mobs are meant to be the
main source of Abomination spectral dust, which gates `adaptation_upgrade` and `living_wand`.

The economy spec worked around it with a deliberately worse crafting recipe. This spec removes the
need for that workaround by giving the mobs a real spawn, and the recipe stays as the grind fallback
exactly as that spec intended. Nothing there has to be undone.

A second, unrelated problem surfaced while verifying the first, and is folded in because the spawn
design depends on the mechanism it breaks: **the SRP evolution phase lookup asks for the wrong
dimension**, so tier scaling has never actually run. See §4.

## Scope

**In scope.** Natural spawning for `sim_wizard` and `sim_battlemage` in SRP's parasite biomes (§1–§2),
two additions to the default NPC spell pool (§3), and the phase-lookup fix (§4).

**Out of scope, deliberately.**

- NPC cast overloads for the six spells that extend bare `Spell`. Those need real new code in the
  casting path and are a different class of risk; §3.3 records what is needed.
- Disabling `insanetweaks:summon_wizard` outright. That is a player-facing change, not an NPC one;
  §3.4 records the safe way to do it.
- Tier-gating spells per tier. §3.5 records why the existing mechanism makes this expensive.

## §1 Spawn mechanism

### 1.1 What the zone is

SRP registers exactly **two** parasite biomes, not four:
`srparasites:biomeparasite_shrouded` and `srparasites:biomeparasite_harlequin`. `SRPBiomes` holds
only `biomeShrouded` and `biomeHarlequin` as static fields; `BiomeParasiteBoils` and
`BiomeParasiteDemen` are classes in the jar that nothing instantiates.

These biomes are written into the world at runtime as infestation spreads — `BlockParasiteSpreading`
and the other spreading blocks reference them — so the zone grows on its own and is visible to the
player. That is what makes it a place to go rather than an invisible dice roll.

### 1.2 Why not `EntityRegistry.addSpawn`

The obvious approach is a permanent `SpawnListEntry` in each biome's `MONSTER` list. It is wrong
here, and the reason is not ordering:

`SRPBiomes.clearMobSpawnList()` calls `mobListClear()` on both biomes, which calls `List.clear()` on
**all four** spawn lists (`field_76762_K` MONSTER, `field_76761_J` CREATURE, `field_76755_L`
WATER_CREATURE, `field_82914_M` AMBIENT). It has two callers: `CommonProxy` during init, after
`SRPSpawning.init()` — and **`SRPCommandRoot`, i.e. a command available at runtime**.

A permanent entry lives in exactly that list. One admin command and our spawn silently stops for the
rest of the session, with nothing in any log to say so.

🚨 **The related sweep is safe, and it is worth knowing why.** `SRPSpawning.removeInit()` walks every
registered biome and removes entries from the `MONSTER` list, matching on
`entry.entityClass.toString().contains("scapeandrunparasites")`. Our class name does not contain that
string, so a permanent entry would survive *this* sweep. It is `mobListClear` that kills it, not
`removeInit`. Do not conclude from reading `removeInit` alone that the list is safe.

### 1.3 The mechanism: `WorldEvent.PotentialSpawns`

Inject the entry on demand instead of registering it permanently. Forge fires
`WorldEvent.PotentialSpawns` from `WorldServer.getSpawnListEntryForTypeAt`, handing over the
candidate list for one specific position immediately before the weighted pick. We append our
`Biome.SpawnListEntry` there.

This settles five things at once:

- **Nothing to clear.** We hold no entry in any biome's list, so `mobListClear` — from init or from
  the command — does not reach us.
- **No init ordering question.** We do not care when SRP does its work.
- **The cap needs no second handler.** Above the limit we simply do not append. No offer, no spawn.
- **An emptied list is fine.** `getSpawnListEntryForTypeAt` fires the event even when the biome's own
  list is empty, so appending to a cleared parasite biome works.
- **It composes with `srpwizcore`'s SpawnEngine.** That mod hooks the same event at `LOWEST`, i.e.
  after us, and can still trim us by its per-dimension namespace budget. The stricter of the two
  limits wins and **no dependency between the mods is created** — content must not depend on
  `srpwizcore`, and this does not.

What is given up: no other mod can see our entry in a biome's spawn list outside of an actual spawn
attempt, so a hypothetical "what spawns here" inspection tool would not list us. Nothing in DEv 1.2
does that.

### 1.4 Cost

The event fires **once per candidate position**, which is a hot path. Two constraints follow, and
they are requirements, not suggestions:

- Test in increasing order of cost: `EnumCreatureType.MONSTER` first, then biome membership, then the
  cap. The first two reject almost every invocation.
- The population count must be cached per dimension and refreshed on an interval (a constant in code,
  ~40 ticks), never computed per event. A scan of `loadedEntityList` in this pack is not cheap, and a
  cap does not need tick accuracy.

CLAUDE.md's measurement is the precedent: a Flare profile put `RandomAccessFile.writeBytes0` at
**15.58%** of the server thread, purely from log I/O. Work on this thread is not free.

### 1.5 New file

`insanetweaks/src/main/java/com/spege/insanetweaks/events/SimWizardNaturalSpawnHandler.java`

Two handlers in one class - the population tracker on `TickEvent.WorldTickEvent` and the entry
injector on `WorldEvent.PotentialSpawns`. They share one private map and change together, so they
share a file. Server-side only by nature of both events; the class references no client type, so it needs no
`@SideOnly` and its registration needs no side guard.

Registered from `InsaneTweaksMod.init` conditionally on `naturalSpawn.enableNaturalSpawn`, following
the established pattern of gating handler registration rather than checking a flag inside a hot
handler. Because registration is what the flag controls, that field carries
`@Config.RequiresMcRestart` — per CLAUDE.md that annotation is the default for exactly this case, and
a flag that claims to be live while actually gating an event-bus registration is a lie.

## §2 Configuration

A new subcategory `entities.assimilatedWizard.naturalSpawn`, **not** new fields on the existing
`spawning` subcategory. Despite its name `spawning` holds attribute multipliers, phase scaling and
the assimilation map — nothing about spawning. Adding six more fields there would entrench a
misleading name.

| field | default | role |
|---|---|---|
| `Enable Natural Spawn` | `true` | master toggle; gates handler registration, so `@Config.RequiresMcRestart` |
| `Spawn Biomes` | `srparasites:biomeparasite_shrouded`, `srparasites:biomeparasite_harlequin` | registry names, resolved through `ForgeRegistries.BIOMES` |
| `Wizard Spawn Weight` | `10` | weight in the pick |
| `Battlemage Spawn Weight` | `3` | ~23% of our spawns |
| `Max Per Dimension` | `12` | shared cap, counts both types |
| `Debug Logging` | `false` | per-decision diagnostics; read live, no restart. Must be throttled — see §1.4 |

Group size is fixed at 1–1 in code. `SpawnListEntry` accepts min/max, but a pack of casters is not a
linear increase in threat and that lever should not exist until something needs it. The count-refresh
interval is likewise a code constant: a performance knob, not a balance one.

### 2.1 Three decisions that are not obvious

**Default on.** The feature is self-limiting in a way that justifies it: it does nothing except where
SRP parasite biomes exist, and those appear only once infestation has genuinely spread. On an
uninfested world it is inert. Defaulting it off would mean the dust supply problem — the reason this
work exists — persists for anyone who does not read the config.

**The cap is the real lever; the weight is secondary.** This follows from §1.2: SRP *clears* these
biomes' monster lists and adds its own parasites per evolution phase. In phases where its list is
short or empty, our weight — whatever its value — takes most or all of the monster spawns in that
biome. The absolute number controls nothing. `Max Per Dimension` does.

**No phase gate and no dimension list.** Both would be dead knobs. The biomes only exist once
infestation has progressed, so a minimum-phase threshold is already implied by the zone existing at
all. And dim 150 never receives SRP — a pack invariant — so those biomes never appear there and
there is nothing to exclude. Each knob would restate the condition next to it.

### 2.2 A shared cap, and what that means

`EntitySimBattlemage extends EntitySimWizard`, so one `instanceof EntitySimWizard` test counts both.
The cap is therefore shared by construction rather than by extra code: a battlemage consumes a
wizard's slot. This is the intended reading of "at most N of these things per dimension".

The cap is per dimension, not per nest. Ten wizards can stand in one place if that is where the zone
is. Accepted: a per-nest cap would need proximity queries on the same hot path, and the zone-shaped
alternative was considered and rejected in the design discussion.

## §3 The NPC spell pool

### 3.1 The `npcs` flag is not the gate, and the cost is not a brake

Two findings that change how this is reasoned about.

🚨 **`npcs: false` does not stop our sim wizard from casting anything.** `Spell.canBeCastBy` reads
that flag, but across all of EBW 4.3.19 it is consulted only by `EntityWizard`, `EntityEvilWizard`,
`EntityWizard.populateSpells` and `BehaviourSpellDispense`. Our `ensureSpellPool()` never asks. The
real gate is the config list `entities.assimilatedWizard.spells.spellPool`, whose default contains
eight EBW spells and only two of ours.

🚨 **The second gate is structural.** `Spell.cast(World, EntityLiving, EnumHand, int,
EntityLivingBase, SpellModifiers)` — the NPC overload — **returns `false` in the base class**. A
spell can only be NPC-cast if a superclass implements it. Ours divide cleanly:

| base class | spells | NPC-castable |
|---|---|---|
| `SpellRay` | `cleanse`, `dispatcher_grasp` | yes, inherited |
| `SpellMinion` (via `AbstractSrpSummonSpell`) | `call_of_demise`, `summon_fer_cow`, `summon_light_bomber`, `summon_primitive_summoner`, `summon_primitive_yelloweye`, `summon_wizard` | yes, inherited |
| bare `Spell` | `immune_bond`, `parasite_shroud`, `purifying_pulse`, `summon_thrall`, `test_projectile`, `yelloweye_gland` | **no** — `cast` returns `false` |

🚨 **And the NPC pays no mana.** The cast gate is `EntitySimWizard.nextCastReadyTime`, an absolute
world-time stamp set from config. Nothing in the NPC casting path reads a spell's cost. So the
economy spec's §3 bands — tool ≤ 250, ritual ≥ 500 — are a **player** economy and mean nothing here:
a 1800-mana capstone costs an NPC exactly what a 130-mana tool costs, one cooldown. "It is a ritual,
so it will be rare" is not an argument when choosing what an NPC may cast.

### 3.2 What is added

Two entries appended to the default `spellPool`:

| spell | why |
|---|---|
| `insanetweaks:dispatcher_grasp` | the only offensive spell of the element that is NPC-ready. This is what makes Abomination visible in combat rather than only in an inventory |
| `insanetweaks:summon_light_bomber` | escalation, the same class as the two summons already in the pool |

Four NPC-capable spells are deliberately left out:

- **`cleanse`** — technically ready, but `applyCleanse` puts `ModPotions.CLEANSE` on **whatever it
  hits**, with `ENTITY_ZOMBIE_VILLAGER_CURE` for a sound. The wizard aims at the player, so the
  effect would be an enemy buffing the player. Inverted behaviour, not a thematic objection.
- **`call_of_demise`** — the capstone. Its 1800-mana price would have defended it; per §3.1 the NPC
  does not pay it, leaving capstone behaviour on an ordinary cooldown.
- **`summon_primitive_summoner`** — a summoner summoning a summoner. Whether the SRP minion summons
  in turn is unverified, and with no mana cost that is exactly the risk not to admit unchecked.
- **`summon_wizard`** — author's call. For the record it summons `EntityWizardMinion`, a minion and
  not another sim wizard, with `minion_lifetime: 900` and `minion_count: 1`; the concern is that it
  is `tier: master` and, per §3.1, tier buys no restraint on an NPC.

### 3.3 The six that need code (out of scope)

The bare-`Spell` group needs an override of the NPC `cast` overload before it can ever be pooled.
Two of them should stay excluded regardless, for reasons already recorded at
`EntitySimWizard.ensureSpellPool`'s javadoc: `summon_thrall` needs an `EntityPlayerMP`, and
`parasite_shroud` hides its target from SRP, which is backwards for something that *is* a parasite.

### 3.4 Disabling a spell safely (recorded, not done here)

Every spell JSON carries an `enabled` block with nine contexts (`book`, `scroll`, `wands`, `npcs`,
`dispensers`, `commands`, `treasure`, `trades`, `looting`) and `Spell.isEnabled()` checks them. To
retire a spell, set those to `false`. **The registry entry stays**, which is the whole point.

🚨 Do not delete the spell class. `Spell` extends `IForgeRegistryEntry.Impl` and
`Spells.createRegistry` never calls `disableSaving()`, so the id map is in `level.dat`; removing a
registered spell gives a missing-registry screen on every existing world.

### 3.5 Tier filters stay empty

`noviceSpellFilter` / `adeptSpellFilter` / `masterSpellFilter` are **allowlists** — an empty array
means no restriction. Making one spell master-only therefore requires enumerating the full permitted
list for the other two tiers: three arrays to maintain in order to express one exclusion. Nothing
added here needs tier gating, so the cost is unjustified. Recorded so a future session knows the
shape before designing against it.

## §4 The phase lookup fix

### 4.1 The bug

`EntitySimWizard.readSrpPhase()` calls:

```java
int rawPhase = data.getEvolutionPhase(cfg.srpSaveDataId) & 0xFF;   // srpSaveDataId == 104
```

`SRPSaveData.getEvolutionPhase(int)` takes a **dimension id**. SRP passes
`world.provider.getDimension()` at every one of its own call sites (verified at three separate sites
in `SRPEventHandlerBus`). We pass a save-data id where a dimension belongs.

Unless play happens in dimension 104, the result is:

- `cachedSrpPhase` is always `0`, so `rolls = 1 + 0 / divisor` is always **one roll**. Tier never
  improves with infestation progress; the distribution is the raw `tierWeights`.
- `cachedPhaseBonus` stays `1.0`, so `Enable SRP Phase Scaling = true` has never done anything.

The fix is to pass `this.world.provider.getDimension()`.

🚨 **The other `104` is fine — do not "fix" it too.** `SRPSaveData.get(world, 104)` is correct
enough: the method body is a singleton over `MapStorage` (`instance` / `clientInstance` statics) and
the int is only forwarded to `createData`. The value decides nothing there. Only the
`getEvolutionPhase` argument is wrong.

`srpSaveDataId` keeps its one real use (the `get` call) but its comment currently claims it is "used
for evolution phase lookup", which after this fix is false. Correct the comment in the same change.

### 4.2 What the fix actually changes

With `tierWeights = {60, 30, 10}`, `tierPhaseRollDivisor = 2` and `phaseScalingMaxPhase = 4`, the
roll is best-of-N over the weights:

| SRP phase | rolls | NOVICE | ADEPT | MASTER |
|---|---|---|---|---|
| today (broken, any phase) | 1 | 60% | 30% | 10% |
| 0–1 | 1 | 60% | 30% | 10% |
| 2–3 | 2 | 36% | 45% | 19% |
| 4+ | 3 | ~22% | ~51% | ~27% |

That shift is precisely what the dust economy needs: the `adept` and `master` loot tables are the
ones carrying dust and spell books.

🚨 **But the fix turns on a second thing at the same time, and that is the part to watch.**
`phaseScalingPerPhase = 0.10` with the phase clamped at 4 means `cachedPhaseBonus` becomes **1.4** —
a 40% health and armour bonus that has never once been active in play. So a single one-line fix
raises both how often a high tier appears *and* how tough every wizard is, on the same axis.

This is the "two curves multiplying" failure the code comments already name as the v2.1 *way too
strong* lesson, and it is why §2.1 deliberately kept spawn density flat. Landing it is still correct
— a config flag that silently does nothing is worse — but the first playtest must look at it.

**If it proves too much, the lever is `phaseScalingPerPhase`, not `tierWeights`.** The tier
distribution is what the economy needs; the attribute bonus is the part that can be dialled back to
0.05 or 0 without costing anything the rest of this spec depends on.

## §5 Failure modes

Every one of these fails silently, which is why each gets an explicit countermeasure.

| risk | countermeasure |
|---|---|
| a biome registry name is wrong → empty set → the handler never fires | resolve names once at init; log the **accepted count at INFO and every rejection at ERROR with its reason**, exactly as `SpawnEngine.reload()` does |
| the biomes do not exist yet in the test world → looks like a broken handler | see §6 |
| the cap counts the wrong dimension | key the cache on `world.provider.getDimension()`; a diagnostics flag in the new subcategory, read live |
| server-thread cost | §1.4's ordering and caching are requirements |
| SpawnEngine collision | same event, SpawnEngine at `LOWEST` runs after us; both single-pass, stricter limit wins |

## §6 Verification

**The test conditions can be created on demand.** SRP ships `CommandHarlequinHere`, described in its
own lang file as converting the surrounding biome to Harlequin and refreshing chunks. That removes
the worst part of testing this — no waiting for infestation to spread naturally.

Sequence, on the DEv 1.2 instance:

1. Run the SRP command to convert the surrounding biome to `biomeparasite_harlequin`.
2. Confirm in `latest.log` that the `[InsaneTweaks]` init line reports **2 accepted spawn biomes**
   and zero rejections.
3. Observe spawns; confirm both types appear and the battlemage is visibly rarer.
4. Exceed the cap deliberately (lower `Max Per Dimension` to 2) and confirm spawning stops.
5. Kill wizards of each tier and confirm the dust drops per the economy spec's table.
6. With SRP evolution advanced, confirm tiers above NOVICE appear at roughly §4.2's rates.

🚨 **`cleanmix.log` proves nothing here.** This spec adds no mixin; it is native code end to end.
The evidence is `latest.log` and in-game observation. Do not go looking for `APPLY` lines.

## §7 Files touched

| file | change |
|---|---|
| `events/SimWizardNaturalSpawnHandler.java` | **new** — the whole spawn mechanism (§1) |
| `config/categories/EntitiesCategory.java` | new `NaturalSpawn` subcategory (§2); two entries appended to `spellPool` (§3.2); corrected comment on `srpSaveDataId` (§4.1) |
| `entities/EntitySimWizard.java` | one-line phase-lookup fix (§4.1) |
| `InsaneTweaksMod.java` | conditional registration of the new handler |

Version bump per CLAUDE.md is **two places** for content: `insanetweaks/build.gradle` and
`InsaneTweaksMod.VERSION`. The second is the one that drifts.
