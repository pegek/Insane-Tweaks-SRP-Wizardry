# Sentinel Expansion (Combat AI, GUI, Modes, Utility) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the ASC battlemage AI with our own state machine (fixes "rarely casts"), merge control + loot into one container-backed GUI with real item withdrawal, add Aggressive/Defensive stance, target priorities, editable guard radius, and pickup/deposit toggles.

**Architecture:** Execution order **F1 → F4 → F2 → F3** (each stage independently testable). F1 mirrors the sim-wizard v4 consolidation (spec 2026-07-10-sim-wizard-ai) in a battlemage variant (melee ≤ 6 blocks, cast > 6). F4 replaces the view-only NBT-snapshot loot screen with a vanilla `Container` opened through `IGuiHandler` (sync + shift-click for free). F2/F3 are DataParameter-backed per-entity settings driven by new `PacketSentinelCommand` actions.

**Tech Stack:** Forge 1.12.2, Java 8. No test suite — `./gradlew build` + manual runClient checklist.

**Spec:** `docs/superpowers/specs/2026-07-10-sentinel-expansion-design.md`

**Facts discovered during brainstorm (do not re-derive):**
- The current loot GUI (`GuiSentinelLoot`) is a read-only snapshot — no withdrawal exists at all; `PacketOpenSentinelLoot` ships a one-shot NBT copy.
- Owner protection ALREADY exists (`EntitySentinel.syncOwnerPriorityTarget`, line ~575) and auto-deposit ALREADY exists (`tickGuardChestDeposit`, line ~443) — F2/F3 build on them, do not duplicate.
- Guard radius is the hardcoded `DEFAULT_GUARD_RADIUS = 20` used in `enforceGuardBoundaries` (lines ~614-620) and patrol-point building.
- Acquisition is deliberately out of scope (user decision).

---

### Task 1 (F1): EntityAISentinelCombat — battlemage state machine

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/entities/ai/EntityAISentinelCombat.java`
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntitySentinel.java:158-189` (initEntityAI)
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/ClientCategory.java`

- [ ] **Step 1: Debug flag**

Append to `ClientCategory` (mirrors the sim-wizard flag):

```java
    @Config.Comment({
            "Verbose diagnostic logging for the Sentinel combat pipeline: target gates,",
            "spell picks, cast results, melee/cast state transitions." })
    @Config.Name("Sentinel Debug Logs")
    public boolean enableSentinelDebugLogs = false;
```

- [ ] **Step 2: Create the combat task**

Before writing, read `EntitySentinel.java` fully once (lines 650-end contain `getSpells()`, `getModifiers()`, `setContinuousSpell`, `getAimingError`, cooldown plumbing from `ICustomCooldown`) — the task below uses those members; adjust names to what actually exists.

```java
package com.spege.insanetweaks.entities.ai;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntitySentinel;

import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.MinecraftForge;

/**
 * F1 (spec 2026-07-10): the Sentinel's single combat task, replacing ASC's
 * EntityAIBattlemageMelee + EntityAIBattlemageSpellcasting (which cast too rarely and
 * were outside our control). Battlemage variant of the sim-wizard v4 state machine:
 *
 *   distance > MELEE_RANGE : casting stance — approach to CAST_RANGE, cast on cadence
 *   distance <= MELEE_RANGE: melee — navigate + swing with attackEntityAsMob
 *
 * Shield use stays in ASC's EntityAIBlockWithShield (task 2, separate mutex — works today).
 */
public class EntityAISentinelCombat extends EntityAIBase {

    private static final double MELEE_RANGE = 6.0D;
    private static final double MELEE_REACH_SQ = 2.5D * 2.5D;
    private static final double CAST_RANGE = 14.0D;
    private static final int MELEE_SWING_COOLDOWN = 20;
    private static final int BASE_CAST_COOLDOWN = 60;
    private static final int MAX_SPELL_COOLDOWN_BONUS = 80;
    private static final int FAILED_CAST_COOLDOWN = 10;
    private static final int REPATH_INTERVAL = 10;
    private static final double MOVE_SPEED = 1.0D;

    private final EntitySentinel sentinel;
    private long nextCastReadyTime;
    private int meleeSwingTimer;
    private int repathTimer;

    public EntityAISentinelCombat(EntitySentinel sentinel) {
        this.sentinel = sentinel;
        this.setMutexBits(3);
    }

