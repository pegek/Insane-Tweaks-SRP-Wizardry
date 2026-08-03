package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Gladiator: the damage swing per perk level.
 *
 * <p>{@code onLivingHurt} applies the perk twice — {@code amount *= (1 + level * 0.05f)} when the
 * player deals the hit, {@code amount *= (1 - level * 0.05f)} when they take it. Both are the same
 * float constant and there are exactly two of them in the method on 4.7.6, so this injection binds
 * to both on purpose: one knob moves attack and defence together, which is how Tombstone wrote the
 * perk. Splitting them would need an ordinal, and an ordinal silently retargets on a Tombstone
 * update — not worth it for a symmetric perk.
 */
@Mixin(targets = "ovh.corail.tombstone.event.EventHandler", remap = false)
public abstract class MixinTombstoneGladiator {

    @ModifyConstant(method = "onLivingHurt", constant = @Constant(floatValue = 0.05F), remap = false)
    private static float tombtweaks$gladiatorDamagePerLevel(float original) {
        return PerkTuningHelper.gladiatorDamagePerLevel(original);
    }
}
