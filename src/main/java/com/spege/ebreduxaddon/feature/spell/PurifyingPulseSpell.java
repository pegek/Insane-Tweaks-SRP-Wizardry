package com.spege.ebreduxaddon.feature.spell;

import com.binaris.wizardry.api.content.spell.Spell;
import com.binaris.wizardry.api.content.spell.SpellAction;
import com.binaris.wizardry.api.content.spell.SpellTypes;
import com.binaris.wizardry.api.content.spell.internal.PlayerCastContext;
import com.binaris.wizardry.api.content.spell.internal.SpellModifiers;
import com.binaris.wizardry.api.content.spell.properties.SpellProperties;
import com.binaris.wizardry.api.content.spell.properties.SpellProperty;
import com.binaris.wizardry.content.spell.DefaultProperties;
import com.binaris.wizardry.setup.registries.Elements;
import com.binaris.wizardry.setup.registries.SpellTiers;
import com.spege.ebreduxaddon.core.PulseShape;
import com.spege.ebreduxaddon.feature.entity.PurifyingWaveEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Purifying Pulse, z 1.12.2: w punkcie, w ktory patrzy rzucajacy (do {@code range} blokow), rusza
 * fala ({@link PurifyingWaveEntity}). Promien 8 blokow plus 2 za kazde pelne +45% potency, zasieg
 * w pionie polowa promienia, nie mniej niz 4 - wszystko jak na 1.12.2.
 *
 * <p>Patrzenie w niebo nie rzuca zaklecia (brak punktu), wiec nie zuzywa many ani cooldownu.
 */
public class PurifyingPulseSpell extends Spell {

    public static final SpellProperty<Integer> BASE_RADIUS = SpellProperty.intProperty("pulse_radius", 8);

    public PurifyingPulseSpell() {
        this.soundValues(0.8f, 1.25f, 0.1f);
    }

    @Override
    public boolean cast(PlayerCastContext ctx) {
        Player caster = ctx.caster();
        double range = ctx.modifiers().get(SpellModifiers.RANGE, property(DefaultProperties.RANGE));
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        BlockHitResult hit = ctx.world().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (!ctx.world().isClientSide) {
            float potency = ctx.modifiers().get(SpellModifiers.POTENCY);
            int radius = PulseShape.radius(property(BASE_RADIUS), potency);
            Vec3 at = hit.getLocation();
            PurifyingWaveEntity wave = new PurifyingWaveEntity(ctx.world());
            wave.setPos(at.x, at.y + 0.15, at.z);
            wave.configure(caster, radius, PulseShape.verticalRange(radius),
                    potency * property(DefaultProperties.HEALTH),
                    potency * property(DefaultProperties.DAMAGE),
                    property(DefaultProperties.KNOCKBACK));
            ctx.world().addFreshEntity(wave);
            ctx.world().playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS,
                    getVolume(), getPitch());
        }
        return true;
    }

    @Override
    public boolean requiresPacket() {
        return false;
    }

    @Override
    protected @NotNull SpellProperties properties() {
        return SpellProperties.builder()
                .assignBaseProperties(SpellTiers.MASTER, Elements.HEALING, SpellTypes.UTILITY, SpellAction.POINT, 150, 40, 1200)
                .add(DefaultProperties.RANGE, 24F)
                .add(DefaultProperties.HEALTH, 4F)
                .add(DefaultProperties.DAMAGE, 8F)
                .add(DefaultProperties.KNOCKBACK, 0.8F)
                .add(BASE_RADIUS)
                .build();
    }
}
