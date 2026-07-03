package com.spege.insanetweaks.events;

import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.api.ITweaksPropertyHolder;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import com.spege.insanetweaks.util.TooltipUtils;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

public class GlobalPropertyTooltipHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ITweaksPropertyHolder)) {
            return;
        }

        ITweaksPropertyHolder holder = (ITweaksPropertyHolder) stack.getItem();
        List<String> activeProps = holder.getActiveAdvProperties(stack);
        if (activeProps == null || activeProps.isEmpty()) {
            return;
        }

        boolean shiftPressed = GuiScreen.isShiftKeyDown();
        int insertIdx = TooltipUtils.getInsertIdx(event.getToolTip());

        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        
        int propertiesHeaderIdx = -1;
        for (int i = 0; i < event.getToolTip().size(); i++) {
            String cleanLine = TextFormatting.getTextWithoutFormattingCodes(event.getToolTip().get(i));
            if (cleanLine != null && cleanLine.startsWith("Properties:")) {
                propertiesHeaderIdx = i;
                break;
            }
        }

        if (propertiesHeaderIdx == -1) {
            String shiftHint = shiftPressed
                    ? TextFormatting.DARK_GRAY + "[Showing details]"
                    : TextFormatting.DARK_GRAY + "[Press " + TextFormatting.AQUA + "SHIFT"
                            + TextFormatting.DARK_GRAY + " to show details]";
            lines.add(TextFormatting.GOLD + "Properties: " + shiftHint);
        }

        for (String propId : activeProps) {
            AdvPropertyRegistry.Property property = AdvPropertyRegistry.getProperty(propId);
            if (property != null) {
                property.addTooltipLines(lines, shiftPressed);
            }
        }
        
        if (!lines.isEmpty()) {
            if (propertiesHeaderIdx != -1) {
                event.getToolTip().addAll(propertiesHeaderIdx + 1, lines);
            } else if (lines.size() > 1) {
                event.getToolTip().addAll(insertIdx, lines);
            }
        }
    }
}
