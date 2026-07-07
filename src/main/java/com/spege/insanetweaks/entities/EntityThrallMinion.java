package com.spege.insanetweaks.entities;


import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.entities.ai.ThrallAICollecting;
import com.spege.insanetweaks.entities.ai.ThrallAIFarming;
import com.spege.insanetweaks.entities.ai.ThrallAIFollowCaster;
import com.spege.insanetweaks.entities.ai.ThrallAIGatherItems;
import com.spege.insanetweaks.entities.ai.ThrallAIMineshaft;
import com.spege.insanetweaks.entities.ai.ThrallAIPorter;
import com.spege.insanetweaks.entities.ai.ThrallAIWander;
import com.spege.insanetweaks.entities.ai.ThrallAIWoodcutting;
import com.spege.insanetweaks.entities.inventory.ThrallInventory;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;

import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * EntityThrallMinion — a permanent, immortal, utility-only companion summoned by SpellSummonThrall.
 *
 * Design decisions:
 * - Immortal: both attackEntityFrom() and isInvulnerableTo() block all damage.
 * - Non-aggressive: ATTACK_DAMAGE attribute is 0 and setAttackTarget() is a no-op.
 * - Permanent: canDespawn() returns false; no lifetime timer.
 * - Owner is tracked via UUID stored in DataManager + NBT.
 * - Slot cap (currently 1 per player) enforced in SpellSummonThrall.cast().
 * - Smart chest deposit on return home (30-block scan, prefers matching items).
 */
@SuppressWarnings("null")
public class EntityThrallMinion extends EntityCreature {

    private static final Logger LOG = LogManager.getLogger("insanetweaks/ThrallEntity");

    private static boolean debugLogs() {
        return ModConfig.client.enableThrallDebugLogs;
    }

    // -------------------------------------------------------------------------
    // DataManager keys
    // -------------------------------------------------------------------------

    private static final DataParameter<String> OWNER_UUID =
            EntityDataManager.createKey(EntityThrallMinion.class, DataSerializers.STRING);
    private static final DataParameter<String> STATUS_TEXT =
            EntityDataManager.createKey(EntityThrallMinion.class, DataSerializers.STRING);
    private static final DataParameter<Integer> MODE_ORDINAL =
            EntityDataManager.createKey(EntityThrallMinion.class, DataSerializers.VARINT);
    /**
     * Home point synced to client as "x,y,z" string (empty = not set).
     * Uses DataManager so the GUI reads the correct value on the client side.
     */
    private static final DataParameter<String> HOME_POS =
            EntityDataManager.createKey(EntityThrallMinion.class, DataSerializers.STRING);
    /** Persistent slot number (1-3). 0 = unassigned. Synced to client for nameplate. */
    private static final DataParameter<Integer> THRALL_SLOT =
            EntityDataManager.createKey(EntityThrallMinion.class, DataSerializers.VARINT);

    // -------------------------------------------------------------------------
    // Sounds (borrowed from Minions mod)
    // -------------------------------------------------------------------------

    private static final SoundEvent SOUND_SPAWN =
            new SoundEvent(new ResourceLocation("insanetweaks", "thrall/minionspawn")).setRegistryName("insanetweaks:thrall_spawn");
    private static final SoundEvent SOUND_FORYOU =
            new SoundEvent(new ResourceLocation("insanetweaks", "thrall/foryou1")).setRegistryName("insanetweaks:thrall_foryou");
    private static final SoundEvent SOUND_ORDER =
            new SoundEvent(new ResourceLocation("insanetweaks", "thrall/randomorder1")).setRegistryName("insanetweaks:thrall_order");
    private static final SoundEvent SOUND_FOLLOW =
            new SoundEvent(new ResourceLocation("insanetweaks", "thrall/orderfollowplayer")).setRegistryName("insanetweaks:thrall_follow");

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final ThrallInventory thrallInventory = new ThrallInventory();

    /** True when the thrall is currently returning home to deposit items. */
    private boolean returningHome;

    /**
     * Set by {@link #commandReturnHome()} to make the post-deposit branch in tickReturnHome
     * land in STAY (instead of FOLLOW) — a manual "go home" should leave the thrall waiting at home.
     */
    private boolean stayAfterReturn;

    /**
     * When > 0, the thrall is in a grace period after a player resummon: it waits in STAY
     * for the player to issue a new command. When the timer hits zero, {@link #resummonResumeMode}
     * is restored so the thrall continues whatever it was doing before being recalled.
     */
    private int resummonPauseTicks;
    @Nullable
    private ThrallMode resummonResumeMode;

