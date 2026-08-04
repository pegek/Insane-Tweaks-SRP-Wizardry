# Wand Soulbinding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** At a decorative grave holding a soul, a player with the Ankh of Prayer in the main hand and a wand in the off hand spends the soul to give the wand Ancient Spellcraft's `soulbound_upgrade`.

**Architecture:** No mixins. Tombstone exposes its soul-consumer contract as a public Forge capability, so we attach our own `ISoulConsumer` to wand stacks and let Tombstone's existing grave dispatch call us. The upgrade is applied through Wizardry's own `ItemWand.applyUpgrade`, which enforces the wand's upgrade limit.

**Tech Stack:** Minecraft 1.12.2, Forge 14.23.5.2860, Java 8. Compile deps: Corail Tombstone 4.7.6 (already present), Electroblob's Wizardry 4.3.19 (added by Task 1). Ancient Spellcraft is **not** a compile dep — its item is resolved by registry name.

**Design spec:** `docs/superpowers/specs/2026-08-05-tombstone-wizardry-soulbound-design.md`

---

## 🚨 How this plan verifies, and why it is not unit tests

This repo has **no test suite and no lint task** (`CLAUDE.md`), and no Minecraft mod of this shape
can be unit-tested without a harness that does not exist here. Writing one is far outside this
feature's scope, so the red/green loop is replaced by the verification this repo actually uses, in
the same spirit — each task ends with a check that fails before the change and passes after:

1. **`javap` against the real jars** — every API call in this plan was read out of bytecode during
   design. Where a task depends on a signature, it states the command that proves it.
2. **`./gradlew :tombtweaks:build`** — the compiler is the type-level test.
3. **In-game, with the log** — the only thing that proves behaviour. Task 6 is a scripted session.

Do not skip step "verify it fails first" where it appears — a check that passes before the change
proves nothing.

---

## File Structure

| file | responsibility |
|---|---|
| `tombtweaks/build.gradle` | adds the Wizardry compile dep; version |
| `.../config/categories/TombstoneCategory.java` | new `wandsoulbinding` config section |
| `.../wizardry/WandSoulbindConsumer.java` | the `ISoulConsumer`: decides and applies |
| `.../wizardry/WandSoulbindAttacher.java` | attaches that capability to wand stacks |
| `.../TombstoneTweaks.java` | registers the attacher, gated on both mods; version |
| `tombtweaks/src/main/resources/mcmod.info` | version |

Java sources live under `tombtweaks/src/main/java/com/spege/tombtweaks/`.

The consumer and the attacher are separate on purpose: the attacher is a hot event handler that must
do nothing but a type check, and the consumer holds all the decision logic. Neither needs to know
how the other works.

---

### Task 1: Wizardry becomes a compile dependency of tombtweaks

**Files:**
- Modify: `tombtweaks/build.gradle`

- [ ] **Step 1: Prove the dependency is missing**

```bash
cd E:/Isuth/modDev && grep -c "ElectroblobsWizardry" tombtweaks/build.gradle
```

Expected: `0`. If it prints anything else, this task is already done — skip to Task 2.

- [ ] **Step 2: Confirm tombtweaks already has the CurseMaven repository**

```bash
cd E:/Isuth/modDev && grep -n "cursemaven" tombtweaks/build.gradle
```

Expected: one line, `maven { url = 'https://www.cursemaven.com' }`. It is already there — do not add
a second one.

- [ ] **Step 3: Add the dependency**

In `tombtweaks/build.gradle`, inside `dependencies { }`, immediately after the
`enigmaticlegacy-legacy-2.7.0.jar` line, add:

```gradle
    // Electroblob's Wizardry — the ItemWand type and WandHelper, for soulbinding a wand with a
    // grave's soul. Pulled from CurseMaven exactly as content does; it is deliberately excluded
    // from the libs fileTree everywhere in this repo, so do not add a jar to libs/.
    implementation fg.deobf('curse.maven:ElectroblobsWizardry-265642:8320066')
```

- [ ] **Step 4: Verify it resolves**

```bash
cd E:/Isuth/modDev && ./gradlew :tombtweaks:build 2>&1 | grep -E "BUILD|error:|Could not resolve"
```

Expected: `BUILD SUCCESSFUL`. A `Could not resolve` means the CurseMaven coordinate or the
repository is wrong — fix before continuing.

- [ ] **Step 5: Commit**

```bash
cd E:/Isuth/modDev && git add tombtweaks/build.gradle && git commit -m "build(tombtweaks): take Electroblob's Wizardry as a compile dependency"
```

---

