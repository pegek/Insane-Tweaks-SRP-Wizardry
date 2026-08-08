package com.spege.insanetweaks.entities;

import javax.annotation.Nonnull;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * Hardened item entity for drops carrying the Ashen Legacy property.
 *
 * <p>This entity ignores the environmental damage sources that proved unreliable on vanilla
 * EntityItem in practice. Which ones it actually ignores is
 * {@code ModConfig.ashenLegacy.immuneTo*} - the branches below read those flags on every hit, so
 * turning one off applies to drops that already exist.
 *
 * <p>{@code isImmuneToFire} is the one exception: it is a vanilla {@code Entity} field, read all
 * over the tick loop rather than at a single decision point, so it is stamped once at construction.
 * A drop created while fire immunity was on keeps it until it despawns.
 */
public class EntityItemIndestructible extends EntityItem {

    private static com.spege.insanetweaks.config.categories.AshenLegacyCategory cfg() {
        return com.spege.insanetweaks.config.ModConfig.ashenLegacy;
    }

    public EntityItemIndestructible(World world) {
        super(world);
        this.isImmuneToFire = cfg().immuneToFire;
    }

    public EntityItemIndestructible(World world, double x, double y, double z) {
        super(world, x, y, z);
        this.isImmuneToFire = cfg().immuneToFire;
    }

    public EntityItemIndestructible(World world, double x, double y, double z, ItemStack stack) {
        super(world, x, y, z, stack);
        this.isImmuneToFire = cfg().immuneToFire;
    }

    @Override
    public boolean isImmuneToExplosions() {
        return cfg().immuneToExplosions;
    }

    @Override
    @SuppressWarnings("null")
    public boolean attackEntityFrom(@Nonnull DamageSource source, float amount) {
        com.spege.insanetweaks.config.categories.AshenLegacyCategory cfg = cfg();
        if (cfg.immuneToFire && (source == DamageSource.IN_FIRE || source == DamageSource.ON_FIRE)) {
            return false;
        }
        if (cfg.immuneToLava && source == DamageSource.LAVA) {
            return false;
        }
        if (cfg.immuneToCactus && source == DamageSource.CACTUS) {
            return false;
        }
        if (cfg.immuneToExplosions && source.isExplosion()) {
            return false;
        }

        return super.attackEntityFrom(source, amount);
    }
}
