package com.spege.insanetweaks.entities;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.util.EnigmaticLegacyCompat;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * Corrupted Sapling — a LIVING plant (deliberately an entity, not a crop block,
 * so SRP parasite AI can naturally target and attack it; the player must defend it).
 *
 * Growth advances only while BOTH conditions hold (checked every 20 t):
 *   1. Active infestation nearby: >= saplingMinParasites living EntityParasiteBase
 *      within saplingConditionRadius, OR any block from the "srparasites" domain
 *      within 8 blocks horizontally / 4 vertically (block scan cached, every 100 t).
 *   2. The planting owner is within saplingConditionRadius AND wears the Blessed Ring.
 *
 * At full growth it drops a Corrupted Fruit and despawns. If killed, everything is lost.
 */
@SuppressWarnings("null")
public class EntityCorruptedSapling extends EntityLiving {

    private static final DataParameter<Integer> STAGE =
            EntityDataManager.createKey(EntityCorruptedSapling.class, DataSerializers.VARINT);
    public static final int MAX_STAGE = 4;

    private int growthTicks;
    private UUID ownerId;
    private boolean cachedBlockInfestation;
    private int blockScanCooldown;
    /** Last max-HP band value applied from the SRP phase; -1 = not yet applied. */
    private int appliedHpBand = -1;

    /** SRP presence, cached on first use. Growth is impossible without SRP (dormant, never crashes). */
    private static Boolean srpLoaded;

    private static boolean isSrpLoaded() {
        if (srpLoaded == null) {
            srpLoaded = Boolean.valueOf(net.minecraftforge.fml.common.Loader.isModLoaded(
                    com.spege.insanetweaks.InsaneTweaksMod.SRP_MODID));
        }
        return srpLoaded.booleanValue();
    }

    /** Isolated in a nested class so EntityParasiteBase only classloads when SRP is present. */
    private static final class ParasiteScan {
        private ParasiteScan() {
        }

        static int countLivingParasites(net.minecraft.world.World world, net.minecraft.util.math.AxisAlignedBB box) {
            java.util.List<com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase> parasites =
                    world.getEntitiesWithinAABB(com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase.class, box);
            int alive = 0;
            for (com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase p : parasites) {
                if (p.isEntityAlive()) {
                    alive++;
                }
            }
            return alive;
        }
    }

    public EntityCorruptedSapling(World world) {
        super(world);
        this.setSize(0.6f, 1.4f);
        this.setNoAI(true);
    }

    public void setOwnerId(UUID id) {
        this.ownerId = id;
    }

