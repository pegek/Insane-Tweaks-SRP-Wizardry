package com.spege.insanetweaks.mixins;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(Locale.class)
public class MixinLocale {

    @Shadow
    Map<String, String> properties;

    @Inject(method = "loadLocaleDataFiles", at = @At("RETURN"))
    private void insanetweaks_overwriteGoldenOsmosis(IResourceManager resourceManager, List<String> languageList, CallbackInfo ci) {
        if (this.properties != null) {
            String newName = this.properties.get("reskillable.unlock.compatskills.golden_osmosis");
            String newDesc = this.properties.get("reskillable.unlock.compatskills.golden_osmosis.desc");

            // Hard overwrite natywnych wpisów Reskillable w słowniku gry
            if (newName != null) {
                this.properties.put("reskillable.unlock.reskillable.golden_osmosis", newName);
            }
            if (newDesc != null) {
                this.properties.put("reskillable.unlock.reskillable.golden_osmosis.desc", newDesc);
            }
        }
    }
}
