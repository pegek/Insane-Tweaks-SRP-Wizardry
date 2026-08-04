package com.spege.enchanteraser.mixins.thirdparty;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.enchanteraser.util.EraserState;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Treasure2's loot chests, via GottschCore's private copy of the {@code enchant_randomly} loot function.
 *
 * <p>GottschCore ships {@code com.someguyssoftware.gottschcore.loot.functions.EnchantRandomly} as a
 * near-verbatim clone of vanilla's — {@code javap} confirms the same candidate loop, the same
 * {@code stack.getItem() == Items.BOOK || enchantment.canApply(stack)} short circuit, the same swap to
 * an {@code ENCHANTED_BOOK}, the same "Couldn't find a compatible enchantment" bail. Because it is a
 * separate class, none of the vanilla mixins in this mod see it, and because it iterates
 * {@code Enchantment.REGISTRY} directly it reads no flag on the enchantment either.
 *
 * <p>GottschCore's sibling {@code EnchantWithLevels} needs nothing: it delegates to
 * {@code EnchantmentHelper.addRandomEnchantment}, which funnels through {@code getEnchantmentDatas} and
 * is therefore already filtered by {@code MixinEnchantmentHelperErase}.
 *
 * <p>{@code @Inject} at RETURN rather than a redirect, for the same reason as the vanilla twin:
 * InsaneTweaks already redirects {@code RegistryNamespaced.iterator()} in this exact method for its
 * quest gate, and two transformers rewriting one instruction means whichever runs second finds it gone.
 * Injectors compose.
 *
 * <p>The handler captures no target arguments — Mixin allows a callback to declare the
 * {@code CallbackInfo} alone — because the third parameter is GottschCore's own {@code LootContext},
 * and naming it would drag a compile dependency on the jar into a mod that deliberately has none.
 */
@Mixin(targets = "com.someguyssoftware.gottschcore.loot.functions.EnchantRandomly", remap = false)
public class MixinGottschCoreEnchantRandomly {

    @Inject(method = "apply", at = @At("RETURN"), cancellable = true, remap = false)
    private void enchanteraser$stripErasedFromTreasure2Loot(CallbackInfoReturnable<ItemStack> cir) {
        if (EraserState.isEmpty()) {
            return;
        }
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }
        boolean wasBook = result.getItem() == Items.ENCHANTED_BOOK;
        if (EraserState.stripFromStack(result, "Treasure2 loot enchant_randomly") && wasBook) {
            cir.setReturnValue(new ItemStack(Items.BOOK, result.getCount()));
        }
    }
}
