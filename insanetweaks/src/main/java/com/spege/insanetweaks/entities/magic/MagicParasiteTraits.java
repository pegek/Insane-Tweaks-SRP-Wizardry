package com.spege.insanetweaks.entities.magic;

import java.util.Random;

import com.spege.insanetweaks.config.ModConfig;
import com.spege.insanetweaks.config.categories.EntitiesCategory;
import com.spege.insanetweaks.entities.sapling.SaplingSiteCheck;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * What the three magic parasites share: config access, the Arcane-touched damage passive, the
 * purple particle language, teleporting and finding a free spot. No client types. Particles from
 * the server go through {@link WorldServer#spawnParticle}; the client aura uses the common
 * {@link World#spawnParticle}, which is a no-op on a server.
 */
public final class MagicParasiteTraits {

    private static final int POSITION_ATTEMPTS = 10;
    private static final int VERTICAL_SEARCH = 3;

    private MagicParasiteTraits() {
    }

    public static EntitiesCategory.MagicParasites cfg() {
        return ModConfig.entities.magicParasites;
    }

    public static boolean enabled() {
        return cfg().enableMagicParasites;
    }

    /** Multiplies an attribute's base value; called from applyEntityAttributes, after SRP's. */
    public static void multiplyBase(EntityLivingBase entity, IAttribute attribute, double multiplier) {
        IAttributeInstance instance = entity.getEntityAttribute(attribute);
        if (instance != null && multiplier != 1.0D) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
        }
    }

    /** Arcane-touched: less magic damage, more fire damage. */
    public static float scaleIncoming(DamageSource source, float amount) {
        if (!enabled()) {
            return amount;
        }
        return ArcaneDamage.scale(amount, source.isMagicDamage(), source.isFireDamage(), cfg().magicDamageTaken,
                cfg().fireDamageTaken);
    }

    /** A puff of purple witch particles, sent from the server. */
    public static void burst(World world, double x, double y, double z, int count, double spread) {
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.SPELL_WITCH, x, y, z, count, spread, spread,
                    spread, 0.02D);
        }
    }

    /** The telegraph cue around a caster's body. */
    public static void telegraph(EntityLivingBase entity) {
        burst(entity.world, entity.posX, entity.posY + entity.height * 0.6D, entity.posZ, 6, entity.width * 0.6D);
    }

    /** A dotted line of particles, e.g. Ward Allies' beam. */
    public static void beam(World world, double x1, double y1, double z1, double x2, double y2, double z2) {
        int points = 10;
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            burst(world, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, z1 + (z2 - z1) * t, 1, 0.05D);
        }
    }

    public static void sound(Entity entity, SoundEvent sound, float pitch) {
        entity.world.playSound(null, entity.posX, entity.posY, entity.posZ, sound, SoundCategory.HOSTILE, 1.0F,
                pitch);
    }

    /**
     * Tries each offset around a base point with vanilla's {@code attemptTeleport} (searches down
     * for ground, refuses solid or liquid collisions and resets the position on failure). Returns
     * whether one succeeded.
     */
    public static boolean tryTeleport(EntityLivingBase entity, double baseX, double baseY, double baseZ,
            double[][] offsets) {
        double fromX = entity.posX;
        double fromY = entity.posY;
        double fromZ = entity.posZ;
        for (double[] o : offsets) {
            if (entity.attemptTeleport(baseX + o[0], baseY, baseZ + o[1])) {
                burst(entity.world, fromX, fromY + entity.height * 0.5D, fromZ, 16, 0.4D);
                burst(entity.world, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ, 16, 0.4D);
                sound(entity, SoundEvents.ENTITY_ENDERMEN_TELEPORT, 1.3F);
                return true;
            }
        }
        return false;
    }

    /** Removes a spectral with a purple poof. {@code setDead} skips onDeath: no drops, no SRP points. */
    public static void dispel(Entity entity) {
        burst(entity.world, entity.posX, entity.posY + entity.height * 0.5D, entity.posZ, 20, 0.4D);
        entity.world.playSound(null, entity.posX, entity.posY, entity.posZ, SoundEvents.ENTITY_ILLAGER_MIRROR_MOVE,
                SoundCategory.HOSTILE, 0.8F, 1.4F);
        entity.setDead();
    }

    /** Client-side aura: sparse while idle, dense while an ability is up. */
    public static void clientAura(EntityLivingBase entity, boolean active) {
        if (!entity.world.isRemote || entity.ticksExisted % (active ? 1 : 5) != 0) {
            return;
        }
        Random rand = entity.getRNG();
        entity.world.spawnParticle(EnumParticleTypes.SPELL_WITCH,
                entity.posX + (rand.nextDouble() - 0.5D) * entity.width,
                entity.posY + rand.nextDouble() * entity.height,
                entity.posZ + (rand.nextDouble() - 0.5D) * entity.width, 0.0D, 0.0D, 0.0D);
    }

    /** A spot with solid ground and two free blocks, {@code min}-{@code max} blocks from center. */
    public static BlockPos findStandable(World world, BlockPos center, Random rand, int min, int max) {
        int inner = Math.min(min, max);
        int outer = Math.max(min, max);
        for (int attempt = 0; attempt < POSITION_ATTEMPTS; attempt++) {
            double angle = rand.nextDouble() * Math.PI * 2.0D;
            double dist = inner + rand.nextDouble() * (outer - inner);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);
            for (int dy = VERTICAL_SEARCH; dy >= -VERTICAL_SEARCH; dy--) {
                BlockPos feet = new BlockPos(x, center.getY() + dy, z);
                if (isStandable(world, feet)) {
                    return feet;
                }
            }
        }
        return null;
    }

    private static boolean isStandable(World world, BlockPos feet) {
        BlockPos below = feet.down();
        if (!world.getBlockState(below).isSideSolid(world, below, EnumFacing.UP)) {
            return false;
        }
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos body = feet.up(dy);
            if (SaplingSiteCheck.collides(world, body) || world.getBlockState(body).getMaterial().isLiquid()) {
                return false;
            }
        }
        return true;
    }
}
