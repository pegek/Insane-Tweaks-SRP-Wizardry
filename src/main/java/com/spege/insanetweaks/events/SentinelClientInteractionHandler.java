package com.spege.insanetweaks.events;

import com.spege.insanetweaks.client.gui.GuiSentinelControl;
import com.spege.insanetweaks.entities.EntitySentinel;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class SentinelClientInteractionHandler {

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getWorld() == null || !event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }

        if (!(event.getTarget() instanceof EntitySentinel)) {
            return;
        }

        EntityPlayer player = event.getEntityPlayer();
        EntitySentinel sentinel = (EntitySentinel) event.getTarget();
        if (!sentinel.canPlayerCommand(player)) {
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(new GuiSentinelControl(sentinel.getEntityId()));
        event.setCanceled(true);
    }
}
