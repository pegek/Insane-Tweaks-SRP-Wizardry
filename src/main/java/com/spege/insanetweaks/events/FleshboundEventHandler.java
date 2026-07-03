package com.spege.insanetweaks.events;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class FleshboundEventHandler {

    public static boolean isMechanicUnlocked(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() == null) return false;

        // 1. Static check
        if (stack.getItem() instanceof com.spege.insanetweaks.items.spellblade.BridgeSpellblade) {
            if (((com.spege.insanetweaks.items.spellblade.BridgeSpellblade) stack.getItem()).hasWeaponProperty(com.spege.insanetweaks.init.ModWeaponProperties.FLESHBOUND)) {
                return true;
            }
        }

        // 2. Dynamic check for Sentient Spellblade
        if ("insanetweaks:sentient_spellblade".equals(String.valueOf(stack.getItem().getRegistryName()))) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.getInteger("SentientKills") >= 1900) {
                return true;
            }
        }

        return false;
    }

    public static boolean isFleshbound(ItemStack stack, World world) {
        if (!isMechanicUnlocked(stack)) return false;

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return true; // Newly evolved or statically assigned sword without tags is bound

        if (!tag.hasKey("FleshboundRegrowTime") && !tag.hasKey("FleshboundRegrowKills")) {
            return true;
        }

        long regrowTime = tag.getLong("FleshboundRegrowTime");
        int regrowKills = tag.getInteger("FleshboundRegrowKills");
        int currentKills = tag.getInteger("SentientKills");

        // If time passed and enough kills gathered
        if (world.getTotalWorldTime() >= regrowTime && currentKills >= regrowKills) {
            return true;
        }

        return false;
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        EntityItem entityItem = event.getEntityItem();
        if (entityItem == null) return;

        ItemStack stack = entityItem.getItem();
        EntityPlayer player = event.getPlayer();

        if (player != null && !player.world.isRemote && isFleshbound(stack, player.world)) {
            event.setCanceled(true);
            player.inventory.addItemStackToInventory(stack);
            player.sendStatusMessage(new TextComponentString(TextFormatting.DARK_RED + "The weapon is grafted to your flesh!"), true);
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity().world.isRemote) return;
        if (!(event.getEntity() instanceof EntityItem)) return;

        EntityItem entityItem = (EntityItem) event.getEntity();
        ItemStack stack = entityItem.getItem();

        if (!stack.isEmpty() && isFleshbound(stack, entityItem.world)) {
            String throwerName = entityItem.getThrower();
            if (throwerName != null) {
                EntityPlayer player = entityItem.world.getPlayerEntityByName(throwerName);
                if (player != null && player.isEntityAlive()) {
                    event.setCanceled(true);
                    player.inventory.addItemStackToInventory(stack);
                    entityItem.setDead();
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        World world = player.world;

        for (ItemStack stack : player.inventory.mainInventory) {
            severLink(stack, world);
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            severLink(stack, world);
        }
    }

    private void severLink(ItemStack stack, World world) {
        if (!isMechanicUnlocked(stack)) return;
        
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            int currentKills = tag.getInteger("SentientKills");
            
            // Set the cooldown logic
            tag.setLong("FleshboundRegrowTime", world.getTotalWorldTime() + 36000L); // 30 minutes
            tag.setInteger("FleshboundRegrowKills", currentKills + 50);
        }
    }
}
