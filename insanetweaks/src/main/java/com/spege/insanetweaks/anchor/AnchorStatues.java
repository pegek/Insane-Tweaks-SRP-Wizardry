package com.spege.insanetweaks.anchor;

import javax.annotation.Nullable;

import electroblob.wizardry.block.BlockStatue;
import electroblob.wizardry.registry.WizardryBlocks;
import electroblob.wizardry.tileentity.TileEntityStatue;

import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Everything the anchor needs from EBW's petrification, in one place. EBW stores the mob's NBT in
 * the statue's tile entity, renders the real model in stone, and spawns the mob back in
 * {@code BlockStatue.breakBlock} - but only for the bottom part ({@code position == 1}) and only
 * while {@code creature} is non-null. That second condition is what {@link #discard} uses to
 * remove a statue without releasing anything.
 */
public final class AnchorStatues {

    /** Bottom part of a statue, per TileEntityStatue.position. */
    private static final int BOTTOM = 1;
    /** Statues are at most three blocks tall. */
    private static final int MAX_PARTS = 3;

    private AnchorStatues() {
    }

    /**
     * Turns {@code mob} (created, positioned, never spawned) into a permanent statue at its own
     * position. Null caster: EBW then checks its dispenserBlockDamage setting instead of the
     * mob-griefing rule.
     */
    public static boolean petrify(EntityLiving mob) {
        return ((BlockStatue) WizardryBlocks.petrified_stone).convertToStatue(mob, null, Integer.MAX_VALUE);
    }

    public static boolean isStatueAt(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() == WizardryBlocks.petrified_stone;
    }

    /** The petrified mob held by the statue's bottom block, or null. */
    @Nullable
    public static EntityLiving creatureAt(World world, BlockPos bottom) {
        TileEntity te = world.getTileEntity(bottom);
        return te instanceof TileEntityStatue ? ((TileEntityStatue) te).creature : null;
    }

    /** Breaks the statue so EBW releases its mob. */
    public static void release(World world, BlockPos bottom) {
        world.destroyBlock(bottom, false);
    }

    /** Removes the statue without releasing its mob. */
    public static void discard(World world, BlockPos bottom) {
        TileEntity te = world.getTileEntity(bottom);
        if (te instanceof TileEntityStatue && ((TileEntityStatue) te).position == BOTTOM) {
            ((TileEntityStatue) te).creature = null;
        }
        for (int i = MAX_PARTS - 1; i >= 0; i--) {
            BlockPos part = bottom.up(i);
            if (isStatueAt(world, part)) {
                world.setBlockState(part, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }
}
