package com.spege.insanetweaks.client;

import com.spege.insanetweaks.client.gui.GuiTombstoneSplitNotice;
import com.spege.insanetweaks.config.TombstoneSplitNotice;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Puts {@link GuiTombstoneSplitNotice} in front of the main menu once per launch, on the first
 * time the menu would be shown — which is the last moment before the player can load a world and
 * lose their perk levels for good. See {@link TombstoneSplitNotice}.
 *
 * <p>🚨 Class-level {@code @SideOnly(Side.CLIENT)}: it names {@code GuiMainMenu} in a method
 * signature, so Forge's SideTransformer throws if anything on a dedicated server so much as
 * instantiates it. Registered from {@code ClientProxy} for that reason, never from the
 * {@code @Mod} class.
 */
@SideOnly(Side.CLIENT)
public class TombstoneSplitNoticeHandler {

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

        if (!TombstoneSplitNotice.shouldWarn()) {
            return;
        }

        event.setGui(new GuiTombstoneSplitNotice((GuiMainMenu) event.getGui()));
    }
}
