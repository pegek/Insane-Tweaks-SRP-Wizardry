package com.spege.insanetweaks.items.nunchaku;

import com.github.alexthe666.iceandfire.integration.CompatLoadUtil;
import com.mujmajnkraft.bettersurvival.items.ItemNunchaku;

import net.minecraft.entity.EntityLivingBase;
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
 */
public class ItemDragonsteelNunchaku extends ItemNunchaku implements DragonsteelHitEffect {

    public ItemDragonsteelNunchaku(Item.ToolMaterial material) {
        super(material);
    }

    /**
     * Dokładnie to, co robi {@code ItemDragonsteelSword}. Bramka na RLCombat jest skopiowana
     * świadomie: pod RLCombat żadna broń dragonsteel nie odpala efektów statusowych (robi to
     * tylko modyfikator obrażeń w RLCombatCompat), więc kopiując bramkę nie rozjeżdżamy się
     * z resztą tieru.
     */
    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase target, EntityLivingBase attacker) {
        if (!CompatLoadUtil.isRLCombatLoaded()) {
            doHitEffect(target, attacker);
        }
        return super.hitEntity(stack, target, attacker);
    }
}
