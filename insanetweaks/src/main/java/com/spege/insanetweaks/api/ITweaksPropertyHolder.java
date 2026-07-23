package com.spege.insanetweaks.api;

import net.minecraft.item.ItemStack;
import java.util.List;

/**
 * Interface for items that support advanced properties (like Ashen Legacy, Ethereal Shell).
 */
public interface ITweaksPropertyHolder {

    /**
     * Zwraca listę aktywnych kluczy z AdvPropertyRegistry dla tego ItemStacka.
     */
    List<String> getActiveAdvProperties(ItemStack stack);

    /**
     * Wygodna metoda boolowska sprawdzająca obecność property.
     */
    default boolean hasAdvProperty(ItemStack stack, String propId) {
        List<String> props = getActiveAdvProperties(stack);
        return props != null && props.contains(propId);
    }
}
