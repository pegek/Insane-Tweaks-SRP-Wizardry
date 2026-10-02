# Concentration Cools Stowed Wands — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a pack running Wizardry's `wandsMustBeHeldToDecrementCooldown`, each level of Tombstone's Concentration perk restores 10% of the normal cooldown rate to every wand the player is carrying but not holding.

**Architecture:** One `PlayerTickEvent` listener in `tombtweaks`, plus one config section. No mixins: `WandHelper.decrementCooldowns` is public static, so we call the same method Wizardry calls, alongside its own path rather than inside it. A per-player fractional carry keeps the rate exact for any config value.

**Tech Stack:** Minecraft 1.12.2, Forge 14.23.5.2860, Java 8. Corail Tombstone 4.8.0, Electroblob's Wizardry 4.3.19 (CurseMaven file 8320066), both already compile dependencies of `tombtweaks`.

**Spec:** `docs/superpowers/specs/2026-08-13-tombstone-concentration-wand-cooldown-design.md`

---

## 🚨 Read before Task 1

**There are no unit tests in this subproject, and none can be added.** `CLAUDE.md` records that `commandsuggest` is the only subproject with tests, because its `core` package deliberately contains no Minecraft types. Every line of this feature touches `EntityPlayer`, `ItemStack` and Forge registries, so a JUnit test would need a running game. The verification steps below replace TDD with what actually proves something in this repo: `javap` against the real jars, a clean compile, and an in-game test whose **control case is run first**.

**Do not report this feature as working on the strength of a clean build.** A clean build proves the names resolve, nothing more.

**Branch:** `feat/tombstone-480-port`. Do not create a new one; this continues that line of work. Leave any modified files under `srpwizmixins/` alone — that is unrelated in-flight work and must not be staged.

---

## File Structure

| file | responsibility |
|---|---|
| `tombtweaks/src/main/java/com/spege/tombtweaks/config/categories/TombstoneCategory.java` | **modify** — add the `concentrationcooldown` section and its field |
| `tombtweaks/src/main/java/com/spege/tombtweaks/wizardry/ConcentrationCooldownHandler.java` | **create** — the entire feature: tick listener, rate carry, inventory walk |
| `tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java` | **modify** — register the handler beside the existing Wizardry gate |
| `tombtweaks/build.gradle`, `tombtweaks/src/main/resources/mcmod.info` | **modify** — version 1.8.3 → 1.9.0 |

The handler is one file on purpose. It is a single event listener with one loop and one piece of state; splitting the carry into its own class would spread six lines across two files for no boundary anyone benefits from.

---

### Task 1: Confirm the anchors still exist

Everything this feature calls belongs to two other mods. If a jar has moved on since the spec was written, that must surface now and not as a `NoSuchMethodError` after deploy.

**Files:** none — verification only.

- [ ] **Step 1: Check the four Tombstone and Wizardry members**

Run, from the repo root:

```bash
javap -p -cp libs/tombstone-1.12.2-4.8.0.jar ovh.corail.tombstone.registry.ModPerks | grep -i concentration
```

Expected: `public static final ovh.corail.tombstone.api.capability.Perk concentration;`

- [ ] **Step 2: Check the perk-level accessor**

```bash
javap -p -cp libs/tombstone-1.12.2-4.8.0.jar ovh.corail.tombstone.helper.EntityHelper | grep -i getPerkLevelWithBonus
```

Expected: `public static int getPerkLevelWithBonus(net.minecraft.entity.player.EntityPlayer, ovh.corail.tombstone.api.capability.Perk);`

- [ ] **Step 3: Check Wizardry's cooldown method and setting**

The Wizardry jar is not in `libs/` — it comes from CurseMaven and lives in the Gradle cache. Locate it, then read it:

```bash
find ~/.gradle/caches -name "ElectroblobsWizardry-265642-8320066*.jar" -not -name "*sources*" | head -1
```

