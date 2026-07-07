# Thrall Fixes & Mob-Ignore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the hard Collecting-mode bugs (auto-return hijack, no near-home pickup, no session looping, NBT restore stall, GatherItems swallowing staged items), guarantee that ALL mobs ignore the immortal thrall (invariant B), add Farming home-fallback scanning, add Porter hotbar-exclusion + a reverse `FROM_HOME` restock direction, and overhaul the control GUI into two columns with hover tooltips.

**Architecture:** Changes are confined to the thrall subsystem. Collecting fixes touch `EntityThrallMinion.onUpdate` (auto-return gate + work-timer mode list) and `ThrallAICollecting` (local scan, session loop, WAITING restore guard, GatherItems interlock). Mob-ignore adds one new server-side `LivingSetAttackTargetEvent` handler (`ThrallTargetProtectionHandler`) registered under `modules.enableSpells` (the same flag that already gates the thrall's spell-summoned companion handlers), plus a startup append to SRP's public `SRPConfig.mobattackingBlackList` as a second layer. Farming and Porter edits stay inside their own AI classes plus two new `config/ModConfig.java` fields. The GUI change is layout + tooltip-only — button ids and packet action ids are untouched.

**Tech Stack:** Java 8, Minecraft 1.12.2 Forge `1.12.5.2860`, EBW 4.3.19 (CurseMaven dev jar), SRParasites 1.10.7 (`libs/`), ForgeGradle 3. No test suite, no lint task.

**Spec:** `docs/superpowers/specs/2026-07-07-thrall-fixes-design.md`

**Testing note:** No automated test suite exists (per CLAUDE.md). Per-task verification = `./gradlew build` green. In-game testing is deferred by the user; the manual checklist is recorded in `NEXT_SESSION_SPELLS.md` at the final task.

---

## Key API facts (verified against sources, do not re-derive)

- **Thrall registry name:** `insanetweaks:thrall_minion`. Registered in `InsaneTweaksMod.init` via
  `EntityRegistry.registerModEntity(new ResourceLocation(MODID, "thrall_minion"), EntityThrallMinion.class, "thrall_minion", 114, this, 64, 3, true)` (tracking id **114** — network-stable, do NOT change).
- **SRP blacklist injection: INJECTABLE.** `com.dhanantry.scapeandrunparasites.util.config.SRPConfig.mobattackingBlackList` is a **`public static String[]`** (default `{"srmonstress", "minecraft:creeper", "minecraft:bat"}`). SRP's targeting selectors call `ParasiteEventEntity.checkEntity(entity, SRPConfig.mobattackingBlackList, SRPConfig.mobattackingBlackListWhite)`, which resolves the entity's registry name via `EntityList.func_191301_a(entity).toString()` (e.g. `"insanetweaks:thrall_minion"`) and then `checkName` does `Arrays.stream(blacklist).anyMatch(potentialElement::contains)` — a **substring `contains` match**. Appending the exact registry name `"insanetweaks:thrall_minion"` to the array at startup makes every SRP mob whose selector routes through `checkEntity` skip the thrall.
  - **Format of entries:** registry names (`"minecraft:zombie"`) or a bare mod-id substring (`"minecraft"`). We append the full `"insanetweaks:thrall_minion"`.
  - **Reachability caveat (recorded, not blocking):** the field is re-parsed from the config file by SRP's `syncConfig`, so a mid-game config reload would drop the injected entry. This is why the append is a *second layer* only; the `LivingSetAttackTargetEvent` handler is the always-on primary guarantee. The append is done once at `FMLLoadCompleteEvent` (after SRP's config load in its own preInit/loadComplete) via reflection-free direct field assignment, guarded by `Loader.isModLoaded("scapeandrunparasites")`.
- **`LivingSetAttackTargetEvent`** (`net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent`) fires whenever any `EntityLiving` sets an attack target. `event.getEntityLiving()` is the aggressor; `event.getTarget()` is the newly-set target. The event is NOT cancelable, so the handler must actively clear the aggressor's target (`setAttackTarget(null)` + `setRevengeTarget(null)`) when the target is a thrall.
- **`EntityThrallMinion` immortality (do NOT touch):** `attackEntityFrom` → `false`, `isEntityInvulnerable` → `true`, `setAttackTarget` → no-op, `canDespawn` → `false`. Invariant A holds; no changes.
- **Work-timer mode list** in `EntityThrallMinion.onUpdate` currently = `{WOODCUTTING, MINESHAFT, FARMING, PORTER}` (and the identical set in `setMode` starts the timer). COLLECTING is absent from both.
- **Auto-return gate** in `EntityThrallMinion.onUpdate` currently excludes only `PORTER`: `if (thrallInventory.isFull() && !returningHome && getHomePoint() != null && getMode() != ThrallMode.PORTER)`.
- **`ThrallAICollecting` phases:** `WAITING_FOR_ITEMS → SEARCHING → HARVESTING → RETURNING → DONE`. `finishDone()` currently clears targets and calls `thrall.setMode(ThrallMode.STAY)`. `startOrResume()` decides resume-vs-fresh. `pickRandomSearchPoint` uses `collectingMinTpDistance`/`collectingMaxTpDistance` for the ring — no local scan today.
- **`ThrallAIGatherItems.shouldExecute`** returns false only on STAY-mode or full-inventory; it has no COLLECTING-WAITING interlock. The thrall reference is `this.thrall`; the collecting AI is reachable via a new accessor (there is no public getter today — add one).
- **`ThrallChestHelper` signatures:** `findNearbyInventories(World, BlockPos center, int hRange, int vRange) → List<IInventory>`; `smartDeposit(EntityThrallMinion, BlockPos center, int hRange, int vRange, boolean keepTorches) → boolean`; `grabItemFromChests(EntityThrallMinion, BlockPos center, Item item, int needed) → int (remaining)`.
- **Porter slot ranges:** `PLAYER_MAIN_INV_START = 9`, `PLAYER_MAIN_INV_END = 35` (hotbar 0–8 already skipped in `pullFromOwner`). Spec 4.1 wording asks us to make the hotbar-skip explicit/hardened; the existing loop already starts at 9, so 4.1 is verified-present but the plan hardens it with a defensive comment + a guard so a future edit can't regress it.
- **`ModConfig` style:** Forge `@Config` POJO. Ints use `@Config.Comment({...})` + `@Config.Name("...")` + `@Config.RangeInt(min=, max=)`. There is **no existing enum field**; Forge `@Config` supports a plain `public MyEnum field = MyEnum.DEFAULT;` (stored/edited by enum constant name), so `porterDirection` is declared that way with a `@Config.Comment` listing the two valid values.
- **GUI button ids (do NOT change):** 0 Follow, 1 Stay, 2 Woodcutting, 3 Mineshaft, 8 Farming, 9 Porter, 11 Collecting, 4 Set Home, 10 Return Home, 6 Inventory, 7 Dismiss. `PacketThrallCommand` action ids are also fixed (ACTION_* 0..11).
- **`PacketThrallCommand`** is NOT modified — no new actions; the GUI keeps sending the same discriminators.

---

## File map

| File | Action | Responsibility |
|---|---|---|
| `src/main/java/com/spege/insanetweaks/entities/EntityThrallMinion.java` | Modify | Exclude COLLECTING from auto-return; add COLLECTING to work-timer mode list; add `getCollectingAI()` accessor for GatherItems interlock |
| `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAICollecting.java` | Modify | Local scan before ring search (C-2a); session looping with rest-at-home (C-3); timer-expiry → WAITING_FOR_ITEMS not STAY; NBT restore DONE→WAITING guard (C-4) |
| `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIGatherItems.java` | Modify | `shouldExecute` returns false while collecting AI is WAITING_FOR_ITEMS (X-2) |
| `src/main/java/com/spege/insanetweaks/config/ModConfig.java` | Modify | `collectingMinTpDistance` default 30→8 (C-2b); new `porterDirection` enum field (4.2) |
| `src/main/java/com/spege/insanetweaks/events/ThrallTargetProtectionHandler.java` | Create | Server-side `LivingSetAttackTargetEvent` handler clearing any `EntityLiving`'s target when it is a thrall (2.1) |
| `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` | Modify | Register `ThrallTargetProtectionHandler` under `enableSpells`; append thrall registry name to `SRPConfig.mobattackingBlackList` at `FMLLoadCompleteEvent` (2.2) |
| `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIFarming.java` | Modify | `tickSearching` home-fallback scan when current-position scan is empty (F-1) |
| `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIPorter.java` | Modify | Harden hotbar exclusion (4.1); `FROM_HOME` reverse-restock cycle (4.3) reading `porterDirection` |
| `src/main/java/com/spege/insanetweaks/client/gui/GuiThrallControl.java` | Modify | Two-column layout + hover tooltips via `drawHoveringText` (5.1, 5.2) |
| `src/main/resources/assets/insanetweaks/lang/en_us.lang` | Modify | `gui.insanetweaks.thrall.tooltip.*` keys (5.2) |
| `NEXT_SESSION_SPELLS.md` | Modify | Append deferred-testing checklist from spec Verification (final task) |

