# Sanctuary Dome — Legibility Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Sanctuary Core's real server state visible and legible (GUI, particle border, chat, server log) without touching any existing mechanic.

**Architecture:** Hybrid sync — the TileEntity pushes display state (tier / radius / cleanse / status) to clients via a dedicated `SPacketUpdateTileEntity` fired on state change; the Container pushes the fast-changing fuel value via a window property to the open GUI only. A split status model (Protection independent of fuel; Cleanse = RUNNING/STALLED/OFF) drives a dedicated GUI panel, transition chat messages, a shift-right-click status line, a particle border, and gated server-side diagnostic logging.

**Tech Stack:** Minecraft 1.12.2 Forge (MCP 20171003-1.12 mappings), Java 8. **No unit-test framework** (per CLAUDE.md) — every task is verified by `./gradlew build` plus an in-game observation via the DEv 1.2 log monitor and the GUI.

**Spec:** `docs/superpowers/specs/2026-07-20-sanctuary-legibility-design.md`

**Build → test loop (every task's verification):**
1. `./gradlew build` (auto-runs `reobfJar`; `-Xlint:all` on — must be warning-clean for our files).
2. Copy `build/libs/insanetweaks-<ver>.jar` into `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\`, **deleting the previous-version jar** (two jars of one modid = duplicate-mod crash). Version is unchanged during dev, so it overwrites the same filename — confirm only one `insanetweaks-*.jar` is present.
3. Launch the DEv 1.2 client, load the test world, observe. Logs: instance `logs/debug.log` (our `[InsaneTweaks]` markers), `logs/player_actions.log` (interactions).

---

## File Structure

| File | Responsibility |
|------|----------------|
| `sanctuary/SanctuaryStatus.java` (new) | enum: why the dome is/ isn't active — `ACTIVE`, `NO_PYRAMID`, `DIM_BLACKLISTED` |
| `sanctuary/CleanseState.java` (new) | enum: `RUNNING`, `STALLED`, `OFF` + `of(tier, enabled, stalled)` factory |
| `sanctuary/TileEntitySanctuaryCore.java` (modify) | track `statusCode`; display-tag sync; notify-on-change; transition messages; client particle emit; diag logging |
| `sanctuary/gui/ContainerSanctuaryCore.java` (modify) | window-property sync of `fuelStored` to the open GUI |
| `sanctuary/gui/GuiSanctuaryCore.java` (modify) | dedicated procedural panel: status text, fuel gauge, tier-0 hints, slot tooltips, `drawDefaultBackground` |
| `sanctuary/BlockSanctuaryCore.java` (modify) | shift-right-click → server-truth status chat line |
| `config/categories/SanctuaryCategory.java` (modify) | add `debugLogging` flag |
| `assets/insanetweaks/lang/en_us.lang` (modify) | GUI + status + hint strings |

No new packets (uses vanilla `SPacketUpdateTileEntity` + Container window properties). No mechanic file (`scanPyramidTier`, `SanctuaryWorldData`, veto, cleanse math) changes behaviour.

---

## Task 1: Split-status model + server-side diagnostics (verifiable server truth first)

**Rationale:** This task alone lets us confirm — in `debug.log` — that a valid pyramid yields the right tier server-side, even before the GUI is fixed. It directly answers "chyba nie działa" with a fact.

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryStatus.java`
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/CleanseState.java`
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/SanctuaryCategory.java`
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`

- [ ] **Step 1: Create the `SanctuaryStatus` enum**

`SanctuaryStatus.java`:
```java
package com.spege.insanetweaks.sanctuary;

/** Why the dome is (or is not) active. Ordinal is synced to the client. */
public enum SanctuaryStatus {
    ACTIVE,          // tier >= 1, region registered
    NO_PYRAMID,      // no complete pyramid under the core
    DIM_BLACKLISTED; // this dimension is warded (dome inert)

    public static SanctuaryStatus byId(int id) {
        SanctuaryStatus[] v = values();
        return (id >= 0 && id < v.length) ? v[id] : NO_PYRAMID;
    }
}
```

- [ ] **Step 2: Create the `CleanseState` enum**

`CleanseState.java`:
```java
package com.spege.insanetweaks.sanctuary;

/** Display state of the cleanse function. Derived, not persisted. */
public enum CleanseState {
    RUNNING, // enabled, tier active, fuel available
    STALLED, // enabled, tier active, but out of fuel
    OFF;     // disabled by toggle, or no active tier

    public static CleanseState of(int tier, boolean cleanseEnabled, boolean cleanseStalled) {
        if (!cleanseEnabled || tier < 1) { return OFF; }
        return cleanseStalled ? STALLED : RUNNING;
    }
}
```

- [ ] **Step 3: Add the `debugLogging` config flag**

In `SanctuaryCategory.java`, after the `particleBorder` field (before the closing brace):
```java
    @Config.Comment({"Log per-core sanctuary state to the game log on tier/status change.",
            "For debugging whether a pyramid is detected. Read live (no restart)."})
    @Config.Name("Debug Logging")
    public boolean debugLogging = false;
```

- [ ] **Step 4: Add the `statusCode` field + persistence to the TE**

In `TileEntitySanctuaryCore.java`, add the field next to the other state fields (after `initialized`, line ~24):
```java
    private int statusCode = SanctuaryStatus.NO_PYRAMID.ordinal(); // SanctuaryStatus ordinal
```
Add getters (near the other getters, ~line 30):
```java
    public int getStatusCode() { return statusCode; }
    public SanctuaryStatus getStatus() { return SanctuaryStatus.byId(statusCode); }
```
In `readFromNBT` (after the `initialized = ...` line):
```java
        statusCode = c.getInteger("status");
```
In `writeToNBT` (after the `c.setBoolean("init", ...)` line):
```java
        c.setInteger("status", statusCode);
```

- [ ] **Step 5: Set `statusCode` in `update()` and `revalidateAndSync()`**

In `update()`, the dimension-blacklist branch — replace the existing `if (isDimensionBlacklisted...)` block body so it records the status:
```java
        if (com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper.isDimensionBlacklisted(world)) {
            if (getTier() != 0 || statusCode != SanctuaryStatus.DIM_BLACKLISTED.ordinal()) {
                setTier(0); setEffectiveRadius(0);
                statusCode = SanctuaryStatus.DIM_BLACKLISTED.ordinal();
                SanctuaryWorldData.get(world).removeRegion(pos);
                markDirty();
            }
            return;
        }
```
In `revalidateAndSync()`, replace the tail (from `setTier(newTier);` through `markDirty();`) with the following. Note `oldTier` is captured **before** `setTier` so the log only fires on an actual tier change:
```java
        int oldTier = tier;
        setTier(newTier);
        setEffectiveRadius(radius);
        statusCode = (newTier >= 1) ? SanctuaryStatus.ACTIVE.ordinal()
                                    : SanctuaryStatus.NO_PYRAMID.ordinal();
        SanctuaryWorldData.get(world).setRegion(pos, radius); // radius<=0 removes
        markDirty();
        if (com.spege.insanetweaks.config.ModConfig.sanctuary.debugLogging && newTier != oldTier) {
            com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info(
                "[InsaneTweaks] Sanctuary @ (" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + ") dim" + world.provider.getDimension() + ": tier=" + newTier + " radius=" + radius
                + " status=" + SanctuaryStatus.byId(statusCode)
                + " cleanse=" + CleanseState.of(newTier, cleanseEnabled, cleanseStalled));
        }
```

- [ ] **Step 6: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`, no warnings for the touched files.

- [ ] **Step 7: In-game verification (server truth in the log)**

Swap the jar into DEv 1.2. In-game: set `Debug Logging = true` in `config/insanetweaks.cfg` (or via the config UI), reload the world. Place a Sanctuary Core on a diamond 5×5+3×3 pyramid. Within `pyramidRevalidateInterval` ticks the `debug.log` monitor shows:
```
[InsaneTweaks] Sanctuary @ (x,y,z) dim0: tier=2 radius=32 status=ACTIVE cleanse=RUNNING
```
Break a base block → next change logs `tier=1` (or `0`) `status=NO_PYRAMID`. This confirms the mechanic works server-side regardless of the GUI.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryStatus.java \
        src/main/java/com/spege/insanetweaks/sanctuary/CleanseState.java \
        src/main/java/com/spege/insanetweaks/config/categories/SanctuaryCategory.java \
        src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java
git commit -m "feat(sanctuary): split status model + gated server-side diag logging"
```

---

## Task 2: TileEntity → client display sync

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`

- [ ] **Step 1: Add imports**

At the top of `TileEntitySanctuaryCore.java` (with the other imports):
```java
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
```

- [ ] **Step 2: Add the display-tag helpers + packet overrides**

Add these methods to the class (e.g. just below `writeToNBT`). The display tag deliberately **excludes** the inventory — the inventory syncs through the Container, and double-syncing it via the block update causes client slot flicker.
```java
    /** Compact tag of just the client-display fields (no inventory). */
    private NBTTagCompound writeDisplayTag(NBTTagCompound c) {
        c.setInteger("tier", tier);
        c.setInteger("radius", effectiveRadius);
        c.setBoolean("cleanse", cleanseEnabled);
        c.setBoolean("stalled", cleanseStalled);
        c.setInteger("status", statusCode);
        return c;
    }

    private void readDisplayTag(NBTTagCompound c) {
        tier = c.getInteger("tier");
        effectiveRadius = c.getInteger("radius");
        cleanseEnabled = c.getBoolean("cleanse");
        cleanseStalled = c.getBoolean("stalled");
        statusCode = c.getInteger("status");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeDisplayTag(super.getUpdateTag()); // super adds id + x/y/z
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 1, writeDisplayTag(new NBTTagCompound()));
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readDisplayTag(pkt.getNbtCompound());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readDisplayTag(tag); // do NOT call super (would readFromNBT and clear inventory client-side)
    }
```

- [ ] **Step 3: Add the change-detecting sync trigger**

Add these fields near the other private fields:
```java
    // last display snapshot pushed to clients (server-side), for change detection
    private int sentTier = -1, sentRadius = -1, sentStatus = -1;
    private boolean sentCleanse, sentStalled, snapshotInit;
```
Add the method:
```java
    /** Server-side: if any display field changed since the last push, send a block update. */
    private void syncDisplayIfChanged() {
        boolean changed = !snapshotInit
                || tier != sentTier || effectiveRadius != sentRadius || statusCode != sentStatus
                || cleanseEnabled != sentCleanse || cleanseStalled != sentStalled;
        if (!changed) { return; }
        sentTier = tier; sentRadius = effectiveRadius; sentStatus = statusCode;
        sentCleanse = cleanseEnabled; sentStalled = cleanseStalled; snapshotInit = true;
        net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, st, st, 3);
    }
```

- [ ] **Step 4: Call the trigger at the end of the server tick**

In `update()`, add `syncDisplayIfChanged();` as the last statement of the server path (after `runCleanse();`):
```java
        runCleanse();
        syncDisplayIfChanged();
```
Also add it before the two early `return;`s that change display state — specifically at the end of the dimension-blacklist branch, replace `return;` with:
```java
            syncDisplayIfChanged();
            return;
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: In-game verification (GUI now shows real state)**

Swap jar, reload. Open the core GUI over the diamond pyramid → the existing text line now reads `Tier 2  R=32` (was always `Tier 0  R=0`). Break/rebuild a layer → the open GUI updates within ~2s. (Full panel styling comes in Task 4; this task only proves the numbers now arrive on the client.)

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java
git commit -m "feat(sanctuary): sync TE display state to client via block update"
```

---

## Task 3: Container fuel window-property sync

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/gui/ContainerSanctuaryCore.java`
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java` (expose fuel getter publicly)

- [ ] **Step 1: Make the fuel getter public on the TE**

In `TileEntitySanctuaryCore.java` change the existing package-private getter:
```java
    int getFuelStored() { return fuelStored; }
```
to:
```java
    public int getFuelStored() { return fuelStored; }
```

- [ ] **Step 2: Add window-property sync to the Container**

Replace the body of `ContainerSanctuaryCore.java` with (keeps existing slot layout, adds sync):
```java
package com.spege.insanetweaks.sanctuary.gui;

import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerSanctuaryCore extends Container {

    private final TileEntitySanctuaryCore te;
    private int lastFuel = -1;      // server-side change tracking
    private int clientFuel = 0;     // client-side mirror (window property)

    public ContainerSanctuaryCore(InventoryPlayer playerInv, TileEntitySanctuaryCore te) {
        this.te = te;
        addSlotToContainer(new SlotItemHandler(te.getInventory(), TileEntitySanctuaryCore.SLOT_FUEL, 26, 35));
        for (int i = 0; i < TileEntitySanctuaryCore.UPGRADE_SLOTS; i++) {
            addSlotToContainer(new SlotItemHandler(te.getInventory(), 1 + i, 80 + i * 18, 35));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 142));
        }
    }

    public TileEntitySanctuaryCore getTe() { return te; }

    /** Client-side fuel value (0 until first window-property arrives). */
    public int getClientFuel() { return clientFuel; }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        listener.sendWindowProperty(this, 0, clampShort(te.getFuelStored()));
        lastFuel = te.getFuelStored();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int fuel = te.getFuelStored();
        if (fuel != lastFuel) {
            for (IContainerListener l : this.listeners) {
                l.sendWindowProperty(this, 0, clampShort(fuel));
            }
            lastFuel = fuel;
        }
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void updateProgressBar(int id, int data) {
        if (id == 0) { clientFuel = data; }
    }

    private static int clampShort(int v) { return v < 0 ? 0 : (v > 32767 ? 32767 : v); }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return te.getWorld().getTileEntity(te.getPos()) == te
                && player.getDistanceSq(te.getPos()) <= 64.0D;
    }

    @Override
    public net.minecraft.item.ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return net.minecraft.item.ItemStack.EMPTY; // minimal: no shift-click transfer in v1
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: In-game verification**

Swap jar, reload. Put an emerald (default fuel, value 64) in the fuel slot with cleanse running over infested ground → `getClientFuel()` is non-zero while the GUI is open (verified visually once the gauge exists in Task 4; for now confirm no crash and the emerald is consumed as cleanse runs, logged if `debugLogging`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/gui/ContainerSanctuaryCore.java \
        src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java
git commit -m "feat(sanctuary): sync fuel to open GUI via container window property"
```

---

## Task 4: Dedicated GUI panel (status text, fuel gauge, hints, tooltips)

**Design choice:** the placeholder panel is drawn **procedurally** (no dispenser texture, only the 5 slots we use) so mechanics can be iterated without waiting on final art. A themed PNG can replace the procedural draw later by binding a texture in `drawGuiContainerBackgroundLayer`.

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/gui/GuiSanctuaryCore.java`
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Add GUI + status lang strings**

In `en_us.lang`, after the `tile.insanetweaks.sanctuary_core.name=Sanctuary Core` line:
```
gui.insanetweaks.sanctuary.title=Sanctuary Core
gui.insanetweaks.sanctuary.tier=Tier: %d / 4
gui.insanetweaks.sanctuary.protection_on=Protection: ACTIVE  (R %d)
gui.insanetweaks.sanctuary.protection_off=Protection: INACTIVE
gui.insanetweaks.sanctuary.cleanse_running=Cleanse: RUNNING
gui.insanetweaks.sanctuary.cleanse_stalled=Cleanse: STALLED - needs fuel
gui.insanetweaks.sanctuary.cleanse_off=Cleanse: OFF
gui.insanetweaks.sanctuary.fuel=Fuel: %d
gui.insanetweaks.sanctuary.hint_pyramid=Build a 3x3+ pyramid of iron/diamond blocks under the core.
gui.insanetweaks.sanctuary.hint_blacklist=This dimension is warded against sanctuaries.
gui.insanetweaks.sanctuary.slot_fuel=Fuel
gui.insanetweaks.sanctuary.slot_upgrade=Radius Upgrade
```

- [ ] **Step 2: Replace `GuiSanctuaryCore.java` with the procedural panel**

```java
package com.spege.insanetweaks.sanctuary.gui;

import com.spege.insanetweaks.sanctuary.CleanseState;
import com.spege.insanetweaks.sanctuary.SanctuaryStatus;
import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;

public class GuiSanctuaryCore extends GuiContainer {

    private final ContainerSanctuaryCore container;

    public GuiSanctuaryCore(ContainerSanctuaryCore container) {
        super(container);
        this.container = container;
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground(); // dark overlay (fixes HEI warning)
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        // panel body
        drawGradientRect(x, y, x + xSize, y + ySize, 0xF0202028, 0xF03A2A3A);
        drawRect(x, y, x + xSize, y + 1, 0xFF6A4A7A);            // top accent
        // core slots: fuel (26,35) + upgrades (80,98,116,134 ; 35)
        drawSocket(x + 26, y + 35);
        for (int i = 0; i < TileEntitySanctuaryCore.UPGRADE_SLOTS; i++) {
            drawSocket(x + 80 + i * 18, y + 35);
        }
        // player inventory sockets
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) { drawSocket(x + 8 + col * 18, y + 84 + row * 18); }
        }
        for (int col = 0; col < 9; col++) { drawSocket(x + 8 + col * 18, y + 142); }
    }

    /** An 18x18 recessed slot background at the container slot origin (x,y are the -1 border corner). */
    private void drawSocket(int sx, int sy) {
        drawRect(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF120C16); // dark inset
        drawRect(sx, sy, sx + 16, sy + 16, 0xFF473557);          // slot face
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        TileEntitySanctuaryCore te = container.getTe();
        int tier = te.getTier();
        int radius = te.getEffectiveRadius();
        SanctuaryStatus status = te.getStatus();
        CleanseState cleanse = CleanseState.of(tier, te.isCleanseEnabled(), te.isCleanseStalled());

        int white = 0xFFFFFF;
        this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.title"), 8, 6, 0xE0D0F0);
        this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.tier", tier), 8, 18, white);

        // protection line (colored)
        if (tier >= 1) {
            this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.protection_on", radius), 8, 30, 0x55FF55);
        } else {
            this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.protection_off"), 8, 30, 0xAAAAAA);
        }

        // cleanse line (colored)
        switch (cleanse) {
            case RUNNING: this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.cleanse_running"), 8, 42, 0x55FF55); break;
            case STALLED: this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.cleanse_stalled"), 8, 42, 0xFF5555); break;
            default:      this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.cleanse_off"), 8, 42, 0xAAAAAA); break;
        }

        // hint when inactive
        if (status == SanctuaryStatus.NO_PYRAMID) {
            this.fontRenderer.drawSplitString(I18n.format("gui.insanetweaks.sanctuary.hint_pyramid"), 8, 56, 160, 0xC0A0C0);
        } else if (status == SanctuaryStatus.DIM_BLACKLISTED) {
            this.fontRenderer.drawSplitString(I18n.format("gui.insanetweaks.sanctuary.hint_blacklist"), 8, 56, 160, 0xC0A0C0);
        }

        // fuel gauge (from window property) drawn near the fuel slot
        int fuel = container.getClientFuel();
        this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.fuel", fuel), 8, 68, 0xC0C0C0);
        drawFuelGauge(8, 78, fuel);

        // slot tooltips
        drawSlotTooltip(mouseX, mouseY, 26, 35, I18n.format("gui.insanetweaks.sanctuary.slot_fuel"));
        for (int i = 0; i < TileEntitySanctuaryCore.UPGRADE_SLOTS; i++) {
            drawSlotTooltip(mouseX, mouseY, 80 + i * 18, 35, I18n.format("gui.insanetweaks.sanctuary.slot_upgrade"));
        }
    }

    /** A simple 8-segment bar; full at >=64 conversions remaining. */
    private void drawFuelGauge(int gx, int gy, int fuel) {
        int segments = 8;
        int filled = Math.min(segments, (fuel + 7) / 8); // 8 conversions per segment, ceil
        for (int i = 0; i < segments; i++) {
            int color = (i < filled) ? 0xFF55FF55 : 0xFF303030;
            int sx = gx + i * 6;
            drawRect(sx, gy, sx + 5, gy + 5, color);
        }
    }

    /** mouseX/Y are screen coords; slotX/Y are container-local slot origins. */
    private void drawSlotTooltip(int mouseX, int mouseY, int slotX, int slotY, String text) {
        int gx = (this.width - this.xSize) / 2 + slotX;
        int gy = (this.height - this.ySize) / 2 + slotY;
        if (mouseX >= gx && mouseX < gx + 16 && mouseY >= gy && mouseY < gy + 16) {
            // only show our label when the slot is empty (item tooltip wins otherwise)
            drawHoveringText(java.util.Collections.singletonList(text), mouseX, mouseY);
        }
    }
}
```
Note: `drawGuiContainerForegroundLayer` coordinates are container-local, but `drawHoveringText`/`renderHoveredToolTip` expect screen coords — the foreground layer is translated by the GUI origin, so `drawHoveringText(list, mouseX - guiLeft, mouseY - guiTop)` is the usual idiom. Since we translate manually in `drawSlotTooltip` by comparing against screen coords and call `drawHoveringText` inside the foreground (translated) space, pass **local** mouse coords: replace the `drawHoveringText(... mouseX, mouseY)` with `drawHoveringText(java.util.Collections.singletonList(text), mouseX - (this.width - this.xSize) / 2, mouseY - (this.height - this.ySize) / 2);`

- [ ] **Step 3: Apply the tooltip-coordinate correction**

Edit `drawSlotTooltip` so the hover text uses GUI-local mouse coordinates:
```java
    private void drawSlotTooltip(int mouseX, int mouseY, int slotX, int slotY, String text) {
        int left = (this.width - this.xSize) / 2;
        int top = (this.height - this.ySize) / 2;
        int gx = left + slotX;
        int gy = top + slotY;
        if (mouseX >= gx && mouseX < gx + 16 && mouseY >= gy && mouseY < gy + 16) {
            drawHoveringText(java.util.Collections.singletonList(text), mouseX - left, mouseY - top);
        }
    }