Expected: one path ending `ElectroblobsWizardry-265642-8320066_mapped_snapshot_20171003-1.12.jar`. Using `$EBW` for that path:

```bash
javap -p -cp "$EBW" electroblob.wizardry.util.WandHelper | grep -i decrementCooldowns
```

Expected: `public static void decrementCooldowns(net.minecraft.item.ItemStack);`

```bash
javap -p -cp "$EBW" electroblob.wizardry.Settings | grep -i wandsMustBeHeld
```

Expected: `public boolean wandsMustBeHeldToDecrementCooldown;`

- [ ] **Step 4: Stop if any of the four differ**

If any expected line is missing or has a different signature, do not continue. Report which one changed — the spec's design rests on all four, and a substitute needs a design decision, not a patch.

---

### Task 2: Config section

**Files:**
- Modify: `tombtweaks/src/main/java/com/spege/tombtweaks/config/categories/TombstoneCategory.java`

- [ ] **Step 1: Add the section field next to the wand soulbinding one**

Find this existing declaration (around line 206):

```java
    public WandSoulbindingConfig wandSoulbinding = new WandSoulbindingConfig();
```

Insert immediately **after** it:

```java

    @Config.Name("concentrationcooldown")
    @Config.Comment({"Lets the Concentration perk cool down wands you are carrying but not holding.",
            "Only does anything when Electroblob's Wizardry is set to stop cooldowns on stowed",
            "wands (its own wandsMustBeHeldToDecrementCooldown option). With that off, cooldowns",
            "already run everywhere and there is nothing for the perk to restore.",
            "A wand in your off hand counts as held by Wizardry's own rule, so it already cools at",
            "full rate and this never touches it."})
    public ConcentrationCooldownConfig concentrationCooldown = new ConcentrationCooldownConfig();
```

- [ ] **Step 2: Add the section class after WandSoulbindingConfig**

Find the closing brace of `WandSoulbindingConfig` (around line 533), directly above the `FIRST-KILL REWARDS` banner comment. Insert this **between** them:

```java

    /**
     * Concentration's reach into Electroblob's Wizardry.
     *
     * <p>The perk's own description promises two things — shorter casting and immunity to
     * interruption. Wizardry has no interruption at all (verified: nothing in the mod calls
     * {@code stopActiveHand} or {@code resetActiveHand}), and its casting time is a per-spell
     * balance value we deliberately leave alone. What is left, and what this section governs, is an
     * axis Wizardry itself does not offer: whether a stowed wand cools down.
     */
    public static class ConcentrationCooldownConfig {

        @Config.Name("Enabled")
        @Config.Comment("Let Concentration cool stowed wands. Read live - no restart needed.")
        public boolean enabled = true;

        @Config.Name("Percent Per Level")
        @Config.RangeInt(min = 0, max = 100)
        @Config.Comment({"Share of the normal cooldown rate each Concentration level gives back to a",
                "wand in your main inventory. Concentration caps at 2 levels, not the 5 most",
                "Tombstone perks allow, so the default 10 means a stowed wand cools at one fifth of",
                "the speed it would in your hand. Raise it to 25 if you want a fully levelled perk",
                "to reach half speed.",
                "0 switches the effect off without disabling the feature."})
        public int percentPerLevel = 10;

        @Config.Name("Scan Interval Ticks")
        @Config.RangeInt(min = 1, max = 100)
        @Config.Comment({"How often the inventory is walked, in ticks. Purely a cost dial: the rate",
                "above is preserved whatever this is set to, because the fractional remainder is",
                "carried between scans rather than rounded away.",
                "Raise it on a busy server, lower it if you want cooldowns to move more smoothly."})
        public int scanIntervalTicks = 10;
    }
```

- [ ] **Step 3: Compile**

```bash
./gradlew :tombtweaks:compileJava
```

