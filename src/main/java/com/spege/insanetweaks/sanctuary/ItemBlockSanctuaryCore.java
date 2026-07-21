package com.spege.insanetweaks.sanctuary;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** ItemBlock for the Sanctuary Nexus, adding a short "what it is / how to build it" tooltip. */
public class ItemBlockSanctuaryCore extends ItemBlock {

    public ItemBlockSanctuaryCore(Block block) {
        super(block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(I18n.format("tile.insanetweaks.sanctuary_core.desc1"));
        tooltip.add(I18n.format("tile.insanetweaks.sanctuary_core.desc2"));
        tooltip.add(I18n.format("tile.insanetweaks.sanctuary_core.desc3"));
    }
}
