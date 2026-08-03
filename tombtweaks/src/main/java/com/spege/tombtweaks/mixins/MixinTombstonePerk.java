package com.spege.tombtweaks.mixins;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.tombtweaks.config.TombTweaksConfig;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * Mixin on all 10 concrete Tombstone Perk subclasses.
 *
 * <p>Only what the subclasses actually declare belongs here — {@code getCost} is not overridden by
 * any of them, so its price cap lives on the base class in {@link MixinTombstonePerkBase}.
 */
@Mixin(targets = {
    "ovh.corail.tombstone.perk.PerkAlchemist",
    "ovh.corail.tombstone.perk.PerkConcentration",
    "ovh.corail.tombstone.perk.PerkGladiator",
    "ovh.corail.tombstone.perk.PerkJailer",
    "ovh.corail.tombstone.perk.PerkMementoMori",
    "ovh.corail.tombstone.perk.PerkRuneInscriber",
    "ovh.corail.tombstone.perk.PerkScribe",
    "ovh.corail.tombstone.perk.PerkShadowWalker",
    "ovh.corail.tombstone.perk.PerkTreasureSeeker",
    "ovh.corail.tombstone.perk.PerkWitchDoctor"
}, remap = false)
public abstract class MixinTombstonePerk {

    @Inject(method = "getLevelMax", at = @At("RETURN"), cancellable = true)
    private void tombtweaks$clampLevelMax(CallbackInfoReturnable<Integer> cir) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks) return;

        com.spege.tombtweaks.config.categories.TombstoneCategory.PerkConfig cfg = tombtweaks$resolveConfig();
        if (cfg == null) return;

        int nativeMax = cir.getReturnValue();
        int capped = Math.min(nativeMax, cfg.maxLevel);
        if (capped != nativeMax) {
            cir.setReturnValue(capped);
        }
    }

    @Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true, require = 0)
    private void tombtweaks$overrideIsDisabled(@Nullable EntityPlayer player,
            CallbackInfoReturnable<Boolean> cir) {
        if (!TombTweaksConfig.tombstone.enableTombstoneTweaks) return;

        com.spege.tombtweaks.config.categories.TombstoneCategory.PerkConfig cfg = tombtweaks$resolveConfig();
        if (cfg == null) return;

        if (!cfg.enabled || cfg.maxLevel == 0) {
            cir.setReturnValue(true);
        }
    }

    @Nullable
    private com.spege.tombtweaks.config.categories.TombstoneCategory.PerkConfig tombtweaks$resolveConfig() {
        // Safe runtime cast without needing Tombstone in compile classpath
        IForgeRegistryEntry<?> registryEntry = (IForgeRegistryEntry<?>) (Object) this;
        if (registryEntry.getRegistryName() == null) return null;

        return com.spege.tombtweaks.util.PerkConfigLookup.byName(registryEntry.getRegistryName().getResourcePath());
    }
}