Expected: `BUILD SUCCESSFUL`. Warnings about Guava's `CompatibleWith` and a deprecated `BaublesApi.getBaublesHandler` are pre-existing and expected — anything else is yours.

- [ ] **Step 4: Commit**

```bash
git add tombtweaks/src/main/java/com/spege/tombtweaks/config/categories/TombstoneCategory.java
git commit -m "feat(tombtweaks): config section for Concentration wand cooldown"
```

---

### Task 3: The handler

**Files:**
- Create: `tombtweaks/src/main/java/com/spege/tombtweaks/wizardry/ConcentrationCooldownHandler.java`

- [ ] **Step 1: Write the whole class**

```java
package com.spege.tombtweaks.wizardry;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.config.categories.TombstoneCategory.ConcentrationCooldownConfig;

import electroblob.wizardry.Wizardry;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.util.WandHelper;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import ovh.corail.tombstone.helper.EntityHelper;
import ovh.corail.tombstone.registry.ModPerks;

/**
 * Concentration cools the wands you are carrying but not holding.
 *
 * <p>Wizardry's {@code ItemWand.onUpdate} decrements a wand's spell cooldowns every tick, but only
 * when the wand is held — {@code isSelected || areItemStacksEqual(stack, getHeldItemOffhand())} —
 * unless {@code wandsMustBeHeldToDecrementCooldown} is off. On a pack that leaves it on, a wand in
 * the backpack never cools at all. Each level of Concentration gives back a share of that rate.
 *
 * <p>No mixin: {@code WandHelper.decrementCooldowns} is public static and is the very method
 * Wizardry calls. We add ticks <i>beside</i> its path rather than patching it — Wizardry still
 * declines to cool a stowed wand, and its behaviour with the setting off, or in the off hand, is
 * left exactly as the author wrote it.
 *
 * <h3>Why the carry</h3>
 * {@code decrementCooldowns} removes exactly one tick per call, so a fractional rate has to become
 * "how many calls per scan". Doing that by calling once every {@code 100 / percent} ticks collapses
 * levels onto the same interval — at 10% per level, levels 4 and 5 both round to every other tick,
 * and the fifth level of the perk buys the player nothing. Keeping the remainder makes every level
 * distinct and honours any configured percentage exactly.
 *
 * <p>Server side only. Cooldowns live in stack NBT, the server copy is authoritative, and a
 * player's own container resyncs changed stacks every tick — so a stowed wand is correct by the
 * time it is drawn.
 */
public class ConcentrationCooldownHandler {

    /** Fractional cooldown ticks owed to each player, carried between scans. Bounded by players online. */
    private final Map<UUID, Double> carry = new HashMap<UUID, Double>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }

        ConcentrationCooldownConfig cfg = TombTweaksConfig.tombstone.concentrationCooldown;
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks || !cfg.enabled) {
            return;
        }
        if (cfg.percentPerLevel <= 0) {
            return;
        }

        // Wizardry's own rule. With this off, cooldowns already run everywhere and there is
        // nothing to give back — granting anything here would double-tick a held wand.
        if (!Wizardry.settings.wandsMustBeHeldToDecrementCooldown) {
            return;
        }

        int interval = Math.max(1, cfg.scanIntervalTicks);
        if (player.world.getTotalWorldTime() % interval != 0L) {
            return;
        }

        // Cheapest checks first, then the capability lookup: a player without the perk costs one
        // of these per interval and never reaches the loop.
        int level = EntityHelper.getPerkLevelWithBonus(player, ModPerks.concentration);
        if (level <= 0) {
            return;
        }

        UUID id = player.getUniqueID();
        double owed = (level * cfg.percentPerLevel / 100.0D) * interval;
        Double pending = carry.get(id);
        if (pending != null) {
            owed += pending.doubleValue();
        }
        int steps = (int) owed;
        carry.put(id, Double.valueOf(owed - steps));
        if (steps <= 0) {
            return;
        }

        NonNullList<ItemStack> main = player.inventory.mainInventory;
        int selected = player.inventory.currentItem;
        for (int slot = 0; slot < main.size(); slot++) {
            // The selected slot is what Wizardry means by isSelected, and it cools it itself.
            // The off hand lives in a different list entirely and is never walked here.
            if (slot == selected) {
                continue;
            }
            ItemStack stack = main.get(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemWand)) {
                continue;
            }
            for (int i = 0; i < steps; i++) {
                WandHelper.decrementCooldowns(stack);
            }
        }
    }

    /** Drop the carry so the map cannot outlive the session. */
    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            carry.remove(event.player.getUniqueID());
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :tombtweaks:compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add tombtweaks/src/main/java/com/spege/tombtweaks/wizardry/ConcentrationCooldownHandler.java
git commit -m "feat(tombtweaks): Concentration cools stowed Wizardry wands"
```

