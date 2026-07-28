package com.spege.insanetweaks.commands;

import com.spege.insanetweaks.util.ArcaneAdaptedFruitHelper;
import com.spege.insanetweaks.util.EnchantGrantMarker;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;

@SuppressWarnings("null")
public class CommandInsaneTweaks extends CommandBase {

    @Override
    @Nonnull
    public String getName() {
        return "itweaks";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/itweaks <claimfruit | restore>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // The root command needs 0 permissions, but subcommands will check internally.
    }

    @Override
    public boolean checkPermission(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender) {
        return true; 
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args) throws CommandException {
        if (args.length < 1) {
            sendHelp(sender);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "claimfruit":
                handleClaimFruit(sender);
                break;
            case "restore":
                handleRestore(server, sender, args);
                break;
            case "grantbook":
                handleGrantBook(server, sender, args);
                break;
            case "propertybook":
                handlePropertyBook(server, sender, args);
                break;
            case "help":
            default:
                sendHelp(sender);
                break;
        }
    }

    private void sendHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentString("\u00A75--- Insane Tweaks Commands ---"));
        sender.sendMessage(new TextComponentString("\u00A7e/itweaks claimfruit\u00A77 - Claim your Arcane Adapted Fruit"));
        if (sender.canUseCommand(2, "itweaks")) { // If they have OP permissions
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks restore cursed <player> [latest|list|timestamp]\u00A77 - Restore saved cursed items"));
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks restore decay <player> [latest|list|timestamp]\u00A77 - Restore items decayed from graves"));
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks grantbook <enchantment> [level] [player]\u00A77 - Give a quest-granted enchanted book"));
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks propertybook <property> [player]\u00A77 - Give a Property Book (anvil onto a tool)"));
        }
    }

    /**
     * Mints a Property Book for one advanced property, the intended way for an FTB Quests reward to
     * hand one out without hand-authored NBT. Same shape as {@link #handleGrantBook}.
     *
     * <p>Only book-grantable properties are accepted; the failure message lists what is valid, since
     * the ids are internal and not otherwise discoverable in-game.
     */
    private void handlePropertyBook(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (!sender.canUseCommand(2, "itweaks")) {
            sender.sendMessage(new TextComponentString("\u00A7cYou do not have permission to use this command."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("\u00A7cUsage: /itweaks propertybook <property> [player]"));
            sendGrantableProperties(sender);
            return;
        }

        String id = args[1].toLowerCase(java.util.Locale.ROOT);
        com.spege.insanetweaks.api.AdvPropertyRegistry.Property property =
                com.spege.insanetweaks.api.AdvPropertyRegistry.getProperty(id);
        if (property == null || !property.bookGrantable) {
            sender.sendMessage(new TextComponentString("\u00A7cUnknown or non-grantable property: " + id));
            sendGrantableProperties(sender);
            return;
        }

        EntityPlayer target = args.length >= 3 ? getPlayer(server, sender, args[2])
                : getCommandSenderAsPlayer(sender);
        ItemStack book = com.spege.insanetweaks.items.PropertyBookItem.create(
                com.spege.insanetweaks.init.ModItems.PROPERTY_BOOK, property.id);
        if (!target.inventory.addItemStackToInventory(book)) {
            target.dropItem(book, false);
        }
        sender.sendMessage(new TextComponentString("\u00A7aGranted Property Book \u00A7e" + property.id
                + "\u00A7a to \u00A7e" + target.getName()));
    }

    private static void sendGrantableProperties(ICommandSender sender) {
        StringBuilder sb = new StringBuilder();
        for (com.spege.insanetweaks.api.AdvPropertyRegistry.Property p
                : com.spege.insanetweaks.api.AdvPropertyRegistry.getAll()) {
            if (p.bookGrantable) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(p.id);
            }
        }
        sender.sendMessage(new TextComponentString("\u00A77Available: \u00A7e"
                + (sb.length() == 0 ? "(none)" : sb.toString())));
    }

    /**
     * Mints an enchanted book stamped with the grant marker, so it actually works on an anvil (see
     * {@link com.spege.insanetweaks.util.EnchantGrantMarker}). This is the intended way for an FTB
     * Quests reward to hand out a quest-gated enchantment: a command reward calling this needs no
     * hand-written NBT.
     *
     * <p>The enchantment argument accepts a full registry name or a bare path, in which case the
     * mod's own namespace is assumed \u2014 {@code /itweaks grantbook swift_picking 3} is enough.
     */
    private void handleGrantBook(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!sender.canUseCommand(2, "itweaks")) {
            sender.sendMessage(new TextComponentString("\u00A7cYou do not have permission to use this command."));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("\u00A7cUsage: /itweaks grantbook <enchantment> [level] [player]"));
            return;
        }

        String id = args[1];
        ResourceLocation key = id.indexOf(':') >= 0 ? new ResourceLocation(id)
                : new ResourceLocation(com.spege.insanetweaks.InsaneTweaksMod.MODID, id);
        Enchantment enchantment = Enchantment.REGISTRY.getObject(key);
        if (enchantment == null) {
            sender.sendMessage(new TextComponentString("\u00A7cUnknown enchantment: " + key));
            return;
        }

        int level = enchantment.getMinLevel();
        if (args.length >= 3) {
            // Clamped rather than rejected: a quest reward asking for a level the config has since
            // lowered should still hand out the best legal book, not fail silently at runtime.
            level = MathHelper.clamp(parseInt(args[2]), enchantment.getMinLevel(), enchantment.getMaxLevel());
        }

        EntityPlayer target = args.length >= 4 ? getPlayer(server, sender, args[3]) : getCommandSenderAsPlayer(sender);
        ItemStack book = EnchantGrantMarker.createGrantedBook(enchantment, level);
        if (!target.inventory.addItemStackToInventory(book)) {
            target.dropItem(book, false);
        }
        sender.sendMessage(new TextComponentString("\u00A7aGranted \u00A7e" + key + " " + level
                + "\u00A7a to \u00A7e" + target.getName()));
    }

    private void handleClaimFruit(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);

        if (!ArcaneAdaptedFruitHelper.hasPendingFruit(player)) {
            player.sendMessage(new TextComponentString(TextFormatting.GRAY + "You have no Arcane Adapted Fruit waiting to be claimed."));
            return;
        }

        if (!ArcaneAdaptedFruitHelper.tryGiveFruit(player)) {
            player.sendMessage(new TextComponentString(TextFormatting.RED + "You still need at least one free inventory slot."));
        }
    }

    private void handleRestore(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        // Permissions for OP-only restore sub-commands
        if (!sender.canUseCommand(2, "itweaks")) {
            sender.sendMessage(new TextComponentString("\u00A7cYou do not have permission to use this command."));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(new TextComponentString("\u00A7cUsage: /itweaks restore <cursed|decay> <player> [list|latest|timestamp]"));
            return;
        }

        String type = args[1].toLowerCase();
        
        String nbtKey;
        if (type.equals("cursed")) {
            nbtKey = "CursedHistory";
        } else if (type.equals("decay")) {
            nbtKey = "GraveDecayHistory";
        } else {
            sender.sendMessage(new TextComponentString("\u00A7cInvalid restore type! Use: cursed or decay."));
            return;
        }

        EntityPlayer target = getPlayer(server, sender, args[2]);
        String action = args.length > 3 ? args[3] : "latest";

        NBTTagCompound playerData = target.getEntityData();
        NBTTagCompound persistentData = playerData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);

        if (!persistentData.hasKey(nbtKey, 9)) {
            sender.sendMessage(new TextComponentString("\u00A7cNo backup history found for " + target.getName() + " !"));
            return;
        }

        NBTTagList backupHistory = persistentData.getTagList(nbtKey, 10);
        if (backupHistory.tagCount() == 0) {
            sender.sendMessage(new TextComponentString("\u00A7cBackup history is empty for " + target.getName() + " !"));
            return;
        }

        if ("list".equalsIgnoreCase(action)) {
            sender.sendMessage(new TextComponentString("\u00A7eAvailable backups for " + target.getName() + " (" + type + "):"));
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
        persistentData.setTag(nbtKey, backupHistory);

        sender.sendMessage(new TextComponentString("\u00A7aSuccessfully restored " + restored + " items from " + backupToRestore.getString("Time") + " !"));
        
        // Also send a message to the target player if they're not the sender
        if (sender != target) {
            target.sendMessage(new TextComponentString("\u00A7aYour " + type + " items have been restored by an Admin!"));
        }
    }
}
