# Sanktuarium / Kopuła ochronna — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dodać do InsaneTweaks moduł „Sanktuarium" — blok-rdzeń na piramidzie, tworzący cylindryczną strefę, która wetuje spawn i infestację SRParasites oraz powoli cofa infestację terenu (za paliwo), nie dotykając pasożytów już w środku.

**Architecture:** Pierwszy custom Block+TileEntity+WorldSavedData w modzie. Rdzeń (TE) skanuje piramidę → tier → promień, synchronizuje aktywne strefy do `SanctuaryWorldData` (per-świat WorldSavedData). Haki SRP pytają ten rejestr: naturalny spawn wetuje handler Forge `LivingSpawnEvent.CheckSpawn` (bez mixina), konwersję bloków wetuje jeden mixin na `BeckonBlockInfestation.beckonInfestation`. Cleanse (za paliwo) i GUI/upkeep w TE.

**Tech Stack:** Java 8, Forge 1.12.2-14.23.5.2860, Cleanroom/MixinBooter (sponge-mixin 0.8.7), ForgeGradle 3. Runtime SRParasites 1.10.7 (`srparasites`).

**Reference spec:** `docs/superpowers/specs/2026-07-19-sanctuary-dome-design.md`.

**Weryfikacja — uwaga o realiach projektu:** brak unit-testów i lintu (CLAUDE.md). „Test" każdego zadania = `./gradlew build` z oczekiwanym `BUILD SUCCESSFUL` (kompilacja + `reobfJar`; `-Xlint:all` włączony — nowe warningi traktuj jak sygnał). Kamienie milowe mają dodatkowy krok in-game: skopiuj `build/libs/insanetweaks-1.2.0.jar` do `C:\Users\spege\curseforge\minecraft\Instances\DEv 1.2\mods\` (nadpisując stary — dwie wersje jednego modid = crash), odpal paczkę, wykonaj opisaną obserwację. Mixin-apply faile ujawniają się TYLKO na starcie gry, nie przy kompilacji.

**Konwencje (z CLAUDE.md):** Java 8 (bez lambd w mixinach, bez `var`/`record`). Rejestracja przez `@Mod.EventBusSubscriber` (wzorzec `init/ModPotions.java`). Config przez Forge `@Config` (wzorzec `config/categories/SrpCompatCategory.java`). Gate handlerów w `InsaneTweaksMod.init` pod flagą modułu + `Loader.isModLoaded`. Mixiny SRP: SRG-nazwy z `remap=false`, potwierdź target `javap -p -c`, gate configiem przy HEAD. Stała `InsaneTweaksMod.SRP_MODID = "srparasites"` do checków obecności.

**Frequent commits:** każde zadanie kończy się commitem. Pracuj na branchu (nie `main`).

---

## Struktura plików (mapa decyzji dekompozycji)

Nowy pakiet `com.spege.insanetweaks.sanctuary` + podpakiety. Jedna odpowiedzialność na plik:

| Plik | Odpowiedzialność |
|---|---|
| `config/categories/SanctuaryCategory.java` | Tunables configu (promienie tierów, paliwa, cleanse, blacklista dim) |
| `config/categories/ModulesCategory.java` (mod) | +flaga `enableSanctuary` |
| `config/ModConfig.java` (mod) | +pole kategorii `sanctuary` |
| `init/ModBlocks.java` | Pierwsza szyna rejestracji bloków + ItemBlock + model |
| `sanctuary/BlockSanctuaryCore.java` | Blok-rdzeń: `hasTileEntity`, `createTileEntity`, `onBlockActivated`→GUI, `breakBlock`→cleanup |
| `sanctuary/TileEntitySanctuaryCore.java` | Stan + serwer-tick (skan piramidy, upkeep, cleanse, sync rejestru), inventory (paliwo+upgrade), NBT |
| `sanctuary/SanctuaryWorldData.java` | Per-świat rejestr aktywnych stref + `isInsideAnySanctuary` |
| `sanctuary/SanctuaryRegionHelper.java` | Bezstanowe query dla haków: `isProtected(World,x,y,z)` (opakowuje WorldData + config guardy) |
| `sanctuary/SanctuaryCleanseHelper.java` | Cofanie infestacji: skan wycinka cylindra, mapowanie infested→naturalny |
| `sanctuary/SanctuarySpawnVetoHandler.java` | Forge `LivingSpawnEvent.CheckSpawn` → DENY dla SRP w strefie |
| `mixins/srp/MixinBeckonBlockInfestation.java` | `@Inject HEAD cancellable` na `beckonInfestation` → cancel w strefie |
| `sanctuary/gui/ContainerSanctuaryCore.java` | Sloty paliwa+upgrade, sync pól tier/radius/fuel |
| `sanctuary/gui/GuiSanctuaryCore.java` | Render GUI (klient) |
| `InsaneTweaksMod.java` (mod) | Rejestracja handlera+GUI id, wpięcie w `IGuiHandler` |
| `resources/mixins.insanetweaks.srp.json` | +wpis mixina |
| `resources/assets/insanetweaks/lang/en_us.lang` | Nazwy/tooltime |

---

## Task 1: Config — kategoria Sanctuary + flaga modułu

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/config/categories/SanctuaryCategory.java`
- Modify: `src/main/java/com/spege/insanetweaks/config/categories/ModulesCategory.java` (koniec klasy)
- Modify: `src/main/java/com/spege/insanetweaks/config/ModConfig.java` (import + pole)
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: Utwórz `SanctuaryCategory.java`** (wzorzec: `SrpCompatCategory.java` — `@Config.Name/Comment/RangeInt`, `String[]` parsowane osobnym helperem)

```java
package com.spege.insanetweaks.config.categories;

import net.minecraftforge.common.config.Config;

/** Tunables for the Sanctuary Dome module (see docs/superpowers/specs/2026-07-19-sanctuary-dome-design.md). */
public class SanctuaryCategory {

    @Config.Comment({"Base protection radius (blocks) per pyramid tier 1-4. Index 0 = tier 1.",
            "Read live (no restart)."})
    @Config.Name("Tier Radii")
    public int[] tierRadii = new int[] { 16, 32, 48, 64 };

    @Config.Comment({"Blocks allowed in the pyramid layers, by registry name (e.g. minecraft:iron_block).",
            "A layer counts only if fully built from these. Read live."})
    @Config.Name("Pyramid Blocks")
    public String[] pyramidBlocks = new String[] { "minecraft:iron_block", "minecraft:diamond_block" };

