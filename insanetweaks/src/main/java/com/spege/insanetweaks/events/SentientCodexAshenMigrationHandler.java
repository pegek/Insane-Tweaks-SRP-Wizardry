package com.spege.insanetweaks.events;

import com.spege.insanetweaks.InsaneTweaksMod;
import com.spege.insanetweaks.api.AdvPropertyRegistry;
import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.enchant.EnchantmentSentientCodex;
import com.spege.insanetweaks.util.AdvPropertyStorage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * One-shot migration for the 1.4.21 split of Ashen Legacy away from Sentient Codex.
 *
 * <p>Sentient Codex used to confer Ashen Legacy for free, so every Codex item in an existing world
 * is silently lava-proof. Flipping {@code conferAshenLegacy} off decouples the two - which is the
 * point - but it would also strip that protection from gear that has had it all along, without the
 * player doing anything or being told. This grants those items the property <b>explicitly</b>, once,
 * so they keep exactly the behaviour they had while everything minted afterwards follows the new
 * rule.
 *
 * <p>Deliberately a migration and not a standing rule. A standing "Codex implies Ashen Legacy unless
 * NBT says otherwise" check could not tell an old item from a new one, so it would keep conferring
 * forever and the split would never actually happen. Running once per player, gated by a flag in
 * their persistent data, draws the line at the moment of the update.
 *
 * <p>Reach is inventory, armour, offhand and ender chest - everything the player carries. A Codex
 * item left in a chest is not seen; it keeps whatever it was explicitly granted, and can be given a
 * Property Book like anything else. Covering every loaded chest in the world would be a far larger,
 * far riskier sweep for a rare, reward-only enchantment.
 */
public class SentientCodexAshenMigrationHandler {

    /** Persistent per-player marker so the sweep runs exactly once. */
    private static final String MIGRATED_TAG = "insanetweaks_codex_ashen_migrated";

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        if (player == null || player.world == null || player.world.isRemote) {
            return;
        }
        // Nothing to migrate while the two are still bundled - the property is conferred live.
        if (ModConfig.enchantments.sentientCodex.conferAshenLegacy
                || !ModConfig.enchantments.sentientCodex.grandfatherAshenLegacy) {
            return;
        }

        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        NBTTagCompound persisted = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(MIGRATED_TAG)) {
            return;
        }
        persisted.setBoolean(MIGRATED_TAG, true);

        int granted = 0;
        granted += grantIn(player.inventory.mainInventory);
        granted += grantIn(player.inventory.armorInventory);
        granted += grantIn(player.inventory.offHandInventory);
        granted += grantIn(player.getInventoryEnderChest());

        if (granted > 0) {
            // The stacks are the live inventory objects, so the NBT edit is already real and will
            // be saved with the player; this just makes sure the client sees the new tooltip line
            // straight away rather than after the next inventory change.
            if (player.inventoryContainer != null) {
                player.inventoryContainer.detectAndSendChanges();
            }
            InsaneTweaksMod.LOGGER.info(
                    "[InsaneTweaks] Sentient Codex / Ashen Legacy split: granted Ashen Legacy to {} "
                    + "existing item(s) carried by {}, so they keep the drop protection they already had.",
                    Integer.valueOf(granted), player.getName());
        }
    }

    private static int grantIn(Iterable<ItemStack> stacks) {
        int granted = 0;
        for (ItemStack stack : stacks) {
            if (grant(stack)) {
                granted++;
            }
        }
        return granted;
    }

    private static int grantIn(IInventory inventory) {
        int granted = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            if (grant(inventory.getStackInSlot(slot))) {
                granted++;
            }
        }
        return granted;
    }

    private static boolean grant(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !EnchantmentSentientCodex.hasSentientCodex(stack)) {
            return false;
        }
        // add() is a no-op when the stack already lists it, so this is safe to call blindly.
        return AdvPropertyStorage.add(stack, AdvPropertyRegistry.ASHEN_LEGACY);
    }
}
