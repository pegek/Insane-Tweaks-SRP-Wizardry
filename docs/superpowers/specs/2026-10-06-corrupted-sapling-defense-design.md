# Corrupted Sapling defense — design (insanetweaks 1.21.0)

Status: approved in brainstorming 2026-10-06, awaiting spec review.
Owner mod: **insanetweaks** (content). All code, lang and player-facing text in English.

## 1. Why

The Corrupted Seed loop (fragments → seed → sapling → Corrupted Fruit) promises a defense, but
nothing in the code makes a parasite attack the sapling. In play:

1. Parasites ignore the sapling — there is no defense, only 20 minutes of standing next to it.
2. The wait is long and passive.
3. Growth conditions are invisible — the sapling just stops and nobody says why.
4. Fragments are too rare: 4 fragments at 5% each is ~80 high-tier kills per seed.

This redesign turns the sapling into a short, readable defense event and shortens the grind.

## 2. Player flow

1. Kill high-tier parasites wearing the Blessed Ring or the Ring of the Seven Curses → fragments
   (10%, guaranteed after 15 dry kills). **3 fragments** craft a Corrupted Seed.
2. Plant the seed on corrupted ground (rules in §3). Refusals name the reason; the seed is kept.
3. An ~8 minute event starts: 60 s of luring, then 4 waves ~105 s apart, the 4th with an elite.
   Nearby parasites are pulled onto the sapling throughout.
4. The sapling grows visibly (model and hitbox to 2.5 blocks tall), gains HP and armor.
5. If the owner leaves, dies, removes the ring, or the site becomes invalid, the event **pauses**:
   growth and new waves stop, parasites keep attacking.
6. When the timer completes, the sapling bears a Corrupted Fruit and disappears. If it dies, the
   seed is lost.

## 3. Site rules (`SaplingSiteCheck`)

Checked at planting (refuse, keep seed) and every 100 ticks during the event (pause).

