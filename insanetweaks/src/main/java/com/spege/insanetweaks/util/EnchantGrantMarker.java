package com.spege.insanetweaks.util;

import java.util.Map;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * The grant marker: an NBT stamp that says "the pack handed this book out on purpose".
 *
 * <p>WHY THIS EXISTS. {@link EnchantSourceGuard} blocks the three vanilla paths that can invent an
 * enchantment out of nowhere, and those three mixins do apply. But a pack-wide scan of 273 jars found
 * that the remaining sources — Treasure2/GottschCore's own copy of the {@code EnchantRandomly} loot
 * function, Chance Cubes rewards, Infernal Mobs gear — do not go through any vanilla choke point.
 * They enumerate {@code Enchantment.REGISTRY} themselves and index it with their own RNG, and that
 * idiom cannot be intercepted safely: the same {@code iterator()} feeds JEI, the enchantment-description
 * tooltip, {@code /enchant} and every legitimate lookup, so filtering it would hide our enchantments
 * from the places they are supposed to be visible.
 *
 * <p>So instead of chasing an open-ended list of exits, this gates the single entrance.
 * A quest-gated enchantment can only be <b>applied</b> from a book that carries this marker, checked in
 * {@code EnchantGrantAnvilHandler}. A book produced by some unforeseen roller is inert: it can exist,
 * it just cannot be put on anything. New sources appearing in a future pack update need no new code.
 *
 * <p>The marker lives at the stack's NBT root, not inside {@code StoredEnchantments}, so it survives
 * everything that copies a stack and is trivial to author by hand:
 * {@code {StoredEnchantments:[{id:...,lvl:1}], insanetweaks_granted:1b}}. The easy route is
 * {@code /itweaks grantbook <enchantment> [level] [player]}, which mints a correctly marked book — an
 * FTB Quests command-reward can call it and no NBT has to be written by hand at all.
 *
 * <p>LIMITS, stated plainly:
 * <ul>
 * <li>{@code AnvilUpdateEvent} carries no player reference in 1.12.2, so there is no creative-mode
 *     bypass. A book taken straight from JEI is unmarked and will be refused — use the command when
 *     testing, or turn {@code enchantments.requireGrantMarker} off.</li>
 * <li>This gates <b>application</b>, not possession, and not an enchantment that some mod applied
 *     directly to a piece of gear rather than to a book. Those paths are what the three mixins cover.</li>
 * </ul>
 */
public final class EnchantGrantMarker {

    /** Root-level NBT key. Kept verbose and namespaced — it ends up in hand-authored quest JSON. */
    public static final String GRANTED_TAG = "insanetweaks_granted";

    private EnchantGrantMarker() {
    }

    /** True when this stack was minted or stamped by the pack. */
    public static boolean isGranted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean(GRANTED_TAG);
    }

    /** Stamp a stack as deliberately granted. Returns the same stack for chaining. */
    public static ItemStack markGranted(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return stack;
        }
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setBoolean(GRANTED_TAG, true);
        return stack;
    }

    /** A marked enchanted book carrying exactly the given enchantment and level. */
    public static ItemStack createGrantedBook(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantedBook.addEnchantment(book, new EnchantmentData(enchantment, level));
        return markGranted(book);
    }

    /**
     * The first quest-gated enchantment on this stack that is NOT covered by a grant marker, or null
     * when the stack is fine to use. A marked stack always returns null, and so does any stack carrying
     * no gated enchantment at all.
     *
     * <p>Reads through {@link EnchantmentHelper#getEnchantments}, which special-cases
     * {@code ENCHANTED_BOOK} and looks at {@code StoredEnchantments} — so it sees a book's payload as
     * well as a tool's live enchantments.
     */
    public static Enchantment findUngranted(ItemStack stack) {
        if (!ModConfig.enchantments.requireGrantMarker || stack == null || stack.isEmpty()) {
            return null;
        }
        if (isGranted(stack)) {
            return null;
        }
        Map<Enchantment, Integer> present = EnchantmentHelper.getEnchantments(stack);
        if (present.isEmpty()) {
            return null;
        }
        for (Enchantment enchantment : present.keySet()) {
            if (EnchantSourceGuard.isNaturallyBlocked(enchantment)) {
                return enchantment;
            }
        }
        return null;
    }
}
