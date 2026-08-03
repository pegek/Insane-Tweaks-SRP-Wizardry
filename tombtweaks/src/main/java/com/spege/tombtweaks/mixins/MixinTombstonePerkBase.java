package com.spege.tombtweaks.mixins;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.tombtweaks.config.TombTweaksConfig;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Mixin on the abstract base class {@code ovh.corail.tombstone.api.capability.Perk}.
 */
@Mixin(targets = "ovh.corail.tombstone.api.capability.Perk", remap = false)
public abstract class MixinTombstonePerkBase {

    @Shadow
    protected String name;

    @Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true)
    private void tombtweaks$baseIsDisabled(@Nullable EntityPlayer player,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks) return;

        com.spege.tombtweaks.config.categories.TombstoneCategory.PerkConfig cfg =
                com.spege.tombtweaks.util.PerkConfigLookup.byName(this.name);
        if (cfg == null) return;

        if (!cfg.enabled || cfg.maxLevel == 0) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Per-perk point price. The base method is a flat {@code level > 0 ? 1 : 0} and not one of the
     * ten native perks overrides it (verified with javap on 4.7.6), so this single injection prices
     * all ten — and prices them everywhere at once, since purchase validation, the used-points sum
     * and the respec refund all read {@code getCost}.
     *
     * <p>This mod's own two perks are untouched by design: they override {@code getCost} in
     * {@code PerkTombTweaksBase} with their own config field, so the base method never runs for them.
     */
    @Inject(method = "getCost", at = @At("HEAD"), cancellable = true)
    private void tombtweaks$overrideCost(int level, CallbackInfoReturnable<Integer> cir) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks) return;
        if (level <= 0) return;

        com.spege.tombtweaks.config.categories.TombstoneCategory.PerkConfig cfg =
                com.spege.tombtweaks.util.PerkConfigLookup.byName(this.name);
        if (cfg == null) return;

        cir.setReturnValue(cfg.pointCostPerLevel);
    }
}
