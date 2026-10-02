# Abomination Exclusivity and Mana Re-pricing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Abomination spells castable only from our own foci or a wand carrying an Adaptation upgrade, delete the now-dead PlayerMana integration, and re-price all 13 spells and both wand capacities against an external reference point.

**Architecture:** The exclusivity rule already exists but lives inside a handler that early-returns when PlayerMana is absent, so it moves first, into a handler that always runs. Two methods buried in the PlayerMana compat class are actually Living Wand evolution machinery and are relocated before the class is deleted. Costs come from a two-index formula anchored on EB Wizardry's own numbers rather than on a percentage of our own wand.

**Tech Stack:** Minecraft 1.12.2, Forge 14.23.5.2860, Java 8. Forge event bus (`SpellCastEvent.Pre`, `RegistryEvent.MissingMappings`), Forge `@Config`, EB Wizardry spell JSONs.

**Spec:** `docs/superpowers/specs/2026-08-20-abomination-exclusivity-and-mana-rebalance-design.md`. Read it before starting — it carries the measurements behind every number here.

---

## About testing in this repo

🚨 **Do not look for a unit test task. There is none, and that is not an oversight.**

Per CLAUDE.md, `insanetweaks` has no test source set and no test framework. The only subproject with tests is `commandsuggest`, and only because its `core` package deliberately contains no Minecraft types. Every line in this plan touches `SpellCastEvent`, `ItemStack`, Forge config or a resource JSON — none of it is reachable from a plain JVM.

The verification ladder that replaces TDD here:

1. **Compile.** `./gradlew :insanetweaks:build` with `-Xlint:all`. This is a real gate: several tasks delete a class that other files import, and the compiler is what proves every call site was found.
2. **Grep.** After the deletion task, a repo-wide grep for the deleted symbols must return nothing. The task specifies the exact command.
3. **In-game.** Task 8 is the real test. It is the user's to run, not a subagent's.

Each task ends in a commit.

---

## Task order matters here

🚨 **Do not reorder these.** Three ordering constraints, each of which produces a silent failure if violated:

- **Task 1 before Task 5.** The exclusivity gate must exist in its new home before the old copy is deleted, or there is a commit range in which the feature does not exist at all.
- **Task 2 before Task 5.** The Living Wand's evolution accounting must be relocated before `PlayerManaCompat` is deleted, or wand evolution silently stops.
- **Task 3 before Task 5.** The missing-mapping handler must exist before the fruit item is unregistered, or an intermediate build shows the missing-registry screen on every world holding one.

---

## File structure

| file | responsibility |
|---|---|
| `events/SpellRestrictionEventHandler.java` | **modify** — gains the exclusivity gate (Task 1) |
| `util/SpellManaAccounting.java` | **create** — mana-to-evolution accounting, rescued from the compat class (Task 2) |
| `events/WandEventHandler.java` | **modify** — points at the new accounting class (Task 2) |
| `events/RetiredItemsRemapHandler.java` | **create** — `MissingMappings` for items this mod no longer registers (Task 3) |
| `config/categories/GearCategory.java` | **modify** — wand capacities (Task 4) |
| 13 files under `assets/insanetweaks/spells/` | **modify** — new costs (Task 6) |
| everything PlayerMana | **delete** (Task 5) |
| `insanetweaks/build.gradle`, `InsaneTweaksMod.java` | **modify** — version (Task 7) |

🚨 **Deviation from the spec, stated deliberately.** Spec §6's file table says to add the fruit's missing-mapping to `events/LegacyDormantRemapHandler`. This plan creates a separate `RetiredItemsRemapHandler` instead. That class is specifically the dormant-waystone migration — its javadoc says so — and an unrelated retired item would muddy a class with one clear job. Spec §2.3 calls it "the working precedent", which is exactly how it is used here. Task 3 updates the spec's table to match.

---

## Task 1: Move the exclusivity gate

Spec §1. This lands the feature. The old copy in `ArcaneBridgeEventHandler` is left alone until Task 5 — it is unreachable anyway (PlayerMana is absent), so there is no double-cancel.

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/events/SpellRestrictionEventHandler.java`

- [ ] **Step 1: Add the import**

At the top of the file, alongside the existing imports, add:

```java
import com.spege.insanetweaks.util.AdaptationUpgradeHelper;
```

`ModElements`, `SpellCastEvent`, `EntityPlayer`, `TextComponentString` and `TextFormatting` are all already imported in this file - `AdaptationUpgradeHelper` is the only new one.

- [ ] **Step 2: Insert the gate**

Find this block in `onSpellCastPre` (it is the creative check, immediately after the cast to `EntityPlayer`):

```java
        EntityPlayer player = (EntityPlayer) caster;
        if (player.isCreative()) {
            return;
        }

        int requiredStage = getRequiredStage(spellId);
