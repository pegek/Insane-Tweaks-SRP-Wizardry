package com.spege.insanetweaks.anchor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.google.common.base.Predicate;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;
import com.spege.insanetweaks.worldgen.sanctum.SanctumLocks;

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
 * The anchor's phase machine (server only; EBW renders the statue).
 *
 * <ul>
 *   <li>EMPTY 0 - create a fresh mob (pin or pool), keep a copy of its NBT, petrify it on top.</li>
 *   <li>DORMANT 1 - wait for a survival player in range and an open SRP phase gate.</li>
 *   <li>WAKING 2 - crack, then remove the statue WITHOUT EBW's release.</li>
 *   <li>RELEASING 4 - after the release delay, spawn the mob from the stored copy.</li>
 *   <li>AWAKE 5 - watch the mob by UUID until it dies or stays missing for 60 s.</li>
 *   <li>SPENT 3 - flip the block to its spent stage, which drops this tile entity.</li>
 * </ul>
 *
 * Spawning from our own copy is what makes a wake-up reliable: EBW rebuilds a statue's creature
 * lazily after a chunk load, and a break before that released nothing (playtest 2026-10-20).
 * Phase numbers 0-3 are unchanged from 1.23, so saved anchors keep their state; a pre-1.25 statue
 * without a copy falls back to EBW's release.
 */
public class TileEntityPetrifiedAnchor extends TileEntity implements ITickable {

    static final byte EMPTY = 0;
    static final byte DORMANT = 1;
    static final byte WAKING = 2;
    static final byte SPENT = 3;
    static final byte RELEASING = 4;
    static final byte AWAKE = 5;

    private static final int BUILD_RETRY_TICKS = 100;
    private static final int WATCH_INTERVAL = 10;
    private static final int GUARD_INTERVAL = 20;
    private static final int MISSING_LIMIT = 1200;
    private static final int WARN_AFTER_ATTEMPTS = 3;
    private static final int TARGET_LOCK_TICKS = 60;
    private static final Set<String> WARNED_POOL_LINES = new HashSet<String>();

    private byte phase = EMPTY;
    private String pinnedMob = "";
    private int attempts;
    private int ticks;
    private int crackTimer;
    private int releaseTimer;
    private NBTTagCompound mobData;
    private UUID mobUuid;
    private int missingTicks;
    /** Where the mob was last seen; a missing mob whose chunk is unloaded is not counted as gone. */
    private long lastMobPos;
    private boolean lastMobKnown;
    private boolean mobGone;
    private boolean hasSanctum;
    private long sanctumId;

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
            case RELEASING:
                release();
                break;
            case AWAKE:
                if (this.ticks % GUARD_INTERVAL == 0) {
                    guard();
                }
                break;
            default:
                setStage(BlockPetrifiedAnchor.Stage.SPENT);
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

