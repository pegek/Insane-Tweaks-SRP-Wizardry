# Zhonya's Hourglass Rework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the current Zhonya behavior (SRP entity restoration) to a new artefact `restoration_hourglass`, and give `zhonyas_hourglass` a new active Gilded Stasis: 3 s immortality + full heal + cleanse + golden freeze, 5 s aggro loss, at the cost of ALL current mana + a 3 h cooldown.

**Architecture:** The restoration logic moves 1:1 into `ItemRestorationHourglassArtefact` (snapshot system `ZhonyasEventHandler`/`SrpOriginSnapshotHelper` stays untouched). The new Zhonya applies a new `PotionGildedStasis`; a server event handler enforces immortality/root/aggro-loss, a client handler renders the gold tint. All numbers live in `TweaksCategory`.

**Tech Stack:** Forge 1.12.2, Java 8, EB Wizardry `ItemArtefact`, player_mana via reflection (`PlayerManaCompat`), no test suite — `./gradlew build` + manual `runClient`.

**Spec:** `docs/superpowers/specs/2026-07-09-zhonya-rework-design.md`

---

## File Structure

- Modify: `src/main/java/com/spege/insanetweaks/util/PlayerManaCompat.java` — add `setCurrentMana`
- Create: `src/main/java/com/spege/insanetweaks/baubles/ItemRestorationHourglassArtefact.java` — receives old logic
- Rewrite: `src/main/java/com/spege/insanetweaks/baubles/ItemZhonyasHourglassArtefact.java` — new stasis behavior
- Create: `src/main/java/com/spege/insanetweaks/potions/PotionGildedStasis.java`
- Modify: `src/main/java/com/spege/insanetweaks/init/ModPotions.java` — register potion
- Create: `src/main/java/com/spege/insanetweaks/events/ZhonyaStasisHandler.java` — server logic
- Create: `src/main/java/com/spege/insanetweaks/events/ZhonyaClientHandler.java` — gold tint (client)
- Modify: `src/main/java/com/spege/insanetweaks/init/ModItems.java` — new item registration + model
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntityPurifyingWave.java:10,86` — call-site switch
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java` — 4 options
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` — handler registration
- Create: `src/main/resources/assets/insanetweaks/models/item/restoration_hourglass.json` + placeholder texture
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`, `ru_ru.lang`

---

### Task 1: PlayerManaCompat.setCurrentMana

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/util/PlayerManaCompat.java`

- [ ] **Step 1: Add reflection handles**

Next to the existing `private static Method` fields add:

```java
    private static Method setManaMethod;   // ISoul.setMP(EntityPlayer, double)
    private static Method syncSoulMethod;  // ISoul.sync(EntityPlayer)
```

In `ensureInitialized()`, after `addMaxManaForPlayerMethod = ...` add:

```java
            setManaMethod = soulClass.getMethod("setMP", EntityPlayer.class, Double.TYPE);
            syncSoulMethod = soulClass.getMethod("sync", EntityPlayer.class);
```

(`ISoul` interface confirmed in `notes/decompiled_mods/playermana_source/decompiled_src/zettasword/player_mana/cap/ISoul.java` — it declares both `setMP(EntityPlayer, double)` and `sync(EntityPlayer)`.)

- [ ] **Step 2: Add the public setter**

Add after `addMaxMana(...)`:

```java
    /**
     * Sets the player's CURRENT mana to the given value and syncs it to the client.
     * Returns false when player_mana is absent or reflection fails.
     */
    public static boolean setCurrentMana(EntityPlayer player, double value) {
        if (player == null || !isAvailable() || setManaMethod == null) {
            return false;
        }
        try {
            Object soul = getSoulMethod.invoke(null, player);
            if (soul == null) {
                return false;
            }
            setManaMethod.invoke(soul, player, value);
            if (syncSoulMethod != null) {
                syncSoulMethod.invoke(soul, player);
            }
            return true;
        } catch (Exception e) {
            logDebugFailure("set player_mana MP", e);
            return false;
        }
    }
```

- [ ] **Step 3: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/util/PlayerManaCompat.java
git commit -m "feat: PlayerManaCompat.setCurrentMana (reflection ISoul.setMP + sync)"
```

---

