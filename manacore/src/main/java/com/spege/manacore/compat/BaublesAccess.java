package com.spege.manacore.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import baubles.api.BaublesApi;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;

/**
 * The stacks in a player's bauble slots, or none when Baubles is absent.
 *
 * <p>Every Baubles type is confined to {@link Impl}, a nested class the JVM loads only on its
 * first use - which {@link #equippedStacks} reaches only after {@code isModLoaded("baubles")}.
 * The public signature names nothing but vanilla types, so callers link without Baubles present.
 */
public final class BaublesAccess {

    private static final String BAUBLES_MODID = "baubles";

    private BaublesAccess() {
    }

    /** Live references to the equipped stacks: changing one changes the slot. Never null. */
    public static List<ItemStack> equippedStacks(EntityPlayer player) {
        if (player == null || !Loader.isModLoaded(BAUBLES_MODID)) {
            return Collections.emptyList();
        }
        return Impl.stacks(player);
    }

    private static final class Impl {

        private Impl() {
        }

        static List<ItemStack> stacks(EntityPlayer player) {
            // The EntityLivingBase overload: the EntityPlayer one is deprecated in BaublesEX.
            IItemHandler handler = BaublesApi.getBaublesHandler((EntityLivingBase) player);
            if (handler == null) {
                return Collections.emptyList();
            }
            List<ItemStack> stacks = new ArrayList<ItemStack>(handler.getSlots());
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
            return stacks;
        }
    }
}
