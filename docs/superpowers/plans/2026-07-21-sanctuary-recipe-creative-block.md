# Sanctuary Nexus Recipe + Creative Sanctuary Block — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add (1) a shaped crafting recipe for the Sanctuary Nexus, and (2) a separate creative-only "Creative Sanctuary" block that is instantly active at a GUI-slider-chosen radius (16–256), with no tier/fuel/ritual.

**Architecture:** The recipe is a programmatic `ShapedOreRecipe` in `ModRecipes`, gated on SRP+ASC. The creative block is a new `Block` that reuses the existing `TileEntitySanctuaryCore` in a new "creative forced" mode (`creativeRadius > 0` → force tier 4 + that radius + ACTIVE, skip the ritual). That reuse means the dome (TESR), purge fire, spawn veto, cleanse, and block-break veto all work with zero new effect code. The radius is picked in a slots-less GUI whose slider sends a `PacketSetSanctuaryRadius` to the server TE.

**Tech Stack:** Minecraft 1.12.2 Forge (Java 8). No unit-test suite — verification is `./gradlew build` (compile, `-Xlint:all`) + in-game (copy jar into DEv 1.2 `mods/`, launch). Set `sanctuary.debugLogging=true` while testing.

**Deploy (reused by in-game steps):**
```bash
cp -f build/libs/insanetweaks-1.2.0.jar "/c/Users/spege/curseforge/minecraft/Instances/DEv 1.2/mods/insanetweaks-1.2.0.jar"
```

---

## File Structure

- **Modify** `sanctuary/TileEntitySanctuaryCore.java` — add `creativeRadius` field + NBT + setter; branch `revalidateAndSync`/`update` on it.
- **Modify** `init/ModRecipes.java` — add the Nexus shaped recipe (gated).
- **Create** `sanctuary/BlockCreativeSanctuary.java` — the creative block (extends `BlockSanctuaryCore`, overrides placement + activation).
- **Modify** `init/ModBlocks.java` — register the creative block + its ItemBlock + model.
- **Create** `network/PacketSetSanctuaryRadius.java` — client→server radius packet.
- **Modify** `network/InsaneTweaksNetwork.java` — register the packet (id 2).
- **Create** `sanctuary/gui/ContainerCreativeSanctuary.java` — slots-less container, syncs current radius.
- **Create** `sanctuary/gui/GuiCreativeSanctuary.java` — slider GUI (16–256).
- **Modify** `InsaneTweaksMod.java` — `GUI_ID_CREATIVE_SANCTUARY = 4` + handler branches.
- **Create** resource JSONs: `blockstates/creative_sanctuary.json`, `models/block/creative_sanctuary.json`, `models/item/creative_sanctuary.json` (mirror the sanctuary_core resources).
- **Modify** `lang/en_us.lang` — creative block name + GUI title.

---

## Task 1: TileEntity — creative forced mode

**Files:** Modify `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`

- [ ] **Step 1: Add the field + getter/setter**

After `private int ritualTicks;` add:

```java
    private int creativeRadius; // 0 = normal ritual mode; >0 = creative forced (tier 4 at this radius)
```

Add near `getProgress()`:

```java
    public int getCreativeRadius() { return creativeRadius; }

    /** Creative-only: force this Nexus active at a fixed radius (16..256), bypassing the ritual. */
    public void setCreativeRadius(int r) {
        this.creativeRadius = r <= 0 ? 0 : Math.max(16, Math.min(256, r));
        markDirty();
        if (world != null && !world.isRemote) {
            revalidateAndSync();
        }
    }
```

- [ ] **Step 2: Persist in NBT**

In `writeToNBT` add (next to `c.setInteger("progress", progress);`):

```java
        c.setInteger("creativeRadius", creativeRadius);
```

In `readFromNBT` add (next to `progress = c.getInteger("progress");`):

```java
        creativeRadius = c.getInteger("creativeRadius");
```

