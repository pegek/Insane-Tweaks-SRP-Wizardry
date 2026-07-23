package com.spege.insanetweaks.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.insanetweaks.util.SpellDisplayUtils;

import electroblob.wizardry.item.ItemScroll;
import electroblob.wizardry.spell.Spell;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

@Mixin(value = ItemScroll.class, remap = false)
public abstract class MixinItemScroll {

    @Inject(method = { "addInformation", "func_77624_a" }, at = @At("TAIL"))
    private void insanetweaks$replaceScrollElementLine(ItemStack stack, World world, List<String> tooltip,
            ITooltipFlag flag, CallbackInfo ci) {

        if (stack == null || stack.getMetadata() == Short.MAX_VALUE) {
            return;
        }

        Spell spell = Spell.byMetadata(stack.getMetadata());
        if (!SpellDisplayUtils.usesAbominationStyling(spell)) {
            return;
        }

        String vanillaElementName = spell.getElement().getDisplayName();
        String replacementElementName = SpellDisplayUtils.getElementDisplayName(spell);

        for (int i = 0; i < tooltip.size(); i++) {
            if (vanillaElementName.equals(tooltip.get(i))) {
                tooltip.set(i, replacementElementName);
                break;
            }
        }
    }
}