```

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: In-game verification**

Swap jar, reload. Open the core GUI:
- Panel is dark/themed, **not** the dispenser grid; only the fuel + 4 upgrade sockets show.
- Over the diamond pyramid: `Tier: 2 / 4`, green `Protection: ACTIVE (R 32)`, `Cleanse: RUNNING`.
- With no pyramid: grey `Protection: INACTIVE` + the build hint.
- Fuel gauge fills with emeralds in the fuel slot; empties as cleanse consumes them.
- Hovering the empty fuel/upgrade slots shows the `Fuel` / `Radius Upgrade` labels.
- HEI no longer logs the "did not draw the dark background layer" warning.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/gui/GuiSanctuaryCore.java \
        src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "feat(sanctuary): dedicated GUI panel with split status, fuel gauge, hints"
```

---

## Task 5: Shift-right-click status line + state-transition chat messages

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java`
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Add chat lang strings**

In `en_us.lang`, after the slot strings from Task 4:
```
msg.insanetweaks.sanctuary.status=Sanctuary: Tier %d, R%d, Protection %s, Cleanse %s, Fuel %d
msg.insanetweaks.sanctuary.activated=Sanctuary activated - Tier %d, radius %d
msg.insanetweaks.sanctuary.deactivated=Sanctuary deactivated - pyramid incomplete
msg.insanetweaks.sanctuary.stalled=Sanctuary cleanse stalled - out of fuel
msg.insanetweaks.sanctuary.resumed=Sanctuary cleanse resumed
```

- [ ] **Step 2: Shift-right-click status in the Block**

In `BlockSanctuaryCore.onBlockActivated`, replace the body with:
```java
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileEntitySanctuaryCore) {
            TileEntitySanctuaryCore te = (TileEntitySanctuaryCore) world.getTileEntity(pos);
            if (player.isSneaking()) {
                te.sendStatusTo(player);
            } else {
                player.openGui(com.spege.insanetweaks.InsaneTweaksMod.INSTANCE,
                        com.spege.insanetweaks.InsaneTweaksMod.GUI_ID_SANCTUARY, world, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return true;
```

- [ ] **Step 3: Add `sendStatusTo` + transition messaging to the TE**

Add to `TileEntitySanctuaryCore.java`:
```java
    public void sendStatusTo(net.minecraft.entity.player.EntityPlayer player) {
        CleanseState cs = CleanseState.of(tier, cleanseEnabled, cleanseStalled);
        player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                "msg.insanetweaks.sanctuary.status", tier, effectiveRadius,
                (tier >= 1 ? "ON" : "OFF"), cs.name(), fuelStored));
    }

    private void announce(String key, Object... args) {
        if (world == null || world.isRemote) { return; }
        net.minecraft.util.text.TextComponentTranslation msg =
                new net.minecraft.util.text.TextComponentTranslation(key, args);
        double r = Math.max(effectiveRadius, 8);
        for (net.minecraft.entity.player.EntityPlayer p : world.playerEntities) {
            double dx = p.posX - (pos.getX() + 0.5);
            double dz = p.posZ - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= r * r) { p.sendMessage(msg); }
        }
    }
