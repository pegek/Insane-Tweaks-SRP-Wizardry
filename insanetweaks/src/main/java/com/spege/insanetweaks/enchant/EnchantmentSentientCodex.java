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
 * Sentient Codex - a VERY_RARE, reward-only treasure/curse enchantment that raises the level of the
 * other enchantments on its item as its holder feeds it experience. Loosely after
 * UniqueEnchantments' Grimoire, which only ships on 1.16.5, but the growth model is no longer
 * theirs: 🚨 only experience earned <b>while the item is carried</b> counts, and the XP the holder
 * already had is worth nothing. All the per-interval logic (feeding, growth, starvation, repair,
 * anvil block) lives in {@link SentientCodexHandler}; which enchantments it may raise and how far is
 * {@link SentientCodexPool}; this class is only the registered {@link Enchantment}.
 *
 * <p>Tunables come from {@link ModConfig#sentientCodex}; the master toggle is
 * {@code ModConfig.modules.enableSentientCodex}.
 *
 * <p>Reward-only, and quest-gated in the modpack: {@link EnchantmentInsaneTweaksBase} supplies the
 * treasure flag and the enchanting-table refusal that used to be overridden here, and the
 * {@code mixins/enchant/} trio closes chest loot and librarian trades.
 */
public class EnchantmentSentientCodex extends EnchantmentInsaneTweaksBase {

    /** int: cumulative growth-step count already applied to the item's live "ench" levels. */
    public static final String LAST_BOOST_TAG = "sentientcodex_boost";
    /** long: experience points fed to this item while it was carried. */
    public static final String FED_TAG = "sentientcodex_fed";
    /** long: world time the item last received experience, for the starvation clock. */
    public static final String LAST_FED_TAG = "sentientcodex_lastfed";
    /** long: world time the item was last seen being carried, so a spell in a chest is not a fast. */
    public static final String LAST_SEEN_TAG = "sentientcodex_lastseen";

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

    // isTreasureEnchantment() -> true comes from EnchantmentInsaneTweaksBase.

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

    // canApplyAtEnchantingTable() -> false comes from EnchantmentInsaneTweaksBase.

    @Override
    public boolean canApply(ItemStack stack) {
        return stack.isItemEnchantable() || stack.getItem() instanceof ItemEnchantedBook;
    }

    /**
     * Refuses to share an item with anything on {@code sentientCodex.excluded}.
     *
     * <p>That list ships with both Mending variants on it, and this is the half that makes the
     * exclusion real: without it the anvil would happily build a Mending + Codex item that the boost
     * logic then quietly declined to touch. Sentient Codex repairs the item it lives on, so it is
     * meant to be an alternative to Mending, not an addition to it.
     *
     * <p>Reading the same config list here as the boost does is deliberate - two lists would drift.
     */
    @Override
    protected boolean canApplyTogether(Enchantment other) {
        if (SentientCodexPool.isIncompatible(other)) {
            return false;
        }
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
            // getInteger on "id", not getShort: JustEnoughIDs widens enchantment ids past 32767 and
            // rewrites vanilla's own reads, but not ours - and a truncated id here would silently
            // report level 0. "lvl" stays a short; JEID only widens the id.
            if (en.getInteger("id") == gid) {
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
