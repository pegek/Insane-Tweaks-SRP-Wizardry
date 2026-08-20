package com.spege.insanetweaks.util;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.init.ModItems;
import com.windanesz.ancientspellcraft.item.ItemBattlemageSword;

import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class AdaptationUpgradeHelper {

    /**
     * Highest adaptation level a focus can reach. One: the upgrade is a key, not a ladder.
     *
     * <p>It used to be 3, and levels II and III did nothing whatsoever - every consumer either
     * tested {@code > 0} or printed a Roman numeral, and the one numeric consumer
     * ({@link #getForeignFocusAbominationCostMultiplier}) shipped at 1.0 for every level. The item
     * description promised "Stacks up to III", so a player could spend three upgrades to buy an
     * effect that existed twice over in the tooltip and nowhere in the code.
     */
    public static final int MAX_ADAPTATION_LEVEL = 1;

    private AdaptationUpgradeHelper() {
    }

    public static ItemStack findCastingItem(EntityPlayer player, Spell spell) {
        if (player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack mainhand = player.getHeldItemMainhand();
        ItemStack offhand = player.getHeldItemOffhand();

        if (matchesCastingSpell(mainhand, spell)) {
            return mainhand;
        }

        if (matchesCastingSpell(offhand, spell)) {
            return offhand;
        }

        if (isSpellcastingItem(mainhand)) {
            return mainhand;
        }

        if (isSpellcastingItem(offhand)) {
            return offhand;
        }

        return ItemStack.EMPTY;
    }

    public static boolean isSpellcastingItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        return item instanceof ItemWand || item instanceof ItemBattlemageSword;
    }

    public static boolean matchesCastingSpell(ItemStack stack, Spell spell) {
        if (stack.isEmpty() || spell == null || !isSpellcastingItem(stack)) {
            return false;
        }

        return WandHelper.getCurrentSpell(stack) == spell;
    }

    public static int getDefaultAdaptationLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        Item item = stack.getItem();
        if (item == ModItems.LIVING_WAND
                || item == ModItems.SENTIENT_WAND
                || item == ModItems.LIVING_SPELLBLADE
                || item == ModItems.SENTIENT_SPELLBLADE) {
            return 1;
        }

        return 0;
    }

    public static int getAppliedAdaptationUpgradeLevel(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        return WandHelper.getUpgradeLevel(stack, ModItems.ADAPTATION_UPGRADE);
    }

    public static int getEffectiveAdaptationLevel(ItemStack stack) {
        return Math.min(MAX_ADAPTATION_LEVEL, getDefaultAdaptationLevel(stack) + getAppliedAdaptationUpgradeLevel(stack));
    }

    public static int getMaxAppliedAdaptationUpgrades(ItemStack stack) {
        return Math.max(0, MAX_ADAPTATION_LEVEL - getDefaultAdaptationLevel(stack));
    }

    /**
     * Cost multiplier for casting an Abomination spell from a focus that is not one of ours and
     * qualifies only through an applied Adaptation upgrade.
     *
     * <p>Defaults to 1.0, i.e. no surcharge. The mechanism ships switched off so the balance
     * question can be settled with a config edit rather than a code change.
     *
     * <p>Takes no level argument: {@link #MAX_ADAPTATION_LEVEL} caps the upgrade at one level, so
     * there is no ladder of levels left to distinguish - a foreign focus either qualifies (one
     * upgrade applied) or it doesn't, and {@link #getDefaultAdaptationLevel(ItemStack)} already
     * screens out anything that qualifies by being one of our own foci before this is called.
     */
    public static float getForeignFocusAbominationCostMultiplier() {
        return (float) ModConfig.gear.wands.foreignFocusAbominationCost;
    }
}