### Task 2: Config section `wandsoulbinding`

**Files:**
- Modify: `tombtweaks/src/main/java/com/spege/tombtweaks/config/categories/TombstoneCategory.java`

- [ ] **Step 1: Add the category field**

Immediately **above** the existing line `@Config.Name("firstkillrewards")`, insert:

```java
    @Config.Name("wandsoulbinding")
    @Config.Comment({"Spend a grave's soul to make a wand survive your death.",
            "Hold the Ankh of Prayer in your main hand and the wand in your off hand, then use a",
            "decorative grave that holds a soul. The wand gains Ancient Spellcraft's soulbinding",
            "upgrade and stays in your inventory when you die.",
            "Needs Electroblob's Wizardry for the wand, and whichever mod owns the upgrade named",
            "below. Without them the whole section is inert."})
    public WandSoulbindingConfig wandSoulbinding = new WandSoulbindingConfig();

```

- [ ] **Step 2: Add the config class**

Immediately **above** the existing comment banner `// FIRST-KILL REWARDS`, insert:

```java
    // ========================================================================
    // WAND SOULBINDING
    // ========================================================================

    /**
     * Trading a grave's soul for a wand upgrade.
     *
     * <p>Written as "which upgrade does a soul buy" rather than hardcoding soulbinding, so the same
     * plumbing serves any wand upgrade a pack wants to make purchasable this way.
     */
    public static class WandSoulbindingConfig {

        @Config.Name("Enabled")
        @Config.Comment("Allow a grave's soul to upgrade a wand. Read live - no restart needed.")
        public boolean enabled = true;

        @Config.Name("Upgrade Item")
        @Config.Comment({"Registry name of the wand upgrade a soul grants.",
                "The default is Ancient Spellcraft's soulbinding upgrade, which is what makes a wand",
                "stay in your inventory on death. An item no mod registers means the interaction is",
                "simply refused, with the reason shown to the player."})
        public String upgradeItem = "ancientspellcraft:soulbound_upgrade";

        @Config.Name("Require Ankh Of Prayer")
        @Config.Comment({"Demand the Ankh of Prayer in the main hand.",
                "Tombstone's own off-hand branch never looks at the main hand, so without this any",
                "wand you happen to carry in the off hand would eat the soul of the next grave you",
                "click. Leave it on unless you want that."})
        public boolean requireAnkhOfPrayer = true;

        @Config.Name("Debug Logging")
        @Config.Comment("Log every accepted binding and every refusal, with the reason.")
        public boolean debugLogging = false;
    }

```

- [ ] **Step 3: Verify it compiles**

```bash
cd E:/Isuth/modDev && ./gradlew :tombtweaks:build 2>&1 | grep -E "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd E:/Isuth/modDev && git add tombtweaks/src/main/java/com/spege/tombtweaks/config/categories/TombstoneCategory.java && git commit -m "feat(tombtweaks): config section for wand soulbinding"
```

---

### Task 3: The soul consumer

**Files:**
- Create: `tombtweaks/src/main/java/com/spege/tombtweaks/wizardry/WandSoulbindConsumer.java`

- [ ] **Step 1: Re-confirm the two signatures this class depends on**

```bash
cd C:/Users/spege/AppData/Local/Temp/claude/E--Isuth-modDev/1a7ad7a1-df66-4933-b867-97efdf02ef58/scratchpad && javap -p tbfull/ovh/corail/tombstone/api/capability/ISoulConsumer.class && javap -p ebw/electroblob/wizardry/item/ItemWand.class | grep applyUpgrade
```

Expected: `ISoulConsumer` lists `isEnchanted`, `setEnchant`, `isUsingOffhandToEnchant`, `canEnchant`,
`getKnowledge`; `ItemWand` lists
`public ItemStack applyUpgrade(EntityPlayer, ItemStack, ItemStack)`.

If the scratchpad is gone, re-extract: `unzip` the pack's `tombstone-1.12.2-4.7.6.jar` and
`ElectroblobsWizardry-4.3.19.jar` from
`C:/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/`.

- [ ] **Step 2: Write the class**

