# Sanctuary Dome v2 — Purge Fire + Block Protection (Design Spec)

**Date:** 2026-07-21
**Branch:** `feat/sanctuary-dome`
**Follows:** `2026-07-19-sanctuary-dome-design.md` (v1 build), `2026-07-20-sanctuary-legibility-design.md` (legibility)

## Problem / intent

Play-test feedback: the Sanctuary Core felt inert against parasites. The v1 sanctuary is a
**passive ward** — it vetoes new parasite spawns, vetoes Beckon block-infestation, and
scan-cleanses infested blocks (fuel-gated). It does **not** attack existing parasite
entities, and it does not stop parasites from **breaking/griefing terrain** (a separate SRP
mechanism from infestation). The user wants the dome to (a) actively burn parasites inside
it, and (b) stop parasites from destroying blocks in the zone — moving protection from
per-tick scanning toward event-driven vetoes for better game fluidity.

## Goals

1. **Purge Fire:** an active sanctuary continuously ignites + damages all parasite entities
   within a capped radius, mirroring the mod's existing "Sentient Aegis" fast-fire.
2. **Block-break veto:** parasites cannot break/grief blocks inside the protected region
   (event-driven, no scan, no fuel), complementing the existing infestation veto.
3. Keep the scan-cleanse for now (it uniquely reverts *pre-existing* infested blocks), with
   a documented path to retire it in favour of a full event-driven model.

Non-goals (this spec): removing the scan-cleanse; the SimWizard "always hostile" fix
(tracked separately); block-*infestation* veto (already shipped in v1).

## Grounding (verified in code)

- **Aegis fast-fire pattern** (`events/AegisEventHandler.java`): for burning enemies, every
  10 ticks it sets `hurtResistantTime = 0` (breaks i-frames) then
  `attackEntityFrom(DamageSource.IN_FIRE, 1.0F)` and re-applies `setFire(2)` for the visual.
  ~2× vanilla fire rate. This is the pattern Purge Fire reuses.
- **Parasite detection** (`sanctuary/SanctuarySpawnVetoHandler.isSrpParasite`): walks the
  superclass chain for `com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase`.
  Catches SRP, SRPExtra, and `EntitySimWizard` (which extends `EntityInfHuman` →
  `EntityParasiteBase`). Player minions (`EntitySummonedCreature`) and thralls
  (`EntityCreature`) are NOT parasites → never targeted. No friend/foe filter needed.
- **Parasite block-breaking** (`EntityParasiteBase.skillBreakBlocks()`, used by ~20 parasite
  types + `EntityAIBlockLight`): fires the cancelable Forge
  `LivingDestroyBlockEvent` (via `ForgeEventFactory.onEntityDestroyBlock`) per block. A plain
  `@SubscribeEvent` handler can veto it — **no mixin required**.
- **Known gap:** `AIDisableBeaconIki` (EntityIki removing a block to disable a beacon) uses
  `World.setBlockToAir` directly and does **not** fire `LivingDestroyBlockEvent`, so the
  handler won't catch that one narrow case. Documented as optional follow-up.

## Section 1 — Purge Fire (parasite DoT)

- **Where:** server side, driven by the Sanctuary Core TE tick (already server-ticking).
- **Cadence:** every `purgeFireInterval` ticks (default 10 = 0.5 s), when `tier >= 1` and
  `enablePurgeFire`.
- **Target set:** `world.getEntitiesWithinAABB(EntityLivingBase.class, box, predicate)` where
  `box` is the axis-aligned cube of half-extent `min(effectiveRadius, purgeFireRadiusCap)`
  centred on the core, and `predicate` = `SanctuaryRegionHelper.isSrpParasite(e)` AND the
  entity's horizontal distance to the core ≤ that capped radius (cylinder, matching the
  protection region shape) AND `e.isEntityAlive()`.
- **Effect per target:** `e.hurtResistantTime = 0`; `e.attackEntityFrom(DamageSource.IN_FIRE,
  purgeFireDamage)`; `if (!e.isBurning()) e.setFire(2)`. No burn-map bookkeeping — re-scanning
  the region each cadence *is* the "still in range" check; a parasite that leaves simply stops
  being re-ignited and its fire fades.
- **No fuel cost.**
- **Detection reuse:** promote `isSrpParasite` from `SanctuarySpawnVetoHandler` (private
  static) to a shared helper (`SanctuaryRegionHelper.isSrpParasite(Entity)`) so both the spawn
  veto and Purge Fire use one implementation.
