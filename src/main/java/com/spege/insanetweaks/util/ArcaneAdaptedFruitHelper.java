package com.spege.insanetweaks.util;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.init.ModItems;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;

public final class ArcaneAdaptedFruitHelper {

    public static final String TAG_GEAR_ACHIEVEMENT = "ArcaneBridgeAchievementUnlocked";
    public static final String TAG_FRUIT_REWARDED = "ArcaneAdaptedFruitRewarded";
    public static final String TAG_FRUIT_PENDING = "ArcaneAdaptedFruitPending";
    public static final String TAG_FRUIT_CONSUMED = "ArcaneAdaptedFruitConsumed";

    public static final double MAX_MANA_BONUS = 2500.0D;
    public static final float FOREIGN_SPELL_COST_MULTIPLIER = 2.0F;
    public static final ResourceLocation ADVANCEMENT_ID = new ResourceLocation(InsaneTweaksMod.MODID,
            "obtain_living_sentient_gear");
    public static final String CLAIM_COMMAND = "/claimarcanefruit";

    private ArcaneAdaptedFruitHelper() {
    }

    public static NBTTagCompound getPersistentData(EntityPlayer player) {
        NBTTagCompound playerData = player.getEntityData();
        if (!playerData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            playerData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static boolean hasUnlockedGearAchievement(EntityPlayer player) {
        return getPersistentData(player).getBoolean(TAG_GEAR_ACHIEVEMENT);
    }

    public static boolean hasRewardedFruit(EntityPlayer player) {
        return getPersistentData(player).getBoolean(TAG_FRUIT_REWARDED);
    }

    public static boolean hasPendingFruit(EntityPlayer player) {
        return getPersistentData(player).getBoolean(TAG_FRUIT_PENDING);
    }

    public static boolean hasConsumedFruit(EntityPlayer player) {
        return getPersistentData(player).getBoolean(TAG_FRUIT_CONSUMED);
    }

    public static void markConsumedFruit(EntityPlayer player) {
        NBTTagCompound persistent = getPersistentData(player);
        persistent.setBoolean(TAG_FRUIT_CONSUMED, true);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persistent);
    }

    public static void markGearAchievementUnlocked(EntityPlayer player) {
        NBTTagCompound persistent = getPersistentData(player);
        persistent.setBoolean(TAG_GEAR_ACHIEVEMENT, true);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persistent);
    }

    public static void setFruitRewarded(EntityPlayer player, boolean rewarded) {
        NBTTagCompound persistent = getPersistentData(player);
        persistent.setBoolean(TAG_FRUIT_REWARDED, rewarded);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persistent);
    }

    public static void setFruitPending(EntityPlayer player, boolean pending) {
        NBTTagCompound persistent = getPersistentData(player);
        persistent.setBoolean(TAG_FRUIT_PENDING, pending);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persistent);
    }

    public static boolean playerHasQualifyingGear(EntityPlayer player) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (isQualifyingGear(stack)) {
                return true;
            }
        }

        for (ItemStack stack : player.inventory.offHandInventory) {
            if (isQualifyingGear(stack)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isUsingBridgeArcaneGear(EntityPlayer player) {
        return isQualifyingGear(player.getHeldItemMainhand()) || isQualifyingGear(player.getHeldItemOffhand());
    }

    public static boolean isQualifyingGear(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        Item item = stack.getItem();
        return item == ModItems.LIVING_WAND
                || item == ModItems.SENTIENT_WAND
                || item == ModItems.LIVING_SPELLBLADE
                || item == ModItems.SENTIENT_SPELLBLADE;
    }

    public static ItemStack createFruitStack() {
        return new ItemStack(ModItems.ARCANE_ADAPTED_FRUIT);
    }

    public static boolean tryGiveFruit(EntityPlayerMP player) {
        ItemStack reward = createFruitStack();
        if (player.inventory.addItemStackToInventory(reward)) {
            player.inventoryContainer.detectAndSendChanges();
            setFruitPending(player, false);
            player.sendMessage(new TextComponentString(
                    TextFormatting.LIGHT_PURPLE + "The Arcane Adapted Fruit has been placed in your inventory."));
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.9f, 0.8f);
            return true;
        }

        setFruitPending(player, true);
        sendPendingClaimMessage(player);
        return false;
    }

    public static void sendPendingClaimMessage(EntityPlayerMP player) {
        TextComponentString prefix = new TextComponentString(
                TextFormatting.DARK_PURPLE + "[InsaneTweaks] " + TextFormatting.GRAY
                        + "Your inventory is full. ");
        TextComponentString button = new TextComponentString(
                TextFormatting.LIGHT_PURPLE + "" + TextFormatting.BOLD + "[Claim Arcane Adapted Fruit]");
        button.setStyle(new Style().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, CLAIM_COMMAND)));
        prefix.appendSibling(button);
        player.sendMessage(prefix);
    }

    public static void sendAchievementMessage(EntityPlayerMP player) {
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "" + TextFormatting.BOLD
                + "Challenge Complete: " + TextFormatting.YELLOW + "Obtain Living/Sentient EBWizardry Gear"));
        player.sendMessage(new TextComponentString(
                TextFormatting.GRAY + "A strange fruit answers the pact and becomes yours."));
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.0f);
    }

    public static void grantAdvancement(EntityPlayerMP player) {
        if (player.getServerWorld() == null) {
            return;
        }

        Advancement advancement = player.getServerWorld().getAdvancementManager().getAdvancement(ADVANCEMENT_ID);
        if (advancement == null) {
            return;
        }

        AdvancementProgress progress = player.getAdvancements().getProgress(advancement);
        if (progress.isDone()) {
            return;
        }

        for (String criterion : progress.getRemaningCriteria()) {
            player.getAdvancements().grantCriterion(advancement, criterion);
        }
    }
}
