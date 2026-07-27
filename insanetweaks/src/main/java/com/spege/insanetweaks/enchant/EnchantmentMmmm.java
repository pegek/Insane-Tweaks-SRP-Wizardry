package com.spege.insanetweaks.enchant;

import com.google.common.base.Predicate;
import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.config.ModConfig;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEnchantedBook;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;

/**
 * Mmmm - native 1.12.2 port of UniqueEnchantments' Ambrosia, applied to food. Eating an enchanted
 * food stack fills the hunger bar outright and grants the {@code insanetweaks:nourished} effect,
 * which pins saturation at full for a duration that scales with the eater's XP level. All the
 * runtime logic lives in {@link MmmmHandler}; this class is only the registered {@link Enchantment}.
 *
 * <p><b>Single tier by design.</b> {@code maxLevel} defaults to 1 and
 * {@code ModConfig.enchantments.mmmm.powerPerLevel} defaults to 2, so our level I feeds the
 * original's formula with an effective level of 2 - i.e. one tier here is exactly as strong as
 * Ambrosia II upstream, with no weaker step to find first.
 *
 * <p><b>Deliberate narrowing vs the original.</b> Upstream Ambrosia also accepts
 * {@code ItemPotion}/{@code ItemSplashPotion}/{@code ItemLingeringPotion}; this port is food-only,
 * both in {@link #canApply(ItemStack)} and in the handler's own re-check.
 *
 * <p>NOT obtainable by natural means - quest-gated in the modpack. That comes for free from
 * {@link EnchantmentInsaneTweaksBase}: the treasure flag plus the enchanting-table refusal, and the
 * {@code mixins/enchant/} + {@code mixins/enchantsources/} guards key off the {@code insanetweaks}
 * namespace, so this enchantment was protected the moment it got a registry name. The
 * enchantability numbers below therefore only ever shape a table roll that cannot happen; they are
 * kept at Sentient Codex's out-of-reach values. Applying a quest-reward book on an anvil still
 * works, which is the intended route.
 *
 * @see com.spege.insanetweaks.util.EnchantSourceGuard
 */
public class EnchantmentMmmm extends EnchantmentInsaneTweaksBase {

    /** Set on construction so {@link #getLevel} works before/without a registry lookup. */
    public static EnchantmentMmmm INSTANCE;

    /** Lazily created so it exists before the super() call in the constructor. */
    private static EnumEnchantmentType type;

    public EnchantmentMmmm() {
        super(Rarity.RARE, foodType(),
                new EntityEquipmentSlot[] { EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND });
        setName("mmmm"); // -> translation key enchantment.mmmm
        setRegistryName(new ResourceLocation(InsaneTweaksMod.MODID, "mmmm"));
        INSTANCE = this;
    }

    /**
     * A private enchantment type matching only {@link ItemFood}, created through {@link EnumHelper}
     * the same way {@link EnchantmentSwiftPicking} makes its picker type. Upstream reuses
     * {@code EnumEnchantmentType.BREAKABLE}, which would drag the enchantment into every
     * "any damageable item" pool a third-party mod builds off the type.
     *
     * <p>The type stays non-null on purpose: {@code ItemEnchantedBook.getSubItems} filters on
     * {@code type != null} alone, so this is what keeps the book visible in JEI and creative.
     * Anonymous class rather than a lambda because {@code EnumHelper.addEnchantmentType} wants a
     * Guava {@link Predicate}.
     */
    private static EnumEnchantmentType foodType() {
        if (type == null) {
            type = EnumHelper.addEnchantmentType("INSANETWEAKS_FOOD", new Predicate<Item>() {
                @Override
                public boolean apply(Item item) {
                    return item instanceof ItemFood;
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
        return ModConfig.enchantments.mmmm.maxLevel;
    }

    @Override
    public int getMinEnchantability(int level) {
        return 70; // practically off the enchanting table, which is additionally blocked outright
    }

    @Override
    public int getMaxEnchantability(int level) {
        return 200;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    // isTreasureEnchantment() -> true and canApplyAtEnchantingTable() -> false come from
    // EnchantmentInsaneTweaksBase.

    /**
     * Food only, plus the book that carries it to the anvil. Note that the anvil ignores this when
     * the player is in creative mode (verified in {@code ContainerRepair} bytecode), so a creative
     * test will happily stick this on a sword - that is vanilla behaviour, not a hole here.
     */
    @Override
    public boolean canApply(ItemStack stack) {
        return stack.getItem() instanceof ItemFood || stack.getItem() instanceof ItemEnchantedBook;
    }

    /** Mmmm level on the stack, or 0 when absent / not yet registered. */
    public static int getLevel(ItemStack stack) {
        if (INSTANCE == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        return EnchantmentHelper.getEnchantmentLevel(INSTANCE, stack);
    }
}
