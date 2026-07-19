package com.spege.insanetweaks.sanctuary;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

public class BlockSanctuaryCore extends Block {
    public BlockSanctuaryCore() {
        super(Material.ROCK);
        setHardness(4.0F);
        setResistance(2000.0F); // blast-resistant like a beacon base
        setSoundType(SoundType.STONE);
        setLightLevel(0.5F);
        // registry name + creative tab set in ModBlocks
    }

    @Override public boolean hasTileEntity(net.minecraft.block.state.IBlockState state) { return true; }

    @Override
    public net.minecraft.tileentity.TileEntity createTileEntity(net.minecraft.world.World world,
            net.minecraft.block.state.IBlockState state) {
        return new TileEntitySanctuaryCore();
    }

    @Override
    public void breakBlock(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.state.IBlockState state) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntitySanctuaryCore) {
            ((TileEntitySanctuaryCore) te).onRemovedFromWorld();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.state.IBlockState state, net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.util.EnumHand hand, net.minecraft.util.EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileEntitySanctuaryCore) {
            player.openGui(com.spege.insanetweaks.InsaneTweaksMod.INSTANCE,
                    com.spege.insanetweaks.InsaneTweaksMod.GUI_ID_SANCTUARY, world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }
}
