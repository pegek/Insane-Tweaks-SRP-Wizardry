package com.spege.tombtweaks.client.gui.button;

import codersafterdark.reskillable.client.gui.GuiSkills;
import codersafterdark.reskillable.client.gui.handler.InventoryTabHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import ovh.corail.tombstone.item.ItemAdvancement;

/**
 * A fourth tab for Reskillable's inventory tab strip, opening Corail Tombstone's Knowledge of Death
 * perk tree.
 *
 * <p>Reskillable's own {@code GuiButtonInventoryTab} cannot be reused: its icon comes from a
 * {@code TabType} enum constant (index into the mod's own 16x16 icon row) and its click is routed by
 * {@code InventoryTabHandler#performAction}, which only knows the three built-in screens. So this is
 * a standalone button that borrows two things from Reskillable on purpose:
 *
 * <ul>
 *   <li>the tab frame is blitted from {@code GuiSkills.SKILLS_RES} at the same (176,0,32,28) — the
 *       strip has to look like one strip, and copying the texture into our assets would drift the
 *       moment a resource pack retextures Reskillable;
 *   <li>the hover text is handed to {@code InventoryTabHandler.tooltip/mx/my}, whose
 *       {@code finishRenderTick} listener draws it at the end of the frame. Drawing a tooltip from
 *       inside {@code drawButton} would land under the item stacks the inventory paints afterwards.
 * </ul>
 *
 * <p>Both are public static state in Reskillable 1.13.1 (verified against the compiled jar), and the
 * whole class only ever loads when {@code KnowledgeTabHandler} is registered — which happens only
 * with both mods present.
 *
 * <p>The two enable checks mirror Reskillable's tab exactly, so the strip appears and disappears as
 * a unit: the recipe book covers this corner when open, and in the creative screen the tabs only
 * belong on the survival-inventory page.
 */
@SideOnly(Side.CLIENT)
public class GuiButtonKnowledgeTab extends GuiButton {

    /** Reskillable owns 82931-82933 for its three tabs; take the next one. */
    public static final int BUTTON_ID = 82934;

    private static final int WIDTH = 32;
    private static final int HEIGHT = 28;

    /** Tombstone's own icon for the knowledge screen — same stack its GUI puts next to the bar. */
    private ItemStack icon = ItemStack.EMPTY;

    public GuiButtonKnowledgeTab(int x, int y) {
        super(BUTTON_ID, x, y, WIDTH, HEIGHT, "");
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        this.enabled = mc.player != null && !mc.player.getRecipeBook().isGuiOpen();
        if (this.enabled && mc.currentScreen instanceof GuiContainerCreative
                && ((GuiContainerCreative) mc.currentScreen).getSelectedTabIndex()
                        != CreativeTabs.INVENTORY.getTabIndex()) {
            this.enabled = false;
        }
        if (!this.enabled) {
            return;
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
        mc.getTextureManager().bindTexture(GuiSkills.SKILLS_RES);
        drawTexturedModalRect(this.x, this.y, 176, 0, WIDTH, HEIGHT);
        drawIcon(mc);

        if (mouseX > this.x && mouseY > this.y
                && mouseX < this.x + WIDTH && mouseY < this.y + HEIGHT) {
            InventoryTabHandler.tooltip =
                    new TextComponentTranslation("tombtweaks.tab.knowledge").getUnformattedText();
            InventoryTabHandler.mx = mouseX;
            InventoryTabHandler.my = mouseY;
        }
    }

    /**
     * Draws Tombstone's knowledge icon where Reskillable blits its own 16x16 sprite (x+12, y+6 — the
     * tab graphic keeps a 4px spine on the left, so the icon is not centred).
     *
     * <p>An item render rather than a texture of our own: it costs no asset, and it stays correct if
     * Tombstone ever changes what represents knowledge. The lighting/rescale dance around it is not
     * optional — {@code renderItemAndEffectIntoGUI} leaves GL item lighting on, which would wash out
     * everything the inventory screen draws after this button.
     */
    private void drawIcon(Minecraft mc) {
        if (this.icon.isEmpty()) {
            this.icon = ItemAdvancement.IconType.FIRST_KNOWLEDGE.getItemStack();
        }
        if (this.icon.isEmpty()) {
            return;
        }
        GlStateManager.enableRescaleNormal();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(this.icon, this.x + 12, this.y + 6);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableRescaleNormal();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
