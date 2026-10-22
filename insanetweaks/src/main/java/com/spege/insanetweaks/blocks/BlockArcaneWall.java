package com.spege.insanetweaks.blocks;

import java.util.Random;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Arcane Wall: the hull around a Sunken Arcane Sanctum. Solid for everything, unbreakable,
 * blast-proof and immovable, so the sanctum can only be entered through its shaft; it turns to
 * stone when the sanctum is cleansed (SanctumWalls). Looks like Ancient Spellcraft's arcane wall,
 * but carries no tile entity - the hull is thousands of blocks.
 */
public class BlockArcaneWall extends Block {

    public BlockArcaneWall() {
        super(Material.ROCK);
        this.setBlockUnbreakable();
        this.setResistance(6000000.0F);
        this.setSoundType(SoundType.GLASS);
        this.setLightOpacity(255);
        this.setUnlocalizedName(InsaneTweaksMod.MODID + ".arcane_wall");
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "arcane_wall"));
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public EnumPushReaction getMobilityFlag(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return net.minecraft.init.Items.AIR;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.TRANSLUCENT;
    }

    @SideOnly(Side.CLIENT)
    @SuppressWarnings("deprecation")
    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return world.getBlockState(pos.offset(side)).getBlock() != this && super.shouldSideBeRendered(state, world, pos, side);
    }
}
