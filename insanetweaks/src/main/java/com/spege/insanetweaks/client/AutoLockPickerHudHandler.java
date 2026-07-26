package com.spege.insanetweaks.client;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.items.AutoLockPickerItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Draws the Auto Lock Picker's channel progress bar above the hotbar. Client only; registered on
 * the Forge bus from {@code InsaneTweaksMod#preInit} when running on the client and the module is
 * on.
 *
 * <p>Extends {@link Gui} purely for access to its protected static {@code drawRect}.
 *
 * <p>The progress comes straight from vanilla's active-hand counter, and the total from the item's
 * own NBT — both sides ran the same computation in
 * {@link AutoLockPickerItem#onItemUse}, so nothing here needs a packet.
 */
@SideOnly(Side.CLIENT)
public class AutoLockPickerHudHandler extends Gui {

    private static final int BAR_WIDTH = 80;
    private static final int BAR_HEIGHT = 6;
    /** Distance above the bottom of the screen — clears the hotbar and its item-name popup. */
    private static final int BAR_BOTTOM_OFFSET = 62;

    private static final int COLOR_BORDER = 0xFF000000;
    private static final int COLOR_TRACK = 0xFF3A3A3A;
    private static final int COLOR_FILL = 0xFFE0B84A;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL
                || !ModConfig.modules.enableAutoLockPicker
                || !ModConfig.autoLockPicker.showProgressBar) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null || !player.isHandActive() || mc.gameSettings.hideGUI) {
            return;
        }

        ItemStack active = player.getActiveItemStack();
        if (active.isEmpty() || !(active.getItem() instanceof AutoLockPickerItem)) {
            return;
        }

        int total = active.getItem().getMaxItemUseDuration(active);
        if (total <= 0) {
            return;
        }
        float progress = 1.0F - player.getItemInUseCount() / (float) total;
        if (progress <= 0.0F) {
            return;
        }
        if (progress > 1.0F) {
            progress = 1.0F;
        }

        ScaledResolution res = new ScaledResolution(mc);
        int left = (res.getScaledWidth() - BAR_WIDTH) / 2;
        int top = res.getScaledHeight() - BAR_BOTTOM_OFFSET;

        drawRect(left - 1, top - 1, left + BAR_WIDTH + 1, top + BAR_HEIGHT + 1, COLOR_BORDER);
        drawRect(left, top, left + BAR_WIDTH, top + BAR_HEIGHT, COLOR_TRACK);
        drawRect(left, top, left + (int) (BAR_WIDTH * progress), top + BAR_HEIGHT, COLOR_FILL);
    }
}
