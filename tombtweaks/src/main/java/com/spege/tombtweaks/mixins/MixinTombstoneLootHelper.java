package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Treasure Seeker: the odds of one extra loot roll when an undead mob dies.
 *
 * <p>Tombstone rolls {@code random(100) <= perkLevel * 20} for a single bonus roll on top of the
 * base count (1, or 5 for a boss). The base count is Tombstone's design and stays untouched — this
 * only prices the perk's contribution.
 *
 * <p>Only int 20 in {@code handleMobDrops} on 4.7.6.
 */
@Mixin(targets = "ovh.corail.tombstone.helper.LootHelper", remap = false)
public abstract class MixinTombstoneLootHelper {

    @ModifyConstant(method = "handleMobDrops", constant = @Constant(intValue = 20), remap = false)
    private static int tombtweaks$treasureSeekerChancePerLevel(int original) {
        return PerkTuningHelper.treasureSeekerLootChancePerLevel(original);
    }
}