`PacketThrallCommand.java`, `ThrallInventory` (SIZE = 27), entity tracking ids, immortality paths, and button/action ids are NOT touched.

**Working-tree note:** the tree has two unrelated *deleted* files staged for removal by an earlier session — `CHANGES_THRALL_T3.md` and `NEXT_SESSION_THRALL.md`. **Never `git add` these.** Every task commits only its own listed files by explicit path.

---

## Task 1a: Collecting — EntityThrallMinion auto-return gate, work-timer, accessor

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/EntityThrallMinion.java`

Fixes spec **1.1** (auto-return excludes COLLECTING) and part of **1.4** (COLLECTING added to work-timer mode list), plus provides the `getCollectingAI()` accessor Task 1b's GatherItems interlock needs.

- [ ] **Step 1: Exclude COLLECTING from the full-inventory auto-return**

In `onUpdate()`, locate the auto-return block and change the mode guard to exclude COLLECTING as well as PORTER. Replace:

```java
            // Auto-return home when inventory is full.
            // PORTER is excluded — it manages its own deposits via smartDeposit at cycle start/end,
            // and a mid-cycle teleport here would orphan the porter's manifest run.
            if (thrallInventory.isFull() && !returningHome && getHomePoint() != null
                    && getMode() != ThrallMode.PORTER) {
                if (debugLogs()) LOG.info("[Thrall#{}] Inventory FULL — starting return home to {}", getEntityId(), getHomePoint());
                startReturnHome();
            }
```

with:

```java
            // Auto-return home when inventory is full.
            // PORTER and COLLECTING are excluded — both manage their own deposits via smartDeposit
            // (PORTER at cycle start/end, COLLECTING in its RETURNING phase). A mid-cycle teleport
            // here would orphan the porter's manifest run or the collecting AI's harvest state.
            if (thrallInventory.isFull() && !returningHome && getHomePoint() != null
                    && getMode() != ThrallMode.PORTER && getMode() != ThrallMode.COLLECTING) {
                if (debugLogs()) LOG.info("[Thrall#{}] Inventory FULL — starting return home to {}", getEntityId(), getHomePoint());
                startReturnHome();
            }
```

- [ ] **Step 2: Add COLLECTING to the onUpdate work-timer mode list**

In `onUpdate()`, locate the work-timer block and add `COLLECTING` to the mode condition. Replace:

```java
            // Work timer — duration from config (0 = disabled)
            ThrallMode currentMode = getMode();
            int workHours = ModConfig.tweaks.thrallWorkDurationHours;
            long workDurationTicks = workHours * 72000L; // 1 hour = 20 ticks/s * 3600 s
            if (workHours > 0
                    && (currentMode == ThrallMode.WOODCUTTING || currentMode == ThrallMode.MINESHAFT
                        || currentMode == ThrallMode.FARMING || currentMode == ThrallMode.PORTER)
                    && workStartTick > 0
                    && world.getTotalWorldTime() - workStartTick >= workDurationTicks) {
                workStartTick = 0;
                setStatusText("Shift done");
                if (getHomePoint() != null) {
                    startReturnHome();
                } else {
                    setMode(ThrallMode.FOLLOW);
                }
            }
```

with:

```java
            // Work timer — duration from config (0 = disabled).
            // COLLECTING is included so thrallWorkDurationHours bounds the session-loop (C-3);
            // when it expires there, the collecting AI's own onWorkTimerExpired() routes to
            // WAITING_FOR_ITEMS rather than STAY, so the auto-return branch below is skipped for it.
            ThrallMode currentMode = getMode();
            int workHours = ModConfig.tweaks.thrallWorkDurationHours;
            long workDurationTicks = workHours * 72000L; // 1 hour = 20 ticks/s * 3600 s
            if (workHours > 0
                    && (currentMode == ThrallMode.WOODCUTTING || currentMode == ThrallMode.MINESHAFT
                        || currentMode == ThrallMode.FARMING || currentMode == ThrallMode.PORTER
                        || currentMode == ThrallMode.COLLECTING)
                    && workStartTick > 0
                    && world.getTotalWorldTime() - workStartTick >= workDurationTicks) {
                workStartTick = 0;
                setStatusText("Shift done");
                if (currentMode == ThrallMode.COLLECTING && collectingAI != null) {
                    // Collecting has its own end-of-shift routine: deposit + drop to WAITING_FOR_ITEMS.
                    collectingAI.onWorkTimerExpired();
                } else if (getHomePoint() != null) {
                    startReturnHome();
                } else {
                    setMode(ThrallMode.FOLLOW);
                }
            }
```

Note: the same `{WOODCUTTING, MINESHAFT, FARMING, PORTER}` set in `setMode(...)` (which starts `workStartTick`) must ALSO include COLLECTING, otherwise the timer never starts for it. Do Step 3.

- [ ] **Step 3: Start the work timer when entering COLLECTING**

In `setMode(ThrallMode mode)`, replace:

```java
        // Start/reset work timer when entering a work mode
        if (mode == ThrallMode.WOODCUTTING || mode == ThrallMode.MINESHAFT
                || mode == ThrallMode.FARMING || mode == ThrallMode.PORTER) {
            if (workStartTick == 0) {
                workStartTick = world.getTotalWorldTime();
                if (debugLogs()) LOG.info("[Thrall#{}] Work timer started at tick {}", getEntityId(), workStartTick);
            }
        } else {
            workStartTick = 0;
        }
```

with:

```java
        // Start/reset work timer when entering a work mode (COLLECTING included so its
        // session-loop is bounded by thrallWorkDurationHours — see onUpdate work-timer block).
        if (mode == ThrallMode.WOODCUTTING || mode == ThrallMode.MINESHAFT
                || mode == ThrallMode.FARMING || mode == ThrallMode.PORTER
                || mode == ThrallMode.COLLECTING) {
            if (workStartTick == 0) {
                workStartTick = world.getTotalWorldTime();
                if (debugLogs()) LOG.info("[Thrall#{}] Work timer started at tick {}", getEntityId(), workStartTick);
            }
        } else {
            workStartTick = 0;
        }
```

- [ ] **Step 4: Add a `getCollectingAI()` accessor**

After the existing `startOrResumeCollectingMode()` method (the `collectingAI` field is already declared), add:

```java
    /** Collecting AI task, or null before initEntityAI has run. Used by ThrallAIGatherItems to
     *  avoid swallowing items the player is staging while COLLECTING waits for target selection. */
    @Nullable
    public ThrallAICollecting getCollectingAI() {
        return collectingAI;
    }
```

(`ThrallAICollecting` is already imported at the top of the file.)

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: BUILD FAILS with "cannot find symbol: method onWorkTimerExpired()" — that method is added in Task 1b. This task is committed together with 1b OR: to keep each task independently green, stub `onWorkTimerExpired()` now in Task 1b BEFORE running the build. **Therefore Task 1a and Task 1b share one build+commit** (see Task 1b Step 5). Do not build 1a alone.

Proceed directly to Task 1b; build and commit both together.

---

## Task 1b: Collecting — local scan, session loop, timer-expiry, restore guard

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAICollecting.java`
- Modify: `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIGatherItems.java`

Fixes spec **1.2** (local scan), **1.3** (min-TP default is in Task 2/ModConfig — see note), **1.4** (session loop + timer expiry to WAITING), **1.5** (NBT restore DONE→WAITING), **1.6 / X-2** (GatherItems interlock).

Design of the session loop (C-3): after a session's harvest work ends, instead of `finishDone()` going straight to STAY, the AI enters a short **RESTING** sub-state at home (~100 ticks) during which it deposits, then — if the target list is non-empty and the work-timer has not expired — restarts a fresh SEARCHING pass with the SAME targets. When `EntityThrallMinion` signals the work-timer expired (`onWorkTimerExpired()`), the AI deposits at home and drops to `WAITING_FOR_ITEMS` (status "Waiting for items") rather than STAY.

- [ ] **Step 1: Add REST fields and a rest-tick constant**

In `ThrallAICollecting`, add a constant near the other `private static final` constants (after `SAFE_SPOT_VERTICAL_PROBE`):

```java
    private static final int REST_AT_HOME_TICKS = 100;
```

Add two instance fields near the other timing fields (after `private int targetCycleIndex;`):

```java
    /** When > 0, the AI is resting at home between looped sessions; counts down each tick. */
    private int restTicksRemaining;
    /** Set by EntityThrallMinion when thrallWorkDurationHours elapses — forces WAITING on next rest end. */
    private boolean workTimerExpired;
```

- [ ] **Step 2: Add the RESTING phase to the enum and dispatch**

Change the phase enum from:

```java
    public enum Phase { WAITING_FOR_ITEMS, SEARCHING, HARVESTING, RETURNING, DONE }
```

to:

```java
    public enum Phase { WAITING_FOR_ITEMS, SEARCHING, HARVESTING, RETURNING, RESTING, DONE }
```

