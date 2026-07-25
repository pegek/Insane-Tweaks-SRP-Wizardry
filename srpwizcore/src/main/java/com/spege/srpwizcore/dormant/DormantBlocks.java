package com.spege.srpwizcore.dormant;

import com.spege.srpwizcore.SrpWizCore;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Registers the dormant waystone unconditionally — save stability must not depend on config. */
@Mod.EventBusSubscriber(modid = SrpWizCore.MODID)
public class DormantBlocks {

    public static BlockDormantWaystone DORMANT_WAYSTONE;

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        DORMANT_WAYSTONE = (BlockDormantWaystone) new BlockDormantWaystone()
                .setUnlocalizedName(SrpWizCore.MODID + ".dormant_waystone")
                .setRegistryName(new ResourceLocation(SrpWizCore.MODID, "dormant_waystone"));
        DORMANT_WAYSTONE.setCreativeTab(CreativeTabs.MISC);
        event.getRegistry().register(DORMANT_WAYSTONE);
    }

    @SubscribeEvent
    public static void registerItemBlocks(RegistryEvent.Register<Item> event) {
        if (DORMANT_WAYSTONE != null) {
            ItemBlock ib = new ItemBlock(DORMANT_WAYSTONE);
            ib.setRegistryName(DORMANT_WAYSTONE.getRegistryName());
            event.getRegistry().register(ib);
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        if (DORMANT_WAYSTONE != null) {
            Item item = Item.getItemFromBlock(DORMANT_WAYSTONE);
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(DORMANT_WAYSTONE.getRegistryName(), "inventory"));
        }
    }
}
