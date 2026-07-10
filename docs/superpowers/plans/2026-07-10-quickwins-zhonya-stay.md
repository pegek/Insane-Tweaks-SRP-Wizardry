# Quick Wins (Zhonya toggle + STAY anchor wander) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship Zhonya's Hourglass disabled-by-default (inert master toggle) and give Thrall STAY mode an anchor with a 4-block calm wander.

**Architecture:** Config toggle + `ItemArtefact.setEnabled(false)` (EB's own per-artefact kill switch — makes `isArtefactActive` return false) + right-click gate + tooltip. STAY anchor is a server-side `BlockPos` captured on mode entry, persisted in NBT, consumed by `ThrallAIWander`.

**Tech Stack:** Forge 1.12.2, Java 8, Forge `@Config` annotations. No test suite in this project — verification is `./gradlew build` (expect `BUILD SUCCESSFUL`) plus the manual runClient checklist at the end.

**Spec:** `docs/superpowers/specs/2026-07-10-quickwins-zhonya-stay-design.md`

**Key facts discovered during brainstorm (do not re-derive):**
- Our mod does NO loot injection for artefacts, and EB's `subsets/epic_artefacts.json` is a static file listing only EB items — Zhonya has no loot path today, so "no acquisition when disabled" needs no loot work at all.
- `electroblob.wizardry.item.ItemArtefact` has public `setEnabled(boolean)` / `isEnabled()`; `isArtefactActive(player, item)` returns false for disabled artefacts (verified in `notes/decompiled_mods/ebwizardry_source/decompiled_src/electroblob/wizardry/item/ItemArtefact.java:141-190`).

---

### Task 1: Zhonya master toggle in config

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java`

- [ ] **Step 1: Add the toggle field**

Insert directly BEFORE the existing `zhonyaCooldownTicks` field (line ~25):

```java
    @Config.Comment({"Master toggle for the Zhonya's Hourglass artefact (the player-stasis one).",
            "When false (default), the artefact is inert: right-click does nothing, EB Wizardry",
            "treats it as a disabled artefact, and the tooltip shows a 'disabled' line.",
            "The item stays registered, so existing copies in worlds are unaffected.",
            "The Restoration Hourglass is NOT affected by this switch."})
    @Config.Name("Enable Zhonya's Hourglass")
    @Config.RequiresMcRestart
    public boolean enableZhonya = false;
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/config/categories/TweaksCategory.java
git commit -m "feat: enableZhonya config toggle, default OFF"
```

### Task 2: Make the item honor the toggle

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/baubles/ItemZhonyasHourglassArtefact.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (init phase)
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`
- Modify: `src/main/resources/assets/insanetweaks/lang/ru_ru.lang`

- [ ] **Step 1: Gate activation**

In `ItemZhonyasHourglassArtefact`, find the `onItemRightClick` override (signature `public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand)`). Add as the FIRST statement of the method body:

```java
        if (!ModConfig.tweaks.enableZhonya) {
            return new ActionResult<ItemStack>(EnumActionResult.PASS, player.getHeldItem(hand));
        }
```

(`ModConfig`, `ActionResult`, `EnumActionResult` are already imported in this file.)

- [ ] **Step 2: Tooltip disabled line**

In the same file, find the `addInformation` override (`@SideOnly(Side.CLIENT)`). Add as the FIRST statement of the method body (before the existing tooltip lines):

```java
        if (!ModConfig.tweaks.enableZhonya) {
            tooltip.add(TextFormatting.DARK_GRAY
                    + net.minecraft.client.resources.I18n.format("item.insanetweaks.zhonyas_hourglass.disabled"));
        }
```

(`TextFormatting` is already imported. If the tooltip list parameter has a different name than `tooltip`, use the actual parameter name.)

- [ ] **Step 3: Disable at EB level in init**

In `InsaneTweaksMod.init(FMLInitializationEvent event)`, near the other unconditional registrations (e.g. right before the `ZhonyaStasisHandler` registration at `MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ZhonyaStasisHandler());`), add:

```java
        if (!com.spege.insanetweaks.config.ModConfig.tweaks.enableZhonya) {
            ((electroblob.wizardry.item.ItemArtefact) com.spege.insanetweaks.init.ModItems.ZHONYAS_HOURGLASS)
                    .setEnabled(false);
            LOGGER.info("[InsaneTweaks] Zhonya's Hourglass is disabled via config (tweaks.enableZhonya=false).");
        }
```

Note: the `ZhonyaStasisHandler` / `ZhonyaClientHandler` registrations stay unconditional — the Gilded Stasis potion effect can only be applied by the item, which is now gated, and the handlers are harmless when the effect is never applied.

- [ ] **Step 4: Lang entries**

`en_us.lang` — add below the existing `item.insanetweaks:zhonyas_hourglass.desc` line:

```
item.insanetweaks.zhonyas_hourglass.disabled=Disabled in config (tweaks -> Enable Zhonya's Hourglass)
```

`ru_ru.lang` — add below the existing Zhonya desc line:

```
item.insanetweaks.zhonyas_hourglass.disabled=Отключено в конфиге (tweaks -> Enable Zhonya's Hourglass)
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/baubles/ItemZhonyasHourglassArtefact.java src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java src/main/resources/assets/insanetweaks/lang/en_us.lang src/main/resources/assets/insanetweaks/lang/ru_ru.lang
git commit -m "feat: Zhonya's Hourglass inert + EB-disabled when enableZhonya=false"
```

### Task 3: STAY anchor on the Thrall

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntityThrallMinion.java`

- [ ] **Step 1: Add the field + accessor**

Near the other private state fields of `EntityThrallMinion` (e.g. next to `resumeWorkPos`), add:

```java
    /** Position captured when the thrall enters STAY mode; wander in STAY is confined
     *  around this point (spec 2026-07-10 A2). Null outside STAY. */
    @Nullable
    private BlockPos stayAnchor;
```

And a public accessor near `getHomePoint()` (line ~704):

```java
    /** Anchor of the current STAY session, or null when not in STAY. */
    @Nullable
    public BlockPos getStayAnchor() {
        return stayAnchor;
    }
```

- [ ] **Step 2: Capture/clear the anchor in setMode**

In `setMode(ThrallMode mode)` (line ~635), directly after `this.dataManager.set(MODE_ORDINAL, mode.ordinal());`, add:

```java
        if (mode == ThrallMode.STAY) {
            if (prev != ThrallMode.STAY || stayAnchor == null) {
                stayAnchor = new BlockPos(this);
            }
        } else {
            stayAnchor = null;
        }
```

- [ ] **Step 3: Cover the setMode-bypass path**

`pauseAfterResummon` (comment at line ~445: "Bypasses setMode for the STAY transition") writes `this.dataManager.set(MODE_ORDINAL, ThrallMode.STAY.ordinal());` directly (line ~458). Immediately after that line add:

```java
        this.stayAnchor = new BlockPos(this);
```

- [ ] **Step 4: NBT persistence**

In `writeEntityToNBT(NBTTagCompound tag)` (line ~910), next to the `ThrallResumePos` block, add:

```java
        if (stayAnchor != null) {
            tag.setIntArray("ThrallStayAnchor", new int[]{stayAnchor.getX(), stayAnchor.getY(), stayAnchor.getZ()});
        }
```

In `readEntityFromNBT(NBTTagCompound tag)` (line ~962), next to the `ThrallResumePos` read, add:

```java
        if (tag.hasKey("ThrallStayAnchor")) {
            int[] anchor = tag.getIntArray("ThrallStayAnchor");
            if (anchor.length == 3) {
                stayAnchor = new BlockPos(anchor[0], anchor[1], anchor[2]);
            }
        }
```

NOTE: `readEntityFromNBT` calls `setMode(...)` when restoring `ThrallMode` — that call runs BEFORE this read in the method body, and for STAY it re-captures the anchor at the entity's restored position. The NBT read above then overwrites it with the saved anchor, which is the desired end state. Keep the read AFTER the `ThrallMode` restore block.

- [ ] **Step 5: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add src/main/java/com/spege/insanetweaks/entities/EntityThrallMinion.java
git commit -m "feat: thrall STAY anchor captured on mode entry, persisted in NBT"
```

### Task 4: Anchored calm wander in STAY

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/ThrallCategory.java`
- Modify: `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIWander.java`

- [ ] **Step 1: Config fields**

In `ThrallCategory.General` (after `followTeleportDistance`), add:

```java
        @Config.Comment({"Wander radius (blocks) around the STAY anchor. In STAY mode the thrall",
                "only strolls within this distance of the spot where it was told to stay." })
        @Config.Name("Stay: Wander Radius")
        @Config.RangeInt(min = 1, max = 16)
        public int stayWanderRadius = 4;

        @Config.Comment({"Movement speed of the STAY-mode stroll (other modes wander at 0.6)." })
        @Config.Name("Stay: Wander Speed")
        @Config.RangeDouble(min = 0.1, max = 1.0)
        public double stayWanderSpeed = 0.4;
```

- [ ] **Step 2: Rewrite ThrallAIWander**

Replace the entire contents of `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIWander.java` with:

```java
package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallMode;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * AI task: Idle wander for Thrall.
 * Active only when no other task is running.
 * Uses world.getTotalWorldTime() for tick-based intervals (not System.currentTimeMillis()).
 *
 * STAY mode (spec 2026-07-10 A2): the stroll is anchored — targets are confined to
 * thrall.general.stayWanderRadius blocks around the STAY anchor and movement is slower
 * (stayWanderSpeed). If the thrall is pushed outside the radius it paths back to the anchor.
 */
@SuppressWarnings("null")
public class ThrallAIWander extends EntityAIBase {

    private static final long WANDER_INTERVAL_TICKS = 200L;
    private static final double WANDER_SPEED = 0.6D;

    private final EntityThrallMinion thrall;
    private double targetX, targetY, targetZ;
    private double moveSpeed;
    private long nextWanderTime;

    public ThrallAIWander(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(1);
        this.nextWanderTime = 0L;
    }

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() == ThrallMode.STAY && thrall.getStayAnchor() != null) {
            return shouldExecuteStay(thrall.getStayAnchor());
        }
        return shouldExecuteDefault();
    }

    private boolean shouldExecuteDefault() {
        long now = thrall.world.getTotalWorldTime();
        if (now < nextWanderTime) return false;

        Vec3d vec = RandomPositionGenerator.getLandPos(thrall, 10, 7);
        if (vec == null) return false;

        this.targetX = vec.x;
        this.targetY = vec.y;
        this.targetZ = vec.z;
        this.moveSpeed = WANDER_SPEED;
        this.nextWanderTime = now + WANDER_INTERVAL_TICKS + (long)(thrall.getRNG().nextFloat() * 100);
        return true;
    }

    private boolean shouldExecuteStay(BlockPos anchor) {
        int radius = ModConfig.thrall.general.stayWanderRadius;
        double radiusSq = (double) radius * radius;

        // Pushed/displaced outside the radius — return to the anchor immediately.
        if (thrall.getDistanceSqToCenter(anchor) > radiusSq) {
            this.targetX = anchor.getX() + 0.5D;
            this.targetY = anchor.getY();
            this.targetZ = anchor.getZ() + 0.5D;
            this.moveSpeed = ModConfig.thrall.general.stayWanderSpeed;
            return true;
        }

        long now = thrall.world.getTotalWorldTime();
        if (now < nextWanderTime) return false;

        Vec3d vec = RandomPositionGenerator.getLandPos(thrall, radius, 3);
        if (vec == null) return false;
        // getLandPos is centred on the THRALL, not the anchor — reject picks that
        // would drift outside the anchor radius.
        if (anchor.distanceSq(vec.x, vec.y, vec.z) > radiusSq) return false;

        this.targetX = vec.x;
        this.targetY = vec.y;
        this.targetZ = vec.z;
        this.moveSpeed = ModConfig.thrall.general.stayWanderSpeed;
        this.nextWanderTime = now + WANDER_INTERVAL_TICKS + (long)(thrall.getRNG().nextFloat() * 100);
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return !thrall.getNavigator().noPath();
    }

    @Override
    public void startExecuting() {
        thrall.getNavigator().tryMoveToXYZ(targetX, targetY, targetZ, moveSpeed);
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/config/categories/ThrallCategory.java src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIWander.java
git commit -m "feat: STAY-mode wander anchored to 4-block radius at reduced speed"
```

### Task 5: Manual verification (runClient)

- [ ] Zhonya OFF (default): give `insanetweaks:zhonyas_hourglass`, right-click → nothing happens, no mana drained, no cooldown; tooltip shows the grey disabled line. Restoration Hourglass still works.
- [ ] Set `enableZhonya=true` in `run/config/insanetweaks.cfg`, restart client: activation works as before this change.
- [ ] Summon thrall, order STAY: it strolls only within ~4 blocks, visibly slower. Piston-push or `/tp` it 10 blocks away → it walks back to the anchor. Save+quit+reload → anchor persists (still returns to the same spot).
- [ ] FOLLOW-mode idle wander unchanged (10-block range, speed 0.6).
