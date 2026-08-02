package com.spege.tombtweaks.commands;

import javax.annotation.Nonnull;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * {@code /tombtweaks restore <cursed|decay> <player> [list|latest|timestamp]} — hands back items
 * this mod took away.
 *
 * <p>Two handlers confiscate items and both keep a snapshot of what they took in the player's
 * persisted data: {@code CurseOfPossessionHandler} writes {@code CursedHistory}, and
 * {@code GraveDecayHandler} writes {@code GraveDecayHistory}. The snapshots are the safety net for
 * both features being heuristic — the curse check matches an enchantment by name fragments, and
 * decay deletes at random — so an admin can undo either.
 *
 * <p>Both NBT keys are the ones Insane Tweaks used before the R4 split, so histories recorded by
 * the old {@code /itweaks restore} are still readable here. A restored snapshot is removed from the
 * history, which is what keeps the same backup from being claimed twice.
 */
@SuppressWarnings("null")
public class CommandTombTweaks extends CommandBase {

    @Override
    @Nonnull
    public String getName() {
        return "tombtweaks";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/tombtweaks restore <cursed|decay> <player> [list|latest|timestamp]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // The root command needs 0 permissions; the subcommand checks internally.
    }

    @Override
    public boolean checkPermission(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
            @Nonnull String[] args) throws CommandException {
        if (args.length < 1) {
            sendHelp(sender);
            return;
        }

        if ("restore".equalsIgnoreCase(args[0])) {
            handleRestore(server, sender, args);
        } else {
            sendHelp(sender);
        }
    }

    private void sendHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentString("§5--- Tombstone Tweaks Commands ---"));
        if (sender.canUseCommand(2, "tombtweaks")) {
            sender.sendMessage(new TextComponentString(
                    "§e/tombtweaks restore cursed <player> [latest|list|timestamp]§7 - Restore saved cursed items"));
            sender.sendMessage(new TextComponentString(
                    "§e/tombtweaks restore decay <player> [latest|list|timestamp]§7 - Restore items decayed from graves"));
        } else {
            sender.sendMessage(new TextComponentString("§7No commands available at your permission level."));
        }
    }

    private void handleRestore(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (!sender.canUseCommand(2, "tombtweaks")) {
            sender.sendMessage(new TextComponentString("§cYou do not have permission to use this command."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(new TextComponentString(
                    "§cUsage: /tombtweaks restore <cursed|decay> <player> [list|latest|timestamp]"));
            return;
        }

        String type = args[1].toLowerCase();

        String nbtKey;
        if (type.equals("cursed")) {
            nbtKey = "CursedHistory";
        } else if (type.equals("decay")) {
            nbtKey = "GraveDecayHistory";
        } else {
            sender.sendMessage(new TextComponentString("§cInvalid restore type! Use: cursed or decay."));
            return;
        }

        EntityPlayer target = getPlayer(server, sender, args[2]);
        String action = args.length > 3 ? args[3] : "latest";

        NBTTagCompound playerData = target.getEntityData();
        NBTTagCompound persistentData = playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);

        if (!persistentData.hasKey(nbtKey, 9)) {
            sender.sendMessage(new TextComponentString("§cNo backup history found for " + target.getName() + " !"));
            return;
        }

        NBTTagList backupHistory = persistentData.getTagList(nbtKey, 10);
        if (backupHistory.tagCount() == 0) {
            sender.sendMessage(new TextComponentString("§cBackup history is empty for " + target.getName() + " !"));
            return;
        }

        if ("list".equalsIgnoreCase(action)) {
            sender.sendMessage(new TextComponentString("§eAvailable backups for " + target.getName() + " (" + type + "):"));
            for (int i = 0; i < backupHistory.tagCount(); i++) {
                NBTTagCompound entry = backupHistory.getCompoundTagAt(i);
                sender.sendMessage(new TextComponentString("§7- §b" + entry.getString("Time")
                        + " §8(" + entry.getTagList("Items", 10).tagCount() + " items)"));
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
            sender.sendMessage(new TextComponentString("§cBackup not found for timestamp: " + action));
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
        persistentData.setTag(nbtKey, backupHistory);

        sender.sendMessage(new TextComponentString("§aSuccessfully restored " + restored
                + " items from " + backupToRestore.getString("Time") + " !"));

        if (sender != target) {
            target.sendMessage(new TextComponentString("§aYour " + type + " items have been restored by an Admin!"));
        }
    }
}
