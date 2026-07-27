package com.spege.insanetweaks.network;

import com.spege.insanetweaks.util.BaubleFruitProgress;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Server -> client mirror of a player's Bauble Fruit progress.
 *
 * <p>Carries a bitmask over {@code ModItems.getAllBaubleFruits()} (bit i = that fruit already
 * consumed) and the active cap (0 = uncapped). The client needs both to render the tooltip
 * counter and, more importantly, to reach the same edible/not-edible verdict as the server in
 * {@code onItemRightClick} — otherwise the eating animation would start and then abort.
 *
 * <p>Sent on login, on respawn, and after every fruit that actually granted something.
 */
public class PacketBaubleFruitProgress implements IMessage {

    private int consumedMask;
    private int cap;

    public PacketBaubleFruitProgress() {
    }

    public PacketBaubleFruitProgress(int consumedMask, int cap) {
        this.consumedMask = consumedMask;
        this.cap = cap;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.consumedMask = buf.readInt();
        this.cap = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.consumedMask);
        buf.writeInt(this.cap);
    }

    /**
     * Deliberately carries no {@code @SideOnly} annotation, on the class or the method.
     * {@code SimpleNetworkWrapper.registerMessage(Class, ...)} instantiates the handler
     * immediately on <i>both</i> sides, and FML's SideTransformer throws outright when a
     * {@code @SideOnly(CLIENT)} class is loaded on a dedicated server. Client-only code is kept
     * safe by the side guard below instead: {@code Minecraft} sits in this class's constant pool
     * but is only resolved when the method actually runs, which happens on the client alone.
     */
    public static class Handler implements IMessageHandler<PacketBaubleFruitProgress, IMessage> {

        @Override
        public IMessage onMessage(final PacketBaubleFruitProgress message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    BaubleFruitProgress.setClientState(message.consumedMask, message.cap);
                }
            });
            return null;
        }
    }
}
