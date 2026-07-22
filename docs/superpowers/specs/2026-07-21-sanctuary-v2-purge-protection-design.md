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
per-tick scanning toward event-driven vetoes for better game fluidity — plus richer
diagnostics so the core's work is observable in the log.

## Goals

1. **Purge Fire:** an active sanctuary continuously ignites + damages all parasite entities
   within a capped radius, mirroring the mod's existing "Sentient Aegis" fast-fire, using the
   lowest-resource event-driven approach.
2. **Block-break veto:** parasites cannot break/grief blocks inside the protected region
   (event-driven, no scan, no fuel), complementing the existing infestation veto.
3. **Operational diagnostics:** gated debug logging that answers, at a glance, whether the
   core sees parasites in-zone, vetoed a spawn, vetoed griefing, cleansed a block, and burned
   a parasite.
4. Keep the scan-cleanse for now (it uniquely reverts *pre-existing* infested blocks), with a
   documented path to retire it in favour of a full event-driven model.

Non-goals (this spec): removing the scan-cleanse; the SimWizard "always hostile" fix (tracked
separately); block-*infestation* veto (already shipped in v1); the true static dome renderer
(tracked separately — see Section 5).

## Grounding (verified in code)

- **Aegis fast-fire pattern** (`events/AegisEventHandler.java`): for burning enemies, on
  `LivingUpdateEvent` every 10 ticks it sets `hurtResistantTime = 0` (breaks i-frames) then
  `attackEntityFrom(DamageSource.IN_FIRE, 1.0F)` and re-applies `setFire(2)` for the visual.
  ~2× vanilla fire rate.
- **Parasite detection** (`sanctuary/SanctuarySpawnVetoHandler.isSrpParasite`): walks the
  superclass chain for `com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase`.
  Catches SRP, SRPExtra, and `EntitySimWizard` (extends `EntityInfHuman` → `EntityParasiteBase`).
  Player minions (`EntitySummonedCreature`) and thralls (`EntityCreature`) are NOT parasites →
  never targeted. No friend/foe filter needed.
- **Parasite block-breaking** (`EntityParasiteBase.skillBreakBlocks()`, used by ~20 parasite
  types + `EntityAIBlockLight`): fires the cancelable Forge `LivingDestroyBlockEvent` (via
  `ForgeEventFactory.onEntityDestroyBlock`) per block. A plain `@SubscribeEvent` handler can
  veto it — no mixin required.
  - **Known gap:** `AIDisableBeaconIki` (EntityIki removing a block to disable a beacon) uses
    `World.setBlockToAir` directly and does not fire the event → not caught. Optional follow-up.
- **Cleanse block coverage** (`util/SrpPurificationHelper`): `isSrpInfested` already detects any
  `srparasites:*` block whose path contains `inf`/`infect`/`parasite`, `startsWith("gore")`, or
  contains `remain` (except `infestation_purifier`). So `srparasites:gorepri` (any meta) is
  already detected and reverts to `AIR`; `srparasites:infestedstain` is detected and reverts to
  `minecraft:dirt`. **No cleanse-list change needed** — coverage confirmed sufficient.
- **Particle dome** (`electroblob.wizardry.client.particle.ParticleSphere`): renders scale as
  `scale × age / maxAge` — the sphere inherently **grows from a point to full radius over its
  lifetime**. It is a burst effect and cannot be made truly static (that is the observed
  "pulse"). See Section 5.

## Section 1 — Purge Fire (parasite DoT, event-driven)

