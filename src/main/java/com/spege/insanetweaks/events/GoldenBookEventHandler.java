package com.spege.insanetweaks.events;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import com.spege.insanetweaks.config.ModConfig;

public class GoldenBookEventHandler {

    private static final ResourceLocation GOLDEN_BOOK_ID = new ResourceLocation("insanetweaks", "golden_book");

    @SubscribeEvent
    @SuppressWarnings("null")
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();

        // Only run on server side
        if (player.world.isRemote) return;

        // Check only once per second (every 20 ticks) for performance
        if (player.ticksExisted % 20 != 0) return;

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            // Check if this is our golden_book
            ResourceLocation regName = stack.getItem().getRegistryName();
            if (regName == null || !regName.equals(GOLDEN_BOOK_ID)) continue;

            // Check if it has been enchanted (has 'ench' NBT tag)
            if (!stack.hasTagCompound()) continue;
            NBTTagCompound nbt = stack.getTagCompound();
            if (nbt == null || !nbt.hasKey("ench")) continue;

            NBTTagList enchList = nbt.getTagList("ench", 10);
            if (enchList.tagCount() == 0) continue;

            // Copy enchantments to StoredEnchantments format
            NBTTagList stored = new NBTTagList();
            for (int j = 0; j < enchList.tagCount(); j++) {
                stored.appendTag(enchList.getCompoundTagAt(j).copy());
            }

            // Build the resulting minecraft:enchanted_book
            ItemStack enchBook = new ItemStack(Items.ENCHANTED_BOOK, 1);
            NBTTagCompound newNbt = new NBTTagCompound();
            newNbt.setTag("StoredEnchantments", stored);
            enchBook.setTagCompound(newNbt);

            // Replace the slot with the converted book
            player.inventory.setInventorySlotContents(i, enchBook);

            if (ModConfig.client.displayDebugInfo) {
                player.sendMessage(new TextComponentString(
                    "\u00a76Golden Book \u00a7flost all its power and transformed into an Enchanted Book!"
                ));
            }
        }
    }
}
