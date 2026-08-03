package com.spege.tombtweaks.mixins.perkinfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Rune Inscriber's bonus line, in step with the tablet keep chance.
 *
 * <p>The tooltip is a percentage per level off an int 10, while the effect is a divisor — so the
 * config divisor is converted back to {@code 100 / divisor}. Only int 10 in the method.
 */
@Mixin(targets = "ovh.corail.tombstone.perk.PerkRuneInscriber", remap = false)
public abstract class MixinPerkRuneInscriberInfo {

    @ModifyConstant(method = "getCurrentBonusInfo", constant = @Constant(intValue = 10), remap = false)
    private int tombtweaks$runeInscriberInfoPerLevel(int original) {
        return PerkTuningHelper.runeInscriberKeepPercentPerLevel(original);
    }
}
