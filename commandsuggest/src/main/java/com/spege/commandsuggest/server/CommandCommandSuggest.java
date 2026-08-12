package com.spege.commandsuggest.server;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * {@code /commandsuggest reload} i {@code /commandsuggest refresh}. Bez aliasow — w paczce z 268
 * modami krotki alias jak {@code /cs} to zaproszenie do kolizji, a ta komenda uzywana jest raz na
 * ruski rok. {@code refresh} istnieje, bo Forge nie ma eventu na zmiane poziomu uprawnien, wiec po
 * {@code /op} drzewo gracza jest nieaktualne az do relogu.
 */
public class CommandCommandSuggest extends CommandBase {

    @Override
    public String getName() {
        return "commandsuggest";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "commandsuggest.command.usage";
    }

    /** Zero — bramkowanie jest per podkomenda, w {@link #execute}. */
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "reload", "refresh");
        }
        return Collections.emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length != 1) {
            throw new WrongUsageException("commandsuggest.command.usage");
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.canUseCommand(2, getName())) {
                throw new CommandException("commands.generic.permission");
            }
            int n = DescriptorLoader.reload();
            TreeDispatcher.sendToAll(server);
            sender.sendMessage(new TextComponentTranslation("commandsuggest.command.reloaded",
                    Integer.valueOf(n)));
            return;
        }
        if ("refresh".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof EntityPlayerMP)) {
                throw new CommandException("commandsuggest.command.playeronly");
            }
            TreeDispatcher.sendTo((EntityPlayerMP) sender);
            sender.sendMessage(new TextComponentTranslation("commandsuggest.command.refreshed"));
            return;
        }
        throw new WrongUsageException("commandsuggest.command.usage");
    }
}
