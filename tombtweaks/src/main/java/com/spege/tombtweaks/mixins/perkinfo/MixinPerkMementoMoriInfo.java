package com.spege.tombtweaks.mixins.perkinfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Memento Mori's bonus line, in step with the experience actually returned.
 *
 * <p>The tooltip mirrors the death handler exactly: {@code min(100 - xpLoss + level * 20, 100)}.
 * Only int 20 in the method; the two 100s are the percentage scale and stay put.
 */
@Mixin(targets = "ovh.corail.tombstone.perk.PerkMementoMori", remap = false)
public abstract class MixinPerkMementoMoriInfo {

    @ModifyConstant(method = "getCurrentBonusInfo", constant = @Constant(intValue = 20), remap = false)
    private int tombtweaks$mementoMoriInfoPerLevel(int original) {
        return PerkTuningHelper.mementoMoriXpKeptPerLevel(original);
    }
}
