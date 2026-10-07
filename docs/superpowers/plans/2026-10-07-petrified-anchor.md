# Petrified Anchor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A structure block `insanetweaks:petrified_anchor` that turns a freshly created mob into an EBW stone statue standing on it, and releases the mob when a survival player comes close.

**Architecture:**
- A block with an `awakened` property and a server-ticking tile entity running a 4-phase state machine (EMPTY → DORMANT → WAKING → SPENT).
- EBW's `BlockStatue` / `TileEntityStatue` does the statue rendering, mob storage and release. The only code naming EBW types sits in `AnchorStatues`.
- The weighted mob pool is pure Java and unit-tested.

**Tech Stack:** Minecraft 1.12.2 Forge (FG3, Java 8), Electroblob's Wizardry 4.3.19 (required dependency), JUnit 4.12.

Spec: `docs/superpowers/specs/2026-10-07-petrified-anchor-design.md`.

Every Gradle call needs JDK 8:
`./gradlew "-Dorg.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-8.0.492.9-hotspot" …`.

**Verified facts (bytecode, 2026-10-07):**
- `WizardryBlocks.petrified_stone` is a `BlockStatue`. `BlockStatue.convertToStatue(EntityLiving, EntityLivingBase caster, int lifetime)` returns boolean and places the statue starting at `new BlockPos(mob)`.
- `TileEntityStatue` has public fields `creature` (EntityLiving) and `position` (1 = bottom).
- `BlockStatue.breakBlock` releases `creature` **only** when `position == 1 && creature != null`.
- The statue self-breaks when `timer > lifetime` (checked every 200 ticks). `Integer.MAX_VALUE` therefore means never.
- With a null caster, EBW's placement check reads `dispenserBlockDamage`, which is true in DEv 1.2.

**Deviations from the spec, deliberate:**
1. **Textures.** The intact plinth uses vanilla `stonebrick_carved` and the awakened one `stonebrick_cracked`. Two vanilla-textured models replace the two new PNGs; custom art can come later without code changes.
2. **No `StatueY` NBT field.** The statue is always at `pos.up()`.
3. **Tile entity removal.** The tile entity overrides `shouldRefresh`. Forge's default keeps a tile entity across a same-block state change, so flipping `awakened` would otherwise leave it ticking.
4. **Registration order.** The anchor registers **before** `ModBlocks`' sanctuary early return, so it exists even with Sanctuary disabled.

---

## File map

| File | Responsibility |
|---|---|
| `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/AnchorPool.java` | Pure: parse `id;weight`, weighted pick |
| `…/anchor/AnchorStatues.java` | The only EBW-typed code: petrify, test for, release and discard a statue |
| `…/anchor/TileEntityPetrifiedAnchor.java` | Phase machine, NBT, creative pin |
| `…/anchor/BlockPetrifiedAnchor.java` | Block, states, hardness, drops, creative interaction |
| `…/config/categories/EntitiesCategory.java` | `PetrifiedAnchor` subcategory |
| `…/init/ModBlocks.java` | Register block, ItemBlock, tile entity, model |
| `insanetweaks/src/main/resources/assets/insanetweaks/blockstates/petrified_anchor.json` + `models/block/petrified_anchor*.json` + `models/item/petrified_anchor.json` | Models |
| `…/lang/en_us.lang` | Names and messages |
| `insanetweaks/src/test/java/com/spege/insanetweaks/anchor/AnchorPoolTest.java` | JUnit |

---

### Task 1: AnchorPool (TDD)

- [ ] **Step 1: Write the failing test.**

