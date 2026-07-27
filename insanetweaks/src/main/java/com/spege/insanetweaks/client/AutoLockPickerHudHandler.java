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
        ChargeBarRenderer.draw(
                ChargeBarRenderer.centeredLeft(res.getScaledWidth()),
                res.getScaledHeight() - ChargeBarRenderer.BAR_BOTTOM_OFFSET,
                progress,
                ChargeBarRenderer.COLOR_FILL_LOCKPICK);
    }
}
