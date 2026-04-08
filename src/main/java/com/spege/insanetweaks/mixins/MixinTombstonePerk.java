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
 * Mixin on Tombstone's base {@code ovh.corail.tombstone.api.capability.Perk} class.
 *
 * <p>Uses a string target instead of a direct class reference so this file compiles
 * even when tombstone is absent from the compile classpath. The mixin config
 * {@code mixins.insanetweaks.tombstone.json} is only loaded at runtime when
 * {@code Loader.isModLoaded("tombstone")} returns true (see {@link LateMixinBooter}).</p>
 *
 * <p>Intercepts two methods:</p>
 * <ul>
 *   <li>{@code isDisabled} — forces the perk to appear disabled when
 *       config flag {@code enabled=false} or {@code maxLevel==0}.</li>
 *   <li>{@code getLevelMax} — clamps the native max level down to the
 *       value configured in {@code maxLevel}. Never raises the native cap.</li>
 * </ul>
 *
 * <p>Both overrides are guarded by the master switch
 * {@code tombstone.enableTombstoneTweaks}.</p>
 */
@Mixin(targets = "ovh.corail.tombstone.api.capability.Perk", remap = false)
public abstract class MixinTombstonePerk {

    /** Shadowed to read the internal perk name for config lookup. */
    @Shadow
    protected String name;

    // ------------------------------------------------------------------
    // isDisabled override
    // ------------------------------------------------------------------

    @Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true)
    private void insanetweaks$overrideIsDisabled(@Nullable EntityPlayer player,
            CallbackInfoReturnable<Boolean> cir) {

        ModConfig.PerkConfig cfg = insanetweaks$getPerkConfig();
        if (cfg == null) return;
        if (!ModConfig.tombstone.enableTombstoneTweaks) return;

        // Force disabled if the config says disabled OR if maxLevel == 0
        if (!cfg.enabled || cfg.maxLevel == 0) {
            cir.setReturnValue(true);
        }
    }

    // ------------------------------------------------------------------
    // getLevelMax override
    // ------------------------------------------------------------------

    @Inject(method = "getLevelMax", at = @At("RETURN"), cancellable = true)
    private void insanetweaks$clampLevelMax(CallbackInfoReturnable<Integer> cir) {

        ModConfig.PerkConfig cfg = insanetweaks$getPerkConfig();
        if (cfg == null) return;
        if (!ModConfig.tombstone.enableTombstoneTweaks) return;

        // Clamp: never raise native max, only lower it
        int nativeMax = cir.getReturnValue();
        int clampedMax = Math.min(nativeMax, cfg.maxLevel);
        if (clampedMax != nativeMax) {
            cir.setReturnValue(clampedMax);
        }
    }

    // ------------------------------------------------------------------
    // Helper — resolves the PerkConfig entry for this perk by name
    // ------------------------------------------------------------------

    @Nullable
    private ModConfig.PerkConfig insanetweaks$getPerkConfig() {
        if (name == null) return null;
        ModConfig.TombstoneTweaks ts = ModConfig.tombstone;
        switch (name) {
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
            default:                return null; // unknown/third-party perk — skip
        }
    }
}