File: `insanetweaks/src/test/java/com/spege/insanetweaks/anchor/AnchorPoolTest.java`
```java
package com.spege.insanetweaks.anchor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AnchorPoolTest {

    @Test
    public void equalWeightsSplitTheRollEvenly() {
        AnchorPool pool = AnchorPool.parse(new String[] { "a:x;1", "a:y;1" });
        assertEquals("a:x", pool.pick(0.0D));
        assertEquals("a:x", pool.pick(0.49D));
        assertEquals("a:y", pool.pick(0.5D));
        assertEquals("a:y", pool.pick(0.999D));
    }

    @Test
    public void heavierEntriesTakeMoreOfTheRoll() {
        AnchorPool pool = AnchorPool.parse(new String[] { "a:x;3", "a:y;1" });
        assertEquals("a:x", pool.pick(0.74D));
        assertEquals("a:y", pool.pick(0.75D));
    }

    @Test
    public void aMissingWeightMeansOne() {
        AnchorPool pool = AnchorPool.parse(new String[] { "a:x", "a:y;1" });
        assertEquals("a:x", pool.pick(0.2D));
        assertEquals("a:y", pool.pick(0.7D));
    }

    @Test
    public void malformedAndNonPositiveEntriesAreRejected() {
        AnchorPool pool = AnchorPool.parse(new String[] { "a:x;0", "a:y;-2", "a:z;abc", " ", "a:w;2" });
        assertEquals("a:w", pool.pick(0.0D));
        assertEquals("a:w", pool.pick(0.99D));
        assertEquals(3, pool.rejected().size());
    }

    @Test
    public void anEmptyPoolPicksNothing() {
        AnchorPool pool = AnchorPool.parse(new String[0]);
        assertTrue(pool.isEmpty());
        assertNull(pool.pick(0.5D));
    }

    @Test
    public void whitespaceAroundPartsIsIgnored() {
        AnchorPool pool = AnchorPool.parse(new String[] { "  a:x ; 2 " });
        assertEquals("a:x", pool.pick(0.5D));
    }
}
```

- [ ] **Step 2: Run the test.** `./gradlew "-Dorg.gradle.java.home=…" :insanetweaks:test`
  Expected: compilation FAIL with "cannot find symbol AnchorPool".

- [ ] **Step 3: Implement.**

File: `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/AnchorPool.java`
```java
package com.spege.insanetweaks.anchor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Petrified Anchor's weighted mob pool, parsed from config lines {@code "modid:entity;weight"}.
 * A missing weight means 1; zero, negative or unparseable weights and blank lines are rejected
 * (and reported by the caller). Pure Java, so it is unit-tested.
 */
public final class AnchorPool {

    private final String[] ids;
    private final int[] cumulative;
    private final int total;
    private final List<String> rejected;

    private AnchorPool(List<String> ids, List<Integer> weights, List<String> rejected) {
        this.ids = ids.toArray(new String[0]);
        this.cumulative = new int[weights.size()];
        int sum = 0;
        for (int i = 0; i < weights.size(); i++) {
            sum += weights.get(i).intValue();
            this.cumulative[i] = sum;
        }
        this.total = sum;
        this.rejected = Collections.unmodifiableList(rejected);
    }

    public static AnchorPool parse(String[] lines) {
        List<String> ids = new ArrayList<String>();
        List<Integer> weights = new ArrayList<Integer>();
        List<String> rejected = new ArrayList<String>();
        for (String raw : lines) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String[] parts = raw.split(";", -1);
            String id = parts[0].trim();
            int weight = 1;
            if (parts.length > 1) {
                try {
                    weight = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    weight = 0;
                }
            }
            if (id.isEmpty() || weight <= 0 || parts.length > 2) {
                rejected.add(raw);
                continue;
            }
            ids.add(id);
            weights.add(Integer.valueOf(weight));
        }
        return new AnchorPool(ids, weights, rejected);
    }

    public boolean isEmpty() {
        return this.total <= 0;
    }

    /** The id whose weight band contains {@code roll} (in [0, 1)), or null for an empty pool. */
    public String pick(double roll) {
        if (isEmpty()) {
            return null;
        }
        int ticket = (int) Math.floor(roll * this.total);
        if (ticket >= this.total) {
            ticket = this.total - 1;
        }
        for (int i = 0; i < this.cumulative.length; i++) {
            if (ticket < this.cumulative[i]) {
                return this.ids[i];
            }
        }
        return this.ids[this.ids.length - 1];
    }

    public List<String> rejected() {
        return this.rejected;
    }
}
```

- [ ] **Step 4: Run the tests.** Expected: all pass (44 existing + 6 new).
- [ ] **Step 5: Commit.** `git commit -m "feat(insanetweaks): petrified anchor weighted mob pool"`

