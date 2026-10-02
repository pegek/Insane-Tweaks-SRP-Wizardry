package com.spege.ebreduxaddon.feature.entity;

import com.binaris.wizardry.api.client.ParticleBuilder;
import com.binaris.wizardry.api.content.entity.projectile.MagicArrowEntity;
import com.binaris.wizardry.content.spell.DefaultProperties;
import com.binaris.wizardry.setup.registries.EBDamageSources;
import com.binaris.wizardry.setup.registries.EBSounds;
import com.binaris.wizardry.setup.registries.client.EBParticles;
import com.spege.ebreduxaddon.feature.ModEntities;
import com.spege.ebreduxaddon.feature.ModSpells;
import com.spege.ebreduxaddon.feature.spell.SpineVolleySpell;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Kolec Spine Volley. Pocisk Redux z grawitacja (MagicArrowEntity), trucizna przy trafieniu.
 * Kolec z flaga "puddle" zostawia przy pierwszym trafieniu chmure trucizny - to jest co n-ty rzut
 * z 1.12.2 (Yelloweye Gland).
 *
 * <p>Liczby czytane z wlasciwosci zaklecia (JSON), wiec strojenie datapackiem dziala tez na kolce
 * juz w locie.
 */
public class SpineEntity extends MagicArrowEntity {

    private static final String TAG_PUDDLE = "Puddle";

    private boolean puddle;

    public SpineEntity(EntityType<? extends SpineEntity> type, Level level) {
        super(type, level);
    }

    public SpineEntity(Level level) {
        this(ModEntities.SPINE.get(), level);
    }

    public void setPuddle(boolean puddle) {
        this.puddle = puddle;
    }

    public boolean isPuddle() {
        return puddle;
    }

    private static SpineVolleySpell spell() {
        return (SpineVolleySpell) ModSpells.SPINE_VOLLEY.get();
    }

    @Override
    public double getDamage(@NotNull EntityHitResult hitResult) {
        return spell().property(DefaultProperties.DAMAGE);
    }

    @Override
    public int getLifetime() {
        return -1;
    }

    @Override
    public ResourceKey<DamageType> getDamageType(@NotNull EntityHitResult hitResult) {
        return EBDamageSources.POISON;
    }

    @Override
    public @NotNull SoundEvent getSoundEvent(HitResult result) {
        return result.getType() == HitResult.Type.BLOCK ? EBSounds.ENTITY_DART_HIT_BLOCK.get() : EBSounds.ENTITY_DART_HIT.get();
    }

    @Override
    public void onHitTargetExtraEffects(@NotNull EntityHitResult hitResult) {
        if (hitResult.getEntity() instanceof LivingEntity target) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON,
                    spell().property(DefaultProperties.EFFECT_DURATION),
                    spell().property(DefaultProperties.EFFECT_STRENGTH)));
        }
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hitResult) {
        Vec3 at = hitResult.getEntity().position();
        super.onHitEntity(hitResult);
        spawnPuddle(at);
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);
        spawnPuddle(result.getLocation());
    }

    /** Jedna chmura na kolec, nawet jesli po trafieniu w moba przeleci dalej i trafi w blok. */
    private void spawnPuddle(Vec3 at) {
        if (!puddle || level().isClientSide) {
            return;
        }
        puddle = false;
        AreaEffectCloud cloud = new AreaEffectCloud(level(), at.x, at.y, at.z);
        if (getOwner() instanceof LivingEntity owner) {
            cloud.setOwner(owner);
        }
        cloud.setRadius(spell().property(SpineVolleySpell.PUDDLE_RADIUS));
        cloud.setRadiusOnUse(-0.25f);
        cloud.setWaitTime(5);
        cloud.setDuration(spell().property(SpineVolleySpell.PUDDLE_DURATION));
        cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
        cloud.setParticle(ParticleTypes.ITEM_SLIME);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 60, spell().property(DefaultProperties.EFFECT_STRENGTH)));
        level().addFreshEntity(cloud);
    }

    @Override
    public void ticksInAir() {
        if (level().isClientSide() && tickCount > 1) {
            ParticleBuilder.create(EBParticles.DUST, this).time(8 + random.nextInt(4))
                    .color(0.85f, 0.8f, 0.25f).spawn(level());
        }
    }

    @Override
    public void tickInGround() {
        if (ticksInGround > 40) {
            discard();
        }
    }

    @Override
    public boolean isValidTarget(@NotNull Entity entity) {
        // Salwa piec kolcow nie moze trafiac rzucajacego, nawet gdy stoi w wachlarzu.
        return entity != getOwner() && super.isValidTarget(entity);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_PUDDLE, puddle);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        puddle = tag.getBoolean(TAG_PUDDLE);
    }
}
