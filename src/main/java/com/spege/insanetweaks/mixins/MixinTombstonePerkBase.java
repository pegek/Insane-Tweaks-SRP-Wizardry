package com.spege.insanetweaks.mixins;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Mixin on the abstract base class {@code ovh.corail.tombstone.api.capability.Perk}.
 */
@Mixin(targets = "ovh.corail.tombstone.api.capability.Perk", remap = false)
public abstract class MixinTombstonePerkBase {

    @Shadow
    protected String name;

    @Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true)
    private void insanetweaks$baseIsDisabled(@Nullable EntityPlayer player,
            CallbackInfoReturnable<Boolean> cir) {
        if (!ModConfig.tombstone.enableTombstoneTweaks) return;

        ModConfig.PerkConfig cfg = insanetweaks$resolveConfig(name);
        if (cfg == null) return;

        if (!cfg.enabled || cfg.maxLevel == 0) {
            cir.setReturnValue(true);
        }
    }

    @Nullable
    private static ModConfig.PerkConfig insanetweaks$resolveConfig(String perkName) {
        if (perkName == null) return null;
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