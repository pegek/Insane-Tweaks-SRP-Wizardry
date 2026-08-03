package com.spege.insanetweaks.network;

import java.util.Random;

import com.dhanantry.scapeandrunparasites.client.particle.ParticleSpawner;
import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-only tail of {@link PacketSrpParticle}: everything that touches {@code Minecraft} or
 * SRP's {@code ParticleSpawner}. Split out so that {@code PacketSrpParticle.Handler} — which
 * {@code SimpleNetworkWrapper} instantiates on <em>both</em> sides — contains no client symbol at
 * all, and so a dedicated server never has a reason to load this class.
 *
 * <p>🚨 The {@code @SideOnly} belongs here and nowhere up the chain. SRP's
 * {@code ParticleSpawner} initialises a {@code static Minecraft mc = Minecraft.getMinecraft()},
 * so merely <em>loading</em> it on a dedicated server fails; keeping it behind a class the server
 * never touches is the point of the split. The single {@code invokestatic} in
 * {@code Handler.onMessage} does not drag this class in: JVM method resolution is lazy, and the
 * verifier's assignability check on the argument short-circuits because the declared parameter
 * type and the stack type are the same class name.
 *
 * <p>Kept in the {@code network} package on purpose, so it can read {@link PacketSrpParticle}'s
 * package-private fields instead of the handler passing nine loose primitives across.
 */
@SideOnly(Side.CLIENT)
final class SrpParticleClient {

    private SrpParticleClient() {
    }

    /** Schedules the burst onto the client thread; called only from a CLIENT-bound handler. */
    static void spawn(final PacketSrpParticle message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
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
            }
        });
    }
}
