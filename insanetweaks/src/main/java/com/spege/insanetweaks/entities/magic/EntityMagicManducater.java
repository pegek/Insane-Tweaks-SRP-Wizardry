package com.spege.insanetweaks.entities.magic;

import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityHull;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.ai.EntityAIArcaneWard;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Arcane Warden: SRP's primitive manducater as a tank and support. Arcane Ward halves incoming
 * damage, blocks and returns projectiles, and gives nearby parasites Resistance (Bastion). The
 * native pull also slows the pulled entity (Anchor Pull).
 */
public class EntityMagicManducater extends EntityHull implements IMagicParasite {

    private static final DataParameter<Byte> ARCANE_STATE = EntityDataManager.createKey(EntityMagicManducater.class,
            DataSerializers.BYTE);
    /**
     * Vanilla's EntityArrow, when its hit is refused, multiplies its own motion by -0.1 after
     * attackEntityFrom returns. Pre-multiplying by 8 makes the net result a -0.8 reflection.
     */
    private static final double ARROW_PRE_REFLECT = 8.0D;
    private static final double REFLECT = -0.8D;

    private boolean wasPulling;

    public EntityMagicManducater(World world) {
        super(world);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(ARCANE_STATE, Byte.valueOf(STATE_IDLE));
    }

    @Override
    protected void initEntityAI() {
        super.initEntityAI();
        this.tasks.addTask(1, new EntityAIArcaneWard(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.MAX_HEALTH, c.wardenHealthMultiplier);
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.ARMOR, c.wardenArmorMultiplier);
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.ATTACK_DAMAGE, c.wardenDamageMultiplier);
    }

    @Override
    public byte getArcaneState() {
        return this.dataManager.get(ARCANE_STATE).byteValue();
    }

    @Override
    public void setArcaneState(byte state) {
        this.dataManager.set(ARCANE_STATE, Byte.valueOf(state));
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (this.world.isRemote) {
            MagicParasiteTraits.clientAura(this, getArcaneState() != STATE_IDLE);
            return;
        }
        boolean pulling = this.hasTargetedEntity();
        if (pulling && !this.wasPulling && MagicParasiteTraits.enabled()) {
            int ticks = MagicParasiteTraits.cfg().pullSlownessSeconds * 20;
            EntityLivingBase pulled = this.getTargetedEntity();
            if (pulled != null && ticks > 0) {
                pulled.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, ticks, 0));
            }
        }
        this.wasPulling = pulling;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        float scaled = amount;
        if (!this.world.isRemote && getArcaneState() == STATE_WARD && MagicParasiteTraits.enabled()) {
            if (source.isProjectile()) {
                reflect(source.getImmediateSource());
                return false;
            }
            scaled = (float) (scaled * MagicParasiteTraits.cfg().wardDamageTaken);
        }
        return super.attackEntityFrom(source, MagicParasiteTraits.scaleIncoming(source, scaled));
    }

    private void reflect(Entity projectile) {
        if (projectile == null) {
            return;
        }
        double factor = REFLECT;
        if (projectile instanceof EntityArrow) {
            ((EntityArrow) projectile).shootingEntity = this;
            factor = ARROW_PRE_REFLECT;
        }
        projectile.motionX *= factor;
        projectile.motionY *= factor;
        projectile.motionZ *= factor;
        projectile.velocityChanged = true;
        MagicParasiteTraits.burst(this.world, projectile.posX, projectile.posY, projectile.posZ, 8, 0.2D);
        MagicParasiteTraits.sound(this, SoundEvents.ITEM_SHIELD_BLOCK, 1.3F);
    }
}
