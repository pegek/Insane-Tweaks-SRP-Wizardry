package com.spege.insanetweaks.anchor;

import java.util.Iterator;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Keeps an anchor's statue sealed while the SRP phase gate is closed
 * ({@link TileEntityPetrifiedAnchor#wakeAllowed}). EBW releases the mob whenever the statue's bottom
 * block breaks, so without this a player could free it early by mining the statue or blowing it up.
 * Creative players may still break it. Only statue blocks standing on an active anchor are guarded;
 * a statue made by EBW's own Petrify spell is untouched.
 */
public class AnchorStatueGuard {

    /** Statues are at most three blocks tall, so an anchor is at most three blocks below any part. */
    private static final int MAX_PARTS = 3;

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        World world = event.getWorld();
        EntityPlayer player = event.getPlayer();
        if (world.isRemote || (player != null && player.isCreative()) || !isSealed(world, event.getPos())) {
            return;
        }
        event.setCanceled(true);
        if (player != null) {
            player.sendStatusMessage(new TextComponentTranslation("msg.insanetweaks.anchor.sealed"), true);
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {
        World world = event.getWorld();
        if (world.isRemote) {
            return;
        }
        Iterator<BlockPos> it = event.getAffectedBlocks().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (isSealed(world, pos)) {
                it.remove();
            }
        }
    }

    /** True for a statue block that stands (directly or via its lower parts) on an active, gated anchor. */
    static boolean isSealed(World world, BlockPos pos) {
        if (!AnchorStatues.isStatueAt(world, pos)) {
            return false;
        }
        for (int k = 1; k <= MAX_PARTS; k++) {
            BlockPos below = pos.down(k);
            IBlockState state = world.getBlockState(below);
            if (state.getBlock() instanceof BlockPetrifiedAnchor) {
                return !state.getValue(BlockPetrifiedAnchor.AWAKENED).booleanValue()
                        && !TileEntityPetrifiedAnchor.wakeAllowed(world);
            }
            if (!AnchorStatues.isStatueAt(world, below)) {
                return false;
            }
        }
        return false;
    }
}
