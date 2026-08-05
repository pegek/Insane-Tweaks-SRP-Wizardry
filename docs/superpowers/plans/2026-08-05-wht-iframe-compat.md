# WorseHurtTimer i-frame compatibility layer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give srpwizcore a registry through which any pack mechanic can lengthen a victim's invincibility frames under WorseHurtTimer, and make `bountifulbaubles:amuletcross` its first consumer.

**Architecture:** Four mixins intercept the numbers WorseHurtTimer computes (`Events.getHurtTime`, `Events.getHurtResistantTime`, `Events.onAttackEntityFromPre`, `BHTAPI.get`) and scale them by `WhtIFrames.getMultiplier(victim)`. `WhtIFrames` is a tiny registry that knows nothing about WHT; providers know nothing about mixins. `CrossNecklaceProvider` is the only provider shipped.

**Tech Stack:** Java 8, Forge 1.12.2-14.23.5.2860, ForgeGradle 3, Mixin via MixinBooter, MCP snapshot `20171003-1.12`.

**Spec:** `docs/superpowers/specs/2026-08-05-wht-iframe-compat-design.md`

---

## Testing approach — read before starting

`CLAUDE.md` states: *"No test suite, no lint task"* and *"Build → test loop (no unit tests)"*. This
plan follows that convention instead of adding JUnit. Every task ends with a compile check; the
behavioural checks are concentrated in Task 9 and run in the live instance.

Three rules from `CLAUDE.md` that this plan depends on and that must not be violated:

1. **`-proc:none`, no refmaps.** Every mixin here targets a mod class, so every `@Mixin`,
   `@Inject` and `@Redirect` carries `remap = false` and names its target by explicit descriptor.
2. **Never allocate in a mixin's static field initialiser.** None of these mixins hold state.
3. **Mixin failures surface at runtime, not at compile.** `BUILD SUCCESSFUL` proves nothing about
   whether an injection landed — only `logs/cleanmix.log` does.

Instance path used throughout: `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2`.

---

## File structure

| File | Responsibility |
|---|---|
| `libs/WorseHurtTimer-1.12.2-1.5.0.3.jar` | compile-only reference for the four mixin targets |
| `srpwizcore/build.gradle` | adds the WHT and BaublesEX compile-only deps, version bump |
| `srpwizcore/src/main/java/com/spege/srpwizcore/api/WhtIFrames.java` | multiplier registry — the only public surface |
| `srpwizcore/src/main/java/com/spege/srpwizcore/whtcompat/CrossNecklaceProvider.java` | reads the baubles inventory, returns the amulet's multiplier |
| `srpwizcore/src/main/java/com/spege/srpwizcore/config/categories/WhtCompatCategory.java` | the four config values |
| `srpwizcore/src/main/java/com/spege/srpwizcore/config/SrpWizCoreConfig.java` | registers the category |
| `srpwizcore/src/main/java/com/spege/srpwizcore/SrpWizCore.java` | registers the provider in `init`, `after:betterhurttimer` |
| `srpwizcore/src/main/java/com/spege/srpwizcore/mixins/betterhurttimer/MixinBhtEventsHurtTime.java` | melee cooldown × multiplier |
| `.../MixinBhtEventsResistantTime.java` | deterministic player base |
| `.../MixinBhtEventsSourceFrames.java` | per-source i-frames × multiplier |
| `.../MixinBhtApiSourceSeed.java` | deterministic seed for unlisted sources |
| `srpwizcore/src/main/resources/mixins.srpwizcore.betterhurttimer.json` | late mixin config |
| `srpwizcore/src/main/java/com/spege/srpwizcore/core/SrpWizCoreLateBooter.java` | queues that config when WHT is present |
| `srpwizcore/src/main/resources/mcmod.info` | version bump, description |

Three of the four mixins target the same class (`Events`). They stay in separate files so each can
be removed independently if a future WHT update breaks one of them.

---

### Task 1: Compile-time dependencies

**Files:**
- Create: `libs/WorseHurtTimer-1.12.2-1.5.0.3.jar` (copy)
- Modify: `srpwizcore/build.gradle`

- [ ] **Step 1: Copy the WHT jar into libs**

```bash
cp "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/WorseHurtTimer-1.12.2-1.5.0.3.jar" /e/Isuth/modDev/libs/
```

- [ ] **Step 2: Verify it landed**

Run: `ls -l /e/Isuth/modDev/libs/WorseHurtTimer-1.12.2-1.5.0.3.jar`
Expected: one file, roughly 133 KB.

- [ ] **Step 3: Add both compile-only dependencies**

In `srpwizcore/build.gradle`, inside `dependencies { … }`, directly above the
`implementation 'zone.rong:mixinbooter:7.1'` line, insert:

