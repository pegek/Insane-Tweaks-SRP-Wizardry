package com.spege.insanetweaks.entities;

import java.util.ArrayList;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.BaubleFruitsCategory;
import com.spege.insanetweaks.entities.sapling.SaplingDefensePlan;
import com.spege.insanetweaks.entities.sapling.SaplingSiteCheck;
import com.spege.insanetweaks.entities.sapling.SaplingStats;
import com.spege.insanetweaks.entities.sapling.SaplingWaveSpawner;
import com.spege.insanetweaks.entities.sapling.SiteProblem;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.EnigmaticLegacyCompat;
import com.spege.insanetweaks.util.SrpPhaseHelper;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Corrupted Sapling — a LIVING plant the player must defend for one short event (spec
 * 2026-10-06-corrupted-sapling-defense-design.md). An entity rather than a crop block, so parasites
 * can attack it.
 *
 * <p>Timeline ({@link SaplingDefensePlan}): a lure period, then waves; it bears a Corrupted Fruit
 * when the timer completes. The timer only runs while the owner is near, wears a qualifying ring and
 * the site is still valid ({@link SaplingSiteCheck}); otherwise the event pauses — no growth, no new
 * waves, but luring, regeneration and incoming damage go on.
 *
 * <p>Size, max HP, armor and toughness follow the growth progress ({@link SaplingStats}). Immune to
 * every potion effect and to SRP conversion; ignores player damage (configurable).
 */
@SuppressWarnings("null")
public class EntityCorruptedSapling extends EntityLiving {

    private static final DataParameter<Integer> PROGRESS =
            EntityDataManager.createKey(EntityCorruptedSapling.class, DataSerializers.VARINT);

    /** Forge custom-data key SRP reads; 0 means immune to COTH conversion (SRP 1.10.7+). */
    private static final String SRP_COTH_IMMUNITY = "srpcothimmunity";
    private static final double BOSS_BAR_RANGE = 32.0D;
    /** Lure re-locks nearby parasites for this long; refreshed every 40 ticks. */
    private static final int LURE_LOCK_TICKS = 100;
    /** Site rules are re-checked every this many 20-tick steps (= 100 ticks). */
    private static final int SITE_CHECK_STEPS = 5;

    private static final String NBT_OWNER = "SaplingOwner";
    private static final String NBT_DEFENSE_TICKS = "SaplingDefenseTicks";
    private static final String NBT_WAVES_FIRED = "SaplingWavesFired";
    private static final String NBT_REGEN_TICKS = "SaplingRegenTicks";
    /** Pre-1.21.0 growth counter, read once for migration. */
    private static final String NBT_LEGACY_GROWTH = "SaplingGrowth";

    private final BossInfoServer bossInfo = new BossInfoServer(this.getDisplayName(),
            BossInfo.Color.PURPLE, BossInfo.Overlay.PROGRESS);

    private UUID ownerId;
    private int defenseTicks;
    private int wavesFired;
    private int regenTicks;
    private int siteCheckCooldown;
    @Nullable
    private SiteProblem cachedSiteProblem;
    @Nullable
    private SiteProblem lastPause;

    public EntityCorruptedSapling(World world) {
        super(world);
        this.setSize(SaplingStats.WIDTH_START, SaplingStats.HEIGHT_START);
        this.setNoAI(true);
    }

    public void setOwnerId(UUID id) {
        this.ownerId = id;
    }

    @Nullable
    public UUID getOwnerId() {
        return this.ownerId;
    }

    /** 0..1 growth, synced — drives the hitbox here and the model scale in the renderer. */
    public double getProgress() {
        return SaplingStats.fromSynced(this.dataManager.get(PROGRESS).intValue());
    }

