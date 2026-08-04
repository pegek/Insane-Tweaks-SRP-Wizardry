package com.spege.enchanteraser.compat;

import com.spege.enchanteraser.EnchantEraser;
import com.spege.enchanteraser.config.EnchantEraserConfig;
import com.spege.enchanteraser.util.EraserState;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientBlacklist;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.item.ItemEnchantedBook;

/**
 * Belt to {@code MixinItemEnchantedBookErase}'s braces: hide erased books using JEI's own blacklist.
 *
 * <p>The mixin filters {@code ItemEnchantedBook.getSubItems}, which is where JEI/HEI sources its items
 * ({@code StackHelper.addSubtypesFromCreativeTabToList} → {@code Item.getSubItems}) — but that is a
 * claim about how one version of one item-list implementation works. This plugin instead tells JEI
 * directly, through the API it publishes for the purpose, so the books stay hidden no matter which
 * path put them in the list.
 *
 * <p>The two are complementary, not redundant: the blacklist only governs JEI/HEI, while the mixin is
 * what keeps the books out of the <b>creative tabs</b>, which JEI has no say over. Both read the same
 * {@code Hide Erased From JEI} flag.
 *
 * <p>Loading is safe without JEI installed. {@code @JEIPlugin} classes are discovered by JEI through
 * Forge's ASM data table and instantiated only by JEI, so with no JEI present nothing here is ever
 * loaded and the missing {@code mezz.jei} types are never resolved. The mod therefore keeps working
 * standalone, and the JEI API stays a {@code compileOnly} dependency.
 *
 * <p>{@code register} runs during JEI's startup in {@code loadComplete}, which is after our
 * {@code postInit}, so {@link EraserState} is fully populated by then.
 *
 * <p>Every method of {@code IModPlugin} is a {@code default}, so overriding just {@code register} also
 * makes this forward-compatible with HEI, whose interface carries extra methods that JEI 4.16's API
 * does not declare.
 */
@JEIPlugin
public class EnchantEraserJeiPlugin implements IModPlugin {

    @Override
    public void register(IModRegistry registry) {
        if (!EnchantEraserConfig.hideErasedFromJei || EraserState.isEmpty()) {
            return;
        }
        IIngredientBlacklist blacklist = registry.getJeiHelpers().getIngredientBlacklist();
        int enchantments = 0;
        int books = 0;
        for (Enchantment enchantment : Enchantment.REGISTRY) {
            if (!EraserState.isDisabled(enchantment)) {
                continue;
            }
            enchantments++;
            // Same level span vanilla uses when it builds the books, so every stack JEI could be
            // holding is named exactly.
            for (int level = enchantment.getMinLevel(); level <= enchantment.getMaxLevel(); level++) {
                blacklist.addIngredientToBlacklist(
                        ItemEnchantedBook.getEnchantedItemStack(new EnchantmentData(enchantment, level)));
                books++;
            }
        }
        EnchantEraser.LOGGER.info("[EnchantEraser] JEI blacklist: hid {} book(s) for {} erased enchantment(s)",
                Integer.valueOf(books), Integer.valueOf(enchantments));
    }
}
