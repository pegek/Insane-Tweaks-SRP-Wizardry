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
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;

/**
 * Sentient Codex - native 1.12.2 port of UniqueEnchantments' Grimoire (which only ships on
 * 1.16.5). A VERY_RARE, reward-only treasure/curse enchantment that dynamically raises the
 * level of every other enchantment on the item as the holder's XP level grows. All the
 * per-tick logic (boost recompute, owner-binding, drop protection, anvil block) lives in
 * {@link SentientCodexHandler}; this class is only the registered {@link Enchantment}.
 *
 * <p>Tunables come from {@link ModConfig#sentientCodex}; the master toggle is
 * {@code ModConfig.modules.enableSentientCodex}.
 */
public class EnchantmentSentientCodex extends Enchantment {

    /** String UUID of the bound owner (owner-binding). */
    public static final String OWNER_TAG = "sentientcodex_owner";
    /** int: cumulative growth-step count already applied to the item's live "ench" levels. */
    public static final String LAST_BOOST_TAG = "sentientcodex_boost";

    public static EnchantmentSentientCodex INSTANCE;

    public EnchantmentSentientCodex() {
        // EnumEnchantmentType.ALL = applies to anything enchantable; all equipment slots.
        super(Rarity.VERY_RARE, EnumEnchantmentType.ALL, EntityEquipmentSlot.values());
        setName("sentientcodex"); // -> translation key enchantment.sentientcodex
        setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "sentientcodex"));
        INSTANCE = this;
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return ModConfig.enchantments.sentientCodex.maxLevel;
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
    public boolean isCurse() {
        return true;
    }

    @Override
    public String getTranslatedName(int level) {
        String s = I18n.translateToLocal(this.getName());
        s = TextFormatting.DARK_RED + s;
        return level == 1 && this.getMaxLevel() == 1 ? s : s + " " + I18n.translateToLocal("enchantment.level." + level);
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

    @Override
    protected boolean canApplyTogether(Enchantment other) {
        // Temporarily commented out Mending exclusion
        /*
        if (other != null && other.getRegistryName() != null
                && other.getRegistryName().toString().equals("minecraft:mending")) {
            return false;
        }
        */
        return super.canApplyTogether(other) && other != this;
    }

    // --- static helpers (shared by SentientCodexHandler + LegendaryDropHelper / tooltip) ---

    /** Sentient Codex enchantment level on the stack, or 0 if absent / not yet registered. */
    public static int getSentientCodexLevel(ItemStack stack) {
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

    /** True when the stack carries the Sentient Codex enchantment at any level. */
    public static boolean hasSentientCodex(ItemStack stack) {
        return getSentientCodexLevel(stack) > 0;
    }
}
