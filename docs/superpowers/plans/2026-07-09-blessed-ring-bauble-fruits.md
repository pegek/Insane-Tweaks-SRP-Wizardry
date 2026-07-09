# Blessed Ring — Bauble Fruit Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Survival acquisition path for Bauble Fruits: fragment drops (Blessed Ring gate) → Corrupted Seed → defended living sapling on infested ground → Corrupted Fruit → Imbuement Altar purification into a typed fruit, OR eat it corrupted for a random slot + certain death + Beckon Stage V.

**Architecture:** Three new items, one new living entity (`EntityCorruptedSapling`, so SRP AI can attack it), one compat util (`EnigmaticLegacyCompat`), two SRP-gated event handlers (fragment drops, corrupted-eat doom), and an extension of the existing Imbuement Altar mixin. All tunables in `TweaksCategory`, gated under the existing `enableBaubleFruits` module.

**Tech Stack:** Forge 1.12.2, Java 8, SRParasites 1.10.7 (`EntityParasiteBase`, `EntityVenkrolSV`), Enigmatic Legacy legacy-2.6.0 (`enigmaticlegacy:blessed_ring`), Baubles API, EB Wizardry Imbuement Altar mixin. No test suite — `./gradlew build` + manual `runClient`.

**Spec:** `docs/superpowers/specs/2026-07-09-blessed-ring-bauble-fruits-design.md`

**Key research facts (verified 2026-07-09 in decompiled sources):**
- Beckon = SRP "Venkrol". Stage V class: `com.dhanantry.scapeandrunparasites.entity.monster.deterrent.nexus.EntityVenkrolSV extends EntityPBeckon`; spawnable via `new EntityVenkrolSV(world)` + `world.spawnEntity(...)` (SRP itself spawns it that way in `ItemMobSpawner`).
- Blessed Ring registry name: `enigmaticlegacy:blessed_ring` (model JSON present in the jar).
- `DamageSource.OUT_OF_WORLD` has `canHarmInCreative() == true` → bypasses BOTH the vanilla Totem of Undying (`checkTotemDeathProtection` returns false for such sources) AND our own armor hardcap (`ArmorEventHandler.onLivingDeath` line 176 early-returns on `canHarmInCreative()`). This is the "unconditional death" mechanism.
- Free entity tracking ID: **116** (used so far: 100–115 in `InsaneTweaksMod.init`).
- High-tier SRP mobs (registry names from `SRPEntities`): all `ada_*` (adapted), all `anc_*` (ancient), and the pure-class mobs `overseer, vigilante, warden, marauder, monarch, grunt, bomber_light, bomber_heavy, wraith, bogle, haunter, seeker, architect, succor, carrier_colony`.

---

## File Structure

- Create: `src/main/java/com/spege/insanetweaks/util/EnigmaticLegacyCompat.java`
- Create: `src/main/java/com/spege/insanetweaks/items/fruit/CorruptedSeedFragmentItem.java`
- Create: `src/main/java/com/spege/insanetweaks/items/fruit/CorruptedSeedItem.java`
- Create: `src/main/java/com/spege/insanetweaks/items/fruit/CorruptedFruitItem.java`
- Create: `src/main/java/com/spege/insanetweaks/entities/EntityCorruptedSapling.java`
- Create: `src/main/java/com/spege/insanetweaks/client/renderer/entity/ModelCorruptedSapling.java`
- Create: `src/main/java/com/spege/insanetweaks/client/renderer/entity/RenderCorruptedSapling.java`
- Create: `src/main/java/com/spege/insanetweaks/events/CorruptedFragmentDropHandler.java`
- Create: `src/main/java/com/spege/insanetweaks/events/CorruptedFruitDoomHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/init/ModItems.java` — fields, registration, models, fruit-array accessor
- Modify: `src/main/java/com/spege/insanetweaks/mixins/MixinTileEntityImbuementAltar.java` — 9 purification recipes
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java` — 7 options
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` — entity registration (ID 116), renderer, handlers
- Create: `src/main/resources/assets/insanetweaks/recipes/corrupted_seed.json`
- Create: model JSONs + placeholder textures for the 3 items and the sapling
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`, `ru_ru.lang`

---

### Task 1: EnigmaticLegacyCompat + config options

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/util/EnigmaticLegacyCompat.java`
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java`

- [ ] **Step 1: Compat util** (same shim pattern as the other `util/` compat classes)

```java
package com.spege.insanetweaks.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Optional compat for Enigmatic Legacy (legacy 1.12.2 port).
 * Currently only detects the Blessed Ring — the acquisition gate for the
 * Bauble Fruit loop (fragment drops + sapling growth).
 */
public final class EnigmaticLegacyCompat {

    private static Item blessedRing;
    private static boolean lookedUp = false;

    private EnigmaticLegacyCompat() {
    }

