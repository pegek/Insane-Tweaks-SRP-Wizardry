package com.spege.insanetweaks.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EntityAreaEffectCloud;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class EntitySummonerVomitCloud extends EntityAreaEffectCloud {

    private static final int REAPPLICATION_DELAY = 20;
    private final List<PotionEffect> insanetweaksEffects = new ArrayList<PotionEffect>();
    private final Map<EntityLivingBase, Integer> insanetweaksReapplicationDelayMap = new HashMap<EntityLivingBase, Integer>();

    public EntitySummonerVomitCloud(World world) {
        super(world);
    }

    public EntitySummonerVomitCloud(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Override
    public void addEffect(PotionEffect effect) {
        this.insanetweaksEffects.add(new PotionEffect(effect));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (this.world.isRemote) {
            this.spawnVisualParticles();
            return;
        }

        if (this.isDead || this.insanetweaksEffects.isEmpty() || this.ticksExisted % 5 != 0) {
            return;
        }

        float radius = this.getRadius();
        if (radius <= 0.0F) {
            return;
        }

        this.cleanupReapplicationMap();

        AxisAlignedBB area = this.getEntityBoundingBox().grow(radius);
        List<EntityLivingBase> targets = this.world.getEntitiesWithinAABB(EntityLivingBase.class, area);
        EntityLivingBase owner = this.getOwner();
        double radiusSq = radius * radius;

        for (EntityLivingBase target : targets) {
            if (target == null || !target.isEntityAlive()) {
                continue;
            }

            if (owner != null && (target == owner || owner.isOnSameTeam(target))) {
                continue;
            }

            Integer nextAllowedTick = this.insanetweaksReapplicationDelayMap.get(target);
            if (nextAllowedTick != null && nextAllowedTick.intValue() > this.ticksExisted) {
                continue;
            }

            if (target.getDistanceSq(this.posX, this.posY, this.posZ) > radiusSq) {
                continue;
            }

            for (PotionEffect effect : this.insanetweaksEffects) {
                target.addPotionEffect(new PotionEffect(effect));
            }

            this.insanetweaksReapplicationDelayMap.put(target, Integer.valueOf(this.ticksExisted + REAPPLICATION_DELAY));
        }
    }

    private void spawnVisualParticles() {
        float radius = this.getRadius();
        if (radius <= 0.0F) {
            return;
        }

        for (int i = 0; i < 18; i++) {
            double angle = this.rand.nextDouble() * Math.PI * 2.0D;
            double distance = this.rand.nextDouble() * radius;
            double x = this.posX + Math.cos(angle) * distance;
            double y = this.posY + 0.05D + this.rand.nextDouble() * 0.4D;
            double z = this.posZ + Math.sin(angle) * distance;

            this.world.spawnParticle(EnumParticleTypes.SPELL_MOB, x, y, z, 0.32D, 0.62D, 0.18D);
            this.world.spawnParticle(EnumParticleTypes.SPELL_MOB, x, y + 0.03D, z, 0.32D, 0.62D, 0.18D);
            if (this.rand.nextBoolean()) {
                this.world.spawnParticle(EnumParticleTypes.SLIME, x, y, z,
                        (this.rand.nextDouble() - 0.5D) * 0.04D, 0.01D + this.rand.nextDouble() * 0.02D,
                        (this.rand.nextDouble() - 0.5D) * 0.04D);
            }
        }
    }

    private void cleanupReapplicationMap() {
        List<EntityLivingBase> expired = new ArrayList<EntityLivingBase>();

        for (Map.Entry<EntityLivingBase, Integer> entry : this.insanetweaksReapplicationDelayMap.entrySet()) {
            EntityLivingBase target = entry.getKey();
            Integer tick = entry.getValue();

            if (target == null || !target.isEntityAlive() || tick == null || tick.intValue() <= this.ticksExisted) {
                expired.add(target);
            }
        }

        for (EntityLivingBase target : expired) {
            this.insanetweaksReapplicationDelayMap.remove(target);
        }
    }
}
