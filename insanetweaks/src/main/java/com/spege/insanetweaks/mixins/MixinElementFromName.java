package com.spege.insanetweaks.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.constants.Element;

/**
 * Keeps {@code "element": "abomination"} resolvable when the enum extension failed.
 *
 * <p>{@code SpellProperties} uses the one-argument {@code Element.fromName}, which ends in
 * {@code throw new IllegalArgumentException}. That is caught upstream and logged rather than
 * crashing, so with {@code ModElements.EXTENDED == false} and no mixin the fourteen spells would
 * load with no properties at all - hollow spells with no tier, cost, cooldown or source flags, and
 * only a log line to say so. Mapping the name to MAGIC turns that into the graceful degradation the
 * design promises.
 *
 * <p>Lives in the EARLY config on purpose: early configs are installed at coremod time, so the
 * transformer is in place before anything can load {@code Element}. The late config is queued during
 * mod construction - the same phase in which our own {@code @Mod} constructor loads {@code Element}.
 */
@Mixin(value = Element.class, remap = false)
public abstract class MixinElementFromName {

    @Inject(method = "fromName(Ljava/lang/String;)Lelectroblob/wizardry/constants/Element;",
            at = @At("HEAD"), cancellable = true)
    private static void insanetweaks$resolveAbominationWhenAbsent(String name,
            CallbackInfoReturnable<Element> cir) {

        if (ModElements.EXTENDED || !"abomination".equals(name)) {
            return;
        }
        cir.setReturnValue(Element.MAGIC);
    }
}