    public static boolean isLoaded() {
        return Loader.isModLoaded("enigmaticlegacy");
    }

    /**
     * True when the player currently WEARS the Blessed Ring in any baubles slot.
     * False when Enigmatic Legacy or Baubles is absent.
     */
    @SuppressWarnings("null")
    public static boolean isWearingBlessedRing(EntityPlayer player) {
        if (player == null || !isLoaded() || !Loader.isModLoaded("baubles")) {
            return false;
        }
        if (!lookedUp) {
            blessedRing = ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("enigmaticlegacy", "blessed_ring"));
            lookedUp = true;
        }
        if (blessedRing == null) {
            return false;
        }
        baubles.api.cap.IBaublesItemHandler handler = baubles.api.BaublesApi.getBaublesHandler(player);
        if (handler == null) {
            return false;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() == blessedRing) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 2: Config options** — append to `TweaksCategory`:

```java
    @Config.Comment({"Corrupted Seed Fragment drop chance from high-tier parasites",
            "(only rolls when the killer wears the Blessed Ring)."})
    @Config.Name("Fragment Drop Chance")
    @Config.RangeDouble(min = 0.0, max = 1.0)
    public double fragmentDropChance = 0.05;

    @Config.Comment({"Registry-name prefixes of parasites that can drop Corrupted Seed Fragments.",
            "Exact names work too (a full name is its own prefix)."})
    @Config.Name("Fragment Drop Entities")
    public String[] fragmentDropEntities = {
            "srparasites:ada_", "srparasites:anc_",
            "srparasites:overseer", "srparasites:vigilante", "srparasites:warden",
            "srparasites:marauder", "srparasites:monarch", "srparasites:grunt",
            "srparasites:bomber_light", "srparasites:bomber_heavy", "srparasites:wraith",
            "srparasites:bogle", "srparasites:haunter", "srparasites:seeker",
            "srparasites:architect", "srparasites:succor", "srparasites:carrier_colony" };

    @Config.Comment({"Total valid-condition growth time of the Corrupted Sapling, in ticks",
            "(default 24000 = 20 min). Growth pauses while conditions are unmet."})
    @Config.Name("Sapling Growth Ticks")
    @Config.RangeInt(min = 20)
    public int saplingGrowthTicks = 24000;

    @Config.Comment({"Radius in which the sapling looks for infestation (living parasites)",
            "and for its Ring-wearing owner."})
    @Config.Name("Sapling Condition Radius")
    @Config.RangeInt(min = 4, max = 64)
    public int saplingConditionRadius = 32;

    @Config.Comment({"Minimum living parasites within the radius for the 'active infestation'",
            "condition (alternative: any srparasites block within 8 blocks)."})
    @Config.Name("Sapling Min Parasites")
    @Config.RangeInt(min = 0)
    public int saplingMinParasites = 2;

    @Config.Comment({"Corrupted Sapling max health."})
    @Config.Name("Sapling Max HP")
    @Config.RangeInt(min = 1)
    public int saplingMaxHp = 40;

    @Config.Comment({"Delay in ticks between eating a Corrupted Fruit and the unavoidable death",
            "(default 120 = 6 s)."})
    @Config.Name("Corrupted Fruit Doom Ticks")
    @Config.RangeInt(min = 1)
    public int corruptedFruitDoomTicks = 120;
```

- [ ] **Step 3: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/util/EnigmaticLegacyCompat.java src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java
git commit -m "feat: EnigmaticLegacyCompat (Blessed Ring detection) + fruit-loop config"
```

---

### Task 2: The three items + registration + recipe + assets

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/items/fruit/CorruptedSeedFragmentItem.java`
- Create: `src/main/java/com/spege/insanetweaks/items/fruit/CorruptedSeedItem.java`
- Create: `src/main/java/com/spege/insanetweaks/items/fruit/CorruptedFruitItem.java`
- Modify: `src/main/java/com/spege/insanetweaks/init/ModItems.java`
- Create: `src/main/resources/assets/insanetweaks/recipes/corrupted_seed.json`
- Create: model JSONs + placeholder textures
- Modify: lang files

- [ ] **Step 1: CorruptedSeedFragmentItem**

```java
package com.spege.insanetweaks.items.fruit;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Corrupted Seed Fragment — rare drop from high-tier parasites, ONLY while the
 * killer wears the Blessed Ring (see CorruptedFragmentDropHandler).
 * 4 fragments craft into a Corrupted Seed (recipes/corrupted_seed.json).
 */
public class CorruptedSeedFragmentItem extends Item {

    public CorruptedSeedFragmentItem() {
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "corrupted_seed_fragment"));
        this.setUnlocalizedName("corrupted_seed_fragment");
        this.setCreativeTab(CreativeTabs.MATERIALS);
        this.setMaxStackSize(16);
    }

    @Override
    @Nonnull
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.RARE;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + "A splinter of something that was never meant to sprout.");
        tooltip.add(TextFormatting.GRAY + "It only reveals itself to hands shielded by blessing.");
        tooltip.add("");
        tooltip.add(TextFormatting.GRAY + "Combine " + TextFormatting.LIGHT_PURPLE + "4 fragments"
                + TextFormatting.GRAY + " into a Corrupted Seed.");
    }
}
```

- [ ] **Step 2: CorruptedSeedItem** (plants the sapling)

```java
package com.spege.insanetweaks.items.fruit;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityCorruptedSapling;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Corrupted Seed — planted (right-click on top of a block) it becomes a LIVING
 * EntityCorruptedSapling that parasites will attack. Tooltip doubles as the
 * growing manual, in the Living/Sentient gear flavor style.
 */
public class CorruptedSeedItem extends Item {

    public CorruptedSeedItem() {
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "corrupted_seed"));
        this.setUnlocalizedName("corrupted_seed");
        this.setCreativeTab(CreativeTabs.MATERIALS);
        this.setMaxStackSize(1);
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, @Nonnull World world,
            @Nonnull BlockPos pos, @Nonnull EnumHand hand, @Nonnull EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        if (facing != EnumFacing.UP) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        BlockPos above = pos.up();
        if (!world.isAirBlock(above)) {
            return EnumActionResult.FAIL;
        }

        EntityCorruptedSapling sapling = new EntityCorruptedSapling(world);
        sapling.setPosition(above.getX() + 0.5D, above.getY(), above.getZ() + 0.5D);
        sapling.setOwnerId(player.getUniqueID());
        world.spawnEntity(sapling);
        world.playSound(null, above, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1.0f, 0.7f);

        player.getHeldItem(hand).shrink(1);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @Nonnull
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + "It pulses. It listens. It waits for tainted soil.");
        tooltip.add("");
        tooltip.add(TextFormatting.GOLD + "How to grow:");
        tooltip.add(TextFormatting.GRAY + "1. Plant it (right-click the ground) inside an active");
        tooltip.add(TextFormatting.GRAY + "   parasite infestation.");
        tooltip.add(TextFormatting.GRAY + "2. Stay close, wearing the " + TextFormatting.LIGHT_PURPLE
                + "Blessed Ring" + TextFormatting.GRAY + " — it grows only");
        tooltip.add(TextFormatting.GRAY + "   under your protection.");
        tooltip.add(TextFormatting.GRAY + "3. The hive will try to reclaim it. " + TextFormatting.RED
                + "Defend the sapling.");
        tooltip.add(TextFormatting.GRAY + "4. In time it bears a " + TextFormatting.DARK_PURPLE
                + "Corrupted Fruit" + TextFormatting.GRAY + ".");
        tooltip.add("");
        tooltip.add(TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC
                + "If it dies, the seed is lost with it.");
    }
}
```

- [ ] **Step 3: CorruptedFruitItem** (random slot + doom; purification happens at the altar, Task 5)

```java
package com.spege.insanetweaks.items.fruit;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.events.CorruptedFruitDoomHandler;
import com.spege.insanetweaks.init.ModItems;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Corrupted Fruit — the sapling's harvest. Two paths:
 *
 *  A) Purify at the Imbuement Altar (MixinTileEntityImbuementAltar) into a CHOSEN
 *     typed Bauble Fruit — the intended path.
 *  B) Eat it corrupted: immediately unlocks ONE RANDOM bauble slot (invokes the
 *     same BaseBaubleFruitItem.onFoodEaten logic — same one-per-type limits,
 *     no bypassing), then an unavoidable death sequence handled by
 *     CorruptedFruitDoomHandler: rooted, near-blind, and after the configured
 *     delay the player dies unconditionally and a Beckon Stage V rises.
 */
@SuppressWarnings("null")
public class CorruptedFruitItem extends ItemFood {

    public CorruptedFruitItem() {
        super(4, 0.6f, false);
        this.setAlwaysEdible();
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "corrupted_fruit"));
        this.setUnlocalizedName("corrupted_fruit");
        this.setCreativeTab(CreativeTabs.FOOD);
        this.setMaxStackSize(1);
    }

    @Override
    public void onFoodEaten(@Nonnull ItemStack stack, @Nonnull World worldIn,
            @Nonnull EntityPlayer player) {
        if (worldIn.isRemote || !(player instanceof EntityPlayerMP)) {
            return;
        }

        // 1. Random slot unlock — exactly the same function as eating that fruit.
        Item[] fruits = ModItems.getAllBaubleFruits();
        BaseBaubleFruitItem chosen = (BaseBaubleFruitItem) fruits[worldIn.rand.nextInt(fruits.length)];
        chosen.onFoodEaten(new ItemStack(chosen), worldIn, player);

        // 2. The price. Doom window — CorruptedFruitDoomHandler takes it from here.
        long doomAt = worldIn.getTotalWorldTime()
                + com.spege.insanetweaks.config.ModConfig.tweaks.corruptedFruitDoomTicks;
        player.getEntityData().setLong(CorruptedFruitDoomHandler.TAG_DOOM_AT, doomAt);
        player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 400, 0, false, false));
        player.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 400, 0, false, false));

        player.sendMessage(new TextComponentString(TextFormatting.DARK_RED + ""
                + TextFormatting.ITALIC + "The hive accepts your bargain."));
        worldIn.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.6f, 1.6f);
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @Nonnull
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + "Grown in tainted soil, under a blessed watch.");
        tooltip.add("");
        tooltip.add(TextFormatting.AQUA + "Purify it at an Imbuement Altar to choose its gift.");
        tooltip.add("");
        tooltip.add(TextFormatting.RED + "Or eat it as it is — the hive grants a random gift");
        tooltip.add(TextFormatting.RED + "at once... and collects its price in full.");
        tooltip.add(TextFormatting.DARK_RED + "" + TextFormatting.ITALIC + "No blessing survives that bargain.");
    }
}
```

- [ ] **Step 4: ModItems changes**

After `BAUBLE_FRUIT_TRINKET` (line 113) add:

```java
    // Corrupted fruit loop (Blessed Ring gate)
    public static final Item CORRUPTED_SEED_FRAGMENT = new com.spege.insanetweaks.items.fruit.CorruptedSeedFragmentItem();
    public static final Item CORRUPTED_SEED          = new com.spege.insanetweaks.items.fruit.CorruptedSeedItem();
    public static final Item CORRUPTED_FRUIT         = new com.spege.insanetweaks.items.fruit.CorruptedFruitItem();
```

Below the private `ALL_BAUBLE_FRUITS` array add the accessor used by CorruptedFruitItem:

```java
    /** Typed fruits for the corrupted-fruit random unlock. Defensive copy. */
    public static Item[] getAllBaubleFruits() {
        return ALL_BAUBLE_FRUITS.clone();
    }
```

In the registration block gated on `ModConfig.modules.enableBaubleFruits` (around line 153,
where the typed fruits are registered), register the three new items the same way, and in
the model-registration section register their models with the existing `registerModel(...)`
helper under the same module flag.

- [ ] **Step 5: Crafting recipe** — `src/main/resources/assets/insanetweaks/recipes/corrupted_seed.json`:

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    { "item": "insanetweaks:corrupted_seed_fragment" },
    { "item": "insanetweaks:corrupted_seed_fragment" },
    { "item": "insanetweaks:corrupted_seed_fragment" },
    { "item": "insanetweaks:corrupted_seed_fragment" }
  ],
  "result": { "item": "insanetweaks:corrupted_seed" }
}
```

- [ ] **Step 6: Item models + placeholder textures**

For each of `corrupted_seed_fragment`, `corrupted_seed`, `corrupted_fruit` create
`src/main/resources/assets/insanetweaks/models/item/<name>.json`:

```json
{
  "parent": "item/generated",
  "textures": { "layer0": "insanetweaks:items/<name>" }
}
```

Placeholder textures (final art later from the user) — copy the existing bauble fruit texture
(check the texture referenced in `models/item/bauble_fruit.json`, e.g. `items/bauble_fruit.png`):

```bash
for n in corrupted_seed_fragment corrupted_seed corrupted_fruit; do cp src/main/resources/assets/insanetweaks/textures/items/bauble_fruit.png "src/main/resources/assets/insanetweaks/textures/items/$n.png"; done
```

- [ ] **Step 7: Lang entries**

`en_us.lang`:

```properties
item.corrupted_seed_fragment.name=Corrupted Seed Fragment
item.corrupted_seed.name=Corrupted Seed
item.corrupted_fruit.name=Corrupted Fruit
```

`ru_ru.lang`:

```properties
item.corrupted_seed_fragment.name=Фрагмент осквернённого семени
item.corrupted_seed.name=Осквернённое семя
item.corrupted_fruit.name=Осквернённый плод
```

(Match the unlocalized-name key style of the existing fruit entries in the file — if the
existing entries use the `item.insanetweaks.<name>.name` form, use that form instead.)

- [ ] **Step 8: Build** — will FAIL on the missing `EntityCorruptedSapling` /
`CorruptedFruitDoomHandler` imports until Tasks 3–4 exist. When executing task-by-task,
fold Tasks 2–4 into one compile unit or comment nothing out — just build at the end of Task 4.

- [ ] **Step 9: Commit** (after Task 4 builds green)

---

### Task 3: EntityCorruptedSapling + model/renderer + registration

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/entities/EntityCorruptedSapling.java`
- Create: `src/main/java/com/spege/insanetweaks/client/renderer/entity/ModelCorruptedSapling.java`
- Create: `src/main/java/com/spege/insanetweaks/client/renderer/entity/RenderCorruptedSapling.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`
- Create: `src/main/resources/assets/insanetweaks/textures/entity/corrupted_sapling.png` (placeholder)

