package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.worldgen.WorldGenShrine;

/**
 * Keeps Abomination out of shrine generation.
 *
 * <p>A shrine is themed by element and built from that element's runestones. This mod ships no
 * Abomination runestone blockstate, so an Abomination shrine would generate as missing models.
 */
@Mixin(value = WorldGenShrine.class, remap = false)
public abstract class MixinWorldGenShrineElements {

    @Redirect(method = "spawnStructure",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowShrineElements() {
        return NativeElements.values();
    }
}
