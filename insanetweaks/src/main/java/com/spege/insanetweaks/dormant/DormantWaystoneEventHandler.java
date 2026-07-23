package com.spege.insanetweaks.dormant;

import com.spege.insanetweaks.api.DormantWaystoneRegistry;
import com.spege.insanetweaks.init.ModBlocks;

import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Keeps the {@link DormantWaystoneRegistry} in sync with manual player intervention.
 *
 * <p>A player <b>placing</b> the block registers it; <b>breaking</b> it unregisters it and clears
 * its pair (so {@code getReturnPos}/{@code getOverworldPos} never dangle). Registered
 * unconditionally — registry consistency must not depend on the worldgen config flag.
 *
 * <p>Note: GroovyScript places the dim-150 return anchor via {@code world.setBlockState}, which does
 * NOT fire {@link BlockEvent.PlaceEvent}; GS registers that anchor itself through the API. This
 * handler only covers hand-placement by a player.
 */
public class DormantWaystoneEventHandler {

    @SubscribeEvent
    public void onPlace(BlockEvent.PlaceEvent event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        if (event.getPlacedBlock().getBlock() == ModBlocks.DORMANT_WAYSTONE) {
            DormantWaystoneRegistry.registerWaystone(world, event.getPos());
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        if (event.getState().getBlock() == ModBlocks.DORMANT_WAYSTONE) {
            DormantWaystoneRegistry.unregisterWaystone(world, event.getPos());
        }
    }
}
