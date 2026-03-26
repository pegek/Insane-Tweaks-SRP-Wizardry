package com.spege.insanetweaks.events;

import java.util.List;
import com.spege.insanetweaks.init.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class CoreTooltipHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        
        Item item = stack.getItem();
        if (item == ModItems.COST_CORE || item == ModItems.POTENCY_CORE || item == ModItems.SPEEDCAST_CORE) {
            List<String> tooltip = event.getToolTip();
            tooltip.add("");
            tooltip.add(TextFormatting.GOLD + "Combine in anvil with any armor piece,");
            tooltip.add(TextFormatting.GOLD + "maximum x2 times per core type!");
        }
    }
}
