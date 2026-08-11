# Abomination Economy and Balance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Abomination element the things it lacks — visible items, a working imbuement path, a crafting currency of its own, and coherent spell numbers.

**Architecture:** Three strands, independent enough to land in any order. **Assets and un-hiding**: ship `spectral_dust_abomination` and `crystal_abomination` models plus the missing `ruined_spell_book_abomination` loot table into the `ebwizardry` resource domain from our jar, then delete the two `getSubItems` mixins and three of the four JEI mixins that were hiding the element. **Economy**: a new `insanetweaks:magic_nucleus` item, ore-named `itMagicNucleus`, made from Abomination dust plus SRP drops nothing else uses, which takes over `adaptation_upgrade` and `living_wand` from the pack-wide `itLivingNucleus` bottleneck. **Balance**: a tool/ritual split across the 13 gameplay spells and a per-wand mana capacity read from config.

**Tech Stack:** Minecraft 1.12.2, Forge 14.23.5.2860, Java 8 source level, Gradle multi-project with ForgeGradle 3. Target dependency: Electroblob's Wizardry 4.3.19 (CurseMaven file id `8320066`). Python 3.13 with Pillow 12.3 is available for the texture step.

**Spec:** `docs/superpowers/specs/2026-08-08-abomination-economy-and-balance-design.md`

**Branch:** `feat/abomination-economy`, already created off `main` at merge commit `46c6727`.

---

## Before you start

**There is no test suite and no lint task.** `CLAUDE.md` is explicit about it. Do not invent one.

What replaces TDD here, in order of strength:

1. **`./gradlew :insanetweaks:build`** — the compiler is the fast feedback loop. Every code task ends with a build.
2. **Reading the artefact you just produced** — for JSON and asset tasks, the build cannot help you. Each such task has an explicit inspection step (parse the JSON, list the jar) instead.
3. **A real launch of the DEv 1.2 instance** — Task 12, not optional.

**This plan removes three mixins and adds one.** Net −2. Task 1 adds `MixinBlockReceptacleColours`; Tasks 2 and 3 delete `MixinItemSpectralDustElements`, `MixinItemCrystalElements` and `MixinJeiArcaneWorkbenchElements`, and trim `MixinJeiImbuementAltarElements` to a single redirect. Outside Task 1, if you find yourself writing a `@Mixin` annotation, stop and re-read the task.

Paths are relative to the repo root `E:\Isuth\modDev` unless stated otherwise. The content mod's Java root is `insanetweaks/src/main/java/com/spege/insanetweaks/`.

🚨 **Commit hygiene.** `git commit` commits the whole **index**, not just what you staged. Every commit in this plan names its paths:

```bash
git commit -m "your message" -- path/one path/two
```

`-m` and its message come **before** `--`. And `git commit -- <paths>` cannot reference a file git has never seen, so new files need `git add -- <path>` first.

---

### Task 1: Abomination particle colours in receptacles

Removes a client crash. **Five** places read `BlockReceptacle.PARTICLE_COLOURS` and dereference the result unchecked — `BlockReceptacle.randomDisplayTick`, `TileEntityImbuementAltar`, `EntityRemnant.onUpdate`, `RenderImbuementAltar`, `RenderDonationPerks` — and the map holds the eight vanilla elements only.

🚨 **Read this before you write anything.** The first version of this task was wrong and was reverted in review; the trap is worth understanding so you do not walk back into it. The field is declared

```java
public static final Map<Element, int[]> PARTICLE_COLOURS;
```

and `<clinit>` does fill a `Maps.newEnumMap(Element.class)` — but its **last line** is `PARTICLE_COLOURS = Maps.immutableEnumMap((Map) map);`. Guava's `ImmutableEnumMap.put` throws `UnsupportedOperationException` unconditionally. Because the field's *declared* type is plain `java.util.Map`, a `PARTICLE_COLOURS.put(...)` compiles silently and then hard-crashes the game at runtime. Reflection is no escape either: the pack runs Java 25, where `Field.class.getDeclaredField("modifiers")` is filtered.

The one writable moment is inside `<clinit>`, before the immutable copy is taken. So: a `@Redirect` of that single `Maps.immutableEnumMap` invoke, which adds Abomination to the still-mutable builder and delegates. Descriptor confirmed by `javap -p -c` on `ElectroblobsWizardry-4.3.19.jar`, one occurrence in the class:

```
385: invokestatic  #655  // Method com/google/common/collect/Maps.immutableEnumMap:(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;
388: putstatic     #428  // Field PARTICLE_COLOURS:Ljava/util/Map;
```

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/init/ModElements.java`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`
- Create: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinBlockReceptacleColours.java`
- Modify: `insanetweaks/src/main/resources/mixins.insanetweaks.late.json`

- [ ] **Step 1: Undo the `init`-phase call**

Commit `5c14dd4` added `ModElements.installReceptacleColours()` and a call to it from `InsaneTweaksMod.init(FMLInitializationEvent)`. **Delete the call site entirely** and **delete the `installReceptacleColours()` method**, including its javadoc — its central claim ("the map itself is a mutable `EnumMap`") is false and must not survive.

**Keep** the `RECEPTACLE_COLOURS` field, and move it up beside the other static fields at the top of the class rather than leaving it after the static initialiser.

- [ ] **Step 2: Give `ModElements` the writer the mixin will call**

Add, in place of the deleted method:

```java
    /**
     * Adds Abomination's colours to a receptacle colour table that is still being built.
     *
     * <p>Called from {@code MixinBlockReceptacleColours} during {@code BlockReceptacle.<clinit>},
     * which is the only moment the table is writable: its final act is to wrap itself in a Guava
     * {@code ImmutableEnumMap}, whose {@code put} throws. Five client-side call sites read that
     * table and dereference the result without a null check, so an element missing from it is a
     * crash, not a cosmetic gap.
     *
     * <p>Ordering is sound: the builder is an {@code EnumMap}, whose key universe comes from
     * {@code Element.class.getEnumConstants()} at construction; Forge's {@code EnumHelper.addEnum}
     * clears that cache when it appends a constant; and we append from the {@code @Mod} constructor,
     * long before block registration runs this {@code <clinit>}.
     */
    public static void addReceptacleColour(java.util.Map<Element, int[]> colours) {
        if (!EXTENDED) {
            return;
        }
        colours.put(ABOMINATION, RECEPTACLE_COLOURS);
    }
```

- [ ] **Step 3: Write the mixin**

Create `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinBlockReceptacleColours.java`:

```java
package com.spege.insanetweaks.mixins;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.block.BlockReceptacle;
import electroblob.wizardry.constants.Element;

