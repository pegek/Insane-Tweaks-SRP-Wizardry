package com.spege.insanetweaks.commands;

import javax.annotation.Nonnull;

import com.spege.insanetweaks.util.ArcaneAdaptedFruitHelper;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

public class CommandClaimArcaneFruit extends CommandBase {

    @Override
    @Nonnull
    public String getName() {
        return "claimarcanefruit";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/claimarcanefruit";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] args)
            throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);

        if (!ArcaneAdaptedFruitHelper.hasPendingFruit(player)) {
            player.sendMessage(new TextComponentString(
                    TextFormatting.GRAY + "You have no Arcane Adapted Fruit waiting to be claimed."));
            return;
        }

        if (!ArcaneAdaptedFruitHelper.tryGiveFruit(player)) {
            player.sendMessage(new TextComponentString(
                    TextFormatting.RED + "You still need at least one free inventory slot."));
        }
    }
}
