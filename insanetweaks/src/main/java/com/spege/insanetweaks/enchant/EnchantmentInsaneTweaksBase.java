package com.spege.insanetweaks.enchant;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;

/**
 * Common base for every InsaneTweaks enchantment. Its only job is to make "not obtainable by natural
 * means" the <b>default</b> rather than something each new enchantment has to remember: extend this
 * instead of {@link Enchantment} and the protection is there.
 *
 * <p>Two layers protect these enchantments, deliberately independent of each other:
 * <ol>
 * <li><b>This class</b> — plain vanilla API. {@link #isTreasureEnchantment()} plus
 *     {@link #canApplyAtEnchantingTable(ItemStack)} are what {@code EnchantmentHelper} itself checks,
 *     so the enchanting table and every {@code allowTreasure == false} roll (mob equipment,
 *     {@code enchant_with_levels} without {@code "treasure": true}) drop the enchantment with no
 *     bytecode patching at all.</li>
 * <li><b>The mixins under {@code mixins/enchant/}</b> — for the vanilla paths that consult neither
 *     flag: {@code enchant_randomly} loot functions, librarian enchanted-book trades, and
 *     {@code allowTreasure == true} book rolls such as {@code chests/stronghold_library}.</li>
 * </ol>
 * Layer 1 matters because mixin application is silent under {@code required: false} — if a mixin ever
 * stops matching after a Forge or Cleanroom bump, the enchanting table is still covered.
 *
 * <p>{@code canApply} is intentionally NOT restricted here: it is what the <b>anvil</b> checks
 * (verified in {@code ContainerRepair} bytecode), and applying a quest-reward book on an anvil is the
 * intended way to obtain these. Subclasses narrow {@code canApply} to their own item where that makes
 * sense — see {@link EnchantmentSwiftPicking}.
 *
 * @see com.spege.insanetweaks.util.EnchantSourceGuard
 */
public abstract class EnchantmentInsaneTweaksBase extends Enchantment {

    protected EnchantmentInsaneTweaksBase(Rarity rarity, EnumEnchantmentType type, EntityEquipmentSlot[] slots) {
        super(rarity, type, slots);
    }

    /**
     * Treasure-only. In {@code EnchantmentHelper.getEnchantmentDatas} this single flag rejects the
     * enchantment outright whenever the caller passed {@code allowTreasure == false}, which is every
     * enchanting table roll and every mob-equipment roll.
     */
    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

    /** Never offered at the enchanting table, for any item. */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return false;
    }
}
