package com.spege.tombtweaks.util;

import com.spege.tombtweaks.config.TombTweaksConfig;

import net.minecraft.item.ItemStack;
import ovh.corail.tombstone.registry.ModEnchantments;
import ovh.corail.tombstone.registry.ModItems;

/**
 * Whether a grave key is immune to Soulbound.
 *
 * <p>Tombstone 4.8.0 treats every grave key as soulbound, unconditionally:
 * {@code EntityHelper.hasSoulbound} returns true for the Soulbound enchantment, for an ancient
 * Fishing Rod of Misadventure <em>and for {@code ModItems.grave_key}</em>, and it never looks at
 * {@code enableEnchantmentSoulbound}. Disabling Soulbound in Tombstone's config therefore leaves
 * carried keys surviving every death - the complaint this exists to answer.
 *
 * <p>Both mixins that use this delegate the whole decision here, so the master switch and the
 * option are honoured in one place. Lives outside {@code com.spege.tombtweaks.mixins} - that
 * package may only hold mixins.
 */
public final class GraveKeySoulboundPolicy {

    private GraveKeySoulboundPolicy() {}

    /** True when the pack wants keys to lose their soulbound status. Read live. */
    private static boolean keysImmune() {
        return TombTweaksConfig.tombstone.enableTombstoneTweaks
                && !TombTweaksConfig.tombstone.graveKeysSurviveDeath;
    }

    private static boolean isGraveKey(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ModItems.grave_key;
    }

    /**
     * At death: should this stack be kept off the grave as soulbound?
     *
     * @param stack     the dropped stack Tombstone is deciding about
     * @param tombstone Tombstone's own answer ({@code EntityHelper.hasSoulbound})
     */
    public static boolean keepOnDeath(ItemStack stack, boolean tombstone) {
        if (tombstone && keysImmune() && isGraveKey(stack)) {
            return false;
        }
        return tombstone;
    }

    /** At the anvil: refuse Soulbound on a grave key, so nobody wastes a book on it. */
    public static boolean refusesEnchantment(Object enchantment, ItemStack stack) {
        return enchantment == ModEnchantments.soulbound && keysImmune() && isGraveKey(stack);
    }
}
