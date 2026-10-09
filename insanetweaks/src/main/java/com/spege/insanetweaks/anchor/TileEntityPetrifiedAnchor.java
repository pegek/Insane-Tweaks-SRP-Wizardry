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
        if (wakeAllowed(this.world) && nearestWaker() != null) {
            this.phase = WAKING;
            this.crackTimer = 0;
            markDirty();
        }
    }

    /**
     * The SRP phase gate: statues stay petrified until the evolution phase (per
     * {@code tweaks.srpPhaseSource}) reaches {@code minPhaseToWake}. Also read by
     * {@link AnchorStatueGuard}, which refuses breaking or blasting a statue while this is false.
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