---

### Task 4: Register the handler

The class names both Wizardry and Tombstone types in its field and method bodies, so it must not be loaded when either mod is absent. It goes inside the existing `ebwizardry` gate, which already sits inside the `tombstone` gate.

**Files:**
- Modify: `tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java`

- [ ] **Step 1: Extend the existing Wizardry block**

Find this block in `init` (around line 88):

```java
            if (Loader.isModLoaded("ebwizardry")) {
                MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.wizardry.WandSoulbindAttacher());
                LOGGER.info("[TombstoneTweaks] Wand soulbinding armed — wands can now spend a grave's soul.");
            }
```

Replace it with:

```java
            if (Loader.isModLoaded("ebwizardry")) {
                MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.wizardry.WandSoulbindAttacher());
                LOGGER.info("[TombstoneTweaks] Wand soulbinding armed — wands can now spend a grave's soul.");

                // Concentration cooling stowed wands. Reads its config and Wizardry's own
                // wandsMustBeHeldToDecrementCooldown live, so it costs a few comparisons per
                // player per scan when either says it should do nothing.
                MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.wizardry.ConcentrationCooldownHandler());
                LOGGER.info("[TombstoneTweaks] Concentration now cools stowed wands.");
            }
```

- [ ] **Step 2: Compile**

```bash
./gradlew :tombtweaks:compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java
git commit -m "feat(tombtweaks): register the Concentration cooldown handler"
```

---

### Task 5: Version bump, build, deploy

`Specification-Version` in `build.gradle` is derived from `version`, so there are **three** places, not four.

**Files:**
- Modify: `tombtweaks/build.gradle`
- Modify: `tombtweaks/src/main/resources/mcmod.info`
- Modify: `tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java`

- [ ] **Step 1: Bump all three**

In `tombtweaks/build.gradle`, change `version = '1.8.3'` to:

```groovy
version = '1.9.0'
```

In `tombtweaks/src/main/resources/mcmod.info`, change `"version": "1.8.3",` to:

```json
  "version": "1.9.0",
```

In `tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java`, change the constant to:

```java
    public static final String VERSION = "1.9.0";
```

- [ ] **Step 2: Confirm no 1.8.3 is left**

```bash
grep -rn "1\.8\.3" tombtweaks/build.gradle tombtweaks/src/main/resources/mcmod.info tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java
```

Expected: no output. Any hit is a place the bump missed — `VERSION` is the one that historically drifts, because it is what `@Mod` reports in the mod list and nothing derives it.

- [ ] **Step 3: Build**

```bash
./gradlew :tombtweaks:build
```

Expected: `BUILD SUCCESSFUL`, and `tombtweaks/build/libs/tombtweaks-1.9.0.jar` exists.

- [ ] **Step 4: Deploy, removing the old jar**

🚨 Two jars of one modid is a duplicate-mod crash. The remove is not optional.

```bash
cp tombtweaks/build/libs/tombtweaks-1.9.0.jar "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/"
rm "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/tombtweaks-1.8.3.jar"
ls "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/" | grep -i tombtweaks
```

