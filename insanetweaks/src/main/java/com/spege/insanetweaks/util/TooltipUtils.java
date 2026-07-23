package com.spege.insanetweaks.util;

import net.minecraft.util.text.TextFormatting;
import java.util.List;

public class TooltipUtils {

    /**
     * Finds the best insertion index for custom tooltip lines, 
     * placing them before technical footers like NBT data or mod IDs.
     */
    public static int getInsertIdx(List<String> tooltip) {
        int insertIdx = tooltip.size();
        
        for (int i = tooltip.size() - 1; i >= 0; i--) {
            // Zdejmujemy niewidzialne kody kolorów (np. §9, §o), żeby bezpiecznie czytać tekst
            String cleanLine = TextFormatting.getTextWithoutFormattingCodes(tooltip.get(i));
            if (cleanLine == null) continue;
            
            String lower = cleanLine.toLowerCase();
            
            // Sprawdzamy czysty tekst
            if (cleanLine.startsWith("NBT: ") || lower.contains("insanetweaks:") || lower.contains("insane tweaks") || cleanLine.trim().isEmpty()) {
                insertIdx = i;
            } else {
                break;
            }
        }
        
        return insertIdx;
    }
}