---

### Task 2: Config and lang

- [ ] **Step 1:** In `EntitiesCategory`, after the `magicParasites` field, add the subcategory field:

```java
    @Config.Name("petrified_anchor")
    @Config.LangKey("config.insanetweaks.category.entities.petrified_anchor")
    @Config.Comment("Petrified Anchor: a structure plinth whose stone statue (EBW petrification) wakes into a mob when a player comes close.")
    public final PetrifiedAnchor petrifiedAnchor = new PetrifiedAnchor();
```

- [ ] **Step 2:** Before the `public static class MagicParasites {` line, add the nested class:

```java
    /** All values are read live. */
    public static class PetrifiedAnchor {
        @Config.Comment({ "Mobs an anchor without a creative pin picks from, as 'modid:entity;weight'.",
                "A missing weight means 1. Malformed lines are skipped with a warning." })
        @Config.Name("Pool")
        public String[] pool = { "insanetweaks:magic_longarms;1", "insanetweaks:magic_manducater;1",
                "insanetweaks:magic_summoner;1" };

        @Config.Comment("A survival or adventure player this close (blocks) to the plinth wakes the statue.")
        @Config.Name("Wake Radius")
        @Config.RangeDouble(min = 1.0, max = 32.0)
        public double wakeRadius = 4.0;

        @Config.Comment("Ticks the statue cracks and shakes before the mob breaks free.")
        @Config.Name("Crack Ticks")
        @Config.RangeInt(min = 0, max = 200)
        public int crackTicks = 20;

        @Config.Comment({ "Dimensions where anchors stay empty plinths and never make a statue.",
                "150 keeps the no-SRP invariant of that dimension." })
        @Config.Name("Dimension Blacklist")
        public int[] dimensionBlacklist = { 150 };
    }
```

- [ ] **Step 3: Lang.** In `en_us.lang`:
  - after `config.insanetweaks.category.entities.magic_parasites=Magic Parasites` add
    `config.insanetweaks.category.entities.petrified_anchor=Petrified Anchor`;
  - after `tile.insanetweaks.creative_sanctuary.name=Creative Sanctuary` add:

```
tile.insanetweaks.petrified_anchor.name=Petrified Anchor
msg.insanetweaks.anchor.pinned=Anchor pinned to %s
msg.insanetweaks.anchor.reset=Anchor reset to the random pool
```

- [ ] **Step 4: Compile** (`:insanetweaks:compileJava`). **Step 5: Commit** `feat(insanetweaks): petrified anchor config and names`.

---

### Task 3: AnchorStatues (the EBW boundary)

File: `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/AnchorStatues.java`
```java
package com.spege.insanetweaks.anchor;

import javax.annotation.Nullable;

import electroblob.wizardry.block.BlockStatue;
import electroblob.wizardry.registry.WizardryBlocks;
import electroblob.wizardry.tileentity.TileEntityStatue;

import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Everything the anchor needs from EBW's petrification, in one place. EBW stores the mob's NBT in
 * the statue's tile entity, renders the real model in stone, and spawns the mob back in
 * {@code BlockStatue.breakBlock} - but only for the bottom part ({@code position == 1}) and only
 * while {@code creature} is non-null. That second condition is what {@link #discard} uses to
 * remove a statue without releasing anything.
 */
public final class AnchorStatues {

    /** Bottom part of a statue, per TileEntityStatue.position. */
    private static final int BOTTOM = 1;
    /** Statues are at most three blocks tall. */
    private static final int MAX_PARTS = 3;

    private AnchorStatues() {
    }

    /**
     * Turns {@code mob} (created, positioned, never spawned) into a permanent statue at its own
     * position. Null caster: EBW then checks its dispenserBlockDamage setting instead of the
     * mob-griefing rule.
     */
    public static boolean petrify(EntityLiving mob) {
        return ((BlockStatue) WizardryBlocks.petrified_stone).convertToStatue(mob, null, Integer.MAX_VALUE);
    }

    public static boolean isStatueAt(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() == WizardryBlocks.petrified_stone;
    }

    /** The petrified mob held by the statue's bottom block, or null. */
    @Nullable
    public static EntityLiving creatureAt(World world, BlockPos bottom) {
        TileEntity te = world.getTileEntity(bottom);
        return te instanceof TileEntityStatue ? ((TileEntityStatue) te).creature : null;
    }

    /** Breaks the statue so EBW releases its mob. */
    public static void release(World world, BlockPos bottom) {
        world.destroyBlock(bottom, false);
    }

    /** Removes the statue without releasing its mob. */
    public static void discard(World world, BlockPos bottom) {
        TileEntity te = world.getTileEntity(bottom);
        if (te instanceof TileEntityStatue && ((TileEntityStatue) te).position == BOTTOM) {
            ((TileEntityStatue) te).creature = null;
        }
        for (int i = MAX_PARTS - 1; i >= 0; i--) {
            BlockPos part = bottom.up(i);
            if (isStatueAt(world, part)) {
                world.setBlockState(part, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }
}
```

