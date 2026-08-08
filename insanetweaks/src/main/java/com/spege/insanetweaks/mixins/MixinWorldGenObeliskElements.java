package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.NativeElements;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.worldgen.WorldGenObelisk;

/** Same reasoning as {@code MixinWorldGenShrineElements}, for obelisks. */
@Mixin(value = WorldGenObelisk.class, remap = false)
public abstract class MixinWorldGenObeliskElements {

    @Redirect(method = "spawnStructure",
            at = @At(value = "INVOKE",
                     target = "Lelectroblob/wizardry/constants/Element;values()[Lelectroblob/wizardry/constants/Element;"))
    private Element[] insanetweaks$narrowObeliskElements() {
        return NativeElements.values();
    }
}
