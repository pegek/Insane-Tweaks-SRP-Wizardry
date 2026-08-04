package com.spege.enchanteraser.mixins;

import java.util.Random;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.spege.enchanteraser.util.EraserState;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.IMerchant;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;

/**
 * Choke point 4 of 4: keep erased enchantments out of librarian trades.
 *
 * <p>Target: {@code EntityVillager$ListEnchantedBookForEmeralds.addMerchantRecipe(IMerchant,
 * MerchantRecipeList, Random)} — {@code func_190888_a}. Its first instruction is
 * {@code Enchantment.REGISTRY.getRandomObject(random)}: the librarian's enchanted book is a uniform
 * pick over the <b>entire</b> registry with no treasure check, no {@code canApply} check and no
 * filtering of any kind, so {@link MixinEnchantmentHelperErase} never sees it. Forge 1.12.2 fires no
 * event when a villager's trade list is populated, so this has to be bytecode.
 *
 * <p>This is the most crowded target in the mod: a DEv 1.2 style pack already has five mixins on this
 * one class (InsaneTweaks, raids, ancientspellcraft, luckified, somanyenchantments), RLTweaker replaces
 * the {@code getRandomObject} call, and InsaneTweaks redirects {@code MerchantRecipeList.add}. An
 * {@code @Inject} at RETURN touches none of those instructions and composes with all of them.
 *
 * <p>It also scans the <b>whole</b> list rather than just the entry this call appended. That is
 * deliberate: with five other mods adding their own book offers to the same list, an erased
 * enchantment can arrive from any of them, and a list of trade offers is short enough that the scan is
 * free. Reading the enchantment back off a built recipe is safe because
 * {@code EnchantmentHelper.getEnchantments} special-cases {@code ENCHANTED_BOOK} and reads
 * {@code StoredEnchantments}.
 *
 * <p>Consequence of vetoing rather than rerolling: that librarian ends up one offer short for this
 * refresh instead of being handed a different book. Acceptable — a trade list has several entries, the
 * gap is invisible in play, and it is far cheaper than re-deriving vanilla's emerald-price formula.
 *
 * <p>The {@code @Mixin} target is an inner class named by string, and the method selector carries both
 * MCP and SRG names, so this matches in dev and in the obfuscated runtime alike.
 */
@Mixin(targets = "net.minecraft.entity.passive.EntityVillager$ListEnchantedBookForEmeralds")
public class MixinVillagerBookTradeErase {

    @Inject(method = { "addMerchantRecipe", "func_190888_a" }, at = @At("RETURN"), remap = false)
    private void enchanteraser$vetoErasedBookTrade(IMerchant merchant, MerchantRecipeList recipes,
            Random random, CallbackInfo ci) {
        if (EraserState.isEmpty() || recipes == null || recipes.isEmpty()) {
            return;
        }
        // Backwards so a removal cannot shift an unvisited index.
        for (int i = recipes.size() - 1; i >= 0; i--) {
            Object entry = recipes.get(i);
            if (!(entry instanceof MerchantRecipe)) {
                continue;
            }
            Enchantment erased = EraserState.firstDisabledOn(((MerchantRecipe) entry).getItemToSell());
            if (erased != null) {
                recipes.remove(i);
                EraserState.logOnce(erased, "villager trade");
            }
        }
    }
}
