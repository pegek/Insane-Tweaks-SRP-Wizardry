# Thrall Porter Rework + Husbandry Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Porter becomes a pure chest-sorter anchored at HOME; a new HUSBANDRY mode breeds, shears and culls farm animals around HOME.

**Architecture:** `ThrallAIPorter` is cut down to deposit+consolidate (the teleport/pull/restock machinery and its config options are deleted). HUSBANDRY is a new `ThrallMode` enum value (APPENDED — ordinals are stored in NBT/DataManager) with a job-queue AI task (`ThrallAIHusbandry`): each cycle it builds a queue of shear/cull/breed jobs, walks to each target, acts, then deposits.

**Tech Stack:** Forge 1.12.2, Java 8. No test suite — verification is `./gradlew build` + the manual runClient checklist at the end.

**Spec:** `docs/superpowers/specs/2026-07-10-thrall-porter-husbandry-design.md`

**Invariant note (user-approved exception):** the thrall "never aggressive" invariant now allows culling farm animals in HUSBANDRY mode via direct `attackEntityFrom` — `setAttackTarget` stays a no-op, no combat AI is added. Never cull babies, in-love animals, or owned tameables.

---

### Task 1: Porter clean cut

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIPorter.java` (full rewrite)
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/ThrallCategory.java`

- [ ] **Step 1: Rewrite ThrallAIPorter**

Replace the ENTIRE file `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIPorter.java` with the version below. It keeps (verbatim from the current file): `consolidateChests`, `countStacksBySig`, `Designation`, `transferBetweenChests`, and `Sig`. It deletes: teleports, manifest build, pull-from-owner, `runReverseCycle`, `buildRestockNeeds`, `pullRestockFromChests`, `topUpOwner`, `drainFromBag`, `ChestBudgetPool`, `ChestBudget`, `containsSignature`, `ownerHasManifestItem`.

```java
package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallChestHelper;
import com.spege.insanetweaks.entities.ThrallMode;
import com.spege.insanetweaks.entities.inventory.ThrallInventory;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI task: Porter mode (chest sorter). REWORK 2026-07-10: the porter no longer ferries
 * items to/from the owner — it is a pure depot keeper anchored at HOME. Every
 * {@code porterIntervalSeconds} it:
 *   1. Deposits anything in its own bag into home chests (smartDeposit).
 *   2. Consolidates chest contents: each item type migrates into the chest where that
 *      type already has the most stacks (bounded per cycle).
 */
@SuppressWarnings("null")
public class ThrallAIPorter extends EntityAIBase {

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallPorter");

    /** Vertical range (blocks) for the chest scan; horizontal range comes from config. */
    private static final int SCAN_VRANGE = 4;

    /** Cap on chest-to-chest sort moves per cycle. Sorting is bounded so a messy depot
     *  takes a few cycles to settle rather than locking up a single tick. */
    private static final int MAX_SORT_TRANSFERS_PER_CYCLE = 8;

    private final EntityThrallMinion thrall;
    private long lastCycleTick;

    public ThrallAIPorter(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(3);
    }

    private static boolean debugLogs() {
        return ModConfig.client.enableThrallDebugLogs;
    }

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() != ThrallMode.PORTER) return false;
        if (!ModConfig.thrall.porter.enablePorterMode) return false;
        return thrall.getHomePoint() != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        lastCycleTick = 0;
        thrall.getNavigator().clearPath();
        thrall.setStatusText("Standing by...");
    }

    @Override
    public void resetTask() {
        thrall.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        long now = thrall.world.getTotalWorldTime();
        long intervalTicks = (long) ModConfig.thrall.porter.porterIntervalSeconds * 20L;
        if (lastCycleTick != 0 && now - lastCycleTick < intervalTicks) {
            return;
        }
        lastCycleTick = now;

        runCycle();
    }

    private void runCycle() {
        BlockPos home = thrall.getHomePoint();
        if (home == null) return;

        int range = ModConfig.thrall.porter.porterChestScanRange;

        // Drop anything we carry into home chests first.
        ThrallInventory inv = thrall.getThrallInventory();
        if (inv.containsItems()) {
            ThrallChestHelper.smartDeposit(thrall, home, range, SCAN_VRANGE, false);
        }

        int sorted = consolidateChests(home, range);
        if (sorted > 0) {
            thrall.setStatusText("Sorting...");
            if (debugLogs()) LOG.info("[Thrall#{}] Porter consolidated {} stacks across chests",
                    thrall.getEntityId(), sorted);
        } else {
            thrall.setStatusText(inv.isFull() ? "Full" : "Standing by...");
        }
    }

    // ------------------------------------------------------------------
    // Chest sorting  (unchanged logic from the pre-rework porter)
    // ------------------------------------------------------------------
```

