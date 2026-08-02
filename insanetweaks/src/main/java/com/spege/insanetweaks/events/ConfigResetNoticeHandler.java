package com.spege.insanetweaks.events;

import com.spege.insanetweaks.config.OldConfigBackup;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * One-shot chat notice after the old-format config was backed up and reset
 * (see OldConfigBackup). Fires for the first player to log in this launch.
 */
public class ConfigResetNoticeHandler {

    private boolean sent = false;

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (this.sent || !OldConfigBackup.didMigrate()) {
            return;
        }

        EntityPlayer player = event.player;
        if (player == null || player.world.isRemote) {
            return;
        }

        this.sent = true;
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "[InsaneTweaks] "
                + TextFormatting.YELLOW
                + "The config layout changed and your settings were reset to defaults. "
                + "Your previous file was saved as config/insanetweaks.cfg.pre-rework - re-apply any customizations."));
    }
}