In `updateTask()`, the termination-trigger guard currently reads:

```java
        if (phase != Phase.RETURNING && phase != Phase.DONE && phase != Phase.WAITING_FOR_ITEMS) {
```

Change it to also skip RESTING (resting is not an active harvest phase, so budget/full/empty triggers must not fire mid-rest):

```java
        if (phase != Phase.RETURNING && phase != Phase.DONE && phase != Phase.WAITING_FOR_ITEMS
                && phase != Phase.RESTING) {
```

Add a `RESTING` case to the `switch (phase)` in `updateTask()`, right before `case DONE:`:

```java
            case RESTING:
                tickResting();
                break;
```

- [ ] **Step 3: Local scan at session start (C-2a)**

Add a local-scan helper and call it at the head of `beginSearch(...)` so a session first sweeps the thrall's current position before any ring teleport. Replace the whole `beginSearch(long now)` method:

```java
    private void beginSearch(long now) {
        if (targets.isEmpty()) {
            // Edge case: lock-in fired without any targets (shouldn't happen via normal flow).
            beginReturn();
            return;
        }
        sessionStartTick = now;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        phase = Phase.SEARCHING;
        thrall.setStatusText("Searching...");
        if (debugLogs()) LOG.info("[Thrall#{}] Collecting: locked in {} targets, beginning search",
                thrall.getEntityId(), targets.size());

        // C-2a: sweep the thrall's CURRENT position first (no teleport), so targets right next to
        // where the player deployed it are collected before any ring-teleport search begins.
        tryLocalScan();
    }

    /**
     * Scans around the thrall's current position (no teleport). If matches are found, loads the
     * harvest queue and switches to HARVESTING immediately. No-op if nothing matches — the normal
     * ring-teleport SEARCHING cycle then takes over.
     */
    private void tryLocalScan() {
        BlockPos here = new BlockPos(thrall);
        List<BlockPos> hits = scanForTargets(here);
        if (hits.isEmpty()) return;

        consecutiveEmptyCycles = 0;
        harvestQueue.clear();
        harvestVisited.clear();
        for (BlockPos p : hits) {
            harvestQueue.add(p);
            harvestVisited.add(p);
        }
        veinRoot = hits.get(0);
        phase = Phase.HARVESTING;
        thrall.setStatusText("Harvesting (" + hits.size() + ")");
    }
```

- [ ] **Step 4: Session loop + rest-at-home + timer expiry**

Replace the `beginReturn()`, `tickReturning()`, and `finishDone()` methods. New behaviour: `tickReturning()` teleports home + deposits, then enters `RESTING` (instead of finishing). `tickResting()` counts down; on reaching zero it either (a) drops to `WAITING_FOR_ITEMS` if the work-timer expired or the target list is empty, or (b) restarts a fresh SEARCHING pass with the same targets. `finishDone()` is kept only for the true terminal path (no home). Add `onWorkTimerExpired()` (called from `EntityThrallMinion`).

Replace:

```java
    private void beginReturn() {
        phase = Phase.RETURNING;
        thrall.setStatusText("Returning...");
    }

    private void tickReturning() {
        BlockPos home = thrall.getHomePoint();
        if (home == null) {
            // No home — just go DONE in place.
            finishDone();
            return;
        }
        thrall.setPositionAndUpdate(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        thrall.playTeleportSound();
        ThrallChestHelper.smartDeposit(thrall, home,
                ModConfig.thrall.collectingChestScanRange, 4, false);
        finishDone();
    }

    private void finishDone() {
        phase = Phase.DONE;
        int harvested = totalItemsHarvestedThisSession;
        thrall.setStatusText("Done collecting (" + harvested + ")");
        targets.clear();
        harvestQueue.clear();
        harvestVisited.clear();
        miningTarget = null;
        veinRoot = null;
        pausedAtTick = 0;
        firstItemTick = 0;
        sessionStartTick = 0;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        totalItemsHarvestedThisSession = 0;
        thrall.setMode(ThrallMode.STAY);
    }
```

with:

```java
    private void beginReturn() {
        phase = Phase.RETURNING;
        thrall.setStatusText("Returning...");
    }

    /**
     * Teleport home, deposit, then rest briefly before deciding whether to loop (C-3).
     * With no home set there is nowhere to deposit or loop, so we go terminal via finishDone().
     */
    private void tickReturning() {
        BlockPos home = thrall.getHomePoint();
        if (home == null) {
            finishDone();
            return;
        }
        thrall.setPositionAndUpdate(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        thrall.playTeleportSound();
        thrall.getNavigator().clearPath();
        ThrallChestHelper.smartDeposit(thrall, home,
                ModConfig.thrall.collectingChestScanRange, 4, false);

        phase = Phase.RESTING;
        restTicksRemaining = REST_AT_HOME_TICKS;
        thrall.setStatusText("Resting...");
    }

    /**
     * Short pause at home between looped sessions. On completion: if the work-timer expired or the
     * target list is empty, drop to WAITING_FOR_ITEMS ("Waiting for items"); otherwise start a fresh
     * SEARCHING pass with the same target list (C-3).
     */
    private void tickResting() {
        if (restTicksRemaining > 0) {
            restTicksRemaining--;
            return;
        }

        if (workTimerExpired || targets.isEmpty()) {
            workTimerExpired = false;
            enterWaitingForItems();
            return;
        }

        // Loop: restart a session with the same targets, resetting per-session counters
        // but KEEPING the locked target list.
        harvestQueue.clear();
        harvestVisited.clear();
        miningTarget = null;
        miningSig = null;
        veinRoot = null;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        totalItemsHarvestedThisSession = 0;
        sessionStartTick = thrall.world.getTotalWorldTime();
        phase = Phase.SEARCHING;
        thrall.setStatusText("Searching...");
        tryLocalScan();
    }

    /**
     * Drops the AI into WAITING_FOR_ITEMS at home without leaving COLLECTING mode. Clears the target
     * list and session state so the player can stage a fresh set of targets.
     */
    private void enterWaitingForItems() {
        phase = Phase.WAITING_FOR_ITEMS;
        targets.clear();
        harvestQueue.clear();
        harvestVisited.clear();
        miningTarget = null;
        miningSig = null;
        veinRoot = null;
        pausedAtTick = 0;
        firstItemTick = 0;
        sessionStartTick = 0;
        restTicksRemaining = 0;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        totalItemsHarvestedThisSession = 0;
        thrall.setStatusText("Waiting for items");
    }

    /** Called by EntityThrallMinion when thrallWorkDurationHours elapses while in COLLECTING mode.
     *  Routes the AI home to deposit, then (via the RESTING branch) into WAITING_FOR_ITEMS — never STAY. */
    public void onWorkTimerExpired() {
        workTimerExpired = true;
        // If already resting/returning, let the existing flow pick up workTimerExpired on rest-end.
        if (phase == Phase.SEARCHING || phase == Phase.HARVESTING) {
            beginReturn();
        } else if (phase == Phase.WAITING_FOR_ITEMS || phase == Phase.DONE) {
            // Nothing in flight — just clear the flag; we're already idle/waiting.
            workTimerExpired = false;
        }
    }

    /** Terminal path used only when NO home is set (nowhere to deposit or loop). */
    private void finishDone() {
        phase = Phase.DONE;
        int harvested = totalItemsHarvestedThisSession;
        thrall.setStatusText("Done collecting (" + harvested + ")");
        targets.clear();
        harvestQueue.clear();
        harvestVisited.clear();
        miningTarget = null;
        veinRoot = null;
        pausedAtTick = 0;
        firstItemTick = 0;
        sessionStartTick = 0;
        restTicksRemaining = 0;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        totalItemsHarvestedThisSession = 0;
        thrall.setMode(ThrallMode.STAY);
    }
```

Also add `restTicksRemaining = 0;` and `workTimerExpired = false;` to `resetSession()` so a fresh start clears them. Replace the `resetSession()` body:

```java
    private void resetSession() {
        targets.clear();
        harvestQueue.clear();
        harvestVisited.clear();
        miningTarget = null;
        veinRoot = null;
        pausedAtTick = 0;
        firstItemTick = 0;
        sessionStartTick = 0;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        totalItemsHarvestedThisSession = 0;
    }
```

with:

```java
    private void resetSession() {
        targets.clear();
        harvestQueue.clear();
        harvestVisited.clear();
        miningTarget = null;
        veinRoot = null;
        pausedAtTick = 0;
        firstItemTick = 0;
        sessionStartTick = 0;
        restTicksRemaining = 0;
        workTimerExpired = false;
        consecutiveEmptyCycles = 0;
        targetCycleIndex = 0;
        totalItemsHarvestedThisSession = 0;
    }
```

- [ ] **Step 5: NBT restore DONE→WAITING guard (C-4)**

