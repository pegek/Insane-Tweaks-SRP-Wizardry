# Spell Architecture Rework — Design

**Date:** 2026-07-03
**Status:** Approved design, implementation NOT started
**Scope:** Architecture refactor of the spell-creation layer (`spells/`). Zero in-game behavior changes. A separate follow-up spec will cover per-spell mechanics/visuals improvements.

## Context

"Spell development" rework, part 1 of 2:
1. **This spec** — how spells are built in code: shared base class, shared utilities, dead-file cleanup.
2. **Future spec** — making individual spells more interesting (mechanics, particles, sounds), building on the new SRP 1.10.7 particle/sound APIs.

### Current pain points

- Five summon spells (`SpellSummonFerCow`, `SpellSummonPrimitiveSummoner`, `SpellSummonPrimitiveYelloweye`, `SpellSummonWizard`, `SpellCallOfDemise`) each duplicate an almost identical ~40-line `spawnMinions` loop, differing only in: flying positioning, whether an attack-damage modifier is applied, and per-minion configuration calls.
- `getFlatMinionCountBonus` is copy-pasted 4×.
- `AbstractSrpMinionSpell.java` and `InsaneTweaksSpellMinion.java` are dead placeholder files ("retired to keep Eclipse/JDT stable" — constraint confirmed stale by the user; build is Gradle/javac).
- `SpellImmuneBond` hand-rolls a ~30-line entity ray-trace that Electroblob's Wizardry utilities already provide.
- Cast feedback (particle burst + sound) is assembled ad hoc in each non-summon spell.

## Stage 0 — Dependency update (DONE 2026-07-03)

- `libs/SRParasites-1.10.3.jar` → `SRParasites-1.10.7.jar`; stale local `ElectroblobsWizardry-4.3.15.jar` removed from `libs/`.
- `build.gradle`: EBW CurseMaven coordinate → `curse.maven:ElectroblobsWizardry-265642:8320066` (4.3.19); fileTree exclude widened to `ElectroblobsWizardry-*.jar`.
- Decompiled reference sources replaced (CFR 0.152): EBW 4.3.19 → `notes/decompiled_mods/ebwizardry_source/decompiled_src` (587 files), SRP 1.10.7 → `notes/decompiled_mods/srp_sourcecode_analis/decompiled_src` (1036 files). `notes/` is gitignored.
- Compat fixes already applied and building green:
  - `ParasiteXPFixHandler`: `SRPSaveData.setTotalKills` gained a trailing `srcID` int; we pass `9` (= `PENALTY_OR_LOSS`).
  - `SummonInfectionSafetyHelper`: **inverted `srpcothimmunity` semantics since SRP 1.10.7** — tag present with value `0` = immune (base-case COTH termination); non-zero = tracked COTH victim that SRP eventually converts into a parasite (`convertEntity`/`spawnInsider` once counter > 1). Helper now forces `0` (was `1`). `ImmuneBondHandler` already used the correct semantics, including deliberately restoring `1` on unbind ("vulnerable again").

## Design

### 1. Base class `AbstractSrpSummonSpell<T extends EntityLivingBase & ISummonedCreature>`

New class in `spells/`, replacing the dead `AbstractSrpMinionSpell.java` content (delete `InsaneTweaksSpellMinion.java` entirely). Extends `SpellMinion<T>` and implements the shared `spawnMinions` loop exactly once:

- server-side guard (`world.isRemote` → `return true`),
- position search via `BlockUtils.findNearbyFloorSpace`, with the flying variant (`this.flying`): `pos.up(2)` on success, random offset around caster on failure; ground variant fails the cast when no floor space is found,
- `createMinion` → `setPosition` → `setCaster` → `SummonInfectionSafetyHelper.onSummonServerTick`,
- lifetime = `MINION_LIFETIME` property × `duration_upgrade` modifier,
- max-health attribute modifier (all spells) + `setHealth(getMaxHealth())`,
- optional attack-damage attribute modifier (hook-controlled),
- `addMinionExtras` + `world.spawnEntity`.

Protected hooks (defaults preserve current behavior of the majority):

| Hook | Default | Overridden by |
|---|---|---|
| `getSummonCount(SpellModifiers)` | `MINION_COUNT` property + flat bonus (`max(0, round(modifiers.get(MINION_COUNT)) - 1)`) | `SpellCallOfDemise` → constant `1` (boss balance, ignores upgrades) |
| `appliesAttackDamageModifier()` | `false` | `SpellSummonFerCow`, `SpellCallOfDemise` → `true` |
| `customizeMinion(T minion, World world, SpellModifiers modifiers)` | no-op | `SpellSummonPrimitiveYelloweye` → `setProjectileDamageMultiplier(POTENCY)`; `SpellSummonPrimitiveSummoner` → `setPotencyMultiplier(POTENCY)`; `SpellSummonWizard` → `configureLoadout(...)` + scaled spell potency |

Migrated spells: the five listed above, each shrinking to a constructor + 1–2 hook overrides (~15 lines). **Not migrated:** `SpellSummonThrall` (slot system, plain `Spell` subclass) and all non-summon spells.

Behavior invariants to preserve exactly:
- `SpellSummonWizard` fails the whole cast when floor space is missing even though it is not flying — keep per-spell ground behavior identical (it already matches the default ground path).
- `SpellSummonPrimitiveYelloweye` is the only `flying(true)` spell.
- `SpellCallOfDemise` keeps its long-lifetime property comment/behavior.
- Wizard loadout randomization (`ArmourClass` pool of WIZARD/SAGE/WARLOCK/BATTLEMAGE, texture variant, potency-step scaling) stays inside `SpellSummonWizard`'s hook.

Fallback: if a JDT-style generics problem resurfaces with the `SpellMinion<T>` hierarchy, degrade to a static `SummonSpellHelper.spawnAll(...)` + options object (composition) without changing behavior.

### 2. Shared utilities

- `SpellImmuneBond`: replace the private `rayTraceEntity` with Electroblob's Wizardry ray-trace utility (`electroblob.wizardry.util.RayTracer` — verify exact method signature in `notes/decompiled_mods/ebwizardry_source/decompiled_src/electroblob/wizardry/util/RayTracer.java` during implementation; must keep 24-block range and living-entities-only filtering).
- New `util/SpellCastFeedback`: small static helper for the recurring "particle burst + sound" pattern currently hand-built in `SpellImmuneBond`, `SpellParasiteShroud`, `SpellPurifyingPulse`. Vanilla `WorldServer.spawnParticle` based for now; SRP 1.10.7 particle API (flash RGB / blood / dot) is reserved for the mechanics-phase spec.

### 3. Conventions & constraints

- Java 8 only, no lambdas in mixins (no mixins touched here), match existing style, keep `@SuppressWarnings("null")` where present.
- No changes to `ModSpells` registration, entity tracking IDs, network packets, or config flags.
- New base class lives in `spells/` alongside its subclasses.

## Verification

- `./gradlew build` green.
- Manual `runClient` test: cast each of the 5 migrated summons + Immune Bond; confirm identical behavior — minion count (incl. `MINION_COUNT` wand upgrade), flying spawn of Primitive Yelloweye, health/damage stats, wizard loadout randomization, Call of Demise single-boss cap, Immune Bond target acquisition at range.
- There is no automated test suite; manual verification is the only path.

## Out of scope

- Per-spell mechanics/visuals rework (next spec).
- Any use of new SRP particles/sounds.
- `SpellSummonThrall` internals, thrall COLLECTING mode (separate spec exists).
- Data-driven spell registration (rejected as overkill).
