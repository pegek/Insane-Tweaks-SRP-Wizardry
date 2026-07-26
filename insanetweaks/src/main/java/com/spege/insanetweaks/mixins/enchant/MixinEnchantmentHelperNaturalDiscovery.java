package com.spege.insanetweaks.mixins.enchant;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.util.EnchantSourceGuard;

import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;

/**
 * Quest-gating, choke point 1 of 3: strip InsaneTweaks enchantments out of the shared candidate pool.
 *
 * <p>Target: {@code EnchantmentHelper.getEnchantmentDatas(int, ItemStack, boolean)} —
 * {@code func_185291_a}, the one method every level-based roll funnels through
 * ({@code buildEnchantmentList} → the enchanting table, {@code addRandomEnchantment} → the
 * {@code enchant_with_levels} loot function, mob equipment, and the enchanted-item villager trades).
 * Patching here also covers third-party mods that roll enchantments through the vanilla helper.
 *
 * <p>Why this is needed on top of {@code EnchantmentInsaneTweaksBase}: vanilla's treasure guard reads
 * {@code (!isTreasureEnchantment() || allowTreasure) && (canApplyAtEnchantingTable(stack) ||
 * (isBook && isAllowedOnBooks()))}. When a caller passes {@code allowTreasure == true} <b>and</b> the
 * item is a book — exactly {@code chests/stronghold_library}'s
 * {@code enchant_with_levels {levels: 20-39, treasure: true}} on a book — the treasure flag is
 * satisfied and {@code isAllowedOnBooks()} lets our enchantment straight through. We keep
 * {@code isAllowedOnBooks()} true on purpose (it is what puts the book in the creative/JEI list), so
 * that hole has to be closed here.
 *
 * <p>{@code @Inject} at RETURN rather than a {@code @Redirect} on the list's {@code add}: injectors
 * coexist, redirects are exclusive, and {@code EnchantmentHelper} is a popular mixin target in a pack
 * that also ships SoManyEnchantments and Unique Enchantments. The returned list is the freshly
 * allocated {@code ArrayList} vanilla is about to hand back, so mutating it in place is safe and
 * nothing else holds a reference yet.
 *
 * <p>RLTweaker's {@code patchEnchantments} (on in the DEv 1.2 pack) inserts its own
 * {@code HookEnchant.restrictEnchantmentDatas} before this method's ARETURN for a config-driven
 * blacklist. That is the one of its three edits we can safely share a method with: both filters only
 * <em>remove</em> entries from the same list, so they compose whichever transformer runs first, and
 * neither rewrites an instruction the other is looking for. The sibling mixins deliberately avoid
 * RLTweaker's other two edit sites — see their javadoc.
 *
 * <p>The three sites, and the vanilla flag each one ignores:
 * <table>
 * <tr><th>Mixin</th><th>Path</th><th>Why the flags miss it</th></tr>
 * <tr><td>this one</td><td>table / {@code enchant_with_levels} / mob gear</td>
 *     <td>{@code treasure: true} + book bypasses the treasure guard</td></tr>
 * <tr><td>{@link MixinEnchantRandomly}</td><td>{@code enchant_randomly} loot function</td>
 *     <td>no treasure check at all; a book skips {@code canApply} too</td></tr>
 * <tr><td>{@link MixinVillagerEnchantedBookTrade}</td><td>librarian enchanted book</td>
 *     <td>picks straight from the registry, checks nothing</td></tr>
 * </table>
 */
@Mixin(EnchantmentHelper.class)
public class MixinEnchantmentHelperNaturalDiscovery {

    @Inject(method = { "getEnchantmentDatas", "func_185291_a" }, at = @At("RETURN"), remap = false)
    private static void insanetweaks$stripQuestGatedEnchants(int enchantability, ItemStack stack,
            boolean allowTreasure, CallbackInfoReturnable<List<EnchantmentData>> cir) {
        EnchantSourceGuard.stripBlocked(cir.getReturnValue(), "enchantment pool");
    }
}
