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
1. **Tiers by potency:** tier 1 (base) hides from primitive parasites only; tier 2 (potency ≥ ~1.3) also from advanced ones. Parasite classification (primitive vs adapted/evolved) to be pinned down in the plan from decompiled SRP class hierarchy; fallback if no clean hierarchy exists: config-driven class list.
2. **Wizard-armour synergy:** duration +~15% per worn wizard-armour piece matching the spell's element, via `ItemWizardArmour.getMatchingArmourCount(player, element)` (EBW 4.3.19).
3. **Break on attack:** the player attacking anything while shrouded removes the shroud early (handler on `AttackEntityEvent`/`LivingHurtEvent` checking `hasShroud`); base duration rises 160 → ~240 ticks in exchange (`BASE_DURATION_TICKS` constant in `SpellParasiteShroud.java`).

**Purifying Pulse — extend the existing wave.**
Block purification already exists (`EntityPurifyingWave.purifyRing` + `SrpPurificationHelper`); Beckons already get dmg 6 + glow + weakness/slowness. New:
1. Generalize `affectBeckons` → `affectParasites`: **all** SRP parasites in the ring get a "searing" debuff (weakness + slowness + short magic DoT); Beckons keep their current harsher treatment.
2. **COTH cleanse:** non-parasite mobs in range get their COTH infection removed — set `srpcothimmunity = 0` (SRP 1.10.7 semantics: tag value 0 = immune/cleared; see `SummonInfectionSafetyHelper`). The spell becomes a real cure for infected animals/villagers.

### 3. Visuals map (silent events only)

| Event (currently silent) | Effect | Path |
|---|---|---|
| Minion spawn (shared loop in `AbstractSrpSummonSpell`) | short FLASH in family colour (blood red for parasitic, violet for Wizard) + a few GCLOUD at ground level | `PacketSrpParticle` from the server loop |
| Thrall spawn (`SpellSummonThrall`) | as above, darker red | same |
| Minion despawn at end of lifetime | small DOT dissolve at the vanish point | packet (hook in minion base entity or handler) |
| Shroud: natural expiry | fading grey GCLOUD around the player | packet from `ParasiteShroudEventHandler` |
| Shroud: broken by attacking (new) | sharp red FLASH + "break" sound | same |
| Yelloweye: charging | yellow-green DOT converging on the hand during chargeup | client-side in `cast()` / EBW idiom (cast-time only) |
| Pulse: parasite debuffed (new) | small golden FLASH on the target | from `affectParasites` |
| Pulse: COTH cleansed from a mob (new) | white-gold burst + cure sound | same |
| Immune Bond: bond active | subtle violet DOT on the bonded mob every ~2 s | packet from `ImmuneBondHandler` tick |

Sounds: existing vanilla/`SRPSounds` only. No new textures — SRP particle sprites only.

## Files touched

- **New:** `network/PacketSrpParticle.java` (+ registration in `InsaneTweaksNetwork.init()`).
- **Extended:** `util/SpellCastFeedback`, `spells/AbstractSrpSummonSpell`, `events/ParasiteShroudEventHandler`, `events/ImmuneBondHandler`, `entities/EntityPurifyingWave`, `spells/SpellYelloweyeGland`, `spells/SpellParasiteShroud` (duration constant, armour synergy, tier from potency at cast time), `spells/SpellSummonThrall`.
- **JSON:** `assets/insanetweaks/spells/yelloweye_gland.json` (chargeup/cost/cooldown).

## Constraints & conventions

- Java 8 only; existing style; `@SuppressWarnings("null")` where present.
- No changes to `ModSpells` registration, entity tracking IDs, existing packet IDs, or config flags (new packet appends the next discriminator).
- Thrall slot system internals untouched (visuals hook only at spawn site).

## Risks

1. **Parasite tier classification** for Shroud tiers — verify in decompiled SRP 1.10.7 whether primitive/adapted/evolved is expressed as a class hierarchy; fallback: config list of class names.
2. **Client-only `ParticleSpawner`** — packet handler must follow SRP's own sided pattern or servers crash.
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
