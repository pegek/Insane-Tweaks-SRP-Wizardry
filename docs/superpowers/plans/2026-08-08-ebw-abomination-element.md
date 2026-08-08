# Abomination Element Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the faked `Element.MAGIC` "Abomination" styling in `insanetweaks` with a real Electroblob's Wizardry element registered through `WizardryEnumHelper.addElement`, and re-key the casting gate onto that element.

**Architecture:** One owner class (`init/ModElements`) adds the enum constant from the `@Mod` constructor, before EBW's blocks snapshot `Element.values()`. Spell JSONs switch to `"element": "abomination"`. The casting gate compares elements instead of registry domains. Fourteen `@Redirect`s route EBW's own random-element pickers through `util/NativeElements`, which returns the eight native elements, so EBW never generates Abomination wizards, shrines or item subtypes. Six files implementing the old fake are deleted.

**Tech Stack:** Minecraft 1.12.2, Forge 14.23.5.2860, Java 8 source level, Cleanroom MixinBooter (sponge-mixin 0.8.7), Gradle multi-project with ForgeGradle 3. Target dependency: Electroblob's Wizardry 4.3.19 (CurseMaven file id `8320066`).

**Spec:** `docs/superpowers/specs/2026-08-08-ebw-abomination-element-design.md`

---

## Before you start: how "testing" works in this repo

**There is no test suite and no lint task.** `CLAUDE.md` is explicit about it. Do not invent one, and
do not add a test framework — that is out of scope for this plan.

What replaces TDD here, in order of strength:

1. **`./gradlew :insanetweaks:build`** — the compiler is the fast feedback loop. Every code task ends
   with a build.
2. **`javap -p -c`** — the only way to confirm a mixin selector before runtime. Mixin selector
   mistakes are *runtime* failures, never compile failures.
3. **A real launch of the DEv 1.2 instance**, checking `logs/cleanmix.log` and `logs/latest.log`.
   This is Task 14 and it is not optional: a mixin that silently fails to apply looks exactly like a
   mixin that applied, and "the game started" proves nothing.

Paths in this plan are relative to the repo root `E:\Isuth\modDev` unless stated otherwise. The
content mod's Java root is `insanetweaks/src/main/java/com/spege/insanetweaks/`.

Work on a branch. Commit after each task.

---

### Task 1: Register the Abomination element

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/init/ModElements.java`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (constructor, currently at line 153)

- [ ] **Step 1: Create `ModElements.java`**

```java
package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;

import electroblob.wizardry.api.WizardryEnumHelper;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.spell.Spell;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

/**
 * Owns the Abomination element - the only place in this mod that touches Wizardry's Element enum.
 *
 * <p>The constant is appended to {@link Element} at runtime through Wizardry's own
 * {@code WizardryEnumHelper}, which wraps Forge's {@code EnumHelper.addEnum}. That has to happen
 * before anything snapshots {@code Element.values()} - notably {@code BlockCrystal}'s
 * {@code PropertyEnum.create}, which runs inside {@code RegistryEvent.Register<Block>}. Hence
 * {@link #init()} from the {@code @Mod} constructor.
 *
 * <p>If the reflective add ever fails, {@link #EXTENDED} goes false and {@link #ABOMINATION} becomes
 * {@link Element#MAGIC}. {@link #isAbomination(Spell)} then falls back to the registry-domain test
 * this mod used before the element existed, so degraded mode is exactly the old behaviour rather
 * than something new. A naive {@code getElement() == ABOMINATION} would, in that state, also match
 * Wizardry's own MAGIC spells and start blocking foreign magic on unadapted wands.
 */
public final class ModElements {

    /** The Abomination element, or {@link Element#MAGIC} if registration failed. */
    public static final Element ABOMINATION;

    /** True when the enum was really extended. False means every consumer must degrade. */
    public static final boolean EXTENDED;

