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

    @Shadow(aliases = "field_135032_a", remap = false)
    Map<String, String> properties;

    @Inject(method = { "loadLocaleDataFiles", "func_135022_a" }, at = @At("RETURN"), remap = false)
    private void insanetweaks_overwriteGoldenOsmosis(IResourceManager resourceManager, List<String> languageList, CallbackInfo ci) {
        if (this.properties != null) {
            String[] nativeSkillsToOverwrite = new String[] {
                "golden_osmosis",
                "safe_port"
            };

            for (String skill : nativeSkillsToOverwrite) {
                String newName = this.properties.get("reskillable.unlock.compatskills." + skill);
                String newDesc = this.properties.get("reskillable.unlock.compatskills." + skill + ".desc");

                // Hard overwrite natywnych wpisów Reskillable w słowniku gry
                if (newName != null) {
                    this.properties.put("reskillable.unlock.reskillable." + skill, newName);
                }
                if (newDesc != null) {
                    this.properties.put("reskillable.unlock.reskillable." + skill + ".desc", newDesc);
                }
            }
        }
    }
}
