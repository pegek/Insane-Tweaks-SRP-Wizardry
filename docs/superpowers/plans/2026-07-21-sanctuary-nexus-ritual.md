# Sanctuary Nexus Ritual — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the diamond-pyramid multiblock with a progressive lure-offering ritual that permanently upgrades a single Nexus block (spec: [2026-07-21-sanctuary-nexus-ritual-design.md](../specs/2026-07-21-sanctuary-nexus-ritual-design.md)).

**Architecture:** The Nexus TileEntity gains a persistent `progress` counter (0–6). Each server tick it checks the flat 5×5 ring of `srparasites:evolutionlure` around it for the currently-demanded meta; a complete ring channels a short ritual, then consumes the 24 lure blocks and increments `progress`. Tier is derived from `progress` (T1@2 … T4@6) — the pyramid scan and beacon requirement are deleted. All existing effects (spawn veto, purge fire, cleanse, dome, block-break veto) already read the stored tier/radius, so only the tier SOURCE changes.

**Tech Stack:** Minecraft 1.12.2 Forge mod (Java 8). No unit-test suite — verification is `./gradlew build` (compile, `-Xlint:all`) plus the in-game build→test loop documented in CLAUDE.md (copy `build/libs/insanetweaks-1.2.0.jar` into the DEv 1.2 pack `mods/`, launch, watch `logs/latest.log`). Set `sanctuary.debugLogging=true` while testing.

