package com.spege.insanetweaks.util;

import com.spege.insanetweaks.init.ModItems;
import com.windanesz.ancientspellcraft.item.ItemBattlemageSword;

import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public final class AdaptationUpgradeHelper {

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
        return Math.min(3, getDefaultAdaptationLevel(stack) + getAppliedAdaptationUpgradeLevel(stack));
    }

    public static int getMaxAppliedAdaptationUpgrades(ItemStack stack) {
        return Math.max(0, 3 - getDefaultAdaptationLevel(stack));
    }

    public static float getForeignSpellCostMultiplier(int level) {
        switch (Math.max(0, Math.min(3, level))) {
            case 1:
                return 2.0f;
            case 2:
                return 1.5f;
            case 3:
                return 1.0f;
            default:
                return 1.0f;
        }
    }

    public static int getForeignSpellCostPenaltyPercent(int level) {
        switch (Math.max(0, Math.min(3, level))) {
            case 1:
                return 100;
            case 2:
                return 50;
            case 3:
                return 0;
            default:
                return 0;
        }
    }
}
