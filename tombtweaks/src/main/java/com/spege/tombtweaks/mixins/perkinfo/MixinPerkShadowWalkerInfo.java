package com.spege.tombtweaks.mixins.perkinfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Shadow Walker's bonus line, in step with the visibility the status handler computes.
 *
 * <p>The tooltip is a percentage off an int 10 where the effect uses a double 0.1, so the value is
 * converted back. Only int 10 in the method.
 */
@Mixin(targets = "ovh.corail.tombstone.perk.PerkShadowWalker", remap = false)
public abstract class MixinPerkShadowWalkerInfo {

    @ModifyConstant(method = "getCurrentBonusInfo", constant = @Constant(intValue = 10), remap = false)
    private int tombtweaks$shadowWalkerInfoPerLevel(int original) {
        return PerkTuningHelper.shadowWalkerVisibilityPercentPerLevel(original);
    }
}
