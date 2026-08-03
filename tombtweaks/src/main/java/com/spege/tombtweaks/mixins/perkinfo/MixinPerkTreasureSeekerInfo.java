package com.spege.tombtweaks.mixins.perkinfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Treasure Seeker's bonus line, in step with the extra-loot roll.
 *
 * <p>Only int 20 in the method. The second bonus line this perk prints uses a 10 for an unrelated
 * figure, which is exactly why the injection matches on the value rather than on an ordinal.
 */
@Mixin(targets = "ovh.corail.tombstone.perk.PerkTreasureSeeker", remap = false)
public abstract class MixinPerkTreasureSeekerInfo {

    @ModifyConstant(method = "getCurrentBonusInfo", constant = @Constant(intValue = 20), remap = false)
    private int tombtweaks$treasureSeekerInfoPerLevel(int original) {
        return PerkTuningHelper.treasureSeekerLootChancePerLevel(original);
    }
}