```

- [ ] **Step 4: Fire transition messages from `syncDisplayIfChanged`**

In `syncDisplayIfChanged()` (from Task 2), between computing `changed` and overwriting the snapshot, add transition detection using the *old* snapshot values. Replace the method body with:
```java
    private void syncDisplayIfChanged() {
        boolean changed = !snapshotInit
                || tier != sentTier || effectiveRadius != sentRadius || statusCode != sentStatus
                || cleanseEnabled != sentCleanse || cleanseStalled != sentStalled;
        if (!changed) { return; }

        if (snapshotInit) { // don't announce on first load
            boolean wasActive = sentTier >= 1;
            boolean nowActive = tier >= 1;
            if (!wasActive && nowActive) { announce("msg.insanetweaks.sanctuary.activated", tier, effectiveRadius); }
            else if (wasActive && !nowActive) { announce("msg.insanetweaks.sanctuary.deactivated"); }
            if (!sentStalled && cleanseStalled) { announce("msg.insanetweaks.sanctuary.stalled"); }
            else if (sentStalled && !cleanseStalled && tier >= 1 && cleanseEnabled) { announce("msg.insanetweaks.sanctuary.resumed"); }
        }

        sentTier = tier; sentRadius = effectiveRadius; sentStatus = statusCode;
        sentCleanse = cleanseEnabled; sentStalled = cleanseStalled; snapshotInit = true;
        net.minecraft.block.state.IBlockState st = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, st, st, 3);
    }
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: In-game verification**

