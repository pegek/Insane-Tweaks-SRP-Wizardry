package com.spege.enchanteraser.mixins;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.enchanteraser.util.EraserState;

import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;

/**
 * Choke point 1 of 4: strip erased enchantments out of the shared candidate pool.
 *
 * <p>Target: {@code EnchantmentHelper.getEnchantmentDatas(int, ItemStack, boolean)} —
 * {@code func_185291_a}, the one method every level-based roll funnels through
 * ({@code buildEnchantmentList} → the enchanting table, {@code addRandomEnchantment} → the
 * {@code enchant_with_levels} loot function, fishing treasure, mob equipment). Patching here also
 * covers third-party mods that roll through the vanilla helper.
 *
 * <p>Why the caller and not the enchantment: vanilla's filter here is
 * {@code (!isTreasureEnchantment() || allowTreasure) && (canApplyAtEnchantingTable(stack) ||
 * (isBook && isAllowedOnBooks()))}, and all three of those are virtual methods that mod enchantments
 * override — SoManyEnchantments' {@code EnchantmentBase} overrides two of them for every one of its
 * enchantments. Removing the finished entry from the list is decided by us, not by the enchantment.
 *
 * <p>{@code @Inject} at RETURN rather than a {@code @Redirect} on the list's {@code add}: injectors
 * coexist, redirects are exclusive, and this method already has two other filters in a DEv 1.2 style
 * pack — RLTweaker's {@code patchEnchantments} inserts {@code HookEnchant.restrictEnchantmentDatas}
 * before the ARETURN, and InsaneTweaks injects at RETURN for its quest gate. All three only
 * <em>remove</em> entries from the same freshly allocated list, so they compose in any order and
 * none of them rewrites an instruction another is looking for.
 */
@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelperErase {

    @Inject(method = { "getEnchantmentDatas", "func_185291_a" }, at = @At("RETURN"), remap = false)
    private static void enchanteraser$stripErased(int enchantability, ItemStack stack,
            boolean allowTreasure, CallbackInfoReturnable<List<EnchantmentData>> cir) {
        if (EraserState.isEmpty()) {
            return;
        }
        EraserState.strip(cir.getReturnValue(), "enchantment pool");
    }
}
