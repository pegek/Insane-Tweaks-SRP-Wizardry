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

    /**
     * Own UUID for the debug modifier installed by {@code setmax}, kept separate from the
     * progression modifier so the two never overwrite each other. Like every attribute modifier
     * id, this must stay constant: vanilla serialises modifiers into the player's NBT, so changing
     * it would orphan the old one in existing worlds instead of replacing it.
     */
    private static final java.util.UUID DEBUG_MAX_MODIFIER_ID =
            java.util.UUID.fromString("c1f4a2b8-0d6e-4c53-9f21-7a8b3e5d4c60");
    private static final String DEBUG_MAX_MODIFIER_NAME = "manacore.debug.setmax";

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
     * Forces the player's total maximum mana to {@code target}, using a debug attribute modifier
     * with its own stable UUID.
     *
     * <p>Deliberately not implemented by touching the progression fields: those are capped by
     * config, so a debug command built on them could not reach an arbitrary value - which is the
     * one thing a debug command is for. Deliberately not implemented by setting the attribute base
     * either, because {@code ManaAttributes.onEntityJoinWorld} rewrites the base from config on
     * every world entry, so the change would silently vanish on the next relog.
     *
     * <p>The modifier is computed as a delta against whatever the player's max would be without
     * it, so calling this twice sets an absolute value rather than stacking. Passing a value equal
     * to the natural maximum removes the modifier entirely.
     */
    private void setMaxTo(EntityPlayer target, double desiredMax) {
        // Drop any previous debug modifier first, so `naturalMax` is the value this command is not
        // responsible for - otherwise repeated calls would compound.
        ManaAPI.addMaxModifier(target, DEBUG_MAX_MODIFIER_ID, DEBUG_MAX_MODIFIER_NAME, 0.0D, 0);
        double naturalMax = ManaAPI.getMaxMana(target);
        double delta = desiredMax - naturalMax;
        if (delta != 0.0D) {
            ManaAPI.addMaxModifier(target, DEBUG_MAX_MODIFIER_ID, DEBUG_MAX_MODIFIER_NAME, delta, 0);
        }
    }

    private void report(ICommandSender sender, EntityPlayer target) {
        sender.sendMessage(new TextComponentString(
                ManaAPI.getMana(target) + " / " + ManaAPI.getMaxMana(target)));
    }
}