Swap jar, reload.
- Shift-right-click the core → chat: `Sanctuary: Tier 2, R32, Protection ON, Cleanse RUNNING, Fuel 128`.
- Complete a pyramid → nearby players see `Sanctuary activated - Tier 2, radius 32` once.
- Break a base layer → `Sanctuary deactivated - pyramid incomplete`.
- Run cleanse out of fuel → `Sanctuary cleanse stalled - out of fuel`; refuel → `Sanctuary cleanse resumed`. Each fires once per transition (no spam).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java \
        src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java \
        src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "feat(sanctuary): shift-click status line + one-shot transition messages"
```

---

## Task 6: Particle border of the active region

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`

- [ ] **Step 1: Add a client-side particle branch to `update()`**

Currently `update()` returns immediately when `world.isRemote`. Replace the first guard:
```java
        if (world == null || world.isRemote) { return; }
```
with:
```java
        if (world == null) { return; }
        if (world.isRemote) { clientParticleTick(); return; }
```

- [ ] **Step 2: Add the client particle method**

The client TE has synced `tier`/`effectiveRadius` (Task 2). Emit a bounded set of points on the arc nearest the player, so a large dome never spawns a full ring:
```java
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private int particleTimer;

    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    private void clientParticleTick() {
        if (!com.spege.insanetweaks.config.ModConfig.sanctuary.particleBorder) { return; }
        if (tier < 1 || effectiveRadius <= 0) { return; }
        if (++particleTimer < 15) { return; }
        particleTimer = 0;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        if (mc.player == null) { return; }
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5, cy = pos.getY() + 0.5;
        double r = effectiveRadius;
        // angle from core to player; draw a short arc of points centred on it
        double base = Math.atan2(mc.player.posZ - cz, mc.player.posX - cx);
        int points = 16;
        double spread = Math.PI / 2; // 90-degree arc facing the player
        for (int i = 0; i < points; i++) {
            double a = base - spread / 2 + spread * i / (points - 1);
            double px = cx + Math.cos(a) * r;
            double pz = cz + Math.sin(a) * r;
            world.spawnParticle(net.minecraft.util.EnumParticleTypes.ENCHANTMENT_TABLE,
                    px, cy, pz, 0.0D, 0.05D, 0.0D);
        }
    }
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: In-game verification**

Swap jar, reload. Stand inside an active dome and look around → a faint enchant-glyph arc traces the boundary at `effectiveRadius` on the side toward you, refreshing ~1.3×/s. Walk around the core → the arc follows. Set `Particle Border = false` → particles stop. No FPS impact at R64 (only 16 particles/emit).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java
git commit -m "feat(sanctuary): client particle border tracing the active region"
```