| # | Rule | Default |
|---|------|---------|
| 1 | SRP evolution phase ≥ 1 | — |
| 2 | Any `srparasites:` block within a box of radius 8 horizontally / 4 vertically | 8 / 4 |
| 3 | Dimension not in `saplingDimensionBlacklist` | `{150}` |
| 4 | The block below the sapling has a full solid top face | — |
| 5 | The sapling's own column, layers base+0 to base+8 inclusive, contains no block with a collision box | 8 |
| 6 | In the 5×5 area around the sapling (radius 2), layers base+0 to base+3 inclusive, at most N blocks have a collision box (the sapling's own column excluded — rule 5 covers it) | radius 2, height 3, N = 3 |
| 7 | Not inside any sanctuary region, nor within M blocks of one (`regionRadius + M`) | M = 32 |
| 8 | (planting only) The player has no other active sapling among loaded entities | — |

"Block with a collision box" = `state.getCollisionBoundingBox(world, pos) != Block.NULL_AABB`.
Grass, flowers, snow layers below a full block and other non-colliding blocks do not count.

Rules 1–7 produce a `SiteProblem` enum (`PHASE_TOO_LOW`, `NO_INFESTATION`, `DIMENSION`,
`NO_GROUND`, `ROOFED`, `CROWDED`, `SANCTUARY`, `ALREADY_ACTIVE`, `OK`) with one lang key each,
so the planting refusal and the pause reason share text.

Rule 7 needs one new method: `SanctuaryWorldData.isInsideWithMargin(x, z, margin)` (radius + margin,
same cylinder test as `isInside`), exposed as `SanctuaryRegionHelper.isNear(world, pos, margin)`,
honouring the sanctuary dimension blacklist like `isProtected` does.

## 4. Timeline (`SaplingDefensePlan`, pure arithmetic)

Driven by `defenseTicks`, which advances by 20 every 20 ticks **only while not paused**.

| t (s) | Event |
|-------|-------|
| 0–60 | Lure only |
| 60 | Wave 1 |
| 165 | Wave 2 (+5 HP) |
| 270 | Wave 3 (+5 HP) |
| 375 | Wave 4 + elite (+5 HP) |
| 480 | Bloom — fruit drops, sapling despawns |

`progress = defenseTicks / totalTicks` (0..1) drives size, HP and armor. Config: `saplingLureSeconds`
60, `saplingWaveCount` 4, `saplingWaveIntervalSeconds` 105. Total = lure + waves × interval.
The "+5 HP after each wave" is applied when a wave is survived, i.e. at the start of waves 2–4
(surviving wave 4 ends in bloom, where healing no longer matters). The plan answers `waveDueAt(defenseTicks)` so a wave fires exactly once even across a
save/reload (the last fired wave index is stored in NBT).

**Paused** (checked every 20 ticks) while any of: the owner is offline, dead, or farther than
`saplingConditionRadius` (32); the owner wears neither qualifying ring; any site rule 1–7 fails
(rules re-checked every 100 ticks, result cached in between). The pause reason shown is the first
failing one in that order. While paused: no growth, no waves; lure, regen and incoming damage
continue.

## 5. Lure and waves (`SaplingWaveSpawner`)

**Lure** — every 40 ticks (paused or not), every living SRP parasite within 24 blocks whose current
attack target is not the sapling gets `EntityParasiteBase.setAttackTarget(sapling, 100)`. That SRP
method sets the target and locks it against changes for the given ticks (`targetNewCool`), so the
player cannot pull the whole wave off by standing closer. No mixin needed.

**Wave spawn** — on each wave:
- `wanted = 4 + phase / 2`; `spawn = clamp(wanted − livingParasitesWithin24, 0, 8)`.
- Pool by phase (config string arrays of registry names):
  - phase 1–3: `pri_longarms, pri_manducater, pri_arachnida, pri_reeker, rupter, buglin`
  - phase 4–6: `pri_longarms, pri_manducater, ada_longarms, ada_manducater, ada_arachnida, ada_reeker`
  - phase 7+: `ada_longarms, ada_manducater, ada_arachnida, ada_reeker, grunt, seeker`
- Wave 4 also spawns exactly 1 elite, on top of the count: phase 1–3 `ada_manducater`,
  phase 4–6 `ada_bolster`, phase 7+ `marauder` (each configurable).
- Position: random point on a ring 10–16 blocks from the sapling, snapped to the top solid block,
  2 blocks of air above, not in or near a sanctuary (rule 7 margin 0). Up to 10 attempts per mob;
  a mob that finds no position is skipped (logged once per wave at debug).
- Creation: `EntityList.createEntityByIDFromName` → `onInitialSpawn` → `spawnEntity` (the pattern
  `SrpWizardryAssimilationHelper` already uses). Direct `spawnEntity` bypasses `CheckSpawn`, so
  srpwizmixins' origin gate does not veto it; the dimension blacklist (rule 3) keeps dim 150 clean.
- Each spawned mob immediately gets `setAttackTarget(sapling, 200)`. It is an ordinary parasite
  afterwards — nothing despawns it when the event ends.
- An unknown registry name is skipped with one WARN per name per session.

**SRP isolation** — every reference to `EntityParasiteBase` lives in a private static nested class
(the existing `ParasiteScan` pattern), so the sapling class still loads without SRParasites. Without
SRP the seed cannot be planted (rule 1 fails), so the event never starts.

## 6. Sapling stats

| | progress 0 | progress 1 |
|---|---|---|
| Max HP | 50 | 80 |
| Armor | 0 | 6 |
| Armor toughness | 0 | 3 |
| Hitbox (w × h) | 0.6 × 0.8 | 1.2 × 2.5 |

- Linear in `progress`. When max HP rises, current HP rises by the same delta.
- The four SRP-phase HP bands (`saplingHpPhase1/4/6/9`) are removed. Forge keeps their values in
  `insanetweaks.cfg` as dead entries; that is harmless.
- Immune to every potion effect: `isPotionApplicable` returns false. NBT `srpcothimmunity = 0`
  (in SRP 1.10.7+ zero means immune) so it can never be converted.
- Knockback resistance 1.0. Speed 0, no AI (unchanged).
- Regenerates 1 HP every 10 s, always (also while paused). +5 HP per survived wave (§4).
- Damage whose true source is a player is ignored (`saplingIgnorePlayerDamage`, default true), so a
  battlemage's own AoE cannot kill it. `/kill` and void damage still work.
- The owner can **uproot** it with sneak + right-click on an empty hand: the sapling is removed and
  the seed is lost (no restart exploit). Without this an abandoned, player-proof sapling would sit
  in the world forever.

**Size sync** — progress is synced as a VARINT 0–1000 data parameter. Both sides call `setSize`
from `notifyDataManagerChange` when it changes; `readEntityFromNBT` restores it on load. The
renderer scales the model from the same value (`RenderCorruptedSapling.preRenderCallback`),
replacing the 5-stage `STAGE` parameter.

## 7. Feedback

- **Boss bar** (`BossInfoServer`, a server-side vanilla class): shown to players within 32 blocks,
  membership refreshed every 20 ticks. Title "Corrupted Sapling — Wave 2/4" (purple, progress =
  growth) or "Corrupted Sapling — Paused: <reason>" (red). Cleared in `setDead` and
  `onRemovedFromWorld`.
- **Action bar** to the owner: "The hive answers — wave 2/4" on each wave, and the pause reason
  each time it changes.
- **Glowing** while the event runs (`saplingGlow`, default true) so it stays visible in a swarm.
  This is the entity glow flag, not a potion, so the potion immunity does not block it.
- **Chat** to the owner on bloom ("The sapling bears fruit.") and on death ("The sapling has died.
  The seed is lost.").
- Seed and fragment tooltips rewritten for the new rules (3 fragments, ~8 min, waves, site rules).

## 8. Fragments (`CorruptedFragmentDropHandler`)

- `fragmentDropChance` default 0.05 → **0.10**.
- New `fragmentPityKills` default **15**: a per-player counter in persisted player NBT
  (`InsaneTweaksFragmentPity`) counts eligible kills without a drop; at the threshold the drop is
  guaranteed and the counter resets. Any drop resets it. 0 disables pity.
- Recipe `corrupted_seed.json`: 3 fragments instead of 4.
- Note: the instance's `insanetweaks.cfg` keeps the old 0.05 — Forge never overwrites an existing
  value with a new default. Update it by hand on deploy.

## 9. Persistence and migration

NBT on the sapling: `SaplingOwner` (unchanged), `SaplingDefenseTicks`, `SaplingWavesFired`,
`SaplingRegenTimer`. Entity id `insanetweaks:corrupted_sapling` and tracking id **116** are
unchanged (network-stable).

A sapling saved by ≤1.20.3 carries only `SaplingGrowth` (out of 24000). On load it is mapped to
`defenseTicks = SaplingGrowth / 24000 × totalTicks`, and waves already behind that point are
marked as fired, so an old sapling resumes mid-event instead of restarting or blooming at once.

## 10. Components

| File | Change |
|------|--------|
| `entities/EntityCorruptedSapling.java` | State machine, stats, pause, boss bar, NBT, migration, uproot |
| `entities/sapling/SaplingSiteCheck.java` | New. Rules 1–8 → `SiteProblem` |
| `entities/sapling/SiteProblem.java` | New. Enum + lang key per value |
| `entities/sapling/SaplingDefensePlan.java` | New. Pure timeline arithmetic |
| `entities/sapling/SaplingWaveSpawner.java` | New. Lure, pools, positions, spawn (SRP isolated) |
| `items/fruit/CorruptedSeedItem.java` | Uses `SaplingSiteCheck`; new tooltip |
| `items/fruit/CorruptedSeedFragmentItem.java` | Tooltip: 3 fragments |
| `events/CorruptedFragmentDropHandler.java` | Pity counter |
| `sanctuary/SanctuaryWorldData.java`, `SanctuaryRegionHelper.java` | `isInsideWithMargin` / `isNear` |
| `client/renderer/entity/RenderCorruptedSapling.java` | Scale from progress |
| `config/categories/BaubleFruitsCategory.java` | New fields (§11), HP bands removed |
| `recipes/corrupted_seed.json` | 3 fragments |
| `lang/en_us.lang` | New keys; `ru_ru.lang` gets the same keys in English so nothing renders raw |
| `InsaneTweaksMod.java`, `insanetweaks/build.gradle` | Version 1.21.0 (both places) |

## 11. New config (`baubleFruits` category)

All read live unless noted. Removed: `saplingGrowthTicks`, `saplingMinParasites`,
`saplingHpPhase1/4/6/9` (the event no longer uses them).

| Field | Default |
|-------|---------|
| `saplingLureSeconds` | 60 |
| `saplingWaveCount` | 4 |
| `saplingWaveIntervalSeconds` | 105 |
| `saplingLureRadius` | 24 |
| `saplingWaveBaseCount` / `saplingWaveMaxSpawn` | 4 / 8 |
| `saplingSpawnRingMin` / `saplingSpawnRingMax` | 10 / 16 |
| `saplingWavePoolEarly` / `Mid` / `Late` | §5 lists |
| `saplingEliteEarly` / `Mid` / `Late` | `ada_manducater` / `ada_bolster` / `marauder` |
| `saplingHpStart` / `saplingHpEnd` | 50 / 80 |
| `saplingArmorEnd` / `saplingToughnessEnd` | 6 / 3 |
| `saplingRegenIntervalSeconds` / `saplingWaveHeal` | 10 / 5 |
| `saplingIgnorePlayerDamage` | true |
| `saplingGlow` | true |
| `saplingConditionRadius` (owner range, kept) | 32 |
| `saplingInfestationRadius` / `saplingInfestationHeight` | 8 / 4 |
| `saplingDimensionBlacklist` | `{150}` |
| `saplingColumnClearance` | 8 |
| `saplingCrowdRadius` / `saplingCrowdHeight` / `saplingCrowdTolerance` | 2 / 3 / 3 |
| `saplingSanctuaryMargin` | 32 |
| `fragmentDropChance` | 0.10 |
| `fragmentPityKills` | 15 |

## 12. Side safety

- `BossInfoServer` is `net.minecraft.world`, server-side; never touched on the client.
- New sapling helpers are common code with no client types. The renderer change stays in
  `RenderCorruptedSapling` (registered from `ClientProxy`, unchanged).
- No new `@SideOnly` anywhere. No new mixins.

## 13. Testing (in game, DEv 1.2)

1. Plant refusals: phase 0, no SRP block nearby, dim 150, roof above, 4 blocks in the 5×5 ring,
   next to a sanctuary, second sapling — each names its reason, seed kept.
2. Full run in phase 1–3: lure pulls nearby parasites onto the sapling; 4 waves at the expected
   times; elite in wave 4; boss bar title/progress; bloom drops one fruit.
3. Pause: walk 40 blocks away, take off the ring, place a block on top, place a sanctuary nearby —
   boss bar turns red with the right reason; resumes after fixing.
4. Stats: HP 50 → 80 and armor visible (e.g. via a debug look or Waila/TOP), potion immunity
   (splash poison, SRP Viral), regen 1 HP/10 s, +5 HP per wave, player hits do nothing.
5. Size: model and hitbox grow smoothly to ~2.5 blocks; hitbox matches the model.
6. Save/reload mid-wave: event resumes, no wave fires twice. A ≤1.20.3 sapling resumes mid-event.
7. Death: sapling killed → seed lost message, boss bar gone.
8. Fragments: pity guarantees a drop by the 15th eligible kill; recipe takes 3.
9. Dedicated server boot (side safety) and `cleanmix.log` unchanged (no new mixins).

## 14. Out of scope

- The first generated structure (3×3 Imbuement Altar showcase + strong enemies) — its own spec next.
- Rewards beyond the existing Corrupted Fruit.
- Several saplings sharing one event.

## 15. Revision 1.21.1 (after the first playtest, 2026-10-06)

The first run was lost two seconds after the lure started: every parasite within 24 blocks was
pinned onto a 50 HP sapling. Changes:

- **Attacker cap** — `saplingMaxAttackers` (4): the lure pins at most this many parasites onto the
  sapling, nearest first; the rest keep their own AI (usually the player). Wave mobs are no longer
  pinned at spawn — the next lure pass hands out the free slots.
- **Provocation** — a bound parasite hit by a player gets SRP's `targetNewCool` cleared, the player
  as its locked target for `saplingProvokeSeconds` (10), and is skipped by the lure meanwhile
  (`SaplingParasiteHandler`, `LivingAttackEvent`).
- **Drop penalty** — every parasite the lure sees or a wave spawns is tagged
  `InsaneTweaksSaplingBound` (`SaplingTags`). On death each dropped stack is lost with
  `saplingDropPenalty` (0.5) and XP is scaled by `1 − penalty`; fragments too, decided inside
  `CorruptedFragmentDropHandler` so a lost fragment counts as a dry kill for pity.
- **Balance** — HP 250 → 350; wave base count 3; spawn ring 16–22; pools one tier lighter
  (no grunt/seeker/marauder; elites ada_longarms / ada_manducater / ada_bolster).
- **Visuals** — client-side purple aura (SPELL_WITCH, PORTAL above 50%) thickening with growth;
  new model (roots, twisting three-segment trunk, swaying branches with leaf clusters, thorned
  pod) and a generated 64×64 texture.