```groovy
    // WorseHurtTimer compileOnly, NOT deobf — the four mixin targets (Events.getHurtTime,
    // Events.getHurtResistantTime, Events.onAttackEntityFromPre, BHTAPI.get) and the
    // HurtSourceInfo$HurtSourceData handler parameter are all plain mod members. Their
    // descriptors carry MC *class* names only, which SRG never renames, so nothing needs
    // remapping. PreLivingAttackEvent extends Forge's Event directly, not LivingEvent.
    compileOnly files(rootProject.file('libs/WorseHurtTimer-1.12.2-1.5.0.3.jar'))
    // BaublesEX compileOnly, NOT deobf — only BaublesApi.isBaubleEquipped(EntityPlayer, Item)
    // is called; a Baubles method name with MC class names in its descriptor, nothing to remap.
    compileOnly files(rootProject.file('libs/BaublesEX-1.12.2-2.3.6.jar'))
```

- [ ] **Step 4: Verify the build still resolves**

Run: `cd /e/Isuth/modDev && ./gradlew :srpwizcore:compileJava`
Expected: `BUILD SUCCESSFUL`. No source changed yet, so this only proves the two new jars resolve.

- [ ] **Step 5: Commit**

```bash
cd /e/Isuth/modDev
git add libs/WorseHurtTimer-1.12.2-1.5.0.3.jar srpwizcore/build.gradle
git commit -m "srpwizcore: WHT + BaublesEX na compile classpath (compileOnly, bez deobf)"
```

---

### Task 2: Config category

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/config/categories/WhtCompatCategory.java`
- Modify: `srpwizcore/src/main/java/com/spege/srpwizcore/config/SrpWizCoreConfig.java`

- [ ] **Step 1: Create the category**

```java
package com.spege.srpwizcore.config.categories;

import net.minecraftforge.common.config.Config;

/**
 * WorseHurtTimer (modid {@code betterhurttimer}) invincibility-frame compatibility.
 *
 * <p>WHT replaces vanilla i-frames wholesale: its own mixin overwrites
 * {@code EntityLivingBase.hurtResistantTime} with a flat config value on every hit, so anything
 * that lengthens invincibility the vanilla way is dead. This module gives the pack one place to
 * ask for longer i-frames that works on every WHT path, and makes the base values WHT computes
 * deterministic.
 *
 * <p>The mixins live in {@code mixins.srpwizcore.betterhurttimer.json}, which
 * {@code SrpWizCoreLateBooter} only queues when {@code betterhurttimer} is present. Each mixin
 * additionally self-gates on {@link #enabled}, so switching it off makes WHT behave exactly as
 * unmodified WHT does.
 */
public class WhtCompatCategory {

    @Config.Comment({
            "Master switch for the WorseHurtTimer invincibility-frame layer.",
            "OFF leaves WorseHurtTimer working exactly as it does without this mod.",
            "Does nothing unless WorseHurtTimer is installed. No restart needed. Default ON."
    })
    @Config.Name("Enabled")
    public boolean enabled = true;

    @Config.Comment({
            "Invincibility frames, in ticks, used as the starting point for two things:",
            " - a player being hit in melee by an attacker holding no attack-speed weapon,",
            " - any damage source that is NOT listed in betterhurttimer.cfg's damageSource table.",
            "20 reproduces WorseHurtTimer's own numbers, so leaving it alone changes nothing.",
            "The second case is a bug fix: unmodified WorseHurtTimer takes that value from",
            "whichever entity that damage source happened to hit first in the session, and then",
            "reuses it globally for everyone.",
            "Raising this makes EVERY player tougher, before any multiplier. 20 ticks = 1 second."
    })
    @Config.Name("Base I-Frame Ticks")
    @Config.RangeInt(min = 1, max = 200)
    public int baseIFrameTicks = 20;

    @Config.Comment({
            "Ceiling on the combined multiplier when a player carries several sources of longer",
            "invincibility at once. They multiply, so two 1.8x items would be 3.24x without this.",
            "Default 3.0."
    })
    @Config.Name("Max Multiplier")
    @Config.RangeDouble(min = 1.0D, max = 10.0D)
    public double maxMultiplier = 3.0D;

    @Config.Comment({
            "How much longer invincibility the Cross Necklace (bountifulbaubles:amuletcross)",
            "grants while worn in a baubles slot. Applies to melee, arrows, magic, fire and",
            "everything else - which is what its tooltip promises and what plain WorseHurtTimer",
            "does not deliver.",
            "1.0 disables the item's effect. Does nothing unless Bountiful Baubles is installed.",
            "Default 1.8, which is the ratio the item was originally written with (20 -> 36)."
    })
    @Config.Name("Cross Necklace Multiplier")
    @Config.RangeDouble(min = 1.0D, max = 10.0D)
    public double crossNecklaceMultiplier = 1.8D;
}
```

- [ ] **Step 2: Register the category**

In `SrpWizCoreConfig.java`, add the import next to the other category imports:

```java
import com.spege.srpwizcore.config.categories.WhtCompatCategory;
```

and add this field directly after the `dragonRanged` field, before the
`@Mod.EventBusSubscriber` inner class:

```java
    @Config.Name("whtCompat")
    @Config.Comment("Make invincibility frames under WorseHurtTimer deterministic, and let items grant longer ones. Covers the Cross Necklace.")
    public static final WhtCompatCategory whtCompat = new WhtCompatCategory();
