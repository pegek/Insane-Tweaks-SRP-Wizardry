package com.spege.commandsuggest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Command Suggest — podpowiedzi komend pod polem czatu, bez ani jednego mixina.
 *
 * <p>Zastepuje brigo, ktory wisial na czterech mixinach i padl 2026-08-10 na regresji CleanMix
 * w sciezce INJECT_PREPARE_LEGACY. Cztery eventy {@code GuiScreenEvent} plus jedna podmiana pola
 * {@code GuiChat.tabCompleter} daja to samo bez ASM — uzasadnienie w specu, sekcja 3.
 *
 * <p>{@code acceptableRemoteVersions = "*"} jest tu wymagane, nie kosmetyczne: mod ma dzialac
 * na serwerze, ktory go nie ma (tryb zdegradowany), wiec nie moze zadac zgodnosci wersji.
 */
@Mod(modid = CommandSuggest.MODID,
        name = CommandSuggest.NAME,
        version = CommandSuggest.VERSION,
        acceptableRemoteVersions = "*")
public class CommandSuggest {

    public static final String MODID = "commandsuggest";
    public static final String NAME = "Command Suggest";
    /** 🚨 Bumpuj RAZEM z 'version' w build.gradle — to jest ta wartosc, ktora widac w liscie modow. */
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "com.spege.commandsuggest.client.ClientProxy",
                serverSide = "com.spege.commandsuggest.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
