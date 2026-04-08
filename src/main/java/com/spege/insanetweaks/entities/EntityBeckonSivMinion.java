package com.spege.insanetweaks.entities;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.dhanantry.scapeandrunparasites.init.SRPSounds;
import com.dhanantry.scapeandrunparasites.util.SRPAttributes;
import com.spege.insanetweaks.entities.ai.SummonTargetingHelper;
import com.spege.insanetweaks.entities.logic.MinionTornadoLogic;
import com.spege.insanetweaks.util.SrpInfestationHelper;
import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.util.BlockUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings("null")
public class EntityBeckonSivMinion extends EntitySummonedCreature {

    private static final int LIGHTNING_INTERVAL_TICKS = 60;
    private static final int BASE_SUMMON_COOLDOWN = 160;
    private static final int SUMMON_COOLDOWN_WHEN_CAPPED = 40;
    private static final int CHILD_MINION_BATCH_SIZE = 2;
    private static final int CHILD_MINION_LIFETIME = 800;
    private static final int TERRAIN_INFEST_RADIUS = 4;
    private static final int TERRAIN_INFEST_VERTICAL_RANGE = 3;
    private static final int TERRAIN_INFEST_MAX_BLOCKS = 18;
    private final BossInfoServer bossInfo = (BossInfoServer) new BossInfoServer(this.getDisplayName(), BossInfo.Color.RED, BossInfo.Overlay.PROGRESS).setDarkenSky(false);
    private int lightningTimer = 0;
    private static final int MAX_ACTIVE_MINIONS = 8;
    private int summonCooldown = 60;
    private boolean terrainInfested = false;
    private final List<EntitySummonedCreature> activeMinions = new ArrayList<>();

