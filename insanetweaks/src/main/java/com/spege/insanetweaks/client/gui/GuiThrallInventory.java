package com.spege.insanetweaks.client.gui;

import com.spege.insanetweaks.entities.EntityThrallMinion;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * GUI screen for the thrall's 27-slot inventory (3×9, matching a single chest),
 * combined with the player inventory.
 * Opens via IGuiHandler (Forge container sync).
 */
@SideOnly(Side.CLIENT)
@SuppressWarnings("null")
public class GuiThrallInventory extends GuiContainer {

    private static final ResourceLocation CHEST_GUI_TEXTURE =
            new ResourceLocation("textures/gui/container/generic_54.png");

    private final int inventoryRows = 3; // 3 rows × 9 cols = 27 slots

    public GuiThrallInventory(EntityPlayer player, EntityThrallMinion thrall) {
        super(new ThrallContainer(player, thrall.getThrallInventory(), thrall.getEntityId()));
        this.ySize = 114 + this.inventoryRows * 18; // standard chest formula
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(CHEST_GUI_TEXTURE);

        int i = (this.width - this.xSize) / 2;
        int j = (this.height - this.ySize) / 2;

        // Draw thrall inventory rows (3 rows)
        this.drawTexturedModalRect(i, j, 0, 0, this.xSize, this.inventoryRows * 18 + 17);
        this.drawTexturedModalRect(i, j + this.inventoryRows * 18 + 17, 0, 126, this.xSize, 96);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRenderer.drawString("Thrall", 8, 6, 0x404040);
        this.fontRenderer.drawString(
                this.mc.player.inventory.getDisplayName().getUnformattedText(),
                8, this.ySize - 96 + 2, 0x404040);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
