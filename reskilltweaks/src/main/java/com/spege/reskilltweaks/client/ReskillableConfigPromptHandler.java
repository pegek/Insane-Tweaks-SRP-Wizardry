package com.spege.reskilltweaks.client;

import com.spege.reskilltweaks.config.ReskillableConfigSwapper;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Puts {@link GuiReskillableConfigPrompt} in front of the main menu once per launch, when this
 * launch skipped the reskillable.cfg swap because nobody has chosen yet
 * ({@link ReskillableConfigSwapper#isPromptPending()}).
 *
 * <p>Chains with any other mod that replaces the main menu the same way (Insane Tweaks' split
 * notices do): whichever listener runs first replaces the screen, the other then sees something
 * that is not a {@code GuiMainMenu} and returns <em>without</em> marking itself handled, and takes
 * its turn when the first screen hands back to the menu.
 *
 * <p>🚨 Class-level {@code @SideOnly(Side.CLIENT)}: {@code new} of this class on a dedicated server
 * throws in Forge's SideTransformer. Registered from {@code ReskillTweaks.init} behind
 * {@code event.getSide() == Side.CLIENT}, and that check is load-bearing.
 */
@SideOnly(Side.CLIENT)
public class ReskillableConfigPromptHandler {

    private boolean handled = false;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (this.handled || !(event.getGui() instanceof GuiMainMenu)) {
            return;
        }

        // Set before the check and before building the replacement: returning to the menu from the
        // prompt re-fires this event, and the verdict is final for the session either way.
        this.handled = true;

        if (!ReskillableConfigSwapper.isPromptPending()) {
            return;
        }

        event.setGui(new GuiReskillableConfigPrompt(event.getGui()));
    }
}
