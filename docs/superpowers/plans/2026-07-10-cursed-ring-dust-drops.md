# Cursed Ring Parity + Infernal Spectral Dust Drops Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The plain `enigmaticlegacy:cursed_ring` unlocks the Corrupted Seed loop exactly like the Blessed Ring (plus player-facing info), and every InfernalMobs elite drops 1–2 random-element `ebwizardry:spectral_dust` on player kill.

**Architecture:** `EnigmaticLegacyCompat` gains a two-ring qualifying check consumed by the two existing call sites. The InfernalMobs reflection bridge moves from `ItemInfernalCrownArtefact` into a `util/InfernalMobsCompat` shim (existing compat pattern) shared by the Crown and a new `LivingDropsEvent` handler.

**Tech Stack:** Forge 1.12.2, Java 8. No test suite — verification is `./gradlew build` + the manual runClient checklist at the end.

**Spec:** `docs/superpowers/specs/2026-07-10-cursed-ring-dust-drops-design.md`

---

### Task 1: Two-ring qualifying check in EnigmaticLegacyCompat

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/util/EnigmaticLegacyCompat.java`
- Modify: `src/main/java/com/spege/insanetweaks/events/CorruptedFragmentDropHandler.java:33`
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntityCorruptedSapling.java:159`

- [ ] **Step 1: Rewrite the compat shim**

Replace the body of `EnigmaticLegacyCompat` (keep package, imports, class javadoc updated) with:

```java
public final class EnigmaticLegacyCompat {

    private static Item blessedRing;
    private static Item cursedRing;
    private static boolean lookedUp = false;

    private EnigmaticLegacyCompat() {
    }

    /**
     * True only when Enigmatic Legacy is installed AND the master interaction switch is on.
     * Gating the ring detection here means the whole Bauble Fruit acquisition path
     * (fragment drops + sapling growth) respects the config switch in one place.
     */
    public static boolean isLoaded() {
        return com.spege.insanetweaks.config.ModConfig.interactions.enableEnigmaticLegacyInteractions
                && Loader.isModLoaded("enigmaticlegacy");
    }

    /**
     * True when the player currently WEARS a ring that unlocks the Corrupted Seed loop:
     * the Blessed Ring OR the plain Cursed Ring (Ring of the Seven Curses) — full parity
     * per spec 2026-07-10. False when Enigmatic Legacy or Baubles is absent.
     */
    @SuppressWarnings("null")
    public static boolean isWearingQualifyingRing(EntityPlayer player) {
        if (player == null || !isLoaded() || !Loader.isModLoaded("baubles")) {
            return false;
        }
        if (!lookedUp) {
            blessedRing = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("enigmaticlegacy", "blessed_ring"));
            cursedRing = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("enigmaticlegacy", "cursed_ring"));
            lookedUp = true;
        }
        if (blessedRing == null && cursedRing == null) {
            return false;
        }
        baubles.api.cap.IBaublesItemHandler handler = baubles.api.BaublesApi
                .getBaublesHandler((net.minecraft.entity.EntityLivingBase) player);
        if (handler == null) {
            return false;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            Item worn = handler.getStackInSlot(i).getItem();
            if ((blessedRing != null && worn == blessedRing)
                    || (cursedRing != null && worn == cursedRing)) {
                return true;
            }
        }
        return false;
    }
}
```

(The old `isWearingBlessedRing` is deleted — Step 2 migrates both call sites.)

- [ ] **Step 2: Migrate the call sites**

`CorruptedFragmentDropHandler.java:33`:
```java
        if (!EnigmaticLegacyCompat.isWearingQualifyingRing(killer)) return;
```

`EntityCorruptedSapling.java:159`:
```java
        if (!EnigmaticLegacyCompat.isWearingQualifyingRing(owner)) {
```

Then run: `grep -rn "isWearingBlessedRing" src/main/java` — expected: no matches.

- [ ] **Step 3: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add src/main/java/com/spege/insanetweaks/util/EnigmaticLegacyCompat.java src/main/java/com/spege/insanetweaks/events/CorruptedFragmentDropHandler.java src/main/java/com/spege/insanetweaks/entities/EntityCorruptedSapling.java
git commit -m "feat: Cursed Ring full parity with Blessed Ring for the Corrupted Seed loop"
```

### Task 2: Player-facing information

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/events/CorruptedFragmentDropHandler.java`
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`, `ru_ru.lang`

- [ ] **Step 1: One-time chat hint**

In `CorruptedFragmentDropHandler.onLivingDrops`, AFTER the `isHighTier` check passes but BEFORE the drop-chance roll (i.e. between current lines 36 and 38), insert:

```java
        sendOneTimeHint(killer);