### Task 2: ItemRestorationHourglassArtefact (old logic moves out)

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/baubles/ItemRestorationHourglassArtefact.java`
- Modify: `src/main/java/com/spege/insanetweaks/init/ModItems.java`
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntityPurifyingWave.java`
- Create: `src/main/resources/assets/insanetweaks/models/item/restoration_hourglass.json`
- Create: `src/main/resources/assets/insanetweaks/textures/items/restoration_hourglass.png` (placeholder)

- [ ] **Step 1: Copy the current class file**

```bash
cp src/main/java/com/spege/insanetweaks/baubles/ItemZhonyasHourglassArtefact.java src/main/java/com/spege/insanetweaks/baubles/ItemRestorationHourglassArtefact.java
```

- [ ] **Step 2: Apply exactly these edits to the NEW file** (the copied restoration class)

1. Class declaration: `public class ItemZhonyasHourglassArtefact extends ItemArtefact` → `public class ItemRestorationHourglassArtefact extends ItemArtefact`
2. Constructor name `public ItemZhonyasHourglassArtefact()` → `public ItemRestorationHourglassArtefact()`
3. `this.setRegistryName("zhonyas_hourglass");` → `this.setRegistryName("restoration_hourglass");`
4. `this.setUnlocalizedName("insanetweaks.zhonyas_hourglass");` → `this.setUnlocalizedName("insanetweaks.restoration_hourglass");`
5. `ItemArtefact.isArtefactActive(player, ModItems.ZHONYAS_HOURGLASS)` → `ItemArtefact.isArtefactActive(player, ModItems.RESTORATION_HOURGLASS)`
6. Chat prefixes `"[Zhonyas] "` (3 occurrences) → `"[Restoration] "`
7. Log prefixes `"[IT][Zhonyas]"` (2 occurrences) → `"[IT][Restoration]"`
8. Header javadoc first line: `Artefakt: Zhonyas Hourglass` → `Artefakt: Hourglass of Restoration (przejęte 1:1 z dawnej Zhonyi)`
9. In `addInformation`, replace the first flavor line
   `"Time stolen from the parasite hive."` → `"A moment of the past, bottled and returned."`

Everything else (RESTORE_RADIUS, CREATIVE_REACH, `tryRestoreInRange`, `doRestore`,
right-click restore with the 432 000 t cooldown, tooltip body) stays byte-identical.

- [ ] **Step 3: Register the item in ModItems**

In `src/main/java/com/spege/insanetweaks/init/ModItems.java`:

After the field `public static final Item ZHONYAS_HOURGLASS = new ItemZhonyasHourglassArtefact();` (line 102) add:

```java
    public static final Item RESTORATION_HOURGLASS = new com.spege.insanetweaks.baubles.ItemRestorationHourglassArtefact();
```

In the `registerAll(...)` call that contains `ZHONYAS_HOURGLASS` (line 131), append `RESTORATION_HOURGLASS`:

```java
            event.getRegistry().registerAll(GOLDEN_BOOK, RUPTER_SOLIED, LIVING_AEGIS, SENTIENT_AEGIS, INFERNAL_CROWN, ZHONYAS_HOURGLASS, RESTORATION_HOURGLASS);
```

Next to `registerModel(ZHONYAS_HOURGLASS);` (line 206) add:

```java
            registerModel(RESTORATION_HOURGLASS);
```

- [ ] **Step 4: Switch the Purifying Wave call site**

In `src/main/java/com/spege/insanetweaks/entities/EntityPurifyingWave.java`:

Line 10: `import com.spege.insanetweaks.baubles.ItemZhonyasHourglassArtefact;`
→ `import com.spege.insanetweaks.baubles.ItemRestorationHourglassArtefact;`

Line 86: `ItemZhonyasHourglassArtefact.tryRestoreInRange(`
→ `ItemRestorationHourglassArtefact.tryRestoreInRange(`

- [ ] **Step 5: Model + placeholder texture**

Create `src/main/resources/assets/insanetweaks/models/item/restoration_hourglass.json` with the
same content as `models/item/zhonyas_hourglass.json` but the texture path changed to
`insanetweaks:items/restoration_hourglass`. Then:

```bash
cp src/main/resources/assets/insanetweaks/textures/items/zhonyas_hourglass.png src/main/resources/assets/insanetweaks/textures/items/restoration_hourglass.png
```

(If the zhonya texture file name differs, check the texture referenced by
`models/item/zhonyas_hourglass.json` and copy that file. Final texture arrives from the user later.)

