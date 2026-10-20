package com.spege.srpwizcore.effectpanel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.potion.Potion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * {@code /effectdesc missing}: the authoring loop for effect descriptions. Appends a blank line for
 * every effect in the pack that nothing describes yet to {@code config/srpwizcore/effect_descriptions.lang},
 * and reports coverage by source. Fill in the blanks, press F3+T, hover the effect.
 *
 * <p>A client command (registered through {@code ClientCommandHandler}), so it works on any server,
 * with or without srpwizcore there. It never rewrites or reorders existing lines, and an effect that
 * already has a line - even an empty one - is not appended again, so running it twice is harmless.
 */
@SideOnly(Side.CLIENT)
public final class CommandEffectDesc extends CommandBase {

    private static final String KEY = "srpwizcore.effectpanel.command.";

    @Override
    public String getName() {
        return "effectdesc";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return KEY + "usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "missing") : Collections.<String>emptyList();
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1 || !"missing".equals(args[0])) {
            throw new WrongUsageException(getUsage(sender));
        }

        List<Potion> potions = new ArrayList<>(ForgeRegistries.POTIONS.getValuesCollection());
        Collections.sort(potions, new Comparator<Potion>() {
            @Override
            public int compare(Potion a, Potion b) {
                return name(a).compareTo(name(b));
            }
        });

        Map<DescriptionChain.Rule, Integer> counts = new EnumMap<>(DescriptionChain.Rule.class);
        for (DescriptionChain.Rule rule : DescriptionChain.Rule.values()) {
            counts.put(rule, 0);
        }
        List<DescriptionFile.Missing> missing = new ArrayList<>();
        for (Potion potion : potions) {
            DescriptionChain.Rule rule = EffectDescriptions.describe(potion, 0).rule;
            counts.put(rule, counts.get(rule) + 1);
            if (rule == DescriptionChain.Rule.NONE && potion.getRegistryName() != null) {
                missing.add(new DescriptionFile.Missing(name(potion), I18n.format(potion.getName()),
                        modName(potion.getRegistryName())));
            }
        }

        List<String> toAppend = DescriptionFile.linesToAppend(EffectDescriptions.current().presentKeys, missing,
                "/effectdesc missing, " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
        File file = EffectDescriptions.file();
        try {
            append(file, toAppend);
        } catch (IOException e) {
            sender.sendMessage(new TextComponentTranslation(KEY + "failed", file.getPath(), e.toString()));
            return;
        }
        EffectDescriptions.reload();

        int appended = 0;
        for (String line : toAppend) {
            if (!line.isEmpty() && !line.startsWith("#")) {
                appended++;
            }
        }
        sender.sendMessage(new TextComponentTranslation(KEY + "appended", appended, file.getPath()));
        sender.sendMessage(new TextComponentTranslation(KEY + "coverage",
                potions.size() - counts.get(DescriptionChain.Rule.NONE), potions.size(),
                counts.get(DescriptionChain.Rule.FILE), counts.get(DescriptionChain.Rule.JEE),
                counts.get(DescriptionChain.Rule.POTION_DESCRIPTIONS), counts.get(DescriptionChain.Rule.SRP_BESTIARY)));
    }

    /** Appends whole lines, starting on a fresh line even if the file's last line has no line break. */
    private static void append(File file, List<String> lines) throws IOException {
        if (lines.isEmpty()) {
            return;
        }
        String sep = System.lineSeparator();
        StringBuilder out = new StringBuilder();
        if (!file.exists()) {
            File dir = file.getParentFile();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("could not create " + dir);
            }
            for (String line : DescriptionFile.header()) {
                out.append(line).append(sep);
            }
        } else if (file.length() > 0) {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes[bytes.length - 1] != '\n') {
                out.append(sep);
            }
        }
        for (String line : lines) {
            out.append(line).append(sep);
        }
        Files.write(file.toPath(), out.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String name(Potion potion) {
        ResourceLocation name = potion.getRegistryName();
        return name == null ? "" : name.toString();
    }

    private static String modName(ResourceLocation registryName) {
        String namespace = registryName.getResourceDomain();
        if ("minecraft".equals(namespace)) {
            return "Minecraft";
        }
        ModContainer mod = Loader.instance().getIndexedModList().get(namespace);
        return mod == null ? namespace : mod.getName();
    }
}