- [ ] **Compile** and **commit** `feat(insanetweaks): petrified anchor EBW statue boundary`.

Note on `discard`: the top parts go first so that removing the bottom never cascades into
`destroyBlock` of a part that still holds a creature. `breakBlock` on any part destroys its
siblings with `destroyBlock`, and the bottom's creature is already null by then.

---

### Task 4: Tile entity

File: `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/TileEntityPetrifiedAnchor.java`
```java
package com.spege.insanetweaks.anchor;

import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Predicate;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * The anchor's phase machine (server only; the client needs nothing, EBW renders the statue).
 *
 * <ul>
 *   <li>EMPTY - create a fresh mob (pin or pool), petrify it on top, retry every 5 s on failure.</li>
 *   <li>DORMANT - every 10 ticks: statue gone means SPENT, a survival player in range means WAKING.</li>
 *   <li>WAKING - crack for crackTicks, then break the statue (EBW spawns the mob) and aim it.</li>
 *   <li>SPENT - flip the block to awakened, which drops this tile entity.</li>
 * </ul>
 *
 * The mob is never in the world while dormant, so it costs no mob cap and no AI time, and a fresh
 * entity per anchor means no UUID is ever duplicated by a structure template.
 */
public class TileEntityPetrifiedAnchor extends TileEntity implements ITickable {

    static final byte EMPTY = 0;
    static final byte DORMANT = 1;
    static final byte WAKING = 2;
    static final byte SPENT = 3;

    private static final int BUILD_RETRY_TICKS = 100;
    private static final int WATCH_INTERVAL = 10;
    private static final int WARN_AFTER_ATTEMPTS = 3;
    private static final int TARGET_LOCK_TICKS = 60;
    private static final Set<String> WARNED_POOL_LINES = new HashSet<String>();

    private byte phase = EMPTY;
    private String pinnedMob = "";
    private int attempts;
    private int ticks;
    private int crackTimer;

    @Override
    public void update() {
        if (this.world == null || this.world.isRemote) {
            return;
        }
        this.ticks++;
        switch (this.phase) {
            case EMPTY:
                if (this.ticks == 1 || this.ticks % BUILD_RETRY_TICKS == 0) {
                    tryBuildStatue();
                }
                break;
            case DORMANT:
                if (this.ticks % WATCH_INTERVAL == 0) {
                    watch();
                }
                break;
            case WAKING:
                crack();
                break;
            default:
                spend();
                break;
        }
    }

    private EntitiesCategory.PetrifiedAnchor cfg() {
        return ModConfig.entities.petrifiedAnchor;
    }

    private BlockPos statuePos() {
        return this.pos.up();
    }

    private boolean blacklistedDimension() {
        int dim = this.world.provider.getDimension();
        for (int banned : cfg().dimensionBlacklist) {
            if (banned == dim) {
                return true;
            }
        }
        return false;
    }

    private void tryBuildStatue() {
        if (blacklistedDimension()) {
            return;
        }
        String id = chooseMob();
        String failure = null;
        if (id == null) {
            failure = "the mob pool is empty";
        } else if (!this.world.isAirBlock(statuePos())) {
            failure = "the block above the plinth is not free";
        } else {
            Entity entity = EntityList.createEntityByIDFromName(new ResourceLocation(id), this.world);
            if (!(entity instanceof EntityLiving)) {
                failure = "'" + id + "' is not a living entity id";
            } else {
                EntityLiving mob = (EntityLiving) entity;
                float yaw = this.world.rand.nextFloat() * 360.0F;
                BlockPos at = statuePos();
                mob.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, yaw, 0.0F);
                mob.rotationYawHead = yaw;
                mob.renderYawOffset = yaw;
                mob.onInitialSpawn(this.world.getDifficultyForLocation(at), null);
                if (!AnchorStatues.petrify(mob)) {
                    failure = "EBW refused the statue (no room for a tall mob, or block placement denied)";
                }
            }
        }
        if (failure == null) {
            this.phase = DORMANT;
            this.attempts = 0;
            markDirty();
            return;
        }
        this.attempts++;
        if (this.attempts == WARN_AFTER_ATTEMPTS) {
            InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Petrified Anchor at {} (dim {}) cannot make its statue: {}. "
                    + "Retrying every 5 s.", this.pos, Integer.valueOf(this.world.provider.getDimension()), failure);
        }
        markDirty();
    }

    private String chooseMob() {
        if (!this.pinnedMob.isEmpty()) {
            return this.pinnedMob;
        }
        AnchorPool pool = AnchorPool.parse(cfg().pool);
        for (String bad : pool.rejected()) {
            if (WARNED_POOL_LINES.add(bad)) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] Petrified Anchor pool: ignoring malformed line '{}'.", bad);
            }
        }
        return pool.pick(this.world.rand.nextDouble());
    }

    private void watch() {
        if (!AnchorStatues.isStatueAt(this.world, statuePos())) {
            // Broken from range or blown up: EBW already released the mob.
            this.phase = SPENT;
            markDirty();
            return;
        }
        if (nearestWaker() != null) {
            this.phase = WAKING;
            this.crackTimer = 0;
            markDirty();
        }
    }

    private EntityPlayer nearestWaker() {
        BlockPos at = statuePos();
        return this.world.getClosestPlayer(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, cfg().wakeRadius,
                new Predicate<Entity>() {
                    @Override
                    public boolean apply(Entity e) {
                        return e instanceof EntityPlayer && !((EntityPlayer) e).isCreative()
                                && !((EntityPlayer) e).isSpectator();
                    }
                });
    }

    private void crack() {
        BlockPos at = statuePos();
        if (!AnchorStatues.isStatueAt(this.world, at)) {
            this.phase = SPENT;
            markDirty();
            return;
        }
        this.crackTimer++;
        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.BLOCK_CRACK, at.getX() + 0.5D,
                    at.getY() + 1.0D, at.getZ() + 0.5D, 6, 0.35D, 0.8D, 0.35D, 0.05D,
                    Block.getStateId(Blocks.STONE.getDefaultState()));
        }
        MagicParasiteTraits.burst(this.world, at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D, 2, 0.4D);
        if (this.crackTimer % 5 == 0) {
            this.world.playSound(null, at, SoundEvents.BLOCK_STONE_HIT, SoundCategory.HOSTILE, 1.0F,
                    0.6F + this.world.rand.nextFloat() * 0.3F);
        }
        if (this.crackTimer < cfg().crackTicks) {
            return;
        }
        EntityLiving mob = AnchorStatues.creatureAt(this.world, at);
        EntityPlayer waker = nearestWaker();
        AnchorStatues.release(this.world, at);
        this.world.playSound(null, at, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.HOSTILE, 1.0F, 0.6F);
        if (mob != null && waker != null && mob.isEntityAlive()) {
            aim(mob, waker);
        }
        this.phase = SPENT;
        markDirty();
    }

    /** SRP parasites take the target-lock overload, so their own retargeting cannot drop the player at once. */
    private static void aim(EntityLiving mob, EntityPlayer target) {
        if (mob instanceof com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase) {
            ((com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase) mob)
                    .setAttackTarget(target, TARGET_LOCK_TICKS);
        } else {
            mob.setAttackTarget(target);
        }
    }

    private void spend() {
        IBlockState state = this.world.getBlockState(this.pos);
        if (state.getBlock() instanceof BlockPetrifiedAnchor) {
            this.world.setBlockState(this.pos, state.withProperty(BlockPetrifiedAnchor.AWAKENED, Boolean.TRUE), 3);
        }
    }

    /** Creative pin: remember the id and rebuild the statue from it. Empty string clears the pin. */
    void pin(String id) {
        this.pinnedMob = id == null ? "" : id;
        if (this.phase == DORMANT || this.phase == WAKING) {
            AnchorStatues.discard(this.world, statuePos());
        }
        this.phase = EMPTY;
        this.attempts = 0;
        this.ticks = 0;
        markDirty();
    }

    /** A same-block state change (awakened=true) must drop this tile entity too; Forge's default keeps it. */
    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock() || !newState.getBlock().hasTileEntity(newState);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.phase = compound.getByte("Phase");
        this.pinnedMob = compound.getString("PinnedMob");
        this.attempts = compound.getByte("Attempts");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setByte("Phase", this.phase);
        compound.setString("PinnedMob", this.pinnedMob);
        compound.setByte("Attempts", (byte) Math.min(this.attempts, 127));
        return compound;
    }
}
```

