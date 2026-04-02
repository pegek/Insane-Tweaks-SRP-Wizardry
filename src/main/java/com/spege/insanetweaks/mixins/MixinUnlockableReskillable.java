package com.spege.insanetweaks.mixins;

import codersafterdark.reskillable.api.unlockable.Unlockable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Unlockable.class, remap = false)
@SuppressWarnings("deprecation")
public abstract class MixinUnlockableReskillable {

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true, remap = false)
    private void insanetweaks$overrideUnlockableName(CallbackInfoReturnable<String> cir) {
        String skill = insanetweaks$getPatchedNativeSkill();
        if (skill != null) {
            cir.setReturnValue(I18n.translateToLocal("reskillable.unlock.compatskills." + skill));
        }
    }

    @Inject(method = "getDescription", at = @At("HEAD"), cancellable = true, remap = false)
    private void insanetweaks$overrideUnlockableDescription(CallbackInfoReturnable<String> cir) {
        String skill = insanetweaks$getPatchedNativeSkill();
        if (skill != null) {
            cir.setReturnValue(insanetweaks$formatMultiline(I18n.translateToLocal("reskillable.unlock.compatskills." + skill + ".desc")));
        }
    }

    private String insanetweaks$getPatchedNativeSkill() {
        ResourceLocation registryName = ((Unlockable) (Object) this).getRegistryName();
        if (registryName == null) {
            return null;
        }

        String id = registryName.toString();
        if ("reskillable:golden_osmosis".equals(id)) {
            return "golden_osmosis";
        }
        if ("reskillable:safe_port".equals(id)) {
            return "safe_port";
        }

        return null;
    }

    private String insanetweaks$formatMultiline(String text) {
        return text == null ? null : text.replace("\\n", "\n");
    }
}
