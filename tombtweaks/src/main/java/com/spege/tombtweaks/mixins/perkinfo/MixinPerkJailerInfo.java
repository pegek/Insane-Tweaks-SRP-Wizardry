package com.spege.tombtweaks.mixins.perkinfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Jailer's bonus line, in step with the chance the grave key actually rolls against.
 *
 * <p>The tooltip builds {@code chanceEnchantedGraveKey + level * 20} — the same expression
 * {@code ItemGraveKey.reenchantOnDeath} evaluates, down to the constant. Only int 20 in the method.
 */
@Mixin(targets = "ovh.corail.tombstone.perk.PerkJailer", remap = false)
public abstract class MixinPerkJailerInfo {

    @ModifyConstant(method = "getCurrentBonusInfo", constant = @Constant(intValue = 20), remap = false)
    private int tombtweaks$jailerInfoPerLevel(int original) {
        return PerkTuningHelper.jailerKeyChancePerLevel(original);
    }
}
