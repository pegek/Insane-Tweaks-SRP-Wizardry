package com.spege.insanetweaks.mixins;

import net.minecraft.client.resources.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(Locale.class)
public interface AccessorLocale {

    @Accessor("properties")
    Map<String, String> insanetweaks$getProperties();
}
