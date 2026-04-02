package com.spege.insanetweaks.entities;

import javax.annotation.Nonnull;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.dhanantry.scapeandrunparasites.init.SRPSounds;
import com.dhanantry.scapeandrunparasites.util.SRPAttributes;
import com.spege.insanetweaks.entities.ai.SummonTargetingHelper;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeNadeProjectile;
import com.spege.insanetweaks.entities.projectile.EntityYelloweyeSpineball;

import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.util.ParticleBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityMoveHelper;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class EntityPrimitiveYelloweyeMinion extends EntitySummonedCreature implements IRangedAttackMob {

    private static final int BASE_ATTACK_COOLDOWN = 80;
    private static final int BASE_ATTACK_INTERVAL = 20;
    private static final int CHARGE_CUE_TICK = BASE_ATTACK_COOLDOWN - 10;
    private static final double MAX_ATTACK_DISTANCE_SQ = 4225.0D;
    private static final double DESIRED_COMBAT_DISTANCE_SQ = 64.0D;
    private static final double MIN_COMBAT_DISTANCE_SQ = 16.0D;

    private int attackTimer = 0;
    private int shotCounter = 0;
    private int idleMoveCooldown = 0;
    private float projectileDamageMultiplier = 1.0F;

    public EntityPrimitiveYelloweyeMinion(World world) {
        super(world);
        this.setSize(0.4F, 1.5F);
        this.experienceValue = 0;
        this.moveHelper = new YelloweyeMoveHelper(this);
        this.setNoGravity(true);
    }

    @Override
    protected void initEntityAI() {
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget<EntityMob>(this, EntityMob.class, true));
        this.tasks.addTask(8, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.setBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, SRPAttributes.EMANA_HEALTH);
        this.setBaseAttribute(SharedMonsterAttributes.ARMOR, SRPAttributes.EMANA_ARMOR);
        this.setBaseAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE, SRPAttributes.EMANA_KD_RESISTANCE);
        this.setBaseAttribute(SharedMonsterAttributes.FOLLOW_RANGE, 24.0D);
        this.setBaseAttribute(SharedMonsterAttributes.MOVEMENT_SPEED, 0.32D);
    }

    private void setBaseAttribute(IAttribute attribute, double value) {
        IAttributeInstance instance = this.getEntityAttribute(attribute);

        if (instance == null) {
            instance = this.getAttributeMap().registerAttribute(attribute);
        }

        instance.setBaseValue(value);
    }

    public void setProjectileDamageMultiplier(float projectileDamageMultiplier) {
        this.projectileDamageMultiplier = projectileDamageMultiplier;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        this.setNoGravity(true);

        if (!this.world.isRemote) {
            this.refreshParasiteRepelProtection();
            SummonTargetingHelper.syncCasterPriorityTarget(this);
            this.handleCombatAndFlight();
        }
    }

    private void refreshParasiteRepelProtection() {
        PotionEffect repel = this.getActivePotionEffect(SRPPotions.EPEL_E);

        if (repel == null || repel.getDuration() <= 40) {
            this.addPotionEffect(new PotionEffect(SRPPotions.EPEL_E, 100, 0, false, false));
        }
    }

    private void handleCombatAndFlight() {
        EntityLivingBase target = this.getAttackTarget();
        if (target != null && target.isEntityAlive()) {
            this.handleTargetMovement(target);
            this.getLookHelper().setLookPositionWithEntity(target, 180.0F, 30.0F);

            if (this.canEntityBeSeen(target) && this.getDistanceSq(target) < MAX_ATTACK_DISTANCE_SQ) {
                this.attackTimer++;

                if (this.attackTimer == CHARGE_CUE_TICK) {
                    this.playYelloweyeChargeCue();
                }

                if (this.attackTimer > BASE_ATTACK_COOLDOWN && this.attackTimer % BASE_ATTACK_INTERVAL == 0) {
                    this.attackTimer = 0;
                    this.idleMoveCooldown = 0;
                    float distanceFactor = MathHelper.sqrt(this.getDistanceSq(target));
                    this.attackEntityWithRangedAttack(target, distanceFactor);
                }
            } else if (this.attackTimer > 0) {
                this.attackTimer--;
            }
            return;
        }

        this.attackTimer = 0;

        EntityLivingBase caster = this.getCaster();
        if (caster != null && this.getDistanceSq(caster) > 36.0D) {
            this.moveHelper.setMoveTo(caster.posX, caster.posY + 2.0D, caster.posZ, 0.9D);
            this.getLookHelper().setLookPositionWithEntity(caster, 180.0F, 20.0F);
            return;
        }

        this.handleIdleFlight();
    }

    private void handleTargetMovement(EntityLivingBase target) {
        double distanceSq = this.getDistanceSq(target);

        if (distanceSq > DESIRED_COMBAT_DISTANCE_SQ) {
            this.moveHelper.setMoveTo(target.posX, target.posY + target.height + 1.0D, target.posZ, 1.0D);
            return;
        }

        if (distanceSq < MIN_COMBAT_DISTANCE_SQ) {
            double dx = this.posX - target.posX;
            double dy = this.posY - target.posY;
            double dz = this.posZ - target.posZ;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (length > 0.001D) {
                this.moveHelper.setMoveTo(this.posX + dx / length * 6.0D, this.posY + 2.0D,
                        this.posZ + dz / length * 6.0D, 1.0D);
            }
        }
    }

    private void handleIdleFlight() {
        if (this.idleMoveCooldown > 0) {
            this.idleMoveCooldown--;
            return;
        }

        this.idleMoveCooldown = 20 + this.rand.nextInt(20);

        double targetX = this.posX + (double) (this.rand.nextInt(15) - 7);
        double targetY = this.posY + (double) (this.rand.nextInt(11) - 5);
        double targetZ = this.posZ + (double) (this.rand.nextInt(15) - 7);

        this.moveHelper.setMoveTo(targetX, targetY, targetZ, 0.5D);
    }

    @Override
    public void attackEntityWithRangedAttack(@Nonnull EntityLivingBase target, float distanceFactor) {
        boolean explosiveShot = this.shotCounter >= 4;
        Vec3d look = this.getLook(1.0F);
        double accelX = target.posX - (this.posX + look.x);
        double accelY = target.getEntityBoundingBox().minY + (double) (target.height / 2.0F)
                - (0.5D + this.posY + (double) (this.height / 2.0F));
        double accelZ = target.posZ - (this.posZ + look.z);

        if (explosiveShot) {
            this.shotCounter = 0;
            EntityYelloweyeNadeProjectile projectile = new EntityYelloweyeNadeProjectile(this.world, this, accelX,
                    accelY, accelZ, 3, 60, this.getProjectileBaseDamage());
            this.spawnYelloweyeProjectile(projectile, look);
            this.playSound(SRPSounds.EMANA_SHOOTING, 2.0F, 2.0F);
            return;
        }

        EntityYelloweyeSpineball projectile = new EntityYelloweyeSpineball(this.world, this, accelX, accelY, accelZ,
                this.getProjectileBaseDamage());
        projectile.applyPrimitiveYelloweyeDefaults();
        projectile.setPotencyMultiplier(this.projectileDamageMultiplier);
        this.spawnYelloweyeProjectile(projectile, look);
        this.playSound(SRPSounds.EMANA_SHOOTING, 2.0F, 1.0F);
    }

    private void playYelloweyeChargeCue() {
        this.shotCounter++;

        if (this.shotCounter >= 4) {
            this.world.setEntityState(this, (byte) 100);
            float pitch = (this.rand.nextFloat() - this.rand.nextFloat()) * 0.4F + 2.0F;
            this.playSound(SRPSounds.EMANA_HURT, 4.0F, pitch);
            return;
        }

        this.playSound(SRPSounds.EMANA_SHOOTINGPOST, 2.0F, 1.0F);
    }

    private float getProjectileBaseDamage() {
        return SRPAttributes.EMANA_RANGED_DAMAGE * this.projectileDamageMultiplier;
    }

    private void spawnYelloweyeProjectile(Entity entity, Vec3d look) {
        entity.posX = this.posX + look.x;
        entity.posY = this.posY + (double) this.getEyeHeight() - 0.2D;
        entity.posZ = this.posZ + look.z;
        this.world.spawnEntity(entity);
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
                    .clr(0.2F, 0.35F, 0.0F)
                    .spawn(this.world);
        }
    }

    @Override
    public void handleStatusUpdate(byte id) {
        if (id == 100) {
            for (int i = 0; i < 2; i++) {
                this.world.spawnParticle(EnumParticleTypes.FLAME,
                        this.posX + (this.rand.nextDouble() - 0.5D) * (double) this.width,
                        this.posY + this.rand.nextDouble() * (double) this.height,
                        this.posZ + (this.rand.nextDouble() - 0.5D) * (double) this.width,
                        0.0D, 0.0D, 0.0D);
            }
            return;
        }

        super.handleStatusUpdate(id);
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SRPSounds.EMANA_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SRPSounds.EMANA_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SRPSounds.EMANA_DEATH;
    }

    @Override
    protected float getSoundPitch() {
        return (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.block.Block blockIn) {
    }

    private static class YelloweyeMoveHelper extends EntityMoveHelper {

        private final EntityPrimitiveYelloweyeMinion yelloweye;

        YelloweyeMoveHelper(EntityPrimitiveYelloweyeMinion yelloweye) {
            super(yelloweye);
            this.yelloweye = yelloweye;
        }

        @Override
        public void onUpdateMoveHelper() {
            if (this.action != Action.MOVE_TO) {
                return;
            }

            double dx = this.posX - this.yelloweye.posX;
            double dy = this.posY - this.yelloweye.posY;
            double dz = this.posZ - this.yelloweye.posZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance < this.yelloweye.getEntityBoundingBox().getAverageEdgeLength()) {
                this.action = Action.WAIT;
                this.yelloweye.motionX *= 0.5D;
                this.yelloweye.motionY *= 0.5D;
                this.yelloweye.motionZ *= 0.5D;
                return;
            }

            this.yelloweye.motionX += dx / distance * 0.05D * this.speed;
            this.yelloweye.motionY += dy / distance * 0.05D * this.speed;
            this.yelloweye.motionZ += dz / distance * 0.05D * this.speed;

            EntityLivingBase target = this.yelloweye.getAttackTarget();
            if (target == null) {
                this.yelloweye.renderYawOffset = this.yelloweye.rotationYaw = -((float) MathHelper.atan2(
                        this.yelloweye.motionX, this.yelloweye.motionZ)) * (180.0F / (float) Math.PI);
            } else {
                double targetDx = target.posX - this.yelloweye.posX;
                double targetDz = target.posZ - this.yelloweye.posZ;
                this.yelloweye.renderYawOffset = this.yelloweye.rotationYaw = -((float) MathHelper.atan2(
                        targetDx, targetDz)) * (180.0F / (float) Math.PI);
            }
        }
    }
}