```

- [ ] **Step 3: Compile**

Run: `cd /e/Isuth/modDev && ./gradlew :srpwizcore:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd /e/Isuth/modDev
git add srpwizcore/src/main/java/com/spege/srpwizcore/config/
git commit -m "srpwizcore: kategoria configu whtCompat"
```

---

### Task 3: `WhtIFrames` registry

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/api/WhtIFrames.java`

- [ ] **Step 1: Write the class**

```java
package com.spege.srpwizcore.api;

import java.util.ArrayList;
import java.util.List;

import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.entity.EntityLivingBase;

/**
 * Registry of invincibility-frame multipliers, consumed by the WorseHurtTimer mixins in
 * {@code com.spege.srpwizcore.mixins.betterhurttimer}.
 *
 * <p>Contributions multiply, so two providers at 1.8x and 1.2x give 2.16x, and the product is
 * clamped to {@code whtCompat.maxMultiplier}. A provider returning 1.0 or less contributes
 * nothing and can never drag the result below 1.0 — this is a "grant more invincibility" API,
 * not a general damage-timing API.
 *
 * <p>This class knows nothing about WorseHurtTimer and nothing about baubles. Register providers
 * during {@code FMLInitializationEvent}; the list is never mutated afterwards, so the read path
 * needs no synchronisation.
 *
 * <p>Server side only in practice — all four call sites run on the server thread.
 */
public final class WhtIFrames {

    /** One source of longer invincibility, e.g. a worn item. */
    public interface Provider {
        /**
         * @param victim the entity taking the hit, never null
         * @return the multiplier to apply, 1.0 for "this provider does not apply here"
         */
        float multiplier(EntityLivingBase victim);
    }

    private static final List<String> IDS = new ArrayList<String>(4);
    private static final List<Provider> PROVIDERS = new ArrayList<Provider>(4);

    private WhtIFrames() {
    }

    /**
     * Registers a provider under a unique id. A duplicate id is ignored, so calling this twice
     * from a reloaded config cannot stack the same effect.
     */
    public static void register(String id, Provider provider) {
        if (id == null || provider == null) {
            return;
        }
        if (IDS.contains(id)) {
            return;
        }
        IDS.add(id);
        PROVIDERS.add(provider);
    }

    /** Combined multiplier for this victim. Returns exactly 1.0 when nothing applies. */
    public static float getMultiplier(EntityLivingBase victim) {
        final int count = PROVIDERS.size();
        if (count == 0 || victim == null) {
            return 1.0F;
        }
        float result = 1.0F;
        for (int i = 0; i < count; i++) {
            final float m = PROVIDERS.get(i).multiplier(victim);
            if (m > 1.0F) {
                result *= m;
            }
        }
        float max = (float) SrpWizCoreConfig.whtCompat.maxMultiplier;
        if (max < 1.0F) {
            max = 1.0F;
        }
        return result > max ? max : result;
    }
}
```

The indexed loop is deliberate: `getMultiplier` runs per incoming hit, and an enhanced-for would
allocate an iterator each time.

- [ ] **Step 2: Compile**

Run: `cd /e/Isuth/modDev && ./gradlew :srpwizcore:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd /e/Isuth/modDev
git add srpwizcore/src/main/java/com/spege/srpwizcore/api/WhtIFrames.java
git commit -m "srpwizcore: WhtIFrames - rejestr mnoznika i-frame"
```

---

### Task 4: Cross Necklace provider

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/whtcompat/CrossNecklaceProvider.java`
- Modify: `srpwizcore/src/main/java/com/spege/srpwizcore/SrpWizCore.java`

- [ ] **Step 1: Write the provider**

```java
package com.spege.srpwizcore.whtcompat;

import com.spege.srpwizcore.SrpWizCore;
import com.spege.srpwizcore.api.WhtIFrames;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import baubles.api.BaublesApi;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Grants the Cross Necklace ({@code bountifulbaubles:amuletcross}) its advertised effect.
 *
 * <p>The item's whole implementation is {@code maxHurtResistantTime = 36} on equip. Under
 * WorseHurtTimer that field survives as an input in exactly one branch of
 * {@code Events.getHurtTime} — the one taken when the attacker holds no attack-speed weapon —
 * so the necklace helps against bare-handed mobs and nothing else. Moving the effect onto
 * {@link WhtIFrames} makes it apply on every path and stops it depending on an entity field that
 * is neither saved to NBT nor exclusively ours.
 *
 * <p>No compile dependency on Bountiful Baubles: the item is looked up by registry name once and
 * cached. Bountiful Baubles keeps writing 36 to the field; {@code MixinBhtEventsResistantTime}
 * makes WorseHurtTimer ignore it for players, so that write is a harmless dead store and the
 * bonus is not counted twice.
 */
