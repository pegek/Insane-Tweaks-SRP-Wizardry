package com.spege.srpwizcore.devspeed;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

/**
 * DEV TOOL — {@code /devspeed [multiplier|reset]} and {@code /devspeed warp <N|off|spawns K>}.
 *
 * <ul>
 * <li>{@code /devspeed} — target TPS, measured TPS over the last 5 s, mean tick time, and a
 * loud line when the server cannot keep up.</li>
 * <li>{@code /devspeed 2} — run the game at x2 (0.5 .. 4; {@code x2} also accepted).
 * {@code /devspeed 1} — tick rate back to normal; {@code /devspeed reset} also turns warp off.</li>
 * <li>{@code /devspeed warp 50} — clock warp: world age and time of day advance x50 while
 * everything else ticks normally (see {@link DevSpeedWarp}); {@code warp off} stops it,
 * {@code warp spawns 2} adds extra spawn passes per tick while it runs.</li>
 * </ul>
 *
 * <p>Op only (level 2). Plain server command, so the pack's agent bridge can drive it from
 * {@code logs/agent_commands.txt}: every reply goes through {@code sender.sendMessage}, which is
 * what the bridge captures, and a change is also broadcast (and logged to the console) because
 * it affects everybody on the server. Registered only when {@code devSpeed} is enabled.
 */
public class CommandDevSpeed extends CommandBase {

    @Override
    public String getName() {
        return "devspeed";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/devspeed [0.5-4 | reset] | /devspeed warp <2-200 | off | spawns 0-4>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            reply(sender, DevSpeed.statusLines(server));
            return;
        }
        if ("warp".equalsIgnoreCase(args[0])) {
            executeWarp(server, sender, args);
            return;
        }
        if (args.length != 1) {
            throw new WrongUsageException(getUsage(sender));
        }

        String arg = args[0];
        if ("reset".equalsIgnoreCase(arg) || "off".equalsIgnoreCase(arg)) {
            // Both modes: "reset" means "back to a normal game".
            DevSpeedWarp.stop(server, "reset by " + sender.getName());
            if (DevSpeedState.serverMsPerTick != DevSpeedState.VANILLA_MS_PER_TICK) {
                DevSpeed.set(server, 1.0D, sender.getName());
            } else {
                reply(sender, "[devspeed] tick rate already normal (x1)");
            }
            return;
        }
        if (!DevSpeedState.serverHookFired) {
            reply(sender, "[devspeed] !! the server tick-loop hook is not active, so the tick "
                    + "rate cannot change (warp still works). See latest.log / cleanmix.log.");
            return;
        }
        if (arg.length() > 1 && (arg.charAt(0) == 'x' || arg.charAt(0) == 'X')) {
            arg = arg.substring(1);
        }
        double multiplier = parseDouble(arg, DevSpeed.MIN_MULTIPLIER, DevSpeed.MAX_MULTIPLIER);
        DevSpeed.set(server, multiplier, sender.getName());
        if (multiplier != 1.0D) {
            reply(sender, "[devspeed] measuring - '/devspeed' shows target vs real TPS "
                    + "(auto report in ~6 s)");
        }
    }

    /** {@code warp}, {@code warp <N>}, {@code warp off}, {@code warp spawns <K>}. */
    private void executeWarp(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length == 1) {
            reply(sender, DevSpeedWarp.statusLines());
            return;
        }
        String arg = args[1];
        if ("spawns".equalsIgnoreCase(arg)) {
            if (args.length != 3) {
                throw new WrongUsageException("/devspeed warp spawns <0-"
                        + DevSpeedWarp.MAX_EXTRA_SPAWN_PASSES + ">");
            }
            int passes = parseInt(args[2], 0, DevSpeedWarp.MAX_EXTRA_SPAWN_PASSES);
            DevSpeedWarp.setExtraSpawnPasses(server, passes, sender.getName());
            return;
        }
        if (args.length != 2) {
            throw new WrongUsageException(getUsage(sender));
        }
        if ("off".equalsIgnoreCase(arg) || "1".equals(arg) || "reset".equalsIgnoreCase(arg)) {
            if (!DevSpeedWarp.isActive()) {
                reply(sender, "[devspeed] warp is already off");
                return;
            }
            DevSpeedWarp.stop(server, "off by " + sender.getName());
            return;
        }
        if (arg.length() > 1 && (arg.charAt(0) == 'x' || arg.charAt(0) == 'X')) {
            arg = arg.substring(1);
        }
        int warp = parseInt(arg, DevSpeedWarp.MIN_WARP, DevSpeedWarp.MAX_WARP);
        DevSpeedWarp.start(server, warp, sender.getName());
        reply(sender, "[devspeed] warp on - '/devspeed' shows the measured game-clock rate "
                + "(auto report in ~6 s)");
    }

    private static void reply(ICommandSender sender, String line) {
        sender.sendMessage(new TextComponentString(line));
    }

    private static void reply(ICommandSender sender, List<String> lines) {
        for (String line : lines) {
            reply(sender, line);
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
            String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "1", "1.5", "2", "2.5", "3", "4",
                    "0.5", "reset", "status", "warp");
        }
        if (args.length == 2 && "warp".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "10", "25", "50", "100", "off",
                    "spawns");
        }
        if (args.length == 3 && "spawns".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "0", "1", "2", "4");
        }
        return Collections.emptyList();
    }
}
