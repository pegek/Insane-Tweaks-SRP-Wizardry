package com.spege.insanetweaks.enchant;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

/**
 * Grimoire - native 1.12.2 port of UniqueEnchantments' Grimoire (which only ships on
 * 1.16.5). A VERY_RARE, reward-only treasure enchantment that dynamically raises the
 * level of every other enchantment on the item as the holder's XP level grows. All the
 * per-tick logic (boost recompute, owner-binding, drop protection, anvil block) lives in
 * {@link GrimoireHandler}; this class is only the registered {@link Enchantment}.
 *
 * <p>Tunables come from {@link ModConfig#grimoire}; the master toggle is
 * {@code ModConfig.modules.enableGrimoire}.
 */
public class EnchantmentGrimoire extends Enchantment {

    /** NBTTagList of the item's base (pre-boost) enchantments, captured once. */
    public static final String STORAGE_TAG = "grimoire_storage";
    /** String UUID of the bound owner (owner-binding). */
    public static final String OWNER_TAG = "grimoire_owner";
    /** int: the last boost value written to the live "ench" list. */
    public static final String LAST_BOOST_TAG = "grimoire_boost";

    public static EnchantmentGrimoire INSTANCE;

    public EnchantmentGrimoire() {
        // EnumEnchantmentType.ALL = applies to anything enchantable; all equipment slots.
        super(Rarity.VERY_RARE, EnumEnchantmentType.ALL, EntityEquipmentSlot.values());
        setName("grimoire"); // -> translation key enchantment.grimoire
        setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "grimoire"));
        INSTANCE = this;
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return ModConfig.grimoire.maxLevel;
    }

    @Override
    public int getMinEnchantability(int level) {
        return 70; // practically off the enchanting table
    }

    @Override
    public int getMaxEnchantability(int level) {
        return 200;
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // reward-only: never obtainable at the enchanting table
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canApply(ItemStack stack) {
        return stack.isItemEnchantable() || stack.getItem() instanceof ItemEnchantedBook;
    }

    // incompatible with Mending (as UE); everything else stays default
    @Override
    protected boolean canApplyTogether(Enchantment other) {
        if (other != null && other.getRegistryName() != null
                && other.getRegistryName().toString().equals("minecraft:mending")) {
            return false;
        }
        return super.canApplyTogether(other) && other != this;
    }

    // --- static helpers (shared by GrimoireHandler + LegendaryDropHelper / tooltip) ---

    /** Grimoire enchantment level on the stack, or 0 if absent / not yet registered. */
    public static int getGrimoireLevel(ItemStack stack) {
        if (INSTANCE == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        NBTTagList list = stack.getEnchantmentTagList();
        int gid = Enchantment.getEnchantmentID(INSTANCE);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound en = list.getCompoundTagAt(i);
            if (en.getShort("id") == gid) {
                return en.getShort("lvl");
            }
        }
        return 0;
    }

    /** True when the stack carries the Grimoire enchantment at any level. */
    public static boolean hasGrimoire(ItemStack stack) {
        return getGrimoireLevel(stack) > 0;
    }
}
