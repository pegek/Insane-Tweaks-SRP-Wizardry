package com.spege.insanetweaks.sanctuary.gui;

import java.io.IOException;

import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketSetSanctuaryRadius;
import com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore;

import net.minecraft.client.gui.GuiPageButtonList;
import net.minecraft.client.gui.GuiSlider;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiCreativeSanctuary extends GuiContainer implements GuiPageButtonList.GuiResponder {

    private final TileEntitySanctuaryCore te;
    private GuiSlider slider;
    private int radius;
    private boolean initialised;

    public GuiCreativeSanctuary(ContainerCreativeSanctuary container) {
        super(container);
        this.te = container.getTe();
        this.xSize = 176;
        this.ySize = 100;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.radius = te.getCreativeRadius() <= 0 ? 64 : te.getCreativeRadius();
        // Vanilla GuiSlider (net.minecraft.client.gui.GuiSlider) has no width/height/prefix+suffix
        // constructor in this MCP mapping -- it takes (responder, id, x, y, name, min, max, current,
        // FormatHelper) and defaults to a fixed 150x20 size; we widen it to 160 afterwards.
        this.slider = new GuiSlider(this, 0, this.guiLeft + 8, this.guiTop + 40, "Radius", 16.0F, 256.0F,
                this.radius, new GuiSlider.FormatHelper() {
                    @Override
                    public String getText(int id, String name, float value) {
                        return name + ": " + Math.round(value);
                    }
                });
        this.slider.width = 160;
        this.buttonList.add(this.slider);
        this.initialised = true;
    }

    /** GuiResponder: called continuously as the slider is dragged. */
    @Override
    public void setEntryValue(int id, float value) {
        if (id == 0) { this.radius = Math.round(value); }
    }

    @Override public void setEntryValue(int id, boolean value) {}
    @Override public void setEntryValue(int id, String value) {}

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        // Read the slider's authoritative mapped value (16..256) directly, rather than the cached
        // `radius` from setEntryValue - inside a GuiContainer the responder callback may not fire on
        // every drag, which previously left the value stuck at its initial position.
        if (initialised && this.slider != null) {
            int r = Math.round(this.slider.getSliderValue());
            InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketSetSanctuaryRadius(te.getPos(), r));
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        this.drawDefaultBackground();
        drawGradientRect(this.guiLeft, this.guiTop, this.guiLeft + this.xSize, this.guiTop + this.ySize,
                0xF0202020, 0xF0202020);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = net.minecraft.client.resources.I18n.format("gui.insanetweaks.creative_sanctuary.title");
        this.fontRenderer.drawString(title, 8, 8, 0xE0C0FF);
        this.fontRenderer.drawString(
                net.minecraft.client.resources.I18n.format("gui.insanetweaks.creative_sanctuary.hint"),
                8, 22, 0xAAAAAA);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
    }
}
