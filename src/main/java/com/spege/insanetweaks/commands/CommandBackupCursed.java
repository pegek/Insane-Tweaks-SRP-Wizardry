package com.spege.insanetweaks.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import javax.annotation.Nonnull;

public class CommandBackupCursed extends CommandBase {

    @Override
    public String getName() {
        return "restorecursed";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/restorecursed <player> [timestamp|list|latest]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    @SuppressWarnings("null")
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
        if (args.length < 1) {
            sender.sendMessage(new TextComponentString("\u00A7cUsage: /restorecursed <player> [timestamp|list|latest]"));
            return;
        }

        EntityPlayer target = getPlayer(server, sender, args[0]);
        String action = args.length > 1 ? args[1] : "latest";

        NBTTagCompound playerData = target.getEntityData();
        NBTTagCompound persistentData = playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);

        if (!persistentData.hasKey("CursedHistory", 9)) {
            sender.sendMessage(new TextComponentString("\u00A7cNo backup history found for " + target.getName() + " !"));
            return;
        }

        NBTTagList backupHistory = persistentData.getTagList("CursedHistory", 10);
        if (backupHistory.tagCount() == 0) {
            sender.sendMessage(new TextComponentString("\u00A7cBackup history is empty for " + target.getName() + " !"));
            return;
        }

        if ("list".equalsIgnoreCase(action)) {
            sender.sendMessage(new TextComponentString("\u00A7eAvailable backups for " + target.getName() + ":"));
            for (int i = 0; i < backupHistory.tagCount(); i++) {
                NBTTagCompound entry = backupHistory.getCompoundTagAt(i);
                sender.sendMessage(new TextComponentString("\u00A77- \u00A7b" + entry.getString("Time") + " \u00A78(" + entry.getTagList("Items", 10).tagCount() + " items)"));
            }
            return;
        }

        NBTTagCompound backupToRestore = null;
        int indexToRemove = -1;

        if ("latest".equalsIgnoreCase(action)) {
            indexToRemove = backupHistory.tagCount() - 1;
            backupToRestore = backupHistory.getCompoundTagAt(indexToRemove);
        } else {
            for (int i = 0; i < backupHistory.tagCount(); i++) {
                NBTTagCompound entry = backupHistory.getCompoundTagAt(i);
                if (entry.getString("Time").equals(action)) {
                    backupToRestore = entry;
                    indexToRemove = i;
                    break;
                }
            }
        }

        if (backupToRestore == null) {
            sender.sendMessage(new TextComponentString("\u00A7cBackup not found for timestamp: " + action));
            return;
        }

        NBTTagList list = backupToRestore.getTagList("Items", 10);
        int restored = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound itemNBT = list.getCompoundTagAt(i);
            ItemStack stack = new ItemStack(itemNBT);
            if (!stack.isEmpty()) {
                if (!target.inventory.addItemStackToInventory(stack)) {
                    target.entityDropItem(stack, target.getEyeHeight());
                }
                restored++;
            }
        }

        backupHistory.removeTag(indexToRemove);
        persistentData.setTag("CursedHistory", backupHistory);

        sender.sendMessage(new TextComponentString("\u00A7aSuccessfully restored " + restored + " items from " + backupToRestore.getString("Time") + " !"));
    }
}
