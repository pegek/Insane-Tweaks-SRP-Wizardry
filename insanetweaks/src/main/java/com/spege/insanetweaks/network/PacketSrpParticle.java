package com.spege.insanetweaks.network;

import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Server-to-client burst of SRP 1.10.7 particles (FLASH / DOT / GCLOUD, ...)
 * with an arbitrary RGB colour. SRP's own SRPPacketParticle only supports a
 * handful of hardcoded scenarios, hence this packet.
 *
 * <p>The rendering half lives in {@link SrpParticleClient}, which is the only client-side class
 * in this file's orbit; see the handler below for why it cannot be inlined here.
 *
 * <p>The packet class itself is side-agnostic: the server constructs and serialises it, so it must
 * load there. {@code SRPEnumParticle} in the constructor is safe for that — despite its
 * {@code ...client.particle} package it is a bare enum, with no annotation and not one
 * {@code net.minecraft} reference in its constant pool (verified with {@code javap -v}).
 */
public class PacketSrpParticle implements IMessage {

    // Package-private, not private: SrpParticleClient reads them directly. Keeping them private
    // would mean either synthetic accessors or a nine-primitive call signature across the split.
    double x;
    double y;
    double z;
    byte typeId;
    int rgb;
    byte count;
    float spreadH;
    float spreadV;
    float speed;

    public PacketSrpParticle() {
    }

    public PacketSrpParticle(double x, double y, double z, SRPEnumParticle type, int rgb,
            int count, float spreadH, float spreadV, float speed) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.typeId = (byte) type.getParticleID();
        this.rgb = rgb;
        this.count = (byte) Math.min(count, 127);
        this.spreadH = spreadH;
        this.spreadV = spreadV;
        this.speed = speed;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.typeId = buf.readByte();
        this.rgb = buf.readInt();
        this.count = buf.readByte();
        this.spreadH = buf.readFloat();
        this.spreadV = buf.readFloat();
        this.speed = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeByte(this.typeId);
        buf.writeInt(this.rgb);
        buf.writeByte(this.count);
        buf.writeFloat(this.spreadH);
        buf.writeFloat(this.spreadV);
        buf.writeFloat(this.speed);
    }

    /**
     * 🚨 Deliberately carries no {@code @SideOnly}, on the class or the method — it used to, and
     * that was a dedicated-server crash. {@code SimpleNetworkWrapper.registerMessage(Class, ...)}
     * calls {@code handler.newInstance()} immediately on <i>both</i> sides (the {@code Side}
     * argument only picks which side <em>processes</em> the message), and FML's SideTransformer
     * throws outright when a {@code @SideOnly(CLIENT)} class is loaded on a dedicated server.
     * Nor can the registration be skipped server-side: discriminator ids have to match on both
     * ends or the channel desynchronises.
     *
     * <p>So this class must stay loadable everywhere, which is why the body only delegates. The
     * {@code invokestatic} to {@link SrpParticleClient} is resolved on first execution, not at
     * load or verification time, and execution is confined to the client by the guard below plus
     * the {@code Side.CLIENT} registration. Same reasoning as
     * {@link PacketBaubleFruitProgress.Handler}, one step stricter: that one leaves
     * {@code Minecraft} in its own constant pool, this one has no client symbol at all.
     */
    public static class Handler implements IMessageHandler<PacketSrpParticle, IMessage> {

        @Override
        public IMessage onMessage(PacketSrpParticle message, MessageContext ctx) {
            if (ctx.side != Side.CLIENT) {
                return null;
            }
            SrpParticleClient.spawn(message);
            return null;
        }
    }
}