- [ ] **Step 6: Lang entries**

`en_us.lang`, next to the existing zhonyas item name entry:

```properties
item.insanetweaks.restoration_hourglass.name=Hourglass of Restoration
```

`ru_ru.lang`:

```properties
item.insanetweaks.restoration_hourglass.name=Песочные часы восстановления
```

- [ ] **Step 7: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add -A src/main/java/com/spege/insanetweaks/baubles src/main/java/com/spege/insanetweaks/init/ModItems.java src/main/java/com/spege/insanetweaks/entities/EntityPurifyingWave.java src/main/resources
git commit -m "feat: Hourglass of Restoration — receives SRP restoration from Zhonya 1:1"
```

---

### Task 3: PotionGildedStasis + config options

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/potions/PotionGildedStasis.java`
- Modify: `src/main/java/com/spege/insanetweaks/init/ModPotions.java`
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java`
- Create: `src/main/resources/assets/insanetweaks/textures/gui/potion/gilded_stasis.png` (placeholder)

- [ ] **Step 1: Potion class**

Marker potion — the actual root/immortality is enforced by `ZhonyaStasisHandler`.
Rendering mirrors `PotionCleanse`'s standalone-PNG approach:

```java
package com.spege.insanetweaks.potions;

import javax.annotation.Nonnull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Gilded Stasis — applied by Zhonya's Hourglass activation.
 *
 * Marker effect: while active, ZhonyaStasisHandler cancels all incoming damage,
 * roots the player and blocks attacks/interactions; ZhonyaClientHandler renders
 * the player model with a golden tint. This class carries no game logic itself.
 */
public class PotionGildedStasis extends Potion {

    @SideOnly(Side.CLIENT)
    private static final ResourceLocation STASIS_TEXTURE =
            new ResourceLocation("insanetweaks", "textures/gui/potion/gilded_stasis.png");

    public PotionGildedStasis() {
        super(false, 0xFFD700); // gold
        this.setPotionName("potion.insanetweaks.gilded_stasis");
        this.setBeneficial();
    }

    @Override
    public boolean isInstant() {
        return false;
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return false; // no performEffect logic
    }

    @Override
    public boolean isBeneficial() {
        return true;
    }

    @Override
    public boolean hasStatusIcon() {
        return false; // use our own PNG rendering below
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("null") // bindTexture param lacks @Nonnull annotation in Forge 1.12.2 API
    public void renderHUDEffect(int x, int y, @Nonnull PotionEffect effect, @Nonnull Minecraft mc, float alpha) {
        mc.getTextureManager().bindTexture(STASIS_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        drawFullTexture(x + 3, y + 3, 18, 18);
    }

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("null") // bindTexture param lacks @Nonnull annotation in Forge 1.12.2 API
    public void renderInventoryEffect(int x, int y, @Nonnull PotionEffect effect, @Nonnull Minecraft mc) {
        mc.getTextureManager().bindTexture(STASIS_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawFullTexture(x + 3, y + 3, 18, 18);
    }

    @SideOnly(Side.CLIENT)
    @SuppressWarnings("null") // DefaultVertexFormats fields lack @Nonnull in Forge 1.12.2 API
    private static void drawFullTexture(int x, int y, int w, int h) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(x,     y + h, 0).tex(0.0, 1.0).endVertex();
        buf.pos(x + w, y + h, 0).tex(1.0, 1.0).endVertex();
        buf.pos(x + w, y,     0).tex(1.0, 0.0).endVertex();
        buf.pos(x,     y,     0).tex(0.0, 0.0).endVertex();
        tess.draw();
    }
}
```

Placeholder icon:
```bash
cp src/main/resources/assets/insanetweaks/textures/gui/potion/cleanse.png src/main/resources/assets/insanetweaks/textures/gui/potion/gilded_stasis.png
```

- [ ] **Step 2: Register in ModPotions**

Add field after `IMMUNE_BOND`:
```java
    public static PotionGildedStasis GILDED_STASIS;
```
Import `com.spege.insanetweaks.potions.PotionGildedStasis;` and register in `registerPotions(...)`:
```java
        GILDED_STASIS = (PotionGildedStasis) new PotionGildedStasis()
                .setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "gilded_stasis"));
        event.getRegistry().register(GILDED_STASIS);
