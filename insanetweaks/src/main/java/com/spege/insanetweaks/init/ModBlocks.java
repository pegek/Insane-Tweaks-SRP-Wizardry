package com.spege.insanetweaks.init;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.sanctuary.BlockSanctuaryCore;

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

@Mod.EventBusSubscriber(modid = InsaneTweaksMod.MODID)
public class ModBlocks {

    public static BlockSanctuaryCore SANCTUARY_CORE;
    public static com.spege.insanetweaks.sanctuary.BlockCreativeSanctuary CREATIVE_SANCTUARY;

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!ModConfig.modules.enableSanctuary
                || !net.minecraftforge.fml.common.Loader.isModLoaded(InsaneTweaksMod.SRP_MODID)) {
            return;
        }
        SANCTUARY_CORE = (BlockSanctuaryCore) new BlockSanctuaryCore()
                .setUnlocalizedName(InsaneTweaksMod.MODID + ".sanctuary_core")
                .setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "sanctuary_core"));
        SANCTUARY_CORE.setCreativeTab(CreativeTabs.MISC);
        event.getRegistry().register(SANCTUARY_CORE);

        net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                com.spege.insanetweaks.sanctuary.TileEntitySanctuaryCore.class,
                new ResourceLocation(InsaneTweaksMod.MODID, "sanctuary_core"));

        CREATIVE_SANCTUARY = (com.spege.insanetweaks.sanctuary.BlockCreativeSanctuary)
                new com.spege.insanetweaks.sanctuary.BlockCreativeSanctuary()
                        .setUnlocalizedName(InsaneTweaksMod.MODID + ".creative_sanctuary")
                        .setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "creative_sanctuary"));
        CREATIVE_SANCTUARY.setCreativeTab(CreativeTabs.MISC);
        event.getRegistry().register(CREATIVE_SANCTUARY);
    }

    @SubscribeEvent
    public static void registerItemBlocks(RegistryEvent.Register<Item> event) {
        if (!ModConfig.modules.enableSanctuary || SANCTUARY_CORE == null
                || !net.minecraftforge.fml.common.Loader.isModLoaded(InsaneTweaksMod.SRP_MODID)) {
            return;
        }
        ItemBlock ib = new com.spege.insanetweaks.sanctuary.ItemBlockSanctuaryCore(SANCTUARY_CORE);
        ib.setRegistryName(SANCTUARY_CORE.getRegistryName());
        event.getRegistry().register(ib);

        if (CREATIVE_SANCTUARY != null) {
            ItemBlock cib = new ItemBlock(CREATIVE_SANCTUARY);
            cib.setRegistryName(CREATIVE_SANCTUARY.getRegistryName());
            event.getRegistry().register(cib);
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        if (!ModConfig.modules.enableSanctuary || SANCTUARY_CORE == null
                || !net.minecraftforge.fml.common.Loader.isModLoaded(InsaneTweaksMod.SRP_MODID)) {
            return;
        }
        Item item = Item.getItemFromBlock(SANCTUARY_CORE);
        ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(SANCTUARY_CORE.getRegistryName(), "inventory"));

        if (CREATIVE_SANCTUARY != null) {
            Item citem = Item.getItemFromBlock(CREATIVE_SANCTUARY);
            ModelLoader.setCustomModelResourceLocation(citem, 0,
                    new ModelResourceLocation(CREATIVE_SANCTUARY.getRegistryName(), "inventory"));
        }
    }
}