The design (spec 1.5) says: a restore that lands in `mode == COLLECTING && phase == DONE` must force phase → WAITING_FOR_ITEMS. `readFromNBT` runs before the entity's mode is guaranteed known here, so gate on the thrall's mode inside `readFromNBT`. Also persist the two new fields. Replace the `writeToNBT` body's first block and `readFromNBT` accordingly.

In `writeToNBT`, after `tag.setInteger("TargetCycle", targetCycleIndex);` add:

```java
        tag.setInteger("RestTicks", restTicksRemaining);
        tag.setBoolean("WorkTimerExpired", workTimerExpired);
```

In `readFromNBT`, after `targetCycleIndex = tag.getInteger("TargetCycle");` add:

```java
        restTicksRemaining = tag.getInteger("RestTicks");
        workTimerExpired = tag.getBoolean("WorkTimerExpired");
```

Then at the very end of `readFromNBT` (after the `harvestVisited.clear();` line), add the DONE→WAITING guard:

```java
        // C-4: a save that landed mid-COLLECTING with phase DONE would otherwise sit idle forever
        // (DONE does nothing in updateTask and STAY was already applied). Force WAITING so the player
        // can immediately re-stage targets after load.
        if (thrall.getMode() == ThrallMode.COLLECTING && phase == Phase.DONE) {
            phase = Phase.WAITING_FOR_ITEMS;
            thrall.setStatusText("Waiting for items");
        }
```

- [ ] **Step 6: GatherItems interlock (X-2 / 1.6)**

In `ThrallAIGatherItems.shouldExecute()`, add a WAITING_FOR_ITEMS check. Replace:

```java
    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() == ThrallMode.STAY) return false;
        if (thrall.getThrallInventory().isFull()) return false;

        this.targetItem = findNearestItem();
        return this.targetItem != null;
    }
```

with:

```java
    @Override
    public boolean shouldExecute() {
        if (thrall.getMode() == ThrallMode.STAY) return false;
        if (thrall.getThrallInventory().isFull()) return false;
        // X-2: while the collecting AI is waiting for the player to toss target items, do not let
        // this task walk over and swallow them — the player is staging them for target selection.
        com.spege.insanetweaks.entities.ai.ThrallAICollecting collecting = thrall.getCollectingAI();
        if (collecting != null && collecting.isWaitingForItems()) return false;

        this.targetItem = findNearestItem();
        return this.targetItem != null;
    }
```

(`isWaitingForItems()` already exists on `ThrallAICollecting` and returns true only when phase is WAITING_FOR_ITEMS AND mode is COLLECTING. `getCollectingAI()` was added in Task 1a Step 4.)

- [ ] **Step 7: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. (This build covers both Task 1a and Task 1b — `onWorkTimerExpired()` now exists, satisfying the 1a call site.)

- [ ] **Step 8: Commit (Task 1a + 1b together)**

```bash
git add src/main/java/com/spege/insanetweaks/entities/EntityThrallMinion.java src/main/java/com/spege/insanetweaks/entities/ai/ThrallAICollecting.java src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIGatherItems.java
git commit -m "fix: Collecting local scan, session loop, WAITING restore guard, GatherItems interlock"
```

---

## Task 2: Config — collectingMinTpDistance default + porterDirection enum

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/config/ModConfig.java`

Fixes spec **1.3** (`collectingMinTpDistance` 30→8) and **4.2** (`porterDirection` enum field). Isolated in one file so it commits cleanly on its own.

- [ ] **Step 1: Lower the collecting min-TP default**

Replace:

```java
                @Config.Comment("Inner radius of the random teleport ring (blocks from home).")
                @Config.Name("Collecting: Min TP Distance")
                @Config.RangeInt(min = 8, max = 256)
                public int collectingMinTpDistance = 30;
```

with:

```java
                @Config.Comment({ "Inner radius of the random teleport ring (blocks from home).",
                                "Default lowered to 8 so a collecting session searches close to home before",
                                "ranging out (C-2b). Range bounds unchanged." })
                @Config.Name("Collecting: Min TP Distance")
                @Config.RangeInt(min = 8, max = 256)
                public int collectingMinTpDistance = 8;
```

- [ ] **Step 2: Add the PorterDirection enum + config field**

Add a public enum inside the same `Thrall` inner class (place it directly above the `porterIntervalSeconds` field so it sits with the porter settings). First the enum:

```java
                /** Direction a Porter carries items. Read per cycle (no restart needed). */
                public enum PorterDirection { TO_HOME, FROM_HOME }