…then paste, UNCHANGED from the current file, the bodies of `consolidateChests(BlockPos home, int range)`, `countStacksBySig(IInventory chest)`, the `Designation` class, `transferBetweenChests(IInventory src, int srcSlot, IInventory dst)` and the `Sig` class (current lines 509-722), and close the class. Do not modify their code.

- [ ] **Step 2: Trim the Porter config**

In `ThrallCategory.java`:
1. DELETE the enum `PorterDirection` (line ~28) — after Step 1 nothing references it.
2. In `Porter` inner class DELETE the fields `porterDirection`, `porterTeleportRange`, `enablePorterSorting` (with their annotations).
3. Replace the `Porter` class-level comment and the `enablePorterMode` comment so they describe the new behaviour:

```java
    public static class Porter {
        @Config.Comment({ "Master toggle for the Porter work mode. If false, Thralls cannot enter PORTER mode.",
                "REWORK 2026-07-10: the Porter is a pure chest sorter anchored at the home point.",
                "Each cycle it deposits its own bag into home chests, then consolidates chest contents",
                "(each item type migrates into the chest where it already has the most stacks)." })
        @Config.Name("Enable Porter Mode")
        public boolean enablePorterMode = true;
```

Keep `porterIntervalSeconds` and `porterChestScanRange` unchanged. Also update the `@Config.Comment` on the `porter` field at the top of `ThrallCategory` (line ~16) to: `"PORTER mode: chest sorting anchored at the home point."`

- [ ] **Step 3: Fix any leftover references**

Run: `grep -rn "porterDirection\|porterTeleportRange\|enablePorterSorting\|PorterDirection" src/main/java`
Expected: no matches. If `commands/CommandInsaneTweaks.java` or GUI tooltips reference them, delete those references.

Also check the porter GUI tooltip lang keys: `grep -n "porter" src/main/resources/assets/insanetweaks/lang/en_us.lang src/main/resources/assets/insanetweaks/lang/ru_ru.lang` — if a description mentions ferrying items to/from the owner, reword it to "Sorts the chests around its home point." (en) / "Сортирует сундуки вокруг своей точки дома." (ru).

- [ ] **Step 4: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java src/main/resources
git commit -m "feat!: Porter mode reworked to pure chest sorting at HOME"
```

### Task 2: HUSBANDRY mode enum + config

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/ThrallMode.java`
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/ThrallCategory.java`

- [ ] **Step 1: Append the enum value (NEVER reorder — ordinals live in NBT)**

In `ThrallMode.java` change the constant list to:

```java
    FOLLOW("follow"),
    STAY("stay"),
    WOODCUTTING("woodcutting"),
    MINESHAFT("mineshaft"),
    FARMING("farming"),
    PORTER("porter"),
    COLLECTING("collecting"),
    HUSBANDRY("husbandry");
```

- [ ] **Step 2: Config subcategory**

In `ThrallCategory`, after the `porter` field declaration add:

```java
    @Config.Name("Husbandry")
    @Config.Comment("HUSBANDRY mode: breed, shear and cull farm animals around the home point.")
    public final Husbandry husbandry = new Husbandry();
```

and after the `Porter` inner class add:

```java
    public static class Husbandry {
        @Config.Comment({ "Master toggle for the Husbandry work mode. If false, Thralls cannot enter HUSBANDRY mode.",
                "The thrall works animals within the radius around its home point: shears shearable animals",
                "(needs shears in a home chest), culls adults above the population cap (drops are collected),",
                "and breeds pairs below the cap (needs matching feed in a home chest)." })
        @Config.Name("Enable Husbandry Mode")
        public boolean enableHusbandryMode = true;

        @Config.Comment("Working radius (blocks) around the home point.")
        @Config.Name("Husbandry: Radius")
        @Config.RangeInt(min = 4, max = 48)
        public int husbandryRadius = 16;

        @Config.Comment("Seconds between husbandry work cycles.")
        @Config.Name("Husbandry: Cycle Interval (seconds)")
        @Config.RangeInt(min = 5, max = 600)
        public int husbandryIntervalSeconds = 30;

        @Config.Comment({ "Maximum ADULT animals per species within the radius. Excess adults are culled;",
                "breeding is skipped at/above the cap. Babies, in-love animals and owned pets never count as excess." })
        @Config.Name("Husbandry: Population Cap")
        @Config.RangeInt(min = 2, max = 64)
        public int husbandryPopulationCap = 8;
    }
