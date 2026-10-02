package com.spege.ebreduxaddon.feature.spell;

import com.binaris.wizardry.api.content.entity.projectile.MagicArrowEntity;
import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.api.content.spell.SpellAction;
import com.binaris.wizardry.api.content.spell.SpellTypes;
import com.binaris.wizardry.api.content.spell.internal.CastContext;
import com.binaris.wizardry.api.content.spell.internal.EntityCastContext;
import com.binaris.wizardry.api.content.spell.internal.PlayerCastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.binaris.wizardry.api.content.spell.properties.SpellProperties;
import com.binaris.wizardry.api.content.spell.properties.SpellProperty;
import com.binaris.wizardry.content.spell.DefaultProperties;
import com.binaris.wizardry.setup.registries.Elements;
import com.binaris.wizardry.setup.registries.SpellTiers;
import com.spege.ebreduxaddon.core.EveryNth;
import com.spege.ebreduxaddon.feature.entity.SpineEntity;
import com.spege.ebreduxaddon.platform.Config;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Spine Volley, z 1.12.2 (Yelloweye Gland): wachlarz kolcow. Co n-ty rzut tego samego rzucajacego
 * ({@code spells.spineVolleyPuddleEvery}) srodkowy kolec zostawia chmure trucizny.
 *
 * <p>Licznik serii jest w persistent data rzucajacego, nie w NBT rozdzki: zapis do trzymanego
 * stacka co rzut powodowalby animacje ponownego wyjecia przedmiotu na kliencie.
 */
public class SpineVolleySpell extends Spell {

    // Id wlasciwosci sa w Redux GLOBALNE (SpellProperty.fromID bierze pierwsze pasujace), wiec nazwy
    // nie moga sie pokrywac z cudzymi o innym typie. Sprawdzone z Redux 0.8.9: brak kolizji.
    public static final SpellProperty<Integer> SPINES = SpellProperty.intProperty("spines", 5);
    public static final SpellProperty<Float> SPREAD = SpellProperty.floatProperty("spread_degrees", 8F);
    public static final SpellProperty<Float> PUDDLE_RADIUS = SpellProperty.floatProperty("puddle_radius", 3F);
    public static final SpellProperty<Integer> PUDDLE_DURATION = SpellProperty.intProperty("puddle_duration", 60);

    static final String TAG_SERIES = "ebreduxaddon.spine_volley_series";

    public SpineVolleySpell() {
        this.soundValues(1.0f, 1.4f, 0.2f);
    }

    @Override
    public boolean canCastByEntity() {
        return true;
    }

    @Override
    public boolean cast(PlayerCastContext ctx) {
        return volley(ctx, ctx.caster(), null);
    }

    @Override
    public boolean cast(EntityCastContext ctx) {
        if (ctx.target() == null) {
            return false;
        }
        return volley(ctx, ctx.caster(), ctx.target());
    }

    private boolean volley(CastContext ctx, LivingEntity caster, net.minecraft.world.entity.Entity target) {
        Level level = ctx.world();
        if (!level.isClientSide) {
            boolean puddle = nextIsPuddle(caster);
            int count = Math.max(1, property(SPINES));
            float spread = property(SPREAD);
            float speed = velocity(ctx, caster);
            for (int i = 0; i < count; i++) {
                SpineEntity spine = new SpineEntity(level);
                if (target != null) {
                    spine.aim(caster, target, speed, 1f);
                } else {
                    spine.aim(caster, speed);
                }
                float offset = (i - (count - 1) / 2f) * spread;
                rotateYaw(spine, offset);
                spine.damageMultiplier = ctx.modifiers().getFactor(SpellModifiers.POTENCY);
                spine.setPuddle(puddle && i == count / 2);
                level.addFreshEntity(spine);
            }
        }
        playSound(level, caster, ctx.castingTicks(), -1);
        return true;
    }

    /** Kolejny krok serii. Zapisuje stan od razu, wiec rzut, ktory sie nie uda, i tak sie liczy. */
    static boolean nextIsPuddle(LivingEntity caster) {
        EveryNth step = EveryNth.step(caster.getPersistentData().getInt(TAG_SERIES),
                Config.INSTANCE.spineVolleyPuddleEvery.get());
        caster.getPersistentData().putInt(TAG_SERIES, step.counter());
        return step.fires();
    }

    /** Jak ArrowSpell.calculateVelocity dla pocisku z grawitacja: zasieg / czas spadku z wysokosci oczu. */
    private float velocity(CastContext ctx, LivingEntity caster) {
        float range = ctx.modifiers().get(SpellModifiers.RANGE, property(DefaultProperties.RANGE));
        float launchHeight = caster.getEyeHeight() - (float) MagicArrowEntity.LAUNCH_Y_OFFSET;
        return range / Mth.sqrt(2 * Math.max(0.1f, launchHeight) / 0.05f);
    }

    private static void rotateYaw(SpineEntity spine, float degrees) {
        if (degrees == 0f) {
            return;
        }
        double rad = Math.toRadians(degrees);
        Vec3 v = spine.getDeltaMovement();
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);
        Vec3 rotated = new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
        spine.setDeltaMovement(rotated);
        double horizontal = Math.sqrt(rotated.x * rotated.x + rotated.z * rotated.z);
        spine.setYRot((float) (Mth.atan2(rotated.x, rotated.z) * (180F / Math.PI)));
        spine.setXRot((float) (Mth.atan2(rotated.y, horizontal) * (180F / Math.PI)));
        spine.yRotO = spine.getYRot();
        spine.xRotO = spine.getXRot();
    }

    @Override
    protected void playSound(Level world, double x, double y, double z, int ticksInUse, int duration) {
        world.playSound(null, x, y, z, SoundEvents.LLAMA_SPIT, SoundSource.PLAYERS,
                getVolume(), getPitch() + getPitchVariation() * (world.random.nextFloat() - 0.5f));
    }

    @Override
    public boolean requiresPacket() {
        return false;
    }

    @Override
    protected @NotNull SpellProperties properties() {
        return SpellProperties.builder()
                .assignBaseProperties(SpellTiers.ADVANCED, Elements.EARTH, SpellTypes.ATTACK, SpellAction.POINT, 25, 10, 60)
                .add(DefaultProperties.DAMAGE, 3F)
                .add(DefaultProperties.RANGE, 20F)
                .add(DefaultProperties.EFFECT_DURATION, 100)
                .add(DefaultProperties.EFFECT_STRENGTH, 0)
                .add(SPINES)
                .add(SPREAD)
                .add(PUDDLE_RADIUS)
                .add(PUDDLE_DURATION)
                .build();
    }
}
