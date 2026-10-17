package com.spege.srpwizcore.devspeed;

import io.netty.buffer.ByteBuf;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Server -> client: the tick step the server loop is running at, in milliseconds (50 = normal).
 *
 * <p>The handler stays free of client classes on purpose — {@code registerMessage} instantiates
 * it on both sides, and all it has to do is set one primitive in {@link DevSpeedState}, which
 * the {@code Timer} mixin reads on the next frame. Safe from the network thread: the field is
 * volatile and nothing else depends on it.
 */
public class PacketDevSpeed implements IMessage {

    private long msPerTick;

    public PacketDevSpeed() {
    }

    public PacketDevSpeed(long msPerTick) {
        this.msPerTick = msPerTick;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.msPerTick = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt((int) this.msPerTick);
    }

    public static class Handler implements IMessageHandler<PacketDevSpeed, IMessage> {
        @Override
        public IMessage onMessage(PacketDevSpeed message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            DevSpeedState.applyClient(message.msPerTick);
            return null;
        }
    }
}