```java
package com.spege.tombtweaks.wizardry;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import com.spege.tombtweaks.TombstoneTweaks;
import com.spege.tombtweaks.config.TombTweaksConfig;
import com.spege.tombtweaks.config.categories.TombstoneCategory.WandSoulbindingConfig;

import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.util.WandHelper;
import ovh.corail.tombstone.api.capability.ISoulConsumer;

/**
 * Lets a grave's soul buy a wand upgrade.
 *
 * <p>Attached to wand stacks by {@link WandSoulbindAttacher}. Tombstone's
 * {@code BlockDecorativeGrave.onBlockActivated} looks for this capability on the <b>off-hand</b>
 * stack first, keeps it only when {@link #isUsingOffhandToEnchant()} is true, and then calls
 * {@link #canEnchant} followed by {@link #setEnchant} on the same stack.
 *
 * <p>🚨 {@code isUsingOffhandToEnchant()} returning {@code true} is load-bearing: it is the filter
 * on that branch. Return false and the wand is never consulted at all — and the main-hand branch
 * would then target the off-hand stack instead, which is the opposite of what is wanted.
 *
 * <p>The Ankh's own behaviour is untouched. It returns {@code PASS} at a soul-bearing grave, its
 * soul path is the perk respec, and its prayer lives on {@code onItemUseFinish} — three separate
 * branches of Tombstone's code, none of which this class enters.
 */
public class WandSoulbindConsumer implements ISoulConsumer {

    private static final String ANKH_NAME = "tombstone:ankh_of_prayer";

    private static WandSoulbindingConfig cfg() {
        return TombTweaksConfig.tombstone.wandSoulbinding;
    }

    /** The configured upgrade, or null when no mod registers it. */
    @Nullable
    private static Item upgradeItem() {
        return Item.getByNameOrId(cfg().upgradeItem);
    }

    /** 🚨 Load-bearing — see the class javadoc. */
    @Override
    public boolean isUsingOffhandToEnchant() {
        return true;
    }

    /** The soul is the whole price. */
    @Override
    public int getKnowledge() {
        return 0;
    }

    @Override
    public boolean isEnchanted(ItemStack stack) {
        Item upgrade = upgradeItem();
        return upgrade != null && WandHelper.getUpgradeLevel(stack, upgrade) > 0;
    }

    /**
     * The grave calls this immediately before {@link #setEnchant} and abandons the whole
     * interaction when it is false, so a refusal here never costs the player a soul.
     */
    @Override
    public boolean canEnchant(World world, BlockPos pos, EntityPlayer player, ItemStack stack) {
        return refusal(player, stack) == null;
    }

    @Override
    public ConsumeResult setEnchant(World world, BlockPos pos, EntityPlayerMP player, ItemStack stack,
            int soulStrength) {
        String why = refusal(player, stack);
        if (why != null) {
            return refuse(player, stack, why);
        }

        Item upgrade = upgradeItem();
        int before = WandHelper.getUpgradeLevel(stack, upgrade);

        // Wizardry's own path, the one the arcane workbench uses: it checks Tier.upgradeLimit and
        // Constants.UPGRADE_STACK_LIMIT, and fires its special_upgrade advancement.
        ((ItemWand) stack.getItem()).applyUpgrade(player, stack, new ItemStack(upgrade));

        // 🚨 applyUpgrade returns the wand whether or not it applied anything - past the limit it
        // simply falls through to its return. The level is the only honest signal.
        if (WandHelper.getUpgradeLevel(stack, upgrade) <= before) {
            return refuse(player, stack, "This wand has no room for another upgrade.");
        }

        if (cfg().debugLogging) {
            TombstoneTweaks.LOGGER.info("[TombstoneTweaks] Soulbound {} for {} at {} (soul strength {}).",
                    stack.getItem().getRegistryName(), player.getName(), pos,
                    Integer.valueOf(soulStrength));
        }
        // Hand back the strength we were given: the grave compares it against the grave's own soul
        // to decide which advancement fires.
        return ConsumeResult.success(new TextComponentString("The soul binds itself to your wand."),
                soulStrength);
    }

    private static ConsumeResult refuse(EntityPlayer player, ItemStack stack, String why) {
        if (cfg().debugLogging) {
            TombstoneTweaks.LOGGER.info("[TombstoneTweaks] Wand soulbinding refused for {} ({}): {}",
                    player.getName(), stack.getItem().getRegistryName(), why);
        }
        return ConsumeResult.fail(new TextComponentString(why));
    }

    /** The reason this interaction cannot proceed, or null when it can. */
    @Nullable
    private static String refusal(EntityPlayer player, ItemStack stack) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks || !cfg().enabled) {
            return "Wand soulbinding is switched off.";
        }
        if (!(stack.getItem() instanceof ItemWand)) {
            return "Hold a wand in your off hand.";
        }
        Item upgrade = upgradeItem();
        if (upgrade == null) {
            return "No mod registers the upgrade \"" + cfg().upgradeItem + "\".";
        }
        if (cfg().requireAnkhOfPrayer) {
            ResourceLocation main = player.getHeldItemMainhand().getItem().getRegistryName();
            if (main == null || !ANKH_NAME.equals(main.toString())) {
                return "Hold the Ankh of Prayer in your main hand.";
            }
        }
        if (WandHelper.getUpgradeLevel(stack, upgrade) > 0) {
            return "This wand is already bound to your soul.";
        }
        return null;
    }
}
```

