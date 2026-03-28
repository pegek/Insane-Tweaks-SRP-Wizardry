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

            for (Element el : receptacleElements) {
                if (el == Element.EARTH) earthCount++;
                else if (el == Element.HEALING) healingCount++;
                else if (el == Element.FIRE) fireCount++;
                else if (el == Element.LIGHTNING) lightningCount++;
                else if (el == Element.SORCERY) sorceryCount++;
            }

            // 1. Potency Core (4x Sorcery)
            if (sorceryCount == 4) {
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

    }
}
