# WorseHurtTimer i-frame compatibility layer (srpwizcore)

**Date:** 2026-08-05
**Mod:** `srpwizcore`
**Status:** design approved, not implemented
**Pack-side audit this came from:** instance `notes/bb_trinket_audit_amuletcross_brokenheart_2026-08-05.md`

## Problem

WorseHurtTimer (`betterhurttimer`, jar `WorseHurtTimer-1.12.2-1.5.0.3.jar`) replaces vanilla
invincibility frames wholesale. Anything in the pack that grants "longer invincibility" by the
vanilla route is therefore silently degraded or dead.

Two concrete defects, both verified from bytecode:

**1. Vanilla i-frames are zeroed.** `HurtTimeMixin` injects into
`EntityLivingBase.attackEntityFrom` at `@At(value="FIELD", target="EntityLivingBase;hurtTime:I",
shift=AFTER)` and assigns `this.hurtResistantTime = BHTConfig.CONFIG.damageFrames.hurtResistantTime`.
The pack config sets that to `0`. Vanilla's own `hurtResistantTime = maxHurtResistantTime`,
executed a few instructions earlier in the same block, is therefore always clobbered.

`maxHurtResistantTime` survives as an input in exactly one place — `Events.getHurtTime`, and only
in the branch where the attacker cannot swing:

```java
public static int getHurtTime(Entity target, Entity attacker) {
    double threshold = getThreshold(attacker);
    if (attacker instanceof EntityLivingBase && canSwing((EntityLivingBase) attacker))
        return (int) (getCoolPeriod((EntityLivingBase) attacker) * threshold);
    return (int) (getHurtResistantTime(target) * getAttackSpeed(attacker) * threshold);
}
```

`canSwing(attacker)` is true when the attacker's main-hand item carries a `generic.attackSpeed`
modifier. So an armed attacker takes the first branch and the victim's field is ignored entirely.

**Measured correction (2026-08-05).** In this pack `canSwing` never returns true — 314 `false`,
zero `true` across a full test session, a sword-wielding zombie included — so the attack-speed
branch never executes and every melee cooldown, armed or not, comes from
`getHurtResistantTime(target)`. The reflection lookup behind `canSwing` succeeds (WHT logs
`No try catch error` on every call), so this is a real attribute-lookup result rather than a
swallowed exception; the cause is unidentified and most likely another mod rewriting weapon
attributes. The "armed attackers are a hole" framing below is therefore correct as a reading of
WorseHurtTimer's code but does not describe this pack's runtime. It changes nothing about the
design: mixin 1 injects at RETURN and covers whichever branch runs.

Net effect on `bountifulbaubles:amuletcross` (its whole implementation is
`maxHurtResistantTime = 36` on equip, `20` on unequip): it works only against bare-handed melee
(19 → 34 ticks), does nothing against armed attackers, and does nothing against arrows, magic,
fire, explosions or environment — those run off the `S:damageSource` table instead.

**2. Per-source i-frames for unlisted sources are seeded from a random entity.**
`BHTAPI.get(EntityLivingBase, DamageSource)` does:

```java
DAMAGE_SOURCE_INFO_MAP.computeIfAbsent(source.getDamageType(),
        HURT_SOURCE_INFO_FUNCTION.apply(entity))
```

`DAMAGE_SOURCE_INFO_MAP` is a **global static** map keyed by source name alone, and the seeding
function builds `new HurtSourceInfo(name, false, entity.maxHurtResistantTime)`. For any damage
source absent from `S:damageSource` — `explosion`, `drown`, `onFire`, `sting`, and every modded
source from SRParasites, Electroblob's Wizardry and CQR — the wait time is fixed forever by
whichever entity that source happened to hit first in the session. This is an upstream bug; it
also makes any per-source multiplier we add non-deterministic unless we fix it.

## Goals

- Any pack mechanic that wants longer i-frames gets a single, documented way to ask for them,
  covering every WHT path.
- Cross Necklace becomes the first consumer and delivers what its tooltip claims.
- The base i-frame values WHT computes become deterministic, so later fixes build on solid ground.
- No balance regression: with no multipliers registered, numbers must match current behaviour.

## Non-goals

- Broken Heart (`bountifulbaubles:trinketbrokenheart`) — separate spec; different root cause
  (FirstAid cancels `LivingHurtEvent`, so `LivingDamageEvent` never fires for players).
- Auditing SoManyEnchantments' i-frame enchantments (Evasion, Parry, Empowered Defence and five
  others read `maxHurtResistantTime` and are likely degraded the same way). Separate audit; it
  gets easier once this layer exists.
- Rewriting WHT's damage-stacking or knockback behaviour.

## Architecture

```
api/WhtIFrames.java                          public multiplier registry
whtcompat/CrossNecklaceProvider.java         first provider
config/categories/WhtCompatCategory.java
mixins/betterhurttimer/                      four mixins
resources/mixins.srpwizcore.betterhurttimer.json
core/SrpWizCoreLateBooter.java               += Loader.isModLoaded("betterhurttimer")
```

