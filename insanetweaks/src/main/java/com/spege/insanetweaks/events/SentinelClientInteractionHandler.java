package com.spege.insanetweaks.events;

import com.spege.insanetweaks.entities.EntitySentinel;
import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketSentinelCommand;

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

        // F4: the combined control+loot screen is a real Container, so it must be opened
        // SERVER-side (player.openGui) — ask the server instead of opening a GuiScreen here.
        InsaneTweaksNetwork.CHANNEL.sendToServer(
                new PacketSentinelCommand(sentinel.getEntityId(), PacketSentinelCommand.ACTION_OPEN_GUI));
        event.setCanceled(true);
    }
}
