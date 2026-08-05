package com.spege.insanetweaks.items.nunchaku;

import java.util.Map;

import javax.annotation.Nonnull;

import com.github.alexthe666.iceandfire.integration.CompatLoadUtil;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mujmajnkraft.bettersurvival.items.ItemNunchaku;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.GearCategory;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import rldragonsteel.item.DragonsteelHitEffect;

/**
 * Dragonsteelowa wersja nunchaku z Better Survival.
 *
 * <p>Dziedziczenie po {@link ItemNunchaku} jest WYMUSZONE, nie wygodne: Better Survival rozpoznaje
 * nunchaku przez {@code instanceof ItemNunchaku} w czterech miejscach (ModClientHandler — spin,
 * CommonEventHandler dwa razy — spin i combo, EnchantmentSpecialBonus). Bez dziedziczenia broń się
 * nie kręci i nie zbiera combo.
 *
 * <p>{@code DragonsteelHitEffect.getMaterial()} ma tę samą sygnaturę co odziedziczone
 * {@code ItemCustomWeapon.getMaterial()}, więc interfejs jest spełniony bez pisania metody.
 *
 * <p>Strojenie statystyk siedzi w {@code gear.nunchaku}. Wytrzymałość wchodzi raz, w konstruktorze
 * (stan itemu, nie stacka — stąd restart), obrażenia i szybkość czytamy na żywo przy każdym
 * zapytaniu o modyfikatory.
 */
public class ItemDragonsteelNunchaku extends ItemNunchaku implements DragonsteelHitEffect {

    public ItemDragonsteelNunchaku(Item.ToolMaterial material) {
        super(material);
        // ItemCustomWeapon zawolal juz setMaxDamage(material.getMaxUses()) - my NADPISUJEMY ten
        // wynik, a nie dokladamy drugiej wartosci.
        double durabilityMultiplier = ModConfig.gear.nunchaku.durabilityMultiplier;
        setMaxDamage((int) Math.max(1.0D, material.getMaxUses() * durabilityMultiplier));
    }

    /**
     * Dokładnie to, co robi {@code ItemDragonsteelSword}. Bramka na RLCombat jest skopiowana
     * świadomie: pod RLCombat żadna broń dragonsteel nie odpala efektów statusowych (robi to
     * tylko modyfikator obrażeń w RLCombatCompat), więc kopiując bramkę nie rozjeżdżamy się
     * z resztą tieru.
     *
     * <p>Flagę z configu czytamy tu i teraz — ma działać bez restartu.
     */
    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        if (!CompatLoadUtil.isRLCombatLoaded()
                && ModConfig.interactions.enableDragonsteelHitEffects) {
            doHitEffect(target, attacker);
        }
        return super.hitEntity(stack, target, attacker);
    }

    /**
     * Przeskalowane obrażenia i szybkość ataku z {@code gear.nunchaku}.
     *
     * <p>Mapa z {@code ItemCustomWeapon} wraca przepisana, a nie zmodyfikowana w miejscu: nie mamy
     * gwarancji, że nadklasa zawsze odda coś mutowalnego. Przy mnożnikach 1.0 (i dla każdego slotu
     * poza ręką) oddajemy mapę nadklasy bez tknięcia — ścieżka domyślna nic nie alokuje.
     *
     * <p>UUID, nazwa i operacja każdego modyfikatora zostają — zmienia się wyłącznie wartość.
     * Podmiana UUID zerwałaby parowanie z modyfikatorem, który MC już nałożył na gracza.
     */
    @Override
    @Nonnull
    @SuppressWarnings("null")
    public Multimap<String, AttributeModifier> getAttributeModifiers(@Nonnull EntityEquipmentSlot slot,
            @Nonnull ItemStack stack) {
        Multimap<String, AttributeModifier> base = super.getAttributeModifiers(slot, stack);

        GearCategory.Nunchaku cfg = ModConfig.gear.nunchaku;
        double damageMultiplier = cfg.attackDamageMultiplier;
        double speedMultiplier = cfg.attackSpeedMultiplier;
        if (base == null || slot != EntityEquipmentSlot.MAINHAND
                || (damageMultiplier == 1.0D && speedMultiplier == 1.0D)) {
            return base;
        }

        String damageKey = SharedMonsterAttributes.ATTACK_DAMAGE.getName();
        String speedKey = SharedMonsterAttributes.ATTACK_SPEED.getName();
        Multimap<String, AttributeModifier> scaled = HashMultimap.create();
        for (Map.Entry<String, AttributeModifier> entry : base.entries()) {
            String key = entry.getKey();
            AttributeModifier modifier = entry.getValue();
            if (damageMultiplier != 1.0D && damageKey.equals(key)) {
                scaled.put(key, withAmount(modifier, modifier.getAmount() * damageMultiplier));
            } else if (speedMultiplier != 1.0D && speedKey.equals(key)) {
                scaled.put(key, withAmount(modifier, scaleAttackSpeed(modifier.getAmount(), speedMultiplier)));
            } else {
                scaled.put(key, modifier);
            }
        }
        return scaled;
    }

    /** Ten sam modyfikator z inną wartością. */
    private static AttributeModifier withAmount(AttributeModifier source, double amount) {
        return new AttributeModifier(source.getID(), source.getName(), amount, source.getOperation());
    }

    /**
     * Szybkość ataku NIE jest w MC liczbą ataków na sekundę — to ujemna różnica względem bazowych
     * 4.0/s (broń bijąca 2,4 raza na sekundę ma zapisane -1,6). Mnożnik ma dotyczyć prędkości
     * końcowej: przemnożenie samej różnicy sprawiłoby, że mnożnik &gt; 1.0 daje broń WOLNIEJSZĄ.
     * Stąd (baza + różnica) * mnożnik - baza.
     *
     * <p>Dolnego ograniczenia nie stawiamy: atrybut {@code generic.attackSpeed} ma własne minimum 0.
     */
    private static double scaleAttackSpeed(double delta, double multiplier) {
        double base = SharedMonsterAttributes.ATTACK_SPEED.getDefaultValue();
        return (base + delta) * multiplier - base;
    }
}
