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

    // Shadow po nazwie SRG bezpośrednio (remap=false, zero aliasów) — Mixin 0.8.7 / CleanMix 0.6.6+
    // zabrania aliasowania pól non-private, a Locale.properties (field_135032_a) jest package-private.
    @Shadow(remap = false)
    Map<String, String> field_135032_a;

    @Inject(method = { "loadLocaleDataFiles", "func_135022_a" }, at = @At("RETURN"), remap = false)
    private void insanetweaks_overwriteGoldenOsmosis(IResourceManager resourceManager, List<String> languageList, CallbackInfo ci) {
        if (this.field_135032_a != null) {
            String[] nativeSkillsToOverwrite = new String[] {
                "golden_osmosis",
                "safe_port"
            };

            for (String skill : nativeSkillsToOverwrite) {
                String newName = this.field_135032_a.get("reskillable.unlock.compatskills." + skill);
                String newDesc = this.field_135032_a.get("reskillable.unlock.compatskills." + skill + ".desc");

                // Hard overwrite natywnych wpisów Reskillable w słowniku gry
                if (newName != null) {
                    this.field_135032_a.put("reskillable.unlock.reskillable." + skill, newName);
                }
                if (newDesc != null) {
                    this.field_135032_a.put("reskillable.unlock.reskillable." + skill + ".desc", newDesc);
                }
            }
        }
    }
}