    /** The last active work position before returning home (to resume after deposit). */
    @Nullable
    private BlockPos resumeWorkPos;
    @Nullable
    private ThrallMode resumeMode;

    /** World tick when the current work mode (WOODCUTTING/MINESHAFT) was activated. 0 = not working. */
    private long workStartTick;

    /** Reference to the mineshaft AI task for NBT persistence of mining progress. */
    @Nullable
    private ThrallAIMineshaft mineshaftAI;

    /** Reference to the collecting AI task for NBT persistence and packet-driven start/resume. */
    @Nullable
    private ThrallAICollecting collectingAI;

    /** Last mode for which updateHeldToolVisual synced the held item — avoids per-tick ItemStack churn. */
    @Nullable
    private ThrallMode lastHeldToolMode = null;

    /**
     * Cached owner player reference. Refreshed every OWNER_CACHE_TTL ticks.
     * Avoids calling world.getPlayerEntityByUUID every tick (UUID → HashMap lookup).
     * Server-side only — client never reads this.
     */
    @Nullable private EntityPlayer cachedOwner;
    private int ownerCacheTicker = 0;
    private static final int OWNER_CACHE_TTL = 40;

    /**
     * Passive pickup (every 5 ticks) complements ThrallAIGatherItems (walks to item).
     * The passive variant fires regardless of active AI mutex, so items dropped right at the thrall's
     * feet are collected immediately even while mining/woodcutting. Range from ModConfig.thrall.passivePickupRange.
     */

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public EntityThrallMinion(World world) {
        super(world);
        this.setSize(0.6F, 1.8F);
        this.experienceValue = 0;
        this.isImmuneToFire = true;
    }

    // -------------------------------------------------------------------------
    // AI
    // -------------------------------------------------------------------------

    @Override
    protected void initEntityAI() {
        // Movement tasks — no combat AI; thrall is a passive immortal worker
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new ThrallAIWoodcutting(this));
        this.mineshaftAI = new ThrallAIMineshaft(this);
        this.tasks.addTask(1, mineshaftAI);
        this.tasks.addTask(1, new ThrallAIFarming(this));
        this.tasks.addTask(1, new ThrallAIPorter(this));
        this.collectingAI = new ThrallAICollecting(this);
        this.tasks.addTask(1, collectingAI);
        this.tasks.addTask(3, new ThrallAIGatherItems(this));
        this.tasks.addTask(4, new ThrallAIFollowCaster(this));
        this.tasks.addTask(6, new ThrallAIWander(this));
        this.tasks.addTask(7, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(2.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.67D); // +20% speed
        this.getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(32.0D);
        // Utility-only — never aggressive. Attribute registered for vanilla compatibility but kept at 0.
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(0.0D);
    }