    @Config.Comment({"Fuel items for the cleanse function, one per line as 'registry=value'.",
            "'value' = how many cleanse-conversions one item powers. Read live. Malformed lines ignored."})
    @Config.Name("Fuel Items")
    public String[] fuelItems = new String[] { "minecraft:emerald=64" };

    @Config.Comment("Infested blocks the cleanse reverts per tick (spread load). Read live.")
    @Config.Name("Cleanse Blocks Per Tick")
    @Config.RangeInt(min = 1, max = 256)
    public int cleanseBlocksPerTick = 8;

    @Config.Comment("Ticks between pyramid re-validations in the core TE. Read live.")
    @Config.Name("Pyramid Revalidate Interval")
    @Config.RangeInt(min = 20, max = 1200)
    public int pyramidRevalidateInterval = 40;

    @Config.Comment("Extra radius (blocks) granted per radius-upgrade item in the core. Read live.")
    @Config.Name("Upgrade Radius Bonus")
    @Config.RangeInt(min = 0, max = 128)
    public int upgradeRadiusBonus = 16;

    @Config.Comment({"Dimension IDs where the dome is INERT (parasite dimensions stay hostile).",
            "Read live."})
    @Config.Name("Dimension Blacklist")
    public int[] dimensionBlacklist = new int[] { 111 };

    @Config.Comment("Master switch for the natural-spawn veto (Forge CheckSpawn). Read live.")
    @Config.Name("Veto Natural Spawn")
    public boolean vetoNaturalSpawn = true;

    @Config.Comment({"Master switch for the block-infestation veto (mixin on BeckonBlockInfestation).",
            "Requires MC restart (mixin gate)."})
    @Config.Name("Veto Block Infestation")
    @Config.RequiresMcRestart
    public boolean vetoBlockInfestation = true;

    @Config.Comment("Whether cleanse is ON by default on a freshly placed core. Read live.")
    @Config.Name("Cleanse Enabled By Default")
    public boolean cleanseEnabledByDefault = true;

    @Config.Comment("Client: render the particle border of active domes. Read live.")
    @Config.Name("Particle Border")
    public boolean particleBorder = true;
}
```

- [ ] **Step 2: Dodaj flagę do `ModulesCategory.java`** (na końcu klasy, przed zamykającą `}` — wzorzec `enableGrimoire`)

```java
    @Config.Comment({
            "Enables the Sanctuary Dome: a pyramid-based core block that blocks SRParasites spawning",
            "and terrain infestation in a cylindrical region, and slowly reverts existing infestation",
            "(fuel-powered cleanse). Requires SRParasites; auto-disabled if absent." })
    @Config.Name("Enable Sanctuary Dome")
    @Config.RequiresMcRestart
    public boolean enableSanctuary = true;
```

- [ ] **Step 3: Zarejestruj kategorię w `ModConfig.java`** (import obok innych `config.categories.*`; pole po `srpCompat`)

```java
// import (sekcja importów):
import com.spege.insanetweaks.config.categories.SanctuaryCategory;

// pole (po bloku srpCompat):
    @Config.Name("sanctuary")
    @Config.LangKey("config.insanetweaks.category.sanctuary")
    @Config.Comment("Sanctuary Dome tunables (radius tiers, fuel, cleanse, dimension blacklist). Master toggle is modules.enableSanctuary.")
    public static final SanctuaryCategory sanctuary = new SanctuaryCategory();
```

- [ ] **Step 4: Lang** — dodaj do `en_us.lang`:

```
config.insanetweaks.category.sanctuary=Sanctuary Dome
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. Nowa sekcja `sanctuary {}` pojawi się w `run/config/insanetweaks.cfg` przy pierwszym uruchomieniu.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/config/ src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "feat(sanctuary): add config category + module flag"
```

---

## Task 2: Rejestracja bloku — `ModBlocks` + `BlockSanctuaryCore` (bez TE)

Cel: pierwszy custom blok w modzie widoczny/stawialny w kreatywie. TE dokładamy w Task 3.

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/init/ModBlocks.java`
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java`
- Create: `src/main/resources/assets/insanetweaks/blockstates/sanctuary_core.json`
- Create: `src/main/resources/assets/insanetweaks/models/block/sanctuary_core.json`
- Create: `src/main/resources/assets/insanetweaks/models/item/sanctuary_core.json`
- Modify: `src/main/resources/assets/insanetweaks/lang/en_us.lang`

- [ ] **Step 1: `BlockSanctuaryCore.java`** (minimalny blok; TE-hooki dodamy w Task 3)

```java
package com.spege.insanetweaks.sanctuary;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;

public class BlockSanctuaryCore extends Block {
    public BlockSanctuaryCore() {
        super(Material.ROCK);
        setHardness(4.0F);
        setResistance(2000.0F); // blast-resistant like a beacon base
        setSoundType(SoundType.STONE);
        setLightLevel(0.5F);
        // registry name + creative tab set in ModBlocks
    }
}
```

- [ ] **Step 2: `ModBlocks.java`** (wzorzec `ModItems`/`ModPotions`: `@Mod.EventBusSubscriber`, `Register<Block>`, `Register<Item>` dla ItemBlocka, `ModelRegistryEvent` klient)

```java
package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.sanctuary.BlockSanctuaryCore;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public class ModBlocks {

    public static BlockSanctuaryCore SANCTUARY_CORE;

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!ModConfig.modules.enableSanctuary) {
            return;
        }
        SANCTUARY_CORE = (BlockSanctuaryCore) new BlockSanctuaryCore()
                .setTranslationKey(InsaneTweaksMod.MODID + ".sanctuary_core")
                .setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "sanctuary_core"));
        SANCTUARY_CORE.setCreativeTab(net.minecraft.creativetab.CreativeTabs.MISC);
        event.getRegistry().register(SANCTUARY_CORE);
    }

    @SubscribeEvent
    public static void registerItemBlocks(RegistryEvent.Register<Item> event) {
        if (!ModConfig.modules.enableSanctuary || SANCTUARY_CORE == null) {
            return;
        }
        ItemBlock ib = new ItemBlock(SANCTUARY_CORE);
        ib.setRegistryName(SANCTUARY_CORE.getRegistryName());
        event.getRegistry().register(ib);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        if (!ModConfig.modules.enableSanctuary || SANCTUARY_CORE == null) {
            return;
        }
        Item item = Item.getItemFromBlock(SANCTUARY_CORE);
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(SANCTUARY_CORE.getRegistryName(), "inventory"));
    }
}
```

- [ ] **Step 3: Zasoby modelu** — placeholder na bazie stone (podmień teksturę później):

`blockstates/sanctuary_core.json`:
```json
{ "variants": { "normal": { "model": "insanetweaks:sanctuary_core" } } }
```
`models/block/sanctuary_core.json`:
```json
{ "parent": "block/cube_all", "textures": { "all": "blocks/lapis_block" } }
```
`models/item/sanctuary_core.json`:
```json
{ "parent": "insanetweaks:block/sanctuary_core" }
```

- [ ] **Step 4: Lang**:
```
tile.insanetweaks.sanctuary_core.name=Sanctuary Core
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6 (MILESTONE, in-game): deploy + sprawdź blok**