- [ ] **Compile** (after Task 5, since the tile entity references the block) and **commit** both tasks together.

---

### Task 5: Block

File: `insanetweaks/src/main/java/com/spege/insanetweaks/anchor/BlockPetrifiedAnchor.java`
```java
package com.spege.insanetweaks.anchor;

import java.util.Random;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/**
 * Petrified Anchor plinth. Active (awakened=false): carries the tile entity, cannot be mined in
 * survival and shrugs off explosions, so an ambush cannot be dug out. Awakened: plain cracked
 * stone that mines normally and drops cobblestone. Creative players pin the statue's mob with a
 * spawn egg, or clear the pin with sneak plus an empty hand.
 */
public class BlockPetrifiedAnchor extends Block {

    public static final PropertyBool AWAKENED = PropertyBool.create("awakened");

    private static final float AWAKENED_HARDNESS = 2.0F;
    private static final float ACTIVE_RESISTANCE = 6000000.0F;

    public BlockPetrifiedAnchor() {
        super(Material.ROCK);
        this.setSoundType(SoundType.STONE);
        this.setResistance(6.0F);
        this.setDefaultState(this.blockState.getBaseState().withProperty(AWAKENED, Boolean.FALSE));
        this.setUnlocalizedName(InsaneTweaksMod.MODID + ".petrified_anchor");
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "petrified_anchor"));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, AWAKENED);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(AWAKENED, Boolean.valueOf((meta & 1) == 1));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(AWAKENED).booleanValue() ? 1 : 0;
    }

    private static boolean active(IBlockState state) {
        return !state.getValue(AWAKENED).booleanValue();
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getBlockHardness(IBlockState state, World world, BlockPos pos) {
        return active(state) ? -1.0F : AWAKENED_HARDNESS;
    }

    @Override
    public float getExplosionResistance(World world, BlockPos pos, Entity exploder, Explosion explosion) {
        return active(world.getBlockState(pos)) ? ACTIVE_RESISTANCE : super.getExplosionResistance(world, pos,
                exploder, explosion);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return active(state);
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return active(state) ? new TileEntityPetrifiedAnchor() : null;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return active(state) ? net.minecraft.init.Items.AIR : Item.getItemFromBlock(Blocks.COBBLESTONE);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!active(state) || !player.isCreative()) {
            return false;
        }
        ItemStack held = player.getHeldItem(hand);
        boolean egg = held.getItem() instanceof ItemMonsterPlacer;
        boolean reset = held.isEmpty() && player.isSneaking();
        if (!egg && !reset) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityPetrifiedAnchor)) {
            return true;
        }
        if (egg) {
            ResourceLocation id = ItemMonsterPlacer.getNamedIdFrom(held);
            if (id == null) {
                return true;
            }
            ((TileEntityPetrifiedAnchor) te).pin(id.toString());
            String key = EntityList.getTranslationName(id);
            player.sendMessage(new TextComponentTranslation("msg.insanetweaks.anchor.pinned",
                    key != null ? new TextComponentTranslation("entity." + key + ".name") : id.toString()));
        } else {
            ((TileEntityPetrifiedAnchor) te).pin("");
            player.sendMessage(new TextComponentTranslation("msg.insanetweaks.anchor.reset"));
        }
        return true;
    }
}
```