    private static SaplingDefensePlan plan(BaubleFruitsCategory cfg) {
        return new SaplingDefensePlan(cfg.saplingLureSeconds, cfg.saplingWaveCount, cfg.saplingWaveIntervalSeconds);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(PROGRESS, Integer.valueOf(0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(ModConfig.baubleFruits.saplingHpStart);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        this.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
    }

    @Override
    public void notifyDataManagerChange(DataParameter<?> key) {
        super.notifyDataManagerChange(key);
        if (PROGRESS.equals(key)) {
            applySize();
        }
    }

    /**
     * Runs on both sides whenever PROGRESS changes. {@code setSize} rebuilds the box from its min
     * corner, so the position is re-applied to keep the box centred on the sapling.
     */
    private void applySize() {
        double progress = getProgress();
        float w = SaplingStats.width(progress);
        float h = SaplingStats.height(progress);
        if (w != this.width || h != this.height) {
            this.setSize(w, h);
            this.setPosition(this.posX, this.posY, this.posZ);
        }
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean isPotionApplicable(@Nonnull PotionEffect effect) {
        return false;
    }

    @Override
    public boolean attackEntityFrom(@Nonnull DamageSource source, float amount) {
        if (ModConfig.baubleFruits.saplingIgnorePlayerDamage
                && (source.getTrueSource() instanceof EntityPlayer || source.getImmediateSource() instanceof EntityPlayer)) {
            return false;
        }
        return super.attackEntityFrom(source, amount);
    }

    /** Sneak + right-click with an empty main hand: the owner uproots it. The seed is lost. */
    @Override
    protected boolean processInteract(@Nonnull EntityPlayer player, @Nonnull EnumHand hand) {
        if (hand != EnumHand.MAIN_HAND || !player.isSneaking() || !player.getHeldItem(hand).isEmpty()
                || this.ownerId == null || !this.ownerId.equals(player.getUniqueID())) {
            return super.processInteract(player, hand);
        }
        if (!this.world.isRemote) {
            player.sendMessage(new TextComponentTranslation("msg.insanetweaks.sapling.uprooted"));
            this.world.playSound(null, this.posX, this.posY, this.posZ, SoundEvents.BLOCK_GRASS_BREAK,
                    SoundCategory.NEUTRAL, 1.0F, 0.8F);
            this.setDead();
        }
        return true;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        this.motionX = 0.0D;
        this.motionZ = 0.0D;

        if (this.world.isRemote) {
            if (this.ticksExisted % 4 == 0) {
                spawnAuraParticles();
            }
            return;
        }
        if (this.ticksExisted % 20 != 0) {
            return;
        }

        BaubleFruitsCategory cfg = ModConfig.baubleFruits;
        SaplingDefensePlan plan = plan(cfg);

        keepImmune();
        regenerate(cfg);
        this.setGlowing(cfg.saplingGlow);
        if (this.ticksExisted % 40 == 0) {
            SaplingWaveSpawner.lure(this, cfg.saplingLureRadius, LURE_LOCK_TICKS, cfg.saplingMaxAttackers);
        }

        SiteProblem pause = pauseReason(cfg);
        announcePause(pause);
        if (pause == null) {
            this.defenseTicks += 20;
            fireDueWaves(plan, cfg);
            applyProgress(plan.progress(this.defenseTicks), cfg);
            if (plan.isComplete(this.defenseTicks)) {
                bearFruit();
                return;
            }
        }
        updateBossBar(plan, pause);
    }

    /**
     * Client side: a purple haze around the sapling that thickens as it grows. Plain client-world
     * particles, nothing sent over the network.
     */
    private void spawnAuraParticles() {
        double progress = getProgress();
        int count = 1 + (int) Math.round(progress * 3.0D);
        double radius = this.width * 0.5D + 0.4D + progress;
        for (int i = 0; i < count; i++) {
            double angle = this.rand.nextDouble() * Math.PI * 2.0D;
            double r = radius * (0.4D + this.rand.nextDouble() * 0.6D);
            this.world.spawnParticle(EnumParticleTypes.SPELL_WITCH,
                    this.posX + Math.cos(angle) * r, this.posY + this.rand.nextDouble() * this.height,
                    this.posZ + Math.sin(angle) * r, 0.0D, 0.02D, 0.0D);
        }
        if (progress > 0.5D && this.rand.nextInt(3) == 0) {
            this.world.spawnParticle(EnumParticleTypes.PORTAL,
                    this.posX, this.posY + this.height * 0.8D, this.posZ,
                    (this.rand.nextDouble() - 0.5D) * 1.5D, -0.3D, (this.rand.nextDouble() - 0.5D) * 1.5D);
        }
    }

    private void keepImmune() {
        if (!this.getActivePotionEffects().isEmpty()) {
            this.clearActivePotions();
        }
        NBTTagCompound data = this.getEntityData();
        if (!data.hasKey(SRP_COTH_IMMUNITY) || data.getInteger(SRP_COTH_IMMUNITY) != 0) {
            data.setInteger(SRP_COTH_IMMUNITY, 0);
        }
    }

    private void regenerate(BaubleFruitsCategory cfg) {
        this.regenTicks += 20;
        if (this.regenTicks >= cfg.saplingRegenIntervalSeconds * 20) {
            this.regenTicks = 0;
            if (this.getHealth() < this.getMaxHealth()) {
                this.heal(1.0F);
            }
        }
    }

    /** Owner rules first (cheap), then the cached site rules, refreshed every 100 ticks. */
    @Nullable
    private SiteProblem pauseReason(BaubleFruitsCategory cfg) {
        EntityPlayer owner = ownerHere();
        double range = cfg.saplingConditionRadius;
        if (owner == null || !owner.isEntityAlive() || owner.getDistanceSq(this) > range * range) {
            return SiteProblem.OWNER_AWAY;
        }
        if (!EnigmaticLegacyCompat.isWearingQualifyingRing(owner)) {
            return SiteProblem.NO_RING;
        }
        if (--this.siteCheckCooldown <= 0) {
            this.siteCheckCooldown = SITE_CHECK_STEPS;
            this.cachedSiteProblem = SaplingSiteCheck.checkSite(this.world, new BlockPos(this));
        }
        return this.cachedSiteProblem;
    }

    /** Action bar to the owner whenever the pause reason changes (including back to running). */
    private void announcePause(@Nullable SiteProblem pause) {
        if (pause == this.lastPause) {
            return;
        }
        this.lastPause = pause;
        EntityPlayer owner = ownerHere();
        if (owner == null) {
            return;
        }
        if (pause != null) {
            owner.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.sapling.paused",
                    new TextComponentTranslation(pause.langKey())), true);
        } else {
            owner.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.sapling.resumed"), true);
        }
    }

    private void fireDueWaves(SaplingDefensePlan plan, BaubleFruitsCategory cfg) {
        int due = plan.wavesDueBy(this.defenseTicks);
        while (this.wavesFired < due) {
            this.wavesFired++;
            if (this.wavesFired >= 2) {
                this.heal(cfg.saplingWaveHeal); // the previous wave was survived
            }
            int phase = SrpPhaseHelper.getEvolutionPhase(this.world);
            int nearby = SaplingWaveSpawner.countParasites(this.world,
                    this.getEntityBoundingBox().grow(cfg.saplingLureRadius));
            int count = SaplingStats.waveSpawnCount(cfg.saplingWaveBaseCount, phase, nearby, cfg.saplingWaveMaxSpawn);
            SaplingWaveSpawner.spawnWave(this, phase, count, this.wavesFired == plan.waveCount());

            EntityPlayer owner = ownerHere();
            if (owner != null) {
                owner.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.sapling.wave",
                        Integer.valueOf(this.wavesFired), Integer.valueOf(plan.waveCount())), true);
            }
        }
    }

    /** Synced progress, then max HP (healing by any increase), armor and toughness. */
    private void applyProgress(double progress, BaubleFruitsCategory cfg) {
        this.dataManager.set(PROGRESS, Integer.valueOf(SaplingStats.toSynced(progress)));

        IAttributeInstance maxHp = this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        double previous = maxHp.getBaseValue();
        double target = SaplingStats.lerp(cfg.saplingHpStart, cfg.saplingHpEnd, progress);
        if (target != previous) {
            maxHp.setBaseValue(target);
            if (target > previous) {
                this.heal((float) (target - previous));
            }
            if (this.getHealth() > target) {
                this.setHealth((float) target);
            }
        }
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR)
                .setBaseValue(SaplingStats.lerp(0.0D, cfg.saplingArmorEnd, progress));
        this.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS)
                .setBaseValue(SaplingStats.lerp(0.0D, cfg.saplingToughnessEnd, progress));
    }

    private void updateBossBar(SaplingDefensePlan plan, @Nullable SiteProblem pause) {
        ITextComponent title;
        if (pause != null) {
            title = new TextComponentTranslation("boss.insanetweaks.sapling.paused",
                    new TextComponentTranslation(pause.langKey()));
            this.bossInfo.setColor(BossInfo.Color.RED);
        } else if (this.wavesFired == 0) {
            title = new TextComponentTranslation("boss.insanetweaks.sapling.lure");
            this.bossInfo.setColor(BossInfo.Color.PURPLE);
        } else {
            title = new TextComponentTranslation("boss.insanetweaks.sapling.wave",
                    Integer.valueOf(this.wavesFired), Integer.valueOf(plan.waveCount()));
            this.bossInfo.setColor(BossInfo.Color.PURPLE);
        }
        this.bossInfo.setName(title);
        this.bossInfo.setPercent((float) plan.progress(this.defenseTicks));
        refreshBossBarViewers();
    }

    private void refreshBossBarViewers() {
        double rangeSq = BOSS_BAR_RANGE * BOSS_BAR_RANGE;
        for (EntityPlayerMP viewer : new ArrayList<EntityPlayerMP>(this.bossInfo.getPlayers())) {
            if (viewer.isDead || viewer.world != this.world || viewer.getDistanceSq(this) > rangeSq) {
                this.bossInfo.removePlayer(viewer);
            }
        }
        for (EntityPlayer player : this.world.playerEntities) {
            if (player instanceof EntityPlayerMP && player.getDistanceSq(this) <= rangeSq
                    && !this.bossInfo.getPlayers().contains(player)) {
                this.bossInfo.addPlayer((EntityPlayerMP) player);
            }
        }
    }

    private void clearBossBar() {
        for (EntityPlayerMP viewer : new ArrayList<EntityPlayerMP>(this.bossInfo.getPlayers())) {
            this.bossInfo.removePlayer(viewer);
        }
    }

    /** The owner if online and in this world, else null. */
    @Nullable
    private EntityPlayer ownerHere() {
        return this.ownerId == null ? null : this.world.getPlayerEntityByUUID(this.ownerId);
    }

    /** The owner if online in any dimension — for the end-of-event chat messages. */
    @Nullable
    private EntityPlayer ownerAnywhere() {
        MinecraftServer server = this.world.getMinecraftServer();
        if (this.ownerId == null || server == null) {
            return null;
        }
        return server.getPlayerList().getPlayerByUUID(this.ownerId);
    }

    private void bearFruit() {
        this.entityDropItem(new ItemStack(ModItems.CORRUPTED_FRUIT), 0.3f);
        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.SPELL_MOB,
                    this.posX, this.posY + this.height * 0.6D, this.posZ, 30,
                    0.4D, 0.6D, 0.4D, 0.05D);
        }
        this.world.playSound(null, this.posX, this.posY, this.posZ,
                SoundEvents.BLOCK_CHORUS_FLOWER_GROW, SoundCategory.NEUTRAL, 1.0f, 0.7f);
        EntityPlayer owner = ownerAnywhere();
        if (owner != null) {
            owner.sendMessage(new TextComponentTranslation("msg.insanetweaks.sapling.bloom"));
        }
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Corrupted Sapling matured at {},{},{}",
                Integer.valueOf((int) this.posX), Integer.valueOf((int) this.posY), Integer.valueOf((int) this.posZ));
        this.setDead();
    }

    @Override
    public void onDeath(@Nonnull DamageSource cause) {
        super.onDeath(cause);
        if (!this.world.isRemote) {
            EntityPlayer owner = ownerAnywhere();
            if (owner != null) {
                owner.sendMessage(new TextComponentTranslation("msg.insanetweaks.sapling.died"));
            }
        }
    }

    @Override
    public void setDead() {
        super.setDead();
        clearBossBar();
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        clearBossBar();
    }

    @Override
    public void writeEntityToNBT(@Nonnull NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        if (this.ownerId != null) {
            compound.setUniqueId(NBT_OWNER, this.ownerId);
        }
        compound.setInteger(NBT_DEFENSE_TICKS, this.defenseTicks);
        compound.setInteger(NBT_WAVES_FIRED, this.wavesFired);
        compound.setInteger(NBT_REGEN_TICKS, this.regenTicks);
    }

    @Override
    public void readEntityFromNBT(@Nonnull NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasUniqueId(NBT_OWNER)) {
            this.ownerId = compound.getUniqueId(NBT_OWNER);
        }
        SaplingDefensePlan plan = plan(ModConfig.baubleFruits);
        if (compound.hasKey(NBT_DEFENSE_TICKS)) {
            this.defenseTicks = compound.getInteger(NBT_DEFENSE_TICKS);
            this.wavesFired = compound.getInteger(NBT_WAVES_FIRED);
            this.regenTicks = compound.getInteger(NBT_REGEN_TICKS);
        } else if (compound.hasKey(NBT_LEGACY_GROWTH)) {
            // Saved by <= 1.20.3: resume at the same share of the new timeline, with the waves
            // already behind that point counted as fired.
            this.defenseTicks = plan.migrateLegacyGrowth(compound.getInteger(NBT_LEGACY_GROWTH));
            this.wavesFired = plan.wavesDueBy(this.defenseTicks);
        }
        this.dataManager.set(PROGRESS, Integer.valueOf(SaplingStats.toSynced(plan.progress(this.defenseTicks))));
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(@Nonnull DamageSource source) {
        return SoundEvents.BLOCK_WOOD_HIT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLOCK_GRASS_BREAK;
    }
}
