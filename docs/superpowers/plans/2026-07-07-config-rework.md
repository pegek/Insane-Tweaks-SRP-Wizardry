# ModConfig Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the mod config: clean category keys with sub-categories, an explicitly-ordered config GUI, truthful restart annotations, Books cooldowns in minutes, old-config backup with a player notice, and a per-category code split.

**Architecture:** `ModConfig` stays the single `@Config` root but its seven category objects become top-level classes in `config/categories/` (sub-categories = nested POJOs, the same mechanism the existing `TraitConfig` already proves). A new `IModGuiFactory` reorders `ConfigElement.from(ModConfig.class).getChildElements()` — public Forge API, no reflection. The old-file backup runs in the `InsaneTweaksMod` constructor (verified: FML syncs annotation configs in `constructMod` AFTER instance creation — sources line 601 vs 615).

**Tech Stack:** Java 8, MC 1.12.2 Forge 14.23.5.2860 (sources verified from the ForgeGradle cache jar), ForgeGradle 3.

**Spec:** `docs/superpowers/specs/2026-07-07-config-rework-design.md`

**Testing note:** No automated test suite. Per-task verification = `./gradlew build` green. Final task = manual `runClient` checklist.

**Verified Forge facts (do not re-derive):**
- `ConfigElement.from(Class<?>)` (public, `net.minecraftforge.common.config.ConfigElement`) builds the element tree for an `@Config` class; `.getChildElements()` yields the category list. The default GUI (`GuiConfig(parent, modid, title)` → `collectConfigElements`) merely sorts that list by localized name — reordering it ourselves is exactly equivalent input.
- Constructor to use: `GuiConfig(GuiScreen parentScreen, List<IConfigElement> configElements, String modID, String configID, boolean allRequireWorldRestart, boolean allRequireMcRestart, String title)` — passing a non-null `configID` makes Done fire `OnConfigChangedEvent`, which our existing sync handler already consumes.
- `IModGuiFactory` (1.12.2): implement `initialize(Minecraft)`, `hasConfigGui() → true`, `createConfigGui(GuiScreen)`, `runtimeGuiCategories() → null`. Registered via `@Mod(..., guiFactory = "...")`.
- FML calls `ConfigManager.sync(modid, Type.INSTANCE)` in `FMLModContainer.constructMod` AFTER `modInstance` is created → the mod constructor is a safe pre-first-sync hook. `Loader.instance().getConfigDir()` is initialized by then (ConfigManager itself uses it in the same phase).
- Forge `Configuration.save` PRESERVES unknown categories — hence old-format files must be backed up + deleted, not merely re-synced, or the junk `"[ N ] …"` sections linger.
- Nested POJO fields render as sub-categories and sync recursively (unbounded recursion in `ConfigManager.sync`); depth 2 is already proven in production by `traits.<trait>` TraitConfig objects.

---

## Restart-annotation audit table

Legend: **MC** = `@Config.RequiresMcRestart`, **WORLD** = `@Config.RequiresWorldRestart`, **live** = no annotation. "Change" column is the delta this plan applies.

| Field (new path) | Class | Evidence (read sites) | Change |
|---|---|---|---|
| modules.* (all 5) | MC | registry events (ModItems/ModRecipes/ModSpells) + handler registration in `init` | none (already MC) |
| tweaks.enableCursedRingFix | live | `MixinEntitySummonedCreature:70` per-call | none |
| tweaks.cleanseAdditionalEffects | live | `PotionCleanse:79` per-application | none |
| traits.\<trait\>.* (TraitConfig) | MC | trait constructors at registration (`skills/Trait*.java`) | none (already MC) |
| client.* (all 5) | live | per-event/per-frame reads everywhere | none |
| tombstone.enableTombstoneTweaks | MC | gates registration `InsaneTweaksMod:272` + `ModRecipes:65` | none (already MC) |
| tombstone.enableCurseOfPossessionPatch | **MC** | gates handler REGISTRATION `InsaneTweaksMod:273` (also per-event `LivingDeathEventHandler:86`) | **ADD MC** |
| tombstone.disableEnchantKeyRecipe | MC | registry event `ModRecipes:66` | none (already MC) |
| tombstone decay/nerf/perk fields | live | `GraveDecayHandler`, `TombstoneDropEventHandler`, `MixinTombstonePerk*` per-call | none |
| tombstone Books cooldowns (renamed) | live | `TombstoneBooksHandler:112/117` per-use | none (renamed, unit change) |
| thrall.general.workDurationHours (moved) | live | `EntityThrallMinion:324` per-tick | none (moved from tweaks) |
| thrall.general.{maxSlotsPerPlayer, passivePickupRange, followTeleportDistance} | live | `ThrallSlotManager:37`, `EntityThrallMinion:375/741` per-use | none |
| thrall.collecting.enableCollectingMode | **live** | `ThrallAICollecting.shouldExecute:116` per-tick + `PacketThrallCommand:158` per-command | **REMOVE MC** (was wrongly annotated) |
| thrall mode toggles (farming/porter/woodcutting/mineshaft) | live | `ThrallAI*.shouldExecute` + `PacketThrallCommand` per-command | none |
| thrall remaining tunables | live | AI classes per-tick/cycle (incl. `porterDirection` per cycle) | none |
| entities.assimilatedWizard.spawning.enabled | MC | gates handler registration `InsaneTweaksMod:315` (+ per-conversion `SrpWizardryAssimilationHelper:121`) | none (already MC) |
| entities.assimilatedWizard.spawning.{healthMultiplier, extraHealth, armorMultiplier, speedMultiplier} | **WORLD** | `EntitySimWizard.applyEntityAttributes:148-152` — applied per entity creation only | **ADD WORLD** |
| entities.assimilatedWizard.combat.minFollowRange | **WORLD** | `EntitySimWizard:152` (attribute at creation) | **ADD WORLD** |
| entities.assimilatedWizard.combat.{decisionRange, rangeMultiplier} | **WORLD** | baked into AI-task constructor `EntityAISimWizardCast:82` | **ADD WORLD** (rangeMultiplier also read per-cast:484 — WORLD is the honest label for the baked part) |
| entities.assimilatedWizard spawning.phase* + srpSaveDataId | live | `EntitySimWizard:198-212` periodic cache refresh | none |
| entities.assimilatedWizard remaining combat/spells fields | live | per-cast/per-decision/per-tick reads (`EntityAISimWizardCast`, `EntitySimWizard:270/280/364-371/482-484`) | none. `spellPool`/`includeAbominationSummons`: verify during implementation whether `buildSpellPool` (`EntitySimWizard:270`) runs per entity creation or lazily per refresh; if per-creation, annotate both WORLD instead |