```

- [ ] **Step 3: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add src/main/java/com/spege/insanetweaks/entities/ThrallMode.java src/main/java/com/spege/insanetweaks/config/categories/ThrallCategory.java
git commit -m "feat: HUSBANDRY thrall mode enum + config subcategory"
```

### Task 3: ThrallAIHusbandry task

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIHusbandry.java`

- [ ] **Step 1: Create the AI task**

```java
package com.spege.insanetweaks.entities.ai;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.entities.ThrallChestHelper;
import com.spege.insanetweaks.entities.ThrallMode;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.IShearable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI task: HUSBANDRY mode (spec 2026-07-10). Anchored at HOME. Each cycle builds a job
 * queue over farm animals in the working radius, walks to each target and acts:
 *   1. SHEAR every ready IShearable adult (requires shears in a home chest; durability is consumed).
 *   2. CULL adults above the per-species population cap (direct damage — NO combat AI;
 *      this is the single user-approved exception to the "never aggressive" invariant).
 *   3. BREED pairs below the cap (requires 2 matching feed items in a home chest per pair).
 * Then deposits its bag into home chests. Never touches babies, in-love animals or owned pets.
 */
@SuppressWarnings("null")
public class ThrallAIHusbandry extends EntityAIBase {

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallHusbandry");

    private static final int CHEST_VRANGE = 4;
    private static final double ACT_RANGE_SQ = 2.5D * 2.5D;
    private static final double WALK_SPEED = 0.7D;
    private static final int JOB_TIMEOUT_TICKS = 200;
    private static final int MAX_JOBS_PER_CYCLE = 8;
    private static final float CULL_DAMAGE = 1000.0F;

    private enum JobType { SHEAR, CULL, BREED }

    private static final class Job {
        final JobType type;
        final EntityAnimal target;
        /** Second animal of a BREED pair; null otherwise. */
        @Nullable final EntityAnimal partner;

        Job(JobType type, EntityAnimal target, @Nullable EntityAnimal partner) {
            this.type = type;
            this.target = target;
            this.partner = partner;
        }
    }

    private final EntityThrallMinion thrall;
    private final Deque<Job> jobs = new ArrayDeque<Job>();
    private long nextCycleTime;
    private int jobTicks;

    public ThrallAIHusbandry(EntityThrallMinion thrall) {
        this.thrall = thrall;
        this.setMutexBits(3);
    }