    static {
        Element registered = null;
        try {
            registered = WizardryEnumHelper.addElement("ABOMINATION",
                    new Style().setColor(TextFormatting.RED),
                    "abomination",
                    InsaneTweaksMod.MODID);
        } catch (Throwable t) {
            InsaneTweaksMod.LOGGER.error("[InsaneTweaks] Could not add the Abomination element to "
                    + "Wizardry's Element enum. Falling back to MAGIC: spells keep working but show "
                    + "as 'None', and the casting gate reverts to a registry-domain check.", t);
        }

        EXTENDED = registered != null;
        ABOMINATION = EXTENDED ? registered : Element.MAGIC;

        if (EXTENDED) {
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Abomination element registered at ordinal {}. EXTENDED=true.",
                    Integer.valueOf(ABOMINATION.ordinal()));
        } else {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Abomination element NOT registered. EXTENDED=false.");
        }
    }

    private ModElements() {
    }

    /** No-op whose only job is to force this class's static initialiser at a chosen moment. */
    public static void init() {
    }

    /**
     * Whether the given spell belongs to this mod's magic.
     *
     * @param spell may be null
     */
    public static boolean isAbomination(Spell spell) {
        if (spell == null) {
            return false;
        }
        if (EXTENDED) {
            return spell.getElement() == ABOMINATION;
        }
        ResourceLocation id = spell.getRegistryName();
        return id != null && InsaneTweaksMod.MODID.equals(id.getResourceDomain());
    }
}
```

- [ ] **Step 2: Call it from the `@Mod` constructor**

In `InsaneTweaksMod.java`, replace the constructor body's opening so `ModElements.init()` is the
first statement. The existing constructor reads:

```java
    public InsaneTweaksMod() {
        // Must run before FML's first ConfigManager.sync (which fires later inside
        // FMLModContainer.constructMod) - see OldConfigBackup.
        com.spege.insanetweaks.config.OldConfigBackup.backupOldConfigIfPresent();
```

Change it to:

```java
    public InsaneTweaksMod() {
        // FIRST. Appends the Abomination constant to Wizardry's Element enum. Must precede both our
        // own ModItems.<clinit> and EBW's RegistryEvent.Register<Block>, because BlockCrystal's
        // <clinit> runs PropertyEnum.create(Element.class), which snapshots values() - an element
        // added after that snapshot is not a legal blockstate value.
        com.spege.insanetweaks.init.ModElements.init();
        // Must run before FML's first ConfigManager.sync (which fires later inside
        // FMLModContainer.constructMod) - see OldConfigBackup.
        com.spege.insanetweaks.config.OldConfigBackup.backupOldConfigIfPresent();
```

- [ ] **Step 3: Build**

Run:

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`. If `WizardryEnumHelper` does not resolve, the EBW dependency is not on
the compile classpath — check `insanetweaks/build.gradle` still pulls `curse.maven` file id
`8320066`; do not add an EBW jar to `libs/`.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/init/ModElements.java insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat(insanetweaks): register the Abomination element via WizardryEnumHelper"
```

---

### Task 2: Lang keys and element icon

**Files:**
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang` (line 194 holds the old key)
- Create: `insanetweaks/src/main/resources/assets/insanetweaks/textures/gui/container/element_icon_abomination.png`

- [ ] **Step 1: Replace the lang key**

`Element.getDisplayName()` reads `element.<unlocalisedName>` and `getWizardName()` reads
`element.<unlocalisedName>.wizard`. Delete line 194:

```
insanetweaks.element.abomination=Abomination
```

and put in its place:

```
element.abomination=Abomination
element.abomination.wizard=Abominator
```

- [ ] **Step 2: Create the icon directory and a stand-in texture**

The element constructor builds the icon path itself as
`insanetweaks:textures/gui/container/element_icon_abomination.png`. Start from EBW's own icon so the
dimensions are right, then hand it to an artist:

```bash
mkdir -p insanetweaks/src/main/resources/assets/insanetweaks/textures/gui/container
unzip -p "$(find ~/.gradle/caches/modules-2/files-2.1/curse.maven/ElectroblobsWizardry-265642 -name '*8320066*.jar' | head -1)" assets/ebwizardry/textures/gui/container/element_icon_magic.png > insanetweaks/src/main/resources/assets/insanetweaks/textures/gui/container/element_icon_abomination.png
```

- [ ] **Step 3: Verify the file is a real PNG of the expected size**

Run:

```bash
ls -la insanetweaks/src/main/resources/assets/insanetweaks/textures/gui/container/element_icon_abomination.png
```

Expected: a file of roughly 250 bytes. A zero-byte file means the `unzip -p` path was wrong — do not
proceed, the arcane workbench would render a broken icon.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang insanetweaks/src/main/resources/assets/insanetweaks/textures/gui/container/element_icon_abomination.png
git commit -m "feat(insanetweaks): Abomination element lang keys and icon"
```

> **Follow-up for the human, not for this plan:** the icon is a recoloured stand-in. Replace it with
> real art before release.

---

### Task 3: Switch the spell JSONs to the new element

**Files:**
- Modify: all 14 files in `insanetweaks/src/main/resources/assets/insanetweaks/spells/`

- [ ] **Step 1: Confirm the starting state**

Run:

```bash
grep -c '"element": "magic"' insanetweaks/src/main/resources/assets/insanetweaks/spells/*.json | grep -v ':0'
```

Expected: 14 lines, each ending in `:1`.

- [ ] **Step 2: Rewrite the element field**

```bash
sed -i 's/"element": "magic"/"element": "abomination"/' insanetweaks/src/main/resources/assets/insanetweaks/spells/*.json
```

- [ ] **Step 3: Verify none were missed**

Run:

```bash
grep -l '"element": "magic"' insanetweaks/src/main/resources/assets/insanetweaks/spells/*.json; grep -c '"element": "abomination"' insanetweaks/src/main/resources/assets/insanetweaks/spells/*.json | grep -cv ':0'
```

Expected: the first command prints nothing; the second prints `14`.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/resources/assets/insanetweaks/spells
git commit -m "feat(insanetweaks): declare all 14 spells as element abomination"
```

---

### Task 4: Fallback guard so a failed registration degrades instead of crashing

`SpellProperties` parses the JSON element with the one-argument `Element.fromName(String)`, which
throws `IllegalArgumentException` for an unknown name. Without this task, `EXTENDED == false` would
abort spell loading rather than fall back.

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinElementFromName.java`
- Modify: `insanetweaks/src/main/resources/mixins.insanetweaks.early.json`

- [ ] **Step 1: Create the mixin**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.constants.Element;

/**
 * Keeps {@code "element": "abomination"} resolvable when the enum extension failed.
 *
 * <p>{@code SpellProperties} uses the one-argument {@code Element.fromName}, which ends in
 * {@code throw new IllegalArgumentException}. With {@code ModElements.EXTENDED == false} our spell
 * JSONs would therefore abort spell-property loading. Mapping the name to MAGIC turns that crash
 * back into the graceful degradation the design promises.
 *
 * <p>Lives in the EARLY config on purpose: early configs are installed at coremod time, so the
 * transformer is in place before anything can load {@code Element}. The late config is queued during
 * mod construction - the same phase in which our own {@code @Mod} constructor loads {@code Element}.
 */
@Mixin(value = Element.class, remap = false)
public abstract class MixinElementFromName {

    @Inject(method = "fromName(Ljava/lang/String;)Lelectroblob/wizardry/constants/Element;",
            at = @At("HEAD"), cancellable = true)
    private static void insanetweaks$resolveAbominationWhenAbsent(String name,
            CallbackInfoReturnable<Element> cir) {

        if (ModElements.EXTENDED || !"abomination".equals(name)) {
            return;
        }
        cir.setReturnValue(Element.MAGIC);
    }
}
```

- [ ] **Step 2: Register it in the early config**

`insanetweaks/src/main/resources/mixins.insanetweaks.early.json` — add `"MixinElementFromName"` to
the `mixins` array, so the file reads:

```json
{
  "required": false,
  "minVersion": "0.8.2",
  "package": "com.spege.insanetweaks.mixins",
  "target": "@env(DEFAULT)",
  "compatibilityLevel": "JAVA_8",
  "mixins": [
    "MixinElementFromName",
    "enchant.MixinEnchantRandomly",
    "enchant.MixinEnchantmentHelperNaturalDiscovery",
    "enchant.MixinVillagerEnchantedBookTrade"
  ],
  "client": [
    "MixinLocale"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 3: Build**

Run:

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinElementFromName.java insanetweaks/src/main/resources/mixins.insanetweaks.early.json
git commit -m "feat(insanetweaks): resolve the abomination element name to MAGIC when registration failed"
```

---

### Task 5: `NativeElements` — the single source of truth for exclusion

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/util/NativeElements.java`

It lives in `util/`, **not** in `mixins.*`: referencing a non-mixin class from inside the mixin
package throws `IllegalClassLoadError` at load.

- [ ] **Step 1: Create the class**

```java
package com.spege.insanetweaks.util;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.constants.Element;

/**
 * Wizardry's elements minus Abomination.
 *
 * <p>Every mixin that narrows one of EBW's own random-element pickers calls this and nothing else,
 * so widening the element's reach later - letting it generate wizards, shrines or crystals - is a
 * matter of deleting redirects, not of rewriting call sites.
 *
 * <p>Returns a fresh copy per call, matching {@code Element.values()}, which clones its backing
 * array too. Callers index and iterate the result and must not see each other's mutations.
 */
public final class NativeElements {

    private static Element[] cache;

    private NativeElements() {
    }

    public static Element[] values() {
        Element[] local = cache;
        if (local == null) {
            local = build();
            cache = local;
        }
        return local.clone();
    }

    private static Element[] build() {
        Element[] all = Element.values();
        if (!ModElements.EXTENDED) {
            return all;
        }

        List<Element> kept = new ArrayList<Element>(all.length);
        for (Element element : all) {
            if (element != ModElements.ABOMINATION) {
                kept.add(element);
            }
        }
        return kept.toArray(new Element[0]);
    }
}
```

- [ ] **Step 2: Build**

Run:

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/util/NativeElements.java
git commit -m "feat(insanetweaks): NativeElements - Wizardry's elements minus Abomination"
```

---

### Task 6: Confirm every mixin selector before writing the mixins

Do this once, now. A wrong selector is a load-time `Scanned 0 target(s)` crash, never a compile
error, and `grep -A` spills across method boundaries and picks the wrong method.

**Files:** none modified.

- [ ] **Step 1: Extract the EBW jar to a scratch directory**

```bash
SCRATCH="$(mktemp -d)" && echo "$SCRATCH" && unzip -q "$(find ~/.gradle/caches/modules-2/files-2.1/curse.maven/ElectroblobsWizardry-265642 -name '*8320066*.jar' | head -1)" -d "$SCRATCH"
```

- [ ] **Step 2: Print every `Element.values()` call with its enclosing method**

Replace `<SCRATCH>` with the directory printed above:

```bash
cd <SCRATCH> && for c in entity/living/EntityWizard entity/living/EntityEvilWizard entity/living/EntityRemnant worldgen/WorldGenShrine worldgen/WorldGenObelisk registry/WizardryLoot loot/RandomSpell item/ItemCrystal item/ItemSpectralDust block/BlockCrystal block/BlockRunestone; do echo "### $c"; javap -p -c "electroblob/wizardry/$c.class" | awk '/^  [a-zA-Z].*\(.*\);$/ {sig=$0} /Element.values/ {print "   " sig}' | sort -u; done
```

Expected output, method-for-method — if any line is missing or different, EBW has changed and the
mixins in Tasks 7-10 must be re-derived before writing them:

```
### entity/living/EntityWizard
     private net.minecraft.item.ItemStack getRandomItemOfTier(electroblob.wizardry.constants.Tier);
     public electroblob.wizardry.constants.Element getElement();
     public net.minecraft.entity.IEntityLivingData func_180482_a(net.minecraft.world.DifficultyInstance, net.minecraft.entity.IEntityLivingData);
     public void func_70037_a(net.minecraft.nbt.NBTTagCompound);
     static electroblob.wizardry.constants.Tier populateSpells(...);
### entity/living/EntityEvilWizard
     public electroblob.wizardry.constants.Element getElement();
     public net.minecraft.entity.IEntityLivingData func_180482_a(...);
     public void func_70037_a(net.minecraft.nbt.NBTTagCompound);
### entity/living/EntityRemnant
     public electroblob.wizardry.constants.Element getElement();
     public net.minecraft.entity.IEntityLivingData func_180482_a(...);
     public void func_70037_a(net.minecraft.nbt.NBTTagCompound);
### worldgen/WorldGenShrine
     public void spawnStructure(...);
### worldgen/WorldGenObelisk
     public void spawnStructure(...);
### registry/WizardryLoot
     (the call sits in `static {}`, which the awk filter does not match - it prints nothing here)
### loot/RandomSpell
     private electroblob.wizardry.spell.Spell pickRandomSpell(...);
### item/ItemCrystal
     public net.minecraft.util.ResourceLocation getModelName(net.minecraft.item.ItemStack);
     public void func_150895_a(...);
### item/ItemSpectralDust
     public net.minecraft.util.ResourceLocation getModelName(net.minecraft.item.ItemStack);
     public void func_150895_a(...);
### block/BlockCrystal
     public net.minecraft.block.state.IBlockState func_176203_a(int);
     public void func_149666_a(...);
### block/BlockRunestone
     protected net.minecraft.block.state.BlockStateContainer func_180661_e();
     public net.minecraft.block.state.IBlockState func_176203_a(int);
     public void func_149666_a(...);
```

- [ ] **Step 3: Confirm the `WizardryLoot` site is in `<clinit>`**

```bash
cd <SCRATCH> && javap -p -c electroblob/wizardry/registry/WizardryLoot.class | sed -n '/^  static {};/,$p' | head -8
```

Expected: offset `0:` is `invokestatic … Element.values:()`.

🚨 **Methods that must NOT be redirected**, though they appear above: `getElement()`, `func_70037_a`
(NBT read, `values()[nbt.getInteger("element")]`), `getModelName`, `func_176203_a`
(`getStateFromMeta`) and `func_180661_e` (`createBlockState`). These are lookups; narrowing the array
would corrupt entity and block loading. Trimming is only safe where the index comes from a `Random`.

---

### Task 7: Exclude Abomination from wizard spawning

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinEntityWizardElements.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinEntityEvilWizardElements.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinEntityRemnantElements.java`
- Modify: `insanetweaks/src/main/resources/mixins.insanetweaks.late.json`

- [ ] **Step 1: Create `MixinEntityWizardElements.java`**

Three redirects. `func_180482_a` and `getRandomItemOfTier` are instance methods, so their handlers
are instance methods; `populateSpells` is static, so its handler is static. Mixin requires the
handler's staticness to match the **enclosing target method**, not the redirected call.

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.entity.living.EntityWizard;

/**
 * Keeps Abomination out of everything a naturally spawned wizard rolls.
 *
 * <p>Without this, {@code onInitialSpawn} can pick Abomination and then ask
 * {@code WizardryItems.getWand} for {@code <tier>_abomination_wand} and
 * {@code ItemWizardArmour.getArmour} for {@code <class>_abomination_<piece>} - registry names this
 * mod deliberately does not provide, because the element is not meant to generate in the world yet.
 */
@Mixin(value = EntityWizard.class, remap = false)
public abstract class MixinEntityWizardElements {

    @Redirect(method = { "func_180482_a", "onInitialSpawn" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowSpawnElements() {
        return NativeElements.values();
    }

    @Redirect(method = "getRandomItemOfTier",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowTradeItemElements() {
        return NativeElements.values();
    }

    @Redirect(method = "populateSpells",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowPopulatedSpellElements() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 2: Create `MixinEntityEvilWizardElements.java`**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.entity.living.EntityEvilWizard;

/** Same reasoning as {@code MixinEntityWizardElements}, for the hostile variant. */
@Mixin(value = EntityEvilWizard.class, remap = false)
public abstract class MixinEntityEvilWizardElements {

    @Redirect(method = { "func_180482_a", "onInitialSpawn" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowSpawnElements() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 3: Create `MixinEntityRemnantElements.java`**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.entity.living.EntityRemnant;

/** Same reasoning as {@code MixinEntityWizardElements}, for remnants (added in EBW 4.3). */
@Mixin(value = EntityRemnant.class, remap = false)
public abstract class MixinEntityRemnantElements {

    @Redirect(method = { "func_180482_a", "onInitialSpawn" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowSpawnElements() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 4: Register the three in the late config**

In `insanetweaks/src/main/resources/mixins.insanetweaks.late.json`, add them to the `mixins` array
(the common list — these are not client-only). The array becomes:

```json
  "mixins": [
    "MixinEntityParasiteBase",
    "MixinEntitySummonedCreature",
    "MixinEntityWizardElements",
    "MixinEntityEvilWizardElements",
    "MixinEntityRemnantElements",
    "MixinParasiteEventEntity",
    "MixinRayOfPurification",
    "MixinSpell",
    "MixinSpellMinion"
  ],
```

- [ ] **Step 5: Build**

Run:

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinEntityWizardElements.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinEntityEvilWizardElements.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinEntityRemnantElements.java insanetweaks/src/main/resources/mixins.insanetweaks.late.json
git commit -m "feat(insanetweaks): keep Abomination out of wizard, evil wizard and remnant spawning"
```

---

### Task 8: Exclude Abomination from shrine and obelisk worldgen

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinWorldGenShrineElements.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinWorldGenObeliskElements.java`
- Modify: `insanetweaks/src/main/resources/mixins.insanetweaks.late.json`

- [ ] **Step 1: Create `MixinWorldGenShrineElements.java`**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.worldgen.WorldGenShrine;

/**
 * Keeps Abomination out of shrine generation.
 *
 * <p>A shrine is themed by element and built from that element's runestones. This mod ships no
 * Abomination runestone blockstate, so an Abomination shrine would generate as missing models.
 */
@Mixin(value = WorldGenShrine.class, remap = false)
public abstract class MixinWorldGenShrineElements {

    @Redirect(method = "spawnStructure",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowShrineElements() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 2: Create `MixinWorldGenObeliskElements.java`**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.worldgen.WorldGenObelisk;

/** Same reasoning as {@code MixinWorldGenShrineElements}, for obelisks. */
@Mixin(value = WorldGenObelisk.class, remap = false)
public abstract class MixinWorldGenObeliskElements {

    @Redirect(method = "spawnStructure",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowObeliskElements() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 3: Register both in the late config**

Add `"MixinWorldGenShrineElements"` and `"MixinWorldGenObeliskElements"` to the same `mixins` array
in `mixins.insanetweaks.late.json`.

- [ ] **Step 4: Build**

Run:

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinWorldGenShrineElements.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinWorldGenObeliskElements.java insanetweaks/src/main/resources/mixins.insanetweaks.late.json
git commit -m "feat(insanetweaks): keep Abomination out of shrine and obelisk worldgen"
```

---

### Task 9: Exclude Abomination from loot generation

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinWizardryLootElements.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinRandomSpellElements.java`
- Modify: `insanetweaks/src/main/resources/mixins.insanetweaks.late.json`

- [ ] **Step 1: Create `MixinWizardryLootElements.java`**

The call lives in `<clinit>`, so the handler is static.

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.registry.WizardryLoot;

/**
 * Keeps Abomination out of the per-element loot entries Wizardry builds in its static initialiser
 * ({@code Arrays.stream(Element.values()).filter(e -> e != MAGIC)…}).
 */
@Mixin(value = WizardryLoot.class, remap = false)
public abstract class MixinWizardryLootElements {

    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowLootElements() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 2: Create `MixinRandomSpellElements.java`**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.loot.RandomSpell;

/**
 * Keeps Abomination out of the {@code random_spell} loot function's default element pool.
 *
 * <p>When a loot entry names no element, {@code pickRandomSpell} falls back to
 * {@code Arrays.asList(Element.values())} and then filters spells by tier and element. Abomination
 * would contribute an element whose spells are all flagged {@code "treasure": false}, leaving an
 * empty candidate set.
 */
@Mixin(value = RandomSpell.class, remap = false)
public abstract class MixinRandomSpellElements {

    @Redirect(method = "pickRandomSpell",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowLootSpellElements() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 3: Register both in the late config**

Add `"MixinWizardryLootElements"` and `"MixinRandomSpellElements"` to the `mixins` array.

- [ ] **Step 4: Build**

Run:

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinWizardryLootElements.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinRandomSpellElements.java insanetweaks/src/main/resources/mixins.insanetweaks.late.json
git commit -m "feat(insanetweaks): keep Abomination out of Wizardry loot generation"
```

---

### Task 10: Hide the ninth item and block subtype

**Files:**
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemCrystalElements.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemSpectralDustElements.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinBlockCrystalElements.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinBlockRunestoneElements.java`
- Modify: `insanetweaks/src/main/resources/mixins.insanetweaks.late.json`

These four target `getSubItems` / `getSubBlocks` only. Redirecting `getModelName`,
`getStateFromMeta` or `createBlockState` in the same classes would corrupt block and item loading.

`getSubItems` and `getSubBlocks` are **not** client-only: Forge strips vanilla's
`@SideOnly(Side.CLIENT)` from them and `CreativeTabs` is a common class. They go in the ordinary
`mixins` list, not `client`.

- [ ] **Step 1: Create `MixinItemCrystalElements.java`**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.ItemCrystal;

/**
 * Stops a ninth magic crystal subtype appearing in creative and JEI.
 *
 * <p>{@code getSubItems} emits one stack per element with {@code meta = element.ordinal()}, and the
 * model name is built in the {@code ebwizardry} namespace, which this mod does not ship into.
 * {@code getModelName} is deliberately left alone: it is a metadata lookup, not a random pick.
 */
@Mixin(value = ItemCrystal.class, remap = false)
public abstract class MixinItemCrystalElements {

    @Redirect(method = { "func_150895_a", "getSubItems" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowCrystalSubtypes() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 2: Create `MixinItemSpectralDustElements.java`**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.item.ItemSpectralDust;

/** Same reasoning as {@code MixinItemCrystalElements}, for spectral dust. */
@Mixin(value = ItemSpectralDust.class, remap = false)
public abstract class MixinItemSpectralDustElements {

    @Redirect(method = { "func_150895_a", "getSubItems" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowDustSubtypes() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 3: Create `MixinBlockCrystalElements.java`**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.block.BlockCrystal;
import electroblob.wizardry.constants.Element;

/**
 * Stops a ninth crystal block variant appearing in creative and JEI.
 *
 * <p>Only {@code getSubBlocks} is redirected. {@code getStateFromMeta} must keep seeing the full
 * enum, or a blockstate round trip would resolve to the wrong element.
 */
@Mixin(value = BlockCrystal.class, remap = false)
public abstract class MixinBlockCrystalElements {

    @Redirect(method = { "func_149666_a", "getSubBlocks" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowCrystalBlockSubtypes() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 4: Create `MixinBlockRunestoneElements.java`**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.block.BlockRunestone;
import electroblob.wizardry.constants.Element;

/**
 * Same reasoning as {@code MixinBlockCrystalElements}, for runestones.
 *
 * <p>{@code createBlockState} and {@code getStateFromMeta} are deliberately untouched: the
 * {@code PropertyEnum} must contain every element, or a state round trip throws.
 */
@Mixin(value = BlockRunestone.class, remap = false)
public abstract class MixinBlockRunestoneElements {

    @Redirect(method = { "func_149666_a", "getSubBlocks" },
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowRunestoneSubtypes() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 5: Register all four in the late config**

Add `"MixinItemCrystalElements"`, `"MixinItemSpectralDustElements"`, `"MixinBlockCrystalElements"`
and `"MixinBlockRunestoneElements"` to the `mixins` array.

- [ ] **Step 6: Build**

Run:

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemCrystalElements.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemSpectralDustElements.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinBlockCrystalElements.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinBlockRunestoneElements.java insanetweaks/src/main/resources/mixins.insanetweaks.late.json
git commit -m "feat(insanetweaks): hide the Abomination crystal, dust, crystal block and runestone subtypes"
```

---

### Task 11: Re-key the casting gate onto the element, and remove the foreign-spell penalty

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/events/ArcaneBridgeEventHandler.java:22-66`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/util/AdaptationUpgradeHelper.java:94-118`

- [ ] **Step 1: Rewrite `onSpellCastPre`**

Replace the whole method (lines 22-66) with:

```java
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onSpellCastPre(SpellCastEvent.Pre event) {
        if (!ModConfig.modules.enableSrpEbWizardryBridge || !PlayerManaCompat.isAvailable()) {
            return;
        }

        EntityLivingBase caster = event.getCaster();
        if (!(caster instanceof EntityPlayer) || caster.world.isRemote || event.getSpell() == null
                || event.getSpell().getRegistryName() == null) {
            return;
        }

        EntityPlayer player = (EntityPlayer) caster;
        net.minecraft.item.ItemStack castingStack = AdaptationUpgradeHelper.findCastingItem(player, event.getSpell());
        int adaptationLevel = AdaptationUpgradeHelper.getEffectiveAdaptationLevel(castingStack);

        if (ModElements.isAbomination(event.getSpell())) {
            if (ArcaneAdaptedFruitHelper.hasConsumedFruit(player)) {
                ArcaneAdaptedFruitHelper.activateFruitRegen(player, ArcaneAdaptedFruitHelper.FRUIT_REGEN_DURATION_TICKS);
            }

            if (event.getSource() == SpellCastEvent.Source.WAND && adaptationLevel <= 0) {
                event.setCanceled(true);
                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        net.minecraft.util.text.TextFormatting.DARK_RED
                                + "This focus has not adapted to Abomination magic."));
                return;
            }

            // Casting our magic from someone else's focus - one that only qualifies through an
            // applied Adaptation upgrade, not by being our own item. Off by default: every level
            // multiplies by 1.0 until the config says otherwise.
            if (AdaptationUpgradeHelper.getDefaultAdaptationLevel(castingStack) == 0) {
                float multiplier = AdaptationUpgradeHelper.getForeignFocusAbominationCostMultiplier(
                        AdaptationUpgradeHelper.getAppliedAdaptationUpgradeLevel(castingStack));
                if (multiplier != 1.0f) {
                    event.getModifiers().set(SpellModifiers.COST,
                            event.getModifiers().get(SpellModifiers.COST) * multiplier, false);
                }
            }
            return;
        }

        // An adapted focus no longer surcharges foreign magic - that whole mechanic went away with
        // the real element. The early return stays because it used to shadow the fruit penalty
        // below, and dropping it would silently start charging adapted foci.
        if (adaptationLevel > 0) {
            return;
        }

        if (ArcaneAdaptedFruitHelper.hasConsumedFruit(player)) {
            float currentCost = event.getModifiers().get(SpellModifiers.COST);
            event.getModifiers().set(SpellModifiers.COST,
                    currentCost * ArcaneAdaptedFruitHelper.FOREIGN_SPELL_COST_MULTIPLIER, false);
        }
    }
```

- [ ] **Step 2: Fix the imports in `ArcaneBridgeEventHandler.java`**

Add:

```java
import com.spege.insanetweaks.init.ModElements;
```

Remove both of these — the rewritten method was their only user in this file (verified: line 35 was
the only `ResourceLocation`, line 38 the only `InsaneTweaksMod`):

```java
import com.spege.insanetweaks.InsaneTweaksMod;
import net.minecraft.util.ResourceLocation;
```

- [ ] **Step 3: Replace the penalty helpers in `AdaptationUpgradeHelper.java`**

Delete `getForeignSpellCostMultiplier` and `getForeignSpellCostPenaltyPercent` (lines 94-118) and put
this in their place:

```java
    /**
     * Cost multiplier for casting an Abomination spell from a focus that is not one of ours and
     * qualifies only through an applied Adaptation upgrade.
     *
     * <p>Defaults to 1.0 at every level, i.e. no surcharge. The mechanism ships switched off so the
     * balance question can be settled with a config edit rather than a code change.
     *
     * @param appliedUpgradeLevel from {@link #getAppliedAdaptationUpgradeLevel(ItemStack)}
     */
    public static float getForeignFocusAbominationCostMultiplier(int appliedUpgradeLevel) {
        switch (Math.max(0, Math.min(3, appliedUpgradeLevel))) {
            case 1:
                return (float) ModConfig.gear.wands.foreignFocusAbominationCostLevel1;
            case 2:
                return (float) ModConfig.gear.wands.foreignFocusAbominationCostLevel2;
            case 3:
                return (float) ModConfig.gear.wands.foreignFocusAbominationCostLevel3;
            default:
                return 1.0f;
        }
    }
```

and add the import:

```java
import com.spege.insanetweaks.config.ModConfig;
```

- [ ] **Step 4: Add the three config fields**

In `insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/GearCategory.java`, inside
`public static class Wands` (declared at line 235 — this is what `ModConfig.gear.wands` resolves to,
the class already holding `sentientPotencyBonus` and `magicDamageMultiplier`), add before its closing
brace:

```java
        @Config.Name("Foreign Focus Abomination Cost (Adaptation I)")
        @Config.Comment({
                "Mana cost multiplier when an Abomination spell is cast from a focus that is NOT one",
                "of ours and only qualifies through an applied Adaptation upgrade at level I.",
                "1.0 means no surcharge, which is the shipped default; 2.0 doubles the cost.",
                "Read live - no restart needed. Default 1.0." })
        @Config.RangeDouble(min = 1.0D, max = 16.0D)
        public double foreignFocusAbominationCostLevel1 = 1.0D;

        @Config.Name("Foreign Focus Abomination Cost (Adaptation II)")
        @Config.Comment({
                "As above, for an applied Adaptation upgrade at level II.",
                "Read live - no restart needed. Default 1.0." })
        @Config.RangeDouble(min = 1.0D, max = 16.0D)
        public double foreignFocusAbominationCostLevel2 = 1.0D;

        @Config.Name("Foreign Focus Abomination Cost (Adaptation III)")
        @Config.Comment({
                "As above, for an applied Adaptation upgrade at level III.",
                "Read live - no restart needed. Default 1.0." })
        @Config.RangeDouble(min = 1.0D, max = 16.0D)
        public double foreignFocusAbominationCostLevel3 = 1.0D;
```

If `ModConfig.gear.wands` turns out to be a different nested class than expected, put the fields in
whichever class `ModConfig.gear.wands` resolves to — the field references in Step 3 must match.

- [ ] **Step 5: Build**

Run:

```bash
./gradlew :insanetweaks:build
```

Expected: **failure**, listing the four remaining callers of the deleted methods —
`BaseCustomWandItem.getArcaneAdaptationPenaltyPercent`,
`BridgeSpellblade.getArcaneAdaptationPenaltyPercent`, and their uses in `WandTooltipHandler` and
`SpellbladeTooltipHandler`. That is the compiler enumerating Task 12's work; do not fix them here.

- [ ] **Step 6: Commit is deferred**

Do not commit yet — the tree does not compile until Task 12. Move straight on.

---

### Task 12: Remove the foreign-penalty tooltips and accessors

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/items/wand/BaseCustomWandItem.java:138-140`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/items/spellblade/BridgeSpellblade.java:354-356`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/events/WandTooltipHandler.java` (two sites, around lines 112-123 and 166-170)
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/events/SpellbladeTooltipHandler.java` (around lines 166-176)
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/util/PropertyDescriptions.java:37`

- [ ] **Step 1: Delete the accessor from `BaseCustomWandItem.java`**

Remove:

```java
    public int getArcaneAdaptationPenaltyPercent(ItemStack stack) {
        return AdaptationUpgradeHelper.getForeignSpellCostPenaltyPercent(this.getArcaneAdaptationLevel(stack));
    }
```

- [ ] **Step 2: Delete the accessor from `BridgeSpellblade.java`**

Remove:

```java
    public int getArcaneAdaptationPenaltyPercent(ItemStack stack) {
        return AdaptationUpgradeHelper.getForeignSpellCostPenaltyPercent(this.getArcaneAdaptationLevel(stack));
    }
```

- [ ] **Step 3: Simplify the custom-wand tooltip in `WandTooltipHandler.java`**

Replace:

```java
        int adaptationLevel = customWand.getArcaneAdaptationLevel(stack);
        if (adaptationLevel > 0) {
            int penaltyPercent = customWand.getArcaneAdaptationPenaltyPercent(stack);
            String penaltyText = penaltyPercent > 0
                    ? "(+" + penaltyPercent + "% Foreign Mana Cost)"
                    : "(No Foreign Mana Penalty)";
            myLines.add(TextFormatting.DARK_RED + "- Adaptation Upgrade " + toRoman(adaptationLevel)
                    + TextFormatting.RED + " " + penaltyText);
```

with:

```java
        int adaptationLevel = customWand.getArcaneAdaptationLevel(stack);
        if (adaptationLevel > 0) {
            myLines.add(TextFormatting.DARK_RED + "- Adaptation Upgrade " + toRoman(adaptationLevel));
```

- [ ] **Step 4: Simplify the generic tooltip in `WandTooltipHandler.java`**

In `addGenericAdaptationTooltip` there are two edits. First replace:

```java
        boolean isShiftPressed = GuiScreen.isShiftKeyDown();
        int penaltyPercent = AdaptationUpgradeHelper.getForeignSpellCostPenaltyPercent(adaptationLevel);
        String penaltyText = penaltyPercent > 0
                ? "(+" + penaltyPercent + "% Foreign Mana Cost)"
                : "(No Foreign Mana Penalty)";
```

with:

```java
        boolean isShiftPressed = GuiScreen.isShiftKeyDown();
```

Then replace the line that consumed `penaltyText` — note this one says **"Arcane Adaptation"**, not
"Adaptation Upgrade" like Step 3's; keep the wording it already has:

```java
        myLines.add(TextFormatting.DARK_RED + "- Arcane Adaptation " + toRoman(adaptationLevel)
                + TextFormatting.RED + " " + penaltyText);
```

with:

```java
        myLines.add(TextFormatting.DARK_RED + "- Arcane Adaptation " + toRoman(adaptationLevel));
```

- [ ] **Step 5: Simplify the spellblade tooltip in `SpellbladeTooltipHandler.java`**

Replace:

```java
            int adaptationLevel = spellblade.getArcaneAdaptationLevel(stack);
            int penaltyPercent = spellblade.getArcaneAdaptationPenaltyPercent(stack);
            String penaltyText = penaltyPercent > 0
                    ? "(+" + penaltyPercent + "% Foreign Mana Cost)"
                    : "(No Foreign Mana Penalty)";

            myLines.add(TextFormatting.DARK_RED + "- Adaptation Upgrade " + toRoman(adaptationLevel)
                    + TextFormatting.RED + " " + penaltyText);
```

with:

```java
            int adaptationLevel = spellblade.getArcaneAdaptationLevel(stack);

            myLines.add(TextFormatting.DARK_RED + "- Adaptation Upgrade " + toRoman(adaptationLevel));
```

- [ ] **Step 6: Update the property description**

In `PropertyDescriptions.java:37`, replace:

```java
                "This focus has been adapted to channel Abomination magic. Higher levels reduce the mana penalty imposed on foreign spells.");
```

with:

```java
                "This focus has been adapted to channel Abomination magic, which no ordinary wand can cast.");
```

- [ ] **Step 7: Build**

Run:

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`. If the compiler still names `getForeignSpellCostPenaltyPercent`, a
caller was missed — find it with:

```bash
grep -rn "getForeignSpellCost\|getArcaneAdaptationPenaltyPercent\|Foreign Mana" insanetweaks/src/main/java
```

Expected from that grep once finished: no output.

- [ ] **Step 8: Commit Tasks 11 and 12 together**

Stage the six touched files explicitly — never a whole directory, this repo's working tree usually
carries unrelated in-progress work:

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/events/ArcaneBridgeEventHandler.java insanetweaks/src/main/java/com/spege/insanetweaks/util/AdaptationUpgradeHelper.java insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/GearCategory.java insanetweaks/src/main/java/com/spege/insanetweaks/items/wand/BaseCustomWandItem.java insanetweaks/src/main/java/com/spege/insanetweaks/items/spellblade/BridgeSpellblade.java insanetweaks/src/main/java/com/spege/insanetweaks/events/WandTooltipHandler.java insanetweaks/src/main/java/com/spege/insanetweaks/events/SpellbladeTooltipHandler.java insanetweaks/src/main/java/com/spege/insanetweaks/util/PropertyDescriptions.java
git commit -m "feat(insanetweaks): gate casting on the Abomination element and drop the foreign-spell mana penalty"
```

Then run `git status --short` and confirm nothing unexpected is staged.

---

### Task 13: Delete the fake-element display layer

Seven files implement the old hack. Six go; `MixinSpell` is trimmed.

**Files:**
- Delete: `insanetweaks/src/main/java/com/spege/insanetweaks/util/SpellDisplayUtils.java`
- Delete: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinGuiSpellInfo.java`
- Delete: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinGuiSpellDisplay.java`
- Delete: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemSpellBook.java`
- Delete: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemScroll.java`
- Delete: `insanetweaks/src/main/java/com/spege/insanetweaks/events/SpellBookGuiHandler.java`
- Delete: `insanetweaks/src/main/java/com/spege/insanetweaks/events/SpellItemTooltipHandler.java`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinSpell.java`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java:491,495`
- Modify: `insanetweaks/src/main/resources/mixins.insanetweaks.late.json`
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Delete the six files**

```bash
git rm insanetweaks/src/main/java/com/spege/insanetweaks/util/SpellDisplayUtils.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinGuiSpellInfo.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinGuiSpellDisplay.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemSpellBook.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemScroll.java insanetweaks/src/main/java/com/spege/insanetweaks/events/SpellBookGuiHandler.java insanetweaks/src/main/java/com/spege/insanetweaks/events/SpellItemTooltipHandler.java
```

- [ ] **Step 2: Trim `MixinSpell.java`**

Delete both colouring injections in full — `insanetweaks$makeOwnMagicSpellsAbominationColored`
(`@Inject(method = "getDisplayNameWithFormatting", …)`) and
`insanetweaks$makeOwnMagicSpellComponentAbominationColored`
(`@Inject(method = "getNameForTranslationFormatted", …)`) — and the five imports that only they used:

```java
import com.spege.insanetweaks.util.SpellDisplayUtils;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
```

Keep the other six imports (`Mixin`, `Shadow`, `At`, `Inject`, `CallbackInfoReturnable`, `Spell`,
`SpellProperties`), the `@Shadow private SpellProperties properties;` field, and —

🚨 **keep `insanetweaks$nullSafeIsEnabled`** (`@Inject(method = "isEnabled", …)`). It is an unrelated
null-safety fix for `Spell.isEnabled` being called from `EntityWizard.populateSpells` before
`SpellProperties.load()` has run, and it has nothing to do with the element. Deleting it reintroduces
a server-thread NPE.

Unused imports are a warning, not a compile error, so the build in Step 7 will **not** catch it if
you leave one behind. Remove them by the list above.

- [ ] **Step 3: Remove the two event-bus registrations**

In `InsaneTweaksMod.java`, delete the lines at 491 and 495:

```java
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellItemTooltipHandler());
```

```java
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellBookGuiHandler());
```

If either sits inside an `if` block that has no other statement left, remove the empty `if` too.

- [ ] **Step 4: Drop the four mixins from the late config's client list**

`mixins.insanetweaks.late.json` — the `client` array becomes empty:

```json
  "client": [],
```

- [ ] **Step 5: Remove the dead lang key**

In `en_us.lang`, delete the line (it was replaced by `element.abomination` in Task 2, and Task 2 may
already have removed it — verify):

```
insanetweaks.element.abomination=Abomination
```

Run:

```bash
grep -n "insanetweaks.element.abomination" insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang
```

Expected: no output.

- [ ] **Step 6: Verify nothing still references the deleted helper**

Run:

```bash
grep -rn "SpellDisplayUtils\|SpellBookGuiHandler\|SpellItemTooltipHandler" insanetweaks/src/main
```

Expected: no output.

- [ ] **Step 7: Build**

Run:

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

🚨 Never `git add -A` in this repo — the working tree routinely carries unrelated in-progress work
across all six mods. Stage exactly these paths (the four deletions are already staged by `git rm` in
Step 1):

```bash
git add insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinSpell.java insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java insanetweaks/src/main/resources/mixins.insanetweaks.late.json insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "refactor(insanetweaks): delete the faked-element display layer, superseded by the real element"
```

Before committing, run `git status --short` and confirm nothing unexpected is staged.

---

### Task 14: Version bump

Both places, because this pair is the one that drifts: `build.gradle` feeds the jar manifest and
`mcmod.info`, while `VERSION` is what `@Mod` reports in the mod list.

**Files:**
- Modify: `insanetweaks/build.gradle` (the `version = '…'` line)
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (the `VERSION` constant)

🚨 Do not trust a line number or a base version written here — this repo's working tree is often
mid-edit and both values move. Read them first.

- [ ] **Step 1: Read the current version from both places**

```bash
grep -n "^version" insanetweaks/build.gradle; grep -n 'VERSION = ' insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
```

Expected: two lines quoting the **same** version string. If they disagree, that drift is a
pre-existing bug — fix it to the higher of the two as part of this step and say so in the commit
message.

- [ ] **Step 2: Bump the minor, reset the patch**

This change adds a feature, so `1.14.2` becomes `1.15.0`, `1.15.3` becomes `1.16.0`, and so on. Edit
both places to the new value: the `version = '…'` assignment in `insanetweaks/build.gradle` and the
`public static final String VERSION = "…";` constant in `InsaneTweaksMod.java`.

- [ ] **Step 3: Verify they agree**

```bash
grep -n "^version" insanetweaks/build.gradle; grep -n 'VERSION = ' insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
```

Expected: both quote the new version, identically.

- [ ] **Step 4: Build and commit**

```bash
./gradlew :insanetweaks:build
git add insanetweaks/build.gradle insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "chore(insanetweaks): 1.15.0 - Abomination element"
```

---

### Task 15: Runtime verification in the DEv 1.2 instance

This is where the work is actually proven. Nothing before this point can show that a mixin applied.

**Files:** none modified.

- [ ] **Step 1: Install the jar**

```bash
ls insanetweaks/build/libs/; ls "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/" | grep insanetweaks
```

Copy the freshly built `insanetweaks-<new version>.jar` into
`C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\` and **delete the older
`insanetweaks-*.jar` the second command listed**. Two jars of one modid is a duplicate-mod crash.

- [ ] **Step 2: Launch the client and check the element registered**

In `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\logs\latest.log`:

```bash
grep "Abomination element" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/latest.log"
```

Expected: `[InsaneTweaks] Abomination element registered at ordinal 8. EXTENDED=true.`
If it says `EXTENDED=false`, stop — read the stack trace above it; nothing else in this list is
meaningful in that state.

- [ ] **Step 3: Check every mixin applied**

```bash
grep -E "MixinElementFromName|Elements " "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log"
```

Expected: an `APPLY` line for `MixinElementFromName` and for each of the eleven `*Elements` mixins.

🚨 A **missing** `Mixing`/`APPLY` line is not automatically a failure: mixins apply lazily at target
classload, so `MixinWorldGenShrineElements` may not appear until new chunks generate. Distinguish the
two by grepping `cleanmix.log` for the target class name — no hits at all means "never loaded", not
"failed".

- [ ] **Step 4: Check no injection failed**

```bash
grep -E "InvalidInjectionException|Scanned 0|VerifyError" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log"
```

Expected: no output.

- [ ] **Step 5: Check the visual identity, in game**

Each of these must show a red **Abomination**, not a grey "None":

1. Spell-book tooltip for any insanetweaks spell.
2. Scroll tooltip.
3. The wand HUD while an insanetweaks spell is selected.
4. The wizard handbook's spell entry.
5. The arcane workbench.
6. The JEI entry.

- [ ] **Step 6: Check the ninth subtype is gone**

In creative and in JEI, search `magic crystal`, `spectral dust`, `crystal block` and `runestone`.
Expected: eight variants each, no Abomination one, no missing-model placeholder.

- [ ] **Step 7: Check worldgen and spawning**

Fly to unexplored terrain so fresh chunks generate, and spawn both wizard types:

```
/summon ebwizardry:wizard
/summon ebwizardry:evil_wizard
```

Expected: every wizard carries a wand; none is Abomination. No Abomination shrine or obelisk.

- [ ] **Step 8: Check the casting gate**

| setup | expected |
|---|---|
| an insanetweaks spell on a vanilla EBW wand | blocked, red chat message |
| the same spell on the Living Wand | casts |
| the same spell on a vanilla wand with the Adaptation upgrade applied | casts |
| a vanilla EBW spell on an adapted focus | casts at base mana cost, no surcharge |

- [ ] **Step 9: Check the dedicated server starts**

Launch the dedicated server with the same jar. Expected: it reaches "Done" with no
`NoClassDefFoundError` and no `Attempted to load class … for invalid side SERVER`.

- [ ] **Step 10: Record the result**

If every check passes, note it in the spec's Status line
(`docs/superpowers/specs/2026-08-08-ebw-abomination-element-design.md`) and commit:

```bash
git commit -am "docs: mark the Abomination element spec verified in DEv 1.2"
```

---

## Deliberately not in this plan

- **Wands, armour, crystals, runestones or shrines for the new element.** Scope decision recorded in
  the spec: minimum now, expansion left open. Promoting Abomination to a fully generating element
  later means deleting redirects from Tasks 7-10, one row at a time.
- **No element on our wands or armour.** Spec §8 explains why: our gear is the pack's universal
  top-end, and an element on it would introduce an Abomination-only progression bonus.
- **Rebalancing mana numbers.** Removing the multipliers shifts the economy; wand mana capacity and
  the per-spell `cost` values in the 14 JSONs are the knobs, and that is its own pass.
- **The Arcane Adapted Fruit.** Kept exactly as it is, including its own foreign-spell multiplier.
  It is expected to be revisited with the planned unified mana pool.
