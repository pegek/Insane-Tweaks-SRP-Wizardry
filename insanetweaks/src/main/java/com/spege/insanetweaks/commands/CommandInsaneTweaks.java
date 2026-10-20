package com.spege.insanetweaks.commands;

import com.spege.insanetweaks.util.EnchantGrantMarker;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
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
        return "/itweaks <grantbook | propertybook | codexpool | sanctum>";
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
            case "grantbook":
                handleGrantBook(server, sender, args);
                break;
            case "propertybook":
                handlePropertyBook(server, sender, args);
                break;
            case "codexpool":
                handleCodexPool(sender);
                break;
            case "sanctum":
                handleSanctum(server, sender, args);
                break;
            case "help":
            default:
                sendHelp(sender);
                break;
        }
    }

    private void sendHelp(ICommandSender sender) {
        sender.sendMessage(new TextComponentString("\u00A75--- Insane Tweaks Commands ---"));
        if (sender.canUseCommand(2, "itweaks")) { // If they have OP permissions
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks grantbook <enchantment> [level] [player]\u00A77 - Give a quest-granted enchanted book"));
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks propertybook <property> [player]\u00A77 - Give a Property Book (anvil onto a tool)"));
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks codexpool\u00A77 - Explain which enchantments Sentient Codex would raise on the held item"));
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks sanctum place\u00A77 - Build a Sunken Arcane Sanctum here"));
            sender.sendMessage(new TextComponentString("\u00A7e/itweaks sanctum nearest\u00A77 - Locate the nearest sanctum site"));
        }
    }

    /**
     * Reports, for every enchantment on the held item, whether Sentient Codex will raise it and how
     * far.
     *
     * <p>The pool rules are mostly derived (single-level enchantments skipped, headroom by rarity,
     * curses last) rather than listed, which makes them impossible to check by reading the config.
     * This is how you check them: hold the item and ask.
     */
    private void handleCodexPool(ICommandSender sender) throws CommandException {
        if (!sender.canUseCommand(2, "itweaks")) {
            sender.sendMessage(new TextComponentString("\u00A7cYou do not have permission to use this command."));
            return;
        }
        EntityPlayer player = getCommandSenderAsPlayer(sender);
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.isEmpty()) {
            sender.sendMessage(new TextComponentString("\u00A7cHold the item you want to inspect."));
            return;
        }

        NBTTagList ench = stack.getEnchantmentTagList();
        if (ench.tagCount() == 0) {
            sender.sendMessage(new TextComponentString("\u00A77That item carries no enchantments."));
            return;
        }

        sender.sendMessage(new TextComponentString("\u00A75--- Sentient Codex pool: \u00A7e"
                + stack.getDisplayName() + " \u00A75---"));
        for (int i = 0; i < ench.tagCount(); i++) {
            NBTTagCompound en = ench.getCompoundTagAt(i);
            // getInteger, not getShort: JustEnoughIDs widens enchantment ids past 32767.
            int id = en.getInteger("id");
            int level = en.getShort("lvl");
            Enchantment enchantment = Enchantment.getEnchantmentByID(id);
            if (enchantment == null) {
                sender.sendMessage(new TextComponentString("\u00A78  unknown enchantment id " + id));
                continue;
            }

            ResourceLocation name = enchantment.getRegistryName();
            String label = name == null ? enchantment.getName() : name.toString();
            com.spege.insanetweaks.enchant.SentientCodexPool.Verdict verdict =
                    com.spege.insanetweaks.enchant.SentientCodexPool.verdict(enchantment, stack, level);

            if (verdict == com.spege.insanetweaks.enchant.SentientCodexPool.Verdict.BOOSTED) {
                sender.sendMessage(new TextComponentString(TextFormatting.GREEN + "  " + label
                        + TextFormatting.GRAY + " " + level + " \u2192 cap "
                        + com.spege.insanetweaks.enchant.SentientCodexPool.capFor(enchantment)
                        + TextFormatting.DARK_GRAY + " (" + rarityLabel(enchantment) + ")"));
            } else {
                sender.sendMessage(new TextComponentString(TextFormatting.RED + "  " + label
                        + TextFormatting.GRAY + " " + level + " \u2014 " + verdict.getReason()));
            }
        }
    }

    private static String rarityLabel(Enchantment enchantment) {
        return enchantment.isCurse() ? "curse" : enchantment.getRarity().toString().toLowerCase();
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


    private void handleSanctum(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!sender.canUseCommand(2, getName())) {
            throw new CommandException("commands.generic.permission");
        }
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("/itweaks sanctum <place | nearest>"));
            return;
        }
        net.minecraft.world.World world = sender.getEntityWorld();
        net.minecraft.util.math.BlockPos at = sender.getPosition();
        if ("place".equalsIgnoreCase(args[1])) {
            com.spege.insanetweaks.worldgen.sanctum.SanctumLayout layout =
                    com.spege.insanetweaks.worldgen.sanctum.SanctumLayout.generate(world.rand.nextLong(), at.getX(),
                            at.getZ(), com.spege.insanetweaks.config.ModConfig.arcaneSanctum.floorYMin,
                            com.spege.insanetweaks.config.ModConfig.arcaneSanctum.floorYMax);
            com.spege.insanetweaks.worldgen.sanctum.SanctumPlacement.register(world, layout.seed, layout);
            com.spege.insanetweaks.worldgen.sanctum.SanctumBuilder.build(world, layout,
                    new com.spege.insanetweaks.worldgen.sanctum.SanctumBox(layout.bounds.minX, 0, layout.bounds.minZ,
                            layout.bounds.maxX, 255, layout.bounds.maxZ), layout.seed);
            sender.sendMessage(new TextComponentString("Sanctum built: hall level Y " + layout.floorY + ", ritual level Y "
                    + layout.floorY2 + ", shaft at " + layout.shaftX + " " + layout.shaftZ));
            return;
        }
        if ("nearest".equalsIgnoreCase(args[1])) {
            int size = com.spege.insanetweaks.config.ModConfig.arcaneSanctum.regionSize;
            int prx = com.spege.insanetweaks.worldgen.sanctum.SanctumRegions.regionOf(at.getX() >> 4, size);
            int prz = com.spege.insanetweaks.worldgen.sanctum.SanctumRegions.regionOf(at.getZ() >> 4, size);
            double best = Double.MAX_VALUE;
            com.spege.insanetweaks.worldgen.sanctum.SanctumLayout found = null;
            for (int rx = prx - 16; rx <= prx + 16; rx++) {
                for (int rz = prz - 16; rz <= prz + 16; rz++) {
                    int[] s = com.spege.insanetweaks.worldgen.sanctum.SanctumPlacement.candidate(world, rx, rz);
                    if (s == null) {
                        continue;
                    }
                    com.spege.insanetweaks.worldgen.sanctum.SanctumLayout l =
                            com.spege.insanetweaks.worldgen.sanctum.SanctumPlacement.layout(world, s[0], s[1]);
                    if (!com.spege.insanetweaks.worldgen.sanctum.SanctumPlacement.biomeAllowed(world, l.originX,
                            l.originZ)) {
                        continue;
                    }
                    double d = at.distanceSq(l.shaftX, at.getY(), l.shaftZ);
                    if (d < best) {
                        best = d;
                        found = l;
                    }
                }
            }
            if (found == null || !com.spege.insanetweaks.worldgen.sanctum.SanctumPlacement.dimensionAllowed(world)) {
                sender.sendMessage(new TextComponentString("No sanctum candidate within 16 regions in this dimension."));
            } else {
                sender.sendMessage(new TextComponentString("Nearest sanctum shaft: " + found.shaftX + " ~ " + found.shaftZ
                        + " (floor Y " + found.floorY + ", " + (int) Math.sqrt(best) + " blocks). Generated only if its "
                        + "chunks were new when first reached."));
            }
            return;
        }
        sender.sendMessage(new TextComponentString("/itweaks sanctum <place | nearest>"));
    }

    @Override
    @Nonnull
    public java.util.List<String> getTabCompletions(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
            @Nonnull String[] args, @javax.annotation.Nullable net.minecraft.util.math.BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "grantbook", "propertybook", "codexpool", "sanctum", "help");
        }
        if (args.length == 2 && "sanctum".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "place", "nearest");
        }
        return java.util.Collections.<String>emptyList();
    }
}
