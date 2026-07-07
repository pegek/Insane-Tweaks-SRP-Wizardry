# Spell Mechanics & Visuals Rework — Design (part 2)

**Date:** 2026-07-07
**Status:** Approved design, implementation NOT started
**Scope:** Part 2 of the spell rework: gameplay mechanics for three spells (Yelloweye Gland, Parasite Shroud, Purifying Pulse) + a minimal visual-feedback layer for all spells, built on SRP 1.10.7 particles and EBW 4.3.19 APIs. Part 1 (architecture) is done: `docs/superpowers/specs/2026-07-03-spell-architecture-rework-design.md`.

## Decisions from brainstorming

- Scope: visuals for **all** spells (incl. SummonThrall and ImmuneBond) + deeper mechanics for **Yelloweye Gland, Parasite Shroud, Purifying Pulse**. Immune Bond mechanics unchanged (just refactored in part 1).
- Visual intensity: **minimal** — particles only for events that are currently silent; existing effects stay untouched.
- Particle delivery: **own network packet** (approach A) — SRP's `ParticleSpawner` is client-only and SRP's `SRPPacketParticle` only supports fixed scenarios without arbitrary RGB.

## Design

### 1. Particle infrastructure

**New `network/PacketSrpParticle`** (4th packet in `InsaneTweaksNetwork`):

- Payload: `double x, y, z`; `byte particleType` (maps to `SRPEnumParticle`: FLASH, DOT, GCLOUD, BLOOD, …); `int rgb` (packed 0xRRGGBB); `byte count`; `float spreadH, spreadV`; `float speed`.
- Sent server→client via `sendToAllAround` (~48-block range, same as SRP).
- Client handler loops `count`× calling `ParticleSpawner.spawnParticle(type, …, r, g, b)` with Gaussian offsets — modelled on `SRPPacketParticle.Handler.spawnParticles` but with free type/RGB choice.
- Sided-class discipline identical to SRP's own packet (handler must not classload client-only code on the server).

**`util/SpellCastFeedback` extension** — server-side overloads that send the packet:
`srpBurst(world, x, y, z, type, rgb, count, spreadH, spreadV, speed)` and `srpBurstAt(world, entity, heightFraction, …)`. Existing vanilla-particle methods unchanged; call sites stay one-liners.

### 2. Mechanics

