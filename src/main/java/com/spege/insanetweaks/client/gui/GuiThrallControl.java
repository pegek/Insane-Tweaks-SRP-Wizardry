package com.spege.insanetweaks.client.gui;

import java.util.List;

import com.spege.insanetweaks.entities.EntityThrallMinion;
import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketThrallCommand;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

@SuppressWarnings("null")
public class GuiThrallControl extends GuiScreen {

    private final int entityId;

    public GuiThrallControl(int entityId) {
        this.entityId = entityId;
    }

    @Override
    public void initGui() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int btnW = 150;
        int btnH = 20;
        int gap = 3;
        int colGap = 10;
        int startY = cy - 100;

        int leftX = cx - btnW - colGap / 2;
        int rightX = cx + colGap / 2;

        this.buttonList.clear();

        // ---- Left column: modes ----
        this.buttonList.add(new GuiButton(0, leftX, startY, btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.follow")));
        this.buttonList.add(new GuiButton(1, leftX, startY + (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.stay")));
        this.buttonList.add(new GuiButton(2, leftX, startY + 2 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.woodcutting")));
        this.buttonList.add(new GuiButton(3, leftX, startY + 3 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.mineshaft")));
        this.buttonList.add(new GuiButton(8, leftX, startY + 4 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.farming")));
        this.buttonList.add(new GuiButton(9, leftX, startY + 5 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.porter")));
        this.buttonList.add(new GuiButton(11, leftX, startY + 6 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.collecting")));

        // ---- Right column: actions ----
        this.buttonList.add(new GuiButton(4, rightX, startY, btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.set_home")));
        this.buttonList.add(new GuiButton(10, rightX, startY + (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.return_home")));
        this.buttonList.add(new GuiButton(6, rightX, startY + 2 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.inventory")));
        this.buttonList.add(new GuiButton(7, rightX, startY + 3 * (btnH + gap), btnW, btnH,
                "§c" + I18n.format("gui.insanetweaks.thrall.action.dismiss")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        int action = -1;
        switch (button.id) {
            case 0: action = PacketThrallCommand.ACTION_FOLLOW;      break;
            case 1: action = PacketThrallCommand.ACTION_STAY;        break;
            case 2: action = PacketThrallCommand.ACTION_WOODCUTTING; break;
            case 3: action = PacketThrallCommand.ACTION_MINESHAFT;   break;
            case 4: action = PacketThrallCommand.ACTION_SET_HOME;    break;
            case 6: action = PacketThrallCommand.ACTION_OPEN_INV;    break;
            case 7: action = PacketThrallCommand.ACTION_DISMISS;     break;
            case 8: action = PacketThrallCommand.ACTION_FARMING;     break;
            case 9: action = PacketThrallCommand.ACTION_PORTER;      break;
            case 10: action = PacketThrallCommand.ACTION_RETURN_HOME; break;
            case 11: action = PacketThrallCommand.ACTION_COLLECTING; break;
        }
        if (action >= 0) {
            InsaneTweaksNetwork.CHANNEL.sendToServer(new PacketThrallCommand(this.entityId, action));
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        int cx = this.width / 2;
        int cy = this.height / 2;

        // Title
        this.drawCenteredString(this.fontRenderer,
                I18n.format("gui.insanetweaks.thrall.title"),
                cx, cy - 117, 0xFFFFFF);

        // Current mode + inventory info
        EntityThrallMinion thrall = getThrall();
        if (thrall != null) {
            int itemCount = 0;
            for (int i = 0; i < thrall.getThrallInventory().getSizeInventory(); i++) {
                if (!thrall.getThrallInventory().getStackInSlot(i).isEmpty()) itemCount++;
            }
            this.drawCenteredString(this.fontRenderer,
                    I18n.format("gui.insanetweaks.thrall.inventory", itemCount, 27),
                    cx, cy + 130, 0xA0A0A0);

            net.minecraft.util.math.BlockPos home = thrall.getHomePoint();
            String homeStr = home == null
                    ? I18n.format("gui.insanetweaks.thrall.home.none")
                    : I18n.format("gui.insanetweaks.thrall.home.coords", home.getX(), home.getY(), home.getZ());
            this.drawCenteredString(this.fontRenderer, homeStr, cx, cy + 142, 0x808080);
        }

        // Hover tooltip for whichever button the mouse is over.
        drawButtonTooltip(mouseX, mouseY);
    }

    /** Draws a wrapped tooltip for the hovered button, keyed by its id. */
    private void drawButtonTooltip(int mouseX, int mouseY) {
        String key = null;
        for (GuiButton b : this.buttonList) {
            if (!b.isMouseOver()) continue;
            key = tooltipKeyFor(b.id);
            break;
        }
        if (key == null) return;

        String text = I18n.format(key);
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(text, 180);
        this.drawHoveringText(lines, mouseX, mouseY);
    }

    private static String tooltipKeyFor(int buttonId) {
        switch (buttonId) {
            case 0:  return "gui.insanetweaks.thrall.tooltip.follow";
            case 1:  return "gui.insanetweaks.thrall.tooltip.stay";
            case 2:  return "gui.insanetweaks.thrall.tooltip.woodcutting";
            case 3:  return "gui.insanetweaks.thrall.tooltip.mineshaft";
            case 8:  return "gui.insanetweaks.thrall.tooltip.farming";
            case 9:  return "gui.insanetweaks.thrall.tooltip.porter";
            case 11: return "gui.insanetweaks.thrall.tooltip.collecting";
            case 4:  return "gui.insanetweaks.thrall.tooltip.set_home";
            case 10: return "gui.insanetweaks.thrall.tooltip.return_home";
            case 6:  return "gui.insanetweaks.thrall.tooltip.inventory";
            case 7:  return "gui.insanetweaks.thrall.tooltip.dismiss";
            default: return null;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (getThrall() == null) {
            this.mc.displayGuiScreen(null);
        }
    }

    private EntityThrallMinion getThrall() {
        if (Minecraft.getMinecraft().world == null) return null;
        net.minecraft.entity.Entity e = Minecraft.getMinecraft().world.getEntityByID(entityId);
        return e instanceof EntityThrallMinion ? (EntityThrallMinion) e : null;
    }
}