```

Replace it with:

```java
        EntityPlayer player = (EntityPlayer) caster;
        if (player.isCreative()) {
            return;
        }

        if (isUnadaptedAbominationCast(event, player)) {
            event.setCanceled(true);
            player.sendStatusMessage(
                    new TextComponentString(TextFormatting.DARK_RED
                            + "This focus has not adapted to Abomination magic."),
                    true);
            return;
        }

        int requiredStage = getRequiredStage(spellId);
```

- [ ] **Step 3: Add the predicate method**

Add this method to the class, immediately above the existing `private boolean isBlockedWizardCaster(...)`:

```java
    /**
     * Abomination magic is castable from OUR foci - the Living/Sentient wand and spellblade - or
     * from any wand carrying an Adaptation upgrade, and from nothing else.
     *
     * <p>No new logic is needed for that rule: {@code getEffectiveAdaptationLevel} already returns 1
     * for our four items by identity and adds any applied upgrade on top, so the rule is exactly
     * "level 0 means no".
     *
     * <p>🚨 Keyed on {@code Source.WAND}, not on the spell alone, and that is load-bearing rather
     * than incidental. Our own {@code sim_wizard} casts {@code dispatcher_grasp} and holds no wand
     * of ours; gating the spell itself would silence the element on the very mobs it is named after.
     * Scrolls, commands and dispensers pass for the same reason - see the design spec's §1.2, which
     * records the scroll bypass as known and accepted rather than missed.
     *
     * <p>This check previously lived in {@code ArcaneBridgeEventHandler}, behind an early return on
     * {@code PlayerManaCompat.isAvailable()} - so it stopped working the day player_mana was
     * disabled in the pack, silently. It lives here now because this handler is gated only on
     * {@code modules.enableSpells} and has no relationship to that mod.
     */
    private boolean isUnadaptedAbominationCast(SpellCastEvent.Pre event, EntityPlayer player) {
        if (!ModElements.isAbomination(event.getSpell())
                || event.getSource() != SpellCastEvent.Source.WAND) {
            return false;
        }
        return AdaptationUpgradeHelper.getEffectiveAdaptationLevel(
                AdaptationUpgradeHelper.findCastingItem(player, event.getSpell())) <= 0;
    }
```

- [ ] **Step 4: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/events/SpellRestrictionEventHandler.java
git commit -m "feat(abomination): gate casts on an adapted focus, in a handler that runs

The rule already existed in ArcaneBridgeEventHandler, but that handler
opens with an early return on PlayerManaCompat.isAvailable() - so it has
been dead since player_mana was disabled in the pack, with nothing said
in any log. SpellRestrictionEventHandler is gated only on
modules.enableSpells and already owns the other Abomination restriction.

Keyed on Source.WAND rather than on the spell, so our own sim_wizard -
which casts dispatcher_grasp and carries no wand of ours - is unaffected.
getEffectiveAdaptationLevel already returns 1 for our four foci by
identity, so 'our wands or a wand with the upgrade' needed no new logic."
```

---

## Task 2: Rescue the mana accounting

Spec §2.2. `getConsumedMana` and `getActualCostMultiplier` are not PlayerMana support — they convert mana spent into Living Wand evolution points, and `WandEventHandler` is their only consumer. They must leave the compat class before it dies.

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/util/SpellManaAccounting.java`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/events/WandEventHandler.java`

- [ ] **Step 1: Create the new class**