    // -------------------------------------------------------------------------
    // Immortality + no despawn
    // -------------------------------------------------------------------------

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        // Thrall is immortal — no damage taken.
        return false;
    }

    @Override
    public boolean isEntityInvulnerable(DamageSource source) {
        // Hard immunity to every damage type (fire, fall, void, magic, /kill, etc.)
        return true;
    }

    @Override
    public void setAttackTarget(@Nullable EntityLivingBase target) {
        // Utility-only thrall — never acquires combat targets.
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    protected void dropEquipment(boolean wasRecentlyHit, int lootingModifier) {
        // Do NOT drop the virtual held tool (cosmetic-only pickaxe/axe)
    }

    // -------------------------------------------------------------------------
    // Team logic — don't attack caster or other thralls of same owner
    // -------------------------------------------------------------------------

    @Override
    public boolean isOnSameTeam(net.minecraft.entity.Entity other) {
        if (other instanceof EntityThrallMinion) {
            EntityThrallMinion otherThrall = (EntityThrallMinion) other;
            UUID ourOwner = getOwnerUUID();
            UUID theirOwner = otherThrall.getOwnerUUID();
            if (ourOwner != null && ourOwner.equals(theirOwner)) return true;
        }
        if (other instanceof EntityPlayer) {
            UUID ownerUUID = getOwnerUUID();
            if (ownerUUID != null && ownerUUID.equals(other.getUniqueID())) return true;
        }
        return super.isOnSameTeam(other);
    }

    // -------------------------------------------------------------------------
    // onUpdate — main server-side tick
    // -------------------------------------------------------------------------

    /**
     * Client-side cosmetic: emit Enderman-style PORTAL particles continuously around the body.
     * Spawned only on the client (visible regardless of server load).
     */
    @Override
    public void onLivingUpdate() {
        if (this.world.isRemote) {
            for (int i = 0; i < 2; ++i) {
                this.world.spawnParticle(net.minecraft.util.EnumParticleTypes.PORTAL,
                        this.posX + (this.rand.nextDouble() - 0.5D) * (double) this.width,
                        this.posY + this.rand.nextDouble() * (double) this.height - 0.25D,
                        this.posZ + (this.rand.nextDouble() - 0.5D) * (double) this.width,
                        (this.rand.nextDouble() - 0.5D) * 2.0D,
                        -this.rand.nextDouble(),
                        (this.rand.nextDouble() - 0.5D) * 2.0D);
            }
        }
        super.onLivingUpdate();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!world.isRemote) {
            // SRP anti-assimilation safety
            SummonInfectionSafetyHelper.onSummonServerTick(this);

            // Auto-return home when inventory is full.
            // PORTER and COLLECTING are excluded — both manage their own deposits via smartDeposit
            // (PORTER at cycle start/end, COLLECTING in its RETURNING phase). A mid-cycle teleport
            // here would orphan the porter's manifest run or the collecting AI's harvest state.
            if (thrallInventory.isFull() && !returningHome && getHomePoint() != null
                    && getMode() != ThrallMode.PORTER && getMode() != ThrallMode.COLLECTING) {
                if (debugLogs()) LOG.info("[Thrall#{}] Inventory FULL — starting return home to {}", getEntityId(), getHomePoint());
                startReturnHome();
            }

            // Handle return home logic
            if (returningHome) {
                tickReturnHome();
            }

            // Owner disconnect — pause to STAY mode
            if (getMode() != ThrallMode.STAY) {
                EntityPlayer owner = getOwner();
                if (owner == null || !owner.isEntityAlive()) {
                    if (debugLogs()) LOG.info("[Thrall#{}] Owner not found/alive — switching to STAY", getEntityId());
                    setMode(ThrallMode.STAY);
                    setStatusText("Waiting...");
                    this.getNavigator().clearPath();
                }
            }

            // Resummon pause: countdown, restore previous work mode when it expires
            if (resummonPauseTicks > 0) {
                resummonPauseTicks--;
                if (resummonPauseTicks <= 0 && resummonResumeMode != null) {
                    ThrallMode resume = resummonResumeMode;
                    if (debugLogs()) LOG.info("[Thrall#{}] Resummon grace period ended — resuming {}", getEntityId(), resume);
                    setMode(resume); // setMode also clears any leftover pause state
                    setStatusText("Resuming...");
                }
            }

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

            // Visual held item based on work mode
            updateHeldToolVisual();

            // FOLLOW mode teleport: snap to owner when too far away
            if (getMode() == ThrallMode.FOLLOW && ticksExisted % 20 == 0) {
                tickFollowTeleport();
            }

            // Backup slot state to owner's persistent NBT every 60 ticks
            if (ticksExisted % 60 == 0 && getThrallSlot() > 0) {
                EntityPlayer owner = getOwner();
                if (owner != null) {
                    ThrallSlotManager.saveSlot(owner, this);
                }
            }

            // Passive item pickup — every 5 ticks, grab items within 2.5 blocks
            if (ticksExisted % 5 == 0 && !thrallInventory.isFull()) {
                passiveItemPickup();
            }
        }
    }

    private void tickFollowTeleport() {
        EntityPlayer owner = getOwner();
        if (owner == null || !owner.isEntityAlive()) return;

        // Immediate teleport if standing in lava (escape damage trap), even before distance check
        boolean inLava = this.isInLava();

        double teleportDist = ModConfig.thrall.followTeleportDistance;
        double teleportDistSq = teleportDist * teleportDist;
        if (inLava || this.getDistanceSq(owner) > teleportDistSq) {
            // Pick a spot slightly behind the owner based on their look direction
            double angle = Math.toRadians(owner.rotationYaw + 180.0);
            double tx = owner.posX + Math.sin(angle) * 2.0;
            double ty = owner.posY;
            double tz = owner.posZ + Math.cos(angle) * 2.0;

            // Ensure target Y is solid ground (scan up to 3 blocks down)
            for (int dy = 0; dy >= -3; dy--) {
                BlockPos candidate = new BlockPos(tx, ty + dy, tz);
                if (!world.isAirBlock(candidate) && world.isAirBlock(candidate.up())
                        && world.isAirBlock(candidate.up(2))) {
                    ty = candidate.getY() + 1;
                    break;
                }
            }

            // Teleport with particle burst + Enderman whoosh
            playTeleportSound();
            world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE,
                    this.posX, this.posY + 1.0, this.posZ, 0.0, 0.0, 0.0);
            this.setPositionAndUpdate(tx, ty, tz);
            world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE,
                    tx, ty + 1.0, tz, 0.0, 0.0, 0.0);
            playTeleportSound();
            this.getNavigator().clearPath();
            if (debugLogs()) LOG.info("[Thrall#{}] Teleported to owner at ({},{},{})",
                    getEntityId(),
                    String.format("%.1f", tx),
                    String.format("%.1f", ty),
                    String.format("%.1f", tz));
        }
    }

    /**
     * Sets a virtual tool in the main hand based on current work mode.
     * Equipment slots sync automatically to clients via EntityLiving.
     * The item is cosmetic-only (thrall never drops it).
     */
    private void updateHeldToolVisual() {
        ThrallMode mode = getMode();
        if (mode == lastHeldToolMode) return; // no change — skip ItemStack allocation
        lastHeldToolMode = mode;
        switch (mode) {
            case WOODCUTTING:
                this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                break;
            case MINESHAFT:
                this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
                break;
            case FARMING:
                this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
                break;
            case PORTER:
                this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND,
                        new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.ENDER_CHEST)));
                break;
            default:
                this.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, ItemStack.EMPTY);
                break;
        }
    }

    /**
     * Grace period after the player recalls the thrall via the summon spell. While active,
     * the thrall idles in STAY waiting for new orders; if the timer expires without a new
     * mode being set, it falls back to the work mode it was in before being recalled.
     *
     * Bypasses {@link #setMode} for the STAY transition so {@link #setMode}'s pause-clear
     * logic doesn't immediately wipe the resume state we just stored.
     *
     * @param durationTicks how long to wait (typically 200 = 10 seconds)
     */
    public void pauseAfterResummon(int durationTicks) {
        ThrallMode current = getMode();
        if (current != ThrallMode.WOODCUTTING && current != ThrallMode.MINESHAFT
                && current != ThrallMode.FARMING && current != ThrallMode.PORTER) {
            return; // Only useful when interrupting an active work mode
        }
        resummonResumeMode = current;
        resummonPauseTicks = durationTicks;
        this.dataManager.set(MODE_ORDINAL, ThrallMode.STAY.ordinal());
        workStartTick = 0;
        lastHeldToolMode = null; // force tool-visual refresh on next tick
        setStatusText("Awaiting orders...");
        this.getNavigator().clearPath();
    }

    /**
     * Player-issued "Return Home" command. Aborts any work-resume state and triggers the same
     * teleport+deposit flow as the auto-return; the post-deposit branch lands in STAY rather
     * than FOLLOW so the thrall waits at home instead of trailing the player.
     */
    public void commandReturnHome() {
        if (returningHome) return;
        if (getHomePoint() == null) return;
        resumeMode = null;
        resumeWorkPos = null;
        stayAfterReturn = true;
        startReturnHome();
    }

    private void startReturnHome() {
        // Save current mode and position to resume later
        resumeMode = getMode();
        resumeWorkPos = new BlockPos(this);
        
        BlockPos target = getHomePoint();
        if (target == null) {
            // No home set — fall back to owner's current position
            EntityPlayer owner = getOwner();
            if (owner != null) target = new BlockPos(owner);
        }

        if (target == null) {
            LOG.warn("[Thrall#{}] Cannot return, no home and no player found", getEntityId());
            return;
        }

        if (debugLogs()) LOG.info("[Thrall#{}] startReturnHome() — teleporting to deposit. Target={}", getEntityId(), target);

        // Departure: smoke + Enderman whoosh
        playTeleportSound();
        world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE, this.posX, this.posY + 1.0, this.posZ, 0.0, 0.0, 0.0);

        // Teleport to target
        this.setPositionAndUpdate(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        this.getNavigator().clearPath();

        // Arrival: smoke + Enderman whoosh
        world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE, this.posX, this.posY + 1.0, this.posZ, 0.0, 0.0, 0.0);
        playTeleportSound();

        returningHome = true;
        setMode(ThrallMode.STAY); // pause current work
        setStatusText("Depositing Loot...");
    }

    private void tickReturnHome() {
        if (debugLogs()) LOG.info("[Thrall#{}] Depositing items after teleport", getEntityId());
        boolean deposited = smartDeposit();
        if (!deposited) {
            LOG.warn("[Thrall#{}] No chest found — dropping items", getEntityId());
            thrallInventory.dropAllItems(world, posX, posY + 0.5, posZ);
        }
        returningHome = false;
        
        if (resumeWorkPos != null && resumeMode != null) {
            // Teleport back to work
            playTeleportSound();
            world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE, this.posX, this.posY + 1.0, this.posZ, 0.0, 0.0, 0.0);
            this.setPositionAndUpdate(resumeWorkPos.getX() + 0.5, resumeWorkPos.getY(), resumeWorkPos.getZ() + 0.5);
            world.spawnParticle(net.minecraft.util.EnumParticleTypes.SMOKE_LARGE, this.posX, this.posY + 1.0, this.posZ, 0.0, 0.0, 0.0);
            playTeleportSound();

            setMode(resumeMode);
            setStatusText("Working...");

            resumeWorkPos = null;
            resumeMode = null;
        } else if (stayAfterReturn) {
            setMode(ThrallMode.STAY);
            setStatusText("At home");
            stayAfterReturn = false;
        } else {
            setMode(ThrallMode.FOLLOW);
            setStatusText("Idle");
        }
    }

    /**
     * Scans 30 blocks around homePoint (or current pos) for IInventory tile entities.
     * Prefers chests that already have matching items.
     * @return true if inventory is empty (excluding torches).
     */
    public boolean smartDeposit() {
        BlockPos home = getHomePoint();
        return ThrallChestHelper.smartDeposit(this, home != null ? home : new BlockPos(this));
    }

    /** Overload for callers that already have a center position (e.g., commands). */
    public boolean smartDeposit(BlockPos center) {
        return ThrallChestHelper.smartDeposit(this, center);
    }

    // -------------------------------------------------------------------------
    // DataManager setup
    // -------------------------------------------------------------------------

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(OWNER_UUID, "");
        this.dataManager.register(STATUS_TEXT, "Idle");
        this.dataManager.register(MODE_ORDINAL, ThrallMode.FOLLOW.ordinal());
        this.dataManager.register(HOME_POS, "");
        this.dataManager.register(THRALL_SLOT, 0);
    }

    // -------------------------------------------------------------------------
    // Owner
    // -------------------------------------------------------------------------

    @Nullable
    public UUID getOwnerUUID() {
        String raw = this.dataManager.get(OWNER_UUID);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setOwnerUUID(UUID uuid) {
        this.dataManager.set(OWNER_UUID, uuid == null ? "" : uuid.toString());
    }

    /**
     * Returns the cached owner player, refreshing every OWNER_CACHE_TTL ticks.
     * Invalidates early if the cached reference goes dead (offline/dimension change).
     * Server-side only — do not call on the client.
     */
    @Nullable
    private EntityPlayer getOwner() {
        if (ownerCacheTicker <= 0 || cachedOwner == null || !cachedOwner.isEntityAlive()) {
            UUID uuid = getOwnerUUID();
            cachedOwner = uuid != null ? world.getPlayerEntityByUUID(uuid) : null;
            ownerCacheTicker = OWNER_CACHE_TTL;
        }
        ownerCacheTicker--;
        return cachedOwner;
    }

    @Nullable
    public EntityLivingBase getCaster() {
        // Route through cache on server; fall back to direct lookup on client (no cache available)
        if (!world.isRemote) return getOwner();
        UUID ownerUUID = getOwnerUUID();
        if (ownerUUID == null) return null;
        return world.getPlayerEntityByUUID(ownerUUID);
    }

    public boolean canPlayerCommand(EntityPlayer player) {
        UUID ownerUUID = getOwnerUUID();
        return ownerUUID != null && ownerUUID.equals(player.getUniqueID());
    }

    // -------------------------------------------------------------------------
    // Mode
    // -------------------------------------------------------------------------

    public ThrallMode getMode() {
        int ordinal = this.dataManager.get(MODE_ORDINAL);
        ThrallMode[] values = ThrallMode.values();
        return (ordinal >= 0 && ordinal < values.length) ? values[ordinal] : ThrallMode.FOLLOW;
    }

    public void setMode(ThrallMode mode) {
        ThrallMode prev = getMode();
        this.dataManager.set(MODE_ORDINAL, mode.ordinal());
        if (debugLogs()) LOG.info("[Thrall#{}] setMode {} -> {}", getEntityId(), prev, mode);
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
        // Any explicit mode change cancels a pending resummon-pause resume.
        // pauseAfterResummon() bypasses this method specifically so it can keep the resume state alive.
        if (resummonPauseTicks > 0 || resummonResumeMode != null) {
            resummonPauseTicks = 0;
            resummonResumeMode = null;
        }
    }

    // -------------------------------------------------------------------------
    // Status text (nameplate)
    // -------------------------------------------------------------------------

    public String getStatusText() {
        return this.dataManager.get(STATUS_TEXT);
    }

    /**
     * Sets the status text. Auto-prefixes with "Thrall #N — " if a slot is assigned.
     * The full nameplate string is what gets stored and displayed.
     */
    public void setStatusText(String text) {
        if (text == null) text = "Idle";
        int slot = getThrallSlot();
        String nameplate = (slot >= 1 && slot <= 3)
                ? "Thrall #" + slot + " \u2014 " + text
                : text;
        this.dataManager.set(STATUS_TEXT, nameplate);
    }

    // -------------------------------------------------------------------------
    // Thrall Slot
    // -------------------------------------------------------------------------

    public int getThrallSlot() {
        return this.dataManager.get(THRALL_SLOT);
    }

    public void setThrallSlot(int slot) {
        this.dataManager.set(THRALL_SLOT, slot);
        // Re-apply current status so nameplate prefix updates
        String current = getStatusText();
        // Strip any existing "Thrall #X — " prefix before re-applying
        if (current.contains(" \u2014 ")) {
            current = current.substring(current.indexOf(" \u2014 ") + 3);
        }
        setStatusText(current);
    }

    // -------------------------------------------------------------------------
    // Home point — stored in DataManager so client GUI stays in sync
    // -------------------------------------------------------------------------

    @Nullable
    public BlockPos getHomePoint() {
        String raw = this.dataManager.get(HOME_POS);
        if (raw == null || raw.isEmpty()) return null;
        try {
            String[] parts = raw.split(",");
            return new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (Exception e) {
            return null;
        }
    }

    public void setHomePoint(BlockPos pos) {
        if (pos == null) {
            this.dataManager.set(HOME_POS, "");
        } else {
            this.dataManager.set(HOME_POS, pos.getX() + "," + pos.getY() + "," + pos.getZ());
            if (debugLogs()) LOG.info("[Thrall#{}] Home point set to {}", getEntityId(), pos);
        }
    }

    // -------------------------------------------------------------------------
    // Inventory accessor
    // -------------------------------------------------------------------------

    public ThrallInventory getThrallInventory() {
        return thrallInventory;
    }

    // -------------------------------------------------------------------------
    // Passive item pickup
    // -------------------------------------------------------------------------

    /** Collects all items within ModConfig.thrall.passivePickupRange. Bypasses AI mutex conflicts. */
    private void passiveItemPickup() {
        double range = ModConfig.thrall.passivePickupRange;
        List<net.minecraft.entity.item.EntityItem> items = world.getEntitiesWithinAABB(
                net.minecraft.entity.item.EntityItem.class,
                getEntityBoundingBox().grow(range, 2, range));

        boolean collectingWait = collectingAI != null && collectingAI.isWaitingForItems();

        for (net.minecraft.entity.item.EntityItem item : items) {
            if (item.isDead || item.getItem().isEmpty()) continue;
            if (item.cannotPickup()) continue; // respects pickup delay
            ItemStack stack = item.getItem();

            if (collectingWait) {
                // During COLLECTING-WAITING, only block-form items become targets. Non-block items
                // (food/sword/etc.) are left on the ground so the player can stage targets without
                // their thrall accidentally swallowing pocket lint.
                if (collectingAI.tryAcceptItem(stack)) {
                    stack.shrink(1);
                    if (stack.isEmpty()) item.setDead();
                }
                continue; // never fall through to inventory pickup while waiting
            }

            if (thrallInventory.addItemStackToInventory(stack)) {
                if (stack.isEmpty() || stack.getCount() <= 0) {
                    item.setDead();
                }
            }
            if (thrallInventory.isFull()) break;
        }
    }

    // -------------------------------------------------------------------------
    // Collecting AI entry point (called from PacketThrallCommand)
    // -------------------------------------------------------------------------

    /**
     * Sets COLLECTING mode and either starts a fresh waiting session or resumes a paused one,
     * depending on whether the resume window is still open and a target list exists.
     */
    public void startOrResumeCollectingMode() {
        setMode(ThrallMode.COLLECTING);
        if (collectingAI != null) collectingAI.startOrResume();
    }

    /** Collecting AI task, or null before initEntityAI has run. Used by ThrallAIGatherItems to
     *  avoid swallowing items the player is staging while COLLECTING waits for target selection. */
    @Nullable
    public ThrallAICollecting getCollectingAI() {
        return collectingAI;
    }

    /**
     * Re-arms the work timer if it is not currently running ({@code workStartTick == 0}).
     * Called by ThrallAICollecting when a new session locks in from WAITING_FOR_ITEMS: after a
     * work-timer expiry the mode stays COLLECTING (setMode is never called on that path), so
     * without this the re-staged session would run with no timer bound.
     */
    public void rearmWorkTimer() {
        if (workStartTick == 0) {
            workStartTick = world.getTotalWorldTime();
            if (debugLogs()) LOG.info("[Thrall#{}] Work timer re-armed at tick {}", getEntityId(), workStartTick);
        }
    }



    // -------------------------------------------------------------------------
    // Mineshaft AI reset
    // -------------------------------------------------------------------------

    /** Resets mineshaft AI state so a new shaft starts at the thrall's current position. */
    public void resetMineshaftAI() {
        if (mineshaftAI != null) {
            mineshaftAI.resetShaftState();
            if (debugLogs()) LOG.info("[Thrall#{}] Mineshaft AI state reset", getEntityId());
        }
    }

    // -------------------------------------------------------------------------
    // Torch resupply (for Mineshaft mode) — delegated to ThrallTorchSupply
    // -------------------------------------------------------------------------

    /** @see ThrallTorchSupply#tryResupply(EntityThrallMinion, int) */
    public boolean tryResupplyTorches(int targetCount) {
        return ThrallTorchSupply.tryResupply(this, targetCount);
    }

    /** Counts torches in thrall inventory. */
    public int countTorches() {
        return ThrallTorchSupply.countTorches(thrallInventory);
    }

    // -------------------------------------------------------------------------
    // Cobblestone supply (for liquid protection)
    // -------------------------------------------------------------------------

    /** Finds a slot containing cobblestone in thrall inventory. -1 if none. */
    public int findCobblestoneSlot() {
        for (int i = 0; i < thrallInventory.getSizeInventory(); i++) {
            ItemStack s = thrallInventory.getStackInSlot(i);
            if (!s.isEmpty() && ThrallMaterialHelper.hasOreName(s, "cobblestone")) return i;
        }
        return -1;
    }

    /**
     * Consumes 1 cobblestone from inventory.
     * @return true if consumed successfully.
     */
    public boolean consumeCobblestone() {
        int slot = findCobblestoneSlot();
        if (slot < 0) return false;
        thrallInventory.getStackInSlot(slot).shrink(1);
        if (thrallInventory.getStackInSlot(slot).isEmpty()) {
            thrallInventory.setInventorySlotContents(slot, ItemStack.EMPTY);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Sounds
    // -------------------------------------------------------------------------

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null; // Thrall is silent by default; sounds triggered by commands
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null; // Immortal — never hurt
    }

    @Override
    protected SoundEvent getDeathSound() {
        return null; // Immortal — never dies
    }

    public void playSoundSpawn() {
        playSound(SOUND_SPAWN, 1.0F, 1.0F);
    }

    public void playSoundOrder() {
        playSound(SOUND_ORDER, 1.0F, 0.9F + getRNG().nextFloat() * 0.2F);
    }

    public void playSoundFollow() {
        playSound(SOUND_FOLLOW, 1.0F, 1.0F);
    }

    public void playSoundDropItems() {
        playSound(SOUND_FORYOU, 1.0F, 1.0F);
    }

    /** Plays the vanilla Enderman teleport sound at the Thrall's current position. Server-side only. */
    public void playTeleportSound() {
        if (world.isRemote) return;
        world.playSound(null, posX, posY, posZ,
                net.minecraft.init.SoundEvents.ENTITY_ENDERMEN_TELEPORT,
                net.minecraft.util.SoundCategory.NEUTRAL, 1.0F, 0.9F + getRNG().nextFloat() * 0.2F);
    }

    // -------------------------------------------------------------------------
    // NBT
    // -------------------------------------------------------------------------

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);

        UUID ownerUUID = getOwnerUUID();
        if (ownerUUID != null) {
            tag.setString("ThrallOwnerUUID", ownerUUID.toString());
        }

        tag.setInteger("ThrallMode", getMode().ordinal());
        tag.setString("ThrallStatus", getStatusText());

        BlockPos home = getHomePoint();
        if (home != null) {
            NBTTagCompound homeTag = new NBTTagCompound();
            homeTag.setInteger("X", home.getX());
            homeTag.setInteger("Y", home.getY());
            homeTag.setInteger("Z", home.getZ());
            tag.setTag("ThrallHome", homeTag);
        }

        tag.setTag("ThrallInventory", thrallInventory.writeToNBT());
        tag.setBoolean("ThrallReturning", returningHome);
        if (resumeWorkPos != null) {
            tag.setIntArray("ThrallResumePos", new int[]{resumeWorkPos.getX(), resumeWorkPos.getY(), resumeWorkPos.getZ()});
        }
        if (resumeMode != null) {
            tag.setInteger("ThrallResumeMode", resumeMode.ordinal());
        }
        tag.setLong("ThrallWorkStart", workStartTick);
        tag.setInteger("ThrallSlot", getThrallSlot());

        if (resummonPauseTicks > 0 && resummonResumeMode != null) {
            tag.setInteger("ThrallResummonPause", resummonPauseTicks);
            tag.setInteger("ThrallResummonResume", resummonResumeMode.ordinal());
        }

        // Persist mineshaft AI progress
        if (mineshaftAI != null) {
            NBTTagCompound mineTag = new NBTTagCompound();
            mineshaftAI.writeToNBT(mineTag);
            tag.setTag("ThrallMineshaft", mineTag);
        }

        // Persist collecting AI state (target list, phase, session timers)
        if (collectingAI != null) {
            NBTTagCompound collectTag = new NBTTagCompound();
            collectingAI.writeToNBT(collectTag);
            tag.setTag("ThrallCollecting", collectTag);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);

        if (tag.hasKey("ThrallOwnerUUID")) {
            try {
                setOwnerUUID(UUID.fromString(tag.getString("ThrallOwnerUUID")));
            } catch (IllegalArgumentException ignored) {}
        }

        if (tag.hasKey("ThrallMode")) {
            int ordinal = tag.getInteger("ThrallMode");
            ThrallMode[] values = ThrallMode.values();
            setMode(ordinal >= 0 && ordinal < values.length ? values[ordinal] : ThrallMode.FOLLOW);
        }

        if (tag.hasKey("ThrallStatus")) {
            setStatusText(tag.getString("ThrallStatus"));
        }

        if (tag.hasKey("ThrallHome")) {
            NBTTagCompound homeTag = tag.getCompoundTag("ThrallHome");
            setHomePoint(new BlockPos(
                    homeTag.getInteger("X"),
                    homeTag.getInteger("Y"),
                    homeTag.getInteger("Z")));
        }

        if (tag.hasKey("ThrallInventory")) {
            thrallInventory.readFromNBT(tag.getTagList("ThrallInventory", Constants.NBT.TAG_COMPOUND));
        }

        returningHome = tag.getBoolean("ThrallReturning");
        if (tag.hasKey("ThrallResumePos")) {
            int[] pos = tag.getIntArray("ThrallResumePos");
            if (pos.length == 3) {
                resumeWorkPos = new BlockPos(pos[0], pos[1], pos[2]);
            }
        }
        if (tag.hasKey("ThrallResumeMode")) {
            int ordinal = tag.getInteger("ThrallResumeMode");
            ThrallMode[] values = ThrallMode.values();
            if (ordinal >= 0 && ordinal < values.length) {
                resumeMode = values[ordinal];
            }
        }
        workStartTick = tag.getLong("ThrallWorkStart");
        if (tag.hasKey("ThrallSlot")) {
            this.dataManager.set(THRALL_SLOT, tag.getInteger("ThrallSlot"));
        }

        if (tag.hasKey("ThrallResummonPause") && tag.hasKey("ThrallResummonResume")) {
            resummonPauseTicks = tag.getInteger("ThrallResummonPause");
            int ordinal = tag.getInteger("ThrallResummonResume");
            ThrallMode[] values = ThrallMode.values();
            if (ordinal >= 0 && ordinal < values.length) {
                resummonResumeMode = values[ordinal];
            }
        }

        // Restore mineshaft AI progress
        if (collectingAI != null && tag.hasKey("ThrallCollecting")) {
            collectingAI.readFromNBT(tag.getCompoundTag("ThrallCollecting"));
        }

        if (mineshaftAI != null && tag.hasKey("ThrallMineshaft")) {
            mineshaftAI.readFromNBT(tag.getCompoundTag("ThrallMineshaft"));
        }
    }

    // -------------------------------------------------------------------------
    // Custom name (nameplate)
    // -------------------------------------------------------------------------

    @Override
    public String getName() {
        String status = getStatusText();
        if (status != null && !status.isEmpty() && !status.equals("Idle")) {
            return "Thrall \u00a77" + status;
        }
        return "Thrall";
    }

    @Override
    public boolean hasCustomName() {
        return false; // Use our getName() override
    }
}
