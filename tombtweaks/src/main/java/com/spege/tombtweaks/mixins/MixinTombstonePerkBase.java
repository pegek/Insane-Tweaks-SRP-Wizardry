package com.spege.tombtweaks.mixins;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.tombtweaks.config.TombTweaksConfig;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

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
     * Why the perk above is greyed out, for the player reading the knowledge screen.
     *
     * <p>Tombstone 4.8.0 added {@code getDisabledInfo} and started drawing disabled perks instead
     * of hiding them, so a perk this mod switches off is now visible and owes an explanation.
     * Before 4.8.0 it simply vanished, which is the same silent loss this repo already guards
     * against elsewhere.
     *
     * <p>🚨 {@code require = 0} is load-bearing: on Tombstone 4.7.x the method does not exist, and
     * the default {@code require = 1} would turn a missing target into an
     * {@code InvalidInjectionException} that takes the whole config down. At zero the injection
     * simply finds nothing and the mod keeps its old behaviour on the old version, so no dependency
     * version floor is needed.
     *
     * <p>The condition is exactly the one {@link #tombtweaks$baseIsDisabled} answers true on, so
     * the message is only ever shown when this mod really is a reason. Tombstone may have its own
     * reason at the same time (Jailer answers true when its chance config is zero); the claim
     * "our config disables it" stays true in that case, it is just not the only truth.
     */
    @Inject(method = "getDisabledInfo", at = @At("HEAD"), cancellable = true, require = 0)
    private void tombtweaks$disabledInfo(@Nullable EntityPlayer player,
            CallbackInfoReturnable<ITextComponent> cir) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks) return;

        com.spege.tombtweaks.config.categories.TombstoneCategory.PerkConfig cfg =
                com.spege.tombtweaks.util.PerkConfigLookup.byName(this.name);
        if (cfg == null) return;

        if (!cfg.enabled || cfg.maxLevel == 0) {
            cir.setReturnValue(new TextComponentTranslation("tombtweaks.perk.disabled"));
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
