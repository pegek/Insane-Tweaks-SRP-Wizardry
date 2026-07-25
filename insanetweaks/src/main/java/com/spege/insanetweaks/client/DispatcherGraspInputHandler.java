package com.spege.insanetweaks.client;

import com.spege.insanetweaks.init.ModSpells;

import electroblob.wizardry.util.EntityUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client half of the Dispatcher Grasp root: while the player channels the
 * spell, the movement inputs are zeroed before they ever reach the movement
 * code, so the client never predicts a step the server-side attribute root
 * would just undo (which is what produces rubber-banding).
 *
 * <p>Exactly the four fields EBW's own paralysis handler clears
 * ({@code WizardryClientEventHandler#onInputUpdateEvent}). Camera and mouse are
 * deliberately left alone — the caster still has to aim.
 *
 * <p>{@link EntityUtils#isCasting} covers both the wand/scroll channel and
 * {@code /cast}, so this stays in sync with the server-side handler's gate.
 */
@SideOnly(Side.CLIENT)
public class DispatcherGraspInputHandler {

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        EntityPlayer player = event.getEntityPlayer();

        if (player == null || !EntityUtils.isCasting(player, ModSpells.DISPATCHER_GRASP)) {
            return;
        }

        event.getMovementInput().moveForward = 0.0F;
        event.getMovementInput().moveStrafe = 0.0F;
        event.getMovementInput().jump = false;
        event.getMovementInput().sneak = false;
    }
}
