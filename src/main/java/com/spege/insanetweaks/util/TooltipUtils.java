package com.spege.insanetweaks.util;

import java.util.List;

public class TooltipUtils {

    /**
     * Finds the best insertion index for custom tooltip lines, 
     * placing them before technical footers like NBT data or mod IDs.
     */
    public static int getInsertIdx(List<String> tooltip) {
        int insertIdx = tooltip.size();
        for (int i = tooltip.size() - 1; i >= 0; i--) {
            String line = tooltip.get(i);
            String lower = line.toLowerCase();
            if (line.contains("NBT: ") || line.contains("insanetweaks:") || lower.contains("insane tweaks") || line.trim().isEmpty()) {
                insertIdx = i;
            } else {
                break;
            }
        }
        return insertIdx;
    }
}
