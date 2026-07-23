package com.spege.insanetweaks.items;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemBook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class GoldenBookItem extends ItemBook {

    @SuppressWarnings("null")
    public GoldenBookItem() {
        super();
        this.setUnlocalizedName("golden_book");
        this.setMaxStackSize(16);
        this.setMaxDamage(0);
        this.setCreativeTab(CreativeTabs.MISC);
    }

    @Override
    @Nonnull
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        return TextFormatting.GOLD + "Golden Book";
    }

    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World worldIn, @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        tooltip.add(TextFormatting.GRAY + "A book with enchantability of 100.");
        tooltip.add(TextFormatting.DARK_GRAY + "Once enchanted, it will transform into an Enchanted Book.");
    }

    @Override
    public boolean isDamageable() {
        return false;
    }

    @Override
    public int getItemEnchantability() {
        return 100;
    }

    /**
     * KEY: Accept ALL enchantments regardless of their type.
     * By default Forge checks enchantment.type.canEnchantItem(this),
     * limiting enchantments to specific item types (sword, armor, etc.).
     * We override this so the Golden Book accepts every enchantment.
     */
    @Override
    public boolean canApplyAtEnchantingTable(@Nonnull ItemStack stack, @Nonnull Enchantment enchantment) {
        return true;
    }
}
