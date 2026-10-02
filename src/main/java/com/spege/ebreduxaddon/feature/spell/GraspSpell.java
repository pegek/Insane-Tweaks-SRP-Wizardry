package com.spege.ebreduxaddon.feature.spell;

import com.binaris.wizardry.api.client.ParticleBuilder;
import com.binaris.wizardry.api.content.spell.SpellAction;
import com.binaris.wizardry.api.content.spell.SpellTypes;
import com.binaris.wizardry.api.content.spell.internal.CastContext;
import com.binaris.wizardry.api.content.spell.internal.PlayerCastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.binaris.wizardry.api.content.spell.properties.SpellProperties;
import com.binaris.wizardry.api.content.spell.properties.SpellProperty;
import com.binaris.wizardry.api.content.util.EntityUtil;
import com.binaris.wizardry.api.content.util.MagicDamageSource;
import com.binaris.wizardry.content.spell.DefaultProperties;
import com.binaris.wizardry.content.spell.abstr.RaySpell;
import com.binaris.wizardry.core.AllyDesignation;
import com.binaris.wizardry.setup.registries.EBDamageSources;
import com.binaris.wizardry.setup.registries.Elements;
import com.binaris.wizardry.setup.registries.SpellTiers;
import com.binaris.wizardry.setup.registries.client.EBParticles;
import com.spege.ebreduxaddon.core.GraspRules;
import com.spege.ebreduxaddon.feature.GraspState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Grasp, z 1.12.2 (Dispatcher Grasp): ciagly promien. Pierwszy trafiony zywy cel zostaje
 * chwycony - od tej chwili zaklecie nie celuje, tylko trzyma (stan w {@link GraspState}). Cel i
 * rzucajacy stoja; co 10 tickow obrazenia; mob ponizej {@code execute_threshold} HP ginie normalna
 * sciezka obrazen (zeby zagraly animacje smierci i lup). Gracze nie sa egzekwowani.
 *
 * <p>Na 1.12.2 stanem byla encja szponu na ofierze. Tu, bez wlasnego modelu, stan jest mapa po
 * stronie serwera, a widac go po czasteczkach.
 */
public class GraspSpell extends RaySpell {

    public static final SpellProperty<Float> EXECUTE_THRESHOLD = SpellProperty.floatProperty("execute_threshold", 0.2F);
    /** Luz zasiegu: chwyt puszcza dopiero przy 1,5 x range. */
    private static final double REACH_SLACK = 1.5;

    public GraspSpell() {
        this.particleSpacing(0.3);
        this.particleJitter(0.04);
        this.soundValues(0.8f, 0.6f, 0.1f);
    }

    @Override
    public boolean isInstantCast() {
        return false;
    }

    @Override
    public boolean canCastByLocation() {
        return false;
    }

    @Override
    public boolean cast(PlayerCastContext ctx) {
        if (!ctx.world().isClientSide && continueHold(ctx)) {
            return true;
        }
        return super.cast(ctx);
    }

    /** Trwajacy chwyt: zwraca true, gdy tick zostal obsluzony bez celowania. */
    private boolean continueHold(CastContext ctx) {
        LivingEntity caster = ctx.caster();
        GraspState.Hold hold = GraspState.get(caster);
        if (hold == null) {
            return false;
        }
        LivingEntity target = hold.target();
        double range = ctx.modifiers().get(SpellModifiers.RANGE, property(DefaultProperties.RANGE));
        if (!target.isAlive() || target.level() != caster.level()
                || !GraspRules.inReach(caster.distanceToSqr(target), range, REACH_SLACK)) {
            GraspState.release(caster);
            return false;
        }
        holdTick(ctx, target);
        return true;
    }

    @Override
    protected boolean onEntityHit(CastContext ctx, EntityHitResult entityHit, Vec3 origin) {
        if (!(entityHit.getEntity() instanceof LivingEntity target) || ctx.caster() == null) {
            return false;
        }
        if (!AllyDesignation.isValidTarget(ctx.caster(), target)) {
            return false;
        }
        if (!ctx.world().isClientSide) {
            holdTick(ctx, target);
        }
        return true;
    }

    private void holdTick(CastContext ctx, LivingEntity target) {
        LivingEntity caster = ctx.caster();
        GraspState.hold(caster, target, ctx.world().getGameTime());
        if (ctx.castingTicks() % 10 != 0) {
            return;
        }
        DamageSource source = MagicDamageSource.causeDirectMagicDamage(caster, EBDamageSources.WITHER);
        if (GraspRules.shouldExecute(target instanceof Player, target.getHealth(), target.getMaxHealth(),
                property(EXECUTE_THRESHOLD))) {
            // Normalna sciezka obrazen, nie kill(): animacja smierci, lup i statystyki jak przy zabiciu.
            target.invulnerableTime = 0;
            target.hurt(source, target.getHealth() + target.getAbsorptionAmount() + 1000f);
            GraspState.release(caster);
            return;
        }
        float damage = ctx.modifiers().get(SpellModifiers.POTENCY, property(DefaultProperties.DAMAGE));
        EntityUtil.attackEntityWithoutKnockback(target, source, damage);
    }

    @Override
    protected boolean onBlockHit(CastContext ctx, BlockHitResult blockHit, Vec3 origin) {
        return false;
    }

    /** Pudlo nie przerywa kanalu: mozna przeciagnac promien po celu. */
    @Override
    protected boolean onMiss(CastContext ctx, Vec3 origin, Vec3 direction) {
        return true;
    }

    @Override
    public void endCast(CastContext ctx) {
        if (ctx.caster() != null && !ctx.world().isClientSide) {
            GraspState.release(ctx.caster());
        }
    }

    @Override
    protected void spawnParticle(CastContext ctx, double x, double y, double z, double vx, double vy, double vz) {
        ParticleBuilder.create(EBParticles.DARK_MAGIC).pos(x, y, z).color(0.25f, 0.05f, 0.3f).spawn(ctx.world());
    }

    @Override
    protected void playSound(Level world, LivingEntity entity, int castTicks, int duration) {
        if (castTicks % 20 == 0) {
            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.PLAYERS, getVolume(), getPitch());
        }
    }

    @Override
    protected @NotNull SpellProperties properties() {
        return SpellProperties.builder()
                .assignBaseProperties(SpellTiers.MASTER, Elements.NECROMANCY, SpellTypes.ATTACK, SpellAction.POINT, 8, 0, 100)
                .add(DefaultProperties.RANGE, 10F)
                .add(DefaultProperties.DAMAGE, 4F)
                .add(EXECUTE_THRESHOLD)
                .build();
    }
}
