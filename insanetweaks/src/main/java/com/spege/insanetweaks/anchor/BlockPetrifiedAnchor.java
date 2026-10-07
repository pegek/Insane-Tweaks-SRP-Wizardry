package com.spege.insanetweaks.anchor;

import java.util.Random;

import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/**
 * Petrified Anchor plinth. Active (awakened=false): carries the tile entity, cannot be mined in
 * survival and shrugs off explosions, so an ambush cannot be dug out. Awakened: plain cracked
 * stone that mines normally and drops cobblestone. Creative players pin the statue's mob with a
 * spawn egg, or clear the pin with sneak plus an empty hand.
 */
public class BlockPetrifiedAnchor extends Block {

    public static final PropertyBool AWAKENED = PropertyBool.create("awakened");

    private static final float AWAKENED_HARDNESS = 2.0F;
    private static final float ACTIVE_RESISTANCE = 6000000.0F;

    public BlockPetrifiedAnchor() {
        super(Material.ROCK);
        this.setSoundType(SoundType.STONE);
        this.setResistance(6.0F);
        this.setDefaultState(this.blockState.getBaseState().withProperty(AWAKENED, Boolean.FALSE));
        this.setUnlocalizedName(InsaneTweaksMod.MODID + ".petrified_anchor");
        this.setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "petrified_anchor"));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, AWAKENED);
    }

    @SuppressWarnings("deprecation")
    @Override
    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState().withProperty(AWAKENED, Boolean.valueOf((meta & 1) == 1));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(AWAKENED).booleanValue() ? 1 : 0;
    }

    private static boolean active(IBlockState state) {
        return !state.getValue(AWAKENED).booleanValue();
    }

    @SuppressWarnings("deprecation")
    @Override
    public float getBlockHardness(IBlockState state, World world, BlockPos pos) {
        return active(state) ? -1.0F : AWAKENED_HARDNESS;
    }

    @Override
    public float getExplosionResistance(World world, BlockPos pos, Entity exploder, Explosion explosion) {
        return active(world.getBlockState(pos)) ? ACTIVE_RESISTANCE : super.getExplosionResistance(world, pos,
                exploder, explosion);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return active(state);
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return active(state) ? new TileEntityPetrifiedAnchor() : null;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return active(state) ? net.minecraft.init.Items.AIR : Item.getItemFromBlock(Blocks.COBBLESTONE);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!active(state) || !player.isCreative()) {
            return false;
        }
        ItemStack held = player.getHeldItem(hand);
        boolean egg = held.getItem() instanceof ItemMonsterPlacer;
        boolean reset = held.isEmpty() && player.isSneaking();
        if (!egg && !reset) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityPetrifiedAnchor)) {
            return true;
        }
        if (egg) {
            ResourceLocation id = ItemMonsterPlacer.getNamedIdFrom(held);
            if (id == null) {
                return true;
            }
            ((TileEntityPetrifiedAnchor) te).pin(id.toString());
            String key = EntityList.getTranslationName(id);
            player.sendMessage(new TextComponentTranslation("msg.insanetweaks.anchor.pinned",
                    key != null ? new TextComponentTranslation("entity." + key + ".name") : id.toString()));
        } else {
            ((TileEntityPetrifiedAnchor) te).pin("");
            player.sendMessage(new TextComponentTranslation("msg.insanetweaks.anchor.reset"));
        }
        return true;
    }
}
