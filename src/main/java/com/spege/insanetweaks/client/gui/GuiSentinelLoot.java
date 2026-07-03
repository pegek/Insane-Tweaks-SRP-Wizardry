package com.spege.insanetweaks.client.gui;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;

@SuppressWarnings("null")
public class GuiSentinelLoot extends GuiScreen {

    public static final int SLOT_COUNT = 20;
    private static final int COLUMNS = 5;
    private static final int ROWS = 4;
    private static final int SLOT_SIZE = 18;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 17 + ROWS * SLOT_SIZE + 12;
    private static final int SLOT_START_X = 43;
    private static final int SLOT_START_Y = 18;
    private static final ResourceLocation CHEST_GUI_TEXTURE =
            new ResourceLocation("textures/gui/container/generic_54.png");

    private final NonNullList<ItemStack> loot;

    public GuiSentinelLoot(int entityId, NonNullList<ItemStack> loot) {
        this.loot = loot;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();

        int left = (this.width - GUI_WIDTH) / 2;
        int top = (this.height - GUI_HEIGHT) / 2;

        this.mc.getTextureManager().bindTexture(CHEST_GUI_TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawTexturedModalRect(left, top, 0, 0, GUI_WIDTH, ROWS * SLOT_SIZE + 17);
        this.drawTexturedModalRect(left, top + ROWS * SLOT_SIZE + 17, 0, 126, GUI_WIDTH, 7);

        this.drawCenteredString(this.fontRenderer, I18n.format("gui.insanetweaks.sentinel.loot.title"), this.width / 2,
                top + 6, 0x404040);

        RenderHelper.enableGUIStandardItemLighting();
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int slot = 0; slot < this.loot.size(); slot++) {
            int row = slot / COLUMNS;
            int col = slot % COLUMNS;
            int x = left + SLOT_START_X + col * SLOT_SIZE;
            int y = top + SLOT_START_Y + row * SLOT_SIZE;
            ItemStack stack = this.loot.get(slot);

            if (!stack.isEmpty()) {
                this.itemRender.renderItemAndEffectIntoGUI(stack, x, y);
                this.itemRender.renderItemOverlayIntoGUI(this.fontRenderer, stack, x, y, null);
            }

            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                hoveredStack = stack;
                drawRect(x, y, x + 16, y + 16, 0x80FFFFFF);
            }
        }

        RenderHelper.disableStandardItemLighting();

        if (this.isLootEmpty()) {
            this.drawCenteredString(this.fontRenderer, I18n.format("gui.insanetweaks.sentinel.loot.empty"),
                    this.width / 2, top + GUI_HEIGHT - 12, 0xA0A0A0);
        } else {
            this.drawCenteredString(this.fontRenderer,
                    I18n.format("gui.insanetweaks.sentinel.loot.summary", this.getFilledSlotCount(), SLOT_COUNT),
                    this.width / 2, top + GUI_HEIGHT - 12, 0xA0A0A0);
        }

        if (!hoveredStack.isEmpty()) {
            this.renderToolTip(hoveredStack, mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private boolean isLootEmpty() {
        for (ItemStack stack : this.loot) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private int getFilledSlotCount() {
        int filled = 0;
        for (ItemStack stack : this.loot) {
            if (!stack.isEmpty()) {
                filled++;
            }
        }
        return filled;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
