package com.spege.insanetweaks.entities;

import javax.annotation.Nonnull;

import com.dhanantry.scapeandrunparasites.init.SRPSounds;
import com.dhanantry.scapeandrunparasites.util.SRPAttributes;
import com.spege.insanetweaks.entities.ai.SummonTargetingHelper;
import com.spege.insanetweaks.entities.projectile.EntityBomberBomb;

import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.util.ParticleBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityMoveHelper;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Summoned clone of SRP's {@code EntityOmboo} (srparasites:bomber_light).
 *
 * <p>Flying bomber minion: hovers in a band above the ground, occasionally
 * rams its target and drops fused bombs on grounded victims. The AI constants
 * mirror SRP's native Omboo tasks; the bomb entity is our own
 * {@link EntityBomberBomb} clone (SRP's {@code EntityBomb} cannot be reused —
 * its constructor requires an {@code EntityParasiteBase} owner and it spawns
 * infectious toxic clouds).
 */
@SuppressWarnings("null")
public class EntityLightBomberMinion extends EntitySummonedCreature {

    private static final DataParameter<Boolean> CHARGING = EntityDataManager
            .createKey(EntityLightBomberMinion.class, DataSerializers.BOOLEAN);

    /** Distance (squared) beyond which an idle bomber flies back to its caster. */
    private static final double RETURN_TO_CASTER_DISTANCE_SQ = 36.0D;

    private float bombDamageMultiplier = 1.0F;

    public EntityLightBomberMinion(World world) {
        super(world);
        this.setSize(1.7F, 2.4F);
        this.experienceValue = 0;
        this.moveHelper = new BomberMoveHelper(this);
        this.setNoGravity(true);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(CHARGING, Boolean.valueOf(false));
    }

