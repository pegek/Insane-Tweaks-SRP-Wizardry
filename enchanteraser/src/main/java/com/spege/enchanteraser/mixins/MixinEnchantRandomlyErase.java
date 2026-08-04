package com.spege.enchanteraser.mixins;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.enchanteraser.util.EraserState;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.functions.EnchantRandomly;

/**
 * Choke point 3 of 4: keep erased enchantments out of generated chest loot.
 *
 * <p>Target: {@code EnchantRandomly.apply(ItemStack, Random, LootContext)} — {@code func_186553_a},
 * the {@code minecraft:enchant_randomly} loot function. This is the vector no flag on the enchantment
 * can stop: the candidate loop is
 * {@code if (stack.getItem() == Items.BOOK || enchantment.canApply(stack)) list.add(enchantment);}
 * — no treasure check anywhere, and for a plain book {@code canApply} is short-circuited away
 * entirely. Vanilla uses this on books in {@code chests/simple_dungeon},
 * {@code chests/abandoned_mineshaft}, {@code gameplay/fishing/treasure} and friends.
 *
 * <p>Both instruction-level anchors inside this method are already taken in a DEv 1.2 style pack:
 * RLTweaker's {@code patchEnchantments} <em>replaces</em> the candidate list's {@code add} with its own
 * hook, and InsaneTweaks redirects {@code RegistryNamespaced.iterator()} for its quest gate. Two
 * transformers rewriting one instruction means whichever runs second finds it gone. So this filters at
 * RETURN instead — injectors compose with both, and with each other.
 *
 * <p>Filtering after the fact is sound here because the function writes exactly one enchantment per
 * call. Removing it from the finished stack reproduces vanilla's own "no compatible enchantment" path,
 * which returns the item unenchanted and which the loot system already handles. A plain book that was
 * swapped for an enchanted book and then emptied is swapped back, so loot never contains a blank
 * enchanted book.
 */
@Mixin(EnchantRandomly.class)
public class MixinEnchantRandomlyErase {

    @Inject(method = { "apply", "func_186553_a" }, at = @At("RETURN"), cancellable = true, remap = false)
    private void enchanteraser$stripErasedFromLoot(ItemStack input, Random rand, LootContext context,
            CallbackInfoReturnable<ItemStack> cir) {
        if (EraserState.isEmpty()) {
            return;
        }
        ItemStack result = cir.getReturnValue();
        if (result == null || result.isEmpty()) {
            return;
        }
        boolean wasBook = result.getItem() == Items.ENCHANTED_BOOK;
        if (EraserState.stripFromStack(result, "loot enchant_randomly") && wasBook) {
            cir.setReturnValue(new ItemStack(Items.BOOK, result.getCount()));
        }
    }
}