/**
 * Puts Abomination into {@code BlockReceptacle.PARTICLE_COLOURS} at the only moment that table can
 * still be written to.
 *
 * <p>The field looks writable - it is declared as a plain {@code java.util.Map} - but
 * {@code <clinit>} ends with {@code PARTICLE_COLOURS = Maps.immutableEnumMap(map)}, and Guava's
 * {@code ImmutableEnumMap.put} throws unconditionally. A {@code put} call site therefore compiles
 * without a warning and crashes the game. Redirecting the wrap itself is the only route.
 *
 * <p>Worth the injection because five client-side sites read this table and dereference the result
 * unchecked - {@code randomDisplayTick}, {@code TileEntityImbuementAltar},
 * {@code EntityRemnant.onUpdate}, {@code RenderImbuementAltar} and {@code RenderDonationPerks} -
 * and one redirect covers all five.
 */
@Mixin(value = BlockReceptacle.class, remap = false)
public abstract class MixinBlockReceptacleColours {

    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                     target = "Lcom/google/common/collect/Maps;immutableEnumMap(Ljava/util/Map;)Lcom/google/common/collect/ImmutableMap;"))
    private static ImmutableMap<Element, int[]> insanetweaks$addAbominationColour(Map<Element, int[]> colours) {
        ModElements.addReceptacleColour(colours);
        return Maps.immutableEnumMap(colours);
    }
}
```

Note there is **no static field** on this mixin — the colour array lives in `ModElements`, a plain class. A `private static final` initialiser on a mixin class fails verification when the merged `<clinit>` is built; `CLAUDE.md` documents that as a hard rule.

- [ ] **Step 4: Register the mixin**

Add `"MixinBlockReceptacleColours"` to the `mixins` array in `insanetweaks/src/main/resources/mixins.insanetweaks.late.json`, keeping the array alphabetically sorted — it goes first, before `"MixinBlockCrystalElements"`.

This is a late config because `BlockReceptacle` is a mod class. That config has `"injectors": {"defaultRequire": 1}`, so a selector mistake is a load-time crash rather than a silent no-op — which is what we want here.

- [ ] **Step 5: Build**

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Verify the mixin will find its target**

The compiler cannot check a mixin selector. Confirm the invoke still exists, exactly once, in the jar the mod is compiled against:

```bash
mkdir -p /c/Users/spege/AppData/Local/Temp/claude/E--Isuth-modDev/47d29113-de9e-4698-852f-8ca0056a95d8/scratchpad/verify && cd /c/Users/spege/AppData/Local/Temp/claude/E--Isuth-modDev/47d29113-de9e-4698-852f-8ca0056a95d8/scratchpad/verify && unzip -o -q /e/Isuth/modDev/notes/decompiled_mods/ebwizardry_source/ElectroblobsWizardry-4.3.19.jar "electroblob/wizardry/block/BlockReceptacle.class" && javap -p -c electroblob/wizardry/block/BlockReceptacle.class | grep -c "immutableEnumMap"
```

Expected: `1`.

- [ ] **Step 7: Commit**

```bash
git commit -m "fix(insanetweaks): write the receptacle colours where the map is still mutable

The previous commit put Abomination straight into BlockReceptacle.PARTICLE_COLOURS
from the init phase. That was a guaranteed startup crash: the field is declared as
a plain java.util.Map, so the call compiled clean, but <clinit> ends with
Maps.immutableEnumMap(...) and Guava's put throws unconditionally.

The only writable moment is inside that <clinit>, before the wrap - so redirect the
wrap. One injection covers all five unchecked read sites, not just randomDisplayTick:
the imbuement altar, EntityRemnant and two renderers dereference the same table." -- insanetweaks/src/main/java/com/spege/insanetweaks/init/ModElements.java insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinBlockReceptacleColours.java insanetweaks/src/main/resources/mixins.insanetweaks.late.json
```

---

### Task 2: Dust and crystal assets, and un-hiding them

`ItemSpectralDust.getModelName` and `ItemCrystal.getModelName` derive `ebwizardry:spectral_dust_abomination` and `ebwizardry:crystal_abomination` from the element name. Model registration goes through `WizardryModels.registerMultiTexturedModel`, which iterates `getSubItems` — so while the two redirects are in place, meta 8's model is not requested at all. Removing them is what creates the demand, which is why the files and the deletions must land together.

Shipping files under `assets/ebwizardry/` is safe **for names EBW does not have**: resource packs merge at file granularity, so we add without replacing.

**Files:**
- Create: `insanetweaks/src/main/resources/assets/ebwizardry/models/item/spectral_dust_abomination.json`
- Create: `insanetweaks/src/main/resources/assets/ebwizardry/models/item/crystal_abomination.json`
- Create: `insanetweaks/src/main/resources/assets/ebwizardry/textures/items/spectral_dust_abomination.png`
- Create: `insanetweaks/src/main/resources/assets/ebwizardry/textures/items/crystal_abomination.png`
- Delete: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemSpectralDustElements.java`
- Delete: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemCrystalElements.java`
- Modify: `insanetweaks/src/main/resources/mixins.insanetweaks.late.json`

- [ ] **Step 1: Confirm the names are not EBW's**

```bash
unzip -l notes/decompiled_mods/ebwizardry_source/ElectroblobsWizardry-4.3.19.jar | grep -E "spectral_dust_abomination|crystal_abomination"
```

Expected: **no output**. Any hit means EBW ships that file and adding ours would replace theirs — stop and report.

- [ ] **Step 2: Generate the two textures**

Write this to the scratchpad and run it. It recolours EBW's necromancy variants to red, preserving saturation and value, so the new icons sit in the same visual family as the rest of the set.

```bash
mkdir -p /c/Users/spege/AppData/Local/Temp/claude/E--Isuth-modDev/47d29113-de9e-4698-852f-8ca0056a95d8/scratchpad/tex
cd /c/Users/spege/AppData/Local/Temp/claude/E--Isuth-modDev/47d29113-de9e-4698-852f-8ca0056a95d8/scratchpad/tex
unzip -o -j /e/Isuth/modDev/notes/decompiled_mods/ebwizardry_source/ElectroblobsWizardry-4.3.19.jar "assets/ebwizardry/textures/items/spectral_dust_necromancy.png" "assets/ebwizardry/textures/items/crystal_necromancy.png"
```

```python
# recolour.py
import colorsys
from PIL import Image

def recolour(src, dst, hue_deg):
    img = Image.open(src).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            r2, g2, b2 = colorsys.hsv_to_rgb(hue_deg / 360.0, s, v)
            px[x, y] = (int(r2 * 255 + 0.5), int(g2 * 255 + 0.5), int(b2 * 255 + 0.5), a)
    img.save(dst)