Comment normalization: version-history sentences ("v2.1: nerfed from…", "v3.2: …", "C-2b" plan references) are trimmed from comments — that context lives in git history. Affected fields (Thrall/Entities, embodied in Task 2's full code): collectingMinTpDistance, healthMultiplier, extraHealth, armorMultiplier, speedMultiplier, potencyMultiplier, rangeMultiplier, spellPool, baseCastCooldownTicks, maxSpellCooldownBonusTicks, spellCooldownDivisor.

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `src/main/java/com/spege/insanetweaks/events/TombstoneBooksHandler.java` | Modify | Minutes-based cooldown reads |
| `src/main/java/com/spege/insanetweaks/config/ModConfig.java` | Rewrite | `@Config` root: 7 category fields, LangKeys, sync handler |
| `src/main/java/com/spege/insanetweaks/config/categories/ModulesCategory.java` | Create | modules |
| `src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java` | Create | tweaks (minus thrall timer) |
| `src/main/java/com/spege/insanetweaks/config/categories/TraitsCategory.java` | Create | traits + nested TraitConfig |
| `src/main/java/com/spege/insanetweaks/config/categories/ClientCategory.java` | Create | client |
| `src/main/java/com/spege/insanetweaks/config/categories/TombstoneCategory.java` | Create | tombstone + nested PerkConfig + Books minutes fields |
| `src/main/java/com/spege/insanetweaks/config/categories/ThrallCategory.java` | Create | thrall + nested General/Collecting/Porter/Farming/Labour + PorterDirection |
| `src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java` | Create | entities + nested AssimilatedWizard (Spawning/Combat/Spells) |
| ~20 read-site files | Modify | Path updates per the mapping table (Task 2 Step 4) |
| `src/main/java/com/spege/insanetweaks/config/OldConfigBackup.java` | Create | Marker detection + backup + delete |
| `src/main/java/com/spege/insanetweaks/events/ConfigResetNoticeHandler.java` | Create | One-shot login chat notice |
| `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` | Modify | Constructor hook, notice registration, `guiFactory` attr |
| `src/main/java/com/spege/insanetweaks/client/gui/config/InsaneTweaksGuiFactory.java` | Create | Ordered config GUI |
| `src/main/resources/assets/insanetweaks/lang/en_us.lang` | Modify | Category display names |
| `NEXT_SESSION_SPELLS.md` | Modify | Deferred manual checklist (final task) |

No changes to: registry names, entity tracking ids, network packets, `ModSpells`, module gating semantics.

---

### Task 1: Tombstone Books cooldowns — hours → minutes

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/config/ModConfig.java` (two fields, still in the old structure — Task 2 carries them into `TombstoneCategory` unchanged)
- Modify: `src/main/java/com/spege/insanetweaks/events/TombstoneBooksHandler.java`

- [ ] **Step 1: Replace the two config fields**

In `ModConfig.java` (class `TombstoneTweaks`, lines ~293-301), replace:

```java
                @Config.Comment("Cooldown in hours before the Book of Disenchantment can be used again. Set to 0 to disable cooldown.")
                @Config.Name("Book of Disenchantment Cooldown (Hours)")
                @Config.RangeDouble(min = 0, max = 720)
                public double bookOfDisenchantmentCooldownConfig = 0.1;

                @Config.Comment("Cooldown in hours before the Book of Magic Impregnation can be used again. Set to 0 to disable cooldown.")
                @Config.Name("Book of Magic Impregnation Cooldown (Hours)")
                @Config.RangeDouble(min = 0, max = 720)
                public double bookOfMagicImpregnationCooldownConfig = 0.1;
```

with:

```java
                @Config.Comment("Cooldown in minutes before the Book of Disenchantment can be used again. 0 disables the cooldown. Max 720 = 12 h.")
                @Config.Name("Book of Disenchantment Cooldown (Minutes)")
                @Config.RangeInt(min = 0, max = 720)
                public int bookOfDisenchantmentCooldownMinutes = 6;

                @Config.Comment("Cooldown in minutes before the Book of Magic Impregnation can be used again. 0 disables the cooldown. Max 720 = 12 h.")
                @Config.Name("Book of Magic Impregnation Cooldown (Minutes)")
                @Config.RangeInt(min = 0, max = 720)
                public int bookOfMagicImpregnationCooldownMinutes = 6;
```

- [ ] **Step 2: Update the handler conversion**

In `TombstoneBooksHandler.java` (lines ~107-127), replace:

```java
        String regName = bookStack.getItem().getRegistryName().toString();
        double cooldownHours = 0;
        String nbtKey = "";

        if (regName.equals("tombstone:book_of_disenchantment")) {
            double conf = com.spege.insanetweaks.config.ModConfig.tombstone.bookOfDisenchantmentCooldownConfig;
            if (conf <= 0) return;
            cooldownHours = conf;
            nbtKey = "InsaneTweaks_DisenchantBookCooldown";
        } else if (regName.equals("tombstone:book_of_magic_impregnation")) {
            double conf = com.spege.insanetweaks.config.ModConfig.tombstone.bookOfMagicImpregnationCooldownConfig;
            if (conf <= 0) return;
            cooldownHours = conf;
            nbtKey = "InsaneTweaks_ImpregnationBookCooldown";
        } else {
            return;
        }

        // Just blindly schedule check, we no longer pre-block here since Vanilla Tracker does it
        long cooldownTicks = (long)(cooldownHours * 60 * 60 * 20);
```

with:

```java
        String regName = bookStack.getItem().getRegistryName().toString();
        int cooldownMinutes = 0;
        String nbtKey = "";

        if (regName.equals("tombstone:book_of_disenchantment")) {
            int conf = com.spege.insanetweaks.config.ModConfig.tombstone.bookOfDisenchantmentCooldownMinutes;
            if (conf <= 0) return;
            cooldownMinutes = conf;
            nbtKey = "InsaneTweaks_DisenchantBookCooldown";
        } else if (regName.equals("tombstone:book_of_magic_impregnation")) {
            int conf = com.spege.insanetweaks.config.ModConfig.tombstone.bookOfMagicImpregnationCooldownMinutes;
            if (conf <= 0) return;
            cooldownMinutes = conf;
            nbtKey = "InsaneTweaks_ImpregnationBookCooldown";
        } else {
            return;
        }

        // Just blindly schedule check, we no longer pre-block here since Vanilla Tracker does it
        long cooldownTicks = (long) cooldownMinutes * 60L * 20L;
```

- [ ] **Step 3: Build** — `./gradlew build` → BUILD SUCCESSFUL (grep first that no OTHER file references the old field names; the audit found only these two call sites).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/config/ModConfig.java src/main/java/com/spege/insanetweaks/events/TombstoneBooksHandler.java
git commit -m "feat: Tombstone Books cooldowns configured in minutes (0-720)"
```

---

### Task 2: Category restructure + code split + annotation audit

The big atomic task: category classes move to `config/categories/`, keys lose the `[ N ]` prefixes, sub-categories appear, misplaced fields move, audit deltas apply, all read sites update. One build verifies everything (renames are compile errors).

**Files:**
- Rewrite: `src/main/java/com/spege/insanetweaks/config/ModConfig.java`
- Create: the seven `config/categories/*.java` files (map above)
- Modify: all read-site files per the mapping table in Step 4

- [ ] **Step 1: Create the small category classes (full content)**

`ModulesCategory.java`, `TweaksCategory.java`, `ClientCategory.java` — each file is:

```java
package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class ModulesCategory {
    // (fields)
}
```

with the class body being the EXACT field blocks moved verbatim from today's `ModConfig.java` (git HEAD before this task):
- `ModulesCategory` ← the five fields of `Modules` (lines 45-71), unchanged.
- `TweaksCategory` ← the `Tweaks` fields MINUS `thrallWorkDurationHours` (i.e. lines 87-101: `enableCursedRingFix`, `cleanseAdditionalEffects`), unchanged.
- `ClientCategory` ← the five fields of `Client` (lines 204-231), unchanged.

Indentation drops one level (fields sit directly in a top-level class). Keep 4-space indent matching new files' style.

- [ ] **Step 2: Create the verbatim-move category classes**

`TraitsCategory.java` — same skeleton; body = the seventeen `TraitConfig` fields of `Traits` (lines 108-171) verbatim, PLUS the whole `TraitConfig` class (lines 177-198) as a `public static class TraitConfig` nested at the end. Update the seventeen field initializers' type references only if needed (they reference `TraitConfig` unqualified — still resolves as the nested class).

`TombstoneCategory.java` — same skeleton; body = all `TombstoneTweaks` fields (lines 238-348, including the Task 1 minutes fields and the section-divider comments) verbatim, with ONE annotation delta: add `@Config.RequiresMcRestart` to `enableCurseOfPossessionPatch` directly under its `@Config.Name` line (audit: it gates handler registration at init). PLUS the whole `PerkConfig` class (lines 718-732) nested at the end as `public static class PerkConfig`.

- [ ] **Step 3: Create `ThrallCategory` and `EntitiesCategory` (full content)**

`ThrallCategory.java` — full file content:

```java
package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class ThrallCategory {

    @Config.Name("General")
    @Config.Comment("Slot cap, timers and shared thrall behaviour.")
    public final General general = new General();

    @Config.Name("Collecting")
    @Config.Comment("COLLECTING mode: toss 1-4 block items, the thrall teleport-searches around home and harvests matches.")
    public final Collecting collecting = new Collecting();

    @Config.Name("Porter")
    @Config.Comment("PORTER mode: item ferry between the owner and home chests.")
    public final Porter porter = new Porter();

    @Config.Name("Farming")
    @Config.Comment("FARMING mode: harvest, replant, bone-meal and re-till.")
    public final Farming farming = new Farming();

    @Config.Name("Labour")
    @Config.Comment("WOODCUTTING and MINESHAFT modes.")
    public final Labour labour = new Labour();

    /** Direction a Porter carries items. Read per cycle (no restart needed). */
    public enum PorterDirection { TO_HOME, FROM_HOME }

    public static class General {
        // fields, verbatim from ModConfig.Thrall unless noted:
        //   maxSlotsPerPlayer            (lines 355-359)
        //   passivePickupRange           (lines 514-517)
        //   followTeleportDistance       (lines 519-522)
        // plus MOVED from ModConfig.Tweaks (lines 79-85), renamed field only:
        //   thrallWorkDurationHours -> workDurationHours
        //   (same @Config.Name("Thrall Work Duration (hours)"), same comment, same @RangeInt)
    }

    public static class Collecting {
        // fields verbatim from ModConfig.Thrall:
        //   enableCollectingMode  (lines 433-437) — DELTA: REMOVE @Config.RequiresMcRestart
        //     (audit: read per-tick in ThrallAICollecting.shouldExecute:116 — it is live)
        //   collectingDurationMinutes            (439-442)
        //   collectingItemPickupTimeoutSeconds   (444-448)
        //   collectingMaxTargets                 (450-453)
        //   collectingMinTpDistance              (455-460) — DELTA: comment trimmed to
        //     "Inner radius of the random teleport ring (blocks from home). The session scans the thrall's current position first."
        //   collectingMaxTpDistance              (462-465)
        //   collectingScanRadius                 (467-470)
        //   collectingVeinMaxBlocks              (472-475)
        //   collectingMaxEmptyCycles             (477-481)
        //   collectingTickInterval               (483-486)
        //   collectingChestScanRange             (488-491)
        //   collectingResumeWindowMinutes        (493-497)
    }

    public static class Porter {
        // fields verbatim from ModConfig.Thrall:
        //   enablePorterMode        (386-391)
        //   porterIntervalSeconds   (393-398)
        //   porterDirection         (403-411) — type reference becomes the outer PorterDirection
        //   porterTeleportRange     (413-418)
        //   porterChestScanRange    (420-424)
        //   enablePorterSorting     (426-431)
    }

    public static class Farming {
        // fields verbatim from ModConfig.Thrall:
        //   enableFarmingMode  (369-371)
        //   farmRadius         (373-378)
        //   farmUseBoneMeal    (380-384)
    }

    public static class Labour {
        // fields verbatim from ModConfig.Thrall:
        //   enableWoodcuttingMode  (365-367)
        //   enableMineshaftMode    (361-363)
        //   mineshaftDepthMin      (499-502)
        //   mineshaftStripLength   (504-507)
        //   mineshaftBranchSpacing (509-512)
    }
}
```

The `// fields …` comments above are ASSEMBLY INSTRUCTIONS for this plan, not code to keep — the class bodies must contain the actual moved field blocks (annotations + comments + declarations). Line numbers refer to the pre-Task-2 `ModConfig.java` in git HEAD; every listed delta is exhaustive — anything not listed as a delta is byte-verbatim.

`EntitiesCategory.java` — full file content, same convention:

```java
package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

public class EntitiesCategory {

    @Config.Name("Assimilated Wizard")
    @Config.Comment("The assimilated wizard parasite-mage (registry name sim_wizard): a full SRP parasite, aggressive vs non-parasites.")
    public final AssimilatedWizard assimilatedWizard = new AssimilatedWizard();

    public static class AssimilatedWizard {

        @Config.Name("Spawning & Stats")
        @Config.Comment("Master toggle, base attributes and SRP phase scaling. Attribute values apply to newly created entities only.")
        public final Spawning spawning = new Spawning();

        @Config.Name("Combat")
        @Config.Comment("Cast AI tunables: ranges, cooldowns, self-heal, telegraph.")
        public final Combat combat = new Combat();

        @Config.Name("Spells")
        @Config.Comment("Spell pool contents.")
        public final Spells spells = new Spells();
    }

    public static class Spawning {
        // fields verbatim from ModConfig.SimWizard (pre-Task-2 lines), with deltas:
        //   enabled            (529-536)  — comment's "sim_wizard entity" -> "Assimilated Wizard entity (registry name sim_wizard)"
        //   healthMultiplier   (538-544)  — ADD @Config.RequiresWorldRestart; trim "v2.1:" history line
        //   extraHealth        (546-552)  — ADD @Config.RequiresWorldRestart; trim history line
        //   armorMultiplier    (554-561)  — ADD @Config.RequiresWorldRestart; trim history line (keep "Values < 1.0 reduce armor below base.")
        //   speedMultiplier    (563-570)  — ADD @Config.RequiresWorldRestart; trim history lines
        //   enablePhaseScaling     (679-685)
        //   phaseScalingPerPhase   (687-690)
        //   phaseScalingMaxPhase   (692-695)
        //   srpSaveDataId          (697-704)
    }

    public static class Combat {
        // fields verbatim, with deltas:
        //   minFollowRange     (572-575) — ADD @Config.RequiresWorldRestart
        //   decisionRange      (577-580) — ADD @Config.RequiresWorldRestart (baked into cast-AI constructor)
        //   potencyMultiplier  (582-588) — trim history line
        //   rangeMultiplier    (590-596) — ADD @Config.RequiresWorldRestart; trim history line
        //   specialSpellChancePercent (625-632)
        //   retreatHealthPercent      (634-637)
        //   baseCastCooldownTicks     (639-646) — trim "v3.2/v2.3" history sentences; keep the plain description
        //   maxSpellCooldownBonusTicks (648-654) — trim history sentence
        //   spellCooldownDivisor      (656-662) — trim "v3.2 default" phrasing to plain "Higher = harsher nerf."
        //   selfHealAmount            (664-667)
        //   selfHealCooldownNormal    (669-672)
        //   selfHealCooldownLow       (674-677)
        //   castTelegraphTicks        (706-712)
    }

    public static class Spells {
        // fields verbatim, with deltas:
        //   includeAbominationSummons (598-604) — comment's "sim_wizard" -> "the Assimilated Wizard"
        //   spellPool                 (606-623) — trim "v3.3:" prefix from the comment
        // Implementation check (audit table): if EntitySimWizard.buildSpellPool (:270) runs once per
        //   entity creation (not per periodic refresh), ADD @Config.RequiresWorldRestart to BOTH fields
        //   and note it in the commit message.
    }
}
```

- [ ] **Step 4: Rewrite the `ModConfig` root**

Full file content:

```java
package com.spege.insanetweaks.config;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.categories.ClientCategory;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.config.categories.ModulesCategory;
import com.spege.insanetweaks.config.categories.ThrallCategory;
import com.spege.insanetweaks.config.categories.TombstoneCategory;
import com.spege.insanetweaks.config.categories.TraitsCategory;
import com.spege.insanetweaks.config.categories.TweaksCategory;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = InsaneTweaksMod.MODID, name = "insanetweaks", category = "")
public class ModConfig {

    @Config.Name("modules")
    @Config.LangKey("config.insanetweaks.category.modules")
    @Config.Comment("Toggle main systems and cross-mod integrations.")
    public static final ModulesCategory modules = new ModulesCategory();

    @Config.Name("tweaks")
    @Config.LangKey("config.insanetweaks.category.tweaks")
    @Config.Comment("Specific bugfixes and mechanic alterations.")
    public static final TweaksCategory tweaks = new TweaksCategory();

    @Config.Name("traits")
    @Config.LangKey("config.insanetweaks.category.traits")
    @Config.Comment("SP costs, parent trees and requirements for custom Reskillable traits.")
    public static final TraitsCategory traits = new TraitsCategory();

    @Config.Name("tombstone")
    @Config.LangKey("config.insanetweaks.category.tombstone")
    @Config.Comment("Bugfixes and mechanic alterations for Corail Tombstone.")
    public static final TombstoneCategory tombstone = new TombstoneCategory();

    @Config.Name("thrall")
    @Config.LangKey("config.insanetweaks.category.thrall")
    @Config.Comment("The Thrall companion entity: slots, work modes, AI tunables.")
    public static final ThrallCategory thrall = new ThrallCategory();

    @Config.Name("entities")
    @Config.LangKey("config.insanetweaks.category.entities")
    @Config.Comment("Custom entities added by the mod.")
    public static final EntitiesCategory entities = new EntitiesCategory();

    @Config.Name("client")
    @Config.LangKey("config.insanetweaks.category.client")
    @Config.Comment("Visual toggles and debugging tools.")
    public static final ClientCategory client = new ClientCategory();

    @Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(InsaneTweaksMod.MODID)) {
                ConfigManager.sync(InsaneTweaksMod.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
```

- [ ] **Step 5: Update all read sites**

Project-wide search-and-replace by this exact mapping (build catches any straggler). Where a file aliases (`SimWizard cfg = ModConfig.simWizard` in `EntitySimWizard`), update the alias's declared type/initializer instead of each use.

| Old | New |
|---|---|
| `ModConfig.tweaks.thrallWorkDurationHours` | `ModConfig.thrall.general.workDurationHours` |
| `ModConfig.simWizard.enabled` | `ModConfig.entities.assimilatedWizard.spawning.enabled` |
| `ModConfig.simWizard.<f>` for f in {healthMultiplier, extraHealth, armorMultiplier, speedMultiplier, enablePhaseScaling, phaseScalingPerPhase, phaseScalingMaxPhase, srpSaveDataId} | `ModConfig.entities.assimilatedWizard.spawning.<f>` |
| `ModConfig.simWizard.<f>` for f in {minFollowRange, decisionRange, potencyMultiplier, rangeMultiplier, specialSpellChancePercent, retreatHealthPercent, baseCastCooldownTicks, maxSpellCooldownBonusTicks, spellCooldownDivisor, selfHealAmount, selfHealCooldownNormal, selfHealCooldownLow, castTelegraphTicks} | `ModConfig.entities.assimilatedWizard.combat.<f>` |
| `ModConfig.simWizard.<f>` for f in {includeAbominationSummons, spellPool} | `ModConfig.entities.assimilatedWizard.spells.<f>` |
| `ModConfig.thrall.<f>` for f in {maxSlotsPerPlayer, passivePickupRange, followTeleportDistance} | `ModConfig.thrall.general.<f>` |
| `ModConfig.thrall.<f>` for the 12 collecting* fields (incl. enableCollectingMode) | `ModConfig.thrall.collecting.<f>` |
| `ModConfig.thrall.<f>` for f in {enablePorterMode, porterIntervalSeconds, porterDirection, porterTeleportRange, porterChestScanRange, enablePorterSorting} | `ModConfig.thrall.porter.<f>` |
| `ModConfig.thrall.<f>` for f in {enableFarmingMode, farmRadius, farmUseBoneMeal} | `ModConfig.thrall.farming.<f>` |
| `ModConfig.thrall.<f>` for f in {enableWoodcuttingMode, enableMineshaftMode, mineshaftDepthMin, mineshaftStripLength, mineshaftBranchSpacing} | `ModConfig.thrall.labour.<f>` |
| `ModConfig.Thrall.PorterDirection` | `com.spege.insanetweaks.config.categories.ThrallCategory.PorterDirection` |
| `ModConfig.SimWizard` (type refs, e.g. the `cfg` alias in EntitySimWizard) | `com.spege.insanetweaks.config.categories.EntitiesCategory.AssimilatedWizard` — adapt: where the alias covers fields from more than one new sub-category, split into sub-category locals (e.g. `Spawning spawn = ModConfig.entities.assimilatedWizard.spawning;`) |
| `ModConfig.traits.<trait>` (type `TraitConfig` in `skills/Trait*.java` constructors) | unchanged paths; the parameter TYPE becomes `TraitsCategory.TraitConfig` (update `TraitBase`'s constructor signature import) |
| `PerkConfig` type references (Tombstone mixins/handlers, if any) | `TombstoneCategory.PerkConfig` |

Known read-site files (from planning greps): `EntityThrallMinion`, `ThrallAICollecting`, `ThrallAIFarming`, `ThrallAIPorter`, `ThrallAIWoodcutting`, `ThrallAIMineshaft`, `ThrallSlotManager`, `PacketThrallCommand`, `EntitySimWizard`, `EntityAISimWizardCast`, `SimWizardFactionHandler` (javadoc), `SrpWizardryAssimilationHelper`, `InsaneTweaksMod`, `skills/Trait*.java` + `TraitBase`/`SkillsModule`, `MixinTombstonePerk*` (PerkConfig reads), plus javadoc mentions. Grep `ModConfig\.(simWizard|thrall\.|tweaks\.thrallWork|Thrall\.|SimWizard)` after replacing to confirm zero stale hits.

- [ ] **Step 6: Delete the old nested classes** — after the moves, `ModConfig.java` contains ONLY the root shown in Step 4 (old `Modules`/`Tweaks`/`Traits`/`TraitConfig`/`Client`/`TombstoneTweaks`/`Thrall`/`SimWizard`/`PerkConfig` bodies are gone).

- [ ] **Step 7: Build** — `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/
git commit -m "refactor: restructure config into clean categories with sub-categories and truthful restart annotations"
```

(Only `src/main/java` paths are touched by this task; never `git add` the two pre-existing deleted `.md` files.)

---

### Task 3: Old-config backup + one-shot login notice

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/config/OldConfigBackup.java`
- Create: `src/main/java/com/spege/insanetweaks/events/ConfigResetNoticeHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`

- [ ] **Step 1: Create the backup helper**

Full file content:

```java
package com.spege.insanetweaks.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraftforge.fml.common.Loader;

/**
 * Detects a pre-rework insanetweaks.cfg (categories named "[ 1 ] ...") and moves it aside so
 * Forge regenerates a clean file. MUST run from the InsaneTweaksMod constructor: FML performs the
 * first ConfigManager.sync inside FMLModContainer.constructMod immediately AFTER the mod instance
 * is created, and Forge's Configuration.save would otherwise preserve the old junk sections.
 */
public final class OldConfigBackup {

    private static final Logger LOGGER = LogManager.getLogger(InsaneTweaksMod.MODID);
    private static final String OLD_STRUCTURE_MARKER = "\"[ 1 ]";
    private static final String BACKUP_NAME = "insanetweaks.cfg.pre-rework";

    /** Set when a backup happened this launch; read by ConfigResetNoticeHandler. */
    private static boolean migrated;

    private OldConfigBackup() {
    }

    public static boolean didMigrate() {
        return migrated;
    }

    public static void backupOldConfigIfPresent() {
        try {
            File configDir = Loader.instance().getConfigDir();
            if (configDir == null) {
                return;
            }
            File cfg = new File(configDir, "insanetweaks.cfg");
            if (!cfg.isFile()) {
                return;
            }

            String content = new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8);
            if (!content.contains(OLD_STRUCTURE_MARKER)) {
                return;
            }

            File backup = new File(configDir, BACKUP_NAME);
            Files.copy(cfg.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(cfg.toPath());
            migrated = true;
            LOGGER.info("[InsaneTweaks] Config structure changed in {}; old settings were backed up to {} - re-apply any customizations.",
                    InsaneTweaksMod.VERSION, BACKUP_NAME);
        } catch (IOException e) {
            // Never fatal: worst case the old sections linger in the file as ignored junk.
            LOGGER.warn("[InsaneTweaks] Could not back up old-format config: {}", e.toString());
        }
    }
}
```

- [ ] **Step 2: Create the one-shot login notice**

Full file content (mirrors the `RecommendationsLoginHandler` one-shot pattern in `InsaneTweaksMod`):

```java
package com.spege.insanetweaks.events;

import com.spege.insanetweaks.config.OldConfigBackup;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * One-shot chat notice after the old-format config was backed up and reset
 * (see OldConfigBackup). Fires for the first player to log in this launch.
 */
public class ConfigResetNoticeHandler {

    private boolean sent = false;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (this.sent || !OldConfigBackup.didMigrate()) {
            return;
        }

        EntityPlayer player = event.player;
        if (player == null || player.world.isRemote) {
            return;
        }

        this.sent = true;
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "[InsaneTweaks] "
                + TextFormatting.YELLOW
                + "The config layout changed and your settings were reset to defaults. "
                + "Your previous file was saved as config/insanetweaks.cfg.pre-rework - re-apply any customizations."));
    }
}
```

- [ ] **Step 3: Wire both into `InsaneTweaksMod`**

a) The class currently has no explicit constructor — add one (place it directly above `preInit`):

```java
    public InsaneTweaksMod() {
        // Must run before FML's first ConfigManager.sync (which fires later inside
        // FMLModContainer.constructMod) - see OldConfigBackup.
        com.spege.insanetweaks.config.OldConfigBackup.backupOldConfigIfPresent();
    }
