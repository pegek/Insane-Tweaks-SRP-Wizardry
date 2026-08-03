package com.spege.tombtweaks.mixins.perkinfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Gladiator's two bonus lines, in step with the damage swing.
 *
 * <p>The perk prints one line for damage dealt and one for damage taken, both off the same int 5,
 * where the effect uses a float 0.05 — so the value is converted back to a percentage. Two matches
 * in the method and both are wanted, the same way the effect injection binds to both of its own.
 */
@Mixin(targets = "ovh.corail.tombstone.perk.PerkGladiator", remap = false)
public abstract class MixinPerkGladiatorInfo {

    @ModifyConstant(method = "getCurrentBonusInfo", constant = @Constant(intValue = 5), remap = false)
    private int tombtweaks$gladiatorInfoPerLevel(int original) {
        return PerkTuningHelper.gladiatorDamagePercentPerLevel(original);
    }
}