Skopiuj jar do paczki (nadpisz stary), odpal klienta. W kreatywie znajdź „Sanctuary Core" (zakładka Misc), postaw — powinien się postawić i świecić (lightLevel 0.5). Brak crasha przy modelu = OK.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/init/ModBlocks.java src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java src/main/resources/assets/insanetweaks/blockstates src/main/resources/assets/insanetweaks/models src/main/resources/assets/insanetweaks/lang/en_us.lang
git commit -m "feat(sanctuary): register core block + itemblock + model"
```

---

## Task 3: TileEntity szkielet — stan + NBT + inventory

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java`
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java` (hasTileEntity/createTileEntity)
- Modify: `src/main/java/com/spege/insanetweaks/init/ModBlocks.java` (registerTileEntity w Register<Block>)

- [ ] **Step 1: `TileEntitySanctuaryCore.java`** — pola stanu + `ItemStackHandler` (paliwo slot 0, upgrade sloty 1-4) + NBT. `ITickable` z pustym `update()` (wypełnimy w Task 5/9).

```java
package com.spege.insanetweaks.sanctuary;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public class TileEntitySanctuaryCore extends TileEntity implements ITickable {

    public static final int SLOT_FUEL = 0;
    public static final int UPGRADE_SLOTS = 4; // slots 1..4

    private final ItemStackHandler inventory = new ItemStackHandler(1 + UPGRADE_SLOTS) {
        @Override protected void onContentsChanged(int slot) { markDirty(); }
    };

    private int tier;            // 0 = inactive (no/incomplete pyramid)
    private int effectiveRadius; // computed from tier + upgrades
    private boolean cleanseEnabled;
    private boolean cleanseStalled; // true when cleanse wants to run but fuel == 0
    private int fuelStored;      // remaining cleanse-conversions from consumed fuel items
    private boolean initialized; // first-tick default for cleanseEnabled

    public ItemStackHandler getInventory() { return inventory; }
    public int getTier() { return tier; }
    public int getEffectiveRadius() { return effectiveRadius; }
    public boolean isCleanseEnabled() { return cleanseEnabled; }
    public boolean isCleanseStalled() { return cleanseStalled; }
    public void setCleanseEnabled(boolean v) { this.cleanseEnabled = v; markDirty(); }

    // setters used by tick logic (Task 5/9)
    void setTier(int t) { this.tier = t; }
    void setEffectiveRadius(int r) { this.effectiveRadius = r; }
    void setCleanseStalled(boolean v) { this.cleanseStalled = v; }
    int getFuelStored() { return fuelStored; }
    void setFuelStored(int v) { this.fuelStored = v; }
    boolean isInitialized() { return initialized; }
    void markInitialized() { this.initialized = true; }

    @Override public void update() { /* filled in Task 5 (scan+sync) and Task 9 (upkeep+cleanse) */ }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        super.readFromNBT(c);
        inventory.deserializeNBT(c.getCompoundTag("inv"));
        tier = c.getInteger("tier");
        effectiveRadius = c.getInteger("radius");
        cleanseEnabled = c.getBoolean("cleanse");
        cleanseStalled = c.getBoolean("stalled");
        fuelStored = c.getInteger("fuel");
        initialized = c.getBoolean("init");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        super.writeToNBT(c);
        c.setTag("inv", inventory.serializeNBT());
        c.setInteger("tier", tier);
        c.setInteger("radius", effectiveRadius);
        c.setBoolean("cleanse", cleanseEnabled);
        c.setBoolean("stalled", cleanseStalled);
        c.setInteger("fuel", fuelStored);
        c.setBoolean("init", initialized);
        return c;
    }

    @Override
    public boolean hasCapability(Capability<?> cap, net.minecraft.util.EnumFacing side) {
        return cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(cap, side);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> cap, net.minecraft.util.EnumFacing side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) inventory;
        }
        return super.getCapability(cap, side);
    }
}
```

- [ ] **Step 2: Podłącz TE w `BlockSanctuaryCore.java`** (dodaj metody):

```java
    @Override public boolean hasTileEntity(net.minecraft.block.state.IBlockState state) { return true; }

    @Override
    public net.minecraft.tileentity.TileEntity createTileEntity(net.minecraft.world.World world,
            net.minecraft.block.state.IBlockState state) {
        return new TileEntitySanctuaryCore();
    }
```

- [ ] **Step 3: Zarejestruj TE** w `ModBlocks.registerBlocks` (na końcu, po register bloku):

```java
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore.class,
                new ResourceLocation(InsaneTweaksMod.MODID, "sanctuary_core"));
```

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java src/main/java/com/spege/insanetweaks/init/ModBlocks.java
git commit -m "feat(sanctuary): core TileEntity skeleton (state, NBT, inventory)"
```

---

## Task 4: `SanctuaryWorldData` + `SanctuaryRegionHelper`

Cel: per-świat rejestr aktywnych stref + bezstanowe query dla haków. To jest źródło prawdy — TE tylko je aktualizuje (Task 5), haki tylko czytają (Task 6/7).

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryWorldData.java`
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryRegionHelper.java`

- [ ] **Step 1: `SanctuaryWorldData.java`** (wzór strukturalny: SRP `SRPWorldData`; parallel-list po BlockPos + radius)