    public EntityBeckonSivMinion(World world) {
        super(world);
        this.setSize(0.8F, 6.9F);
        this.experienceValue = 0;
        this.isImmuneToFire = true;
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        // Values based on SRPAttributes.VENKROLSIV_HEALTH, etc., which are usually around 800+
        // Since we don't have direct access in compile environment easily if it varies, we use the public fields of SRPAttributes
        this.setBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, SRPAttributes.VENKROLSIV_HEALTH);
        this.setBaseAttribute(SharedMonsterAttributes.ARMOR, SRPAttributes.VENKROLSIV_ARMOR);
        this.setBaseAttribute(SharedMonsterAttributes.MOVEMENT_SPEED, 0.0D); // Stationary
        this.setBaseAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE, 1.0D);
        this.setBaseAttribute(SharedMonsterAttributes.ATTACK_DAMAGE, SRPAttributes.VENKROLSIV_ATTACK_DAMAGE);
        this.setBaseAttribute(SharedMonsterAttributes.FOLLOW_RANGE, 64.0D);
    }

    private void setBaseAttribute(IAttribute attribute, double value) {
        IAttributeInstance instance = this.getEntityAttribute(attribute);
        if (instance == null) {
            instance = this.getAttributeMap().registerAttribute(attribute);
        }
        instance.setBaseValue(value);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        // Venkrol stays rooted in place and acts as a summoned defense nexus.
        this.motionX = 0;
        this.motionY = 0;
        this.motionZ = 0;
        this.rotationYaw = this.prevRotationYaw;

        if (!this.world.isRemote) {
            this.infestNearbyTerrainOnce();
            this.refreshParasiteRepelProtection();
            SummonTargetingHelper.syncCasterPriorityTarget(this);
            MinionTornadoLogic.tickTornadoEffects(this);

            if (this.lightningTimer > 0) {
                this.lightningTimer--;
            } else {
                this.lightningTimer = LIGHTNING_INTERVAL_TICKS;
                strikeHostilesWithLightning();
            }

            this.trySummonMinions();
        }
    }

    private void infestNearbyTerrainOnce() {
        if (this.terrainInfested) {
            return;
        }

        this.terrainInfested = true;
        int converted = SrpInfestationHelper.infestNearbyBlocks(this.world, this.getPosition().down(), TERRAIN_INFEST_RADIUS,
                TERRAIN_INFEST_VERTICAL_RANGE, TERRAIN_INFEST_MAX_BLOCKS);
        if (converted > 0 && this.world instanceof WorldServer) {
            WorldServer worldServer = (WorldServer) this.world;
            worldServer.spawnParticle(EnumParticleTypes.SPELL_MOB_AMBIENT, this.posX, this.posY + 0.8D, this.posZ,
                    18, 2.2D, 0.4D, 2.2D, 0.02D);
        }
    }

    private void refreshParasiteRepelProtection() {
        PotionEffect repel = this.getActivePotionEffect(SRPPotions.EPEL_E);
        if (repel == null || repel.getDuration() <= 40) {
            this.addPotionEffect(new PotionEffect(SRPPotions.EPEL_E, 100, 0, false, false));
        }
    }

    private void trySummonMinions() {
        this.cleanupMinions();

        if (this.summonCooldown > 0) {
            this.summonCooldown--;
            return;
        }

        int availableSlots = MAX_ACTIVE_MINIONS - this.activeMinions.size();
        if (availableSlots <= 0) {
            this.summonCooldown = SUMMON_COOLDOWN_WHEN_CAPPED;
            return;
        }

        int spawnedCount = 0;
        int summonCount = Math.min(CHILD_MINION_BATCH_SIZE, availableSlots);
        for (int i = 0; i < summonCount; i++) {
            EntitySummonedCreature minion = this.createRandomChildMinion();
            if (minion != null && this.spawnChildMinion(minion)) {
                spawnedCount++;
            }
        }

        if (spawnedCount <= 0) {
            this.summonCooldown = SUMMON_COOLDOWN_WHEN_CAPPED;
            return;
        }

        this.world.setEntityState(this, (byte) 101);
        this.playSound(SRPSounds.VENKROLSIII_HURT, 1.5F, 0.8F);
        this.summonCooldown = BASE_SUMMON_COOLDOWN;
    }

    private EntitySummonedCreature createRandomChildMinion() {
        int roll = this.rand.nextInt(3);
        if (roll == 0) {
            return new EntityFerCowMinion(this.world);
        }
        if (roll == 1) {
            return new EntityPrimitiveYelloweyeMinion(this.world);
        }
        return new EntityPrimitiveSummonerMinion(this.world);
    }

    private boolean spawnChildMinion(EntitySummonedCreature minion) {
        BlockPos pos = BlockUtils.findNearbyFloorSpace(this, 3, 6);
        if (pos == null) {
            return false;
        }

        minion.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        minion.setCaster(this.getCaster());
        minion.setLifetime(CHILD_MINION_LIFETIME);

        EntityLivingBase priorityTarget = this.getAttackTarget();
        if (priorityTarget != null && priorityTarget.isEntityAlive() && !minion.isOnSameTeam(priorityTarget)) {
            minion.setAttackTarget(priorityTarget);
        }

        if (!this.world.spawnEntity(minion)) {
            return false;
        }

        this.activeMinions.add(minion);
        if (this.rand.nextFloat() < 0.35F) {
            this.world.addWeatherEffect(new EntityLightningBolt(this.world, minion.posX, minion.posY, minion.posZ, true));
        }
        return true;
    }

    private void cleanupMinions() {
        Iterator<EntitySummonedCreature> iterator = this.activeMinions.iterator();
        while (iterator.hasNext()) {
            EntitySummonedCreature minion = iterator.next();
            if (minion == null || !minion.isEntityAlive()) {
                iterator.remove();
            }
        }
    }

    private void strikeHostilesWithLightning() {
        double range = 48.0D;
        AxisAlignedBB box = new AxisAlignedBB(this.posX - range, this.posY - 10, this.posZ - range,
                this.posX + range, this.posY + 40, this.posZ + range);

        List<EntityLivingBase> targets = this.world.getEntitiesWithinAABB(EntityLivingBase.class, box);
        boolean struck = false;

        for (EntityLivingBase target : targets) {
            if (target == this || !target.isEntityAlive()) {
                continue;
            }

            if (this.isOnSameTeam(target) || target == this.getCaster()) {
                continue;
            }

            if (this.rand.nextFloat() < 0.3F && !struck) {
                BlockPos pos = target.getPosition();
                this.world.addWeatherEffect(new EntityLightningBolt(this.world, pos.getX(), pos.getY(), pos.getZ(), false));
                SummonInfectionSafetyHelper.clearCoth(target);
                struck = true;
            }
        }
    }

    @Override
    protected void updateAITasks() {
        super.updateAITasks();
        this.bossInfo.setPercent(this.getHealth() / this.getMaxHealth());
    }

    @Override
    public void setCustomNameTag(@Nonnull String name) {
        super.setCustomNameTag(name);
        this.bossInfo.setName(this.getDisplayName());
    }

    @Override
    public void addTrackingPlayer(EntityPlayerMP player) {
        super.addTrackingPlayer(player);
        this.bossInfo.addPlayer(player);
    }

    @Override
    public void removeTrackingPlayer(EntityPlayerMP player) {
        super.removeTrackingPlayer(player);
        this.bossInfo.removePlayer(player);
    }

    @Override
    public boolean attackEntityFrom(@Nonnull DamageSource source, float amount) {
        boolean flag = super.attackEntityFrom(source, amount);
        if (flag && source.getTrueSource() instanceof EntityPlayerMP) {
            this.bossInfo.addPlayer((EntityPlayerMP) source.getTrueSource());
        }
        return flag;
    }

    @Override
    public java.util.UUID getOwnerId() {
        return super.func_184753_b();
    }

    @Override
    public Entity getOwner() {
        return this.getCaster();
    }

    @Override
    public boolean hasRangedAttack() {
        return false;
    }

    @Override
    public void onSpawn() {
        // Visual summon layer handled client-side; terrain infestation runs on the first server tick.
    }

    @Override
    public void onDespawn() {
    }

    @Override
    public boolean hasParticleEffect() {
        return true;
    }

    @Override
    public boolean isInvisible() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SRPSounds.VENKROLSIV;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SRPSounds.VENKROLSIII_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SRPSounds.VENKROLSIII_DEATH;
    }

    @Override
    public void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        this.terrainInfested = compound.getBoolean("TerrainInfested");
    }

    @Override
    public void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setBoolean("TerrainInfested", this.terrainInfested);
    }
}
