package com.spege.insanetweaks.events;

import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.util.AdvPropertyResolver;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import com.spege.insanetweaks.util.TooltipUtils;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

/**
 * Renders the "Properties:" tooltip block for every advanced property on a stack.
 *
 * <p>Asks {@link AdvPropertyResolver} rather than testing {@code instanceof
 * ITweaksPropertyHolder}. That interface check used to be the gate, which meant this handler could
 * only ever see properties declared by our own item classes - so a Sentient Codex conferring Ashen
 * Legacy on a vanilla sword was invisible here, and a whole second handler
 * ({@code SentientCodexTooltipHandler}) existed purely to draw that one line, carefully staying
 * silent whenever this one would have spoken. Going through the resolver covers class-, stack- and
 * enchant-granted properties in one pass, which is what allowed the duplicate to be deleted.
 */
public class GlobalPropertyTooltipHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        List<String> activeProps = AdvPropertyResolver.resolve(stack);
        if (activeProps.isEmpty()) {
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
