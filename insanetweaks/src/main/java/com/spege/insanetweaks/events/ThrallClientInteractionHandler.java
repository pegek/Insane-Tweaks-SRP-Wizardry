package com.spege.insanetweaks.events;

import com.spege.insanetweaks.client.gui.GuiThrallControl;
import com.spege.insanetweaks.entities.EntityThrallMinion;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ThrallClientInteractionHandler {

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getWorld() == null || !event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }

        if (!(event.getTarget() instanceof EntityThrallMinion)) {
            return;
        }

        EntityPlayer player = event.getEntityPlayer();
        EntityThrallMinion thrall = (EntityThrallMinion) event.getTarget();

        if (!thrall.canPlayerCommand(player)) {
            return;
        }

        Minecraft.getMinecraft().displayGuiScreen(new GuiThrallControl(thrall.getEntityId()));
        event.setCanceled(true);
    }
}