    private static boolean debugLogs() {
        return ModConfig.client.enableThrallDebugLogs;
    }

    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() != ThrallMode.HUSBANDRY) return false;
        if (!ModConfig.thrall.husbandry.enableHusbandryMode) return false;
        return thrall.getHomePoint() != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        jobs.clear();
        nextCycleTime = 0;
        thrall.getNavigator().clearPath();
        thrall.setStatusText("Tending...");
    }

    @Override
    public void resetTask() {
        jobs.clear();
        thrall.getNavigator().clearPath();
    }

    @Override
    public void updateTask() {
        BlockPos home = thrall.getHomePoint();
        if (home == null) return;

        if (jobs.isEmpty()) {
            long now = thrall.world.getTotalWorldTime();
            if (now < nextCycleTime) return;
            nextCycleTime = now + (long) ModConfig.thrall.husbandry.husbandryIntervalSeconds * 20L;
            buildJobQueue(home);
            depositBag(home);
            if (jobs.isEmpty()) {
                thrall.setStatusText("Standing by...");
            }
            return;
        }

        Job job = jobs.peekFirst();
        if (!isJobStillValid(job)) {
            jobs.pollFirst();
            jobTicks = 0;
            return;
        }

        jobTicks++;
        if (jobTicks > JOB_TIMEOUT_TICKS) {
            if (debugLogs()) LOG.info("[Thrall#{}] Husbandry job {} timed out", thrall.getEntityId(), job.type);
            jobs.pollFirst();
            jobTicks = 0;
            return;
        }

        if (thrall.getDistanceSq(job.target) > ACT_RANGE_SQ) {
            thrall.getNavigator().tryMoveToEntityLiving(job.target, WALK_SPEED);
            thrall.getLookHelper().setLookPositionWithEntity(job.target, 30.0F, 30.0F);
            return;
        }

        thrall.getNavigator().clearPath();
        performJob(job, thrall.getHomePoint());
        jobs.pollFirst();
        jobTicks = 0;

        if (jobs.isEmpty()) {
            depositBag(thrall.getHomePoint());
            thrall.setStatusText("Standing by...");
        }
    }

    // ------------------------------------------------------------------
    // Queue building
    // ------------------------------------------------------------------

    private void buildJobQueue(BlockPos home) {
        int radius = ModConfig.thrall.husbandry.husbandryRadius;
        int cap = ModConfig.thrall.husbandry.husbandryPopulationCap;

        AxisAlignedBB box = new AxisAlignedBB(home).grow(radius, 8, radius);
        List<EntityAnimal> animals = thrall.world.getEntitiesWithinAABB(EntityAnimal.class, box,
                a -> a != null && a.isEntityAlive() && !isOwnedPet(a));

        // Group ADULTS by species for cap/breeding logic.
        Map<Class<?>, List<EntityAnimal>> adultsBySpecies = new HashMap<Class<?>, List<EntityAnimal>>();
        for (EntityAnimal a : animals) {
            if (a.isChild()) continue;
            List<EntityAnimal> list = adultsBySpecies.get(a.getClass());
            if (list == null) {
                list = new ArrayList<EntityAnimal>();
                adultsBySpecies.put(a.getClass(), list);
            }
            list.add(a);
        }

        int queued = 0;

        // 1. SHEAR — every ready shearable adult (independent of the cap).
        for (EntityAnimal a : animals) {
            if (queued >= MAX_JOBS_PER_CYCLE) break;
            if (a.isChild() || !(a instanceof IShearable)) continue;
            ItemStack probe = new ItemStack(Items.SHEARS);
            if (((IShearable) a).isShearable(probe, thrall.world, new BlockPos(a))) {
                jobs.addLast(new Job(JobType.SHEAR, a, null));
                queued++;
            }
        }

        // 2. CULL — excess adults per species (never in-love; babies/pets excluded above).
        for (Map.Entry<Class<?>, List<EntityAnimal>> e : adultsBySpecies.entrySet()) {
            int excess = e.getValue().size() - cap;
            for (EntityAnimal a : e.getValue()) {
                if (queued >= MAX_JOBS_PER_CYCLE || excess <= 0) break;
                if (a.isInLove()) continue;
                jobs.addLast(new Job(JobType.CULL, a, null));
                queued++;
                excess--;
            }
        }

        // 3. BREED — pair up eligible adults while below the cap.
        for (Map.Entry<Class<?>, List<EntityAnimal>> e : adultsBySpecies.entrySet()) {
            if (queued >= MAX_JOBS_PER_CYCLE) break;
            List<EntityAnimal> eligible = new ArrayList<EntityAnimal>();
            for (EntityAnimal a : e.getValue()) {
                if (a.getGrowingAge() == 0 && !a.isInLove()) eligible.add(a);
            }
            int room = cap - e.getValue().size();
            for (int i = 0; i + 1 < eligible.size() && room > 0 && queued < MAX_JOBS_PER_CYCLE; i += 2) {
                jobs.addLast(new Job(JobType.BREED, eligible.get(i), eligible.get(i + 1)));
                queued++;
                room--;
            }
        }

        if (debugLogs() && !jobs.isEmpty()) {
            LOG.info("[Thrall#{}] Husbandry queued {} jobs", thrall.getEntityId(), jobs.size());
        }
    }

    private static boolean isOwnedPet(EntityAnimal a) {
        return a instanceof EntityTameable && ((EntityTameable) a).getOwnerId() != null;
    }

    private boolean isJobStillValid(Job job) {
        if (job.target == null || !job.target.isEntityAlive()) return false;
        if (job.type == JobType.BREED) {
            return job.partner != null && job.partner.isEntityAlive()
                    && job.target.getGrowingAge() == 0 && !job.target.isInLove()
                    && job.partner.getGrowingAge() == 0 && !job.partner.isInLove();
        }
        if (job.type == JobType.CULL) {
            return !job.target.isChild() && !job.target.isInLove();
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Job execution
    // ------------------------------------------------------------------

    private void performJob(Job job, BlockPos home) {
        switch (job.type) {
            case SHEAR: performShear(job.target, home); break;
            case CULL:  performCull(job.target);        break;
            case BREED: performBreed(job, home);        break;
        }
    }

    private void performShear(EntityAnimal animal, BlockPos home) {
        ChestSlotRef shears = findChestStack(home, s -> s.getItem() == Items.SHEARS);
        if (shears == null) {
            thrall.setStatusText("No shears");
            return;
        }
        IShearable shearable = (IShearable) animal;
        BlockPos pos = new BlockPos(animal);
        if (!shearable.isShearable(shears.stack, thrall.world, pos)) return;

        List<ItemStack> drops = shearable.onSheared(shears.stack, thrall.world, pos, 0);
        for (ItemStack drop : drops) {
            ItemStack working = drop.copy();
            thrall.getThrallInventory().addItemStackToInventory(working);
            if (!working.isEmpty()) {
                animal.entityDropItem(working, 0.5F);
            }
        }
        shears.stack.damageItem(1, thrall);
        if (shears.stack.isEmpty()) {
            shears.chest.setInventorySlotContents(shears.slot, ItemStack.EMPTY);
        }
        shears.chest.markDirty();
        thrall.setStatusText("Shearing...");
    }

    private void performCull(EntityAnimal animal) {
        thrall.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        animal.attackEntityFrom(DamageSource.causeMobDamage(thrall), CULL_DAMAGE);
        thrall.setStatusText("Culling...");
        // Drops land inside the thrall's passive pickup range (it is standing next to
        // the animal) and are banked during the end-of-queue deposit.
    }

    private void performBreed(Job job, BlockPos home) {
        ChestSlotRef feedA = findChestStack(home, s -> job.target.isBreedingItem(s));
        if (feedA == null || feedA.stack.getCount() < 2) {
            thrall.setStatusText("No feed");
            return;
        }
        job.target.setInLove(null);
        job.partner.setInLove(null);
        feedA.stack.shrink(2);
        if (feedA.stack.isEmpty()) {
            feedA.chest.setInventorySlotContents(feedA.slot, ItemStack.EMPTY);
        }
        feedA.chest.markDirty();
        thrall.setStatusText("Breeding...");
    }

    private void depositBag(BlockPos home) {
        if (thrall.getThrallInventory().containsItems()) {
            ThrallChestHelper.smartDeposit(thrall, home,
                    ModConfig.thrall.porter.porterChestScanRange, CHEST_VRANGE, false);
        }
    }

    // ------------------------------------------------------------------
    // Chest access
    // ------------------------------------------------------------------

    private interface StackPredicate { boolean test(ItemStack stack); }

    private static final class ChestSlotRef {
        final IInventory chest;
        final int slot;
        final ItemStack stack;
        ChestSlotRef(IInventory chest, int slot, ItemStack stack) {
            this.chest = chest; this.slot = slot; this.stack = stack;
        }
    }

    @Nullable
    private ChestSlotRef findChestStack(BlockPos home, StackPredicate predicate) {
        List<IInventory> chests = ThrallChestHelper.findNearbyInventories(thrall.world, home,
                ModConfig.thrall.porter.porterChestScanRange, CHEST_VRANGE);
        for (IInventory chest : chests) {
            for (int i = 0; i < chest.getSizeInventory(); i++) {
                ItemStack s = chest.getStackInSlot(i);
                if (!s.isEmpty() && predicate.test(s)) {
                    return new ChestSlotRef(chest, i, s);
                }
            }
        }
        return null;
    }
}
```

Adaptation notes for the implementer (verify against the actual codebase, adjust if signatures differ):
- `thrall.world.getEntitiesWithinAABB(Class, AABB, Predicate)` takes a `com.google.common.base.Predicate` in 1.12 — if the lambda does not compile, replace with an anonymous `com.google.common.base.Predicate<EntityAnimal>`.
- The private `StackPredicate` interface exists because Java 8 + guava mixing is awkward; keep it.
- `ThrallInventory.addItemStackToInventory(ItemStack)` mutates its argument (see usage in the old porter) — the shear code relies on that.

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (the task is not yet wired — that is Task 4)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIHusbandry.java
git commit -m "feat: ThrallAIHusbandry job-queue task (shear/cull/breed)"
```

### Task 4: Wire HUSBANDRY into entity, packet and GUI

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntityThrallMinion.java`
- Modify: `src/main/java/com/spege/insanetweaks/network/PacketThrallCommand.java`
- Modify: `src/main/java/com/spege/insanetweaks/client/gui/GuiThrallControl.java`
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`, `ru_ru.lang`

- [ ] **Step 1: Register the AI task**

In `EntityThrallMinion.initEntityAI()` (line ~171), after `this.tasks.addTask(1, new ThrallAIPorter(this));` add:

```java
        this.tasks.addTask(1, new ThrallAIHusbandry(this));
```

- [ ] **Step 2: Work-timer inclusion**

In `EntityThrallMinion.setMode(ThrallMode mode)` (line ~641), extend the work-mode condition to include HUSBANDRY:

```java
        if (mode == ThrallMode.WOODCUTTING || mode == ThrallMode.MINESHAFT
                || mode == ThrallMode.FARMING || mode == ThrallMode.PORTER
                || mode == ThrallMode.COLLECTING || mode == ThrallMode.HUSBANDRY) {
```

- [ ] **Step 3: Packet action**

In `PacketThrallCommand`, add the constant after `ACTION_COLLECTING`:

```java
    public static final int ACTION_HUSBANDRY   = 12;
```

and in `Handler.handle`, add a case (next to `ACTION_PORTER`):

```java
                case ACTION_HUSBANDRY:
                    if (!com.spege.insanetweaks.config.ModConfig.thrall.husbandry.enableHusbandryMode) {
                        player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.disabled"), true);
                        break;
                    }
                    thrall.setMode(ThrallMode.HUSBANDRY);
                    thrall.setStatusText("Tending...");
                    thrall.playSoundOrder();
                    player.sendStatusMessage(new TextComponentTranslation("gui.insanetweaks.thrall.mode.husbandry"), true);
                    break;
```

- [ ] **Step 4: GUI button**

In `GuiThrallControl.initGui()`, after the COLLECTING button (id 11, left column row 6), add an 8th left-column row:

```java
        this.buttonList.add(new GuiButton(12, leftX, startY + 7 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.husbandry")));
```

In `actionPerformed`, add to the switch:

```java
            case 12: action = PacketThrallCommand.ACTION_HUSBANDRY;  break;
```

Check `drawScreen`: the bottom info strings are drawn at `cy + 130` / `cy + 142`; the new 8th row ends at `startY + 8*23 - 3 = cy - 100 + 181 = cy + 81` — no overlap, no layout change needed.

If the GUI has a hover-tooltip map keyed by button id (see the code after line 120 of `GuiThrallControl`), add an entry for id 12 pointing at `gui.insanetweaks.thrall.tooltip.husbandry`.

- [ ] **Step 5: Lang entries**

`en_us.lang` (next to the other `gui.insanetweaks.thrall.*` keys):

```
gui.insanetweaks.thrall.action.husbandry=Husbandry
gui.insanetweaks.thrall.mode.husbandry=Thrall: tending the animals
gui.insanetweaks.thrall.tooltip.husbandry=Breeds, shears and culls farm animals around the home point. Needs feed and shears in home chests; keeps each species at the configured population cap.
```

`ru_ru.lang`:

```
gui.insanetweaks.thrall.action.husbandry=Животноводство
gui.insanetweaks.thrall.mode.husbandry=Трэлл: ухаживает за животными
gui.insanetweaks.thrall.tooltip.husbandry=Разводит, стрижёт и забивает животных вокруг точки дома. Нужны корм и ножницы в сундуках; поддерживает лимит популяции каждого вида.
```

(Match the exact tooltip-key naming convention used by the existing buttons — check with `grep -n "thrall.tooltip" src/main/resources/assets/insanetweaks/lang/en_us.lang` and follow it.)

- [ ] **Step 6: Build + commit**

Run: `./gradlew build` — expected `BUILD SUCCESSFUL`, then:

```bash
git add -A src/main/java src/main/resources
git commit -m "feat: HUSBANDRY mode wired into thrall AI, packet, GUI and lang"
```

### Task 5: Manual verification (runClient)

- [ ] Porter: place 3 chests with mixed item types near HOME, set PORTER → over a few cycles types consolidate into their dominant chest; no teleporting to the player ever happens; thrall bag empties into chests.
- [ ] Porter config: `porterDirection` / `porterTeleportRange` / `enablePorterSorting` no longer appear in a regenerated `insanetweaks.cfg` (delete the thrall section and restart to confirm).
- [ ] Husbandry breeding: wheat (≥2) in a home chest + 2 adult cows → thrall walks to them, hearts appear, calf spawns; empty chest → status "No feed", no breeding.
- [ ] Husbandry cap: spawn 10 adult cows with cap 8 → exactly 2 culled, beef/leather end up in home chests; babies and in-love animals untouched; a tamed wolf nearby is ignored.
- [ ] Shearing: shears in chest + unsheared sheep → wool in chests, shears durability visibly drops; no shears → status "No shears".
- [ ] Legacy worlds: a thrall saved in PORTER mode before this change still loads in PORTER mode (ordinal unchanged).
