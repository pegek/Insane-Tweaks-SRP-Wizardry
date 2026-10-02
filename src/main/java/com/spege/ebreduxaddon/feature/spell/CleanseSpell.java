package com.spege.ebreduxaddon.feature.spell;

import com.binaris.wizardry.api.client.ParticleBuilder;
import com.binaris.wizardry.api.content.spell.SpellAction;
import com.binaris.wizardry.api.content.spell.SpellTypes;
import com.binaris.wizardry.api.content.spell.internal.CastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.binaris.wizardry.api.content.spell.properties.SpellProperties;
import com.binaris.wizardry.content.spell.DefaultProperties;
import com.binaris.wizardry.content.spell.abstr.RaySpell;
import com.binaris.wizardry.setup.registries.Elements;
import com.binaris.wizardry.setup.registries.SpellTiers;
import com.binaris.wizardry.setup.registries.client.EBParticles;
import com.spege.ebreduxaddon.feature.ModEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Cleanse, z 1.12.2 (SpellCleanse): promien. Trafiony zywy cel dostaje Cleansing; trafienie w blok
 * albo pudlo oczyszcza rzucajacego.
 *
 * <p>Trafienie w nie-zywa encje (rama, lodka) tez konczy promien, zamiast przeleciec do
 * oczyszczenia siebie - tak jak na 1.12.2.
 */
public class CleanseSpell extends RaySpell {

    public CleanseSpell() {
        this.soundValues(0.9f, 1.3f, 0.2f);
        this.particleSpacing(0.5);
        this.particleJitter(0.05);
    }

    @Override
    protected boolean onEntityHit(CastContext ctx, EntityHitResult entityHit, Vec3 origin) {
        if (entityHit.getEntity() instanceof LivingEntity target) {
            apply(ctx, target);
        }
        return true;
    }

    @Override
    protected boolean onBlockHit(CastContext ctx, BlockHitResult blockHit, Vec3 origin) {
        return selfCast(ctx);
    }

    @Override
    protected boolean onMiss(CastContext ctx, Vec3 origin, Vec3 direction) {
        return selfCast(ctx);
    }

    private boolean selfCast(CastContext ctx) {
        // Rzut z dozownika nie ma rzucajacego, ktorego mozna by oczyscic.
        if (ctx.caster() == null) {
            return false;
        }
        apply(ctx, ctx.caster());
        return true;
    }

    private void apply(CastContext ctx, LivingEntity target) {
        if (ctx.world().isClientSide) {
            for (int i = 0; i < 12; i++) {
                ParticleBuilder.create(EBParticles.SPARKLE, target).time(12 + ctx.world().random.nextInt(8))
                        .color(0.67f, 0.87f, 1.0f).spawn(ctx.world());
            }
            return;
        }
        int duration = (int) ctx.modifiers().get(SpellModifiers.DURATION, property(DefaultProperties.EFFECT_DURATION));
        target.addEffect(new MobEffectInstance(ModEffects.CLEANSING.get(), Math.max(1, duration), 0, false, true));
    }

    @Override
    protected void spawnParticle(CastContext ctx, double x, double y, double z, double vx, double vy, double vz) {
        ParticleBuilder.create(EBParticles.SPARKLE).pos(x, y, z).time(10 + ctx.world().random.nextInt(6))
                .color(0.67f, 0.87f, 1.0f).spawn(ctx.world());
    }

    /** Dzwiek vanilla: zaklecie nie rejestruje wlasnych SoundEventow (spell.ebreduxaddon.* nie istnieja). */
    @Override
    protected void playSound(Level world, double x, double y, double z, int ticksInUse, int duration) {
        world.playSound(null, x, y, z, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS,
                getVolume(), getPitch() + getPitchVariation() * (world.random.nextFloat() - 0.5f));
    }

    @Override
    protected @NotNull SpellProperties properties() {
        return SpellProperties.builder()
                .assignBaseProperties(SpellTiers.ADVANCED, Elements.HEALING, SpellTypes.DEFENCE, SpellAction.POINT, 60, 0, 200)
                .add(DefaultProperties.RANGE, 12F)
                .add(DefaultProperties.EFFECT_DURATION, 200)
                .build();
    }
}