    /** 0..MAX_STAGE — synced for the renderer's scale. */
    public int getStage() {
        return this.dataManager.get(STAGE);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(STAGE, 0);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        // Base/fallback value only - the real max HP is set from the current SRP phase on the first
        // server tick (applyPhaseHealth), because SRPSaveData needs a bound world which is not
        // available here. Using the phase 1-3 value keeps a sane default if the phase read fails.
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH)
                .setBaseValue(ModConfig.tweaks.saplingHpPhase1);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.0D);
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
    public void onLivingUpdate() {
        super.onLivingUpdate();
        this.motionX = 0.0D;
        this.motionZ = 0.0D;

        if (this.world.isRemote || this.ticksExisted % 20 != 0) {
            return;
        }

        applyPhaseHealth();

        if (conditionsMet()) {
            this.growthTicks += 20;
            int total = Math.max(20, ModConfig.tweaks.saplingGrowthTicks);
            int stage = Math.min(MAX_STAGE, (this.growthTicks * MAX_STAGE) / total);
            this.dataManager.set(STAGE, stage);

            if (this.world instanceof WorldServer) {
                ((WorldServer) this.world).spawnParticle(EnumParticleTypes.SPELL_WITCH,
                        this.posX, this.posY + this.height * 0.6D, this.posZ, 3,
                        0.2D, 0.3D, 0.2D, 0.01D);
            }

            if (this.growthTicks >= total) {
                bearFruit();
            }
        }
    }

    /**
     * Keeps the sapling's max HP in step with the CURRENT SRP evolution phase (server-side,
     * re-evaluated every 20t). Only touches the attribute when the band value actually changes.
     * When the band rises, an undamaged sapling is topped up to the new max; a damaged one keeps
     * its current health under the higher cap. Health is always clamped down to a lower cap.
     */
    private void applyPhaseHealth() {
        int target = hpForPhase(com.spege.insanetweaks.util.SrpPhaseHelper.getEvolutionPhase(this.world));
        if (target == this.appliedHpBand) {
            return;
        }
        net.minecraft.entity.ai.attributes.IAttributeInstance inst =
                this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (inst == null) {
            return;
        }
        boolean wasFull = this.getHealth() >= (float) inst.getBaseValue();
        this.appliedHpBand = target;
        inst.setBaseValue(target);
        if (this.getHealth() > target) {
            this.setHealth(target);            // new cap is lower - clamp down
        } else if (wasFull) {
            this.setHealth(target);            // undamaged - keep it full at the new cap
        }
    }

    /** Max HP for an SRP evolution phase, per the configured bands. Phase 0-3 -> low band. */
    private static int hpForPhase(int phase) {
        if (phase >= 9) {
            return ModConfig.tweaks.saplingHpPhase9;
        }
        if (phase >= 6) {
            return ModConfig.tweaks.saplingHpPhase6;
        }
        if (phase >= 4) {
            return ModConfig.tweaks.saplingHpPhase4;
        }
        return ModConfig.tweaks.saplingHpPhase1;
    }

    private boolean conditionsMet() {
        // Owner nearby, wearing a qualifying ring (Blessed or Cursed).
        if (this.ownerId == null) {
            return false;
        }
        EntityPlayer owner = this.world.getPlayerEntityByUUID(this.ownerId);
        double radius = ModConfig.tweaks.saplingConditionRadius;
        if (owner == null || owner.getDistanceSq(this) > radius * radius) {
            return false;
        }
        if (!EnigmaticLegacyCompat.isWearingQualifyingRing(owner)) {
            return false;
        }
        return infestationNearby(radius);
    }

    private boolean infestationNearby(double radius) {
        if (!isSrpLoaded()) {
            return false;
        }
        AxisAlignedBB box = this.getEntityBoundingBox().grow(radius);
        int alive = ParasiteScan.countLivingParasites(this.world, box);
        if (alive >= ModConfig.tweaks.saplingMinParasites) {
            return true;
        }

        // Fallback: infested SRP blocks nearby (scan every 100 t, cached in between).
        if (--this.blockScanCooldown <= 0) {
            this.blockScanCooldown = 5; // 5 * 20t checks = 100 t
            this.cachedBlockInfestation = false;
            BlockPos base = new BlockPos(this);
            for (BlockPos pos : BlockPos.getAllInBoxMutable(base.add(-8, -4, -8), base.add(8, 4, 8))) {
                ResourceLocation name = this.world.getBlockState(pos).getBlock().getRegistryName();
                if (name != null && "srparasites".equals(name.getResourceDomain())) {
                    this.cachedBlockInfestation = true;
                    break;
                }
            }
        }
        return this.cachedBlockInfestation;
    }

    private void bearFruit() {
        this.entityDropItem(new ItemStack(ModItems.CORRUPTED_FRUIT), 0.3f);
        if (this.world instanceof WorldServer) {
            ((WorldServer) this.world).spawnParticle(EnumParticleTypes.SPELL_MOB,
                    this.posX, this.posY + this.height * 0.6D, this.posZ, 30,
                    0.4D, 0.6D, 0.4D, 0.05D);
        }
        this.world.playSound(null, this.posX, this.posY, this.posZ,
                net.minecraft.init.SoundEvents.BLOCK_CHORUS_FLOWER_GROW,
                SoundCategory.NEUTRAL, 1.0f, 0.7f);
        InsaneTweaksMod.LOGGER.info("[InsaneTweaks] Corrupted Sapling matured at {},{},{}",
                (int) this.posX, (int) this.posY, (int) this.posZ);
        this.setDead();
    }

    @Override
    public void writeEntityToNBT(@Nonnull NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setInteger("SaplingGrowth", this.growthTicks);
        if (this.ownerId != null) {
            compound.setUniqueId("SaplingOwner", this.ownerId);
        }
    }

    @Override
    public void readEntityFromNBT(@Nonnull NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.growthTicks = compound.getInteger("SaplingGrowth");
        if (compound.hasUniqueId("SaplingOwner")) {
            this.ownerId = compound.getUniqueId("SaplingOwner");
        }
        int total = Math.max(20, ModConfig.tweaks.saplingGrowthTicks);
        this.dataManager.set(STAGE, Math.min(MAX_STAGE, (this.growthTicks * MAX_STAGE) / total));
    }

    @Nullable
    @Override
    protected net.minecraft.util.SoundEvent getHurtSound(@Nonnull net.minecraft.util.DamageSource source) {
        return net.minecraft.init.SoundEvents.BLOCK_WOOD_HIT;
    }

    @Nullable
    @Override
    protected net.minecraft.util.SoundEvent getDeathSound() {
        return net.minecraft.init.SoundEvents.BLOCK_GRASS_BREAK;
    }
}
