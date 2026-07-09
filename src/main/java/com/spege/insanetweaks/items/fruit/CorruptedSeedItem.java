package com.spege.insanetweaks.items.fruit;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.entities.EntityCorruptedSapling;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Corrupted Seed — planted (right-click on top of a block) it becomes a LIVING
 * EntityCorruptedSapling that parasites will attack. Tooltip doubles as the
 * growing manual, in the Living/Sentient gear flavor style.
 */
public class CorruptedSeedItem extends Item {

    public CorruptedSeedItem() {
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "corrupted_seed"));
        this.setUnlocalizedName("corrupted_seed");
        this.setCreativeTab(CreativeTabs.MATERIALS);
        this.setMaxStackSize(1);
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public EnumActionResult onItemUse(@Nonnull EntityPlayer player, @Nonnull World world,
            @Nonnull BlockPos pos, @Nonnull EnumHand hand, @Nonnull EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        if (facing != EnumFacing.UP) {
            return EnumActionResult.PASS;
        }
        if (world.isRemote) {
            return EnumActionResult.SUCCESS;
        }

        BlockPos above = pos.up();
        if (!world.isAirBlock(above)) {
            return EnumActionResult.FAIL;
        }

        EntityCorruptedSapling sapling = new EntityCorruptedSapling(world);
        sapling.setPosition(above.getX() + 0.5D, above.getY(), above.getZ() + 0.5D);
        sapling.setOwnerId(player.getUniqueID());
        world.spawnEntity(sapling);
        world.playSound(null, above, SoundEvents.BLOCK_GRASS_PLACE, SoundCategory.BLOCKS, 1.0f, 0.7f);

        player.getHeldItem(hand).shrink(1);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public boolean hasEffect(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    @Nonnull
    public net.minecraftforge.common.IRarity getForgeRarity(@Nonnull ItemStack stack) {
        return EnumRarity.EPIC;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(TextFormatting.DARK_PURPLE + "It pulses. It listens. It waits for tainted soil.");
        tooltip.add("");
        tooltip.add(TextFormatting.GOLD + "How to grow:");
        tooltip.add(TextFormatting.GRAY + "1. Plant it (right-click the ground) inside an active");
        tooltip.add(TextFormatting.GRAY + "   parasite infestation.");
        tooltip.add(TextFormatting.GRAY + "2. Stay close, wearing the " + TextFormatting.LIGHT_PURPLE
                + "Blessed Ring" + TextFormatting.GRAY + " — it grows only");
        tooltip.add(TextFormatting.GRAY + "   under your protection.");
        tooltip.add(TextFormatting.GRAY + "3. The hive will try to reclaim it. " + TextFormatting.RED
                + "Defend the sapling.");
        tooltip.add(TextFormatting.GRAY + "4. In time it bears a " + TextFormatting.DARK_PURPLE
                + "Corrupted Fruit" + TextFormatting.GRAY + ".");
        tooltip.add("");
        tooltip.add(TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC
                + "If it dies, the seed is lost with it.");
    }
}
