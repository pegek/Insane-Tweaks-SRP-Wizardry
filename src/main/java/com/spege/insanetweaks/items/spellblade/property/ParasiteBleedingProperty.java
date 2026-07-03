package com.spege.insanetweaks.items.spellblade.property;

import com.dhanantry.scapeandrunparasites.init.SRPPotions;
import com.oblivioussp.spartanweaponry.api.ToolMaterialEx;
import com.oblivioussp.spartanweaponry.api.weaponproperty.WeaponPropertyWithCallback;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;

/**
 * Local re-implementation of swparasites {@code BleedingWeaponProperty}, so InsaneTweaks
 * spellblades no longer compile-depend on swparasites (SW: Parasites).
 *
 * Behaviour is a 1:1 copy of the original: on every hit, roll {@code level} times, each roll
 * a 25% chance to stack {@link SRPPotions#BLEED_E} on the target for 100 ticks. The bleed
 * effect itself comes from srparasites, which remains a hard dependency.
 *
 * The property {@code type} is kept as {@code "bleeding"} so the existing
 * {@code SpellbladeTooltipHandler} resolves the same tooltip name/colour as before.
 */
public class ParasiteBleedingProperty extends WeaponPropertyWithCallback {

    /** Chance per roll to apply a bleed stack (matches swparasites). */
    private static final double BLEED_CHANCE = 0.25D;
    /** Bleed duration in ticks (matches swparasites). */
    private static final int BLEED_DURATION = 100;

    public ParasiteBleedingProperty(int level) {
        // (type, modId, level, magnitude) - magnitude == level, exactly like the original.
        super("bleeding", "insanetweaks", level, (float) level);
    }

    @Override
    public void onHitEntity(ToolMaterialEx material, ItemStack stack, EntityLivingBase target,
            EntityLivingBase attacker, Entity projectile) {
        if (target == null || attacker == null || attacker.world == null || attacker.world.isRemote) {
            return;
        }
        for (int i = 0; i < this.getMagnitude(); i++) {
            if (attacker.world.rand.nextDouble() < BLEED_CHANCE) {
                SRPPotions.applyStackPotion((Potion) SRPPotions.BLEED_E, target, BLEED_DURATION, 0);
            }
        }
    }
}
