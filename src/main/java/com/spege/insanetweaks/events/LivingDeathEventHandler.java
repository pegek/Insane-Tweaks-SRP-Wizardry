package com.spege.insanetweaks.events;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.util.NonNullList;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

public class LivingDeathEventHandler {

    private void debugMessage(EntityPlayer player, String message) {
        if (!com.spege.insanetweaks.config.ModConfig.displayInfoOnDeath)
            return;

        if (!player.world.isRemote) {
            player.sendMessage(new TextComponentString("\u00A7d[Insane Tweaks]\u00A7f " + message));
        }
    }

    private boolean hasPossessionCurse(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty() || !itemStack.hasTagCompound()) {
            return false;
        }

        NBTTagCompound nbt = itemStack.getTagCompound();
        if (nbt == null) return false;
        
        NBTTagList enchantTag = null;

        if (nbt.hasKey("ench")) {
            enchantTag = nbt.getTagList("ench", 10);
        } else if (nbt.hasKey("StoredEnchantments")) {
            enchantTag = nbt.getTagList("StoredEnchantments", 10);
        }

        if (enchantTag != null) {
            for (int i = 0; i < enchantTag.tagCount(); i++) {
                NBTTagCompound enchant = enchantTag.getCompoundTagAt(i);
                Enchantment enchantObj = Enchantment.getEnchantmentByID(enchant.getShort("id"));
                if (enchantObj != null) {
                    net.minecraft.util.ResourceLocation regName = enchantObj.getRegistryName();
                    String registryName = regName != null
                            ? regName.toString().toLowerCase()
                            : "";
                    String translatedName = enchantObj.getTranslatedName(enchant.getShort("lvl")).toLowerCase();

                    if ((registryName.contains("curse") && registryName.contains("posses")) ||
                            (translatedName.contains("curse") && translatedName.contains("posses"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean scanInventoryAndRemove(NonNullList<ItemStack> inventoryList, EntityPlayer player,
            List<ItemStack> removedList) {
        boolean removed = false;
        for (int i = 0; i < inventoryList.size(); i++) {
            ItemStack currentStack = inventoryList.get(i);
            if (!currentStack.isEmpty()) {
                if (hasPossessionCurse(currentStack)) {
                    debugMessage(player,
                            "Found item with Curse of Possession: " + currentStack.getDisplayName() + ". Removing.");
                    removedList.add(currentStack.copy());
                    inventoryList.set(i, ItemStack.EMPTY);
                    removed = true;
                }
            }
        }
        return removed;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    @SuppressWarnings("null")
    public void onLivingDeath(LivingDeathEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.enableCurseOfPossessionPatch) return;
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getEntity();
        debugMessage(player, "Player death detected! Scanning inventory...");

        List<ItemStack> removedItems = new ArrayList<>();
        scanInventoryAndRemove(player.inventory.mainInventory, player, removedItems);
        scanInventoryAndRemove(player.inventory.armorInventory, player, removedItems);
        scanInventoryAndRemove(player.inventory.offHandInventory, player, removedItems);

        if (removedItems.size() > 0) {
            NBTTagCompound playerData = player.getEntityData();
            NBTTagCompound persistentData = playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            if (!playerData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
                if (persistentData != null) playerData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persistentData);
            }

            if (persistentData == null) return;

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM:HH:mm");
            String timestamp = sdf.format(new java.util.Date());

            NBTTagList backupHistory;
            if (persistentData.hasKey("CursedHistory", 9)) {
                backupHistory = persistentData.getTagList("CursedHistory", 10);
            } else {
                backupHistory = new NBTTagList();
            }

            NBTTagCompound newEntry = new NBTTagCompound();
            if (timestamp != null) newEntry.setString("Time", timestamp);

            NBTTagList itemsList = new NBTTagList();
            for (ItemStack stack : removedItems) {
                if (stack != null && !stack.isEmpty()) {
                    itemsList.appendTag(stack.writeToNBT(new NBTTagCompound()));
                }
            }
            newEntry.setTag("Items", itemsList);

            backupHistory.appendTag(newEntry);

            while (backupHistory.tagCount() > 3) {
                backupHistory.removeTag(0);
            }

            persistentData.setTag("CursedHistory", backupHistory);
            debugMessage(player,
                    "Scan complete. " + removedItems.size() + " items backed up as snapshot [" + timestamp + "].");
        } else {
            debugMessage(player, "Scan complete. No items with Curse of Possession found.");
        }
    }
}
