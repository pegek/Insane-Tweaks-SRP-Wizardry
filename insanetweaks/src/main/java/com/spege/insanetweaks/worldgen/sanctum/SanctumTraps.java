package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.config.ModConfig;

import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.util.ResourceLocation;

/**
 * Loads a sanctum trap dispenser with 2-4 scrolls of one hostile spell, picked by position from
 * {@code Trap Spells}. EBW casts a scroll fired from a dispenser; spells it would not cast from
 * one ({@link Spell#canBeCastBy(TileEntityDispenser)}) are skipped, and no usable spell leaves the
 * dispenser empty - a dud trap, never a crash.
 */
public final class SanctumTraps {

    private SanctumTraps() {
    }

    public static void load(IInventory dispenser, long seed, int x, int y, int z) {
        List<Spell> usable = new ArrayList<Spell>();
        for (String id : ModConfig.arcaneSanctum.trapSpells) {
            Spell spell = Spell.registry.getValue(new ResourceLocation(id.trim()));
            if (spell != null && spell.canBeCastBy((TileEntityDispenser) null)) {
                usable.add(spell);
            }
        }
        if (usable.isEmpty() || dispenser.getSizeInventory() == 0) {
            return;
        }
        Spell spell = usable.get(Math.min(usable.size() - 1, (int) (SanctumNoise.at(seed, x, y, z, 90) * usable.size())));
        int count = 2 + (int) (SanctumNoise.at(seed, x, y, z, 91) * 3);
        dispenser.setInventorySlotContents(0, new ItemStack(WizardryItems.scroll, count, spell.metadata()));
    }
}
