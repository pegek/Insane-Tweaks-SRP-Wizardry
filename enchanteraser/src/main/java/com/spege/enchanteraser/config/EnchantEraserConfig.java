package com.spege.enchanteraser.config;

import com.spege.enchanteraser.EnchantEraser;
import com.spege.enchanteraser.util.EraserState;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * The whole mod's configuration: one list plus three cosmetic/diagnostic switches, all in the
 * {@code general} category of {@code config/enchanteraser.cfg}.
 *
 * <p>🚨 The sibling mods in this repo all set {@code category = ""}, and that must NOT be copied here.
 * An empty category means "every field of this class is itself a category object": verified in
 * {@code ConfigManager.sync}, any field that {@code FieldWrapper.hasWrapperFor} accepts — primitives,
 * enums, maps and <b>arrays</b> — throws
 * {@code "An empty category may not contain anything but objects representing categories!"} at mod
 * construction, which is a hard crash during loading. This config has no category classes at all, so
 * it uses the default {@code general} category, stated explicitly so nobody "fixes" it back.
 *
 * <p>That is also the closest thing to a drop-in migration from {@code disench}, whose list lives in
 * {@code general { S:"Enchantments to Remove" < ... > }} — same structure, different key name.
 *
 * <p>None of these flags gate mixin <em>application</em> — neither mixin config declares an
 * {@code IMixinConfigPlugin}, so every mixin applies unconditionally and the flags are early returns
 * inside the handlers. That is why nothing here carries {@code @Config.RequiresMcRestart}: the values
 * are read live.
 */
@Config(modid = EnchantEraser.MODID, name = EnchantEraser.MODID, category = "general")
public class EnchantEraserConfig {

    @Config.Name("Disabled Enchantments")
    @Config.Comment({
            "Registry names of enchantments that must stop being obtainable, one per line.",
            "The enchantment STAYS in the registry, so existing gear keeps working and nothing crashes;",
            "it simply stops appearing at the enchanting table, in enchant_with_levels and",
            "enchant_randomly loot, in fishing treasure, in librarian trades, on the anvil and in",
            "Infernal Mobs elite drops.",
            "",
            "Entries that do not exist in the registry are ignored silently - this list is expected to",
            "accumulate leftovers from removed mods. The startup log reports how many of them matched,",
            "which is the only way to catch a misspelled registry name.",
            "",
            "Read live. Example: mujmajnkraftsbettersurvival:penetration" })
    public static String[] disabledEnchantments = new String[0];

    @Config.Name("Log Blocks")
    @Config.Comment({
            "Log one INFO line the first time each enchantment is blocked at each source.",
            "Diagnostic only - leave off in normal play. Read live." })
    public static boolean logBlocks = false;

    @Config.Name("Hide Erased From JEI")
    @Config.Comment({
            "Remove the enchanted books of erased enchantments from the JEI/HEI item list and from the",
            "creative tabs. Both come from the same vanilla method, so this is one switch, not two.",
            "",
            "Read live, but JEI builds its ingredient list once at startup - after editing the list",
            "above in game, the books already in JEI stay until a /jei reload or a restart. The creative",
            "tabs rebuild on their own.",
            "",
            "Turn this off if you want the books to stay browsable, e.g. for building quest rewards." })
    public static boolean hideErasedFromJei = true;

    @Config.Name("Mark Erased In Tooltip")
    @Config.Comment({
            "Append a marker to the tooltip line of any enchantment from the list above that is still",
            "sitting in an item's NBT, so a player can tell at a glance that it no longer does anything",
            "and can no longer be obtained. Client-side only. Read live." })
    public static boolean markErasedInTooltip = true;

    @Config.Name("Erased Tooltip Suffix")
    @Config.Comment({
            "Text appended to an erased enchantment's tooltip line. Leave empty to use the translated",
            "default (lang key enchanteraser.tooltip.erased), which is yellow italic.",
            "",
            "Plain text entered here is styled yellow italic for you, so writing just  erased  is enough.",
            "To pick your own styling, include Minecraft formatting codes with the section sign and the",
            "text is then used exactly as written, e.g. §8§o(erased) for dark grey italic. Put the colour",
            "code BEFORE the style code - a colour resets formatting. Read live." })
    public static String erasedTooltipSuffix = "";

    @Mod.EventBusSubscriber(modid = EnchantEraser.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(final ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(EnchantEraser.MODID)) {
                ConfigManager.sync(EnchantEraser.MODID, Config.Type.INSTANCE);
                // The parsed ResourceLocation set is derived state; rebuild it or the edit does nothing.
                EraserState.rebuild(disabledEnchantments);
            }
        }
    }
}