```

b) In `init`, next to the other unconditional handler registrations (e.g. after the `ThrallTargetProtectionHandler` line), add:

```java
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ConfigResetNoticeHandler());
```

(Registered unconditionally — it no-ops unless a migration happened this launch. It intentionally ignores `client.suppressStartupWarningsInChat`: a silent settings reset must not be suppressible by a setting that itself just got reset.)

- [ ] **Step 4: Build** — `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/config/OldConfigBackup.java src/main/java/com/spege/insanetweaks/events/ConfigResetNoticeHandler.java src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat: back up pre-rework config and notify players about the reset"
```

---

### Task 4: Ordered config GUI factory + lang entries

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/client/gui/config/InsaneTweaksGuiFactory.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (`@Mod` attribute)
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Create the factory**

Full file content:

```java
package com.spege.insanetweaks.client.gui.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Config GUI with an explicit section order (gameplay first, client/debug last) instead of the
 * default factory's alphabetical sort. Uses the same public API the default GUI uses
 * (ConfigElement.from(ModConfig.class).getChildElements()) - the only difference is the ordering.
 */
@SideOnly(Side.CLIENT)
public class InsaneTweaksGuiFactory implements IModGuiFactory {

    /** Internal category keys (@Config.Name values on the ModConfig root fields), in display order. */
    private static final List<String> CATEGORY_ORDER = Arrays.asList(
            "modules", "tweaks", "traits", "tombstone", "thrall", "entities", "client");

    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        List<IConfigElement> children = ConfigElement.from(ModConfig.class).getChildElements();