```

and add these members to the class:

```java
    private static final String HINT_SHOWN_TAG = "InsaneTweaksCorruptedHintShown";

    /** One-time flavor hint (per player, persists through death) the first time a
     *  qualifying-ring wearer kills a fragment-eligible parasite. */
    private static void sendOneTimeHint(EntityPlayer player) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        NBTTagCompound persisted = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(HINT_SHOWN_TAG)) return;
        persisted.setBoolean(HINT_SHOWN_TAG, true);
        player.sendMessage(new TextComponentTranslation("msg.insanetweaks.corrupted_hint"));
    }
```

Add imports: `net.minecraft.nbt.NBTTagCompound`, `net.minecraft.util.text.TextComponentTranslation`.

- [ ] **Step 2: Lang entries**

`en_us.lang`:
```
msg.insanetweaks.corrupted_hint=§5Your ring pulses hungrily... High-tier parasites slain while wearing the Blessed Ring or the Ring of the Seven Curses may yield Corrupted Seed Fragments.
```

`ru_ru.lang`:
```
msg.insanetweaks.corrupted_hint=§5Ваше кольцо жадно пульсирует... Сильные паразиты, убитые с Благословенным кольцом или Кольцом семи проклятий, могут оставлять фрагменты порченого семени.
```

- [ ] **Step 3: Update item tooltips to name both rings**

Run `grep -n "corrupted_seed\|corrupted_fruit" src/main/resources/assets/insanetweaks/lang/en_us.lang` to find the fragment/seed/fruit `.desc` lines. In BOTH lang files, wherever the text mentions only the Blessed Ring, reword to name both rings, e.g. (en): "…while wearing the Blessed Ring or the Ring of the Seven Curses." / (ru): "…нося Благословенное кольцо или Кольцо семи проклятий." Keep the rest of each line unchanged.

- [ ] **Step 4: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java src/main/resources
git commit -m "feat: one-time corrupted-loop hint + tooltips name both rings"
```

### Task 3: InfernalMobsCompat shim (reflection bridge extraction)

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/util/InfernalMobsCompat.java`
- Modify: `src/main/java/com/spege/insanetweaks/baubles/ItemInfernalCrownArtefact.java`

- [ ] **Step 1: Create the shim**

Move the ENTIRE body of `ItemInfernalCrownArtefact.InfernalMobsDirectAPI` (current lines 100-152: `forceInfernal`, `isRare`, `getCore`, the three cached `Method` fields) into a new top-level class:

```java
package com.spege.insanetweaks.util;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.entity.EntityLivingBase;

/**
 * Reflection bridge to InfernalMobs (atomicstryker.infernalmobs). NOT a mixin.
 * Shared by the Infernal Crown artefact (forceInfernal) and the spectral-dust
 * drop handler (isRare). All methods fail soft when InfernalMobs is absent.
 */
public final class InfernalMobsCompat {

    private static java.lang.reflect.Method instanceMethod;
    private static java.lang.reflect.Method addModifiersMethod;
    private static java.lang.reflect.Method isRareMethod;

    private InfernalMobsCompat() {
    }

    public static boolean forceInfernal(EntityLivingBase entity) {
        // ... body copied verbatim from ItemInfernalCrownArtefact.InfernalMobsDirectAPI.forceInfernal
    }

    public static boolean isRare(EntityLivingBase entity) {
        // ... body copied verbatim from InfernalMobsDirectAPI.isRare
    }

    private static Object getCore() throws Exception {
        // ... body copied verbatim from InfernalMobsDirectAPI.getCore
    }
}
```

Copy the three method bodies verbatim (they only reference the `Method` fields, `InsaneTweaksMod.LOGGER` and `Class.forName("atomicstryker.infernalmobs.common.InfernalMobsCore")`).

- [ ] **Step 2: Delegate from the Crown**

In `ItemInfernalCrownArtefact`:
1. DELETE the nested `InfernalMobsDirectAPI` class entirely.
2. Replace its two usages:
   - line ~61: `if (InfernalMobsDirectAPI.isRare(minion))` → `if (com.spege.insanetweaks.util.InfernalMobsCompat.isRare(minion))`
   - line ~68: `boolean success = InfernalMobsDirectAPI.forceInfernal(minion);` → `boolean success = com.spege.insanetweaks.util.InfernalMobsCompat.forceInfernal(minion);`
   - line ~69: `success || InfernalMobsDirectAPI.isRare(minion)` → `success || com.spege.insanetweaks.util.InfernalMobsCompat.isRare(minion)`

Then run: `grep -rn "InfernalMobsDirectAPI" src/main/java` — expected: no matches.

- [ ] **Step 3: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add src/main/java/com/spege/insanetweaks/util/InfernalMobsCompat.java src/main/java/com/spege/insanetweaks/baubles/ItemInfernalCrownArtefact.java
git commit -m "refactor: extract InfernalMobs reflection bridge to util/InfernalMobsCompat"
```