- [ ] **Step 1: The entity**

```java
package com.spege.insanetweaks.entities;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.EnigmaticLegacyCompat;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Corrupted Sapling — a LIVING plant (deliberately an entity, not a crop block,
 * so SRP parasite AI can naturally target and attack it; the player must defend it).
 *
 * Growth advances only while BOTH conditions hold (checked every 20 t):
 *   1. Active infestation nearby: >= saplingMinParasites living EntityParasiteBase
 *      within saplingConditionRadius, OR any block from the "srparasites" domain
 *      within 8 blocks horizontally / 4 vertically (block scan cached, every 100 t).
 *   2. The planting owner is within saplingConditionRadius AND wears the Blessed Ring.
 *
 * At full growth it drops a Corrupted Fruit and despawns. If killed, everything is lost.
 */
@SuppressWarnings("null")
public class EntityCorruptedSapling extends EntityLiving {

    private static final DataParameter<Integer> STAGE =
            EntityDataManager.createKey(EntityCorruptedSapling.class, DataSerializers.VARINT);
    public static final int MAX_STAGE = 4;

    private int growthTicks;
    private UUID ownerId;
    private boolean cachedBlockInfestation;
    private int blockScanCooldown;

    public EntityCorruptedSapling(World world) {
        super(world);
        this.setSize(0.6f, 1.4f);
        this.setNoAI(true);
    }

    public void setOwnerId(UUID id) {
        this.ownerId = id;
    }

    /** 0..MAX_STAGE — synced for the renderer's scale. */
    public int getStage() {
        return this.dataManager.get(STAGE);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(STAGE, 0);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH)
                .setBaseValue(ModConfig.tweaks.saplingMaxHp);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.0D);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        this.motionX = 0.0D;
        this.motionZ = 0.0D;

        if (this.world.isRemote || this.ticksExisted % 20 != 0) {
            return;
        }

        if (conditionsMet()) {
            this.growthTicks += 20;
            int total = Math.max(20, ModConfig.tweaks.saplingGrowthTicks);
            int stage = Math.min(MAX_STAGE, (this.growthTicks * MAX_STAGE) / total);
            this.dataManager.set(STAGE, stage);

            if (this.world instanceof WorldServer) {
                ((WorldServer) this.world).spawnParticle(EnumParticleTypes.SPELL_WITCH,
                        this.posX, this.posY + this.height * 0.6D, this.posZ, 3,
                        0.2D, 0.3D, 0.2D, 0.01D);
            }

            if (this.growthTicks >= total) {
                bearFruit();
            }
        }
    }

    private boolean conditionsMet() {
        // Owner nearby, wearing the Blessed Ring.
        if (this.ownerId == null) {
            return false;
        }
        EntityPlayer owner = this.world.getPlayerEntityByUUID(this.ownerId);
        double radius = ModConfig.tweaks.saplingConditionRadius;
        if (owner == null || owner.getDistanceSq(this) > radius * radius) {
            return false;
        }
        if (!EnigmaticLegacyCompat.isWearingBlessedRing(owner)) {
            return false;
        }
        return infestationNearby(radius);
    }

    private boolean infestationNearby(double radius) {
        AxisAlignedBB box = this.getEntityBoundingBox().grow(radius);
        List<EntityParasiteBase> parasites =
                this.world.getEntitiesWithinAABB(EntityParasiteBase.class, box);
        int alive = 0;
        for (EntityParasiteBase p : parasites) {
            if (p.isEntityAlive()) {
                alive++;
            }
        }
        if (alive >= ModConfig.tweaks.saplingMinParasites) {
            return true;
        }

        // Fallback: infested SRP blocks nearby (scan every 100 t, cached in between).
        if (--this.blockScanCooldown <= 0) {
            this.blockScanCooldown = 5; // 5 * 20t checks = 100 t
            this.cachedBlockInfestation = false;
            BlockPos base = new BlockPos(this);
            for (BlockPos pos : BlockPos.getAllInBoxMutable(base.add(-8, -4, -8), base.add(8, 4, 8))) {
                ResourceLocation name = this.world.getBlockState(pos).getBlock().getRegistryName();
                if (name != null && "srparasites".equals(name.getResourceDomain())) {
                    this.cachedBlockInfestation = true;
                    break;
                }
            }
        }
        return this.cachedBlockInfestation;
    }

    private void bearFruit() {
        this.entityDropItem(new ItemStack(ModItems.CORRUPTED_FRUIT), 0.3f);
        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.SPELL_MOB,
                    this.posX, this.posY + this.height * 0.6D, this.posZ, 30,
                    0.4D, 0.6D, 0.4D, 0.05D);
        }
        this.world.playSound(null, this.posX, this.posY, this.posZ,
                net.minecraft.init.SoundEvents.BLOCK_CHORUS_FLOWER_GROW,
                SoundCategory.NEUTRAL, 1.0f, 0.7f);
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Corrupted Sapling matured at {},{},{}",
                (int) this.posX, (int) this.posY, (int) this.posZ);
        this.setDead();
    }

    @Override
    public void writeEntityToNBT(@Nonnull NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("SaplingGrowth", this.growthTicks);
        if (this.ownerId != null) {
            compound.setUniqueId("SaplingOwner", this.ownerId);
        }
    }

    @Override
    public void readEntityFromNBT(@Nonnull NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.growthTicks = compound.getInteger("SaplingGrowth");
        if (compound.hasUniqueId("SaplingOwner")) {
            this.ownerId = compound.getUniqueId("SaplingOwner");
        }
        int total = Math.max(20, ModConfig.tweaks.saplingGrowthTicks);
        this.dataManager.set(STAGE, Math.min(MAX_STAGE, (this.growthTicks * MAX_STAGE) / total));
    }

    @Nullable
    @Override
    protected net.minecraft.util.SoundEvent getHurtSound(@Nonnull net.minecraft.util.DamageSource source) {
        return net.minecraft.init.SoundEvents.BLOCK_WOOD_HIT;
    }

    @Nullable
    @Override
    protected net.minecraft.util.SoundEvent getDeathSound() {
        return net.minecraft.init.SoundEvents.BLOCK_GRASS_BREAK;
    }
}
```