Expected: exactly one line, `tombtweaks-1.9.0.jar`. If the copy fails with `Device or resource busy`, the game is running — close it and retry.

- [ ] **Step 5: Commit**

```bash
git add tombtweaks/build.gradle tombtweaks/src/main/resources/mcmod.info tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java
git commit -m "chore(tombtweaks): 1.9.0"
```

---

### Task 6: Verify in game

Ask the user to launch. Nothing below can be checked from source.

- [ ] **Step 1: Confirm the handler registered**

After launch:

```bash
grep -E "Concentration now cools stowed wands" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/latest.log"
```

Expected: one line. If it is absent, either Wizardry is not loaded or the master switch is off — check `latest.log` for `[TombstoneTweaks] Master switch is off`.

- [ ] **Step 2: Confirm Wizardry's setting is actually on**

```bash
grep -rn "wandsMustBeHeldToDecrementCooldown" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/config/"
```

Expected: a line showing it set to `true`. **If it is `false`, the whole feature is inert by design and every test below is meaningless** — turn it on first.

- [ ] **Step 3: The control case — run this before anything else**

With Concentration at level 0: cast a spell with a long cooldown, put the wand in an inventory slot that is not the selected one, wait 30 seconds, take it out.

Expected: the cooldown has **not** moved. This is what makes the next step evidence rather than coincidence. If the cooldown moved here, stop — something else in the pack is already cooling stowed wands and the feature's premise is wrong.

- [ ] **Step 4: The feature**

Raise Concentration to 5. Repeat step 3.

Raise Concentration to its cap, which is **2**, not 5 — `PerkConcentration.getLevelMax()` returns 2.

Expected: after 30 seconds stowed, roughly 6 seconds of cooldown are gone — one fifth of the rate, matching 2 levels × 10%. If you expected 15 seconds, you were working from the level cap most other Tombstone perks use.

- [ ] **Step 5: Confirm Wizardry's own path is untouched**

With Concentration at 0, put the wand in the **off hand** and wait.

Expected: it cools at full rate, exactly as before this feature existed. This is the regression check — if it changed, we are interfering with Wizardry's path rather than adding to it.

- [ ] **Step 6: Confirm the feature is inert when Wizardry's setting is off**

Set `wandsMustBeHeldToDecrementCooldown = false` in Wizardry's config and restart. Stow a wand on
cooldown with Concentration at 0, then with it at 5.

Expected: identical in both cases — cooldowns already run everywhere, so the perk changes nothing.
A difference here means the handler is granting ticks on top of Wizardry's own, double-cooling the
wand. Set it back to `true` afterwards.

- [ ] **Step 7: Confirm the config levers**

Set `Percent Per Level = 0` in `tombtweaks.cfg`, then repeat step 4.

Expected: no cooling while stowed, without touching `Enabled`.

- [ ] **Step 8: Confirm Ancient Spellcraft wands are covered**

Repeat step 4 with an Ancient Spellcraft wand.

Expected: identical behaviour — its wands extend `ItemWand`, so the `instanceof` catches them.

- [ ] **Step 9: Report honestly**

Report which of steps 3–8 were actually observed and which were not. Do not describe an unrun step as passing.

---

## Out of scope

Do not add these while implementing; each was considered and deferred in the spec:

- A tooltip line on the Concentration perk. Tombstone prints its own casting-speed figure and will not mention this bonus. Unlike the seven `perkinfo` mixins — which exist because we *change* a number Tombstone prints — here we *add* one it never knew about, so the tooltip is incomplete rather than wrong. Adding it means a new mixin and a decision about how a third-party bonus should read inside another mod's GUI.
- Anything touching `Spell.getChargeup()`.
- Bridges for Alchemist, Rune Inscriber or Gladiator, and knowledge or alignment from magic.
