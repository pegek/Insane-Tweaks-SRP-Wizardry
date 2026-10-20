package com.spege.insanetweaks.worldgen.sanctum;

import java.util.List;
import java.util.Random;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModElements;
import com.spege.insanetweaks.init.ModItems;
import com.spege.insanetweaks.items.PropertyBookItem;

import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellProperties;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * Rare spell books in a sanctum's EBW bookshelves: per roll a chance for one book, then 50% an
 * Abomination spell, 25% any Master, 25% any Advanced - each drawn from spells enabled for
 * treasure. An empty bucket falls back to Advanced. Deterministic per shelf via SanctumNoise.
 */
public final class SanctumBooks {

    private static List<ItemStack> insaneBooks;
    private static List<Spell> abomination;
    private static List<Spell> master;
    private static List<Spell> advanced;

    private SanctumBooks() {
    }

    public static void fill(IInventory shelf, long seed, int x, int y, int z, int rolls, double chance) {
        for (int i = 0; i < rolls; i++) {
            if (SanctumNoise.at(seed, x, y, z, 40 + i) >= chance) {
                continue;
            }
            Spell spell = pick(SanctumNoise.at(seed, x, y, z, 50 + i), SanctumNoise.at(seed, x, y, z, 60 + i));
            if (spell == null) {
                continue;
            }
            for (int slot = 0; slot < shelf.getSizeInventory(); slot++) {
                if (shelf.getStackInSlot(slot).isEmpty()) {
                    shelf.setInventorySlotContents(slot, new ItemStack(WizardryItems.spell_book, 1, spell.metadata()));
                    break;
                }
            }
        }
    }

    /**
     * Extra rolls (spec 14.7): per roll a treasure-allowed enchanted book or, rarer, a book from the
     * insanetweaks pool - every insanetweaks enchantment at level 1 (the Sentient Codex curse
     * included) and every book-grantable Property Book, picked uniformly. A deliberate, narrow
     * exception to the quest gate (EnchantSourceGuard): pre-built books written straight into a
     * sanctum shelf, nothing else. Deterministic per shelf.
     */
    public static void fillExtras(IInventory shelf, long seed, int x, int y, int z, int rolls, double treasure,
            double insane) {
        for (int i = 0; i < rolls; i++) {
            int kind = SanctumShelfRoll.kind(SanctumNoise.at(seed, x, y, z, 130 + i), treasure, insane);
            ItemStack book = ItemStack.EMPTY;
            if (kind == SanctumShelfRoll.TREASURE) {
                book = EnchantmentHelper.addRandomEnchantment(new Random(SanctumNoise.seedAt(seed, x, y, z) + i),
                        new ItemStack(Items.BOOK), 30, true);
            } else if (kind == SanctumShelfRoll.INSANE) {
                List<ItemStack> pool = insanePool();
                if (!pool.isEmpty()) {
                    int pick = Math.min(pool.size() - 1, (int) (SanctumNoise.at(seed, x, y, z, 140 + i) * pool.size()));
                    book = pool.get(pick).copy();
                }
            }
            if (book.isEmpty()) {
                continue;
            }
            for (int slot = 0; slot < shelf.getSizeInventory(); slot++) {
                if (shelf.getStackInSlot(slot).isEmpty()) {
                    shelf.setInventorySlotContents(slot, book);
                    break;
                }
            }
        }
    }

    private static synchronized List<ItemStack> insanePool() {
        if (insaneBooks != null) {
            return insaneBooks;
        }
        List<ItemStack> pool = new java.util.ArrayList<ItemStack>();
        for (Enchantment e : ForgeRegistries.ENCHANTMENTS.getValuesCollection()) {
            if (e.getRegistryName() != null && InsaneTweaksMod.MODID.equals(e.getRegistryName().getResourceDomain())) {
                pool.add(ItemEnchantedBook.getEnchantedItemStack(new EnchantmentData(e, 1)));
            }
        }
        if (ModConfig.modules.enablePropertyBooks) {
            for (AdvPropertyRegistry.Property p : AdvPropertyRegistry.getAll()) {
                if (p.bookGrantable) {
                    pool.add(PropertyBookItem.create(ModItems.PROPERTY_BOOK, p.id));
                }
            }
        }
        insaneBooks = pool;
        return pool;
    }

    private static synchronized void init() {
        if (advanced != null) {
            return;
        }
        abomination = Spell.getSpells(new java.util.function.Predicate<Spell>() {
            @Override
            public boolean test(Spell s) {
                return ModElements.ABOMINATION != null && s.getElement() == ModElements.ABOMINATION && treasure(s);
            }
        });
        master = Spell.getSpells(new java.util.function.Predicate<Spell>() {
            @Override
            public boolean test(Spell s) {
                return s.getTier() == Tier.MASTER && treasure(s);
            }
        });
        advanced = Spell.getSpells(new java.util.function.Predicate<Spell>() {
            @Override
            public boolean test(Spell s) {
                return s.getTier() == Tier.ADVANCED && treasure(s);
            }
        });
    }

    private static boolean treasure(Spell s) {
        return s.isEnabled(SpellProperties.Context.TREASURE);
    }

    private static Spell pick(double bucket, double index) {
        init();
        List<Spell> list = bucket < 0.5D ? abomination : bucket < 0.75D ? master : advanced;
        if (list.isEmpty()) {
            list = advanced;
        }
        if (list.isEmpty()) {
            return null;
        }
        return list.get(Math.min(list.size() - 1, (int) (index * list.size())));
    }
}