```java
package com.spege.insanetweaks.sanctuary;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

/** Per-world registry of active sanctuary regions. Single source of truth queried by the SRP vetoes. */
public class SanctuaryWorldData extends WorldSavedData {

    private static final String NAME = "insanetweaks_sanctuaries";

    private final List<int[]> regions = new ArrayList<int[]>(); // each: {x, y, z, radius}

    public SanctuaryWorldData() { super(NAME); }
    public SanctuaryWorldData(String name) { super(name); }

    public static SanctuaryWorldData get(World world) {
        MapStorage storage = world.getPerWorldStorage();
        SanctuaryWorldData data = (SanctuaryWorldData) storage.getOrLoadData(SanctuaryWorldData.class, NAME);
        if (data == null) {
            data = new SanctuaryWorldData();
            storage.setData(NAME, data);
        }
        return data;
    }

    /** Insert-or-update the region anchored at pos. radius<=0 removes it. */
    public void setRegion(BlockPos pos, int radius) {
        for (int i = 0; i < regions.size(); i++) {
            int[] r = regions.get(i);
            if (r[0] == pos.getX() && r[1] == pos.getY() && r[2] == pos.getZ()) {
                if (radius <= 0) { regions.remove(i); } else { r[3] = radius; }
                markDirty();
                return;
            }
        }
        if (radius > 0) {
            regions.add(new int[] { pos.getX(), pos.getY(), pos.getZ(), radius });
            markDirty();
        }
    }

    public void removeRegion(BlockPos pos) { setRegion(pos, 0); }

    /** Cylinder test (full height): dx^2 + dz^2 <= r^2 for any active region. */
    public boolean isInside(int x, int z) {
        for (int i = 0; i < regions.size(); i++) {
            int[] r = regions.get(i);
            long dx = x - r[0];
            long dz = z - r[2];
            long rr = (long) r[3] * r[3];
            if (dx * dx + dz * dz <= rr) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound c) {
        regions.clear();
        NBTTagList list = c.getTagList("regions", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            regions.add(new int[] { t.getInteger("x"), t.getInteger("y"), t.getInteger("z"), t.getInteger("r") });
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound c) {
        NBTTagList list = new NBTTagList();
        for (int[] r : regions) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("x", r[0]); t.setInteger("y", r[1]); t.setInteger("z", r[2]); t.setInteger("r", r[3]);
            list.appendTag(t);
        }
        c.setTag("regions", list);
        return c;
    }
}
```

- [ ] **Step 2: `SanctuaryRegionHelper.java`** — jedyny punkt, przez który haki pytają. Opakowuje config-guardy (dim blacklist, moduł włączony) + WorldData.

```java
package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class SanctuaryRegionHelper {

    private SanctuaryRegionHelper() {}

    public static boolean isDimensionBlacklisted(World world) {
        int dim = world.provider.getDimension();
        for (int d : ModConfig.sanctuary.dimensionBlacklist) {
            if (d == dim) {
                return true;
            }
        }
        return false;
    }

    /** True when (x,z) in `world` lies inside any active sanctuary and the module is not gated off there. */
    public static boolean isProtected(World world, int x, int z) {
        if (world == null || world.isRemote) {
            return false;
        }
        if (isDimensionBlacklisted(world)) {
            return false;
        }
        return SanctuaryWorldData.get(world).isInside(x, z);
    }

    public static boolean isProtected(World world, BlockPos pos) {
        return pos != null && isProtected(world, pos.getX(), pos.getZ());
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryWorldData.java src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryRegionHelper.java
git commit -m "feat(sanctuary): per-world region registry + query helper"
```

---

## Task 5: Skan piramidy → tier → promień → sync rejestru (TE tick)

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java` (metody skanu + `update()` częściowo)
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java` (`breakBlock` → usuń wpis)

- [ ] **Step 1: Dodaj skan piramidy + sync do `TileEntitySanctuaryCore`** (metody prywatne + wypełnij część `update()`)

```java
    private int pyramidTickCounter;

    private boolean isPyramidBlock(net.minecraft.block.state.IBlockState state) {
        net.minecraft.util.ResourceLocation rn = state.getBlock().getRegistryName();
        if (rn == null) { return false; }
        String s = rn.toString();
        for (String allowed : com.spege.insanetweaks.config.ModConfig.sanctuary.pyramidBlocks) {
            if (allowed.equalsIgnoreCase(s)) { return true; }
        }
        return false;
    }

    /** Beacon-style: count complete pyramid layers directly below the core. Returns 0..4.
     *  Layer L is the (2L+1)x(2L+1) square of blocks at y = coreY - L. Layers must be
     *  contiguous from the top: the first incomplete layer stops the count. */
    private int scanPyramidTier() {
        int tiers = 0;
        for (int layer = 1; layer <= 4; layer++) {
            int y = pos.getY() - layer;
            boolean complete = true;
            for (int dx = -layer; dx <= layer && complete; dx++) {
                for (int dz = -layer; dz <= layer; dz++) {
                    net.minecraft.util.math.BlockPos p =
                            new net.minecraft.util.math.BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
                    if (!isPyramidBlock(world.getBlockState(p))) {
                        complete = false;
                        break;
                    }
                }
            }
            if (complete) { tiers = layer; } else { break; }
        }
        return tiers;
    }

    private int countUpgradeRadiusItems() {
        int n = 0;
        for (int slot = 1; slot <= UPGRADE_SLOTS; slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) { n += inventory.getStackInSlot(slot).getCount(); }
        }
        return n;
    }

    private void revalidateAndSync() {
        int newTier = scanPyramidTier();
        int radius = 0;
        if (newTier >= 1) {
            int[] radii = com.spege.insanetweaks.config.ModConfig.sanctuary.tierRadii;
            int base = radii[Math.min(newTier, radii.length) - 1];
            radius = base + countUpgradeRadiusItems() * com.spege.insanetweaks.config.ModConfig.sanctuary.upgradeRadiusBonus;
        }
        this.tier = newTier;
        this.effectiveRadius = radius;
        SanctuaryWorldData.get(world).setRegion(pos, radius); // radius<=0 removes
        markDirty();
    }

    public void onRemovedFromWorld() {
        if (world != null && !world.isRemote) {
            SanctuaryWorldData.get(world).removeRegion(pos);
        }
    }
```

- [ ] **Step 2: Wypełnij `update()`** (część skanu; upkeep/cleanse w Task 9). Zastąp pustą metodę:

```java
    @Override
    public void update() {
        if (world == null || world.isRemote) { return; }
        if (!com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary) { return; }
        if (com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper.isDimensionBlacklisted(world)) {
            if (tier != 0) { tier = 0; effectiveRadius = 0; SanctuaryWorldData.get(world).removeRegion(pos); }
            return;
        }
        if (!initialized) {
            cleanseEnabled = com.spege.insanetweaks.config.ModConfig.sanctuary.cleanseEnabledByDefault;
            markInitialized();
        }
        if (++pyramidTickCounter >= com.spege.insanetweaks.config.ModConfig.sanctuary.pyramidRevalidateInterval) {
            pyramidTickCounter = 0;
            revalidateAndSync();
        }
        // Task 9 inserts upkeep + cleanse here.
    }
```

