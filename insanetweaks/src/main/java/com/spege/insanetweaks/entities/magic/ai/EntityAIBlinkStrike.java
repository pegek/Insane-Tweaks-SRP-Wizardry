package com.spege.insanetweaks.entities.magic.ai;

import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.BlinkPlanner;
import com.spege.insanetweaks.entities.magic.EntityMagicLongarms;
import com.spege.insanetweaks.entities.magic.IMagicParasite;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.SoundEvents;

/**
 * Blink Strike: with a target 6-20 blocks away and in sight, telegraph for 0.75 s, then teleport
 * behind it (or beside it if behind is blocked). Mutex 0: it never claims the movement or look
 * slots, so SRP's own tasks keep running and the telegraph is a cue, not a freeze. Reads the
 * target, never picks one.
 */
public class EntityAIBlinkStrike extends EntityAIBase {

    private static final int TELEGRAPH_TICKS = 15;
    private static final long BONUS_WINDOW_TICKS = 40L;

    private final EntityMagicLongarms blinker;
    private int ticks;
    private long nextReady;

    public EntityAIBlinkStrike(EntityMagicLongarms blinker) {
        this.blinker = blinker;
        this.setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        if (!c.enableMagicParasites || this.blinker.world.getTotalWorldTime() < this.nextReady
                || !this.blinker.onGround) {
            return false;
        }
        EntityLivingBase target = this.blinker.getAttackTarget();
        if (target == null || !target.isEntityAlive()) {
            return false;
        }
        double distance = this.blinker.getDistance(target);
        return distance >= c.blinkMinRange && distance <= c.blinkMaxRange
                && this.blinker.getEntitySenses().canSee(target);
    }

    @Override
    public void startExecuting() {
        this.ticks = 0;
        this.blinker.setArcaneState(IMagicParasite.STATE_TELEGRAPH);
        this.blinker.getNavigator().clearPath();
        MagicParasiteTraits.sound(this.blinker, SoundEvents.EVOCATION_ILLAGER_PREPARE_ATTACK, 1.4F);
    }

    @Override
    public boolean shouldContinueExecuting() {
        EntityLivingBase target = this.blinker.getAttackTarget();
        return this.ticks < TELEGRAPH_TICKS && target != null && target.isEntityAlive();
    }

    @Override
    public void updateTask() {
        this.ticks++;
        EntityLivingBase target = this.blinker.getAttackTarget();
        if (target == null) {
            return;
        }
        this.blinker.getLookHelper().setLookPositionWithEntity(target, 30.0F, 30.0F);
        if (this.ticks % 3 == 0) {
            MagicParasiteTraits.telegraph(this.blinker);
        }
        if (this.ticks >= TELEGRAPH_TICKS) {
            blink(target);
        }
    }

    @Override
    public void resetTask() {
        this.blinker.setArcaneState(IMagicParasite.STATE_IDLE);
        this.nextReady = this.blinker.world.getTotalWorldTime() + MagicParasiteTraits.cfg().blinkCooldownSeconds * 20L;
    }

    private void blink(EntityLivingBase target) {
        double distance = BlinkPlanner.lerp(1.5D, 2.5D, this.blinker.getRNG().nextDouble());
        double[][] offsets = BlinkPlanner.behind(target.rotationYaw, distance);
        if (MagicParasiteTraits.tryTeleport(this.blinker, target.posX, target.posY, target.posZ, offsets)) {
            this.blinker.faceEntity(target, 360.0F, 360.0F);
            this.blinker.grantBlinkBonus(this.blinker.world.getTotalWorldTime() + BONUS_WINDOW_TICKS);
        }
    }
}