- [ ] **Step 3: Branch `revalidateAndSync` on creative mode**

At the very start of `revalidateAndSync()` (before `int newTier = tierFromProgress(progress);`), insert:

```java
        if (creativeRadius > 0) {
            setTier(4);
            setEffectiveRadius(Math.min(256, creativeRadius));
            statusCode = SanctuaryStatus.ACTIVE.ordinal();
            SanctuaryWorldData.get(world).setRegion(pos, effectiveRadius);
            markDirty();
            return;
        }
```

- [ ] **Step 4: Skip the ritual in creative mode**

In `update()`, change the `tickRitual();` call to:

```java
        if (creativeRadius <= 0) { tickRitual(); }
```

- [ ] **Step 5: Compile**

Run: `./gradlew build 2>&1 | tail -8` → Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java
git commit -m "feat(sanctuary): TileEntity creative-forced mode (fixed radius, no ritual)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Nexus crafting recipe

**Files:** Modify `src/main/java/com/spege/insanetweaks/init/ModRecipes.java`

- [ ] **Step 1: Add the recipe at the end of `registerRecipes`**

Insert just before the closing brace of `registerRecipes(...)` (after the last battlemage-boots loop, before line `    }`):

```java
        // ---------------------------------------------------------------------
        // Sanctuary Nexus. Ingredients come from SRP + Ancient Spellcraft, so gate on both
        // (safeItem throws if an ingredient is missing). Output is the sanctuary_core ItemBlock,
        // which only exists when the Sanctuary module + SRP are on.
        //   Eye of Beholder | Beacon      | Eye of Beholder
        //   (empty)         | Level Clock | (empty)
        //   Devoritium Blk  | False Apple | Devoritium Blk
        // ---------------------------------------------------------------------
        if (ModBlocks.SANCTUARY_CORE != null
                && Loader.isModLoaded("srparasites") && Loader.isModLoaded("ancientspellcraft")) {
            registerFallback(event, "sanctuary_nexus",
                    new ShapedOreRecipe(
                            new ResourceLocation(InsaneTweaksMod.MODID, "sanctuary_nexus"),
                            new ItemStack(ModBlocks.SANCTUARY_CORE),
                            "PBP",
                            " C ",
                            "DFD",
                            'P', new ItemStack(safeItem("srparasites", "pearl")),
                            'B', new ItemStack(safeItem("minecraft", "beacon")),
                            'C', new ItemStack(safeItem("srparasites", "levelclock")),
                            'D', new ItemStack(safeItem("ancientspellcraft", "devoritium_block")),
                            'F', new ItemStack(safeItem("srparasites", "false_apple"))));
        }
```

(`ModBlocks` is in the same package, so no import needed.)

- [ ] **Step 2: Compile**

Run: `./gradlew build 2>&1 | tail -8` → Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Deploy + in-game test**

