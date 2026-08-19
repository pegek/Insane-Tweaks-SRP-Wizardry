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
            // Kept as an alias for cast progression, matching what this subcommand always did
            // before the progression field split.
            ManaAPI.addCastProgression(target, amount);
        } else if ("addprogitem".equals(args[0])) {
            ManaAPI.addItemProgression(target, amount);
        } else if ("setmax".equals(args[0])) {
            setMaxTo(target, amount);
        } else {
            throw new WrongUsageException(getUsage(sender));
        }

        report(sender, target);
    }

    /**
     * Forces the player's PERSISTENT maximum mana to {@code desiredMax}, by writing the difference
     * into the flat granted bucket ({@code ManaAPI.setGrantedMax}).
     *
     * <p>Deliberately not implemented by touching the progression fields: those are capped by
     * config, so a debug command built on them could not reach an arbitrary value - which is the
     * one thing a debug command is for. Deliberately not implemented by setting the attribute base
     * either, because {@code ManaAttributes.onEntityJoinWorld} rewrites the base from config on
     * every world entry, so the change would silently vanish on the next relog. And deliberately
     * not implemented as a bare attribute modifier, which is what this did until 2026-08-19: that
     * version reset on death, because vanilla builds a new player entity on respawn and copies no
     * attribute modifiers onto it. Routing through the capability is what makes the value stick.
     *
     * <p>Targets the persistent half only - worn gear is NOT counted towards {@code desiredMax},
     * so a bauble granting bonus mana still adds on top of whatever is set here. Otherwise the
     * command would silently bank the gear's contribution into a permanent grant, and taking the
     * item off would leave the player richer than before they put it on.
     *
     * <p>The grant is computed as a delta against the maximum WITHOUT it, so calling this twice
     * sets an absolute value rather than stacking. Values below the natural maximum cannot be
     * reached - the grant floors at zero rather than going negative.
     */
    private void setMaxTo(EntityPlayer target, double desiredMax) {
        // Clear the grant first, so `naturalMax` is the part this command is not responsible for -
        // otherwise repeated calls would compound.
        ManaAPI.setGrantedMax(target, 0.0D);
        double naturalMax = ManaAPI.getPersistentMaxMana(target);
        ManaAPI.setGrantedMax(target, desiredMax - naturalMax);
    }

    /**
     * Reports current/maximum, plus the split into persistent and gear-granted maximum whenever
     * gear is actually contributing. The breakdown is hidden when the bonus is zero, which is the
     * normal case, so the common reading stays a single short line.
     */
    private void report(ICommandSender sender, EntityPlayer target) {
        String line = ManaAPI.getMana(target) + " / " + ManaAPI.getMaxMana(target);
        double bonus = ManaAPI.getBonusMana(target);
        if (bonus != 0.0D) {
            line += " (max: " + ManaAPI.getPersistentMaxMana(target) + " + " + bonus + " from gear)";
        }
        sender.sendMessage(new TextComponentString(line));
    }
}