- [ ] **Step 2: Model** (simple two-box plant; stage handled by render scale)

```java
package com.spege.insanetweaks.client.renderer.entity;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

/** Minimal stem+bulb model for the Corrupted Sapling. Stage growth = render scale. */
public class ModelCorruptedSapling extends ModelBase {

    private final ModelRenderer stem;
    private final ModelRenderer bulb;

    public ModelCorruptedSapling() {
        this.textureWidth = 64;
        this.textureHeight = 64;

        this.stem = new ModelRenderer(this, 0, 0);
        this.stem.addBox(-1.5f, 0.0f, -1.5f, 3, 14, 3);
        this.stem.setRotationPoint(0.0f, 10.0f, 0.0f);

        this.bulb = new ModelRenderer(this, 16, 0);
        this.bulb.addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8);
        this.bulb.setRotationPoint(0.0f, 10.0f, 0.0f);
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scale) {
        this.stem.render(scale);
        this.bulb.render(scale);
    }
}
```

- [ ] **Step 3: Renderer**

```java
package com.spege.insanetweaks.client.renderer.entity;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.entities.EntityCorruptedSapling;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderCorruptedSapling extends RenderLiving<EntityCorruptedSapling> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("insanetweaks", "textures/entity/corrupted_sapling.png");

    public RenderCorruptedSapling(RenderManager manager) {
        super(manager, new ModelCorruptedSapling(), 0.3f);
    }

    @Override
    protected void preRenderCallback(@Nonnull EntityCorruptedSapling entity, float partialTickTime) {
        // Stage 0 = small sprout, MAX_STAGE = full size.
        float s = 0.35f + 0.65f * (entity.getStage() / (float) EntityCorruptedSapling.MAX_STAGE);
        GlStateManager.scale(s, s, s);
    }

    @Override
    @Nonnull
    protected ResourceLocation getEntityTexture(@Nonnull EntityCorruptedSapling entity) {
        return TEXTURE;
    }
}
```