```

- [ ] **Step 3: Config options in TweaksCategory**

Append to `TweaksCategory`:

```java
    @Config.Comment({"Zhonya's Hourglass: cooldown after activation, in ticks.",
            "Default 216000 = 3 hours."})
    @Config.Name("Zhonya Cooldown Ticks")
    @Config.RangeInt(min = 0)
    public int zhonyaCooldownTicks = 216000;

    @Config.Comment({"Zhonya's Hourglass: Gilded Stasis duration in ticks (default 60 = 3 s)."})
    @Config.Name("Zhonya Stasis Duration Ticks")
    @Config.RangeInt(min = 1)
    public int zhonyaStasisTicks = 60;

    @Config.Comment({"Zhonya's Hourglass: aggro-loss window in ticks (default 100 = 5 s).",
            "During this window all mobs targeting the user are de-aggroed every tick."})
    @Config.Name("Zhonya Aggro Loss Ticks")
    @Config.RangeInt(min = 0)
    public int zhonyaAggroLossTicks = 100;

    @Config.Comment({"Zhonya's Hourglass: minimum CURRENT mana required to activate.",
            "Activation always drains ALL current mana."})
    @Config.Name("Zhonya Minimum Mana")
    @Config.RangeInt(min = 0)
    public int zhonyaMinMana = 100;
```

- [ ] **Step 4: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/potions/PotionGildedStasis.java src/main/java/com/spege/insanetweaks/init/ModPotions.java src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java src/main/resources/assets/insanetweaks/textures/gui/potion/gilded_stasis.png
git commit -m "feat: PotionGildedStasis + Zhonya config options"
```

---

### Task 4: New Zhonya behavior (rewrite ItemZhonyasHourglassArtefact)

**Files:**
- Rewrite: `src/main/java/com/spege/insanetweaks/baubles/ItemZhonyasHourglassArtefact.java`

- [ ] **Step 1: Replace the whole file with:**

```java
package com.spege.insanetweaks.baubles;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.events.ZhonyaStasisHandler;
import com.spege.insanetweaks.init.ModPotions;
import com.spege.insanetweaks.util.PlayerManaCompat;

import electroblob.wizardry.item.ItemArtefact;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Artefakt: Zhonyas Hourglass (REWORK 2026-07-09)
 *
 * Dawna funkcja (restoracja SRP entity) przeniesiona 1:1 do
 * ItemRestorationHourglassArtefact. Ten item ma NOWE działanie:
 *
 * AKTYWNE (PPM trzymając w ręce):
 *   1. Koszt: drenaż CAŁEJ aktualnej many (player_mana) + cooldown (config, domyślnie 3 h).
 *      Wymagane minimum many (config, domyślnie 100) — poniżej aktywacja odmawia
 *      i nie zużywa cooldownu.
 *   2. Gilded Stasis (config, domyślnie 3 s): pełna nieśmiertelność + full heal
 *      + Cleanse + root w miejscu + złoty tint modelu (ZhonyaStasisHandler /
 *      ZhonyaClientHandler egzekwują efekt — ten item tylko go nakłada).
 *   3. Aggro loss (config, domyślnie 5 s): wszystkie moby celujące w gracza
 *      tracą target, per-tick przez całe okno (pokrywa agresywny re-targeting SRP).
 */
@SuppressWarnings("null")
public class ItemZhonyasHourglassArtefact extends ItemArtefact {

    public ItemZhonyasHourglassArtefact() {
        super(EnumRarity.EPIC, Type.CHARM);
        this.setRegistryName("zhonyas_hourglass");
        this.setUnlocalizedName("insanetweaks.zhonyas_hourglass");
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);

        if (world.isRemote) {
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }

        if (player.getCooldownTracker().hasCooldown(this)) {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        // --- Koszt: wymaga player_mana i minimum aktualnej many ---
        if (!PlayerManaCompat.isAvailable()) {
            player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "[Zhonyas] The hourglass is inert without a mana soul (player_mana missing)."));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        double currentMana = PlayerManaCompat.getCurrentMana(player);
        if (currentMana < ModConfig.tweaks.zhonyaMinMana) {
            player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "[Zhonyas] Not enough mana ("
                + (int) currentMana + "/" + ModConfig.tweaks.zhonyaMinMana + ")."));
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        // Płacimy: cała mana + cooldown.
        PlayerManaCompat.setCurrentMana(player, 0.0D);
        if (!player.isCreative()) {
            player.getCooldownTracker().setCooldown(this, ModConfig.tweaks.zhonyaCooldownTicks);
        }

        // --- Gilded Stasis ---
        int stasisTicks = ModConfig.tweaks.zhonyaStasisTicks;
        player.setHealth(player.getMaxHealth());
        player.addPotionEffect(new PotionEffect(ModPotions.GILDED_STASIS, stasisTicks, 0, false, false));
        player.addPotionEffect(new PotionEffect(ModPotions.CLEANSE, stasisTicks, 0, false, false));

        // --- Aggro loss window (NBT timestamp; handler czyści per-tick) ---
        long until = world.getTotalWorldTime() + ModConfig.tweaks.zhonyaAggroLossTicks;
        player.getEntityData().setLong(ZhonyaStasisHandler.TAG_AGGRO_LOSS_UNTIL, until);
        ZhonyaStasisHandler.clearAggroAround(player);

        // --- Audio-wizualia ---
        world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0f, 0.6f);
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.CRIT_MAGIC,
                    player.posX, player.posY + player.height * 0.5D, player.posZ, 60,
                    0.5D, 0.9D, 0.5D, 0.05D);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "Time stolen from the parasite hive.");
        tooltip.add("");
        tooltip.add(TextFormatting.GOLD + "Gilded Stasis");
        tooltip.add(TextFormatting.GRAY + "Right-click: freeze yourself in golden stasis");
        tooltip.add(TextFormatting.GRAY + "for a moment — invulnerable, fully healed,");
        tooltip.add(TextFormatting.GRAY + "cleansed, and forgotten by your enemies.");
        tooltip.add("");
        tooltip.add(TextFormatting.RED + "Cost: ALL of your current mana.");
        tooltip.add(TextFormatting.RED + "Long cooldown.");
    }

    @Override
    @Nonnull
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.EPIC;
    }
}
```