```java
package com.spege.insanetweaks.util;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;

/**
 * How much mana a cast actually cost, which is how the Living Wand earns its evolution progress.
 *
 * <p>🚨 This is NOT compatibility code, despite where it used to live. It was carved out of
 * {@code PlayerManaCompat} when the player_mana integration was deleted, because deleting the whole
 * class would have silently stopped the Living Wand from evolving - the one consumer,
 * {@code WandEventHandler}, converts the value returned here into evolution points.
 *
 * <p>What is kept is the branch that ran when player_mana was absent, which is the branch that has
 * been running in the pack ever since the mod was disabled. The player_mana branch is gone with the
 * mod.
 */
public final class SpellManaAccounting {

    private SpellManaAccounting() {
    }

    /**
     * The cost multiplier actually applied to this cast - wand discounts, upgrades and any
     * modifier another mod set.
     */
    public static float getActualCostMultiplier(SpellModifiers modifiers) {
        if (modifiers == null) {
            return 1.0f;
        }
        return modifiers.get(SpellModifiers.COST);
    }

    /**
     * Mana consumed by one cast.
     *
     * <p>🚨 The continuous branch divides by 20 on purpose and the division is load-bearing: a
     * continuous spell's JSON {@code cost} is per SECOND, while {@code SpellCastEvent.Finish}
     * reports how many TICKS the channel lasted. Without the divide, a five-second channel would
     * credit the wand with a hundred casts' worth of evolution.
     */
    public static double getConsumedMana(SpellCastEvent event) {
        if (event == null || event.getSpell() == null) {
            return 0.0D;
        }

        Spell spell = event.getSpell();
        float multiplier = getActualCostMultiplier(event.getModifiers());
        double baseCost = spell.getCost() * multiplier;

        if (spell.isContinuous && event instanceof SpellCastEvent.Finish) {
            return (baseCost * ((SpellCastEvent.Finish) event).getCount()) / 20.0D;
        }

        return baseCost;
    }
}
```

- [ ] **Step 2: Repoint the one consumer**

In `insanetweaks/src/main/java/com/spege/insanetweaks/events/WandEventHandler.java`, change the import on line 22 from:

```java
import com.spege.insanetweaks.util.PlayerManaCompat;
```

to:

```java
import com.spege.insanetweaks.util.SpellManaAccounting;
```

and change line 93 from:

```java
        double consumed = PlayerManaCompat.getConsumedMana(event);
```

to:

```java
        double consumed = SpellManaAccounting.getConsumedMana(event);
```

- [ ] **Step 3: Verify no other consumer exists**

Run:

```bash
grep -rn "getConsumedMana\|getActualCostMultiplier" --include=*.java insanetweaks/src
```

Expected: hits only in `SpellManaAccounting.java`, `WandEventHandler.java`, and `PlayerManaCompat.java` (which Task 5 deletes). If any other file appears, stop and report — it means a consumer this plan did not account for.

- [ ] **Step 4: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/util/SpellManaAccounting.java insanetweaks/src/main/java/com/spege/insanetweaks/events/WandEventHandler.java
git commit -m "refactor(wand): move mana accounting out of the player_mana compat class

getConsumedMana and getActualCostMultiplier are Living Wand evolution
machinery, not compatibility code - WandEventHandler turns their result
into evolution points. They only lived in PlayerManaCompat because that
class also knew how to read player_mana's cost table. Deleting the class
wholesale, which the next commits do, would have stopped wand evolution
with nothing in any log.

Only the non-player_mana branch is kept, which is the branch that has
been running since the mod was disabled in the pack."
```

---

## Task 3: Missing-mapping handler for retired items

Spec §2.3. This must exist before the fruit stops being registered.

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/events/RetiredItemsRemapHandler.java`
- Modify: `docs/superpowers/specs/2026-08-20-abomination-exclusivity-and-mana-rebalance-design.md`

- [ ] **Step 1: Create the handler**

```java
package com.spege.insanetweaks.events;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Items this mod used to register and no longer does.
 *
 * <p>🚨 Forge does not quietly forget a registry entry that disappears. An id still referenced by a
 * saved world - an item sitting in a chest, in an inventory, in an item frame - produces the
 * missing-registry screen on load, and the player is offered the choice between losing the world's
 * registry mapping and not playing. A retired item therefore needs a mapping decision recorded here
 * for as long as any world might still contain one, which in practice means forever.
 *
 * <p>{@code ignore()} rather than {@code remap()}: there is nothing to redirect these to.
 * {@code ignore()} drops the stack and leaves the numeric slot dead without shifting any other id.
 *
 * <p>Distinct from {@link LegacyDormantRemapHandler}, which is one specific block's migration to
 * another mod and remaps rather than ignores.
 *
 * <h3>Retired</h3>
 * <ul>
 * <li>{@code arcane_adapted_fruit} - the Arcane Adapted Fruit, retired with the whole player_mana
 * integration in 1.18.0. It granted a mana-regeneration bonus in a mod the pack no longer runs.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public final class RetiredItemsRemapHandler {

    private static final String[] RETIRED_PATHS = {
            "arcane_adapted_fruit"
    };

    private RetiredItemsRemapHandler() {
    }

    @SubscribeEvent
    public static void onMissingItems(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getMappings()) {
            for (String retired : RETIRED_PATHS) {
                if (retired.equals(mapping.key.getResourcePath())) {
                    mapping.ignore();
                    InsaneTweaksMod.LOGGER.info(
                            "[InsaneTweaks] Dropping retired item '{}' from this world's registry.",
                            mapping.key);
                    break;
                }
            }
        }
    }
}
```

