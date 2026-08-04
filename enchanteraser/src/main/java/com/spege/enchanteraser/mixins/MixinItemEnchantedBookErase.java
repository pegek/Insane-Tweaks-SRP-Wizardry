package com.spege.enchanteraser.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.enchanteraser.config.EnchantEraserConfig;
import com.spege.enchanteraser.util.EraserState;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

/**
 * Optional: drop erased enchantments' books out of the item list.
 *
 * <p>Target: {@code ItemEnchantedBook.getSubItems(CreativeTabs, NonNullList)} — {@code func_150895_a},
 * which walks {@code Enchantment.REGISTRY} and emits one book per level for every enchantment with a
 * non-null {@code type}. It filters on {@code type} and <b>nothing else</b> — not
 * {@code isTreasureEnchantment}, not {@code isAllowedOnBooks} — so nothing else in this mod hides these
 * books. JEI and HEI build their ingredient list by calling this with {@code CreativeTabs.SEARCH}
 * (JEI's {@code ItemStackListFactory}), which is why one filter covers both the JEI/HEI list and the
 * creative tabs.
 *
 * <p>Gated on {@code Hide Erased From JEI}, read live. Off by default is not the choice here: a book
 * the player cannot obtain, cannot apply on an anvil, and that does nothing if applied is an invitation
 * to waste time, so it goes away with the rest.
 *
 * <p>Not a client-only mixin. Forge removes vanilla's {@code @SideOnly(Side.CLIENT)} from
 * {@code Item.getSubItems} and {@code CreativeTabs} is a common class, so this belongs in the ordinary
 * {@code mixins} list rather than {@code client} — verified in the Forge sources, not assumed.
 *
 * <p>{@code @Inject} at RETURN over a redirect on the registry iterator: JustEnoughIDs mixes this same
 * class twice ({@code MixinItemEnchantedBook} in both {@code core.enchant} and
 * {@code core.enchant.client}) to widen enchantment ids. Neither declares an {@code @Overwrite} and
 * neither touches this method, but filtering the finished list stays out of their way regardless, and
 * it also catches books another mixin may have appended.
 */
@Mixin(ItemEnchantedBook.class)
public class MixinItemEnchantedBookErase {

    @Inject(method = { "getSubItems", "func_150895_a" }, at = @At("RETURN"), remap = false)
    private void enchanteraser$hideErasedBooks(CreativeTabs tab, NonNullList<ItemStack> items,
            CallbackInfo ci) {
        if (!EnchantEraserConfig.hideErasedFromJei || EraserState.isEmpty()
                || items == null || items.isEmpty()) {
            return;
        }
        // Backwards so a removal cannot shift an unvisited index.
        for (int i = items.size() - 1; i >= 0; i--) {
            net.minecraft.enchantment.Enchantment erased = EraserState.firstDisabledOn(items.get(i));
            if (erased != null) {
                items.remove(i);
                EraserState.logOnce(erased, "JEI/creative book list");
            }
        }
    }
}