public final class CrossNecklaceProvider implements WhtIFrames.Provider {

    private static final ResourceLocation AMULET_CROSS =
            new ResourceLocation("bountifulbaubles", "amuletcross");

    private final Item amuletCross;

    private CrossNecklaceProvider(Item amuletCross) {
        this.amuletCross = amuletCross;
    }

    /**
     * Registers the provider when both Bountiful Baubles and Baubles are present and the item
     * actually resolves. Call from {@code FMLInitializationEvent} — item registration is over by
     * then.
     */
    public static void registerIfPossible() {
        if (!Loader.isModLoaded("bountifulbaubles") || !Loader.isModLoaded("baubles")) {
            return;
        }
        final Item item = ForgeRegistries.ITEMS.getValue(AMULET_CROSS);
        if (item == null) {
            SrpWizCore.LOGGER.warn("[srpwizcore] whtCompat: {} not in the item registry, "
                    + "Cross Necklace multiplier disabled", AMULET_CROSS);
            return;
        }
        WhtIFrames.register("srpwizcore:cross_necklace", new CrossNecklaceProvider(item));
        SrpWizCore.LOGGER.info("[srpwizcore] whtCompat: Cross Necklace multiplier armed ({}x)",
                SrpWizCoreConfig.whtCompat.crossNecklaceMultiplier);
    }

    @Override
    public float multiplier(EntityLivingBase victim) {
        if (!(victim instanceof EntityPlayer)) {
            return 1.0F;
        }
        if (BaublesApi.isBaubleEquipped((EntityPlayer) victim, this.amuletCross) == -1) {
            return 1.0F;
        }
        return (float) SrpWizCoreConfig.whtCompat.crossNecklaceMultiplier;
    }
}
```

No result cache on purpose. `isBaubleEquipped` is a loop of `getItem()` comparisons over roughly
twenty slots and runs per incoming hit, not per tick. Add a cache only if a flare profile shows it.

- [ ] **Step 2: Register it at init**

In `SrpWizCore.java`, inside the `init` method, directly after the two unconditional
`SpawnEngine` registrations and before the `if (Loader.isModLoaded("cqrepoured"))` block, add:

```java
        // WorseHurtTimer i-frame layer. Registered unconditionally, like the SpawnEngine
        // handlers above: every whtCompat flag is read live inside the provider and inside the
        // mixins, so gating registration on `enabled` would only mean that switching the module
        // on at runtime did nothing until a restart. Harmless when WHT is absent — nothing reads
        // WhtIFrames then.
        com.spege.srpwizcore.whtcompat.CrossNecklaceProvider.registerIfPossible();
```

- [ ] **Step 3: Declare the load-order dependency**

In `SrpWizCore.java`, extend the `dependencies` string in the `@Mod` annotation. Change:

```java
        dependencies = "after:openterraingenerator;after:futuremc;after:iceandfire;"
                + "after:dldungeonsjbg;after:cqrepoured;after:raids;"
                + "after:spartanfire;after:spartandragonsteel",
```

to:

```java
        dependencies = "after:openterraingenerator;after:futuremc;after:iceandfire;"
                + "after:dldungeonsjbg;after:cqrepoured;after:raids;"
                + "after:spartanfire;after:spartandragonsteel;"
                + "after:betterhurttimer;after:bountifulbaubles",
```

- [ ] **Step 4: Compile**

Run: `cd /e/Isuth/modDev && ./gradlew :srpwizcore:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /e/Isuth/modDev
git add srpwizcore/src/main/java/com/spege/srpwizcore/whtcompat/ srpwizcore/src/main/java/com/spege/srpwizcore/SrpWizCore.java
git commit -m "srpwizcore: dostawca mnoznika dla Cross Necklace"
```

---

### Task 5: Mixin 1 — melee cooldown, plus the mixin config

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/mixins/betterhurttimer/MixinBhtEventsHurtTime.java`
- Create: `srpwizcore/src/main/resources/mixins.srpwizcore.betterhurttimer.json`
- Modify: `srpwizcore/src/main/java/com/spege/srpwizcore/core/SrpWizCoreLateBooter.java`

- [ ] **Step 1: Write the mixin**

