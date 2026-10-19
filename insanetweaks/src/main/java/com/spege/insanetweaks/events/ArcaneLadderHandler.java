package com.spege.insanetweaks.events;

import com.spege.insanetweaks.blocks.BlockArcaneLadder;
import com.spege.insanetweaks.blocks.BlockArcaneWard;
import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Faster, gentler Arcane Ladders. Runs at the END of the player tick, after vanilla's ladder
 * logic, so the motion set here is what the next tick moves by. Both sides: the local player's
 * movement is computed client-side, and the server copy keeps its own motion consistent.
 */
public class ArcaneLadderHandler {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        EntityPlayer player = event.player;
        if (player.isSpectator() || player.capabilities.isFlying || !player.isOnLadder()) {
            return;
        }
        Block block = player.world.getBlockState(new BlockPos(player)).getBlock();
        if (!(block instanceof BlockArcaneLadder) && !(block instanceof BlockArcaneWard)) {
            return;
        }
        double climb = ModConfig.tweaks.arcaneLadderClimbSpeed;
        double descent = ModConfig.tweaks.arcaneLadderMaxDescent;
        if (player.collidedHorizontally) {
            player.motionY = climb;
        } else if (player.motionY < -descent) {
            player.motionY = -descent;
        }
        player.fallDistance = 0.0F;
    }
}