- [ ] **Step 3: `BlockSanctuaryCore.breakBlock`** — usuń wpis z rejestru zanim TE zniknie:

```java
    @Override
    public void breakBlock(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.state.IBlockState state) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntitySanctuaryCore) {
            ((TileEntitySanctuaryCore) te).onRemovedFromWorld();
        }
        super.breakBlock(world, pos, state);
    }
```

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5 (MILESTONE, in-game): tier/promień działa**

Deploy jar, odpal. Postaw rdzeń, zbuduj pod nim piramidę z `minecraft:iron_block` (warstwa 3×3 tuż pod rdzeniem = tier 1). Tymczasowa weryfikacja przez log: dodaj w `revalidateAndSync` `com.spege.insanetweaks.InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Sanctuary tier={} radius={}", newTier, radius);` (usuń po teście) i obserwuj `logs/latest.log`. Rozbuduj piramidę → tier rośnie; rozbij warstwę → tier spada; rozbij rdzeń → brak dalszych logów (wpis usunięty).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java
git commit -m "feat(sanctuary): pyramid scan -> tier/radius -> registry sync"
```

---

## Task 6: Veto naturalnego spawnu (event, bez mixina)

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuarySpawnVetoHandler.java`
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (rejestracja w `init`)

- [ ] **Step 1: `SanctuarySpawnVetoHandler.java`** — deny SRP spawn w strefie. LOWEST priority (wygrywa po SRP). Detekcja SRP przez FQN klasy bazowej (unikamy twardego importu, gdyby SRP był absent — ale i tak gate'ujemy `isModLoaded`).

```java
package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.Entity;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class SanctuarySpawnVetoHandler {

    private static final String SRP_PARASITE_BASE =
            "com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase";

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        if (!ModConfig.sanctuary.vetoNaturalSpawn) {
            return;
        }
        if (event.getResult() == Event.Result.DENY) {
            return; // already denied by someone else
        }
        Entity e = event.getEntityLiving();
        if (e == null || !isSrpParasite(e)) {
            return;
        }
        if (SanctuaryRegionHelper.isProtected(event.getWorld(),
                (int) Math.floor(event.getX()), (int) Math.floor(event.getZ()))) {
            event.setResult(Event.Result.DENY);
        }
    }

    private static boolean isSrpParasite(Entity e) {
        Class<?> c = e.getClass();
        while (c != null) {
            if (c.getName().equals(SRP_PARASITE_BASE)) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }
}
```

- [ ] **Step 2: Zarejestruj w `InsaneTweaksMod.init`** (przy innych module-gated blokach, np. po bloku Grimoire). Gate: moduł + SRP obecny.

```java
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary
                && Loader.isModLoaded(SRP_MODID)) {
            MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.sanctuary.SanctuarySpawnVetoHandler());
        }
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4 (MILESTONE, in-game): spawn zablokowany**

Deploy, odpal na paczce DEv 1.2 (SRP obecny). Postaw aktywny rdzeń (tier≥1, spory promień). W strefie: poczekaj/wymuś naturalny spawn pasożytów (np. wysoka faza SRP lub `/summon` NIE testuje eventu — użyj naturalnego spawnu / spawnera). Obserwuj: pasożyty SRP nie spawnią się naturalnie w promieniu, ale vanilla moby (zombie itd.) spawnią normalnie. Poza promieniem SRP spawni jak zwykle.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/SanctuarySpawnVetoHandler.java src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat(sanctuary): veto SRP natural spawn via CheckSpawn event"
```

---

## Task 7: Veto konwersji bloków (mixin `BeckonBlockInfestation`)

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/mixins/srp/MixinBeckonBlockInfestation.java`
- Modify: `src/main/resources/mixins.insanetweaks.srp.json`

- [ ] **Step 1: POTWIERDŹ sygnaturę targetu** (gotcha CLAUDE.md — bez tego crash startu). Uruchom:

Run: `javap -p -c -classpath notes/decompiled_mods/srp_sourcecode/fulljar/<srp>.jar com.dhanantry.scapeandrunparasites.util.convert.BeckonBlockInfestation`
Expected: potwierdź istnienie `public static void beckonInfestation(net.minecraft.world.World, net.minecraft.util.math.BlockPos, java.util.Random, int, boolean)` i jej SRG deskryptor. Jeśli nazwa/parametry inne — dostosuj `method`/`@At` poniżej.

- [ ] **Step 2: `MixinBeckonBlockInfestation.java`** (dev-nazwy w ciele: `World`, `BlockPos` mapują reobf; target-string zależy od kroku 1). Metoda `beckonInfestation` to własna metoda SRP (nie MC), więc `remap = false` i nazwa dosłowna.

```java
package com.spege.insanetweaks.mixins.srp;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.sanctuary.SanctuaryRegionHelper;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Mixin(targets = "com.dhanantry.scapeandrunparasites.util.convert.BeckonBlockInfestation", remap = false)
public class MixinBeckonBlockInfestation {

    @Inject(method = "beckonInfestation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void insanetweaks$vetoInSanctuary(World world, BlockPos pos, java.util.Random rand,
            int a, boolean b, CallbackInfo ci) {
        if (!ModConfig.sanctuary.vetoBlockInfestation) {
            return;
        }
        if (SanctuaryRegionHelper.isProtected(world, pos)) {
            ci.cancel();
        }
    }
}
```

- [ ] **Step 3: Dodaj do `mixins.insanetweaks.srp.json`** — do tablicy `mixins`:

```
"MixinBeckonBlockInfestation"
```
(zachowaj format istniejącej listy — dopisz jako kolejny element z przecinkiem).

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (mixin-apply NIE jest sprawdzany przy kompilacji!).

- [ ] **Step 5 (MILESTONE, in-game — krytyczny): brak crasha + veto konwersji**

Deploy, odpal na DEv 1.2. **Najpierw potwierdź brak crasha startu** (mixin-apply faile → crash z `[mixin]` w `logs/latest.log`; „Scanned 0 target(s)" = zły target string z kroku 1). Jeśli crash — wróć do kroku 1, popraw `method`/`@At`. Gdy gra wstaje: w aktywnej strefie postaw beckon/infested blok obok terenu → bloki w promieniu **nie konwertują się** na infested; poza promieniem konwersja działa normalnie.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/mixins/srp/MixinBeckonBlockInfestation.java src/main/resources/mixins.insanetweaks.srp.json
git commit -m "feat(sanctuary): mixin veto on BeckonBlockInfestation in region"
```

---

## Task 8: GUI — Container + Gui + wpięcie w IGuiHandler

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/gui/ContainerSanctuaryCore.java`
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/gui/GuiSanctuaryCore.java`
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java` (`onBlockActivated`)
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (`GUI_ID_SANCTUARY` + `getServerGuiElement`/`getClientGuiElement`)

- [ ] **Step 1: `ContainerSanctuaryCore.java`** — sloty: paliwo (0) + upgrade (1-4) + inventory gracza. Wzoruj się na istniejącym containerze thralla (`gui`/`inventory` moda) dla layoutu slotów gracza.

```java
package com.spege.insanetweaks.sanctuary.gui;

import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.items.SlotItemHandler;

public class ContainerSanctuaryCore extends Container {

    private final TileEntitySanctuaryCore te;

    public ContainerSanctuaryCore(InventoryPlayer playerInv, TileEntitySanctuaryCore te) {
        this.te = te;
        // fuel slot
        addSlotToContainer(new SlotItemHandler(te.getInventory(), TileEntitySanctuaryCore.SLOT_FUEL, 26, 35));
        // upgrade slots 1..4
        for (int i = 0; i < TileEntitySanctuaryCore.UPGRADE_SLOTS; i++) {
            addSlotToContainer(new SlotItemHandler(te.getInventory(), 1 + i, 80 + i * 18, 35));
        }
        // player inventory (3x9) + hotbar
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

- [ ] **Step 2: `GuiSanctuaryCore.java`** — minimalny render (tło z vanilla dispenser/generic + teksty tier/promień/cleanse). Bez własnej tekstury: użyj `GuiContainer` z generyczną teksturą.

```java
package com.spege.insanetweaks.sanctuary.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

public class GuiSanctuaryCore extends GuiContainer {

    private static final ResourceLocation BG = new ResourceLocation("textures/gui/container/dispenser.png");
    private final ContainerSanctuaryCore container;

    public GuiSanctuaryCore(ContainerSanctuaryCore container) {
        super(container);
        this.container = container;
        this.ySize = 166;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        net.minecraft.client.renderer.GlStateManager.color(1, 1, 1, 1);
        this.mc.getTextureManager().bindTexture(BG);
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        drawTexturedModalRect(x, y, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int tier = container.getTe().getTier();
        int radius = container.getTe().getEffectiveRadius();
        String state = container.getTe().isCleanseStalled() ? TextFormatting.RED + "cleanse: no fuel"
                : (container.getTe().isCleanseEnabled() ? TextFormatting.GREEN + "cleanse: on" : "cleanse: off");
        this.fontRenderer.drawString("Tier " + tier + "  R=" + radius, 8, 6, 0x404040);
        this.fontRenderer.drawString(state, 8, 72, 0x404040);
    }
}
```
> Uwaga: `getTier()`/`getEffectiveRadius()`/`isCleanseStalled()`/`isCleanseEnabled()` czytają stan TE po stronie klienta — działa dla single-player i integrated server; dla dedicated server wartości mogą być nieświeże bez packet-sync. v1: akceptowalne (feature głównie SP/base). Sync przez packet = do rozważenia w v2 (mod ma już `InsaneTweaksNetwork`).

- [ ] **Step 3: `BlockSanctuaryCore.onBlockActivated`** — otwórz GUI:

```java
    @Override
    public boolean onBlockActivated(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.state.IBlockState state, net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.util.EnumHand hand, net.minecraft.util.EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileEntitySanctuaryCore) {
            player.openGui(com.spege.insanetweaks.InsaneTweaksMod.instance,
                    com.spege.insanetweaks.InsaneTweaksMod.GUI_ID_SANCTUARY, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }
```
> Sprawdź nazwę statycznej instancji moda w `InsaneTweaksMod` (np. `instance`/`INSTANCE`) i użyj właściwej.

- [ ] **Step 4: `InsaneTweaksMod`** — dodaj stałą i obsłuż w `IGuiHandler`:

```java
    public static final int GUI_ID_SANCTUARY = 2;
```
W `getServerGuiElement(...)`:
```java
        if (ID == GUI_ID_SANCTUARY) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
            if (te instanceof com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) {
                return new com.spege.insanetweaks.sanctuary.gui.ContainerSanctuaryCore(
                        player.inventory, (com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) te);
            }
        }
```
W `getClientGuiElement(...)`:
```java
        if (ID == GUI_ID_SANCTUARY) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(new net.minecraft.util.math.BlockPos(x, y, z));
            if (te instanceof com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) {
                return new com.spege.insanetweaks.sanctuary.gui.GuiSanctuaryCore(
                        new com.spege.insanetweaks.sanctuary.gui.ContainerSanctuaryCore(
                                player.inventory, (com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore) te));
            }
        }
```

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6 (MILESTONE, in-game): GUI działa**

Deploy, odpal. PPM na rdzeń → otwiera się GUI z slotem paliwa + 4 slotami upgrade + ekwipunkiem gracza; nagłówek pokazuje `Tier N R=...`. Włóż item do slotu upgrade → po rewalidacji promień rośnie (widać w nagłówku po ponownym otwarciu).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/gui/ src/main/java/com/spege/insanetweaks/sanctuary/BlockSanctuaryCore.java src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java
git commit -m "feat(sanctuary): core GUI (fuel + upgrade slots, tier/radius readout)"
```

---

## Task 9: Upkeep + cleanse driver + degradacja

**Files:**
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryCleanseHelper.java`
- Modify: `src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java` (`update()` — dopisz upkeep+cleanse; kursor rolling)

- [ ] **Step 1: `SanctuaryCleanseHelper.java`** — zwraca liczbę faktycznych konwersji przy skanie wycinka. Reużyj `SrpPurificationHelper` jeśli ma metodę „revert block"; inaczej fallback wg tabeli. Sprawdź API `util/SrpPurificationHelper.java` przed pisaniem — poniżej wersja z fallbackiem.

```java
package com.spege.insanetweaks.sanctuary;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Reverts SRP-infested terrain back to natural blocks within a cylinder slice. */
public final class SanctuaryCleanseHelper {

    private SanctuaryCleanseHelper() {}

    /** Returns true if this position held infested SRP terrain and was reverted. */
    public static boolean tryCleanse(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        ResourceLocation rn = state.getBlock().getRegistryName();
        if (rn == null || !"srparasites".equals(rn.getResourceDomain())) {
            return false;
        }
        String path = rn.getResourcePath();
        // Fallback conversion table: residue/cyst/infested -> natural. Refine against actual SRP block ids.
        if (path.contains("residue") || path.contains("cyst") || path.contains("infest")
                || path.contains("flesh") || path.contains("nerve")) {
            world.setBlockState(pos, Blocks.STONE.getDefaultState(), 3);
            return true;
        }
        return false;
    }
}
```
> Przed implementacją: `grep -rn "srparasites:" ` + `javap` po blokach SRP, by uzupełnić tabelę o realne id (np. `srparasites:block_infest`, `srparasites:block_residue`). Jeśli `SrpPurificationHelper` ma gotowe „purify block" — użyj go zamiast fallbacku.

- [ ] **Step 2: Dopisz upkeep+cleanse w `TileEntitySanctuaryCore.update()`** — na końcu metody (po skanie), oraz helper konsumpcji paliwa. Dodaj pola `cleanseCursor`.

```java
    private int cleanseCursor; // rolling index over the cylinder volume

    private boolean consumeFuelUnit() {
        if (fuelStored > 0) { fuelStored--; markDirty(); return true; }
        // try to burn one fuel item from slot 0
        net.minecraft.item.ItemStack stack = inventory.getStackInSlot(SLOT_FUEL);
        if (stack.isEmpty()) { return false; }
        net.minecraft.util.ResourceLocation rn = stack.getItem().getRegistryName();
        if (rn == null) { return false; }
        int value = fuelValueFor(rn.toString());
        if (value <= 0) { return false; }
        stack.shrink(1);
        inventory.setStackInSlot(SLOT_FUEL, stack);
        fuelStored = value - 1; // consume one conversion now
        markDirty();
        return true;
    }

    private static int fuelValueFor(String registryName) {
        for (String line : com.spege.insanetweaks.config.ModConfig.sanctuary.fuelItems) {
            int eq = line.indexOf('=');
            if (eq <= 0) { continue; }
            if (line.substring(0, eq).trim().equalsIgnoreCase(registryName)) {
                try { return Integer.parseInt(line.substring(eq + 1).trim()); } catch (NumberFormatException ex) { return 0; }
            }
        }
        return 0;
    }

    private void runCleanse() {
        if (!cleanseEnabled || tier < 1 || effectiveRadius <= 0) { cleanseStalled = false; return; }
        int r = effectiveRadius;
        int diameter = r * 2 + 1;
        int height = world.getHeight(); // full column
        int perTick = com.spege.insanetweaks.config.ModConfig.sanctuary.cleanseBlocksPerTick;
        boolean anyStall = false;
        for (int i = 0; i < perTick; i++) {
            // map cleanseCursor -> (dx, y, dz) over the bounding box; skip outside cylinder
            long total = (long) diameter * diameter * height;
            if (total <= 0) { break; }
            int idx = (int) (((long) cleanseCursor) % total);
            cleanseCursor = (int) (((long) cleanseCursor + 1) % total);
            int y = idx / (diameter * diameter);
            int rem = idx % (diameter * diameter);
            int dx = (rem / diameter) - r;
            int dz = (rem % diameter) - r;
            if ((long) dx * dx + (long) dz * dz > (long) r * r) { continue; }
            net.minecraft.util.math.BlockPos p = new net.minecraft.util.math.BlockPos(pos.getX() + dx, y, pos.getZ() + dz);
            net.minecraft.util.math.BlockPos check = p; // read cheap first
            if (!isInfestedQuick(check)) { continue; }
            if (!consumeFuelUnit()) { anyStall = true; break; }
            com.spege.insanetweaks.sanctuary.SanctuaryCleanseHelper.tryCleanse(world, p);
        }
        cleanseStalled = anyStall;
    }

    private boolean isInfestedQuick(net.minecraft.util.math.BlockPos p) {
        net.minecraft.util.ResourceLocation rn = world.getBlockState(p).getBlock().getRegistryName();
        return rn != null && "srparasites".equals(rn.getResourceDomain());
    }
```
Następnie w `update()` dopisz na końcu (po bloku skanu):
```java
        runCleanse();
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4 (MILESTONE, in-game): cleanse + degradacja**

Deploy, odpal na DEv 1.2. Aktywny rdzeń, w strefie kilka infested bloków SRP. Włóż paliwo (np. emerald), cleanse ON → infested bloki znikają stopniowo (kamień), emerald ubywa. Opróżnij paliwo → nagłówek GUI „cleanse: no fuel", ale próba postawienia beckona w strefie NADAL wetowana (tarcza żyje). Gdy brak infested w promieniu → paliwo nie ubywa.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryCleanseHelper.java src/main/java/com/spege/insanetweaks/sanctuary/TileEntitySanctuaryCore.java
git commit -m "feat(sanctuary): fuel upkeep + cleanse driver + degradation"
```

---

## Task 10: Auto-disable bez SRP + particle border + receptura

**Files:**
- Modify: `src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java` (auto-disable w preInit)
- Create: `src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryBorderRenderer.java` (klient, particle)
- Create: `src/main/resources/assets/insanetweaks/recipes/sanctuary_core.json`

- [ ] **Step 1: Auto-disable modułu gdy SRP nieobecny** — w `preInit` (wzorzec Bauble Fruits→Baubles). Znajdź istniejący blok auto-disable i dopisz:

```java
        if (com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary && !Loader.isModLoaded(SRP_MODID)) {
            com.spege.insanetweaks.config.ModConfig.modules.enableSanctuary = false;
            LOGGER.warn("[InsaneTweaks] Sanctuary module auto-disabled: SRParasites not present.");
        }
```
> Uwaga kolejność: blok musi być JUŻ zarejestrowany (Register<Block> odpala przed init). Jeśli auto-disable ma zapobiec też rejestracji bloku, przenieś check tak, by `ModBlocks` widział flagę OFF — najprościej: `ModBlocks` już gate'uje na `enableSanctuary`, więc gdy SRP absent i flaga w configu = true, blok się zarejestruje mimo braku SRP. Zaakceptuj to (blok istnieje, veto no-op), albo dodatkowo w `ModBlocks` sprawdzaj `Loader.isModLoaded(SRP_MODID)`. Wybierz: dodaj `&& Loader.isModLoaded(InsaneTweaksMod.SRP_MODID)` do gate'a w `ModBlocks.registerBlocks/registerItemBlocks/registerModels`.

- [ ] **Step 2: Particle border (klient)** — `SanctuaryBorderRenderer` na `TickEvent.ClientTickEvent`/`RenderWorldLastEvent`, rysuje particle po obwodzie cylindra pobliskich stref (czyta lokalny `SanctuaryWorldData` klienta — dla SP OK). Gate `ModConfig.sanctuary.particleBorder`. Rejestruj client-only w `init` pod `enableSanctuary`.

```java
package com.spege.insanetweaks.sanctuary;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class SanctuaryBorderRenderer {

    private int tick;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !ModConfig.sanctuary.particleBorder) { return; }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) { return; }
        if ((++tick % 10) != 0) { return; }
        // NOTE: client SanctuaryWorldData is only populated in single-player/integrated server.
        // For a first pass, spawn a ring of particles around the player-nearest region if inside one.
        // (Full border rendering can be refined later.)
        // Implementation detail: iterate a coarse ring at effectiveRadius; spawn SPELL_MOB particles.
        // Left minimal here; verify visually and refine spacing.
        double px = mc.player.posX, pz = mc.player.posZ;
        for (int deg = 0; deg < 360; deg += 15) {
            double rad = Math.toRadians(deg);
            double x = px + Math.cos(rad) * 8.0;
            double z = pz + Math.sin(rad) * 8.0;
            mc.world.spawnParticle(EnumParticleTypes.SPELL_MOB, x, mc.player.posY + 1, z, 0.0, 0.0, 0.0);
        }
    }
}
```
> To jest placeholder-wizual (pierścień wokół gracza, nie realna granica strefy) — świadomie minimalny na v1; dokładne rysowanie granicy cylindra wymaga dostępu do promienia/centrum strefy po stronie klienta (packet-sync, v2). Jeśli wolisz, POMIŃ particle w v1 (usuń rejestrację) — nie jest krytyczny. Zaznacz decyzję przy wykonaniu.

Rejestracja (client-only, w `init` pod `enableSanctuary`):
```java
            if (event.getSide() == net.minecraftforge.fml.relauncher.Side.CLIENT) {
                MinecraftForge.EVENT_BUS.register(new com.spege.insanetweaks.sanctuary.SanctuaryBorderRenderer());
            }
```

- [ ] **Step 3: Receptura** — `recipes/sanctuary_core.json` (placeholder; dostosuj składniki do balansu paczki):

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": ["OEO", "EBE", "OEO"],
  "key": {
    "O": { "item": "minecraft:obsidian" },
    "E": { "item": "minecraft:emerald" },
    "B": { "item": "minecraft:beacon" }
  },
  "result": { "item": "insanetweaks:sanctuary_core" }
}
```

- [ ] **Step 4: Build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5 (MILESTONE, in-game): pełny cykl**

Deploy, odpal. Skraft rdzeń recepturą. Zbuduj piramidę, otwórz GUI, sprawdź tier/promień. Wejdź w strefę → particle border widoczne (jeśli włączone). W dim `111` → kopuła inert. Bez SRP w innym instancie (jeśli masz) → moduł auto-disabled w logu.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/spege/insanetweaks/InsaneTweaksMod.java src/main/java/com/spege/insanetweaks/init/ModBlocks.java src/main/java/com/spege/insanetweaks/sanctuary/SanctuaryBorderRenderer.java src/main/resources/assets/insanetweaks/recipes/sanctuary_core.json
git commit -m "feat(sanctuary): auto-disable w/o SRP, particle border, recipe"
```

---

## Task 11: Integracja końcowa + aktualizacja notatek

**Files:**
- Modify: `notes/insanetweaks_projects.md` (status #2)
- (opcjonalnie) `CLAUDE.md` — jeśli dodanie pierwszego bloku/TE zmienia opis architektury

- [ ] **Step 1: Pełny przelot testowy** — wykonaj scenariusz z sekcji „Weryfikacja" spec-a (spawn veto, konwersja veto, cleanse+degradacja, chunk unload, dim blacklist). Zanotuj wyniki.

- [ ] **Step 2: Zaktualizuj `notes/insanetweaks_projects.md`** — zmień status #2 z „BRIEF" na „v1 zaimplementowane" ze streszczeniem (moduł, pliki, co v2). Wzór jak wpis #1 (Grimoire).

- [ ] **Step 3 (jeśli dotyczy): CLAUDE.md** — sekcja Architektura: dopisz, że mod ma teraz pierwszy custom Block+TileEntity+WorldSavedData (pakiet `sanctuary/`, `init/ModBlocks.java`, `GUI_ID_SANCTUARY=2`).

- [ ] **Step 4: Commit**

```bash
git add notes/insanetweaks_projects.md CLAUDE.md
git commit -m "docs(sanctuary): update project status + architecture notes"
```

---

## Self-review (autor planu)

**Spec coverage:** aktywacja hybryda (Task 5 tier + Task 8 upgrade sloty) ✓; cylinder full-height (Task 4 `isInside`) ✓; prevent (Task 6 spawn + Task 7 konwersja) ✓; cleanse (Task 9) ✓; upkeep split tarcza/cleanse — tarcza = Task 6/7 (bez paliwa), cleanse = Task 9 (paliwo) ✓; degradacja (Task 9 `cleanseStalled`) ✓; dim blacklist (Task 4/6 `isDimensionBlacklisted`) ✓; config (Task 1) ✓; auto-disable bez SRP (Task 10) ✓; particle (Task 10, minimalny) ✓; v2 poza zakresem (mixiny AI/meteor) — świadomie pominięte.

**Type consistency:** `SanctuaryWorldData.isInside(int x,int z)` + `setRegion(BlockPos,int)` + `removeRegion(BlockPos)` używane spójnie w Task 4/5/6/7. `SanctuaryRegionHelper.isProtected(World,int,int)`/`(World,BlockPos)` + `isDimensionBlacklisted(World)` spójne. TE: `getTier/getEffectiveRadius/isCleanseEnabled/isCleanseStalled` (Task 3) używane w GUI (Task 8). `SLOT_FUEL`/`UPGRADE_SLOTS` spójne (Task 3/8/9). `TileEntitySanctuaryCore.onRemovedFromWorld()` (Task 5) wołane w `breakBlock` (Task 5).

**Znane punkty do potwierdzenia przy wykonaniu (nie placeholdery — realne zależności od SRP/instancji):** (a) sygnatura `beckonInfestation` — `javap` w Task 7 kroku 1; (b) realne id bloków SRP dla tabeli cleanse — Task 9 krok 1; (c) nazwa statycznej instancji moda w `InsaneTweaksMod` (`instance` vs `INSTANCE`) — Task 8 krok 3; (d) istniejący blok auto-disable w preInit — Task 10 krok 1; (e) API `SrpPurificationHelper` — Task 9. Każdy ma krok weryfikacyjny w zadaniu.
