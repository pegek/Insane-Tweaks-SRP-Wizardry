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
    private GuiButton stanceButton;
    private GuiButton radiusDownButton;
    private GuiButton radiusUpButton;
    private GuiButton depositButton;
    private GuiButton pickupButton;

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
        int top = this.guiTop + 8;
        this.buttonList.clear();
        this.followButton = new GuiButton(0, left, top,
                100, 20, I18n.format("gui.insanetweaks.sentinel.action.follow"));
        this.guardButton = new GuiButton(1, left, top + 24,
                100, 20, I18n.format("gui.insanetweaks.sentinel.action.guard_here"));
        this.stanceButton = new GuiButton(2, left, top + 48, 100, 20, "");
        this.radiusDownButton = new GuiButton(3, left, top + 72, 20, 20, "-");
        this.radiusUpButton = new GuiButton(4, left + 80, top + 72, 20, 20, "+");
        this.depositButton = new GuiButton(5, left, top + 96, 100, 20, "");
        this.pickupButton = new GuiButton(6, left, top + 120, 100, 20, "");
        this.buttonList.add(this.followButton);
        this.buttonList.add(this.guardButton);
        this.buttonList.add(this.stanceButton);
        this.buttonList.add(this.radiusDownButton);
        this.buttonList.add(this.radiusUpButton);
        this.buttonList.add(this.depositButton);
        this.buttonList.add(this.pickupButton);
        this.refreshButtonLabels();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        // Commands do NOT close the screen — labels update live via refreshButtonLabels.
        int action;
        switch (button.id) {
            case 0: action = PacketSentinelCommand.ACTION_FOLLOW;               break;
            case 1: action = PacketSentinelCommand.ACTION_GUARD_HERE;           break;
            case 2: action = PacketSentinelCommand.ACTION_STANCE_TOGGLE;        break;
            case 3: action = PacketSentinelCommand.ACTION_RADIUS_DOWN;          break;
            case 4: action = PacketSentinelCommand.ACTION_RADIUS_UP;            break;
            case 5: action = PacketSentinelCommand.ACTION_TOGGLE_DEPOSIT;       break;
            case 6: action = PacketSentinelCommand.ACTION_TOGGLE_PICKUP_FILTER; break;
            default: return;
        }
        InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketSentinelCommand(this.entityId, action));
    }

    /** Live labels mirroring the sentinel's DataManager-synced settings. */
    private void refreshButtonLabels() {
        EntitySentinel sentinel = this.getSentinel();
        if (sentinel == null) return;
        this.stanceButton.displayString = I18n.format(sentinel.isAggressiveStance()
                ? "gui.insanetweaks.sentinel.stance.aggressive"
                : "gui.insanetweaks.sentinel.stance.defensive");
        this.depositButton.displayString = I18n.format(sentinel.isAutoDeposit()
                ? "gui.insanetweaks.sentinel.deposit.on"
                : "gui.insanetweaks.sentinel.deposit.off");
        this.pickupButton.displayString = I18n.format(sentinel.isCollectAll()
                ? "gui.insanetweaks.sentinel.pickup.all"
                : "gui.insanetweaks.sentinel.pickup.valuables");
        boolean guarding = sentinel.getGuardAnchor() != null;
        this.radiusDownButton.enabled = guarding;
        this.radiusUpButton.enabled = guarding;
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

            // Radius readout centered in the gap between the -/+ stepper buttons
            // (buttons occupy relative x -105..-85 and -25..-5 at y 80..100).
            String radiusText = I18n.format("gui.insanetweaks.sentinel.radius", sentinel.getGuardRadius());
            int radiusWidth = this.fontRenderer.getStringWidth(radiusText);
            this.fontRenderer.drawString(radiusText, -55 - radiusWidth / 2, 86, 0x404040);

            BlockPos anchor = sentinel.getGuardAnchor();
            if (anchor != null) {
                // Anchor coords in the left button margin, below the toggle buttons.
                String anchorText = I18n.format("gui.insanetweaks.sentinel.anchor.coords",
                        anchor.getX(), anchor.getY(), anchor.getZ());
                this.fontRenderer.drawString(anchorText, -105, 8 + 146, 0xA0A0A0);
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
            return;
        }
        this.refreshButtonLabels();
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
