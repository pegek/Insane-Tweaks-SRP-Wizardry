package com.spege.insanetweaks.integration.patchouli;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * The Insane Codex as an item stack, built WITHOUT touching Patchouli's classes.
 *
 * <p>Patchouli's book item is one generic {@code patchouli:guide_book}; which book it opens is the NBT
 * string {@code patchouli:book}. Building that by registry name and NBT key keeps this class safe to
 * load in a pack without Patchouli - it just answers {@link ItemStack#EMPTY} there - so the sanctum
 * filler and the crafting recipe can use it unguarded.
 *
 * <p>Its registry name contains "book", which is what Akashic Tome's default whitelist matches.
 */
public final class CodexBook {

    public static final String BOOK_ID = "insanetweaks:codex";

    private static final ResourceLocation GUIDE_BOOK = new ResourceLocation("patchouli", "guide_book");
    private static final String TAG_BOOK = "patchouli:book";

    private CodexBook() {
    }

    /** Patchouli's guide book item, or null when Patchouli is not installed. */
    public static Item guideBook() {
        return ForgeRegistries.ITEMS.getValue(GUIDE_BOOK);
    }

    /** A fresh Codex, or EMPTY without Patchouli. */
    public static ItemStack stack() {
        Item book = guideBook();
        if (book == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(book);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(TAG_BOOK, BOOK_ID);
        stack.setTagCompound(tag);
        return stack;
    }

    /** Whether this stack is a Codex (and not some other Patchouli book). */
    public static boolean isCodex(ItemStack stack) {
        Item book = guideBook();
        return book != null && !stack.isEmpty() && stack.getItem() == book && stack.hasTagCompound()
                && BOOK_ID.equals(stack.getTagCompound().getString(TAG_BOOK));
    }
}