- [ ] **Step 3: Verify it compiles**

```bash
cd E:/Isuth/modDev && ./gradlew :tombtweaks:build 2>&1 | grep -E "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`. An `error: cannot find symbol: ItemWand` means Task 1 was skipped.

- [ ] **Step 4: Commit**

```bash
cd E:/Isuth/modDev && git add tombtweaks/src/main/java/com/spege/tombtweaks/wizardry/WandSoulbindConsumer.java && git commit -m "feat(tombtweaks): soul consumer that soulbinds a wand"
```

---

### Task 4: Attach the capability to wand stacks

**Files:**
- Create: `tombtweaks/src/main/java/com/spege/tombtweaks/wizardry/WandSoulbindAttacher.java`

- [ ] **Step 1: Write the class**

```java
package com.spege.tombtweaks.wizardry;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.spege.tombtweaks.TombstoneTweaks;

import electroblob.wizardry.item.ItemWand;
import ovh.corail.tombstone.capability.TBSoulConsumerProvider;

/**
 * Gives every wand stack Tombstone's soul-consumer capability, so a grave will talk to it.
 *
 * <p>🚨 This fires for <b>every ItemStack that is ever constructed</b>, which on a loaded server is
 * a great many per tick. It must stay exactly this cheap: one {@code instanceof} and return. Do not
 * add config reads, registry lookups or logging here — all of that belongs in
 * {@link WandSoulbindConsumer}, which only runs when a player actually uses a grave.
 *
 * <p>Deliberately not a {@code @Mod.EventBusSubscriber}: this class names Wizardry and Tombstone
 * types, so it must not be loaded when either mod is absent. {@code TombstoneTweaks.init} registers
 * an instance behind a presence check, and passing it as an {@code Object} keeps the verifier from
 * resolving the class on the way in.
 */
public class WandSoulbindAttacher {

    private static final ResourceLocation KEY =
            new ResourceLocation(TombstoneTweaks.MODID, "wand_soulbind");

    @SubscribeEvent
    public void onAttachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        // getItem() is set before Forge fires this; isEmpty() is not safe to call here, and is not
        // needed - an empty stack holds air, which is not a wand.
        if (!(event.getObject().getItem() instanceof ItemWand)) {
            return;
        }
        event.addCapability(KEY, new TBSoulConsumerProvider(new WandSoulbindConsumer()));
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd E:/Isuth/modDev && ./gradlew :tombtweaks:build 2>&1 | grep -E "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd E:/Isuth/modDev && git add tombtweaks/src/main/java/com/spege/tombtweaks/wizardry/WandSoulbindAttacher.java && git commit -m "feat(tombtweaks): attach the soul-consumer capability to wands"
```

---

### Task 5: Register the attacher, gated on both mods

**Files:**
- Modify: `tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java`

- [ ] **Step 1: Add the registration**

In `init(FMLInitializationEvent)`, inside the existing `if (Loader.isModLoaded("tombstone")) { ... }`
block, immediately **after** the `FirstKillRewardHandler` registration, insert:

```java

            // Soulbinding a wand with a grave's soul. Both mods must be present: the attacher and
            // its consumer name Wizardry AND Tombstone types, so the class must not be loaded
            // otherwise. Registering the instance as an Object keeps the verifier out of it.
            if (Loader.isModLoaded("ebwizardry")) {
                MinecraftForge.EVENT_BUS.register(new com.spege.tombtweaks.wizardry.WandSoulbindAttacher());
                LOGGER.info("[TombstoneTweaks] Wand soulbinding armed — wands can now spend a grave's soul.");
            }
```

- [ ] **Step 2: Verify it compiles**

```bash
cd E:/Isuth/modDev && ./gradlew :tombtweaks:build 2>&1 | grep -E "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd E:/Isuth/modDev && git add tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java && git commit -m "feat(tombtweaks): register wand soulbinding when Wizardry is present"
```

---

### Task 6: Version, deploy, and verify in game

**Files:**
- Modify: `tombtweaks/build.gradle`, `tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java`, `tombtweaks/src/main/resources/mcmod.info`

- [ ] **Step 1: Read the current version**

```bash
cd E:/Isuth/modDev && grep -n "^version" tombtweaks/build.gradle
```