    private void logDiag(String message) {
        if (!ModConfig.client.enableSentinelDebugLogs) return;
        com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks][Sentinel#{}] {}", this.sentinel.getEntityId(), message);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = this.sentinel.getAttackTarget();
        return target != null && target.isEntityAlive();
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    @Override
    public void resetTask() {
        this.sentinel.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = this.sentinel.getAttackTarget();
        if (target == null) return;

        this.sentinel.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        if (this.meleeSwingTimer > 0) this.meleeSwingTimer--;

        double distance = this.sentinel.getDistance(target);

        if (distance <= MELEE_RANGE) {
            tickMelee(target, distance);
        } else {
            tickCasting(target, distance);
        }
    }

    private void tickMelee(EntityLivingBase target, double distance) {
        if (distance * distance > MELEE_REACH_SQ) {
            repathTo(target);
            return;
        }
        this.sentinel.getNavigator().clearPath();
        if (this.meleeSwingTimer <= 0) {
            this.sentinel.swingArm(EnumHand.MAIN_HAND);
            this.sentinel.attackEntityAsMob(target);
            this.meleeSwingTimer = MELEE_SWING_COOLDOWN;
            logDiag("melee swing at " + target.getName());
        }
    }

    private void tickCasting(EntityLivingBase target, double distance) {
        if (distance > CAST_RANGE) {
            repathTo(target);
        } else {
            this.sentinel.getNavigator().clearPath();
        }

        if (this.sentinel.world.getTotalWorldTime() < this.nextCastReadyTime) return;
        if (distance > CAST_RANGE) return;

        Spell spell = pickSpell(target, distance);
        logDiag("pickSpell -> " + (spell == null ? "null" : String.valueOf(spell.getRegistryName()))
                + " (dist " + String.format("%.1f", distance) + ")");
        if (spell == null || spell == Spells.none) {
            setCooldown(FAILED_CAST_COOLDOWN);
            return;
        }

        SpellModifiers modifiers = this.sentinel.getModifiers();
        if (MinecraftForge.EVENT_BUS.post(
                new SpellCastEvent.Pre(SpellCastEvent.Source.NPC, spell, this.sentinel, modifiers))) {
            setCooldown(FAILED_CAST_COOLDOWN);
            return;
        }

        boolean cast = spell.cast(this.sentinel.world, this.sentinel, EnumHand.MAIN_HAND, 0, target, modifiers);
        logDiag("cast " + spell.getRegistryName() + " -> " + cast);
        if (!cast) {
            setCooldown(FAILED_CAST_COOLDOWN);
            return;
        }
        MinecraftForge.EVENT_BUS.post(
                new SpellCastEvent.Post(SpellCastEvent.Source.NPC, spell, this.sentinel, modifiers));
        this.sentinel.swingArm(EnumHand.MAIN_HAND);
        setCooldown(BASE_CAST_COOLDOWN + Math.min(spell.getCooldown() / 2, MAX_SPELL_COOLDOWN_BONUS));
    }

    private void repathTo(EntityLivingBase target) {
        if (--this.repathTimer > 0) return;
        this.repathTimer = REPATH_INTERVAL;
        this.sentinel.getNavigator().tryMoveToEntityLiving(target, MOVE_SPEED);
    }

    private void setCooldown(int ticks) {
        this.nextCastReadyTime = this.sentinel.world.getTotalWorldTime() + Math.max(0, ticks);
    }

    /**
     * Heuristics ported from the sim wizard: low HP -> heal/summon; otherwise a random
     * pick among the sentinel's pool entries that make sense at this distance
     * (summons and heal excluded from the offensive pick).
     */
    private Spell pickSpell(EntityLivingBase target, double distance) {
        List<Spell> pool = this.sentinel.getSpells();
        if (pool.isEmpty()) return null;

        if (this.sentinel.getHealth() / this.sentinel.getMaxHealth() <= 0.35F) {
            List<Spell> selfCare = new ArrayList<Spell>(3);
            for (Spell s : pool) {
                if (s == null || s.getRegistryName() == null) continue;
                String path = s.getRegistryName().getResourcePath();
                if (path.equals("heal") || path.startsWith("summon_")) selfCare.add(s);
            }
            if (!selfCare.isEmpty()) {
                return selfCare.get(this.sentinel.getRNG().nextInt(selfCare.size()));
            }
        }

        List<Spell> offensive = new ArrayList<Spell>(pool.size());
        for (Spell s : pool) {
            if (s == null || s == Spells.none || s.getRegistryName() == null) continue;
            String path = s.getRegistryName().getResourcePath();
            if (path.equals("heal") || path.startsWith("summon_")) continue;
            offensive.add(s);
        }
        if (offensive.isEmpty()) {
            return pool.get(this.sentinel.getRNG().nextInt(pool.size()));
        }
        return offensive.get(this.sentinel.getRNG().nextInt(offensive.size()));
    }
}
```

Adaptation notes: heal spells cast on the sentinel itself need `castTarget = this.sentinel` — if `spell.cast` with `target` misbehaves for heal, special-case it exactly like `EntityAISimWizardCast.fireCommittedCast` does (`isSelfHeal` branch). If `EntitySentinel.getModifiers()` does not exist under that name, use the ISpellCaster member that returns `SpellModifiers` (check the file); it must fold in `spellPotencyMultiplier`.

Deliberate deviations from the spec, agreed at plan time: (1) NO telegraph for the sentinel — telegraph exists to give the PLAYER a dodge window against an enemy caster; the sentinel is an ally, a wind-up would only nerf it. (2) The distance-band/cluster heuristics from the sim wizard are NOT ported in v1 of this task — the sentinel's pool is randomized per-spawn (ADVANCED/MASTER tier), so band tables keyed to specific spell names would not apply; the low-HP self-care branch + offensive-only filtering is the meaningful part and IS ported. Revisit after playtest if spell choice feels dumb.

- [ ] **Step 3: Swap the tasks in initEntityAI**

In `EntitySentinel.initEntityAI()` replace:

```java
        this.tasks.addTask(3, this.battlemageMeleeAI);
        this.tasks.addTask(3, this.battlemageSpellcastingAI);
```

with:

```java
        this.tasks.addTask(3, new com.spege.insanetweaks.entities.ai.EntityAISentinelCombat(this));
```

Delete the fields `battlemageMeleeAI` / `battlemageSpellcastingAI` (lines ~122-123), their construction (lines ~169-171) and the now-unused ASC imports `EntityAIBattlemageMelee`, `EntityAIBattlemageSpellcasting`. KEEP `EntityAIBlockWithShield` (task 2) and everything else. Run `grep -n "battlemage" src/main/java/com/spege/insanetweaks/entities/EntitySentinel.java` — remaining matches must be unrelated (e.g. gear method names).

- [ ] **Step 4: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java
git commit -m "feat: EntityAISentinelCombat replaces ASC battlemage tasks (F1)"
```

### Task 2 (F4): Container-backed loot access

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/entities/inventory/SentinelLootInventory.java`
- Create: `src/main/java/com/spege/insanetweaks/inventory/ContainerSentinelLoot.java` (or match the package of the existing thrall container — check with `grep -rn "class Container" src/main/java` and follow that location convention)
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntitySentinel.java` (loot list accessor)
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (IGuiHandler)

- [ ] **Step 1: Expose the loot list + IInventory wrapper**

In `EntitySentinel` add next to `storeLootStack`:

```java
    /** Direct access for the loot container GUI (server-side authoritative). */
    public net.minecraft.util.NonNullList<ItemStack> getLootInventoryList() {
        return this.lootInventory;
    }
```

Create `SentinelLootInventory` implementing `IInventory` over that list — model it exactly on the existing `ThrallInventory` (`src/main/java/com/spege/insanetweaks/entities/inventory/ThrallInventory.java`): `getSizeInventory()=20`, get/set/decrStackSize backed by the list, `markDirty()` no-op, `isUsableByPlayer` checks `!sentinel.isDead && player.getDistanceSq(sentinel) <= 64`.

- [ ] **Step 2: Container**

`ContainerSentinelLoot extends Container`: 20 sentinel slots in a 5x4 grid at (44, 18); player inventory at (8, 104); hotbar at (8, 162); `transferStackInSlot` implementing the standard two-region shift-click (mirror the thrall container's implementation verbatim, adjusting slot counts); `canInteractWith` delegates to `SentinelLootInventory.isUsableByPlayer`.

- [ ] **Step 3: IGuiHandler wiring**

In `InsaneTweaksMod`, next to `GUI_ID_THRALL_INV = 1`, add `public static final int GUI_ID_SENTINEL = 2;`. In `getServerGuiElement` / `getClientGuiElement` add the `GUI_ID_SENTINEL` case (the `x` parameter carries the entity id — same convention as the thrall case): server returns `new ContainerSentinelLoot(player.inventory, new SentinelLootInventory(sentinel), sentinel)`, client returns the new `GuiSentinelControl` (Task 3 makes it a `GuiContainer`).

- [ ] **Step 4: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java
git commit -m "feat: sentinel loot exposed via Container/IGuiHandler (F4 groundwork)"
```

### Task 3 (F4): Combined control+loot screen

**Files:**
- Rewrite: `src/main/java/com/spege/insanetweaks/client/gui/GuiSentinelControl.java` (becomes a `GuiContainer`)
- Delete: `src/main/java/com/spege/insanetweaks/client/gui/GuiSentinelLoot.java`
- Delete: `src/main/java/com/spege/insanetweaks/network/PacketOpenSentinelLoot.java`
- Modify: `src/main/java/com/spege/insanetweaks/network/InsaneTweaksNetwork.java`
- Modify: `src/main/java/com/spege/insanetweaks/network/PacketSentinelCommand.java`
- Modify: `src/main/java/com/spege/insanetweaks/events/SentinelClientInteractionHandler.java`
- Modify: lang files

- [ ] **Step 1: Rewrite GuiSentinelControl as GuiContainer**

Layout (single 246x180 screen, texture: reuse `generic_54.png` regions or draw flat rects like the old loot GUI):
- top strip (y 6-16): title + `HP x/y — mode — stance` status line (read live from the client entity like the old GUI's `getSentinel()`);
- left column (x 8, width 90): buttons FOLLOW (id 0), GUARD HERE (id 1) — send the existing packet actions and DO NOT close the screen;
- right pane: the 20 container loot slots (5x4 at 140,30) + player inventory below — slot positions must match `ContainerSentinelLoot` from Task 2 exactly;
- `doesGuiPauseGame()` false; entity-gone check in `updateScreen()` closes the screen (port from the old class).

Keep button ids 0/1 mapped to `ACTION_FOLLOW` / `ACTION_GUARD_HERE`. The old loot button disappears — loot is always visible.

- [ ] **Step 2: Open the GUI server-side**

Add to `PacketSentinelCommand`: `public static final int ACTION_OPEN_GUI = 3;` and in `Handler.handle` replace the `ACTION_OPEN_LOOT` branch with:

```java
            if (message.actionId == ACTION_OPEN_GUI) {
                player.openGui(com.spege.insanetweaks.InsaneTweaksMod.INSTANCE,
                        com.spege.insanetweaks.InsaneTweaksMod.GUI_ID_SENTINEL,
                        player.world, sentinel.getEntityId(), 0, 0);
            }
```

Delete the `ACTION_OPEN_LOOT` constant. In `SentinelClientInteractionHandler`, replace the client-side `displayGuiScreen(new GuiSentinelControl(...))` call with sending `new PacketSentinelCommand(entityId, PacketSentinelCommand.ACTION_OPEN_GUI)` to the server.

- [ ] **Step 3: Remove the dead snapshot path**

`git rm` `GuiSentinelLoot.java` and `PacketOpenSentinelLoot.java`; remove the `PacketOpenSentinelLoot` registration line from `InsaneTweaksNetwork.init()` (renumbering the remaining discriminators is safe — client and server always ship the same jar). Check `EntitySentinel.writeLootInventoryToNBT` — if its only caller was the deleted packet, delete it too (verify NBT save/load of loot uses a different method first: `grep -n "writeLootInventoryToNBT" src/main/java`).

- [ ] **Step 4: Lang + build + commit**

Update `gui.insanetweaks.sentinel.*` keys: remove `action.loot`, add `stance` labels used by Task 4. Run `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java src/main/resources
git commit -m "feat!: combined sentinel control+loot container GUI, snapshot path removed (F4)"
```

### Task 4 (F2): Stance, target priorities, editable guard radius

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntitySentinel.java`
- Create: `src/main/java/com/spege/insanetweaks/entities/ai/EntityAISentinelTargetPriority.java`
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java`
- Modify: `src/main/java/com/spege/insanetweaks/network/PacketSentinelCommand.java`
- Modify: `src/main/java/com/spege/insanetweaks/client/gui/GuiSentinelControl.java`

- [ ] **Step 1: DataParameters + NBT**

In `EntitySentinel` add:

```java
    private static final DataParameter<Boolean> AGGRESSIVE_STANCE = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> GUARD_RADIUS = EntityDataManager.createKey(EntitySentinel.class,
            DataSerializers.VARINT);
```

register in `entityInit()` (`AGGRESSIVE_STANCE` -> `Boolean.TRUE`, `GUARD_RADIUS` -> `DEFAULT_GUARD_RADIUS`), add getters/setters (`isAggressiveStance`, `setAggressiveStance`, `getGuardRadius` clamped to 8..48, `setGuardRadius` sets `guardRegionDirty = true`), persist both in `writeEntityToNBT`/`readEntityFromNBT` (tags `SentinelAggressive`, `SentinelGuardRadius`).

- [ ] **Step 2: Stance gates proactive targeting**

In `initEntityAI`, extend `targetSelector` with `&& this.isAggressiveStance()` as the FIRST condition after the null checks. Defensive semantics then come for free: `EntityAIHurtByTarget` (retaliation) and `syncOwnerPriorityTarget` (owner defense) do not consult the selector and stay active.

- [ ] **Step 3: Radius replaces the constant**

Replace every use of `DEFAULT_GUARD_RADIUS` in `enforceGuardBoundaries` (lines ~614, ~620) and in the patrol-point builder (`rebuildGuardPatrolPoints`) with `this.getGuardRadius()`. Keep the constant only as the DataParameter default.

- [ ] **Step 4: Priority target task**

Config — in `EntitiesCategory`, add inside a new `Sentinel` inner class (field `public final Sentinel sentinel = new Sentinel();` at the top level of the category):

```java
    public static class Sentinel {
        @Config.Comment({ "Registry-name prefixes defining target priority, highest first.",
                "Entities matching an earlier prefix are attacked before later ones;",
                "undead rank after all listed prefixes; everything else last." })
        @Config.Name("Sentinel: Target Priority Prefixes")
        public String[] targetPriorityPrefixes = { "srparasites:", "srpextra:" };
    }
```

Create `EntityAISentinelTargetPriority extends net.minecraft.entity.ai.EntityAITarget`: in `shouldExecute` scan `world.getEntitiesWithinAABB(EntityLivingBase.class, boundingBox.grow(followRange))` filtered by the sentinel's public target predicate (expose `targetSelector` via a getter), score each candidate as `(priorityIndex(entity) * 1_000_000) + distanceSq`, pick the minimum, `this.targetEntity = best`; `startExecuting` calls `this.taskOwner.setAttackTarget(...)`. `priorityIndex`: index of first matching prefix of `EntityList.getKey(entity)`, else `prefixes.length` if `entity.isEntityUndead()`, else `prefixes.length + 1`. Replace the existing `EntityAINearestAttackableTarget` registration (targetTasks priority 2) with this task. Model the boilerplate (`shouldContinueExecuting`, mutex) on how `EntityAINearestAttackableTarget` uses `EntityAITarget` superclass helpers.

- [ ] **Step 5: GUI + packet wiring**

`PacketSentinelCommand`: add `ACTION_STANCE_TOGGLE = 4`, `ACTION_RADIUS_UP = 5`, `ACTION_RADIUS_DOWN = 6`; handler cases flip `setAggressiveStance(!isAggressiveStance())` and `setGuardRadius(getGuardRadius() ± 4)`. `GuiSentinelControl`: stance toggle button (label reflects live state: `I18n.format("gui.insanetweaks.sentinel.stance." + (aggressive ? "aggressive" : "defensive"))`) and radius stepper `[-] 20 [+]` (disabled outside GUARD mode). Lang (en/ru): `stance.aggressive=Stance: Aggressive`, `stance.defensive=Stance: Defensive`, `radius=Guard radius: %d` (+ Russian equivalents).

- [ ] **Step 6: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java src/main/resources
git commit -m "feat: sentinel stance, target priorities and editable guard radius (F2)"
```

### Task 5 (F3): Pickup filter, deposit toggle, regen config

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntitySentinel.java`
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/EntitiesCategory.java` (Sentinel class)
- Modify: `src/main/java/com/spege/insanetweaks/network/PacketSentinelCommand.java`
- Modify: `src/main/java/com/spege/insanetweaks/client/gui/GuiSentinelControl.java`

- [ ] **Step 1: Two more DataParameters**

`COLLECT_ALL` (Boolean, default true) and `AUTO_DEPOSIT` (Boolean, default true), registered/persisted like Task 4 Step 1 (tags `SentinelCollectAll`, `SentinelAutoDeposit`), with getters/setters `isCollectAll`/`setCollectAll`/`isAutoDeposit`/`setAutoDeposit`.

- [ ] **Step 2: Filter in tickLootCollection**

In `tickLootCollection` (line ~379), before `storeLootStack`, add:

```java
            if (!this.isCollectAll() && !isValuable(stack)) {
                continue;
            }
```

with:

```java
    private static boolean isValuable(ItemStack stack) {
        if (stack.getItem().getRegistryName() == null) return false;
        String path = stack.getItem().getRegistryName().getResourcePath();
        for (String keyword : ModConfig.entities.sentinel.valuableKeywords) {
            if (keyword != null && !keyword.isEmpty() && path.contains(keyword)) return true;
        }
        return false;
    }
```

Config (append to the `Sentinel` inner class):

```java
        @Config.Comment({ "Registry-path keywords that count as 'valuable' for the pickup filter",
                "(used when a Sentinel's 'collect everything' toggle is OFF)." })
        @Config.Name("Sentinel: Valuable Keywords")
        public String[] valuableKeywords = { "ore", "ingot", "gem", "diamond", "emerald",
                "gold", "crystal", "dust", "wand", "scroll", "book", "pearl" };
```

- [ ] **Step 3: Deposit toggle + regen config**

Gate `tickGuardChestDeposit()` (line ~443) with `if (!this.isAutoDeposit()) return;` as the first line. For regen, add to the `Sentinel` config class:

```java
        @Config.Comment("Out-of-combat self-heal amount per pulse (0 disables regeneration).")
        @Config.Name("Sentinel: Regen Amount")
        @Config.RangeDouble(min = 0.0, max = 20.0)
        public double regenAmount = 3.75;
```

and in `tickPassiveSelfHeal` (line ~360) replace the hardcoded `3.75F` base with `(float) ModConfig.entities.sentinel.regenAmount` (keep the ×2 healing-element bonus by computing `regenAmount * 2` for `Element.HEALING`), and add an early `if (ModConfig.entities.sentinel.regenAmount <= 0) return;`. Additionally skip regeneration while in combat: `if (this.getAttackTarget() != null) return;`.

- [ ] **Step 4: Packet + GUI toggles**

`PacketSentinelCommand`: `ACTION_TOGGLE_DEPOSIT = 7`, `ACTION_TOGGLE_PICKUP_FILTER = 8` with handler cases flipping the DataParameters. GUI: two toggle buttons in the left column, labels reflecting live state (`gui.insanetweaks.sentinel.deposit.on/off`, `gui.insanetweaks.sentinel.pickup.all/valuables` — en+ru lang entries).

- [ ] **Step 5: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java src/main/resources
git commit -m "feat: sentinel pickup filter, deposit toggle, regen config (F3)"
```

### Task 6: Manual verification (runClient)

- [ ] Combat: `/summon insanetweaks:sentinel`, aggro a zombie 10 blocks away → sentinel casts within a few seconds and varies spells (debug logs confirm ≥4 distinct over minutes); zombie closes in → clean switch to sword swings; shield still raises (ASC block task).
- [ ] GUI: right-click sentinel → ONE screen with buttons + live loot grid; shift-click items out into player inventory; screen survives without desync after the sentinel picks up more loot while open.
- [ ] Stance: Defensive → sentinel ignores a nearby zombie until the zombie hits it or the owner; Aggressive → proactive attack resumes.
- [ ] Priorities: parasite + zombie both in range in Aggressive stance → parasite targeted first.
- [ ] Radius: set GUARD, step radius down to 8 → sentinel abandons chases beyond ~8 blocks from anchor; patrol adapts.
- [ ] Pickup filter: valuables-only ON → cobblestone stays on the ground, diamonds are collected. Deposit toggle OFF → loot stays in the sentinel in GUARD mode.
- [ ] Old worlds: a sentinel saved before this change loads with default stance/radius/toggles and no NBT errors.
