package com.spege.insanetweaks.sanctuary;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

public class BlockSanctuaryCore extends Block {
    public BlockSanctuaryCore() {
        super(Material.ROCK);
        // Obsidian-tier durability: slow to mine, needs a diamond pickaxe, and the 2000 resistance
        // (-> 6000 blast) shrugs off TNT/creepers.
        setHardness(50.0F);
        setResistance(2000.0F);
        setHarvestLevel("pickaxe", 3);
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
        if (world.isRemote || !(placer instanceof net.minecraft.entity.player.EntityPlayer)) {
            return;
        }
        net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) placer;
        com.spege.insanetweaks.config.categories.SanctuaryCategory cfg =
                com.spege.insanetweaks.config.ModConfig.sanctuary;

        // Per-player limit. The Creative Sanctuary is a different block that never registers an owner,
        // so it never counts. Reject the placement (remove block, refund item, message) when at cap.
        if (cfg.enableSanctuaryLimit) {
            int dim = world.provider.getDimension();
            boolean every = cfg.limitEveryDimension;
            int have = SanctuaryOwnerData.get(world).count(player.getUniqueID(), dim, every);
            if (have >= Math.max(1, cfg.maxSanctuariesPerPlayer)) {
                world.setBlockToAir(pos);
                if (!player.capabilities.isCreativeMode) {
                    player.addItemStackToInventory(new net.minecraft.item.ItemStack(this));
                }
                player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                        every ? "msg.insanetweaks.sanctuary.limit_every" : "msg.insanetweaks.sanctuary.limit_perdim",
                        cfg.maxSanctuariesPerPlayer));
                return;
            }
        }

        // Accept: bind the owner + register it globally, then announce the first lure demand.
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntitySanctuaryCore) {
            ((TileEntitySanctuaryCore) te).setOwner(player.getUniqueID(), player.getName());
            SanctuaryOwnerData.get(world).add(player.getUniqueID(), world.provider.getDimension(), pos);
        }
        player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                "msg.insanetweaks.sanctuary.demand",
                new net.minecraft.util.text.TextComponentTranslation(TileEntitySanctuaryCore.lureNameKey(0))));
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