Placeholder texture — a 64x64 PNG; copy any existing entity texture as a stand-in:

```bash
cp src/main/resources/assets/insanetweaks/textures/items/bauble_fruit.png src/main/resources/assets/insanetweaks/textures/entity/corrupted_sapling.png
```

- [ ] **Step 4: Register entity + renderer in InsaneTweaksMod**

In `init(...)`, after the `thrall_minion` registration (line 276–277), add (**ID 116 — next
free; never reuse/reorder**):

```java
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "corrupted_sapling"),
                com.spege.insanetweaks.entities.EntityCorruptedSapling.class, "corrupted_sapling", 116, this, 64, 3, false);
```

In `preInit(...)`, inside the `Side.CLIENT` renderer block, add the registration following
the established anonymous `IRenderFactory` style of the neighbouring entries:

```java
            net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
                    com.spege.insanetweaks.entities.EntityCorruptedSapling.class,
                    new net.minecraftforge.fml.client.registry.IRenderFactory<com.spege.insanetweaks.entities.EntityCorruptedSapling>() {
                        @Override
                        public net.minecraft.client.renderer.entity.Render<com.spege.insanetweaks.entities.EntityCorruptedSapling> createRenderFor(
                                net.minecraft.client.renderer.entity.RenderManager manager) {
                            return new com.spege.insanetweaks.client.renderer.entity.RenderCorruptedSapling(manager);
                        }
                    });
```

