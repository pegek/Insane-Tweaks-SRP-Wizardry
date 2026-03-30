package com.spege.insanetweaks.entities;

import com.dhanantry.scapeandrunparasites.init.SRPSounds;
import com.dhanantry.scapeandrunparasites.util.SRPAttributes;
import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.util.ParticleBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWanderAvoidWater;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

public class EntityAssimilatedCowMinion extends EntitySummonedCreature {

    public EntityAssimilatedCowMinion(World world) {
        super(world);
        this.setSize(0.9F, 1.4F);
        this.experienceValue = 0;
    }

    @Override
    protected void initEntityAI() {
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget<EntityMob>(this, EntityMob.class, true));
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(2, new EntityAIAttackMelee(this, 1.5D, false));
        this.tasks.addTask(7, new EntityAIWanderAvoidWater(this, 1.0D));
        this.tasks.addTask(8, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.setBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, SRPAttributes.FERCOW_HEALTH);
        this.setBaseAttribute(SharedMonsterAttributes.ARMOR, SRPAttributes.FERCOW_ARMOR);
        this.setBaseAttribute(SharedMonsterAttributes.MOVEMENT_SPEED, 0.23000000298023224D);
        this.setBaseAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE, SRPAttributes.FERCOW_KD_RESISTANCE);
        this.setBaseAttribute(SharedMonsterAttributes.ATTACK_DAMAGE, SRPAttributes.FERCOW_ATTACK_DAMAGE);
        this.setBaseAttribute(SharedMonsterAttributes.FOLLOW_RANGE, 24.0D);
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

        EntityLivingBase caster = this.getCaster();
        if (!this.world.isRemote && caster != null && this.getAttackTarget() == null && this.getDistanceSq(caster) > 64.0D) {
            this.getNavigator().tryMoveToEntityLiving(caster, 1.2D);
        }
    }

    @Override
    public boolean attackEntityAsMob(@Nonnull Entity entityIn) {
        float damage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        boolean attacked = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), damage);

        if (attacked && entityIn instanceof EntityLivingBase) {
            this.applyEnchantments(this, entityIn);
            this.onSuccessfulAttack((EntityLivingBase) entityIn);
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
                    .clr(0.1F, 0.2F, 0.0F)
                    .spawn(this.world);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SRPSounds.INFECTEDCOW_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SRPSounds.INFECTEDCOW_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SRPSounds.INFECTEDCOW_DEATH;
    }

    @Override
    protected float getSoundPitch() {
        return (this.rand.nextFloat() - this.rand.nextFloat()) * 0.2F + 0.5F;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.block.Block blockIn) {
        this.playSound(SRPSounds.MONSTER_STEP, 0.15F, 1.0F);
    }
}