**Deploy command (reused by every task's in-game step):**
```bash
cp -f build/libs/insanetweaks-1.2.0.jar "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/insanetweaks-1.2.0.jar"
```

---

## File Structure

- **Modify** `src/main/java/com/spege/insanetweaks/config/categories/SanctuaryCategory.java` — remove pyramid config, add ritual tunables.
- **Modify** `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java` — the heart: progress field + NBT, tier-from-progress, ritual detection/consumption, chat messages. Remove pyramid scan.
- **Modify** `src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java` — initial demand message on placement.
- **Modify** `src/main/resources/assets/insanetweaks/lang/en_us.lang` — ritual chat message keys.

No new files. The block registry id `sanctuary_core` is unchanged (avoids breaking placed blocks / recipes); only in-game flavor treats it as the "Nexus".

---

## Task 1: Config — retire pyramid tunables, add ritual tunables

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/SanctuaryCategory.java`

- [ ] **Step 1: Remove the `pyramidBlocks` field**

Delete this block (lines ~13–16):

```java
    @Config.Comment({"Blocks allowed in the pyramid layers, by registry name (e.g. minecraft:iron_block).",
            "A layer counts only if fully built from these. Read live."})
    @Config.Name("Pyramid Blocks")
    public String[] pyramidBlocks = new String[] { "minecraft:iron_block", "minecraft:diamond_block" };
```

- [ ] **Step 2: Rename `pyramidRevalidateInterval` → `revalidateInterval` and add ritual tunables**

Replace this block (lines ~35–38):

```java
    @Config.Comment("Ticks between pyramid re-validations in the core TE. Read live.")
    @Config.Name("Pyramid Revalidate Interval")
    @Config.RangeInt(min = 20, max = 1200)
    public int pyramidRevalidateInterval = 40;
```

with:

```java
    @Config.Comment("Ticks between Nexus tier/radius/region re-validations in the core TE. Read live.")
    @Config.Name("Revalidate Interval")
    @Config.RangeInt(min = 20, max = 1200)
    public int revalidateInterval = 40;

    @Config.Comment({"Registry name of the block the Nexus ritual consumes (SRP evolution lure).",
            "Read live."})
    @Config.Name("Lure Block Id")
    public String lureBlockId = "srparasites:evolutionlure";

    @Config.Comment({"Ticks the Nexus channels a completed lure ring before consuming it (40 = 2s).",
            "Read live."})
    @Config.Name("Ritual Duration Ticks")
    @Config.RangeInt(min = 1, max = 400)
    public int ritualDurationTicks = 40;
```

- [ ] **Step 3: Compile**

Run: `./gradlew build 2>&1 | tail -8`
Expected: `BUILD FAILED` — `TileEntitySanctuaryCore.java` still references `pyramidBlocks` / `pyramidRevalidateInterval`. That is expected; Task 3 fixes the TE. (If you prefer a green build here, do Task 1 + Task 3 together before building.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/config/categories/SanctuaryCategory.java
git commit -m "config(sanctuary): retire pyramid tunables, add lure ritual config

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: TE — add `progress`/`ritualTicks` fields + NBT persistence

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`

- [ ] **Step 1: Add the fields**

After the existing field `private int statusCode = ...;` (line ~28), add:

```java
    private int progress;       // 0..6 consumed lure offerings; permanent. Tier derived from this.
    private int ritualTicks;    // transient: >0 while channeling a completed lure ring
```

- [ ] **Step 2: Add a getter**

Next to `public int getTier()` (line ~35), add:

```java
    public int getProgress() { return progress; }
```

- [ ] **Step 3: Persist `progress` in main NBT**

In `writeToNBT` add (next to `c.setInteger("tier", tier);`):

```java
        c.setInteger("progress", progress);
```

In `readFromNBT` add (next to `tier = c.getInteger("tier");`):

```java
        progress = c.getInteger("progress"); // 0 default: pre-ritual worlds reset to tier 0, rebuild via ritual
```

- [ ] **Step 4: Compile check deferred**

(Do not build yet — the TE still calls `scanPyramidTier()`; Task 3 replaces it. Building now fails on the config rename from Task 1, which is expected.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java
git commit -m "feat(sanctuary): Nexus progress field + NBT (tier source groundwork)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: TE — derive tier from `progress`, delete pyramid scan

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`

- [ ] **Step 1: Delete the pyramid helpers**

Remove `isPyramidBlock(...)` (lines ~54–62) and `scanPyramidTier()` (lines ~64–85) entirely.

- [ ] **Step 2: Add `tierFromProgress`**

Add this static helper where `scanPyramidTier()` was:

```java
    /** Progress (0..6 consumed offerings) -> tier. T1 at 2 offerings, T2 at 4, T3 at 5, T4 at 6. */
    private static int tierFromProgress(int p) {
        if (p >= 6) { return 4; }
        if (p >= 5) { return 3; }
        if (p >= 4) { return 2; }
        if (p >= 2) { return 1; }
        return 0;
    }
```

- [ ] **Step 3: Point `revalidateAndSync` at `progress`**

In `revalidateAndSync()` replace:

```java
        int newTier = scanPyramidTier();
```

with:

```java
        int newTier = tierFromProgress(progress);
```

- [ ] **Step 4: Rename the revalidate throttle field + config ref**

Rename the field declaration `private int pyramidTickCounter;` to:

```java
    private int revalidateTickCounter;
```

In `update()` replace the throttle block:

```java
        if (++pyramidTickCounter >= com.spege.insanetweaks.config.ModConfig.sanctuary.pyramidRevalidateInterval) {
            pyramidTickCounter = 0;
            revalidateAndSync();
            logParasitesInZoneDebug();
        }
```

with:

```java
        if (++revalidateTickCounter >= com.spege.insanetweaks.config.ModConfig.sanctuary.revalidateInterval) {
            revalidateTickCounter = 0;
            revalidateAndSync();
            logParasitesInZoneDebug();
        }
```

- [ ] **Step 5: Revalidate once on first tick (so a reloaded upgraded Nexus is active immediately)**

In `update()`, replace the init block:

```java
        if (!isInitialized()) {
            setCleanseEnabled(com.spege.insanetweaks.config.ModConfig.sanctuary.cleanseEnabledByDefault);
            markInitialized();
        }
```

with:

```java
        if (!isInitialized()) {
            setCleanseEnabled(com.spege.insanetweaks.config.ModConfig.sanctuary.cleanseEnabledByDefault);
            markInitialized();
            revalidateAndSync(); // establish tier/radius/region from stored progress on load
        }
```

- [ ] **Step 6: Compile**

Run: `./gradlew build 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL` (Task 1 + 2 + 3 together compile clean; pyramid references gone).

- [ ] **Step 7: Deploy + in-game smoke test**

Deploy (command at top). Launch, place a Nexus (`sanctuary_core`). With `debugLogging=true`, expected: tier 0, no dome, no region (progress 0). Shift-right-click shows status Tier 0. No crash in `logs/latest.log`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java
git commit -m "feat(sanctuary): derive tier from progress, delete pyramid scan

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: TE — lure-ring detection + ritual consumption

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`

- [ ] **Step 1: Add ring-check, consume, FX, and the ritual state machine**

Add these methods (near `revalidateAndSync`):

```java
    /** True when all 24 cells of the flat 5x5 ring at the Nexus's Y level are the demanded lure meta. */
    private boolean lureRingComplete(int meta) {
        String lureId = com.spege.insanetweaks.config.ModConfig.sanctuary.lureBlockId;
        int y = pos.getY();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) { continue; } // Nexus occupies the center
                net.minecraft.util.math.BlockPos p =
                        new net.minecraft.util.math.BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                net.minecraft.block.state.IBlockState st = world.getBlockState(p);
                net.minecraft.util.ResourceLocation rn = st.getBlock().getRegistryName();
                if (rn == null || !rn.toString().equals(lureId)) { return false; }
                if (st.getBlock().getMetaFromState(st) != meta) { return false; }
            }
        }
        return true;
    }

    /** Set the 24 ring cells to air (the offering is consumed). */
    private void consumeRing() {
        int y = pos.getY();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) { continue; }
                world.setBlockToAir(new net.minecraft.util.math.BlockPos(pos.getX() + dx, y, pos.getZ() + dz));
            }
        }
    }

    /** Ambient channel particles while a ritual is winding down. */
    private void spawnRitualFx() {
        if (world instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) world).spawnParticle(
                    net.minecraft.util.EnumParticleTypes.SPELL_WITCH,
                    pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D,
                    6, 1.6D, 0.3D, 1.6D, 0.02D);
        }
    }

    /** Detect a completed demanded ring, channel it for ritualDurationTicks, then consume + advance. */
    private void tickRitual() {
        if (progress >= 6) { ritualTicks = 0; return; }        // fully built
        if (!lureRingComplete(progress)) { ritualTicks = 0; return; } // no/incorrect ring -> idle
        if (ritualTicks <= 0) {
            ritualTicks = Math.max(1, com.spege.insanetweaks.config.ModConfig.sanctuary.ritualDurationTicks);
        }
        spawnRitualFx();
        if (--ritualTicks <= 0) {
            consumeRing();
            progress++;
            onProgressAdvanced();
        }
    }
```

- [ ] **Step 2: Add a stub `onProgressAdvanced` (messages filled in Task 5)**

Add this method now so Task 4 compiles standalone; Task 5 fills the body:

```java
    /** Called right after progress increments: refresh tier/radius/region + (Task 5) notify the player. */
    private void onProgressAdvanced() {
        revalidateAndSync(); // tier/radius/region update immediately from the new progress
    }
```

- [ ] **Step 3: Call `tickRitual()` every server tick**

In `update()`, add the call immediately before the revalidate-throttle block:

```java
        tickRitual();
        if (++revalidateTickCounter >= com.spege.insanetweaks.config.ModConfig.sanctuary.revalidateInterval) {
```

(i.e. insert the `tickRitual();` line above the existing `if (++revalidateTickCounter ...`.)

- [ ] **Step 4: Compile**

Run: `./gradlew build 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Deploy + in-game test (the core mechanic)**

Deploy + launch. Place a Nexus. Build a **flat 5×5 ring of Evolution Lure variant ONE (meta 0)** around it at the Nexus's own Y level (24 blocks, Nexus in the center). Expected: witch-spell particles for ~2s, then the 24 lure blocks vanish and (with `debugLogging`) the tier log shows tier still 0 but progress advanced. Repeat with meta 1 → tier becomes **T1** (dome appears). Continue metas 2,3 → T2; 4 → T3; 5 → T4. Breaking the ring mid-channel cancels it (no consumption).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java
git commit -m "feat(sanctuary): lure-ring detection + ritual consumption -> progress

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: TE — ritual chat messages + tier-up FX

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Add the lang keys**

In `en_us.lang`, next to the other `msg.insanetweaks.sanctuary.*` lines, add:

```
msg.insanetweaks.sanctuary.demand=§5The Sanctuary hungers — offer §dEvolution Lure %d§5 to hold back the horrible hour.
msg.insanetweaks.sanctuary.tierup=§5The Sanctuary swells to §dTier %d§5.
msg.insanetweaks.sanctuary.whole=§5The Sanctuary is whole. §dTier IV§5 — your doom is held at bay.
```

- [ ] **Step 1b: Rename the Nexus block's display name (registry id stays `sanctuary_core`)**

Find the existing block name key:

Run: `grep -rn "sanctuary_core.name" src/main/resources/assets/insanetweaks/lang/en_us.lang`

Change its value to `Sanctuary Nexus` (keep the key exactly as found — typically `tile.insanetweaks.sanctuary_core.name=Sanctuary Nexus`). If no such key exists, add `tile.insanetweaks.sanctuary_core.name=Sanctuary Nexus`.

- [ ] **Step 2: Flesh out `onProgressAdvanced` with FX + chat**

Replace the Task-4 stub `onProgressAdvanced()` with:

```java
    /** Called right after progress increments: refresh tier/region, play FX, notify the nearest player. */
    private void onProgressAdvanced() {
        int oldTier = tier;
        revalidateAndSync(); // tier/radius/region update immediately from the new progress

        if (world instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) world).spawnParticle(
                    net.minecraft.util.EnumParticleTypes.SPELL_MOB,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    40, 1.2D, 0.6D, 1.2D, 0.1D);
        }
        world.playSound(null, pos, net.minecraft.init.SoundEvents.BLOCK_END_PORTAL_SPAWN,
                net.minecraft.util.SoundCategory.BLOCKS, 0.6F, 1.4F);

        net.minecraft.entity.player.EntityPlayer p = world.getClosestPlayer(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 64.0D, false);
        if (p != null) {
            if (tier > oldTier) {
                p.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                        "msg.insanetweaks.sanctuary.tierup", tier));
            }
            if (progress >= 6) {
                p.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                        "msg.insanetweaks.sanctuary.whole"));
            } else {
                p.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                        "msg.insanetweaks.sanctuary.demand", progress + 1)); // display 1..6
            }
        }
    }
