package com.spege.srpwizcore.whtcompat;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

/**
 * {@code /whtdiag [dump|reset]} — prints or clears the WorseHurtTimer diagnostic counters.
 *
 * <p>Complements the interval dump in {@link WhtDiagHandler}: the interval is what you want while
 * something is actually happening, the command is what you want for a sterile before/after test
 * where an arbitrary dump boundary would split the two halves.
 */
public class CommandWhtDiag extends CommandBase {

    @Override
    public String getName() {
        return "whtdiag";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/whtdiag [dump|reset]";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("whtdiag");
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        String sub = args.length == 0 ? "dump" : args[0].toLowerCase();
        if ("reset".equals(sub)) {
            WhtDiag.reset();
            sender.sendMessage(new TextComponentString("whtdiag: counters cleared"));
            return;
        }
        if (!WhtDiag.ENABLED) {
            sender.sendMessage(new TextComponentString(
                    "whtdiag is OFF - enable whtCompat.diagEnabled in srpwizcore.cfg"));
        }
        for (String line : WhtDiag.render()) {
            sender.sendMessage(new TextComponentString(line));
        }
        WhtDiag.dumpToLog();
    }
}
