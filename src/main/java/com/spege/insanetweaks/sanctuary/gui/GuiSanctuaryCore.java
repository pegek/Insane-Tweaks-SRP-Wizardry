package com.spege.insanetweaks.sanctuary.gui;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

public class GuiSanctuaryCore extends GuiContainer {

    private static final ResourceLocation BG = new ResourceLocation("textures/gui/container/dispenser.png");
    private final ContainerSanctuaryCore container;

    public GuiSanctuaryCore(ContainerSanctuaryCore container) {
        super(container);
        this.container = container;
        this.ySize = 166;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        net.minecraft.client.renderer.GlStateManager.color(1, 1, 1, 1);
        this.mc.getTextureManager().bindTexture(BG);
        int x = (this.width - this.xSize) / 2;
        int y = (this.height - this.ySize) / 2;
        drawTexturedModalRect(x, y, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int tier = container.getTe().getTier();
        int radius = container.getTe().getEffectiveRadius();
        String state = container.getTe().isCleanseStalled() ? TextFormatting.RED + "cleanse: no fuel"
                : (container.getTe().isCleanseEnabled() ? TextFormatting.GREEN + "cleanse: on" : "cleanse: off");
        this.fontRenderer.drawString("Tier " + tier + "  R=" + radius, 8, 6, 0x404040);
        this.fontRenderer.drawString(state, 8, 72, 0x404040);
    }
}