recolour("spectral_dust_necromancy.png", "spectral_dust_abomination.png", 348)
recolour("crystal_necromancy.png", "crystal_abomination.png", 0)
```

The dust uses crimson (348°) rather than pure red, deliberately. Measured over opaque pixels, `spectral_dust_fire` sits at hue 11 and our first attempt at hue 0 landed close enough that both read as red-orange flame silhouettes of identical shape at 16 px — distinguishable side by side, easy to mis-grab from a hotbar. The crystal has no such neighbour (`crystal_fire` is hue 21 but a third brighter), so it stays at 0.

Run it, then copy both outputs to `insanetweaks/src/main/resources/assets/ebwizardry/textures/items/`.

These are derived placeholders of the same quality as the existing `element_icon_abomination.png`, which is still a byte-copy of EBW's "None" icon. A real art pass covers all three at once and is not part of this plan.

- [ ] **Step 3: Write the two model files**

`insanetweaks/src/main/resources/assets/ebwizardry/models/item/spectral_dust_abomination.json`:

```json
{
    "parent": "item/generated",
    "textures": {
        "layer0": "ebwizardry:items/spectral_dust_abomination"
    }
}
```

`insanetweaks/src/main/resources/assets/ebwizardry/models/item/crystal_abomination.json`:

```json
{
    "parent": "item/generated",
    "textures": {
        "layer0": "ebwizardry:items/crystal_abomination"
    }
}
```

- [ ] **Step 3b: Add the crystal's lang key**

🚨 The two items differ here and it is easy to get backwards. `ItemSpectralDust` does **not** override `getUnlocalizedName(ItemStack)`, so all eight dusts share `item.ebwizardry:spectral_dust.name` and ours needs no key. `ItemCrystal` **does** override it, as `"item." + getModelName(stack)`, so it asks for `item.ebwizardry:crystal_abomination.name` — a key EBW ships for its own eight and cannot ship for ours. Without it the item's name renders as the literal key and JEI cannot find it by search.

Add to `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang`, next to the existing `element.abomination` line. Lang keys are global, so our file can supply a key in EBW's namespace; escape the colon exactly as EBW does:

```
item.ebwizardry\:crystal_abomination.name=Abominable Crystal
item.ebwizardry\:crystal_abomination.desc=A crystal the colour of clotted blood, warm to the touch and faintly moving. Whatever it holds was alive once, and has not entirely stopped.
```

- [ ] **Step 4: Delete the two mixins and de-register them**

```bash
git rm insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemSpectralDustElements.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemCrystalElements.java
```

In `insanetweaks/src/main/resources/mixins.insanetweaks.late.json`, remove these two lines from the `mixins` array:

```json
    "MixinItemCrystalElements",
    "MixinItemSpectralDustElements",
```

Leave `MixinBlockCrystalElements` alone — the crystal **block** stays hidden, because `BlockCrystal` renders through a single `assets/ebwizardry/blockstates/crystal_block.json` listing every variant, and shipping our own copy would replace EBW's file and take the other eight variants with it.

- [ ] **Step 5: Verify the JSON still parses and the array shrank by exactly two**

```bash
python -c "import json; d=json.load(open('insanetweaks/src/main/resources/mixins.insanetweaks.late.json')); print(len(d['mixins'])); print([m for m in d['mixins'] if 'Item' in m])"
```

Expected: `15` and `[]`. Fifteen, not fourteen: the array started at sixteen, Task 1 added `MixinBlockReceptacleColours`, and this task removes two.

- [ ] **Step 6: Build**

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add -- insanetweaks/src/main/resources/assets/ebwizardry
git commit -m "feat(insanetweaks): Abomination dust and crystal become real items

EBW already derived spectral_dust_abomination and crystal_abomination from the
element name - the model loops were never redirected, only getSubItems was. So
this ships the two models and textures into EBW's resource domain (names EBW
does not have, so nothing is replaced) and drops the two hiding mixins.

The crystal BLOCK stays hidden: BlockCrystal renders from one blockstate file
listing all variants, and ours would replace it wholesale." -- insanetweaks/src/main/resources/assets/ebwizardry insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemSpectralDustElements.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinItemCrystalElements.java insanetweaks/src/main/resources/mixins.insanetweaks.late.json
```

---

### Task 3: JEI — keep only the crystal-block exclusion

Three of the four JEI redirects existed because Abomination had no textures and led nowhere. Task 2 changed that. Each is re-decided on its own merits.

🚨 `generateArmourRecipes` needs no exclusion because **EBW already wrote the guard**: it calls `TileEntityImbuementAltar.getImbuementResult(...)` and then `if (output.isEmpty()) continue;`. `getArmour(ABOMINATION, …)` is a registry lookup that misses, returns null, and `new ItemStack(null)` is empty — so the Abomination rows drop out on their own. This is what keeps the future `living_warlock_armour` hook free: register those items and the row appears with no code change anywhere.

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinJeiImbuementAltarElements.java`
- Delete: `insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinJeiArcaneWorkbenchElements.java`
- Modify: `insanetweaks/src/main/resources/mixins.insanetweaks.jei.json`

- [ ] **Step 1: Replace `MixinJeiImbuementAltarElements.java` in full**

```java
package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.integration.jei.ImbuementAltarRecipeCategory;

/**
 * Keeps the Abomination crystal <em>block</em> out of the Imbuement Altar's JEI recipe list.
 *
 * <p>Only the block. The crystal <em>item</em> recipe is now genuine - the item has a model and the
 * altar really does produce it - and the armour recipes need no help: EBW's own
 * {@code if (output.isEmpty()) continue;} drops them, because {@code getArmour} misses for
 * Abomination and yields an empty stack. That self-filtering is deliberate load-bearing behaviour:
 * the day {@code living_warlock_armour} is registered under the {@code ebwizardry} namespace, the
 * armour row appears by itself.
 *
 * <p>The block is different because {@code BlockCrystal} renders from a single blockstate file
 * listing every variant, which we cannot extend without replacing EBW's copy - so a ninth block
 * variant would show in JEI with no model behind it.
 */
@Mixin(value = ImbuementAltarRecipeCategory.class, remap = false)
public abstract class MixinJeiImbuementAltarElements {