```

Then, immediately after the existing `porterIntervalSeconds` field block, add the config field:

```java
                @Config.Comment({
                                "Porter carry direction, read fresh each cycle (no restart needed):",
                                "  TO_HOME   — default. Pulls matching items from the owner's main inventory and stores them",
                                "              in home chests (the classic auto-stocker).",
                                "  FROM_HOME — reverse restock. Pulls from home chests ONLY item types the owner already",
                                "              carries with non-full stacks, teleports to the owner and tops those stacks up.",
                                "              Never introduces new item types and never touches hotbar/armour/offhand." })
                @Config.Name("Porter: Direction")
                public PorterDirection porterDirection = PorterDirection.TO_HOME;
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. (Nothing references `porterDirection` yet — Task 4 wires it. Forge `@Config` accepts an enum field with a `@Config.Comment`/`@Config.Name`; no `@RangeInt` applies.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/config/ModConfig.java
git commit -m "feat: collectingMinTpDistance default 8, add porterDirection config enum"
```

---

## Task 3: Mob-ignore — ThrallTargetProtectionHandler + registration + SRP blacklist injection

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/events/ThrallTargetProtectionHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`

Fixes spec **2.1** (handler) and **2.2** (SRP blacklist second layer — INJECTABLE per Key API facts).

- [ ] **Step 1: Create the handler**

Full file content:

```java
package com.spege.insanetweaks.events;

import com.spege.insanetweaks.entities.EntityThrallMinion;

import net.minecraft.entity.EntityLiving;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Guarantees invariant B: EVERY mob ignores the immortal utility thrall.
 *
 * <p>Whenever any {@link EntityLiving} sets its attack target to a thrall, this handler clears that
 * target (and the aggressor's revenge target) on the same tick. The event is generic across all
 * mods' EntityLiving mobs — vanilla hostiles, SRP parasites, and anything else that routes through
 * the vanilla targeting system. Server-side only (targeting AI never runs client-side).
 *
 * <p>This is the always-on primary layer. SRP additionally gets a config-blacklist append at startup
 * (see InsaneTweaksMod / SRPConfig.mobattackingBlackList) so its selectors never even build a path
 * to the thrall, but that append can be undone by a mid-game config reload — this handler cannot.
 */
public class ThrallTargetProtectionHandler {

    @SubscribeEvent
    public void onSetAttackTarget(LivingSetAttackTargetEvent event) {
        if (!(event.getTarget() instanceof EntityThrallMinion)) {
            return;
        }
        if (!(event.getEntityLiving() instanceof EntityLiving)) {
            return;
        }

        EntityLiving aggressor = (EntityLiving) event.getEntityLiving();
        if (aggressor.world.isRemote) {
            return;
        }

        aggressor.setAttackTarget(null);
        aggressor.setRevengeTarget(null);
        aggressor.getNavigator().clearPath();
    }
}
```

- [ ] **Step 2: Register the handler under `enableSpells`**

In `InsaneTweaksMod.java` `init`, the thrall's spell-summoned companion handlers register under `modules.enableSpells`. Add the new handler to that block. Locate:

```java
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSpells) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellRestrictionEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ParasiteShroudEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ImmuneBondHandler());
        }
```

and replace with:

```java
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSpells) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.SpellRestrictionEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ParasiteShroudEventHandler());
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ImmuneBondHandler());
            // Invariant B: make every mob ignore the immortal thrall (see spec 2.1).
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.events.ThrallTargetProtectionHandler());
        }
```

- [ ] **Step 3: Append the thrall registry name to SRP's mob-attacking blacklist at load complete**

The append must run AFTER SRP has loaded its config. `init` (FMLInitializationEvent) is safe because SRP's config sync runs in its own preInit, but to be robust and match the "startup, after config" intent, do it in the same `init` method, guarded by SRP presence. Add this block near the end of `init` — right before the `NetworkRegistry.INSTANCE.registerGuiHandler(this, this);` line — but only if `enableSpells` is on (the thrall only exists when spells are enabled):

```java
        // SRP second layer for invariant B: append the thrall's registry name to SRP's public
        // mobattackingBlackList so parasite targeting selectors skip it. checkEntity() does a
        // substring 'contains' match against the entity's registry name, so the exact id works.
        // Guarded by SRP presence; a mid-game SRP config reload can drop this — the
        // ThrallTargetProtectionHandler remains the always-on guarantee.
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSpells
                && Loader.isModLoaded("scapeandrunparasites")) {
            appendThrallToSrpBlacklist();
        }
```

Then add the helper method to the `InsaneTweaksMod` class (a private method, placed after `init`):

```java
    /**
     * Appends "insanetweaks:thrall_minion" to SRPConfig.mobattackingBlackList (a public static
     * String[]) so SRP parasites never target the immortal thrall. Idempotent — skips if already
     * present. Isolated in its own method so the SRP class link only loads when SRP is present.
     */
    private static void appendThrallToSrpBlacklist() {
        try {
            String thrallId = MODID + ":thrall_minion";
            String[] current = com.dhanantry.scapeandrunparasites.util.config.SRPConfig.mobattackingBlackList;
            if (current == null) {
                com.dhanantry.scapeandrunparasites.util.config.SRPConfig.mobattackingBlackList =
                        new String[] { thrallId };
                LOGGER.info("[InsaneTweaks] Initialised SRP mobattackingBlackList with thrall id.");
                return;
            }
            for (String s : current) {
                if (thrallId.equals(s)) {
                    return; // already present
                }
            }
            String[] updated = java.util.Arrays.copyOf(current, current.length + 1);
            updated[current.length] = thrallId;
            com.dhanantry.scapeandrunparasites.util.config.SRPConfig.mobattackingBlackList = updated;
            LOGGER.info("[InsaneTweaks] Added '{}' to SRP mobattackingBlackList (parasites will ignore the thrall).", thrallId);
        } catch (Throwable t) {
            // Never fatal — the LivingSetAttackTargetEvent handler is the primary guarantee.
            LOGGER.warn("[InsaneTweaks] Could not append thrall to SRP mobattackingBlackList: {}", t.toString());
        }
    }
```

(`MODID`, `LOGGER`, and `Loader` are already available/imported in `InsaneTweaksMod`. Fully-qualified `com.dhanantry...` names match the file's established style.)

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/events/ThrallTargetProtectionHandler.java src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat: thrall mob-ignore handler + SRP mobattackingBlackList injection"
```

---

## Task 4: Farming — home-fallback scan

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIFarming.java`

Fixes spec **3** (F-1). When the scan centred on the thrall finds no work and a home point exists, run a second scan centred on home. This task is done separately from Porter (Task 5) so it commits cleanly, even though both are in the same directory.

- [ ] **Step 1: Refactor `tickSearching` to try current-position, then home**

`tickSearching` currently runs all five phase-searches against `center = new BlockPos(thrall)`. Extract the five-phase search into a helper that takes a `center` and returns true if it set a target, then call it first with the thrall position and, on miss, with home. Replace the entire `tickSearching()` method:

```java
    private void tickSearching() {
        long now = thrall.world.getTotalWorldTime();
        if (now - lastScanTime < SCAN_INTERVAL_TICKS && lastScanTime != 0) return;
        lastScanTime = now;

        // Anchor the scan to the thrall's current position, not the home point.
        // The home point is the deposit/recall target; the actual farm may be many
        // blocks away (e.g. home set to a chest in a storage room).
        if (thrall.getHomePoint() == null) return;
        BlockPos center = new BlockPos(thrall);

        // Phase 1 — find a mature crop to harvest
        BlockPos mature = findCropInRange(center, true);
        if (mature != null) {
            targetPos = mature;
            targetKind = TargetKind.HARVEST_CROP;
            expectedBlock = null;
            state = State.NAVIGATING;
            navTimer = 0;
            thrall.setStatusText("Harvesting...");
            return;
        }

        // Phase 2 — sugar cane / cactus / melon / pumpkin
        HarvestTarget hb = findHarvestableBlock(center);
        if (hb != null) {
            targetPos = hb.pos;
            targetKind = TargetKind.HARVEST_BLOCK;
            expectedBlock = hb.block;
            state = State.NAVIGATING;
            navTimer = 0;
            thrall.setStatusText("Harvesting...");
            return;
        }

        // Phase 3 — plant on empty farmland (prefer neighbour crop, fall back to any CROP seed)
        HarvestTarget plant = findEmptyFarmland(center);
        if (plant != null) {
            targetPos = plant.pos;
            targetKind = TargetKind.PLANT;
            expectedBlock = plant.block;
            state = State.NAVIGATING;
            navTimer = 0;
            thrall.setStatusText("Planting...");
            return;
        }

        // Phase 4 — only if bone meal use is enabled, look for an immature crop to fertilize
        if (ModConfig.thrall.farmUseBoneMeal && hasBoneMeal()) {
            BlockPos immature = findCropInRange(center, false);
            if (immature != null) {
                targetPos = immature;
                targetKind = TargetKind.BONEMEAL;
                expectedBlock = null;
                state = State.NAVIGATING;
                navTimer = 0;
                thrall.setStatusText("Fertilizing...");
                return;
            }
        }

        // Phase 5 — restore trampled farmland: re-till dirt blocks adjacent to existing farmland.
        // Bounded by tillsThisShift to avoid runaway restoration if a hostile mob keeps trampling.
        if (tillsThisShift < MAX_TILLS_PER_SHIFT && hasHoe()) {
            BlockPos tillable = findTrampledFarmland(center);
            if (tillable != null) {
                targetPos = tillable;
                targetKind = TargetKind.TILL;
                expectedBlock = null;
                state = State.NAVIGATING;
                navTimer = 0;
                thrall.setStatusText("Restoring...");
                return;
            }
        }

        thrall.setStatusText("No crops");
    }
```

with:

```java
    private void tickSearching() {
        long now = thrall.world.getTotalWorldTime();
        if (now - lastScanTime < SCAN_INTERVAL_TICKS && lastScanTime != 0) return;
        lastScanTime = now;

        // The home point is the deposit/recall target; the actual farm may be many blocks away.
        // Scan the thrall's current position first (so a thrall deployed onto the farm works locally),
        // then fall back to scanning around home (F-1) so a thrall standing OFF the field — e.g. parked
        // at its home chest — still finds and walks/teleports to farm work.
        BlockPos home = thrall.getHomePoint();
        if (home == null) return;

        if (searchAround(new BlockPos(thrall))) return;
        if (!home.equals(new BlockPos(thrall)) && searchAround(home)) return;

        thrall.setStatusText("No crops");
    }

    /**
     * Runs the five prioritized farm-task searches around {@code center}. Sets targetPos/targetKind/
     * state and returns true if a target was found; returns false (no state change) otherwise.
     */
    private boolean searchAround(BlockPos center) {
        // Phase 1 — find a mature crop to harvest
        BlockPos mature = findCropInRange(center, true);
        if (mature != null) {
            targetPos = mature;
            targetKind = TargetKind.HARVEST_CROP;
            expectedBlock = null;
            state = State.NAVIGATING;
            navTimer = 0;
            thrall.setStatusText("Harvesting...");
            return true;
        }

        // Phase 2 — sugar cane / cactus / melon / pumpkin
        HarvestTarget hb = findHarvestableBlock(center);
        if (hb != null) {
            targetPos = hb.pos;
            targetKind = TargetKind.HARVEST_BLOCK;
            expectedBlock = hb.block;
            state = State.NAVIGATING;
            navTimer = 0;
            thrall.setStatusText("Harvesting...");
            return true;
        }

        // Phase 3 — plant on empty farmland (prefer neighbour crop, fall back to any CROP seed)
        HarvestTarget plant = findEmptyFarmland(center);
        if (plant != null) {
            targetPos = plant.pos;
            targetKind = TargetKind.PLANT;
            expectedBlock = plant.block;
            state = State.NAVIGATING;
            navTimer = 0;
            thrall.setStatusText("Planting...");
            return true;
        }

        // Phase 4 — only if bone meal use is enabled, look for an immature crop to fertilize
        if (ModConfig.thrall.farmUseBoneMeal && hasBoneMeal()) {
            BlockPos immature = findCropInRange(center, false);
            if (immature != null) {
                targetPos = immature;
                targetKind = TargetKind.BONEMEAL;
                expectedBlock = null;
                state = State.NAVIGATING;
                navTimer = 0;
                thrall.setStatusText("Fertilizing...");
                return true;
            }
        }

        // Phase 5 — restore trampled farmland: re-till dirt blocks adjacent to existing farmland.
        // Bounded by tillsThisShift to avoid runaway restoration if a hostile mob keeps trampling.
        if (tillsThisShift < MAX_TILLS_PER_SHIFT && hasHoe()) {
            BlockPos tillable = findTrampledFarmland(center);
            if (tillable != null) {
                targetPos = tillable;
                targetKind = TargetKind.TILL;
                expectedBlock = null;
                state = State.NAVIGATING;
                navTimer = 0;
                thrall.setStatusText("Restoring...");
                return true;
            }
        }

        return false;
    }
```

Note: the existing NAVIGATING state already handles walking/teleporting to any `targetPos` regardless of how far it is, so no behaviour change is needed there — a home-found target is reached the same way as a locally-found one (`teleportToTarget` fallback after `NAV_TIMEOUT_TICKS`).

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIFarming.java
git commit -m "feat: Farming falls back to a home-centred scan when off the field"
```

---

## Task 5: Porter — hotbar exclusion hardening + FROM_HOME reverse restock

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIPorter.java`

Fixes spec **4.1** (hotbar exclusion — already present at slot 9 start; harden with an assert-style guard + comment) and **4.3** (`FROM_HOME` reverse restock cycle reading `porterDirection`).

Design of FROM_HOME: each cycle, if `porterDirection == FROM_HOME`, the thrall (1) builds a "restock manifest" from the owner's MAIN inventory (slots 9–35) of item types with **non-full** stacks — these are the only types eligible, so no new type is ever introduced and hotbar/armour/offhand are never read or written; (2) pulls from home chests only those types, capped at the amount needed to top the owner's partial stacks to full; (3) teleports to the owner and tops up those partial stacks in place; (4) teleports home and deposits any leftover it couldn't place back into chests. Reuses the existing `teleportToOwner`/`teleportToHome`/`smartDeposit` and `ChestBudgetPool` machinery.

- [ ] **Step 1: Harden the hotbar exclusion (4.1)**

`pullFromOwner` already iterates `PLAYER_MAIN_INV_START (9) .. PLAYER_MAIN_INV_END (35)`. Add a defensive comment and a compile-time-style guard so a future edit can't silently drop below 9. Replace the loop header line in `pullFromOwner`:

```java
        for (int i = PLAYER_MAIN_INV_START; i <= PLAYER_MAIN_INV_END && transfers < MAX_TRANSFERS_PER_CYCLE; i++) {
```

with:

```java
        // Hotbar exclusion (spec 4.1): slots 0-8 are the hotbar, 36-40 armour, 40 offhand — none are
        // ever read. PLAYER_MAIN_INV_START is pinned to 9 so the porter only manages the main inventory.
        for (int i = PLAYER_MAIN_INV_START; i <= PLAYER_MAIN_INV_END && transfers < MAX_TRANSFERS_PER_CYCLE; i++) {
```

(No range value changes — 4.1 is verified already-correct; this hardens intent.)

- [ ] **Step 2: Branch `runCycle` on direction**

At the top of `runCycle()`, after `BlockPos home = thrall.getHomePoint(); if (home == null) return;`, branch to the reverse cycle when configured. Replace:

```java
    private void runCycle() {
        BlockPos home = thrall.getHomePoint();
        if (home == null) return;

        int range = ModConfig.thrall.porterChestScanRange;
```

with:

```java
    private void runCycle() {
        BlockPos home = thrall.getHomePoint();
        if (home == null) return;

        if (ModConfig.thrall.porterDirection
                == ModConfig.Thrall.PorterDirection.FROM_HOME) {
            runReverseCycle(home);
            return;
        }

        int range = ModConfig.thrall.porterChestScanRange;
```

(The rest of the existing `runCycle` — the TO_HOME auto-stocker — is unchanged.)

Note on the enum reference: `porterDirection` lives on the `Thrall` config category (`ModConfig.thrall`), and the enum type is `ModConfig.Thrall.PorterDirection`. Confirm the outer class path matches how other `ModConfig.thrall.*` fields are referenced in this file (`ModConfig.thrall.porterChestScanRange`), and that the `Thrall` inner class is `public static`. If the accessor object is `ModConfig.thrall`, the field compare is `ModConfig.thrall.porterDirection == ModConfig.Thrall.PorterDirection.FROM_HOME`.

- [ ] **Step 3: Add the reverse cycle**

Add these methods after `runCycle()` (before `buildManifest`):

```java
    /**
     * FROM_HOME reverse restock (spec 4.3). Tops up the owner's existing partial main-inventory
     * stacks from home chests. Never introduces new item types (only types the owner already
     * carries with a non-full stack qualify) and never touches hotbar/armour/offhand. Leftovers
     * the owner couldn't absorb are deposited back into home chests on return.
     */
    private void runReverseCycle(BlockPos home) {
        int range = ModConfig.thrall.porterChestScanRange;
        ThrallInventory inv = thrall.getThrallInventory();

        // Clear anything we might still be carrying so the pulled items don't mix with stale loot.
        if (inv.containsItems()) {
            ThrallChestHelper.smartDeposit(thrall, home, range, MANIFEST_VRANGE, false);
        }
        if (inv.isFull()) {
            thrall.setStatusText("Full");
            return;
        }

        // Owner presence / range checks (mirror the TO_HOME path).
        EntityLivingBase casterEntity = thrall.getCaster();
        if (!(casterEntity instanceof EntityPlayer)) {
            thrall.setStatusText("Awaiting owner");
            return;
        }
        EntityPlayer owner = (EntityPlayer) casterEntity;
        if (!owner.isEntityAlive()) {
            thrall.setStatusText("Awaiting owner");
            return;
        }
        if (owner.world.provider.getDimension() != thrall.world.provider.getDimension()) {
            thrall.setStatusText("Owner away");
            return;
        }
        double rangeSq = (double) ModConfig.thrall.porterTeleportRange * ModConfig.thrall.porterTeleportRange;
        if (owner.getDistanceSq(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > rangeSq) {
            thrall.setStatusText("Owner away");
            return;
        }

        // Build the top-up manifest: one entry per owner main-inventory type that has a NON-FULL stack,
        // with the total missing count (how much would fill every partial stack of that type).
        Map<Sig, Integer> needed = buildRestockNeeds(owner);
        if (needed.isEmpty()) {
            thrall.setStatusText("Standing by...");
            return;
        }

        // Pull from home chests only those types, capped at the needed amount, into our bag.
        int pulled = pullRestockFromChests(home, range, needed);
        if (pulled <= 0) {
            thrall.setStatusText("No stock");
            return;
        }

        thrall.setStatusText("Restocking...");
        teleportToOwner(owner);
        int topped = topUpOwner(owner);
        teleportToHome(home);

        // Return any leftover (types the owner filled up before we drained our bag) to home chests.
        if (inv.containsItems()) {
            ThrallChestHelper.smartDeposit(thrall, home, range, MANIFEST_VRANGE, false);
        }

        if (topped > 0) {
            thrall.setStatusText("Restocked " + topped);
            if (debugLogs()) LOG.info("[Thrall#{}] Porter FROM_HOME topped up {} stacks", thrall.getEntityId(), topped);
        } else {
            thrall.setStatusText("Standing by...");
        }
    }

    /**
     * Maps each owner MAIN-inventory item type (slots 9-35) that has at least one non-full stack to
     * the total count needed to fill every partial stack of that type. Hotbar/armour/offhand ignored.
     */
    private Map<Sig, Integer> buildRestockNeeds(EntityPlayer owner) {
        Map<Sig, Integer> needs = new HashMap<>();
        for (int i = PLAYER_MAIN_INV_START; i <= PLAYER_MAIN_INV_END; i++) {
            ItemStack s = owner.inventory.mainInventory.get(i);
            if (s.isEmpty()) continue;
            int room = s.getMaxStackSize() - s.getCount();
            if (room <= 0) continue; // full stack — not eligible
            Sig sig = Sig.of(s);
            Integer prev = needs.get(sig);
            needs.put(sig, (prev == null ? 0 : prev) + room);
        }
        return needs;
    }

    /**
     * Walks home chests and pulls up to the needed amount of each manifest type into the thrall bag.
     * Decrements the needs map as it pulls so we never over-draw beyond what the owner can absorb.
     * Returns the total item count pulled.
     */
    private int pullRestockFromChests(BlockPos home, int range, Map<Sig, Integer> needed) {
        ThrallInventory inv = thrall.getThrallInventory();
        int pulled = 0;
        List<IInventory> chests = ThrallChestHelper.findNearbyInventories(thrall.world, home, range, MANIFEST_VRANGE);
        for (IInventory chest : chests) {
            for (int i = 0; i < chest.getSizeInventory(); i++) {
                if (inv.isFull()) return pulled;
                ItemStack s = chest.getStackInSlot(i);
                if (s.isEmpty()) continue;
                Sig sig = Sig.of(s);
                Integer need = needed.get(sig);
                if (need == null || need <= 0) continue;

                int take = Math.min(need, s.getCount());
                ItemStack working = s.copy();
                working.setCount(take);
                int requested = working.getCount();
                inv.addItemStackToInventory(working);
                int actuallyTaken = requested - working.getCount();
                if (actuallyTaken > 0) {
                    s.shrink(actuallyTaken);
                    if (s.isEmpty()) {
                        chest.setInventorySlotContents(i, ItemStack.EMPTY);
                    }
                    chest.markDirty();
                    needed.put(sig, need - actuallyTaken);
                    pulled += actuallyTaken;
                }
            }
        }
        return pulled;
    }

    /**
     * Tops up the owner's existing partial MAIN-inventory stacks (slots 9-35) from the thrall bag.
     * Only merges into stacks that already exist (never fills empty slots, never creates a new type,
     * never touches hotbar/armour/offhand). Returns the number of owner slots that received items.
     */
    private int topUpOwner(EntityPlayer owner) {
        ThrallInventory inv = thrall.getThrallInventory();
        int slotsToppedUp = 0;
        for (int i = PLAYER_MAIN_INV_START; i <= PLAYER_MAIN_INV_END; i++) {
            ItemStack dst = owner.inventory.mainInventory.get(i);
            if (dst.isEmpty()) continue;
            int room = dst.getMaxStackSize() - dst.getCount();
            if (room <= 0) continue;

            int moved = drainFromBag(inv, dst, room);
            if (moved > 0) {
                dst.grow(moved);
                slotsToppedUp++;
            }
        }
        if (slotsToppedUp > 0 && owner.inventoryContainer != null) {
            owner.inventoryContainer.detectAndSendChanges();
        }
        return slotsToppedUp;
    }

    /**
     * Removes up to {@code want} items matching {@code template} (item+meta+NBT) from the thrall bag.
     * Returns the count removed. Does not modify {@code template}; caller grows the destination stack.
     */
    private int drainFromBag(ThrallInventory inv, ItemStack template, int want) {
        int drained = 0;
        for (int i = 0; i < inv.getSizeInventory() && drained < want; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (!ItemStack.areItemsEqual(s, template) || !ItemStack.areItemStackTagsEqual(s, template)) continue;
            int take = Math.min(want - drained, s.getCount());
            s.shrink(take);
            if (s.isEmpty()) {
                inv.setInventorySlotContents(i, ItemStack.EMPTY);
            }
            drained += take;
        }
        return drained;
    }
```

Note: `Map`, `HashMap`, `List`, `IInventory`, `ItemStack`, `EntityPlayer`, `EntityLivingBase`, `ThrallInventory`, and the inner `Sig` type are all already imported/defined in this file (`buildManifest`/`consolidateChests`/`ChestBudget` use them). No new imports needed.

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/entities/ai/ThrallAIPorter.java
git commit -m "feat: Porter hotbar-exclusion hardening + FROM_HOME reverse restock cycle"
```

---

## Task 6: GUI — two columns + hover tooltips + lang keys

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/client/gui/GuiThrallControl.java`
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`

Fixes spec **5.1** (two-column layout: left = modes, right = actions) and **5.2** (hover tooltips via `drawHoveringText` + `listFormattedStringToWidth`).

Column assignment (enumerated exactly from the current file):
- **Left (modes):** Follow (0), Stay (1), Woodcutting (2), Mineshaft (3), Farming (8), Porter (9), Collecting (11).
- **Right (actions):** Set Home (4), Return Home (10), Inventory (6), Dismiss (7).

Tooltip key per button id (added to lang in Step 2). All button ids and packet actions are unchanged.

- [ ] **Step 1: Rewrite `GuiThrallControl.java`**

Full file content:

```java
package com.spege.insanetweaks.client.gui;

import java.util.List;

import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketThrallCommand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

@SuppressWarnings("null")
public class GuiThrallControl extends GuiScreen {

    private final int entityId;

    public GuiThrallControl(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public void initGui() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int btnW = 150;
        int btnH = 20;
        int gap = 3;
        int colGap = 10;
        int startY = cy - 100;

        int leftX = cx - btnW - colGap / 2;
        int rightX = cx + colGap / 2;

        this.buttonList.clear();

        // ---- Left column: modes ----
        this.buttonList.add(new GuiButton(0, leftX, startY, btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.follow")));
        this.buttonList.add(new GuiButton(1, leftX, startY + (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.stay")));
        this.buttonList.add(new GuiButton(2, leftX, startY + 2 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.woodcutting")));
        this.buttonList.add(new GuiButton(3, leftX, startY + 3 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.mineshaft")));
        this.buttonList.add(new GuiButton(8, leftX, startY + 4 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.farming")));
        this.buttonList.add(new GuiButton(9, leftX, startY + 5 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.porter")));
        this.buttonList.add(new GuiButton(11, leftX, startY + 6 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.collecting")));

        // ---- Right column: actions ----
        this.buttonList.add(new GuiButton(4, rightX, startY, btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.set_home")));
        this.buttonList.add(new GuiButton(10, rightX, startY + (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.return_home")));
        this.buttonList.add(new GuiButton(6, rightX, startY + 2 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.inventory")));
        this.buttonList.add(new GuiButton(7, rightX, startY + 3 * (btnH + gap), btnW, btnH,
                "§c" + I18n.format("gui.insanetweaks.thrall.action.dismiss")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int action = -1;
        switch (button.id) {
            case 0: action = PacketThrallCommand.ACTION_FOLLOW;      break;
            case 1: action = PacketThrallCommand.ACTION_STAY;        break;
            case 2: action = PacketThrallCommand.ACTION_WOODCUTTING; break;
            case 3: action = PacketThrallCommand.ACTION_MINESHAFT;   break;
            case 4: action = PacketThrallCommand.ACTION_SET_HOME;    break;
            case 6: action = PacketThrallCommand.ACTION_OPEN_INV;    break;
            case 7: action = PacketThrallCommand.ACTION_DISMISS;     break;
            case 8: action = PacketThrallCommand.ACTION_FARMING;     break;
            case 9: action = PacketThrallCommand.ACTION_PORTER;      break;
            case 10: action = PacketThrallCommand.ACTION_RETURN_HOME; break;
            case 11: action = PacketThrallCommand.ACTION_COLLECTING; break;
        }
        if (action >= 0) {
            InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketThrallCommand(this.entityId, action));
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Title
        this.drawCenteredString(this.fontRenderer,
                I18n.format("gui.insanetweaks.thrall.title"),
                cx, cy - 117, 0xFFFFFF);

        // Current mode + inventory info
        EntityThrallMinion thrall = getThrall();
        if (thrall != null) {
            int itemCount = 0;
            for (int i = 0; i < thrall.getThrallInventory().getSizeInventory(); i++) {
                if (!thrall.getThrallInventory().getStackInSlot(i).isEmpty()) itemCount++;
            }
            this.drawCenteredString(this.fontRenderer,
                    I18n.format("gui.insanetweaks.thrall.inventory", itemCount, 27),
                    cx, cy + 130, 0xA0A0A0);

            net.minecraft.util.math.BlockPos home = thrall.getHomePoint();
            String homeStr = home == null
                    ? I18n.format("gui.insanetweaks.thrall.home.none")
                    : I18n.format("gui.insanetweaks.thrall.home.coords", home.getX(), home.getY(), home.getZ());
            this.drawCenteredString(this.fontRenderer, homeStr, cx, cy + 142, 0x808080);
        }

        // Hover tooltip for whichever button the mouse is over.
        drawButtonTooltip(mouseX, mouseY);
    }

    /** Draws a wrapped tooltip for the hovered button, keyed by its id. */
    private void drawButtonTooltip(int mouseX, int mouseY) {
        String key = null;
        for (GuiButton b : this.buttonList) {
            if (!b.isMouseOver()) continue;
            key = tooltipKeyFor(b.id);
            break;
        }
        if (key == null) return;

        String text = I18n.format(key);
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(text, 180);
        this.drawHoveringText(lines, mouseX, mouseY);
    }

    private static String tooltipKeyFor(int buttonId) {
        switch (buttonId) {
            case 0:  return "gui.insanetweaks.thrall.tooltip.follow";
            case 1:  return "gui.insanetweaks.thrall.tooltip.stay";
            case 2:  return "gui.insanetweaks.thrall.tooltip.woodcutting";
            case 3:  return "gui.insanetweaks.thrall.tooltip.mineshaft";
            case 8:  return "gui.insanetweaks.thrall.tooltip.farming";
            case 9:  return "gui.insanetweaks.thrall.tooltip.porter";
            case 11: return "gui.insanetweaks.thrall.tooltip.collecting";
            case 4:  return "gui.insanetweaks.thrall.tooltip.set_home";
            case 10: return "gui.insanetweaks.thrall.tooltip.return_home";
            case 6:  return "gui.insanetweaks.thrall.tooltip.inventory";
            case 7:  return "gui.insanetweaks.thrall.tooltip.dismiss";
            default: return null;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (getThrall() == null) {
            this.mc.displayGuiScreen(null);
        }
    }

    private EntityThrallMinion getThrall() {
        if (Minecraft.getMinecraft().world == null) return null;
        net.minecraft.entity.Entity e = Minecraft.getMinecraft().world.getEntityByID(entityId);
        return e instanceof EntityThrallMinion ? (EntityThrallMinion) e : null;
    }
}
```

- [ ] **Step 2: Add tooltip lang keys**

In `src/main/resources/assets/insanetweaks/lang/en_us.lang`, immediately after the existing `gui.insanetweaks.thrall.mode.disabled=...` line (the last thrall GUI key), add:

```
gui.insanetweaks.thrall.tooltip.follow=Thrall trails you and picks up dropped items. Teleports to you if it falls too far behind.
gui.insanetweaks.thrall.tooltip.stay=Thrall waits in place and stops all work until given a new order.
gui.insanetweaks.thrall.tooltip.woodcutting=Thrall fells nearby trees and gathers the logs. Needs a home chest to deposit into.
gui.insanetweaks.thrall.tooltip.mineshaft=Thrall digs a branching mineshaft from here, torching and hauling ore home.
gui.insanetweaks.thrall.tooltip.farming=Thrall harvests, replants, bone-meals and re-tills farmland near itself or its home.
gui.insanetweaks.thrall.tooltip.porter=Auto-stocker: ferries matching items between your inventory and home chests each cycle. Direction set in config.
gui.insanetweaks.thrall.tooltip.collecting=Toss up to 4 block items to set targets; the thrall teleport-searches around home and mines every match.
gui.insanetweaks.thrall.tooltip.set_home=Sets the thrall's home to its current spot and deposits its bag into nearby chests.
gui.insanetweaks.thrall.tooltip.return_home=Sends the thrall home right now to deposit its inventory, then wait there.
gui.insanetweaks.thrall.tooltip.inventory=Opens the thrall's 27-slot backpack.
gui.insanetweaks.thrall.tooltip.dismiss=Removes the thrall and drops its items. Its slot backup is kept for re-summoning.
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/client/gui/GuiThrallControl.java src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "feat: two-column thrall control GUI with hover tooltips"
```

---

## Task 7: Final — deferred-testing checklist + whole-diff review

**Files:**
- Modify: `NEXT_SESSION_SPELLS.md`

- [ ] **Step 1: Append the deferred manual checklist**

Add a new section to the end of `NEXT_SESSION_SPELLS.md` (do NOT stage the two deleted THRALL files):

```markdown

## Thrall fixes & mob-ignore (plan `2026-07-07-thrall-fixes.md`) — deferred manual checklist

Build green after every task; in-game testing deferred by the user (2026-07-07). Verify in `runClient`
(and one `runServer` boot for sanity):

- [ ] Collecting near-home pickup: deploy thrall onto a target block cluster < 30 blocks from home; it mines locally before ring-teleporting.
- [ ] Collecting loop: after a session the thrall rests at home ~5 s, deposits, then re-searches with the same targets.
- [ ] Collecting work-timer expiry: with `thrallWorkDurationHours` > 0, on expiry the thrall returns home, deposits, and shows "Waiting for items" (NOT Stay).
- [ ] Collecting full bag: filling the bag mid-session does NOT hijack the thrall into a Stay/return via the generic auto-return (collecting handles its own RETURNING).
- [ ] Collecting NBT restore: save/quit while collecting is DONE, reload — thrall shows "Waiting for items", accepts fresh targets.
- [ ] GatherItems interlock: while collecting is "Waiting for items", tossed staging items are NOT walked-to and swallowed by the gather task.
- [ ] Mob-ignore: parasites (primitive + advanced) and vanilla zombies/skeletons never approach or swing at the thrall; check with `srpcothimmunity` unset.
- [ ] SRP blacklist: log shows "Added 'insanetweaks:thrall_minion' to SRP mobattackingBlackList" at load.
- [ ] Farming home-fallback: park the thrall at its home chest, off the field; it still finds farm work within `farmRadius` of home and walks/teleports to it.
- [ ] Porter hotbar: TO_HOME porter never removes items from hotbar slots 0-8, armour, or offhand.
- [ ] Porter FROM_HOME: set `porterDirection = FROM_HOME`; thrall tops up the owner's partial main-inventory stacks from home chests, never adding new types or touching hotbar/armour/offhand; leftovers returned to chests.
- [ ] GUI: two columns (modes left, actions right); hovering each button shows a wrapped one-line tooltip.
- [ ] `runServer` boots without a client-classloading crash (ThrallTargetProtectionHandler + SRPConfig append are server-safe).
```

- [ ] **Step 2: Whole-diff review**

Run `git log --oneline -8` and confirm the six thrall commits landed (Task 1a+1b combined, Task 2, Task 3, Task 4, Task 5, Task 6). Run `git status --short` and confirm the only unstaged/uncommitted items are the two pre-existing deleted files (`CHANGES_THRALL_T3.md`, `NEXT_SESSION_THRALL.md`) — NOT anything from this plan. Do a final `./gradlew build` to confirm green.

- [ ] **Step 3: Commit**

```bash
git add NEXT_SESSION_SPELLS.md
git commit -m "docs: thrall fixes deferred-testing checklist"
```

---

## Self-review results

**Spec coverage (every numbered item → task/step):**

| Spec item | Where |
|---|---|
| 1.1 auto-return excludes COLLECTING | Task 1a Step 1 |
| 1.2 local scan before ring TP (C-2a) | Task 1b Step 3 (`tryLocalScan` in `beginSearch`) |
| 1.3 `collectingMinTpDistance` 30→8 (C-2b) | Task 2 Step 1 |
| 1.4 session loop + COLLECTING in work-timer list + expiry→WAITING (C-3) | Task 1a Steps 2–3 (timer list + `onWorkTimerExpired` call), Task 1b Steps 2/4 (RESTING phase, loop, `onWorkTimerExpired`) |
| 1.5 NBT restore DONE→WAITING (C-4) | Task 1b Step 5 |
| 1.6 / X-2 GatherItems interlock | Task 1a Step 4 (accessor), Task 1b Step 6 |
| 2.1 LivingSetAttackTargetEvent handler | Task 3 Steps 1–2 |
| 2.2 SRP blacklist injection (INJECTABLE) | Task 3 Step 3 |
| 3 Farming home-fallback scan (F-1) | Task 4 Step 1 |
| 4.1 Porter hotbar exclusion | Task 5 Step 1 (verified-present; hardened) |
| 4.2 `porterDirection` config | Task 2 Step 2 |
| 4.3 FROM_HOME reverse restock | Task 5 Steps 2–3 |
| 5.1 two-column GUI | Task 6 Step 1 |
| 5.2 hover tooltips + lang keys | Task 6 Steps 1–2 |

**Placeholder scan:** none. Every code step shows complete method bodies / full file content. No "TBD" / "add handling" / "similar to Task N".

**Type/signature consistency:**
- `onWorkTimerExpired()` — declared in `ThrallAICollecting` (Task 1b Step 4), called in `EntityThrallMinion.onUpdate` (Task 1a Step 2). Signature matches (`public void`, no args).
- `getCollectingAI()` — added in `EntityThrallMinion` (Task 1a Step 4), called in `ThrallAIGatherItems.shouldExecute` (Task 1b Step 6). Returns `@Nullable ThrallAICollecting`.
- `isWaitingForItems()` — pre-existing on `ThrallAICollecting`, used unchanged.
- `porterDirection` field + `ModConfig.Thrall.PorterDirection` enum — declared in Task 2 Step 2, referenced in Task 5 Step 2. Enum constants `TO_HOME`/`FROM_HOME` spelled identically.
- Tooltip lang keys `gui.insanetweaks.thrall.tooltip.<name>` — the eleven keys in `tooltipKeyFor` (Task 6 Step 1) exactly match the eleven keys added to `en_us.lang` (Task 6 Step 2): follow, stay, woodcutting, mineshaft, farming, porter, collecting, set_home, return_home, inventory, dismiss.
- `Sig.of`, `ThrallInventory`, `ThrallChestHelper.findNearbyInventories`/`smartDeposit` — reused with their existing signatures in Task 5.

**Ordering / dependency notes:**
- **Task 1a + 1b are one build/commit** (1a's `onWorkTimerExpired` call needs 1b's method; 1b's GatherItems interlock needs 1a's `getCollectingAI`). Never build 1a alone.
- **Task 2 must precede Task 5** (Task 5 references `ModConfig.Thrall.PorterDirection` / `porterDirection`).
- **Task 3, Task 4, Task 6 are independent** of each other and of Tasks 1/2/5; they can be done in any order after their file is untouched by others (no overlap — each touches distinct files except InsaneTweaksMod.java, touched only by Task 3).
- **Task 7 is last** (references all prior commits).

**Resolved-in-planning decisions:**
1. *Work-timer set appears twice* (`onUpdate` and `setMode`). Both must include COLLECTING or the timer never starts — handled explicitly in Task 1a Steps 2 AND 3.
2. *Spec 4.1 already satisfied.* The porter loop already starts at slot 9. Rather than a no-op, the plan hardens it with an intent comment + pinned-constant note (Task 5 Step 1) so the requirement is documented and regression-resistant.
3. *SRP injection timing.* Field is re-parsed on config reload, so the append is a second layer only; done in `init` guarded by `Loader.isModLoaded` + `enableSpells`, wrapped in try/catch so a class-shape change never crashes startup.
4. *`finishDone` vs looping.* Kept `finishDone` for the no-home terminal path; the home path now routes through RESTING → loop-or-WAITING, so the STAY drop only happens when there is genuinely nowhere to deposit (Task 1b Step 4).
5. *FROM_HOME "top-up only" invariant.* `topUpOwner` merges strictly into existing non-empty owner stacks (never empty slots, never new types, never hotbar/armour/offhand), satisfying the spec's "never introduces new item types" constraint; leftovers go back to home chests (Task 5 Step 3).
