package com.spege.ebreduxaddon.feature.effect;

import com.binaris.wizardry.api.content.effect.CurseMobEffect;
import com.spege.ebreduxaddon.feature.ModEffects;
import com.spege.ebreduxaddon.platform.Config;
import com.spege.ebreduxaddon.platform.IdLists;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Cleansing: co {@code spells.cleanseTickInterval} tickow zdejmuje wszystkie efekty HARMFUL oraz te
 * z listy {@code spore.cleansedEffects}. Klatwy Redux zostaja, jak w jego CureEffects.
 *
 * <p>🚨 Zdejmowanie NIE dzieje sie w applyEffectTick. Vanilla woła je z wnetrza iteracji po mapie
 * aktywnych efektow (LivingEntity.tickEffects), a usuniecie innego efektu w tym miejscu konczy sie
 * ConcurrentModificationException. LivingTickEvent leci na poczatku LivingEntity.tick, przed ta
 * iteracja.
 */
public final class CleansingEffect extends MobEffect {

    public CleansingEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xAADDFF);
    }

    /** Nasluch na szynie Forge. Rejestrowany w EbreduxAddon. */
    public static final class Ticker {

        @SubscribeEvent
        public void onLivingTick(LivingEvent.LivingTickEvent event) {
            LivingEntity entity = event.getEntity();
            if (entity.level().isClientSide || !entity.hasEffect(ModEffects.CLEANSING.get())) {
                return;
            }
            if (entity.tickCount % Config.INSTANCE.cleanseTickInterval.get() != 0) {
                return;
            }
            cleanse(entity);
        }
    }

    /** Zdejmuje to, co zdejmuje efekt. Wspolne z Last Stand. Zwraca, czy cokolwiek zdjeto. */
    public static boolean cleanse(LivingEntity entity) {
        Set<MobEffect> extra = IdLists.cleansedEffects();
        List<MobEffect> doomed = new ArrayList<>();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            MobEffect effect = instance.getEffect();
            if (effect instanceof CurseMobEffect) {
                continue;
            }
            if (effect.getCategory() == MobEffectCategory.HARMFUL || extra.contains(effect)) {
                doomed.add(effect);
            }
        }
        for (MobEffect effect : doomed) {
            entity.removeEffect(effect);
        }
        return !doomed.isEmpty();
    }
}
