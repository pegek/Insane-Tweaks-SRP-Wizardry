package com.spege.tombtweaks.platform;

import com.spege.tombtweaks.core.ItemKey;
import com.spege.tombtweaks.core.StackView;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Jedyne miejsce, w ktorym ItemStack zamienia sie w cos, co rozumie core. */
public final class StackViews {

    private static final String UNKNOWN = "minecraft:air";

    private StackViews() {
    }

    public static String idOf(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? UNKNOWN : id.toString();
    }

    public static String idOf(ItemStack stack) {
        return idOf(stack.getItem());
    }

    /**
     * Hash NBT musi byc stabilny miedzy restartami JVM, bo snapshoty sa zapisywane.
     * {@code CompoundTag.hashCode()} liczy sie z zawartosci, wiec jest.
     */
    public static ItemKey keyOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return new ItemKey(idOf(stack), tag == null ? 0 : tag.hashCode());
    }

    public static StackView view(final ItemStack stack) {
        return new StackView() {
            @Override
            public String itemId() {
                return idOf(stack);
            }

            @Override
            public Set<String> enchantmentIds() {
                Map<Enchantment, Integer> found = EnchantmentHelper.getEnchantments(stack);
                if (found.isEmpty()) {
                    return Collections.emptySet();
                }
                Set<String> ids = new HashSet<>();
                for (Enchantment enchantment : found.keySet()) {
                    ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
                    if (id != null) {
                        ids.add(id.toString());
                    }
                }
                return ids;
            }

            @Override
            public String nbtString(String key) {
                CompoundTag tag = stack.getTag();
                if (tag == null || !tag.contains(key, Tag.TAG_STRING)) {
                    return null;
                }
                return tag.getString(key);
            }
        };
    }
}