Add the nameplate lang keys (both forms, per the two-key rule from spell_theory.md §14) —
`en_us.lang`:

```properties
entity.insanetweaks.corrupted_sapling.name=Corrupted Sapling
entity.corrupted_sapling.name=Corrupted Sapling
```

`ru_ru.lang`:

```properties
entity.insanetweaks.corrupted_sapling.name=Осквернённый росток
entity.corrupted_sapling.name=Осквернённый росток
```

- [ ] **Step 5: Build** — still red until Task 4 (`CorruptedFruitDoomHandler`). Continue.

---

### Task 4: Drop handler + doom handler + registration

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/events/CorruptedFragmentDropHandler.java`
- Create: `src/main/java/com/spege/insanetweaks/events/CorruptedFruitDoomHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`

- [ ] **Step 1: Fragment drop handler**

```java
package com.spege.insanetweaks.events;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.EnigmaticLegacyCompat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Corrupted Seed Fragment drops from high-tier parasites (configurable prefix list),
 * ONLY when the killing player wears the Blessed Ring. Registered when both
 * enableBaubleFruits and SRP are present.
 */
public class CorruptedFragmentDropHandler {

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingDrops(LivingDropsEvent event) {
        Entity killed = event.getEntity();
        if (killed.world.isRemote) return;
        if (!(killed instanceof EntityParasiteBase)) return;
        if (!(event.getSource().getTrueSource() instanceof EntityPlayer)) return;

        EntityPlayer killer = (EntityPlayer) event.getSource().getTrueSource();
        if (!EnigmaticLegacyCompat.isWearingBlessedRing(killer)) return;

        ResourceLocation key = EntityList.getKey(killed);
        if (key == null || !isHighTier(key.toString())) return;

        if (killed.world.rand.nextDouble() >= ModConfig.tweaks.fragmentDropChance) return;

        event.getDrops().add(new EntityItem(killed.world,
                killed.posX, killed.posY + 0.3D, killed.posZ,
                new ItemStack(ModItems.CORRUPTED_SEED_FRAGMENT)));
    }

