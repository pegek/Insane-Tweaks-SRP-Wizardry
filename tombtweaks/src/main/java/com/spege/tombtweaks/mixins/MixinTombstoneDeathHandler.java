package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Memento Mori: how much experience each level hands back on death.
 *
 * <p>Tombstone keeps {@code clamp(100 - xpLoss + perkLevel * 20, 0, 100)} percent of your total
 * experience, so at the default 100% loss a maxed perk natively cancels the penalty outright.
 * Because the result is clamped, an overshooting value here is harmless rather than exploitable.
 *
 * <p>Only int 20 in {@code addPlayerDead} on 4.7.6 — the 100s in the same expression are the
 * percentage scale, not the perk.
 */
@Mixin(targets = "ovh.corail.tombstone.helper.DeathHandler", remap = false)
public abstract class MixinTombstoneDeathHandler {

    @ModifyConstant(method = "addPlayerDead", constant = @Constant(intValue = 20), remap = false)
    private int tombtweaks$mementoMoriXpPerLevel(int original) {
        return PerkTuningHelper.mementoMoriXpKeptPerLevel(original);
    }
}
