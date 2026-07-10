package com.spege.insanetweaks.client.gui;

import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketSentinelCommand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;

/**
 * F4 (spec 2026-07-10): ONE screen for the Sentinel — command buttons on the left
 * margin plus the 20-slot loot inventory inline as a real container (vanilla slot
 * sync + shift-click withdrawal). Replaces the old two-hop flow (control screen ->
 * read-only NBT-snapshot loot screen).
 *
 * Opened server-side via IGuiHandler (GUI_ID_SENTINEL); command buttons send the
 * usual PacketSentinelCommand actions WITHOUT closing the screen.
 */
@SuppressWarnings("null")
public class GuiSentinelControl extends GuiContainer {

    private static final ResourceLocation CHEST_GUI_TEXTURE =
            new ResourceLocation("textures/gui/container/generic_54.png");
    private static final int LOOT_ROWS = 4;

    private final int entityId;
    private GuiButton followButton;
    private GuiButton guardButton;

    public GuiSentinelControl(SentinelLootContainer container, int entityId) {
        super(container);
        this.entityId = entityId;
        this.xSize = 176;
        this.ySize = LOOT_ROWS * 18 + 124; // 196: 4 loot rows + player inventory section
    }

    @Override
    public void initGui() {
        super.initGui();
        int left = this.guiLeft - 105;
        int top = this.guiTop + 18;
        this.buttonList.clear();
        this.followButton = new GuiButton(0, left, top,
                100, 20, I18n.format("gui.insanetweaks.sentinel.action.follow"));
        this.guardButton = new GuiButton(1, left, top + 24,
                100, 20, I18n.format("gui.insanetweaks.sentinel.action.guard_here"));
        this.buttonList.add(this.followButton);
        this.buttonList.add(this.guardButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        // Commands do NOT close the screen — the mode label updates live.
        if (button.id == 0) {
            InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketSentinelCommand(this.entityId,
                    PacketSentinelCommand.ACTION_FOLLOW));
            return;
        }
        if (button.id == 1) {
            InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketSentinelCommand(this.entityId,
                    PacketSentinelCommand.ACTION_GUARD_HERE));
        }
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
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, LOOT_ROWS * 18 + 17);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop + LOOT_ROWS * 18 + 17, 0, 126, this.xSize, 96);

        // Mask the texture's unused slot outlines (columns 0-1 and 7-8 of each loot row) —
        // the loot grid is 5 columns wide, centered on texture columns 2-6.
        int slotTopY = this.guiTop + 17;
        drawRect(this.guiLeft + 7, slotTopY, this.guiLeft + 7 + 2 * 18 + 1, slotTopY + LOOT_ROWS * 18 + 1, 0xFFC6C6C6);
        drawRect(this.guiLeft + 7 + 7 * 18, slotTopY, this.guiLeft + 7 + 9 * 18 + 2, slotTopY + LOOT_ROWS * 18 + 1, 0xFFC6C6C6);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("gui.insanetweaks.sentinel.title"), 8, 6, 0x404040);

        EntitySentinel sentinel = this.getSentinel();
        if (sentinel != null) {
            String modeKey = "gui.insanetweaks.sentinel.mode." + sentinel.getCommandMode().getTranslationSuffix();
            String status = I18n.format(modeKey)
                    + "  " + (int) sentinel.getHealth() + "/" + (int) sentinel.getMaxHealth() + " HP";
            this.fontRenderer.drawString(status, 8, this.ySize - 94, 0x404040);

            BlockPos anchor = sentinel.getGuardAnchor();
            if (anchor != null) {
                // Anchor coords in the left button margin, below the buttons.
                String anchorText = I18n.format("gui.insanetweaks.sentinel.anchor.coords",
                        anchor.getX(), anchor.getY(), anchor.getZ());
                this.fontRenderer.drawString(anchorText, -105, 68, 0xA0A0A0);
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (this.getSentinel() == null) {
            this.mc.player.closeScreen();
        }
    }

    private EntitySentinel getSentinel() {
        if (Minecraft.getMinecraft().world == null) {
            return null;
        }
        if (!(Minecraft.getMinecraft().world.getEntityByID(this.entityId) instanceof EntitySentinel)) {
            return null;
        }
        return (EntitySentinel) Minecraft.getMinecraft().world.getEntityByID(this.entityId);
    }
}
