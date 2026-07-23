package com.spege.insanetweaks.sanctuary;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Creative-only sanctuary: reuses TileEntitySanctuaryCore in creative-forced mode. Placed it is
 *  instantly active at a fixed radius (default 64); right-click opens a slider GUI (16..256). No
 *  ritual, no tier progression. Obtainable only from the creative tab (there is no crafting recipe). */
public class BlockCreativeSanctuary extends BlockSanctuaryCore {

    /** Radius a freshly placed creative sanctuary starts at. */
    public static final int DEFAULT_RADIUS = 64;

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer,
            ItemStack stack) {
        // Intentionally NOT calling super (which posts the ritual "demands a lure" message).
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntitySanctuaryCore) {
                ((TileEntitySanctuaryCore) te).setCreativeRadius(DEFAULT_RADIUS);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote && world.getTileEntity(pos) instanceof TileEntitySanctuaryCore) {
            player.openGui(com.spege.insanetweaks.InsaneTweaksMod.INSTANCE,
                    com.spege.insanetweaks.InsaneTweaksMod.GUI_ID_CREATIVE_SANCTUARY,
                    world, pos.getX(), pos.getY(), pos.getZ());
        }
        return true;
    }
}
