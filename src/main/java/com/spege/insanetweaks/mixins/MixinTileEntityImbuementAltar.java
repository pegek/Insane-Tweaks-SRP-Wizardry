package com.spege.insanetweaks.mixins;

import electroblob.wizardry.constants.Element;
import electroblob.wizardry.tileentity.TileEntityImbuementAltar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.spege.insanetweaks.init.ModItems;

/**
 * Replaces the faulty registration in Electroblob's Wizardry's Imbuement Altar
 * (TileEntityImbuementAltar).
 * Due to a bug in "ImbuementActivateEvent", it was impossible to create custom
 * imbuement recipes normally.
 * This Mixin intercepts the altar's ingredients right before returning the
 * result.
 */
@Mixin(value = TileEntityImbuementAltar.class, remap = false)
public class MixinTileEntityImbuementAltar {

    @SuppressWarnings("null")
    @Inject(method = "getImbuementResult", at = @At("HEAD"), cancellable = true, remap = false)
    private static void insanetweaks$onGetImbuementResult(ItemStack input, Element[] receptacleElements,
            boolean fullLootGen, World world, EntityPlayer lastUser, CallbackInfoReturnable<ItemStack> cir) {

        if (input.isEmpty())
            return;

        ResourceLocation inputRegName = input.getItem().getRegistryName();
        if (inputRegName == null)
            return;

        // RECIPE BUNDLE: Any Master Wand + Custom Dusts -> Potency / Cost / Speedcast Core
        if (inputRegName.getResourceDomain().equals("ebwizardry") &&
                inputRegName.getResourcePath().startsWith("master_") &&
                inputRegName.getResourcePath().endsWith("wand")) {

            int earthCount = 0;
            int healingCount = 0;
            int fireCount = 0;
            int lightningCount = 0;
            int sorceryCount = 0;
            int necromancyCount = 0;

            for (Element el : receptacleElements) {
                if (el == Element.EARTH) earthCount++;
                else if (el == Element.HEALING) healingCount++;
                else if (el == Element.FIRE) fireCount++;
                else if (el == Element.LIGHTNING) lightningCount++;
                else if (el == Element.SORCERY) sorceryCount++;
                else if (el == Element.NECROMANCY) necromancyCount++;
            }

            // 1. Potency Core (2x Sorcery, 2x Necromancy)
            if (sorceryCount == 2 && necromancyCount == 2) {
                cir.setReturnValue(new ItemStack((net.minecraft.item.Item) ModItems.POTENCY_CORE, 1));
            } 
            // 2. Cost Core (2x Earth, 2x Healing)
            else if (earthCount == 2 && healingCount == 2) {
                cir.setReturnValue(new ItemStack((net.minecraft.item.Item) ModItems.COST_CORE, 1));
            } 
            // 3. Speedcast Core (2x Fire, 2x Lightning)
            else if (fireCount == 2 && lightningCount == 2) {
                cir.setReturnValue(new ItemStack((net.minecraft.item.Item) ModItems.SPEEDCAST_CORE, 1));
            }
        }

        // RECIPE BUNDLE 2: Corrupted Fruit + receptacle combo -> typed Bauble Fruit.
        // The four receptacle elements pick which bauble slot the purified fruit grants.
        if (inputRegName.getResourceDomain().equals("insanetweaks")
                && inputRegName.getResourcePath().equals("corrupted_fruit")) {

            int earth = 0, healing = 0, fire = 0, lightning = 0, sorcery = 0, necromancy = 0, ice = 0;
            for (Element el : receptacleElements) {
                if (el == Element.EARTH) earth++;
                else if (el == Element.HEALING) healing++;
                else if (el == Element.FIRE) fire++;
                else if (el == Element.LIGHTNING) lightning++;
                else if (el == Element.SORCERY) sorcery++;
                else if (el == Element.NECROMANCY) necromancy++;
                else if (el == Element.ICE) ice++;
            }

            net.minecraft.item.Item result = null;
            if      (sorcery == 2 && healing == 2)   result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_RING;
            else if (healing == 4)                   result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_AMULET;
            else if (earth == 4)                     result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_BODY;
            else if (sorcery == 4)                   result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_HEAD;
            else if (fire == 2 && sorcery == 2)      result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_CHARM;
            else if (earth == 2 && healing == 2)     result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_BELT;
            else if (lightning == 4)                 result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_ELYTRA;
            else if (necromancy == 4)                result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_TOTEM;
            else if (lightning == 2 && ice == 2)     result = (net.minecraft.item.Item) ModItems.BAUBLE_FRUIT_TRINKET;

            if (result != null) {
                cir.setReturnValue(new ItemStack(result, 1));
            }
        }

    }
}
