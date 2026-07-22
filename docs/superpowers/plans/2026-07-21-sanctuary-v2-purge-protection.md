# Sanctuary Dome v2 — Purge Fire + Block Protection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make an active Sanctuary Dome actively burn parasites inside it, veto parasite terrain-destruction in the zone, expose gated operational debug logging, and disable the pulsing dome particle pending a static renderer.

**Architecture:** Two new server-side Forge event handlers — Purge Fire on `LivingUpdateEvent` (Aegis-style fast-fire against `EntityParasiteBase` in range) and a block-break veto on the cancelable `LivingDestroyBlockEvent` — both driven by the existing per-world `SanctuaryWorldData` region registry. A shared `SanctuaryRegionHelper.isSrpParasite`/`isInPurgeRange` and a throttled `SanctuaryDebug` logger back everything. No mixins, no GUI/lang changes.

**Tech Stack:** Minecraft 1.12.2 Forge (MCP 20171003-1.12), Java 8. **No unit-test framework** — every task is verified by `./gradlew build` plus in-game observation via the DEv 1.2 `debug.log` monitor.

**Spec:** `docs/superpowers/specs/2026-07-21-sanctuary-v2-purge-protection-design.md`

**Build → test loop (every task):** `./gradlew build` (auto `reobfJar`, `-Xlint:all` — touched files must add no new warnings) → copy `build/libs/insanetweaks-1.2.0.jar` into `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\` replacing the old jar → launch, enable `sanctuary.debugLogging`, observe `debug.log`.

---

## File Structure

| File | Responsibility |
|------|----------------|
| `config/categories/SanctuaryCategory.java` (modify) | 5 new config fields |
| `sanctuary/SanctuaryWorldData.java` (modify) | `isInsideCapped(x,z,cap)` cylinder test |
| `sanctuary/SanctuaryRegionHelper.java` (modify) | shared `isSrpParasite(Entity)` + `isInPurgeRange(World,x,z)` |
| `sanctuary/SanctuarySpawnVetoHandler.java` (modify) | delegate to shared `isSrpParasite`; gated `spawn-vetoed` log |
| `sanctuary/SanctuaryDebug.java` (new) | throttled, category-keyed gated logger |
| `sanctuary/SanctuaryPurgeFireHandler.java` (new) | `LivingUpdateEvent` fast-fire + `purge-fire` log |
| `sanctuary/SanctuaryBlockBreakVetoHandler.java` (new) | `LivingDestroyBlockEvent` veto + `grief-vetoed` log |
| `sanctuary/TileEntitySanctuaryCore.java` (modify) | `cleansed` log; `in-zone` debug summary; disable dome particle |
| `InsaneTweaksMod.java` (modify) | register the two new handlers under `enableSanctuary` |

---

## Task 1: Config fields + shared parasite/range helpers

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/SanctuaryCategory.java`
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryWorldData.java`
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryRegionHelper.java`
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuarySpawnVetoHandler.java`

- [ ] **Step 1: Add the 5 config fields**

In `SanctuaryCategory.java`, after the existing `debugLogging` field (before the class closing brace):
```java
    @Config.Comment("Purge Fire: an active sanctuary ignites/damages parasites inside it. Read live.")
    @Config.Name("Enable Purge Fire")
    public boolean enablePurgeFire = true;

    @Config.Comment("Fire damage dealt to each parasite per Purge Fire cadence. Read live.")
    @Config.Name("Purge Fire Damage")
    @Config.RangeDouble(min = 0.0D, max = 100.0D)
    public double purgeFireDamage = 1.0D;

    @Config.Comment("Ticks between Purge Fire damage applications (10 = 0.5s, Aegis parity). Read live.")
    @Config.Name("Purge Fire Interval")
    @Config.RangeInt(min = 1, max = 200)
    public int purgeFireInterval = 10;

    @Config.Comment("Hard cap (blocks) on the Purge Fire radius, regardless of protection radius. Read live.")
    @Config.Name("Purge Fire Radius Cap")
    @Config.RangeInt(min = 1, max = 128)
    public int purgeFireRadiusCap = 128;

    @Config.Comment("Veto parasite block-breaking/griefing inside an active sanctuary. Read live.")
    @Config.Name("Veto Block Break")
    public boolean vetoBlockBreak = true;
