package com.spege.tombtweaks.mixins.perkinfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Makes the Scribe entry in the Knowledge of Death screen quote the number the book actually uses.
 *
 * <p>Tombstone writes the tooltip from its own literals rather than from the code that runs the
 * perk, so retuning the effect alone leaves the GUI advertising the stock figure. The formula here
 * is the same {@code level + 2} the item uses, so the same two knobs apply — and the same two kinds
 * of anchor: the base is a constant, the per-level share is the incoming level.
 *
 * <p>{@code getNextBonusInfo} is not overridden by any native perk — the base class delegates it
 * straight to {@code getCurrentBonusInfo} — so this one injection fixes both the "now" and the
 * "next level" line.
 */
@Mixin(targets = "ovh.corail.tombstone.perk.PerkScribe", remap = false)
public abstract class MixinPerkScribeInfo {

    @ModifyConstant(method = "getCurrentBonusInfo", constant = @Constant(intValue = 2), remap = false)
    private int tombtweaks$scribeInfoBase(int original) {
        return PerkTuningHelper.scribeBaseEnchants(original);
    }

    @ModifyVariable(method = "getCurrentBonusInfo", at = @At("HEAD"), argsOnly = true, ordinal = 0, remap = false)
    private int tombtweaks$scribeInfoLevel(int level) {
        return PerkTuningHelper.scribeScaledLevel(level);
    }
}
