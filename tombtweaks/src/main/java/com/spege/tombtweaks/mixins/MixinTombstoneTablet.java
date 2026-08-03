package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Rune Inscriber: the chance a tablet survives being used.
 *
 * <p>Tombstone consumes the tablet unless {@code random(10) + 1 <= perkLevel}, i.e. a flat 10% keep
 * chance per level. Raising the divisor is what weakens the perk; the perk level itself is left
 * alone so the GUI and every other consumer still agree on it.
 *
 * <p>Only int 10 in {@code onConsumeItem} on 4.7.6.
 */
@Mixin(targets = "ovh.corail.tombstone.item.ItemTablet", remap = false)
public abstract class MixinTombstoneTablet {

    @ModifyConstant(method = "onConsumeItem", constant = @Constant(intValue = 10), remap = false)
    private int tombtweaks$runeInscriberKeepDivisor(int original) {
        return PerkTuningHelper.runeInscriberKeepDivisor(original);
    }
}
