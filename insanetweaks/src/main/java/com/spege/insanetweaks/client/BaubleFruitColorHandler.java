package com.spege.insanetweaks.client;

import java.util.IdentityHashMap;
import java.util.Map;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModItems;

import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Gives each of the nine typed Bauble Fruits its own colour.
 *
 * <p>All nine share a single model ({@code insanetweaks:bauble_fruit}) so that adding a fruit
 * needs no new JSON — the trade-off was that Ring, Elytra and Totem looked identical in the
 * inventory. A vanilla {@code item/generated} model tints layer N with tint index N, so tinting
 * index 0 per item restores the visual distinction without a single new texture.
 *
 * <p>The shared texture is greyscale for this reason: the tint is a multiply, so a coloured
 * base would drag every fruit towards the same muddy hue.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = InsaneTweaksMod.MODID)
public final class BaubleFruitColorHandler implements IItemColor {

    private static final Map<Item, Integer> COLORS = new IdentityHashMap<Item, Integer>();

    private BaubleFruitColorHandler() {
    }

    @SubscribeEvent
    public static void registerItemColors(ColorHandlerEvent.Item event) {
        // Same gate as the model registration: without Baubles the fruits were never registered.
        if (!ModConfig.modules.enableBaubleFruits || !Loader.isModLoaded("baubles")) {
            return;
        }

        COLORS.put(ModItems.BAUBLE_FRUIT_RING, Integer.valueOf(0xFFD24A));    // gold band
        COLORS.put(ModItems.BAUBLE_FRUIT_AMULET, Integer.valueOf(0xE05CFF));  // arcane violet
        COLORS.put(ModItems.BAUBLE_FRUIT_BODY, Integer.valueOf(0xB5651D));    // leather brown
        COLORS.put(ModItems.BAUBLE_FRUIT_HEAD, Integer.valueOf(0x7FD8FF));    // clear-thought cyan
        COLORS.put(ModItems.BAUBLE_FRUIT_CHARM, Integer.valueOf(0xFF6B6B));   // charm red
        COLORS.put(ModItems.BAUBLE_FRUIT_BELT, Integer.valueOf(0x8B5A2B));    // tanned hide
        COLORS.put(ModItems.BAUBLE_FRUIT_ELYTRA, Integer.valueOf(0xF2F2F2));  // wing white
        COLORS.put(ModItems.BAUBLE_FRUIT_TOTEM, Integer.valueOf(0x4CD964));   // undying green
        COLORS.put(ModItems.BAUBLE_FRUIT_TRINKET, Integer.valueOf(0x9B7BFF)); // odds-and-ends lilac

        BaubleFruitColorHandler handler = new BaubleFruitColorHandler();
        event.getItemColors().registerItemColorHandler(handler, ModItems.getAllBaubleFruits());
    }

    @Override
    public int colorMultiplier(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) {
            return 0xFFFFFF;
        }
        Integer color = COLORS.get(stack.getItem());
        return color == null ? 0xFFFFFF : color.intValue();
    }
}