    // ---- EMPTY ----------------------------------------------------------------------------

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
                mob.enablePersistence();
                if (mob instanceof EntityParasiteBase) {
                    ((EntityParasiteBase) mob).cannotDespawn(false);
                }
                AnchorLeash.tag(mob, this.pos, this.world.provider.getDimension(), this.hasSanctum, this.sanctumId);
                NBTTagCompound data = new NBTTagCompound();
                if (!mob.writeToNBTAtomically(data)) {
                    failure = "the mob could not be saved";
                } else if (!AnchorStatues.petrify(mob)) {
                    failure = "EBW refused the statue (no room for a tall mob, or block placement denied)";
                } else {
                    this.mobData = data;
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

    // ---- DORMANT / WAKING / RELEASING ------------------------------------------------------

    private void watch() {
        if (!AnchorStatues.isStatueAt(this.world, statuePos())) {
            // Removed by something other than us (e.g. /setblock): release from the copy if we have one.
            if (this.mobData != null) {
                spawnMob(null);
            } else {
                finish();
            }
            return;
        }
        if (wakeAllowed(this.world) && nearestWaker() != null) {
            this.phase = WAKING;
            this.crackTimer = 0;
            markDirty();
        }
    }

    /**
     * The SRP phase gate: statues stay petrified until the evolution phase (per
     * {@code tweaks.srpPhaseSource}) reaches {@code minPhaseToWake}. Also read by AnchorStatueGuard.
     */
    public static boolean wakeAllowed(World world) {
        int min = ModConfig.entities.petrifiedAnchor.minPhaseToWake;
        return min <= 0 || com.spege.insanetweaks.util.SrpPhaseHelper.getGatingPhase(world) >= min;
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
            watch();
            return;
        }
        this.crackTimer++;
        debris(at, 6);
        if (this.crackTimer % 5 == 0) {
            this.world.playSound(null, at, SoundEvents.BLOCK_STONE_HIT, SoundCategory.HOSTILE, 1.0F,
                    0.6F + this.world.rand.nextFloat() * 0.3F);
        }
        if (this.crackTimer < cfg().crackTicks) {
            return;
        }
        if (this.mobData == null) {
            // Pre-1.25 statue: no copy, so let EBW release its creature.
            EntityLiving legacy = AnchorStatues.creatureAt(this.world, at);
            AnchorStatues.release(this.world, at);
            if (legacy != null && legacy.isEntityAlive()) {
                track(legacy, nearestWaker());
            } else {
                finish();
            }
            return;
        }
        AnchorStatues.discard(this.world, at);
        debris(at, 40);
        this.world.playSound(null, at, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 1.2F, 0.7F);
        this.phase = RELEASING;
        this.releaseTimer = 0;
        markDirty();
    }

    private void release() {
        this.releaseTimer++;
        BlockPos at = statuePos();
        MagicParasiteTraits.burst(this.world, at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D, 3, 0.5D);
        if (this.releaseTimer >= cfg().releaseDelayTicks) {
            spawnMob(nearestWaker());
        }
    }

    private void spawnMob(EntityPlayer waker) {
        Entity entity = this.mobData == null ? null : EntityList.createEntityFromNBT(this.mobData, this.world);
        if (!(entity instanceof EntityLiving)) {
            finish();
            return;
        }
        BlockPos at = statuePos();
        entity.setLocationAndAngles(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, entity.rotationYaw, 0.0F);
        if (!this.world.spawnEntity(entity)) {
            finish();
            return;
        }
        MagicParasiteTraits.burst(this.world, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ, 24, 0.5D);
        this.world.playSound(null, at, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, SoundCategory.HOSTILE, 1.0F, 0.6F);
        track((EntityLiving) entity, waker);
    }

    private void track(EntityLiving mob, EntityPlayer waker) {
        this.mobUuid = mob.getUniqueID();
        this.mobData = null;
        this.missingTicks = 0;
        this.lastMobPos = mob.getPosition().toLong();
        this.lastMobKnown = true;
        if (waker != null) {
            aim(mob, waker);
        }
        this.phase = AWAKE;
        setStage(BlockPetrifiedAnchor.Stage.AWAKE);
        markDirty();
    }

    /** SRP parasites take the target-lock overload, so their own retargeting cannot drop the player at once. */
    private static void aim(EntityLiving mob, EntityPlayer target) {
        if (mob instanceof EntityParasiteBase) {
            ((EntityParasiteBase) mob).setAttackTarget(target, TARGET_LOCK_TICKS);
        } else {
            mob.setAttackTarget(target);
        }
    }

    // ---- AWAKE ----------------------------------------------------------------------------

    private void guard() {
        if (this.mobGone || this.mobUuid == null
                || (this.hasSanctum && SanctumLocks.isAnchorDone(this.world, this.sanctumId, this.pos))) {
            finish();
            return;
        }
        Entity mob = this.world instanceof WorldServer ? ((WorldServer) this.world).getEntityFromUuid(this.mobUuid)
                : null;
        if (mob != null && mob.isDead) {
            finish();
            return;
        }
        if (mob == null) {
            if (!this.lastMobKnown || this.world.isBlockLoaded(BlockPos.fromLong(this.lastMobPos))) {
                this.missingTicks += GUARD_INTERVAL;
                if (this.missingTicks >= MISSING_LIMIT) {
                    finish();
                }
            }
        } else {
            this.missingTicks = 0;
            this.lastMobPos = mob.getPosition().toLong();
            this.lastMobKnown = true;
        }
    }

