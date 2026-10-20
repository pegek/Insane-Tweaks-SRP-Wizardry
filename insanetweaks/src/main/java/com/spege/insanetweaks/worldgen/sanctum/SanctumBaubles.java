package com.spege.insanetweaks.worldgen.sanctum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/**
 * One random bauble/artefact from any mod for the main chest. Baubles is optional for content,
 * so its type is only named inside {@link Api}, which loads only when Baubles is present.
 */
public final class SanctumBaubles {

    private static List<Item> candidates;

    private SanctumBaubles() {
    }

    public static ItemStack pick(double roll) {
        List<Item> list = candidates();
        if (list.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(list.get(Math.min(list.size() - 1, (int) (roll * list.size()))));
    }

    private static synchronized List<Item> candidates() {
        if (candidates == null) {
            candidates = Loader.isModLoaded("baubles") ? Api.collect() : new ArrayList<Item>();
        }
        return candidates;
    }

    private static final class Api {
        private Api() {
        }

        static List<Item> collect() {
            Set<String> banned = new HashSet<String>(Arrays.asList(ModConfig.arcaneSanctum.baubleBlacklist));
            List<Item> out = new ArrayList<Item>();
            for (Item item : Item.REGISTRY) {
                if (item instanceof baubles.api.IBauble && item.getCreativeTab() != null
                        && item.getRegistryName() != null && !banned.contains(item.getRegistryName().toString())) {
                    out.add(item);
                }
            }
            return out;
        }
    }
}
