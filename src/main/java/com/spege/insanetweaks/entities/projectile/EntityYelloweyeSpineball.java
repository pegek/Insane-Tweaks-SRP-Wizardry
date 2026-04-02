package com.spege.insanetweaks.entities.projectile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.dhanantry.scapeandrunparasites.util.config.SRPConfigMobs;
import com.dhanantry.scapeandrunparasites.util.config.SRPConfigSystems;
import com.spege.insanetweaks.entities.SummonInfectionSafetyHelper;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

@SuppressWarnings("null")
public class EntityYelloweyeSpineball extends EntityYelloweyeProjectileBase {

    private float damage;
    private int witherDurationTicks;
    private int witherAmplifier;
    private float potencyMultiplier = 1.0F;
    private double gearDamage;

    public EntityYelloweyeSpineball(World worldIn) {
        super(worldIn);
        this.setSize(0.5F, 0.5F);
    }

    public EntityYelloweyeSpineball(World worldIn, EntityLivingBase shooter, double accelX, double accelY,
            double accelZ, float projectileDamage) {
        super(worldIn, shooter, accelX, accelY, accelZ);
        this.setSize(0.5F, 0.5F);
        this.damage = projectileDamage;
    }

    public void applyPrimitiveYelloweyeDefaults() {
        this.setDurationAmplifier(SRPConfigMobs.emanaPoisonDuration, SRPConfigMobs.emanaPoisonAmplifier);
        this.setGearDamage(SRPConfigMobs.emanaGearD);
    }

    public void setDurationAmplifier(int durationSeconds, int amplifier) {
        this.witherDurationTicks = durationSeconds * 20;
        this.witherAmplifier = amplifier - 1;
    }

    public void setGearDamage(double gearDamage) {
        this.gearDamage = gearDamage;
    }

    public void setPotencyMultiplier(float potencyMultiplier) {
        this.potencyMultiplier = potencyMultiplier;
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        if (this.world.isRemote) {
            return;
        }

        EntityLivingBase target = this.findImpactTarget(result);
        EntityLivingBase shooter = this.shootingEntity;

        if (target != null) {
            DamageSource damageSource = shooter == null ? DamageSource.causeFireballDamage(this, this)
                    : DamageSource.causeFireballDamage(this, shooter);

            target.attackEntityFrom(damageSource, this.damage);
            SummonInfectionSafetyHelper.clearCoth(target);

            if (this.witherDurationTicks > 0) {
                int baseAmplifier = Math.max(0, this.witherAmplifier);
                int potencyBonus = this.getPotencyWitherBonus();
                target.addPotionEffect(new PotionEffect(MobEffects.WITHER, this.witherDurationTicks,
                        baseAmplifier + potencyBonus));
            }

            this.damageArmor(target, this.gearDamage);
        }

        this.setDead();
    }

    private EntityLivingBase findImpactTarget(RayTraceResult result) {
        if (result.entityHit instanceof EntityLivingBase) {
            EntityLivingBase directHit = (EntityLivingBase) result.entityHit;
            return this.isValidHitTarget(directHit) ? directHit : null;
        }

        Vec3d hit = result.hitVec != null ? result.hitVec : new Vec3d(this.posX, this.posY, this.posZ);
        AxisAlignedBB searchBox = new AxisAlignedBB(hit.x - 0.9D, hit.y - 0.9D, hit.z - 0.9D, hit.x + 0.9D,
                hit.y + 0.9D, hit.z + 0.9D);
        List<EntityLivingBase> targets = this.world.getEntitiesWithinAABB(EntityLivingBase.class, searchBox);
        EntityLivingBase closest = null;
        double closestDistance = Double.MAX_VALUE;

        for (EntityLivingBase candidate : targets) {
            if (!this.isValidHitTarget(candidate)) {
                continue;
            }

            double distance = candidate.getDistanceSq(hit.x, hit.y, hit.z);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    private boolean isValidHitTarget(EntityLivingBase target) {
        if (target == null || !target.isEntityAlive()) {
            return false;
        }

        EntityLivingBase shooter = this.shootingEntity;
        if (shooter != null && (target == shooter || shooter.isOnSameTeam(target))) {
            return false;
        }

        return true;
    }

    private int getPotencyWitherBonus() {
        float extraPotency = Math.max(0.0F, this.potencyMultiplier - 1.0F);
        int bonus = MathHelper.floor(extraPotency / 0.5F);
        return Math.min(2, bonus);
    }

    private void damageArmor(EntityLivingBase target, double percentage) {
        ArrayList<ItemStack> gear = new ArrayList<ItemStack>();
        Iterable<ItemStack> worn = target.getArmorInventoryList();

        if (worn == null || worn.equals(Collections.emptyList())) {
            return;
        }

        for (ItemStack part : worn) {
            if (!part.isEmpty() && part.isItemStackDamageable()) {
                gear.add(part);
            }
        }

        for (ItemStack part : gear) {
            if ((double) part.getMaxDamage() * SRPConfigSystems.corrNot
                    >= (double) (part.getMaxDamage() - part.getItemDamage())) {
                continue;
            }

            part.damageItem((int) ((double) part.getMaxDamage() * percentage), target);
        }
    }
}