        List<IConfigElement> ordered = new ArrayList<IConfigElement>(children.size());
        for (String name : CATEGORY_ORDER) {
            for (IConfigElement element : children) {
                if (name.equals(element.getName())) {
                    ordered.add(element);
                    break;
                }
            }
        }
        // Anything not in the explicit list (future additions) goes to the end, original order.
        for (IConfigElement element : children) {
            if (!ordered.contains(element)) {
                ordered.add(element);
            }
        }

        return new GuiConfig(parentScreen, ordered, InsaneTweaksMod.MODID, InsaneTweaksMod.MODID,
                false, false, InsaneTweaksMod.NAME);
    }

    @Override
    public java.util.Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}
```

Note: passing `configID = InsaneTweaksMod.MODID` (non-null) forces `OnConfigChangedEvent` on Done, which the existing `ModConfig.EventHandler` consumes to `ConfigManager.sync` — identical live-apply behaviour to the default factory.

- [ ] **Step 2: Register the factory**

In `InsaneTweaksMod.java`, extend the `@Mod` annotation (line ~56) with the attribute (keep everything else identical):

```java
@Mod(modid = InsaneTweaksMod.MODID, name = InsaneTweaksMod.NAME, version = InsaneTweaksMod.VERSION,
        guiFactory = "com.spege.insanetweaks.client.gui.config.InsaneTweaksGuiFactory",
        dependencies = "... (unchanged existing string) ...")
