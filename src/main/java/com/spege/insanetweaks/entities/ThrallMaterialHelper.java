package com.spege.insanetweaks.entities;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockLog;
import net.minecraft.block.IGrowable;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;

/**
 * Forge OreDictionary helpers used by Thrall AI tasks for cross-mod material identification.
 * All methods are null-safe and stateless.
 */
@SuppressWarnings("null")
public final class ThrallMaterialHelper {
    private ThrallMaterialHelper() {}

    /** Returns true if the stack is registered to the given Forge OreDictionary entry. */
    public static boolean hasOreName(ItemStack stack, String name) {
        if (stack.isEmpty()) return false;
        for (int id : OreDictionary.getOreIDs(stack)) {
            if (name.equals(OreDictionary.getOreName(id))) return true;
        }
        return false;
    }

    /** Logs from any mod, via OreDict "logWood". Falls back to BlockLog instanceof for unregistered logs. */
    public static boolean isLogItem(ItemStack stack) {
        if (hasOreName(stack, "logWood")) return true;
        Block block = Block.getBlockFromItem(stack.getItem());
        return block instanceof BlockLog;
    }

    /** Vanilla "coal" OreDict entry covers both coal and charcoal; modded coals usually register here too. */
    public static boolean isCoalItem(ItemStack stack) {
        return hasOreName(stack, "coal");
    }

    // -------------------------------------------------------------------------
    // FARMING helpers (D1)
    // -------------------------------------------------------------------------

    /**
     * Returns true if the block at this state is a fully-grown plant ready to harvest.
     * Covers vanilla {@link BlockCrops} and any modded {@link IGrowable} that reports it
     * cannot grow further (server-side check — pass the live world).
     */
    public static boolean isMatureCrop(World world, BlockPos pos, IBlockState state) {
        if (state == null) return false;
        Block block = state.getBlock();
        if (block instanceof BlockCrops) {
            return ((BlockCrops) block).isMaxAge(state);
        }
        if (block instanceof IGrowable) {
            return !((IGrowable) block).canGrow(world, pos, state, false);
        }
        return false;
    }

    /**
     * Vanilla seed mapping kept as a fast-path; modded crops fall through to the
     * generic {@link #findPlantableSeedSlot} probe.
     */
    @Nullable
    public static Item getVanillaSeedItem(Block cropBlock) {
        if (cropBlock == Blocks.WHEAT)     return Items.WHEAT_SEEDS;
        if (cropBlock == Blocks.CARROTS)   return Items.CARROT;
        if (cropBlock == Blocks.POTATOES)  return Items.POTATO;
        if (cropBlock == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        return null;
    }

    /**
     * Searches the inventory for a stack whose item is an {@link IPlantable} that, when planted,
     * produces the given crop block. Works for vanilla and modded seeds (anything implementing
     * IPlantable). Returns the slot index, or -1 if no matching seed is in inventory.
     *
     * @param plantPos the position the seed would be planted at — passed to IPlantable.getPlant
     *                 because some implementations consult world context (rare, but legal)
     */
    public static int findPlantableSeedSlot(IInventory inv, IBlockAccess world, BlockPos plantPos, Block cropBlock) {
        // Vanilla fast-path
        Item vanilla = getVanillaSeedItem(cropBlock);
        if (vanilla != null) {
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (!s.isEmpty() && s.getItem() == vanilla) return i;
            }
        }
        // Generic IPlantable probe
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty()) continue;
            Item item = s.getItem();
            if (!(item instanceof IPlantable)) continue;
            try {
                IBlockState planted = ((IPlantable) item).getPlant(world, plantPos);
                if (planted != null && planted.getBlock() == cropBlock) return i;
            } catch (RuntimeException ignored) {
                // Defensive: some modded IPlantables NPE without a real world/pos. Skip them.
            }
        }
        return -1;
    }

    public static boolean isHoeItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemHoe;
    }

    /** Bone meal is dye damage 15. Forge ItemDye.applyBonemeal handles the actual growth. */
    public static boolean isBoneMeal(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() == Items.DYE
                && stack.getMetadata() == 15;
    }
}
