package com.spege.insanetweaks.entities;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nonnull;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.dhanantry.scapeandrunparasites.init.SRPSounds;
import com.dhanantry.scapeandrunparasites.util.SRPAttributes;
import com.spege.insanetweaks.entities.ai.SummonTargetingHelper;

import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.util.ParticleBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class EntityPrimitiveSummonerMinion extends EntitySummonedCreature implements IRangedAttackMob {

    private static final int BASE_RANGED_COOLDOWN = 280;
    private static final int CHARGE_CUE_TICK = BASE_RANGED_COOLDOWN - 20;
    private static final int BASE_SUMMON_COOLDOWN = 180;
    private static final int INITIAL_SUMMON_COOLDOWN = 40;
    private static final int POST_VOMIT_RETREAT_TICKS = 24;
    private static final int MAX_ACTIVE_RUPTERS = 4;
    private static final double VOMIT_MIN_DISTANCE = 4.0D;
    private static final double VOMIT_MAX_DISTANCE = 5.5D;
    private static final double VOMIT_MIN_DISTANCE_SQ = VOMIT_MIN_DISTANCE * VOMIT_MIN_DISTANCE;
    private static final double VOMIT_MAX_DISTANCE_SQ = VOMIT_MAX_DISTANCE * VOMIT_MAX_DISTANCE;
    private static final double VOMIT_VERTICAL_TOLERANCE = 2.75D;
    private static final double APPROACH_DISTANCE_MIN = 4.0D;
    private static final double APPROACH_DISTANCE_MAX = 5.0D;
    private static final double APPROACH_DISTANCE_MIN_SQ = APPROACH_DISTANCE_MIN * APPROACH_DISTANCE_MIN;
    private static final double APPROACH_DISTANCE_MAX_SQ = APPROACH_DISTANCE_MAX * APPROACH_DISTANCE_MAX;
    private static final double COOLDOWN_COMBAT_DISTANCE_MIN = 7.0D;
    private static final double COOLDOWN_COMBAT_DISTANCE_MAX = 8.0D;
    private static final double COOLDOWN_COMBAT_DISTANCE_MIN_SQ = COOLDOWN_COMBAT_DISTANCE_MIN
            * COOLDOWN_COMBAT_DISTANCE_MIN;
    private static final double COOLDOWN_COMBAT_DISTANCE_MAX_SQ = COOLDOWN_COMBAT_DISTANCE_MAX
            * COOLDOWN_COMBAT_DISTANCE_MAX;
    private static final double CASTER_RETURN_DISTANCE = 12.0D;
    private static final double CASTER_RETURN_DISTANCE_SQ = CASTER_RETURN_DISTANCE * CASTER_RETURN_DISTANCE;
    private static final double CASTER_STEP_DISTANCE = 2.0D;

    private int rangedAttackTimer = 0;
    private int summonCooldown = INITIAL_SUMMON_COOLDOWN;
    private int vomitParticles = 0;
    private float potencyMultiplier = 1.0F;
    private boolean initialRupterWavePending = true;
    private int combatMoveDecisionCooldown = 0;
    private int postVomitRetreatTicks = 0;
    private final List<EntityRupterMinion> activeRupters = new ArrayList<EntityRupterMinion>();

    public EntityPrimitiveSummonerMinion(World world) {
        super(world);
        this.setSize(1.2F, 1.8F);
        this.experienceValue = 0;
    }

    @Override
    protected void initEntityAI() {
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget<EntityMob>(this, EntityMob.class, true));
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(8, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.setBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, SRPAttributes.CANRA_HEALTH);
        this.setBaseAttribute(SharedMonsterAttributes.ARMOR, SRPAttributes.CANRA_ARMOR);
        this.setBaseAttribute(SharedMonsterAttributes.MOVEMENT_SPEED, 0.322D);
        this.setBaseAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE, SRPAttributes.CANRA_KD_RESISTANCE);
        this.setBaseAttribute(SharedMonsterAttributes.ATTACK_DAMAGE, SRPAttributes.CANRA_ATTACK_DAMAGE);
        this.setBaseAttribute(SharedMonsterAttributes.FOLLOW_RANGE, 24.0D);
    }

    private void setBaseAttribute(IAttribute attribute, double value) {
        IAttributeInstance instance = this.getEntityAttribute(attribute);

        if (instance == null) {
            instance = this.getAttributeMap().registerAttribute(attribute);
        }

        instance.setBaseValue(value);
    }

    public void setPotencyMultiplier(float potencyMultiplier) {
        this.potencyMultiplier = potencyMultiplier;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (this.world.isRemote && this.vomitParticles > 0) {
            this.vomitParticles--;
            this.spawnVomitMouthParticles();
        }

        if (!this.world.isRemote) {
            this.refreshParasiteRepelProtection();
            SummonTargetingHelper.syncCasterPriorityTarget(this);
            this.trySummonRupters();
            this.handleCombatSupport();
        }
    }

    private void refreshParasiteRepelProtection() {
        PotionEffect repel = this.getActivePotionEffect(SRPPotions.EPEL_E);

        if (repel == null || repel.getDuration() <= 40) {
            this.addPotionEffect(new PotionEffect(SRPPotions.EPEL_E, 100, 0, false, false));
        }
    }

    private void trySummonRupters() {
        this.cleanupRupters();

        if (this.summonCooldown > 0) {
            this.summonCooldown--;
            return;
        }

        boolean initialWave = this.initialRupterWavePending;
        EntityLivingBase target = this.getAttackTarget();
        if (!initialWave && (target == null || !target.isEntityAlive())) {
            return;
        }

        int availableSlots = MAX_ACTIVE_RUPTERS - this.activeRupters.size();
        if (availableSlots <= 0) {
            this.summonCooldown = 40;
            return;
        }

        int summonCount = Math.min(2, availableSlots);
        for (int i = 0; i < summonCount; i++) {
            EntityRupterMinion rupter = new EntityRupterMinion(this.world);
            double offsetX = (this.rand.nextDouble() - 0.5D) * 2.0D;
            double offsetZ = (this.rand.nextDouble() - 0.5D) * 2.0D;
            rupter.setPosition(this.posX + offsetX, this.posY, this.posZ + offsetZ);
            rupter.setCaster(this.getCaster());
            rupter.setSummonerAnchor(this);
            rupter.setLifetime(260);
            this.world.spawnEntity(rupter);
            this.activeRupters.add(rupter);
        }

        this.world.setEntityState(this, (byte) 101);
        this.playSound(SRPSounds.CANRA_SPECIAL, 2.5F, 1.0F);
        this.initialRupterWavePending = false;
        this.summonCooldown = BASE_SUMMON_COOLDOWN;
    }

    private void cleanupRupters() {
        Iterator<EntityRupterMinion> iterator = this.activeRupters.iterator();
        while (iterator.hasNext()) {
            EntityRupterMinion rupter = iterator.next();
            if (rupter == null || !rupter.isEntityAlive()) {
                iterator.remove();
            }
        }
    }

    private void handleCombatSupport() {
        EntityLivingBase target = this.getAttackTarget();
        if (target != null && target.isEntityAlive()) {
            this.getLookHelper().setLookPositionWithEntity(target, 180.0F, 20.0F);
            double horizontalDistanceSq = this.getHorizontalDistanceSq(target);
            double verticalDifference = this.getVerticalDifference(target);

            if (this.canEntityBeSeen(target)) {
                if (this.rangedAttackTimer < BASE_RANGED_COOLDOWN) {
                    this.rangedAttackTimer++;
                }

                if (this.rangedAttackTimer == CHARGE_CUE_TICK) {
                    this.world.setEntityState(this, (byte) 100);
                    this.playSound(SRPSounds.CANRA_SPECIAL, 2.0F, 1.0F);
                }

                if (this.rangedAttackTimer >= BASE_RANGED_COOLDOWN
                        && horizontalDistanceSq >= VOMIT_MIN_DISTANCE_SQ
                        && horizontalDistanceSq <= VOMIT_MAX_DISTANCE_SQ
                        && verticalDifference <= VOMIT_VERTICAL_TOLERANCE) {
                    this.rangedAttackTimer = 0;
                    this.postVomitRetreatTicks = POST_VOMIT_RETREAT_TICKS;
                    this.combatMoveDecisionCooldown = 0;
                    this.attackEntityWithRangedAttack(target, MathHelper.sqrt(horizontalDistanceSq));
                }
            } else if (this.rangedAttackTimer > 0) {
                this.rangedAttackTimer--;
            }

            this.handleCombatMovement(target, horizontalDistanceSq, verticalDifference);
            return;
        }

        this.rangedAttackTimer = 0;
        this.postVomitRetreatTicks = 0;
        this.combatMoveDecisionCooldown = 0;

        EntityLivingBase caster = this.getCaster();
        if (caster != null && this.getDistanceSq(caster) > 64.0D) {
            this.getNavigator().tryMoveToEntityLiving(caster, 1.15D);
        }
    }

    /*
     * Backup of the previous spacing scheme before the dedicated combat loop:
     * - keep caster leash at 12 blocks
     * - while vomit is on cooldown, retreat if enemy < 4.5 blocks
     * - step forward if enemy > 6 blocks
     * - otherwise stay in place
     *
     * Current scheme below is the newer loop:
     * approach for vomit -> cast -> retreat to 7-8 blocks -> spacing/wander ->
     * re-approach.
     */
    private void handleCombatMovement(EntityLivingBase target, double horizontalDistanceSq, double verticalDifference) {
        if (this.combatMoveDecisionCooldown > 0) {
            this.combatMoveDecisionCooldown--;
            return;
        }

        EntityLivingBase caster = this.getCaster();

        if (caster != null && caster.isEntityAlive() && this.getDistanceSq(caster) > CASTER_RETURN_DISTANCE_SQ) {
            this.stepTowards(caster, CASTER_STEP_DISTANCE, 1.15D);
            this.combatMoveDecisionCooldown = 8;
            return;
        }

        if (verticalDifference > VOMIT_VERTICAL_TOLERANCE) {
            this.stepTowards(target, 1.6D, 1.1D);
            this.combatMoveDecisionCooldown = 5 + this.rand.nextInt(3);
            return;
        }

        if (this.rangedAttackTimer >= CHARGE_CUE_TICK) {
            this.moveToCombatRing(target, 4.35D + this.rand.nextDouble() * 0.45D,
                    (this.rand.nextDouble() - 0.5D) * 0.45D, 1.15D);
            this.combatMoveDecisionCooldown = 4 + this.rand.nextInt(3);
            return;
        }

        if (this.postVomitRetreatTicks > 0) {
            this.moveToCombatRing(target, 7.1D + this.rand.nextDouble() * 0.9D,
                    (this.rand.nextBoolean() ? 1.0D : -1.0D) * (0.6D + this.rand.nextDouble() * 0.7D), 1.1D);
            this.postVomitRetreatTicks--;
            this.combatMoveDecisionCooldown = 5 + this.rand.nextInt(3);
            return;
        }

        if (horizontalDistanceSq < COOLDOWN_COMBAT_DISTANCE_MIN_SQ) {
            this.moveToCombatRing(target, 7.2D + this.rand.nextDouble() * 0.6D,
                    (this.rand.nextBoolean() ? 1.0D : -1.0D) * (0.7D + this.rand.nextDouble() * 0.7D), 1.0D);
            this.combatMoveDecisionCooldown = 8 + this.rand.nextInt(4);
        } else if (horizontalDistanceSq > COOLDOWN_COMBAT_DISTANCE_MAX_SQ) {
            this.moveToCombatRing(target, 7.1D + this.rand.nextDouble() * 0.7D,
                    (this.rand.nextDouble() - 0.5D) * 0.5D, 1.0D);
            this.combatMoveDecisionCooldown = 8 + this.rand.nextInt(4);
        } else if (horizontalDistanceSq >= APPROACH_DISTANCE_MIN_SQ
                && horizontalDistanceSq <= APPROACH_DISTANCE_MAX_SQ) {
            this.getNavigator().clearPath();
            this.combatMoveDecisionCooldown = 10;
        } else {
            this.moveToCombatRing(target, 7.1D + this.rand.nextDouble() * 0.7D,
                    (this.rand.nextBoolean() ? 1.0D : -1.0D) * (1.0D + this.rand.nextDouble() * 0.9D), 0.9D);
            this.combatMoveDecisionCooldown = 12 + this.rand.nextInt(8);
        }
    }

    private void stepTowards(EntityLivingBase target, double stepDistance, double speed) {
        double dx = target.posX - this.posX;
        double dz = target.posZ - this.posZ;
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length <= 0.001D) {
            return;
        }

        double moveX = this.posX + dx / length * stepDistance;
        double moveZ = this.posZ + dz / length * stepDistance;
        this.getNavigator().tryMoveToXYZ(moveX, this.posY, moveZ, speed);
    }

    private void moveToCombatRing(EntityLivingBase target, double desiredDistance, double lateralOffset, double speed) {
        double dx = this.posX - target.posX;
        double dz = this.posZ - target.posZ;
        double length = Math.sqrt(dx * dx + dz * dz);

        if (length <= 0.001D) {
            return;
        }

        double normX = dx / length;
        double normZ = dz / length;
        double sideX = -normZ;
        double sideZ = normX;

        double moveX = target.posX + normX * desiredDistance + sideX * lateralOffset;
        double moveZ = target.posZ + normZ * desiredDistance + sideZ * lateralOffset;
        this.getNavigator().tryMoveToXYZ(moveX, this.posY, moveZ, speed);
    }

    private double getHorizontalDistanceSq(EntityLivingBase target) {
        double dx = target.posX - this.posX;
        double dz = target.posZ - this.posZ;
        return dx * dx + dz * dz;
    }

    private double getVerticalDifference(EntityLivingBase target) {
        return Math.abs(target.getEntityBoundingBox().minY - this.getEntityBoundingBox().minY);
    }

    @Override
    public void attackEntityWithRangedAttack(@Nonnull EntityLivingBase target, float distanceFactor) {
        if (this.world.isRemote) {
            return;
        }

        this.world.setEntityState(this, (byte) 100);

        Vec3d look = this.getLook(1.0F);
        double distance = Math.min(VOMIT_MAX_DISTANCE, Math.sqrt(this.getDistanceSq(target)));
        double cloudX = this.posX + look.x * distance;
        double cloudZ = this.posZ + look.z * distance;
        double cloudY = target.getEntityBoundingBox().minY + 0.02D;

        EntitySummonerVomitCloud cloud = new EntitySummonerVomitCloud(this.world, cloudX, cloudY, cloudZ);
        cloud.setOwner(this);
        cloud.setWaitTime(0);

        float radius = 4.5F + Math.max(0.0F, this.potencyMultiplier - 1.0F) * 0.35F;
        int duration = 100 + Math.round(Math.max(0.0F, this.potencyMultiplier - 1.0F) * 20.0F);
        int debuffBonus = Math.min(2, MathHelper.floor(Math.max(0.0F, this.potencyMultiplier - 1.0F) / 0.5F));

        cloud.setRadius(radius);
        cloud.setDuration(duration);
        cloud.setRadiusPerTick(-radius / (float) duration);
        cloud.setParticle(EnumParticleTypes.SPELL_MOB);
        cloud.setColor(0x6D9440);
        cloud.addEffect(new PotionEffect(SRPPotions.VOMIT_E, 100, 0, false, true));
        cloud.addEffect(new PotionEffect(SRPPotions.VIRA_E, 100, debuffBonus, false, true));
        cloud.addEffect(new PotionEffect(MobEffects.WITHER, 100, debuffBonus, false, true));
        cloud.addEffect(new PotionEffect(MobEffects.WEAKNESS, 100, debuffBonus, false, true));

        this.world.spawnEntity(cloud);
    }

    @Override
    public boolean attackEntityAsMob(@Nonnull Entity entityIn) {
        float damage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        boolean attacked = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), damage);

        if (attacked && entityIn instanceof EntityLivingBase) {
            this.applyEnchantments(this, entityIn);
            EntityLivingBase target = (EntityLivingBase) entityIn;
            SummonInfectionSafetyHelper.clearCoth(target);
            target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 60, 0, false, true));
        }

        return attacked;
    }

    @Override
    public void setSwingingArms(boolean swingingArms) {
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
        return true;
    }

    @Override
    public void onSpawn() {
        this.spawnParticleEffect();
    }

    @Override
    public void onDespawn() {
        this.spawnParticleEffect();
    }

    @Override
    public boolean hasParticleEffect() {
        return true;
    }

    @Override
    public boolean isInvisible() {
        return false;
    }

    private void spawnParticleEffect() {
        if (!this.world.isRemote) {
            return;
        }

        for (int i = 0; i < 15; i++) {
            ParticleBuilder.create(ParticleBuilder.Type.DARK_MAGIC)
                    .pos(this.posX + this.rand.nextFloat(), this.posY + this.rand.nextFloat(),
                            this.posZ + this.rand.nextFloat())
                    .clr(0.25F, 0.55F, 0.15F)
                    .spawn(this.world);
        }
    }

    @Override
    public void handleStatusUpdate(byte id) {
        if (id == 100) {
            this.vomitParticles = 40;
            return;
        }
        if (id == 101) {
            for (int i = 0; i < 12; i++) {
                ParticleBuilder.create(ParticleBuilder.Type.DARK_MAGIC)
                        .pos(this.posX + (this.rand.nextDouble() - 0.5D) * this.width * 1.8D,
                                this.posY + 0.2D + this.rand.nextDouble() * this.height * 0.5D,
                                this.posZ + (this.rand.nextDouble() - 0.5D) * this.width * 1.8D)
                        .clr(0.45F, 0.0F, 0.55F)
                        .spawn(this.world);
            }
            return;
        }

        super.handleStatusUpdate(id);
    }

    private void spawnVomitMouthParticles() {
        Vec3d look = this.getLook(1.0F);

        for (int i = 0; i < 6; i++) {
            double offsetX = this.posX + look.x * 1.2D;
            double offsetY = this.posY + this.getEyeHeight() - 0.2D;
            double offsetZ = this.posZ + look.z * 1.2D;
            double motionX = look.x * 0.2D + (this.rand.nextDouble() - 0.5D) * 0.25D;
            double motionY = 0.01D + this.rand.nextDouble() * 0.1D;
            double motionZ = look.z * 0.2D + (this.rand.nextDouble() - 0.5D) * 0.25D;

            this.world.spawnParticle(EnumParticleTypes.SPELL_MOB, offsetX, offsetY, offsetZ,
                    0.32D, 0.62D, 0.18D);
            this.world.spawnParticle(EnumParticleTypes.SLIME, offsetX, offsetY, offsetZ,
                    motionX, motionY, motionZ);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SRPSounds.CANRA_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SRPSounds.CANRA_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SRPSounds.CANRA_DEATH;
    }

    @Override
    protected float getSoundPitch() {
        return (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.block.Block blockIn) {
        this.playSound(SRPSounds.MONSTER_STEP, 0.15F, 1.0F);
    }
}