```

- [ ] **Step 3: Lang entries**

Append to `src/main/resources/assets/insanetweaks/lang/en_us.lang` (after the thrall tooltip block):

```
config.insanetweaks.category.modules=Modules & Integrations
config.insanetweaks.category.tweaks=Tweaks & Fixes
config.insanetweaks.category.traits=Skill Traits
config.insanetweaks.category.tombstone=Tombstone Tweaks
config.insanetweaks.category.thrall=Thrall
config.insanetweaks.category.entities=Entities
config.insanetweaks.category.client=Client & Debug
```

- [ ] **Step 4: Build** — `./gradlew build` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/client/gui/config/InsaneTweaksGuiFactory.java src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "feat: explicitly ordered config GUI with localized category names"
```

---

### Task 5: Deferred manual checklist + final review

**Files:**
- Modify: `NEXT_SESSION_SPELLS.md`

- [ ] **Step 1: Append the checklist** to `NEXT_SESSION_SPELLS.md`:

```markdown

## Config rework (plan `2026-07-07-config-rework.md`) — deferred manual checklist

- [ ] Fresh world, no old config: insanetweaks.cfg has clean lowercase sections (modules/tweaks/traits/tombstone/thrall/entities/client), thrall/entities show nested sub-sections, no "[ 1 ]" keys anywhere.
- [ ] Old-format config present at launch: backed up to insanetweaks.cfg.pre-rework, fresh file generated, log line present, one-time chat notice on first login (fires even with Suppress Startup Chat Warnings = true).
- [ ] Mod Options GUI: sections in order modules→tweaks→traits→tombstone→thrall→entities→client with localized names; thrall/entities open nested sub-screens (depth 3 renders for entities.assimilated_wizard.*).
- [ ] Live reload: change Collecting scan radius via GUI while a thrall collects — applies without restart; Enable Collecting Mode shows NO restart flag; Curse of Possession Patch and Assimilated Wizard attribute fields DO show their restart/world flags.
- [ ] Books cooldowns: set 15 min, use Book of Disenchantment on a tombstone block — cooldown ~15 min; 0 disables.
- [ ] Assimilated Wizard still spawns/converts correctly (entities.assimilated_wizard config paths wired right); thrall modes all reachable and tunables respected.
- [ ] runServer boots clean (GUI factory is @SideOnly client; verify no classloading on the dedicated server).
```

