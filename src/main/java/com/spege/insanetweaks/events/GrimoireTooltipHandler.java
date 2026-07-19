package com.spege.insanetweaks.events;

import java.util.ArrayList;
import java.util.List;

import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.api.ITweaksPropertyHolder;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.enchant.EnchantmentGrimoire;
import com.spege.insanetweaks.util.TooltipUtils;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Renders the "Ashen Legacy" property line on Grimoire-enchanted items.
 *
 * <p>The advanced-property tooltip system ({@code GlobalPropertyTooltipHandler}) only fires
 * for items whose class implements {@link ITweaksPropertyHolder}. Grimoire can sit on any
 * vanilla item, so this handler surfaces the same property line (matching visual style) for
 * Grimoire stacks, but defers to {@code GlobalPropertyTooltipHandler} when the item is
 * already a property holder to avoid a duplicate line.
 *
 * <p>Only active when {@code ModConfig.grimoire.conferAshenLegacy} is ON - the same flag that
 * routes the drop through {@code EntityItemIndestructible}.
 */
public class GrimoireTooltipHandler {

    @SideOnly(Side.CLIENT)
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onItemTooltip(ItemTooltipEvent event) {
        if (!ModConfig.grimoire.conferAshenLegacy) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() instanceof ITweaksPropertyHolder) {
            return; // property holders are handled by GlobalPropertyTooltipHandler
        }
        if (!EnchantmentGrimoire.hasGrimoire(stack)) {
            return;
        }

        AdvPropertyRegistry.Property property = AdvPropertyRegistry.getProperty(AdvPropertyRegistry.ASHEN_LEGACY);
        if (property == null) {
            return;
        }

        boolean shiftPressed = GuiScreen.isShiftKeyDown();
        List<String> tooltip = event.getToolTip();

        // Reuse an existing "Properties:" header if some other handler already added one.
        int propertiesHeaderIdx = -1;
        for (int i = 0; i < tooltip.size(); i++) {
            String cleanLine = TextFormatting.getTextWithoutFormattingCodes(tooltip.get(i));
            if (cleanLine != null && cleanLine.startsWith("Properties:")) {
                propertiesHeaderIdx = i;
                break;
            }
        }

        List<String> lines = new ArrayList<String>();
        if (propertiesHeaderIdx == -1) {
            String shiftHint = shiftPressed
                    ? TextFormatting.DARK_GRAY + "[Showing details]"
                    : TextFormatting.DARK_GRAY + "[Press " + TextFormatting.AQUA + "SHIFT"
                            + TextFormatting.DARK_GRAY + " to show details]";
            lines.add(TextFormatting.GOLD + "Properties: " + shiftHint);
        }
        property.addTooltipLines(lines, shiftPressed);

        if (propertiesHeaderIdx != -1) {
            tooltip.addAll(propertiesHeaderIdx + 1, lines);
        } else {
            tooltip.addAll(TooltipUtils.getInsertIdx(tooltip), lines);
        }
    }
}
