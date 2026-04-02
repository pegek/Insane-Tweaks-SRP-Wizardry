package com.spege.insanetweaks.mixins;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.client.resources.Locale;
import net.minecraft.util.text.translation.LanguageMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(LanguageManager.class)
public abstract class MixinLanguageManager {

    @Shadow
    @Final
    protected static Locale CURRENT_LOCALE;

    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void insanetweaks$overwriteReskillableDescriptions(IResourceManager resourceManager, CallbackInfo ci) {
        Map<String, String> properties = ((AccessorLocale) CURRENT_LOCALE).insanetweaks$getProperties();
        if (properties == null || properties.isEmpty()) {
            return;
        }

        boolean changed = false;
        String[] nativeSkillsToOverwrite = new String[] {
                "golden_osmosis",
                "safe_port"
        };

        for (String skill : nativeSkillsToOverwrite) {
            String newName = properties.get("reskillable.unlock.compatskills." + skill);
            String newDesc = properties.get("reskillable.unlock.compatskills." + skill + ".desc");

            if (newName != null) {
                properties.put("reskillable.unlock.reskillable." + skill, newName);
                changed = true;
            }

            if (newDesc != null) {
                properties.put("reskillable.unlock.reskillable." + skill + ".desc", newDesc);
                changed = true;
            }
        }

        if (changed) {
            LanguageMap.replaceWith(properties);
        }
    }
}
