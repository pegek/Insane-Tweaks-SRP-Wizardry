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
        this.ySize = 166;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground(); // dark overlay (fixes HEI warning)
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        // panel body
        drawGradientRect(x, y, x + xSize, y + ySize, 0xF0202028, 0xF03A2A3A);
        drawRect(x, y, x + xSize, y + 1, 0xFF6A4A7A);            // top accent
        // core slots: fuel (26,35) + upgrades (80,98,116,134 ; 35)
        drawSocket(x + 26, y + 35);
        for (int i = 0; i < TileEntitySanctuaryCore.UPGRADE_SLOTS; i++) {
            drawSocket(x + 80 + i * 18, y + 35);
        }
        // player inventory sockets
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) { drawSocket(x + 8 + col * 18, y + 84 + row * 18); }
        }
        for (int col = 0; col < 9; col++) { drawSocket(x + 8 + col * 18, y + 142); }
    }

    /** An 18x18 recessed slot background at the container slot origin (x,y are the -1 border corner). */
    private void drawSocket(int sx, int sy) {
        drawRect(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF120C16); // dark inset
        drawRect(sx, sy, sx + 16, sy + 16, 0xFF473557);          // slot face
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        TileEntitySanctuaryCore te = container.getTe();
        int tier = te.getTier();
        int radius = te.getEffectiveRadius();
        SanctuaryStatus status = te.getStatus();
        CleanseState cleanse = CleanseState.of(tier, te.isCleanseEnabled(), te.isCleanseStalled());

        int white = 0xFFFFFF;
        this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.title"), 8, 6, 0xE0D0F0);
        this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.tier", tier), 8, 18, white);

        // protection line (colored)
        if (tier >= 1) {
            this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.protection_on", radius), 8, 30, 0x55FF55);
        } else {
            this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.protection_off"), 8, 30, 0xAAAAAA);
        }

        // cleanse line (colored)
        switch (cleanse) {
            case RUNNING: this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.cleanse_running"), 8, 42, 0x55FF55); break;
            case STALLED: this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.cleanse_stalled"), 8, 42, 0xFF5555); break;
            default:      this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.cleanse_off"), 8, 42, 0xAAAAAA); break;
        }

        // hint when inactive
        if (status == SanctuaryStatus.NO_PYRAMID) {
            this.fontRenderer.drawSplitString(I18n.format("gui.insanetweaks.sanctuary.hint_pyramid"), 8, 56, 160, 0xC0A0C0);
        } else if (status == SanctuaryStatus.DIM_BLACKLISTED) {
            this.fontRenderer.drawSplitString(I18n.format("gui.insanetweaks.sanctuary.hint_blacklist"), 8, 56, 160, 0xC0A0C0);
        }

        // fuel gauge (from window property) drawn near the fuel slot
        int fuel = container.getClientFuel();
        this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sanctuary.fuel", fuel), 8, 68, 0xC0C0C0);
        drawFuelGauge(8, 78, fuel);

        // slot tooltips
        drawSlotTooltip(mouseX, mouseY, 26, 35, I18n.format("gui.insanetweaks.sanctuary.slot_fuel"));
        for (int i = 0; i < TileEntitySanctuaryCore.UPGRADE_SLOTS; i++) {
            drawSlotTooltip(mouseX, mouseY, 80 + i * 18, 35, I18n.format("gui.insanetweaks.sanctuary.slot_upgrade"));
        }
    }

    /** A simple 8-segment bar; full at >=64 conversions remaining. */
    private void drawFuelGauge(int gx, int gy, int fuel) {
        int segments = 8;
        int filled = Math.min(segments, (fuel + 7) / 8); // 8 conversions per segment, ceil
        for (int i = 0; i < segments; i++) {
            int color = (i < filled) ? 0xFF55FF55 : 0xFF303030;
            int sx = gx + i * 6;
            drawRect(sx, gy, sx + 5, gy + 5, color);
        }
    }

    /**
     * mouseX/Y are screen coords; slotX/Y are container-local slot origins.
     * drawHoveringText is called inside the foreground layer (already translated by the GUI
     * origin), so it must receive GUI-local mouse coords.
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