---

## Post-implementation

- [ ] Update `CLAUDE.md` Architecture note if the sync pattern is worth recording (TE display-tag sync + container window property).
- [ ] (Deferred asset task, non-blocking) Replace the procedural GUI panel with a themed `sanctuary_core.png` bound in `drawGuiContainerBackgroundLayer`, keeping the same slot coordinates.
- [ ] Leave `sanctuary.debugLogging` **false** by default; flip on only when diagnosing detection.

---

## Self-Review (author checklist — completed)

- **Spec coverage:** §1 sync → Tasks 2+3; §2 status model → Task 1 (enums) + Task 4 (display); §3 GUI → Task 4; §4 particles → Task 6; §5 interaction/transitions → Task 5; §6 diagnostics → Task 1; §7 config → Task 1. All covered.
- **Placeholder scan:** no TBD/TODO; every code step shows full code.
- **Type consistency:** `SanctuaryStatus.byId`, `CleanseState.of(tier,enabled,stalled)`, `getStatus()`, `getClientFuel()`, `sendStatusTo()`, `syncDisplayIfChanged()`, `clientParticleTick()` used consistently across tasks. `statusCode` int field synced in the display tag and persisted in NBT. Fuel window-property id = 0 in both `sendWindowProperty` and `updateProgressBar`.