🚨 The version moves outside this plan — do not assume it is still what this document says. Read
what step 1 actually printed and put **that** value into `OLD` below, with `NEW` as its next minor.
The command below is written for `1.6.0` → `1.7.0`; if step 1 printed anything else, substitute both.

- [ ] **Step 2: Bump all three places**

```bash
cd E:/Isuth/modDev && OLD=1.6.0 && NEW=1.7.0 && \
sed -i "s/version = '$OLD'/version = '$NEW'/; s/\"Specification-Version\": \"$OLD\"/\"Specification-Version\": \"$NEW\"/" tombtweaks/build.gradle && \
sed -i "s/VERSION = \"$OLD\"/VERSION = \"$NEW\"/" tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java && \
sed -i "s/\"version\": \"$OLD\"/\"version\": \"$NEW\"/" tombtweaks/src/main/resources/mcmod.info && \
grep -n "1\.7\.0" tombtweaks/build.gradle tombtweaks/src/main/java/com/spege/tombtweaks/TombstoneTweaks.java tombtweaks/src/main/resources/mcmod.info
```

Expected: **four** lines (build.gradle twice, the VERSION constant, mcmod.info). Fewer means a
`sed` missed — fix by hand before continuing.

- [ ] **Step 3: Build**

```bash
cd E:/Isuth/modDev && ./gradlew :tombtweaks:build 2>&1 | grep -E "BUILD|error:"
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Deploy, removing the old jar**

The game must be closed or the delete fails with `Device or resource busy`.

```bash
cd "C:/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods" && rm -f tombtweaks-$OLD.jar && cp "E:/Isuth/modDev/tombtweaks/build/libs/tombtweaks-1.7.0.jar" . && ls tombtweaks*.jar
```

Expected: exactly one jar, `tombtweaks-1.7.0.jar`. 🚨 Two versions of one modid is a duplicate-mod
crash.

- [ ] **Step 5: Turn on debug logging before launching**

In `C:/Users/spege/curseforge/minecraft/Instances/DEv 1.2/config/tombtweaks.cfg`, the new
`wandsoulbinding` section appears on first launch. Launch once, quit, then set
`B:"Debug Logging"=true` inside it, and launch again. Every refusal then names its reason, which is
what makes the failure cases below checkable.

- [ ] **Step 6: Confirm the feature armed**

```bash
cd "C:/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs" && grep -n "Wand soulbinding armed" latest.log
```

Expected: one line. Nothing means either Wizardry or Tombstone was not detected, or the master
switch is off.

- [ ] **Step 7: The happy path**

Find or place a decorative grave, wait for it to grow a soul (the blue or pink orb above it), then:
Ankh of Prayer in the main hand, a wand **without** the upgrade in the off hand, right-click the grave.

Expected: chat says the soul binds itself to the wand; the orb is gone; the wand's tooltip lists the
soulbinding upgrade.

- [ ] **Step 8: The refusals — each must leave the soul intact**

At a soul-bearing grave, confirm each of these is refused with its reason in chat, and that the orb
is **still there** afterwards:

| off hand | main hand | expected reason |
|---|---|---|
| the same wand again | Ankh | already bound to your soul |
| a wand | anything but the Ankh | hold the Ankh of Prayer |
| a wand at its upgrade limit | Ankh | no room for another upgrade |

- [ ] **Step 9: Confirm Tombstone's own paths still work**

- Ankh in the main hand, **off hand empty**, at a soul-bearing grave → perk respec, as before.
- Ankh at a grave **without** a soul → the prayer, with its random effect, as before.

Both must behave exactly as they did before this feature.

- [ ] **Step 10: Confirm the point of it all**

Die with the bound wand in the inventory. On respawn the wand must be **in your inventory**, not in
the grave.

🚨 While doing this, watch for the known risk from the spec: our `SlotSnapshotHandler` and Ancient
Spellcraft's `storeSoulboundWands` both listen to `LivingDeathEvent` at NORMAL priority. Turn on
`B:"Slot Restore Debug Logging"=true` for this run and check whether the snapshot recorded the wand
in a slot the grave never received. If it did and the restore misbehaves, that is a separate fix —
record what you saw rather than patching it inside this task.

- [ ] **Step 11: Commit**

```bash
cd E:/Isuth/modDev && git add tombtweaks/ && git commit -m "feat(tombtweaks) 1.7.0: spend a grave's soul to soulbind a wand"
```

---

## Out of scope

Retuning the existing perks to affect casting, knowledge or alignment from magic, and any other
death-and-magic mechanic. Each gets its own spec and plan.