Separation of concerns: the mixins intercept WHT's numbers and multiply, and know nothing about
baubles. `WhtIFrames` knows nothing about WHT. The provider knows nothing about mixins. Each part
is understandable and testable on its own.

### `WhtIFrames`

```java
public final class WhtIFrames {
    public interface Provider { float multiplier(EntityLivingBase victim); }
    public static void register(String id, Provider provider);
    public static float getMultiplier(EntityLivingBase victim);
}
```

- Contributions **multiply**: two providers at ×1.8 and ×1.2 yield ×2.16.
- Result is clamped to `[1.0, maxMultiplier]`.
- First line of `getMultiplier` returns `1.0f` when the provider list is empty. With no Bountiful
  Baubles installed, or the module disabled, the cost is one comparison. (Cheapest guard first —
  the pack's standing rule for anything on a hot path.)
- Providers are registered once during `FMLInitializationEvent`; the list is never mutated
  afterwards, so no synchronisation is needed on the read path.
- Server-side only. All four call sites are server-side already.

### Mixins

All four live in `com.spege.srpwizcore.mixins.betterhurttimer` and self-gate on
`SrpWizCoreConfig.whtCompat.enabled`.

| # | Class | Target | Behaviour |
|---|---|---|---|
| 1 | `MixinBhtEventsHurtTime` | `Events.getHurtTime(Entity,Entity)`, `@Inject(at=@At("RETURN"), cancellable=true)` | returns `(int)(original * getMultiplier(target))` |
| 2 | `MixinBhtEventsResistantTime` | `Events.getHurtResistantTime(Entity)` | for `EntityPlayer` returns `whtCompat.baseIFrameTicks`; for every other entity returns `entity.maxHurtResistantTime` unchanged |
| 3 | `MixinBhtEventsSourceFrames` | `Events.onAttackEntityFromPre(PreLivingAttackEvent)`, `@Redirect` on the `HurtSourceData.trigger()` call | calls original `trigger()`, then `data.tick = (int)(data.tick * getMultiplier(event.getEntityLiving()))` |
| 4 | `MixinBhtApiSourceSeed` | `BHTAPI.get(EntityLivingBase,DamageSource)`, `@Redirect` on `Object2ObjectMap.computeIfAbsent` | seeds absent entries with `new HurtSourceInfo(name, false, whtCompat.baseIFrameTicks)` |

Notes that matter during implementation:

- **Mixin 1 needs no attacker/victim check.** `getHurtTime` is also called from
  `Events.lambda$onPlayerAttack$3` as `(mobTarget, player)`. Because the multiplier is computed
  from the *target*, a player attacking a mob gets no bonus. The correct behaviour falls out of
  the argument order — do not add a special case.
- **Mixin 2 is what prevents double counting.** Bountiful Baubles keeps writing
  `maxHurtResistantTime = 36` (from `ItemAmuletCross.onEquipped` and, on every incoming attack,
  from `bountifulbaubles.event.EventHandler.onDamage(LivingAttackEvent)`). Without mixin 2 the
  unarmed-attacker branch would compute 36 × 1.8 = 64 ticks while every other path computed
  20 × 1.8 = 36. With mixin 2, WHT stops reading the field for players and all player paths share
  one base.
- **Mixin 2 must be restricted to players.** A scan of all 269 jars in the instance found
  `maxHurtResistantTime` referenced by 13 mods, of which exactly two write it:

  | mod | writes | reads |
  |---|---|---|
  | Bountiful Baubles | 4 | 1 |
  | BabyMobs | 1 | 0 |
  | SoManyEnchantments | 0 | 11 |
  | Ancient Spellcraft | 0 | 3 |
  | Electroblob's Wizardry, MmmMmmMmmMmm, PotionCore | 0 | 2 each |
  | Ice and Fire, ScalingHealth, Spartan Weaponry, enigmatic-addons-legacy, SWParasites | 0 | 1 each |

  `BabyMobs.EntityBabyWitherSkeleton` sets **its own** field to `50` in its constructor, which today
  buys that mob roughly 48 ticks of melee cooldown as a victim instead of 19. A blanket override
  would silently nerf it. Restricting mixin 2 to `EntityPlayer` keeps every mob's self-declared
  value intact while still cutting the amulet's double count, because Bountiful Baubles is the only
  writer that touches a *player's* field.
- **Do not neutralise the field itself.** All the reads above stay valid — mixin 2 only stops
  **WHT** from consuming the field for players. SoManyEnchantments' i-frame enchantments in
  particular keep reading it exactly as they do now, and Bountiful Baubles' write becomes a
  harmless dead store.
- **Mixin 4 targets the `computeIfAbsent` call, not the seeding lambda.** Targeting
  `BHTAPI.lambda$null$0` by name would work but binds us to a synthetic method name.

### `CrossNecklaceProvider`

```java
if (!(victim instanceof EntityPlayer)) return 1f;
if (BaublesApi.isBaubleEquipped((EntityPlayer) victim, amuletCross) == -1) return 1f;
return SrpWizCoreConfig.whtCompat.crossNecklaceMultiplier;
```

- The item is resolved once via
  `Item.REGISTRY.getObject(new ResourceLocation("bountifulbaubles", "amuletcross"))` and cached in
  a field — no compile-time dependency on Bountiful Baubles, no jar in `libs/`.
- Registered only when `Loader.isModLoaded("bountifulbaubles")` and the resolved item is non-null.
- No result cache. `isBaubleEquipped` is a loop of `getItem()` comparisons over roughly twenty
  slots, and it runs per incoming hit, not per tick. If a flare profile ever shows it, add a
  `WeakHashMap` cache invalidated on baubles change — not before.
- Because the effect no longer depends on an entity field, it is immune to anything that
  overwrites `maxHurtResistantTime`, and to the field not being saved to NBT.

## Config

New category `whtCompat` in `SrpWizCoreConfig`:

```
B: enabled                  = true    master switch; off leaves WHT byte-for-byte as it is today
I: baseIFrameTicks          = 20      base used by mixins 2 and 4
D: maxMultiplier            = 3.0     clamp on the product of all providers
D: crossNecklaceMultiplier  = 1.8     1.0 disables the amulet's effect
```

Mixins are queued whenever `betterhurttimer` is loaded and gate on `enabled` inside the handler.
Gating the queue on a config value would mean reading it before Forge has injected it — the same
reasoning already documented for the Ice and Fire module in `SrpWizCoreLateBooter`.

`baseIFrameTicks = 20` is chosen so that behaviour with no providers registered is identical to
today: an unarmed mob hitting a player still faces 20 × 0.96 × 1.0 = 19 ticks of cooldown.

## Expected numbers after the change

`getAttackSpeed(attacker)` is `1.2 - 0.06 × attackSpeed`; mob threshold is `1.0`.

| Situation | today | after, no amulet | after, with amulet (×1.8) |
|---|---|---|---|
| bare-handed mob melee | 19 ticks | 19 ticks | 34 ticks |
| zombie with iron sword | 19 ticks | 19 ticks | 34 ticks |
| arrow (`^arrow$:true:10`) | 10 ticks | 10 ticks | 18 ticks |
| source outside `S:damageSource` | whatever entity was hit first | 20 ticks | 36 ticks |

The sword row is not a typo: with the attack-speed branch dead, an armed zombie produces exactly
the same numbers as a bare-handed one. All four rows were confirmed in-game on 2026-08-05.

## Edge cases

- **No `betterhurttimer`** — the mixin config is never queued; `WhtIFrames` exists and nothing
  calls it.
- **No `bountifulbaubles`** — the provider is not registered; `getMultiplier` short-circuits to 1.0.
- **`enabled = false`** — all four mixins return WHT's original values.
- **Victim is not a player** — `CrossNecklaceProvider` returns 1.0 immediately and mixin 2 hands
  back the entity's own `maxHurtResistantTime`, so mobs behave exactly as they do today unless a
  future provider says otherwise.

None of these needs a separate code path beyond one `if`.

## Verification

- **No unit tests.** `CLAUDE.md` records that this workspace has no test suite and verifies through
  build → jar swap → log inspection. Adding a JUnit source set for one class is not worth breaking
  that convention, so `WhtIFrames`' contract — product of several providers, clamping at
  `maxMultiplier`, empty list returning exactly `1.0f`, a provider below 1.0 unable to drag the
  result under 1.0 — is verified by the measured numbers below rather than by assertions.
- **Runtime, melee:** set `B:doLogging=true` in the instance's `betterhurttimer.cfg`. WHT then logs
  `Threshold is {}` and `ticksSinceLastHurt: {}` per attack. Compare with and without the amulet,
  against one bare-handed and one armed mob, against the table above. Turn logging back off
  afterwards — it is per-attack and noisy.
- **Runtime, per-source:** stand in fire with and without the amulet and count the tick gap between
  hits in `debug.log`.
- **Runtime, unlisted source:** confirm a source outside `S:damageSource` gives 20 ticks on a fresh
  world regardless of what was hit first.
- **Runtime, mob regression:** punch a BabyMobs baby wither skeleton bare-handed with logging on.
  `ticksSinceLastHurt` must stay near 48, not drop to 19 — that is the check that mixin 2 really is
  player-only.
- **Sanity:** `logs/cleanmix.log` must contain an `APPLY` line for all four mixins. A missing line
  means the injection did not land.

## Risks

Binding to WHT internals (`Events`, `BHTAPI`, `HurtSourceInfo$HurtSourceData`). Mitigated by
`"required": false` on the mixin config, matching `mixins.srpwizcore.cqr.json`, while keeping
`"injectors": { "defaultRequire": 1 }` so a signature change fails loudly at startup instead of
silently no-opping. WorseHurtTimer has not been updated since 2025-05-04, so the practical risk is
low, and a loud failure is the outcome we want if that changes.

Second risk: `baseIFrameTicks` becomes a single knob affecting both the melee branch and unlisted
sources. That is intentional — it is the determinism the layer exists to provide — but it means
changing it moves two things at once. Documented in the config comment.