    /** Called by AnchorLeashHandler when this anchor's mob dies. */
    void markMobGone() {
        this.mobGone = true;
        markDirty();
    }

    private void finish() {
        this.phase = SPENT;
        markDirty();
        if (this.hasSanctum) {
            SanctumLocks.anchorDone(this.world, this.sanctumId, this.pos);
        }
        setStage(BlockPetrifiedAnchor.Stage.SPENT);
    }

    private void setStage(BlockPetrifiedAnchor.Stage stage) {
        IBlockState state = this.world.getBlockState(this.pos);
        if (state.getBlock() instanceof BlockPetrifiedAnchor && state.getValue(BlockPetrifiedAnchor.STAGE) != stage) {
            this.world.setBlockState(this.pos, state.withProperty(BlockPetrifiedAnchor.STAGE, stage), 3);
        }
    }

    private void debris(BlockPos at, int count) {
        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.BLOCK_CRACK, at.getX() + 0.5D,
                    at.getY() + 1.0D, at.getZ() + 0.5D, count, 0.35D, 0.8D, 0.35D, 0.05D,
                    Block.getStateId(Blocks.STONE.getDefaultState()));
        }
        MagicParasiteTraits.burst(this.world, at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D, 2, 0.4D);
    }

    // ---- configuration from worldgen and creative -----------------------------------------

    /** Creative pin: remember the id and rebuild the statue from it. Empty string clears the pin. */
    void pin(String id) {
        this.pinnedMob = id == null ? "" : id;
        if (this.phase == DORMANT || this.phase == WAKING) {
            AnchorStatues.discard(this.world, statuePos());
        }
        this.phase = EMPTY;
        this.mobData = null;
        this.attempts = 0;
        this.ticks = 0;
        markDirty();
    }

    /** Worldgen: pin a mob before the first statue is built. */
    public void pinAtGeneration(String id) {
        this.pinnedMob = id == null ? "" : id;
        markDirty();
    }

    /** Worldgen: this anchor belongs to a sanctum (Arcane Lock bookkeeping). */
    public void setSanctum(long id) {
        this.hasSanctum = true;
        this.sanctumId = id;
        markDirty();
    }

    /** Same-block stage changes keep this tile entity except the change to SPENT. */
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
        this.mobData = compound.hasKey("MobData", 10) ? compound.getCompoundTag("MobData") : null;
        this.mobUuid = compound.hasUniqueId("Mob") ? compound.getUniqueId("Mob") : null;
        this.missingTicks = compound.getInteger("Missing");
        this.lastMobPos = compound.getLong("LastMobPos");
        this.lastMobKnown = compound.getBoolean("LastMobKnown");
        this.mobGone = compound.getBoolean("MobGone");
        this.hasSanctum = compound.getBoolean("HasSanctum");
        this.sanctumId = compound.getLong("Sanctum");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setByte("Phase", this.phase);
        compound.setString("PinnedMob", this.pinnedMob);
        compound.setByte("Attempts", (byte) Math.min(this.attempts, 127));
        if (this.mobData != null) {
            compound.setTag("MobData", this.mobData);
        }
        if (this.mobUuid != null) {
            compound.setUniqueId("Mob", this.mobUuid);
        }
        compound.setInteger("Missing", this.missingTicks);
        compound.setLong("LastMobPos", this.lastMobPos);
        compound.setBoolean("LastMobKnown", this.lastMobKnown);
        compound.setBoolean("MobGone", this.mobGone);
        compound.setBoolean("HasSanctum", this.hasSanctum);
        compound.setLong("Sanctum", this.sanctumId);
        return compound;
    }
}
