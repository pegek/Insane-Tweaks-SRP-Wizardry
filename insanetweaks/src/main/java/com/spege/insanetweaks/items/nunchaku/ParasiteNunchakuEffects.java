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
 * Nakładanie pasożytniczych efektów przez nunchaku, wraz z hamulcami.
 *
 * <p>🚨 CAŁY BALANS TEJ BRONI SIEDZI TUTAJ:
 * <ul>
 *   <li><b>Bleeding</b> ({@code BLEED_E}) tnie {@code Bleeding Damage = 0.06} — 6% CAŁKOWITEGO HP
 *       ofiary — z limitem {@code Bleeding Damage Limit = 100.0}. Rośnie z amplifierem i jest
 *       gorsze, gdy ofiara się rusza. Im grubszy cel, tym mocniejsze: to jest ta własność, dla
 *       której bronią o niskich obrażeniach bazowych w ogóle warto bić dużego bossa.</li>
 *   <li><b>Indeaf</b> ({@code INDEAF_E}) to ROOT — kasuje sterowanie ruchem i ciągle zeruje pęd
 *       poziomy. Przy 3,14 ataku/s trzymanie go na wspólnym hamulcu dałoby zakorzenienie
 *       permanentne, dlatego ma WŁASNY, znacznie dłuższy cooldown.</li>
 * </ul>
 *
 * <p>Żaden z tych dwóch efektów NIE jest na liście odporności pasożytów (blokowane są tylko
 * {@code COTH_E}, {@code VIRA_E}, {@code CORRO_E}, {@code DLER_E}), więc działają na SRP
 * bez żadnych obejść.
 *
 * <p>SRP nie ma helpera inkrementującego amplifier (sprawdzone: nakłada zwykłym
 * {@code new PotionEffect(...)}), więc wspinaczkę piszemy sami — i dzięki temu w pełni ją
 * kontrolujemy sufitem z {@link ParasiteTier}.
 */
public final class ParasiteNunchakuEffects {

    /**
     * Ostatni tick nałożenia Bleeding na daną ofiarę.
     *
     * <p>{@code WeakHashMap} celowo: klucze to żywe encje, a wpisy mają zniknąć razem z nimi.
     * Zwykła mapa trzymałaby przy życiu każdego zabitego moba do końca sesji.
     *
     * <p>Dotykana wyłącznie z wątku serwera (guard {@code !world.isRemote} w
     * {@code ItemParasiteNunchaku.hitEntity}), więc bez synchronizacji.
     */
    private static final Map<EntityLivingBase, Long> LAST_BLEED = new WeakHashMap<>();

    /**
     * To samo dla Indeaf, ale OSOBNO.
     *
     * <p>🚨 Nie scalaj tych dwóch map. Root musi mieć dłuższy odstęp niż krwawienie; wspólny
     * licznik oznaczałby, że albo Bleeding nakłada się za rzadko, albo Indeaf za często —
     * a to drugie zamienia broń w permanentne unieruchomienie celu.
     */
    private static final Map<EntityLivingBase, Long> LAST_INDEAF = new WeakHashMap<>();

    private ParasiteNunchakuEffects() {
    }

    /**
     * Nakłada efekty tieru na cel, o ile hamulce pozwalają.
     *
     * <p>Wołane wyłącznie po stronie serwera i wyłącznie z {@code hitEntity}.
     */
    public static void applyOnHit(EntityLivingBase target, ParasiteTier tier) {
        GearCategory.Nunchaku cfg = ModConfig.gear.nunchaku;
        long now = target.world.getTotalWorldTime();

        if (cfg.parasiteBleedChance > 0.0D
                && !onCooldown(LAST_BLEED, target, now, cfg.parasiteEffectCooldownTicks)
                && target.world.rand.nextDouble() < cfg.parasiteBleedChance) {
            climb(target, SRPPotions.BLEED_E, tier.getBleedMaxAmplifier(),
                    cfg.parasiteBleedDurationTicks);
            LAST_BLEED.put(target, Long.valueOf(now));
        }

        if (tier.appliesIndeaf() && cfg.parasiteIndeafChance > 0.0D
                && !onCooldown(LAST_INDEAF, target, now, cfg.parasiteIndeafCooldownTicks)
                && target.world.rand.nextDouble() < cfg.parasiteIndeafChance) {
            // Amplifier ZAWSZE 0: Indeaf jest binarne (rusza sie / nie rusza), wiec wspinaczka
            // niczego by nie dala, a wydluzylaby tylko liste efektow na ofierze.
            apply(target, SRPPotions.INDEAF_E, 0, cfg.parasiteIndeafDurationTicks);
            LAST_INDEAF.put(target, Long.valueOf(now));
        }
    }

    /** @return true, jeśli tej ofierze nakładaliśmy dany efekt mniej niż {@code cooldown} temu. */
    private static boolean onCooldown(Map<EntityLivingBase, Long> log, EntityLivingBase target,
            long now, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return false;
        }
        Long last = log.get(target);
        if (last == null) {
            return false;
        }
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
        apply(target, potion, nextAmplifier, durationTicks);
    }

    /**
     * Samo nałożenie, z furtką na odporność.
     *
     * <p>Bleeding i Indeaf nie są na liście odporności pasożytów, więc furtka jest dziś martwa
     * i {@code interactions.enableParasiteNunchakuImmunityBypass} stoi domyślnie na OFF.
     * Zostawiamy ją gotową na wypadek powrotu do Virala albo Needlera — patrz
     * {@link ParasiteEffectBypass}.
     */
    private static void apply(EntityLivingBase target, Potion potion, int amplifier,
            int durationTicks) {
        if (potion == null) {
            return;
        }
        // ambient=false, showParticles=true - gracz MA widziec, ze cos na nim siedzi.
        PotionEffect effect = new PotionEffect(potion, durationTicks, amplifier, false, true);

        if (!ModConfig.interactions.enableParasiteNunchakuImmunityBypass) {
            target.addPotionEffect(effect);
            return;
        }

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
