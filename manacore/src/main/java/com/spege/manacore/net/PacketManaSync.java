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
     * The handler class MUST NOT carry @SideOnly: registerMessage calls newInstance() on BOTH
     * sides, and the trailing Side argument only picks which side PROCESSES the message. The
     * client-side work lives in a separate class, reached through a single invokestatic.
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
