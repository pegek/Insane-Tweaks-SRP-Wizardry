package com.spege.insanetweaks.events;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class TombstoneBooksHandler {

    private static class PendingCooldownCheck {
        EntityPlayer player;
        EnumHand hand;
        ItemStack originalStack;
        int originalSize;
        long timeStamp;
        long cooldownTicks;
        String nbtKey;

        public PendingCooldownCheck(EntityPlayer player, EnumHand hand, ItemStack originalStack, long ticks, String nbtKey) {
            this.player = player;
            this.hand = hand;
            this.originalStack = originalStack.copy();
            this.originalSize = originalStack.getCount();
            this.timeStamp = player.world.getTotalWorldTime();
            this.cooldownTicks = ticks;
            this.nbtKey = nbtKey;
        }
    }

    private final List<PendingCooldownCheck> pendingChecks = new ArrayList<>();

    private void syncCooldownToTracker(EntityPlayer player, Item item, String nbtKey) {
        NBTTagCompound persistentData = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (persistentData.hasKey(nbtKey)) {
            long cooldownEnd = persistentData.getLong(nbtKey);
            long currentTime = player.world.getTotalWorldTime();
            if (currentTime < cooldownEnd) {
                int remainingTicks = (int)(cooldownEnd - currentTime);
                player.getCooldownTracker().setCooldown(item, remainingTicks);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!com.spege.insanetweaks.config.ModConfig.tombstone.enableTombstoneTweaks || event.player.world.isRemote) {
            return;
        }
        Item disenchantBook = Item.getByNameOrId("tombstone:book_of_disenchantment");
        if (disenchantBook != null) {
            syncCooldownToTracker(event.player, disenchantBook, "InsaneTweaks_DisenchantBookCooldown");
        }
        
        Item impregnationBook = Item.getByNameOrId("tombstone:book_of_magic_impregnation");
        if (impregnationBook != null) {
            syncCooldownToTracker(event.player, impregnationBook, "InsaneTweaks_ImpregnationBookCooldown");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickGrave(PlayerInteractEvent.RightClickBlock event) {
        if (!com.spege.insanetweaks.config.ModConfig.tombstone.enableTombstoneTweaks || event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }

        EntityPlayer player = event.getEntityPlayer();
        ItemStack mainStack = player.getHeldItemMainhand();
        ItemStack offStack = player.getHeldItemOffhand();
        ItemStack bookStack = ItemStack.EMPTY;
        EnumHand bookHand = EnumHand.MAIN_HAND;

        if (!mainStack.isEmpty() && mainStack.getItem().getRegistryName() != null &&
            (mainStack.getItem().getRegistryName().toString().equals("tombstone:book_of_disenchantment") || 
             mainStack.getItem().getRegistryName().toString().equals("tombstone:book_of_magic_impregnation"))) {
             bookStack = mainStack;
             bookHand = EnumHand.MAIN_HAND;
        } else if (!offStack.isEmpty() && offStack.getItem().getRegistryName() != null &&
            (offStack.getItem().getRegistryName().toString().equals("tombstone:book_of_disenchantment") || 
             offStack.getItem().getRegistryName().toString().equals("tombstone:book_of_magic_impregnation"))) {
             bookStack = offStack;
             bookHand = EnumHand.OFF_HAND;
        }

        if (bookStack.isEmpty()) return;

        // If the vanilla tracker already has a cooldown, Tombstone's native block handler will block it.
        // We just need to make sure the tracker is up to date, which we did on login.
        if (player.getCooldownTracker().hasCooldown(bookStack.getItem())) {
            return; 
        }

        String blockRegName = event.getWorld().getBlockState(event.getPos()).getBlock().getRegistryName().toString();
        if (!blockRegName.startsWith("tombstone:decorative_")) {
            return;
        }

        String regName = bookStack.getItem().getRegistryName().toString();
        double cooldownHours = 0;
        String nbtKey = "";

        if (regName.equals("tombstone:book_of_disenchantment")) {
            double conf = com.spege.insanetweaks.config.ModConfig.tombstone.bookOfDisenchantmentCooldownConfig;
            if (conf <= 0) return;
            cooldownHours = conf;
            nbtKey = "InsaneTweaks_DisenchantBookCooldown";
        } else if (regName.equals("tombstone:book_of_magic_impregnation")) {
            double conf = com.spege.insanetweaks.config.ModConfig.tombstone.bookOfMagicImpregnationCooldownConfig;
            if (conf <= 0) return;
            cooldownHours = conf;
            nbtKey = "InsaneTweaks_ImpregnationBookCooldown";
        } else {
            return;
        }

        // Just blindly schedule check, we no longer pre-block here since Vanilla Tracker does it
        long cooldownTicks = (long)(cooldownHours * 60 * 60 * 20);
        pendingChecks.add(new PendingCooldownCheck(player, bookHand, bookStack, cooldownTicks, nbtKey));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingChecks.isEmpty()) return;

        List<PendingCooldownCheck> toRemove = new ArrayList<>();
        long currentTime = pendingChecks.get(0).player.world.getTotalWorldTime();

        for (PendingCooldownCheck check : pendingChecks) {
            if (currentTime - check.timeStamp >= 1) {
                toRemove.add(check);

                ItemStack currentStack = check.player.getHeldItem(check.hand);
                boolean consumed = false;
                if (currentStack.isEmpty() || !currentStack.getItem().getRegistryName().equals(check.originalStack.getItem().getRegistryName()) || currentStack.getCount() < check.originalSize) {
                    consumed = true;
                }

                if (consumed) {
                    // Update Vanilla visual overlay
                    check.player.getCooldownTracker().setCooldown(check.originalStack.getItem(), (int)check.cooldownTicks);

                    // Cross-session persistent storage
                    NBTTagCompound playerData = check.player.getEntityData();
                    NBTTagCompound persistentData = playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
                    if (!playerData.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
                        playerData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persistentData);
                    }

                    long cooldownEndTime = check.player.world.getTotalWorldTime() + check.cooldownTicks;
                    persistentData.setLong(check.nbtKey, cooldownEndTime);
                }
            }
        }

        pendingChecks.removeAll(toRemove);
    }
}