- **Caveats (documented, accepted):**
  - Aegis-parity damage (`1.0`/0.5 s ≈ 2 HP/s) is brisk for small parasites but slow for
    high-HP ones. `purgeFireDamage` is configurable; a %-max-HP scaling mode is a deferred
    future option, not built now.
  - Fire-immune parasites (rare) take no fire damage and won't ignite — an accepted natural
    exception, consistent with how Aegis fire behaves.
  - AABB entity query over a up-to-128 half-extent cube every 10 ticks touches many chunks'
    entity lists; entity lists are small and the cadence is 10 ticks, so cost is modest. The
    128 cap bounds worst case.

## Section 2 — Block-break veto (event-driven, no mixin)

- **New handler** `sanctuary/SanctuaryBlockBreakVetoHandler` (plain Forge event subscriber,
  lives beside the other sanctuary handlers — NOT in `mixins/srp`):
  ```
  @SubscribeEvent
  public void onDestroyBlock(LivingDestroyBlockEvent event) {
      if (!ModConfig.sanctuary.vetoBlockBreak) return;
      Entity e = event.getEntity();
      if (e == null || !SanctuaryRegionHelper.isSrpParasite(e)) return;
      if (SanctuaryRegionHelper.isProtected(e.world, event.getPos())) {
          event.setCanceled(true);
      }
  }
  ```
- **Registration:** in `InsaneTweaksMod.init`, under the existing `modules.enableSanctuary`
  gate, alongside `SanctuarySpawnVetoHandler`.
- **Config:** `vetoBlockBreak` (default true) — read live inside the handler, so **no
  `@RequiresMcRestart`** (it's an event handler, not a mixin gate).
- **Relationship to existing protection:** the v1 `MixinBeckonBlockInfestation` already vetoes
  block *infestation*; this adds veto of block *destruction*. Together the region is protected
  from parasite block modification going forward.
- **Scan-cleanse:** unchanged for now — it remains the only thing that reverts blocks infested
  *before* the sanctuary existed. Retiring it (full event-driven pivot: revert-on-change +
  this veto) is recorded as the fallback direction if scanning proves too costly; a one-shot
  "purge sweep" on demand would replace continuous scanning for pre-existing cleanup.
- **Known gap (optional follow-up):** `AIDisableBeaconIki` bypasses `LivingDestroyBlockEvent`.
  If it matters in practice, veto via a targeted mixin `@Redirect` on the
  `World.setBlockToAir` call in `AIDisableBeaconIki#updateTask`, or coarsely via
  `EntityMobGriefingEvent` for EntityIki in-region. Low priority.

## Section 3 — Config additions (`SanctuaryCategory`)

- `enablePurgeFire` (boolean, default `true`, live) — master switch for Section 1.
- `purgeFireDamage` (float, default `1.0`, `RangeDouble` 0–100, live) — fire damage per cadence.
- `purgeFireInterval` (int, default `10`, `RangeInt` 1–200, live) — ticks between burns.
- `purgeFireRadiusCap` (int, default `128`, `RangeInt` 1–128, live) — DoT radius hard cap.
- `vetoBlockBreak` (boolean, default `true`, live) — Section 2 veto.

Purge Fire and the veto are both gated on the sanctuary being active (`tier >= 1`) and on
`modules.enableSanctuary` (handler registration).

## Components touched

| File | Change |
|------|--------|
| `sanctuary/SanctuaryRegionHelper.java` | add shared `isSrpParasite(Entity)` (moved from spawn-veto handler) |
| `sanctuary/SanctuarySpawnVetoHandler.java` | delegate to the shared `isSrpParasite` |
| `sanctuary/TileEntitySanctuaryCore.java` | Purge Fire tick (server): cadence + AABB scan + fast-fire |
| `sanctuary/SanctuaryBlockBreakVetoHandler.java` (new) | `LivingDestroyBlockEvent` veto |
| `config/categories/SanctuaryCategory.java` | 5 new config fields |
| `InsaneTweaksMod.java` | register the block-break veto handler under `enableSanctuary` |

No mixin changes. No lang/GUI changes.

## Verification (end-to-end, no unit tests)

1. `./gradlew build`, swap jar into DEv 1.2 (remove old-version jar).
2. Active dome + parasites inside → they ignite and take steady damage; parasites outside
   `min(radius,128)` are untouched; parasites walking out stop burning.
3. Player minions / thralls / a friendly-standing entity inside the dome are **not** burned.
4. A block-breaking parasite (e.g. a breaker type) inside the region cannot destroy blocks;
   the same parasite outside the region breaks blocks normally.
5. Toggle `enablePurgeFire` / `vetoBlockBreak` live in config → effects start/stop without
   restart.
6. `purgeFireRadiusCap` capped at 128 even when `effectiveRadius` (protection) is larger.
