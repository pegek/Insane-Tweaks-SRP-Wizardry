package com.spege.insanetweaks.enchant;

import com.google.common.base.Predicate;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.items.AutoLockPickerItem;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;

/**
 * Swift Picking I-III: shortens the {@link AutoLockPickerItem} channel by
 * {@code ModConfig.autoLockPicker.swiftReductionPerLevel} per level (default -15%/level, so III
 * channels at 55% of the base time). The reduction itself is applied in
 * {@link AutoLockPickerItem#computeChannelTicks}; this class is only the registered
 * {@link Enchantment}.
 *
 * <p>Applies to nothing but our picker, via a dedicated {@link EnumEnchantmentType} — the same
 * trick Locks uses for its own {@code LOCK_TYPE}.
 *
 * <p>NOT obtainable by natural means — quest-gated in the modpack. Extending
 * {@link EnchantmentInsaneTweaksBase} blocks the enchanting table and every {@code allowTreasure}
 * roll; the {@code mixins/enchant/} trio closes chest loot and librarian trades. The
 * enchantability tunables below therefore only shape the anvil cost and are kept for config
 * compatibility. Applying a quest-reward book on an anvil still works, which is the intended route.
 */
public class EnchantmentSwiftPicking extends EnchantmentInsaneTweaksBase {

    /** Set on construction so {@link #getLevel} works before/without a registry lookup. */
    public static EnchantmentSwiftPicking INSTANCE;

    /** Lazily created so it exists before the super() call in the constructor. */
    private static EnumEnchantmentType type;

    public EnchantmentSwiftPicking() {
        super(Rarity.RARE, pickerType(), new EntityEquipmentSlot[] { EntityEquipmentSlot.MAINHAND });
        setName("swiftpicking"); // -> translation key enchantment.swiftpicking
        setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "swift_picking"));
        INSTANCE = this;
    }

    /**
     * A private enchantment type matching only the Auto Lock Picker. Created through
     * {@link EnumHelper} because {@link EnumEnchantmentType} is a vanilla enum; mirrors
     * {@code LocksEnchantments.LOCK_TYPE}. Anonymous class rather than a lambda because
     * {@code EnumHelper.addEnchantmentType} wants a Guava {@link Predicate}.
     */
    private static EnumEnchantmentType pickerType() {
        if (type == null) {
            type = EnumHelper.addEnchantmentType("INSANETWEAKS_AUTO_LOCK_PICK", new Predicate<Item>() {
                @Override
                public boolean apply(Item item) {
                    return item instanceof AutoLockPickerItem;
                }
            });
        }
        return type;
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return ModConfig.enchantments.swiftPicking.maxLevel;
    }

    @Override
    public int getMinEnchantability(int level) {
        return ModConfig.enchantments.swiftPicking.minEnchantability
                + (level - 1) * ModConfig.enchantments.swiftPicking.enchantabilityPerLevel;
    }

    @Override
    public int getMaxEnchantability(int level) {
        return getMinEnchantability(level) + ModConfig.enchantments.swiftPicking.enchantabilityWindow;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    @Override
    public boolean canApply(ItemStack stack) {
        return stack.getItem() instanceof AutoLockPickerItem || stack.getItem() instanceof ItemEnchantedBook;
    }

    /** Swift Picking level on the stack, or 0 when absent / not yet registered. */
    public static int getLevel(ItemStack stack) {
        if (INSTANCE == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        return EnchantmentHelper.getEnchantmentLevel(INSTANCE, stack);
    }
}