**Yelloweye Gland — charge-up replaces the 4-shot cycle.**
- Delete `advanceShotCycle` + NBT tag `insanetweaksYelloweyeGlandCycle`.
- Every shot is the heavy/explosive variant (today's every-4th shot).
- `yelloweye_gland.json`: `chargeup` 12 → ~30 ticks; raise `cost`/`cooldown` to compensate (single stronger shot). EBW native chargeup provides the charge sound (`ITEM_WAND_CHARGEUP`) and HUD charge bar for free.
- Explicit constraint: EBW chargeup is **fixed-time with auto-fire**; variable "hold longer = stronger" is NOT natively supported and is out of scope (would need a wand mixin).

**Parasite Shroud — three combined mechanics:**
1. **Tiers by potency:** tier 1 (base) hides from primitive parasites only; tier 2 (potency ≥ ~1.3) also from advanced ones. Classification resolved during planning: primitive parasites share the base class `com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityPPrimitive` — tier 1 filters with `instanceof EntityPPrimitive`; no config list needed. Scent disruption stays tier-independent.
2. **Wizard-armour synergy:** duration +~15% per worn wizard-armour piece — any `ItemWizardArmour` counts (decided in planning: element-matched counting via `getMatchingArmourCount` would almost never trigger, since this spell's element is generic MAGIC and players wear elemental sets; a plain `instanceof ItemWizardArmour` count over armour slots serves the battlemage-synergy intent).
3. **Break on attack:** the player attacking anything while shrouded removes the shroud early (handler on `AttackEntityEvent`/`LivingHurtEvent` checking `hasShroud`); base duration rises 160 → ~240 ticks in exchange (`BASE_DURATION_TICKS` constant in `SpellParasiteShroud.java`).

**Purifying Pulse — extend the existing wave.**
Block purification already exists (`EntityPurifyingWave.purifyRing` + `SrpPurificationHelper`); Beckons already get dmg 6 + glow + weakness/slowness. New:
1. Generalize `affectBeckons` → `affectParasites`: **all** SRP parasites in the ring get a "searing" debuff (weakness + slowness + short magic DoT); Beckons keep their current harsher treatment.
2. **COTH cleanse:** non-parasite mobs in range get their COTH infection removed — strip the `COTH_E` potion effect and **remove** the `srpcothimmunity` tag (cured but re-infectable; deliberately NOT set to 0, which would grant permanent immunity and stack oddly with Immune Bond — bonded mobs hold the tag at 0 and are skipped by the `> 0` victim check). The spell becomes a real cure for infected animals/villagers.

### 3. Visuals map

**Planning-time reality check** (corrections to the brainstormed "silent events" list, found by reading the code):
- Minion spawn/despawn is **not silent** — every minion's `onSpawn()`/`onDespawn()` already spawns 15× EBW `DARK_MAGIC` particles with a per-entity colour (e.g. FerCow dark green). Left unchanged (minimal principle).
- Immune Bond active is **not silent** — `ImmuneBondHandler` already draws a yellow `REDSTONE` ring every 10 ticks. Replaced (not added) with the spec'd violet SRP DOT.
- Thrall spawn *looks* covered (`world.spawnParticle(SMOKE_LARGE, …)` in `ThrallSlotManager`) but `World.spawnParticle` is a client-side no-op on the server, so it is effectively silent. Replaced with the packet burst.

| Event | Effect | Path |
|---|---|---|
| Thrall spawn (`ThrallSlotManager.spawnThrall`) | dark-red FLASH + a few dark GCLOUD (replaces dead `SMOKE_LARGE` call) | `PacketSrpParticle` |
| Shroud: natural expiry | fading grey GCLOUD around the player, no sound | packet from `ParasiteShroudEventHandler` |
| Shroud: broken by attacking (new) | sharp red FLASH + glass-break sound | same |
| Yelloweye: charging | yellow-green DOT converging on the hand during chargeup | client-only tick handler calling SRP `ParticleSpawner` directly (no packet) |
| Pulse: parasite seared (new) | small golden FLASH on the target | from `affectParasites` |
| Pulse: COTH cleansed from a mob (new) | white-gold DOT burst + XP-orb sound | same |
| Immune Bond: bond active | subtle violet SRP DOT on the bonded mob every ~2 s (replaces yellow REDSTONE ring every 0.5 s) | packet from `ImmuneBondHandler` tick |

Sounds: existing vanilla/`SRPSounds` only. No new textures — SRP particle sprites only.

## Files touched

- **New:** `network/PacketSrpParticle.java` (+ registration in `InsaneTweaksNetwork.init()`, discriminator 4 — 0/1/3 are taken, 2 is historically skipped), `client/YelloweyeChargeHandler.java` (+ client-side registration in `InsaneTweaksMod.init`).
- **Extended:** `util/SpellCastFeedback` (SRP overloads), `events/ParasiteShroudEventHandler` (tiers, break-on-attack, expiry/break particles), `spells/SpellParasiteShroud` (duration constant 240, armour synergy, tier from potency), `events/ImmuneBondHandler` (particle swap), `entities/EntityPurifyingWave` (`affectBeckons` → `affectParasites` + COTH cleanse), `spells/SpellYelloweyeGland` (cycle removal, always explosive), `entities/ThrallSlotManager` (spawn burst).
- **JSON:** `assets/insanetweaks/spells/yelloweye_gland.json` (chargeup 12→30, cost 120→140, cooldown 70→100).
- **Not touched after all:** `spells/AbstractSrpSummonSpell`, `spells/SpellSummonThrall` (spawn happens in `ThrallSlotManager`), minion entity classes.

## Constraints & conventions

- Java 8 only; existing style; `@SuppressWarnings("null")` where present.
- No changes to `ModSpells` registration, entity tracking IDs, existing packet IDs, or config flags (new packet appends the next discriminator).
- Thrall slot system internals untouched (visuals hook only at spawn site).

## Risks

1. **Parasite tier classification** — RESOLVED in planning: `instanceof EntityPPrimitive` (all primitive-tier parasites share that base class).
2. **Client-only `ParticleSpawner`** — packet handler must follow the sided pattern already used by `PacketOpenSentinelLoot` (`@SideOnly(Side.CLIENT)` static inner `Handler`); `SRPEnumParticle` itself is server-safe (imports only Guava/javax).
3. **Yelloweye rebalance** changes game feel — judged in manual runClient; numbers (chargeup 30, cost, cooldown) are starting points, tunable in JSON.
4. Shroud tier threshold (potency ≥ 1.3) is a starting point, tune during manual test.

## Verification

- `./gradlew build` green per task; no automated tests exist.
- Manual `runClient` checklist (to be detailed in the plan): minion/thrall spawn+despawn particles, shroud tiers vs primitive/advanced parasites, armour-scaled duration, break-on-attack, Yelloweye chargeup feel + every-shot explosive, Pulse debuffs all parasites + cures COTH mobs, bond-active DOT ticks.
- Part 1's deferred Task 9 manual checklist can be run in the same session.

## Out of scope

- Immune Bond mechanics; summon mechanics (visuals only).
- Thrall system internals (separate spec exists).
- New textures/sounds/assets; variable-power charge for Yelloweye (wand mixin).
- Data-driven particle configs.
