package com.spege.insanetweaks.client;

import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Shared "channel / charge progress" bar drawn above the hotbar.
 *
 * <p>Extracted from {@code AutoLockPickerHudHandler} when the Coiled Spring trait needed the
 * same widget with a different progress source and fill colour. Extends {@link Gui} purely
 * for access to its protected static {@code drawRect}.
 */
@SideOnly(Side.CLIENT)
public final class ChargeBarRenderer extends Gui {

    public static final int BAR_WIDTH = 80;
    public static final int BAR_HEIGHT = 6;
    /** Distance above the bottom of the screen — clears the hotbar and its item-name popup. */
    public static final int BAR_BOTTOM_OFFSET = 62;
    /** One bar height + padding, so a second bar can stack above the first. */
    public static final int BAR_STACK_STEP = 10;

    public static final int COLOR_BORDER = 0xFF000000;
    public static final int COLOR_TRACK = 0xFF3A3A3A;
    /** Auto Lock Picker gold. */
    public static final int COLOR_FILL_LOCKPICK = 0xFFE0B84A;
    /** Coiled Spring green — distinct from the lock picker so a stacked pair reads at a glance. */
    public static final int COLOR_FILL_CHARGE = 0xFF6ADF7A;

    private ChargeBarRenderer() {
    }

    /**
     * @param left     left edge in scaled-GUI pixels
     * @param top      top edge in scaled-GUI pixels
     * @param progress 0..1; values outside are clamped
     * @param fill     ARGB fill colour
     */
    public static void draw(int left, int top, float progress, int fill) {
        float clamped = progress;
        if (clamped < 0.0F) clamped = 0.0F;
        if (clamped > 1.0F) clamped = 1.0F;

        drawRect(left - 1, top - 1, left + BAR_WIDTH + 1, top + BAR_HEIGHT + 1, COLOR_BORDER);
        drawRect(left, top, left + BAR_WIDTH, top + BAR_HEIGHT, COLOR_TRACK);
        drawRect(left, top, left + (int) (BAR_WIDTH * clamped), top + BAR_HEIGHT, fill);
    }

    /** Left edge that centres the bar on a screen of the given scaled width. */
    public static int centeredLeft(int scaledWidth) {
        return (scaledWidth - BAR_WIDTH) / 2;
    }
}