```

- [ ] **Step 2: Add `isInsideCapped` to `SanctuaryWorldData`**

In `SanctuaryWorldData.java`, right after the existing `isInside(int x, int z)` method:
```java
    /** Cylinder test using min(regionRadius, radiusCap) — for effects with a smaller cap than protection. */
    public boolean isInsideCapped(int x, int z, int radiusCap) {
        for (int i = 0; i < regions.size(); i++) {
            int[] r = regions.get(i);
            int eff = Math.min(r[3], radiusCap);
            long dx = x - r[0];
            long dz = z - r[2];
            long rr = (long) eff * eff;
            if (dx * dx + dz * dz <= rr) {
                return true;
            }
        }
        return false;
    }
```

- [ ] **Step 3: Add shared `isSrpParasite` + `isInPurgeRange` to `SanctuaryRegionHelper`**

In `SanctuaryRegionHelper.java`, add these methods (and note the new import is unnecessary — use the FQN style already in the file). Insert before the closing brace:
```java
    private static final String SRP_PARASITE_BASE =
            "com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase";

    /** True if the entity's class chain includes SRP's EntityParasiteBase (covers SRP, SRPExtra, SimWizard). */
    public static boolean isSrpParasite(net.minecraft.entity.Entity e) {
        if (e == null) {
            return false;
        }
        Class<?> c = e.getClass();
        while (c != null) {
            if (c.getName().equals(SRP_PARASITE_BASE)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    /** True when (x,z) is inside any active sanctuary within min(regionRadius, purgeFireRadiusCap). */
    public static boolean isInPurgeRange(World world, int x, int z) {
        if (world == null || world.isRemote) {
            return false;
        }
        if (isDimensionBlacklisted(world)) {
            return false;
        }
        return SanctuaryWorldData.get(world).isInsideCapped(x, z, ModConfig.sanctuary.purgeFireRadiusCap);
    }
```

- [ ] **Step 4: Delegate the spawn veto's detection to the shared helper**

In `SanctuarySpawnVetoHandler.java`: remove the private `SRP_PARASITE_BASE` constant and the private `isSrpParasite(Entity)` method, and change the call site. Specifically, replace:
```java
        Entity e = event.getEntityLiving();
        if (e == null || !isSrpParasite(e)) {
            return;
        }
```
with:
```java
        Entity e = event.getEntityLiving();
        if (e == null || !SanctuaryRegionHelper.isSrpParasite(e)) {
            return;
        }
```
Then delete the now-unused private constant `SRP_PARASITE_BASE` and the private `isSrpParasite` method at the bottom of the class. Leave everything else (the `@SubscribeEvent`, priority, `isProtected` call) unchanged.

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, no new warnings in the touched files.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/config/categories/SanctuaryCategory.java \
        src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryWorldData.java \
        src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryRegionHelper.java \
        src/main/java/com/spege/insanetweaks/sanctuary/SanctuarySpawnVetoHandler.java
git commit -m "feat(sanctuary): v2 config + shared isSrpParasite/isInPurgeRange helpers"
```

---

## Task 2: Throttled debug logger

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryDebug.java`

- [ ] **Step 1: Create `SanctuaryDebug.java`**

```java
package com.spege.insanetweaks.sanctuary;

import java.util.HashMap;
import java.util.Map;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

/**
 * Throttled, category-keyed debug logger for the Sanctuary module. No-op unless
 * {@code sanctuary.debugLogging} is on; each category prints at most once per
 * {@link #THROTTLE_TICKS} to keep high-frequency events (spawn/grief/cleanse/burn)
 * from flooding the log. Throttle state is global per category (a representative sample).
 */
public final class SanctuaryDebug {

    /** Minimum world-ticks between two logs of the same category (~1s at 20 tps). */
    public static final long THROTTLE_TICKS = 20L;

    private static final Map<String, Long> LAST = new HashMap<String, Long>();

    private SanctuaryDebug() {}

    public static void log(long worldTime, String category, String message) {
        if (!ModConfig.sanctuary.debugLogging) {
            return;
        }
        Long last = LAST.get(category);
        if (last != null && worldTime - last.longValue() < THROTTLE_TICKS) {
            return;
        }
        LAST.put(category, Long.valueOf(worldTime));
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Sanctuary/" + category + ": " + message);
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryDebug.java
git commit -m "feat(sanctuary): throttled category-keyed debug logger"
```

---

## Task 3: Purge Fire handler

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryPurgeFireHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`

- [ ] **Step 1: Create `SanctuaryPurgeFireHandler.java`**

```java
package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Purge Fire: an active sanctuary ignites and damages parasites inside it. Event-driven
 * (mirrors {@code AegisEventHandler}'s fast-fire) so cost scales with entity count and is a
 * cheap reject for non-parasites — no per-core AABB scanning. Server side only.
 */
public class SanctuaryPurgeFireHandler {

    @SubscribeEvent
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!ModConfig.sanctuary.enablePurgeFire) {
            return;
        }
        EntityLivingBase e = event.getEntityLiving();
        World world = e.world;
        if (world == null || world.isRemote) {
            return;
        }
        if (!SanctuaryRegionHelper.isSrpParasite(e)) {
            return;
        }
        int x = (int) Math.floor(e.posX);
        int z = (int) Math.floor(e.posZ);
        if (!SanctuaryRegionHelper.isInPurgeRange(world, x, z)) {
            return;
        }
        if (e.ticksExisted % ModConfig.sanctuary.purgeFireInterval == 0) {
            e.hurtResistantTime = 0; // break i-frames so the DoT actually lands each cadence
            e.attackEntityFrom(DamageSource.IN_FIRE, (float) ModConfig.sanctuary.purgeFireDamage);
            SanctuaryDebug.log(world.getTotalWorldTime(), "purge-fire",
                    e.getName() + " hp=" + ((int) e.getHealth())
                    + " @(" + x + "," + ((int) Math.floor(e.posY)) + "," + z + ")");
        }
        if (!e.isBurning()) {
            e.setFire(2); // maintain the visual fire between damage ticks
        }
    }
}
```

- [ ] **Step 2: Register the handler in `InsaneTweaksMod.init`**

In `InsaneTweaksMod.java`, find the sanctuary spawn-veto registration (~line 402-405):
```java
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary
                && Loader.isModLoaded(SRP_MODID)) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.sanctuary.SanctuarySpawnVetoHandler());
        }
```
Add the Purge Fire registration inside that same `if` block, after the spawn-veto line:
```java
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.sanctuary.SanctuaryPurgeFireHandler());
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: In-game verification**

Swap jar, reload, enable `debugLogging`. Stand parasites inside an active dome → they ignite and lose ~2 HP/s; `debug.log` shows throttled `Sanctuary/purge-fire:` lines. A parasite walked outside `min(radius,128)` stops burning. A player minion/thrall inside is unaffected.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryPurgeFireHandler.java \
        src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat(sanctuary): event-driven Purge Fire DoT against in-range parasites"
```

---

## Task 4: Block-break veto handler

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryBlockBreakVetoHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`

- [ ] **Step 1: Create `SanctuaryBlockBreakVetoHandler.java`**

```java
package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Vetoes parasite terrain destruction inside an active sanctuary. SRP's
 * {@code EntityParasiteBase.skillBreakBlocks()} fires the cancelable Forge
 * {@link LivingDestroyBlockEvent} per block, so a plain handler suffices (no mixin).
 * Does NOT cover {@code AIDisableBeaconIki} (bypasses the event) — tracked as a follow-up.
 */
public class SanctuaryBlockBreakVetoHandler {

    @SubscribeEvent
    public void onDestroyBlock(LivingDestroyBlockEvent event) {
        if (!ModConfig.sanctuary.vetoBlockBreak) {
            return;
        }
        EntityLivingBase e = event.getEntityLiving();
        if (e == null || e.world.isRemote) {
            return;
        }
        if (!SanctuaryRegionHelper.isSrpParasite(e)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (SanctuaryRegionHelper.isProtected(e.world, pos)) {
            event.setCanceled(true);
            SanctuaryDebug.log(e.world.getTotalWorldTime(), "grief-vetoed",
                    e.getName() + " break @(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")");
        }
    }
}
```

- [ ] **Step 2: Register the handler in `InsaneTweaksMod.init`**

In the same sanctuary `if` block used in Task 3 Step 2, after the Purge Fire registration line, add:
```java
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.sanctuary.SanctuaryBlockBreakVetoHandler());
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: In-game verification**

Swap jar, reload. A block-breaking parasite (e.g. a breaker/adapted type) inside the region cannot destroy blocks; `debug.log` shows `Sanctuary/grief-vetoed:`. The same parasite outside the region breaks blocks normally. Toggle `vetoBlockBreak` off live → breaking resumes without restart.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryBlockBreakVetoHandler.java \
        src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat(sanctuary): veto parasite block-breaking in-region via LivingDestroyBlockEvent"
```

---

## Task 5: Remaining debug emitters + disable dome particle

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuarySpawnVetoHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`

- [ ] **Step 1: Add the `spawn-vetoed` debug log**

In `SanctuarySpawnVetoHandler.java`, where the spawn is denied — change:
```java
        if (SanctuaryRegionHelper.isProtected(event.getWorld(),
                (int) Math.floor(event.getX()), (int) Math.floor(event.getZ()))) {
            event.setResult(Event.Result.DENY);
        }
```
to:
```java
        if (SanctuaryRegionHelper.isProtected(event.getWorld(),
                (int) Math.floor(event.getX()), (int) Math.floor(event.getZ()))) {
            event.setResult(Event.Result.DENY);
            SanctuaryDebug.log(event.getWorld().getTotalWorldTime(), "spawn-vetoed",
                    e.getName() + " @(" + ((int) Math.floor(event.getX())) + ","
                    + ((int) Math.floor(event.getY())) + "," + ((int) Math.floor(event.getZ())) + ")");
        }
```
(`e` is the `Entity` local already resolved earlier in the method.)

- [ ] **Step 2: Add the `cleansed` debug log in the TE cleanse path**

In `TileEntitySanctuaryCore.java`, inside `runCleanse()`, the current success line is:
```java
            if (com.spege.insanetweaks.sanctuary.SanctuaryCleanseHelper.tryCleanse(world, p)) { converted++; }
```
Replace it with (capture the SRP block id before reverting, log on success):
```java
            net.minecraft.util.ResourceLocation cleansedId = world.getBlockState(p).getBlock().getRegistryName();
            if (com.spege.insanetweaks.sanctuary.SanctuaryCleanseHelper.tryCleanse(world, p)) {
                converted++;
                com.spege.insanetweaks.sanctuary.SanctuaryDebug.log(world.getTotalWorldTime(), "cleansed",
                        "@(" + p.getX() + "," + p.getY() + "," + p.getZ() + ") " + cleansedId);
            }
```

- [ ] **Step 3: Add the `in-zone` debug summary + call it on revalidate**

In `TileEntitySanctuaryCore.java`, add this method (e.g. right after `revalidateAndSync`):
```java
    /** Debug-only: counts parasites in the purge cylinder and logs a summary. Runs ONLY when
     *  debugLogging is on, so the AABB scan has no cost in normal play. */
    private void logParasitesInZoneDebug() {
        if (!com.spege.insanetweaks.config.ModConfig.sanctuary.debugLogging || tier < 1) {
            return;
        }
        int cap = Math.min(effectiveRadius, com.spege.insanetweaks.config.ModConfig.sanctuary.purgeFireRadiusCap);
        net.minecraft.util.math.AxisAlignedBB box = new net.minecraft.util.math.AxisAlignedBB(
                pos.getX() - cap, 0, pos.getZ() - cap,
                pos.getX() + cap + 1, world.getHeight(), pos.getZ() + cap + 1);
        java.util.List<net.minecraft.entity.EntityLivingBase> parasites =
                world.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class, box,
                        new com.google.common.base.Predicate<net.minecraft.entity.EntityLivingBase>() {
                            @Override
                            public boolean apply(net.minecraft.entity.EntityLivingBase ent) {
                                return com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper.isSrpParasite(ent);
                            }
                        });
        com.spege.insanetweaks.sanctuary.SanctuaryDebug.log(world.getTotalWorldTime(), "in-zone",
                "parasitesInZone=" + parasites.size() + " tier=" + tier + " r=" + effectiveRadius
                + " purgeFire=" + (com.spege.insanetweaks.config.ModConfig.sanctuary.enablePurgeFire ? "on" : "off"));
    }
```
Then, in `update()`, the revalidate block currently reads:
```java
        if (++pyramidTickCounter >= com.spege.insanetweaks.config.ModConfig.sanctuary.pyramidRevalidateInterval) {
            pyramidTickCounter = 0;
            revalidateAndSync();
        }
```
Change it to also emit the summary:
```java
        if (++pyramidTickCounter >= com.spege.insanetweaks.config.ModConfig.sanctuary.pyramidRevalidateInterval) {
            pyramidTickCounter = 0;
            revalidateAndSync();
            logParasitesInZoneDebug();
        }
```

- [ ] **Step 4: Disable the dome particle (interim)**

In `TileEntitySanctuaryCore.java`, DELETE the `particleTimer` field declaration:
```java
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private int particleTimer;
```
and replace the entire body of `clientParticleTick()` with a no-op:
```java
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private void clientParticleTick() {
        // Dome particle disabled: EBW's SPHERE inherently expands (rendered scale = age/maxAge) and
        // cannot be static, so it pulsed constantly. A dedicated static dome renderer is tracked
        // separately. Left as a no-op so the client tick wiring + particleBorder config stay in place.
    }
```
(The `update()` client branch still calls `clientParticleTick()`; it is now a harmless no-op. Removing `particleTimer` avoids an unused-field warning under `-Xlint:all`.)

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, no new warnings.

- [ ] **Step 6: In-game verification**

Swap jar, reload, `debugLogging` on. `debug.log` now shows, throttled: `Sanctuary/in-zone: parasitesInZone=N ...` each revalidate, `Sanctuary/spawn-vetoed:` on denied spawns, `Sanctuary/cleansed: @... srparasites:...` as infested blocks revert, alongside `purge-fire`/`grief-vetoed` from Tasks 3–4. The dome particle no longer appears (no pulse).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/SanctuarySpawnVetoHandler.java \
        src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java
git commit -m "feat(sanctuary): spawn/cleanse/in-zone debug logs; disable pulsing dome particle"
```

---

## Self-Review (author checklist — completed)

- **Spec coverage:** §1 Purge Fire → Task 3 (+ helpers Task 1); §2 block-break veto → Task 4; §3 config → Task 1; §4 debug logging → Task 2 (util) + Tasks 3/4/5 (emitters); §5 dome interim-off → Task 5 Step 4; cleanse-list "no change" → confirmed in spec, no task needed. All covered.
- **Placeholder scan:** none — every step shows full code.
- **Type consistency:** `SanctuaryRegionHelper.isSrpParasite(Entity)`, `isInPurgeRange(World,int,int)`, `SanctuaryWorldData.isInsideCapped(int,int,int)`, `SanctuaryDebug.log(long,String,String)` used identically across tasks. Config names (`enablePurgeFire`, `purgeFireDamage` [double], `purgeFireInterval` [int], `purgeFireRadiusCap` [int], `vetoBlockBreak`) match every reference. `purgeFireDamage` is cast `(float)` at the one `attackEntityFrom` call. Handlers registered in the existing `enableSanctuary && Loader.isModLoaded(SRP_MODID)` block.