- [ ] **Step 2: Correct the spec's file table**

In `docs/superpowers/specs/2026-08-20-abomination-exclusivity-and-mana-rebalance-design.md`, §6, replace the row:

```
| `events/LegacyDormantRemapHandler.java` | add the fruit to `MissingMappings<Item>` (§2.3) |
```

with:

```
| `events/RetiredItemsRemapHandler.java` | **new** — `MissingMappings<Item>` for retired items (§2.3) |
```

- [ ] **Step 3: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/events/RetiredItemsRemapHandler.java docs/superpowers/specs/2026-08-20-abomination-exclusivity-and-mana-rebalance-design.md
git commit -m "feat(compat): missing-mapping handler for retired items

Lands before the Arcane Adapted Fruit is unregistered, not after: Forge
answers a saved id with no registry entry by showing the missing-registry
screen, so any commit between the item's removal and this handler would
break every world holding one.

Its own class rather than an addition to LegacyDormantRemapHandler -
that one is a specific block's migration to another mod and remaps;
this one is a list of ids that are simply gone, and ignores."
```

---

## Task 4: Wand capacities

Spec §4. Done before the cost re-pricing because the costs in Task 6 were calibrated against the 3000 figure, and doing capacities first means no commit exists where cheap spells meet the old huge pool.

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/GearCategory.java`

- [ ] **Step 1: Living Wand capacity**

Find:

```java
                "Read live - no restart needed. Default 4000." })
        @Config.RangeInt(min = 100, max = 100000)
        public int livingManaCapacity = 4000;
```

Replace with:

```java
                "Read live - no restart needed. Default 3000." })
        @Config.RangeInt(min = 100, max = 100000)
        public int livingManaCapacity = 3000;
```

- [ ] **Step 2: Sentient Wand capacity**

Find:

```java
                "Read live - no restart needed. Default 6500." })
        @Config.RangeInt(min = 100, max = 100000)
        public int sentientManaCapacity = 6500;
```

Replace with:

```java
                "Read live - no restart needed. Default 3500." })
        @Config.RangeInt(min = 100, max = 100000)
        public int sentientManaCapacity = 3500;
```

- [ ] **Step 3: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/GearCategory.java
git commit -m "balance(wands): Living 4000 -> 3000, Sentient 6500 -> 3500

Both wands were far above EB Wizardry's own master wand (2500), which is
what let the old percentage-of-our-own-pool pricing rule drift as far as
it did. Safe to lower despite mana being stored as damage: the negative
clamp in BaseCustomWandItem.onUpdate pins an over-charged wand to empty
rather than letting it report negative mana and refuse every spell.

Sentient keeps a 17% edge rather than the 8% a 3250 figure would have
left it, because at full evolution the Living Wand matches its 0.20 cost
reduction exactly - capacity is the only advantage it has left."
```

---

## Task 5: Delete the PlayerMana layer

Spec §2.1. The largest task, but mechanical: the compiler finds every call site.

**Files:** deletions and edits listed per step.

- [ ] **Step 1: Delete the files**

```bash
git rm insanetweaks/src/main/java/com/spege/insanetweaks/items/bridge/ArcaneAdaptedFruitItem.java \
       insanetweaks/src/main/java/com/spege/insanetweaks/util/ArcaneAdaptedFruitHelper.java \
       insanetweaks/src/main/java/com/spege/insanetweaks/util/PlayerManaCompat.java \
       insanetweaks/src/main/java/com/spege/insanetweaks/util/PlayerManaContext.java \
       insanetweaks/src/main/java/com/spege/insanetweaks/events/ArcaneBridgeEventHandler.java \
       insanetweaks/src/main/java/com/spege/insanetweaks/mixins/playermana/MixinPlayerManaEventsHandler.java \
       insanetweaks/src/main/resources/mixins.insanetweaks.playermana.json \
       insanetweaks/src/main/resources/assets/insanetweaks/models/item/arcane_adapted_fruit.json \
       insanetweaks/src/main/resources/assets/insanetweaks/textures/items/arcane_adapted_fruit.png
```

- [ ] **Step 2: `LateMixinBooter` — drop the mixin config**

In `insanetweaks/src/main/java/com/spege/insanetweaks/core/LateMixinBooter.java`, delete these three lines:

```java
        if (net.minecraftforge.fml.common.Loader.isModLoaded("player_mana")) {
            configs.add("mixins.insanetweaks.playermana.json");
        }
