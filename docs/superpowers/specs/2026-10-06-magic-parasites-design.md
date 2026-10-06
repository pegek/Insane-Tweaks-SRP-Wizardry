# Magic Parasites — design (insanetweaks 1.22.0)

Status: approved in brainstorming 2026-10-06, awaiting spec review.
Owner mod: **insanetweaks** (content). All code, lang and player-facing text in English.

## 1. Why

The pack mixes Electroblob's Wizardry / Ancient Spellcraft with SRParasites, but no parasite
shows that mix in combat — they only differ by tier. Three purple, magic-touched variants of
existing primitive parasites give the magic side its own threat, and become the enemy roster for
two later features: the **anchor** (a one-shot spawner block holding a petrified mob) and the
first **generated structure** (3x3 imbuement altar showcase).

## 2. Scope

In scope:

- Three new entities, each a subclass of an SRP primitive parasite, with their own abilities.
- Tint + glow rendering on top of SRP's own model, renderer and skin variants.
- Reduced base drops plus a bonus loot table with this mod's magic items.
- Config, lang, spawn eggs, tests for the pure logic.

Out of scope (each gets its own spec later):

- The anchor block / petrified one-shot spawner (likely reusing EBW's petrification look).
- The generated structure.
- Using the variants in Corrupted Sapling waves.
- Adapted-tier variants.
- Natural spawning of any kind. In this version they appear only via spawn egg and `/summon`.

## 3. Roster

| Registry id | Display name | Base (SRP class / id) | Role | Tracking id |
|---|---|---|---|---|
| `insanetweaks:magic_longarms` | Arcane Blinker | `EntityShyco` / `pri_longarms` | Mobile striker | 121 |
| `insanetweaks:magic_manducater` | Arcane Warden | `EntityHull` / `pri_manducater` | Tank / support | 122 |
| `insanetweaks:magic_summoner` | Arcane Conjurer | `EntityCanra` / `pri_summoner` | Summoner / support | 123 |

SRP base stats (SRPAttributes, SRP config multipliers in the pack are all 1.0):
longarms 45 HP / 9 armor / 15 dmg; manducater 30 HP / 4 armor / 12 dmg; summoner 40 HP /
4 armor / 15 dmg.

## 4. Architecture

SRParasites is a `required-after` dependency of insanetweaks, so the entity classes may name SRP
types directly (unlike optional dependencies — no isolation class needed). Registration follows
the existing pattern in `InsaneTweaksMod.init` with hardcoded tracking ids 121–123 and purple
spawn-egg colours.

Package `com.spege.insanetweaks.entities.magic`:

- `EntityMagicLongarms extends EntityShyco`, `EntityMagicManducater extends EntityHull`,
  `EntityMagicSummoner extends EntityCanra` — each implements `IMagicParasite`.
- `IMagicParasite` — our own marker interface on our own classes only (never mixed into SRP's
  hierarchy). Exposes `float getArcaneGlow(float partialTicks)` (0–1) for the renderer.
- `MagicParasiteTraits` — shared server logic: damage scaling (§5.1), telegraph particles and
  sounds, safe-position search, client aura particles.
- `SpectralTags` — entity-data keys for spectral summons (§5.4), modelled on `SaplingTags`.
- `ai/` — one AI task per active ability: `EntityAIBlinkStrike`, `EntityAIArcaneWard`,
  `EntityAIWardAllies`.
- Pure, Minecraft-free, unit-tested: `BlinkPlanner` (candidate offsets behind / beside a target
  from its yaw and a distance band), `ArcaneDamage` (the §5.1 multiplier), `SpectralBudget`
  (how many to summon from min/max/roll/active/limit).

Each entity:

- Calls `super.applyEntityAttributes()` and then multiplies max health / armor / damage by its
  config multipliers, so SRP's own multipliers keep working underneath ours.
- Inherits `getParasiteIDRegister()`, so SRP treats it as its base for kill counting and
  evolution points.
- Keeps every native SRP skill and AI task unless §5 says otherwise, and inherits SRP's target
  selection. Abilities read `getAttackTarget()` only and never acquire targets themselves. If
  playtests show sluggish acquisition (SRP's 1-in-10 status-gated poll, memory
  `srp-status-gated-targeting`), the fix is an own target task, as sim_wizard did.
- Syncs one `DataParameter<Byte>` `ARCANE_STATE` (0 idle, 1 telegraph, 2 ward) registered at the
  end of its own `entityInit()` after `super.entityInit()`. Everything else is server-only.

Client package `client/renderer/entity/magic`:

- `RenderMagicLongarms extends RenderShyco`, `RenderMagicManducater extends RenderHull`,
  `RenderMagicSummoner extends RenderCanra`. Each multiplies the colour by a purple tint
  (config RGB, default `0.78, 0.55, 1.0`) around the main model pass, so all of SRP's skin
  variants (v/b/h/snow) are tinted automatically and no SRP texture is copied or shipped.
- `LayerArcaneGlow` — generic over `IMagicParasite`, modelled on `LayerSimWizardGlow`: re-renders
  the same model fullbright, additive, slightly inflated, in violet. Alpha comes from
  `getArcaneGlow`: idle 0.10, telegraph ramps to 0.6, ward holds 0.8.
- All three registered in `ClientProxy.preInit` (never in the `@Mod` class).

## 5. Abilities

### 5.1 Shared

- **Telegraph:** every active ability shows a cue first — `ARCANE_STATE = 1`, purple
  `SPELL_WITCH` particles, a sound and a glow flare — so players can react.
- **Arcane-touched (passive):** in `attackEntityFrom`, magic damage (`isMagicDamage`) ×0.75 and
  fire damage (`isFireDamage`) ×1.25, applied multiplicatively when a source is both. The plan
  checks which EBW `MagicDamage` types set `isMagicDamage`.
- **Aura:** client-side purple particles every few ticks, sparse when idle.

### 5.2 Arcane Blinker (`magic_longarms`)

- Stats: HP ×1.2 (54), damage ×1.0, armor ×1.0, speed unchanged.
- **Blink Strike** (`EntityAIBlinkStrike`):
  - Trigger: target at 6–20 blocks with line of sight.
  - Cooldown: 8 s.
  - Telegraph of 15 ticks, then a teleport to a position 1.5–2.5 blocks behind the target,
    opposite its look direction. If that fails, the sides are tried.
  - A position is valid with solid ground and 2 clear blocks of headroom. If no position is
    valid, the blink is skipped and the cooldown still applies.
  - The next melee hit within 40 ticks deals +30% damage.
- **Phase Dodge:**
  - Trigger: a projectile damage source (`isProjectile`).
  - Chance: 30%.
  - Cooldown: 6 s.
  - Effect: the damage is cancelled and the Blinker teleports 3–5 blocks sideways relative to
    the incoming projectile, using the same safe-position search.
- Native shockwave and launch skills are kept.

### 5.3 Arcane Warden (`magic_manducater`)

- Stats: HP ×2.0 (60), armor ×2.0 (8), damage ×1.0.
- **Arcane Ward** (`EntityAIArcaneWard`):
  - Trigger: HP below 70%, or a target within 10 blocks.
  - Cooldown: 20 s, counted from the end of the ward.
  - Telegraph of 10 ticks, then 6 s with `ARCANE_STATE = 2`:
    - incoming damage ×0.5;
    - a projectile damage source deals 0 and its immediate source has its motion reversed
      (×0.8); a reflected `EntityArrow` gets the Warden as its shooter;
    - **Bastion:** every `EntityParasiteBase` within 6 blocks gets Resistance I, refreshed
      every second while the ward lasts.
  - Visuals: a strong glow plus a ring of particles around the Warden.
- **Anchor Pull:** when the native pull starts (`hasTargetedEntity()` false → true), the pulled
  entity gets Slowness I for 3 s.

### 5.4 Arcane Conjurer (`magic_summoner`)

- Stats: HP ×1.25 (50), damage ×1.0.
- **Spectral Swarm:**
  - The Conjurer overrides the public `doSpecialSkill(byte)` for the summon skill, so the native
    vomit summon is **replaced**, not doubled. The plan confirms which skill byte is the summon.
    Other bytes still go to `super`.
  - Each cast summons 2–3 real SRP mobs from a config list (default `srparasites:rupter`,
    `srparasites:buglin`) around the Conjurer. They go through
    `createEntityByIDFromName` → `onInitialSpawn` → `spawnEntity`, are tagged spectral, and are
    pointed at the Conjurer's target with `setAttackTarget(target, 60)`.
  - Limit: 4 live spectrals per Conjurer, enforced through a transient list of the entities it
    spawned, pruned of dead ones. On its death the Conjurer dispels all of them.
  - Spectral tags: `InsaneTweaksSpectral` (boolean) and `InsaneTweaksSpectralUntil` (world time).
    Lifetime 20 s.
  - A `LivingUpdateEvent` handler checks only `EntityParasiteBase` instances and only every
    20 ticks. Past the expiry it plays a purple poof and removes the entity with `setDead()`.
    The same handler emits server-side `SPELL_WITCH` particles around spectrals. They are foreign
    entities, so they are not tinted.
  - Spectrals drop nothing and give 0 XP (§6).
  - Evolution points:
    - expiry and dispel use `setDead()`, which bypasses `onDeath`, so SRP awards no points;
    - a spectral **killed** by a player still goes through SRP's normal `onDeath` point logic.
      Neutralising that would need a mixin and is not worth it for a 20 s mob.
  - Reloading the world clears the transient list but not the tags, so spectrals still expire
    on time.
- **Ward Allies** (`EntityAIWardAllies`):
  - Check: every 20 ticks.
  - Trigger: an `EntityParasiteBase` within 10 blocks below 50% HP and without Absorption.
  - Effect: that parasite gets Absorption II (4 hearts) for 10 s, with a particle beam from the
    Conjurer.
  - Cooldown: 12 s.
- The native ranged attack (an effect cloud) is kept.

## 6. Drops and XP

`MagicParasiteDropHandler`, registered with the other SRP-dependent content handlers.

- `LivingDropsEvent` at LOWEST priority, so SRP's drop handlers have already added their
  stacks:
  - **Magic parasite:** each existing stack is removed with `magicParasites.baseDropPenalty`
    (default 0.3) via `SaplingDropPenalty.keepsDrop`. Then the bonus table
    `insanetweaks:entities/magic_parasite` is rolled with a `LootContext` (killer, damage source,
    luck) and its stacks are appended. The bonus is never penalised.
  - **Spectral:** all drops are cleared.
- `LivingExperienceDropEvent`: spectrals drop 0 XP. Magic parasites keep their XP.

Bonus table `loot_tables/entities/magic_parasite.json`:

- Pool 1: `ebwizardry:spectral_dust` with `insanetweaks:abomination_meta`, 1 (+0–1 looting),
  weight 2 against empty 4.
- Pool 2: `ebwizardry:magic_crystal`, 0–2 (+0–1 looting).

## 7. Config

New subcategory `entities.magicParasites` (`EntitiesCategory.MagicParasites`):

- **Shared:**
  - `enableMagicParasites` (master; when off, the abilities and the drop handler stay inactive,
    but the entities stay registered because their ids are save-stable);
  - `baseDropPenalty` 0.3;
  - `tintRgb` `[0.78, 0.55, 1.0]` (client);
  - `magicDamageTaken` 0.75;
  - `fireDamageTaken` 1.25.
- **Blinker:**
  - `healthMultiplier` 1.2, `damageMultiplier` 1.0;
  - `blinkCooldownSeconds` 8, `blinkMinRange` 6, `blinkMaxRange` 20;
  - `blinkBonusDamage` 0.3;
  - `dodgeChance` 0.3, `dodgeCooldownSeconds` 6.
- **Warden:**
  - `healthMultiplier` 2.0, `armorMultiplier` 2.0, `damageMultiplier` 1.0;
  - `wardCooldownSeconds` 20, `wardDurationSeconds` 6;
  - `wardDamageTaken` 0.5;
  - `bastionRadius` 6, `pullSlownessSeconds` 3.
- **Conjurer:**
  - `healthMultiplier` 1.25, `damageMultiplier` 1.0;
  - `spectralPool` `[srparasites:rupter, srparasites:buglin]`;
  - `spectralMin` 2, `spectralMax` 3, `spectralLimit` 4;
  - `spectralLifetimeSeconds` 20;
  - `wardAlliesRadius` 10, `wardAlliesCooldownSeconds` 12.

Ability numbers are read live. Multipliers apply when an entity is created.

## 8. Lang (en_us)

- `entity.insanetweaks.magic_longarms.name=Arcane Blinker`
- `entity.insanetweaks.magic_manducater.name=Arcane Warden`
- `entity.insanetweaks.magic_summoner.name=Arcane Conjurer`

ru_ru falls back to en_us.

## 9. Safety and invariants

- No client types in the entity, AI or handler classes. The renderers and the layer are
  `@SideOnly(CLIENT)` and are referenced only from `ClientProxy`. Verify with
  `javap -v … | grep -cE 'minecraft/client|fml/client'` on the common classes.
- `IMagicParasite` lives on our classes only. Nothing gets `implements` on `EntityParasiteBase`
  (CLAUDE.md mixin rule; here it is plain subclassing, which is safe).
- Spawning goes through `world.spawnEntity`, which bypasses CheckSpawn. That is fine because
  the only spawn paths are explicit: egg, `/summon`, and a Conjurer that is already there. No
  path spawns a magic parasite on its own, so the dim-150 invariant (memory
  `dim150-no-srparasites-invariant`) is only at risk from deliberate operator spawns.
- New `DataParameter` is appended last in `entityInit` (sim_wizard's lesson on ordering).

## 10. Testing

- **JUnit:** `BlinkPlannerTest`, `ArcaneDamageTest`, `SpectralBudgetTest`, run with
  `./gradlew :insanetweaks:test`.
- **Bytecode checks:** side safety (§9); the reobfuscated jar overrides `doSpecialSkill` and
  `applyEntityAttributes` (`func_110147_ax`) under the expected names.
- **In game, DEv 1.2:**
  - Each egg spawns a purple-tinted mob with the glow. Snow and other skin variants are tinted
    too.
  - Blink telegraph and teleport behind the player. Arrows trigger the dodge.
  - Ward: halved damage, arrows bounce back, nearby parasites get Resistance.
  - Pull applies Slowness.
  - Conjurer: spectrals appear (never more than 4), expire after 20 s with a poof, drop nothing
    and vanish when the Conjurer dies. Ward Allies gives Absorption.
  - Drops: base drops thinned, bonus dust and crystals appear.
  - The log is free of errors and `/summon` works.

## 11. Later (not this spec)

- Anchor block: a one-shot spawner showing a petrified mob until a player comes close.
- Generated structure with the 3x3 imbuement altar.
- Sapling wave pools and elites.
- Adapted-tier variants.
