package com.spege.insanetweaks.worldgen.sanctum;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Grants "Something Sleeps Below" the first time a player stands within 8 blocks of a sanctum's
 * footprint, at any height - so the warning comes at the surface entrance, before the descent.
 * Checked every 40 ticks per player, and only until the player has it.
 */
public class SanctumFoundHandler {

    private static final int INTERVAL = 40;
    private static final int MARGIN = 8;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                || event.player.ticksExisted % INTERVAL != 0) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (!SanctumPlacement.dimensionAllowed(player.world) || SanctumAdvancements.has(player, SanctumAdvancements.FOUND)) {
            return;
        }
        if (SanctumWorldData.get(player.world).nearSanctum((int) Math.floor(player.posX), (int) Math.floor(player.posZ),
                MARGIN)) {
            SanctumAdvancements.grant(player, SanctumAdvancements.FOUND);
        }
    }
}
