package com.spege.tombtweaks.client;

import com.spege.tombtweaks.client.gui.button.GuiButtonKnowledgeTab;

import codersafterdark.reskillable.base.ConfigHandler;
import codersafterdark.reskillable.client.gui.button.GuiButtonInventoryTab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import ovh.corail.tombstone.api.capability.ITBCapability;
import ovh.corail.tombstone.capability.TBCapabilityProvider;
import ovh.corail.tombstone.gui.ScreenKnowledge;

/**
 * Hangs a "Knowledge of Death" tab off the bottom of Reskillable's inventory tab strip and opens
 * Tombstone's perk tree from it.
 *
 * <p>Client only, and registered from {@code InsaneTweaksMod#init} only when Reskillable AND
 * Tombstone are both loaded — this class names types from both in its signatures, so it must not be
 * loaded otherwise.
 *
 * <p>The geometry is Reskillable's, read off {@code InventoryTabHandler#addTabs}: origin at
 * {@code (width/2 - 120, height/2 - 76)}, shifted {@code (-10, +15)} on the creative screen, one tab
 * every 29px. It is duplicated rather than derived because the mod exposes no accessor for it; if a
 * Reskillable update moves the strip, this tab drifts off it and the constants below are the single
 * place to fix.
 */
@SideOnly(Side.CLIENT)
public class KnowledgeTabHandler {

    private static final int ORIGIN_X_OFFSET = -120;
    private static final int ORIGIN_Y_OFFSET = -76;
    private static final int CREATIVE_X_SHIFT = -10;
    private static final int CREATIVE_Y_SHIFT = 15;
    private static final int TAB_PITCH = 29;

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        // Ride Reskillable's own switch: with its strip off, a lone tab floating over the inventory
        // would look like a bug, and the tooltip renderer we borrow would never run.
        if (!ConfigHandler.enableTabs) {
            return;
        }
        GuiScreen gui = event.getGui();
        boolean creative = gui instanceof GuiContainerCreative;
        if (!creative && !(gui instanceof GuiInventory)) {
            return;
        }

        int x = gui.width / 2 + ORIGIN_X_OFFSET;
        int y = gui.height / 2 + ORIGIN_Y_OFFSET;
        if (creative) {
            x += CREATIVE_X_SHIFT;
            y += CREATIVE_Y_SHIFT;
        }

        // Reskillable always ADDS its abilities tab but hides it until the player has an ability, so
        // taking the fourth slot unconditionally would leave a 29px hole in the strip on most saves.
        // Sitting in the third slot then overlaps a button that is disabled (and therefore neither
        // clickable nor hoverable) until the day an ability unlocks, when this moves down one slot.
        int slot = GuiButtonInventoryTab.TabType.ABILITIES.shouldRender() ? 3 : 2;
        event.getButtonList().add(new GuiButtonKnowledgeTab(x, y + TAB_PITCH * slot));
    }

    @SubscribeEvent
    public void onActionPerformed(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.getButton() instanceof GuiButtonKnowledgeTab)) {
            return;
        }
        event.setCanceled(true);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) {
            return;
        }
        // Same two lines Tombstone's own KEYBIND_KNOWLEDGE handler runs.
        ITBCapability cap = mc.player.getCapability(TBCapabilityProvider.TB_CAPABILITY, null);
        if (cap != null) {
            mc.displayGuiScreen(new ScreenKnowledge(mc.player, cap));
        }
    }
}
