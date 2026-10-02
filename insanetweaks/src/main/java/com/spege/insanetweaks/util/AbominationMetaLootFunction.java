package com.spege.insanetweaks.util;

import java.util.Random;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.init.ModElements;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;

/**
 * {@code "function": "insanetweaks:abomination_meta"} - sets an element-keyed EBW item (spectral
 * dust, crystal) to the Abomination variant by reading {@code ModElements.ABOMINATION.ordinal()}.
 *
 * <p>Replaces {@code "set_data": 8} in the sim wizard tables. JSON cannot compute an ordinal, so the
 * tables used to hard-code it while the recipes read it live: a second element-adding mod in the
 * pack would move the recipes and leave the drops behind on someone else's element.
 *
 * <p>🚨 Keep it the LAST function of its entry. With the element missing ({@code EXTENDED == false})
 * there is no right variant to drop, so the stack is shrunk to zero and {@code LootEntryItem} skips
 * it. A {@code set_count} or {@code looting_enchant} after this one would grow it back - into the
 * vanilla-meta item, which is the wrong element. The old {@code set_data 8} was worse in that state:
 * a meta past the end of {@code Element.values()}.
 */
public class AbominationMetaLootFunction extends LootFunction {

    public static final ResourceLocation ID = new ResourceLocation(InsaneTweaksMod.MODID, "abomination_meta");

    public AbominationMetaLootFunction(LootCondition[] conditions) {
        super(conditions);
    }

    @Override
    public ItemStack apply(ItemStack stack, Random rand, LootContext context) {
        if (ModElements.EXTENDED) {
            stack.setItemDamage(ModElements.ABOMINATION.ordinal());
        } else {
            stack.setCount(0);
        }
        return stack;
    }

    public static class Serializer extends LootFunction.Serializer<AbominationMetaLootFunction> {

        public Serializer() {
            super(ID, AbominationMetaLootFunction.class);
        }

        @Override
        public void serialize(JsonObject object, AbominationMetaLootFunction function,
                JsonSerializationContext context) {
            // No parameters: the element is this mod's own, there is nothing to choose.
        }

        @Override
        public AbominationMetaLootFunction deserialize(JsonObject object, JsonDeserializationContext context,
                LootCondition[] conditions) {
            return new AbominationMetaLootFunction(conditions);
        }
    }
}
