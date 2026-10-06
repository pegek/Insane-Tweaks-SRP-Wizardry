package com.spege.insanetweaks.entities.magic;

import java.util.UUID;

import com.dhanantry.scapeandrunparasites.entity.monster.primitive.EntityShyco;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.ai.EntityAIBlinkStrike;

import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Arcane Blinker: SRP's primitive longarms with Blink Strike (telegraphed teleport behind its
 * target, the next hit within 2 s is stronger) and Phase Dodge (a projectile may make it blink
 * sideways instead of landing). Native shockwave / launch skills are untouched.
 */
public class EntityMagicLongarms extends EntityShyco implements IMagicParasite {

    private static final DataParameter<Byte> ARCANE_STATE = EntityDataManager.createKey(EntityMagicLongarms.class,
            DataSerializers.BYTE);
    private static final UUID BLINK_BONUS_ID = UUID.fromString("5f3c9a1e-7b2d-4c8e-9a61-2d4f8b0c6e13");
    /** Op 2 multiplies the final value: 0.3 = +30%. */
    private static final int OP_MULTIPLY_TOTAL = 2;

    // No initialisers: initEntityAI runs from the superclass constructor, before field initialisers.
    private long blinkBonusUntil;
    private long nextDodgeTime;

    public EntityMagicLongarms(World world) {
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
        this.tasks.addTask(1, new EntityAIBlinkStrike(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.MAX_HEALTH, c.blinkerHealthMultiplier);
        MagicParasiteTraits.multiplyBase(this, SharedMonsterAttributes.ATTACK_DAMAGE, c.blinkerDamageMultiplier);
    }

    @Override
    public byte getArcaneState() {
        return this.dataManager.get(ARCANE_STATE).byteValue();
    }

    @Override
    public void setArcaneState(byte state) {
        this.dataManager.set(ARCANE_STATE, Byte.valueOf(state));
    }

    /** Called by Blink Strike after a successful teleport. */
    public void grantBlinkBonus(long until) {
        IAttributeInstance damage = this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        damage.removeModifier(BLINK_BONUS_ID);
        damage.applyModifier(new AttributeModifier(BLINK_BONUS_ID, "Blink Strike",
                MagicParasiteTraits.cfg().blinkBonusDamage, OP_MULTIPLY_TOTAL));
        this.blinkBonusUntil = until;
    }

    private void clearBlinkBonus() {
        this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).removeModifier(BLINK_BONUS_ID);
        this.blinkBonusUntil = 0L;
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (this.world.isRemote) {
            MagicParasiteTraits.clientAura(this, getArcaneState() != STATE_IDLE);
        } else if (this.world.getTotalWorldTime() > this.blinkBonusUntil
                && this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getModifier(BLINK_BONUS_ID) != null) {
            // Also clears a modifier that was saved mid-window and reloaded (blinkBonusUntil is 0 then).
            clearBlinkBonus();
        }
    }

    @Override
    public boolean attackEntityAsMob(Entity target) {
        boolean hit = super.attackEntityAsMob(target);
        if (hit && this.blinkBonusUntil != 0L) {
            clearBlinkBonus();
        }
        return hit;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (!this.world.isRemote && tryPhaseDodge(source)) {
            return false;
        }
        return super.attackEntityFrom(source, MagicParasiteTraits.scaleIncoming(source, amount));
    }

    private boolean tryPhaseDodge(DamageSource source) {
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        if (!c.enableMagicParasites || !source.isProjectile() || this.isEntityInvulnerable(source)) {
            return false;
        }
        long now = this.world.getTotalWorldTime();
        if (now < this.nextDodgeTime || this.rand.nextDouble() >= c.dodgeChance) {
            return false;
        }
        Entity projectile = source.getImmediateSource();
        double mx = projectile != null ? projectile.motionX : 0.0D;
        double mz = projectile != null ? projectile.motionZ : 0.0D;
        double distance = BlinkPlanner.lerp(3.0D, 5.0D, this.rand.nextDouble());
        double[][] offsets = BlinkPlanner.sidestep(mx, mz, distance, this.rand.nextBoolean(), this.rotationYaw);
        if (!MagicParasiteTraits.tryTeleport(this, this.posX, this.posY, this.posZ, offsets)) {
            return false;
        }
        this.nextDodgeTime = now + c.dodgeCooldownSeconds * 20L;
        return true;
    }
}
