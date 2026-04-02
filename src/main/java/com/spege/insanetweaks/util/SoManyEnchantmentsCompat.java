package com.spege.insanetweaks.util;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

import com.google.common.collect.Multimap;
import com.spege.insanetweaks.InsaneTweaksMod;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/**
 * Re-applies SME attack speed enchant modifiers for bridge weapons that bypass
 * the vanilla Item#getAttributeModifiers path patched by So Many Enchantments.
 */
public final class SoManyEnchantmentsCompat {

    private static final UUID SWIFTER_SLASHES_UUID = UUID.fromString("fc1c8dca-9411-4a4e-97a4-90e66c883a77");
    private static final UUID HEAVY_WEIGHT_UUID = UUID.fromString("e2765897-134f-4c14-a535-29c3ae5c7a21");
    private static final String ATTACK_SPEED_NAME = SharedMonsterAttributes.ATTACK_SPEED.getName();

    private static boolean initialized = false;
    private static Enchantment swifterSlashes;
    private static Enchantment heavyWeight;

    private SoManyEnchantmentsCompat() {
    }

    public static void addAttackSpeedModifiers(ItemStack stack, Multimap<String, AttributeModifier> multimap) {
        if (!Loader.isModLoaded("somanyenchantments") || stack.isEmpty()) {
            return;
        }

        ensureInitialized();

        addSwifterSlashesModifier(stack, multimap);
        addHeavyWeightModifier(stack, multimap);
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        initialized = true;

        try {
            Class<?> registryClass = Class.forName("com.shultrea.rin.registry.EnchantmentRegistry");
            Object swifter = registryClass.getField("swifterSlashes").get(null);
            Object heavy = registryClass.getField("heavyWeight").get(null);

            if (swifter instanceof Enchantment) {
                swifterSlashes = (Enchantment) swifter;
            }
            if (heavy instanceof Enchantment) {
                heavyWeight = (Enchantment) heavy;
            }
        } catch (Throwable t) {
            if (com.spege.insanetweaks.config.ModConfig.client.displayDebugInfo) {
                InsaneTweaksMod.LOGGER.warn("[InsaneTweaks] SME compat init failed: {}", t.getMessage());
            }
        }
    }

    private static void addSwifterSlashesModifier(ItemStack stack, Multimap<String, AttributeModifier> multimap) {
        if (swifterSlashes == null || hasModifier(multimap.get(ATTACK_SPEED_NAME), SWIFTER_SLASHES_UUID)) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(swifterSlashes, stack);
        if (level <= 0 || !isEnabled(swifterSlashes)) {
            return;
        }

        multimap.put(ATTACK_SPEED_NAME,
                new AttributeModifier(SWIFTER_SLASHES_UUID, "swifterSlashes", 0.2D * level, 1));
    }

    private static void addHeavyWeightModifier(ItemStack stack, Multimap<String, AttributeModifier> multimap) {
        if (heavyWeight == null || hasModifier(multimap.get(ATTACK_SPEED_NAME), HEAVY_WEIGHT_UUID)) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(heavyWeight, stack);
        if (level <= 0 || !isEnabled(heavyWeight)) {
            return;
        }

        multimap.put(ATTACK_SPEED_NAME,
                new AttributeModifier(HEAVY_WEIGHT_UUID, "heavyWeight", -(0.2D + (0.1D * level)), 1));
    }

    private static boolean isEnabled(Enchantment enchantment) {
        try {
            Method method = enchantment.getClass().getMethod("isEnabled");
            Object result = method.invoke(enchantment);
            return !(result instanceof Boolean) || ((Boolean) result).booleanValue();
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean hasModifier(Collection<AttributeModifier> modifiers, UUID uuid) {
        if (modifiers == null) {
            return false;
        }

        for (AttributeModifier modifier : modifiers) {
            if (uuid.equals(modifier.getID())) {
                return true;
            }
        }

        return false;
    }
}