```

- [ ] **Step 3: `ModItems` — drop the item**

Delete the field declaration:

```java
    public static final Item ARCANE_ADAPTED_FRUIT = new ArcaneAdaptedFruitItem();
```

Change the registration from:

```java
            event.getRegistry().registerAll(ADAPTATION_UPGRADE, ARCANE_ADAPTED_FRUIT, MAGIC_NUCLEUS);
```

to:

```java
            event.getRegistry().registerAll(ADAPTATION_UPGRADE, MAGIC_NUCLEUS);
```

Delete the model registration:

```java
            registerModel(ARCANE_ADAPTED_FRUIT);
```

And delete the now-unused `import com.spege.insanetweaks.items.bridge.ArcaneAdaptedFruitItem;` if present.

- [ ] **Step 4: `InsaneTweaksMod` — drop the handler registration**

Delete this line (it sits inside the `enableSrpEbWizardryBridge` block):

```java
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ArcaneBridgeEventHandler());
```

- [ ] **Step 5: `CommandInsaneTweaks` — drop the subcommand**

🚨 The subcommand is `claimfruit`, not `claimarcanefruit` — the latter is a string constant in the deleted helper and was never the command.

Delete the import:

```java
import com.spege.insanetweaks.util.ArcaneAdaptedFruitHelper;
```

Delete the switch case:

```java
            case "claimfruit":
                handleClaimFruit(sender);
                break;
```

Delete the whole `handleClaimFruit` method:

```java
    private void handleClaimFruit(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);

        if (!ArcaneAdaptedFruitHelper.hasPendingFruit(player)) {
            player.sendMessage(new TextComponentString(TextFormatting.GRAY + "You have no Arcane Adapted Fruit waiting to be claimed."));
            return;
        }

        if (!ArcaneAdaptedFruitHelper.tryGiveFruit(player)) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "You still need at least one free inventory slot."));
        }
    }
```

Change the usage string from:

```java
        return "/itweaks <claimfruit | grantbook | propertybook | codexpool>";
```

to:

```java
        return "/itweaks <grantbook | propertybook | codexpool>";
```

And delete the help line:

```java
        sender.sendMessage(new TextComponentString("§e/itweaks claimfruit§7 - Claim your Arcane Adapted Fruit"));
```

- [ ] **Step 6: `ItemZhonyasHourglassArtefact` — EB mana becomes the only cost model**

Delete the `import com.spege.insanetweaks.util.PlayerManaCompat;`.

Replace the mode predicate:

```java
    /**
     * The item's own EB mana pool is the active cost model only when player_mana is absent
     * AND the fallback is enabled in config. Otherwise cost is player mana, or the item is inert.
     */
    private static boolean isEbManaMode() {
        return !PlayerManaCompat.isAvailable() && ModConfig.tweaks.zhonyaEbManaFallback;
    }
```

with:

```java
    /**
     * The item's own EB mana pool is the cost model. It used to be the fallback for when player_mana
     * was absent; that integration is gone, so the fallback is now the only path and the method is
     * kept only because several call sites read better with a name than with {@code true}.
     */
    private static boolean isEbManaMode() {
        return true;
    }
```

Replace the entire three-branch cost block - from the `if (PlayerManaCompat.isAvailable())` line
down to the closing brace of the final `else` - with the single path that survives:

```java
        if (PlayerManaCompat.isAvailable()) {
            double currentMana = PlayerManaCompat.getCurrentMana(player);
            if (currentMana < ModConfig.tweaks.zhonyaMinMana) {
                player.sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "[Zhonyas] Not enough mana ("
                    + (int) currentMana + "/" + ModConfig.tweaks.zhonyaMinMana + ")."));
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }
            PlayerManaCompat.setCurrentMana(player, 0.0D);
        } else if (ModConfig.tweaks.zhonyaEbManaFallback) {
            if (getMana(stack) < getManaCapacity(stack)) {
                player.sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "[Zhonyas] The hourglass is not fully charged ("
                    + getMana(stack) + "/" + getManaCapacity(stack) + " mana)."));
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }
            setMana(stack, 0);
        } else {
            player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "[Zhonyas] The hourglass is inert without a mana source "
                + "(install player_mana or enable the EB-mana fallback in config)."));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
```

becomes exactly this:

```java
        // Cost: the hourglass's own EB mana pool, spent in full. This used to be the middle of
        // three branches - player_mana first, this as its fallback, an inert third when neither
        // was available. player_mana is gone, so this is simply what the hourglass costs.
        if (getMana(stack) < getManaCapacity(stack)) {
            player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "[Zhonyas] The hourglass is not fully charged ("
                + getMana(stack) + "/" + getManaCapacity(stack) + " mana)."));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        setMana(stack, 0);
