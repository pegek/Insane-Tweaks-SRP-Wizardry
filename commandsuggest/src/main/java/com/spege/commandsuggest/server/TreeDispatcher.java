package com.spege.commandsuggest.server;

import com.spege.commandsuggest.CommandSuggest;
import com.spege.commandsuggest.core.CommandIndex;
import com.spege.commandsuggest.core.TreeCodec;
import com.spege.commandsuggest.net.PacketHandler;
import com.spege.commandsuggest.net.S2CCommandTree;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/** Buduje drzewo pod jednego gracza i wysyla mu je. Jedno miejsce, trzy wolajace. */
public final class TreeDispatcher {

    /**
     * 🚨 Musi zostac rowne prywatnej stalej {@code S2CCommandTree.MAX_PAYLOAD_BYTES} (wanilijny
     * sufit {@code SPacketCustomPayload} dla client-bound, 1 MiB). Sprawdzenie po stronie
     * odbiorcy w {@code S2CCommandTree.fromBytes} istnieje po to, zeby uszkodzony/zlosliwy
     * strumien z sieci nie trafil w {@code new byte[len]} z absurdalna dlugoscia — ale gdyby TU
     * nic nie sprawdzalo rozmiaru, za duze drzewo poszloby normalnie przez siec i dopiero klient
     * by je odrzucil, bez ani jednej linii w logu serwera tlumaczacej, czemu gracz nie ma
     * podpowiedzi. Ten check jest wiec diagnostyczny, nie bezpieczenstwa — zapobiega cichej
     * porazce, nie atakowi.
     */
    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;

    private TreeDispatcher() {
    }

    public static void sendTo(EntityPlayerMP player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        try {
            CommandIndex index = TreeBuilder.build(server, player);
            byte[] payload = TreeCodec.encode(index);
            if (payload.length > MAX_PAYLOAD_BYTES) {
                CommandSuggest.LOGGER.warn(
                        "Drzewo komend dla {} ma {} bajtow, wiecej niz limit {} - nie wysylam, "
                        + "gracz dostanie waniliowe tab-complete.",
                        player.getName(), Integer.valueOf(payload.length),
                        Integer.valueOf(MAX_PAYLOAD_BYTES));
                return;
            }
            PacketHandler.CHANNEL.sendTo(new S2CCommandTree(payload), player);
            CommandSuggest.LOGGER.info("Wyslano drzewo komend do {}: {} komend, {} bajtow.",
                    player.getName(), Integer.valueOf(index.getCommands().size()),
                    Integer.valueOf(payload.length));
        } catch (RuntimeException e) {
            // gracz zostaje w trybie zdegradowanym; to nie powod, zeby wywalac serwer
            CommandSuggest.LOGGER.error("Nie udalo sie wyslac drzewa do " + player.getName(), e);
        }
    }

    public static void sendToAll(MinecraftServer server) {
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            sendTo(p);
        }
    }
}
