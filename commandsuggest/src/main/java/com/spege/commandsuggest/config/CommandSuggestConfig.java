package com.spege.commandsuggest.config;

import com.spege.commandsuggest.CommandSuggest;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Siedem pol w kategorii {@code general} pliku {@code config/commandsuggest.cfg}.
 *
 * <p>🚨 {@code category = "general"}, a NIE {@code ""}. Pusta kategoria znaczy "kazde pole tej
 * klasy jest samo w sobie kategoria" i jest twardym crashem dla pol prostych:
 * {@code ConfigManager.sync} rzuca wtedy
 * {@code "An empty category may not contain anything but objects representing categories!"}
 * juz przy konstruowaniu moda. Siostrzane mody w tym repo maja {@code ""}, bo maja pola-kategorie —
 * tutaj tego nie kopiuj (tak samo jak w {@code enchanteraser}).
 *
 * <p>Zadne z tych pol nie bramkuje rejestracji handlerow — {@code Enabled} jest wczesnym powrotem
 * w srodku handlera — wiec nic tu nie ma {@code @Config.RequiresMcRestart}: wszystko czytane na zywo.
 */
@Config(modid = CommandSuggest.MODID, name = CommandSuggest.MODID, category = "general")
public class CommandSuggestConfig {

    @Config.Name("Enabled")
    @Config.Comment({
            "Master switch. Off = the chat behaves exactly like vanilla, including the",
            "comma-separated completion list printed into chat on TAB. Read live." })
    public static boolean enabled = true;

    @Config.Name("Max Visible Rows")
    @Config.Comment("How many suggestions the popup shows at once; the rest scroll. Read live.")
    @Config.RangeInt(min = 1, max = 20)
    public static int maxVisibleRows = 10;

    @Config.Name("Colour Arguments By Type")
    @Config.Comment({
            "Colour each suggestion by the argument type it belongs to. Off = everything is",
            "plain grey. Read live." })
    public static boolean colourArgumentsByType = true;

    @Config.Name("Show Usage Line")
    @Config.Comment("Show the usage line under the suggestion list. Read live.")
    public static boolean showUsageLine = true;

    @Config.Name("Recompute Debounce Ms")
    @Config.Comment({
            "Wait this long after the last keystroke before recomputing suggestions.",
            "0 recomputes on every change. Read live." })
    @Config.RangeInt(min = 0, max = 1000)
    public static int recomputeDebounceMs = 50;

    @Config.Name("Handshake Timeout Ticks")
    @Config.Comment({
            "How long after joining to wait for the server's command tree before deciding the",
            "server does not have this mod and falling back to vanilla tab-completion.",
            "20 ticks = 1 second. Read live." })
    @Config.RangeInt(min = 20, max = 600)
    public static int handshakeTimeoutTicks = 100;

    @Config.Name("Log Handshake Mode")
    @Config.Comment({
            "Log one INFO line per session saying which mode started and why.",
            "Leave on - it is the cheapest way to answer 'why are there no types here'." })
    public static boolean logHandshakeMode = true;

    @Mod.EventBusSubscriber(modid = CommandSuggest.MODID)
    public static class EventHandler {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (CommandSuggest.MODID.equals(event.getModID())) {
                ConfigManager.sync(CommandSuggest.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
