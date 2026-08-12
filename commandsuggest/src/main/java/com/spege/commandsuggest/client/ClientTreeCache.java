package com.spege.commandsuggest.client;

import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.config.CommandSuggestConfig;
import com.spege.commandsuggest.core.CommandIndex;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Drzewo komend na czas jednej sesji plus okno handshake'u.
 *
 * <p>Nie ma tu przelacznika trybow, bo nie ma dwoch trybow: brak drzewa to po prostu pusty
 * {@link CommandIndex}, a {@code SuggestionEngine} sam wtedy oddaje sprawe waniliowemu
 * tab-complete. Jedyne, co robi licznik tickow, to jedna linia INFO w logu — zeby dalo sie
 * odpowiedziec na pytanie "czemu tu nie ma typow".
 */
@SideOnly(Side.CLIENT)
public final class ClientTreeCache {

    private static CommandIndex index = CommandIndex.EMPTY;
    private static boolean received;
    private static boolean connected;
    private static int ticksSinceJoin;
    private static boolean modeLogged;

    private ClientTreeCache() {
    }

    public static CommandIndex get() {
        return index;
    }

    public static boolean hasTree() {
        return received;
    }

    /** Wolane z watku glownego przez {@code TreeApplier}. */
    public static void accept(CommandIndex fresh) {
        index = fresh != null ? fresh : CommandIndex.EMPTY;
        received = true;
        if (CommandSuggestConfig.logHandshakeMode && !modeLogged) {
            modeLogged = true;
            CommandSuggest.LOGGER.info(
                    "Serwer przyslal drzewo komend ({} pozycji) - pelne podpowiedzi z typami.",
                    Integer.valueOf(index.getCommands().size()));
        }
    }

    /**
     * Ten sam obiekt jest handlerem — rejestruje go {@code ClientProxy}, rejestrowany dokladnie
     * raz na sesje gry z {@code ClientProxy.preInit}.
     *
     * <p>🚨 Adnotacje NIE dziedzicza sie do klas zagniezdzonych — kompilator generuje
     * {@code ClientTreeCache$Events.class} jako osobny plik klasy, ktory sam z siebie NIE niesie
     * {@code @SideOnly} zewnetrznej klasy. Dlatego ma je tutaj jawnie, mimo ze bezpieczenstwo
     * strony i tak zapewnia miejsce jedynej instancji: {@code new ClientTreeCache.Events()} stoi
     * wprost w wywolaniu {@code EVENT_BUS.register(Object)} (bez lokalnej zmiennej ani pola
     * typowanego na {@code Events} — dokladnie bezpieczny wzorzec z {@code CLAUDE.md}) i siedzi w
     * {@code ClientProxy}, ktora sama nigdy nie laduje sie na dedykowanym serwerze. Jawna
     * adnotacja to obrona w glab na wypadek, gdyby ktos kiedys przeniosl rejestracje gdzies indziej.
     */
    @SideOnly(Side.CLIENT)
    public static final class Events {

        @SubscribeEvent
        public void onConnect(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            index = CommandIndex.EMPTY;
            received = false;
            connected = true;
            ticksSinceJoin = 0;
            modeLogged = false;
            // rejestry moga byc przemapowane pod inny modset niz na poprzednim serwerze - patrz
            // javadoc LocalValueSource.invalidate()
            LocalValueSource.invalidate();
        }

        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            index = CommandIndex.EMPTY;
            received = false;
            connected = false;
            ticksSinceJoin = 0;
            modeLogged = false;
            LocalValueSource.invalidate();
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !connected || received || modeLogged) {
                return;
            }
            ticksSinceJoin++;
            if (ticksSinceJoin > CommandSuggestConfig.handshakeTimeoutTicks) {
                modeLogged = true;
                if (CommandSuggestConfig.logHandshakeMode) {
                    CommandSuggest.LOGGER.info(
                            "Brak drzewa komend po {} tickach - serwer nie ma tego moda. "
                            + "Podpowiedzi leca z waniliowego tab-complete, bez typow.",
                            Integer.valueOf(CommandSuggestConfig.handshakeTimeoutTicks));
                }
            }
        }
    }
}
