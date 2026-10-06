package com.spege.insanetweaks.entities.magic.ai;

import com.dhanantry.scapeandrunparasites.entity.ai.misc.EntityParasiteBase;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.magic.EntityMagicSummoner;
import com.spege.insanetweaks.entities.magic.MagicParasiteTraits;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;

/**
 * Ward Allies: once a second, the most hurt parasite in range below 50% health (and without
 * Absorption) gets Absorption II for 10 s, with a beam from the Conjurer. A one-shot task: all
 * work is in startExecuting, then it ends.
 */
public class EntityAIWardAllies extends EntityAIBase {

    private static final float HEALTH_TRIGGER = 0.5F;
    private static final int ABSORPTION_TICKS = 200;
    private static final int ABSORPTION_II = 1;

    private final EntityMagicSummoner caster;
    private long nextReady;
    private EntityParasiteBase chosen;

    public EntityAIWardAllies(EntityMagicSummoner caster) {
        this.caster = caster;
        this.setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        if (!MagicParasiteTraits.enabled() || this.caster.ticksExisted % 20 != 0
                || this.caster.world.getTotalWorldTime() < this.nextReady) {
            return false;
        }
        this.chosen = pickAlly();
        return this.chosen != null;
    }

    @Override
    public void startExecuting() {
        EntityParasiteBase ally = this.chosen;
        this.chosen = null;
        if (ally == null) {
            return;
        }
        ally.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, ABSORPTION_TICKS, ABSORPTION_II));
        MagicParasiteTraits.beam(this.caster.world, this.caster.posX, this.caster.posY + this.caster.height * 0.7D,
                this.caster.posZ, ally.posX, ally.posY + ally.height * 0.5D, ally.posZ);
        MagicParasiteTraits.sound(ally, SoundEvents.ENTITY_ILLAGER_CAST_SPELL, 1.5F);
        this.nextReady = this.caster.world.getTotalWorldTime()
                + MagicParasiteTraits.cfg().wardAlliesCooldownSeconds * 20L;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return false;
    }

    private EntityParasiteBase pickAlly() {
        EntitiesCategory.MagicParasites c = MagicParasiteTraits.cfg();
        double r2 = c.wardAlliesRadius * c.wardAlliesRadius;
        EntityParasiteBase best = null;
        float bestRatio = HEALTH_TRIGGER;
        for (EntityParasiteBase p : this.caster.world.getEntitiesWithinAABB(EntityParasiteBase.class,
                this.caster.getEntityBoundingBox().grow(c.wardAlliesRadius))) {
            if (p == this.caster || !p.isEntityAlive() || p.isPotionActive(MobEffects.ABSORPTION)
                    || this.caster.getDistanceSq(p) > r2) {
                continue;
            }
            float ratio = p.getHealth() / p.getMaxHealth();
            if (ratio < bestRatio) {
                bestRatio = ratio;
                best = p;
            }
        }
        return best;
    }
}
