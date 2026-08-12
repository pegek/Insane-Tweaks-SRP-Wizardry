package com.spege.commandsuggest.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/** Drzewo idzie raz, przy wejsciu. Zadnych typow klienckich — ta klasa zyje takze na dedyku. */
public class LoginHandler {

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            TreeDispatcher.sendTo((EntityPlayerMP) event.player);
        }
    }
}