```

The two Polish comment lines directly above that block describe the three-branch structure and are
now false. Replace them with nothing - the new comment above says what is left.

Then check whether `zhonyaMinMana` still has a reader:

```bash
grep -rn "zhonyaMinMana" --include=*.java insanetweaks/src
```

If its declaration in `TweaksCategory` is the only hit left, delete that field in Step 8 as well: it
configured a threshold against a mana pool that no longer exists.

Replace the tooltip branch:

```java
        if (isEbManaMode()) {
            tooltip.add(TextFormatting.AQUA + "Charge: " + getMana(stack) + " / " + getManaCapacity(stack) + " mana");
            tooltip.add(TextFormatting.RED + "Cost: a full charge (recharge with a Mana Flask while held).");
        } else if (PlayerManaCompat.isAvailable()) {
            tooltip.add(TextFormatting.RED + "Cost: ALL of your current mana.");
        } else {
            tooltip.add(TextFormatting.DARK_GRAY + "Inert: needs player_mana or the EB-mana fallback.");
        }
```

with:

```java
        tooltip.add(TextFormatting.AQUA + "Charge: " + getMana(stack) + " / " + getManaCapacity(stack) + " mana");
        tooltip.add(TextFormatting.RED + "Cost: a full charge (recharge with a Mana Flask while held).");
```

- [ ] **Step 7: `BridgeSpellblade` — drop the mana flag**

The whole `onUpdate` body below `super.onUpdate(...)` exists only to write a `mana_available` NBT flag from player_mana. Replace:

```java
    @Override
    public void onUpdate(@Nonnull ItemStack stack, @Nonnull World world, @Nonnull Entity entity, int itemSlot,
            boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);

        if (world.isRemote || !(entity instanceof EntityPlayer) || !PlayerManaCompat.isAvailable()) {
            return;
        }

        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }

        NBTTagCompound nbt = stack.getTagCompound();
        if (nbt != null && PlayerManaCompat.hasUsableMana((EntityPlayer) entity)) {
            nbt.setBoolean("mana_available", true);
        }
    }
```

with:

```java
    @Override
    public void onUpdate(@Nonnull ItemStack stack, @Nonnull World world, @Nonnull Entity entity, int itemSlot,
            boolean isSelected) {
        super.onUpdate(stack, world, entity, itemSlot, isSelected);
    }
```

Then check whether `mana_available` is read anywhere:

```bash
grep -rn "mana_available" --include=*.java insanetweaks/src
```

If the only remaining hits are in this file, delete the override entirely rather than leaving one that does nothing but call `super`. If something else reads the flag, stop and report — the flag then has a consumer this plan did not account for.

Delete the `import com.spege.insanetweaks.util.PlayerManaCompat;` and any import left unused by the deletion (`NBTTagCompound` may become unused — `-Xlint:all` will not fail on it, so remove it by inspection).

- [ ] **Step 8: `TweaksCategory` — drop the dead flag**

Delete the `zhonyaEbManaFallback` field together with its `@Config.Name` and `@Config.Comment` annotations.

- [ ] **Step 9: Lang files**

Delete the line `item.arcane_adapted_fruit.name=Arcane Adapted Fruit` from
`insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang` and the corresponding
`item.arcane_adapted_fruit.name=...` line from `ru_ru.lang`.

- [ ] **Step 10: Prove nothing is left**

Run:

```bash
grep -rn "PlayerManaCompat\|PlayerManaContext\|ArcaneAdaptedFruit\|arcane_adapted_fruit\|ArcaneBridgeEventHandler\|zhonyaEbManaFallback\|playermana" --include=* insanetweaks/src
```

Expected: **no output at all.** Any hit is a call site this task missed. (`player_mana` as a word may still legitimately appear in a comment; if so, judge whether the comment is still true and update it rather than leaving a reference to a mod the code no longer knows about.)

- [ ] **Step 11: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`. This step is the real verification for this task — nine files were deleted and the compiler is what proves every reference was found.

- [ ] **Step 12: Commit**

