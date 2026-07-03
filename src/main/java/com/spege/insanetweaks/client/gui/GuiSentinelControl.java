package com.spege.insanetweaks.client.gui;

import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketSentinelCommand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.BlockPos;

@SuppressWarnings("null")
public class GuiSentinelControl extends GuiScreen {

    private final int entityId;
    private GuiButton followButton;
    private GuiButton guardButton;
    private GuiButton lootButton;

    public GuiSentinelControl(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public void initGui() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.buttonList.clear();
        this.followButton = new GuiButton(0, centerX - 75, centerY - 30, 150, 20,
                I18n.format("gui.insanetweaks.sentinel.action.follow"));
        this.guardButton = new GuiButton(1, centerX - 75, centerY - 5, 150, 20,
                I18n.format("gui.insanetweaks.sentinel.action.guard_here"));
        this.lootButton = new GuiButton(2, centerX - 75, centerY + 20, 150, 20,
                I18n.format("gui.insanetweaks.sentinel.action.loot"));
        this.buttonList.add(this.followButton);
        this.buttonList.add(this.guardButton);
        this.buttonList.add(this.lootButton);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketSentinelCommand(this.entityId,
                    PacketSentinelCommand.ACTION_FOLLOW));
            this.mc.displayGuiScreen(null);
            return;
        }

        if (button.id == 1) {
            InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketSentinelCommand(this.entityId,
                    PacketSentinelCommand.ACTION_GUARD_HERE));
            this.mc.displayGuiScreen(null);
            return;
        }

        if (button.id == 2) {
            InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketSentinelCommand(this.entityId,
                    PacketSentinelCommand.ACTION_OPEN_LOOT));
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        EntitySentinel sentinel = this.getSentinel();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        this.drawCenteredString(this.fontRenderer, I18n.format("gui.insanetweaks.sentinel.title"), centerX,
                centerY - 65, 0xFFFFFF);

        if (sentinel != null) {
            String modeKey = "gui.insanetweaks.sentinel.mode." + sentinel.getCommandMode().getTranslationSuffix();
            this.drawCenteredString(this.fontRenderer, I18n.format(modeKey), centerX, centerY - 50, 0xC7C7C7);

            BlockPos anchor = sentinel.getGuardAnchor();
            String anchorText = anchor == null
                    ? I18n.format("gui.insanetweaks.sentinel.anchor.none")
                    : I18n.format("gui.insanetweaks.sentinel.anchor.coords", anchor.getX(), anchor.getY(), anchor.getZ());
            this.drawCenteredString(this.fontRenderer, anchorText, centerX, centerY + 48, 0xA0A0A0);
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
            this.mc.displayGuiScreen(null);
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