- [ ] **Step 2: Build** — will FAIL until Task 5 provides `ZhonyaStasisHandler`; that's
expected. If executing tasks strictly in order, do Task 5 before building, or build at
the end of Task 5. (When executing with one subagent per task, fold Tasks 4+5 into one
dispatch — they compile together.)

---

### Task 5: ZhonyaStasisHandler (server) + ZhonyaClientHandler (client tint)

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/events/ZhonyaStasisHandler.java`
- Create: `src/main/java/com/spege/insanetweaks/events/ZhonyaClientHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`

- [ ] **Step 1: Server handler**

```java
package com.spege.insanetweaks.events;

import com.spege.insanetweaks.init.ModPotions;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Egzekwuje Gilded Stasis (Zhonya rework):
 *  - nieśmiertelność: cancel LivingAttackEvent gdy efekt aktywny,
 *  - root: zerowanie ruchu co tick (obie strony — client też, żeby input nie szarpał),
 *  - blokada ataku i interakcji podczas stasis,
 *  - aggro loss: przez okno TAG_AGGRO_LOSS_UNTIL czyści per-tick target każdego moba
 *    celującego w gracza (pokrywa agresywny re-targeting AI SRParasites).
 */
public class ZhonyaStasisHandler {

    /** NBT (entityData): world-time do którego trwa okno utraty aggro. */
    public static final String TAG_AGGRO_LOSS_UNTIL = "ZhonyaAggroLossUntil";