- **Where:** a new server-side Forge handler `sanctuary/SanctuaryPurgeFireHandler` on
  `LivingUpdateEvent` (mirrors `AegisEventHandler`). NOT a per-core AABB scan — cost scales with
  entity count (cheap reject for non-parasites) and is ~zero when no parasites are near a dome.
  This is the lowest-resource option (the user's explicit priority).
- **Per living entity (server side):**
  1. early-out if `event.getEntityLiving().world.isRemote` or `!ModConfig.sanctuary.enablePurgeFire`.
  2. early-out if `!SanctuaryRegionHelper.isSrpParasite(e)` (cheap superclass-walk reject).
  3. early-out if `!SanctuaryRegionHelper.isInPurgeRange(e.world, floor(e.posX), floor(e.posZ))`
     — inside any registered region within `min(regionRadius, purgeFireRadiusCap)` horizontally
     (cylinder). Cheap iteration over the small `SanctuaryWorldData` region list.
  4. if `e.ticksExisted % ModConfig.sanctuary.purgeFireInterval == 0`:
     `e.hurtResistantTime = 0; e.attackEntityFrom(DamageSource.IN_FIRE, purgeFireDamage);`
  5. if `!e.isBurning()`: `e.setFire(2);`
- **No fuel cost.**
- **Detection reuse:** promote `isSrpParasite` from `SanctuarySpawnVetoHandler` (private static)
  to `SanctuaryRegionHelper.isSrpParasite(Entity)`; the spawn veto delegates to it.
- **New helper:** `SanctuaryRegionHelper.isInPurgeRange(World, x, z)` backed by a cap-aware
  cylinder test over `SanctuaryWorldData` regions (`min(region.radius, purgeFireRadiusCap)`).
- **Registration:** `InsaneTweaksMod.init`, under `modules.enableSanctuary`.
- **Caveats (documented, accepted):** Aegis-parity damage (`1.0`/0.5 s ≈ 2 HP/s) is brisk for
  small parasites, slow for high-HP ones — `purgeFireDamage` is configurable, %-max-HP scaling
  is a deferred future option. Fire-immune parasites (rare) are naturally skipped.

## Section 2 — Block-break veto (event-driven, no mixin)

- **New handler** `sanctuary/SanctuaryBlockBreakVetoHandler` (plain Forge subscriber, lives
  beside the other sanctuary handlers — NOT in `mixins/srp`):
  ```java
  @SubscribeEvent
  public void onDestroyBlock(LivingDestroyBlockEvent event) {
      if (!ModConfig.sanctuary.vetoBlockBreak) return;
      net.minecraft.entity.EntityLivingBase e = event.getEntityLiving();
      if (e == null || e.world.isRemote || !SanctuaryRegionHelper.isSrpParasite(e)) return;
      if (SanctuaryRegionHelper.isProtected(e.world, event.getPos())) {
          event.setCanceled(true);
      }
  }
  ```
- **Registration:** `InsaneTweaksMod.init`, under `modules.enableSanctuary`, beside `SanctuarySpawnVetoHandler`.
- **Config:** `vetoBlockBreak` (default true) — read live in the handler, so **no `@RequiresMcRestart`**.
- **Relationship:** v1 `MixinBeckonBlockInfestation` vetoes block *infestation*; this adds veto of
  block *destruction*. Together the region is protected from parasite block modification going forward.
- **Scan-cleanse:** unchanged — still the only thing that reverts blocks infested *before* the
  sanctuary existed. Retiring it (event-driven pivot: revert-on-change + this veto, with a one-shot
  "purge sweep" for pre-existing cleanup) is recorded as the fallback direction if scanning proves costly.
- **Known gap (optional follow-up):** `AIDisableBeaconIki` bypasses `LivingDestroyBlockEvent`;
  veto later via a targeted mixin `@Redirect` on its `World.setBlockToAir` call, or coarsely via
  `EntityMobGriefingEvent` for EntityIki in-region. Low priority.

## Section 3 — Config additions (`SanctuaryCategory`)

- `enablePurgeFire` (boolean, default `true`, live) — master switch for Section 1.
- `purgeFireDamage` (float, default `1.0`, `RangeDouble` 0–100, live) — fire damage per cadence.
- `purgeFireInterval` (int, default `10`, `RangeInt` 1–200, live) — ticks between burns.
- `purgeFireRadiusCap` (int, default `128`, `RangeInt` 1–128, live) — DoT radius hard cap.
- `vetoBlockBreak` (boolean, default `true`, live) — Section 2 veto.

Purge Fire and the veto are gated on the sanctuary being active (`tier >= 1` / region registered)
and on `modules.enableSanctuary` (handler registration). `debugLogging` (existing) gates Section 4.

## Section 4 — Operational debug logging (gated on existing `sanctuary.debugLogging`)

Goal: at a glance answer "is it working?" without spam. **All lines gated on
`ModConfig.sanctuary.debugLogging` and throttled** so high-frequency events can't flood the log.

- **New util** `sanctuary/SanctuaryDebug`:
  - `log(String category, String message)` — logs `[InsaneTweaks] Sanctuary/<category>: <message>`
    via `InsaneTweaksMod.LOGGER.info`, but at most once per `SanctuaryDebug.THROTTLE_TICKS`
    (default 20 = 1 s) **per category** (a `Map<String, Long>` of last-log world-time; use a
    passed-in `world.getTotalWorldTime()` since `Date`/`System` time is not needed). No-op if
    `!debugLogging`.
- **Emitters (each gated + throttled via the util):**
  1. **parasites-in-zone** — the Core TE, every `pyramidRevalidateInterval` ticks, ONLY when
     `debugLogging`, does a one-off debug scan (`getEntitiesWithinAABB` parasite count in its
     `min(radius,128)` cylinder) and logs `parasitesInZone=N (tier=T, purgeFire=on/off)`. The
     scan runs only under debugLogging → no production cost.
  2. **spawn-vetoed** — `SanctuarySpawnVetoHandler`, on DENY: `spawn vetoed: <entityName> @(x,y,z)`.
  3. **grief-vetoed** — `SanctuaryBlockBreakVetoHandler`, on cancel: `grief vetoed: <entityName> break @(x,y,z)`.
  4. **cleansed** — the cleanse path (`runCleanse`/`SanctuaryCleanseHelper`), on a successful
     revert: `cleansed @(x,y,z): <srpId> -> <vanillaId>`.
  5. **purge-fire** — `SanctuaryPurgeFireHandler`, on applying damage: `purge-fire: <entityName>
     hp=<hp> @(x,y,z)`.
- Throttling means each category prints a representative sample ~once/second while active,
  enough to confirm "yes it's happening" without per-event spam. The DEv 1.2 `debug.log` monitor
  picks these up via the existing `[InsaneTweaks]` marker.

## Section 5 — Dome particle: interim disable now, static renderer later

- **Now (interim, this spec):** disable the EBW SPHERE emission (it inherently grows/pulses and
  cannot be static — see Grounding). `clientParticleTick` becomes a no-op / early return so the
  dome stops spamming during testing. Keep the `particleBorder` config field for the future renderer.
- **Later (separate task, NOT this spec):** a true static, dim translucent dome renderer (custom
  `Particle` or a TESR on the core drawing a fixed-radius sphere). Tracked as its own task.

## Components touched

| File | Change |
|------|--------|
| `sanctuary/SanctuaryRegionHelper.java` | add shared `isSrpParasite(Entity)` + `isInPurgeRange(World,x,z)` |
| `sanctuary/SanctuarySpawnVetoHandler.java` | delegate to shared `isSrpParasite`; add gated `spawn vetoed` debug log |
| `sanctuary/SanctuaryPurgeFireHandler.java` (new) | `LivingUpdateEvent` fast-fire on in-range parasites + gated `purge-fire` debug log |
| `sanctuary/SanctuaryBlockBreakVetoHandler.java` (new) | `LivingDestroyBlockEvent` veto + gated `grief vetoed` debug log |
| `sanctuary/SanctuaryDebug.java` (new) | throttled, category-keyed gated logger |
| `sanctuary/TileEntitySanctuaryCore.java` | disable dome particle (interim); debug-only parasites-in-zone summary; `cleansed` debug log in cleanse path |
| `config/categories/SanctuaryCategory.java` | 5 new config fields (Section 3) |
| `InsaneTweaksMod.java` | register the two new handlers under `enableSanctuary` |

No mixin changes. No lang/GUI changes. `SanctuaryWorldData` may gain a cap-aware cylinder test
(or the cap logic lives in `SanctuaryRegionHelper.isInPurgeRange`).

## Verification (end-to-end, no unit tests)

1. `./gradlew build`, swap jar into DEv 1.2 (remove old-version jar). Enable `debugLogging`.
2. Active dome + parasites inside → they ignite and take steady damage; parasites outside
   `min(radius,128)` untouched; parasites walking out stop burning. `debug.log` shows throttled
   `purge-fire:` and `parasitesInZone=N`.
3. Player minions / thralls inside the dome are NOT burned.
4. A block-breaking parasite inside the region cannot destroy blocks; `debug.log` shows
   `grief vetoed:`; the same parasite outside breaks blocks normally.
5. Natural parasite spawn attempt in-zone denied; `debug.log` shows `spawn vetoed:`.
6. An infested block in-zone reverts; `debug.log` shows `cleansed @...: srp -> vanilla`.
7. Toggle `enablePurgeFire` / `vetoBlockBreak` / `debugLogging` live → effects and logs start/stop
   without restart. `purgeFireRadiusCap` bounds the DoT at 128 even when protection radius is larger.
8. Dome particle no longer emitted (no pulse spam).
