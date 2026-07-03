package com.spege.insanetweaks.mixins;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.registries.IForgeRegistryEntry;

/**
 * Mixin on all 10 concrete Tombstone Perk subclasses.
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
    private void insanetweaks$clampLevelMax(CallbackInfoReturnable<Integer> cir) {
        if (!ModConfig.tombstone.enableTombstoneTweaks) return;

        ModConfig.PerkConfig cfg = insanetweaks$resolveConfig();
        if (cfg == null) return;

        int nativeMax = cir.getReturnValue();
        int capped = Math.min(nativeMax, cfg.maxLevel);
        if (capped != nativeMax) {
            cir.setReturnValue(capped);
        }
    }

    @Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true, require = 0)
    private void insanetweaks$overrideIsDisabled(@Nullable EntityPlayer player,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ModConfig.tombstone.enableTombstoneTweaks) return;

        ModConfig.PerkConfig cfg = insanetweaks$resolveConfig();
        if (cfg == null) return;

        if (!cfg.enabled || cfg.maxLevel == 0) {
            cir.setReturnValue(true);
        }
    }

    @Nullable
    private ModConfig.PerkConfig insanetweaks$resolveConfig() {
        // Safe runtime cast without needing Tombstone in compile classpath
        IForgeRegistryEntry<?> registryEntry = (IForgeRegistryEntry<?>) (Object) this;
        if (registryEntry.getRegistryName() == null) return null;
        
        String perkName = registryEntry.getRegistryName().getResourcePath();
        
        ModConfig.TombstoneTweaks ts = ModConfig.tombstone;
        switch (perkName) {
            case "alchemist":       return ts.alchemist;
            case "concentration":   return ts.concentration;
            case "gladiator":       return ts.gladiator;
            case "jailer":          return ts.jailer;
            case "memento_mori":    return ts.mementoMori;
            case "rune_inscriber":  return ts.runeInscriber;
            case "scribe":          return ts.scribe;
            case "shadow_walker":   return ts.shadowWalker;
            case "treasure_seeker": return ts.treasureSeeker;
            case "witch_doctor":    return ts.witchDoctor;
            default:                return null;
        }
    }
}