    /** Promień czyszczenia aggro wokół gracza. */
    private static final double AGGRO_CLEAR_RADIUS = 24.0D;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings("null") // ModPotions.GILDED_STASIS is guaranteed non-null at runtime
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;
        if (event.getEntityLiving().isPotionActive(ModPotions.GILDED_STASIS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntityPlayer().isPotionActive(ModPotions.GILDED_STASIS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelable() && event.getEntityPlayer().isPotionActive(ModPotions.GILDED_STASIS)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;

        // Root — obie strony, żeby klient nie przewidywał ruchu.
        if (player.isPotionActive(ModPotions.GILDED_STASIS)) {
            player.motionX = 0.0D;
            player.motionZ = 0.0D;
            if (player.motionY > 0.0D) player.motionY = 0.0D;
        }

        if (player.world.isRemote) return;

        // Aggro loss window.
        NBTTagCompound data = player.getEntityData();
        if (data.hasKey(TAG_AGGRO_LOSS_UNTIL)) {
            if (player.world.getTotalWorldTime() <= data.getLong(TAG_AGGRO_LOSS_UNTIL)) {
                clearAggroAround(player);
            } else {
                data.removeTag(TAG_AGGRO_LOSS_UNTIL);
            }
        }
    }

    /** Zdejmuje gracza z celownika wszystkich mobów w promieniu. */
    public static void clearAggroAround(EntityPlayer player) {
        AxisAlignedBB box = player.getEntityBoundingBox().grow(AGGRO_CLEAR_RADIUS);
        for (EntityLiving mob : player.world.getEntitiesWithinAABB(EntityLiving.class, box)) {
            if (mob.getAttackTarget() == player) {
                mob.setAttackTarget(null);
            }
            if (mob.getRevengeTarget() == player) {
                mob.setRevengeTarget(null);
            }
        }
    }
}
```

- [ ] **Step 2: Client tint handler**

```java
package com.spege.insanetweaks.events;

import com.spege.insanetweaks.init.ModPotions;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Złoty tint modelu gracza podczas Gilded Stasis (Zhonya rework).
 * EB-owa petryfikacja nie działa na graczach (zamienia EntityLiving w BlockStatue),
 * więc "złota petryfikacja" jest naszą iluzją: multiplikatywny złoty kolor na renderze.
 */
@SideOnly(Side.CLIENT)
public class ZhonyaClientHandler {

    @SubscribeEvent
    @SuppressWarnings("null") // ModPotions.GILDED_STASIS is guaranteed non-null at runtime
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (event.getEntityPlayer().isPotionActive(ModPotions.GILDED_STASIS)) {
            GlStateManager.color(1.0f, 0.82f, 0.15f, 1.0f);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (event.getEntityPlayer().isPotionActive(ModPotions.GILDED_STASIS)) {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}
```

- [ ] **Step 3: Register both handlers in InsaneTweaksMod**

In `init(...)`, directly after the unconditional
`MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.IndestructibleDropHandler());`
(line 282), add:

```java
        // Zhonya rework: Gilded Stasis enforcement (immortality, root, aggro loss).
        MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ZhonyaStasisHandler());
```

In `preInit(...)`, inside the existing `Side.CLIENT` block (where entity renderers are
registered), add at its end:

```java
            // Zhonya rework: golden player tint during Gilded Stasis.
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ZhonyaClientHandler());
```

- [ ] **Step 4: Build**

Run: `.\gradlew build`
Expected: `BUILD SUCCESSFUL` (this also resolves Task 4's pending compile).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/baubles/ItemZhonyasHourglassArtefact.java src/main/java/com/spege/insanetweaks/events/ZhonyaStasisHandler.java src/main/java/com/spege/insanetweaks/events/ZhonyaClientHandler.java src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat: Zhonya rework — Gilded Stasis active (mana drain, 3h cd, root, aggro loss, gold tint)"
```

---

### Task 6: Manual verification (runClient)

Lower `zhonyaCooldownTicks` in the run config to e.g. 200 for testing.

- [ ] **Step 1: Activation** — hold Zhonya, right-click with full mana: mana → 0,
  full heal, gold-tinted frozen player for 3 s, cooldown starts. Below-minimum mana:
  refusal message, no cooldown.
- [ ] **Step 2: Immortality + root** — stand in fire / let mobs hit you during stasis:
  zero damage; movement keys do nothing; attacks and item use blocked.
- [ ] **Step 3: Aggro loss** — aggro several zombies AND several SRP parasites, activate:
  all must drop the player as target and not re-acquire for 5 s. If SRP mobs still chase
  (their AI holds targets outside `attackTarget`/`revengeTarget`), inspect
  `EntityParasiteBase` AI in the decompiled SRP tree and extend `clearAggroAround`
  accordingly — this is the one research-flagged risk of this plan.
- [ ] **Step 4: Restoration regression** — with the new Hourglass of Restoration equipped
  (CHARM slot), cast Purifying Pulse on an SRP-converted mob → restored exactly as before;
  right-click restore works with its 6 h cooldown.
- [ ] **Step 5: Commit any tuning**

```bash
git add -A
git commit -m "balance: zhonya stasis tuning after manual testing"
```
