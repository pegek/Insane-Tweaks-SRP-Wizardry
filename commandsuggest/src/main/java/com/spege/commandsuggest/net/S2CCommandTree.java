package com.spege.commandsuggest.net;

import com.spege.commandsuggest.client.net.TreeApplier;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Drzewo komend przefiltrowane pod uprawnienia jednego gracza, zakodowane przez
 * {@code TreeCodec} (wlasna flaga gzipa w srodku, wiec tutaj to zwykla tablica bajtow).
 */
public class S2CCommandTree implements IMessage {

    /**
     * 🚨 Ta sama dyscyplina co przy licznikach w {@code TreeCodec.decode}: dlugosc payloadu to
     * liczba z sieci, wiec NIE trafia prosto w {@code new byte[len]}. Vanilla ogranicza
     * client-bound {@code SPacketCustomPayload} do 1 MiB — to naturalny, hojny sufit: dobrze
     * uformowany pakiet fizycznie nie moze niesc wiecej.
     *
     * <p>Jedyne zrodlo prawdy dla tej granicy — {@code TreeDispatcher.MAX_PAYLOAD_BYTES} tylko
     * odwoluje sie tutaj, zeby nadawca i odbiorca nie mogly rozjechac sie liczbowo.
     */
    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;

    private byte[] payload;

    /** Wymagany przez {@code SimpleNetworkWrapper} — wola go refleksja. */
    public S2CCommandTree() {
    }

    public S2CCommandTree(byte[] payload) {
        this.payload = payload;
    }

    public byte[] getPayload() {
        return this.payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int len = ByteBufUtils.readVarInt(buf, 5);
        if (len < 0 || len > MAX_PAYLOAD_BYTES) {
            throw new IndexOutOfBoundsException(
                    "dlugosc payloadu S2CCommandTree poza zakresem: " + len);
        }
        if (len > buf.readableBytes()) {
            throw new IndexOutOfBoundsException(
                    "dlugosc payloadu S2CCommandTree (" + len + ") wieksza niz dostepne bajty ("
                            + buf.readableBytes() + ")");
        }
        this.payload = new byte[len];
        buf.readBytes(this.payload);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, this.payload.length, 5);
        buf.writeBytes(this.payload);
    }

    /**
     * 🚨 Ta klasa NIE MOZE miec {@code @SideOnly}. {@code registerMessage} wola
     * {@code newInstance()} natychmiast po OBU stronach — koncowy argument {@code Side} wybiera
     * tylko, ktora strona przetwarza wiadomosc, i niczego nie bramkuje. Cala robota kliencka
     * siedzi wiec za jednym {@code invokestatic} do klasy oznaczonej {@code @SideOnly}, ktora
     * weryfikator rozwiaze dopiero przy pierwszym wykonaniu — i tylko na kliencie.
     */
    public static class Handler implements IMessageHandler<S2CCommandTree, IMessage> {

        @Override
        public IMessage onMessage(S2CCommandTree message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            TreeApplier.apply(message.getPayload());
            return null;
        }
    }
}