    @Redirect(method = "generateCrystalBlockRecipes",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private static Element[] insanetweaks$narrowJeiCrystalBlockRecipes() {
        return NativeElements.values();
    }
}
```

- [ ] **Step 2: Delete the arcane workbench mixin**

```bash
git rm insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinJeiArcaneWorkbenchElements.java
```

- [ ] **Step 3: Update the JEI mixin config**

Replace the `client` array in `insanetweaks/src/main/resources/mixins.insanetweaks.jei.json` so the file reads:

```json
{
  "required": false,
  "minVersion": "0.8.2",
  "package": "com.spege.insanetweaks.mixins",
  "target": "@env(DEFAULT)",
  "compatibilityLevel": "JAVA_8",
  "mixins": [],
  "client": [
    "MixinJeiImbuementAltarElements"
  ],
  "server": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

Leave `LateMixinBooter` untouched — the config is still gated on `Loader.isModLoaded("jei")` and still has a mixin in it.

- [ ] **Step 4: Build**

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(insanetweaks): JEI shows the Abomination crystal and altar recipes again

Three of the four JEI redirects were placeholders for 'the element has no
textures yet'. Now it does. Only the crystal BLOCK stays excluded, because its
blockstate file cannot be extended without replacing EBW's.

The armour category needs no mixin at all: EBW skips empty outputs, and
getArmour misses for Abomination. That is also the hook - registering
living_warlock_armour later lights the row up with no code change." -- insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinJeiImbuementAltarElements.java insanetweaks/src/main/java/com/spege/insanetweaks/mixins/MixinJeiArcaneWorkbenchElements.java insanetweaks/src/main/resources/mixins.insanetweaks.jei.json
```

---

### Task 4: The missing `ruined_spell_book_abomination` loot table

This is the `Couldn't find resource table` WARN in the 1.15.1 launch log. EBW **already registers** the ResourceLocation — `WizardryLoot.RUINED_SPELL_BOOK_LOOT_TABLES` is built from `Element.values()` — so only the file is missing. No `LootTableList.register` call is needed on our side.

**Files:**
- Create: `insanetweaks/src/main/resources/assets/ebwizardry/loot_tables/gameplay/imbuement_altar/ruined_spell_book_abomination.json`

- [ ] **Step 1: Confirm EBW does not ship it**

```bash
unzip -l notes/decompiled_mods/ebwizardry_source/ElectroblobsWizardry-4.3.19.jar | grep "ruined_spell_book_abomination"
```

Expected: **no output**.

- [ ] **Step 2: Write the table**

```json
{
    "pools": [
        {
            "name": "spell_book",
            "rolls": 1,
            "entries": [
                {
                    "type": "item",
                    "name": "ebwizardry:spell_book",
                    "entryName": "matching_book",
                    "weight": 3,
                    "functions": [
                        {
                            "function": "ebwizardry:random_spell",
                            "elements": [
                                "abomination"
                            ],
                            "undiscovered_bias": 0.3
                        }
                    ]
                },
                {
                    "type": "item",
                    "name": "ebwizardry:spell_book",
                    "entryName": "random_book",
                    "weight": 2,
                    "functions": [
                        {
                            "function": "ebwizardry:random_spell",
                            "tiers": [
                                "novice",
                                "apprentice",
                                "advanced"
                            ],
                            "undiscovered_bias": 0.3
                        }
                    ]
                }
            ]
        }
    ]
}
```

Structurally EBW's `ruined_spell_book_fire.json` with the element swapped. No per-spell list is needed: `ebwizardry:random_spell` filters candidates by loot context, so `call_of_demise` (`treasure: false`) excludes itself.

- [ ] **Step 3: Verify it parses**

```bash
python -c "import json; d=json.load(open('insanetweaks/src/main/resources/assets/ebwizardry/loot_tables/gameplay/imbuement_altar/ruined_spell_book_abomination.json')); print(d['pools'][0]['entries'][0]['functions'][0]['elements'])"
```

Expected: `['abomination']`.

- [ ] **Step 4: Commit**

```bash
git add -- insanetweaks/src/main/resources/assets/ebwizardry/loot_tables
git commit -m "feat(insanetweaks): supply the missing ruined_spell_book_abomination table

EBW builds RUINED_SPELL_BOOK_LOOT_TABLES from Element.values(), so it already
registers ours - only the file was absent, which is the 'Couldn't find resource
table' WARN. With it in place the imbuement altar becomes a second, aimable way
to get Abomination spell books." -- insanetweaks/src/main/resources/assets/ebwizardry/loot_tables
```

---

### Task 5: The `magic_nucleus` item

A second crafting currency, so `adaptation_upgrade` and `living_wand` stop competing with SRParasites' own weapons and every parasite addon for `itLivingNucleus`.

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/init/ModItems.java`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/init/ModOreDict.java`
- Create: `insanetweaks/src/main/resources/assets/insanetweaks/models/item/magic_nucleus.json`
- Create: `insanetweaks/src/main/resources/assets/insanetweaks/textures/items/magic_nucleus.png`
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Declare the item**

In `ModItems.java`, immediately after the `INFECTIOUS_LONG_BLADE_FRAGMENT` declaration (around line 50), add:

```java
    /**
     * The magical half of the crafting economy. Unlike {@link #LIVING_NUCLEUS} this has no
     * swparasites counterpart, so it is registered unconditionally and its ore name resolves to
     * exactly one item - the ore entry exists so an addon could substitute one later.
     */
    public static final Item MAGIC_NUCLEUS = new Item().setRegistryName("insanetweaks", "magic_nucleus")
            .setUnlocalizedName("magic_nucleus").setCreativeTab(CreativeTabs.MISC);
```

- [ ] **Step 2: Register it**

In `ModItems.registerItems`, inside the `if (com.spege.insanetweaks.config.ModConfig.modules.enableSrpEbWizardryBridge)` block, on the line that currently reads:

```java
            event.getRegistry().registerAll(ADAPTATION_UPGRADE, ARCANE_ADAPTED_FRUIT);
```

change it to:

```java
            event.getRegistry().registerAll(ADAPTATION_UPGRADE, ARCANE_ADAPTED_FRUIT, MAGIC_NUCLEUS);
```

Register it **outside** the `if (!Loader.isModLoaded("swparasites"))` block that guards `LIVING_NUCLEUS` — this item is ours alone and has nothing to clash with.

- [ ] **Step 3: Register its model**

In `ModItems.registerModels`, next to `registerModel(ADAPTATION_UPGRADE);`, add:

```java
            registerModel(MAGIC_NUCLEUS);
```

- [ ] **Step 4: Add the ore name**

In `ModOreDict.java`, add the constant next to the existing two:

```java
    public static final String ORE_MAGIC_NUCLEUS = "itMagicNucleus";
```

and inside `register()`, after the two existing `registerComponent` calls:

```java
        // No swparasites counterpart exists, so this always resolves to our own item. The ore entry
        // is here for symmetry and so an addon can add a second source without touching our recipes.
        registerComponent(ORE_MAGIC_NUCLEUS, ModItems.MAGIC_NUCLEUS);
```

- [ ] **Step 5: Write the model file**

`insanetweaks/src/main/resources/assets/insanetweaks/models/item/magic_nucleus.json`:

```json
{
    "parent": "item/generated",
    "textures": {
        "layer0": "insanetweaks:items/magic_nucleus"
    }
}
```

- [ ] **Step 6: Generate the texture**

Recolour our own `living_nucleus.png` to violet — the colour already used for the assimilated-mage identity (`sim_wizard`'s spawn-egg colours are `0x2A1033` / `0x9B30D9`). That reads as "harvested magic" and keeps it distinct from both the yellow living nucleus and the red Abomination dust.

Reuse the `recolour` function from Task 2, Step 2:

```python
recolour("E:/Isuth/modDev/insanetweaks/src/main/resources/assets/insanetweaks/textures/items/living_nucleus.png",
         "magic_nucleus.png", 280)
```

Copy the output to `insanetweaks/src/main/resources/assets/insanetweaks/textures/items/magic_nucleus.png`.

- [ ] **Step 7: Add the lang entries**

In `insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang`, directly after the two `living_nucleus` lines (13 and 14), add:

```
item.magic_nucleus.name=§dMagic Nucleus
item.magic_nucleus.desc=§fArcana wrung out of an assimilated mage and set hard. A Component for §5wands and arcane upgrades§f.
```

- [ ] **Step 8: Build**

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```bash
git add -- insanetweaks/src/main/resources/assets/insanetweaks/models/item/magic_nucleus.json insanetweaks/src/main/resources/assets/insanetweaks/textures/items/magic_nucleus.png
git commit -m "feat(insanetweaks): magic_nucleus, the arcane half of the crafting economy

itLivingNucleus gates five of our recipes and is simultaneously a key component
of SRParasites' own weapons and every parasite addon, so everything magical we
hang on it competes with the whole pack for the same drops. This adds the
second currency; the rewiring is the next commit.

Registered unconditionally: unlike living_nucleus it has no swparasites
counterpart to clash with." -- insanetweaks/src/main/java/com/spege/insanetweaks/init/ModItems.java insanetweaks/src/main/java/com/spege/insanetweaks/init/ModOreDict.java insanetweaks/src/main/resources/assets/insanetweaks/models/item/magic_nucleus.json insanetweaks/src/main/resources/assets/insanetweaks/textures/items/magic_nucleus.png insanetweaks/src/main/resources/assets/insanetweaks/lang/en_us.lang
```

---

### Task 6: The three dust recipes

🚨 **These go in code, not JSON, and the reason matters.** A JSON recipe would have to hard-code the dust metadata as `8`, which is `ModElements.ABOMINATION.ordinal()` — true today only because we are the only mod in the pack that adds an element. A code recipe reads the ordinal, so it cannot drift. `ModRecipes` already registers recipes this way.

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/init/ModRecipes.java`

- [ ] **Step 1: Read the surrounding conventions**

```bash
sed -n 40,90p insanetweaks/src/main/java/com/spege/insanetweaks/init/ModRecipes.java
sed -n 145,180p insanetweaks/src/main/java/com/spege/insanetweaks/init/ModRecipes.java
```

You need `registerFallback(event, name, recipe)` and `safeItem(modid, path)` — both already exist in this file, and `safeItem` records misses so `registerFallback` can skip a recipe whose ingredients are absent. Use them; do not construct `ItemStack`s from `ForgeRegistries` directly.

- [ ] **Step 2: Add the three recipes**

In `registerRecipes(RegistryEvent.Register<IRecipe> event)`, after the existing `infectious_long_blade_fragment` fallback block, add:

```java
        // ---------------------------------------------------------------------
        // Abomination dust economy. Registered in code rather than JSON because every one of these
        // references the dust's METADATA, which is ModElements.ABOMINATION.ordinal() - a number a
        // JSON file would have to hard-code. It happens to be 8 today only because nothing else in
        // the pack adds an element.
        //
        // Skipped entirely in degraded mode: with no element there is no dust, and every recipe
        // below would silently mean "necromancy dust" or worse.
        // ---------------------------------------------------------------------
        if (com.spege.insanetweaks.init.ModElements.EXTENDED) {
            final int dustMeta = com.spege.insanetweaks.init.ModElements.ABOMINATION.ordinal();
            final Item spectralDust = safeItem("ebwizardry", "spectral_dust");
            final Item magicCrystal = safeItem("ebwizardry", "magic_crystal");

            // Dust from SRP leftovers. Deliberately expensive and low-yield: this is the fallback
            // for a supply that is meant to come from sim wizards, not the main road.
            registerFallback(event, "abomination_spectral_dust",
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "abomination_spectral_dust"),
                            new ItemStack(spectralDust, 2, dustMeta),
                            " Y ",
                            "FCF",
                            " H ",
                            'Y', new ItemStack(safeItem("srparasites", "ada_yelloweye_drop")),
                            'F', new ItemStack(safeItem("srparasites", "assimilated_flesh")),
                            'C', new ItemStack(magicCrystal, 1, 0),
                            'H', new ItemStack(safeItem("srparasites", "hive_scrap"))));

            // Magic nucleus. Shape mirrors living_nucleus (" S ", "FLF", " M ") so the two read as
            // siblings. Consuming an Abomination crystal is what gives that crystal a job and puts
            // the imbuement altar on the road to the wand upgrade instead of beside it.
            registerFallback(event, "magic_nucleus",
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "magic_nucleus"),
                            new ItemStack(ModItems.MAGIC_NUCLEUS),
                            " B ",
                            "DKD",
                            " V ",
                            'B', new ItemStack(safeItem("srparasites", "ada_burrower_drop")),
                            'D', new ItemStack(spectralDust, 1, dustMeta),
                            'K', new ItemStack(magicCrystal, 1, dustMeta),
                            'V', new ItemStack(safeItem("srparasites", "ada_viscera_drop"))));

            // Ruined spell books, so the altar has steady fuel and the spell path does not depend
            // on a rare drop either. Four more dust go into the receptacles, so one crafted book
            // costs six dust in total.
            registerFallback(event, "ruined_spell_book",
                    new ShapelessOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "ruined_spell_book"),
                            new ItemStack(safeItem("ebwizardry", "ruined_spell_book")),
                            new ItemStack(net.minecraft.init.Items.BOOK),
                            new ItemStack(spectralDust, 1, dustMeta),
                            new ItemStack(spectralDust, 1, dustMeta)));
        }