- [ ] **Compile** `:insanetweaks:compileJava`. If `getBlockHardness` is not deprecated in these mappings, drop the `@SuppressWarnings`. **Commit** `feat(insanetweaks): petrified anchor block and phase machine`.

---

### Task 6: Registration and assets

- [ ] **Step 1: `ModBlocks`.**
  - Add the field `public static com.spege.insanetweaks.anchor.BlockPetrifiedAnchor PETRIFIED_ANCHOR;`.
  - Add the following as the **first** statements of `registerBlocks`, before the sanctuary early return:

```java
        // Petrified Anchor: independent of the Sanctuary module, so it registers before that gate.
        PETRIFIED_ANCHOR = new com.spege.insanetweaks.anchor.BlockPetrifiedAnchor();
        PETRIFIED_ANCHOR.setCreativeTab(CreativeTabs.MISC);
        event.getRegistry().register(PETRIFIED_ANCHOR);
        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                com.spege.insanetweaks.anchor.TileEntityPetrifiedAnchor.class,
                new ResourceLocation(InsaneTweaksMod.MODID, "petrified_anchor"));
```

  First statements of `registerItemBlocks`:

```java
        if (PETRIFIED_ANCHOR != null) {
            ItemBlock anchorItem = new ItemBlock(PETRIFIED_ANCHOR);
            anchorItem.setRegistryName(PETRIFIED_ANCHOR.getRegistryName());
            event.getRegistry().register(anchorItem);
        }
```

  First statements of `registerModels`:

