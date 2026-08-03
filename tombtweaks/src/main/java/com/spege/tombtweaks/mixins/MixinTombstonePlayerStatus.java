package com.spege.tombtweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.spege.tombtweaks.util.PerkTuningHelper;

/**
 * Shadow Walker: how much each level hides the player.
 *
 * <p>{@code createStatus} multiplies visibility by {@code (1 - perkLevel * 0.1)}, on top of the
 * separate term the Shadow Step enchantment contributes. Only double 0.1 in the method on 4.7.6;
 * the other value the method builds ({@code visibleFactorHighPriority}, driven by Ghostly Shape,
 * Diversion and Bait) has nothing to do with the perk and is untouched.
 *
 * <p>Worth knowing before tuning this: the whole effect is delivered through Forge's
 * {@code PlayerEvent.Visibility}, and in vanilla 1.12.2 that event fires from exactly one method —
 * {@code World.getNearestAttackablePlayer} — whose only callers are Enderman AI and the Ender
 * Dragon. Mobs that target through {@code EntityAINearestAttackableTarget} never consult it.
 * Scape and Run: Parasites is the big exception: its single targeting task calls the Forge hook
 * directly, and that task is wired into every parasite base class.
 */
@Mixin(targets = "ovh.corail.tombstone.helper.PlayerStatusHandler", remap = false)
public abstract class MixinTombstonePlayerStatus {

    @ModifyConstant(method = "createStatus", constant = @Constant(doubleValue = 0.1D), remap = false)
    private static double tombtweaks$shadowWalkerPerLevel(double original) {
        return PerkTuningHelper.shadowWalkerVisibilityPerLevel(original);
    }
}