```java
package com.spege.srpwizcore.mixins.betterhurttimer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.srpwizcore.api.WhtIFrames;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * Scales WorseHurtTimer's melee cooldown by the victim's i-frame multiplier.
 *
 * <p>{@code Events.getHurtTime(target, attacker)} returns how many ticks the attacker must wait
 * before it may hit this target again; {@code Events.onEntityAttack} cancels the
 * {@code LivingAttackEvent} until then. Injecting at RETURN covers both of its branches — the
 * attack-speed one taken when the attacker holds a weapon, and the
 * {@code maxHurtResistantTime} one taken when it does not. Scaling only the second branch would
 * leave armed attackers as a hole, which is exactly the hole the Cross Necklace has today.
 *
 * <p>The multiplier is computed from the <em>target</em>, so the call from
 * {@code Events.lambda$onPlayerAttack$3}, which passes (mob, player), correctly gives a player
 * attacking a mob no bonus. Do not add an attacker-side check — it would be wrong.
 */
@Mixin(targets = "arekkuusu.betterhurttimer.common.Events", remap = false)
public class MixinBhtEventsHurtTime {

    @Inject(
            method = "getHurtTime(Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;)I",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private static void srpwizcore$scaleHurtTime(Entity target, Entity attacker,
            CallbackInfoReturnable<Integer> cir) {
        if (!SrpWizCoreConfig.whtCompat.enabled) {
            return;
        }
        if (!(target instanceof EntityLivingBase)) {
            return;
        }
        final float multiplier = WhtIFrames.getMultiplier((EntityLivingBase) target);
        if (multiplier == 1.0F) {
            return;
        }
        cir.setReturnValue(Integer.valueOf((int) (cir.getReturnValueI() * multiplier)));
    }
}
```

- [ ] **Step 2: Write the mixin config**

`srpwizcore/src/main/resources/mixins.srpwizcore.betterhurttimer.json`:

```json
{
  "required": false,
  "minVersion": "0.8.2",
  "package": "com.spege.srpwizcore.mixins.betterhurttimer",
  "target": "@env(DEFAULT)",
  "compatibilityLevel": "JAVA_8",
  "mixins": [ "MixinBhtEventsHurtTime" ],
  "client": [],
  "server": [],
  "injectors": { "defaultRequire": 1 }
}
```

`defaultRequire: 1` is deliberate. If a future WorseHurtTimer changes a signature we want a loud
startup failure, not a silent no-op that leaves the amulet quietly broken again.

- [ ] **Step 3: Queue the config**

In `SrpWizCoreLateBooter.getMixinConfigs()`, add before `return configs;`:

```java
        if (Loader.isModLoaded("betterhurttimer")) {
            // Invincibility-frame compatibility layer. Queued whenever WorseHurtTimer is
            // present; the master switch lives in the config and is checked inside each
            // handler, because gating the queue would mean reading the config before Forge
            // has injected it.
            configs.add("mixins.srpwizcore.betterhurttimer.json");
        }
```

- [ ] **Step 4: Build the jar**

Run: `cd /e/Isuth/modDev && ./gradlew :srpwizcore:build`
Expected: `BUILD SUCCESSFUL`, jar at `srpwizcore/build/libs/srpwizcore-1.11.0.jar`.

- [ ] **Step 5: Smoke-test in the instance**

```bash
rm "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/srpwizcore-1.9.1.jar"
cp /e/Isuth/modDev/srpwizcore/build/libs/srpwizcore-1.11.0.jar "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/"
```

Then ask the user to launch the game and load a world. Afterwards:

Run: `grep -i "betterhurttimer" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log" | grep srpwizcore`
Expected: a line
`[CleanMix/Audit]: APPLY mixins.srpwizcore.betterhurttimer.json:MixinBhtEventsHurtTime from mod srpwizcore -> arekkuusu.betterhurttimer.common.Events`

Run: `grep -E "InvalidInjectionException|Scanned 0|VerifyError" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log"`
Expected: no output.

Stop here and report if either check fails — the remaining three mixins use the same target class
and would fail the same way.

- [ ] **Step 6: Commit**

```bash
cd /e/Isuth/modDev
git add srpwizcore/src/main/java/com/spege/srpwizcore/mixins/betterhurttimer/ srpwizcore/src/main/resources/mixins.srpwizcore.betterhurttimer.json srpwizcore/src/main/java/com/spege/srpwizcore/core/SrpWizCoreLateBooter.java
git commit -m "srpwizcore: mixin skalujacy cooldown melee WHT mnoznikiem ofiary"
```

---

### Task 6: Mixin 2 — deterministic player base

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/mixins/betterhurttimer/MixinBhtEventsResistantTime.java`
- Modify: `srpwizcore/src/main/resources/mixins.srpwizcore.betterhurttimer.json`

- [ ] **Step 1: Write the mixin**

```java
package com.spege.srpwizcore.mixins.betterhurttimer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.srpwizcore.config.SrpWizCoreConfig;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Stops WorseHurtTimer reading a <em>player's</em> {@code maxHurtResistantTime}, replacing it
 * with a fixed base from the config.
 *
 * <p>Without this, the Cross Necklace would be counted twice: Bountiful Baubles writes 36 to the
 * field (on equip, and again on every incoming attack from its own
 * {@code EventHandler.onDamage}), so the bare-handed-attacker branch of
 * {@code Events.getHurtTime} would produce 36 x 1.8 = 64 ticks while every other path produced
 * 20 x 1.8 = 36.
 *
 * <p><b>Players only.</b> A scan of all 269 jars in the instance found exactly two writers of
 * this field: Bountiful Baubles, and BabyMobs — whose {@code EntityBabyWitherSkeleton} sets its
 * own field to 50 in its constructor, buying that mob roughly 48 ticks of melee cooldown as a
 * victim instead of 19. A blanket override would silently nerf it. Bountiful Baubles is the only
 * writer that touches a player's field, so restricting the override to players removes the
 * double count and leaves every mob's self-declared value intact.
 *
 * <p>The eleven mods that only <em>read</em> the field — SoManyEnchantments most of all, with
 * eleven reads across eight enchantments — are untouched. This mixin changes what WorseHurtTimer
 * consumes, not what the field holds.
 */
