package com.spege.manacore.command;

import com.spege.manacore.api.ManaAPI;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * Debug command for inspecting and setting a player's mana. Operator level only; this exists to
 * make the pool observable in game, since none of it is visible to the tests.
 */
public class CommandMana extends CommandBase {

    @Override
    public String getName() {
        return "mana";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "manacore.command.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }

        // The player argument is last so the common case - acting on yourself - stays short.
        EntityPlayer target = args.length >= 3
                ? getPlayer(server, sender, args[2])
                : getCommandSenderAsPlayer(sender);

        if ("get".equals(args[0])) {
            report(sender, target);
            return;
        }

        if (args.length < 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        double amount = parseDouble(args[1]);

        if ("set".equals(args[0])) {
            ManaAPI.setMana(target, amount);
        } else if ("add".equals(args[0])) {
            ManaAPI.add(target, amount);
        } else if ("addprog".equals(args[0])) {
            ManaAPI.addProgression(target, amount);
        } else {
            throw new WrongUsageException(getUsage(sender));
        }

        report(sender, target);
    }

    private void report(ICommandSender sender, EntityPlayer target) {
        sender.sendMessage(new TextComponentString(
                ManaAPI.getMana(target) + " / " + ManaAPI.getMaxMana(target)));
    }
}
