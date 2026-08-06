package com.spege.insanetweaks.client;

import com.spege.insanetweaks.client.gui.GuiReskillableSplitNotice;
import com.spege.insanetweaks.config.ReskillableSplitNotice;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Puts {@link GuiReskillableSplitNotice} in front of the main menu once per launch, on the first
 * time the menu would be shown — the last moment before the player can load a world and lose every
 * bought trait without being told. See {@link ReskillableSplitNotice}.
 *
 * <p>This and {@code TombstoneSplitNoticeHandler} listen to the same event and can both be armed on
 * the same launch. They chain rather than collide, in either firing order: whichever runs first
 * replaces the screen, at which point {@code event.getGui()} is no longer a {@code GuiMainMenu}, so
 * the other returns <em>without</em> marking itself handled. Dismissing the first notice shows the
 * main menu again, {@code GuiOpenEvent} fires a second time, and the other one takes that turn.
 *
 * <p>🚨 Class-level {@code @SideOnly(Side.CLIENT)}: it names {@code GuiMainMenu} in a method
 * signature, so Forge's SideTransformer throws if anything on a dedicated server so much as
 * instantiates it. Registered from {@code ClientProxy} for that reason, never from the
 * {@code @Mod} class.
 */
@SideOnly(Side.CLIENT)
public class ReskillableSplitNoticeHandler {

    private boolean handled = false;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (this.handled || !(event.getGui() instanceof GuiMainMenu)) {
            return;
        }

        // Set before the check, not after: whatever the verdict is, this listener is done for the
        // session. Set before constructing the replacement too, because displaying the main menu
        // again from the notice re-fires this event.
        this.handled = true;

        if (!ReskillableSplitNotice.shouldWarn()) {
            return;
        }

        event.setGui(new GuiReskillableSplitNotice((GuiMainMenu) event.getGui()));
    }
}