```

- [ ] **Step 3: Compile**

Run: `./gradlew build 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Deploy + in-game test (messages)**

Deploy + launch. Each completed ring now posts chat: a "demands Evolution Lure N" line after every offering, a "swells to Tier X" line when the tier increases, and "is whole" on the sixth offering. Confirm the demanded number matches what you must build next (1 → variant ONE, etc.).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "feat(sanctuary): ritual chat messages + tier-up FX

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Block — initial demand message on placement

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java`

- [ ] **Step 1: Message the placer the first demand**

Add this override to `BlockSanctuaryCore` (after `breakBlock`):

```java
    @Override
    public void onBlockPlacedBy(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.state.IBlockState state, net.minecraft.entity.EntityLivingBase placer,
            net.minecraft.item.ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && placer instanceof net.minecraft.entity.player.EntityPlayer) {
            ((net.minecraft.entity.player.EntityPlayer) placer).sendMessage(
                    new net.minecraft.util.text.TextComponentTranslation(
                            "msg.insanetweaks.sanctuary.demand", 1)); // demands Evolution Lure 1 (meta 0)
        }
    }
```

- [ ] **Step 2: Compile**

Run: `./gradlew build 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Deploy + in-game test**

Deploy + launch. Placing a fresh Nexus immediately posts the chat line "The Sanctuary hungers — offer Evolution Lure 1…".

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java
git commit -m "feat(sanctuary): Nexus announces first lure demand on placement

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Full playthrough + cleanup

