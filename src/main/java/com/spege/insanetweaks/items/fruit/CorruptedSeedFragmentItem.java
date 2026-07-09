package com.spege.insanetweaks.items.fruit;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Corrupted Seed Fragment — rare drop from high-tier parasites, ONLY while the
 * killer wears the Blessed Ring (see CorruptedFragmentDropHandler).
 * 4 fragments craft into a Corrupted Seed (recipes/corrupted_seed.json).
 */
public class CorruptedSeedFragmentItem extends Item {

    public CorruptedSeedFragmentItem() {
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "corrupted_seed_fragment"));
        this.setUnlocalizedName("corrupted_seed_fragment");
        this.setCreativeTab(CreativeTabs.MATERIALS);
        this.setMaxStackSize(16);
    }

    @Override
    @Nonnull
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.RARE;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + "A splinter of something that was never meant to sprout.");
        tooltip.add(TextFormatting.GRAY + "It only reveals itself to hands shielded by blessing.");
        tooltip.add("");
        tooltip.add(TextFormatting.GRAY + "Combine " + TextFormatting.LIGHT_PURPLE + "4 fragments"
                + TextFormatting.GRAY + " into a Corrupted Seed.");
    }
}
