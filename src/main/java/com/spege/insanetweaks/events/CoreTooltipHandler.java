package com.spege.insanetweaks.events;

import java.util.List;

import com.spege.insanetweaks.items.core.WizardryCoreItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
        if (item instanceof WizardryCoreItem) {
            List<String> tooltip = event.getToolTip();
            ((WizardryCoreItem) item).addCoreTooltip(tooltip);
        }
    }
}
