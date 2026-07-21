package com.spege.insanetweaks.sanctuary.gui;

import com.spege.insanetweaks.sanctuary.CleanseState;
import com.spege.insanetweaks.sanctuary.SanctuaryStatus;
import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.resources.I18n;

public class GuiSanctuaryCore extends GuiContainer {

    private final ContainerSanctuaryCore container;

    public GuiSanctuaryCore(ContainerSanctuaryCore container) {
        super(container);
        this.container = container;
        this.xSize = 176;
        this.ySize = 210;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        drawGradientRect(x, y, x + xSize, y + ySize, 0xF0202028, 0xF03A2A3A);
        drawRect(x, y, x + xSize, y + 1, 0xFF6A4A7A);
        drawSocket(x + 26, y + 104);
        for (int i = 0; i < TileEntitySanctuaryCore.UPGRADE_SLOTS; i++) {
            drawSocket(x + 80 + i * 18, y + 104);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) { drawSocket(x + 8 + col * 18, y + 128 + row * 18); }
        }
        for (int col = 0; col < 9; col++) { drawSocket(x + 8 + col * 18, y + 186); }
    }

    /** An 18x18 recessed slot background at the container slot origin (sx,sy are the slot's top-left). */
    private void drawSocket(int sx, int sy) {
        drawRect(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF120C16);
        drawRect(sx, sy, sx + 16, sy + 16, 0xFF473557);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        TileEntitySanctuaryCore te = container.getTe();
        int tier = te.getTier();
        int radius = te.getEffectiveRadius();
        SanctuaryStatus status = te.getStatus();
        CleanseState cleanse = CleanseState.of(tier, te.isCleanseEnabled(), te.isCleanseStalled());

        this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.title"), 8, 8, 0xE0D0F0);
        String owner = te.getOwnerName();
        if (owner != null && !owner.isEmpty()) {
            String label = I18n.format("gui.insanetweaks.sanctuary.owner", owner);
            this.fontRenderer.drawString(label, this.xSize - 8 - this.fontRenderer.getStringWidth(label), 8, 0xB090C0);
        }
        this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.tier", tier), 8, 22, 0xFFFFFF);

        if (tier >= 1) {
            this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.protection_on", radius), 8, 36, 0x55FF55);
        } else {
            this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.protection_off"), 8, 36, 0xAAAAAA);
        }

        switch (cleanse) {
            case RUNNING: this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.cleanse_running"), 8, 50, 0x55FF55); break;
            case STALLED: this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.cleanse_stalled"), 8, 50, 0xFF5555); break;
            default:      this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.cleanse_off"), 8, 50, 0xAAAAAA); break;
        }

        // Row at y=64: active -> fuel + gauge; inactive -> the actionable hint instead.
        // Fuel is irrelevant while there is no active pyramid, so they never need to coexist.
        if (status == SanctuaryStatus.NO_PYRAMID) {
            this.fontRenderer.drawSplitString(I18n.format("gui.insanetweaks.sanctuary.hint_pyramid"), 8, 64, 158, 0xC0A0C0);
        } else if (status == SanctuaryStatus.DIM_BLACKLISTED) {
            this.fontRenderer.drawSplitString(I18n.format("gui.insanetweaks.sanctuary.hint_blacklist"), 8, 64, 158, 0xC0A0C0);
        } else {
            int fuel = container.getClientFuel();
            this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.fuel", fuel), 8, 64, 0xC0C0C0);
            drawFuelGauge(8, 76, fuel);
        }

        drawSlotTooltip(mouseX, mouseY, 26, 104, I18n.format("gui.insanetweaks.sanctuary.slot_fuel"));
        for (int i = 0; i < TileEntitySanctuaryCore.UPGRADE_SLOTS; i++) {
            drawSlotTooltip(mouseX, mouseY, 80 + i * 18, 104, I18n.format("gui.insanetweaks.sanctuary.slot_upgrade"));
        }
    }

    /** A simple 8-segment bar; full at >=64 conversions remaining. */
    private void drawFuelGauge(int gx, int gy, int fuel) {
        int segments = 8;
        int filled = Math.min(segments, (fuel + 7) / 8);
        for (int i = 0; i < segments; i++) {
            int color = (i < filled) ? 0xFF55FF55 : 0xFF303030;
            int sx = gx + i * 6;
            drawRect(sx, gy, sx + 5, gy + 5, color);
        }
    }

    /**
     * mouseX/Y are screen coords; slotX/Y are container-local slot origins.
     * drawHoveringText runs inside the foreground layer (already translated by the GUI origin),
     * so it must receive GUI-local mouse coords.
     */
    private void drawSlotTooltip(int mouseX, int mouseY, int slotX, int slotY, String text) {
        int left = (this.width - this.xSize) / 2;
        int top = (this.height - this.ySize) / 2;
        int gx = left + slotX;
        int gy = top + slotY;
        if (mouseX >= gx && mouseX < gx + 16 && mouseY >= gy && mouseY < gy + 16) {
            drawHoveringText(java.util.Collections.singletonList(text), mouseX - left, mouseY - top);
        }
    }
}
