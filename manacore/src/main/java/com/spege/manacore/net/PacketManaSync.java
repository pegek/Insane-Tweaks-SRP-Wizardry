package com.spege.manacore.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

public class PacketManaSync implements IMessage {

    private double current;

    public PacketManaSync() {
    }

    public PacketManaSync(double current) {
        this.current = current;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.current = buf.readDouble();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(this.current);
    }

    /**
     * Klasa handlera NIE MOZE nosic @SideOnly: registerMessage wola newInstance() po OBU stronach,
     * a trailing Side wybiera tylko, ktora strona PRZETWARZA wiadomosc. Praca klienta siedzi
     * w osobnej klasie wolanej jednym invokestatic.
     */
    public static class Handler implements IMessageHandler<PacketManaSync, IMessage> {

        @Override
        public IMessage onMessage(PacketManaSync message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            ManaSyncClient.apply(message.current);
            return null;
        }
    }
}