    @Override
    protected void initEntityAI() {
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget<EntityMob>(this, EntityMob.class, true));
        this.tasks.addTask(4, new AIChargeAttack());
        this.tasks.addTask(4, new AIFlightLimits(7, false));
        this.tasks.addTask(4, new AIFlightLimits(20, true));
        this.tasks.addTask(5, new AIBomb());
        this.tasks.addTask(6, new AIMoveRandom());
        this.tasks.addTask(8, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.setBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, SRPAttributes.OMBOO_HEALTH);
        this.setBaseAttribute(SharedMonsterAttributes.ARMOR, SRPAttributes.OMBOO_ARMOR);
        this.setBaseAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE, SRPAttributes.OMBOO_KD_RESISTANCE);
        // ATTACK_DAMAGE is not registered by default on EntityCreature; the
        // setBaseAttribute fallback registers it so the potency modifier applied
        // by AbstractSrpSummonSpell has something to scale.
        this.setBaseAttribute(SharedMonsterAttributes.ATTACK_DAMAGE, SRPAttributes.OMBOO_ATTACK_DAMAGE);
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

    public void setBombDamageMultiplier(float bombDamageMultiplier) {
        this.bombDamageMultiplier = bombDamageMultiplier;
    }

    public float getBombDamage() {
        return (float) SRPAttributes.OMBOO_BOMBDAMAGE * this.bombDamageMultiplier;
    }

    public boolean isCharging() {
        return this.dataManager.get(CHARGING).booleanValue();
    }

    public void setCharging(boolean charging) {
        this.dataManager.set(CHARGING, Boolean.valueOf(charging));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        this.setNoGravity(true);

        if (!this.world.isRemote) {
            SummonInfectionSafetyHelper.onSummonServerTick(this);
            SummonTargetingHelper.syncCasterPriorityTarget(this);
            this.handleIdleReturn();
        }
    }

    /** Idle bombers drift back towards their caster instead of wandering off. */
    private void handleIdleReturn() {
        EntityLivingBase target = this.getAttackTarget();

        if (target != null && target.isEntityAlive()) {
            return;
        }

        EntityLivingBase caster = this.getCaster();
        if (caster != null && this.getDistanceSq(caster) > RETURN_TO_CASTER_DISTANCE_SQ) {
            this.moveHelper.setMoveTo(caster.posX, caster.posY + 4.0D, caster.posZ, 0.9D);
            this.getLookHelper().setLookPositionWithEntity(caster, 180.0F, 20.0F);
        }
    }

    @Override
    public boolean attackEntityAsMob(@Nonnull Entity entityIn) {
        float damage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        boolean attacked = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), damage);

        if (attacked && entityIn instanceof EntityLivingBase) {
            this.applyEnchantments(this, entityIn);
            EntityLivingBase target = (EntityLivingBase) entityIn;
            SummonInfectionSafetyHelper.onSuccessfulSummonHit(target);
            this.onSuccessfulAttack(target);
        }

        return attacked;
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
                    .clr(0.25F, 0.15F, 0.0F)
                    .spawn(this.world);
        }
    }

    @Override
    public void fall(float distance, float damageMultiplier) {
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SRPSounds.OMBOO_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SRPSounds.OMBOO_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SRPSounds.OMBOO_DEATH;
    }

    @Override
    protected float getSoundPitch() {
        return (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 1.0F;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.block.Block blockIn) {
    }

    // -------------------------------------------------------------------------
    // AI — constants mirror SRP's native Omboo tasks verbatim.
    // -------------------------------------------------------------------------

    /** Ram attack: climbs to a point 10 blocks above the target and dives through it. */
    private class AIChargeAttack extends EntityAIBase {

        AIChargeAttack() {
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            EntityLivingBase target = EntityLightBomberMinion.this.getAttackTarget();
            return target != null && EntityLightBomberMinion.this.getRNG().nextInt(7) == 0
                    && EntityLightBomberMinion.this.getDistanceSq(target) > 3.0D;
        }

        @Override
        public boolean shouldContinueExecuting() {
            EntityLivingBase target = EntityLightBomberMinion.this.getAttackTarget();
            return EntityLightBomberMinion.this.getMoveHelper().isUpdating()
                    && EntityLightBomberMinion.this.isCharging() && target != null && target.isEntityAlive();
        }

        @Override
        public void startExecuting() {
            EntityLivingBase target = EntityLightBomberMinion.this.getAttackTarget();

            if (target == null) {
                return;
            }

            Vec3d eyes = target.getPositionEyes(1.0F);
            EntityLightBomberMinion.this.getMoveHelper().setMoveTo(eyes.x, eyes.y + 10.0D, eyes.z, 1.0D);
            EntityLightBomberMinion.this.setCharging(true);
        }

        @Override
        public void resetTask() {
            EntityLightBomberMinion.this.setCharging(false);
        }

        @Override
        public void updateTask() {
            EntityLivingBase target = EntityLightBomberMinion.this.getAttackTarget();

            if (target == null) {
                return;
            }

            if (EntityLightBomberMinion.this.getEntityBoundingBox().intersects(target.getEntityBoundingBox())) {
                EntityLightBomberMinion.this.attackEntityAsMob(target);
                EntityLightBomberMinion.this.setCharging(false);
                return;
            }

            if (EntityLightBomberMinion.this.getDistanceSq(target) < 9.0D) {
                Vec3d eyes = target.getPositionEyes(1.0F);
                EntityLightBomberMinion.this.getMoveHelper().setMoveTo(eyes.x, eyes.y + 10.0D, eyes.z, 1.0D);
            }
        }
    }

    /** Drops a fused bomb on a grounded target that is within 5 blocks horizontally. */
    private class AIBomb extends EntityAIBase {

        private int ccc;

        @Override
        public boolean shouldExecute() {
            if (++this.ccc < 15) {
                return false;
            }

            this.ccc = 0;
            EntityLivingBase target = EntityLightBomberMinion.this.getAttackTarget();

            if (target == null) {
                return false;
            }

            if (!target.onGround) {
                this.ccc = 7;
                return false;
            }

            return target.getDistanceSq(EntityLightBomberMinion.this.posX, target.posY,
                    EntityLightBomberMinion.this.posZ) < 25.0D;
        }

        @Override
        public boolean shouldContinueExecuting() {
            return false;
        }

        @Override
        public void updateTask() {
            if (EntityLightBomberMinion.this.world.isRemote) {
                return;
            }

            EntityBomberBomb bomb = new EntityBomberBomb(EntityLightBomberMinion.this.world,
                    EntityLightBomberMinion.this);
            bomb.setFuse(80);
            bomb.setDamage(EntityLightBomberMinion.this.getBombDamage());
            EntityLightBomberMinion.this.world.spawnEntity(bomb);
        }
    }

    /** Vex-style random hover drift; tightens around the target once one is picked. */
    private class AIMoveRandom extends EntityAIBase {

        AIMoveRandom() {
            this.setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            return !EntityLightBomberMinion.this.getMoveHelper().isUpdating()
                    && EntityLightBomberMinion.this.getRNG().nextInt(7) == 0;
        }

        @Override
        public boolean shouldContinueExecuting() {
            return false;
        }

        @Override
        public void updateTask() {
            double centerX = EntityLightBomberMinion.this.posX;
            double centerY = EntityLightBomberMinion.this.posY;
            double centerZ = EntityLightBomberMinion.this.posZ;
            double spread = 1.0D;
            double speed = 0.2D;

            EntityLivingBase target = EntityLightBomberMinion.this.getAttackTarget();

            if (target != null) {
                double distanceSq = EntityLightBomberMinion.this.getDistanceSq(target);

                if (distanceSq > 100.0D) {
                    centerX = target.posX;
                    centerY = target.posY;
                    centerZ = target.posZ;
                    spread = 2.0D;
                    speed = 0.3D;
                } else if (distanceSq < 36.0D) {
                    centerX = target.posX;
                    centerY = target.posY;
                    centerZ = target.posZ;
                    spread = 3.0D;
                    speed = 0.3D;
                }
            }

            for (int i = 0; i < 3; i++) {
                double x = centerX + (double) (EntityLightBomberMinion.this.getRNG().nextInt(15) - 7) * spread;
                double y = centerY + (double) (EntityLightBomberMinion.this.getRNG().nextInt(11) - 5) * spread;
                double z = centerZ + (double) (EntityLightBomberMinion.this.getRNG().nextInt(15) - 7) * spread;

                if (EntityLightBomberMinion.this.world.isAirBlock(new BlockPos(x, y, z))) {
                    EntityLightBomberMinion.this.getMoveHelper().setMoveTo(x, y, z, speed);
                    break;
                }
            }
        }
    }

    /**
     * Keeps the bomber inside a flight band. {@code descend == true} pushes it
     * down when it climbs more than {@code limit} above the target (or above
     * {@code limit} blocks of open air); {@code descend == false} pushes it up
     * when there are fewer than {@code limit} blocks of air below it.
     */
    private class AIFlightLimits extends EntityAIBase {

        private final int limit;
        private final boolean descend;

        AIFlightLimits(int limit, boolean descend) {
            this.limit = limit;
            this.descend = descend;
            this.setMutexBits(0);
        }

        @Override
        public boolean shouldExecute() {
            return this.isTriggered();
        }

        @Override
        public boolean shouldContinueExecuting() {
            return this.isTriggered();
        }

        @Override
        public void updateTask() {
            if (this.descend) {
                EntityLightBomberMinion.this.motionY -= 0.04D;
            } else {
                EntityLightBomberMinion.this.motionY += 0.04D;
            }
        }

        private boolean isTriggered() {
            if (this.descend) {
                EntityLivingBase target = EntityLightBomberMinion.this.getAttackTarget();

                if (target != null) {
                    return EntityLightBomberMinion.this.posY > target.posY + (double) this.limit;
                }

                return this.getAirBlocksBelow() > this.limit;
            }

            return this.getAirBlocksBelow() < this.limit;
        }

        private int getAirBlocksBelow() {
            BlockPos origin = new BlockPos(EntityLightBomberMinion.this.posX, EntityLightBomberMinion.this.posY,
                    EntityLightBomberMinion.this.posZ);
            int air = 0;

            while (air <= this.limit) {
                BlockPos check = origin.down(air + 1);

                if (check.getY() < 0 || !EntityLightBomberMinion.this.world.isAirBlock(check)) {
                    break;
                }

                air++;
            }

            return air;
        }
    }

    /** Vex-style flight steering, identical in shape to the Yelloweye helper. */
    private static class BomberMoveHelper extends EntityMoveHelper {

        private final EntityLightBomberMinion bomber;

        BomberMoveHelper(EntityLightBomberMinion bomber) {
            super(bomber);
            this.bomber = bomber;
        }

        @Override
        public void onUpdateMoveHelper() {
            if (this.action != Action.MOVE_TO) {
                return;
            }

            double dx = this.posX - this.bomber.posX;
            double dy = this.posY - this.bomber.posY;
            double dz = this.posZ - this.bomber.posZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (distance < this.bomber.getEntityBoundingBox().getAverageEdgeLength()) {
                this.action = Action.WAIT;
                this.bomber.motionX *= 0.5D;
                this.bomber.motionY *= 0.5D;
                this.bomber.motionZ *= 0.5D;
                return;
            }

            this.bomber.motionX += dx / distance * 0.05D * this.speed;
            this.bomber.motionY += dy / distance * 0.05D * this.speed;
            this.bomber.motionZ += dz / distance * 0.05D * this.speed;

            EntityLivingBase target = this.bomber.getAttackTarget();
            if (target == null) {
                this.bomber.renderYawOffset = this.bomber.rotationYaw = -((float) MathHelper.atan2(
                        this.bomber.motionX, this.bomber.motionZ)) * (180.0F / (float) Math.PI);
            } else {
                double targetDx = target.posX - this.bomber.posX;
                double targetDz = target.posZ - this.bomber.posZ;
                this.bomber.renderYawOffset = this.bomber.rotationYaw = -((float) MathHelper.atan2(
                        targetDx, targetDz)) * (180.0F / (float) Math.PI);
            }
        }
    }
}
