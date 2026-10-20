package com.spege.insanetweaks.worldgen.sanctum;

import java.util.List;

import com.spege.insanetweaks.init.ModElements;

import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellProperties;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * Rare spell books in a sanctum's EBW bookshelves: per roll a chance for one book, then 50% an
 * Abomination spell, 25% any Master, 25% any Advanced - each drawn from spells enabled for
 * treasure. An empty bucket falls back to Advanced. Deterministic per shelf via SanctumNoise.
 */
public final class SanctumBooks {

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