@Mixin(targets = "arekkuusu.betterhurttimer.common.Events", remap = false)
public class MixinBhtEventsResistantTime {

    @Inject(
            method = "getHurtResistantTime(Lnet/minecraft/entity/Entity;)D",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void srpwizcore$deterministicPlayerBase(Entity entity,
            CallbackInfoReturnable<Double> cir) {
        if (!SrpWizCoreConfig.whtCompat.enabled) {
            return;
        }
        if (!(entity instanceof EntityPlayer)) {
            return;
        }
        cir.setReturnValue(Double.valueOf(SrpWizCoreConfig.whtCompat.baseIFrameTicks));
    }
}
```

- [ ] **Step 2: Add it to the mixin config**

Change the `mixins` array in `mixins.srpwizcore.betterhurttimer.json` to:

```json
  "mixins": [ "MixinBhtEventsHurtTime", "MixinBhtEventsResistantTime" ],
```

- [ ] **Step 3: Compile**

Run: `cd /e/Isuth/modDev && ./gradlew :srpwizcore:compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd /e/Isuth/modDev
git add srpwizcore/src/main/java/com/spege/srpwizcore/mixins/betterhurttimer/MixinBhtEventsResistantTime.java srpwizcore/src/main/resources/mixins.srpwizcore.betterhurttimer.json
git commit -m "srpwizcore: deterministyczna baza i-frame gracza (player-only)"
```

---

### Task 7: Mixin 3 — per-source i-frames

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/mixins/betterhurttimer/MixinBhtEventsSourceFrames.java`
- Modify: `srpwizcore/src/main/resources/mixins.srpwizcore.betterhurttimer.json`

- [ ] **Step 1: Write the mixin**

```java
package com.spege.srpwizcore.mixins.betterhurttimer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.srpwizcore.api.WhtIFrames;
import com.spege.srpwizcore.config.SrpWizCoreConfig;

import arekkuusu.betterhurttimer.api.capability.data.HurtSourceInfo;
import arekkuusu.betterhurttimer.api.event.PreLivingAttackEvent;

import net.minecraft.entity.EntityLivingBase;

/**
 * Scales the per-damage-source invincibility frames — the ones configured in
 * {@code betterhurttimer.cfg}'s {@code damageSource} table — by the victim's multiplier.
 *
 * <p>{@code HurtSourceData.trigger()} sets {@code tick = info.waitTime} and clears
 * {@code canApply}. {@code info} is shared globally between every entity, so it must not be
 * touched; {@code data} is per-entity, so scaling {@code data.tick} right after the original
 * call is the correct place. This is what makes the Cross Necklace work against arrows, magic,
 * fire and everything else in that table, which plain WorseHurtTimer never let it do.
 *
 * <p>The redirect handler takes the enclosing method's argument as a trailing parameter, which
 * is how the victim gets into scope. {@code onAttackEntityFromPre} contains exactly one
 * {@code trigger()} call.
 */
@Mixin(targets = "arekkuusu.betterhurttimer.common.Events", remap = false)
public class MixinBhtEventsSourceFrames {

    @Redirect(
            method = "onAttackEntityFromPre(Larekkuusu/betterhurttimer/api/event/PreLivingAttackEvent;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Larekkuusu/betterhurttimer/api/capability/data/HurtSourceInfo$HurtSourceData;trigger()V"),
            remap = false)
    private static void srpwizcore$scaleSourceFrames(HurtSourceInfo.HurtSourceData data,
            PreLivingAttackEvent event) {
        data.trigger();
        if (!SrpWizCoreConfig.whtCompat.enabled) {
            return;
        }
        final EntityLivingBase victim = event.getEntityLiving();
        if (victim == null) {
            return;
        }
        final float multiplier = WhtIFrames.getMultiplier(victim);
        if (multiplier == 1.0F) {
            return;
        }
        data.tick = (int) (data.tick * multiplier);
    }
}
```

- [ ] **Step 2: Add it to the mixin config**

```json
  "mixins": [ "MixinBhtEventsHurtTime", "MixinBhtEventsResistantTime", "MixinBhtEventsSourceFrames" ],
```

- [ ] **Step 3: Compile**

Run: `cd /e/Isuth/modDev && ./gradlew :srpwizcore:compileJava`
Expected: `BUILD SUCCESSFUL`.

If javac cannot resolve `HurtSourceInfo.HurtSourceData`, the inner class is referenced as
`HurtSourceInfo$HurtSourceData` in bytecode but is a normal static nested class in source — the
import above is correct and the failure would mean Task 1's jar is missing from `libs/`.

- [ ] **Step 4: Commit**

```bash
cd /e/Isuth/modDev
git add srpwizcore/src/main/java/com/spege/srpwizcore/mixins/betterhurttimer/MixinBhtEventsSourceFrames.java srpwizcore/src/main/resources/mixins.srpwizcore.betterhurttimer.json
git commit -m "srpwizcore: mnoznik i-frame per zrodlo obrazen"
```

---

### Task 8: Mixin 4 — deterministic seed for unlisted sources

**Files:**
- Create: `srpwizcore/src/main/java/com/spege/srpwizcore/mixins/betterhurttimer/MixinBhtApiSourceSeed.java`
- Modify: `srpwizcore/src/main/resources/mixins.srpwizcore.betterhurttimer.json`

- [ ] **Step 1: Write the mixin**

```java
package com.spege.srpwizcore.mixins.betterhurttimer;

import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.srpwizcore.config.SrpWizCoreConfig;

import arekkuusu.betterhurttimer.api.capability.data.HurtSourceInfo;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;

/**
 * Replaces WorseHurtTimer's seeding of unconfigured damage sources with a fixed value.
 *
 * <p>{@code BHTAPI.get} does
 * {@code DAMAGE_SOURCE_INFO_MAP.computeIfAbsent(source.getDamageType(), HURT_SOURCE_INFO_FUNCTION.apply(entity))},
 * and that function builds {@code new HurtSourceInfo(name, false, entity.maxHurtResistantTime)}.
 * The map is a global static keyed by source name alone, so for any source missing from
 * {@code betterhurttimer.cfg}'s {@code damageSource} table — {@code explosion}, {@code drown},
 * {@code onFire}, {@code sting}, and every modded source from SRParasites, Electroblob's
 * Wizardry and CQR — the wait time is fixed for the whole session by whichever entity that
 * source happened to hit first. That is an upstream bug, and it would also make the per-source
 * multiplier land on a random base.
 *
 * <p>Sources that <em>are</em> configured were inserted by {@code BHTAPI.addSource} at config
 * load, so {@code computeIfAbsent} never fires for them and their tuning is untouched.
 *
 * <p>The redirect targets the {@code computeIfAbsent} call rather than the seeding lambda: the
 * lambda would have to be named {@code lambda$null$0}, which is a compiler-generated name.
 */
@Mixin(targets = "arekkuusu.betterhurttimer.api.BHTAPI", remap = false)
public class MixinBhtApiSourceSeed {

    @Redirect(
            method = "get(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/util/DamageSource;)Larekkuusu/betterhurttimer/api/capability/data/HurtSourceInfo$HurtSourceData;",
            at = @At(
                    value = "INVOKE",
                    target = "Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            remap = false)
    private static Object srpwizcore$seedDeterministic(Object2ObjectMap<Object, Object> map,
            Object key, Function<Object, Object> original) {
        final Object existing = map.get(key);
        if (existing != null) {
            return existing;
        }
        if (!SrpWizCoreConfig.whtCompat.enabled) {
            return map.computeIfAbsent(key, original);
        }
        final HurtSourceInfo info = new HurtSourceInfo((CharSequence) key, false,
                SrpWizCoreConfig.whtCompat.baseIFrameTicks);
        map.put(key, info);
        return info;
    }
}
```

`doFrames = false` matches what WorseHurtTimer's own seeding function passes, so nothing but the
wait time changes.

- [ ] **Step 2: Add it to the mixin config**

```json
  "mixins": [ "MixinBhtEventsHurtTime", "MixinBhtEventsResistantTime", "MixinBhtEventsSourceFrames", "MixinBhtApiSourceSeed" ],
```

- [ ] **Step 3: Compile**

Run: `cd /e/Isuth/modDev && ./gradlew :srpwizcore:compileJava`
Expected: `BUILD SUCCESSFUL`. `-Xlint:all` may warn about the raw `Object2ObjectMap` bound; if it
errors instead, drop the type arguments to `Object2ObjectMap` and cast at the call sites — the
redirect only cares about the erased descriptor.

- [ ] **Step 4: Commit**

```bash
cd /e/Isuth/modDev
git add srpwizcore/src/main/java/com/spege/srpwizcore/mixins/betterhurttimer/MixinBhtApiSourceSeed.java srpwizcore/src/main/resources/mixins.srpwizcore.betterhurttimer.json
git commit -m "srpwizcore: deterministyczny seed i-frame dla zrodel spoza tabeli WHT"
```

---

### Task 9: Version bump, build, and in-game verification

**Files:**
- Modify: `srpwizcore/build.gradle` (two places)
- Modify: `srpwizcore/src/main/java/com/spege/srpwizcore/SrpWizCore.java`
- Modify: `srpwizcore/src/main/resources/mcmod.info`

- [ ] **Step 1: Bump the version in all four places**

`srpwizcore/build.gradle` line 11: `version = '1.11.0'` → `version = '1.12.0'`
`srpwizcore/build.gradle` manifest: `"Specification-Version": "1.11.0"` → `"1.12.0"`
`SrpWizCore.java`: `public static final String VERSION = "1.11.0";` → `"1.12.0"`
`mcmod.info`: `"version": "1.11.0"` → `"1.12.0"`

- [ ] **Step 2: Extend the mcmod.info description**

Append to the end of the `description` string, before the closing quote:

```
 Also makes invincibility frames deterministic under WorseHurtTimer and lets worn items grant longer ones, which is what finally makes Bountiful Baubles' Cross Necklace work as advertised.
```

- [ ] **Step 3: Build**

Run: `cd /e/Isuth/modDev && ./gradlew :srpwizcore:build`
Expected: `BUILD SUCCESSFUL`, jar at `srpwizcore/build/libs/srpwizcore-1.12.0.jar`.

- [ ] **Step 4: Install into the instance**

```bash
rm -f "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/srpwizcore-1.11.0.jar"
rm -f "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/srpwizcore-1.9.1.jar"
cp /e/Isuth/modDev/srpwizcore/build/libs/srpwizcore-1.12.0.jar "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/"
ls "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/" | grep srpwizcore
```

Expected: exactly one `srpwizcore-*.jar`. Two versions of one modid is a duplicate-mod crash.

- [ ] **Step 5: Turn WorseHurtTimer logging on**

In `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\config\betterhurttimer.cfg`, set
`B:doLogging=false` → `B:doLogging=true`. This makes WHT log `Threshold is {}` and
`ticksSinceLastHurt: {}` for every attack. It is noisy — Step 10 turns it back off.

- [ ] **Step 6: Ask the user to launch and check that all four mixins applied**

Run: `grep "APPLY mixins.srpwizcore.betterhurttimer" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log"`

Expected: four lines, one each for `MixinBhtEventsHurtTime`, `MixinBhtEventsResistantTime`,
`MixinBhtEventsSourceFrames` (all → `arekkuusu.betterhurttimer.common.Events`) and
`MixinBhtApiSourceSeed` (→ `arekkuusu.betterhurttimer.api.BHTAPI`).

Run: `grep -E "InvalidInjectionException|Scanned 0|VerifyError" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log"`
Expected: no output.

Run: `grep "whtCompat" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/latest.log"`
Expected: `[srpwizcore] whtCompat: Cross Necklace multiplier armed (1.8x)`

- [ ] **Step 7: Measure melee, without the amulet**

Ask the user to let a bare-handed mob (a plain zombie with empty hands) hit them, then an armed
one (a zombie holding an iron sword), with no Cross Necklace equipped.

Run: `grep "ticksSinceLastHurt" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/latest.log" | tail -20`

Expected: **19** for the bare-handed mob, **12** for the sword zombie. These must match today's
values — this is the no-regression check.

- [ ] **Step 8: Measure melee, with the amulet**

Same two mobs, Cross Necklace in a baubles slot.

Expected: **34** for the bare-handed mob, **22** for the sword zombie. The second number is the
whole point: plain WorseHurtTimer leaves it at 12.

- [ ] **Step 9: Measure the mob regression and the non-melee paths**

Punch a BabyMobs baby wither skeleton bare-handed. Expected `ticksSinceLastHurt`: near **48**,
not 19 — this is the check that Mixin 2 really is player-only.

Stand in fire with and without the amulet and compare the tick gap between damage entries in
`latest.log`. Expected: **10** without, **18** with (`^inFire$:false:10` from the config table).

- [ ] **Step 10: Turn logging back off**

Restore `B:doLogging=false` in `betterhurttimer.cfg`.

- [ ] **Step 11: Commit**

```bash
cd /e/Isuth/modDev
git add srpwizcore/build.gradle srpwizcore/src/main/java/com/spege/srpwizcore/SrpWizCore.java srpwizcore/src/main/resources/mcmod.info
git commit -m "srpwizcore 1.12.0: warstwa kompatybilnosci i-frame WHT"
```

---

## If a measurement does not match

| Symptom | Likely cause |
|---|---|
| Bare-handed reads 34 without the amulet | `CrossNecklaceProvider.multiplier` is not checking `isBaubleEquipped`, or the item resolved to the wrong id |
| Bare-handed reads 64 with the amulet | Mixin 2 did not apply — WHT is still reading the field Bountiful Baubles set to 36 |
| Armed zombie unchanged at 12 with the amulet | Mixin 1 injected into only one branch; confirm `@At("RETURN")`, not `@At("HEAD")` |
| Baby wither skeleton drops to 19 | Mixin 2 is missing its `instanceof EntityPlayer` guard |
| Fire unchanged at 10 with the amulet | Mixin 3 did not apply, or `onAttackEntityFromPre` gained a second `trigger()` call |
| Everything unchanged | `whtCompat.enabled` is false, or the old jar is still in `mods/` |