- [ ] **Step 2: Whole-diff review** — confirm commits for Tasks 1–4 landed; `git status --short` shows only the two pre-existing deleted `.md` files (and `ReskillableConfigSwapper.java.new` if still undecided); `./gradlew build` green.

- [ ] **Step 3: Commit**

```bash
git add NEXT_SESSION_SPELLS.md
git commit -m "docs: config rework deferred-testing checklist"
```

---

## Self-review results

**Spec coverage:** §1 structure/keys/moves (Task 2), §2 GUI factory + LangKeys (Task 4), §3 audit (table + deltas embodied in Task 2), §4 Books minutes (Task 1), §5 backup+notice (Task 3), §6 code split (Task 2), verification (Task 5). Out-of-scope list respected. ✓

**Placeholders:** the `// fields …` blocks in Task 2 Steps 2–3 are explicit assembly instructions (verbatim source ranges + exhaustive delta lists), not TBDs; every new/changed line of code appears in full elsewhere. ✓

**Type consistency:** category class names match between Task 2 file map, root imports (Step 4) and read-site table (Step 5); `PorterDirection` new path used consistently; Books field names match between Task 1 and Task 2's TombstoneCategory carry-over; lang keys in Task 4 Step 3 match the `@Config.LangKey` values in Task 2 Step 4. ✓

**Ordering:** Task 1 first (isolated); Task 2 is the atomic restructure; Task 3 depends on nothing from 2 but its marker only matches pre-rework files (correct in any order — shipped releases carry all tasks); Task 4 after 2 (imports the new root shape); Task 5 last. ✓