### Task 4: Infernal dust drop handler

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/InteractionsCategory.java`
- Create: `src/main/java/com/spege/insanetweaks/events/InfernalDustDropHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (init)

- [ ] **Step 1: Config fields**

Append to `InteractionsCategory`:

```java
    @Config.Comment({
            "When InfernalMobs is installed: every infernal (elite) mob killed by a player",
            "drops spectral dust of a random element (Electroblob's Wizardry).",
            "Kills without player credit (environment, other mobs, automated farms) drop nothing." })
    @Config.Name("Enable Infernal Spectral Dust Drops")
    @Config.RequiresMcRestart
    public boolean enableInfernalDustDrops = true;

    @Config.Comment("Minimum spectral dust dropped per infernal kill.")
    @Config.Name("Infernal Dust: Min")
    @Config.RangeInt(min = 0, max = 16)
    public int infernalDustMin = 1;

    @Config.Comment("Maximum spectral dust dropped per infernal kill.")
    @Config.Name("Infernal Dust: Max")
    @Config.RangeInt(min = 1, max = 16)
    public int infernalDustMax = 2;
```

- [ ] **Step 2: Create the handler**

```java
package com.spege.insanetweaks.events;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.util.InfernalMobsCompat;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Every InfernalMobs elite killed with player credit drops 1-2 (configurable)
 * ebwizardry:spectral_dust of a random element. Registered only when InfernalMobs
 * is loaded AND interactions.enableInfernalDustDrops is on.
 *
 * spectral_dust metadata = Element ordinal: 1 fire, 2 ice, 3 lightning,
 * 4 necromancy, 5 earth, 6 sorcery, 7 healing (0 = MAGIC, deliberately excluded —
 * it has no imbuement use).
 */
public class InfernalDustDropHandler {

    private static final int[] ELEMENT_METAS = {1, 2, 3, 4, 5, 6, 7};

    private static Item spectralDust;
    private static boolean lookedUp = false;
    private static boolean lookupFailed = false;

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingDrops(LivingDropsEvent event) {
        EntityLivingBase killed = event.getEntityLiving();
        if (killed.world.isRemote || lookupFailed) return;
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) return;
        if (!InfernalMobsCompat.isRare(killed)) return;

        if (!lookedUp) {
            spectralDust = ForgeRegistries.ITEMS.getValue(new ResourceLocation("ebwizardry", "spectral_dust"));
            lookedUp = true;
            if (spectralDust == null) {
                lookupFailed = true;
                InsaneTweaksMod.LOGGER.warn(
                        "[InsaneTweaks] ebwizardry:spectral_dust not found — infernal dust drops disabled.");
                return;
            }
        }

        int min = ModConfig.interactions.infernalDustMin;
        int max = Math.max(min, ModConfig.interactions.infernalDustMax);
        int count = min + (max > min ? killed.world.rand.nextInt(max - min + 1) : 0);
        if (count <= 0) return;

        int meta = ELEMENT_METAS[killed.world.rand.nextInt(ELEMENT_METAS.length)];
        event.getDrops().add(new EntityItem(killed.world,
                killed.posX, killed.posY + 0.3D, killed.posZ,
                new ItemStack(spectralDust, count, meta)));
    }
}
```

- [ ] **Step 3: Register in init**

In `InsaneTweaksMod.init`, next to the SRP-gated `ZhonyasEventHandler` registration (line ~360), add:

```java
        // Infernal elite kills drop spectral dust — independent of the SRP/EBW bridge.
        if (Loader.isModLoaded("infernalmobs")
                && com.spege.insanetweaks.config.ModConfig.interactions.enableInfernalDustDrops) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.InfernalDustDropHandler());
        }
```

- [ ] **Step 4: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java
git commit -m "feat: infernal mobs drop 1-2 random-element spectral dust on player kill"
```

### Task 5: Manual verification (runClient — requires infernalmobs + enigmaticlegacy dev jars in libs/)

- [ ] Wear ONLY `enigmaticlegacy:cursed_ring`, kill a listed parasite (e.g. `/summon srparasites:vigilante`, set `fragmentDropChance=1.0` for the test): fragment drops + the one-time hint appears exactly once (kill another — no second hint).
- [ ] Sapling growth-ticks with the owner wearing only the cursed ring.
- [ ] Spawn any mob, force it infernal (InfernalMobs command or spawn until elite), kill as player → 1-2 spectral dust of a random element in drops; kill a NON-infernal mob → none; let an infernal die to lava with no player credit → none.
- [ ] Infernal Crown still works (10 summons → forced infernal) — the extracted shim behaves identically.
