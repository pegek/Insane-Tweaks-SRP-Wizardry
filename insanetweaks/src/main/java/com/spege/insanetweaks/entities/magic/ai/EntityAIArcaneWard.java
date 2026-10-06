package com.spege.insanetweaks.entities.magic.ai;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.EntityMagicManducater;
import com.spege.insanetweaks.entities.magic.IMagicParasite;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;

/**
 * Arcane Ward: when hurt below 70% or with a target within 10 blocks, telegraph for 0.5 s, then
 * hold the ward (STATE_WARD, which EntityMagicManducater.attackEntityFrom reads) for the configured
 * time. Resistance I is refreshed every second on nearby parasites (Bastion). The cooldown counts
 * from the end. Mutex 0, like the other abilities.
 */
public class EntityAIArcaneWard extends EntityAIBase {

    private static final int TELEGRAPH_TICKS = 10;
    private static final float HEALTH_TRIGGER = 0.7F;
    private static final double TARGET_TRIGGER_SQ = 10.0D * 10.0D;
    private static final int RING_POINTS = 12;

    private final EntityMagicManducater warden;
    private int ticks;
    private long nextReady;

    public EntityAIArcaneWard(EntityMagicManducater warden) {
        this.warden = warden;
        this.setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        if (!MagicParasiteTraits.enabled() || this.warden.world.getTotalWorldTime() < this.nextReady) {
            return false;
        }
        if (this.warden.getHealth() < this.warden.getMaxHealth() * HEALTH_TRIGGER) {
            return true;
        }
        EntityLivingBase target = this.warden.getAttackTarget();
        return target != null && target.isEntityAlive() && this.warden.getDistanceSq(target) <= TARGET_TRIGGER_SQ;
    }

    @Override
    public void startExecuting() {
        this.ticks = 0;
        this.warden.setArcaneState(IMagicParasite.STATE_TELEGRAPH);
        MagicParasiteTraits.sound(this.warden, SoundEvents.EVOCATION_ILLAGER_PREPARE_WOLOLO, 0.8F);
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.warden.isEntityAlive() && this.ticks < TELEGRAPH_TICKS + wardTicks();
    }

    @Override
    public void updateTask() {
        this.ticks++;
        if (this.ticks < TELEGRAPH_TICKS) {
            if (this.ticks % 2 == 0) {
                MagicParasiteTraits.telegraph(this.warden);
            }
            return;
        }
        if (this.ticks == TELEGRAPH_TICKS) {
            this.warden.setArcaneState(IMagicParasite.STATE_WARD);
            MagicParasiteTraits.sound(this.warden, SoundEvents.EVOCATION_ILLAGER_CAST_SPELL, 0.7F);
        }
        int warded = this.ticks - TELEGRAPH_TICKS;
        if (warded % 4 == 0) {
            ring();
        }
        if (warded % 20 == 0) {
            bastion();
        }
    }

    @Override
    public void resetTask() {
        this.warden.setArcaneState(IMagicParasite.STATE_IDLE);
        this.nextReady = this.warden.world.getTotalWorldTime() + MagicParasiteTraits.cfg().wardCooldownSeconds * 20L;
    }

    private static int wardTicks() {
        return MagicParasiteTraits.cfg().wardDurationSeconds * 20;
    }

    private void ring() {
        double radius = this.warden.width * 0.5D + 0.8D;
        double y = this.warden.posY + this.warden.height * 0.5D;
        double spin = this.ticks * 0.15D;
        for (int i = 0; i < RING_POINTS; i++) {
            double a = spin + Math.PI * 2.0D * i / RING_POINTS;
            MagicParasiteTraits.burst(this.warden.world, this.warden.posX + Math.cos(a) * radius, y,
                    this.warden.posZ + Math.sin(a) * radius, 1, 0.0D);
        }
    }

    private void bastion() {
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        if (c.bastionRadius <= 0.0D) {
            return;
        }
        double r2 = c.bastionRadius * c.bastionRadius;
        for (EntityParasiteBase ally : this.warden.world.getEntitiesWithinAABB(EntityParasiteBase.class,
                this.warden.getEntityBoundingBox().grow(c.bastionRadius))) {
            if (ally != this.warden && ally.isEntityAlive() && this.warden.getDistanceSq(ally) <= r2) {
                ally.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 30, 0, true, true));
            }
        }
    }
}