**Files:**
- (verification only; small edits if the playthrough surfaces issues)

- [ ] **Step 1: Full ritual playthrough**

Deploy + launch with `debugLogging=true`. From a fresh Nexus, offer all six lures in sequence (meta 0→5). Verify:
- Each ring is consumed after ~2s of particles; chat demands the next lure.
- Tier crosses to T1 (after meta 1), T2 (meta 3), T3 (meta 4), T4 (meta 5) — matching the spec table.
- The protection dome appears at T1 and grows with tier; `logs/latest.log` shows `tier=`/`radius=` advancing and `SanctuaryWorldData` region active.
- Purge fire / spawn veto / cleanse operate at the stored tier (existing behavior, unchanged).
- After T4, the Nexus stands alone; building obsidian (or anything) around it does not change function.

- [ ] **Step 2: Persistence + migration check**

Save & quit, reload the world. The T4 Nexus is active immediately on load (tier restored from `progress` NBT on first tick). Confirm any pre-existing (old pyramid-era) Nexus loads at tier 0 and must be rebuilt via ritual — expected migration behavior.

- [ ] **Step 3: Grep the tree for stale pyramid references**

Run: `grep -rn "pyramid\|scanPyramidTier\|isPyramidBlock" src/main/java/com/spege/insanetweaks/sanctuary src/main/java/com/spege/insanetweaks/config`
Expected: no matches (all removed). If any remain, delete them and rebuild.

- [ ] **Step 4: Final build**

Run: `./gradlew build 2>&1 | tail -8`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit any cleanup**

```bash
git add -A
git commit -m "chore(sanctuary): Nexus ritual playthrough cleanup

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Notes / deferred (out of scope, per spec)

- Fuel/paliwo economy, upgrade components, crafting recipes, and GUI rework are separate future iterations. The existing fuel/upgrade GUI and the `countUpgradeRadiusItems()` radius bonus keep working unchanged.
- Breaking the Nexus loses progress (no NBT-preserve-on-pickup yet).
- The `evolutionlure` block is an active SRP block; during the ~2s a ring stands it may briefly do its own thing. Consumed quickly, so ignored.
- Consider throttling `tickRitual()`'s ring scan if many Nexus blocks exist (24 block reads/tick each); negligible for realistic counts.