```bash
git add -A insanetweaks/src
git commit -m "feat(bridge)!: remove the player_mana integration

player_mana is disabled in the pack, so every part of this was already
inert: the fruit granted a regen bonus in a mod that is not running, and
ArcaneBridgeEventHandler early-returned on isAvailable() before reaching
any of its logic.

Removed: the Arcane Adapted Fruit item, its helper, its model, texture
and lang entries, the player_mana mixin and its whole mixin config,
PlayerManaCompat, PlayerManaContext, ArcaneBridgeEventHandler, the
/itweaks claimfruit subcommand, and the zhonyaEbManaFallback flag - with
player_mana gone the fallback is the only cost model Zhonya's Hourglass
has, and a flag choosing between one option is worse than none.

The two things that had to survive left first: the Living Wand's mana
accounting moved to SpellManaAccounting, and RetiredItemsRemapHandler
answers the fruit's id so existing worlds do not meet the missing-registry
screen. The obtain_living_sentient_gear advancement needs no rescue - its
JSON is four vanilla inventory_changed criteria under one OR requirement,
so vanilla awards it without the deleted handler's help."
```

---

## Task 6: Re-price the 13 spells

Spec §3.3.

**Files:**
- Modify: 13 files under `insanetweaks/src/main/resources/assets/insanetweaks/spells/`

- [ ] **Step 1: Apply the new costs**

Only the `cost` field changes. `chargeup` and `cooldown` are deliberately untouched (spec §3.4).

| file | old `cost` | new `cost` |
|---|---|---|
| `dispatcher_grasp.json` | 130 | 70 |
| `immune_bond.json` | 140 | 75 |
| `yelloweye_gland.json` | 150 | 80 |
| `summon_thrall.json` | 520 | 200 |
| `summon_fer_cow.json` | 540 | 210 |
| `parasite_shroud.json` | 600 | 230 |
| `summon_wizard.json` | 640 | 245 |
| `summon_primitive_yelloweye.json` | 700 | 270 |
| `cleanse.json` | 760 | 290 |
| `summon_light_bomber.json` | 760 | 290 |
| `summon_primitive_summoner.json` | 850 | 325 |
| `purifying_pulse.json` | 1100 | 350 |
| `call_of_demise.json` | 1800 | 500 |

`test_projectile.json` (cost 15) is **not** in the list and must not be touched.

This script applies exactly those thirteen edits and refuses to touch a file whose current value is not the expected one:

```bash
cd insanetweaks/src/main/resources/assets/insanetweaks/spells
while IFS='|' read -r file old new; do
  if ! grep -q "\"cost\"[[:space:]]*:[[:space:]]*${old}\b" "$file"; then
    echo "SKIPPED $file - expected cost ${old}, not found"; continue
  fi
  sed -i "s/\"cost\"[[:space:]]*:[[:space:]]*${old}\b/\"cost\": ${new}/" "$file"
  echo "ok $file ${old} -> ${new}"
done <<'ROWS'
dispatcher_grasp.json|130|70
immune_bond.json|140|75
yelloweye_gland.json|150|80
summon_thrall.json|520|200
summon_fer_cow.json|540|210
parasite_shroud.json|600|230
summon_wizard.json|640|245
summon_primitive_yelloweye.json|700|270
cleanse.json|760|290
summon_light_bomber.json|760|290
summon_primitive_summoner.json|850|325
purifying_pulse.json|1100|350
call_of_demise.json|1800|500
ROWS
```

Expected: thirteen `ok` lines, no `SKIPPED`. A `SKIPPED` line means that file's cost was not what this plan believed — stop and report rather than editing it by hand.

- [ ] **Step 2: Verify every value and that nothing else moved**

```bash
cd insanetweaks/src/main/resources/assets/insanetweaks/spells
grep -H '"cost"\|"cooldown"\|"chargeup"' *.json | sed 's/ *//g'
```

Check against the table above, and confirm every `chargeup` and `cooldown` still matches what it was before this task (`git diff` should show **only** `cost` lines changed).

```bash
git diff --stat
git diff | grep '^[-+]' | grep -v '^[-+][-+]' | grep -v '"cost"'
```

Expected: the second command prints nothing.

- [ ] **Step 3: Compile**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`. (JSON is a resource, so this only proves the build still packages — the real check is Step 2.)

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/resources/assets/insanetweaks/spells
git commit -m "balance(spells): re-price the Abomination family against EB Wizardry

The old rule expressed cost as a percentage of our own wand, so wand and
spells could drift together indefinitely with the rule still reporting
success. Measured against the mod we are a guest in, call_of_demise at
1800 cost more than ten times summon_iron_golem (175), the most expensive
spell EB Wizardry ships, and our 'tools' at 130-150 were priced like its
master spells.

New prices come from two indices anchored outside this mod: burst against
EBW's 7% of-pool ceiling, sustained against its 7.0 mana/s master mean.
The data held a surprise worth recording - our sustained pricing was
never the problem (summon_thrall sat at 8.7 mana/s against EBW's 7.0, and
the big rituals were cheaper to sustain than EBW's average). Only burst
was broken.

Cooldowns are untouched, so rituals are now gated by time rather than by
mana. That is accepted: a ritual's constraint should be the cooldown and
its cost a tax. If they come to feel free, the lever is the cooldown, not
a return to four-figure costs."
```

