package com.spege.insanetweaks.blocks;

import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Arcane Ward: a translucent seal only players can pass. Every other entity (mobs, items,
 * projectiles) collides with a full box, pathfinding treats it as solid, it cannot be broken or
 * blown up, and players can climb through it like a ladder, so a ward cell inside a ladder shaft
 * does not break the climb. Used to seal the Sunken Arcane Sanctum's only exit.
 */
public class BlockArcaneWard extends Block {

    public BlockArcaneWard() {
        super(Material.BARRIER);
        this.setBlockUnbreakable();
        this.setResistance(6000000.0F);
        this.setSoundType(SoundType.GLASS);
        this.setLightLevel(0.4F);
        this.setUnlocalizedName(InsaneTweaksMod.MODID + ".arcane_ward");
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "arcane_ward"));
    }

    @SuppressWarnings("deprecation")
    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos, AxisAlignedBB entityBox,
            List<AxisAlignedBB> collidingBoxes, @Nullable Entity entity, boolean isActualState) {
        if (entity instanceof EntityPlayer) {
            return;
        }
        addCollisionBoxToList(pos, entityBox, collidingBoxes, FULL_BLOCK_AABB);
    }

    @Override
    public boolean isLadder(IBlockState state, IBlockAccess world, BlockPos pos, EntityLivingBase entity) {
        return entity instanceof EntityPlayer;
    }

    @Override
    public boolean isPassable(IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
        return false;
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

    @SuppressWarnings("deprecation")
    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockFaceShape getBlockFaceShape(IBlockAccess world, IBlockState state, BlockPos pos, EnumFacing face) {
        return BlockFaceShape.UNDEFINED;
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

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, Random rand) {
        if (rand.nextInt(3) == 0) {
            world.spawnParticle(EnumParticleTypes.SPELL_WITCH, pos.getX() + rand.nextDouble(),
                    pos.getY() + rand.nextDouble(), pos.getZ() + rand.nextDouble(), 0.0D, 0.0D, 0.0D);
        }
    }
}
