package com.spege.insanetweaks.worldgen.sanctum;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * The ritual room's hint: a written book whose three pages (what it does, how it awakens, what is
 * missing and how to make it) are text components translated from {@code en_us.lang}
 * ({@code book.insanetweaks.ritual.<id>.p<n>.head/.body}), so they follow the reader's language.
 */
public final class SanctumRitualBook {

    private static final int PAGES = 3;

    private SanctumRitualBook() {
    }

    public static ItemStack create(int ritual) {
        String base = "book.insanetweaks.ritual." + SanctumRituals.id(ritual) + ".p";
        NBTTagList pages = new NBTTagList();
        for (int n = 1; n <= PAGES; n++) {
            pages.appendTag(new NBTTagString("[\"\",{\"translate\":\"" + base + n + ".head\",\"bold\":true},\"\\n\\n\","
                    + "{\"translate\":\"" + base + n + ".body\"}]"));
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", "Ritual Notes");
        tag.setString("author", "A Departed Sage");
        tag.setTag("pages", pages);
        tag.setBoolean("resolved", true);
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.setTagCompound(tag);
        return book;
    }
}
