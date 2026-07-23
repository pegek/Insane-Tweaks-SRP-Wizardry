package com.spege.insanetweaks.util;

import com.dhanantry.scapeandrunparasites.client.particle.SRPEnumParticle;
import com.spege.insanetweaks.network.InsaneTweaksNetwork;
import com.spege.insanetweaks.network.PacketSrpParticle;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;

/**
 * Server-side cast feedback for spells: particle bursts and the common
 * "particle burst + sound at an entity" impact pattern. Centralizes the
 * {@code world instanceof WorldServer} dance every spell used to repeat.
 */
public final class SpellCastFeedback {

    private SpellCastFeedback() {
    }

    /** Spawns a server-side particle burst at the given position. No-op client-side. */
    public static void particleBurst(World world, double x, double y, double z,
            EnumParticleTypes type, int count, double spreadX, double spreadY, double spreadZ, double speed) {
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(type, x, y, z, count, spreadX, spreadY, spreadZ, speed);
        }
    }

    /**
     * Particle burst anchored to an entity, at {@code heightFraction} of its
     * height (0 = feet, 0.5 = torso, 1 = head).
     */
    public static void particleBurstAt(World world, EntityLivingBase entity, double heightFraction,
            EnumParticleTypes type, int count, double spreadX, double spreadY, double spreadZ, double speed) {
        particleBurst(world, entity.posX, entity.posY + entity.height * heightFraction, entity.posZ,
                type, count, spreadX, spreadY, spreadZ, speed);
    }

    /** Particle burst + sound at the same entity — the usual cast/impact feedback. */
    public static void impactAt(World world, EntityLivingBase entity, double heightFraction,
            EnumParticleTypes type, int count, double spreadX, double spreadY, double spreadZ, double speed,
            SoundEvent sound, SoundCategory category, float volume, float pitch) {
        particleBurstAt(world, entity, heightFraction, type, count, spreadX, spreadY, spreadZ, speed);
        world.playSound(null, entity.posX, entity.posY, entity.posZ, sound, category, volume, pitch);
    }

    /**
     * Server-side SRP particle burst: sends {@link PacketSrpParticle} to all
     * clients within 48 blocks. No-op client-side. {@code rgb} is 0xRRGGBB.
     */
    public static void srpBurst(World world, double x, double y, double z,
            SRPEnumParticle type, int rgb, int count, float spreadH, float spreadV, float speed) {
        if (world.isRemote) {
            return;
        }
        InsaneTweaksNetwork.CHANNEL.sendToAllAround(
                new PacketSrpParticle(x, y, z, type, rgb, count, spreadH, spreadV, speed),
                new NetworkRegistry.TargetPoint(world.provider.getDimension(), x, y, z, 48.0D));
    }

    /**
     * SRP particle burst anchored to an entity at {@code heightFraction} of its
     * height (0 = feet, 0.5 = torso, 1 = head).
     */
    public static void srpBurstAt(World world, EntityLivingBase entity, double heightFraction,
            SRPEnumParticle type, int rgb, int count, float spreadH, float spreadV, float speed) {
        srpBurst(world, entity.posX, entity.posY + entity.height * heightFraction, entity.posZ,
                type, rgb, count, spreadH, spreadV, speed);
    }
}
