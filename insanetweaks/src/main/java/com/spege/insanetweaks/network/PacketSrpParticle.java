package com.spege.insanetweaks.network;

import java.util.Random;

import com.dhanantry.scapeandrunparasites.client.particle.ParticleSpawner;
import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Server-to-client burst of SRP 1.10.7 particles (FLASH / DOT / GCLOUD, ...)
 * with an arbitrary RGB colour. SRP's own SRPPacketParticle only supports a
 * handful of hardcoded scenarios, hence this packet. The handler is client-only
 * because {@link ParticleSpawner} is a client-only class (same sided pattern as
 * PacketOpenSentinelLoot).
 */
public class PacketSrpParticle implements IMessage {

    private double x;
    private double y;
    private double z;
    private byte typeId;
    private int rgb;
    private byte count;
    private float spreadH;
    private float spreadV;
    private float speed;

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

    // This class must never be classloaded on a dedicated server: ParticleSpawner's
    // static initializer touches Minecraft. registerMessage only stores the Class
    // reference; the handler is instantiated when a CLIENT-bound packet arrives.
    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketSrpParticle, IMessage> {

        @Override
        public IMessage onMessage(PacketSrpParticle message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                SRPEnumParticle type = SRPEnumParticle.getParticleFromId(message.typeId);
                if (type == null) {
                    return;
                }

                Random rand = new Random();
                int r = (message.rgb >> 16) & 0xFF;
                int g = (message.rgb >> 8) & 0xFF;
                int b = message.rgb & 0xFF;

                for (int i = 0; i < message.count; i++) {
                    double px = message.x + (rand.nextFloat() * 2.0F - 1.0F) * message.spreadH;
                    double py = message.y + (rand.nextFloat() * 2.0F - 1.0F) * message.spreadV;
                    double pz = message.z + (rand.nextFloat() * 2.0F - 1.0F) * message.spreadH;
                    ParticleSpawner.spawnParticle(type, px, py, pz,
                            rand.nextGaussian() * message.speed,
                            rand.nextGaussian() * message.speed,
                            rand.nextGaussian() * message.speed,
                            r, g, b);
                }
            });
            return null;
        }
    }
}
