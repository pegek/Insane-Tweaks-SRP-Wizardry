package com.spege.insanetweaks.mixins.enchant;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.spege.insanetweaks.util.EnchantSourceGuard;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;

/**
 * Quest-gating, choke point 3 of 3: keep InsaneTweaks enchantments out of librarian trades.
 *
 * <p>Target: {@code EntityVillager$ListEnchantedBookForEmeralds.addMerchantRecipe(IMerchant,
 * MerchantRecipeList, Random)} — {@code func_190888_a}. Verified in bytecode, its first instruction is
 * {@code Enchantment.REGISTRY.getRandomObject(random)}: the librarian's enchanted book is a uniform
 * pick over the <b>entire</b> enchantment registry with no treasure check, no {@code canApply} check
 * and no filtering of any kind. It does read {@code isTreasureEnchantment()} afterwards, but only to
 * double the emerald price — a treasure enchantment is still offered. Forge 1.12.2 fires no event when
 * a villager's trade list is populated, so this has to be bytecode.
 *
 * <p>We veto the finished trade at {@code MerchantRecipeList.add} instead of rerolling the registry
 * pick, which would be the natural place. Reason: <b>RLTweaker's {@code patchEnchantments}</b>
 * (enabled in the DEv 1.2 pack) ASM-<em>replaces</em> that {@code getRandomObject} call with
 * {@code HookEnchant.getRandomRestricted} for its own blacklist, so a redirect there would collide —
 * whichever transformer ran second would silently lose its patch. Nothing touches the
 * {@code MerchantRecipeList.add}, so both mods' filters survive.
 *
 * <p>Consequence of vetoing rather than rerolling: that librarian ends up one offer short for this
 * refresh instead of being handed a different book. Acceptable — a librarian's trade list has several
 * entries, the gap is invisible in play, and "one fewer offer" is a far cheaper failure mode than
 * either a broken guarantee or re-deriving vanilla's emerald-price formula to build a replacement.
 *
 * <p>Reading the enchantment back off the built recipe is safe:
 * {@code EnchantmentHelper.getEnchantments} special-cases {@code ENCHANTED_BOOK} and reads
 * {@code StoredEnchantments} (confirmed in bytecode), so it sees the book's stored enchantment rather
 * than an empty {@code ench} list.
 *
 * <p>NOTE: the {@code @Mixin} target is an inner class named by string, and the enclosing method
 * selector carries both MCP and SRG names, so this matches in dev and in the obfuscated runtime alike.
 */
@Mixin(targets = "net.minecraft.entity.passive.EntityVillager$ListEnchantedBookForEmeralds")
public class MixinVillagerEnchantedBookTrade {

    @Redirect(method = { "addMerchantRecipe", "func_190888_a" },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/village/MerchantRecipeList;add(Ljava/lang/Object;)Z"),
            remap = false)
    /**
     * The element parameter must be declared {@code Object}: Mixin matches a redirect against the call
     * site's descriptor, and {@code ArrayList.add} erases to {@code (Ljava/lang/Object;)Z}. The cast on
     * the pass-through is guaranteed by the target's own bytecode ({@code new MerchantRecipe} feeds
     * this very {@code add}) — and a loud ClassCastException would be the right outcome anyway if some
     * future transformer changed that, rather than silently dropping a trade.
     */
    private boolean insanetweaks$vetoQuestGatedBookTrade(MerchantRecipeList recipes, Object recipe) {
        if (EnchantSourceGuard.isActive() && recipe instanceof MerchantRecipe) {
            Enchantment blocked = insanetweaks$findBlocked(((MerchantRecipe) recipe).getItemToSell());
            if (blocked != null) {
                EnchantSourceGuard.logOnce(blocked, "villager trade");
                return false; // not added: the librarian simply does not stock this book
            }
        }
        return recipes.add((MerchantRecipe) recipe);
    }

    /** The first quest-gated enchantment stored on the sold book, or null when it is safe to sell. */
    private Enchantment insanetweaks$findBlocked(ItemStack sold) {
        if (sold == null || sold.isEmpty()) {
            return null;
        }
        Map<Enchantment, Integer> stored = EnchantmentHelper.getEnchantments(sold);
        for (Enchantment enchantment : stored.keySet()) {
            if (EnchantSourceGuard.isNaturallyBlocked(enchantment)) {
                return enchantment;
            }
        }
        return null;
    }
}