```

- [ ] **Step 3: Add any missing imports**

At the top of `ModRecipes.java`, ensure these are present (add only the ones that are not):

```java
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
```

- [ ] **Step 4: Build**

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`. A `cannot find symbol` on `ShapelessOreRecipe` means Step 3 was skipped.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(insanetweaks): recipes for Abomination dust, magic nucleus and ruined books

In code rather than JSON on purpose: each references the dust metadata, which is
ModElements.ABOMINATION.ordinal(). A JSON file would hard-code 8, which is true
only while nothing else in the pack adds an element.

The whole block is skipped in degraded mode - with no element there is no dust,
and every one of these would quietly mean some other element." -- insanetweaks/src/main/java/com/spege/insanetweaks/init/ModRecipes.java
```

---

### Task 7: Rewire `adaptation_upgrade` and `living_wand`

The rule a player can feel: **meat gates weapons, harvested magic gates wands.** `living_aegis`, `living_spellblade` and `parasite_living_nunchaku` keep `itLivingNucleus` and are not touched.

The old `itLivingNucleus` variant is **replaced, not kept alongside** — keeping both would leave the bottleneck in place and forfeit half the point. No world data is involved, so this is safe mid-playthrough.

**Files:**
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/recipes/adaptation_upgrade.json`
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/recipes/living_wand.json`

- [ ] **Step 1: Edit `adaptation_upgrade.json`**

Change the `"N"` key's ore name. The file becomes:

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    " V ",
    "GAG",
    " N "
  ],
  "key": {
    "V": {
      "item": "srparasites:vile_shell"
    },
    "G": {
      "item": "insanetweaks:golden_book"
    },
    "A": {
      "item": "ebwizardry:attunement_upgrade"
    },
    "N": {
      "type": "forge:ore_dict",
      "ore": "itMagicNucleus"
    }
  },
  "result": {
    "item": "insanetweaks:adaptation_upgrade",
    "count": 1
  }
}
```

- [ ] **Step 2: Edit `living_wand.json`**

