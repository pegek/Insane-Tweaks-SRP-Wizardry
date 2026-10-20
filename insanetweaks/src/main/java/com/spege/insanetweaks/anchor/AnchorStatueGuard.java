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
 * A statue standing on a dormant anchor can never be broken or blown up in survival: the anchor
 * itself releases its mob (from its own copy), and a statue broken by hand would bypass that
 * (playtest 2026-10-20). Creative players may still break it. Statues from EBW's own Petrify spell
 * are untouched.
 */
public class AnchorStatueGuard {

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
            player.sendStatusMessage(new TextComponentTranslation(TileEntityPetrifiedAnchor.wakeAllowed(world)
                    ? "msg.insanetweaks.anchor.sealed_force" : "msg.insanetweaks.anchor.sealed"), true);
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
            if (isSealed(world, it.next())) {
                it.remove();
            }
        }
    }

    /** True for a statue block that stands (directly or via its lower parts) on a dormant anchor. */
    static boolean isSealed(World world, BlockPos pos) {
        if (!AnchorStatues.isStatueAt(world, pos)) {
            return false;
        }
        for (int k = 1; k <= MAX_PARTS; k++) {
            BlockPos below = pos.down(k);
            IBlockState state = world.getBlockState(below);
            if (state.getBlock() instanceof BlockPetrifiedAnchor) {
                return state.getValue(BlockPetrifiedAnchor.STAGE) == BlockPetrifiedAnchor.Stage.DORMANT;
            }
            if (!AnchorStatues.isStatueAt(world, below)) {
                return false;
            }
        }
        return false;
    }
}
