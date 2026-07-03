package com.spege.insanetweaks.api;

import com.spege.insanetweaks.util.PropertyDescriptions;
import net.minecraft.util.text.TextFormatting;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class AdvPropertyRegistry {

    public static class Property {
        public final String id;
        public final String displayName;
        public final TextFormatting color;

        public Property(String id, String displayName, TextFormatting color) {
            this.id = id;
            this.displayName = displayName;
            this.color = color;
        }

        public void addTooltipLines(List<String> tooltip, boolean isShiftPressed) {
            tooltip.add(this.color + "- " + this.displayName);
            if (isShiftPressed) {
                String desc = PropertyDescriptions.getDescription(this.id);
                if (desc != null) {
                    tooltip.add(TextFormatting.GRAY + "" + TextFormatting.ITALIC + "  " + desc);
                }
            }
        }
    }

    private static final Map<String, Property> REGISTRY = new HashMap<>();

    public static final String ASHEN_LEGACY = "ashen_legacy";
    public static final String ARMOR_LAST_STAND = "armor_last_stand";
    public static final String ETHEREAL_SHELL = "ethereal_shell";
    public static final String ETHEREAL_SHELL_AWAKENED = "ethereal_shell_awakened";

    static {
        REGISTRY.put(ASHEN_LEGACY, new Property(ASHEN_LEGACY, "Ashen Legacy", TextFormatting.GOLD));
        REGISTRY.put(ARMOR_LAST_STAND, new Property(ARMOR_LAST_STAND, "Grave Defiance", TextFormatting.GOLD));
        REGISTRY.put(ETHEREAL_SHELL, new Property(ETHEREAL_SHELL, "Veil of Stasis", TextFormatting.DARK_GRAY));
        REGISTRY.put(ETHEREAL_SHELL_AWAKENED, new Property(ETHEREAL_SHELL_AWAKENED, "Awakened Veil of Stasis", TextFormatting.LIGHT_PURPLE));
    }

    public static Property getProperty(String id) {
        return REGISTRY.get(id);
    }
}
