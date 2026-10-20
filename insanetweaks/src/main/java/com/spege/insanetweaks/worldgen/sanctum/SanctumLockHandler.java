package com.spege.insanetweaks.worldgen.sanctum;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Lazy half of the sanctum lock: a container that was unloaded when its sanctum was cleared is
 * unlocked on first touch, at HIGHEST priority so EBW's lock handlers then see no lock. A still
 * locked container explains itself in the action bar.
 */
public class SanctumLockHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!touch(event.getWorld(), event.getPos()) && !event.getWorld().isRemote) {
            hint(event.getWorld(), event.getPos(), event.getEntityPlayer());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        touch(event.getWorld(), event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        touch(event.getWorld(), event.getPos());
    }

    /** True if the block is not (or no longer) sanctum-locked. */
    private static boolean touch(World world, BlockPos pos) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        Long id = te == null ? null : SanctumLocks.sanctumOf(te);
        if (id == null) {
            return true;
        }
        if (SanctumWorldData.get(world).isCleared(id.longValue())) {
            SanctumLocks.unlock(world, pos, te);
            return true;
        }
        return false;
    }

    private static void hint(World world, BlockPos pos, EntityPlayer player) {
        TileEntity te = world.getTileEntity(pos);
        if (te != null && SanctumLocks.sanctumOf(te) != null && player != null) {
            player.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.sanctum.locked"), true);
        }
    }
}
