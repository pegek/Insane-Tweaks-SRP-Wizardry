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
    public void onBlockPlacedBy(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.state.IBlockState state, net.minecraft.entity.EntityLivingBase placer,
            net.minecraft.item.ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (!world.isRemote && placer instanceof net.minecraft.entity.player.EntityPlayer) {
            ((net.minecraft.entity.player.EntityPlayer) placer).sendMessage(
                    new net.minecraft.util.text.TextComponentTranslation(
                            "msg.insanetweaks.sanctuary.demand",
                            new net.minecraft.util.text.TextComponentTranslation(
                                    TileEntitySanctuaryCore.lureNameKey(0)))); // demands Lure (Weakened)
        }
    }

    @Override
    public boolean onBlockActivated(net.minecraft.world.World world, net.minecraft.util.math.BlockPos pos,
            net.minecraft.block.state.IBlockState state, net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.util.EnumHand hand, net.minecraft.util.EnumFacing facing,
            float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileEntitySanctuaryCore) {
            TileEntitySanctuaryCore te = (TileEntitySanctuaryCore) world.getTileEntity(pos);
            if (player.isSneaking()) {
                te.sendStatusTo(player);
            } else {
                player.openGui(com.spege.insanetweaks.InsaneTweaksMod.INSTANCE,
                        com.spege.insanetweaks.InsaneTweaksMod.GUI_ID_SANCTUARY, world, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return true;
    }
}