Deploy + launch. In a crafting table lay out Eye of the Beholder (top corners), Beacon (top-mid), Level Clock (center), Devoritium Block (bottom corners), False Apple (bottom-mid), middle-left/right empty → yields 1 Sanctuary Nexus. (JEI should also show it.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/init/ModRecipes.java
git commit -m "feat(sanctuary): crafting recipe for the Sanctuary Nexus

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Network — PacketSetSanctuaryRadius

**Files:**
- Create `src/main/java/com/spege/insanetweaks/network/PacketSetSanctuaryRadius.java`
- Modify `src/main/java/com/spege/insanetweaks/network/InsaneTweaksNetwork.java`

- [ ] **Step 1: Create the packet**

```java
package com.spege.insanetweaks.network;

import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client -> server: set a Creative Sanctuary's forced radius (16..256). */
public class PacketSetSanctuaryRadius implements IMessage {

    private BlockPos pos;
    private int radius;

    public PacketSetSanctuaryRadius() {}

    public PacketSetSanctuaryRadius(BlockPos pos, int radius) {
        this.pos = pos;
        this.radius = radius;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        this.radius = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        buf.writeInt(radius);
    }

    public static class Handler implements IMessageHandler<PacketSetSanctuaryRadius, IMessage> {
        @Override
        public IMessage onMessage(PacketSetSanctuaryRadius msg, MessageContext ctx) {
            net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    // Creative-only guard: only creative-mode players may reconfigure.
                    if (!player.capabilities.isCreativeMode) { return; }
                    if (player.getDistanceSq(msg.pos) > 64.0D) { return; }
                    TileEntity te = player.world.getTileEntity(msg.pos);
                    if (te instanceof TileEntitySanctuaryCore) {
                        ((TileEntitySanctuaryCore) te).setCreativeRadius(msg.radius);
                    }
                }
            });
            return null;
        }
    }
}
```

- [ ] **Step 2: Register the packet (id 2 is free)**

In `InsaneTweaksNetwork.init()`, after the thrall registration line, add:

```java
        CHANNEL.registerMessage(PacketSetSanctuaryRadius.Handler.class, PacketSetSanctuaryRadius.class, 2, Side.SERVER);
```

- [ ] **Step 3: Compile**

Run: `./gradlew build 2>&1 | tail -8` → Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/network/PacketSetSanctuaryRadius.java src/main/java/com/spege/insanetweaks/network/InsaneTweaksNetwork.java
git commit -m "feat(sanctuary): PacketSetSanctuaryRadius (creative radius, client->server)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Creative block class

**Files:** Create `src/main/java/com/spege/insanetweaks/sanctuary/BlockCreativeSanctuary.java`

- [ ] **Step 1: Create the block (extends BlockSanctuaryCore, overrides placement + activation)**

```java
package com.spege.insanetweaks.sanctuary;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Creative-only sanctuary: reuses TileEntitySanctuaryCore in creative-forced mode. Placed it is
 *  instantly active at a fixed radius (default 64); right-click opens a slider GUI (16..256). No
 *  ritual, no tier progression. Obtainable only from the creative tab (there is no crafting recipe). */
public class BlockCreativeSanctuary extends BlockSanctuaryCore {

    /** Radius a freshly placed creative sanctuary starts at. */
    public static final int DEFAULT_RADIUS = 64;

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
            ItemStack stack) {
        // Intentionally NOT calling super (which posts the ritual "demands a lure" message).
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntitySanctuaryCore) {
                ((TileEntitySanctuaryCore) te).setCreativeRadius(DEFAULT_RADIUS);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileEntitySanctuaryCore) {
            player.openGui(com.spege.insanetweaks.InsaneTweaksMod.INSTANCE,
                    com.spege.insanetweaks.InsaneTweaksMod.GUI_ID_CREATIVE_SANCTUARY,
                    world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew build 2>&1 | tail -8` → Expected: `BUILD FAILED` — `GUI_ID_CREATIVE_SANCTUARY` does not exist yet (added in Task 6). Expected; proceed. (Or do Tasks 4–6 before building.)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/BlockCreativeSanctuary.java
git commit -m "feat(sanctuary): BlockCreativeSanctuary (creative-forced sanctuary block)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Container + Gui (slider)

**Files:**
- Create `src/main/java/com/spege/insanetweaks/sanctuary/gui/ContainerCreativeSanctuary.java`
- Create `src/main/java/com/spege/insanetweaks/sanctuary/gui/GuiCreativeSanctuary.java`

- [ ] **Step 1: Container (no slots; syncs current radius via window property 0)**

```java
package com.spege.insanetweaks.sanctuary.gui;

import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;

public class ContainerCreativeSanctuary extends Container {

    private final TileEntitySanctuaryCore te;
    private int lastRadius = -1;
    private int clientRadius = 0;

    public ContainerCreativeSanctuary(TileEntitySanctuaryCore te) {
        this.te = te;
    }

    public TileEntitySanctuaryCore getTe() { return te; }
    public int getClientRadius() { return clientRadius; }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);
        listener.sendWindowProperty(this, 0, clampShort(te.getCreativeRadius()));
        lastRadius = te.getCreativeRadius();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int r = te.getCreativeRadius();
        if (r != lastRadius) {
            for (IContainerListener l : this.listeners) {
                l.sendWindowProperty(this, 0, clampShort(r));
            }
            lastRadius = r;
        }
    }

    @Override
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void updateProgressBar(int id, int data) {
        if (id == 0) { clientRadius = data; }
    }

    private static int clampShort(int v) { return v < 0 ? 0 : (v > 32767 ? 32767 : v); }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return te.getWorld().getTileEntity(te.getPos()) == te
                && player.getDistanceSq(te.getPos()) <= 64.0D;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return ItemStack.EMPTY;
    }
}
```

- [ ] **Step 2: Gui (vanilla GuiSlider, sends the packet on release)**

```java
package com.spege.insanetweaks.sanctuary.gui;

import java.io.IOException;

import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketSetSanctuaryRadius;
import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.client.gui.GuiPageButtonList;
import net.minecraft.client.gui.GuiSlider;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiCreativeSanctuary extends GuiContainer implements GuiPageButtonList.GuiResponder {

    private final TileEntitySanctuaryCore te;
    private GuiSlider slider;
    private int radius;
    private boolean initialised;

    public GuiCreativeSanctuary(ContainerCreativeSanctuary container) {
        super(container);
        this.te = container.getTe();
        this.xSize = 176;
        this.ySize = 100;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.radius = te.getCreativeRadius() <= 0 ? 64 : te.getCreativeRadius();
        // GuiSlider maps its 0..1 handle onto [16,256] and shows the value via the formatter.
        this.slider = new GuiSlider(this, 0, this.guiLeft + 8, this.guiTop + 40, 160, 20,
                "Radius: ", "", 16.0D, 256.0D, this.radius, false, true);
        this.buttonList.add(this.slider);
        this.initialised = true;
    }

    /** GuiResponder: called continuously as the slider is dragged. */
    @Override
    public void setEntryValue(int id, float value) {
        if (id == 0) { this.radius = Math.round(value); }
    }

    @Override public void setEntryValue(int id, boolean value) {}
    @Override public void setEntryValue(int id, String value) {}

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        // Commit the chosen radius to the server when the drag ends.
        if (initialised) {
            BlockPos pos = te.getPos();
            InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketSetSanctuaryRadius(pos, this.radius));
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDefaultBackground();
        drawGradientRect(this.guiLeft, this.guiTop, this.guiLeft + this.xSize, this.guiTop + this.ySize,
                0xF0202020, 0xF0202020);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = net.minecraft.client.resources.I18n.format("gui.insanetweaks.creative_sanctuary.title");
        this.fontRenderer.drawString(title, 8, 8, 0xE0C0FF);
        this.fontRenderer.drawString(
                net.minecraft.client.resources.I18n.format("gui.insanetweaks.creative_sanctuary.hint"),
                8, 22, 0xAAAAAA);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
    }
}
```

Notes for the implementer:
- `GuiSlider(GuiResponder, id, x, y, width, height, prefix, suffix, minValue, maxValue, currentValue, showDecimal, drawString)` is the vanilla 1.12.2 constructor. If the exact constructor signature differs in this MCP mapping, adjust to the available `GuiSlider` constructor (all params above map to name/min/max/current). Confirm by opening `net.minecraft.client.gui.GuiSlider` in the decompiled MC sources.
- The slider handles its own drag; `setEntryValue(int,float)` receives the live value; we only push to the server on `mouseReleased`, so the packet isn't spammed.

- [ ] **Step 3: Compile** (will still fail until Task 6 adds the GUI id — do 4/5/6 then build). After Task 6, run `./gradlew build 2>&1 | tail -8` → Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/gui/ContainerCreativeSanctuary.java src/main/java/com/spege/insanetweaks/sanctuary/gui/GuiCreativeSanctuary.java
git commit -m "feat(sanctuary): creative sanctuary container + slider GUI

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: GUI handler wiring

**Files:** Modify `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java`

- [ ] **Step 1: Add the GUI id constant**

After `public static final int GUI_ID_SANCTUARY = 3;` add:

```java
    /** GUI ID for the Creative Sanctuary radius slider. */
    public static final int GUI_ID_CREATIVE_SANCTUARY = 4;
```

- [ ] **Step 2: Server handler branch**

In `getServerGuiElement`, after the `if (id == GUI_ID_SANCTUARY) { ... }` block, add:

```java
        if (id == GUI_ID_CREATIVE_SANCTUARY) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
            if (te instanceof com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) {
                return new com.spege.insanetweaks.sanctuary.gui.ContainerCreativeSanctuary(
                        (com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) te);
            }
        }
```

- [ ] **Step 3: Client handler branch**

In `getClientGuiElement`, after the `if (id == GUI_ID_SANCTUARY) { ... }` block, add:

```java
        if (id == GUI_ID_CREATIVE_SANCTUARY) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
            if (te instanceof com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) {
                return new com.spege.insanetweaks.sanctuary.gui.GuiCreativeSanctuary(
                        new com.spege.insanetweaks.sanctuary.gui.ContainerCreativeSanctuary(
                                (com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) te));
            }
        }
```

- [ ] **Step 4: Compile** (Tasks 4+5+6 now resolve)

Run: `./gradlew build 2>&1 | tail -8` → Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat(sanctuary): wire Creative Sanctuary GUI (id 4)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Register the creative block + resources + lang

**Files:**
- Modify `src/main/java/com/spege/insanetweaks/init/ModBlocks.java`
- Create `src/main/resources/assets/insanetweaks/blockstates/creative_sanctuary.json`
- Create `src/main/resources/assets/insanetweaks/models/block/creative_sanctuary.json`
- Create `src/main/resources/assets/insanetweaks/models/item/creative_sanctuary.json`
- Modify `src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Add a static field + register the block (creative tab)**

In `ModBlocks`, add a field next to `SANCTUARY_CORE`:

```java
    public static com.spege.insanetweaks.sanctuary.BlockCreativeSanctuary CREATIVE_SANCTUARY;
```

In `registerBlocks`, after the `SANCTUARY_CORE` registration + `registerTileEntity` call (still inside the same `if` guard), add:

```java
        CREATIVE_SANCTUARY = (com.spege.insanetweaks.sanctuary.BlockCreativeSanctuary)
                new com.spege.insanetweaks.sanctuary.BlockCreativeSanctuary()
                        .setUnlocalizedName(InsaneTweaksMod.MODID + ".creative_sanctuary")
                        .setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "creative_sanctuary"));
        CREATIVE_SANCTUARY.setCreativeTab(CreativeTabs.MISC);
        event.getRegistry().register(CREATIVE_SANCTUARY);
