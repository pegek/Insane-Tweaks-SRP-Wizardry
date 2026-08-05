package com.spege.srpwizcore.dragonranged;

import com.github.alexthe666.iceandfire.api.ChainLightningUtils;
import com.github.alexthe666.iceandfire.api.IEntityEffectCapability;
import com.github.alexthe666.iceandfire.api.InFCapabilities;
import com.github.alexthe666.iceandfire.entity.EntityFireDragon;
import com.github.alexthe666.iceandfire.entity.EntityIceDragon;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.WorldServer;

/**
 * The three dragon elements and what they do on hit. Values copied verbatim from Spartan Fire's
 * melee weapon properties, so a fire longbow burns exactly as long as a fire katana.
 *
 * <p>Two deliberate departures from the melee versions: no knockback (the projectile already
 * knocks the target back) and no shield damage (that stays a privilege of dragonbone ammunition,
 * otherwise an ordinary arrow would match it on a second axis).
 *
 * <p>The lightning bonus is 6.75, matching the sword and the bolt. Ice and Fire's own arrow uses
 * 4.0; {@code MixinIandfDragonArrowLightning} raises that one to agree.
 */
public enum DragonElement {

    FIRE((byte) 1),
    ICE((byte) 2),
    LIGHTNING((byte) 3);

    /** Reserved: a missing NBT key reads back as 0, so no element may use it. */
    public static final byte ID_NONE = 0;

    private final byte nbtId;

    private DragonElement(byte nbtId) {
        this.nbtId = nbtId;
    }

    /** @return the value persisted on the projectile. Stable across reordering of the constants. */
    public byte nbtId() {
        return this.nbtId;
    }

    /** @return the element for a persisted id, or null for {@link #ID_NONE} and anything unknown. */
    public static DragonElement byNbtId(byte id) {
        if (id == ID_NONE) {
            return null;
        }
        DragonElement[] all = values();
        for (int i = 0; i < all.length; i++) {
            if (all[i].nbtId == id) {
                return all[i];
            }
        }
        return null;
    }

    /**
     * Applies this element to a target that was just hit. Server side only.
     *
     * @param shooter whoever fired the projectile; must NOT be null — chain lightning builds an
     *                {@code EntityDamageSourceIndirect} out of it and a null there dereferences on
     *                the death message. A caller without a real shooter passes the projectile itself.
     */
    public void applyOnHit(EntityLivingBase target, Entity shooter) {
        switch (this) {
            case FIRE: {
                if (target instanceof EntityIceDragon) {
                    target.attackEntityFrom(DamageSource.IN_FIRE, 13.5F);
                }
                target.setFire(5);
                break;
            }
            case ICE: {
                if (target instanceof EntityFireDragon) {
                    target.attackEntityFrom(DamageSource.DROWN, 13.5F);
                }
                IEntityEffectCapability cap = InFCapabilities.getEntityEffectCapability(target);
                if (cap != null) {
                    cap.setFrozen(200);
                }
                target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 2));
                target.addPotionEffect(new PotionEffect(MobEffects.MINING_FATIGUE, 100, 2));
                break;
            }
            case LIGHTNING: {
                if (target instanceof EntityFireDragon || target instanceof EntityIceDragon) {
                    target.attackEntityFrom(DamageSource.LIGHTNING_BOLT, 6.75F);
                }
                ChainLightningUtils.createChainLightningFromTarget(target.world, target, shooter);
                break;
            }
            default:
                break;
        }
    }

    /**
     * A short burst at the point of impact. Server-side broadcast with vanilla particle types —
     * Ice and Fire's own trail runs client-side off its entity's DataParameter, which an ordinary
     * arrow does not have, and syncing one would cost a packet for cosmetics.
     */
    public void spawnImpactParticles(WorldServer world, double x, double y, double z) {
        switch (this) {
            case FIRE:
                world.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 12, 0.25D, 0.25D, 0.25D, 0.02D);
                break;
            case ICE:
                world.spawnParticle(EnumParticleTypes.SNOW_SHOVEL, x, y, z, 14, 0.25D, 0.25D, 0.25D, 0.05D);
                break;
            case LIGHTNING: {
                // REDSTONE with count 0 hijacks the three offsets as RGB and the speed as a
                // multiplier, so one call = one coloured particle. Jitter the position instead.
                for (int i = 0; i < 10; i++) {
                    double dx = (world.rand.nextDouble() - 0.5D) * 0.6D;
                    double dy = (world.rand.nextDouble() - 0.5D) * 0.6D;
                    double dz = (world.rand.nextDouble() - 0.5D) * 0.6D;
                    world.spawnParticle(EnumParticleTypes.REDSTONE, x + dx, y + dy, z + dz,
                            0, 0.6D, 0.1D, 0.9D, 1.0D);
                }
                break;
            }
            default:
                break;
        }
    }
}