---

## Task 7: Version bump

**Files:**
- Modify: `insanetweaks/build.gradle`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`

- [ ] **Step 1: Read both current values**

```bash
grep -n "^version" insanetweaks/build.gradle
grep -n 'String VERSION' insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
```

They must agree before you change anything. 🚨 Per CLAUDE.md these two have drifted before, and `InsaneTweaksMod.VERSION` is the one that matters — it is what `@Mod` reports and what Forge prints in the mod list. `mcmod.info` and the jar manifest derive from `build.gradle`, so they need no edit.

- [ ] **Step 2: Bump both to 1.18.0**

A registered item is removed and every spell in the family is re-priced — a minor bump at least. The previous release was 1.17.0.

`insanetweaks/build.gradle`:

```groovy
version = '1.18.0'
```

`InsaneTweaksMod.java`:

```java
    public static final String VERSION = "1.18.0";
```

- [ ] **Step 3: Build**

Run: `./gradlew :insanetweaks:build`
Expected: `BUILD SUCCESSFUL`, and `insanetweaks/build/libs/insanetweaks-1.18.0.jar` exists.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/build.gradle insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "chore(insanetweaks): 1.18.0 - Abomination exclusivity, player_mana removal, re-pricing"
```

---

## Task 8: Deploy and verify in game

Spec §5. **Everything before this only proves the code compiles.**

🚨 **Steps 3 onward cannot be done by an agent** — they need a running Minecraft client and a person looking at it. An agent may do Steps 1 and 2 and must then hand over rather than claiming any of the rest.

- [ ] **Step 1: Deploy**

Remove the old jar first — two jars of one modid is a duplicate-mod crash.

```bash
rm -f "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/insanetweaks-"*.jar
cp insanetweaks/build/libs/insanetweaks-1.18.0.jar "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/"
```

- [ ] **Step 2: Confirm the mixin config is gone**

```bash
grep -c "playermana" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log" || true
```

After the next launch this must be `0`. The **absence** of those `APPLY` lines is the evidence here — there is no new mixin to look for.

- [ ] **Step 3: Load an existing world**

Expected: **no missing-registry screen.** This is Task 3 working. If one appears, do not click through it — report, because clicking the wrong option rewrites the world's registry.

- [ ] **Step 4: The gate refuses an unadapted focus**

Give yourself an EB Wizardry master wand, put an Abomination spell on it at an arcane workbench, and cast. Expected: refused, with `This focus has not adapted to Abomination magic.` on the action bar.

Apply an Adaptation upgrade to that same wand and cast again. Expected: it works.

- [ ] **Step 5: Our own foci need no upgrade**

Cast an Abomination spell from a Living Wand and from a Living Spellblade, neither carrying an Adaptation upgrade. Expected: both work — `getDefaultAdaptationLevel` grants them level 1 by identity.

- [ ] **Step 6: The NPC carve-out still holds**

Find or spawn a `sim_wizard` and watch it fight. Expected: it still casts `dispatcher_grasp`. If it has gone silent, the gate was written against the spell rather than against `Source.WAND`.

- [ ] **Step 7: Wand evolution still accrues**

Cast a continuous spell from a Living Wand and confirm its evolution progress moves. Expected: it does. **This failure is silent** — nothing is logged if the accounting was lost, which is why it has its own step.

- [ ] **Step 8: The advancement still fires**

On a profile that has never held one, pick up a Living Wand. Expected: the `obtain_living_sentient_gear` toast appears, granted by vanilla's own criteria rather than by the deleted handler.

- [ ] **Step 9: An over-charged wand degrades safely**

Take a wand charged above the new ceiling into the world. Expected: it reads as empty and recharges normally — **not** negative, and not refusing every spell while claiming to be non-empty.

- [ ] **Step 10: Spot-check the new prices**

On a fully-evolved Living Wand, confirm `call_of_demise` costs 500 raw (400 after the 20% reduction) and that a full wand affords roughly seven casts.

---

## Done when

- `./gradlew :insanetweaks:build` succeeds.
- The grep in Task 5 Step 10 returns nothing.
- An existing world loads with no missing-registry screen.
- An unadapted foreign wand is refused; the same wand with an Adaptation upgrade is not; our own foci never are.
- `sim_wizard` still casts `dispatcher_grasp`.
- Living Wand evolution still accrues from casting.
- The new costs read as in Task 6's table.