```

(No extra `registerTileEntity` — `BlockCreativeSanctuary.createTileEntity` returns `TileEntitySanctuaryCore`, whose registration already happened above.)

- [ ] **Step 2: Register the ItemBlock**

In `registerItemBlocks`, after the sanctuary_core ItemBlock registration (inside the guard), add:

```java
        if (CREATIVE_SANCTUARY != null) {
            ItemBlock cib = new ItemBlock(CREATIVE_SANCTUARY);
            cib.setRegistryName(CREATIVE_SANCTUARY.getRegistryName());
            event.getRegistry().register(cib);
        }
```

- [ ] **Step 3: Register the model**

In `registerModels`, after the sanctuary_core model registration (inside the guard), add:

```java
        if (CREATIVE_SANCTUARY != null) {
            Item citem = Item.getItemFromBlock(CREATIVE_SANCTUARY);
            ModelLoader.setCustomModelResourceLocation(citem, 0,
                    new ModelResourceLocation(CREATIVE_SANCTUARY.getRegistryName(), "inventory"));
        }
```

- [ ] **Step 4: Create the resource JSONs by mirroring sanctuary_core**

First read the existing sanctuary_core resources to copy their shape:

Run: `cat src/main/resources/assets/insanetweaks/blockstates/sanctuary_core.json src/main/resources/assets/insanetweaks/models/block/sanctuary_core.json src/main/resources/assets/insanetweaks/models/item/sanctuary_core.json`

Create `blockstates/creative_sanctuary.json`, `models/block/creative_sanctuary.json`, `models/item/creative_sanctuary.json` as copies of the sanctuary_core equivalents (reusing the same textures — the creative block deliberately looks identical to the Nexus). Replace any `sanctuary_core` model reference inside them with `creative_sanctuary` where it points to its own model file; keep texture paths pointing at the existing `sanctuary_core` textures. If the sanctuary_core item model simply parents the block model, mirror that (`"parent": "insanetweaks:block/creative_sanctuary"`), and have `block/creative_sanctuary.json` parent/replicate `block/sanctuary_core.json` (e.g. `{"parent": "insanetweaks:block/sanctuary_core"}` is the simplest valid body if that model is a standalone cube).

- [ ] **Step 5: Lang entries**

In `en_us.lang` add (near the sanctuary_core name):

```
tile.insanetweaks.creative_sanctuary.name=Creative Sanctuary
gui.insanetweaks.creative_sanctuary.title=Creative Sanctuary
gui.insanetweaks.creative_sanctuary.hint=Set the protection radius. Always active, no tier or fuel.
```

- [ ] **Step 6: Compile**

Run: `./gradlew build 2>&1 | tail -8` → Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Deploy + in-game test**

Deploy + launch. In creative, find "Creative Sanctuary" (MISC tab). Place it → immediately active (dome appears at radius 64; `debugLogging` shows `tier=4 radius=64`). Right-click → slider GUI; drag to e.g. 128, release → dome/region resize to 128 (log updates). Confirm purge fire / spawn veto / cleanse operate. Break + replace works. Save/reload keeps the set radius.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/init/ModBlocks.java src/main/resources/assets/insanetweaks/blockstates/creative_sanctuary.json src/main/resources/assets/insanetweaks/models/block/creative_sanctuary.json src/main/resources/assets/insanetweaks/models/item/creative_sanctuary.json src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "feat(sanctuary): register Creative Sanctuary block + resources

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-review checklist (done by author)

- **Spec coverage:** recipe (Task 2, verified items incl. devoritium correction), creative-only block with 16–256 slider GUI (Tasks 1,4,5,6,7), no tier/fuel (Task 1 forces tier 4 + fixed radius, ritual skipped). ✓
- **Type consistency:** `creativeRadius`/`getCreativeRadius`/`setCreativeRadius`, `GUI_ID_CREATIVE_SANCTUARY`, `ContainerCreativeSanctuary`/`GuiCreativeSanctuary`, packet id 2, window property id 0 — consistent across tasks. ✓
- **Compile coupling:** Tasks 4–6 land together (BlockCreativeSanctuary references GUI_ID added in Task 6). Build green only expected after Task 6; Task 7 completes registration.

## Notes / risks

- **GuiSlider constructor**: verify the exact 1.12.2 signature (Task 5 note). If it diverges, adapt — the semantics (min 16, max 256, current, prefix "Radius: ") are what matter.
- **Model JSONs**: the safest `block/creative_sanctuary.json` is `{"parent":"insanetweaks:block/sanctuary_core"}` if that model is self-contained; verify against the actual sanctuary_core model in Task 7 Step 4.
- **Creative-only**: enforced by (a) no crafting recipe for creative_sanctuary and (b) the packet handler rejecting non-creative players. The block still exists in survival if given via commands — acceptable.
- Out of scope (next iterations): the cost-of-power debuffs (magic exhaustion / HP penalty / life-essence fuel) and upgrades — separate brainstorm.
