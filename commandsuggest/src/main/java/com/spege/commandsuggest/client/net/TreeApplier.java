package com.spege.commandsuggest.client.net;

import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.client.ClientTreeCache;
import com.spege.commandsuggest.core.CommandIndex;
import com.spege.commandsuggest.core.TreeCodec;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Klient-only odbior pakietu. Dekodowanie leci na watku sieciowym (jest czyste i moze byc drogie
 * przy duzym drzewie), a do cache'u wchodzimy juz przez {@code addScheduledTask}, bo czyta go
 * watek renderujacy.
 *
 * <p>🚨 {@code TreeCodec.decode} rzuca {@code IllegalStateException} na uszkodzonym wejsciu, a
 * wolamy go tu na watku sieciowym (Netty), NIE glownym — {@code Handler.onMessage} nie skacze do
 * glownego watku przed wywolaniem {@code apply}. Gdyby ten wyjatek uciekl std stad, poszedlby
 * przez {@code MessageToMessageDecoder.decode} w gore Netty pipeline'u do {@code exceptionCaught}
 * na {@code NetworkManager}, ktory na to reaguje rozlaczeniem klienta z serwerem (ekran
 * rozlaczenia z tresc wyjatku) — dla czysto kosmetycznej funkcji podpowiedzi to reakcja o wiele
 * gorsza niz po prostu nie dostac drzewa. Dlatego lapiemy tutaj: logujemy i zostawiamy cache
 * nietkniety, zeby zwykly mechanizm timeoutu w {@link ClientTreeCache} zrobil swoje i gracz
 * dostal waniliowe tab-complete zamiast rozlaczenia.
 */
@SideOnly(Side.CLIENT)
public final class TreeApplier {

    private TreeApplier() {
    }

    public static void apply(byte[] payload) {
        final CommandIndex index;
        try {
            index = TreeCodec.decode(payload);
        } catch (IllegalStateException e) {
            CommandSuggest.LOGGER.error(
                    "Nie udalo sie odkodowac drzewa komend od serwera - zostaje waniliowe "
                    + "tab-complete zamiast crashu albo rozlaczenia.", e);
            return;
        }
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                ClientTreeCache.accept(index);
            }
        });
    }
}
