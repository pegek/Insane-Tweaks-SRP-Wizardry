package com.spege.insanetweaks.client.gui;

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
        int btnW = 160;
        int btnH = 20;
        int gap = 3;
        int startY = cy - 125;

        this.buttonList.clear();

        // Follow
        this.buttonList.add(new GuiButton(0, cx - btnW / 2, startY, btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.follow")));
        // Stay
        this.buttonList.add(new GuiButton(1, cx - btnW / 2, startY + (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.stay")));
        // Woodcutting
        this.buttonList.add(new GuiButton(2, cx - btnW / 2, startY + 2 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.woodcutting")));
        // Create Mineshaft
        this.buttonList.add(new GuiButton(3, cx - btnW / 2, startY + 3 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.mineshaft")));
        // Farming
        this.buttonList.add(new GuiButton(8, cx - btnW / 2, startY + 4 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.farming")));
        // Porter
        this.buttonList.add(new GuiButton(9, cx - btnW / 2, startY + 5 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.porter")));
        // Collecting
        this.buttonList.add(new GuiButton(11, cx - btnW / 2, startY + 6 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.collecting")));
        // Set Home
        this.buttonList.add(new GuiButton(4, cx - btnW / 2, startY + 7 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.set_home")));
        // Return Home
        this.buttonList.add(new GuiButton(10, cx - btnW / 2, startY + 8 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.return_home")));
        // Inventory
        this.buttonList.add(new GuiButton(6, cx - btnW / 2, startY + 9 * (btnH + gap), btnW, btnH,
                I18n.format("gui.insanetweaks.thrall.action.inventory")));
        // Dismiss (red)
        this.buttonList.add(new GuiButton(7, cx - btnW / 2, startY + 10 * (btnH + gap), btnW, btnH,
                "\u00a7c" + I18n.format("gui.insanetweaks.thrall.action.dismiss")));
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
            // Count items in inventory
            int itemCount = 0;
            for (int i = 0; i < thrall.getThrallInventory().getSizeInventory(); i++) {
                if (!thrall.getThrallInventory().getStackInSlot(i).isEmpty()) itemCount++;
            }
            this.drawCenteredString(this.fontRenderer,
                    I18n.format("gui.insanetweaks.thrall.inventory", itemCount, 27),
                    cx, cy + 130, 0xA0A0A0);

            // Home point
            net.minecraft.util.math.BlockPos home = thrall.getHomePoint();
            String homeStr = home == null
                    ? I18n.format("gui.insanetweaks.thrall.home.none")
                    : I18n.format("gui.insanetweaks.thrall.home.coords", home.getX(), home.getY(), home.getZ());
            this.drawCenteredString(this.fontRenderer, homeStr, cx, cy + 142, 0x808080);
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