Same single change, but here is the whole file after it so there is nothing to guess:

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    " N ",
    "VWV",
    "GR "
  ],
  "key": {
    "N": {
      "type": "forge:ore_dict",
      "ore": "itMagicNucleus"
    },
    "V": {
      "item": "srparasites:vile_shell"
    },
    "W": {
      "item": "ebwizardry:master_wand"
    },
    "R": {
      "item": "insanetweaks:rupter_solied"
    },
    "G": {
      "item": "insanetweaks:golden_book"
    }
  },
  "result": {
    "item": "insanetweaks:living_wand",
    "count": 1
  }
}
```

- [ ] **Step 3: Verify exactly two files changed and no other recipe lost its nucleus**

```bash
grep -rl "itLivingNucleus" insanetweaks/src/main/resources/assets/insanetweaks/recipes/
grep -rl "itMagicNucleus" insanetweaks/src/main/resources/assets/insanetweaks/recipes/
```

Expected first list: `living_aegis.json`, `living_spellblade.json`, `parasite_living_nunchaku.json` — three files, in any order.
Expected second list: `adaptation_upgrade.json`, `living_wand.json` — two files.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(insanetweaks): wands and arcane upgrades move off the living nucleus

adaptation_upgrade is a WAND upgrade - the thing that lets a foreign wand cast
Abomination - so gating it on parasite meat said nothing about what it does, and
put it in competition with every SRParasites weapon in the pack for the same
drops.

Replaced rather than added alongside: keeping both would leave the bottleneck
where it is. Aegis, spellblade and nunchaku are combat gear and keep the meat." -- insanetweaks/src/main/resources/assets/insanetweaks/recipes/adaptation_upgrade.json insanetweaks/src/main/resources/assets/insanetweaks/recipes/living_wand.json
```

---

### Task 8: Sim wizard drops

The flavourful source. `EntitySimBattlemage` extends `EntitySimWizard` and inherits `getLootTable` plus an ADEPT tier floor, so it needs no table of its own.

**Files:**
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/entities/sim_wizard.json`
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/entities/sim_wizard_adept.json`
- Modify: `insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/entities/sim_wizard_master.json`

- [ ] **Step 1: Add one pool to `sim_wizard.json`**

Append to the existing `pools` array, after the `arcane_residue` pool:

```json
        {
            "name": "abomination_dust",
            "rolls": 1,
            "entries": [
                {
                    "type": "item",
                    "name": "ebwizardry:spectral_dust",
                    "weight": 2,
                    "functions": [
                        { "function": "set_data", "data": 8 },
                        { "function": "set_count", "count": { "min": 1, "max": 1 } },
                        { "function": "looting_enchant", "count": { "min": 0, "max": 1 } }
                    ]
                },
                { "type": "empty", "weight": 4 }
            ]
        }
```

Weight 2 against empty 4 is a one-in-three chance of any dust at all.

- [ ] **Step 2: Add two pools to `sim_wizard_adept.json`**

Append both to the existing `pools` array:

```json
        {
            "name": "abomination_dust",
            "rolls": 1,
            "entries": [
                {
                    "type": "item",
                    "name": "ebwizardry:spectral_dust",
                    "weight": 1,
                    "functions": [
                        { "function": "set_data", "data": 8 },
                        { "function": "set_count", "count": { "min": 1, "max": 2 } },
                        { "function": "looting_enchant", "count": { "min": 0, "max": 1 } }
                    ]
                }
            ]
        },
        {
            "name": "abomination_spell_book",
            "rolls": 1,
            "entries": [
                {
                    "type": "item",
                    "name": "ebwizardry:spell_book",
                    "weight": 3,
                    "functions": [
                        {
                            "function": "ebwizardry:random_spell",
                            "elements": [ "abomination" ],
                            "undiscovered_bias": 0.3
                        }
                    ]
                },
                { "type": "empty", "weight": 97 }
            ]
        }
```

- [ ] **Step 3: Add two pools to `sim_wizard_master.json`**

Append both to the existing `pools` array, after `scavenged_focus`:

```json
        {
            "name": "abomination_dust",
            "rolls": 1,
            "entries": [
                {
                    "type": "item",
                    "name": "ebwizardry:spectral_dust",
                    "weight": 1,
                    "functions": [
                        { "function": "set_data", "data": 8 },
                        { "function": "set_count", "count": { "min": 2, "max": 4 } },
                        { "function": "looting_enchant", "count": { "min": 0, "max": 2 } }
                    ]
                }
            ]
        },
        {
            "name": "abomination_spell_book",
            "rolls": 1,
            "entries": [
                {
                    "type": "item",
                    "name": "ebwizardry:spell_book",
                    "weight": 8,
                    "functions": [
                        {
                            "function": "ebwizardry:random_spell",
                            "elements": [ "abomination" ],
                            "undiscovered_bias": 0.3
                        }
                    ]
                },
                { "type": "empty", "weight": 92 }
            ]
        }
```

The `data: 8` here is the same ordinal Task 6 refused to hard-code, and here we have no choice — loot-table JSON has no way to compute it. That is acceptable because a wrong number yields the wrong dust, not a crash, and Task 12 checks the drop by eye. If the pack ever gains another element-adding mod, these three files are the first place to look.

- [ ] **Step 4: Verify all three parse and gained the right pools**

```bash
python -c "
import json
for t in ['sim_wizard','sim_wizard_adept','sim_wizard_master']:
    p='insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/entities/%s.json'%t
    d=json.load(open(p))
    print(t, [q['name'] for q in d['pools']])
"
```

Expected:
```
sim_wizard ['arcane_residue', 'abomination_dust']
sim_wizard_adept ['arcane_residue', ..., 'abomination_dust', 'abomination_spell_book']
sim_wizard_master ['arcane_residue', 'focus_fragments', 'scavenged_focus', 'abomination_dust', 'abomination_spell_book']
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(insanetweaks): sim wizards drop Abomination dust and, rarely, spells

The flavourful source for the new currency, scaled by tier. The battlemage needs
no table of its own - it extends EntitySimWizard and inherits getLootTable plus
an ADEPT tier floor.

Only the loot tables are touched here. Giving these mobs a spawn of their own
(today they exist only where SRP assimilates a wizard) is a separate session." -- insanetweaks/src/main/resources/assets/insanetweaks/loot_tables/entities
```

---

### Task 9: Per-wand mana capacity

`ItemWand.getManaCapacity(stack)` returns `getMaxDamage(stack)`, which is `super.getMaxDamage(stack) * (1 + STORAGE_INCREASE_PER_LEVEL * storageUpgradeLevel)`, and the base comes from `tier.maxCharge` set in the constructor. EBW's stock master wand is 2500.

🚨 **Override `getMaxDamage(ItemStack)`; do NOT call `setMaxDamage` in the constructor.** `ModItems` is a `@Mod.EventBusSubscriber`, so its `<clinit>` — and therefore every `new …WandItem()` — can run before Forge's first `ConfigManager.sync`. A config read in the constructor would silently capture the Java field default instead of the file value. Reading at call time cannot go wrong.