    private static boolean isHighTier(String registryName) {
        for (String prefix : ModConfig.tweaks.fragmentDropEntities) {
            if (prefix != null && !prefix.isEmpty() && registryName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 2: Doom handler**

```java
package com.spege.insanetweaks.events;

import com.dhanantry.scapeandrunparasites.entity.monster.deterrent.nexus.EntityVenkrolSV;
import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The price of eating a Corrupted Fruit (see CorruptedFruitItem).
 *
 * While the doom window (NBT world-time deadline) is open:
 *   - the player is rooted (motion zeroed every tick),
 *   - blindness + nausea are RE-APPLIED every tick, so no cleanse/milk can help.
 * When the deadline passes:
 *   - a Beckon Stage V (EntityVenkrolSV) rises at the player's position,
 *   - the player dies via DamageSource.OUT_OF_WORLD + Float.MAX_VALUE, which
 *     bypasses the Totem of Undying AND our armor hardcap (both early-return
 *     on canHarmInCreative() sources).
 *
 * Registered only when SRP is present (direct EntityVenkrolSV import).
 */
public class CorruptedFruitDoomHandler {

    /** NBT (entityData): world-time at which the player dies. */
    public static final String TAG_DOOM_AT = "CorruptedFruitDoomAt";

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;
        if (player.world.isRemote) return;

        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(TAG_DOOM_AT)) return;

        long doomAt = data.getLong(TAG_DOOM_AT);
        long now = player.world.getTotalWorldTime();

        if (now < doomAt) {
            // Root + unremovable dread.
            player.motionX = 0.0D;
            player.motionZ = 0.0D;
            player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 60, 0, false, false));
            player.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 60, 0, false, false));
            return;
        }

        data.removeTag(TAG_DOOM_AT);

        EntityVenkrolSV beckon = new EntityVenkrolSV(player.world);
        beckon.setPosition(player.posX, player.posY, player.posZ);
        player.world.spawnEntity(beckon);

        InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks] Corrupted Fruit claimed {} — Beckon Stage V rises at {},{},{}",
                player.getName(), (int) player.posX, (int) player.posY, (int) player.posZ);

        player.attackEntityFrom(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
    }
}
```

- [ ] **Step 3: Register both handlers**

In `InsaneTweaksMod.init(...)`, where `BaubleFruitEventHandler` is registered (line ~418,
inside the `enableBaubleFruits` + Baubles condition), add directly after it:

```java
            if (Loader.isModLoaded("scapeandrunparasites")) {
                // Corrupted fruit loop (fragment drops + corrupted-eat doom).
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CorruptedFragmentDropHandler());
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.CorruptedFruitDoomHandler());
            }
```

- [ ] **Step 4: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL` (Tasks 2, 3 and 4 compile together now).

- [ ] **Step 5: Commit**

```bash
git add -A src/main/java/com/spege/insanetweaks src/main/resources
git commit -m "feat: corrupted fruit loop — fragments, seed, living sapling, doom path"
```

---

### Task 5: Imbuement Altar purification recipes (9 slot types)

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/mixins/MixinTileEntityImbuementAltar.java`

- [ ] **Step 1: Add the fruit branch**

Inside `insanetweaks$onGetImbuementResult`, after the closing brace of the master-wand
recipe bundle (line ~72), add:

```java
        // RECIPE BUNDLE 2: Corrupted Fruit + receptacle combo -> typed Bauble Fruit.
        // The four receptacle elements pick which bauble slot the purified fruit grants.
        if (inputRegName.getResourceDomain().equals("insanetweaks")
                && inputRegName.getResourcePath().equals("corrupted_fruit")) {

            int earth = 0, healing = 0, fire = 0, lightning = 0, sorcery = 0, necromancy = 0, ice = 0;
            for (Element el : receptacleElements) {
                if (el == Element.EARTH) earth++;
                else if (el == Element.HEALING) healing++;
                else if (el == Element.FIRE) fire++;
                else if (el == Element.LIGHTNING) lightning++;
                else if (el == Element.SORCERY) sorcery++;
                else if (el == Element.NECROMANCY) necromancy++;
                else if (el == Element.ICE) ice++;
            }

            net.minecraft.item.Item result = null;
            if      (sorcery == 2 && healing == 2)   result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_RING;
            else if (healing == 4)                   result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_AMULET;
            else if (earth == 4)                     result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_BODY;
            else if (sorcery == 4)                   result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_HEAD;
            else if (fire == 2 && sorcery == 2)      result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_CHARM;
            else if (earth == 2 && healing == 2)     result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_BELT;
            else if (lightning == 4)                 result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_ELYTRA;
            else if (necromancy == 4)                result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_TOTEM;
            else if (lightning == 2 && ice == 2)     result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_TRINKET;

            if (result != null) {
                cir.setReturnValue(new ItemStack(result, 1));
            }
        }
```

Recipe table (goes into the lang-side documentation / Patchouli later, and the PR description):

| Receptacles (4) | Result |
|---|---|
| 2× Sorcery + 2× Healing | Ring Fruit |
| 4× Healing | Amulet Fruit |
| 4× Earth | Body Fruit |
| 4× Sorcery | Head Fruit |
| 2× Fire + 2× Sorcery | Charm Fruit |
| 2× Earth + 2× Healing | Belt Fruit |
| 4× Lightning | Elytra Fruit |
| 4× Necromancy | Totem Fruit |
| 2× Lightning + 2× Ice | Trinket Fruit |

- [ ] **Step 2: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/mixins/MixinTileEntityImbuementAltar.java
git commit -m "feat: imbuement altar purifies corrupted fruit into 9 typed bauble fruits"
```

---

### Task 6: Manual verification (runClient)

Lower `saplingGrowthTicks` (e.g. 400) and raise `fragmentDropChance` (e.g. 1.0) in the
run config for testing.

- [ ] **Step 1: Drop gate** — kill an `ada_*` parasite with and without the Blessed Ring
  equipped: fragment drops only with the ring. A `pri_*` kill never drops.
- [ ] **Step 2: Craft + tooltip** — 4 fragments → seed; seed tooltip shows the growing manual.
- [ ] **Step 3: Growth conditions** — plant near spawned parasites while wearing the ring:
  stage particles + visible scale-up; walk away / remove the ring / kill all parasites →
  growth pauses. Parasites should attack the sapling; killing it drops nothing.
- [ ] **Step 4: Harvest + purify** — mature sapling drops Corrupted Fruit; altar with
  4× Healing receptacles converts it into an Amulet Fruit; wrong combo yields nothing.
- [ ] **Step 5: The bargain** — eat a Corrupted Fruit holding a Totem of Undying, with the
  armor hardcap ready: random slot unlocked immediately (verify BaublesEX slot count),
  player rooted + blinded, dies after ~6 s DESPITE totem and hardcap, Beckon Stage V
  stands at the corpse, and the slot persists after respawn.
- [ ] **Step 6: Commit tuning**

```bash
git add -A
git commit -m "balance: corrupted fruit loop tuning after manual testing"
```