```java
        if (PETRIFIED_ANCHOR != null) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(PETRIFIED_ANCHOR), 0,
                    new ModelResourceLocation(PETRIFIED_ANCHOR.getRegistryName(), "inventory"));
        }
```

- [ ] **Step 2: Assets.**

File: `insanetweaks/src/main/resources/assets/insanetweaks/blockstates/petrified_anchor.json`
```json
{
  "variants": {
    "awakened=false": { "model": "insanetweaks:petrified_anchor" },
    "awakened=true": { "model": "insanetweaks:petrified_anchor_cracked" },
    "inventory": { "model": "insanetweaks:petrified_anchor" }
  }
}
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/models/block/petrified_anchor.json`
```json
{ "parent": "block/cube_all", "textures": { "all": "minecraft:blocks/stonebrick_carved" } }
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/models/block/petrified_anchor_cracked.json`
```json
{ "parent": "block/cube_all", "textures": { "all": "minecraft:blocks/stonebrick_cracked" } }
```

File: `insanetweaks/src/main/resources/assets/insanetweaks/models/item/petrified_anchor.json`
```json
{ "parent": "insanetweaks:block/petrified_anchor" }
```

- [ ] **Step 3: Compile and commit** — `feat(insanetweaks): register petrified anchor`.

---

### Task 7: Version, build, verify, deploy

- [ ] Bump the version to `1.23.0` in `insanetweaks/build.gradle` and `InsaneTweaksMod.VERSION`.
- [ ] Run `:insanetweaks:test :insanetweaks:build`. Expected: all tests pass.
- [ ] Bytecode checks:
  - side safety: `javap -v` on `anchor/*.class` must show `0` hits for `minecraft/client|fml/client`;
  - the reobfuscated tile entity has `func_73660_a`, `shouldRefresh`, `func_145839_a` and `func_189515_b`;
  - the block has `func_180639_a` (`onBlockActivated`), `func_176195_g` (`getBlockHardness`) and `func_180660_a` (`getItemDropped`);
  - the jar contains the blockstate, the three model JSONs and the lang keys.
- [ ] Deploy `insanetweaks-1.23.0.jar` and delete `insanetweaks-1.22.1.jar` from the instance `mods/`.
- [ ] Commit `chore(insanetweaks): 1.23.0 - petrified anchor`.
- [ ] Give the user the spec §11 in-game checklist.