**Files:**
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/GearCategory.java`
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/items/wand/BaseCustomWandItem.java`

- [ ] **Step 1: Add two config fields**

In `GearCategory.java`, inside `public static class Wands`, after the `sentientExtraMinion` field:

```java
        @Config.Name("Living Wand Mana Capacity")
        @Config.Comment({
                "How much mana a Living Wand holds before storage upgrades. EBW's own master wands",
                "hold 2500, so this is where our wands earn the right to carry the big rituals.",
                "Storage upgrades still multiply on top of it exactly as they do for any wand.",
                "Read live - no restart needed. Default 3200." })
        @Config.RangeInt(min = 100, max = 100000)
        public int livingManaCapacity = 3200;

        @Config.Name("Sentient Wand Mana Capacity")
        @Config.Comment({
                "How much mana a Sentient Wand holds before storage upgrades. This is the endgame",
                "wand, so it is the one that can afford Call of Demise without being emptied.",
                "Read live - no restart needed. Default 4400." })
        @Config.RangeInt(min = 100, max = 100000)
        public int sentientManaCapacity = 4400;
```

- [ ] **Step 2: Override `getMaxDamage` in `BaseCustomWandItem`**

Add these two methods after the existing `getPotencyBonus()` method:

```java
    /**
     * Mana capacity, read from config at call time.
     *
     * <p>EBW routes {@code getManaCapacity} straight through {@code getMaxDamage}, whose base is
     * {@code tier.maxCharge} fixed in the constructor. We cannot set that base from config in the
     * constructor: {@code ModItems} is a {@code @Mod.EventBusSubscriber}, so its {@code <clinit>}
     * can run before Forge's first {@code ConfigManager.sync}, and the read would capture the Java
     * field default instead of the file value - silently.
     *
     * <p>The scaling expression is EBW's own, copied deliberately so storage upgrades keep
     * behaving identically. If EBW ever changes it, the symptom is a different number, never a
     * crash.
     */
    @Override
    public int getMaxDamage(ItemStack stack) {
        int base = this.getBaseManaCapacity();
        if (base <= 0) {
            return super.getMaxDamage(stack);
        }
        int storage = electroblob.wizardry.util.WandHelper.getUpgradeLevel(
                stack, electroblob.wizardry.registry.WizardryItems.storage_upgrade);
        return (int) (base * (1.0F + electroblob.wizardry.constants.Constants.STORAGE_INCREASE_PER_LEVEL * storage) + 0.5F);
    }

    /** Zero means "not one of ours" - fall back to whatever the tier says. */
    private int getBaseManaCapacity() {
        ResourceLocation reg = this.getRegistryName();
        if (reg != null) {
            if ("living_wand".equals(reg.getResourcePath())) {
                return com.spege.insanetweaks.config.ModConfig.gear.wands.livingManaCapacity;
            }
            if ("sentient_wand".equals(reg.getResourcePath())) {
                return com.spege.insanetweaks.config.ModConfig.gear.wands.sentientManaCapacity;
            }
        }
        return 0;
    }
```

`ResourceLocation` and `ItemStack` are already imported in this file. The three EBW types are written fully qualified to match the file's existing style for config access.

- [ ] **Step 3: Build**

```bash
./gradlew :insanetweaks:build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(insanetweaks): our wands carry their own mana capacity

3200 and 4400 against EBW's stock master 2500. These are the top-end wands every
mage in the pack wants, so it is right that they are the ones that can afford a
ritual.

Overriding getMaxDamage rather than calling setMaxDamage in the constructor:
ModItems is an EventBusSubscriber, so its <clinit> can beat ConfigManager.sync
and a constructor read would silently capture the field default. Nothing else's
wands are affected and no shared config is touched." -- insanetweaks/src/main/java/com/spege/insanetweaks/config/categories/GearCategory.java insanetweaks/src/main/java/com/spege/insanetweaks/items/wand/BaseCustomWandItem.java
```

---

### Task 10: Spell rebalance

The rule, from the spec: a **tool** costs ≤ 5% of a full wand with a 5–15 s cooldown; a **ritual** costs ≥ 10% with a cooldown of 60 s or more. The 5–10% band stays empty on purpose — a spell that lands there is one whose role has not been decided.

**Files:** thirteen files in `insanetweaks/src/main/resources/assets/insanetweaks/spells/`. `test_projectile.json` is **not** one of them — it stays exactly as it is (see the note at the end of this task).

- [ ] **Step 1: Apply the values**

For each file, change only `cost`, `chargeup` and `cooldown`. Leave `tier`, `element`, `type`, `enabled` and `base_properties` untouched.

| file | cost | chargeup | cooldown |
|---|---|---|---|
| `dispatcher_grasp.json` | 130 | 20 | 200 |
| `yelloweye_gland.json` | 150 | 30 | 240 |
| `immune_bond.json` | 140 | 30 | 300 |
| `summon_thrall.json` | 320 | 40 | 1200 |
| `summon_fer_cow.json` | 340 | 45 | 1300 |
| `summon_wizard.json` | 420 | 55 | 1600 |
| `summon_primitive_yelloweye.json` | 450 | 45 | 1800 |
| `summon_light_bomber.json` | 500 | 45 | 1800 |
| `summon_primitive_summoner.json` | 560 | 60 | 2600 |
| `parasite_shroud.json` | 400 | 80 | 1800 |
| `cleanse.json` | 500 | 60 | 3600 |
| `purifying_pulse.json` | 800 | 100 | 6000 |
| `call_of_demise.json` | 1800 | 180 | 12000 |

`call_of_demise` is unchanged — it was already the one number that was right. `immune_bond` keeps `"tier": "advanced"`; only its price comes down, from 280, which was more than most master *tools*.

Worked example — `dispatcher_grasp.json` in full after the edit:

```json
{
  "enabled": {
    "book": true,
    "scroll": true,
    "wands": true,
    "npcs": false,
    "dispensers": true,
    "commands": true,
    "treasure": true,
    "trades": true,
    "looting": true
  },
  "tier": "master",
  "element": "abomination",
  "type": "attack",
  "cost": 130,
  "chargeup": 20,
  "cooldown": 200,
  "base_properties": {
    "range": 10,
    "damage": 4,
    "execute_threshold": 0.2
  }
}
```

- [ ] **Step 2: Verify every file against the table**

```bash
cd insanetweaks/src/main/resources/assets/insanetweaks/spells && python -c "
import json, glob, os
want = {
 'dispatcher_grasp':(130,20,200), 'yelloweye_gland':(150,30,240), 'immune_bond':(140,30,300),
 'summon_thrall':(320,40,1200), 'summon_fer_cow':(340,45,1300), 'summon_wizard':(420,55,1600),
 'summon_primitive_yelloweye':(450,45,1800), 'summon_light_bomber':(500,45,1800),
 'summon_primitive_summoner':(560,60,2600), 'parasite_shroud':(400,80,1800),
 'cleanse':(500,60,3600), 'purifying_pulse':(800,100,6000), 'call_of_demise':(1800,180,12000),
 'test_projectile':(15,5,20),
}
bad = 0
for f in sorted(glob.glob('*.json')):
    n = os.path.splitext(f)[0]
    d = json.load(open(f))
    got = (d['cost'], d['chargeup'], d['cooldown'])
    if n not in want:
        print('UNEXPECTED FILE', n); bad += 1
    elif got != want[n]:
        print('MISMATCH', n, 'got', got, 'want', want[n]); bad += 1
print('OK' if bad == 0 else 'FAILURES: %d' % bad)
"
```

