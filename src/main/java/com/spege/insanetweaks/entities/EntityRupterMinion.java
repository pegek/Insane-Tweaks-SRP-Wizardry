package com.spege.insanetweaks.entities;

import javax.annotation.Nonnull;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.dhanantry.scapeandrunparasites.init.SRPSounds;
import com.dhanantry.scapeandrunparasites.util.SRPAttributes;
import com.spege.insanetweaks.entities.ai.SummonTargetingHelper;

import electroblob.wizardry.entity.living.EntitySummonedCreature;
import electroblob.wizardry.util.ParticleBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILeapAtTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class EntityRupterMinion extends EntitySummonedCreature {

    private EntityLivingBase summonerAnchor;

    public EntityRupterMinion(World world) {
        super(world);
        this.setSize(0.85F, 1.0F);
        this.experienceValue = 0;
    }

    public void setSummonerAnchor(EntityLivingBase summonerAnchor) {
        this.summonerAnchor = summonerAnchor;
    }

    @Override
    protected void initEntityAI() {
        this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, true));
        this.targetTasks.addTask(2, new EntityAINearestAttackableTarget<EntityMob>(this, EntityMob.class, true));
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(2, new EntityAILeapAtTarget(this, 0.4F));
        this.tasks.addTask(3, new EntityAIAttackMelee(this, 1.35D, false));
        this.tasks.addTask(8, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.setBaseAttribute(SharedMonsterAttributes.MAX_HEALTH, SRPAttributes.MUDO_HEALTH);
        this.setBaseAttribute(SharedMonsterAttributes.ARMOR, SRPAttributes.MUDO_ARMOR);
        this.setBaseAttribute(SharedMonsterAttributes.MOVEMENT_SPEED, 0.33D);
        this.setBaseAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE, SRPAttributes.MUDO_KD_RESISTANCE);
        this.setBaseAttribute(SharedMonsterAttributes.ATTACK_DAMAGE, SRPAttributes.MUDO_ATTACK_DAMAGE);
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

        if (!this.world.isRemote) {
            this.refreshParasiteRepelProtection();
            SummonTargetingHelper.syncCasterPriorityTarget(this);
        }

        if (!this.world.isRemote && this.getAttackTarget() == null) {
            EntityLivingBase anchor = this.summonerAnchor != null && this.summonerAnchor.isEntityAlive()
                    ? this.summonerAnchor
                    : this.getCaster();
            if (anchor != null && this.getDistanceSq(anchor) > 36.0D) {
                this.getNavigator().tryMoveToEntityLiving(anchor, 1.35D);
            }
        }
    }

    private void refreshParasiteRepelProtection() {
        PotionEffect repel = this.getActivePotionEffect(SRPPotions.EPEL_E);

        if (repel == null || repel.getDuration() <= 40) {
            this.addPotionEffect(new PotionEffect(SRPPotions.EPEL_E, 100, 0, false, false));
        }
    }

    @Override
    public boolean attackEntityAsMob(@Nonnull Entity entityIn) {
        float damage = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        boolean attacked = entityIn.attackEntityFrom(DamageSource.causeMobDamage(this), damage);

        if (attacked && entityIn instanceof EntityLivingBase) {
            this.applyEnchantments(this, entityIn);
            EntityLivingBase target = (EntityLivingBase) entityIn;
            SummonInfectionSafetyHelper.clearCoth(target);
            target.addPotionEffect(new PotionEffect(MobEffects.POISON, 60, 0, false, true));
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

        for (int i = 0; i < 10; i++) {
            ParticleBuilder.create(ParticleBuilder.Type.DARK_MAGIC)
                    .pos(this.posX + this.rand.nextFloat(), this.posY + this.rand.nextFloat(),
                            this.posZ + this.rand.nextFloat())
                    .clr(0.45F, 0.15F, 0.15F)
                    .spawn(this.world);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SRPSounds.MUDO_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SRPSounds.MUDO_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SRPSounds.MUDO_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.block.Block blockIn) {
        this.playSound(SRPSounds.SMALL_STEPS, 0.15F, 1.0F);
    }
}
