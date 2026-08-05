package com.spege.insanetweaks.items.nunchaku;

import java.util.Map;
import java.util.WeakHashMap;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.GearCategory;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;

/**
 * Nakładanie pasożytniczych efektów przez nunchaku, wraz z hamulcem.
 *
 * <p>🚨 CAŁY BALANS TEJ BRONI SIEDZI TUTAJ:
 * <ul>
 *   <li><b>Viral</b> ({@code VIRA_E}) to mnożnik obrażeń OTRZYMYWANYCH — {@code Viral Multiplier
 *       = 0.5} w configu SRP znaczy +50% na amplifier. Nakłada się na obrażenia z KAŻDEGO źródła,
 *       więc ta broń podbija cały arsenał gracza, nie tylko siebie.</li>
 *   <li><b>Needler</b> ({@code DLER_E}) przy {@code Needler Terminal Amplifier = 7} wysadza ofiarę
 *       za {@code Needler Damage = 0.4} jej CAŁKOWITEGO HP. Im grubszy boss, tym silniejszy.</li>
 *   <li>Nunchaku uderza 3,14 raz/s.</li>
 * </ul>
 *
 * <p>Hamulcem jest {@link #onCooldown} — nakładamy co N ticków NA CEL, nie co trafienie. Bez tego
 * szybkość broni staje się szybkością nakładania i reszta balansu przestaje mieć znaczenie.
 *
 * <p>SRP nie ma helpera inkrementującego amplifier (sprawdzone: nakłada zwykłym
 * {@code new PotionEffect(...)}), więc wspinaczkę piszemy sami — i dzięki temu w pełni ją
 * kontrolujemy sufitem z {@link ParasiteTier}.
 */
public final class ParasiteNunchakuEffects {

    /**
     * Ostatni tick, w którym nakładaliśmy coś danej ofierze.
     *
     * <p>{@code WeakHashMap} celowo: klucze to żywe encje, a wpisy mają zniknąć razem z nimi.
     * Zwykła mapa trzymałaby przy życiu każdego zabitego moba do końca sesji.
     *
     * <p>Dotykana wyłącznie z wątku serwera (guard {@code !world.isRemote} w
     * {@code ItemParasiteNunchaku.hitEntity}), więc bez synchronizacji.
     */
    private static final Map<EntityLivingBase, Long> LAST_APPLIED = new WeakHashMap<>();

    private ParasiteNunchakuEffects() {
    }

    /**
     * Nakłada efekty tieru na cel, o ile hamulec pozwala.
     *
     * <p>Wołane wyłącznie po stronie serwera i wyłącznie z {@code hitEntity}.
     */
    public static void applyOnHit(EntityLivingBase target, ParasiteTier tier) {
        GearCategory.Nunchaku cfg = ModConfig.gear.nunchaku;

        if (onCooldown(target, cfg.parasiteEffectCooldownTicks)) {
            return;
        }

        boolean appliedAnything = false;

        if (cfg.parasiteViralChance > 0.0D
                && target.world.rand.nextDouble() < cfg.parasiteViralChance) {
            climb(target, SRPPotions.VIRA_E, tier.getViralMaxAmplifier(),
                    cfg.parasiteViralDurationTicks);
            appliedAnything = true;
        }

        if (tier.appliesNeedler() && cfg.parasiteNeedlerChance > 0.0D
                && target.world.rand.nextDouble() < cfg.parasiteNeedlerChance) {
            climb(target, SRPPotions.DLER_E, cfg.parasiteNeedlerMaxAmplifier,
                    cfg.parasiteNeedlerDurationTicks);
            appliedAnything = true;
        }

        // Cooldown startuje tylko po FAKTYCZNYM nalozeniu. Inaczej nieudany rzut koscmi blokowalby
        // kolejne proby i realna czestotliwosc spadlaby ponizej tego, co mowi config.
        if (appliedAnything) {
            LAST_APPLIED.put(target, Long.valueOf(target.world.getTotalWorldTime()));
        }
    }

    /** @return true, jeśli tej ofierze nakładaliśmy coś mniej niż {@code cooldownTicks} temu. */
    private static boolean onCooldown(EntityLivingBase target, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return false;
        }
        Long last = LAST_APPLIED.get(target);
        if (last == null) {
            return false;
        }
        long now = target.world.getTotalWorldTime();
        // Odejmowanie w te strone przezywa przewiniecie czasu swiata w tyl (komenda /time set).
        return now - last.longValue() < cooldownTicks;
    }

    /**
     * Podnosi amplifier efektu o 1, nie wyżej niż {@code maxAmplifier}.
     *
     * <p>Waniliowe {@code addPotionEffect} IGNORUJE nałożenie o niższym amplifierze, a przy równym
     * tylko przedłuża. Dlatego przy suficie odświeżamy czas trwania jawnie, zamiast liczyć na to,
     * że silnik zrobi to za nas.
     */
    private static void climb(EntityLivingBase target, Potion potion, int maxAmplifier,
            int durationTicks) {
        if (potion == null || maxAmplifier < 0) {
            return;
        }
        PotionEffect current = target.getActivePotionEffect(potion);
        int nextAmplifier = current == null ? 0 : Math.min(current.getAmplifier() + 1, maxAmplifier);
        // ambient=false, showParticles=true - gracz MA widziec, ze cos na nim siedzi.
        PotionEffect effect = new PotionEffect(potion, durationTicks, nextAmplifier, false, true);

        if (!ModConfig.interactions.enableParasiteNunchakuImmunityBypass) {
            target.addPotionEffect(effect);
            return;
        }

        // Pasozyty sa odporne na VIRA_E i DLER_E - czyli dokladnie na to, czym ta bron wojuje.
        // Furtka podnosi sie TYLKO na czas tego jednego wywolania; uzasadnienie w
        // ParasiteEffectBypass i w MixinEntityParasiteBase.
        // 🚨 try/finally jest OBOWIAZKOWE: wyjatek przy podniesionej fladze zostawilby ja
        // podniesiona na stale dla tego watku i zdjal odpornosc calemu SRParasites.
        ParasiteEffectBypass.begin();
        try {
            target.addPotionEffect(effect);
        } finally {
            ParasiteEffectBypass.end();
        }
    }
}
