# ModConfig Rework — Design

**Date:** 2026-07-07
**Status:** Approved design, implementation NOT started
**Scope:** Restructure `config/ModConfig.java` (746 lines, 7 categories, ~90 fields): clean category keys and sub-categories, a custom ordered config GUI, a truthful restart-annotation audit, Books-cooldown unit change, old-config backup with player notice, and a per-category code split.

## Decisions from brainstorming

- **Priorities:** functionality (truthful live-reload annotations) + in-game GUI first; `.cfg` readability and code organization also in scope.
- **Config reset is acceptable** (mod lives in the user's modpack circle). Players are informed: automatic backup + log line + one-time login chat message.
- **`simwizard` category becomes `entities.assimilated_wizard`** — the category is future-proofed for more entities; the entity's official display name is "Assimilated Wizard". The registry name `sim_wizard`, tracking id, and class `EntitySimWizard` are NOT touched (network-stable). Config comments/display names switch to "Assimilated Wizard".
- **Thrall stays a top-level category** (too large to nest); may move under `entities` in a later iteration.
- **Tombstone Books cooldowns switch hours → minutes** (see below). Ankh of Pray is Corail Tombstone's own config — NOT touched.
- Explicitly rejected for this iteration: content validation of string fields, a `/itweaks config reload` subcommand, non-English translations, full per-field LangKeys (categories only).

## Design

### 1. Category structure and keys

Internal keys (= `.cfg` section names) lose the `"[ N ] ..."` ordering hack:

```
modules      — unchanged content (5 toggles, all genuinely @RequiresMcRestart)
tweaks       — SHRINKS: thrallWorkDurationHours moves out; keeps cursedRingFix,
               cleanseAdditionalEffects (+ anything the field audit leaves here)
traits       — unchanged structure (TraitConfig objects already render as sub-categories)
tombstone    — structure unchanged; Books cooldown fields change unit (see §4)
thrall       — split into sub-categories:
               general    (workDurationHours ← from tweaks, shared chest-scan ranges, debug logs)
               collecting (tp distances, max targets, chest scan range, …)
               porter     (direction, interval, teleport range, …)
               farming    (radius, bone meal, …)
               labour     (woodcutting + mineshaft fields, if distinct fields exist)
entities     — NEW umbrella category:
               assimilated_wizard (display "Assimilated Wizard")
                 spawning / combat / perks  (exact assignment from the field audit)
client       — unchanged content; deliberately last in the GUI order
```

Rules:
- A field moves only when it is plainly in the wrong category; the implementation plan performs a full ~90-field audit and lists every move explicitly (known so far: `thrallWorkDurationHours` → `thrall.general`).
- `@Config.Name` display names stay English.
- Comment style normalized: 1–3 lines; first line = what it does; last line = units/values/defaults where relevant.
- Sub-category mechanism: nested POJO instances (same pattern as existing `TraitConfig`), verified against Forge's annotation-config recursion during planning.

### 2. Custom config GUI (ordering + display names)

- New `client/gui/config/InsaneTweaksGuiFactory implements IModGuiFactory`, registered via `@Mod(guiFactory = "…")` on `InsaneTweaksMod`.
- The factory builds a `GuiConfig` whose element list is in **explicit order**: `modules → tweaks → traits → tombstone → thrall → entities → client` (gameplay first, debug last).
- Element source (planning-time correction — **no reflection needed**): `ConfigElement.from(ModConfig.class).getChildElements()` is the public Forge API the default GUI itself uses (`GuiConfig.collectConfigElements`, verified in Forge 1.12.2-14.23.5.2860 sources); the default merely sorts that list by localized name. Our factory reorders the same list explicitly and passes it to the `GuiConfig(parent, List<IConfigElement>, modid, configID, …)` constructor. The reflection fallback from the original design is dropped.
- `@Config.LangKey` on the seven category fields + `en_us.lang` entries (`config.insanetweaks.category.modules=Modules & Integrations`, …) so display names are independent of file keys. Field-level LangKeys are out of scope.
- The existing `OnConfigChangedEvent` → `ConfigManager.sync` handler stays as-is (works with any GUI factory).

### 3. Live-reload / restart-annotation audit

Every field is audited (grep of its read sites) and classified:

1. **`@Config.RequiresMcRestart`** — only fields controlling registrations or read once during `preInit`/`init` (all `modules` toggles; any value baked into constants at startup). Fields read per-tick/per-use LOSE the annotation.
2. **`@Config.RequiresWorldRestart`** — fields applied at entity/world creation (e.g. Assimilated Wizard base attributes read in `applyEntityAttributes`: new spawns pick them up, existing entities don't — the GUI should say so honestly).
3. **No annotation = live** — dynamically-read tunables (thrall AI intervals/ranges, `porterDirection`, cleanse list, …). They already hot-apply via the existing sync handler.

Deliverable inside the implementation plan: a table `field → class (restart/world/live) → read site(s)` so reviewers can verify each classification.

### 4. Tombstone Books cooldowns: hours → minutes

- `bookOfDisenchantmentCooldownConfig` (double, hours, 0–720) → `bookOfDisenchantmentCooldownMinutes` (int, `@RangeInt(min = 0, max = 720)`, default **6** = today's 0.1 h). Same for `bookOfMagicImpregnationCooldownMinutes`.
- Rationale: players tune these between ~15 min and 12 h; an hour-unit double with a 720 h ceiling is imprecise overkill. 720 min = 12 h ceiling; 0 still disables.
- `TombstoneBooksHandler` conversion updated (minutes → internal time unit; read both call sites at lines ~112/117).
- Field rename falls under the accepted config reset.

### 5. Old-config detection, backup, player notice

- Planning-time correction on timing: FML syncs annotation configs in `FMLModContainer.constructMod` (line 615 of the 2860 sources), immediately AFTER the mod instance is constructed (line 601) — so `preInit` is too late, but the **mod class constructor** runs before the first sync. The backup hook therefore lives in `new InsaneTweaksMod()`: if `config/insanetweaks.cfg` exists and contains the old-structure marker (literal `"[ 1 ]`), copy it to `insanetweaks.cfg.pre-rework`, delete the original, and let Forge regenerate defaults.
- Log: `[InsaneTweaks] Config structure changed in <version>; old settings backed up to insanetweaks.cfg.pre-rework — re-apply any customizations.`
- One-time chat notice on first login (reuse the `RecommendationsLoginHandler` one-shot pattern) telling the player the config was reset and where the backup is.

### 6. Code split

`ModConfig.java` → categories become top-level classes in `config/categories/`: `ModulesCategory`, `TweaksCategory`, `TraitsCategory`, `TombstoneCategory`, `ThrallCategory` (+ its sub-category classes), `EntitiesCategory` (+ `AssimilatedWizardCategory` and its sub-classes), `ClientCategory`, plus shared `TraitConfig`/`PerkConfig`. `ModConfig` remains the `@Config` root: instance fields, LangKeys, and the sync handler. All existing read paths (`ModConfig.thrall.*`, `ModConfig.tombstone.*`, …) keep working — only the declared types move; access paths for renamed/moved fields (`thrallWorkDurationHours`, Books cooldowns, `simWizard` → `entities.assimilatedWizard`) are updated at every call site (audit in plan).

## Constraints

- Java 8; existing style; `@SuppressWarnings("null")` where present.
- No changes to registry names, entity tracking ids, network packets, or module gating semantics (only annotation truthfulness).
- `run/`-relative config path conventions unchanged; the anchored `/config/` gitignore (fixed 2026-07-07) already keeps `config/categories/` sources tracked.

## Risks

1. ~~`ConfigManager` reflection~~ — RESOLVED in planning: `ConfigElement.from(Class)` public API removes the need entirely (see §2).
2. **POJO nesting depth** — depth 2 (`thrall.collecting`) is proven today by the existing `traits.fastLearner` TraitConfig objects; depth 3 (`entities.assimilated_wizard.combat`) relies on the same unbounded recursion in `ConfigManager.sync` (verified recursive in sources) — confirmed additionally in the manual runClient pass; fallback: flatten Assimilated Wizard sub-categories to depth 2 with prefixed names.
3. **Missed read-site during field moves** — mitigated by grep-audit per field in the plan + `-Xlint` build (field renames are compile errors, not silent).

## Planning-time audit findings (selected, full table in the plan)

- `tombstone.enableCurseOfPossessionPatch` gates handler REGISTRATION at `init` (InsaneTweaksMod:273) — currently unannotated; gains `@Config.RequiresMcRestart`.
- `thrall.enableCollectingMode` currently has `@Config.RequiresMcRestart` but is read per-tick in `ThrallAICollecting.shouldExecute` — annotation is WRONG and is removed (live).
- Assimilated Wizard `healthMultiplier`/`extraHealth`/`armorMultiplier`/`speedMultiplier`/`minFollowRange` (applied in `applyEntityAttributes`) and `decisionRange`/`rangeMultiplier` (baked into the cast-AI constructor) gain `@Config.RequiresWorldRestart`.
- Version-history sentences in comments ("v2.1: nerfed from …") are trimmed — that context belongs in git history.

## Verification

- `./gradlew build` green per task.
- Manual `runClient`: fresh `.cfg` has clean keys + expected nesting; Mod Options shows sections in the designed order with sub-screens; a live field (e.g. a thrall scan range) edited via GUI applies without restart; restart-flagged fields show the restart notice; old-format file triggers backup + log + one-time chat notice; Books cooldowns act in minutes (default 6).

## Out of scope

- String-content validation, `/itweaks config reload`, translations beyond en_us, per-field LangKeys.
- Ankh of Pray / any Corail Tombstone native config.
- Registry names, entity classes, thrall-under-entities move.
- The housekeeping/feature backlog discussed alongside this design (tracked separately).