Expected: `OK`. Note the table includes `test_projectile` at its **current** values — if that line reports a mismatch, someone edited a file this task said to leave alone.

- [ ] **Step 3: Commit**

```bash
git commit -m "balance(insanetweaks): split the Abomination spells into tools and rituals

These numbers were set while a foreign-spell mana penalty multiplied them. That
penalty is gone and what was left did not hang together: dispatcher_grasp cost
1.4% of a wand, immune_bond cost more at ADVANCED tier than most master tools,
and parasite_shroud was the cheapest ritual with nearly the longest cooldown.

Rule: a tool is <=5% of a full wand on a 5-15s cooldown, a ritual is >=10% on a
minute or more, and the band between them is left empty on purpose. Call of
Demise is untouched - it was already right." -- insanetweaks/src/main/resources/assets/insanetweaks/spells
```

📌 **`test_projectile.json` stays.** Deleting a spell is not free: `Spell extends IForgeRegistryEntry.Impl<Spell>` and `Spells.createRegistry` never calls `disableSaving()`, so the id map lives in `level.dat` and removing a registered spell throws Forge's missing-registry-entry screen on every world that has ever loaded with it. The spell is `apprentice` tier with `npcs: false` and closed to `treasure`/`trades`/`looting`, so it never reaches a player anyway. If a spell is ever genuinely removed, it needs a `RegistryEvent.MissingMappings<Spell>` handler calling `mapping.ignore()` — the same shape as content's `LegacyDormantRemapHandler`, and `ignore()` rather than `remap()` because it leaves the id slot dead and so cannot shuffle the numeric ids `ItemSpellBook` stores in item metadata.

---

### Task 11: Version bump

Content's version lives in **two** places and the second is the one that drifts. `1.15.2` → `1.16.0` (new items and recipes, so a minor bump).

**Files:**
- Modify: `insanetweaks/build.gradle` line 23
- Modify: `insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (the `VERSION` constant)

- [ ] **Step 1: Edit `insanetweaks/build.gradle`**

```groovy
version = '1.16.0'
```

- [ ] **Step 2: Edit `InsaneTweaksMod.VERSION`**

```java
    public static final String VERSION = "1.16.0";
```

The manifest `Specification-Version` and `mcmod.info` both derive from the Gradle `version`, but `@Mod` reports the Java constant — that is the one that sat at `1.11.0` while the build said `1.12.1`, which made the version in a log meaningless.

- [ ] **Step 3: Verify both agree**

```bash
grep -n "^version" insanetweaks/build.gradle && grep -n 'VERSION = ' insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
```

Expected: both read `1.16.0`.

- [ ] **Step 4: Build and commit**

```bash
./gradlew :insanetweaks:build
```

```bash
git commit -m "chore(insanetweaks): 1.16.0" -- insanetweaks/build.gradle insanetweaks/src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
```

---

### Task 12: Runtime verification in DEv 1.2

Not optional. The build proves nothing about resource domains, loot tables, or whether a removed mixin left something behind.

- [ ] **Step 1: Deploy**

```bash
./gradlew :insanetweaks:build
```

Copy `insanetweaks/build/libs/insanetweaks-1.16.0.jar` into `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\` and **delete the old `insanetweaks-1.15.2.jar`**. Two versions of one modid is a duplicate-mod crash.

- [ ] **Step 2: Launch and check the logs**

```bash
grep -nE "InvalidInjectionException|Scanned 0|VerifyError" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log"
```

Expected: no output.

Then confirm the one **new** mixin actually applied — a missing `APPLY` line here is not "lazy loading", because `BlockReceptacle` is loaded during block registration on every launch:

```bash
grep -n "MixinBlockReceptacleColours" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/cleanmix.log"
```

Expected: an `APPLY … -> electroblob.wizardry.block.BlockReceptacle` line. Net across the plan: three mixins gone, one added.

```bash
grep -nE "ruined_spell_book_abomination|Unable to load model.*(spectral_dust_abomination|crystal_abomination)|receptacle particle colours" "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/logs/latest.log"
```

Expected: the `[InsaneTweaks] Abomination receptacle particle colours installed.` line from Task 1, and **nothing else** — no `Couldn't find resource table`, no missing-model errors.

- [ ] **Step 3: Check in-game**

- Creative and JEI show Abomination spectral dust and the Abomination crystal item.
- The crystal **block** still shows eight variants, not nine.
- JEI's imbuement altar category has an Abomination crystal recipe, has **no** Abomination crystal *block* recipe, and has **no** Abomination armour row.
- Place a receptacle, put Abomination dust in it, and watch it for at least ten seconds on the client. This is the NPE path from Task 1.
- Imbuement altar: ruined spell book plus four Abomination receptacles yields an Abomination spell book.
- Craft the dust, then `magic_nucleus`, then `adaptation_upgrade` and `living_wand` from it. Confirm the `itLivingNucleus` variants of those two are gone from JEI and that aegis, spellblade and nunchaku still craft.
- Kill sim wizards of each tier and confirm the dust drops, scaling with tier.
- Both our wands report the new capacity in the tooltip, and applying a storage upgrade still raises it.
- Load an existing DEv 1.2 world: no missing-registry screen.

- [ ] **Step 4: Report**

Write what passed and what did not. Do **not** mark this task complete on a partial pass — an unchecked box is information; a wrongly ticked one is not.

---

## Spec coverage

| spec section | task |
|---|---|
| §1.1 dust and crystal assets, un-hiding, crystal block stays hidden | 2 |
| §1.2 receptacle particles | 1 |
| §1.3 ruined spell book loot table | 4 |
| §1.4 JEI, three of four exclusions removed | 3 |
| §1.5 element icon | **not implemented** — see below |
| §2.2 magic_nucleus, ore name, recipes | 5, 6 |
| §2.3 dust sources: drops and craft | 6, 8 |
| §3.1–3.3 tool/ritual split | 10 |
| §3.4 wand capacity | 9 |
| §4 extension points | nothing to build; Task 3 preserves the hook |
| §5.1 spell removal hazard | 10, documented, deliberately not acted on |
| §5.4 version bump | 11 |
| §5.5 verification checklist | 12 |

**Deliberately not implemented: §1.5, the element icon.** `element_icon_abomination.png` is still a byte-copy of EBW's "None" icon, and Task 2 adds two more derived placeholder textures beside it. Drawing three icons is an art pass, not an engineering task, and doing it badly inside this plan would just make it harder to spot later. Track it separately.
